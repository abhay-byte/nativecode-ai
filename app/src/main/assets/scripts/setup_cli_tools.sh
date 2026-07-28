#!/usr/bin/env bash
# setup_cli_tools.sh
# Run inside Debian (proot/chroot) during onboarding — Node.js + AI CLIs for user flux.
# Idempotent: skip tools already on PATH; re-wire shell PATH every run.

set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

FLUX_USER="${FLUX_USER:-flux}"
NODE_MAJOR="${NODE_MAJOR:-26}"
NVM_VERSION="${NVM_VERSION:-v0.40.5}"
MARKER="flux-cli-tools"

if [ "$(id -u)" != "0" ]; then
    echo "ERROR: run as root inside guest (proot/chroot)."
    exit 1
fi

if ! id "$FLUX_USER" &>/dev/null; then
    echo "ERROR: user '$FLUX_USER' missing — run setup_debian_family first."
    exit 1
fi

FLUX_HOME=$(getent passwd "$FLUX_USER" | cut -d: -f6)
if [ -z "$FLUX_HOME" ] || [ ! -d "$FLUX_HOME" ]; then
    echo "ERROR: home for $FLUX_USER not found."
    exit 1
fi

echo ">>> AI CLI tools provisioning (user=$FLUX_USER home=$FLUX_HOME)"

# ── helpers ──────────────────────────────────────────────────────────────────

pkg_ok() {
    dpkg -s "$1" >/dev/null 2>&1
}

ensure_pkgs() {
    local need=() p
    for p in "$@"; do
        pkg_ok "$p" || need+=("$p")
    done
    if [ "${#need[@]}" -eq 0 ]; then
        echo ">>> apt packages already present"
        return 0
    fi
    echo ">>> apt install: ${need[*]}"
    apt-get update -qq
    apt-get install -y --no-install-recommends "${need[@]}"
}

# Run as flux with login env; args are bash -c script body
as_flux() {
    su -s /bin/bash - "$FLUX_USER" -c "$1"
}

