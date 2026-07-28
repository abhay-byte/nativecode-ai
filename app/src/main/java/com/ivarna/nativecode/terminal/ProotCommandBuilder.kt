package com.ivarna.nativecode.terminal

import android.content.Context

/** Builds shell arguments and environment map for proot Debian sessions. */
object ProotCommandBuilder {

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true
    ): Pair<Array<String>, HashMap<String, String>> {
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val sharedTmpFlag = if (useSharedTmp) "--shared-tmp" else ""
        val prootDistro = TermuxHostPaths.PROOT_DISTRO

        val args = if (shellCmd == "exec zsh" || shellCmd == "/bin/bash --login" || shellCmd.isBlank()) {
            arrayOf(
                shell, "-c",
                "exec python $prootDistro login debian $sharedTmpFlag --user $user"
            )
        } else {
            arrayOf(
                shell, "-c",
                "exec python $prootDistro login debian $sharedTmpFlag --user $user -- zsh -c \"$shellCmd\""
            )
        }

        // Host package env + interactive TERM (guest login inherits package identity)
        val envMap = HostCommandBuilder.envMap(ctx, forceHostSetup = false, includeTerm = true)
        return args to envMap
    }
}
