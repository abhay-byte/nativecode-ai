# shellcheck shell=sh
# Common helpers for NativeCode regression suite (host-side via adb, or on-device).
# Safe defaults: NativeCode package only; no DRM glmark.

NC_PACKAGE="${NC_PACKAGE:-com.zenithblue.nativecode}"
NC_PREFIX="${NC_PREFIX:-/data/data/${NC_PACKAGE}/files/usr}"
NC_ROOTFS="${NC_ROOTFS:-${NC_PREFIX}/var/lib/proot-distro/containers/debian/rootfs}"
NC_CHROOT="${NC_CHROOT:-/data/local/tmp/chrootDebian13}"
NC_BB="${NC_BB:-/data/adb/ksu/bin/busybox}"
NC_PROOT_DISTRO_HELPER="${NC_PROOT_DISTRO_HELPER:-/data/local/tmp/nativecode_proot.sh}"
NC_PROOT_FAST_HELPER="${NC_PROOT_FAST_HELPER:-/data/local/tmp/nativecode_proot_fast.sh}"
NC_OFFLINE="${NC_OFFLINE:-0}"

# Result counters (host bash/sh)
SUITE_PASS=0
SUITE_FAIL=0
SUITE_SKIP=0
SUITE_XFAIL=0

log() { printf '%s\n' "$*"; }
logj() { printf '%s\n' "$*" >>"${SUITE_JSONL:-/dev/null}"; }

record() {
  # record LEVEL ID NAME STATUS DETAIL
  _lvl=$1; _id=$2; _name=$3; _st=$4; shift 4
  _detail=$*
  case "$_st" in
    PASS) SUITE_PASS=$((SUITE_PASS + 1)) ;;
    FAIL) SUITE_FAIL=$((SUITE_FAIL + 1)) ;;
    SKIP) SUITE_SKIP=$((SUITE_SKIP + 1)) ;;
    XFAIL) SUITE_XFAIL=$((SUITE_XFAIL + 1)) ;;
  esac
  printf '| %s | %s | %s | %s | %s |\n' "$_lvl" "$_id" "$_name" "$_st" "$_detail" | tee -a "${SUITE_SUMMARY_MD:-/dev/null}"
  logj "{\"level\":\"$_lvl\",\"id\":\"$_id\",\"name\":\"$_name\",\"status\":\"$_st\",\"detail\":\"$_detail\"}"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1
}

is_offline() {
  [ "$NC_OFFLINE" = "1" ]
}
