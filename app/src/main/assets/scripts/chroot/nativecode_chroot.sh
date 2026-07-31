#!/system/bin/sh
# nativecode-chroot v1
# SSOT chroot runner for NativeCode Debian 13 (requires root).
# Do not nest this under run_debian13_root.sh — it already owns mounts + one chroot.
#
# Usage:
#   nativecode_chroot.sh version
#   nativecode_chroot.sh mount [--x11]
#   nativecode_chroot.sh login [--user flux|root] [--shell zsh|bash]
#   nativecode_chroot.sh sh    [--user flux|root] -- 'shell string'
#   nativecode_chroot.sh exec  [--user flux|root] -- CMD [ARGS...]
#   nativecode_chroot.sh b64   [--user flux|root] -- BASE64_PAYLOAD
#
# Env:
#   NC_CHROOT  NC_PACKAGE  NC_HOST_TMP  NC_PREFIX  NC_BB  NC_SHELL
set -u

VERSION_STR="nativecode-chroot v1"
NC_PACKAGE="${NC_PACKAGE:-com.ivarna.nativecode}"
NC_CHROOT="${NC_CHROOT:-/data/local/tmp/chrootDebian13}"
NC_HOST_TMP="${NC_HOST_TMP:-/data/data/${NC_PACKAGE}/files/usr/tmp}"
NC_PREFIX="${NC_PREFIX:-/data/data/${NC_PACKAGE}/files/usr}"
LOGIN_SHELL="${NC_SHELL:-zsh}"
USER_NAME="flux"
WANT_X11=0
MODE=""
BB=""

die() {
  echo "nativecode_chroot: $*" >&2
  exit 2
}

usage() {
  cat <<'EOF' >&2
usage:
  nativecode_chroot.sh version
  nativecode_chroot.sh mount [--x11]
  nativecode_chroot.sh login [--user flux|root] [--shell zsh|bash]
  nativecode_chroot.sh sh    [--user flux|root] -- SHELL_STRING
  nativecode_chroot.sh exec  [--user flux|root] -- CMD [ARGS...]
  nativecode_chroot.sh b64   [--user flux|root] -- BASE64_PAYLOAD
EOF
  exit 2
}

require_root() {
  [ "$(id -u)" = "0" ] || die "must run as root (id=$(id -u))"
}

resolve_bb() {
  if [ -n "${NC_BB:-}" ] && [ -x "$NC_BB" ]; then
    BB="$NC_BB"
    return 0
  fi
  if command -v busybox >/dev/null 2>&1; then
    _det=$(command -v busybox)
    case "$_det" in
      *com.termux*|*com.ivarna.nativecode*) ;;
      *) BB="$_det" ;;
    esac
  fi
  if [ -z "$BB" ]; then
    for path in \
      /data/adb/ksu/bin/busybox \
      /data/adb/magisk/busybox \
      /data/adb/modules/busybox-ndk/system/bin/busybox \
      /debug_ramdisk/busybox \
      /sbin/busybox \
      /system/xbin/busybox \
      /system/bin/busybox
    do
      if [ -x "$path" ]; then
        BB="$path"
        break
      fi
    done
  fi
  [ -n "$BB" ] || die "root-capable busybox not found"
}

# True if target path is already a mount point (exact match in /proc/mounts).
is_mounted() {
  _tgt="$1"
  grep -q " ${_tgt} " /proc/mounts 2>/dev/null
}

bind_if_missing() {
  _src="$1"
  _dst="$2"
  mkdir -p "$_dst" 2>/dev/null || true
  if is_mounted "$_dst"; then
    return 0
  fi
  $BB mount --bind "$_src" "$_dst" 2>/dev/null || true
}

mount_type_if_missing() {
  _type="$1"
  _src="$2"
  _dst="$3"
  _opts="${4:-}"
  mkdir -p "$_dst" 2>/dev/null || true
  if is_mounted "$_dst"; then
    return 0
  fi
  if [ -n "$_opts" ]; then
    $BB mount -t "$_type" -o "$_opts" "$_src" "$_dst" 2>/dev/null || true
  else
    $BB mount -t "$_type" "$_src" "$_dst" 2>/dev/null || true
  fi
}

