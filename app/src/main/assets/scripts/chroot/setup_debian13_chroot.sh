#!/bin/sh

# setup_debian13_chroot.sh
# Installs a Debian 13 (Trixie) Chroot environment (Requires Root)
# Based on LinuxDroidMaster/Termux-Desktops Guide

# Global Variables
DEBIANPATH="/data/local/tmp/chrootDebian13"
USERNAME="flux"

# Same pinned rootfs as proot path (app assets → deploy to $HOME)
PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.nativecode}"
APP_HOME="${TERMUX__HOME:-/data/data/${PKG}/files/home}"
APP_PREFIX="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}"
ROOTFS_NAME="debian_13_rootfs.tar.xz"
ROOTFS_URL="${FLUX_ROOTFS_URL:-https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/debian_13_rootfs.tar.xz}"
ROOTFS_SHA256="${FLUX_ROOTFS_SHA256:-13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803}"

# Function to show progress message
progress() {
    printf "\033[1;36m[+] %s\033[0m\n" "$1"
}

# Function to show success message
success() {
    printf "\033[1;32m[✓] %s\033[0m\n" "$1"
}

# Function to show error message
error() {
    printf "\033[1;31m[!] %s\033[0m\n" "$1"
}

# Cleanup function
cleanup_mounts() {
    printf "\033[1;36m[+] Safety Check: Unmounting filesystems...\033[0m\n"
    $BB umount "$DEBIANPATH/sdcard" 2>/dev/null
    $BB umount "$DEBIANPATH/dev/shm" 2>/dev/null
    $BB umount "$DEBIANPATH/dev/pts" 2>/dev/null
    $BB umount "$DEBIANPATH/proc" 2>/dev/null
    $BB umount "$DEBIANPATH/sys" 2>/dev/null
    $BB umount "$DEBIANPATH/dev" 2>/dev/null
    $BB umount "$DEBIANPATH/tmp" 2>/dev/null
}

# Exit handler
goodbye() {
    error "Something went wrong."
    cleanup_mounts
    error "Exiting..."
    exit 1
}

# Resolve app-packaged / manual / download rootfs (same file as flux_install.sh)
resolve_rootfs_archive() {
    ROOTFS_ARCHIVE=""

    if [ -n "${FLUX_ROOTFS_PATH:-}" ] && [ -f "$FLUX_ROOTFS_PATH" ] && [ -s "$FLUX_ROOTFS_PATH" ]; then
        ROOTFS_ARCHIVE="$FLUX_ROOTFS_PATH"
        progress "rootfs from FLUX_ROOTFS_PATH=$ROOTFS_ARCHIVE"
        return 0
    fi

    for candidate in \
        "$APP_HOME/$ROOTFS_NAME" \
        "$APP_HOME/rootfs/$ROOTFS_NAME" \
        "$APP_PREFIX/var/lib/proot-distro/cache/rootfs/$ROOTFS_NAME" \
        "/sdcard/Download/$ROOTFS_NAME" \
        "/sdcard/Download/rootfs.tar.xz" \
        "/storage/emulated/0/Download/$ROOTFS_NAME" \
        "/storage/emulated/0/Download/rootfs.tar.xz" \
        "$DEBIANPATH/$ROOTFS_NAME" \
        "$DEBIANPATH/rootfs.tar.xz"
    do
        if [ -f "$candidate" ] && [ -s "$candidate" ]; then
            ROOTFS_ARCHIVE="$candidate"
            progress "rootfs found: $ROOTFS_ARCHIVE"
            return 0
        fi
    done

    return 1
}

# Download Helper (fallback only — prefer app-local archive)
download_file() {
    # $1=dir $2=filename $3=url
    progress "Downloading file..."
    if [ -e "$1/$2" ] && [ -s "$1/$2" ]; then
        printf "\033[1;33m[!] File already exists: %s\033[0m\n" "$2"
        printf "\033[1;33m[!] Skipping download...\033[0m\n"
        return 0
    fi
    mkdir -p "$1" 2>/dev/null || true
    if command -v wget >/dev/null 2>&1; then
        wget -O "$1/$2" "$3"
        if [ $? -eq 0 ]; then
            success "File downloaded successfully: $2"
            return 0
        fi
        error "Standard wget failed: $2."
    fi
    progress "Trying Busybox wget..."
    $BB wget -O "$1/$2" "$3"
    if [ $? -eq 0 ]; then
        success "File downloaded successfully (Fallback)"
        return 0
    fi
    goodbye
}

