package com.zenithblue.nativecode.cliauth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.zenithblue.nativecode.terminal.LinuxCommandBuilder
import com.zenithblue.nativecode.terminal.ProjectPathResolver
import com.zenithblue.nativecode.terminal.ShellCommandRunner
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * SSOT for AI CLI browser / device-code / API-key auth in guest (flux).
 * Per-method state (proot ≠ chroot). Mirrors GitHubCliService patterns.
 */
object CliAuthService {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private val activeSession = AtomicBoolean(false)

    /** toolId|method → status */
    private val statusCache = mutableMapOf<String, CliToolStatus>()
    /** method → last successful probeAll wall time */
    private val cacheAtMs = mutableMapOf<String, Long>()

    /** Skip guest re-probe if cache younger than this (unless force). */
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private val URL_PATTERN = Pattern.compile("(https?://[^\\s\"'<>\\]\\)]+)")
    private val OTP_PATTERN = Pattern.compile(
        "\\b([A-Z0-9]{4,5}-[A-Z0-9]{4,6})\\b"
    )
    private val CLAUDE_TOKEN = Pattern.compile(
        "\\b(sk-ant-oat[A-Za-z0-9_-]+|claude_oauth_[A-Za-z0-9_-]+)\\b"
    )

    fun isSessionActive(): Boolean = activeSession.get()

    fun cachedStatus(toolId: String, method: String): CliToolStatus? =
        statusCache["$toolId|$method"]

    /** Full tool list from memory if every catalog entry is cached for [method]. */
    fun cachedAll(method: String): List<CliToolStatus>? {
        val defs = CliToolCatalog.forMethod(method)
        if (defs.isEmpty()) return emptyList()
        val list = defs.map { def ->
            statusCache["${def.id}|$method"] ?: return null
        }
        return list
    }

    fun cacheAgeMs(method: String): Long {
        val t = cacheAtMs[method] ?: return -1L
        return (System.currentTimeMillis() - t).coerceAtLeast(0L)
    }

    fun isCacheFresh(method: String): Boolean {
        val age = cacheAgeMs(method)
        return age in 0 until CACHE_TTL_MS && cachedAll(method) != null
    }

    fun invalidateCache(method: String? = null) {
        if (method == null) {
            statusCache.clear()
            cacheAtMs.clear()
        } else {
            val keys = statusCache.keys.filter { it.endsWith("|$method") }
            keys.forEach { statusCache.remove(it) }
            cacheAtMs.remove(method)
        }
    }

    /**
     * Probe all tools. Uses in-memory cache when fresh unless [force].
     * [onResult] always on main. Cache hit is synchronous on caller thread
     * if already on main from UI — still posts for consistency.
     */
    fun probeAll(
        ctx: Context,
        method: String,
        force: Boolean = false,
        onResult: (List<CliToolStatus>) -> Unit
    ) {
        if (!force && isCacheFresh(method)) {
            val hit = cachedAll(method)
            if (hit != null) {
                mainHandler.post { onResult(hit) }
                return
            }
        }
        executor.execute {
            val list = CliToolCatalog.forMethod(method).map { def ->
                probeOneSync(ctx, method, def)
            }
            cacheAtMs[method] = System.currentTimeMillis()
            mainHandler.post { onResult(list) }
        }
    }

    fun probeOne(
        ctx: Context,
        method: String,
        toolId: String,
        force: Boolean = false,
        onResult: (CliToolStatus) -> Unit
    ) {
        if (!force) {
            val hit = statusCache["$toolId|$method"]
            if (hit != null && isCacheFresh(method)) {
                mainHandler.post { onResult(hit) }
                return
            }
        }
        executor.execute {
            val def = CliToolCatalog.byId(toolId)
            val st = if (def == null) {
                CliToolStatus(toolId, method, false, false, error = "unknown tool")
            } else {
                probeOneSync(ctx, method, def)
            }
            mainHandler.post { onResult(st) }
        }
    }

