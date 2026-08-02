package com.zenithblue.nativecode.terminal

import android.content.Context
import android.util.Log
import com.zenithblue.nativecode.RootShell

/**
 * Measure Debian chroot rootfs size via staged [chroot_size.sh].
 *
 * Emits `SIZE_BYTES=` token (immune to Magisk/su preamble). Excludes host binds
 * (sdcard/dev/proc/sys/mnt/run). Blocking — call from a background executor only.
 */
object ChrootSizeManager {

    private const val TAG = "ChrootSize"
    private const val ASSET = "scripts/chroot/chroot_size.sh"
    /** Long du walks after heavy chroot use — hard cap so chrootMeasuring cannot stick. */
    private const val MEASURE_TIMEOUT_MS = 90_000L

    data class Result(
        val path: String,
        val bytes: Long?,
        val dirExists: Boolean,
        val viaRoot: Boolean,
        val raw: String,
        val exitCode: Int,
        val rootOk: Boolean,
        val error: String? = null
    )

    /**
     * Stage helper + run measure. Background thread only.
     */
    fun measure(
        context: Context,
        path: String = ChrootCommandBuilder.CHROOT_PATH
    ): Result {
        if (!RootShell.isRootAvailable()) {
            return Result(
                path = path,
                bytes = null,
                dirExists = false,
                viaRoot = false,
                raw = "",
                exitCode = -1,
                rootOk = false,
                error = "root_required"
            )
        }
        val staged = RootShell.stageAsset(context, ASSET)
        if (staged == null) {
            Log.w(TAG, "stage failed for $ASSET — falling back to inline")
            return measureInline(path)
        }
        val cap = RootShell.captureResult(
            "sh \"$staged\" '$path'",
            timeoutMs = MEASURE_TIMEOUT_MS
        )
        Log.d(TAG, "measure exit=${cap.exitCode} raw:\n${cap.stdout.take(400)}")
        val parsed = parse(cap.stdout, path, cap.exitCode, rootOk = true)
        if (parsed.bytes == null && parsed.error != "no_dir") {
            Log.w(TAG, "parse failed duOut=${cap.stdout.take(400)} exit=${cap.exitCode}")
            // One more try: inline fallback if staged helper produced nothing useful
            if (cap.exitCode == -2 || cap.stdout.isBlank()) {
                return measureInline(path)
            }
        }
        return parsed
    }

    /**
     * Inline walk (quoted paths + SIZE_BYTES= marker) if staging fails.
     */
    private fun measureInline(path: String): Result {
        val existsOut = RootShell.capture(
            "if [ -d '$path' ]; then echo YES; else echo NO; fi"
        )
        val dirExists = existsOut.trim().lines().lastOrNull()?.trim() == "YES"
        if (!dirExists) {
            return Result(
                path = path,
                bytes = null,
                dirExists = false,
                viaRoot = false,
                raw = existsOut,
                exitCode = 1,
                rootOk = true,
                error = "no_dir"
            )
        }
        // Never sum with shell $(( )) — toybox ash is 32-bit signed; multi-GB overflows
        // to negative SIZE_BYTES. Pipe du lines into awk for a 64-bit-safe total.
        val d = "${'$'}"
        val duCmd = buildString {
            append("echo SIZE_BYTES=${d}(")
            append("for e in \"$path\"/* \"$path\"/.[!.]* \"$path\"/..?*; do ")
            append("[ -e ${d}e ] || continue; ")
            append("n=${d}(basename \"${d}e\"); ")
            append("case ${d}n in sdcard|dev|proc|sys|mnt|run) continue ;; esac; ")
            append("du -sb \"${d}e\" 2>/dev/null; ")
            append("done | awk '{ s += ${d}1 } END { printf \"%.0f\", s+0 }'")
            append(")")
        }
        val cap = RootShell.captureResult(duCmd, timeoutMs = MEASURE_TIMEOUT_MS)
        Log.d(TAG, "inline exit=${cap.exitCode} raw:\n${cap.stdout.take(400)}")
        if (cap.stdout.isBlank() || parseSizeBytes(cap.stdout) == null) {
            Log.w(TAG, "inline probe failed duOut=${cap.stdout.take(400)} exit=${cap.exitCode}")
        }
        return parse(cap.stdout, path, cap.exitCode, rootOk = true).copy(dirExists = true)
    }

    internal fun parse(
        raw: String,
        defaultPath: String,
        exitCode: Int,
        rootOk: Boolean
    ): Result {
        var path = defaultPath
        var error: String? = null
        var bytes: Long? = null

        for (line in raw.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("# path=") -> path = t.removePrefix("# path=").trim()
                t.startsWith("# error=") -> error = t.removePrefix("# error=").trim()
                t.startsWith("SIZE_BYTES=") -> {
                    val n = t.removePrefix("SIZE_BYTES=").trim().toLongOrNull()
                    if (n != null && n >= 0L) bytes = n
                }
            }
        }
        // Legacy fallback: last pure integer line
        if (bytes == null) {
            bytes = raw.trim().lines()
                .mapNotNull { it.trim().toLongOrNull() }
                .lastOrNull()
                ?.takeIf { it >= 0L }
        }

        val dirExists = error != "no_dir" && (bytes != null || error == null)
        val viaRoot = bytes != null
        if (bytes == null && error == null) {
            error = when {
                exitCode == -2 -> "timeout"
                raw.isBlank() -> "empty_output"
                else -> "measure_failed"
            }
        }
        return Result(
            path = path,
            bytes = bytes,
            dirExists = dirExists && error != "no_dir",
            viaRoot = viaRoot,
            raw = raw,
            exitCode = exitCode,
            rootOk = rootOk && error != "root_required",
            error = error
        )
    }

    private fun parseSizeBytes(raw: String): Long? {
        for (line in raw.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("SIZE_BYTES=")) {
                return t.removePrefix("SIZE_BYTES=").trim().toLongOrNull()?.takeIf { it >= 0L }
            }
        }
        return null
    }
}
