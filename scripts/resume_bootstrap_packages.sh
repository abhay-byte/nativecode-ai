#!/usr/bin/env bash
# Resume rebuild: remaining packages (continue on failure, log + live view).
set -uo pipefail
ARCH="aarch64"
cd "$(dirname "$0")/../termux-packages"
LOG="build_resume.log"
: > "$LOG"

FILTER=(-viE 'warning|expanded|R\(a,b\)|C\(x\)|D\(x\)|E\(x\)|F\(x\)|SKIP|^\s+[0-9]+ \||INFO: (Done|Found|Running|Identifying|Generating|Showing|Total)')

docker rm -f termux-package-builder 2>/dev/null || true
export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host --cpus 10 --memory 10g"

for pkg in $(cat /tmp/opencode/todo.txt); do
  docker rm -f termux-package-builder 2>/dev/null || true
  echo "=== building $pkg ($(date +%T)) ===" | tee -a "$LOG"
  if ./scripts/run-docker.sh ./build-package.sh -a "${ARCH}" "${pkg}" 2>&1 | tee -a "$LOG" | grep "${FILTER[@]}"; then
    echo "OK  $pkg" | tee -a "$LOG"
  else
    echo "FAIL $pkg" | tee -a "$LOG"
  fi
done
echo "DONE. failures:"
grep "^FAIL" "$LOG" || echo "none"