    /**
     * Start login for [toolId]. Returns null if another session is active
     * or tool needs UI-only path handled before call.
     */
    fun startLogin(
        ctx: Context,
        method: String,
        toolId: String,
        listener: CliAuthListener
    ): CliAuthSession? {
        val def = CliToolCatalog.byId(toolId)
        if (def == null) {
            mainHandler.post { listener.onFailed("Unknown tool: $toolId") }
            return null
        }
        if (def.strategy == CliLoginStrategy.API_KEY_FORM) {
            mainHandler.post {
                listener.onFailed("Use API key form for ${def.displayName}")
            }
            return null
        }
        if (def.strategy == CliLoginStrategy.TERMINAL_GUIDED) {
            // Still try stream first; if binary missing fail; else may hand off
            // Fall through for stream attempt on opencode/agy with optional terminal
        }
        if (!activeSession.compareAndSet(false, true)) {
            mainHandler.post { listener.onFailed("Auth already in progress") }
            return null
        }
        val session = CliAuthSession()
        val appCtx = ctx.applicationContext
        executor.execute {
            try {
                runLogin(appCtx, method, def, session, listener)
            } catch (e: Exception) {
                if (!session.cancelled) {
                    mainHandler.post {
                        listener.onFailed(e.message ?: "Login error")
                    }
                }
            } finally {
                activeSession.set(false)
            }
        }
        return session
    }

    fun saveApiKey(
        ctx: Context,
        method: String,
        toolId: String,
        apiKey: String,
        onResult: (Boolean, String, CliToolStatus?) -> Unit
    ) {
        executor.execute {
            val def = CliToolCatalog.byId(toolId)
            if (def == null) {
                mainHandler.post { onResult(false, "Unknown tool", null) }
                return@execute
            }
            val key = apiKey.trim()
            if (key.isBlank()) {
                mainHandler.post { onResult(false, "Empty key", null) }
                return@execute
            }
            val envKey = def.envKey ?: when (toolId) {
                "qwen" -> "BAILIAN_CODING_PLAN_API_KEY"
                "grok" -> "XAI_API_KEY"
                "kiro" -> "KIRO_API_KEY"
                "claude" -> "CLAUDE_CODE_OAUTH_TOKEN"
                else -> null
            }
            if (envKey == null) {
                mainHandler.post { onResult(false, "No env key for tool", null) }
                return@execute
            }
            val ok = writeAuthEnvKey(ctx, method, envKey, key)
            if (toolId == "qwen" && ok) {
                writeQwenSettings(ctx, method, key)
            }
            val st = probeOneSync(ctx, method, def)
            mainHandler.post {
                onResult(ok, if (ok) "Saved $envKey" else "Write failed", st)
            }
        }
    }

    fun logout(
        ctx: Context,
        method: String,
        toolId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        executor.execute {
            val def = CliToolCatalog.byId(toolId)
            if (def == null) {
                mainHandler.post { onResult(false, "Unknown tool") }
                return@execute
            }
            val cmd = when (toolId) {
                "codex" -> CliGuestCommands.logoutCodex()
                "opencode" -> CliGuestCommands.logoutOpencode()
                "kiro" -> CliGuestCommands.logoutKiro()
                "claude" -> CliGuestCommands.logoutClaudeFiles()
                else -> null
            }
            if (cmd != null) {
                val (args, env) = fluxCmd(ctx, cmd, method)
                mergeCliEnv(env)
                ShellCommandRunner.runCaptureExit(ctx, args, env)
            }
            // Clear env keys for this tool
            def.envKey?.let { clearAuthEnvKey(ctx, method, it) }
            when (toolId) {
                "qwen" -> {
                    clearAuthEnvKey(ctx, method, "BAILIAN_CODING_PLAN_API_KEY")
                    clearAuthEnvKey(ctx, method, "DASHSCOPE_API_KEY")
                    try {
                        File(ProjectPathResolver.guestHomeDir(ctx, method), ".qwen/settings.json")
                            .delete()
                    } catch (_: Exception) {
                    }
                }
                "grok" -> {
                    clearAuthEnvKey(ctx, method, "XAI_API_KEY")
                    clearAuthEnvKey(ctx, method, "GROK_API_KEY")
                }
            }
            statusCache.remove("$toolId|$method")
            mainHandler.post { onResult(true, "Logged out ${def.displayName}") }
        }
    }

