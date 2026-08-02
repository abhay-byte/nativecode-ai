package com.zenithblue.nativecode.marketplace

import android.content.Context
import android.util.Log
import com.zenithblue.nativecode.RootShell
import com.zenithblue.nativecode.terminal.ChrootCommandBuilder
import com.zenithblue.nativecode.terminal.HostCommandBuilder
import com.zenithblue.nativecode.terminal.LinuxCommandBuilder
import com.zenithblue.nativecode.terminal.ShellCommandRunner
import com.zenithblue.nativecode.terminal.TermuxHostPaths
import java.io.File

/**
 * Resolve marketplace deps, stage scripts into guest `/tmp/nc-mp`, run install/uninstall.
 *
 * Staging SSOT (must match guest view of `/tmp`):
 * - **proot** with `--shared-tmp` → guest `/tmp` = [TermuxHostPaths.TMPDIR]
 * - **chroot** → guest `/tmp` = [ChrootCommandBuilder.CHROOT_PATH]/tmp` (disk tmp, not host bind)
 *
 * Uninstall = marketplace-tracked only (caller enforces).
 */
object PackageInstallRunner {
    private const val TAG = "PackageInstallRunner"
    private const val GUEST_STAGE = "/tmp/nc-mp"

    /** Prepared guest run for Terminal session (repairs-style script runner). */
    data class PreparedRun(
        val title: String,
        val method: String,
        val uninstall: Boolean,
        val packageIds: List<String>,
        val packages: List<MpPackage>,
        val guestCmd: String,
        val args: Array<String>,
        val envMap: HashMap<String, String>
    )

    /**
     * Topological install order for [id] including deps. Throws on cycle/missing.
     */
    fun resolveOrder(catalog: MpCatalog, id: String): List<MpPackage> {
        val result = mutableListOf<MpPackage>()
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()

        fun visit(pid: String) {
            if (pid in done) return
            if (pid in visiting) throw IllegalStateException("Dependency cycle at $pid")
            visiting.add(pid)
            val pkg = catalog.packageById(pid)
                ?: throw IllegalStateException("Unknown package: $pid")
            pkg.deps.forEach { visit(it) }
            visiting.remove(pid)
            done.add(pid)
            result.add(pkg)
        }
        visit(id)
        return result
    }

