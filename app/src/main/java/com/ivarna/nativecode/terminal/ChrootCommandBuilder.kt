package com.ivarna.nativecode.terminal

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/** Builds shell arguments and environment map for chroot Debian sessions. */
object ChrootCommandBuilder {

    const val CHROOT_PATH = "/data/local/tmp/chrootDebian13"

    /** Session executable — must be a system binary (SELinux blocks exec of app-data scripts). */
    const val SESSION_EXEC = "/system/bin/sh"

    private const val LAUNCH_TOOL_VERSION = "nativecode-launch-tool v2"
    private const val TERM_WRAPPER_VERSION = "nativecode-chroot-term-wrapper v3"

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux"
    ): Pair<Array<String>, HashMap<String, String>> {
        // Refresh host launch_tool before bind/copy into guest /tmp
        ensureLauncherScript(ctx)

        val runScript = "/data/local/tmp/run_debian13_root.sh"
        val mountCmds = listOf(
            // busybox often cannot resolve /data in /proc/mounts on KSU; system mount works.
            // Without suid, guest sudo fails: "effective uid is not 0 ... nosuid".
            "/system/bin/mount -o remount,dev,suid /data >/dev/null 2>&1 || busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true",
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

        // Inline WINCH trap on outer sh (mShellPid). Do NOT exec a separate wrapper script —
        // app-data paths are not executable under SELinux; /data/local/tmp is not app-writable.
        // Keep this shell as parent (no leading exec) so the trap stays installed.
        val winchCmd =
            "trap 'kill -WINCH -\$\$ 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH; $cmd"

        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "/system/bin:/system/xbin:/sbin:" + (envMap["PATH"] ?: "")
        envMap["TERM"] = "xterm-256color"
        envMap["HOME"] = if (user == "root") "/root" else "/home/flux"
        envMap["LANG"] = "en_US.UTF-8"
        envMap["LC_ALL"] = "en_US.UTF-8"
        envMap["XDG_RUNTIME_DIR"] = "/tmp"
        envMap["TMPDIR"] = "/tmp"

        return arrayOf(SESSION_EXEC, "-c", winchCmd) to envMap
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
     * AI tool launcher written to app usr/tmp; session mount copies into chroot /tmp.
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
                "mkdir -p $dir && cd $dir && exec zsh"
            }
        }
        val dir = workDir?.takeIf { it.isNotBlank() } ?: "/home/flux"
        val tool = toolBinaryName(type)
        // Write host launch_tool; chroot copies into guest /tmp; proot --shared-tmp maps PREFIX/tmp → /tmp
        ensureLauncherScript(ctx)
        return if (workDir.isNullOrBlank()) {
            "/tmp/launch_tool.sh $tool"
        } else {
            "mkdir -p $dir && cd $dir && /tmp/launch_tool.sh $tool"
        }
    }
}
