#!/bin/bash
# setup_termux_adapted.sh
# Core initialization script for com.example.termuxapp
# Installs necessary dependencies in Termux

MARKER_FILE="$HOME/.fluxlinux/setup_termux.done"
mkdir -p "$HOME/.fluxlinux"

if [ -f "$MARKER_FILE" ]; then
    echo "FluxLinux: Termux Setup already completed. Skipping."
    exit 0
fi

# Trap errors
set -e
trap 'echo "FluxLinux: Setup Failed!"' ERR

echo "FluxLinux: Initializing Termux Environment..."

required_bins="proot-distro pulseaudio"
for bin in $required_bins; do
    command -v "$bin" >/dev/null || {
        echo "FluxLinux: Missing bundled host dependency: $bin"
        exit 1
    }
done

test -f "$PREFIX/libexec/termux-x11/loader.apk" || {
    echo "FluxLinux: Missing bundled Termux:X11 loader"
    exit 1
}

echo "FluxLinux: Setup Complete"
echo ""

# Create marker file to track initialization
touch "$MARKER_FILE"
