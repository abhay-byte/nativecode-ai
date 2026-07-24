#!/usr/bin/env bash
# setup_cli_tools.sh
# Run inside Debian container during onboarding page 4 to setup Node.js and AI CLI tools.

echo ">>> Starting AI CLI Tools Provisioning..."

# Ensure we have curl / wget / build tools / git / ca-certificates / unzip
apt-get update
apt-get install -y curl wget git ca-certificates python3 build-essential unzip

# Install musl compatibility layer so musl-linked binaries (e.g. opencode-ai) work on glibc Debian
echo ">>> Installing musl compatibility..."
apt-get install -y musl 2>/dev/null || true
# Create ld-musl loader symlink for arm64 if not already present
if [ ! -f /lib/ld-musl-aarch64.so.1 ]; then
    MUSL_LIB=$(find /usr/lib /lib -name "ld-musl-aarch64*" -o -name "libc.musl-aarch64*" 2>/dev/null | head -1)
    if [ -n "$MUSL_LIB" ]; then
        ln -sf "$MUSL_LIB" /lib/ld-musl-aarch64.so.1 2>/dev/null || true
        echo ">>> musl symlink created: /lib/ld-musl-aarch64.so.1 -> $MUSL_LIB"
    else
        echo "WARN: musl library not found, opencode-ai may not run"
    fi
fi

# Run NVM, Node, and npm-based tools as flux user
su - flux -c '
export NVM_DIR="$HOME/.nvm"
if [ ! -d "$NVM_DIR" ]; then
    echo ">>> Installing NVM v0.40.5 for flux user..."
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.5/install.sh | bash
fi

# Load NVM
\. "$NVM_DIR/nvm.sh"

# Download and install Node.js 26
echo ">>> Installing Node.js v26 for flux user..."
nvm install 26
nvm use 26
nvm alias default 26

# Verify Node and NPM
echo ">>> Node version: $(node -v)"
echo ">>> NPM version: $(npm -v)"

# Install opencode-ai separately — needs musl compat on Debian glibc
echo ">>> Installing opencode-ai..."
npm install -g opencode-ai --unsafe-perm 2>&1 || \
    npm install -g opencode-ai --unsafe-perm --ignore-scripts 2>&1 || \
    echo "WARN: opencode-ai install failed"

echo ">>> Installing @openai/codex and @qwen-code/qwen-code..."
npm install -g @openai/codex @qwen-code/qwen-code --unsafe-perm 2>&1 || \
    echo "WARN: codex/qwen-code install failed"

# Ensure NVM loader and PATH are in shell startup scripts
for RC in .zshrc .bashrc; do
    [ -f "$HOME/$RC" ] || touch "$HOME/$RC"
    if ! grep -q "NVM_DIR" "$HOME/$RC"; then
        cat >> "$HOME/$RC" << NVMBLOCK

export NVM_DIR="\$HOME/.nvm"
[ -s "\$NVM_DIR/nvm.sh" ] && \. "\$NVM_DIR/nvm.sh"
[ -s "\$NVM_DIR/bash_completion" ] && \. "\$NVM_DIR/bash_completion"
NVMBLOCK
    fi
done
'

# Install curl-based AI CLI tools (agy, claude-code, grok, kiro) as flux user
su - flux -c '

echo ">>> Installing agy (Antigravity)..."
curl -fsSL https://antigravity.google/cli/install.sh | bash 2>&1 || echo "WARN: agy install failed"

echo ">>> Installing claude-code..."
curl -fsSL https://claude.ai/install.sh | bash 2>&1 || echo "WARN: claude install failed"

echo ">>> Installing grok CLI..."
curl -fsSL https://x.ai/cli/install.sh | bash 2>&1 || echo "WARN: grok install failed"

echo ">>> Installing kiro CLI..."
curl -fsSL https://cli.kiro.dev/install | bash 2>&1 || echo "WARN: kiro install failed"

# Add common install dirs to PATH in both shells
for RC in .zshrc .bashrc; do
    [ -f "$HOME/$RC" ] || touch "$HOME/$RC"
    if ! grep -q "# ai-cli-paths" "$HOME/$RC"; then
        cat >> "$HOME/$RC" << PATHBLOCK

# ai-cli-paths
export PATH="\$HOME/.local/bin:\$HOME/bin:\$HOME/.cargo/bin:\$PATH"
PATHBLOCK
    fi
done
'

echo ">>> AI CLI Tools Provisioning Complete!"
