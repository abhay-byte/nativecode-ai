package com.ivarna.nativecode.terminal

import android.content.Context
import java.io.File

/** Builds shell arguments and environment map for proot Debian sessions. */
object ProotCommandBuilder {

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true
    ): Pair<Array<String>, HashMap<String, String>> {
        val nld = ctx.applicationInfo.nativeLibraryDir
        val shell = File(nld, "libbash.so").absolutePath
        val sharedTmpFlag = if (useSharedTmp) "--shared-tmp" else ""

        val args = if (shellCmd == "exec zsh" || shellCmd == "/bin/bash --login" || shellCmd.isBlank()) {
            arrayOf(
                shell, "-c",
                "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian $sharedTmpFlag --user $user"
            )
        } else {
            arrayOf(
                shell, "-c",
                "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian $sharedTmpFlag --user $user -- zsh -c \"$shellCmd\""
            )
        }

        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        envMap["PD_PROOT_BIN"] = File(nld, "libproot.so").absolutePath
        envMap["PROOT_LOADER"] = File(nld, "libloader.so").absolutePath
        envMap["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        envMap["TERM"] = "xterm-256color"
        envMap["PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib"
        setLdPreloadEnv(ctx, envMap)
        envMap["TERMUX_APP__PACKAGE_NAME"] = "com.ivarna.nativecode"
        envMap["TERMUX__PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["TERMUX__HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        envMap["SSL_CERT_FILE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        envMap["CURL_CA_BUNDLE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"

        return args to envMap
    }

    private fun setLdPreloadEnv(ctx: Context, envMap: MutableMap<String, String>) {
        val termuxExec = File(ctx.filesDir, "usr/lib/libtermux-exec.so")
        if (termuxExec.exists()) {
            envMap["LD_PRELOAD"] = termuxExec.absolutePath
        } else {
            envMap.remove("LD_PRELOAD")
        }
    }
}
