# Settings: XFCE desktop launch — proot vs chroot

**Date:** 2026-07-30  
**Status:** implemented (2026-07-30) 
**Scope:** Settings hub **Graphical Desktop** card (`START/STOP XFCE DESKTOP`) must branch on `linux_method`. Proot keeps current `start_gui.sh` / `stop_gui.sh`. Chroot must start host X11 + Pulse (+ optional VirGL), then enter Debian chroot and run XFCE (not proot-distro).  
**Out of scope:** KDE Plasma, multi-distro GUI picker, HyperOS-only one-off hacks beyond fluxlinux-proven SELinux soft-fail, redesign of Graphical Desktop card chrome.

**Related:**
- FluxLinux SSOT (working chroot XFCE):  
  - Host orchestrate: `TermuxIntentFactory.buildLaunchGuiIntent` (`debian13_chroot`) — VirGL + Pulse in **Termux uid**, then `su -c "sh /data/local/tmp/start_debian13_gui.sh"`  
  - Root GUI wrapper generated in `setup_debian13_chroot.sh` → `/data/local/tmp/start_debian13_gui.sh`  
  - Mount + `startxfce4` core: `/data/local/tmp/start_debian13.sh`  
  - Stop: `/data/local/tmp/stop_debian13_gui.sh` (asset + generated)  
  - Docs: `fluxlinux/docs/CHROOT_HARDWARE_ACCEL.md`, `fluxlinux/docs/hyperos_x11_fix.md`
- NativeCode proot GUI (works today): `app/src/main/assets/scripts/start_gui.sh`, `stop_gui.sh`
- NativeCode chroot install already generates **partial** launchers:  
  `setup_debian13_chroot.sh` → `/data/local/tmp/start_debian13.sh` + `stop_debian13_gui.sh`  
  **Missing:** full X11 host wrapper (`start_debian13_gui.sh`) and app-side branch
- Paths: `ChrootCommandBuilder.CHROOT_PATH` = `/data/local/tmp/chrootDebian13`, user `flux`
- Package / X11: `com.zenithblue.nativecode` embeds Termux:X11 (`app_process` + `loader.apk`) — **not** `com.termux`

**Primary files (target):**

| Path | Role |
|------|------|
| `MainActivity.kt` | `startGui()` / `stopGui()` branch on `linux_method`; deploy new scripts; optional UI subtitle |
| `app/src/main/assets/scripts/start_gui.sh` | Unchanged proot path (or tiny shared notes only) |
| `app/src/main/assets/scripts/stop_gui.sh` | Unchanged proot path |
| `app/src/main/assets/scripts/chroot/start_gui_chroot.sh` | **NEW** app-uid orchestrator (Pulse/VirGL/X11 + su root body) |
| `app/src/main/assets/scripts/chroot/start_debian13_gui.sh` | **NEW** root body: mounts, X11 socket visibility, GPU env, `startxfce4` |
| `app/src/main/assets/scripts/chroot/stop_debian13_gui.sh` | **NEW** asset SSOT stop (replace reliance on install-time-only generation) |
| `setup_debian13_chroot.sh` | Align generated launchers with asset SSOT (regenerate same content / call note) |
| `OnboardingActivity.deployScripts` / `MainActivity.deployScripts` | Deploy new chroot GUI scripts to `$HOME` |
| `docs/start-gui-debug.md` | Append chroot path notes (optional follow-up) |

---

## 1. Problem

### 1.1 Settings always launches proot XFCE

```kotlin
// MainActivity.startGui()
ShellCommandRunner.run(..., start_gui.sh, "debian")
```

`start_gui.sh`:
1. Starts PulseAudio, optional VirGL, embedded termux-x11 (`app_process` Loader)
2. Opens `com.zenithblue.nativecode/com.termux.x11.MainActivity`
3. `proot-distro login debian --shared-tmp -- … startxfce4`

When **`linux_method=chroot`**:
- Guest rootfs is **`/data/local/tmp/chrootDebian13`**, not proot container
- `proot-distro login` is wrong / may be empty or unrelated
- XFCE in chroot requires **root mounts** + **busybox chroot** + `su - flux -c startxfce4`

### 1.2 Install-time scripts are incomplete for in-app start

`setup_debian13_chroot.sh` (NativeCode) writes:

| Script | What it does | Gap |
|--------|--------------|-----|
| `/data/local/tmp/start_debian13.sh` | mounts + `chroot … startxfce4` | No host Pulse/VirGL/X11 |
| `/data/local/tmp/stop_debian13_gui.sh` | kill XFCE in chroot + kill X11/Pulse | Only exists if setup ran; not re-deployed from assets on app update |

