#!/usr/bin/env bash
# Host-side P0 checks via adb (package identity, helpers, binary integrity).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# shellcheck source=/dev/null
source "$ROOT/lib/common.sh"
# shellcheck source=/dev/null
source "$ROOT/lib/adb_env.sh"

SUITE_SUMMARY_MD=${SUITE_SUMMARY_MD:-/dev/stdout}
SUITE_JSONL=${SUITE_JSONL:-/dev/null}

echo "### Host P0"
echo "| Lvl | ID | Name | Status | Detail |"
echo "|-----|----|------|--------|--------|"

# P0-01 package
pkg_line=$(adb_sh "test -f ${NC_PREFIX}/etc/fluxlinux-host.env && grep TERMUX_APP__PACKAGE_NAME ${NC_PREFIX}/etc/fluxlinux-host.env" || true)
if echo "$pkg_line" | grep -q "$NC_PACKAGE"; then
  record P0 P0-01 host_package PASS "$pkg_line"
else
  record P0 P0-01 host_package FAIL "$pkg_line"
fi

# P0-02 proot ELF
ft=$(adb_sh "file ${NC_PREFIX}/bin/proot 2>/dev/null || echo missing")
if echo "$ft" | grep -qi ELF; then
  record P0 P0-02 proot_binary_elf PASS "$ft"
else
  record P0 P0-02 proot_binary_elf FAIL "$ft"
fi

# P0-11 distro helper
if adb_sh "test -x ${NC_PROOT_DISTRO_HELPER}"; then
  if adb_sh "/system/bin/su $(resolve_app_uid) -c '${NC_PROOT_DISTRO_HELPER} cmd true'" >/dev/null 2>&1; then
    record P0 P0-11 distro_still_works PASS "cmd true"
  else
    record P0 P0-11 distro_still_works FAIL "cmd true"
  fi
else
  record P0 P0-11 distro_still_works SKIP "helper missing"
fi

# P0-12 chroot
if adb_sh "test -d ${NC_CHROOT}/bin"; then
  adb_sh "${NC_BB} mount --bind /dev ${NC_CHROOT}/dev 2>/dev/null; ${NC_BB} mount -t proc proc ${NC_CHROOT}/proc 2>/dev/null; true" || true
  if adb_sh "${NC_BB} chroot ${NC_CHROOT} /bin/true" >/dev/null 2>&1; then
    record P0 P0-12 chroot_still_works PASS "chroot true"
  else
    record P0 P0-12 chroot_still_works FAIL "chroot true"
  fi
else
  record P0 P0-12 chroot_still_works SKIP "no chroot"
fi

# P0-13 rootfs path
if adb_sh "test -d ${NC_ROOTFS}" && echo "$NC_ROOTFS" | grep -q "$NC_PACKAGE"; then
  record P0 P0-13 no_termux_default PASS "$NC_ROOTFS"
else
  record P0 P0-13 no_termux_default FAIL "$NC_ROOTFS"
fi

# P0-14 sanity: wrapper not left behind
if adb_sh "head -1 ${NC_PREFIX}/bin/proot 2>/dev/null | grep -q '^#!'"; then
  record P0 P0-14 kill_clean FAIL "proot is shell script wrapper"
else
  record P0 P0-14 kill_clean PASS "proot not wrapper"
fi

# static: no drm glmark in suite
if grep -R "glmark2-.*-drm" "$ROOT" --include='*.sh' 2>/dev/null | grep -v 'must not' | grep -q .; then
  record P0 P1-52 no_drm_glmark FAIL "drm glmark found in suite scripts"
else
  record P0 P1-52 no_drm_glmark PASS "no drm glmark in suite"
fi

echo "HOST_P0_DONE pass=$SUITE_PASS fail=$SUITE_FAIL skip=$SUITE_SKIP"
[ "$SUITE_FAIL" -eq 0 ]
