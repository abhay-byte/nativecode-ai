# Custom Termux Package Build Plan & Script

This document details the step-by-step plan and provides an automated shell script to compile Termux packages for any user-defined custom Android package namespace (which modifies the prefix paths).

---

## The Build Plan

### Phase 1: Build Environment Setup
1. Clone the official `termux/termux-packages` repository.
2. Update the target package name variable (`TERMUX_APP__PACKAGE_NAME`) in `scripts/properties.sh` to match the user's custom package name (e.g. `com.example.termuxapp`).
3. This changes the internal target directories from `/data/data/com.termux/files/usr` to `/data/data/YOUR_CUSTOM_PACKAGE/files/usr`.

### Phase 2: Host Network Configuration
To bypass missing IPv4 forwarding permission checks on some host machines (which block default Docker bridge internet access), launch the Docker builder container using **host networking** (`--network host`).

### Phase 3: Package Compilation
Run the `./build-package.sh` script inside the container targeting the correct architecture (`aarch64` for modern ARM64 devices).

---

## Automated Compilation Script

Save this script as `build_custom_package.sh` in your project folder:

```bash
#!/usr/bin/env bash
set -e

# Configuration
CUSTOM_PACKAGE="${1:-com.example.termuxapp}"
ARCH="aarch64" # Target architectures: aarch64, arm, i686, x86_64
PACKAGE_TO_BUILD="bash"

echo "[*] Cloning termux-packages repository..."
if [ ! -d "termux-packages" ]; then
  git clone --depth 1 https://github.com/termux/termux-packages.git
fi
cd termux-packages

echo "[*] Modifying scripts/properties.sh to use custom package: ${CUSTOM_PACKAGE}..."
sed -i "s/TERMUX_APP__PACKAGE_NAME=\"com.termux\"/TERMUX_APP__PACKAGE_NAME=\"${CUSTOM_PACKAGE}\"/g" scripts/properties.sh

# Fix known upstream checksum issues for terminfo dependencies if any
sed -i 's/TERMUX_PKG_SHA256=9b9568ec5a9ff728f49c77d73644e7691fe386956e2d9acbdef0fc590e5828c8/TERMUX_PKG_SHA256=f5917cad2d7b723b99873e53d78fd10ea202923d189aed5086591fc53b70b7e3/g' x11-packages/foot/build.sh

# Remove existing container to apply new volumes and host network settings
echo "[*] Cleaning old build container..."
docker rm -f termux-package-builder || true

# Run compilation
echo "[*] Compiling ${PACKAGE_TO_BUILD} with dependency chain for prefix /data/data/${CUSTOM_PACKAGE}/files/usr..."
export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host"
./scripts/run-docker.sh ./build-package.sh -a ${ARCH} ${PACKAGE_TO_BUILD}

echo "[*] Build Complete! Output .deb files are generated in: termux-packages/output/"
```

---

## Compiled Core Packages (for `com.example.termuxapp`)

The compilation compiled `bash` along with all its recursive dependencies:

| Package | Version | Type | Arch |
| --- | --- | --- | --- |
| `bash` | `5.3.9-1` | Shell | `aarch64` |
| `readline` | `8.3.3` | Library | `aarch64` |
| `ncurses` | `6.6.2026` | UI/Terminfo | `aarch64` |
| `openssl` | `3.6.3` | Cryptography | `aarch64` |
| `libiconv` | `1.18` | Encoding | `aarch64` |
| `libandroid-support` | `29` | Compatibility | `aarch64` |
| `util-linux` | `2.42.1` | Core Utils | `aarch64` |
| `liblzma` | `5.8.3` | Compression | `aarch64` |
| `gawk` | `5.3.2` | Processing | `aarch64` |
| `libmpfr` | `4.2.1` | Math Lib | `aarch64` |
| `curl` | `8.21.0` | Network | `aarch64` |
| `coreutils` | `9.11-1` | Core Binaries | `aarch64` |
