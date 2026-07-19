#!/usr/bin/env bash
# setup_cli_tools.sh
# Run inside Debian container during onboarding page 4 to setup Node.js and AI CLI tools.

echo ">>> Starting AI CLI Tools Provisioning..."

# Ensure we have curl / build tools / git / ca-certificates
apt-get update
apt-get install -y curl git ca-certificates python3 build-essential

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

# Install npm-based AI CLI tools
echo ">>> Installing npm AI CLI tools..."
npm install -g opencode-ai @openai/codex @qwen-code/qwen-code --unsafe-perm

# Ensure NVM loader and PATH are in shell startup scripts
for RC in .zshrc .bashrc; do
    [ -f "$HOME/$RC" ] || touch "$HOME/$RC"
    if ! grep -q "NVM_DIR" "$HOME/$RC"; then
        cat >> "$HOME/$RC" << '"'"'NVMBLOCK'"'"'

export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
[ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"
NVMBLOCK
    fi
done
'

# Install curl-based AI CLI tools (agy, claude-code, grok, kiro) as flux user
su - flux -c '

echo ">>> Installing agy (Antigravity)..."
curl -fsSL https://antigravity.google/cli/install.sh | bash || echo "WARN: agy install failed"

echo ">>> Installing claude-code..."
curl -fsSL https://claude.ai/install.sh | bash || echo "WARN: claude install failed"

echo ">>> Installing grok CLI..."
curl -fsSL https://x.ai/cli/install.sh | bash || echo "WARN: grok install failed"

echo ">>> Installing kiro CLI..."
curl -fsSL https://cli.kiro.dev/install | bash || echo "WARN: kiro install failed"

# Add common install dirs to PATH in both shells
for RC in .zshrc .bashrc; do
    [ -f "$HOME/$RC" ] || touch "$HOME/$RC"
    if ! grep -q "# ai-cli-paths" "$HOME/$RC"; then
        cat >> "$HOME/$RC" << '"'"'PATHBLOCK'"'"'

# ai-cli-paths
export PATH="$HOME/.local/bin:$HOME/bin:$HOME/.cargo/bin:$PATH"
PATHBLOCK
    fi
done
'

echo ">>> AI CLI Tools Provisioning Complete!"
