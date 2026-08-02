package com.zenithblue.nativecode.terminal

import android.content.Context
import android.util.Log
import com.zenithblue.nativecode.RootShell
import java.io.File
import java.util.concurrent.TimeUnit

/** Builds shell arguments and environment map for chroot Debian sessions (SSOT helper). */
object ChrootCommandBuilder {

    private const val TAG = "ChrootCommandBuilder"

    const val CHROOT_PATH = "/data/local/tmp/chrootDebian13"

    /** On-device SSOT helper (assets/scripts/chroot/nativecode_chroot.sh). */
    const val CHROOT_HELPER = "/data/local/tmp/nativecode_chroot.sh"
    const val CHROOT_HELPER_ASSET = "scripts/chroot/nativecode_chroot.sh"
    /** Must match first `# nativecode-chroot vN` line in the asset. */
    const val CHROOT_HELPER_VERSION = "nativecode-chroot v2.2"

    /** Compat wrappers (thin → helper); setup still installs for ADB users. */
    const val RUNNER_ROOT = "/data/local/tmp/run_debian13_root.sh"
    const val ENTER_FLUX = "/data/local/tmp/enter_debian13.sh"
    const val ENTER_ROOT = "/data/local/tmp/enter_debian13_root.sh"

    /** Session executable — must be a system binary (SELinux blocks exec of app-data scripts). */
    const val SESSION_EXEC = "/system/bin/sh"

