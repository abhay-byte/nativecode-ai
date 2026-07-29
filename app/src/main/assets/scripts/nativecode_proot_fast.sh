#!/system/bin/sh
# nativecode_proot_fast.sh — optional plain-proot fast path (Task 2)
# Does NOT replace proot-distro / nativecode_proot.sh (compat default).
#
# Usage:
#   nativecode_proot_fast.sh exec   [--profile cli|gpu-turnip|gpu-virgl|compat] -- CMD [ARGS...]
#   nativecode_proot_fast.sh sh     [--profile ...] -- 'shell string'
#   nativecode_proot_fast.sh login  [--shell zsh|bash] [--profile ...]
#   nativecode_proot_fast.sh root-exec [--profile ...] -- CMD [ARGS...]
#
# Env overrides:
#   NC_PROOT_PROFILE, NC_PROOT_SYSVIPC=1, NC_PROOT_FAKE_UNAME=1
#   NC_PACKAGE, PREFIX, ROOTFS, NC_SHELL
set -eu

NC_PACKAGE="${NC_PACKAGE:-com.ivarna.nativecode}"
PREFIX="${PREFIX:-/data/data/${NC_PACKAGE}/files/usr}"
TERMUX__HOME="${TERMUX__HOME:-/data/data/${NC_PACKAGE}/files/home}"
ROOTFS="${ROOTFS:-${PREFIX}/var/lib/proot-distro/containers/debian/rootfs}"
PROOT_BIN="${PROOT_BIN:-${PREFIX}/bin/proot}"
PROFILE="${NC_PROOT_PROFILE:-cli}"
LOGIN_SHELL="${NC_SHELL:-zsh}"
MODE=""
GUEST_UID=""
GUEST_GID=""
GUEST_HOME="/home/flux"
GUEST_USER="flux"
IS_ROOT_EXEC=0

die() {
  echo "nativecode_proot_fast: $*" >&2
  exit 2
}

usage() {
  cat <<'EOF' >&2
usage:
  nativecode_proot_fast.sh exec   [--profile cli|gpu-turnip|gpu-virgl|compat] -- CMD [ARGS...]
  nativecode_proot_fast.sh sh     [--profile ...] -- SHELL_STRING
  nativecode_proot_fast.sh login  [--shell zsh|bash] [--profile ...]
  nativecode_proot_fast.sh root-exec [--profile ...] -- CMD [ARGS...]
EOF
  exit 2
}

# --- safety ---
safety_check() {
  case "$PREFIX" in
    *com.termux*) die "refusing com.termux PREFIX: $PREFIX" ;;
  esac
  case "$ROOTFS" in
    *com.termux*) die "refusing com.termux ROOTFS: $ROOTFS" ;;
  esac
  if [ -f "$PREFIX/etc/fluxlinux-host.env" ]; then
    # shellcheck disable=SC1090
    . "$PREFIX/etc/fluxlinux-host.env"
  fi
  PKG="${TERMUX_APP__PACKAGE_NAME:-$NC_PACKAGE}"
  [ "$PKG" = "com.ivarna.nativecode" ] || die "package must be com.ivarna.nativecode (got $PKG)"
  [ -x "$PROOT_BIN" ] || die "proot missing: $PROOT_BIN"
  [ -d "$ROOTFS" ] || die "rootfs missing: $ROOTFS"
  [ -d "${PREFIX}/tmp" ] || mkdir -p "${PREFIX}/tmp" 2>/dev/null || true
  [ -d "${ROOTFS}/tmp" ] || mkdir -p "${ROOTFS}/tmp" 2>/dev/null || true
  export TERMUX_APP__PACKAGE_NAME=com.ivarna.nativecode
  export TERMUX__PREFIX="$PREFIX"
  export TERMUX__HOME
  export PREFIX
  export PATH="${PREFIX}/bin:${PREFIX}/bin/applets:/system/bin:/system/xbin:${PATH:-}"
  export LD_LIBRARY_PATH="${PREFIX}/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  export TMPDIR="${PREFIX}/tmp"
  export PROOT_TMP_DIR="${PREFIX}/tmp"
  export LANG="${LANG:-C.UTF-8}"
}

