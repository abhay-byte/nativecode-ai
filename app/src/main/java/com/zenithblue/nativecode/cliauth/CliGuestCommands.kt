package com.zenithblue.nativecode.cliauth

/**
 * Pure guest shell snippets for AI CLI auth. No Android UI.
 * Always run as flux with NVM/PATH bootstrap matching setup_cli_tools.sh.
 */
object CliGuestCommands {

    const val AUTH_TIMEOUT_MS = 12 * 60 * 1000L
    const val AUTH_ENV_REL = ".config/fluxlinux/cli-auth.env"

    /** Non-interactive env hints (merged into ProcessBuilder). */
    fun cliEnv(): Map<String, String> = mapOf(
        "NO_COLOR" to "1",
        "CI" to "1",
        "TERM" to "dumb",
        "BROWSER" to "echo",
        "GH_BROWSER" to "echo"
    )

    /**
     * PATH + nvm + managed env so npm-global / curl bins resolve.
     *
     * Quote-safe for both:
     * - proot: single-quoted zsh -c (host must not expand $HOME)
     * - chroot: nested double-quoted su -c (no bare "…" around $vars)
     *
     * Zsh-safe: no bare v* globs (NOMATCH aborts whole zsh -c → false MISSING).
     * Absolute /home/flux paths (flux is fixed guest user).
     */
    fun pathBootstrap(): String = listOf(
        // zsh NOMATCH aborts whole -c script → false MISSING for every tool
        "setopt NULL_GLOB 2>/dev/null || setopt nonomatch 2>/dev/null || true",
        "export HOME=/home/flux",
        "export USER=flux",
        "export PATH=/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo/bin:/opt/nodejs/bin:/usr/local/bin:/usr/bin:/bin:/sbin",
        "export NVM_DIR=/home/flux/.nvm",
        // find: no v* glob. No double-quotes: chroot nests su -c "… -c \"cmd\""
        "if [ -d /home/flux/.nvm/versions/node ]; then " +
            "_n=\$(find /home/flux/.nvm/versions/node -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1); " +
            "case \$_n in /home/flux/.nvm/*) PATH=\$_n/bin:\$PATH;; esac; unset _n; fi",
        // source after NULL_GLOB so cli-tools.env nvm ls globs cannot kill shell
        "[ -f /home/flux/.config/fluxlinux/cli-tools.env ] && . /home/flux/.config/fluxlinux/cli-tools.env 2>/dev/null || true",
        "[ -f /home/flux/.config/fluxlinux/cli-auth.env ] && . /home/flux/.config/fluxlinux/cli-auth.env 2>/dev/null || true"
    ).joinToString("; ")

    fun wrap(body: String): String = "${pathBootstrap()}; $body"

    /**
     * Detect bin via command -v + absolute path probes (curl installers land in ~/.local/bin).
     */
    fun detectBin(bin: String, altBins: List<String> = emptyList()): String {
        val all = (listOf(bin) + altBins).joinToString(" ")
        return wrap(
            """
            for c in $all; do
              if command -v ${'$'}c >/dev/null 2>&1; then
                echo BIN_OK ${'$'}c
                ${'$'}c --version 2>/dev/null | head -1 || ${'$'}c -v 2>/dev/null | head -1 || echo ok
                exit 0
              fi
              for d in /home/flux/.local/bin /home/flux/bin /usr/local/bin /usr/bin; do
                if [ -x ${'$'}d/${'$'}c ]; then
                  echo BIN_OK ${'$'}d/${'$'}c
                  ${'$'}d/${'$'}c --version 2>/dev/null | head -1 || echo ok
                  exit 0
                fi
              done
            done
            echo BIN_MISSING
            """.trimIndent().replace('\n', ' ')
        )
    }

    // ── per-tool status ─────────────────────────────────────────────────────

    fun statusClaude(): String = wrap(
        """
        if [ -f "${'$'}HOME/.claude/.credentials.json" ] && [ -s "${'$'}HOME/.claude/.credentials.json" ]; then
          echo AUTH_OK credentials
          head -c 200 "${'$'}HOME/.claude/.credentials.json" 2>/dev/null | tr '\n' ' '
          echo
        elif [ -n "${'$'}{CLAUDE_CODE_OAUTH_TOKEN:-}" ]; then
          echo AUTH_OK env_token
        else
          echo AUTH_NO
        fi
        """.trimIndent().replace('\n', ' ')
    )

    fun statusCodex(): String = wrap(
        "codex login status 2>&1 || true"
    )

