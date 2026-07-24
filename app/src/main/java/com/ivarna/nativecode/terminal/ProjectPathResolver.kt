package com.ivarna.nativecode.terminal

import android.content.Context
import com.ivarna.nativecode.MainActivity
import java.io.File

/** Resolves project paths from their in-Debian representation to Android host filesystem paths.
 *  Works for both proot (Termux-style) and chroot (KernelSU/Magisk) installations. */
object ProjectPathResolver {

    /** Returns the Android host filesystem [File] for [projectPath].
     *  [projectPath] is the path inside Debian, e.g. "/home/flux/repos/my-app".
     *  The returned file points to the actual location on the Android host where
     *  that path is stored (chroot rootfs or proot container rootfs). */
    fun resolve(ctx: Context, projectPath: String, method: String = LinuxCommandBuilder.currentMethod): File {
        val rootfs = if (method == "chroot") {
            ChrootCommandBuilder.CHROOT_PATH
        } else {
            "${ctx.filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs"
        }
        return if (projectPath.startsWith("/")) {
            File(rootfs, projectPath.removePrefix("/"))
        } else {
            File(rootfs, "home/flux/$projectPath")
        }
    }

    /** Inverse: given an Android host [file], returns the Debian-internal path.
     *  Returns null if the file is not inside the current Debian rootfs. */
    fun toDebianPath(ctx: Context, file: File, method: String = LinuxCommandBuilder.currentMethod): String? {
        val rootfs = if (method == "chroot") {
            ChrootCommandBuilder.CHROOT_PATH
        } else {
            "${ctx.filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs"
        }
        val abs = file.absolutePath
        return if (abs.startsWith(rootfs)) {
            abs.removePrefix(rootfs)
        } else {
            null
        }
    }

    /** Returns the guest home directory for the currently active linux method. */
    fun guestHomeDir(ctx: Context, method: String = LinuxCommandBuilder.currentMethod): File {
        return if (method == "chroot") {
            File(ChrootCommandBuilder.CHROOT_PATH, "home/flux")
        } else {
            File(ctx.filesDir, "usr/var/lib/proot-distro/containers/debian/rootfs/home/flux")
        }
    }

    /** True if chroot is installed (marker file exists). */
    fun isChrootInstalled(): Boolean {
        return File("${ChrootCommandBuilder.CHROOT_PATH}/.flux_configured").exists()
    }

    /** Human-readable label for a linux method. */
    fun methodLabel(method: String = LinuxCommandBuilder.currentMethod): String {
        return if (method == "chroot") "Chroot (Debian Trixie)" else "PRoot (Debian Trixie)"
    }
}
