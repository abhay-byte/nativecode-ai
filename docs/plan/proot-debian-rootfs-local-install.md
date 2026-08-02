# Proot Debian: local rootfs download + install-from-file

**Date:** 2026-07-28  
**Status:** implemented (code done; device checklist open)  
**Scope:** replace live `proot-distro install <name>` registry/plugin pull with **app-packaged rootfs → install local archive**; same archive for chroot  
**Related:**  
- `docs/plan/host-scripts-tweaks-flux-install.md` (`flux_install` one-click + tweaks removal)  
- `docs/plan/host-setup-termux-package-env.md` (package path SSOT)  
- Upstream: [termux/proot-distro](https://github.com/termux/proot-distro) (v5.x OCI + local archive)

---

## Problem (before)

`flux_install.sh` used:

```sh
"$PYTHON" "$PROOT_DISTRO" install "$DISTRO"   # bare name → registry
```

That pulled Debian via **Docker/OCI registry** at install time (network, tag drift, zstd/layer failures). Chroot used a **different** broken URL (`abhay-byte/nativecode` 404). No shared app-owned rootfs.

---

## Goal (achieved)

```text
APK assets/rootfs/debian_13_rootfs.tar.xz
        │
        ▼  deployRootfsArchive (Main + Onboarding)
$HOME/debian_13_rootfs.tar.xz
        │
        ├─► flux_install.sh  →  proot-distro install <abs-path> --name debian
        │                         → containers/debian/rootfs + setup_debian_family
        │
        └─► setup_debian13_chroot.sh  →  tar extract into /data/local/tmp/chrootDebian13
```

`proot` remains runtime; **install orchestration stays on `proot-distro`** for proot path. Chroot extracts the same tarball with busybox/tar (no proot-distro).

---

## What we did (changelog)

### 1. Rootfs source research

| Source tried | Result |
|--------------|--------|
| `github.com/abhay-byte/nativecode/releases/.../debian_13_rootfs.tar.xz` | **404** |
| `get.debian.org/images/cloud/` (trixie/bookworm `generic-arm64.tar.xz`) | Found (~285 MiB) but **cloud VM disk images**, not plain proot/LXC rootfs — unsuitable for drop-in `proot-distro install` |
| `github.com/abhay-byte/fluxlinux/releases/download/rootfs/debian_13_rootfs.tar.xz` | **OK** — plain rootfs, ~82 MiB |

### 2. Download + validate (dev)

| Item | Value |
|------|--------|
| Dev path | `downloads/rootfs/debian_13_rootfs.tar.xz` |
| Size | 85 009 380 (~82 MiB) |
| SHA256 | `13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803` |
| Layout | `./bin`, `./etc/`, `./lib`, `./sbin`, … (plain rootfs; strip-friendly) |
| Integrity | `xz -t` OK |

### 3. Package in app assets

| Item | Detail |
|------|--------|
| Asset path | `app/src/main/assets/rootfs/debian_13_rootfs.tar.xz` |
| Git | gitignored (`app/src/main/assets/rootfs/*.tar.xz`, `debian_13_rootfs.tar.xz`) — same idea as `bootstrap.tar` |
| Gradle | `androidResources { noCompress += listOf("xz", "tar") }` so APK does not re-compress archive |

### 4. Deploy from Kotlin → device `$HOME`

| File | Change |
|------|--------|
| `MainActivity.kt` | `deployRootfsArchive(homeDir)` from `deployScripts()` |
| `OnboardingActivity.kt` | same |

Behavior:

- Source: `assets.open("rootfs/debian_13_rootfs.tar.xz")`
- Dest: `$filesDir/home/debian_13_rootfs.tar.xz` (= `$HOME` under SSOT)
- Skip re-copy if dest exists and size **> 50 MiB**

### 5. `flux_install.sh` — install from local file

| Behavior | Detail |
|----------|--------|
| Default `DISTRO` | `debian` (one-click no-args unchanged) |
| Constants | `ROOTFS_NAME`, `ROOTFS_URL` (fluxlinux), `ROOTFS_SHA256` |
| Resolve order | `FLUX_ROOTFS_PATH` → `$HOME/$ROOTFS_NAME` → `$HOME/rootfs/` → `$PREFIX/.../cache/rootfs/` → `/sdcard/Download/{debian_13_rootfs,rootfs}.tar.xz` → URL download to cache |
| Install cmd | `"$PYTHON" "$PROOT_DISTRO" install "$ROOTFS_ARCHIVE" --name "$DISTRO"` |
| Path rule | Force absolute path so proot-distro treats as **file** (`/` / `./` / `../` / `~` only) |
| SHA | `sha256sum` when available |
| Skip | if `containers/$DISTRO/rootfs/bin/sh` already exists |
| Post-check | fail if install exit 0 but `bin/sh` missing |
| Debug escape | `FLUX_PD_INSTALL_MODE=registry` → old bare-name registry install |
| Setup | base64 (onboarding) or local `setup_debian_family.sh` (one-click) unchanged |

**Verified syntax** against bundled proot-distro parser:

```text
proot-distro install /data/data/com.zenithblue.nativecode/files/home/debian_13_rootfs.tar.xz --name debian
→ image_ref = abs path
→ custom_container_name = debian
→ _is_local_path = True
```

Upstream: path must start with `/`, `./`, `../`, or `~`; bare `debian` = registry image.

### 6. `setup_debian13_chroot.sh` — same local file

| Before | After |
|--------|--------|
| Manual `/sdcard/Download/rootfs.tar.xz` only | Same resolve list as proot (`$APP_HOME/debian_13_rootfs.tar.xz` first) |
| Download from **nativecode** release (404) | Fallback download from **fluxlinux** release |
| Always `extract $DEBIANPATH/rootfs.tar.xz` | `extract_file "$DEBIANPATH" "$ROOTFS_ARCHIVE"` (any resolved path) |
| No SHA | Optional SHA256 check |

Shared constants with proot path:

- `ROOTFS_NAME=debian_13_rootfs.tar.xz`
- `ROOTFS_URL=https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/debian_13_rootfs.tar.xz`
- `ROOTFS_SHA256=13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803`
- `APP_HOME` / `APP_PREFIX` from package SSOT (`com.zenithblue.nativecode`)

### 7. Docs / gitignore

| File | Change |
|------|--------|
| This plan | Status → implemented; full done-log (this section) |
| `.gitignore` | Ignore large rootfs under `assets/rootfs/` |
| `app/build.gradle.kts` | `noCompress` for `xz` / `tar` |

---

## Constants (SSOT — as implemented)

| Key | Value |
|-----|--------|
| Container name (proot) | `debian` |
| Archive name | `debian_13_rootfs.tar.xz` |
| Primary URL | `https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/debian_13_rootfs.tar.xz` |
| SHA256 | `13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803` |
| Size | 85 009 380 (~82 MiB) |
| APK asset | `app/src/main/assets/rootfs/debian_13_rootfs.tar.xz` |
| On-device path | `$HOME/debian_13_rootfs.tar.xz` |
| Dev mirror | `downloads/rootfs/debian_13_rootfs.tar.xz` |
| Manual paths | `/sdcard/Download/debian_13_rootfs.tar.xz`, `/sdcard/Download/rootfs.tar.xz` |
| Proot install | `"$PYTHON" "$PROOT_DISTRO" install "$ARCHIVE" --name debian` |
| Chroot extract dest | `/data/local/tmp/chrootDebian13` |

Env overrides:

| Env | Purpose |
|-----|---------|
| `FLUX_ROOTFS_PATH` | Force archive path (both scripts) |
| `FLUX_ROOTFS_URL` | Override download URL |
| `FLUX_ROOTFS_SHA256` | Override expected hash |
| `FLUX_PD_INSTALL_MODE=registry` | Debug: registry install (proot only) |

---

## Files changed

| File | What |
|------|------|
| `app/src/main/assets/rootfs/debian_13_rootfs.tar.xz` | **Added** (local/CI; gitignored) |
| `app/src/main/assets/scripts/flux_install.sh` | Resolve + `install <path> --name debian` + SHA + registry escape |
| `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh` | Same resolve/URL/SHA; extract from resolved archive |
| `app/src/main/java/.../MainActivity.kt` | `deployRootfsArchive` |
| `app/src/main/java/.../OnboardingActivity.kt` | `deployRootfsArchive` |
| `app/build.gradle.kts` | `noCompress` xz/tar |
| `.gitignore` | rootfs asset patterns |
| `docs/plan/proot-debian-rootfs-local-install.md` | This document |

**Not changed:** container name `debian`, `start_gui.sh` / `ProjectPathResolver` paths, onboarding base64 setup payload flow.

---

## Upstream API (reference)

```sh
# Product path (verified)
proot-distro install /data/data/com.zenithblue.nativecode/files/home/debian_13_rootfs.tar.xz --name debian

# Debug only
FLUX_PD_INSTALL_MODE=registry
proot-distro install debian
```

After install (unchanged):

```sh
proot-distro login debian --shared-tmp -- bash -c '…'
proot-distro list
proot-distro remove debian
# reset only if OCI + manifest.json — plain tar installs have no reset
```

---

## proot vs proot-distro

| Layer | Role |
|-------|------|
| **proot** | Runtime for login sessions |
| **proot-distro** | Install/extract plain rootfs, container layout, login argv |
| **chroot script** | Root extract of **same** tarball into `/data/local/tmp/chrootDebian13` |

---

## Risks (status)

| Risk | Mitigation | Status |
|------|------------|--------|
| Tarball large / OOM | ~82 MiB slim; not cloud ~285 MiB image | Mitigated by source choice |
| Wrong strip level | Plain rootfs top-level `bin`/`etc` | Validated on archive listing |
| SHA mismatch | Pin + verify in both scripts | Implemented |
| Offline after first deploy | Asset in APK + skip re-copy if >50 MiB | Implemented |
| Name collision | Always `--name debian` | Implemented |
| Registry zstd/Hub | Default = local file only | Implemented |
| `reset` N/A for plain tar | Reinstall = remove + re-run flux_install | Documented |
| APK size growth | +~82 MiB assets (like bootstrap) | Accepted |

---

## Verify checklist

### Done offline

- [x] Pin fluxlinux rootfs URL + SHA256  
- [x] Download + layout/xz/SHA validate  
- [x] Ship under `assets/rootfs/` + gitignore  
- [x] Deploy to `$HOME` from Main + Onboarding  
- [x] `flux_install.sh` local-file install + absolute path  
- [x] Install CLI syntax verified vs bundled proot-distro parser  
- [x] Chroot uses same local file + fluxlinux fallback  
- [x] `noCompress` for xz/tar  

### Device (still open)

- [ ] One-click scripts page: deploy rootfs + `install <abs> --name debian` + family setup  
- [ ] Log shows absolute path install, **not** bare `install debian`  
- [ ] `containers/debian/rootfs/bin/sh` exists  
- [ ] `proot-distro login debian --shared-tmp -- uname -a`  
- [ ] `start_gui.sh` finds `…/containers/debian/rootfs`  
- [ ] Onboarding base64 setup after install  
- [ ] Chroot path uses `$HOME/debian_13_rootfs.tar.xz` without network when present  
- [ ] Manual `/sdcard/Download/…` offline  
- [ ] No stock `com.termux` paths  
- [ ] Second run skips install when rootfs valid  

---

## Out of scope (still)

- Default product distro = Ubuntu  
- `proot-distro build` / `push`  
- Git-LFS / CI auto-fetch of rootfs asset (place file under `assets/rootfs/` like bootstrap)  
- Free-space pre-check UI strings  

---

## Decision log

| Decision | Choice |
|----------|--------|
| Default install source | **App-packaged** plain rootfs archive (not registry, not get.debian.org cloud) |
| Install tool (proot) | **`proot-distro install <abs-path> --name debian`** |
| Runtime | **proot** via proot-distro login |
| Registry install | `FLUX_PD_INSTALL_MODE=registry` only |
| Container name | Keep **`debian`** |
| Rootfs URL | **fluxlinux** release asset (shared with chroot) |
| get.debian.org cloud images | Rejected for proot (disk image tarball, not rootfs) |
| Chroot rootfs | **Same file** as proot (no second download source) |

---

## Next

1. Device retest: scripts-page one-click + onboarding proot install.  
2. Device retest: chroot with pre-deployed `$HOME/debian_13_rootfs.tar.xz`.  
3. Mark checklist complete when device passes; optional: CI step to fetch rootfs into `assets/rootfs/` before assemble.
