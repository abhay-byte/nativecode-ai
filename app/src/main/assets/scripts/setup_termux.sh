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

# Force clear any deadlocks from background updates
echo "FluxLinux: Clearing potential locks..."
pkill -9 apt || true
pkill -9 apt-get || true
pkill -9 dpkg || true
rm -rf "$PREFIX/var/lib/dpkg/lock"
rm -rf "$PREFIX/var/lib/dpkg/lock-frontend"
rm -rf "$PREFIX/var/cache/apt/archives/lock"

# Repair any interrupted installations
echo "FluxLinux: Repairing package database..."
dpkg --configure -a || true

# 1. Update Packages
echo "FluxLinux: Updating packages..."
apt-get update -y

# 2. Install Core Dependencies
echo "FluxLinux: Installing core dependencies..."
apt-get install -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" --allow-change-held-packages -y proot-distro x11-repo pulseaudio wget zsh fastfetch git unzip util-linux

# 3. Install Termux:X11
echo "FluxLinux: Installing Termux:X11..."
apt-get install -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" --allow-change-held-packages -y termux-x11-nightly

# 4. Install Hardware Acceleration Tools
echo "FluxLinux: Installing Hardware Acceleration tools..."
# Enable TUR repo for advanced packages
apt-get install -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" --allow-change-held-packages -y tur-repo
apt-get update -y
# Install VirGL server and Zink
apt-get install -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" --allow-change-held-packages -y virglrenderer-android mesa-zink

# 5. Install Mali Vulkan Wrapper
ARCH=$(dpkg --print-architecture)
if [ "$ARCH" = "aarch64" ]; then
    echo "FluxLinux: Installing Vulkan Wrapper for Mali (aarch64)..."
    WRAPPER_URL="https://github.com/sabamdarif/termux-desktop/releases/download/pipetto-crypto-vulkan-wrapper-android/pipetto-crypto-vulkan-wrapper-android_25.0.0-1_aarch64.deb"
    
    mkdir -p "$PREFIX/tmp"
    echo "Downloading wrapper..."
    curl -L -o "$PREFIX/tmp/vulkan-wrapper.deb" "$WRAPPER_URL"
    echo "Installing wrapper..."
    dpkg -i "$PREFIX/tmp/vulkan-wrapper.deb" || apt-get install -f -y
    rm "$PREFIX/tmp/vulkan-wrapper.deb"
else
    echo "FluxLinux: Skipping Vulkan Wrapper (Architecture $ARCH not supported)"
fi

# 6. Patch com.termux prefix to com.ivarna.nativecode
echo "FluxLinux: Patching downloaded packages to com.ivarna.nativecode prefix..."

# Patch ELF binaries (RUNPATH) - only scan bin and lib root (maxdepth 1) to avoid pyc/perl scanning
find "$PREFIX/bin" -type f | while read -r filepath; do
    if file "$filepath" 2>/dev/null | grep -q "ELF"; then
        old_rpath=$(readelf -d "$filepath" 2>/dev/null | grep RUNPATH | grep -o '\[.*\]' | tr -d '[]')
        if [ -n "$old_rpath" ] && echo "$old_rpath" | grep -q "com.termux"; then
            new_rpath=$(echo "$old_rpath" | sed "s|com.termux|com.ivarna.nativecode|g")
            patchelf --set-rpath "$new_rpath" "$filepath" 2>/dev/null || true
        fi
    fi
done

find "$PREFIX/lib" -maxdepth 1 -type f | while read -r filepath; do
    if file "$filepath" 2>/dev/null | grep -q "ELF"; then
        old_rpath=$(readelf -d "$filepath" 2>/dev/null | grep RUNPATH | grep -o '\[.*\]' | tr -d '[]')
        if [ -n "$old_rpath" ] && echo "$old_rpath" | grep -q "com.termux"; then
            new_rpath=$(echo "$old_rpath" | sed "s|com.termux|com.ivarna.nativecode|g")
            patchelf --set-rpath "$new_rpath" "$filepath" 2>/dev/null || true
        fi
    fi
done

# Patch Text files (scripts and configs) - avoid heavy folders
find "$PREFIX/bin" "$PREFIX/etc" "$PREFIX/libexec" -maxdepth 2 -type f 2>/dev/null | while read -r filepath; do
    if [ ! -L "$filepath" ] && ! file "$filepath" 2>/dev/null | grep -q "ELF"; then
        if grep -q "com.termux" "$filepath" 2>/dev/null; then
            sed -i 's|com.termux|com.ivarna.nativecode|g' "$filepath" 2>/dev/null || true
        fi
    fi
done

echo "FluxLinux: Setup Complete"
echo ""

# Create marker file to track initialization
touch "$MARKER_FILE"
