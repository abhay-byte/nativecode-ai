# Apps page — installed apps launcher + Home XFCE card

**Date:** 2026-08-02  
**Status:** implemented (2026-08-02)  
**Scope:** New **Apps** surface: list **installed launchable apps** for the **active isolation mode** (proot | chroot), with **icons, info, LAUNCH, UNINSTALL** using the same runner/registry logic as Marketplace. Entry points: **Settings Hub** button + **dedicated page** + **Home** module card. Also add a **Home Graphical Desktop (XFCE) card** with cyber-brutalist UI distinct from Settings Hub’s desktop strip.  
**Out of scope:** full Debian GUI package browser (10k+ `.desktop`); Play Store; rewriting start_gui scripts; changing proot/chroot builders or `nativecode_chroot.sh`; merging Software Manager into Apps; automatic apt upgrade of whole system.

**Related:**
- [`software-manager-marketplace.md`](./software-manager-marketplace.md) — SM (apt inventory) + MP (catalog) already shipped  
- [`settings-xfce-desktop-view-logs.md`](./settings-xfce-desktop-view-logs.md) — Settings XFCE start/stop/logs (host scripts)  
- [`settings-xfce-chroot-gui-launch.md`](./settings-xfce-chroot-gui-launch.md) — proot/chroot GUI host stack  
- [`chroot-ssot-shell-runner.md`](./chroot-ssot-shell-runner.md) — guest SSOT  
- [`docs/environment/nativecode-chroot-ssot.md`](../environment/nativecode-chroot-ssot.md)  
- **UI SSOT:** [`docs/project/ui_design.md`](../project/ui_design.md) + `DesignTokens.kt` (`NC.*`)

---

## 0. Product intent (what “Apps” is)

| Surface | What it shows | Actions |
|---------|---------------|---------|
| **Marketplace** (exists) | Curated catalog to **install** | Install / open detail / launch if already installed |
| **Software Manager** (exists) | **All** `dpkg` packages in guest, by section | Browse / size / limited MP uninstall via apt name map |
| **Apps (new)** | Only **installed apps** the user can **open** — primarily Marketplace `kind=app` registry entries for **current env** | **Launch**, **Uninstall**, detail sheet (icons + info) |

**Not** a third catalog. **Not** a second Software Manager.  
**Apps = “My installed apps”** for the active guest, launch-first UX.

### 0.1 Why not reuse SM list as-is

Software Manager lists every library (`libs`, `base`, …). Users do not “launch” those.  
Apps page must be **sparse**, **icon-forward**, **action-forward** (Launch primary).

---

## 1. Isolation + path SSOT (freeze + follow)

### 1.1 Global isolation (read only)

| Item | Source | Apps rule |
|------|--------|-----------|
| Pref | `nativecode_prefs` → `linux_method` (`proot` \| `chroot`) | **Read only** — same as MP/SM |
| Memory | `LinuxCommandBuilder.currentMethod` | Prefer this after env sync; **do not write** from Apps |
| Badge | Header chip `PROOT` / `CHROOT` | Same as SM/MP (`envBadgeLabel()` / `currentLinuxEnv()`) |

Switching method: Apps list **reloads for new env**. Registry is **per-env** (`InstallRegistry` keys `env|id`). No cross-env migration.

### 1.2 Runner layers (do not mix)

```text
Apps LAUNCH / UNINSTALL (guest)
  └─ PackageInstallRunner.launchApp / .uninstall
        └─ LinuxCommandBuilder.build(method, user=flux|root)
              ├─ proot  → ProotCommandBuilder
              └─ chroot → ChrootCommandBuilder → nativecode_chroot.sh (SSOT)

Apps inventory (guest read)
  └─ InstallRegistry (host JSON)  + optional guest probes via LinuxCommandBuilder
  └─ AptInventoryService only if needed for size/version enrich — NOT primary list

Home XFCE card START/STOP (host scripts — same as Settings Graphical Desktop)
  └─ startGui / stopGui already in MainActivity
        proot:  start_gui.sh / stop_gui.sh
        chroot: start_gui_chroot.sh / stop_gui_chroot.sh
  └─ NOT LinuxCommandBuilder
  └─ Never hold isScriptRunning for XFCE lifetime (stream capture rules from XFCE plan)

Marketplace / Software Manager pages
  └─ Unchanged; Apps only reuses their services + install page chrome
```