    /**
     * Download + stage scripts, build guest command + host exec args for TerminalSession.
     */
    fun prepareInstall(
        ctx: Context,
        catalog: MpCatalog,
        id: String,
        method: String = LinuxCommandBuilder.currentMethod
    ): Result<PreparedRun> {
        return try {
            val order = resolveOrder(catalog, id)
            val toInstall = order.filter { !InstallRegistry.isInstalled(ctx, method, it.id) }
            if (toInstall.isEmpty()) {
                return Result.failure(IllegalStateException("Already installed: $id"))
            }
            for (pkg in toInstall) {
                if (!pkg.supportsEnv(method)) {
                    return Result.failure(
                        IllegalStateException("${pkg.id} does not support $method")
                    )
                }
                val scripts = MarketplaceClient.ensurePackageScripts(ctx, pkg)
                if (scripts.isFailure) {
                    return Result.failure(
                        scripts.exceptionOrNull()
                            ?: IllegalStateException("download failed: ${pkg.id}")
                    )
                }
                val staged = stageIntoGuest(ctx, scripts.getOrThrow(), pkg.id, method)
                if (staged.isFailure) {
                    return Result.failure(
                        staged.exceptionOrNull() ?: IllegalStateException("stage failed")
                    )
                }
            }
            val plan = toInstall.joinToString(" → ") { it.id }
            val guestCmd = buildGuestBatch(toInstall.map { it.id }, uninstall = false)
            val (args, env) = buildRootExec(ctx, guestCmd, method)
            Log.i(TAG, "prepareInstall plan=$plan method=$method")
            Result.success(
                PreparedRun(
                    title = "Install $id ($method)",
                    method = method,
                    uninstall = false,
                    packageIds = toInstall.map { it.id },
                    packages = toInstall,
                    guestCmd = guestCmd,
                    args = args,
                    envMap = env
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "prepareInstall", e)
            Result.failure(e)
        }
    }

    fun prepareUninstall(
        ctx: Context,
        catalog: MpCatalog,
        id: String,
        method: String = LinuxCommandBuilder.currentMethod
    ): Result<PreparedRun> {
        return try {
            if (!InstallRegistry.isInstalled(ctx, method, id)) {
                return Result.failure(IllegalStateException("Not marketplace-installed: $id"))
            }
            val pkg = catalog.packageById(id)
                ?: return Result.failure(IllegalStateException("Package missing from catalog: $id"))
            val scripts = MarketplaceClient.ensurePackageScripts(ctx, pkg)
            if (scripts.isFailure) {
                return Result.failure(
                    scripts.exceptionOrNull()
                        ?: IllegalStateException("download failed: $id")
                )
            }
            val staged = stageIntoGuest(ctx, scripts.getOrThrow(), pkg.id, method)
            if (staged.isFailure) {
                return Result.failure(
                    staged.exceptionOrNull() ?: IllegalStateException("stage failed")
                )
            }
            val guestCmd = buildGuestBatch(listOf(pkg.id), uninstall = true)
            val (args, env) = buildRootExec(ctx, guestCmd, method)
            Result.success(
                PreparedRun(
                    title = "Uninstall $id ($method)",
                    method = method,
                    uninstall = true,
                    packageIds = listOf(pkg.id),
                    packages = listOf(pkg),
                    guestCmd = guestCmd,
                    args = args,
                    envMap = env
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "prepareUninstall", e)
            Result.failure(e)
        }
    }

    /** After terminal exit 0: write/remove registry entries. */
    fun applyRegistryAfterRun(ctx: Context, run: PreparedRun, success: Boolean) {
        if (!success) return
        if (run.uninstall) {
            run.packageIds.forEach { InstallRegistry.remove(ctx, run.method, it) }
            return
        }
        val now = InstallRegistry.nowIso()
        for (pkg in run.packages) {
            InstallRegistry.put(
                ctx,
                RegistryEntry(
                    id = pkg.id,
                    env = run.method,
                    kind = pkg.kind,
                    version = pkg.version,
                    installedAt = now,
                    sizeBytes = 0L,
                    aptProvides = pkg.aptProvides,
                    paths = emptyList(),
                    launchCommand = pkg.launch?.command,
                    launchType = pkg.launch?.type,
                    state = "installed"
                )
            )
        }
    }

    /**
     * Blocking install via [ShellCommandRunner] (no UI). Prefer terminal path for UX.
     */
    fun install(
        ctx: Context,
        catalog: MpCatalog,
        id: String,
        method: String = LinuxCommandBuilder.currentMethod,
        onEvent: (InstallEvent) -> Unit
    ) {
        val prepared = prepareInstall(ctx, catalog, id, method)
        if (prepared.isFailure) {
            onEvent(InstallEvent.Log("ERROR: ${prepared.exceptionOrNull()?.message}"))
            onEvent(InstallEvent.Finished(false, 1, 0, emptyList()))
            return
        }
        val run = prepared.getOrThrow()
        onEvent(InstallEvent.Log("Install plan: ${run.packageIds.joinToString(" → ")}"))
        onEvent(InstallEvent.Log("Stage: $GUEST_STAGE (method=${run.method})"))
        execPrepared(ctx, run, onEvent)
    }

    fun uninstall(
        ctx: Context,
        catalog: MpCatalog,
        id: String,
        method: String = LinuxCommandBuilder.currentMethod,
        onEvent: (InstallEvent) -> Unit
    ) {
        if (!InstallRegistry.isInstalled(ctx, method, id)) {
            onEvent(InstallEvent.Log("Not marketplace-installed: $id"))
            onEvent(InstallEvent.Finished(false, 1, 0, emptyList()))
            return
        }
        val pkg = catalog.packageById(id)
        if (pkg == null) {
            onEvent(InstallEvent.Log("WARN: package missing from catalog; removing registry only"))
            InstallRegistry.remove(ctx, method, id)
            onEvent(InstallEvent.Finished(true, 0, 0, emptyList()))
            return
        }
        val prepared = prepareUninstall(ctx, catalog, id, method)
        if (prepared.isFailure) {
            onEvent(InstallEvent.Log("ERROR: ${prepared.exceptionOrNull()?.message}"))
            onEvent(InstallEvent.Finished(false, 1, 0, emptyList()))
            return
        }
        execPrepared(ctx, prepared.getOrThrow(), onEvent)
    }

    /**
     * Launch X11 app as flux. Caller ensures Graphical Desktop is up.
     */
    fun launchApp(
        ctx: Context,
        entry: RegistryEntry,
        method: String = LinuxCommandBuilder.currentMethod,
        onEvent: (InstallEvent) -> Unit
    ) {
        val cmd = entry.launchCommand
        if (cmd.isNullOrBlank() || !entry.isApp) {
            onEvent(InstallEvent.Log("Not a launchable app"))
            onEvent(InstallEvent.Finished(false, 1, 0, emptyList()))
            return
        }
        val shell =
            "export DISPLAY=\${DISPLAY:-:0}; export PULSE_SERVER=\${PULSE_SERVER:-127.0.0.1}; " +
                "cd /home/flux 2>/dev/null || true; " +
                "nohup $cmd >/tmp/nc-mp-launch.log 2>&1 & echo NC_MP_STATUS=launched pid=\$!"
        onEvent(InstallEvent.Log("Launch: $cmd ($method)"))
        try {
            val (args, env) = LinuxCommandBuilder.build(ctx, shell, user = "flux", method = method)
            ShellCommandRunner.runStreamed(
                ctx, args, env,
                onLine = { onEvent(InstallEvent.Log(it)) },
                onDone = { code ->
                    onEvent(InstallEvent.Finished(code == 0, code, 0, emptyList()))
                }
            )
        } catch (e: Exception) {
            onEvent(InstallEvent.Log("ERROR: ${e.message}"))
            onEvent(InstallEvent.Finished(false, 1, 0, emptyList()))
        }
    }

    private fun execPrepared(
        ctx: Context,
        run: PreparedRun,
        onEvent: (InstallEvent) -> Unit
    ) {
        var size = 0L
        val paths = mutableListOf<String>()
        var exit = 1
        val latch = java.util.concurrent.CountDownLatch(1)
        try {
            ShellCommandRunner.runStreamed(
                ctx, run.args, run.envMap,
                onLine = { line ->
                    onEvent(InstallEvent.Log(line))
                    when {
                        line.startsWith("NC_MP_STATUS=") ->
                            onEvent(InstallEvent.Progress(line.removePrefix("NC_MP_STATUS=")))
                        line.startsWith("NC_MP_SIZE_BYTES=") -> {
                            size = line.removePrefix("NC_MP_SIZE_BYTES=").trim().toLongOrNull() ?: 0L
                            onEvent(InstallEvent.Size(size))
                        }
                        line.startsWith("NC_MP_PATHS=") -> {
                            val p = line.removePrefix("NC_MP_PATHS=").split(':')
                                .map { it.trim() }.filter { it.isNotEmpty() }
                            paths.clear()
                            paths.addAll(p)
                            onEvent(InstallEvent.Paths(p))
                        }
                    }
                },
                onDone = { code ->
                    exit = code
                    latch.countDown()
                }
            )
            latch.await()
            val ok = exit == 0
            applyRegistryAfterRun(ctx, run, ok)
            if (!ok) onEvent(InstallEvent.Log("Script exit $exit"))
            onEvent(InstallEvent.Finished(ok, exit, size, paths.toList()))
        } catch (e: Exception) {
            Log.e(TAG, "execPrepared", e)
            onEvent(InstallEvent.Log("ERROR: ${e.message}"))
            onEvent(InstallEvent.Finished(false, 1, 0, emptyList()))
        }
    }

    private fun buildGuestBatch(ids: List<String>, uninstall: Boolean): String {
        val script = if (uninstall) "uninstall.sh" else "install.sh"
        val lines = mutableListOf<String>()
        lines += "set -e"
        lines += "export DEBIAN_FRONTEND=noninteractive"
        lines += "echo 'NC_MP_STATUS=plan ids=${ids.joinToString(",")}'"
        lines += "ls -la $GUEST_STAGE 2>/dev/null || true"
        for (id in ids) {
            lines += "echo '── ${if (uninstall) "uninstall" else "install"} $id ──'"
            lines += "chmod +x $GUEST_STAGE/$id/*.sh $GUEST_STAGE/lib/*.sh 2>/dev/null || true"
            lines += "if [ ! -f $GUEST_STAGE/$id/$script ]; then " +
                "echo \"ERROR: missing $GUEST_STAGE/$id/$script\"; " +
                "ls -la $GUEST_STAGE/$id 2>/dev/null || true; exit 127; fi"
            lines += "/bin/bash $GUEST_STAGE/$id/$script"
        }
        lines += "echo 'NC_MP_STATUS=all_done'"
        return lines.joinToString("; ")
    }

    /**
     * Copy package + lib scripts so guest sees them at `/tmp/nc-mp/...`.
     */
    private fun stageIntoGuest(
        ctx: Context,
        hostPkgDir: File,
        id: String,
        method: String
    ): Result<Unit> {
        val libHost = MarketplacePaths.libCacheDir(ctx)
        if (!hostPkgDir.isDirectory) {
            return Result.failure(IllegalStateException("Host package cache missing: $hostPkgDir"))
        }
        if (method == "chroot") {
            // Guest /tmp = CHROOT/tmp (disk). Stage via RootShell (KSU/Magisk su SSOT).
            val destPkg = "${ChrootCommandBuilder.CHROOT_PATH}/tmp/nc-mp/$id"
            val destLib = "${ChrootCommandBuilder.CHROOT_PATH}/tmp/nc-mp/lib"
            val srcPkg = hostPkgDir.absolutePath
            val srcLib = libHost.absolutePath
            val script = (
                "mkdir -p '$destPkg' '$destLib' && " +
                    "cp -a '$srcPkg'/. '$destPkg'/ && " +
                    "if [ -d '$srcLib' ]; then cp -a '$srcLib'/. '$destLib'/; fi && " +
                    "chmod -R a+rX '$destPkg' '$destLib' 2>/dev/null || true; " +
                    "chmod +x '$destPkg'/*.sh '$destLib'/*.sh 2>/dev/null || true; " +
                    "test -f '$destPkg/install.sh' && test -f '$destLib/nc_mp_common.sh'"
                )
            val code = runSu(script)
            if (code != 0) {
                return Result.failure(
                    IllegalStateException(
                        "chroot stage failed (su exit $code). Need root for $destPkg"
                    )
                )
            }
            Log.i(TAG, "staged chroot $id → $destPkg")
            return Result.success(Unit)
        }

        // proot --shared-tmp: guest /tmp ≡ TermuxHostPaths.TMPDIR
        val destRoot = File(TermuxHostPaths.TMPDIR, "nc-mp")
        val destPkg = File(destRoot, id)
        val destLib = File(destRoot, "lib")
        return try {
            destPkg.mkdirs()
            destLib.mkdirs()
            hostPkgDir.listFiles()?.forEach { f ->
                f.copyTo(File(destPkg, f.name), overwrite = true)
            }
            libHost.listFiles()?.forEach { f ->
                f.copyTo(File(destLib, f.name), overwrite = true)
            }
            destPkg.listFiles()?.forEach { it.setExecutable(true) }
            destLib.listFiles()?.forEach { it.setExecutable(true) }
            val install = File(destPkg, "install.sh")
            val common = File(destLib, "nc_mp_common.sh")
            if (!install.isFile) {
                return Result.failure(
                    IllegalStateException("proot stage missing install.sh at ${install.absolutePath}")
                )
            }
            if (!common.isFile) {
                return Result.failure(
                    IllegalStateException("proot stage missing lib at ${common.absolutePath}")
                )
            }
            Log.i(TAG, "staged proot $id → ${destPkg.absolutePath} (guest $GUEST_STAGE/$id)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "proot stage failed", e)
            Result.failure(e)
        }
    }

    /** Host-as-root only (stage into chroot tree). Prefer RootShell su discovery over hardcode. */
    private fun runSu(script: String): Int {
        return try {
            if (!RootShell.isRootAvailable()) {
                Log.w(TAG, "runSu: no root")
                return -1
            }
            val code = RootShell.executeSync(script)
            if (code != 0) Log.w(TAG, "su stage exit=$code")
            code
        } catch (e: Exception) {
            Log.w(TAG, "runSu failed", e)
            -1
        }
    }

    fun buildRootExec(
        ctx: Context,
        guestCmd: String,
        method: String
    ): Pair<Array<String>, HashMap<String, String>> {
        if (method == "chroot") {
            // root inside guest — same path as repairs chroot_guest
            return ChrootCommandBuilder.build(ctx, guestCmd, user = "root")
        }
        // proot: bash as root (not zsh) so non-interactive scripts are reliable
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val prootDistro = TermuxHostPaths.PROOT_DISTRO
        val escaped = guestCmd.replace("'", "'\\''")
        val args = arrayOf(
            shell, "-c",
            "exec python $prootDistro login debian --shared-tmp --user root -- /bin/bash -lc '$escaped'"
        )
        val envMap = HostCommandBuilder.envMap(ctx, forceHostSetup = false, includeTerm = true)
        return args to envMap
    }
}
