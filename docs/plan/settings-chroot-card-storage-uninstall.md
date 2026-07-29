# Settings: Chroot Settings (page, storage, install/uninstall)

**Date:** 2026-07-28  
**Status:** **implemented** + **§14 size probe fix implemented** (2026-07-29)  
**Scope:** Settings Hub **nav row only** → dedicated **Chroot Settings** page for external rootfs at `/data/local/tmp/chrootDebian13` (status, root probe, size, install via onboarding, uninstall via root script). UI: [`docs/project/ui_design.md`](../project/ui_design.md) (Cyber-Brutalist / `NC` tokens).  
**Out of scope (later):** proot storage management, mount status list, partial wipe (home-only), Play-policy wording polish, cyber-brutalist dialogs for other AlertDialogs (exit/commit).

**Related:**
- [`docs/project/ui_design.md`](../project/ui_design.md) — colors, sharp 0px, two-tone extrusion, button/card rules  
- [`docs/plan/proot-debian-rootfs-local-install.md`](./proot-debian-rootfs-local-install.md) — proot lives *inside* app files  
- Path: `ChrootCommandBuilder.CHROOT_PATH` = `/data/local/tmp/chrootDebian13`  
- Install script: `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh`  
- Uninstall script: `app/src/main/assets/scripts/chroot/uninstall_debian13_chroot.sh`  
- Marker: `ProjectPathResolver.isChrootInstalled()` → `$CHROOT_PATH/.flux_configured`

**Primary files:**

| Path | Role |
|------|------|
| `MainActivity.kt` | Hub nav row; `ID_CHROOT_SETTINGS` page; content card; size/root probe; install/uninstall; brutalist confirm; `scriptRunnerTitle` |
| `OnboardingActivity.kt` | Deep-link: `preferred_isolation`, `target_page`, `auto_start_setup` for full chroot install chain |
| `RootShellService.kt` / `RootShell` | Multi-path su discovery (no `File.exists` gate); `capture` / `clearSuCache` |
| `ProjectPathResolver.kt` | `isChrootInstalled`, `isChrootRootfsPresent`, path helpers |
| `ChrootCommandBuilder.kt` | `CHROOT_PATH` |
| `uninstall_debian13_chroot.sh` | Root uninstall (unchanged behavior) |
| `DesignTokens.kt` | `NC.*` |

---

## 1. Problem

### 1.1 Chroot is outside the app sandbox

| Mode | Rootfs location | Survives app uninstall? |
|------|-----------------|-------------------------|
| **proot** | `filesDir/.../proot-distro/containers/debian/rootfs` | No |
| **chroot** | `/data/local/tmp/chrootDebian13` | **Yes** — host path, root-owned |

1. Storage invisible under Android app storage UI.  
2. Uninstalling APK does **not** remove Debian rootfs.  
3. Need first-class Settings surface for size + install + uninstall (not only System Scripts).

### 1.2 Settings Hub layout (current)

`buildSettingsHubLayout()`:

1. Header (SETTINGS HUB)  
2. Graphical Desktop  
3. Terminal Settings  
4. Linux Isolation Mode (proot/chroot toggle)  
5. **Chroot Settings** nav row → `ID_CHROOT_SETTINGS`  
6. System Scripts  
7. Re-run Onboarding  

Detail UI is **not** expanded on the hub.

---

## 2. Goals (shipped)

1. Hub: nav row only under Isolation (Scripts-style chrome).  
2. Detail page: warn strip, STATUS, ROOT ACCESS, LINUX STORAGE + REFRESH, HOST PATH, INSTALL or UNINSTALL.  
3. Async root probe + `du -sb`; instant status from marker/dir; prefs cache.  
4. **Uninstall:** brutalist confirm → `runScriptInTerminal(..., "chroot_host")`.  
5. **Install (not detected only):** primary CTA → Onboarding full chroot chain.  
6. Page **back** control; script runner **title** by script (`when`).  
7. UI: sharp cards/buttons, NC palette, destructive = error tokens not primary green.

---