# Extraction Helper — archive path is $2 (or $1/rootfs.tar.xz)
extract_file() {
    # $1=dest root  $2=optional archive path
    _dest="$1"
    _archive="${2:-$_dest/rootfs.tar.xz}"
    progress "Extracting file from $_archive ..."
    if [ -f "$_dest/bin/bash" ] || [ -e "$_dest/bin/sh" ]; then
        printf "\033[1;33m[!] Rootfs appears populated: %s/bin\033[0m\n" "$_dest"
        printf "\033[1;33m[!] Skipping extraction...\033[0m\n"
        return 0
    fi
    if [ ! -f "$_archive" ] || [ ! -s "$_archive" ]; then
        error "Rootfs archive missing: $_archive"
        goodbye
    fi

    if tar xpvf "$_archive" -C "$_dest" --numeric-owner >/dev/null 2>&1; then
        success "Rootfs extracted successfully."
        return 0
    fi

    progress "Standard extract failed. Trying unxz pipe..."
    UNXZ_CMD="unxz"
    if ! command -v unxz >/dev/null 2>&1; then
        if "$BB" unxz --help >/dev/null 2>&1; then
            UNXZ_CMD="$BB unxz"
        else
            error "No 'unxz' tool found. Cannot extract .tar.xz file."
            goodbye
        fi
    fi

    if $UNXZ_CMD -c "$_archive" | tar xpv -C "$_dest" --numeric-owner >/dev/null 2>&1; then
         success "Rootfs extracted successfully (via unxz pipe)."
         return 0
    fi

    if tar xJvf "$_archive" -C "$_dest" --numeric-owner >/dev/null 2>&1; then
         success "Rootfs extracted successfully (Fallback flags)."
         return 0
    fi

    error "Extraction Failed! Your Busybox/Tar does not support XZ compression."
    goodbye
}

