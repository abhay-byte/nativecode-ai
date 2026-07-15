#!/data/data/com.ivarna.nativecode/files/usr/bin/bash
# start_gui.sh - Launch XFCE4 Desktop Environment in PRoot Distro

DISTRO=${1:-debian}

# Detect how we're running
IS_ROOT=false
if [ "$(id -u)" = "0" ]; then IS_ROOT=true; fi

# Termux paths
TERMUX_PREFIX="/data/data/com.ivarna.nativecode/files/usr"
TERMUX_HOME="$TERMUX_PREFIX/home"
export HOME="$TERMUX_HOME"
export TMPDIR="$TERMUX_PREFIX/tmp"
export PROOT_TMP_DIR="$TMPDIR"
export PATH="$TERMUX_PREFIX/bin:$TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin:$PATH"
export LD_LIBRARY_PATH="$TERMUX_PREFIX/lib:$TERMUX_PREFIX/opt/virglrenderer-android/lib"
export TERMUX_APP__PACKAGE_NAME="com.ivarna.nativecode"
export TERMUX__PREFIX="$TERMUX_PREFIX"
export TERMUX__HOME="$TERMUX_HOME"
export XKB_CONFIG_ROOT="$TERMUX_PREFIX/share/X11/xkb"

# Configure PulseAudio (use home to avoid root-owned stale tmp dirs)
export PULSE_RUNTIME_PATH="${HOME}/.pulse"
mkdir -p "$PULSE_RUNTIME_PATH" 2>/dev/null

# Kill stale processes
am force-stop com.termux.x11 2>/dev/null
pkill -f "virgl_test_server" 2>/dev/null
pkill -f pulseaudio 2>/dev/null
pkill -f termux-x11 2>/dev/null
pkill -f app_process.*termux-x11 2>/dev/null
sleep 2

# Start PulseAudio over TCP
if $IS_ROOT; then
  pulseaudio --system --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null || \
  pulseaudio --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null
else
  pulseaudio --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null
fi

# VirGL is optional. Custom package builds currently omit it; XFCE runs
# with software rendering until a compatible GPU bundle is available.
if command -v virgl_test_server_android >/dev/null; then
  echo "FluxLinux: Starting VirGL server..."
  virgl_test_server_android --socket-path "$TERMUX_PREFIX/tmp/.virgl_test" >/dev/null 2>&1 &
  sleep 2
  test -S "${TMPDIR}/.virgl_test" && echo "FluxLinux: VirGL socket ready" || \
    echo "FluxLinux: [WARN] VirGL socket not found"
else
  echo "FluxLinux: VirGL unavailable; using software rendering"
fi

# Start X server via app_process (needs system context)
echo "FluxLinux: Starting termux-x11 server..."
mkdir -p "$TMPDIR/x11" 2>/dev/null
export XDG_RUNTIME_DIR="$TMPDIR/x11"
export DISPLAY=:0

# Fix Android 14 SecurityException for writable dex files (loader.apk must be read-only)
chmod 0400 "$TERMUX_PREFIX/libexec/termux-x11/loader.apk" 2>/dev/null || true

# Fix broken xkb symlink if pointing to old com.termux prefix
if [ -L "$TERMUX_PREFIX/share/X11/xkb" ] && [ ! -e "$TERMUX_PREFIX/share/X11/xkb" ]; then
  rm -f "$TERMUX_PREFIX/share/X11/xkb"
  ln -s "$TERMUX_PREFIX/share/xkeyboard-config-2" "$TERMUX_PREFIX/share/X11/xkb"
fi

# Launch the X server app_process directly, clearing LD_LIBRARY_PATH to avoid system linker crashes
LD_LIBRARY_PATH="" LD_PRELOAD="" CLASSPATH="$TERMUX_PREFIX/libexec/termux-x11/loader.apk" \
LANG=en_US.UTF-8 \
/system/bin/app_process -Xnoimage-dex2oat / \
  --nice-name="termux-x11" com.termux.x11.Loader :0 &
XSERVER_PID=$!
sleep 3

# The loader above owns the X server. Starting the activity through `am` from
# this app sandbox aborts on current Android builds and prevents guest launch.

# Verify guest setup. Installing a hand-picked set of Debian packages with
# --force-depends can leave the rootfs unbootable, so setup owns installation.
ROOTFS="$TERMUX_PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"
if [ ! -f "$ROOTFS/usr/bin/startxfce4" ]; then
  echo "FluxLinux: XFCE setup incomplete. Re-run environment setup."
  exit 1
fi

echo "FluxLinux: startxfce4=READY"

# Launch XFCE in proot
if [ "$DISTRO" = "termux" ]; then
  export PULSE_SERVER=127.0.0.1
  env DISPLAY=:0 startxfce4
else
  python "$TERMUX_PREFIX/bin/proot-distro" login "$DISTRO" --shared-tmp -- /bin/bash -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp
    export VTEST_SOCKET_NAME=/tmp/.virgl_test
    su - flux -c "
      export DISPLAY=:0
      export PULSE_SERVER=tcp:127.0.0.1
      export XDG_RUNTIME_DIR=/tmp
      export VTEST_SOCKET_NAME=/tmp/.virgl_test
      xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null
      dbus-launch --exit-with-session startxfce4
    "
  '
fi

exit 0