load_flux_ids() {
  if [ "$IS_ROOT_EXEC" = "1" ]; then
    GUEST_UID=0
    GUEST_GID=0
    GUEST_HOME="/root"
    GUEST_USER="root"
    return 0
  fi
  pw="${ROOTFS}/etc/passwd"
  [ -r "$pw" ] || die "cannot read $pw"
  line=$(grep '^flux:' "$pw" 2>/dev/null || true)
  [ -n "$line" ] || die "flux user missing in guest passwd"
  GUEST_UID=$(echo "$line" | cut -d: -f3)
  GUEST_GID=$(echo "$line" | cut -d: -f4)
  GUEST_HOME=$(echo "$line" | cut -d: -f6)
  GUEST_USER=flux
  [ -n "$GUEST_UID" ] && [ -n "$GUEST_GID" ] || die "bad flux passwd line"
  [ -n "$GUEST_HOME" ] || GUEST_HOME=/home/flux
}

# Append bind if source exists. Usage: add_bind SRC [DST]
add_bind() {
  src=$1
  dst=${2:-}
  if [ ! -e "$src" ]; then
    return 0
  fi
  if [ -n "$dst" ]; then
    BINDS="$BINDS --bind=${src}:${dst}"
  else
    BINDS="$BINDS --bind=${src}"
  fi
}

build_binds_cli() {
  BINDS="--bind=/dev --bind=/proc --bind=/sys"
  add_bind /dev/urandom /dev/random
  # shared tmp (host PREFIX/tmp often faster)
  add_bind "${PREFIX}/tmp" /tmp
  add_bind "${ROOTFS}/tmp" /dev/shm
  # empty selinux if present (cheap, avoids probes)
  if [ -d "${PREFIX}/var/lib/proot-distro/sysdata/sys_empty" ]; then
    add_bind "${PREFIX}/var/lib/proot-distro/sysdata/sys_empty" /sys/fs/selinux
  elif [ -d "${PREFIX}/lib/proot-distro/sysdata/sys_empty" ]; then
    add_bind "${PREFIX}/lib/proot-distro/sysdata/sys_empty" /sys/fs/selinux
  fi
  # storage: single bind if readable
  if [ -d /storage/emulated/0 ] && [ -r /storage/emulated/0 ]; then
    add_bind /storage/emulated/0 /sdcard
  elif [ -d /sdcard ] && [ -r /sdcard ]; then
    add_bind /sdcard /sdcard
  fi
}

build_binds_android_sys() {
  add_bind /system
  add_bind /vendor
  add_bind /apex
  # linkerconfig fragments used by Android loader (Turnip path)
  if [ -f /linkerconfig/ld.config.txt ]; then
    add_bind /linkerconfig/ld.config.txt /system/etc/ld.config.txt
  fi
  if [ -f /apex/com.android.runtime/etc/ld.config.txt ]; then
    add_bind /apex/com.android.runtime/etc/ld.config.txt
  fi
  # kgsl if present
  add_bind /dev/kgsl-3d0
  add_bind /dev/dri
}

build_binds_virgl() {
  # X11 socket from host shared tmp
  if [ -d "${PREFIX}/tmp/.X11-unix" ]; then
    add_bind "${PREFIX}/tmp/.X11-unix" /tmp/.X11-unix
  fi
  # virtgpu / render nodes if present
  add_bind /dev/dri
}

build_binds_compat() {
  # proot-distro-like full set (best-effort)
  build_binds_cli
  build_binds_android_sys
  add_bind /odm
  add_bind /product
  add_bind /system_ext
  add_bind /data/app
  add_bind "/data/data/${NC_PACKAGE}/cache"
  add_bind "${TERMUX__HOME}"
  add_bind "${PREFIX}"
  # dalvik caches (if exist)
  add_bind /data/dalvik-cache
  add_bind /data/data/com.ivarna.nativecode/cache/dalvik-cache
}