    private const val LAUNCH_TOOL_VERSION = "nativecode-launch-tool v2"
    private const val TERM_WRAPPER_VERSION = "nativecode-chroot-term-wrapper v3"

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux"
    ): Pair<Array<String>, HashMap<String, String>> {
        ensureLauncherScript(ctx)
        ensureHelperScript(ctx)

        val u = if (user == "root") "root" else "flux"
        val workdir = parseInteractiveWorkdir(shellCmd)
        val isInteractive = workdir != null ||
            shellCmd == "exec zsh" ||
            shellCmd == "/bin/bash --login" ||
            shellCmd.isBlank()
        val toolExec = parseToolExec(shellCmd)

        // Always `sh $HELPER` (not bare exec of script) — SELinux often blocks exec of
        // /data/local/tmp/*.sh; /system/bin/sh interpreting the script is reliable.
        val rootInner: String = when {
            isInteractive && u == "root" -> {
                val wd = workdir?.let { " --workdir ${shellSingleQuote(it)}" } ?: ""
                "exec sh $CHROOT_HELPER login --user root --shell bash$wd"
            }
            isInteractive -> {
                val wd = workdir?.let { " --workdir ${shellSingleQuote(it)}" } ?: ""
                "exec sh $CHROOT_HELPER login --user flux --shell zsh$wd"
            }
            toolExec != null -> {
                val (dir, argv) = toolExec
                if (dir != null) {
                    // cd then exec launch_tool — b64 keeps TTY (v2.2); argv may have spaces
                    val payload = "mkdir -p ${shellSingleQuote(dir)} && cd ${shellSingleQuote(dir)} && exec $argv"
                    val b64 = android.util.Base64.encodeToString(
                        payload.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP
                    )
                    "exec sh $CHROOT_HELPER b64 --user $u -- $b64"
                } else {
                    "exec sh $CHROOT_HELPER exec --user $u -- $argv"
                }
            }
            isSimpleGuestCmd(shellCmd) -> {
                val esc = shellCmd.replace("'", "'\\''")
                "exec sh $CHROOT_HELPER sh --user $u -- '$esc'"
            }
            else -> {
                val b64 = android.util.Base64.encodeToString(
                    shellCmd.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                "exec sh $CHROOT_HELPER b64 --user $u -- $b64"
            }
        }

        val cmd = RootShell.shellRootCommand(rootInner)

        // Inline WINCH trap on outer sh (mShellPid). Keep this shell as parent (no leading exec).
        val winchCmd =
            "trap 'kill -WINCH -\$\$ 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH; $cmd"

        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "/system/bin:/system/xbin:/sbin:" + (envMap["PATH"] ?: "")
        envMap["TERM"] = "xterm-256color"
        envMap["HOME"] = if (u == "root") "/root" else "/home/flux"
        envMap["LANG"] = "en_US.UTF-8"
        envMap["LC_ALL"] = "en_US.UTF-8"
        envMap["XDG_RUNTIME_DIR"] = "/tmp"
        envMap["TMPDIR"] = "/tmp"

        return arrayOf(SESSION_EXEC, "-c", winchCmd) to envMap
    }

    /**
     * Stage [CHROOT_HELPER] from assets when missing or version stamp mismatches.
     * Uses [RootShell] su discovery (not hard-coded `/system/bin/su`).
     * Safe to call from a background thread; may briefly invoke su.
     */
    fun ensureHelperScript(ctx: Context): Boolean {
        return try {
            val existing = RootShell.capture(
                "head -n 2 $CHROOT_HELPER 2>/dev/null || true",
                timeoutMs = 4_000L
            )
            if (existing.contains(CHROOT_HELPER_VERSION)) {
                return true
            }

            val staged = RootShell.stageAsset(ctx, CHROOT_HELPER_ASSET)
                ?: run {
                    val tmp = File(ctx.cacheDir, "nativecode_chroot.sh")
                    ctx.assets.open(CHROOT_HELPER_ASSET).use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    tmp.absolutePath
                }

            var code = RootShell.executeSync(
                "cp -f '$staged' $CHROOT_HELPER && chmod 755 $CHROOT_HELPER && " +
                    "grep -q '$CHROOT_HELPER_VERSION' $CHROOT_HELPER"
            )
            if (code != 0) {
                // Last resort: base64 stream (app cannot write /data/local/tmp)
                val bytes = ctx.assets.open(CHROOT_HELPER_ASSET).use { it.readBytes() }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                code = RootShell.executeSync(
                    "echo $b64 | base64 -d > $CHROOT_HELPER && chmod 755 $CHROOT_HELPER"
                )
            }
            if (code != 0) {
                Log.w(TAG, "ensureHelperScript root stage failed exit=$code")
                return false
            }
            Log.i(TAG, "ensureHelperScript staged $CHROOT_HELPER")
            true
        } catch (e: Exception) {
            Log.w(TAG, "ensureHelperScript failed: ${e.message}")
            false
        }
    }

    /**
     * Guest payload safe to embed in `sh --user U -- '…'`.
     * Reject `$` / backticks / quotes / newlines / backslash — those need b64.
     * Single-quote must force b64: nested `su -c '…'` + `sh -- '…'` breaks git paths.
     */
    private fun isSimpleGuestCmd(shellCmd: String): Boolean {
        if (shellCmd.isEmpty()) return false
        for (c in shellCmd) {
            when (c) {
                '$', '`', '"', '\'', '\n', '\r', '\\' -> return false
            }
        }
        return true
    }

    private fun shellSingleQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    /**
     * Workspace shell: `mkdir -p DIR && cd DIR && exec zsh` → interactive login + --workdir.
     * Returns workdir path, or null if not that form. Empty string = interactive home (no workdir).
     * Pure `exec zsh` / blank handled separately (null here, isInteractive true).
     */
    private fun parseInteractiveWorkdir(shellCmd: String): String? {
        val t = shellCmd.trim()
        // mkdir -p DIR && cd DIR && exec zsh  (DIR unquoted, no spaces in practice)
        val m = Regex("""^mkdir -p (.+) && cd \1 && exec zsh$""").matchEntire(t)
            ?: return null
        val dir = m.groupValues[1].trim()
        return dir.takeIf { it.isNotEmpty() && !it.contains('\'') }
    }

    /**
     * AI tool launch: `/tmp/launch_tool.sh TOOL` or `mkdir -p DIR && cd DIR && /tmp/launch_tool.sh TOOL`.
     * Returns (workdir or null, argv string for helper exec/b64).
     */
    private fun parseToolExec(shellCmd: String): Pair<String?, String>? {
        val t = shellCmd.trim()
        val withDir = Regex(
            """^mkdir -p (.+) && cd \1 && (/tmp/launch_tool\.sh\s+\S+)$"""
        ).matchEntire(t)
        if (withDir != null) {
            val dir = withDir.groupValues[1].trim()
            if (dir.contains('\'')) return null
            return dir to withDir.groupValues[2].trim()
        }
        val bare = Regex("""^(/tmp/launch_tool\.sh\s+\S+)$""").matchEntire(t)
        if (bare != null) {
            return null to bare.groupValues[1].trim()
        }
        return null
    }

    /**
     * Best-effort ADB mirror of WINCH wrapper under /data/local/tmp (via su).
     * Not used as sessionExec — SELinux/path; session uses SESSION_EXEC + inline trap.
     */
    fun ensureTermWrapper(): Boolean {
        val scriptPath = "/data/local/tmp/chroot_term_wrapper.sh"
        val script = """#!/system/bin/sh
# $TERM_WRAPPER_VERSION
# ADB/debug mirror only — app sessions use inline trap on /system/bin/sh
trap 'kill -WINCH -${'$'}${'$'} 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH
"${'$'}@"
exit ${'$'}?
""".trimIndent()
        return try {
            val existing = try {
                val p = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c", "cat $scriptPath 2>/dev/null"))
                p.inputStream.bufferedReader().readText().also {
                    p.waitFor(3, TimeUnit.SECONDS)
                }
            } catch (_: Exception) {
                ""
            }
            if (existing == script) return true
            val b64 = android.util.Base64.encodeToString(script.toByteArray(), android.util.Base64.NO_WRAP)
            val p = Runtime.getRuntime().exec(
                arrayOf(
                    "/system/bin/su", "-c",
                    "echo $b64 | base64 -d > $scriptPath && chmod 755 $scriptPath"
                )
            )
            p.waitFor(5, TimeUnit.SECONDS)
            p.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * AI tool launcher written to app usr/tmp; helper copies into chroot /tmp on mount.
     * Always rewrites when content differs (stale short-PATH scripts fixed).
     */
    fun ensureLauncherScript(ctx: Context): Boolean {
        val scriptPath = "${ctx.filesDir}/usr/tmp/launch_tool.sh"
        val script = """#!/bin/zsh
# $LAUNCH_TOOL_VERSION
export HOME=/home/flux
export TERM="${'$'}{TERM:-xterm-256color}"
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
export XDG_RUNTIME_DIR=/tmp
export TMPDIR=/tmp
export NVM_DIR="${'$'}{NVM_DIR:-${'$'}HOME/.nvm}"

export PATH="${'$'}HOME/.local/bin:${'$'}HOME/bin:${'$'}HOME/.cargo/bin:/opt/nodejs/bin:/usr/local/bin:/usr/bin:/bin:/sbin"

# SSOT: same env as interactive setup_cli_tools
if [ -f "${'$'}HOME/.config/fluxlinux/cli-tools.env" ]; then
  . "${'$'}HOME/.config/fluxlinux/cli-tools.env"
fi

# Fallback if cli-tools.env missing: latest nvm node bin
if [ -d "${'$'}NVM_DIR/versions/node" ]; then
  _n=${'$'}(ls -1d "${'$'}NVM_DIR/versions/node"/v* 2>/dev/null | sort -V | tail -1)
  [ -n "${'$'}_n" ] && [ -d "${'$'}_n/bin" ] && export PATH="${'$'}_n/bin:${'$'}PATH"
  unset _n
fi

if [ ${'$'}# -lt 1 ]; then
  echo "launch_tool: missing command" >&2
  exit 127
fi
if ! command -v "${'$'}1" >/dev/null 2>&1; then
  echo "launch_tool: command not found: ${'$'}1 (PATH=${'$'}PATH)" >&2
  exit 127
fi
exec "${'$'}@"
""".trimIndent()
        return try {
            val f = File(scriptPath)
            f.parentFile?.mkdirs()
            if (f.exists() && f.readText() == script) return true
            f.writeText(script)
            f.setExecutable(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Guest shell env one-liner (fallback only; tools prefer launch_tool.sh).
     * Null-glob-safe nvm scan; single-quoted by ProotCommandBuilder so host bash
     * does not expand $HOME/$PATH.
     */
    fun toolEnvInit(): String =
        "setopt NULL_GLOB 2>/dev/null; " +
            "[ -f \$HOME/.config/fluxlinux/cli-tools.env ] && . \$HOME/.config/fluxlinux/cli-tools.env; " +
            "export PATH=\$HOME/.local/bin:\$HOME/bin:\$HOME/.cargo/bin:\$PATH; " +
            "export NVM_DIR=\${NVM_DIR:-\$HOME/.nvm}; " +
            "for _n in \$NVM_DIR/versions/node/v*/bin; do " +
            "[ -d \"\$_n\" ] && export PATH=\$_n:\$PATH; done; unset _n"

    fun toolBinaryName(type: String): String = when (type) {
        "opencode" -> "opencode"
        "codex" -> "codex"
        "agy" -> "agy"
        "claude-code" -> "claude"
        "qwen-code" -> "qwen"
        "grok" -> "grok"
        "kiro" -> "kiro-cli"
        else -> "zsh"
    }

    /**
     * Shell command for tool/shell session.
     * Both chroot and proot use /tmp/launch_tool.sh (proot via --shared-tmp → PREFIX/tmp).
     * @param workDir guest cwd; null = user home (/home/flux or /root for shell-root)
     */
    fun buildToolShellCommand(ctx: Context, type: String, workDir: String?): String {
        if (type == "shell" || type == "shell-root") {
            val home = if (type == "shell-root") "/root" else "/home/flux"
            val dir = workDir?.takeIf { it.isNotBlank() } ?: home
            return if (workDir.isNullOrBlank()) {
                // Interactive login: builders use su -/proot --user (HOME from login)
                "exec zsh"
            } else {
                // Chroot maps this to login --workdir (preserves TTY); proot still zsh -c
                "mkdir -p $dir && cd $dir && exec zsh"
            }
        }
        val dir = workDir?.takeIf { it.isNotBlank() } ?: "/home/flux"
        val tool = toolBinaryName(type)
        // Write host launch_tool; helper copies into guest /tmp on mount
        ensureLauncherScript(ctx)
        return if (workDir.isNullOrBlank()) {
            "/tmp/launch_tool.sh $tool"
        } else {
            "mkdir -p $dir && cd $dir && /tmp/launch_tool.sh $tool"
        }
    }
}
