#!/bin/sh
# optimize_debian_rootfs_perf.sh — Task 3 rootfs hardening (guest Debian)
# Run as root inside proot/chroot (e.g. nativecode_proot_fast.sh root-exec).
#
# Usage:
#   optimize_debian_rootfs_perf.sh [--safe|--aggressive] [--dry-run] [--quiet]
#
# --safe (default): caches, apt hygiene, policy-rc.d, tmp perms, dash check.
# --aggressive: ALSO eatmydata apt wrapper conf + force-unsafe-io + git fsync=none.
# Never default aggressive in app onboarding.
set -eu

# Minimal chroot/proot often has empty PATH
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"

MODE=safe
DRY=0
QUIET=0
LOG=${NC_OPTIMIZE_LOG:-/var/log/nativecode-rootfs-optimize.log}
CHANGED=0
SKIPPED=0
FAILED=0

log() {
  if [ "$QUIET" != "1" ]; then
    printf '%s\n' "$*"
  fi
  # best-effort log file
  if [ -w "$(dirname "$LOG")" ] 2>/dev/null || mkdir -p "$(dirname "$LOG")" 2>/dev/null; then
    printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo now)" "$*" >>"$LOG" 2>/dev/null || true
  fi
}

ok() { log "OK  $*"; }
note() { log "NOTE $*"; }
skip() { log "SKIP $*"; SKIPPED=$((SKIPPED + 1)); }
fail() { log "FAIL $*"; FAILED=$((FAILED + 1)); }
chg() { log "CHG $*"; CHANGED=$((CHANGED + 1)); }

die() {
  log "ERROR $*"
  exit 2
}

usage() {
  cat <<'EOF'
usage: optimize_debian_rootfs_perf.sh [--safe|--aggressive] [--dry-run] [--quiet]
  --safe         default: safe caches + apt/dpkg hygiene (no data-risk flags)
  --aggressive   opt-in: force-unsafe-io, eatmydata apt, git core.fsync=none
  --dry-run      print actions only
  --quiet        less stdout (still logs if possible)
EOF
  exit 2
}

run() {
  if [ "$DRY" = "1" ]; then
    log "DRY $*"
    return 0
  fi
  "$@"
}

write_file() {
  # write_file PATH CONTENT_via_stdin or use heredoc from caller
  path=$1
  if [ "$DRY" = "1" ]; then
    log "DRY write $path"
    cat >/dev/null
    return 0
  fi
  dir=$(dirname "$path")
  mkdir -p "$dir"
  cat >"$path"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --safe) MODE=safe; shift ;;
    --aggressive) MODE=aggressive; shift ;;
    --dry-run) DRY=1; shift ;;
    --quiet) QUIET=1; shift ;;
    -h|--help) usage ;;
    *) die "unknown arg: $1" ;;
  esac
done

# --- safety: must look like a Debian guest, prefer root ---
if [ "$(id -u)" != "0" ]; then
  die "must run as root inside guest (use root-exec / sudo)"
fi
if [ ! -f /etc/debian_version ] && [ ! -f /etc/os-release ]; then
  die "not a Debian-like rootfs (no /etc/debian_version)"
fi
# refuse host Android rootfs confusion
if [ -d /system/bin ] && [ ! -d /usr/bin ]; then
  die "looks like Android host, not Debian guest"
fi

log "=== nativecode rootfs optimize mode=$MODE dry=$DRY ==="
log "host=$(hostname 2>/dev/null || echo unknown) debian=$(cat /etc/debian_version 2>/dev/null || echo ?)"

# ---------------------------------------------------------------------------
# 1) /bin/sh → dash (do not force if dash missing; do not remove zsh)
# ---------------------------------------------------------------------------
check_and_fix_dash() {
  if [ ! -e /bin/sh ]; then
    fail "/bin/sh missing"
    return 0
  fi
  target=$(readlink /bin/sh 2>/dev/null || true)
  if [ -z "$target" ]; then
    # not a symlink — inspect file
    note "/bin/sh is not a symlink ($(ls -la /bin/sh 2>/dev/null || true))"
    return 0
  fi
  case "$target" in
    *dash*|*busybox*|*ash*)
      ok "/bin/sh -> $target"
      ;;
    *bash*)
      if command -v dash >/dev/null 2>&1 || [ -x /bin/dash ]; then
        note "/bin/sh -> $target; re-pointing to dash (safe)"
        if [ "$DRY" = "1" ]; then
          log "DRY ln -sf dash /bin/sh"
        else
          ln -sf dash /bin/sh
          chg "/bin/sh -> dash"
        fi
      else
        skip "bash as sh but dash not installed — leave alone"
      fi
      ;;
    *)
      note "/bin/sh -> $target (leave; not dash/bash)"
      ;;
  esac
  # never touch flux login shell
  if [ -f /etc/passwd ]; then
    flux_shell=$(awk -F: '$1=="flux"{print $7}' /etc/passwd)
    note "flux shell=$flux_shell (unchanged)"
  fi
}

