#!/bin/bash
# termux_tweaks_adapted.sh
# Enhance Termux with Oh My Zsh, themes, fonts, and fastfetch non-interactively

echo "🎨 FluxLinux: Enhancing Termux Environment..."

install_ohmyzsh() {
    echo "🐚 Installing Oh My Zsh..."
    RUNZSH=no CHSH=no sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"
    export ZSH_CUSTOM="${ZSH_CUSTOM:-$HOME/.oh-my-zsh/custom}"

    echo "🔌 Installing Zsh plugins..."
    rm -rf "$ZSH_CUSTOM/plugins/zsh-autosuggestions"
    rm -rf "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting"
    git clone https://github.com/zsh-users/zsh-autosuggestions "$ZSH_CUSTOM/plugins/zsh-autosuggestions"
    git clone https://github.com/zsh-users/zsh-syntax-highlighting "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting"

    echo "⚙️ Configuring .zshrc..."
    if grep -q "plugins=" "$HOME/.zshrc" 2>/dev/null; then
        sed -i 's/plugins=(.*)/plugins=(git zsh-autosuggestions zsh-syntax-highlighting)/' "$HOME/.zshrc"
    else
        echo 'plugins=(git zsh-autosuggestions zsh-syntax-highlighting)' >> "$HOME/.zshrc"
    fi
    sed -i 's/^ZSH_THEME=.*$/ZSH_THEME="random"/' "$HOME/.zshrc"
    echo "✅ Zsh configuration complete."
}

set_termux_colors() {
    echo "🎨 Applying GitHub Dark color scheme (default)..."
    mkdir -p ~/.termux
    cat > ~/.termux/colors.properties << 'EOF'
foreground=#c9d1d9
background=#0d1117
cursor=#c9d1d9
color0=#484f58
color1=#ff7b72
color2=#3fb950
color3=#d29922
color4=#58a6ff
color5=#bc8cff
color6=#39c5cf
color7=#b1bac4
color8=#6e7681
color9=#ffa198
color10=#56d364
color11=#e3b341
color12=#79c0ff
color13=#d2a8ff
color14=#56d4dd
color15=#f0f6fc
EOF
    echo "✅ Color scheme applied."
}

install_nerd_font() {
    echo "🔤 Installing JetBrains Mono Nerd Font (offline)..."
    mkdir -p ~/.termux
    if [ -f ~/.termux/font.ttf ]; then
        echo "✅ Font already deployed."
    else
        echo "⚠️ Font file not found."
    fi
    termux-reload-settings || true
}

configure_fastfetch() {
    echo > "$PREFIX/etc/motd" || true
    rm -f "$PREFIX/etc/motd"

    echo "⚡ Configuring fastfetch..."
    mkdir -p ~/.local/share/fastfetch/presets
    curl -fsSL https://raw.githubusercontent.com/abhay-byte/Linux_Setup/dev/config/termux.jsonc \
        -o ~/.local/share/fastfetch/presets/termux.jsonc

    RCFILE="$HOME/.zshrc"
    [ -f "$RCFILE" ] || touch "$RCFILE"

    # Add fastfetch at top if missing
    if ! grep -q 'fastfetch --config termux' "$RCFILE"; then
        sed -i '1ifastfetch --config termux' "$RCFILE"
    fi

    echo "✅ Fastfetch configured."
    echo "🐚 Setting Zsh as default shell..."
    chsh -s zsh || true
}

# Main execution
install_ohmyzsh
set_termux_colors
install_nerd_font
configure_fastfetch

echo ""
echo "🎉 FluxLinux: Termux enhancement complete!"

# Create marker file to track tweaks application
mkdir -p "$HOME/.fluxlinux"
touch "$HOME/.fluxlinux/termux_tweaks.done"