**Frozen (zero edits unless a later plan says otherwise):**
- `LinuxCommandBuilder` / `ProotCommandBuilder` / `ChrootCommandBuilder`
- `nativecode_chroot.sh` and other chroot SSOT helpers
- GUI host assets (`start_gui*.sh`, `stop_gui*.sh`, `start_debian13_gui.sh`, …)
- Marketplace catalog schema / remote repo contract

**Allowed to extend (thin wrappers only):**
- `InstallRegistry` read helpers (e.g. `listInstalledApps(ctx, env)`)
- Optional small `AppsInventory` object in `marketplace/` package
- `MainActivity` UI pages + home cards
- Reuse `ID_MP_INSTALLER` stream page for uninstall (same as MP)

### 1.3 Paths (correct per mode)

| Concern | proot | chroot |
|---------|-------|--------|
| Guest rootfs | `files/usr/var/lib/proot-distro/containers/debian/rootfs` (via proot-distro) | `/data/local/tmp/chrootDebian13` (`ChrootCommandBuilder.CHROOT_PATH`) |
| Launch user | `flux` via `LinuxCommandBuilder.build(..., user="flux")` | same |
| Uninstall user | `root` via `PackageInstallRunner` / `buildRootExec` | same (su + helper) |
| MP stage dir | Host `TermuxHostPaths.TMPDIR/nc-mp` ≡ guest `/tmp/nc-mp` (`--shared-tmp`) | Guest disk `${CHROOT}/tmp/nc-mp` staged via root |
| Registry file | Host-only `MarketplacePaths.registryFile` — **not** in guest | same host file, entries tagged `env` |
| Icons cache | `MarketplacePaths.cacheDir/.../icons` | same |
| XFCE host scripts | `$HOME/start_gui.sh` | `$HOME/start_gui_chroot.sh` |

Guest ready checks:
- proot: existing MP/SM guest-ready helpers (`AptInventoryService.guestReady` / container present)
- chroot: `ProjectPathResolver.isChrootInstalled()` / rootfs present  
Empty state copy must name the right settings page (Proot vs Chroot).

---

## 2. Data model

### 2.1 Primary source: InstallRegistry (Marketplace installs)

```kotlin
// Existing RegistryEntry fields used by Apps:
// id, env, kind, version, installedAt, sizeBytes, aptProvides,
// launchCommand, launchType, source, state

fun listApps(ctx, env): List<RegistryEntry> =
    InstallRegistry.listInstalled(ctx, env)
        .filter { it.state == "installed" && it.kind == PackageKind.APP }
```

**v1 list = only `PackageKind.APP` for current env.**  
Components (`box64`, `fex`, …) stay in Marketplace/SM, not Apps.

### 2.2 Enrichment (icons + display name)

| Field | Source |
|-------|--------|
| Title | Catalog `MpPackage.name` if cached catalog has id; else `entry.id` |
| Summary | Catalog `summary` / `description` |
| Version | `entry.version` |
| Size | `entry.sizeBytes` → `AptInventoryService.formatSize` |
| Icon | `MarketplaceIcons.load(ctx, pkg)` same as MP rows; fallback bundled mark |
| Launch cmd | `entry.launchCommand` (snapshot at install) |
| Env chip | `entry.env` |

If catalog fetch fails: still list registry rows with id + launch + uninstall registry-only fallback (same as MP uninstall when package missing from catalog).

### 2.3 Optional v1.1: system desktop apps (not required for ship)

Scan guest `.desktop` files for broader “all GUI apps”:

```text
proot/chroot guest:
  /usr/share/applications/*.desktop
  /usr/local/share/applications/*.desktop
  /home/flux/.local/share/applications/*.desktop
```

Parse `Name=`, `Exec=`, `Icon=`, `NoDisplay=`, `Terminal=`.  
**Uninstall** for non-registry desktops = **disabled** or “open Software Manager” only (never silent `apt remove` without confirm plan).  
**Launch** = `Exec` via same flux + DISPLAY pattern as `PackageInstallRunner.launchApp`.  
**Mark plan:** v1 ships registry apps only; desktop scan is **phase B** behind flag if time allows.

### 2.4 New thin service (recommended)

```text
marketplace/AppsInventory.kt   (or AppsCatalogBridge.kt)
  listInstalledApps(ctx, method): List<AppListItem>
  AppListItem(
    id, title, summary, version, sizeBytes,
    iconKey, launchCommand, launchType,
    source: "marketplace" | "desktop",
    registry: RegistryEntry?,
    catalog: MpPackage?
  )
```

