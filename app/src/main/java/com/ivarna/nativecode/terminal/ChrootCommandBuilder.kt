package com.ivarna.nativecode.terminal

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/** Builds shell arguments and environment map for chroot Debian sessions. */
object ChrootCommandBuilder {

    const val CHROOT_PATH = "/data/local/tmp/chrootDebian13"

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux"
    ): Pair<Array<String>, HashMap<String, String>> {
        val runScript = "/data/local/tmp/run_debian13_root.sh"
        val mountCmds = listOf(
            "busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true",
            "busybox mount --bind /dev $CHROOT_PATH/dev >/dev/null 2>&1 || true",
            "busybox mount --bind /sys $CHROOT_PATH/sys >/dev/null 2>&1 || true",
            "busybox mount -t proc proc $CHROOT_PATH/proc >/dev/null 2>&1 || true",
            "busybox mount -t devpts devpts $CHROOT_PATH/dev/pts >/dev/null 2>&1 || true",
            "mkdir -p $CHROOT_PATH/dev/shm && busybox mount -t tmpfs -o size=512M tmpfs $CHROOT_PATH/dev/shm >/dev/null 2>&1 || true",
            // Sticky disk /tmp for apt; never bind app usr/tmp onto /tmp
            "mkdir -p $CHROOT_PATH/tmp $CHROOT_PATH/mnt/host-tmp",
            "busybox umount $CHROOT_PATH/tmp >/dev/null 2>&1 || true",
            "busybox umount $CHROOT_PATH/mnt/termux-tmp >/dev/null 2>&1 || true",
            "chmod 1777 $CHROOT_PATH/tmp >/dev/null 2>&1 || true",
            "busybox mount --bind ${ctx.filesDir}/usr/tmp $CHROOT_PATH/mnt/host-tmp >/dev/null 2>&1 || true",
            "cp -f ${ctx.filesDir}/usr/tmp/launch_tool.sh $CHROOT_PATH/tmp/launch_tool.sh >/dev/null 2>&1 || true",
            "chmod 755 $CHROOT_PATH/tmp/launch_tool.sh >/dev/null 2>&1 || true",
            "chmod 755 $CHROOT_PATH/home/flux >/dev/null 2>&1 || true"
        ).joinToString("; ")

        val isInteractive = shellCmd == "exec zsh" || shellCmd == "/bin/bash --login" || shellCmd.isBlank()

        val cmd = if (isInteractive) {
            "/system/bin/su -c \"$mountCmds; exec busybox chroot $CHROOT_PATH /bin/su - $user\""
        } else if (user == "root" && File(runScript).exists()) {
            "/system/bin/su -c \"$runScript $shellCmd\""
        } else {
            val escapedCmd = shellCmd.replace("\"", "\\\"")
            "/system/bin/su -c \"$mountCmds; exec busybox chroot $CHROOT_PATH /bin/su - $user -c \\\"$escapedCmd\\\"\""
        }

        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "/system/bin:/system/xbin:/sbin:" + (envMap["PATH"] ?: "")
        envMap["TERM"] = "xterm-256color"
        envMap["HOME"] = "/home/flux"
        envMap["LANG"] = "en_US.UTF-8"
        envMap["LC_ALL"] = "en_US.UTF-8"
        envMap["XDG_RUNTIME_DIR"] = "/tmp"
        envMap["TMPDIR"] = "/tmp"

        return arrayOf("/system/bin/sh", "-c", cmd) to envMap
    }

    /** Creates a SIGWINCH forwarding wrapper so inner zsh inside chroot receives resize signals.
     *  Termux TerminalSession sends SIGWINCH to mShellPid (direct child). In chroot mode that child
     *  is /system/bin/sh which ignores SIGWINCH. This wrapper catches SIGWINCH and forwards it to
     *  the entire process group (kill -WINCH 0), reaching the inner zsh. */
    fun ensureTermWrapper(): Boolean {
        val scriptPath = "/data/local/tmp/chroot_term_wrapper.sh"
        if (File(scriptPath).exists() && File(scriptPath).length() > 0) return true
        val script = """#!/system/bin/sh
# Run command in foreground (same process group) so SIGWINCH reaches all children.
trap 'kill -WINCH 0 2>/dev/null' WINCH
"${'$'}@"
""".trimIndent()
        return try {
            val f = File(scriptPath)
            f.writeText(script)
            val chmodProc = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c",
                "chmod 755 $scriptPath"))
            chmodProc.waitFor(5, TimeUnit.SECONDS)
            f.exists() && f.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Creates AI tool launcher. Written to app usr/tmp; session mount copies it into chroot /tmp. */
    fun ensureLauncherScript(ctx: Context): Boolean {
        val scriptPath = "${ctx.filesDir}/usr/tmp/launch_tool.sh"
        if (File(scriptPath).exists() && File(scriptPath).length() > 0) return true
        val script = """#!/bin/zsh
export PATH=/home/flux/.local/bin:/opt/nodejs/bin:/usr/local/bin:/usr/bin:/bin:/sbin:/usr/local/sbin
export HOME=/home/flux
export TERM=xterm-256color
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
export XDG_RUNTIME_DIR=/tmp
export TMPDIR=/tmp
export ZSH=${'$'}HOME/.oh-my-zsh
ZSH_THEME=agnosterzak
DISABLE_UPDATE_PROMPT=true
DISABLE_AUTO_UPDATE=true
ZSH_DISABLE_COMPFIX=true
plugins=(git zsh-autosuggestions zsh-syntax-highlighting)
source ${'$'}ZSH/oh-my-zsh.sh
exec "${'$'}@"
""".trimIndent()
        return try {
            val f = File(scriptPath)
            f.writeText(script)
            val chmodProc = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c",
                "chmod 755 $scriptPath"))
            chmodProc.waitFor(5, TimeUnit.SECONDS)
            f.exists() && f.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
