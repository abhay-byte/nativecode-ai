package com.ivarna.nativecode.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.ivarna.nativecode.marketplace.PackageInstallRunner
import com.ivarna.nativecode.terminal.LinuxCommandBuilder
import com.ivarna.nativecode.terminal.ProjectPathResolver
import com.ivarna.nativecode.terminal.ShellCommandRunner
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * SSOT for guest `gh`: probe, install, device-code auth, repo list, logout.
 * Per-method state (proot ≠ chroot). Auth always as flux.
 *
 * OAuth device flow runs on **Android network** (survives browser switch / proot
 * flaky HTTPS). Token is injected into guest via `gh auth login --with-token`.
 */
object GitHubCliService {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private val statusCache = mutableMapOf<String, GhAuthStatus>()
    private val activeSession = AtomicBoolean(false)

    private val OTP_PATTERN = Pattern.compile("\\b([A-Z0-9]{4}-[A-Z0-9]{4})\\b")

    /** Public OAuth client id used by official GitHub CLI (`cli/cli`). */
    private const val GH_CLI_CLIENT_ID = "178c6fc778ccc68e1d6a"
    private const val OAUTH_SCOPES = "repo read:org gist workflow"
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"

    fun cachedStatus(method: String): GhAuthStatus? = statusCache[method]

    fun isSessionActive(): Boolean = activeSession.get()

    // ── Public API ──────────────────────────────────────────────────────────

    fun probeStatus(
        ctx: Context,
        method: String,
        onResult: (GhAuthStatus) -> Unit
    ) {
        executor.execute {
            val status = probeStatusSync(ctx, method)
            mainHandler.post { onResult(status) }
        }
    }

    fun listRepos(
        ctx: Context,
        method: String,
        onResult: (Result<List<GhRepo>>) -> Unit
    ) {
        executor.execute {
            val result = try {
                Result.success(listReposSync(ctx, method))
            } catch (e: Exception) {
                Result.failure(e)
            }
            mainHandler.post { onResult(result) }
        }
    }

    fun logout(
        ctx: Context,
        method: String,
        onResult: (Boolean, String) -> Unit
    ) {
        executor.execute {
            val status = probeStatusSync(ctx, method)
            val user = status.username
            if (user.isNullOrBlank()) {
                mainHandler.post { onResult(false, "Not signed in") }
                return@execute
            }
            val (args, env) = fluxCmd(ctx, GhGuestCommands.authLogout(user), method)
            mergeGhEnv(env)
            val (exit, out) = ShellCommandRunner.runCaptureExit(ctx, args, env)
            statusCache.remove(method)
            val ok = exit == 0
            mainHandler.post {
                onResult(ok, if (ok) "Logged out @$user" else out.trim().ifBlank { "Logout failed" })
            }
        }
    }

    /**
     * Full connect: ensure gh → status → login if needed → setup-git → verify.
     * Returns null if another session is already active.
     */
    fun connect(
        ctx: Context,
        method: String,
        listener: GhAuthListener
    ): GhAuthSession? {
        if (!activeSession.compareAndSet(false, true)) {
            mainHandler.post { listener.onFailed("Auth already in progress") }
            return null
        }
        val session = GhAuthSession()
        val appCtx = ctx.applicationContext

        executor.execute {
            try {
                runConnect(appCtx, method, session, listener)
            } catch (e: Exception) {
                if (!session.cancelled) {
                    mainHandler.post {
                        listener.onFailed(e.message ?: "Connect failed")
                    }
                }
            } finally {
                session.clearJob()
                activeSession.set(false)
            }
        }
        return session
    }

