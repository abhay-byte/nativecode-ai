# FluxLinux Custom PRoot Distro Setup & Architecture

This document describes the design, implementation, and execution flow of running a faked root Linux container (Debian XFCE4) inside a custom Android application (`com.ivarna.nativecode`).

---

## 1. What We Did So Far

We successfully bypassed Android's filesystem and security restrictions to run a full Linux distribution natively under a custom application package:

1. **Custom Binary Compilation**:
   - Recompiled core Termux utilities (`bash`, `proot`, `apt`, `dpkg`, `curl`, `coreutils`, etc.) targeting the app's private package namespace: `/data/data/com.ivarna.nativecode/files/usr`.
   - Packaged these custom binaries into a `bootstrap.tar` archive.

2. **Automated Deployment & Environment Protection**:
   - Programmed the Android application to copy and extract `bootstrap.tar` to internal storage on first launch.
   - Pinned the custom packages using `apt-mark hold` to prevent `apt` upgrades from replacing them with standard (`com.termux` prefix) binaries.

3. **Graphic & Audio Integration**:
   - Configured **PulseAudio** on the host with explicit plugin paths to support sound output from the guest container.
   - Initialized the **VirGL** rendering engine with a custom socket path to provide GPU acceleration.
   - Resolved dynamic linking and execution issues for the **Termux:X11** display server, enabling XFCE4 graphical display.

4. **Debian Distro Installation**:
   - Downloaded and set up a base Debian guest system under `proot-distro`.
   - Fully customized the XFCE4 desktop with theme assets (Space-transparency, Papirus icons, Vimix cursors) and JetBrains Mono Nerd Font.

---

## 2. Technical Implementation Details (How to Do It)

### Host Path & SELinux Bypassing
By setting `targetSdk = 28` in `app/build.gradle.kts`, the app escapes Android 10+ W^X (Write-Xor-Execute) security policies, allowing execution of binary files in the private `files` directory.

### Path Translation via PRoot
We bind-mount the custom package directory to the standard Termux prefix using PRoot:
```bash
proot -b /data/data/com.ivarna.nativecode:/data/data/com.termux
```
Any utility compiled for `com.termux` intercepts this mapping and runs successfully in our custom namespace.

### SSL Verification Resolution
Since the compiled Python and Curl binaries look for certificates in the default `com.termux` prefix, we expose the SSL certificate file by exporting variables:
```bash
export SSL_CERT_FILE="/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
export CURL_CA_BUNDLE="/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
```

---

## 3. What We Are Doing Currently

We are currently executing the **First-Time Automated Setup** from within the Android application's background thread:

1. **Extraction & File Move**: Toybox `tar` extracts `bootstrap.tar` assets and moves them directly to `/data/data/com.ivarna.nativecode/files/` directory.
2. **Host Configuration**: Executing `setup_termux.sh` and `termux_tweaks.sh` to install core utilities and configure Oh My Zsh.
3. **Guest Distro Setup**: Running `flux_install.sh` to initialize the Debian guest container, apply hardware acceleration settings, and install XFCE4 styling packages.
4. **Interactive Dashboard**: Constructing an application dashboard containing start/stop buttons for the desktop environment and a bottom navigation menu to switch to the interactive terminal.
