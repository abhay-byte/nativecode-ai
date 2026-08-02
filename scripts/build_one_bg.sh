#!/usr/bin/env bash
# Build one package in background, immune to terminal Ctrl-C.
# Usage: ./scripts/build_one_bg.sh python
set -u
PKG="${1:?usage: build_one_bg.sh PACKAGE}"
ARCH="aarch64"
cd "$(dirname "$0")/../termux-packages"
docker rm -f termux-package-builder 2>/dev/null || true
LOG="/tmp/opencode/build_${PKG}.log"
: > "$LOG"
export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host --cpus 10 --memory 10g"
nohup ./scripts/run-docker.sh ./build-package.sh -a "${ARCH}" "${PKG}" > "$LOG" 2>&1 &
echo "started ${PKG} pid $! — watch: tail -f ${LOG}"