build_binds() {
  BINDS=""
  case "$PROFILE" in
    cli)
      build_binds_cli
      ;;
    gpu-turnip)
      build_binds_cli
      build_binds_android_sys
      ;;
    gpu-virgl)
      build_binds_cli
      build_binds_virgl
      ;;
    compat)
      build_binds_compat
      ;;
    *)
      die "unknown profile: $PROFILE (cli|gpu-turnip|gpu-virgl|compat)"
      ;;
  esac
}

# GPU env for guest (exported into env -i list)
guest_gpu_env_args() {
  # prints KEY=VAL pairs for env -i
  case "$PROFILE" in
    gpu-turnip)
      printf '%s\n' \
        "MESA_LOADER_DRIVER_OVERRIDE=zink" \
        "GALLIUM_DRIVER=zink" \
        "VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json" \
        "TU_DEBUG=noconform" \
        "MESA_VK_WSI_DEBUG=sw" \
        "MESA_GL_VERSION_OVERRIDE=4.6" \
        "MESA_GLES_VERSION_OVERRIDE=3.2" \
        "MESA_SHADER_CACHE_DIR=/tmp/mesa_shader_cache"
      ;;
    gpu-virgl)
      # do not force Turnip ICD; pass through common virgl-friendly vars if set
      if [ -n "${GALLIUM_DRIVER:-}" ]; then printf 'GALLIUM_DRIVER=%s\n' "$GALLIUM_DRIVER"; fi
      if [ -n "${MESA_LOADER_DRIVER_OVERRIDE:-}" ]; then printf 'MESA_LOADER_DRIVER_OVERRIDE=%s\n' "$MESA_LOADER_DRIVER_OVERRIDE"; fi
      if [ -n "${DISPLAY:-}" ]; then printf 'DISPLAY=%s\n' "$DISPLAY"; fi
      if [ -n "${XDG_RUNTIME_DIR:-}" ]; then printf 'XDG_RUNTIME_DIR=%s\n' "$XDG_RUNTIME_DIR"; fi
      printf 'MESA_SHADER_CACHE_DIR=/tmp/mesa_shader_cache\n'
      ;;
  esac
}

build_proot_flags() {
  FLAGS="--kill-on-exit --link2symlink -L"
  # sysvipc: compat always; others opt-in
  if [ "$PROFILE" = "compat" ] || [ "${NC_PROOT_SYSVIPC:-0}" = "1" ]; then
    FLAGS="$FLAGS --sysvipc"
  fi
  if [ "${NC_PROOT_FAKE_UNAME:-0}" = "1" ]; then
    # lightweight fake; optional
    kr=$(uname -r 2>/dev/null || echo 6.1.0)
    FLAGS="$FLAGS --kernel-release=Linux\\localhost\\${kr}-PRoot-Fast"
  fi
  FLAGS="$FLAGS --change-id=${GUEST_UID}:${GUEST_GID}"
  FLAGS="$FLAGS --rootfs=${ROOTFS}"
  FLAGS="$FLAGS --cwd=${GUEST_HOME}"
}

# Resolve guest PATH without loading zsh/oh-my-zsh (keep AI CLI + nvm default node).
build_guest_path() {
  gpath="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
  # user bins (claude, grok, cargo, …)
  gpath="${GUEST_HOME}/.local/bin:${GUEST_HOME}/bin:${GUEST_HOME}/.cargo/bin:/opt/nodejs/bin:${gpath}"
  # latest nvm node bin (host-side scan of rootfs; no nvm.sh)
  nvm_base="${ROOTFS}${GUEST_HOME}/.nvm/versions/node"
  if [ -d "$nvm_base" ]; then
    # shellcheck disable=SC2012
    latest=$(ls -1d "$nvm_base"/v* 2>/dev/null | sort -V | tail -1)
    if [ -n "$latest" ] && [ -d "$latest/bin" ]; then
      # map host path under ROOTFS → guest path
      guest_node_bin="${GUEST_HOME}/.nvm/versions/node/$(basename "$latest")/bin"
      gpath="${guest_node_bin}:${gpath}"
    fi
  fi
  GUEST_PATH="$gpath"
}

