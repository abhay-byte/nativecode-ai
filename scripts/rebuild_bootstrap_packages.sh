#!/usr/bin/env bash
# Rebuild all termux packages with the new package namespace prefix.
# Requires docker (see docker install commands). Run from repo root.
set -e

ARCH="aarch64"
cd termux-packages

echo "[*] Cleaning old build container..."
docker rm -f termux-package-builder 2>/dev/null || true

echo "[*] Rebuilding $(wc -l < /tmp/opencode/packages.txt) packages with prefix /data/data/com.zenithblue.nativecode/files/usr ..."
export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host"

for pkg in $(cat /tmp/opencode/packages.txt); do
  echo "=== building $pkg ==="
  ./scripts/run-docker.sh ./build-package.sh -a "${ARCH}" "${pkg}"
done

echo "[*] Done. Debs in termux-packages/output/"
