package com.ivarna.nativecode.github

/**
 * Pure guest shell snippets for `gh`. No Android / UI.
 * Auth + list always run as flux; install runs as root via PackageInstallRunner.
 */
object GhGuestCommands {

    const val HOST = "github.com"
    const val DEVICE_URL = "https://github.com/login/device"
    const val AUTH_TIMEOUT_MS = 10 * 60 * 1000L
    const val REPO_LIMIT = 100

    /** Non-interactive env for guest `gh` (merged into ProcessBuilder env). */
    fun ghEnv(): Map<String, String> = mapOf(
        "GH_PROMPT_DISABLED" to "1",
        "GH_SPINNER_DISABLED" to "1",
        "NO_COLOR" to "1",
        "BROWSER" to "true",
        "GH_BROWSER" to "true"
    )

    fun detectGh(): String =
        "if command -v gh >/dev/null 2>&1; then echo GH_OK; gh --version 2>&1 | head -1; else echo GH_MISSING; fi"

    fun installPrimary(): String =
        "export DEBIAN_FRONTEND=noninteractive; " +
            "apt-get update -y && apt-get install -y gh"

    /** Official GitHub CLI apt source then install (arm64 Debian). */
    fun installFallback(): String =
        "export DEBIAN_FRONTEND=noninteractive; " +
            "set -e; " +
            "apt-get update -y; " +
            "apt-get install -y curl ca-certificates gnupg; " +
            "mkdir -p -m 755 /etc/apt/keyrings; " +
            "curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg " +
            "-o /etc/apt/keyrings/githubcli-archive-keyring.gpg; " +
            "chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg; " +
            "echo 'deb [arch=arm64 signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] " +
            "https://cli.github.com/packages stable main' " +
            "> /etc/apt/sources.list.d/github-cli.list; " +
            "apt-get update -y && apt-get install -y gh"

    fun authStatusJson(): String =
        "gh auth status --hostname $HOST --json hosts 2>&1 || true"

    /** Human status (works on older gh without --json). */
    fun authStatusHuman(): String =
        "gh auth status --hostname $HOST 2>&1 || true"

    /**
     * Guest Debian `gh` often cannot keep HTTPS to github.com while app is backgrounded
     * (browser open). Prefer Android device-flow + write hosts.yml / --with-token.
     * Older gh: no `--clipboard`.
     */
    fun authLoginWeb(): String =
        "export GH_PROMPT_DISABLED=1 GH_SPINNER_DISABLED=1 NO_COLOR=1 BROWSER=true GH_BROWSER=true; " +
            "gh auth login " +
            "--hostname $HOST " +
            "--git-protocol https " +
            "--web " +
            "--insecure-storage " +
            "2>&1"

    /**
     * Read token from a guest path file (avoids shell pipe + quoting through proot zsh -c).
     * File should contain only the token, one line.
     */
    fun authLoginWithTokenFile(guestTokenPath: String): String {
        val p = guestTokenPath.replace("'", "'\\''")
        return "export GH_PROMPT_DISABLED=1 NO_COLOR=1; " +
            "gh auth login " +
            "--hostname $HOST " +
            "--git-protocol https " +
            "--with-token " +
            "--insecure-storage " +
            "< '$p' 2>&1; " +
            "ec=\$?; rm -f '$p'; exit \$ec"
    }

    fun authSetupGit(): String =
        "gh auth setup-git --hostname $HOST 2>&1 || true"

    fun authLogout(username: String): String {
        val u = username.replace("'", "'\\''")
        return "gh auth logout --hostname $HOST --user '$u' 2>&1"
    }

    fun repoList(limit: Int = REPO_LIMIT): String =
        "gh repo list --limit $limit " +
            "--json nameWithOwner,url,isPrivate,description,updatedAt 2>&1"

    fun whoami(): String =
        "gh api user -q .login 2>&1"
}
