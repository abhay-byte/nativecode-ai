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

# Start VirGL server
echo "FluxLinux: Starting VirGL server..."
virgl_test_server_android --socket-path "$TERMUX_PREFIX/tmp/.virgl_test" >/dev/null 2>&1 &
VIRGL_PID=$!
sleep 2

if [ -S "${TMPDIR}/.virgl_test" ]; then
  echo "FluxLinux: VirGL socket ready"
else
  echo "FluxLinux: [WARN] VirGL socket not found"
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

# Launch X11 activity
am start -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
sleep 2

# Ensure packages in proot distro
ROOTFS="$TERMUX_PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"
if [ ! -f "$ROOTFS/usr/bin/startxfce4" ]; then
  echo "FluxLinux: Missing XFCE packages. Downloading..."
  mkdir -p "$TMPDIR/debs"
  cd "$TMPDIR/debs"

  DEB_MIRROR="http://deb.debian.org/debian"
  for pkg_url in \
    "/pool/main/x/xfce4/xfce4_4.20.1_all.deb" \
    "/pool/main/x/xfce4-session/xfce4-session_4.20.2-2_arm64.deb" \
    "/pool/main/x/xfwm4/xfwm4_4.20.0-1_arm64.deb" \
    "/pool/main/x/xfdesktop4/xfdesktop4_4.20.1-1_arm64.deb" \
    "/pool/main/x/xfce4-panel/xfce4-panel_4.20.4-1_arm64.deb" \
    "/pool/main/x/xfce4-appfinder/xfce4-appfinder_4.20.0-2_arm64.deb" \
    "/pool/main/x/xfce4-settings/xfce4-settings_4.20.1-1_arm64.deb" \
    "/pool/main/d/dbus/dbus-x11_1.16.2-2_arm64.deb" \
    "/pool/main/t/tigervnc/tigervnc-standalone-server_1.15.0+dfsg-2.1~deb13u1_arm64.deb" \
    "/pool/main/x/xfconf/xfconf_4.20.0-1_arm64.deb" \
    "/pool/main/x/xfce4-notifyd/xfce4-notifyd_0.9.7-2_arm64.deb" \
    "/pool/main/x/xinit/xinit_1.4.2-1+b2_arm64.deb" \
    "/pool/main/x/xorg-server/xserver-xorg-core_21.1.16-1.3+deb13u3_arm64.deb" \
    "/pool/main/x/xorg-server/xserver-common_21.1.16-1.3+deb13u3_all.deb" \
    "/pool/main/x/xorg/xserver-xorg_7.7+24+deb13u1_arm64.deb"
  do
    fname=$(basename "$pkg_url")
    if [ ! -f "$fname" ]; then
      wget -q "$DEB_MIRROR$pkg_url" 2>/dev/null && echo "  OK: $fname" || echo "  FAIL: $fname"
    fi
  done

  echo "FluxLinux: Installing packages in $DISTRO..."
  python "$TERMUX_PREFIX/bin/proot-distro" login "$DISTRO" --shared-tmp -- \
    bash -c "dpkg -i --force-depends /tmp/debs/*.deb 2>/dev/null; echo DONE" 2>/dev/null
  rm -rf "$TMPDIR/debs"
  echo "FluxLinux: Package install complete."
fi

echo "FluxLinux: startxfce4=$(test -f $ROOTFS/usr/bin/startxfce4 && echo READY || echo MISSING)"

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
