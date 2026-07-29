#!/usr/bin/env bash
# NativeCode proot/chroot regression runner (host → adb).
# Usage:
#   NC_ADB_SERIAL=192.168.1.52:43055 ./run_regression.sh --p0
#   ./run_regression.sh --p0 --p1
#   ./run_regression.sh --all
#   NC_ENVS=proot-distro,chroot,proot-fast ./run_regression.sh --p0 --p1
set -euo pipefail

ROOT=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=/dev/null
source "$ROOT/lib/common.sh"
# shellcheck source=/dev/null
source "$ROOT/lib/adb_env.sh"

RUN_P0=0
RUN_P1=0
RUN_P2=0
for a in "$@"; do
  case "$a" in
    --p0) RUN_P0=1 ;;
    --p1) RUN_P1=1 ;;
    --p2) RUN_P2=1 ;;
    --all) RUN_P0=1; RUN_P1=1; RUN_P2=1 ;;
    -h|--help)
      sed -n '1,20p' "$0"
      exit 0
      ;;
  esac
done
# default: p0 only
if [ "$RUN_P0$RUN_P1$RUN_P2" = "000" ]; then RUN_P0=1; fi

NC_ENVS=${NC_ENVS:-proot-distro,chroot}
IFS=',' read -r -a ENVS <<<"$NC_ENVS"

TS=$(date -u +%Y%m%dT%H%M%SZ)
OUT=${NC_RESULTS_DIR:-"$ROOT/results/$TS"}
mkdir -p "$OUT"
export SUITE_SUMMARY_MD="$OUT/summary.md"
export SUITE_JSONL="$OUT/results.jsonl"
: >"$SUITE_JSONL"

{
  echo "# Regression results — $TS"
  echo
  echo "- ADB: \`${NC_ADB_SERIAL:-default}\`"
  echo "- Envs: \`${NC_ENVS}\`"
  echo "- Offline: \`${NC_OFFLINE:-0}\`"
  echo "- Package: \`${NC_PACKAGE}\`"
  echo
} >"$SUITE_SUMMARY_MD"

echo "Results → $OUT"

# --- Host P0 ---
if [ "$RUN_P0" = "1" ]; then
  bash "$ROOT/host/p0_host.sh" | tee -a "$OUT/host-p0.log" || true
fi

# Push guest scripts
push_guest() {
  adb_sh "mkdir -p /data/local/tmp/nc_suite"
  if [ -n "${NC_ADB_SERIAL:-}" ]; then
    ADB=(adb -s "$NC_ADB_SERIAL")
  else
    ADB=(adb)
  fi
  "${ADB[@]}" push "$ROOT/guest/p0_core.sh" /data/local/tmp/nc_suite/p0_core.sh >/dev/null
  "${ADB[@]}" push "$ROOT/guest/p1_dev.sh" /data/local/tmp/nc_suite/p1_dev.sh >/dev/null
  "${ADB[@]}" push "$ROOT/guest/p1_ai.sh" /data/local/tmp/nc_suite/p1_ai.sh >/dev/null
  "${ADB[@]}" push "$ROOT/guest/ai_offline_smoke.sh" /data/local/tmp/nc_suite/ai_offline_smoke.sh >/dev/null
  adb_sh "chmod 755 /data/local/tmp/nc_suite/*.sh"
}

push_guest

