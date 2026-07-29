# GPU accel: vendor detect → Turnip (Snapdragon) / VirGL (rest)

**Date:** 2026-07-28  
**Status:** implemented (code done; device verify open)  
**Scope:** fix missing/wrong GPU mode selection; Snapdragon/Adreno → Turnip+Zink; Mali/PowerVR/other → VirGL  
**Related:**  
- `app/src/main/assets/scripts/setup_hw_accel_debian.sh`  
- `app/src/main/assets/scripts/start_gui.sh`  
- `app/src/main/java/com/ivarna/nativecode/terminal/GpuAccelDetector.kt`  
- Onboarding + MainActivity script runners  
- Upstream Turnip builds: [lfdevs/mesa-for-android-container](https://github.com/lfdevs/mesa-for-android-container)

---

## Problem (before)

1. **Kotlin always forced VirGL**  
   Onboarding (proot + chroot) and hard-coded:

   ```text
   env FLUX_GPU=virgl bash /tmp/setup_hw_accel_debian.sh
   ```

   Snapdragon devices never received Turnip install.

2. **No host GPU vendor identification**  
   No `Build` / `getprop` / KGSL probe on the Android side. Guest script only trusted `FLUX_GPU` or an interactive menu (blocks non-interactive onboarding).

3. **Manual Scripts UI**  
   `MainActivity.runScriptInTerminal` ran `setup_hw_accel_debian.sh` with no `FLUX_GPU` → interactive menu / default path, not device-aware.

4. **`start_gui.sh` always software**  
   Guest XFCE always set `LIBGL_ALWAYS_SOFTWARE=1` + `GALLIUM_DRIVER=llvmpipe`, ignoring Turnip or VirGL after setup.

5. **Script gaps**  
   - `FLUX_GPU` only matched exact `turnip` (aliases like `adreno`/`snapdragon` ignored → treated as virgl).  
   - Arch check used only `dpkg --print-architecture`.  
   - No persisted mode for later GUI launch.

---

## Goal (achieved)

```text
Host (Kotlin GpuAccelDetector)
  Build.* + SystemProperties + /dev/kgsl-3d0
        │
        ├─ Adreno / QCOM / KGSL  →  FLUX_GPU=turnip
        └─ Mali / PowerVR / other →  FLUX_GPU=virgl
        │
        ▼
Onboarding F / MainActivity Scripts / env override
        │
        ▼
setup_hw_accel_debian.sh (guest root)
  normalize + optional guest auto-detect
        │
        ├─ turnip → download Turnip+Mesa, pin apt, gpu-launch, XFCE no-compositor
        └─ virgl  → mesa deps + gpu-launch virpipe
        │
        ▼
/etc/fluxlinux/gpu_mode   (+ gpu_vendor)
        │
        ▼
start_gui.sh reads mode → Zink / virpipe / llvmpipe fallback
```

---

## Policy

| Host GPU | Mode | Guest stack |
|----------|------|-------------|
| Snapdragon / Adreno / KGSL | `turnip` | Turnip ICD + Zink (`MESA_LOADER_DRIVER_OVERRIDE=zink`) |
| Mali, PowerVR, Xclipse, unknown | `virgl` | `GALLIUM_DRIVER=virpipe` + host `virgl_test_server_android` |
| VirGL socket missing | soft fallback | `llvmpipe` + `LIBGL_ALWAYS_SOFTWARE=1` |
| Turnip download fail / non-arm64 | fall back | `virgl` |

---

## What changed

### 1. `GpuAccelDetector.kt` (new)

Path: `app/src/main/java/com/ivarna/nativecode/terminal/GpuAccelDetector.kt`

- Collects: `Build.HARDWARE/BOARD/DEVICE/...`, API 31+ `SOC_*`, `SystemProperties` / `getprop` keys (`ro.hardware`, `ro.board.platform`, `ro.soc.model`, EGL/Vulkan driver props, …).  
- Strong Adreno signal: `/dev/kgsl-3d0` (or readable).  
- Keyword match: qcom, adreno, sm8xxx/sdm, lahaina/taro/kalama/pineapple, …  
- Else Mali / PowerVR / Xclipse hints for logging only; mode still `virgl`.  
- API: `detect()` → `Detection(mode, vendorHint, signals)`, `fluxGpuEnv()` → `"turnip"|"virgl"`.  
- Logs: `GpuAccelDetector` tag.

### 2. Onboarding (`OnboardingActivity.kt`)

- **Proot path (step F):** detect → status line `mode + vendor` → prefs `flux_gpu` / `flux_gpu_vendor` →  
  `env FLUX_GPU=<mode> bash /tmp/setup_hw_accel_debian.sh` via proot-distro login.  
- **Chroot path (step F):** same detect + prefs;  
  `env FLUX_GPU=<mode> bash /tmp/setup_hw_accel_debian.sh` inside chroot.

### 3. MainActivity Scripts UI

- Card description updated (Turnip vs VirGL).  
- `runScriptInTerminal`: for `setup_hw_accel_debian.sh` only:
  - **proot:** `env FLUX_GPU=<detect> bash …/setup_hw_accel_debian.sh`
  - **chroot_guest:** same env inside `run_debian13_root.sh` / chroot `su` command  
- Writes `flux_gpu` pref on run.

### 4. `setup_hw_accel_debian.sh` (rewritten)

- `set -euo pipefail`; root check.  
- **`normalize_mode`:** `turnip|adreno|snapdragon|qcom|…` → turnip; `virgl|mali|…` → virgl; empty → ask/auto.  
- **`auto_detect_mode` (guest):** `getprop` + `/proc/cpuinfo` + `/dev/kgsl-3d0` (backup if host env missing).  
- **Menu:** only when `FLUX_GPU=ask|manual` **and** TTY; never blocks onboarding.  
- **Unset `FLUX_GPU`:** auto-detect (no hang).  
- Arch: `dpkg` else `uname -m`; non-arm64 turnip → virgl.  
- Turnip path: lfdevs release **26.2.0-devel-20260709** (proot curl download), Mesa upgrade + apt pin, fake `/dev/dri`, XFCE compositor off.  
- Download fail → virgl fallback (no hard abort).  
- **`gpu-launch`:** mode from bake-in + `/etc/fluxlinux/gpu_mode` + `FLUX_GPU_RUNTIME` override.  
- **State files:**  
  - `/etc/fluxlinux/gpu_mode`  
  - `/etc/fluxlinux/gpu_vendor`  
  - `/etc/profile.d/flux-gpu.sh` (`FLUX_GPU_MODE`)

### 5. `start_gui.sh`

- Still starts host VirGL server if `virgl_test_server_android` present.  
- Guest login reads `/etc/fluxlinux/gpu_mode`:
  - **turnip:** Zink + freedreno ICD + TU_DEBUG; no force-software.  
  - **virgl + socket:** `virpipe`.  
  - **else:** llvmpipe software fallback + warning.  
- Applied for root shell env and `su - flux` XFCE session.

---

## Files touched

| File | Action |
|------|--------|
| `app/src/main/java/com/ivarna/nativecode/terminal/GpuAccelDetector.kt` | **new** |
| `app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt` | detect + `FLUX_GPU` both paths |
| `app/src/main/java/com/ivarna/nativecode/MainActivity.kt` | Scripts env inject + card text |
| `app/src/main/assets/scripts/setup_hw_accel_debian.sh` | rewrite: detect, normalize, state |
| `app/src/main/assets/scripts/start_gui.sh` | honor `gpu_mode` for XFCE GL |

---

## Env / knobs

| Var | Where | Meaning |
|-----|--------|---------|
| `FLUX_GPU` | setup script | `turnip` / `virgl` / `ask` / `manual` / aliases |
| `FLUX_GPU_RUNTIME` | `gpu-launch` | one-shot override of baked mode |
| `FLUX_GPU_DEBUG=1` | `gpu-launch` virgl | socket diagnostics |
| prefs `flux_gpu` | app | last chosen mode |
| prefs `flux_gpu_vendor` | app | last vendor hint |

---

## Device checklist (open)

- [ ] Snapdragon: onboarding F logs `mode=turnip`, Turnip tarball install, `/etc/fluxlinux/gpu_mode` = `turnip`  
- [ ] Snapdragon: `gpu-launch glxinfo` shows Zink/Adreno-ish renderer (not llvmpipe)  
- [ ] Mali/other: onboarding F logs `mode=virgl`, no Turnip download  
- [ ] VirGL: `start_gui` + socket → guest `virpipe`; no socket → llvmpipe warning  
- [ ] Scripts UI re-run picks correct mode without menu hang  
- [ ] Chroot path same as proot for mode selection  

---

## Not done / follow-ups

- CI unit tests for `GpuAccelDetector` keyword tables.  
- Package Turnip/Mesa offline in APK (still network download on first Turnip setup).  
- Per-SoC Adreno gen blacklist (very old Adreno may need forced virgl).  
- UI toggle to force mode if auto-detect wrong.  
- Chroot `start_gui` path if separate from proot (confirm host GUI entry uses same state file under chroot rootfs).