## 3. UX / UI (`ui_design.md`)

| Element | Spec |
|---------|------|
| Surfaces | `NC.SURFACE_LOW` / lowest; stroke `OUTLINE_VAR`; 6px two-tone extrusion |
| Primary actions | Terminal green (`NC.PRIMARY`), text `#0A0A0A` — **INSTALL** |
| Destructive | `NC.ERROR` stroke/label, dark red fill — **UNINSTALL** |
| Labels / data | JetBrains Mono (`Typeface.MONOSPACE`) |
| Shapes | **0px radius only** |
| Press | offset 6→2, translate 4px |
| Confirm | custom `showBrutalistConfirmDialog` scrim + card — **not** Material `AlertDialog` |

### Detail page wireframe

```
[←] [icon] CHROOT SETTINGS
// ROOT-LEVEL DEBIAN — OUTSIDE APP STORAGE
────────────────────────────────────────
┌ glass card: Storage & Manage ─────────┐
│ warn strip (rootfs survives app wipe) │
│ STATUS          INSTALLED|PARTIAL|…   │
│ ROOT ACCESS     GRANTED (if root)     │
│ LINUX STORAGE   50.2 GB   [REFRESH]   │
│ HOST PATH       /data/local/tmp/…     │
│ [ INSTALL CHROOT ]  OR  [ UNINSTALL ] │
└───────────────────────────────────────┘
```

---

## 4. Architecture / data flow

### 4.1 Detection

| Signal | Source |
|--------|--------|
| Marker | `$CHROOT_PATH/.flux_configured` via `isChrootInstalled()` |
| Dir | `isChrootRootfsPresent()` + root `test -d` when su available |
| Installed | `marker \|\| dir` |
| Root | `RootShell.isRootAvailable()` (multi-path su; no exists skip) |
| Size | root `du -sb '$path' \| cut -f1` |

### 4.2 Prefs (`nativecode_prefs`)

| Key | Meaning |
|-----|---------|
| `chroot_installed` | last installed flag |
| `chroot_dir_present` | dir present |
| `chroot_size_bytes` | last size (−1 if none) |
| `chroot_root_ok` | last root probe |
| `chroot_size_via_root` | size from root du |
| `chroot_last_check_ms` | cache timestamp |

Cache paint: if `chroot_root_ok=false` → no-root UI (never stale DENIED / partial GB).

### 4.3 Install / uninstall actions

| State | Primary CTA |
|-------|-------------|
| Not detected (`!marker && !dir`) | **INSTALL CHROOT** → Onboarding |
| Detected (INSTALLED / PARTIAL) | **UNINSTALL CHROOT** only |

**Install deep-link extras** (`OnboardingActivity`):

```text
force_onboarding = true
preferred_isolation = "chroot"
target_page = 3              // Environment Setup
auto_start_setup = true      // run full chain immediately
```

Chain: root check → `setup_debian13_chroot.sh` → `setup_debian_family` → HW accel → optional customization → CLI tools → `linux_method=chroot`.

**Uninstall:**

1. `showBrutalistConfirmDialog` (destructive)  
2. `pendingChrootUninstall = true`  
3. `runScriptInTerminal("uninstall_debian13_chroot.sh", "chroot_host")`  
4. On finish / deep-link success → `onChrootUninstalled` (prefs → proot if needed, clear cache, refresh UI)

### 4.4 Nav

| Item | Value |
|------|--------|
| Page id | `ID_CHROOT_SETTINGS = 14` |
| Open | hub `buildChrootSettingsSectionButton` |
| Back | page header `ic_arrow_back` → Settings Hub; system back same |
| Probe | on page open + REFRESH (not every hub visit) |
| Global nav | hidden on chroot page (sub-page) |

### 4.5 Script runner title (`scriptRunnerTitle`)

Script install page top bar is **not** always “Script Installation”. `when (scriptName)`:

| Script | Title |
|--------|--------|
| `uninstall_debian13_chroot.sh` | **Chroot Uninstall** |
| `setup_debian13_chroot.sh` | Chroot Installation |
| `setup_debian_family.sh` | Debian Family Setup |
| `setup_customization_debian.sh` | Debian Customization |
| `setup_hw_accel_debian.sh` | Hardware Acceleration Setup |
| `setup_cli_tools.sh` | CLI Tools Setup |
| `setup_termux.sh` | Host Environment Setup |
| `flux_install.sh` | Flux / PRoot Install |
| `start_gui.sh` / `stop_gui.sh` | Start / Stop Graphical Desktop |
| else | uninstall/setup/start/stop prefix heuristics, else mode-based |

Set in `runScriptInTerminal` via `scriptInstallTitleTv`.

### 4.6 Code map (`MainActivity`)

```text
buildSettingsHubLayout()
  → buildChrootSettingsSectionButton()     // hub nav only

buildChrootSettingsPage()                  // ScrollView + header w/ back
  → buildChrootSettingsContentCard()       // status / size / CTAs

refreshChrootSettingsCard / applyInstant / applyCached / applyNoRoot
applyChrootInstallUninstallVisibility
launchChrootInstallOnboarding
confirmAndUninstallChroot → showBrutalistConfirmDialog
onChrootUninstalled

runScriptInTerminal → scriptRunnerTitle(scriptName, runMode)
```

---

## 5. Root / no-root policy

| App root? | UI |
|-----------|-----|
| **No** | STATUS **NOT INSTALLED**; hide ROOT row, path, uninstall; show **INSTALL**; size `—`; hint root required |
| **Yes, no rootfs** | NOT INSTALLED + ROOT GRANTED + **INSTALL** |
| **Yes, rootfs present** | INSTALLED/PARTIAL + GRANTED + size + **UNINSTALL** |

### RootShell fix (why false DENIED)

1. Do **not** skip su paths with `File.exists()` (KSU/Magisk hide until exec).  
2. Multi-path + `sh -c 'su -c …'` wrap matching install runner.  
3. Cache working invocation; REFRESH → `clearSuCache()`.

---

## 6. Security / safety

1. Uninstall requires root + confirm dialog.  
2. Fixed script path only — no user-supplied `rm -rf`.  
3. After uninstall: never leave `linux_method=chroot` if rootfs gone.  
4. Size probe quotes fixed `CHROOT_PATH` only.

---

## 7. Acceptance criteria

- [x] Hub shows **Chroot Settings** nav row under Linux Isolation.  
- [x] Detail page has status / storage / path; probe on open + REFRESH.  
- [x] Root granted + rootfs → human-readable size (async).  
- [x] No root → NOT INSTALLED only + INSTALL CTA (no false DENIED / partial GB).  
- [x] Not detected → **INSTALL** → onboarding full chroot chain.  
- [x] Detected → **UNINSTALL** only; brutalist confirm; script title **Chroot Uninstall**.  
- [x] Page back → Settings Hub.  
- [x] Successful uninstall: rootfs gone, UI/cache update, method not stuck on chroot.  
- [x] Proot data unaffected.  
- [x] Visual: sharp NC chrome; destructive not primary green.

---

## 8. File-level change summary

| File | Change |
|------|--------|
| `MainActivity.kt` | Page + hub row; probe/cache; install/uninstall; brutalist dialog; `scriptRunnerTitle` |
| `OnboardingActivity.kt` | `preferred_isolation`, `auto_start_setup`, preselect isolation |
| `RootShell` / service | Robust su discovery + `capture` |
| `ProjectPathResolver.kt` | present/installed helpers |
| This plan | Living doc for shipped behavior |

No new Gradle deps. Layouts built in code (no new XML).

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| `du` slow on huge rootfs | BG only; loading strip; cache |
| SELinux / su path variance | Multi-path + sh wrap; REFRESH clears cache |
| Partial extract | STATUS PARTIAL; uninstall still available |
| Onboarding install without root | Onboarding logs grant/retry message |

---

## 10. Future extensions

