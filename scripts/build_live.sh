#!/usr/bin/env bash
# Build package in FOREGROUND with live logs. Ctrl-C kills the build + container.
# Usage: ./scripts/build_live.sh python
set -u
PKG="${1:?usage: build_live.sh PACKAGE}"
ARCH="aarch64"
cd "$(dirname "$0")/../termux-packages"
docker rm -f termux-package-builder 2>/dev/null || true
export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host --cpus 10 --memory 10g"
echo "building ${PKG} — Ctrl-C aborts. done when: ls -la output/${PKG}_*.deb | tail -1"
./scripts/run-docker.sh ./build-package.sh -a "${ARCH}" "${PKG}"
echo "exit: $?"
