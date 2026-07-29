#!/bin/sh
# Guest-side P0 checks. Run inside chroot/proot as flux (or via env runner).
# Usage: p0_core.sh [report_file]
set -u
REPORT=${1:-/tmp/nc_p0_report.txt}
: >"$REPORT"
pass=0; fail=0; skip=0

ok() { echo "PASS $1 ${2:-}" | tee -a "$REPORT"; pass=$((pass+1)); }
bad() { echo "FAIL $1 ${2:-}" | tee -a "$REPORT"; fail=$((fail+1)); }
skp() { echo "SKIP $1 ${2:-}" | tee -a "$REPORT"; skip=$((skip+1)); }

# P0-03 true
if true; then ok P0-03 true; else bad P0-03 true; fi

# P0-04 echo
out=$(echo regression-ok)
case "$out" in *regression-ok*) ok P0-04 echo ;; *) bad P0-04 "got $out" ;; esac

# P0-05 user
u=$(id -un 2>/dev/null || whoami)
case "$u" in flux|root) ok P0-05 "user=$u" ;; *) bad P0-05 "user=$u" ;; esac

# P0-06 home-ish cwd or HOME
if [ -n "${HOME:-}" ] && [ -d "$HOME" ]; then ok P0-06 "HOME=$HOME"; else bad P0-06 "no HOME"; fi

# P0-07 sh
if /bin/sh -c 'echo shok' | grep -q shok; then ok P0-07 sh; else bad P0-07 sh; fi
if [ -L /bin/sh ] || [ -f /bin/sh ]; then
  t=$(readlink /bin/sh 2>/dev/null || echo file)
  ok P0-07b "sh->$t"
fi

# P0-08 tmp
T=${TMPDIR:-/tmp}/nc_p0_$$
if echo ping >"$T" && grep -q ping "$T"; then ok P0-08 tmp; else bad P0-08 tmp; fi
rm -f "$T"

# P0-09 proc
if test -r /proc/self/status; then ok P0-09 proc; else bad P0-09 proc; fi

# P0-10 dns optional
if [ "${NC_OFFLINE:-0}" = "1" ]; then
  skp P0-10 offline
else
  if command -v getent >/dev/null 2>&1 && getent hosts localhost >/dev/null 2>&1; then
    ok P0-10 dns
  elif command -v ping >/dev/null 2>&1 && ping -c1 -W2 127.0.0.1 >/dev/null 2>&1; then
    ok P0-10 ping_local
  else
    skp P0-10 no_dns_tool
  fi
fi

# P0-20 fs touch
F=$HOME/nc_reg_touch_$$
if touch "$F" && rm -f "$F"; then ok P0-20 touch; else bad P0-20 touch; fi

# P0-21 mkdir
D=$HOME/nc_reg_dir_$$
if mkdir "$D" && rmdir "$D"; then ok P0-21 mkdir; else bad P0-21 mkdir; fi

# P0-22 apt
if command -v apt-get >/dev/null 2>&1; then
  if apt-get --version >/dev/null 2>&1; then ok P0-22 apt; else bad P0-22 apt; fi
else
  skp P0-22 no_apt
fi

# P0-23 dpkg
if command -v dpkg >/dev/null 2>&1; then
  if dpkg --version >/dev/null 2>&1; then ok P0-23 dpkg; else bad P0-23 dpkg; fi
else
  skp P0-23 no_dpkg
fi

# P0-24 sudo (flux must escalate; fails if host /data is nosuid without remount)
if command -v sudo >/dev/null 2>&1; then
  if sudo -n true 2>/dev/null; then
    ok P0-24 sudo_n
  else
    # surface cause: nosuid on host /data is the usual chroot killer
    bad P0-24 "sudo -n true failed (nosuid mount or sudoers?)"
  fi
else
  skp P0-24 no_sudo
fi

# P0-24b dpkg status-old must not be proot L2S symlink (unlink EPERM)
if [ -L /var/lib/dpkg/status-old ]; then
  bad P0-24b "status-old is L2S symlink — dpkg --configure will EPERM"
elif [ -e /var/lib/dpkg/status-old ] || [ -f /var/lib/dpkg/status ]; then
  ok P0-24b status_old_ok
else
  skp P0-24b no_dpkg_status
fi

# P0-25 apt-get update as root via sudo (network; real acquire, not --version)
if command -v apt-get >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
  if [ "${NC_OFFLINE:-0}" = "1" ]; then
    skp P0-25 offline
  else
    if sudo -n dpkg --configure -a 2>/tmp/nc_dpkg_cfg.err; then
      ok P0-25a dpkg_configure
    else
      tail -5 /tmp/nc_dpkg_cfg.err 2>/dev/null | while read -r L; do echo "DPKG_ERR $L"; done
      bad P0-25a dpkg_configure_failed
    fi
    if sudo -n apt-get update -qq 2>/tmp/nc_apt_upd.err; then
      ok P0-25 apt_update
    else
      # surface last lines for harness logs
      tail -5 /tmp/nc_apt_upd.err 2>/dev/null | while read -r L; do echo "APT_ERR $L"; done
      bad P0-25 apt_update_failed
    fi
  fi
else
  skp P0-25 no_apt_or_sudo
fi

echo "SUMMARY pass=$pass fail=$fail skip=$skip" | tee -a "$REPORT"
[ "$fail" -eq 0 ]
