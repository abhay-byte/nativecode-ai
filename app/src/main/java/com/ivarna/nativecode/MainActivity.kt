package com.ivarna.nativecode

import android.graphics.Color
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var homeLayout: LinearLayout
    private lateinit var terminalLayout: FrameLayout

    private lateinit var terminalView: TerminalView
    private var terminalSession: TerminalSession? = null

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ID_HOME = 1
    private val ID_TERMINAL = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatic layout configuration
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        bottomNavigation = BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            menu.add(Menu.NONE, ID_HOME, Menu.NONE, "Home").setIcon(android.R.drawable.ic_menu_info_details)
            menu.add(Menu.NONE, ID_TERMINAL, Menu.NONE, "Terminal").setIcon(android.R.drawable.ic_media_play)
        }

        rootLayout.addView(contentFrame)
        rootLayout.addView(bottomNavigation)
        setContentView(rootLayout)

        // Initialize Home Layout
        homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val x11ButtonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val launchX11Button = Button(this).apply {
            text = "Launch X11 Display"
            setOnClickListener {
                val intent = Intent(this@MainActivity, com.termux.x11.MainActivity::class.java)
                startActivity(intent)
            }
        }

        val x11PrefsButton = Button(this).apply {
            text = "X11 Preferences"
            setOnClickListener {
                val intent = Intent(this@MainActivity, com.termux.x11.LoriePreferences::class.java)
                startActivity(intent)
            }
        }

        x11ButtonContainer.addView(launchX11Button)
        x11ButtonContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) })
        x11ButtonContainer.addView(x11PrefsButton)
        homeLayout.addView(x11ButtonContainer)

        statusText = TextView(this).apply {
            text = "FluxLinux: Starting Setup..."
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }
        homeLayout.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isIndeterminate = true
            setPadding(0, 0, 0, 16)
        }
        homeLayout.addView(progressBar)

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        startButton = Button(this).apply {
            text = "Start GUI"
            isEnabled = false
            setOnClickListener { startGui() }
        }

        stopButton = Button(this).apply {
            text = "Stop GUI"
            isEnabled = false
            setOnClickListener { stopGui() }
        }

        buttonContainer.addView(startButton)
        // Add spacing
        buttonContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(32, 1) })
        buttonContainer.addView(stopButton)
        homeLayout.addView(buttonContainer)

        logScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.BLACK)
        }
        logText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.GREEN)
            setPadding(16, 16, 16, 16)
        }
        logScrollView.addView(logText)
        homeLayout.addView(logScrollView)

        // Initialize Terminal Layout
        terminalLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        terminalView = TerminalView(this, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        terminalLayout.addView(terminalView)

        contentFrame.addView(homeLayout)
        contentFrame.addView(terminalLayout)

        // Bottom Navigation Logic
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                ID_HOME -> {
                    homeLayout.visibility = View.VISIBLE
                    terminalLayout.visibility = View.GONE
                    true
                }
                ID_TERMINAL -> {
                    homeLayout.visibility = View.GONE
                    terminalLayout.visibility = View.VISIBLE
                    terminalView.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
                    true
                }
                else -> false
            }
        }

        // Always deploy scripts on startup to apply any asset updates
        deployScripts()

        // Check Setup State
        val setupCompleteFile = File(filesDir, "setup_complete")
        if (setupCompleteFile.exists()) {
            statusText.text = "FluxLinux: Environment Ready!"
            progressBar.visibility = View.GONE
            startButton.isEnabled = true
            stopButton.isEnabled = true
            initTerminalView()
        } else {
            runFirstTimeSetup()
        }
    }

    private fun deployScripts() {
        try {
            val homeDir = File(filesDir, "home")
            homeDir.mkdirs()

            val scripts = arrayOf("setup_termux.sh", "termux_tweaks.sh", "flux_install.sh", "start_gui.sh", "stop_gui.sh")
            for (script in scripts) {
                val scriptFile = File(homeDir, script)
                val assetPath = if (script.contains("tweaks")) "scripts/termux_tweaks.sh" else "scripts/$script"
                assets.open(assetPath).use { input ->
                    FileOutputStream(scriptFile).use { output ->
                        input.copyTo(output)
                    }
                }
                scriptFile.setExecutable(true)
            }

            // Copy all scripts to a dedicated scripts/ folder in home for manual running
            val scriptsDir = File(homeDir, "scripts")
            scriptsDir.mkdirs()
            val allScripts = arrayOf(
                "setup_termux.sh", "termux_tweaks.sh", "flux_install.sh", 
                "start_gui.sh", "stop_gui.sh", "setup_debian_family.sh", 
                "setup_hw_accel_debian.sh", "setup_customization_debian.sh"
            )
            for (script in allScripts) {
                val scriptFile = File(scriptsDir, script)
                val assetPath = if (script.contains("tweaks")) "scripts/termux_tweaks.sh" else "scripts/$script"
                assets.open(assetPath).use { input ->
                    FileOutputStream(scriptFile).use { output ->
                        input.copyTo(output)
                    }
                }
                scriptFile.setExecutable(true)
            }
        } catch (e: Exception) {
            Log.e("FluxSetup", "Failed to deploy scripts", e)
        }
    }

    private fun runFirstTimeSetup() {
        // Disable bottom nav during setup
        bottomNavigation.menu.findItem(ID_TERMINAL).isEnabled = false
        progressBar.isIndeterminate = true

        executor.execute {
            try {
                updateStatus("A. Preparing Directories...")
                val usrDir = File(filesDir, "usr")
                val tmpDir = File(usrDir, "tmp")
                val etcDir = File(usrDir, "etc")
                val varDir = File(usrDir, "var")
                val homeDir = File(filesDir, "home")
                
                // Create required directories including log/apt and lib/dpkg
                tmpDir.mkdirs()
                etcDir.mkdirs()
                homeDir.mkdirs()
                File(varDir, "log/apt").mkdirs()
                File(varDir, "lib/dpkg").mkdirs()

                updateStatus("B. Extracting Bootstrap Assets...")
                // Copy bootstrap.tar from assets
                val tarFile = File(filesDir, "bootstrap.tar")
                if (!tarFile.exists()) {
                    Log.d("FluxSetup", "Copying bootstrap.tar from assets...")
                    assets.open("bootstrap.tar").use { input ->
                        FileOutputStream(tarFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Extract bootstrap.tar
                Log.d("FluxSetup", "Unpacking bootstrap.tar...")
                val tarProcess = Runtime.getRuntime().exec(
                    arrayOf("tar", "-xf", tarFile.absolutePath, "-C", filesDir.absolutePath)
                )
                tarProcess.waitFor()
                tarFile.delete()

                // Move directory contents from nested tar layout if present
                val nestedFilesDir = File(filesDir, "data/data/com.ivarna.nativecode/files")
                if (nestedFilesDir.exists()) {
                    Log.d("FluxSetup", "Moving nested files out of tar layout...")
                    moveDirectoryContents(nestedFilesDir, filesDir)
                    File(filesDir, "data").deleteRecursively()
                }

                // Re-create critical directory structures
                File(varDir, "log/apt").mkdirs()
                File(varDir, "lib/dpkg").mkdirs()
                tmpDir.mkdirs()

                updateStatus("C. Deploying Custom Scripts...")
                deployScripts()

                // Write resolv.conf
                val resolvConf = File(etcDir, "resolv.conf")
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

                updateStatus("D. Initializing Host Environment...")
                check(runShellCommand(
                    arrayOf(
                        "/data/data/com.ivarna.nativecode/files/usr/bin/proot",
                        "/data/data/com.ivarna.nativecode/files/usr/bin/bash",
                        "/data/data/com.ivarna.nativecode/files/home/setup_termux.sh"
                    )
                ) == 0) { "Host environment setup failed" }

                updateStatus("E. Downloading & Installing Debian Guest...")
                // Dynamically Base64 encode the setup_debian_family.sh asset at runtime
                val debianSetupBytes = assets.open("scripts/setup_debian_family.sh").use { it.readBytes() }
                val debianSetupPayload = Base64.encodeToString(debianSetupBytes, Base64.NO_WRAP)
                check(runShellCommand(
                    arrayOf(
                        "/data/data/com.ivarna.nativecode/files/usr/bin/bash",
                        "/data/data/com.ivarna.nativecode/files/home/flux_install.sh",
                        "debian",
                        debianSetupPayload
                    )
                ) == 0) { "Debian guest setup failed" }

                updateStatus("F. Setting Up GPU Hardware Acceleration...")
                // Copy guest scripts to shared tmp for execution
                val hwAccelScript = File(File(usrDir, "tmp"), "setup_hw_accel_debian.sh")
                assets.open("scripts/setup_hw_accel_debian.sh").use { input ->
                    FileOutputStream(hwAccelScript).use { output ->
                        input.copyTo(output)
                    }
                }
                hwAccelScript.setExecutable(true)

                check(runShellCommand(
                    arrayOf(
                        "/data/data/com.ivarna.nativecode/files/usr/bin/python", "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                        "login", "debian", "--shared-tmp", "--",
                        "env", "FLUX_GPU=virgl", "bash", "/tmp/setup_hw_accel_debian.sh"
                    )
                ) == 0) { "GPU setup failed" }

                updateStatus("G. Setting Up Desktop Customizations...")
                val customScript = File(File(usrDir, "tmp"), "setup_customization_debian.sh")
                assets.open("scripts/setup_customization_debian.sh").use { input ->
                    FileOutputStream(customScript).use { output ->
                        input.copyTo(output)
                    }
                }
                customScript.setExecutable(true)

                check(runShellCommand(
                    arrayOf(
                        "/data/data/com.ivarna.nativecode/files/usr/bin/python", "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                        "login", "debian", "--shared-tmp", "--",
                        "env", "FLUX_THEME=dark", "bash", "/tmp/setup_customization_debian.sh"
                    )
                ) == 0) { "Desktop customization failed" }

                // Complete
                File(filesDir, "setup_complete").createNewFile()
                mainHandler.post {
                    statusText.text = "FluxLinux: Setup Successful!"
                    progressBar.visibility = View.GONE
                    startButton.isEnabled = true
                    stopButton.isEnabled = true
                    bottomNavigation.menu.findItem(ID_TERMINAL).isEnabled = true
                    initTerminalView()
                }
            } catch (e: Exception) {
                Log.e("FluxSetup", "Setup failed", e)
                updateStatus("Error during setup: ${e.message}")
            }
        }
    }

    private fun moveDirectoryContents(source: File, target: File) {
        source.listFiles()?.forEach { file ->
            val dest = File(target, file.name)
            if (file.isDirectory) {
                dest.mkdirs()
                moveDirectoryContents(file, dest)
            } else {
                file.renameTo(dest)
            }
        }
    }

    private fun updateStatus(text: String) {
        mainHandler.post {
            statusText.text = text
            logText.append("\n>>> $text\n")
            logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun runShellCommand(cmd: Array<String>): Int {
        val adjustedCmd = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/")) {
            arrayOf("/system/bin/linker64") + cmd
        } else {
            cmd
        }
        val pb = ProcessBuilder(*adjustedCmd)
        val env = pb.environment()
        env["PATH"] = "/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        env["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib:/data/data/com.ivarna.nativecode/files/usr/opt/virglrenderer-android/lib"
        env["LD_PRELOAD"] = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        env["PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        env["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        env["TMPDIR"] = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["PROOT_TMP_DIR"] = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["TERMUX_APP__PACKAGE_NAME"] = "com.ivarna.nativecode"
        env["TERMUX_X11_APK_PATH"] = applicationInfo.sourceDir
        env["TERMUX_X11_OVERRIDE_PACKAGE"] = "com.ivarna.nativecode"
        env["TERMUX__PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        env["TERMUX__HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        env["SSL_CERT_FILE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        env["CURL_CA_BUNDLE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        
        pb.redirectErrorStream(true)
        val process = pb.start()
        val stream = process.inputStream
        val buffer = ByteArray(1024)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            val output = String(buffer, 0, read)
            mainHandler.post {
                logText.append(output)
                logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
        return process.waitFor()
    }

    private fun startGui() {
        val serviceIntent = Intent(this, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Start the shell script in background (starts X server + XFCE)
        executor.execute {
            updateStatus("Starting XFCE4 GUI session...")
            runShellCommand(
                arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/bash",
                    "/data/data/com.ivarna.nativecode/files/home/start_gui.sh",
                    "debian"
                )
            )
        }

        // Launch X11 display activity immediately so user sees it open
        mainHandler.postDelayed({
            val x11Intent = Intent(this, com.termux.x11.MainActivity::class.java)
            x11Intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(x11Intent)
        }, 500)
    }

    private fun stopGui() {
        // Send ACTION_STOP broadcast so X11 activity closes itself
        val stopBroadcast = Intent("com.termux.x11.ACTION_STOP")
        stopBroadcast.setPackage(packageName)
        sendBroadcast(stopBroadcast)

        stopService(Intent(this, BackgroundService::class.java))

        executor.execute {
            updateStatus("Stopping XFCE4 GUI session...")
            runShellCommand(
                arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/bash",
                    "/data/data/com.ivarna.nativecode/files/home/stop_gui.sh",
                    "debian"
                )
            )
        }
    }

    private fun initTerminalView() {
        terminalView.setTextSize(40)

        val shellPath = "/system/bin/linker64"
        val cwd = File(filesDir, "home").absolutePath
        val args = arrayOf(
            "/system/bin/linker64",
            "/data/data/com.ivarna.nativecode/files/usr/bin/bash",
            "-l"
        )
        
        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        envMap["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        envMap["TERM"] = "xterm-256color"
        envMap["PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib"
        envMap["LD_PRELOAD"] = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        envMap["TERMUX_APP__PACKAGE_NAME"] = "com.ivarna.nativecode"
        envMap["TERMUX__PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["TERMUX__HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        envMap["SSL_CERT_FILE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        envMap["CURL_CA_BUNDLE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        val transcriptRows = 10000

        val viewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: MotionEvent) {
                terminalView.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true
            override fun copyModeChanged(active: Boolean) {}
            override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
            override fun onLongPress(e: MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        }

        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {
                terminalView.onScreenUpdated()
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {
                Log.d("TermuxApp", "Session finished with exit code: ${session.exitStatus}")
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = 1
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: java.lang.Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: java.lang.Exception) { Log.e(tag, "Stacktrace", e) }
        }

        terminalView.setTerminalViewClient(viewClient)
        terminalSession = TerminalSession(
            shellPath,
            cwd,
            args,
            env,
            transcriptRows,
            sessionClient
        )
        terminalView.attachSession(terminalSession)

        // Request keyboard focus
        terminalView.postDelayed({
            terminalView.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, BackgroundService::class.java))
        terminalSession?.finishIfRunning()
    }
}
