#!/data/data/com.ivarna.nativecode/files/usr/bin/bash
# stop_gui.sh - Stop XFCE4 Desktop Environment in PRoot Distro
# Paths: TermuxHostPaths via fluxlinux-host.env (SSOT)

DISTRO=${1:-debian}
PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.nativecode}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

export TERMUX_APP__PACKAGE_NAME="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
export TERMUX__PREFIX="${TERMUX__PREFIX:-/data/data/${TERMUX_APP__PACKAGE_NAME}/files/usr}"
export TERMUX__HOME="${TERMUX__HOME:-/data/data/${TERMUX_APP__PACKAGE_NAME}/files/home}"
export HOME="${HOME:-$TERMUX__HOME}"
export PROOT_TMP_DIR="${PROOT_TMP_DIR:-$TERMUX__PREFIX/tmp}"
PKG="$TERMUX_APP__PACKAGE_NAME"

echo "========================================"
echo "FluxLinux: Stopping GUI for $DISTRO"
echo "========================================"

# Step 1: Kill XFCE processes
echo "[1/4] Stopping XFCE4 processes..."
pkill -9 -f "xfce4-session|xfwm4|xfdesktop|xfce4-panel|dbus-launch|dbus-daemon" 2>/dev/null

# Step 2: Stop proot-distro sessions
echo "[2/4] Killing proot sessions..."
pkill -9 -f "proot-distro" 2>/dev/null
pkill -9 -f "proot" 2>/dev/null

# Step 3: Stop Termux X11 server
echo "[3/4] Stopping Termux X11..."
# Restore write permissions to allow clean updates/deployments
chmod 0700 "$TERMUX__PREFIX/libexec/termux-x11" 2>/dev/null || true
chmod 0600 "$TERMUX__PREFIX/libexec/termux-x11/loader.apk" 2>/dev/null || true
# Send ACTION_STOP broadcast to close the X11 activity in our app
am broadcast -a com.termux.x11.ACTION_STOP -p "$PKG" >/dev/null 2>&1
# Kill the app_process X server
pkill -9 -f "termux-x11" 2>/dev/null
pkill -9 -f "app_process.*termux-x11" 2>/dev/null
killall -9 Xwayland 2>/dev/null

# Step 4: Stop PulseAudio
echo "[4/4] Stopping PulseAudio..."
pulseaudio --kill 2>/dev/null

echo ""
echo "✅ GUI stopped successfully!"
echo "========================================"
exit 0