No new network protocol. Catalog read via existing `MarketplaceClient` cache.

---

## 3. Actions (reuse Marketplace logic)

### 3.1 LAUNCH

```text
1. Guard: entry.isApp && launchCommand non-blank
2. Toast if desktop not up? Prefer soft warning:
   "Start Graphical Desktop for best results" — do not hard-block
3. PackageInstallRunner.launchApp(ctx, entry, method)
     → LinuxCommandBuilder.build(flux shell with DISPLAY=:0, PULSE, nohup cmd)
4. Optional: open X11 Activity (same Intent as startGui delay path) if user wants display
```

**Do not** invent a second launch path. Call existing runner.

### 3.2 UNINSTALL

```text
1. Confirm dialog (cyber-brutalist sheet / existing confirm helper)
2. PackageInstallRunner.uninstall(ctx, catalog, id, method) 
   OR prepareUninstall + open ID_MP_INSTALLER stream (preferred — same UX as MP)
3. On success: InstallRegistry already updated by runner; refresh Apps list
4. If not marketplace-installed: refuse with toast (v1)
```

Busy flags: reuse `isScriptRunning` / `mpBusy` rules from Marketplace — **do not** start uninstall if installer page already running.

### 3.3 DETAIL

Bottom sheet or sub-card (match MP detail sheet patterns):
- Icon 48–56dp, title, version, env badge, size, summary  
- Primary **LAUNCH** (green)  
- Secondary **UNINSTALL** (danger)  
- Mono metadata: `id`, `installedAt`, `launchCommand`

### 3.4 What Apps must NOT do

| Forbidden | Why |
|-----------|-----|
| Call `LinuxCommandBuilder` with wrong method | Cross-env damage |
| Uninstall via raw `apt remove` without registry | Can nuke system deps |
| Route XFCE start through guest builders | GUI is host-script SSOT |
| Hold `isScriptRunning` for desktop lifetime | Breaks MP/SM (see XFCE plan §0) |
| Write `linux_method` | Isolation toggle owns it |

---

## 4. Navigation & page IDs

### 4.1 New page

| Constant | Value | Layout |
|----------|------:|--------|
| `ID_APPS` | **21** (next free after `ID_AI_SAFETY = 20`) | `appsScrollView` + `appsBody` |

Wire:
- `buildAppsPage()` in onCreate cluster with SM/MP  
- `contentFrame.addView(appsScrollView)`  
- `navigateToPage` visibility branch  
- Back stack: Settings → Apps or Home → Apps; back pops to previous  
- Global nav: Apps is **subpage** (like SM/MP), not bottom-nav tab

### 4.2 Settings Hub

Insert after Software Manager / Marketplace cluster (keep order readable):

```text
… Isolation / Proot / Chroot …
Software Manager
Marketplace
Apps              ← NEW section button (icon: apps / grid)
X11 / Scripts / …
```

Reuse `buildSettingsSectionButton` / same pattern as `buildSoftwareManagerSectionButton()`.

Copy:
- Title: **APPS**  
- Subtitle: `Installed launchable apps · launch & uninstall · {env}`  

### 4.3 Home

Extend `buildHomeLayout()`:

1. **`// DESKTOP` section** — XFCE card (new visual; §5)  
2. **`// PACKAGE OPS` section** — expand to **3 modules** or 2+1 row:

| Layout option | Description | Recommendation |
|---------------|-------------|----------------|
| A. 3-column row | MP \| SM \| Apps | Cramped on phone |
| B. 2+1 | Keep MP+SM twin row; full-width **Apps** strip under | **Recommended** |
| C. Replace SM on home | SM only from Settings | Reject — SM still useful |

Home Apps module card (same `buildHomePackageModuleCard` DNA):
- kicker: `INSTALLED`  
- title: `APPS`  
- subtitle: `Launch & remove\nguest desktop apps`  
- footer: `OPEN · LAUNCH`  
- `pageId = ID_APPS`  
- accent: tertiary or primary-alt rail (must **read different** from MP green and SM muted)

---

## 5. Home XFCE card (UI different from Settings)

Settings Graphical Desktop = compact hub strip (START/STOP/VIEW LOGS).  
Home card = **hero desktop module** — larger, different composition, still NC tokens.

