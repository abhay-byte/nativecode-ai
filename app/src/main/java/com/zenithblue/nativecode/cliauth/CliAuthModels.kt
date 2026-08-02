package com.zenithblue.nativecode.cliauth

/** Auth status for one AI CLI tool in one isolation method. */
data class CliToolStatus(
    val toolId: String,
    val method: String,
    val installed: Boolean,
    val loggedIn: Boolean,
    val accountLabel: String? = null,
    val detail: String? = null,
    val raw: String = "",
    val error: String? = null
)

enum class CliAuthPhase {
    IDLE,
    CHECK_BIN,
    LOGIN,
    WAIT_BROWSER,
    CAPTURE_TOKEN,
    VERIFY,
    SUCCESS,
    FAILED,
    CANCELLED
}

interface CliAuthListener {
    fun onPhase(phase: CliAuthPhase, message: String)
    fun onLog(line: String)
    fun onOtp(code: String)
    fun onUrl(url: String)
    fun onDone(status: CliToolStatus)
    fun onFailed(message: String)
    fun onCancelled()
    /** UI should open a guided terminal session for this tool type. */
    fun onTerminalGuided(toolId: String, commandHint: String) {}
}
