#!/data/data/com.ivarna.nativecode/files/usr/bin/bash
# stop_gui_adapted.sh - Stop XFCE4 Desktop Environment in PRoot Distro for com.ivarna.nativecode

DISTRO=${1:-debian}

# Set critical environment variables for proot-distro
export TERMUX_APP__PACKAGE_NAME="com.ivarna.nativecode"
export TERMUX__PREFIX="/data/data/com.ivarna.nativecode/files/usr"
export TERMUX__HOME="/data/data/com.ivarna.nativecode/files/home"
export HOME="/data/data/com.ivarna.nativecode/files/home"
export PROOT_TMP_DIR="/data/data/com.ivarna.nativecode/files/usr/tmp"

echo "========================================"
echo "FluxLinux: Stopping GUI for $DISTRO"
echo "========================================"

# Step 1: Kill XFCE processes inside proot
echo "[1/3] Stopping XFCE4 processes..."
if [ "$DISTRO" == "termux" ]; then
    # Termux native
    killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon 2>/dev/null
else
    # Inside proot
    python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login $DISTRO -- bash -c 'killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon' 2>/dev/null
fi

# Step 2: Stop Termux X11
echo "[2/3] Stopping Termux X11..."
am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1
killall -9 Xwayland termux-x11 2>/dev/null
kill -9 $(pgrep -f "termux.x11") 2>/dev/null

# Step 3: Stop PulseAudio (optional)
echo "[3/3] Stopping PulseAudio..."
pulseaudio --kill 2>/dev/null

echo ""
echo "✅ GUI stopped successfully!"
echo "========================================"
exit 0