# Build guest env -i argv pieces into ENV_ARGS (space-separated KEY=VAL)
build_guest_env() {
  build_guest_path
  ENV_ARGS="HOME=${GUEST_HOME}"
  ENV_ARGS="$ENV_ARGS USER=${GUEST_USER}"
  ENV_ARGS="$ENV_ARGS LOGNAME=${GUEST_USER}"
  ENV_ARGS="$ENV_ARGS PATH=${GUEST_PATH}"
  ENV_ARGS="$ENV_ARGS LANG=C.UTF-8"
  ENV_ARGS="$ENV_ARGS TERM=${TERM:-xterm-256color}"
  ENV_ARGS="$ENV_ARGS TMPDIR=/tmp"
  ENV_ARGS="$ENV_ARGS XDG_RUNTIME_DIR=/tmp"
  ENV_ARGS="$ENV_ARGS NVM_DIR=${GUEST_HOME}/.nvm"
  ENV_ARGS="$ENV_ARGS DEBIAN_FRONTEND=noninteractive"
  ENV_ARGS="$ENV_ARGS FLUX_QUIET_SHELL=1"
  # shellcheck disable=SC2046
  for kv in $(guest_gpu_env_args); do
    ENV_ARGS="$ENV_ARGS $kv"
  done
}

run_proot() {
  # remaining args are guest command
  # shellcheck disable=SC2086
  exec "$PROOT_BIN" $FLAGS $BINDS "$@"
}

# --- parse ---
[ "$#" -ge 1 ] || usage
MODE=$1
shift

while [ "$#" -gt 0 ]; do
  case "$1" in
    --profile)
      [ "$#" -ge 2 ] || die "--profile needs value"
      PROFILE=$2
      shift 2
      ;;
    --profile=*)
      PROFILE=${1#--profile=}
      shift
      ;;
    --shell)
      [ "$#" -ge 2 ] || die "--shell needs value"
      LOGIN_SHELL=$2
      shift 2
      ;;
    --shell=*)
      LOGIN_SHELL=${1#--shell=}
      shift
      ;;
    --)
      shift
      break
      ;;
    -h|--help)
      usage
      ;;
    *)
      # for login, no -- required; for others, remaining might be cmd without --
      break
      ;;
  esac
done

case "$MODE" in
  root-exec)
    IS_ROOT_EXEC=1
    ;;
  exec|sh|login) ;;
  *)
    die "unknown mode: $MODE"
    ;;
esac

safety_check
load_flux_ids
build_binds
build_proot_flags
build_guest_env

case "$MODE" in
  exec|root-exec)
    if [ "$#" -eq 0 ]; then
      die "$MODE requires a command (after optional --)"
    fi
    # strip leading -- if still present
    if [ "$1" = "--" ]; then shift; fi
    [ "$#" -ge 1 ] || die "$MODE requires a command"
    # Direct exec: env -i CLEAN CMD (no GNU-only -- after assignments)
    # shellcheck disable=SC2086
    run_proot /usr/bin/env -i $ENV_ARGS "$@"
    ;;
  sh)
    if [ "$#" -eq 0 ]; then
      die "sh requires a shell string (after optional --)"
    fi
    if [ "$1" = "--" ]; then shift; fi
    # join remaining as one string if multiple words passed without quotes via adb
    CMD_STR=$*
    [ -n "$CMD_STR" ] || die "sh requires a shell string"
    # shellcheck disable=SC2086
    run_proot /usr/bin/env -i $ENV_ARGS /bin/sh -c "$CMD_STR"
    ;;
  login)
    case "$LOGIN_SHELL" in
      zsh) SH_BIN=/bin/zsh ;;
      bash) SH_BIN=/bin/bash ;;
      sh|dash) SH_BIN=/bin/sh ;;
      *) die "unsupported --shell $LOGIN_SHELL" ;;
    esac
    # interactive login shell
    # shellcheck disable=SC2086
    run_proot /usr/bin/env -i $ENV_ARGS "$SH_BIN" -l
    ;;
esac
