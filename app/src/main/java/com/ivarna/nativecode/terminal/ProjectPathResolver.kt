package com.ivarna.nativecode.terminal

import android.content.Context
import com.ivarna.nativecode.MainActivity
import java.io.File

/** Resolves project paths from their in-Debian representation to Android host filesystem paths.
 *  Works for both proot (Termux-style) and chroot (KernelSU/Magisk) installations. */
object ProjectPathResolver {

    /** Relative path under [Context.getFilesDir] for Debian proot rootfs (SSOT). */
    const val PROOT_ROOTFS_REL = "usr/var/lib/proot-distro/containers/debian/rootfs"

    /** Debian proot rootfs under app private storage. */
    fun prootRootfsDir(ctx: Context): File =
        File(ctx.filesDir, PROOT_ROOTFS_REL)

    /** True if proot rootfs looks present (has etc or bin). */
    fun isProotRootfsPresent(ctx: Context): Boolean =
        prootRootfsDir(ctx).let {
            it.isDirectory && (File(it, "etc").exists() || File(it, "bin").exists())
        }

    /** Returns the Android host filesystem [File] for [projectPath].
     *  [projectPath] is the path inside Debian, e.g. "/home/flux/repos/my-app".
     *  The returned file points to the actual location on the Android host where
     *  that path is stored (chroot rootfs or proot container rootfs). */
    fun resolve(ctx: Context, projectPath: String, method: String = LinuxCommandBuilder.currentMethod): File {
        val rootfs = if (method == "chroot") {
            ChrootCommandBuilder.CHROOT_PATH
        } else {
            prootRootfsDir(ctx).absolutePath
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
            prootRootfsDir(ctx).absolutePath
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
            File(prootRootfsDir(ctx), "home/flux")
        }
    }

    /**
     * App-writable staging dir for image attach (under host-tmp bind parent).
     * Chroot sessions bind [Context.getFilesDir]/usr/tmp → /mnt/host-tmp.
     */
    fun stageAttachDir(ctx: Context): File =
        File(ctx.filesDir, "usr/tmp/nativecode_attach").also { it.mkdirs() }

    /** Guest path for staged attach when no-root chroot bind fallback is used. */
    fun stageAttachGuestPath(fileName: String): String =
        "/mnt/host-tmp/nativecode_attach/$fileName"

    /** True if chroot is installed (marker file exists). */
    fun isChrootInstalled(): Boolean {
        return File("${ChrootCommandBuilder.CHROOT_PATH}/.flux_configured").exists()
    }

    /** Host rootfs directory for Debian chroot (outside app storage). */
    fun chrootRootfsDir(): File = File(ChrootCommandBuilder.CHROOT_PATH)

    /** True if chroot rootfs directory exists on host (may be partial install). */
    fun isChrootRootfsPresent(): Boolean = chrootRootfsDir().exists()

    /** Human-readable label for a linux method. */
    fun methodLabel(method: String = LinuxCommandBuilder.currentMethod): String {
        return if (method == "chroot") "Chroot (Debian Trixie)" else "PRoot (Debian Trixie)"
    }
}