FluxLinux **also** generates `/data/local/tmp/start_debian13_gui.sh` which starts X11 as root then calls `start_debian13.sh`. NativeCode never generates that wrapper, and never wires Settings to either script.

### 1.3 NativeCode vs FluxLinux path differences (must not copy blindly)

| Concern | FluxLinux | NativeCode (required) |
|---------|-----------|------------------------|
| Package | `com.termux` | `com.zenithblue.nativecode` |
| `TARGET_TERMUX_PREFIX` | `/data/data/com.termux/files/usr` | `/data/data/com.zenithblue.nativecode/files/usr` |
| X11 start | `termux-x11 :0` binary + external Termux:X11 app | Embedded: `app_process` + `loader.apk` + `am start …/com.termux.x11.MainActivity` (same as proot `start_gui.sh`) |
| Guest `/tmp` | Often bind-mount **entire** host `usr/tmp` → chroot `/tmp` | **Sticky disk `/tmp`** for apt; host tmp only at `/mnt/host-tmp` (`ChrootCommandBuilder`) |
| X11 socket | Visible at guest `/tmp/.X11-unix` via full bind | Must **not** replace sticky `/tmp`; bind only `.X11-unix` (and lock) or symlink strategy |
| GPU | VirGL socket in host tmp | Same + read `/etc/fluxlinux/gpu_mode` (turnip/virgl) like proot `start_gui.sh` |
| User | `flux` | `flux` |
| Rootfs | `/data/local/tmp/chrootDebian13` | same |

**Critical:** Full bind of app `usr/tmp` onto chroot `/tmp` was deliberately removed for apt (`ChrootCommandBuilder` comments). GUI must preserve sticky `/tmp` while still exposing X11 socket.

### 1.4 Service ownership (flux proven)

| Service | Must start as | Why |
|---------|---------------|-----|
| PulseAudio | **App uid** (not root) | Socket/auth under app home; root-started PA breaks ACL |
| VirGL `virgl_test_server_android` | **App uid** | Socket in app `usr/tmp` |
| termux-x11 Loader / X socket | App uid preferred (match proot `start_gui.sh`) | Root `termux-x11` was flux path for HyperOS; NativeCode proot path uses app_process — **reuse proot host path for consistency** |
| mounts + chroot + startxfce4 | **Root** | Needs busybox chroot + bind mounts |

Flux order for chroot XFCE:
1. App: VirGL + Pulse  
2. Root: X11 + mounts + XFCE  

NativeCode preferred order (aligned with working proot GUI host stack):
1. App: Pulse + VirGL + **embedded X11** + open X11 activity  
2. Root: mounts + expose X socket into chroot + startxfce4  

---

## 2. Goals

1. **`linux_method=proot`**: unchanged — `start_gui.sh` / `stop_gui.sh`.  
2. **`linux_method=chroot`**: Settings START runs chroot XFCE path; STOP stops chroot DE + host X11/Pulse (not proot pkill).  
3. Asset-backed SSOT scripts under `scripts/chroot/` re-deployed on app start (survive upgrades).  
4. Preflight: root available + `.flux_configured` / `startxfce4` present; clear log lines if not.  
5. Preserve sticky guest `/tmp`; X11 via **bind of `.X11-unix` only** (and `.X0-lock` cleanup on host).  
6. Honor guest GPU mode file `/etc/fluxlinux/gpu_mode` (turnip | virgl | softpipe fallback).  
7. Disable xfwm compositor (Turnip black-screen prevention) — same as proot/flux.  
8. UI: subtitle or toast may mention active method (`proot` / `chroot`); button labels can stay.

### Non-goals
- KDE launch buttons  
- Auto-switch isolation mode when pressing Start  
- Changing terminal isolation  
- Play Store policy essay  

---

## 3. Architecture

```
[Settings] START XFCE
        │
        ▼
  read prefs linux_method
        │
   ┌────┴────┐
   │ proot   │ chroot
   ▼         ▼
start_gui.sh   start_gui_chroot.sh  (app uid, ShellCommandRunner + bash)
   │              │
   │              ├─ PulseAudio TCP 127.0.0.1
   │              ├─ virgl_test_server_android (optional)
   │              ├─ termux-x11 Loader :0 (app_process, CLEAR LD_LIBRARY_PATH)
   │              ├─ am start package/com.termux.x11.MainActivity
   │              └─ su → start_debian13_gui.sh (root)
   │                        │
   │                        ├─ remount dev,suid /data
   │                        ├─ bind /dev /sys /proc /dev/pts /dev/shm /sdcard
   │                        ├─ host tmp → /mnt/host-tmp (sticky /tmp kept)
   │                        ├─ bind host .X11-unix → chroot /tmp/.X11-unix
   │                        ├─ kill stale xfce in chroot
   │                        └─ su - flux → GPU env + dbus-launch startxfce4
   │
   └── proot-distro login debian → flux startxfce4

[Settings] STOP XFCE
   proot  → stop_gui.sh  (pkill xfce/proot + X11 + PA)
   chroot → stop_debian13_gui.sh via su (kill xfce in chroot + X11; no proot pkill)
```

