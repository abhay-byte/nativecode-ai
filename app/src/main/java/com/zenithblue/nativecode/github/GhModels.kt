package com.zenithblue.nativecode.github

/** Auth status for one isolation method (proot or chroot). */
data class GhAuthStatus(
    val method: String,
    val ghInstalled: Boolean,
    val loggedIn: Boolean,
    val username: String? = null,
    val raw: String = "",
    val error: String? = null
)

data class GhRepo(
    val nameWithOwner: String,
    val url: String,
    val isPrivate: Boolean,
    val description: String? = null
)

enum class GhAuthPhase {
    IDLE,
    CHECK_GH,
    INSTALL_GH,
    CHECK_AUTH,
    LOGIN,
    WAIT_BROWSER,
    VERIFY,
    SUCCESS,
    FAILED,
    CANCELLED
}

/** Callbacks for a full connect session (install → login → verify). */
interface GhAuthListener {
    fun onPhase(phase: GhAuthPhase, message: String)
    fun onLog(line: String)
    fun onOtp(code: String)
    fun onDone(status: GhAuthStatus)
    fun onFailed(message: String)
    fun onCancelled()
}