# Main Configuration Logic
configure_debian_chroot() {
    progress "Configuring Debian chroot environment..."

    if [ ! -d "$DEBIANPATH" ]; then
        mkdir -p "$DEBIANPATH"
        [ $? -ne 0 ] && goodbye
    fi

    progress "Mounting filesystems..."
    $BB mount -o remount,dev,suid /data

    $BB mount --bind /dev "$DEBIANPATH/dev" || goodbye
    $BB mount --bind /sys "$DEBIANPATH/sys" || goodbye
    $BB mount -t proc proc "$DEBIANPATH/proc" || goodbye
    $BB mount -t devpts devpts "$DEBIANPATH/dev/pts" || goodbye

    mkdir -p "$DEBIANPATH/dev/shm"
    $BB mount -t tmpfs -o size=512M tmpfs "$DEBIANPATH/dev/shm" || goodbye

    mkdir -p "$DEBIANPATH/tmp"
    $BB mount --bind /data/data/com.ivarna.nativecode/files/usr/tmp "$DEBIANPATH/tmp" 2>/dev/null || true

    mkdir -p "$DEBIANPATH/sdcard"
    $BB mount --bind /sdcard "$DEBIANPATH/sdcard" || goodbye

    progress "Configuring Network and Groups..."
    $BB chroot "$DEBIANPATH" /bin/bash -c '
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        rm -f /etc/resolv.conf
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
        echo "127.0.0.1 localhost" > /etc/hosts

        groupadd -g 3003 aid_inet 2>/dev/null
        groupadd -g 3004 aid_net_raw 2>/dev/null
        groupadd -g 1003 aid_graphics 2>/dev/null

        usermod -g 3003 -G 3003,3004 -a _apt 2>/dev/null || true
        usermod -G 3003 -a root 2>/dev/null || true

        echo "Testing Network..."
        if ping -c 1 8.8.8.8 >/dev/null 2>&1; then
            echo " [OK] Network is working."
        else
            echo " [!] Network check failed. Apt might fail."
        fi
    ' || goodbye

    progress "Updating packages (apt update/upgrade)..."
    $BB chroot "$DEBIANPATH" /bin/bash -c '
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        export DEBIAN_FRONTEND=noninteractive
        apt update
        apt upgrade -y
        apt install -y nano vim net-tools sudo git dbus-x11 wget unzip
    ' || goodbye

    progress "Creating User ($USERNAME)..."
    $BB chroot "$DEBIANPATH" /bin/bash -c "
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        groupadd storage 2>/dev/null
        groupadd wheel 2>/dev/null
        id -u $USERNAME >/dev/null 2>&1 || useradd -m -g users -G wheel,audio,video,storage,aid_inet -s /bin/bash $USERNAME
        echo '$USERNAME:flux' | chpasswd
    " || goodbye

    progress "Configuring Sudoers..."
    $BB chroot "$DEBIANPATH" /bin/bash -c "
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        echo '$USERNAME ALL=(ALL:ALL) NOPASSWD:ALL' > /etc/sudoers.d/$USERNAME
        chmod 0440 /etc/sudoers.d/$USERNAME
    " || goodbye

    progress "Installing XFCE4..."
    $BB chroot "$DEBIANPATH" /bin/bash -c '
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        export DEBIAN_FRONTEND=noninteractive
        apt install -y xfce4 xfce4-terminal
    ' || goodbye

    touch "$DEBIANPATH/.flux_configured"
    success "Debian Environment Configured!"

    # --- GENERATE GUI LAUNCH SCRIPT ---
    LAUNCH_SCRIPT="/data/local/tmp/start_debian13.sh"
    progress "Creating GUI launch script at $LAUNCH_SCRIPT..."

    cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh
DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

\$BB mount -o remount,dev,suid /data

\$BB mount --bind /dev \$DEBIANPATH/dev
\$BB mount --bind /sys \$DEBIANPATH/sys
\$BB mount -t proc proc \$DEBIANPATH/proc
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts

mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm

mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.ivarna.nativecode/files/usr/tmp \$DEBIANPATH/tmp 2>/dev/null || true
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

echo "Cleaning internal XFCE4 session..."
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

echo "Starting Debian 13 Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=tcp:127.0.0.1 && export XDG_RUNTIME_DIR=/tmp && xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null; dbus-launch --exit-with-session startxfce4'
EOF
    chmod +x "$LAUNCH_SCRIPT"
    success "GUI Launch script created."

    # --- GENERATE GUI STOP SCRIPT ---
    STOP_LAUNCHER="/data/local/tmp/stop_debian13_gui.sh"
    progress "Creating GUI Stop Script at $STOP_LAUNCHER..."

    cat <<EOF > "$STOP_LAUNCHER"
#!/bin/sh
DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

echo "Terminating Debian 13 Chroot GUI processes..."
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

TARGET_TERMUX_PREFIX="/data/data/com.ivarna.nativecode/files/usr"
if [ -f "\$TARGET_TERMUX_PREFIX/bin/pulseaudio" ]; then
    \$TARGET_TERMUX_PREFIX/bin/pulseaudio --kill >/dev/null 2>&1
fi

pkill -f com.termux.x11 >/dev/null 2>&1
echo "Debian 13 GUI Stopped."
EOF
    chmod +x "$STOP_LAUNCHER"
    success "GUI Stop Script created: $STOP_LAUNCHER"

    # --- GENERATE ROOT COMMAND RUNNER ---
    ROOT_RUNNER="/data/local/tmp/run_debian13_root.sh"
    progress "Creating Root Runner at $ROOT_RUNNER..."

    cat <<EOF > "$ROOT_RUNNER"
#!/bin/sh
# Wrapper to run a command inside Chroot as Root (with mounts)

DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

\$BB mount -o remount,dev,suid /data >/dev/null 2>&1
\$BB mount --bind /dev \$DEBIANPATH/dev >/dev/null 2>&1
\$BB mount --bind /sys \$DEBIANPATH/sys >/dev/null 2>&1
\$BB mount -t proc proc \$DEBIANPATH/proc >/dev/null 2>&1
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts >/dev/null 2>&1
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm >/dev/null 2>&1
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.ivarna.nativecode/files/usr/tmp \$DEBIANPATH/tmp >/dev/null 2>&1
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard >/dev/null 2>&1

CMD="\$@"
if [ -z "\$CMD" ]; then
    echo "Usage: \$0 <command>"
    exit 1
fi

\$BB chroot \$DEBIANPATH /bin/su - root -c "\$CMD"
EOF
    chmod +x "$ROOT_RUNNER"
    success "Root runner created."

    # --- GENERATE CLI LAUNCHER (User Shell) ---
    CLI_SCRIPT="/data/local/tmp/enter_debian13.sh"
    progress "Creating CLI Launcher at $CLI_SCRIPT..."

    cat <<EOF > "$CLI_SCRIPT"
#!/bin/sh
# CLI Entry for Debian 13 Chroot

DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

\$BB mount -o remount,dev,suid /data 2>/dev/null

\$BB mount --bind /dev \$DEBIANPATH/dev 2>/dev/null
\$BB mount --bind /sys \$DEBIANPATH/sys 2>/dev/null
\$BB mount -t proc proc \$DEBIANPATH/proc 2>/dev/null
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts 2>/dev/null

mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm 2>/dev/null

mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.ivarna.nativecode/files/usr/tmp \$DEBIANPATH/tmp 2>/dev/null || true
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard 2>/dev/null

echo "Entering Debian 13 Chroot (CLI)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME
EOF
    chmod +x "$CLI_SCRIPT"
    success "CLI Launcher created: $CLI_SCRIPT"

    # --- GENERATE ROOT CLI LAUNCHER (Root Shell) ---
    ROOT_CLI_SCRIPT="/data/local/tmp/enter_debian13_root.sh"
    progress "Creating Root CLI Launcher at $ROOT_CLI_SCRIPT..."

    cat <<EOF > "$ROOT_CLI_SCRIPT"
#!/bin/sh
# Root CLI Entry for Debian 13 Chroot

DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

\$BB mount -o remount,dev,suid /data 2>/dev/null

\$BB mount --bind /dev \$DEBIANPATH/dev 2>/dev/null
\$BB mount --bind /sys \$DEBIANPATH/sys 2>/dev/null
\$BB mount -t proc proc \$DEBIANPATH/proc 2>/dev/null
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts 2>/dev/null

mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm 2>/dev/null

mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.ivarna.nativecode/files/usr/tmp \$DEBIANPATH/tmp 2>/dev/null || true
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard 2>/dev/null

echo "Entering Debian 13 Chroot as ROOT..."
\$BB chroot \$DEBIANPATH /bin/bash -c "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin && exec /bin/bash --login"
EOF
    chmod +x "$ROOT_CLI_SCRIPT"
    success "Root CLI Launcher created: $ROOT_CLI_SCRIPT"

    cleanup_mounts
    success "NativeCode: Chroot Setup Complete!"

    # Notify NativeCode app
    am start -a android.intent.action.VIEW -d "nativecode://callback?result=success&name=distro_install_debian13_chroot" >/dev/null 2>&1
}

