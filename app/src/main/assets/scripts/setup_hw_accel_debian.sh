#!/bin/bash
# setup_hw_accel_debian.sh
# Hardware Acceleration Setup for Debian (PRoot / chroot)
#
# Modes:
#   turnip — Snapdragon / Adreno (KGSL) via Mesa Turnip + Zink
#   virgl  — Mali / PowerVR / Xclipse / unknown (host virgl_test_server)
#
# FLUX_GPU values:
#   turnip | adreno | snapdragon | qcom  → turnip
#   virgl  | mali | powervr | soft | ask | manual → see below
#   unset  → auto-detect inside guest (getprop / /dev/kgsl-3d0 / cpuinfo)

set -euo pipefail

if [ "$(id -u)" != "0" ]; then
    echo "This script must be run as root."
    exit 1
fi

echo "FluxLinux: Setting up Hardware Acceleration (Debian)..."

MODE_STATE_DIR="/etc/fluxlinux"
MODE_STATE_FILE="$MODE_STATE_DIR/gpu_mode"
VENDOR_STATE_FILE="$MODE_STATE_DIR/gpu_vendor"

# ── helpers ──────────────────────────────────────────────────────────────────

normalize_mode() {
    # stdin/arg → turnip|virgl|ask
    local raw
    raw=$(echo "${1:-}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
    case "$raw" in
        turnip|adreno|snapdragon|qcom|qualcomm|kgsl|zink)
            echo "turnip"
            ;;
        virgl|virpipe|mali|powervr|xclipse|llvmpipe|soft|software|sw)
            echo "virgl"
            ;;
        ask|manual|"")
            echo "ask"
            ;;
        *)
            # unknown explicit value → treat as virgl (safe default)
            echo "virgl"
            ;;
    esac
}

collect_gpu_hints() {
    local hints=""
    if command -v getprop >/dev/null 2>&1; then
        for k in \
            ro.hardware ro.hardware.chipname ro.chipname ro.board.platform \
            ro.soc.model ro.soc.manufacturer ro.product.board \
            ro.hardware.egl ro.hardware.vulkan ro.gfx.driver.0; do
            hints+=" $(getprop "$k" 2>/dev/null || true)"
        done
    fi
    # Android props often visible via /system even under proot
    if [ -r /proc/cpuinfo ]; then
        hints+=" $(grep -E 'Hardware|model name|Processor' /proc/cpuinfo 2>/dev/null | head -20 || true)"
    fi
    if [ -e /dev/kgsl-3d0 ] || [ -e /dev/kgsl ]; then
        hints+=" kgsl"
    fi
    # Some proot binds expose kgsl under host path
    if [ -e /dev/kgsl-3d0 ]; then
        hints+=" kgsl-3d0"
    fi
    echo "$hints" | tr '[:upper:]' '[:lower:]'
}

auto_detect_mode() {
    local hints vendor
    hints=$(collect_gpu_hints)
    vendor="unknown"

    # Strong signal: KGSL device node (Adreno)
    if [ -e /dev/kgsl-3d0 ] || echo "$hints" | grep -qE '\bkgsl\b'; then
        vendor="adreno/snapdragon"
        echo "turnip|$vendor|$hints"
        return
    fi

    if echo "$hints" | grep -qE 'qcom|qualcomm|adreno|snapdragon|msm[0-9]|sdm[0-9]|sm[0-9]{3,4}|lahaina|taro|kalama|pineapple|kona|lito|bengal|holi|crow|parrot|blair|waipio|yupik|shima|atoll|trinket'; then
        vendor="adreno/snapdragon"
        echo "turnip|$vendor|$hints"
        return
    fi

    if echo "$hints" | grep -qE 'mali|exynos|kirin|hisi|mediatek|mt6[0-9]|dimensity|helio|tensor|gs10|gs20|gs30'; then
        vendor="mali"
        echo "virgl|$vendor|$hints"
        return
    fi

    if echo "$hints" | grep -qE 'powervr|imgtec|imagination|rogue'; then
        vendor="powervr"
        echo "virgl|$vendor|$hints"
        return
    fi

    if echo "$hints" | grep -qE 'xclipse|amdgpu'; then
        vendor="xclipse"
        echo "virgl|$vendor|$hints"
        return
    fi

    echo "virgl|$vendor|$hints"
}

# ── 1. deps ──────────────────────────────────────────────────────────────────

echo "FluxLinux: Detecting Package Manager..."