### 5.1 Visual (ui_design.md)

| Rule | Apply |
|------|--------|
| Surface | `#121212` / `NC.SURFACE_LOW` card, 0px corners |
| Extrusion | 6px two-tone L-shadow (`outline-variant` + `surface-bright`) |
| Primary action | Terminal Green `#3DDC84` / `NC.PRIMARY_CON` |
| Danger stop | Existing danger button colors |
| Type | Title Space-Grotesk-like bold; labels JetBrains Mono (`Typeface.MONOSPACE`) |
| Text on green | Dark on-primary (`NC.ON_PRIMARY`) |
| Env line | Mono subtitle: `PROOT · proot-distro debian` / `CHROOT · Debian13 root` |

### 5.2 Home desktop card structure

```text
┌─────────────────────────────────────────────┐
│ [rail primary 4dp]                          │
│  GRAPHICAL DESKTOP            [PROOT|CHROOT]│
│  Termux X11 + XFCE4                         │
│                                             │
│  ┌──────────────────┐  ┌─────────────────┐  │
│  │ START XFCE       │  │ VIEW LOGS       │  │  ← VIEW LOGS only after healthy start
│  └──────────────────┘  └─────────────────┘  │     (same rules as Settings XFCE plan)
│  or STOP | VIEW LOGS side-by-side           │
│                                             │
│  mono status: Idle | Starting | Running     │
└─────────────────────────────────────────────┘
```

**Logic:** call existing `startGui()` / `stopGui()` / `openGuiLogPage()` — **one implementation**, dual UI bindings.  
If Settings buttons and Home buttons both exist: share state via `setGuiCardIdle` / `setGuiCardRunning` helpers **generalized** to update **all** registered GUI control sets (Settings row + Home card). Avoid divergent state.

```kotlin
// Conceptual
interface GuiCardControls {
  fun setIdle(); fun setRunning(showLogs: Boolean)
}
// register settingsGuiControls + homeGuiControls
```

### 5.3 Do not duplicate script paths

Home card **must not** re-branch `linux_method` differently from Settings. Single `startGui`/`stopGui`.

---

## 6. Apps page UI (concrete)

### 6.1 Page chrome

| Element | Spec |
|---------|------|
| Header | `buildSubPageHeader("APPS", back → Settings or stack)` |
| Env badge | PROOT / CHROOT mono chip |
| Status line | `N apps · total size · env` |
| Search | Optional filter on title/id (mono EditText, focus border primary) |
| Refresh | Secondary button — re-read registry + optional catalog hydrate |
| Empty | “No installed apps. Open Marketplace to install.” + button → `ID_MARKETPLACE` |
| Guest not ready | Error mono + link copy to Proot/Chroot settings |

### 6.2 List rows (icon-forward)

Each row = extruded list cell (`NC.SURFACE_LOW`, 1px outline-var, 0 radius):

```text
[ 48dp icon ]  Title                    [LAUNCH]
               v1.0 · 42 MB · APP
               summary one line…
```

- Long-press or chevron → detail sheet  
- LAUNCH = primary small button  
- Uninstall only in sheet or overflow (avoid mis-tap)

Loading: reuse `buildListLoadingView("LOADING APPS…")`.

### 6.3 Icons

Reuse `MarketplaceIcons` (disk cache + URL/path).  
No new icon pipeline. Missing icon → generic app mark drawable.

### 6.4 Tokens checklist (from ui_design.md)

- [ ] No rounded corners  
- [ ] Primary = green slab + dark text  
- [ ] Secondary = dark fill + green stroke  
- [ ] Press = 6→2 offset + 4dp translate  
- [ ] Body/labels mono where data; bold for titles  
- [ ] `#FAFAFA`-class on-surface for readable text (`NC.ON_SURFACE`)

---

## 7. Primary files

| Path | Role |
|------|------|
| `MainActivity.kt` | `ID_APPS`, Settings button, `buildAppsPage`, refresh, home Apps module, home XFCE card, wire launch/uninstall, multi-bind GUI controls |
| `marketplace/AppsInventory.kt` (new, thin) | List APP registry entries + catalog enrich |
| `marketplace/InstallRegistry.kt` | Optional `listApps(env)` helper only |
| `marketplace/PackageInstallRunner.kt` | **Reuse only** — no behavior change required |
| `marketplace/MarketplaceIcons.kt` | Reuse |
| `marketplace/MarketplaceClient.kt` | Optional catalog cache hydrate for titles |
| `DesignTokens.kt` / `ui_design.md` | Visual SSOT |
| GUI scripts / builders / chroot helper | **No changes** |