- Proot app-storage breakdown card.  
- Live mount list / Stop GUI from this page.  
- Storage threshold warning (> N GB).  
- Brutalist dialogs for exit/commit (global).  
- Type-to-confirm for uninstall.

---

## 11. Quick reference — constants

```text
CHROOT_PATH      = /data/local/tmp/chrootDebian13
MARKER           = $CHROOT_PATH/.flux_configured
INSTALL_SCRIPT   = setup_debian13_chroot.sh
UNINSTALL_SCRIPT = uninstall_debian13_chroot.sh
RUN_MODE         = chroot_host
PREFS            = nativecode_prefs
linux_method     = proot | chroot
PAGE_ID          = ID_CHROOT_SETTINGS (14)
CALLBACK_NAME    = distro_uninstall_debian13_chroot
```

---

## 12. Changelog (session)

| § | Change |
|---|--------|
| MVP | Hub card with size + uninstall |
| §14-era | RootShell su fix; no-root = NOT INSTALLED only |
| Page split | Hub nav only; `ID_CHROOT_SETTINGS` detail page |
| Install CTA | INSTALL when not detected → onboarding auto-start chroot |
| Back + dialog | Page back; `showBrutalistConfirmDialog` for uninstall |
| Runner title | `scriptRunnerTitle` when-switch (uninstall ≠ “Script Installation”) |

---

## 13. Historical notes (condensed)

Earlier drafts placed the full card on the Settings Hub and used stock `AlertDialog` for uninstall. Superseded by:

1. Dedicated page + header back.  
2. Brutalist confirm (scrim, sharp card, CANCEL / UNINSTALL).  
3. Mutual exclusivity INSTALL vs UNINSTALL.  
4. Script runner title switch so uninstall is labeled **Chroot Uninstall**.

Full intermediate prose lived in prior plan revisions; this document is the source of truth for **shipped** behavior.

---

## 14. Bug review — LINUX STORAGE shows `—` / “Root OK · size probe failed” (2026-07-28)

### 14.1 Symptom (device)

| Observation | Detail |
|-------------|--------|
| First open | Often OK: STATUS INSTALLED, ROOT GRANTED, size number paints |
| Later revisit (after chroot shells / other work) | Loading strip runs → size **blank (`—`)** + hint **`Root OK · size probe failed`** |
| Root / path | Still GRANTED; HOST PATH correct; Processes card can show `0 running` |
| Screenshot | Storage metric panel empty; fail hint under LINUX STORAGE |

So: **su works, dir exists, only the size parse/probe path fails** (or returns unparseable output).

### 14.2 Code path (current)

`refreshChrootSettingsCard()` → BG:

1. `RootShell.isRootAvailable()` → GRANTED  
2. `RootShell.capture("if [ -d '$path' ]; then echo YES…")` → dir exists  
3. Complex **top-level walk + per-entry `du -sb`** (exclude `sdcard|dev|proc|sys|mnt|run`):

```text
total=0
for e in $path/* $path/.[!.]* $path/..?*; do
  … basename / case skip …
  s=$(du -sb $e 2>/dev/null | awk '{print $1; exit}')
  total=$((total + ${s:-0}))
done
echo $total
```

4. Parse:

```kotlin
bytes = duOut.trim().lines().mapNotNull { it.trim().toLongOrNull() }.firstOrNull()
viaRoot = bytes != null && bytes >= 0
// else measureNote = "Root OK · size probe failed"
```

5. **Always** `saveChrootInfo(..., bytes, viaRoot)` — failure writes `chroot_size_bytes=-1`.

Fail condition is strict: **no line in capture stdout is a pure integer** (empty string, Magisk noise, shell errors, partial output).

### 14.3 Findings (ranked)