    fun statusQwen(): String = wrap(
        """
        if [ -n "${'$'}{BAILIAN_CODING_PLAN_API_KEY:-}" ] || [ -n "${'$'}{DASHSCOPE_API_KEY:-}" ] || [ -n "${'$'}{OPENAI_API_KEY:-}" ]; then
          echo AUTH_OK env_key
        elif [ -f "${'$'}HOME/.qwen/settings.json" ] && grep -qE 'api[_-]?key|API_KEY|sk-' "${'$'}HOME/.qwen/settings.json" 2>/dev/null; then
          echo AUTH_OK settings
        else
          echo AUTH_NO
        fi
        """.trimIndent().replace('\n', ' ')
    )

    fun statusOpencode(): String = wrap(
        """
        if command -v opencode >/dev/null 2>&1; then
          out=${'$'}(opencode auth list 2>&1 || opencode auth ls 2>&1 || true)
          echo "${'$'}out"
          if [ -f "${'$'}HOME/.local/share/opencode/auth.json" ] && [ -s "${'$'}HOME/.local/share/opencode/auth.json" ]; then
            echo AUTH_OK auth_json
          elif echo "${'$'}out" | grep -qiE 'anthropic|openai|google|copilot|provider|logged|authenticated'; then
            echo AUTH_OK list
          else
            echo AUTH_NO
          fi
        else
          echo AUTH_NO
        fi
        """.trimIndent().replace('\n', ' ')
    )

    fun statusAgy(): String = wrap(
        """
        if [ -d "${'$'}HOME/.gemini/antigravity-cli" ] || [ -d "${'$'}HOME/.config/antigravity" ]; then
          # any non-empty token-ish file under known dirs
          if find "${'$'}HOME/.gemini" "${'$'}HOME/.config/antigravity" -type f \( -name '*token*' -o -name '*cred*' -o -name '*.json' \) 2>/dev/null | head -1 | grep -q .; then
            echo AUTH_OK files
          else
            echo AUTH_NO
          fi
        else
          echo AUTH_NO
        fi
        """.trimIndent().replace('\n', ' ')
    )

    fun statusGrok(): String = wrap(
        """
        if [ -n "${'$'}{XAI_API_KEY:-}" ] || [ -n "${'$'}{GROK_API_KEY:-}" ]; then
          echo AUTH_OK env_key
        elif [ -f "${'$'}HOME/.grok/user-settings.json" ] || [ -f "${'$'}HOME/.grok/config.toml" ]; then
          if grep -qiE 'api[_-]?key|xai-|token' "${'$'}HOME/.grok/user-settings.json" "${'$'}HOME/.grok/config.toml" 2>/dev/null; then
            echo AUTH_OK config
          else
            echo AUTH_MAYBE config_present
          fi
        else
          echo AUTH_NO
        fi
        """.trimIndent().replace('\n', ' ')
    )

    fun statusKiro(): String = wrap(
        """
        if [ -n "${'$'}{KIRO_API_KEY:-}" ]; then
          echo AUTH_OK env_key
        elif command -v kiro-cli >/dev/null 2>&1; then
          kiro-cli login status 2>&1 || kiro-cli whoami 2>&1 || true
          if [ -d "${'$'}HOME/.kiro" ] || [ -d "${'$'}HOME/.config/kiro" ]; then
            echo AUTH_MAYBE home
          else
            echo AUTH_NO
          fi
        elif command -v kiro >/dev/null 2>&1; then
          kiro login status 2>&1 || true
          echo AUTH_MAYBE kiro
        else
          echo AUTH_NO
        fi
        """.trimIndent().replace('\n', ' ')
    )

    // ── login commands ──────────────────────────────────────────────────────

    fun loginCodexDevice(): String = wrap(
        "export BROWSER=echo; codex login --device-auth 2>&1"
    )

    fun loginClaudeSetupToken(): String = wrap(
        "export BROWSER=echo; claude setup-token 2>&1"
    )

    fun loginKiro(): String = wrap(
        """
        export BROWSER=echo
        if command -v kiro-cli >/dev/null 2>&1; then
          kiro-cli login 2>&1
        else
          kiro login 2>&1
        fi
        """.trimIndent().replace('\n', ' ')
    )

    fun loginOpencode(): String = wrap(
        "export BROWSER=echo; opencode auth login 2>&1"
    )

    fun loginAgy(): String = wrap(
        "export BROWSER=echo; agy 2>&1 || true"
    )

    fun loginGrok(): String = wrap(
        "export BROWSER=echo; grok 2>&1 || true"
    )

    // ── logout ──────────────────────────────────────────────────────────────

    fun logoutCodex(): String = wrap("codex logout 2>&1 || true")

    fun logoutOpencode(): String = wrap("opencode auth logout 2>&1 || true")

    fun logoutKiro(): String = wrap(
        "kiro-cli logout 2>&1 || kiro logout 2>&1 || true"
    )

    fun logoutClaudeFiles(): String = wrap(
        "rm -f \"${'$'}HOME/.claude/.credentials.json\" 2>/dev/null; echo CLEARED"
    )
}