---

## 8. Implementation order

1. **Plan approval** (this doc).  
2. `AppsInventory.listInstalledApps` + unit-ish parse tests if any exist for registry.  
3. `ID_APPS` page shell + Settings hub button + navigate/back.  
4. List UI + detail sheet + LAUNCH/UNINSTALL → existing runner + MP installer page.  
5. Home **Apps** module card.  
6. Home **XFCE** card + shared GUI control binding (idle/running/logs).  
7. Guest-not-ready + empty states for both envs.  
8. Manual matrix §10 + compileDebugKotlin / compileReleaseKotlin.  
9. Mark plan implemented.

---

## 9. Acceptance criteria

1. Settings Hub has **Apps** entry → dedicated page.  
2. Home has **Apps** entry + **Graphical Desktop** card (UI distinct from Settings strip).  
3. Apps list shows **only installed `kind=app`** for **current** `linux_method`.  
4. Switching proot↔chroot changes list to that env’s registry (no bleed).  
5. LAUNCH uses `PackageInstallRunner.launchApp` → `LinuxCommandBuilder` flux path.  
6. UNINSTALL uses same MP uninstall path; registry updates; list refreshes.  
7. Icons + title/summary/version/size shown when data available.  
8. XFCE Home card START/STOP/VIEW LOGS share logic with Settings (correct proot/chroot scripts).  
9. No edits to isolation builders, chroot helper, or GUI shell assets.  
10. Marketplace + Software Manager still work; `isScriptRunning` not held by XFCE.  
11. UI matches cyber-brutalist rules in `docs/project/ui_design.md` / `NC.*`.  
12. Compile: `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` only unless user asks install.

---

## 10. Manual test matrix

| # | Mode | Steps | Expect |
|---|------|-------|--------|
| A1 | proot | Settings → Apps | Empty or MP apps only for proot |
| A2 | proot | MP install glmark2 → Apps | Row appears with icon; LAUNCH works with X11 up |
| A3 | proot | Uninstall from Apps | Registry clear; gone from list |
| A4 | chroot | Same install/launch/uninstall | Paths via chroot builder; list independent of proot |
| A5 | either | Home → Apps | Same page |
| A6 | either | Home XFCE START | Correct script for method; STOP+VIEW LOGS after healthy start |
| A7 | either | XFCE up + MP install | Not blocked (`isScriptRunning` free) |
| A8 | either | Guest missing | Friendly empty + no crash |
| A9 | either | Catalog offline | Registry still lists; titles may be ids |
| A10 | either | Component-only install (box64) | **Not** listed on Apps |

---

## 11. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Users expect every apt “app” | Empty copy points to Marketplace; phase B desktop scan optional |
| Dual GUI controls desync | Single state helpers update all bound views |
| Launch without X11 | Soft toast; optional auto-open X11 Activity |
| Uninstall wrong env | Always pass `currentLinuxEnv()` / `currentMethod` |
| Scope creep into SM | Explicit filter `PackageKind.APP` only in v1 |

---

## 12. Open decisions (defaults)

| # | Decision | Default |
|---|----------|---------|
| D1 | Desktop `.desktop` scan in v1? | **No** — registry apps only |
| D2 | Hard-require XFCE before LAUNCH? | **No** — warn only |
| D3 | Home package ops layout | **2+1** (MP\|SM row + full-width Apps) |
| D4 | Uninstall UI | Reuse `ID_MP_INSTALLER` stream (same as Marketplace) |
| D5 | Bottom nav tab for Apps? | **No** — subpage only |

---

## 13. Summary

**Apps** = installed **launchable** guest apps for the **active proot/chroot env**, powered by **InstallRegistry + PackageInstallRunner** (same as Marketplace), with Settings + Home entry points and cyber-brutalist UI per `ui_design.md`.  

**Home XFCE card** = second chrome over existing host GUI start/stop/log path (isolation-correct scripts, no guest SSOT edits).  

**Do not** rebuild package systems; **do** compose existing SM/MP isolation-safe services into a launch-first Apps surface.

**Next step:** user approves this plan → implement §8 order only.