| # | Finding | Severity | Why it matches “works once, fails later” |
|---|---------|----------|------------------------------------------|
| **F1** | **Concurrent root captures on page open** | High | On `ID_CHROOT_SETTINGS`: `refreshChrootSettingsCard` **and** `refreshChrootProcessesCard` both fire on shared `executor`, each calling `RootShell.capture` / su in parallel. After Processes card landed, storage probe races with process list/reap staging. Some KSU/Magisk builds drop / empty one concurrent `su -c`. First cold open may serialize luckily; after “some work” su policy/load makes race lose. |
| **F2** | **Failed probe poisons prefs cache** | High | `saveChrootInfo` stores `bytes=-1` / `viaRoot=false` on failure. Next open: cache paint has nothing useful; live probe fails again → permanent `—` until a lucky success. Good sizes are **not** retained. |
| **F3** | **Fragile multi-statement `su -c` one-liner** | High | Size uses long `for` + `$(du)` + `$((…))` in one argv. Simple `test -d` / process script still work (short cmd). After chroot use, more top-level entries / slower `du` → more chance of shell abort, truncated stdout, or su timeout → empty / non-numeric → fail. |
| **F3b** | **Shell `$((total+s))` is 32-bit signed (adb-confirmed)** | **Critical** | Toybox/ash arithmetic overflows past 2³¹−1 (~2 GiB). Device roots ~6–7 GiB → `SIZE_BYTES` negative (e.g. `-1261644925`). App accepts only `n >= 0` → **always probe fail** once rootfs is multi-GB. First open may “work” only if sum still &lt;2G or lucky cache. **Fix: sum with awk, never shell arithmetic.** |
| **F4** | **Parse = first `toLongOrNull()` only** | Med | Any leading noise line without a number is skipped, but if **only** noise (Permission denied, `sh: …`, Magisk text) → null. No “last numeric line”, no marker token (`SIZE_BYTES=`), no raw log on fail. Hard to diagnose on device. |
| **F5** | **No timeout on `RootShell.capture`** | Med | Post-use rootfs + binds: `du` on `usr`/`home` can run very long. If UI “finishes” via another code path or user leaves, next entry hits `if (chrootMeasuring) return` and **skips refresh** while UI still shows old fail/empty. (If measuring never clears, stuck loading — related edge.) |
| **F6** | **No fallback measure** | Med | When walk-sum fails: no second try (`du -sb` on path with fixed excludes, or `du -s -k` + ×1024, or staged helper script). Single fragile path. |
| **F7** | **Unquoted `$e` in shell** | Low | Top-level names with spaces break `du -sb $e`. Uncommon in Debian rootfs. |
| **F8** | **Exclude list incomplete** | Low | Only skips `sdcard\|dev\|proc\|sys\|mnt\|run`. Other host binds under rootfs can make `du` huge/slow (amplifies F3/F5), less often pure parse fail. |
| **F9** | **`chrootMeasuring` early-return** | Low | Second refresh while first in-flight is dropped. Usually OK; combined with hang (F5) can freeze updates. |

**Not the root cause (ruled out by UI):**

- Root lost → would flip no-root / NOT INSTALLED UI (screenshot still GRANTED).  
- Rootfs missing → hint would be “No chroot rootfs on host”.  
- Processes card kill → does not unmount/delete; unrelated to size logic except **F1 race**.

### 14.4 Likely failure chain (repro)

```text
Open Chroot Settings (after using chroot terminals)
  ├─ refreshChrootSettingsCard  ──► long du walk via su -c
  └─ refreshChrootProcessesCard ──► concurrent su capture (list)
         │
         ▼
  storage capture returns "" or non-numeric (race / fragile cmd)
         │
         ▼
  bytes=null → UI "—" + "Root OK · size probe failed"
         │
         ▼
  saveChrootInfo(bytes=-1)  // poisons cache
         │
         ▼
  next visit: cache useless → same probe → looks “always broken”
```

### 14.5 Fix plan (**implemented** 2026-07-29)

Ordered for minimal risk; keep bind-exclude behavior (must not count `/sdcard` as 79G).

#### P0 — Correctness / race

1. **Serialize chroot Settings root work** on one BG queue (or single `executor.execute` block):  
   `root probe → size → process list` **sequentially**. Do **not** fire two `RootShell.capture` in parallel on page enter.  
