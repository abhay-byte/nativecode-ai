package com.zenithblue.nativecode.terminal

import java.io.File

/** Scans Debian project directories on the Android host filesystem.
 *  No proot/chroot magic needed — paths are already resolved to the host rootfs by
 *  [ProjectPathResolver.resolve]. */
object DirectoryScanner {

    data class FileEntry(val file: File, val relativePath: String, val isDirectory: Boolean)

    /** Lists the immediate children of [dir], sorted (dirs first, then by name).
     *  Hidden files (starting with ".") are skipped. */
    fun list(dir: File): List<FileEntry> {
        return dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?.map { f ->
                FileEntry(f, f.name, f.isDirectory)
            }
            ?: emptyList()
    }

    /** Recursively searches [rootDir] for files/directories matching [query]. */
    fun search(rootDir: File, query: String): List<FileEntry> {
        val matches = mutableListOf<FileEntry>()
        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                val relPath = if (f.absolutePath.startsWith(rootDir.absolutePath)) {
                    f.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/')
                } else {
                    f.name
                }
                if (f.name.contains(query, ignoreCase = true) || relPath.contains(query, ignoreCase = true)) {
                    matches.add(FileEntry(f, relPath, f.isDirectory))
                }
                if (f.isDirectory) {
                    scan(f)
                }
            }
        }
        scan(rootDir)
        matches.sortWith(compareBy({ !it.isDirectory }, { it.file.name }))
        return matches
    }

    /** Resolves [pathStr] relative to [baseDir]. If [pathStr] is already absolute
     *  and does not exist, tries resolving under [baseDir]. */
    fun resolveFile(baseDir: File, pathStr: String): File {
        val f = File(pathStr)
        return if (!f.exists() && !f.isAbsolute) {
            File(baseDir, pathStr)
        } else {
            f
        }
    }
}