main() {
    export LD_LIBRARY_PATH=/data/data/com.ivarna.nativecode/files/usr/lib

    if [ "$(id -u)" != "0" ]; then
        error "This script must be run as root. Exiting."
        exit 1
    fi

    # --- BUSYBOX DETECTION ---
    BB=""

    if command -v busybox >/dev/null 2>&1; then
        DETECTED_BB=$(command -v busybox)
        case "$DETECTED_BB" in
            *"com.ivarna.nativecode"*) ;;
            *)
                if [ -x "$DETECTED_BB" ]; then
                    BB="$DETECTED_BB"
                fi
                ;;
        esac
    fi

    if [ -z "$BB" ]; then
        CANDIDATES="/data/adb/magisk/busybox \
        /data/adb/modules/busybox-ndk/system/bin/busybox \
        /sbin/busybox \
        /system/xbin/busybox \
        /system/bin/busybox \
        /debug_ramdisk/busybox"

        for path in $CANDIDATES; do
            if [ -x "$path" ]; then
                BB="$path"
                break
            fi
        done
    fi

    if [ -z "$BB" ]; then
         error "Root-capable Busybox not found!"
         exit 1
    fi

    progress "Using Root Busybox: $BB"

    DEBIANPATH="/data/local/tmp/chrootDebian13"

    if [ -f "$DEBIANPATH/.flux_configured" ]; then
        success "Debian 13 Chroot already installed."
        progress "Skipping installation..."
        am start -a android.intent.action.VIEW -d "nativecode://callback?result=success&name=distro_install_debian13_chroot" >/dev/null 2>&1
        exit 0
    fi

    if [ ! -d "$DEBIANPATH" ]; then
        mkdir -p "$DEBIANPATH"
        success "Created directory: $DEBIANPATH"
    fi

    # Prefer app-deployed rootfs (same as flux_install / assets/rootfs/)
    if resolve_rootfs_archive; then
        :
    else
        progress "No local rootfs — downloading $ROOTFS_URL"
        download_file "$DEBIANPATH" "$ROOTFS_NAME" "$ROOTFS_URL"
        ROOTFS_ARCHIVE="$DEBIANPATH/$ROOTFS_NAME"
    fi

    # Optional SHA check (when sha256sum available)
    if [ -n "$ROOTFS_SHA256" ] && command -v sha256sum >/dev/null 2>&1; then
        _got="$(sha256sum "$ROOTFS_ARCHIVE" | awk '{print $1}')"
        if [ "$_got" != "$ROOTFS_SHA256" ]; then
            error "SHA256 mismatch for $ROOTFS_ARCHIVE"
            error "  expected $ROOTFS_SHA256"
            error "  got      $_got"
            goodbye
        fi
        success "SHA256 OK"
    fi

    extract_file "$DEBIANPATH" "$ROOTFS_ARCHIVE"
    configure_debian_chroot
}

main "$@"