if command -v pacman >/dev/null 2>&1; then
    echo "Arch Linux detected (pacman). Running System Update..."
    pacman -Syu --noconfirm
    echo "FluxLinux: Installing Arch Dependencies..."
    pacman -S --noconfirm \
        mesa vulkan-radeon vulkan-swrast vulkan-tools mesa-utils \
        curl unzip xdg-desktop-portal
    echo "FluxLinux: Arch Setup Complete."
elif command -v apt-get >/dev/null 2>&1; then
    echo "Debian/Ubuntu detected (apt)."
    echo "FluxLinux: Installing Vulkan/Mesa dependencies..."
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        mesa-utils libgl1-mesa-dri mesa-vulkan-drivers vulkan-tools \
        curl unzip libvulkan1 libgl1 libglx0 xdg-desktop-portal
else
    echo "Error: Neither apt nor pacman found. Cannot install dependencies."
    exit 1
fi

# ── 2. arch ──────────────────────────────────────────────────────────────────

ARCH=""
if command -v dpkg >/dev/null 2>&1; then
    ARCH=$(dpkg --print-architecture 2>/dev/null || true)
fi
if [ -z "$ARCH" ]; then
    case "$(uname -m)" in
        aarch64|arm64) ARCH=arm64 ;;
        armv7*|armhf) ARCH=armhf ;;
        x86_64|amd64) ARCH=amd64 ;;
        *) ARCH=$(uname -m) ;;
    esac
fi
if [ "$ARCH" != "arm64" ] && [ "$ARCH" != "aarch64" ]; then
    echo "Warning: Turnip packages are arm64-only. Your arch is $ARCH."
fi

# ── 3. mode select ───────────────────────────────────────────────────────────

# Empty FLUX_GPU → auto. ask/manual → menu only on TTY. else normalize.
RAW_FLUX_GPU="${FLUX_GPU:-}"
NORMALIZED=$(normalize_mode "$RAW_FLUX_GPU")
VENDOR_HINT="unknown"
DETECT_HINTS=""
MODE=""

want_menu=0
if [ "$RAW_FLUX_GPU" = "ask" ] || [ "$RAW_FLUX_GPU" = "manual" ]; then
    want_menu=1
fi

if [ "$want_menu" = "1" ] && [ -t 0 ]; then
    echo "============================================"
    echo "      Select your GPU / Acceleration Mode"
    echo "============================================"
    echo "1) Adreno (Turnip + Zink) — Snapdragon"
    echo "2) VirGL (Universal) — Mali / PowerVR / other"
    echo "============================================"
    read -r -p "Enter choice [1-2]: " GPU_CHOICE
    case "${GPU_CHOICE:-}" in
        1) MODE="turnip"; VENDOR_HINT="manual-adreno" ;;
        2) MODE="virgl"; VENDOR_HINT="manual-virgl" ;;
        *)
            echo "Invalid choice. Defaulting to VirGL."
            MODE="virgl"
            VENDOR_HINT="invalid-default-virgl"
            ;;
    esac
elif [ "$NORMALIZED" = "ask" ]; then
    # unset / empty → auto-detect (never block onboarding)
    DET=$(auto_detect_mode)
    MODE=$(echo "$DET" | cut -d'|' -f1)
    VENDOR_HINT=$(echo "$DET" | cut -d'|' -f2)
    DETECT_HINTS=$(echo "$DET" | cut -d'|' -f3-)
    echo "FluxLinux: Auto-detected GPU mode=$MODE vendor=$VENDOR_HINT"
    echo "FluxLinux: hints: $DETECT_HINTS"
else
    MODE="$NORMALIZED"
    if [ "$MODE" = "turnip" ]; then
        VENDOR_HINT="env-adreno"
    else
        VENDOR_HINT="env-other"
    fi
    echo "FluxLinux: FLUX_GPU=${RAW_FLUX_GPU} → mode=$MODE"
fi

# Arm64-only turnip packages: fall back to virgl on other arches
if [ "$MODE" = "turnip" ] && [ "$ARCH" != "arm64" ] && [ "$ARCH" != "aarch64" ]; then
    echo "FluxLinux: [WARN] Turnip not available for arch=$ARCH — falling back to VirGL."
    MODE="virgl"
    VENDOR_HINT="${VENDOR_HINT}+arch-fallback"
fi

echo "FluxLinux: Configuring for mode=$MODE vendor=$VENDOR_HINT..."

# ── 4. turnip install ────────────────────────────────────────────────────────