ensure_sticky_tmp() {
  mkdir -p "$NC_CHROOT/tmp" "$NC_CHROOT/var/tmp" 2>/dev/null || true
  # Never keep a host bind/tmpfs on guest /tmp (breaks apt _apt mkstemp).
  if is_mounted "$NC_CHROOT/tmp"; then
    $BB umount "$NC_CHROOT/tmp" 2>/dev/null || $BB umount -l "$NC_CHROOT/tmp" 2>/dev/null || true
  fi
  chmod 1777 "$NC_CHROOT/tmp" 2>/dev/null || true
  chmod 1777 "$NC_CHROOT/var/tmp" 2>/dev/null || true
}

ensure_mounts() {
  [ -d "$NC_CHROOT" ] || die "chroot missing: $NC_CHROOT"
  mkdir -p \
    "$NC_CHROOT/dev" "$NC_CHROOT/dev/pts" "$NC_CHROOT/dev/shm" \
    "$NC_CHROOT/proc" "$NC_CHROOT/sys" \
    "$NC_CHROOT/tmp" "$NC_CHROOT/mnt/host-tmp" "$NC_CHROOT/sdcard" \
    "$NC_HOST_TMP" 2>/dev/null || true

  # Soft remount /data for dev,suid (KSU/Magisk; fail soft)
  /system/bin/mount -o remount,dev,suid /data >/dev/null 2>&1 \
    || $BB mount -o remount,dev,suid /data >/dev/null 2>&1 \
    || $BB mount -o remount,dev,suid / >/dev/null 2>&1 \
    || true

  bind_if_missing /dev "$NC_CHROOT/dev"
  bind_if_missing /sys "$NC_CHROOT/sys"
  mount_type_if_missing proc proc "$NC_CHROOT/proc"
  mount_type_if_missing devpts devpts "$NC_CHROOT/dev/pts"
  mount_type_if_missing tmpfs tmpfs "$NC_CHROOT/dev/shm" "size=512M,mode=1777"

  ensure_sticky_tmp

  bind_if_missing "$NC_HOST_TMP" "$NC_CHROOT/mnt/host-tmp"
  bind_if_missing /sdcard "$NC_CHROOT/sdcard"

  # launch_tool bridge: host-tmp → guest sticky /tmp
  if [ -f "$NC_HOST_TMP/launch_tool.sh" ]; then
    cp -f "$NC_HOST_TMP/launch_tool.sh" "$NC_CHROOT/tmp/launch_tool.sh" 2>/dev/null || true
    chmod 755 "$NC_CHROOT/tmp/launch_tool.sh" 2>/dev/null || true
  fi

  if [ "$WANT_X11" = "1" ]; then
    mkdir -p "$NC_PREFIX/tmp/.X11-unix" "$NC_CHROOT/tmp/.X11-unix" 2>/dev/null || true
    chmod 1777 "$NC_PREFIX/tmp/.X11-unix" 2>/dev/null || true
    # Refresh X11 bind so new host sockets appear
    if is_mounted "$NC_CHROOT/tmp/.X11-unix"; then
      $BB umount "$NC_CHROOT/tmp/.X11-unix" 2>/dev/null \
        || $BB umount -l "$NC_CHROOT/tmp/.X11-unix" 2>/dev/null || true
    fi
    $BB mount --bind "$NC_PREFIX/tmp/.X11-unix" "$NC_CHROOT/tmp/.X11-unix" 2>/dev/null \
      || mount --bind "$NC_PREFIX/tmp/.X11-unix" "$NC_CHROOT/tmp/.X11-unix" 2>/dev/null || true
  fi
}

# Single-quote escape for embedding into su -c '…'
sq() {
  # shellcheck disable=SC2001
  printf "%s" "$1" | sed "s/'/'\\\\''/g"
}

# shell-join argv into a single-quoted string safe for su -c
quote_argv() {
  _out=""
  for _a in "$@"; do
    _q=$(sq "$_a")
    if [ -z "$_out" ]; then
      _out="'$_q'"
    else
      _out="$_out '$_q'"
    fi
  done
  printf "%s" "$_out"
}

guest_login() {
  case "$USER_NAME" in
    root)
      case "$LOGIN_SHELL" in
        zsh) exec $BB chroot "$NC_CHROOT" /bin/zsh -l ;;
        bash|*) exec $BB chroot "$NC_CHROOT" /bin/bash --login ;;
      esac
      ;;
    flux|*)
      case "$LOGIN_SHELL" in
        bash) exec $BB chroot "$NC_CHROOT" /bin/su - "$USER_NAME" -s /bin/bash ;;
        zsh|*) exec $BB chroot "$NC_CHROOT" /bin/su - "$USER_NAME" -s /bin/zsh ;;
      esac
      ;;
  esac
}

