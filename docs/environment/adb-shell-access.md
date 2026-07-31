# ADB access: proot & chroot Debian shells

How to reach both Linux environments installed by **NativeCode** (`com.ivarna.nativecode`) on a connected device.

**Safety:** inspection and interactive login only. Do **not** use ADB to kill processes, delete rootfs trees, uninstall packages, or force-wipe app data unless you intend to destroy the env.

> **HARD RULE (2026-07-31):** Agent ADB chroot stress **hard-crashed** a KernelSU device (mount storm + nested `su` + multi-runner spam).  
> Read **[`chroot-adb-device-crash-postmortem.md`](./chroot-adb-device-crash-postmortem.md)** before any live chroot.  
> Forbidden: loop `run_debian13_root.sh` / remount-bind stacks, nested `su` inside chroot, parallel mount jobs, retries after ADB dies.  
> Default: read-only file checks. Live chroot: **one** trivial host-timeouted command max, then stop.  
> **SSOT design + wire map + safe test log:** [`nativecode-chroot-ssot.md`](./nativecode-chroot-ssot.md) · plan: [`docs/plan/chroot-ssot-shell-runner.md`](../plan/chroot-ssot-shell-runner.md)

---

## Device connect

```bash
# Wireless example (port changes after reboot / adb tcpip)
adb connect 192.168.1.78:41417
adb -s 192.168.1.78:41417 shell id   # often already root (KernelSU)

# Or serial / USB
adb devices
adb -s <serial> shell
```

Replace `SERIAL` below with the device id from `adb devices` (e.g. `192.168.1.78:41417`).

---

## Package paths (SSOT)

| Item | Path |
|------|------|
| App package | `com.ivarna.nativecode` |
| Host prefix | `/data/data/com.ivarna.nativecode/files/usr` |
| Host home | `/data/data/com.ivarna.nativecode/files/home` |
| **proot rootfs** | `$PREFIX/var/lib/proot-distro/containers/debian/rootfs` |
| **chroot rootfs** | `/data/local/tmp/chrootDebian13` |
| Distro name (proot) | `debian` (Debian 13 **trixie**) |
| Guest user | `flux` (default; interactive shell `zsh`) |

Code references:

- `TermuxHostPaths.kt` — package / `PREFIX`
- `ProotCommandBuilder.kt` — `proot-distro login debian --user flux`
- `ChrootCommandBuilder.kt` — `CHROOT_PATH = /data/local/tmp/chrootDebian13`

App prefs (`/data/data/com.ivarna.nativecode/shared_prefs/nativecode_prefs.xml`):

- `linux_method` → `proot` or `chroot`
- `proot_dir_present` / `chroot_dir_present`
- `chroot_installed`, markers like `…/chrootDebian13/.flux_configured`

---

## Quick presence check (read-only)

```bash
SERIAL=192.168.1.78:41417
PREFIX=/data/data/com.ivarna.nativecode/files/usr
CHROOT=/data/local/tmp/chrootDebian13

adb -s "$SERIAL" shell "
echo '=== prefs ==='
grep -E 'linux_method|proot_dir|chroot_dir|chroot_installed' \
  /data/data/com.ivarna.nativecode/shared_prefs/nativecode_prefs.xml

echo '=== proot rootfs ==='
ls -la $PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc/os-release
head -5 $PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc/os-release

echo '=== chroot rootfs ==='
ls -la $CHROOT/.flux_configured $CHROOT/etc/os-release
head -5 $CHROOT/etc/os-release
"
```

Both should report `Debian GNU/Linux 13 (trixie)` when installed.

---

## 1. Chroot shell (KernelSU / Magisk root)

Needs **root** on the adb shell. App SSOT: `/data/local/tmp/nativecode_chroot.sh` (idempotent mounts + login/sh/exec/b64).  
Kotlin hub: `ChrootCommandBuilder` / `RootShell.executeInChroot` → helper only. **Do not** re-stack raw mounts.

### Preferred (SSOT helper) — one probe max