# ---------------------------------------------------------------------------
# 2) tmp perms
# ---------------------------------------------------------------------------
fix_tmp() {
  for d in /tmp /var/tmp; do
    if [ ! -d "$d" ]; then
      run mkdir -p "$d" && chg "mkdir $d" || fail "mkdir $d"
    fi
    # 1777 sticky world-writable
    cur=$(stat -c '%a' "$d" 2>/dev/null || echo unknown)
    if [ "$cur" != "1777" ]; then
      run chmod 1777 "$d" && chg "chmod 1777 $d (was $cur)" || fail "chmod $d"
    else
      ok "$d mode 1777"
    fi
  done
  # build cache dirs often used by compilers (owned root; world-writable sticky optional)
  for d in /tmp/ccache /tmp/mesa_shader_cache; do
    if [ ! -d "$d" ]; then
      if [ "$DRY" = "1" ]; then
        log "DRY mkdir $d"
      else
        mkdir -p "$d"
        chmod 1777 "$d" 2>/dev/null || chmod 777 "$d" 2>/dev/null || true
        chg "mkdir $d"
      fi
    fi
  done
}

# ---------------------------------------------------------------------------
# 3) ldconfig + optional caches
# ---------------------------------------------------------------------------
run_ldconfig() {
  if command -v ldconfig >/dev/null 2>&1; then
    if run ldconfig; then
      ok "ldconfig"
      CHANGED=$((CHANGED + 1))
    else
      fail "ldconfig"
    fi
  else
    skip "ldconfig not installed"
  fi
}

run_mime_font_caches() {
  if command -v update-mime-database >/dev/null 2>&1 && [ -d /usr/share/mime ]; then
    if run update-mime-database /usr/share/mime 2>/dev/null; then
      ok "update-mime-database"
      CHANGED=$((CHANGED + 1))
    else
      skip "update-mime-database failed (non-fatal)"
    fi
  else
    skip "mime tools/db absent"
  fi
  if command -v fc-cache >/dev/null 2>&1; then
    if run fc-cache -f 2>/dev/null; then
      ok "fc-cache"
      CHANGED=$((CHANGED + 1))
    else
      skip "fc-cache failed (non-fatal)"
    fi
  else
    skip "fc-cache absent"
  fi
}

# ---------------------------------------------------------------------------
# 4) locales: prefer C.UTF-8 only (do not wipe existing if already set)
# ---------------------------------------------------------------------------
tune_locales() {
  # ensure C.UTF-8 available; avoid generating all locales
  if [ -f /etc/locale.gen ]; then
    if grep -qE '^[^#]*C\.UTF-8' /etc/locale.gen 2>/dev/null; then
      ok "locale.gen has C.UTF-8"
    else
      # enable C.UTF-8 line if commented or missing
      if grep -q 'C\.UTF-8' /etc/locale.gen 2>/dev/null; then
        if [ "$DRY" = "1" ]; then
          log "DRY sed enable C.UTF-8 in locale.gen"
        else
          sed -i 's/^# *\(C\.UTF-8.*\)/\1/' /etc/locale.gen 2>/dev/null || true
          chg "enabled C.UTF-8 in locale.gen"
        fi
      else
        if [ "$DRY" = "1" ]; then
          log "DRY append C.UTF-8 UTF-8 to locale.gen"
        else
          echo 'C.UTF-8 UTF-8' >>/etc/locale.gen
          chg "appended C.UTF-8 to locale.gen"
        fi
      fi
    fi
  fi
  if command -v locale-gen >/dev/null 2>&1; then
    # only generate if C.UTF-8 not already working
    if locale -a 2>/dev/null | grep -qi 'c.utf'; then
      ok "C.UTF-8 locale present"
    else
      if run locale-gen C.UTF-8 2>/dev/null || run locale-gen 2>/dev/null; then
        ok "locale-gen"
        CHANGED=$((CHANGED + 1))
      else
        skip "locale-gen failed (non-fatal)"
      fi
    fi
  else
    skip "locale-gen absent"
  fi
  # default locale file for containers (only if missing)
  if [ ! -f /etc/default/locale ]; then
    write_file /etc/default/locale <<'EOF'
LANG=C.UTF-8
LC_ALL=C.UTF-8
EOF
    chg "wrote /etc/default/locale (C.UTF-8)"
  else
    ok "/etc/default/locale exists"
  fi
}

