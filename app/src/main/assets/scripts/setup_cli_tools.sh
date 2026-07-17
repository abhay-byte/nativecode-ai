#!/usr/bin/env bash
# setup_cli_tools.sh
# Run inside Debian container during onboarding page 4 to setup Node.js and AI CLI tools.

echo ">>> Starting AI CLI Tools Provisioning..."

# Ensure we have curl / build tools / git / ca-certificates
apt-get update
apt-get install -y curl git ca-certificates python3 build-essential

# Run NVM and Node installation as flux user
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

# Install latest global npm packages: opencode-ai and @openai/codex
echo ">>> Installing global npm packages: opencode-ai & @openai/codex for flux user..."
npm install -g opencode-ai @openai/codex --unsafe-perm

# Ensure NVM loader is added to shell startup scripts
for RC in .zshrc .bashrc; do
    [ -f "$HOME/$RC" ] || touch "$HOME/$RC"
    if ! grep -q "NVM_DIR" "$HOME/$RC"; then
        echo "" >> "$HOME/$RC"
        echo "export NVM_DIR=\"\$HOME/.nvm\"" >> "$HOME/$RC"
        echo "[ -s \"\$NVM_DIR/nvm.sh\" ] && \\. \"\$NVM_DIR/nvm.sh\"" >> "$HOME/$RC"
        echo "[ -s \"\$NVM_DIR/bash_completion\" ] && \\. \"\$NVM_DIR/bash_completion\"" >> "$HOME/$RC"
    fi
done
'

echo ">>> AI CLI Tools Provisioning Complete!"
