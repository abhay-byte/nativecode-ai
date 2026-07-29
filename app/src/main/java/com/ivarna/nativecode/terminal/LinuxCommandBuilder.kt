package com.ivarna.nativecode.terminal

import android.content.Context

/** Entry point for building terminal commands.
 *  Delegates to ProotCommandBuilder or ChrootCommandBuilder based on method. */
object LinuxCommandBuilder {

    /** Current isolation method: "proot" (default) or "chroot". */
    var currentMethod = "proot"

    /** Guest user for session type. Rooted shell → root; everything else → flux. */
    fun sessionUserForType(type: String): String =
        if (type == "shell-root") "root" else "flux"

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true,
        method: String = currentMethod
    ): Pair<Array<String>, HashMap<String, String>> {
        return when (method) {
            "chroot" -> ChrootCommandBuilder.build(ctx, shellCmd, user)
            else -> ProotCommandBuilder.build(ctx, shellCmd, user, useSharedTmp)
        }
    }
}
