package com.zenithblue.nativecode.terminal

import android.content.Context

/**
 * Builds argv + environment for **host** (native Termux prefix) scripts/commands.
 * Package is always [TermuxHostPaths.PACKAGE] — not com.termux.
 */
object HostCommandBuilder {

    /**
     * Full host environment map (starts from [System.getenv] or empty).
     *
     * @param forceHostSetup when true, sets FLUX_SETUP_FORCE=1 for setup_termux.sh
     * @param includeTerm when true, sets TERM=xterm-256color (interactive terminal)
     */
    fun envMap(
        ctx: Context,
        forceHostSetup: Boolean = false,
        includeTerm: Boolean = true,
        base: Map<String, String>? = System.getenv()
    ): HashMap<String, String> {
        val nld = ctx.applicationInfo.nativeLibraryDir
        val env = HashMap<String, String>()
        if (base != null) env.putAll(base)

        env["PATH"] = "$nld:${TermuxHostPaths.BIN}:/system/bin"
        env["PD_PROOT_BIN"] = TermuxHostPaths.libProot(ctx).absolutePath
        env["PROOT_LOADER"] = TermuxHostPaths.libLoader(ctx).absolutePath
        env["LD_LIBRARY_PATH"] =
            "${TermuxHostPaths.LIB}:${TermuxHostPaths.PREFIX}/opt/virglrenderer-android/lib"
        env["PREFIX"] = TermuxHostPaths.PREFIX
        env["HOME"] = TermuxHostPaths.HOME
        env["TMPDIR"] = TermuxHostPaths.TMPDIR
        env["PROOT_TMP_DIR"] = TermuxHostPaths.TMPDIR
        env["TERMUX_APP__PACKAGE_NAME"] = TermuxHostPaths.PACKAGE
        env["TERMUX_VERSION"] = TermuxHostPaths.TERMUX_VERSION
        env["TERMUX_X11_APK_PATH"] = ctx.applicationInfo.sourceDir
        env["TERMUX_X11_OVERRIDE_PACKAGE"] = TermuxHostPaths.PACKAGE
        env["TERMUX__PREFIX"] = TermuxHostPaths.PREFIX
        env["TERMUX__HOME"] = TermuxHostPaths.HOME
        env["SSL_CERT_FILE"] = TermuxHostPaths.SSL_CERT
        env["CURL_CA_BUNDLE"] = TermuxHostPaths.SSL_CERT

        val termuxExec = TermuxHostPaths.termuxExec(ctx)
        if (termuxExec.exists()) {
            env["LD_PRELOAD"] = termuxExec.absolutePath
        } else {
            env.remove("LD_PRELOAD")
        }

        if (includeTerm) {
            env["TERM"] = env["TERM"] ?: "xterm-256color"
        }

        if (forceHostSetup) {
            env["FLUX_SETUP_FORCE"] = "1"
        } else {
            env.remove("FLUX_SETUP_FORCE")
        }

        return env
    }

    /** Apply host env onto a [ProcessBuilder] (mutates process environment). */
    fun applyTo(
        ctx: Context,
        pb: ProcessBuilder,
        forceHostSetup: Boolean = false
    ) {
        val env = pb.environment()
        // base=null: only our keys; ProcessBuilder already has system env under it
        val built = envMap(ctx, forceHostSetup = forceHostSetup, includeTerm = false, base = null)
        built.forEach { (k, v) -> env[k] = v }
        if (!built.containsKey("LD_PRELOAD")) env.remove("LD_PRELOAD")
        if (!forceHostSetup) env.remove("FLUX_SETUP_FORCE")
    }

    /**
     * Run a host script with libbash.so.
     * @return argv + env for TerminalSession / ProcessBuilder
     */
    fun build(
        ctx: Context,
        scriptPath: String,
        forceHostSetup: Boolean = false
    ): Pair<Array<String>, HashMap<String, String>> {
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val args = arrayOf(shell, scriptPath)
        val env = envMap(ctx, forceHostSetup = forceHostSetup, includeTerm = true)
        return args to env
    }

    /** True when [scriptName] should force re-validation of host setup. */
    fun shouldForceHostSetup(scriptName: String): Boolean =
        scriptName == "setup_termux.sh" || scriptName.endsWith("/setup_termux.sh")

    fun clearSetupMarker(ctx: Context) {
        TermuxHostPaths.clearSetupTermuxMarker(ctx)
    }
}