```bash
# after app stages helper (open chroot session once) or: adb push … /data/local/tmp/nativecode_chroot.sh
timeout 12 adb shell 'sh /data/local/tmp/nativecode_chroot.sh sh --user root -- true'
timeout 12 adb shell 'sh /data/local/tmp/nativecode_chroot.sh sh --user flux -- "whoami; id -u"'
# interactive (PTY): adb shell -t 'sh /data/local/tmp/nativecode_chroot.sh login --user flux'
```

### Legacy one-shot (avoid if helper present)

```bash
SERIAL=192.168.1.78:41417
CHROOT=/data/local/tmp/chrootDebian13

adb -s "$SERIAL" shell "
# Prefer /system/bin/mount — busybox often fails "can't find /data" on KSU (sudo stays nosuid).
/system/bin/mount -o remount,dev,suid /data >/dev/null 2>&1 || busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true
busybox mount --bind /dev  $CHROOT/dev  >/dev/null 2>&1 || true
busybox mount --bind /sys  $CHROOT/sys  >/dev/null 2>&1 || true
busybox mount -t proc proc $CHROOT/proc >/dev/null 2>&1 || true
busybox mount -t devpts devpts $CHROOT/dev/pts >/dev/null 2>&1 || true
mkdir -p $CHROOT/dev/shm $CHROOT/tmp
busybox mount -t tmpfs -o size=64M tmpfs $CHROOT/dev/shm >/dev/null 2>&1 || true
busybox chroot $CHROOT /bin/su - flux -c 'whoami; pwd; head -5 /etc/os-release; id; sudo -n true'
"
```

### Interactive login

```bash
# Prefer SSOT:
#   adb shell -t 'sh /data/local/tmp/nativecode_chroot.sh login --user flux'
adb -s "$SERIAL" shell
# then on device (as root) — legacy:
CHROOT=/data/local/tmp/chrootDebian13
/system/bin/mount -o remount,dev,suid /data >/dev/null 2>&1 || busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true
busybox mount --bind /dev  $CHROOT/dev  >/dev/null 2>&1 || true
busybox mount --bind /sys  $CHROOT/sys  >/dev/null 2>&1 || true
busybox mount -t proc proc $CHROOT/proc >/dev/null 2>&1 || true
busybox mount -t devpts devpts $CHROOT/dev/pts >/dev/null 2>&1 || true
busybox chroot $CHROOT /bin/su - flux
# if sudo says nosuid: remount failed — re-run /system/bin/mount -o remount,dev,suid /data
```

### Root inside chroot

```bash
# Prefer: sh /data/local/tmp/nativecode_chroot.sh login --user root
busybox chroot $CHROOT /bin/bash -l
# or
busybox chroot $CHROOT /bin/su -
```

### Notes

- Guest home: `/home/flux` (repos often under `~/repos/`).
- Kernel is the **real** Android kernel (`uname -r` is not faked).
- Mounts are bind mounts; leave them unless you know you need `umount` (avoid recursive force-umount of the whole rootfs).

---

## 2. PRoot shell (NativeCode only — never stock Termux)

Package is **`com.ivarna.nativecode`**. Stock Termux is **`com.termux`** and must **not** appear in host paths.

SSOT on device (generated by `TermuxHostPaths.kt` / `HostCommandBuilder.kt`):

```text
/data/data/com.ivarna.nativecode/files/usr/etc/fluxlinux-host.env
```

`proot-distro` reads env **at Python import time**:

| Env (required) | Must be |
|----------------|---------|
| `TERMUX_APP__PACKAGE_NAME` | `com.ivarna.nativecode` |
| `TERMUX__PREFIX` | `/data/data/com.ivarna.nativecode/files/usr` |
| `TERMUX__HOME` | `/data/data/com.ivarna.nativecode/files/home` |
| `PREFIX` / `HOME` / `TMPDIR` / `PROOT_TMP_DIR` | same package tree |
| `PD_PROOT_BIN` / `PROOT_LOADER` | this app’s `libproot.so` / `libloader.so` |

If those are missing, defaults fall back to **`com.termux`** → wrong containers dir, failed login, and warnings like:

```text
can't sanitize binding "/data/data/com.termux/cache"
can't sanitize binding "/data/data/com.termux/files/home"
container 'debian' is not installed
```

### Do not