2. **Do not overwrite good size cache on probe failure**  
   - If `bytes == null` but previous prefs had `chroot_size_bytes >= 0` and dir still present: keep old bytes, set hint `Size probe failed · showing cache · {age}`.  
   - Only write `-1` when dir missing or first-ever measure.  
3. **Log raw** on fail: `Log.w("ChrootSize", "duOut=${duOut.take(400)}")` (and exit code if capture gains it).

#### P1 — Robust measure command

4. Prefer **staged helper** (same pattern as `chroot_processes.sh`): e.g. `chroot_size.sh` emitting:

   ```text
   # chroot_size v1
   SIZE_BYTES=1234567890
   ```

   Parse `SIZE_BYTES=` token only (immune to Magisk preamble).  
5. Or keep inline but simplify + harden:  
   - Quote paths: `du -sb "$e"`  
   - `echo SIZE_BYTES=$total` marker  
   - Fallback if walk fails: `du -sb` on selected top-level dirs listed explicitly (`bin boot etc home …`)  
6. Optional: `timeout` wrapper if device toybox has it (`timeout 60 …`) so hung `du` cannot pin `chrootMeasuring`.

#### P2 — Capture API

7. Extend `RootShell.capture` → `captureResult(cmd): Pair<Int,String>` (exit + stdout) **or** `captureWithTimeout`.  
8. Treat empty stdout + non-zero exit as soft-fail (cache keep), not as “installed=false”.

#### P3 — UI polish

9. On fail with cache: show **cached number dimmed** (alpha 0.75) + fail hint — never blank if we ever measured successfully.  
10. REFRESH force path: `clearSuCache()` then sequential measure (already clears su).  
11. Processes SCAN/KILL must not start while size measuring (or share one “chroot settings busy” flag).

### 14.6 Acceptance (when fixed)

- [x] Code: sequential page refresh (size → processes); SCAN/KILL blocked during measure  
- [x] Code: cache-keep-on-fail + dimmed paint + `SIZE_BYTES=` parse  
- [x] Code: `chroot_size.sh` + `ChrootSizeManager` + `RootShell.captureResult` + 90s timeout  
- [ ] Device: First open and 5× leave/re-enter after open chroot shells still show numeric size.  
- [ ] Device: Concurrent Processes SCAN does not blank storage.  
- [ ] Device: Forced fail keeps last good GB + “probe failed · cache” hint.  
- [ ] Device: Size still excludes sdcard/mnt/dev (not ~full device).  
- [ ] Device: No root path unchanged (NOT INSTALLED + INSTALL).  
- [ ] Device: Logcat contains raw probe output on failure.

### 14.7 File touch list (fix)

| File | Change |
|------|--------|
| `MainActivity.kt` | Sequential refresh; cache-keep-on-fail; parse `SIZE_BYTES=`; dimmed cache paint |
| `RootShellService.kt` | Optional timeout / exit-code capture |
| Optional `assets/scripts/chroot/chroot_size.sh` | SSOT size helper |
| This plan | Track bug + fix status |

### 14.8 Status

| Item | State |
|------|--------|
| Bug confirmed (UX) | Yes — screenshot + code path |
| Root cause | Strong: **F1 race + F2 cache poison + F3/F4 fragile cmd/parse** |
| Code fix | **Implemented** — see files below; device acceptance pending |

**Shipped files (fix):**

| File | Change |
|------|--------|
| `assets/scripts/chroot/chroot_size.sh` | SSOT measure; `SIZE_BYTES=`; exclude binds |
| `terminal/ChrootSizeManager.kt` | Stage + parse + inline fallback + timeout |
| `RootShellService.kt` | `captureResult` + optional timeout (exit -2) |
| `MainActivity.kt` | `refreshChrootSettingsPage` sequential; cache-keep; dimmed fail UI; busy gates |

---

## 15. Changelog (continued)

| § | Change |
|---|--------|
| §14 | Intermittent size probe fail review + fix plan (post Processes card / concurrent su) |
| §14.5–14.8 | Size fix implemented: sequential su, cache-keep, `chroot_size.sh`, `captureResult` |