    fun openBrowser(ctx: Context, url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (_: Exception) {
        }
    }

    fun copyCode(ctx: Context, code: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("cli-auth-code", code))
        } catch (_: Exception) {
        }
    }

    // ── login state machine ─────────────────────────────────────────────────

    private fun runLogin(
        ctx: Context,
        method: String,
        def: CliToolDef,
        session: CliAuthSession,
        listener: CliAuthListener
    ) {
        fun phase(p: CliAuthPhase, msg: String) {
            if (!session.cancelled) mainHandler.post { listener.onPhase(p, msg) }
        }
        fun log(line: String) {
            if (!session.cancelled) mainHandler.post { listener.onLog(line) }
        }
        fun fail(msg: String) {
            phase(CliAuthPhase.FAILED, msg)
            mainHandler.post { listener.onFailed(msg) }
        }

        phase(CliAuthPhase.CHECK_BIN, "Checking ${def.bin}…")
        // Tools mandatory via setup_cli_tools — soft probe only; do not hard-block login
        val installed = isInstalledSync(ctx, method, def)
        if (!installed) {
            log("WARN: ${def.bin} not found on PATH (continuing login attempt)")
        }
        if (session.cancelled) {
            mainHandler.post { listener.onCancelled() }
            return
        }

        when (def.strategy) {
            CliLoginStrategy.DEVICE_CODE -> {
                phase(CliAuthPhase.LOGIN, "Starting device login…")
                val loginCmd = when (def.id) {
                    "codex" -> CliGuestCommands.loginCodexDevice()
                    "kiro" -> CliGuestCommands.loginKiro()
                    else -> null
                }
                if (loginCmd == null) {
                    fail("No device login for ${def.id}")
                    return
                }
                val ok = streamLoginAndCapture(ctx, method, def, loginCmd, session, listener)
                if (session.cancelled) {
                    mainHandler.post { listener.onCancelled() }
                    return
                }
                if (!ok) {
                    // kiro may need terminal; codex fail is hard
                    if (def.id == "kiro") {
                        phase(CliAuthPhase.LOGIN, "Opening guided terminal…")
                        mainHandler.post {
                            listener.onTerminalGuided(def.id, "kiro-cli login")
                        }
                        // Still fail session so overlay closes path can open term
                        fail("Complete login in terminal (device code)")
                        return
                    }
                    fail("Device login failed or timed out")
                    return
                }
            }
            CliLoginStrategy.STREAM_URL -> {
                phase(CliAuthPhase.LOGIN, "Starting browser login…")
                val loginCmd = when (def.id) {
                    "claude" -> CliGuestCommands.loginClaudeSetupToken()
                    else -> null
                }
                if (loginCmd == null) {
                    fail("No stream login for ${def.id}")
                    return
                }
                val token = streamAndCaptureToken(ctx, method, def, loginCmd, session, listener)
                if (session.cancelled) {
                    mainHandler.post { listener.onCancelled() }
                    return
                }
                if (token.isNullOrBlank()) {
                    // fallback guided
                    mainHandler.post {
                        listener.onTerminalGuided(def.id, "claude setup-token")
                    }
                    fail("No token captured — finish in terminal if needed")
                    return
                }
                phase(CliAuthPhase.CAPTURE_TOKEN, "Saving token…")
                log("Token captured (${token.take(12)}…)")
                writeAuthEnvKey(ctx, method, "CLAUDE_CODE_OAUTH_TOKEN", token)
            }
            CliLoginStrategy.TERMINAL_GUIDED -> {
                phase(CliAuthPhase.LOGIN, "Guided terminal login…")
                // Try short stream for URL hints first
                val tryCmd = when (def.id) {
                    "opencode" -> CliGuestCommands.loginOpencode()
                    "agy" -> CliGuestCommands.loginAgy()
                    else -> null
                }
                if (tryCmd != null) {
                    streamLoginAndCapture(ctx, method, def, tryCmd, session, listener, shortTimeout = true)
                }
                if (session.cancelled) {
                    mainHandler.post { listener.onCancelled() }
                    return
                }
                val hint = when (def.id) {
                    "opencode" -> "opencode auth login"
                    "agy" -> "agy"
                    else -> def.bin
                }
                mainHandler.post { listener.onTerminalGuided(def.id, hint) }
                // Verify after short wait not possible; mark for re-probe
                phase(CliAuthPhase.VERIFY, "Complete login in terminal, then refresh")
                val st = probeOneSync(ctx, method, def)
                if (st.loggedIn) {
                    phase(CliAuthPhase.SUCCESS, "Signed in")
                    mainHandler.post { listener.onDone(st) }
                } else {
                    // Soft success path: guided opened — not a hard fail if terminal opened
                    mainHandler.post {
                        listener.onDone(
                            st.copy(detail = "Open terminal: $hint")
                        )
                    }
                }
                return
            }
            CliLoginStrategy.API_KEY_FORM -> {
                fail("Use API key form")
                return
            }
        }

        if (session.cancelled) {
            mainHandler.post { listener.onCancelled() }
            return
        }
        phase(CliAuthPhase.VERIFY, "Verifying…")
        // Poll status a few times (credentials may settle)
        var status = probeOneSync(ctx, method, def)
        var attempts = 0
        while (!status.loggedIn && attempts < 8 && !session.cancelled) {
            Thread.sleep(1500)
            status = probeOneSync(ctx, method, def)
            attempts++
            log("Probe ${attempts}: installed=${status.installed} in=${status.loggedIn}")
        }
        if (session.cancelled) {
            mainHandler.post { listener.onCancelled() }
            return
        }
        if (status.loggedIn) {
            phase(CliAuthPhase.SUCCESS, "Signed in")
            mainHandler.post { listener.onDone(status) }
        } else {
            // Device flow may have completed in CLI but probe weak — soft fail with tip
            fail("Auth ran but status still signed-out (refresh or re-check)")
        }
    }

    /**
     * Stream login command; extract URL/OTP; open browser; wait until process ends
     * or status becomes logged-in.
     */
    private fun streamLoginAndCapture(
        ctx: Context,
        method: String,
        def: CliToolDef,
        loginCmd: String,
        session: CliAuthSession,
        listener: CliAuthListener,
        shortTimeout: Boolean = false
    ): Boolean {
        val (args, env) = fluxCmd(ctx, loginCmd, method)
        mergeCliEnv(env)
        val timeout = if (shortTimeout) 45_000L else CliGuestCommands.AUTH_TIMEOUT_MS
        val latch = CountDownLatch(1)
        var exitCode = -1
        val lastUrl = AtomicReference<String?>(null)
        val lastOtp = AtomicReference<String?>(null)

        val job = ShellCommandRunner.runStreamedCancelable(
            ctx, args, env,
            onLine = { line ->
                if (session.cancelled) return@runStreamedCancelable
                mainHandler.post { listener.onLog(line) }
                extractUrl(line)?.let { url ->
                    if (lastUrl.get() != url) {
                        lastUrl.set(url)
                        mainHandler.post {
                            listener.onPhase(CliAuthPhase.WAIT_BROWSER, "Open browser…")
                            listener.onUrl(url)
                        }
                        openBrowser(ctx, url)
                    }
                }
                extractOtp(line)?.let { otp ->
                    if (lastOtp.get() != otp) {
                        lastOtp.set(otp)
                        copyCode(ctx, otp)
                        mainHandler.post { listener.onOtp(otp) }
                    }
                }
            },
            onDone = { code ->
                exitCode = code
                latch.countDown()
            }
        )
        session.attach(job)

        // Parallel poll status while process runs
        val pollThread = Thread {
            var n = 0
            while (!session.cancelled && latch.count > 0 && n < 120) {
                Thread.sleep(3000)
                n++
                if (session.cancelled) break
                val st = probeOneSync(ctx, method, def)
                if (st.loggedIn) {
                    session.cancel() // stop login process once authenticated
                    break
                }
            }
        }
        pollThread.start()

        val finished = latch.await(timeout, TimeUnit.MILLISECONDS)
        if (!finished) {
            session.cancel()
        }
        session.clearJob()
        try {
            pollThread.join(500)
        } catch (_: Exception) {
        }

        if (session.cancelled && probeOneSync(ctx, method, def).loggedIn) {
            return true
        }
        // Success if logged in OR process exited 0 after OTP shown
        val st = probeOneSync(ctx, method, def)
        if (st.loggedIn) return true
        if (finished && exitCode == 0 && lastOtp.get() != null) {
            // give status a moment
            Thread.sleep(2000)
            return probeOneSync(ctx, method, def).loggedIn
        }
        return st.loggedIn
    }

    private fun streamAndCaptureToken(
        ctx: Context,
        method: String,
        @Suppress("UNUSED_PARAMETER") def: CliToolDef,
        loginCmd: String,
        session: CliAuthSession,
        listener: CliAuthListener
    ): String? {
        val (args, env) = fluxCmd(ctx, loginCmd, method)
        mergeCliEnv(env)
        val latch = CountDownLatch(1)
        val tokenRef = AtomicReference<String?>(null)
        val all = StringBuilder()

        val job = ShellCommandRunner.runStreamedCancelable(
            ctx, args, env,
            onLine = { line ->
                if (session.cancelled) return@runStreamedCancelable
                all.append(line).append('\n')
                mainHandler.post { listener.onLog(line) }
                extractUrl(line)?.let { url ->
                    mainHandler.post {
                        listener.onPhase(CliAuthPhase.WAIT_BROWSER, "Open browser…")
                        listener.onUrl(url)
                    }
                    openBrowser(ctx, url)
                }
                extractClaudeToken(line)?.let { t ->
                    tokenRef.set(t)
                }
            },
            onDone = { latch.countDown() }
        )
        session.attach(job)
        latch.await(CliGuestCommands.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        session.clearJob()
        if (tokenRef.get() == null) {
            extractClaudeToken(all.toString())?.let { tokenRef.set(it) }
        }
        return tokenRef.get()
    }

    // ── probe ───────────────────────────────────────────────────────────────

    private fun probeOneSync(ctx: Context, method: String, def: CliToolDef): CliToolStatus {
        val installed = isInstalledSync(ctx, method, def)
        if (!installed) {
            val st = CliToolStatus(def.id, method, false, false, detail = "not installed")
            statusCache["${def.id}|$method"] = st
            return st
        }
        val statusCmd = when (def.id) {
            "claude" -> CliGuestCommands.statusClaude()
            "codex" -> CliGuestCommands.statusCodex()
            "qwen" -> CliGuestCommands.statusQwen()
            "opencode" -> CliGuestCommands.statusOpencode()
            "agy" -> CliGuestCommands.statusAgy()
            "grok" -> CliGuestCommands.statusGrok()
            "kiro" -> CliGuestCommands.statusKiro()
            else -> "echo AUTH_NO"
        }
        val raw = try {
            val (args, env) = fluxCmd(ctx, statusCmd, method)
            mergeCliEnv(env)
            ShellCommandRunner.runCaptureExit(ctx, args, env).second
        } catch (e: Exception) {
            val st = CliToolStatus(
                def.id, method, true, false,
                error = e.message, raw = ""
            )
            statusCache["${def.id}|$method"] = st
            return st
        }
        val st = parseStatus(def, method, raw)
        statusCache["${def.id}|$method"] = st
        return st
    }

    /**
     * Guest command -v + host-visible rootfs path check.
     * Host FS is authoritative when proot/chroot capture is flaky (quote/NOMATCH).
     * CLI tools are mandatory onboarding — host hit alone is enough for INSTALLED.
     */
    private fun isInstalledSync(ctx: Context, method: String, def: CliToolDef): Boolean {
        if (hostBinPresent(ctx, method, def)) return true
        return try {
            val (args, env) = fluxCmd(
                ctx,
                CliGuestCommands.detectBin(def.bin, def.altBins),
                method
            )
            mergeCliEnv(env)
            val out = ShellCommandRunner.runCaptureExit(ctx, args, env).second
            out.contains("BIN_OK")
        } catch (_: Exception) {
            // Soft: host miss + capture fail → still try host once more with nvm scan
            hostBinPresent(ctx, method, def, deep = true)
        }
    }

    /** Walk guest home for bin under .local/bin, bin, nvm node bins. */
    private fun hostBinPresent(
        ctx: Context,
        method: String,
        def: CliToolDef,
        deep: Boolean = true
    ): Boolean {
        val names = listOf(def.bin) + def.altBins
        val home = ProjectPathResolver.guestHomeDir(ctx, method)
        val dirs = mutableListOf(
            File(home, ".local/bin"),
            File(home, "bin"),
            File(home, ".cargo/bin")
        )
        if (deep) {
            val nodeRoot = File(home, ".nvm/versions/node")
            if (nodeRoot.isDirectory) {
                nodeRoot.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("v") }
                    ?.sortedBy { it.name }
                    ?.forEach { dirs.add(File(it, "bin")) }
            }
            // system paths on chroot rootfs (visible on host)
            val root = if (method == "chroot") {
                ProjectPathResolver.chrootRootfsDir()
            } else {
                ProjectPathResolver.prootRootfsDir(ctx)
            }
            dirs.add(File(root, "usr/local/bin"))
            dirs.add(File(root, "usr/bin"))
            dirs.add(File(root, "bin"))
        }
        for (d in dirs) {
            for (n in names) {
                val f = File(d, n)
                if (f.isFile && (f.canExecute() || f.length() > 0L)) return true
            }
        }
        return false
    }

    private fun parseStatus(def: CliToolDef, method: String, raw: String): CliToolStatus {
        val lower = raw.lowercase()
        when (def.id) {
            "codex" -> {
                val loggedIn = lower.contains("logged in") ||
                    lower.contains("signed in") ||
                    (lower.contains("chatgpt") && !lower.contains("not logged")) ||
                    (lower.contains("email") && lower.contains("@")) ||
                    (!lower.contains("not logged") && lower.contains("account"))
                val notIn = lower.contains("not logged") ||
                    lower.contains("not signed") ||
                    lower.contains("log in") && lower.contains("required")
                val inOk = loggedIn && !notIn
                val label = Regex("""[\w.+-]+@[\w.-]+\.\w+""").find(raw)?.value
                return CliToolStatus(
                    def.id, method, true, inOk,
                    accountLabel = label,
                    detail = if (inOk) "codex" else "not logged in",
                    raw = raw
                )
            }
            else -> {
                val authOk = raw.contains("AUTH_OK")
                val authMaybe = raw.contains("AUTH_MAYBE")
                val label = when {
                    raw.contains("credentials") -> "credentials"
                    raw.contains("env_token") || raw.contains("env_key") -> "env"
                    raw.contains("settings") -> "settings"
                    raw.contains("auth_json") -> "auth.json"
                    else -> null
                }
                return CliToolStatus(
                    def.id, method, true,
                    loggedIn = authOk || (authMaybe && def.id == "kiro"),
                    accountLabel = label,
                    detail = when {
                        authOk -> "signed in"
                        authMaybe -> "maybe"
                        else -> "not logged in"
                    },
                    raw = raw
                )
            }
        }
    }

    // ── file writes ─────────────────────────────────────────────────────────

    private fun authEnvFile(ctx: Context, method: String): File {
        val home = ProjectPathResolver.guestHomeDir(ctx, method)
        return File(home, CliGuestCommands.AUTH_ENV_REL)
    }

    private fun writeAuthEnvKey(
        ctx: Context,
        method: String,
        envKey: String,
        value: String
    ): Boolean {
        return try {
            val f = authEnvFile(ctx, method)
            f.parentFile?.mkdirs()
            val safeKey = envKey.replace(Regex("[^A-Za-z0-9_]"), "")
            if (safeKey.isBlank()) return false
            // Escape single quotes for shell
            val safeVal = value.replace("'", "'\\''")
            val existing = if (f.exists()) f.readText() else ""
            val lines = existing.lines().filter {
                !it.trimStart().startsWith("export $safeKey=") &&
                    !it.trimStart().startsWith("$safeKey=")
            }.toMutableList()
            if (lines.isEmpty() || lines.first() != "# Managed by CliAuthService — do not commit") {
                lines.add(0, "# Managed by CliAuthService — do not commit")
            }
            lines.add("export $safeKey='$safeVal'")
            f.writeText(lines.filter { it.isNotBlank() || it.startsWith("#") }.joinToString("\n") + "\n")
            try {
                f.setReadable(false, false)
                f.setReadable(true, true)
                f.setWritable(false, false)
                f.setWritable(true, true)
            } catch (_: Exception) {
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun clearAuthEnvKey(ctx: Context, method: String, envKey: String) {
        try {
            val f = authEnvFile(ctx, method)
            if (!f.exists()) return
            val safeKey = envKey.replace(Regex("[^A-Za-z0-9_]"), "")
            val lines = f.readText().lines().filter {
                !it.trimStart().startsWith("export $safeKey=") &&
                    !it.trimStart().startsWith("$safeKey=")
            }
            f.writeText(lines.joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }

    private fun writeQwenSettings(ctx: Context, method: String, apiKey: String): Boolean {
        return try {
            val dir = File(ProjectPathResolver.guestHomeDir(ctx, method), ".qwen")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "settings.json")
            val json = JSONObject()
            val modelProviders = JSONObject()
            val openai = JSONObject()
            openai.put("protocol", "openai")
            val models = org.json.JSONArray()
            val m = JSONObject()
            m.put("id", "qwen3-coder-plus")
            m.put("name", "qwen3-coder-plus (Coding Plan)")
            m.put("baseUrl", "https://coding-intl.dashscope.aliyuncs.com/v1")
            m.put("envKey", "BAILIAN_CODING_PLAN_API_KEY")
            models.put(m)
            openai.put("models", models)
            modelProviders.put("openai", openai)
            json.put("modelProviders", modelProviders)
            val env = JSONObject()
            env.put("BAILIAN_CODING_PLAN_API_KEY", apiKey)
            json.put("env", env)
            val security = JSONObject()
            val auth = JSONObject()
            auth.put("selectedType", "openai")
            security.put("auth", auth)
            json.put("security", security)
            val model = JSONObject()
            model.put("name", "qwen3-coder-plus")
            json.put("model", model)
            f.writeText(json.toString(2))
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── parse helpers ───────────────────────────────────────────────────────

    private fun extractUrl(line: String): String? {
        val m = URL_PATTERN.matcher(line)
        if (!m.find()) return null
        var u = m.group(1) ?: return null
        // trim trailing punctuation
        u = u.trimEnd('.', ',', ';', ')', ']', '"', '\'')
        if (!u.startsWith("http")) return null
        // prefer auth-related URLs
        val lower = u.lowercase()
        if (lower.contains("auth") || lower.contains("login") ||
            lower.contains("oauth") || lower.contains("device") ||
            lower.contains("openai") || lower.contains("claude") ||
            lower.contains("kiro") || lower.contains("x.ai") ||
            lower.contains("google") || lower.contains("anthropic")
        ) {
            return u
        }
        // still return first https for setup-token
        return u
    }

    private fun extractOtp(line: String): String? {
        // Avoid matching years/dates: prefer patterns near "code"
        val m = OTP_PATTERN.matcher(line.uppercase())
        if (!m.find()) return null
        val code = m.group(1) ?: return null
        // skip pure hex that looks like hashes if too long segments
        return code
    }

    private fun extractClaudeToken(text: String): String? {
        val m = CLAUDE_TOKEN.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun fluxCmd(
        ctx: Context,
        shellCmd: String,
        method: String
    ): Pair<Array<String>, HashMap<String, String>> =
        LinuxCommandBuilder.build(ctx, shellCmd, user = "flux", method = method)

    private fun mergeCliEnv(env: HashMap<String, String>) {
        CliGuestCommands.cliEnv().forEach { (k, v) -> env[k] = v }
    }
}