    fun openDeviceBrowser(ctx: Context, code: String? = null) {
        val url = if (!code.isNullOrBlank()) {
            "${GhGuestCommands.DEVICE_URL}?user_code=$code"
        } else {
            GhGuestCommands.DEVICE_URL
        }
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun copyCode(ctx: Context, code: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("GitHub one-time code", code))
        } catch (_: Exception) {
        }
    }

    // ── Sync helpers ────────────────────────────────────────────────────────

    private fun probeStatusSync(ctx: Context, method: String): GhAuthStatus {
        // Detect gh
        val (dArgs, dEnv) = fluxCmd(ctx, GhGuestCommands.detectGh(), method)
        mergeGhEnv(dEnv)
        val (_, dOut) = try {
            ShellCommandRunner.runCaptureExit(ctx, dArgs, dEnv)
        } catch (e: Exception) {
            val s = GhAuthStatus(method, false, false, error = e.message)
            statusCache[method] = s
            return s
        }
        val installed = dOut.contains("GH_OK") && !dOut.contains("GH_MISSING")
        if (!installed) {
            val s = GhAuthStatus(method, false, false, raw = dOut)
            statusCache[method] = s
            return s
        }

        // Prefer whoami (works even if auth status --json missing / guest API flaky)
        val (wArgs, wEnv) = fluxCmd(ctx, GhGuestCommands.whoami(), method)
        mergeGhEnv(wEnv)
        val wPair: Pair<Int, String> = try {
            ShellCommandRunner.runCaptureExit(ctx, wArgs, wEnv)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "")
        }
        val wExit = wPair.first
        val wOut = wPair.second
        val loginFromApi = wOut.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.contains(' ') && !it.contains("error", true) && it.matches(Regex("[A-Za-z0-9-]+")) }
        if (wExit == 0 && !loginFromApi.isNullOrBlank()) {
            val s = GhAuthStatus(method, true, true, loginFromApi, raw = wOut)
            statusCache[method] = s
            return s
        }

        // JSON status (newer gh)
        val (sArgs, sEnv) = fluxCmd(ctx, GhGuestCommands.authStatusJson(), method)
        mergeGhEnv(sEnv)
        val sOut: String = try {
            ShellCommandRunner.runCaptureExit(ctx, sArgs, sEnv).second
        } catch (_: Exception) {
            ""
        }
        val fromJson = parseAuthStatusJson(method, sOut)
        if (fromJson.loggedIn) {
            statusCache[method] = fromJson
            return fromJson
        }

        // Human status (older gh / offline token file present)
        val (hArgs, hEnv) = fluxCmd(ctx, GhGuestCommands.authStatusHuman(), method)
        mergeGhEnv(hEnv)
        val hOut: String = try {
            ShellCommandRunner.runCaptureExit(ctx, hArgs, hEnv).second
        } catch (e: Exception) {
            e.message ?: ""
        }
        val fromHuman = parseAuthStatusHuman(method, hOut)
        // Also: hosts.yml on disk with user key (no network)
        if (!fromHuman.loggedIn) {
            val fromFile = readHostsYmlUser(ctx, method)
            if (fromFile != null) {
                val s = GhAuthStatus(method, true, true, fromFile, raw = hOut)
                statusCache[method] = s
                return s
            }
        }
        statusCache[method] = fromHuman
        return fromHuman
    }

    private fun listReposSync(ctx: Context, method: String): List<GhRepo> {
        val (args, env) = fluxCmd(ctx, GhGuestCommands.repoList(), method)
        mergeGhEnv(env)
        val (exit, out) = ShellCommandRunner.runCaptureExit(ctx, args, env)
        if (exit != 0) {
            throw IllegalStateException(out.trim().ifBlank { "repo list failed (exit $exit)" })
        }
        return parseRepoList(out)
    }

    private fun runConnect(
        ctx: Context,
        method: String,
        session: GhAuthSession,
        listener: GhAuthListener
    ) {
        fun phase(p: GhAuthPhase, msg: String) {
            if (session.cancelled) return
            mainHandler.post { listener.onPhase(p, msg) }
        }
        fun log(line: String) {
            if (session.cancelled) return
            mainHandler.post { listener.onLog(line) }
        }
        fun fail(msg: String) {
            if (session.cancelled) {
                mainHandler.post { listener.onCancelled() }
            } else {
                mainHandler.post { listener.onFailed(msg) }
            }
        }

        // CHECK_GH
        phase(GhAuthPhase.CHECK_GH, "Checking gh…")
        var status = probeStatusSync(ctx, method)
        if (session.cancelled) {
            mainHandler.post { listener.onCancelled() }
            return
        }

        // INSTALL if needed
        if (!status.ghInstalled) {
            phase(GhAuthPhase.INSTALL_GH, "Installing gh…")
            log("\$ apt-get install -y gh")
            val installOk = runInstall(ctx, method, session, listener)
            if (session.cancelled) {
                mainHandler.post { listener.onCancelled() }
                return
            }
            if (!installOk) {
                fail("Failed to install gh")
                return
            }
            status = probeStatusSync(ctx, method)
            if (!status.ghInstalled) {
                fail("gh still missing after install")
                return
            }
            log("gh installed")
        }

        // CHECK_AUTH
        phase(GhAuthPhase.CHECK_AUTH, "Checking auth…")
        if (status.loggedIn && !status.username.isNullOrBlank()) {
            // ensure git helper
            runSetupGit(ctx, method)
            status = probeStatusSync(ctx, method)
            phase(GhAuthPhase.SUCCESS, "Already signed in")
            mainHandler.post { listener.onDone(status) }
            return
        }

        // LOGIN — Android device flow (not guest gh --web poll; proot often drops HTTPS)
        phase(GhAuthPhase.LOGIN, "Starting device login…")
        log("OAuth device flow via Android network…")
        val loginOk = runLoginAndroidDeviceFlow(ctx, method, session, listener)
        if (session.cancelled) {
            mainHandler.post { listener.onCancelled() }
            return
        }
        if (!loginOk) {
            fail("Login failed or timed out")
            return
        }

        // VERIFY
        phase(GhAuthPhase.VERIFY, "Verifying…")
        runSetupGit(ctx, method)
        status = probeStatusSync(ctx, method)
        if (status.loggedIn) {
            phase(GhAuthPhase.SUCCESS, "Signed in as @${status.username}")
            mainHandler.post { listener.onDone(status) }
        } else {
            fail("Auth completed but not logged in")
        }
    }

    private fun runInstall(
        ctx: Context,
        method: String,
        session: GhAuthSession,
        listener: GhAuthListener
    ): Boolean {
        // Primary apt
        var ok = streamRoot(ctx, method, GhGuestCommands.installPrimary(), session, listener)
        if (session.cancelled) return false
        if (ok) {
            // re-check installed
            val st = probeStatusSync(ctx, method)
            if (st.ghInstalled) return true
        }
        mainHandler.post {
            listener.onLog("Primary apt failed or package missing — trying GitHub CLI repo…")
        }
        streamRoot(ctx, method, GhGuestCommands.installFallback(), session, listener)
        if (session.cancelled) return false
        val st = probeStatusSync(ctx, method)
        return st.ghInstalled
    }

    private fun streamRoot(
        ctx: Context,
        method: String,
        guestCmd: String,
        session: GhAuthSession,
        listener: GhAuthListener
    ): Boolean {
        val (args, env) = PackageInstallRunner.buildRootExec(ctx, guestCmd, method)
        return streamBlocking(ctx, args, env, session, listener, timeoutMs = 8 * 60 * 1000L)
    }

    /**
     * Device OAuth on Android HTTPS, then store token for guest flux.
     * 1) Write ~/.config/gh/hosts.yml into rootfs (reliable, no proot pipe)
     * 2) Fallback: token file + `gh auth login --with-token`
     */
    private fun runLoginAndroidDeviceFlow(
        ctx: Context,
        method: String,
        session: GhAuthSession,
        listener: GhAuthListener
    ): Boolean {
        val logLine: (String) -> Unit = { line ->
            if (!session.cancelled) mainHandler.post { listener.onLog(line) }
        }

        val device = try {
            requestDeviceCode()
        } catch (e: Exception) {
            logLine("Device code request failed: ${e.message}")
            return false
        }
        if (session.cancelled) return false

        val userCode = device.userCode
        logLine("One-time code: $userCode")
        logLine("Open: ${device.verificationUri}")
        logLine("(Approve in Android browser; app polls GitHub on device network)")
        copyCode(ctx, userCode)
        mainHandler.post {
            listener.onPhase(GhAuthPhase.WAIT_BROWSER, "Code: $userCode")
            listener.onOtp(userCode)
        }
        openDeviceBrowser(ctx, userCode)

        val token = pollAccessToken(device, session, logLine)
        if (session.cancelled) return false
        if (token.isNullOrBlank()) {
            logLine("No access token (denied, expired, or timeout)")
            return false
        }

        logLine("Token received — resolving username…")
        mainHandler.post { listener.onPhase(GhAuthPhase.VERIFY, "Saving credentials…") }
        val username = try {
            fetchGithubLogin(token)
        } catch (e: Exception) {
            logLine("api.github.com/user failed: ${e.message}")
            null
        }
        if (username.isNullOrBlank()) {
            logLine("Could not resolve GitHub username from token")
        } else {
            logLine("GitHub user: @$username")
        }

        // Primary: write hosts.yml into guest rootfs from Android (no guest network)
        val wrote = writeHostsYml(ctx, method, token, username ?: "user")
        if (wrote) {
            logLine("Wrote ~/.config/gh/hosts.yml for flux ($method)")
        } else {
            logLine("Direct hosts.yml write failed — trying gh --with-token…")
            if (!injectTokenViaGh(ctx, method, token, logLine)) {
                logLine("Token inject failed")
                return false
            }
        }

        // Ensure git credential helper
        runSetupGit(ctx, method)
        return true
    }

    /** Write gh hosts.yml into guest flux home on host-visible rootfs. */
    private fun writeHostsYml(
        ctx: Context,
        method: String,
        token: String,
        username: String
    ): Boolean {
        val home = ProjectPathResolver.guestHomeDir(ctx, method)
        val dir = File(home, ".config/gh")
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            // Safe: token is OAuth alnum/_ ; username is login
            val safeUser = username.replace(Regex("[^A-Za-z0-9-]"), "")
            if (safeUser.isBlank()) return false
            val safeToken = token.trim()
            // gho_/ghp_/github_pat_* — reject whitespace / YAML-breakers only
            if (safeToken.isEmpty() || safeToken.any { it.isWhitespace() || it == '"' || it == '\'' }) {
                return false
            }
            val hosts = File(dir, "hosts.yml")
            // Format compatible with gh 2.4x insecure storage
            hosts.writeText(
                """
                |github.com:
                |    oauth_token: $safeToken
                |    user: $safeUser
                |    git_protocol: https
                |    users:
                |        $safeUser:
                |            oauth_token: $safeToken
                """.trimMargin() + "\n"
            )
            hosts.setReadable(false, false)
            hosts.setReadable(true, true)
            hosts.setWritable(false, false)
            hosts.setWritable(true, true)
            // config.yml optional
            val cfg = File(dir, "config.yml")
            if (!cfg.exists()) {
                cfg.writeText("git_protocol: https\n")
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readHostsYmlUser(ctx: Context, method: String): String? {
        return try {
            val f = File(ProjectPathResolver.guestHomeDir(ctx, method), ".config/gh/hosts.yml")
            if (!f.isFile) return null
            val text = f.readText()
            // user: name
            Regex("""(?m)^\s*user:\s*(\S+)\s*$""").find(text)?.groupValues?.get(1)
                ?.takeIf { it != "github.com" && !it.contains("token") }
        } catch (_: Exception) {
            null
        }
    }

    private fun injectTokenViaGh(
        ctx: Context,
        method: String,
        token: String,
        log: (String) -> Unit
    ): Boolean {
        // Stage token file inside guest home (host-visible)
        val home = ProjectPathResolver.guestHomeDir(ctx, method)
        val tokenFile = File(home, ".config/gh/.nc_token_inject")
        return try {
            tokenFile.parentFile?.mkdirs()
            tokenFile.writeText(token.trim() + "\n")
            val guestPath = "/home/flux/.config/gh/.nc_token_inject"
            val (args, env) = fluxCmd(ctx, GhGuestCommands.authLoginWithTokenFile(guestPath), method)
            mergeGhEnv(env)
            val (exit, out) = ShellCommandRunner.runCaptureExit(ctx, args, env)
            out.lineSequence().forEach { line ->
                val safe = if (line.contains(token)) "***" else line
                if (safe.isNotBlank()) log(safe)
            }
            // cleanup host-side if guest rm failed
            try { tokenFile.delete() } catch (_: Exception) {}
            exit == 0
        } catch (e: Exception) {
            log("inject: ${e.message}")
            try { tokenFile.delete() } catch (_: Exception) {}
            false
        }
    }

    private fun fetchGithubLogin(token: String): String? {
        val conn = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "NativeCode-GitHubConnect")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${text.take(120)}")
            }
            return JSONObject(text).optString("login", "").ifBlank { null }
        } finally {
            conn.disconnect()
        }
    }

    private data class DeviceCodeResponse(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSec: Int,
        val expiresInSec: Int
    )

    private fun requestDeviceCode(): DeviceCodeResponse {
        val body = formEncode(
            "client_id" to GH_CLI_CLIENT_ID,
            "scope" to OAUTH_SCOPES
        )
        val raw = httpPost(DEVICE_CODE_URL, body)
        val json = JSONObject(raw)
        if (json.has("error")) {
            throw IllegalStateException(json.optString("error_description", json.optString("error")))
        }
        val userCode = json.getString("user_code")
        val deviceCode = json.getString("device_code")
        val uri = json.optString("verification_uri", GhGuestCommands.DEVICE_URL)
        val interval = json.optInt("interval", 5).coerceAtLeast(1)
        val expires = json.optInt("expires_in", 900)
        return DeviceCodeResponse(deviceCode, userCode, uri, interval, expires)
    }

    private fun pollAccessToken(
        device: DeviceCodeResponse,
        session: GhAuthSession,
        log: (String) -> Unit
    ): String? {
        val deadline = System.currentTimeMillis() +
            (device.expiresInSec * 1000L).coerceAtMost(GhGuestCommands.AUTH_TIMEOUT_MS)
        var intervalMs = device.intervalSec * 1000L
        var lastStatusLog = 0L

        while (System.currentTimeMillis() < deadline) {
            if (session.cancelled) return null
            Thread.sleep(intervalMs)
            if (session.cancelled) return null

            val body = formEncode(
                "client_id" to GH_CLI_CLIENT_ID,
                "device_code" to device.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            )
            val raw = try {
                httpPost(ACCESS_TOKEN_URL, body)
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                if (now - lastStatusLog > 15_000) {
                    log("Poll network error (retrying): ${e.message}")
                    lastStatusLog = now
                }
                continue
            }

            val json = try {
                JSONObject(raw)
            } catch (_: Exception) {
                // form-encoded fallback: access_token=...&...
                if (raw.contains("access_token=")) {
                    return raw.substringAfter("access_token=").substringBefore('&').trim()
                }
                continue
            }

            when {
                json.has("access_token") -> {
                    return json.getString("access_token")
                }
                json.optString("error") == "authorization_pending" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastStatusLog > 20_000) {
                        log("Waiting for browser approval…")
                        lastStatusLog = now
                    }
                }
                json.optString("error") == "slow_down" -> {
                    intervalMs += 5000
                    log("Slowing poll…")
                }
                json.optString("error") == "expired_token" -> {
                    log("Device code expired")
                    return null
                }
                json.optString("error") == "access_denied" -> {
                    log("Access denied in browser")
                    return null
                }
                json.has("error") -> {
                    log(json.optString("error_description", json.optString("error")))
                    return null
                }
            }
        }
        log("Auth timed out")
        return null
    }

    private fun formEncode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    private fun httpPost(url: String, formBody: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "NativeCode-GitHubConnect")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(formBody) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            if (code !in 200..299 && text.isBlank()) {
                throw IllegalStateException("HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun runSetupGit(ctx: Context, method: String) {
        try {
            val (args, env) = fluxCmd(ctx, GhGuestCommands.authSetupGit(), method)
            mergeGhEnv(env)
            ShellCommandRunner.runCaptureExit(ctx, args, env)
        } catch (_: Exception) {
        }
    }

    /**
     * Block bg thread until stream completes or cancel/timeout.
     * onLine/onDone already on main from runner; we need exit on bg.
     */
    private fun streamBlocking(
        ctx: Context,
        args: Array<String>,
        env: Map<String, String>?,
        session: GhAuthSession,
        listener: GhAuthListener,
        timeoutMs: Long
    ): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var exitCode = -1
        val job = ShellCommandRunner.runStreamedCancelable(
            ctx, args, env,
            onLine = { line -> mainHandler.post { listener.onLog(line) } },
            onDone = { code ->
                exitCode = code
                latch.countDown()
            }
        )
        session.attach(job)
        val finished = latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            session.cancel()
            return false
        }
        session.clearJob()
        return exitCode == 0 && !session.cancelled
    }

    // ── Parse ───────────────────────────────────────────────────────────────

    fun parseAuthStatusJson(method: String, raw: String): GhAuthStatus {
        // Find JSON object in output (may have warnings around it)
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return GhAuthStatus(method, true, false, raw = raw)
        }
        return try {
            val obj = JSONObject(raw.substring(jsonStart, jsonEnd + 1))
            val hosts = obj.optJSONObject("hosts")
            val arr = hosts?.optJSONArray(GhGuestCommands.HOST)
            if (arr == null || arr.length() == 0) {
                return GhAuthStatus(method, true, false, raw = raw)
            }
            var username: String? = null
            var loggedIn = false
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val active = e.optBoolean("active", false)
                val state = e.optString("state", "")
                val login = e.optString("login", "").ifBlank { null }
                if (active && state == "success" && login != null) {
                    loggedIn = true
                    username = login
                    break
                }
                if (state == "success" && login != null && username == null) {
                    loggedIn = true
                    username = login
                }
            }
            GhAuthStatus(method, true, loggedIn, username, raw)
        } catch (_: Exception) {
            GhAuthStatus(method, true, false, raw = raw, error = "parse error")
        }
    }

    fun parseAuthStatusHuman(method: String, raw: String): GhAuthStatus {
        // e.g. "✓ Logged in to github.com as abhay-byte" / "account abhay-byte"
        val patterns = listOf(
            Regex("""Logged in to github\.com as (\S+)""", RegexOption.IGNORE_CASE),
            Regex("""Logged in to github\.com account (\S+)""", RegexOption.IGNORE_CASE),
            Regex("""account\s+(\S+)\s+\(""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(raw)
            if (m != null) {
                val user = m.groupValues[1].trim().trimEnd(')', ',', '.')
                if (user.isNotBlank()) {
                    return GhAuthStatus(method, true, true, user, raw)
                }
            }
        }
        val notLogged = raw.contains("not logged", true) ||
            raw.contains("You are not logged", true) ||
            raw.contains("no accounts", true)
        return GhAuthStatus(method, true, false, raw = raw, error = if (notLogged) null else null)
    }

    fun parseRepoList(raw: String): List<GhRepo> {
        val jsonStart = raw.indexOf('[')
        val jsonEnd = raw.lastIndexOf(']')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()
        val arr = org.json.JSONArray(raw.substring(jsonStart, jsonEnd + 1))
        val out = ArrayList<GhRepo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("nameWithOwner", "")
            val url = o.optString("url", "")
            if (name.isBlank() || url.isBlank()) continue
            val desc = o.optString("description", "").ifBlank { null }
            out.add(
                GhRepo(
                    nameWithOwner = name,
                    url = url,
                    isPrivate = o.optBoolean("isPrivate", false),
                    description = desc
                )
            )
        }
        return out
    }

    fun extractOtp(line: String): String? {
        val m = OTP_PATTERN.matcher(line.uppercase())
        return if (m.find()) m.group(1) else null
    }

    // ── Builders ────────────────────────────────────────────────────────────

    private fun fluxCmd(
        ctx: Context,
        shellCmd: String,
        method: String
    ): Pair<Array<String>, HashMap<String, String>> =
        LinuxCommandBuilder.build(ctx, shellCmd, user = "flux", method = method)

    private fun mergeGhEnv(env: HashMap<String, String>) {
        GhGuestCommands.ghEnv().forEach { (k, v) -> env[k] = v }
    }
}
