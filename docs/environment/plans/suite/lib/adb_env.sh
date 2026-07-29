# shellcheck shell=sh
# ADB-backed env runners for chroot / proot-distro / proot-fast.

adb_sh() {
  if [ -n "${NC_ADB_SERIAL:-}" ]; then
    adb -s "$NC_ADB_SERIAL" shell "$@"
  else
    adb shell "$@"
  fi
}

detect_app_uid() {
  # prints u0_aXXX
  adb_sh "stat -c %U ${NC_PREFIX} 2>/dev/null || ls -ld ${NC_PREFIX} | awk '{print \$3}'"
}

resolve_app_uid() {
  if [ -n "${NC_APP_UID:-}" ]; then
    printf '%s\n' "$NC_APP_UID"
    return 0
  fi
  detect_app_uid
}

# Run command string inside guest env.
# Usage: run_in_env ENV "shell command string"
run_in_env() {
  _env=$1
  _cmd=$2
  _uid=$(resolve_app_uid)

  case "$_env" in
    proot-distro)
      adb_sh "/system/bin/su ${_uid} -c '${NC_PROOT_DISTRO_HELPER} cmd $(printf %q "$_cmd")'"
      ;;
    proot-fast)
      # exec -- uses direct path when helper exists
      adb_sh "/system/bin/su ${_uid} -c 'if [ -x ${NC_PROOT_FAST_HELPER} ]; then ${NC_PROOT_FAST_HELPER} sh -- $(printf %q "$_cmd"); else echo PROOT_FAST_MISSING; exit 99; fi'"
      ;;
    chroot)
      adb_sh "${NC_BB} chroot ${NC_CHROOT} /bin/su - flux -c $(printf %q "$_cmd")"
      ;;
    *)
      echo "unknown env: $_env" >&2
      return 2
      ;;
  esac
}

env_available() {
  _env=$1
  case "$_env" in
    proot-distro)
      adb_sh "test -x ${NC_PROOT_DISTRO_HELPER} -a -d ${NC_ROOTFS}" >/dev/null 2>&1
      ;;
    proot-fast)
      adb_sh "test -x ${NC_PROOT_FAST_HELPER} -a -d ${NC_ROOTFS}" >/dev/null 2>&1
      ;;
    chroot)
      adb_sh "test -d ${NC_CHROOT}/bin -a -x ${NC_BB}" >/dev/null 2>&1
      ;;
    *) return 1 ;;
  esac
}