if [ "$MODE" = "turnip" ]; then
    # Mesa Turnip for Adreno — KGSL, proot-compatible
    # https://github.com/lfdevs/mesa-for-android-container
    TURNIP_VERSION="26.2.0-devel-20260709"
    MESA_VERSION="26.2.0-devel-20260709"

    if [ -f /etc/debian_version ]; then
        DISTRO="debian_trixie"
    elif [ -f /etc/lsb-release ] && grep -qi "Ubuntu" /etc/lsb-release; then
        DISTRO="ubuntu_noble"
    elif [ -f /etc/fedora-release ]; then
        DISTRO="fedora_43"
    else
        DISTRO="debian_trixie"
    fi

    URL="https://github.com/lfdevs/mesa-for-android-container/releases/download/turnip-${TURNIP_VERSION}/turnip_${TURNIP_VERSION}_${DISTRO}_arm64.tar.gz"

    echo "FluxLinux: Downloading Turnip drivers v${TURNIP_VERSION} for ${DISTRO}..."
    if ! curl -L --fail -o /tmp/turnip.tar.gz "$URL"; then
        echo "Error: Failed to download Turnip. Falling back to VirGL."
        MODE="virgl"
        VENDOR_HINT="${VENDOR_HINT}+turnip-download-fail"
    else
        echo "FluxLinux: Installing Turnip..."
        tar -zxf /tmp/turnip.tar.gz -C /
        ldconfig
        rm -f /tmp/turnip.tar.gz
        echo "FluxLinux: Turnip installed successfully!"

        # XFCE compositor black-screens with Turnip
        echo "FluxLinux: Disabling XFCE4 compositor for Turnip..."
        for userdir in /home/* /root; do
            [ -d "$userdir" ] || continue
            XFCE_CONF_DIR="$userdir/.config/xfce4/xfconf/xfce-perchannel-xml"
            mkdir -p "$XFCE_CONF_DIR" 2>/dev/null || true
            cat > "$XFCE_CONF_DIR/xfwm4.xml" << 'XFCEXML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
  </property>
</channel>
XFCEXML
            chown -R "$(stat -c '%U:%G' "$userdir")" "$userdir/.config" 2>/dev/null || true
        done

        # Fake /dev/dri for apps that probe it (Turnip uses kgsl)
        echo "FluxLinux: Creating /dev/dri compatibility layer..."
        mkdir -p /dev/dri 2>/dev/null || true
        [ -e /dev/dri/card0 ] || ln -sf /dev/null /dev/dri/card0 2>/dev/null || true
        [ -e /dev/dri/renderD128 ] || ln -sf /dev/null /dev/dri/renderD128 2>/dev/null || true
        chmod 755 /dev/dri 2>/dev/null || true

        MESA_URL="https://github.com/lfdevs/mesa-for-android-container/releases/download/mesa-${MESA_VERSION}/mesa-for-android-container_${MESA_VERSION}_${DISTRO}_arm64.tar.gz"
        echo "FluxLinux: Upgrading system Mesa to ${MESA_VERSION}..."
        if curl -L --fail -o /tmp/mesa-upgrade.tar.gz "$MESA_URL"; then
            tar -zxf /tmp/mesa-upgrade.tar.gz -C /
            ldconfig
            rm -f /tmp/mesa-upgrade.tar.gz
            echo "FluxLinux: Mesa upgraded to ${MESA_VERSION}!"
            cat > /etc/apt/preferences.d/pin-mesa << 'PINEOF'
# FluxLinux: Mesa pinned — runtime upgraded via mesa-for-android-container
Package: libgl1-mesa-dri
Pin: version *
Pin-Priority: -1

Package: mesa-libgallium
Pin: version *
Pin-Priority: -1

Package: libglx-mesa0
Pin: version *
Pin-Priority: -1

Package: libegl-mesa0
Pin: version *
Pin-Priority: -1

Package: mesa-va-drivers
Pin: version *
Pin-Priority: -1

Package: mesa-vdpau-drivers
Pin: version *
Pin-Priority: -1

Package: mesa-vulkan-drivers
Pin: version *
Pin-Priority: -1
PINEOF
            echo "FluxLinux: Mesa packages pinned."
        else
            rm -f /tmp/mesa-upgrade.tar.gz
            echo "[WARN] Mesa upgrade download failed — stock Mesa remains."
        fi
    fi
fi

# ── 5. virgl note (deps already installed) ───────────────────────────────────

if [ "$MODE" = "virgl" ]; then
    echo "FluxLinux: VirGL mode — guest uses GALLIUM_DRIVER=virpipe."
    echo "FluxLinux: Host must run virgl_test_server_android (start_gui.sh)."
fi

# ── 6. gpu-launch wrapper ────────────────────────────────────────────────────

echo "FluxLinux: Creating 'gpu-launch' wrapper (mode=$MODE)..."

cat <<'EOF' > /usr/local/bin/gpu-launch
#!/bin/bash
# FluxLinux GPU Launcher — mode baked at install time; override via FLUX_GPU_RUNTIME
MODE="MODE_PLACEHOLDER"

# Prefer state file if present (re-run start without reinstall)
if [ -r /etc/fluxlinux/gpu_mode ]; then
    _m=$(tr -d '[:space:]' </etc/fluxlinux/gpu_mode)
    [ -n "$_m" ] && MODE="$_m"
fi
[ -n "${FLUX_GPU_RUNTIME:-}" ] && MODE="$FLUX_GPU_RUNTIME"

unset GALLIUM_DRIVER
unset MESA_LOADER_DRIVER_OVERRIDE
unset VK_ICD_FILENAMES
unset LIBGL_ALWAYS_SOFTWARE
unset TU_DEBUG
unset MESA_VK_WSI_DEBUG

if [ "$MODE" = "turnip" ]; then
    export MESA_LOADER_DRIVER_OVERRIDE=zink
    export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
    export TU_DEBUG=noconform
    export MESA_VK_WSI_DEBUG=sw
    export MESA_GL_VERSION_OVERRIDE=4.6
    export MESA_GLES_VERSION_OVERRIDE=3.2
    export MESA_NO_ERROR=1
elif [ "$MODE" = "virgl" ]; then
    export GALLIUM_DRIVER=virpipe
    export MESA_GL_VERSION_OVERRIDE=4.0
    export MESA_GLES_VERSION_OVERRIDE=3.1
    export MESA_NO_ERROR=1
    export VTEST_SOCKET_NAME=${VTEST_SOCKET_NAME:-/tmp/.virgl_test}
    if [ "${FLUX_GPU_DEBUG:-0}" = "1" ]; then
        echo "[DEBUG] GALLIUM_DRIVER=$GALLIUM_DRIVER"
        echo "[DEBUG] VTEST_SOCKET_NAME=$VTEST_SOCKET_NAME"
        ls -la /tmp/.virgl_test 2>/dev/null && echo "[DEBUG] VirGL socket exists" || \
            echo "[DEBUG] VirGL socket NOT FOUND at /tmp/.virgl_test"
    fi
else
    # soft fallback
    export LIBGL_ALWAYS_SOFTWARE=1
    export GALLIUM_DRIVER=llvmpipe
fi

exec "$@"
EOF

sed -i "s/MODE_PLACEHOLDER/$MODE/g" /usr/local/bin/gpu-launch
chmod +x /usr/local/bin/gpu-launch

# Profile snippet so login shells pick up mode (optional, non-destructive)
mkdir -p /etc/profile.d
cat > /etc/profile.d/flux-gpu.sh << 'PROFILE'
# FluxLinux GPU mode helpers
if [ -r /etc/fluxlinux/gpu_mode ]; then
    export FLUX_GPU_MODE=$(tr -d '[:space:]' </etc/fluxlinux/gpu_mode)
fi
PROFILE
chmod 644 /etc/profile.d/flux-gpu.sh

# Persist mode for start_gui.sh / gpu-launch
mkdir -p "$MODE_STATE_DIR"
echo "$MODE" > "$MODE_STATE_FILE"
echo "$VENDOR_HINT" > "$VENDOR_STATE_FILE"
chmod 644 "$MODE_STATE_FILE" "$VENDOR_STATE_FILE"

echo ""
echo "============================================"
echo "  Hardware Acceleration Setup Complete!"
echo "============================================"
echo "Mode:   $MODE"
echo "Vendor: $VENDOR_HINT"
echo "State:  $MODE_STATE_FILE"
echo ""
echo "Usage: gpu-launch <application>"
echo "Example: gpu-launch glmark2"
echo ""

if [ "$MODE" = "virgl" ]; then
    echo "IMPORTANT: VirGL needs virgl_test_server on the host (start_gui.sh)."
    echo ""
fi

if [ "$MODE" = "turnip" ]; then
    echo "Turnip configured for Adreno/Snapdragon (KGSL)."
    echo "If GL fails, re-run with: FLUX_GPU=virgl bash $0"
    echo ""
fi

echo "Test:"
echo "  gpu-launch glmark2"
echo "  gpu-launch glxinfo | grep 'OpenGL renderer'"
echo "============================================"
