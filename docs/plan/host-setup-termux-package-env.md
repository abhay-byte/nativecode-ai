# Host setup: custom package env (`com.ivarna.nativecode`)

**Date:** 2026-07-28  
**Scope:** host package paths — script gate, Kotlin builders, bootstrap residual rewrite  
**Goal:** single source of truth; never fall back to stock `com.termux` paths at runtime

---

## Single source of truth

```text
TermuxHostPaths.kt          ← only definition of PACKAGE + path constants
        │
        ├─ HostCommandBuilder (ProcessBuilder / terminal env)
        ├─ writeHostEnvFile → usr/etc/fluxlinux-host.env
        └─ applyPackageToExtractedPrefix(filesDir)
                rewrites /data/data/com.termux → /data/data/com.ivarna.nativecode
                in bootstrap text under usr/{bin,etc,share}, proot_distro, headers
```

| Layer | Role |
|-------|------|
| **`TermuxHostPaths.kt`** | SSOT: `PACKAGE`, `PREFIX`, `HOME`, `STOCK_*`, rewrite + env write |
| **`HostCommandBuilder.kt`** | Host argv/env for sessions and shell runners |
| **`usr/etc/fluxlinux-host.env`** | Generated shell env; scripts **source** this |
| **Scripts** | `setup_termux.sh`, `flux_install.sh`, `start_gui.sh`, `stop_gui.sh` — source env, fallback pin only |

**Canonical values:**

```text
PACKAGE = com.ivarna.nativecode
PREFIX  = /data/data/com.ivarna.nativecode/files/usr
HOME    = /data/data/com.ivarna.nativecode/files/home
TMPDIR  = /data/data/com.ivarna.nativecode/files/usr/tmp
```

Rule: do not invent a second package string at call sites. Kotlin reads `TermuxHostPaths`; shell reads generated env (or env already set by Kotlin).

---

## Problem

### Original

1. Host scripts trusted caller `$PREFIX` / defaults → stock Termux tooling fell back to  
   `/data/data/com.termux/files/...` when `TERMUX_APP__PACKAGE_NAME` / `TERMUX__*` unset.
2. Marker file skipped host checks after first success.
3. Host env was copy-pasted across MainActivity, Onboarding, ProotCommandBuilder, ShellCommandRunner.

### Device test (post first fix) — what still looked broken

Logs showed **gate OK**, but **noise from bootstrap text**:

```text
# Working (our pin):
PREFIX=/data/data/com.ivarna.nativecode/files/usr
TERMUX_APP__PACKAGE_NAME=com.ivarna.nativecode
Fluxlinux: Setup Complete (marker v2)

# Not the gate — residual stock paths in bootstrap:
mkdir: cannot create directory '/data/data/com.termux': Permission denied
cp: cannot stat '.../com.termux/.../termux.properties': No such file or directory
```

**Source of mkdir/cp:**

```text
usr/etc/profile.d/init-termux-properties.sh   # hardcodes com.termux mkdir + cp
usr/bin/login                                 # many stock paths
(+ pkg, motd, termux-* tools — ~25 text files / ~100 stock path hits in bootstrap.tar)
```

Sourced when host scripts load `$PREFIX/etc/profile`.  
**proot** `can't sanitize binding /odm|/product|...` warnings are separate and usually benign.

---

## What we did

### 1. `TermuxHostPaths` (SSOT)

- Constants: `PACKAGE`, `STOCK_PACKAGE`, `DATA_ROOT`, `PREFIX`, `HOME`, libs, marker helpers
- `writeHostEnvFile(filesDir)` → `usr/etc/fluxlinux-host.env`
- `applyPackageToExtractedPrefix(filesDir)` → text rewrite of stock data-root + write env
- Skips binaries (NUL in first 512 bytes); max file size 2MB

### 2. Bootstrap rewrite when?

| When | Where |
|------|--------|
| After bootstrap extract | Onboarding base setup |
| Every `deployScripts()` | Onboarding + MainActivity |
| `ensureBootstrapExtracted()` | MainActivity (even if already extracted) |

### 3. Host gate script `setup_termux.sh`