---

## 4. Script design (SSOT)

### 4.1 `scripts/chroot/start_gui_chroot.sh` (app uid)

Shebang: app bash (same prefix style as `start_gui.sh`).  
Source `fluxlinux-host.env` when present.

Steps:
1. Export package paths (`TERMUX__PREFIX`, `HOME`, `XKB_CONFIG_ROOT`, `TERMUX_X11_OVERRIDE_PACKAGE`).  
2. Kill stale host: virgl, pulse, termux-x11 / app_process (same as proot start — clean slate).  
3. Start PulseAudio (app uid; TCP anonymous localhost).  
4. Optional VirGL → socket `$PREFIX/tmp/.virgl_test`.  
5. Start embedded X11 exactly like proot `start_gui.sh` (loader chmod, APK path, libXlorie extract, `app_process` Loader `:0 -legacy-drawing`).  
6. `am start -n $PKG/com.termux.x11.MainActivity`.  
7. Preflight:
   - `CHROOT=/data/local/tmp/chrootDebian13`
   - require `$CHROOT/.flux_configured` or `$CHROOT/usr/bin/startxfce4`
   - require working `su` (or rely on next step failure message)
8. Deploy/copy asset root script to a path root can exec:
   - Prefer: script already at `$HOME/start_debian13_gui.sh` → `su -c "cp … /data/local/tmp/ && sh /data/local/tmp/start_debian13_gui.sh"`  
   - Matches flux KDE deploy pattern (copy to `/data/local/tmp` then su).  
9. Exit 0 when XFCE session ends (blocking) or background policy: **block like proot** so BackgroundService keeps session alive.

### 4.2 `scripts/chroot/start_debian13_gui.sh` (root)

Port of flux `start_debian13.sh` + essential bits of flux `start_debian13_gui.sh`, adapted:

```
DEBIANPATH=/data/local/tmp/chrootDebian13
TARGET_PREFIX=/data/data/com.zenithblue.nativecode/files/usr
USERNAME=flux
BB=<detect magisk/system busybox, skip termux busybox>
```

Mounts (idempotent, ignore already-mounted):
- remount `/data` `dev,suid`
- bind `/dev`, `/sys`, `proc`, `devpts`
- tmpfs `/dev/shm` 512M
- **do not** bind host tmp over `/tmp`; ensure `chmod 1777 $DEBIANPATH/tmp`
- bind `$TARGET_PREFIX/tmp` → `$DEBIANPATH/mnt/host-tmp`
- bind `/sdcard` → `$DEBIANPATH/sdcard`
- **X11 socket visibility:**
  ```
  mkdir -p $DEBIANPATH/tmp/.X11-unix
  mount --bind $TARGET_PREFIX/tmp/.X11-unix $DEBIANPATH/tmp/.X11-unix
  ```
  Wait/retry up to ~5s if socket not ready yet.
- Optional SELinux: if `getenforce` = Enforcing, try `setenforce 0` (flux HyperOS fix; soft-fail). `chcon` on host tmp if available.

Guest launch (flux-compatible + GPU from proot script):
```
killall xfce session procs as root inside chroot
su - flux -c '
  DISPLAY=:0
  PULSE_SERVER=tcp:127.0.0.1
  XDG_RUNTIME_DIR=/tmp
  VTEST_SOCKET_NAME=/mnt/host-tmp/.virgl_test
  # apply GPU_MODE from /etc/fluxlinux/gpu_mode
  xfconf-query … use_compositing false
  dbus-launch --exit-with-session startxfce4
'
```

### 4.3 `scripts/chroot/stop_debian13_gui.sh` (root)

Asset version of flux stop:
1. Busybox chroot kill XFCE + dbus  
2. Stop X11: broadcast optional; `pkill` termux-x11 / app_process  
3. Clean host `.X11-unix` / locks under nativecode prefix  
4. Umount binds (sdcard, shm, pts, proc, sys, dev, host-tmp, `.X11-unix` bind) — lazy umount ok  
5. **Do not** `pkill proot` (that is proot stop_gui behavior and can nuke unrelated work)