# ---------------------------------------------------------------------------
# 5) CA certs
# ---------------------------------------------------------------------------
run_ca() {
  if command -v update-ca-certificates >/dev/null 2>&1; then
    if run update-ca-certificates 2>/dev/null; then
      ok "update-ca-certificates"
      CHANGED=$((CHANGED + 1))
    else
      skip "update-ca-certificates failed (non-fatal)"
    fi
  else
    skip "update-ca-certificates absent"
  fi
}

# ---------------------------------------------------------------------------
# 6) apt conf (safe)
# ---------------------------------------------------------------------------
tune_apt() {
  conf=/etc/apt/apt.conf.d/99nativecode-perf
  if [ -f "$conf" ] && grep -q 'Install-Recommends' "$conf" 2>/dev/null; then
    ok "apt conf already present: $conf"
  else
    write_file "$conf" <<'EOF'
// NativeCode safe perf — fewer recommends / lang packs (Task 3)
APT::Install-Recommends "false";
APT::Install-Suggests "false";
Acquire::Languages "none";
EOF
    chg "wrote $conf"
  fi

  # proot rootfs lives on app-uid host tree: _apt (uid 42) cannot write lists/partial
  # when perms are 700 root. Disable drop-to-_apt for acquires (safe in single-user guest).
  sand=/etc/apt/apt.conf.d/15nativecode-proot-sandbox
  if [ -f "$sand" ] && grep -q 'Sandbox::User' "$sand" 2>/dev/null; then
    ok "apt sandbox conf present: $sand"
  else
    write_file "$sand" <<'EOF'
// NativeCode: apt under proot cannot use _apt sandbox on app-uid rootfs
APT::Sandbox::User "root";
EOF
    chg "wrote $sand"
  fi

  # proot --link2symlink turns dpkg status-old into .l2s symlink (often host abs path);
  # dpkg then fails: "error removing old backup file ... status-old: Operation not permitted"
  l2s=/etc/apt/apt.conf.d/16nativecode-dpkg-l2s
  if [ -f "$l2s" ] && grep -q 'status-old' "$l2s" 2>/dev/null; then
    ok "dpkg L2S hook present: $l2s"
  else
    write_file "$l2s" <<'EOF'
// NativeCode: delink proot L2S status-old before dpkg (unlink EPERM otherwise)
DPkg::Pre-Invoke {
  "if [ -L /var/lib/dpkg/status-old ]; then rm -f /var/lib/dpkg/status-old; fi";
  "if [ -L /var/lib/dpkg/status-new ]; then rm -f /var/lib/dpkg/status-new; fi";
};
EOF
    chg "wrote $l2s"
  fi

  # one-shot repair if already broken
  if [ "$DRY" != "1" ] && [ -L /var/lib/dpkg/status-old ]; then
    rm -f /var/lib/dpkg/status-old
    [ -f /var/lib/dpkg/status ] && cp -f /var/lib/dpkg/status /var/lib/dpkg/status-old || true
    chg "replaced L2S status-old with regular file"
  fi

  # repair lists/partial permissions left by mixed root/_apt runs
  if [ -d /var/lib/apt/lists ]; then
    mkdir -p /var/lib/apt/lists/partial
    if [ "$DRY" != "1" ]; then
      rm -rf /var/lib/apt/lists/partial/* 2>/dev/null || true
      chmod 755 /var/lib/apt/lists /var/lib/apt/lists/partial 2>/dev/null || true
    fi
    chg "normalized /var/lib/apt/lists permissions"
  fi
}

# ---------------------------------------------------------------------------
# 7) dpkg path-exclude (safe: man/doc/locale extras; keep en*)
# ---------------------------------------------------------------------------
tune_dpkg_excludes() {
  conf=/etc/dpkg/dpkg.cfg.d/99nativecode-path-exclude
  if [ -f "$conf" ]; then
    ok "dpkg excludes already: $conf"
    return 0
  fi
  write_file "$conf" <<'EOF'
# NativeCode: skip man/doc/i18n noise on future package installs (keeps en*).
# Does not delete already-installed files.
path-exclude=/usr/share/man/*
path-exclude=/usr/share/doc/*
path-include=/usr/share/doc/*/copyright
path-exclude=/usr/share/locale/*
path-include=/usr/share/locale/en/*
path-include=/usr/share/locale/en_US/*
path-include=/usr/share/locale/locale.alias
path-exclude=/usr/share/info/*
path-exclude=/usr/share/groff/*
path-exclude=/usr/share/linda/*
path-exclude=/usr/share/lintian/*
EOF
  chg "wrote $conf"
}

# ---------------------------------------------------------------------------
# 8) policy-rc.d → 101 (no service starts in container)
# ---------------------------------------------------------------------------
tune_policy_rc() {
  f=/usr/sbin/policy-rc.d
  if [ -x "$f" ] && grep -q 'exit 101' "$f" 2>/dev/null; then
    ok "policy-rc.d already exit 101"
    return 0
  fi
  write_file "$f" <<'EOF'
#!/bin/sh
# NativeCode container: do not start services on package install
exit 101
EOF
  if [ "$DRY" != "1" ]; then
    chmod 755 "$f"
  fi
  chg "wrote $f (exit 101)"
}

# ---------------------------------------------------------------------------
# 9) do not remove packages; record GPU/Mesa presence (T3-R07)
# ---------------------------------------------------------------------------
record_gpu_packages() {
  if command -v dpkg-query >/dev/null 2>&1; then
    pkgs=$(dpkg-query -W -f='${Package}\n' 2>/dev/null | grep -E 'mesa|vulkan|libgl|virgl|zink|turnip|freedreno' | tr '\n' ' ' || true)
    if [ -n "$pkgs" ]; then
      note "GPU-related packages present (will NOT remove): $pkgs"
    else
      note "no mesa/vulkan packages detected (ok)"
    fi
  fi
  # critical binaries stay
  for b in python3 apt-get dpkg; do
    if command -v "$b" >/dev/null 2>&1; then
      ok "keep $b=$(command -v "$b")"
    else
      note "missing $b (pre-existing)"
    fi
  done
}

# ---------------------------------------------------------------------------
# Aggressive (opt-in)
# ---------------------------------------------------------------------------
tune_aggressive() {
  if [ "$MODE" != "aggressive" ]; then
    return 0
  fi
  note "AGGRESSIVE mode: data-risk flags enabled"

  conf=/etc/dpkg/dpkg.cfg.d/99nativecode-force-unsafe-io
  write_file "$conf" <<'EOF'
# NativeCode aggressive: skip fsync in dpkg (crash can corrupt packages)
force-unsafe-io
EOF
  chg "wrote $conf (force-unsafe-io)"

  # eatmydata: only if package present; otherwise conf that documents intent
  eat=/etc/apt/apt.conf.d/98nativecode-eatmydata
  if command -v eatmydata >/dev/null 2>&1; then
    write_file "$eat" <<'EOF'
// NativeCode aggressive: apt via eatmydata when DPkg::Options used by tools
// Prefer: eatmydata apt-get install ...
EOF
    chg "eatmydata available; wrote $eat note"
  else
    skip "eatmydata not installed — not auto-installing (opt-in package)"
  fi

  if command -v git >/dev/null 2>&1; then
    if [ "$DRY" = "1" ]; then
      log "DRY git config --system core.fsync none"
    else
      git config --system core.fsync none 2>/dev/null || git config --system core.fsyncObjectFiles false 2>/dev/null || true
      chg "git core.fsync relaxed (system)"
    fi
  else
    skip "git absent for fsync tweak"
  fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
check_and_fix_dash
fix_tmp
run_ldconfig
run_mime_font_caches
tune_locales
run_ca
tune_apt
tune_dpkg_excludes
tune_policy_rc
record_gpu_packages
tune_aggressive

log "=== summary mode=$MODE changed=$CHANGED skipped=$SKIPPED failed=$FAILED dry=$DRY ==="
if [ "$FAILED" -gt 0 ]; then
  exit 1
fi
exit 0