# true if command exists for flux (with common bins + nvm default node)
flux_has() {
    local cmd="$1"
    as_flux "
        export PATH=\"\$HOME/.local/bin:\$HOME/bin:\$HOME/.cargo/bin:\$PATH\"
        export NVM_DIR=\"\$HOME/.nvm\"
        if [ -s \"\$NVM_DIR/nvm.sh\" ]; then
            . \"\$NVM_DIR/nvm.sh\" >/dev/null 2>&1 || true
        fi
        # nvm may not load in non-interactive; probe default node bin
        if [ -d \"\$NVM_DIR/versions/node\" ]; then
            _n=\$(ls -1d \"\$NVM_DIR/versions/node\"/v* 2>/dev/null | sort -V | tail -1)
            [ -n \"\$_n\" ] && PATH=\"\$_n/bin:\$PATH\"
        fi
        command -v $cmd >/dev/null 2>&1
    "
}

record_ok()   { OK_LIST+=("$1"); }
record_skip() { SKIP_LIST+=("$1"); }
record_fail() { FAIL_LIST+=("$1"); }

OK_LIST=()
SKIP_LIST=()
FAIL_LIST=()

# ── system deps ──────────────────────────────────────────────────────────────

ensure_pkgs curl wget git ca-certificates python3 build-essential unzip ca-certificates

echo ">>> musl compat (opencode-ai / musl binaries)..."
if ! pkg_ok musl; then
    apt-get install -y --no-install-recommends musl 2>/dev/null || \
        echo "WARN: musl package unavailable"
fi
if [ ! -e /lib/ld-musl-aarch64.so.1 ]; then
    MUSL_LIB=$(find /usr/lib /lib -name 'ld-musl-aarch64*' -o -name 'libc.musl-aarch64*' 2>/dev/null | head -1 || true)
    if [ -n "${MUSL_LIB:-}" ]; then
        ln -sf "$MUSL_LIB" /lib/ld-musl-aarch64.so.1
        echo ">>> musl loader: /lib/ld-musl-aarch64.so.1 -> $MUSL_LIB"
    else
        echo "WARN: ld-musl-aarch64 not found (non-arm64 or musl missing)"
    fi
else
    echo ">>> musl loader already present"
fi

# ── PATH / NVM env for shells (zshenv critical for non-interactive zsh -c) ───
# zsh order: .zshenv (always) → .zprofile (login) → .zshrc (interactive)
# bash: .bashrc / .profile

ENV_DIR="$FLUX_HOME/.config/fluxlinux"
ENV_FILE="$ENV_DIR/cli-tools.env"
mkdir -p "$ENV_DIR"

cat > "$ENV_FILE" << 'ENVEOF'
# Managed by setup_cli_tools.sh — do not edit by hand
# AI CLI + NVM PATH for flux

export PATH="$HOME/.local/bin:$HOME/bin:$HOME/.cargo/bin:$PATH"

export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"

# Fast path: put default Node bin on PATH without loading full nvm (non-interactive)
if [ -d "$NVM_DIR/versions/node" ]; then
    _flux_node=$(ls -1d "$NVM_DIR/versions/node"/v* 2>/dev/null | sort -V | tail -1)
    if [ -n "$_flux_node" ] && [ -d "$_flux_node/bin" ]; then
        export PATH="$_flux_node/bin:$PATH"
    fi
    unset _flux_node
fi

# Full nvm (npm, nvm use, etc.) — safe if missing
if [ -s "$NVM_DIR/nvm.sh" ]; then
    # shellcheck disable=SC1091
    . "$NVM_DIR/nvm.sh"
fi
if [ -s "$NVM_DIR/bash_completion" ] && [ -n "${BASH_VERSION:-}" ]; then
    # shellcheck disable=SC1091
    . "$NVM_DIR/bash_completion"
fi
ENVEOF
chown -R "$FLUX_USER:$FLUX_USER" "$ENV_DIR" 2>/dev/null || chown -R "$FLUX_USER" "$ENV_DIR" 2>/dev/null || true
chmod 644 "$ENV_FILE"

# Idempotent source line for shell rc files
ensure_source_line() {
    local rc="$1"
    local label="$2"
    touch "$rc"
    chown "$FLUX_USER:$FLUX_USER" "$rc" 2>/dev/null || chown "$FLUX_USER" "$rc" 2>/dev/null || true
    if grep -qF "# $MARKER" "$rc" 2>/dev/null; then
        # Refresh block in place (rewrite section)
        local tmp
        tmp=$(mktemp)
        # drop old block between markers
        awk -v m="# $MARKER" '
            $0 == m {skip=1; next}
            $0 == m"-end" {skip=0; next}
            !skip {print}
        ' "$rc" > "$tmp" || cp "$rc" "$tmp"
        mv "$tmp" "$rc"
    fi
    cat >> "$rc" << RCEOF

# $MARKER
# $label — AI CLI tools PATH + NVM
if [ -f "\$HOME/.config/fluxlinux/cli-tools.env" ]; then
    . "\$HOME/.config/fluxlinux/cli-tools.env"
fi
# $MARKER-end
RCEOF
    chown "$FLUX_USER:$FLUX_USER" "$rc" 2>/dev/null || chown "$FLUX_USER" "$rc" 2>/dev/null || true
}

echo ">>> wiring PATH into zsh/bash startup files..."
# .zshenv: always loaded (interactive + non-interactive zsh -c)
ensure_source_line "$FLUX_HOME/.zshenv" "zshenv"
# .zprofile: login zsh (su - flux)
ensure_source_line "$FLUX_HOME/.zprofile" "zprofile"
# .zshrc: interactive zsh (after oh-my-zsh may reshuffle; re-apply PATH at end)
ensure_source_line "$FLUX_HOME/.zshrc" "zshrc"
# bash fallbacks
ensure_source_line "$FLUX_HOME/.bashrc" "bashrc"
ensure_source_line "$FLUX_HOME/.profile" "profile"

# System profile.d so any login gets local bins early (optional safety)
cat > /etc/profile.d/flux-cli-tools.sh << 'PROFILE'
# flux AI CLI path hints (user home resolved at runtime)
if [ -n "${HOME:-}" ] && [ -f "$HOME/.config/fluxlinux/cli-tools.env" ]; then
    # shellcheck disable=SC1091
    . "$HOME/.config/fluxlinux/cli-tools.env"
fi
PROFILE
chmod 644 /etc/profile.d/flux-cli-tools.sh

# ── NVM + Node (flux) ────────────────────────────────────────────────────────

install_nvm_node() {
    as_flux "
        set -e
        export NVM_DIR=\"\$HOME/.nvm\"
        if [ ! -s \"\$NVM_DIR/nvm.sh\" ]; then
            echo '>>> Installing NVM $NVM_VERSION...'
            curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/${NVM_VERSION}/install.sh | bash
        else
            echo '>>> NVM already installed'
        fi
        . \"\$NVM_DIR/nvm.sh\"
        # Skip download if node major already present and works
        if command -v node >/dev/null 2>&1 && node -v 2>/dev/null | grep -qE \"^v${NODE_MAJOR}\\.\"; then
            echo \">>> Node \$(node -v) already active — skip nvm install\"
            nvm alias default ${NODE_MAJOR} >/dev/null 2>&1 || true
        else
            echo \">>> Installing Node.js ${NODE_MAJOR}...\"
            nvm install ${NODE_MAJOR}
            nvm alias default ${NODE_MAJOR}
        fi
        nvm use default >/dev/null 2>&1 || nvm use ${NODE_MAJOR}
        echo \">>> Node \$(node -v) / npm \$(npm -v)\"
    "
}

if install_nvm_node; then
    record_ok "node@${NODE_MAJOR}"
else
    record_fail "node@${NODE_MAJOR}"
    echo "ERROR: Node/NVM install failed — npm tools will be skipped"
fi

# ── npm global tools ─────────────────────────────────────────────────────────

npm_global_install() {
    local pkg="$1"
    local bin="$2"
    if flux_has "$bin"; then
        echo ">>> skip $pkg ($bin already on PATH)"
        record_skip "$bin"
        return 0
    fi
    echo ">>> npm install -g $pkg..."
    if as_flux "
        export NVM_DIR=\"\$HOME/.nvm\"
        . \"\$NVM_DIR/nvm.sh\"
        npm install -g '$pkg' --unsafe-perm 2>&1 || \
            npm install -g '$pkg' --unsafe-perm --ignore-scripts 2>&1
    "; then
        if flux_has "$bin"; then
            record_ok "$bin"
        else
            echo "WARN: $pkg installed but '$bin' not found on PATH"
            record_fail "$bin"
        fi
    else
        echo "WARN: npm install failed: $pkg"
        record_fail "$bin"
    fi
}

# Only if node ok
if flux_has node; then
    npm_global_install "opencode-ai" "opencode"
    npm_global_install "@openai/codex" "codex"
    npm_global_install "@qwen-code/qwen-code" "qwen"
else
    record_fail "opencode"
    record_fail "codex"
    record_fail "qwen"
fi

# Official native opencode installer fallback if npm left no binary
if ! flux_has opencode; then
    echo ">>> fallback: opencode.ai install script..."
    if as_flux "curl -fsSL https://opencode.ai/install | bash 2>&1"; then
        flux_has opencode && record_ok "opencode" || record_fail "opencode"
    else
        record_fail "opencode"
    fi
fi

# ── curl installers ──────────────────────────────────────────────────────────

curl_install() {
    local name="$1"
    local bin="$2"
    local url="$3"
    if flux_has "$bin"; then
        echo ">>> skip $name ($bin already on PATH)"
        record_skip "$bin"
        return 0
    fi
    echo ">>> Installing $name..."
    if as_flux "curl -fsSL '$url' | bash 2>&1"; then
        if flux_has "$bin"; then
            record_ok "$bin"
        else
            echo "WARN: $name install finished but '$bin' not on PATH"
            record_fail "$bin"
        fi
    else
        echo "WARN: $name install failed"
        record_fail "$bin"
    fi
}

curl_install "agy (Antigravity)" "agy" "https://antigravity.google/cli/install.sh"
curl_install "claude-code" "claude" "https://claude.ai/install.sh"
curl_install "grok CLI" "grok" "https://x.ai/cli/install.sh"
# MainActivity launches kiro-cli
curl_install "kiro CLI" "kiro-cli" "https://cli.kiro.dev/install"
# some installers ship as `kiro` only
if ! flux_has kiro-cli && flux_has kiro; then
    as_flux 'mkdir -p "$HOME/.local/bin"; ln -sfn "$(command -v kiro)" "$HOME/.local/bin/kiro-cli" 2>/dev/null || true'
    flux_has kiro-cli && record_ok "kiro-cli" || true
fi

# ── re-apply PATH block (installers may rewrite .zshrc) ──────────────────────

echo ">>> re-wire shell PATH after installers..."
ensure_source_line "$FLUX_HOME/.zshenv" "zshenv"
ensure_source_line "$FLUX_HOME/.zprofile" "zprofile"
ensure_source_line "$FLUX_HOME/.zshrc" "zshrc"
ensure_source_line "$FLUX_HOME/.bashrc" "bashrc"
ensure_source_line "$FLUX_HOME/.profile" "profile"

# ── verify summary ───────────────────────────────────────────────────────────

echo ""
echo ">>> Verification (as $FLUX_USER):"
as_flux '
    export PATH="$HOME/.local/bin:$HOME/bin:$HOME/.cargo/bin:$PATH"
    export NVM_DIR="$HOME/.nvm"
    [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh" >/dev/null 2>&1 || true
    if [ -d "$NVM_DIR/versions/node" ]; then
        _n=$(ls -1d "$NVM_DIR/versions/node"/v* 2>/dev/null | sort -V | tail -1)
        [ -n "$_n" ] && PATH="$_n/bin:$PATH"
    fi
    for c in node npm opencode codex qwen agy claude grok kiro-cli kiro; do
        if command -v "$c" >/dev/null 2>&1; then
            ver=$("$c" --version 2>/dev/null | head -1 || "$c" -v 2>/dev/null | head -1 || echo ok)
            printf "  OK  %-12s %s\n" "$c" "$ver"
        else
            printf "  --  %-12s not found\n" "$c"
        fi
    done
    echo "  PATH head: $(echo "$PATH" | tr ":" "\n" | head -6 | tr "\n" " ")"
'

echo ""
_ok="${OK_LIST[*]-}"
_sk="${SKIP_LIST[*]-}"
_fl="${FAIL_LIST[*]-}"
echo ">>> Summary: ok=[${_ok}] skip=[${_sk}] fail=[${_fl}]"
echo ">>> Shell: source $ENV_FILE via .zshenv / .zprofile / .zshrc / .bashrc"
echo ">>> AI CLI Tools Provisioning Complete!"

# Non-zero if critical node missing
if ! flux_has node; then
    exit 1
fi
exit 0
