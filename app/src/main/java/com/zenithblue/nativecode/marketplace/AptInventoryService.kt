package com.zenithblue.nativecode.marketplace

import android.content.Context
import android.util.Log
import com.zenithblue.nativecode.terminal.LinuxCommandBuilder
import com.zenithblue.nativecode.terminal.ProjectPathResolver
import com.zenithblue.nativecode.terminal.ShellCommandRunner
import java.util.Locale

/** Scan guest dpkg inventory for Software Manager. */
object AptInventoryService {
    private const val TAG = "AptInventory"

    private val SECTION_TITLES = mapOf(
        "admin" to "System",
        "base" to "System",
        "kernel" to "System",
        "libs" to "Libraries",
        "oldlibs" to "Libraries",
        "libdevel" to "Libraries",
        "devel" to "Development",
        "debug" to "Development",
        "interpreters" to "Development",
        "rust" to "Development",
        "python" to "Development",
        "java" to "Development",
        "net" to "Networking",
        "web" to "Networking",
        "mail" to "Networking",
        "x11" to "Graphics / X11",
        "graphics" to "Graphics / X11",
        "sound" to "Graphics / X11",
        "video" to "Graphics / X11",
        "utils" to "Utils",
        "shells" to "Utils",
        "editors" to "Utils",
        "text" to "Utils",
        "doc" to "Utils",
        "games" to "Games",
        "science" to "Science",
        "math" to "Science"
    )

    fun guestReady(ctx: Context, method: String = LinuxCommandBuilder.currentMethod): Boolean {
        return if (method == "chroot") {
            ProjectPathResolver.isChrootInstalled()
        } else {
            ProjectPathResolver.isProotRootfsPresent(ctx)
        }
    }

    fun scan(
        ctx: Context,
        method: String = LinuxCommandBuilder.currentMethod
    ): Result<List<AptPackage>> {
        if (!guestReady(ctx, method)) {
            return Result.failure(IllegalStateException("Guest rootfs not ready for $method"))
        }
        val query =
            "dpkg-query -W -f='\${db:Status-Abbrev}\\t\${Package}\\t\${Version}\\t\${Installed-Size}\\t\${Section}\\n' 2>/dev/null"
        return try {
            val (cmd, env) = LinuxCommandBuilder.build(ctx, query, user = "root", method = method)
            val out = ShellCommandRunner.runCapture(ctx, cmd, env)
            val pkgs = parseDpkgQuery(out)
            if (pkgs.isEmpty() && out.isBlank()) {
                // retry as flux (read-only dpkg)
                val (cmd2, env2) = LinuxCommandBuilder.build(ctx, query, user = "flux", method = method)
                val out2 = ShellCommandRunner.runCapture(ctx, cmd2, env2)
                Result.success(parseDpkgQuery(out2))
            } else {
                Result.success(pkgs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "scan failed", e)
            Result.failure(e)
        }
    }

    fun groupByCategory(packages: List<AptPackage>): List<AptCategoryGroup> {
        val map = linkedMapOf<String, MutableList<AptPackage>>()
        packages.sortedBy { it.name.lowercase(Locale.US) }.forEach { p ->
            val key = p.section.ifBlank { "other" }.lowercase(Locale.US)
            map.getOrPut(key) { mutableListOf() }.add(p)
        }
        return map.entries
            .map { (section, list) ->
                AptCategoryGroup(
                    title = SECTION_TITLES[section] ?: section.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                    },
                    sectionKey = section,
                    packages = list
                )
            }
            .sortedBy { it.title.lowercase(Locale.US) }
    }

    fun friendlyTitle(section: String): String {
        val key = section.ifBlank { "other" }.lowercase(Locale.US)
        return SECTION_TITLES[key] ?: section.ifBlank { "Other" }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun parseDpkgQuery(text: String): List<AptPackage> {
        val out = mutableListOf<AptPackage>()
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            if (parts.size < 4) return@forEach
            val status = parts[0].trim()
            // installed: ii, hi, etc.
            if (!status.startsWith("ii") && !status.startsWith("hi")) return@forEach
            val name = parts[1].trim()
            if (name.isEmpty()) return@forEach
            val version = parts.getOrElse(2) { "" }.trim()
            val sizeKib = parts.getOrElse(3) { "0" }.trim().toLongOrNull() ?: 0L
            val section = parts.getOrElse(4) { "other" }.trim().ifBlank { "other" }
            // strip subsection after /
            val sectionBase = section.substringBefore('/').ifBlank { "other" }
            out.add(
                AptPackage(
                    name = name,
                    version = version,
                    sizeBytes = sizeKib * 1024L,
                    section = sectionBase,
                    status = status
                )
            )
        }
        return out
    }
}