run_guest_script() {
  env=$1
  script=$2
  label=$3
  logf="$OUT/env-${env}-${label}.log"
  echo "### env=$env script=$label" | tee -a "$SUITE_SUMMARY_MD"
  if ! env_available "$env"; then
    echo "SKIP env $env not available" | tee -a "$logf" "$SUITE_SUMMARY_MD"
    return 0
  fi
  # Copy smoke into guest tmp when possible
  case "$env" in
    proot-distro|proot-fast)
      uid=$(resolve_app_uid)
      # shared-tmp binds $PREFIX/tmp → /tmp (not rootfs/tmp). Stage there.
      adb_sh "cp /data/local/tmp/nc_suite/ai_offline_smoke.sh ${NC_PREFIX}/tmp/ai_offline_smoke.sh 2>/dev/null; chmod 755 ${NC_PREFIX}/tmp/ai_offline_smoke.sh 2>/dev/null; true"
      adb_sh "cp /data/local/tmp/nc_suite/${script} ${NC_PREFIX}/tmp/${script}; chmod 755 ${NC_PREFIX}/tmp/${script}"
      if [ "$env" = "proot-fast" ]; then
        adb_sh "/system/bin/su ${uid} -c '${NC_PROOT_FAST_HELPER} sh -- \"NC_OFFLINE=${NC_OFFLINE:-0} sh /tmp/${script} /tmp/${script}.rep\"'" 2>&1 | tee "$logf" || true
      else
        adb_sh "/system/bin/su ${uid} -c '${NC_PROOT_DISTRO_HELPER} cmd \"NC_OFFLINE=${NC_OFFLINE:-0} sh /tmp/${script} /tmp/${script}.rep\"'" 2>&1 | tee "$logf" || true
      fi
      ;;
    chroot)
      adb_sh "cp /data/local/tmp/nc_suite/ai_offline_smoke.sh ${NC_CHROOT}/tmp/ai_offline_smoke.sh 2>/dev/null; chmod 755 ${NC_CHROOT}/tmp/ai_offline_smoke.sh 2>/dev/null; true"
      adb_sh "cp /data/local/tmp/nc_suite/${script} ${NC_CHROOT}/tmp/${script}; chmod 755 ${NC_CHROOT}/tmp/${script}"
      adb_sh "${NC_BB} mount --bind /dev ${NC_CHROOT}/dev 2>/dev/null; ${NC_BB} mount -t proc proc ${NC_CHROOT}/proc 2>/dev/null; true"
      adb_sh "${NC_BB} chroot ${NC_CHROOT} /bin/su - flux -c \"NC_OFFLINE=${NC_OFFLINE:-0} sh /tmp/${script} /tmp/${script}.rep\"" 2>&1 | tee "$logf" || true
      ;;
  esac
  if grep -q '^FAIL ' "$logf" 2>/dev/null; then
    echo "RESULT $env $label FAIL" | tee -a "$SUITE_SUMMARY_MD"
    return 1
  fi
  if grep -q 'SUMMARY pass=' "$logf" 2>/dev/null; then
    echo "RESULT $env $label $(grep SUMMARY "$logf" | tail -1)" | tee -a "$SUITE_SUMMARY_MD"
  else
    echo "RESULT $env $label UNKNOWN (see log)" | tee -a "$SUITE_SUMMARY_MD"
  fi
  return 0
}

OVERALL=0

for env in "${ENVS[@]}"; do
  env=$(echo "$env" | tr -d ' ')
  [ -n "$env" ] || continue
  if [ "$RUN_P0" = "1" ]; then
    run_guest_script "$env" p0_core.sh p0 || OVERALL=1
  fi
  if [ "$RUN_P1" = "1" ]; then
    run_guest_script "$env" p1_dev.sh p1_dev || OVERALL=1
    run_guest_script "$env" p1_ai.sh p1_ai || OVERALL=1
  fi
  if [ "$RUN_P2" = "1" ]; then
    echo "### P2 perf placeholders for $env" | tee -a "$SUITE_SUMMARY_MD"
    echo "SKIP P2: use Task 4 bench scripts / bench_nongpu.sh" | tee -a "$SUITE_SUMMARY_MD"
  fi
done

{
  echo
  echo "## Harness note"
  echo "Guest scripts print PASS/FAIL lines; host P0 recorded in table above."
  echo "Exit policy: fail if any guest log contains FAIL lines or host P0 failed."
} | tee -a "$SUITE_SUMMARY_MD"

# Aggregate FAIL from logs
if grep -R "^FAIL " "$OUT" --include='*.log' >/dev/null 2>&1; then
  OVERALL=1
fi

if [ "$OVERALL" -eq 0 ]; then
  echo "REGRESSION OVERALL: PASS" | tee -a "$SUITE_SUMMARY_MD"
else
  echo "REGRESSION OVERALL: FAIL" | tee -a "$SUITE_SUMMARY_MD"
fi

exit "$OVERALL"