Pulse kill: optional from app-uid stop wrapper; root may not own PA.

### 4.4 App-uid stop entry (optional thin wrapper)

Either:
- `stop_gui_chroot.sh` app-uid: deploy stop script + `su -c sh …` + `pulseaudio --kill`, or  
- `MainActivity.stopGui()` for chroot: `RootShell` / bash `su -c` + app-side pulse kill.

Prefer thin `stop_gui_chroot.sh` for symmetry with start.

### 4.5 Setup script regeneration

Update `setup_debian13_chroot.sh` generated `/data/local/tmp/start_debian13.sh` / stop to:
- Use **nativecode** prefix (already does)
- Prefer same X11 bind strategy as asset SSOT (not full `/tmp` host bind)
- Optionally write `start_debian13_gui.sh` identical to asset for ADB manual launch

App always re-deploys assets to `$HOME` on `deployScripts()` so upgrades fix old generators.

---

## 5. Kotlin wiring

### 5.1 `deployScripts()`

Add to home deploy list:
- `start_gui_chroot.sh` → asset `scripts/chroot/start_gui_chroot.sh`
- `start_debian13_gui.sh` → asset `scripts/chroot/start_debian13_gui.sh`
- `stop_debian13_gui.sh` / `stop_gui_chroot.sh` as needed  

Extend asset path rule: `script.contains("chroot") || script.endsWith("_chroot.sh")` → `scripts/chroot/…`  
(current rule already routes `*chroot*` filenames).

Same list in `OnboardingActivity.deployScripts()` if mirrored.

### 5.2 `startGui()` / `stopGui()`

```kotlin
private fun startGui() {
    // existing BackgroundService + delayed X11 activity open stays
    val method = prefs.getString("linux_method", "proot") ?: "proot"
    executor.execute {
        val bash = File(nld, "libbash.so").absolutePath
        val script = if (method == "chroot") {
            File(TermuxHostPaths.HOME, "start_gui_chroot.sh")
        } else {
            File(TermuxHostPaths.HOME, "start_gui.sh")
        }
        ShellCommandRunner.run(this, arrayOf(bash, script.absolutePath, "debian"))
    }
    // postDelayed open X11 activity — keep for both
}

private fun stopGui() {
    // broadcast ACTION_STOP + stop BackgroundService
    val method = prefs.getString("linux_method", "proot") ?: "proot"
    executor.execute {
        val bash = …
        if (method == "chroot") {
            ShellCommandRunner.run(…, stop_gui_chroot.sh or su stop_debian13_gui.sh)
        } else {
            ShellCommandRunner.run(…, stop_gui.sh, "debian")
        }
    }
}
```

Hard-fail UX if chroot selected but not installed: script prints error; optional Toast from main after quick `ProjectPathResolver.isChrootInstalled()` check before launch.

### 5.3 Graphical Desktop card copy (small)

Subtitle:
- proot: current text  
- chroot: `"Launch Termux X11 and start XFCE4 inside Debian chroot (root)."`  

Optional: append method badge. Not required for v1.

### 5.4 Enable Start button

Existing `onSetupComplete()` enables buttons. Also ensure chroot-only users who finished onboarding enable Start (already if setup_complete / same gate). If Start disabled until proot setup only — verify gate; fix if chroot path never calls `onSetupComplete`.

---

## 6. X11 socket + sticky `/tmp` (detail)

**Problem:** Guest XFCE uses `DISPLAY=:0` → connects to `/tmp/.X11-unix/X0`. Host creates socket under `$PREFIX/tmp/.X11-unix/X0`. Sticky guest `/tmp` is a real directory, not the host tmp.

**Solution (chosen):**
```
mkdir -p $CHROOT/tmp/.X11-unix
mount --bind $PREFIX/tmp/.X11-unix $CHROOT/tmp/.X11-unix
```

**Alternatives rejected:**
| Approach | Why not |
|----------|---------|
| Bind entire host tmp → `/tmp` | Breaks apt sticky tmp policy |
| `DISPLAY` unix path override | Fragile; many clients assume standard path |
| Run X as root inside chroot | Different security model; not flux pattern |

**VirGL:** socket at `$PREFIX/tmp/.virgl_test` → guest `VTEST_SOCKET_NAME=/mnt/host-tmp/.virgl_test` (host-tmp bind already required).

---

## 7. GPU env (parity with proot)

Inside guest as `flux`, read `/etc/fluxlinux/gpu_mode`:

| Mode | Env |
|------|-----|
| `turnip` | `MESA_LOADER_DRIVER_OVERRIDE=zink`, freedreno ICD, `TU_DEBUG=noconform`, `MESA_VK_WSI_DEBUG=sw`, GL version overrides; compositor off |
| `virgl` | If socket exists: `GALLIUM_DRIVER=virpipe` + `VTEST_SOCKET_NAME=…`; else llvmpipe |
| else | `LIBGL_ALWAYS_SOFTWARE=1`, `GALLIUM_DRIVER=llvmpipe` |

Written by `setup_hw_accel_debian.sh` (already run in chroot onboarding chain).

---

## 8. Implementation steps (ordered)

1. **Write** `docs/plan/settings-xfce-chroot-gui-launch.md` (this file).  
2. **Add** `start_debian13_gui.sh` (root body).  
3. **Add** `start_gui_chroot.sh` (app orchestrator; reuse host X11 block from `start_gui.sh`).  
4. **Add** `stop_debian13_gui.sh` + `stop_gui_chroot.sh`.  
5. **Wire** `deployScripts` (Main + Onboarding).  
6. **Wire** `startGui` / `stopGui` on `linux_method`.  
7. **Align** `setup_debian13_chroot.sh` generated launchers with sticky-tmp + X11 bind.  
8. **UI** subtitle optional.  
9. **Manual test checklist** (below).  

---

## 9. Manual test checklist

| # | Case | Expect |
|---|------|--------|
| T1 | proot + START | Same as today: XFCE via proot-distro |
| T2 | proot + STOP | XFCE/proot/X11 stop; Start returns |
| T3 | chroot installed + root granted + START | Host X11 activity; XFCE desktop from chroot; no proot-distro |
| T4 | chroot + STOP | XFCE gone; X11 closed; proot processes (if any) untouched |
| T5 | chroot not installed + START | Script error message; no crash |
| T6 | chroot + root denied | Clear su failure; no hang forever without log |
| T7 | Switch isolation proot↔chroot then START | Correct path for **current** method |
| T8 | VirGL present | Socket used; no hard fail if missing |
| T9 | turnip mode file | Zink env applied; compositor off |
| T10 | After app upgrade | New scripts redeployed; old generated `/data/local/tmp` scripts overwritten by start copy |

---

## 10. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| SELinux blocks bind of X11 socket | Soft `setenforce 0` when Enforcing (flux); log if fails |
| Root script not executable under app-data | Always `cp` to `/data/local/tmp` then `sh` |
| Concurrent su with chroot size probe | GUI start long-running; user-initiated; document avoid Refresh during Start |
| `am force-stop $PKG` in proot start kills app | **Do not copy** `am force-stop $PKG` into chroot start (proot script has it — dangerous for self). Chroot orchestrator: kill only x11/virgl/pulse, **never** force-stop own package |
| Sticky `/tmp` vs X11 | Bind only `.X11-unix` |
| Old installs without startxfce4 | Preflight exit 1 + message re-run onboarding |

---

## 11. Acceptance criteria

- [x] Plan doc at `docs/plan/settings-xfce-chroot-gui-launch.md`
- [x] Assets: `start_gui_chroot.sh`, `start_debian13_gui.sh`, `stop_gui_chroot.sh`, `stop_debian13_gui.sh`
- [x] `deployScripts` Main + Onboarding
- [x] `startGui` / `stopGui` branch on `linux_method`
- [x] Sticky `/tmp` + bind `.X11-unix` only
- [ ] Device: proot START/STOP regression
- [ ] Device: chroot START shows XFCE from chroot
- [ ] Device: chroot STOP does not pkill proot

---

## 12. Implementation notes (shipped)

| File | Action |
|------|--------|
| `docs/plan/settings-xfce-chroot-gui-launch.md` | Plan |
| `assets/scripts/chroot/start_gui_chroot.sh` | App-uid host orchestrator |
| `assets/scripts/chroot/start_debian13_gui.sh` | Root mounts + XFCE |
| `assets/scripts/chroot/stop_gui_chroot.sh` | App-uid stop wrapper |
| `assets/scripts/chroot/stop_debian13_gui.sh` | Root stop (no proot pkill) |
| `MainActivity.kt` | Branch + deploy + subtitle |
| `OnboardingActivity.kt` | Deploy new scripts |
| `setup_debian13_chroot.sh` | X11 bind + stage asset stop/start |

Flux SSOT: `TermuxIntentFactory` chroot path + generated `start_debian13_gui.sh` / `start_debian13.sh`. NativeCode X11 = embedded `app_process` Loader (proot `start_gui.sh`).