# Non-interactive: one shell string as USER_NAME (single chroot + one su layer).
# Host-encode to base64 then guest_b64 — avoids double-quote/$/` breakage (G3).
guest_sh() {
  _cmd="$1"
  _b64=""
  if command -v base64 >/dev/null 2>&1; then
    _b64=$(printf '%s' "$_cmd" | base64 | tr -d '\n')
  elif [ -n "$BB" ]; then
    _b64=$(printf '%s' "$_cmd" | $BB base64 2>/dev/null | tr -d '\n')
  fi
  if [ -n "$_b64" ]; then
    guest_b64 "$_b64"
    return
  fi
  # Fallback only if host has no base64 (should not happen on Android root)
  if [ "$USER_NAME" = "root" ]; then
    exec $BB chroot "$NC_CHROOT" /bin/bash --noprofile --norc -c "$_cmd"
  else
    exec $BB chroot "$NC_CHROOT" /bin/su - "$USER_NAME" -s /bin/bash -c "$_cmd"
  fi
}

# Argv-preserving exec (root: chroot + binary; flux: su -c with quoted argv).
guest_exec() {
  if [ "$#" -lt 1 ]; then
    die "exec requires CMD"
  fi
  if [ "$USER_NAME" = "root" ]; then
    exec $BB chroot "$NC_CHROOT" "$@"
  else
    _joined=$(quote_argv "$@")
    exec $BB chroot "$NC_CHROOT" /bin/su - "$USER_NAME" -s /bin/bash -c "exec $_joined"
  fi
}

# Base64 payload → bash as USER_NAME (Kotlin / RootShell path).
guest_b64() {
  _b64="$1"
  [ -n "$_b64" ] || die "b64 requires payload"
  if [ "$USER_NAME" = "root" ]; then
    exec $BB chroot "$NC_CHROOT" /bin/bash --noprofile --norc -c "echo $_b64 | base64 -d | /bin/bash"
  else
    exec $BB chroot "$NC_CHROOT" /bin/su - "$USER_NAME" -s /bin/bash -c "echo $_b64 | base64 -d | /bin/bash"
  fi
}

parse_common_flags() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --user)
        [ "$#" -ge 2 ] || die "--user needs value"
        USER_NAME="$2"
        shift 2
        ;;
      --shell)
        [ "$#" -ge 2 ] || die "--shell needs value"
        LOGIN_SHELL="$2"
        shift 2
        ;;
      --x11)
        WANT_X11=1
        shift
        ;;
      --)
        shift
        REMAINING_ARGS="$@"
        return 0
        ;;
      -*)
        die "unknown flag: $1"
        ;;
      *)
        REMAINING_ARGS="$@"
        return 0
        ;;
    esac
  done
  REMAINING_ARGS=""
}

# --- main ---
[ "$#" -ge 1 ] || usage
MODE="$1"
shift

case "$MODE" in
  version|-V|--version)
    echo "$VERSION_STR"
    exit 0
    ;;
  mount|login|sh|exec|b64) ;;
  -h|--help|help) usage ;;
  *) die "unknown mode: $MODE" ;;
esac

require_root
resolve_bb

# Flag parse: collect until -- or end; modes sh/exec/b64 need --
REMAINING_ARGS=""
WANT_X11=0
USER_NAME="flux"
LOGIN_SHELL="${NC_SHELL:-zsh}"

case "$MODE" in
  mount)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --x11) WANT_X11=1; shift ;;
        *) die "mount: unknown arg $1" ;;
      esac
    done
    ensure_mounts
    exit 0
    ;;
  login)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --shell)
          [ "$#" -ge 2 ] || die "--shell needs value"
          LOGIN_SHELL="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        *) die "login: unknown arg $1" ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    ensure_mounts
    guest_login
    ;;
  sh)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        --) shift; break ;;
        *)
          # allow bare string without -- for convenience
          break
          ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    [ "$#" -ge 1 ] || die "sh requires a shell string"
    # Join remaining as one command string (caller may pass one quoted arg)
    CMD_STR="$*"
    ensure_mounts
    guest_sh "$CMD_STR"
    ;;
  exec)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        --) shift; break ;;
        *) break ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    [ "$#" -ge 1 ] || die "exec requires CMD"
    ensure_mounts
    guest_exec "$@"
    ;;
  b64)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        --) shift; break ;;
        *) break ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    [ "$#" -ge 1 ] || die "b64 requires payload"
    B64_PAYLOAD="$1"
    ensure_mounts
    guest_b64 "$B64_PAYLOAD"
    ;;
esac