- Point `PREFIX` / `HOME` at `/data/data/com.termux/...`
- Run bare `proot-distro` as adb root without sourcing host env
- Assume `export PREFIX=…` alone is enough — **must** set `TERMUX_APP__PACKAGE_NAME` + `TERMUX__PREFIX` + `TERMUX__HOME`

### App UID

```bash
adb -s "$SERIAL" shell 'stat -c "%u %U" /data/data/com.ivarna.nativecode'
# e.g. 10510 u0_a510
```

Run proot as that UID (`/system/bin/su u0_a510 -c …`), not as root.

App UID **cannot** scan `/data/app` for `libproot.so` — resolve once as root and bake into the wrapper:

```bash
adb -s "$SERIAL" shell 'find /data/app -path "*com.ivarna.nativecode*" -name libproot.so'
```

### Wrapper on device: `/data/local/tmp/nativecode_proot.sh`

Sources `fluxlinux-host.env`, forces package identity, refuses `*/com.termux/*` PREFIX, checks `proot_distro.constants` before login.

```bash
# list
adb -s "$SERIAL" shell '/system/bin/su u0_a510 -c /data/local/tmp/nativecode_proot.sh list'

# one-shot (quote the whole guest command — do not use bare ; outside quotes)
adb -s "$SERIAL" shell "/system/bin/su u0_a510 -c '/data/local/tmp/nativecode_proot.sh cmd \"whoami; id; cat /etc/os-release | head -3\"'"

# interactive
adb -s "$SERIAL" shell -t '/system/bin/su u0_a510 -c /data/local/tmp/nativecode_proot.sh login'
```

Healthy login check:

```text
OK com.ivarna.nativecode /data/data/com.ivarna.nativecode/files/usr ...
uid=…(flux) …
PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
# printenv must NOT contain com.termux data paths
```

Missing `/odm` `/product` `/system` bind warnings can still appear under KSU/SELinux; they are not package-path bugs. **`com.termux` bind warnings are package-path bugs** — fix env.

### Redeploy wrapper after APK reinstall

```bash
LIBDIR=$(adb -s "$SERIAL" shell 'find /data/app -path "*com.ivarna.nativecode*" -name libproot.so' | tr -d '\r' | xargs dirname)
# embed $LIBDIR into nativecode_proot.sh, push, chmod 755
```

### Notes

- Guest rootfs: `$PREFIX/var/lib/proot-distro/containers/debian/rootfs` under **nativecode**, not termux.
- `uname -r` inside proot is fake (`*-PRoot-Distro`).
- Same guest layout idea as chroot (`/home/flux`, `~/repos/`) but **separate** tree.

---

## Side-by-side

| | **proot** | **chroot** |
|--|-----------|------------|
| Rootfs | app data under `…/proot-distro/containers/debian/rootfs` | `/data/local/tmp/chrootDebian13` |
| Privilege | app UID + proot | real root + busybox chroot |
| Enter as | `flux` via proot-distro | `flux` via `su - flux` |
| Distro | Debian 13 trixie | Debian 13 trixie |
| Isolation | user-space (proot) | kernel chroot + mounts |
| App default builder | `ProotCommandBuilder` | `ChrootCommandBuilder` |

Projects can pin method per project (`linuxMethod` in `projects_json`); global default is `linux_method` in prefs. Trees are **not** shared — a clone in chroot does not appear under proot and vice versa.

---

## What not to do over ADB

- No `rm -rf` on either rootfs, bootstrap, or app `files/`
- No `pkill` / `kill -9` on guest or app sessions unless debugging a hang and user asked
- No `proot-distro remove`, chroot uninstall scripts, or `pm clear` for casual access
- Prefer read-only probes (`ls`, `cat`, `whoami`, `head`, `mount | grep …`) first

---

## Related

- `docs/plan/gpu-accel-vendor-detect-turnip-virgl.md` — turnip vs virgl
- `docs/plan/project-clone-method-mismatch.md` — proot vs chroot project paths
- `docs/plan/terminal-auto-resize.md` — chroot session / WINCH notes
- Assets: `flux_install.sh`, `setup_hw_accel_debian.sh`, `chroot/setup_debian13_chroot.sh`
- Device helper: `/data/local/tmp/nativecode_proot.sh`