- Sources `fluxlinux-host.env` when present
- Forces package identity; always re-validates (marker never skips checks)
- `SETUP_VERSION=2`; `FLUX_SETUP_FORCE=1` clears marker
- Checks: `proot-distro`, `pulseaudio`, `python`, proot/`PD_PROOT_BIN`, non-empty `loader.apk`, writable `TMPDIR`
- Refuses prefix containing `/com.termux/`

### 4. Other scripts

| Script | Change |
|--------|--------|
| `flux_install.sh` | Source env; paths via `$PREFIX` / `$HOME` only |
| `start_gui.sh` / `stop_gui.sh` | Source env first, then derive paths |

### 5. Call-site wiring

| Call site | Change |
|-----------|--------|
| **MainActivity** host run | `HostCommandBuilder.build()`; force for `setup_termux.sh` |
| **Onboarding** step D | clear marker; force setup; `HostCommandBuilder.applyTo` on shell |
| **ProotCommandBuilder** | env from `HostCommandBuilder`; paths from `TermuxHostPaths` |
| **ShellCommandRunner** | base env via `HostCommandBuilder.applyTo` |

### 6. Build

`:app:compileDebugKotlin` succeeded after SSOT + rewrite work.

---

## Flow (onboarding proot path)

```text
A. dirs
B. extract bootstrap.tar → files/
   applyPackageToExtractedPrefix()     ← kill residual com.termux text
C. deployScripts()
   apply again + write fluxlinux-host.env
D. clear setup_termux.done
   proot + libbash + setup_termux.sh   (FLUX_SETUP_FORCE=1)
E. flux_install.sh debian …
F/G. guest GPU / customization via proot-distro login
→ setup_complete
```

Manual host run (MainActivity “RUN ON HOST”):

```text
deployScripts()  → rewrite + env file
HostCommandBuilder.build(scriptPath, force if setup_termux.sh)
libbash.so + package-scoped env
```

---

## Device retest checklist

1. Install build that includes rewrite + env file (or clear app data so extract/deploy re-run).
2. Onboarding → Debian base (proot path).
3. **Pass:** no `mkdir .../com.termux` / `cp .../com.termux/.../termux.properties`.
4. **Pass:** log shows `PREFIX` / `TERMUX_APP__PACKAGE_NAME` = `com.ivarna.nativecode`.
5. **Pass:** `Setup Complete (marker v2)` then guest install continues.
6. **OK to ignore:** proot sanitize warnings for `/odm`, `/product`, `/system`, `/vendor`, …

If mkdir/cp stock paths still appear: rewrite did not run (old APK) or profile.d file not under scanned roots — check `TermuxHostPaths.applyPackageToExtractedPrefix` coverage.

---

## Intentionally not changed

- Host script still **validates only** (no apt / patchelf / ELF rewrite on device).
- `bootstrap.tar` in assets may still **contain** stock strings; runtime rewrite is the apply step.
- proot-distro Python defaults fall back to `com.termux` **only if env unset** — Kotlin always sets env.
- Chroot asset scripts still hardcode full package paths in places (optional later migrate).

---

## Files touched

```text
app/src/main/java/com/ivarna/nativecode/terminal/TermuxHostPaths.kt
app/src/main/java/com/ivarna/nativecode/terminal/HostCommandBuilder.kt
app/src/main/java/com/ivarna/nativecode/terminal/ProotCommandBuilder.kt
app/src/main/java/com/ivarna/nativecode/terminal/ShellCommandRunner.kt
app/src/main/java/com/ivarna/nativecode/MainActivity.kt
app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt
app/src/main/assets/scripts/setup_termux.sh
app/src/main/assets/scripts/flux_install.sh
app/src/main/assets/scripts/start_gui.sh
app/src/main/assets/scripts/stop_gui.sh
docs/plan/host-setup-termux-package-env.md
```

---

## Follow-ups (optional)

1. **Build-time** rewrite of `bootstrap.tar` so assets never ship stock paths.
2. Bump `SETUP_VERSION` when host check set changes.
3. Migrate chroot scripts to sourced `fluxlinux-host.env` only.
4. Full device smoke after reinstall (this checklist).
