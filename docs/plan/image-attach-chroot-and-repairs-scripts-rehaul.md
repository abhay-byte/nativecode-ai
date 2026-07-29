# Plan: Image Attach (proot/chroot) + Settings “Repairs” (System Scripts rehaul)

**Date:** 2026-07-30  
**Status:** **implemented** (2026-07-30)  
**Scope:** Two independent workstreams that share path/method SSOT and UI rules.  
**Design:** [`docs/project/ui_design.md`](../project/ui_design.md) (Cyber-Brutalist / `NC` tokens).

**Related:**
- [`docs/plan/settings-chroot-card-storage-uninstall.md`](./settings-chroot-card-storage-uninstall.md) — chroot path, root gate, in-page back
- [`docs/plan/project-clone-method-mismatch.md`](./project-clone-method-mismatch.md) — project `linuxMethod` vs global
- Path SSOT: `ChrootCommandBuilder.CHROOT_PATH` = `/data/local/tmp/chrootDebian13`
- Proot rootfs: `ProjectPathResolver.PROOT_ROOTFS_REL` under app `filesDir`

---

## Table of contents

1. [Task A — Image attach fails on chroot](#task-a--image-attach-fails-on-chroot)
2. [Task B — System Scripts → Repairs rehaul](#task-b--system-scripts--repairs-rehaul)
3. [Shared constraints & SSOT](#3-shared-constraints--ssot)
4. [Implementation order](#4-implementation-order)
5. [Test matrix](#5-test-matrix)
6. [Out of scope](#6-out-of-scope)
7. [Approval gates](#7-approval-gates)

---

# Task A — Image attach fails on chroot

## A1. User-reported symptom

From device screenshots (OpenCode in FluxLinux terminal):

1. User attaches an image → UI claims path like `attach_*.png`.
2. Guest tool resolves **`/home/flux/attach_….png`**.
3. File **not found** on disk inside the session.
4. Behavior “works on proot, not chroot.”

This matches a **host write + guest path mismatch**, not an OpenCode bug.

---

## A2. Current code (facts)

### A2.1 Entry points (`MainActivity.kt`)

| UI surface | Picker | Flag |
|------------|--------|------|
| App terminal toolbar | `termImagePickerLauncher` | `isWorkspace = false` |
| Project workspace toolbar | `wsImagePickerLauncher` | `isWorkspace = true` |

Both call:

```kotlin
handleImageAttachment(uri, isWorkspace = …)
```

**Bug today:** `isWorkspace` is **unused**. Project cwd is ignored.

### A2.2 Current attach logic (broken for chroot)

```text
handleImageAttachment(uri):
  ext  = mime → jpg/png/…
  fname = attach_<ms>.$ext
  guestHomeDir = ProjectPathResolver.guestHomeDir(this)  // uses LinuxCommandBuilder.currentMethod
  targetDir =
    if guestHomeDir.exists && isDirectory → guestHomeDir
    else → filesDir/home   // host Termux home fallback
  copy ContentResolver stream → FileOutputStream(destFile)   // app UID write
  guestPath =
    if targetDir == guestHomeDir → "/home/flux/$fname"
    else → "/data/data/com.ivarna.nativecode/files/home/$fname"
  clipboard = guestPath
  toast "Image path copied"
```

### A2.3 Where `guestHomeDir` points

| Method | Host path (`ProjectPathResolver.guestHomeDir`) | App can write? |
|--------|--------------------------------------------------|----------------|
| **proot** | `filesDir/usr/var/lib/proot-distro/containers/debian/rootfs/home/flux` | **Yes** (app private) |
| **chroot** | `/data/local/tmp/chrootDebian13/home/flux` | **No** (root-owned, outside sandbox) |

### A2.4 Why proot “works”

1. Direct `FileOutputStream` into proot rootfs succeeds.
2. Guest path `/home/flux/$fname` is the real file inside the container.
3. AI tools (OpenCode, etc.) open that path and find the file.

### A2.5 Why chroot fails (root causes)

| # | Cause | Detail |
|---|--------|--------|
| **C1** | **Non-writable chroot home** | App UID cannot create files under `CHROOT_PATH/home/flux`. `open`/`copyTo` fails or falls through. |
| **C2** | **Silent failure / wrong fallback** | On failure or missing dir, code falls back to `filesDir/home`. Clipboard may still show `/home/flux/…` only if `exists()` was true but write failed; or shows host data path that **does not exist inside chroot**. |
| **C3** | **Host path not bind-mounted as guest home** | Chroot sessions **do not** bind app `filesDir/home` onto `/home/flux`. Guest home is real rootfs tree under `CHROOT_PATH`. |
| **C4** | **Partial bind only for host-tmp** | `ChrootCommandBuilder` binds `filesDir/usr/tmp` → `$CHROOT_PATH/mnt/host-tmp` and copies `launch_tool.sh` into guest `/tmp`. Staging under host-tmp **is** visible as `/mnt/host-tmp/…` **only if mounts ran for that session**. |
| **C5** | **Method SSOT can lag** | Attach uses ambient `LinuxCommandBuilder.currentMethod`. Workspace should prefer `activeProjectMethod` (set by `applyProjectIsolation`). If global is still `proot` while session is chroot (or reverse), wrong rootfs is targeted. |
| **C6** | **No inject into TTY** | Only clipboard; user/tool must paste. Acceptable if path is correct; optional later: paste into active `TerminalSession`. |

### A2.6 Existing mounts relevant to attach (chroot)

From `ChrootCommandBuilder.build` (session start):

```text
mount --bind filesDir/usr/tmp  →  $CHROOT_PATH/mnt/host-tmp
cp launch_tool.sh into         →  $CHROOT_PATH/tmp/
chmod home/flux
(+ /dev /sys /proc /dev/pts /dev/shm …)
```

From `RootShellService` (related host setup patterns):

```text
mkdir -p $chrootPath/tmp $chrootPath/mnt/host-tmp $chrootPath/sdcard
mount --bind /sdcard → $chrootPath/sdcard
```

**Implication:** A pure no-root attach **can** land under `filesDir/usr/tmp/…` and advertise guest path **`/mnt/host-tmp/…`**, but:

- Mount must already be active (session was started with current builder).
- Tools that only look in `$HOME` will still fail unless path is under `/home/flux`.
- Product expectation from screenshots: **`/home/flux/attach_*.png`**.

**Therefore preferred chroot path is root-assisted copy into `CHROOT_PATH/home/flux`.**

---

## A3. Path SSOT (must use these constants — no hardcode drift)

| Concept | Source | Value / formula |
|---------|--------|-----------------|
| Chroot rootfs | `ChrootCommandBuilder.CHROOT_PATH` | `/data/local/tmp/chrootDebian13` |
| Chroot guest home (host) | `ProjectPathResolver.guestHomeDir(ctx, "chroot")` | `$CHROOT_PATH/home/flux` |
| Proot rootfs | `ProjectPathResolver.prootRootfsDir(ctx)` | `filesDir/…/containers/debian/rootfs` |
| Proot guest home (host) | `ProjectPathResolver.guestHomeDir(ctx, "proot")` | `…/rootfs/home/flux` |
| Guest home path (Debian) | constant | `/home/flux` |
| Project host dir | `ProjectPathResolver.resolve(ctx, projectPath, method)` | rootfs + debian path |
| Active method (settings/app term) | `LinuxCommandBuilder.currentMethod` + prefs `linux_method` | `"proot"` \| `"chroot"` |
| Active method (project workspace) | `activeProjectMethod` (also mirrored into global on open) | per-project `Project.linuxMethod` |
| Root available | `RootShell.isRootAvailable()` | KernelSU / Magisk su probe |
| Host staging (app-writable) | **new** | `filesDir/usr/tmp/nativecode_attach/` (under existing host-tmp bind parent) |

**Never** hardcode `/data/local/tmp/chrootDebian13` in attach code — always `ChrootCommandBuilder.CHROOT_PATH` / `ProjectPathResolver`.

---

## A4. Method resolution for attach (settings vs project)

```text
fun resolveAttachMethod(isWorkspace: Boolean): String {
  return if (isWorkspace) {
    // Project workspace terminal
    activeProjectMethod.ifBlank { LinuxCommandBuilder.currentMethod }
  } else {
    // App-level terminal / settings-adjacent sessions
    LinuxCommandBuilder.currentMethod
  }
}
```

| Context | Method source | Where file should live (guest) |
|---------|---------------|--------------------------------|
| App terminal (home) | global `linux_method` | `/home/flux/attach_*.ext` |
| Project workspace | `activeProjectMethod` | Prefer **project dir** if set: `$activeProjectPath/attach_*.ext` (absolute debian path), else `/home/flux/…` |
| Settings script runner terminal | n/a (no attach bar today) | out of scope unless attach is added later |

**Project path rules (workspace):**

- `activeProjectPath` is Debian path (e.g. `/home/flux/repos/my-app`).
- Host dest = `ProjectPathResolver.resolve(ctx, activeProjectPath, method)` + filename **or** guest home if path empty.
- Guest clipboard path = `ProjectPathResolver.toDebianPath(…)` result, or explicit `"$activeProjectPath/$fname"`.

---

## A5. Target design (correct copy pipeline)

### A5.1 High-level flow

```text
                    ┌─────────────────────┐
  content:// URI ──►│ stage under app UID │  filesDir/usr/tmp/nativecode_attach/$fname
                    └─────────┬───────────┘
                              │
              ┌───────────────┴────────────────┐
              ▼                                ▼
        method=proot                     method=chroot
              │                                │
   File.copy → prootRootfs           RootShell: mkdir + cp + chown
   …/home/flux[/project]/fname      $CHROOT_PATH/home/flux[/project]/fname
              │                                │
              └───────────────┬────────────────┘
                              ▼
              guestPath = /home/flux/… or /home/flux/repos/…/…
              clipboard + toast (+ optional session paste)
```

### A5.2 Algorithm (implementation contract)

```text
fun handleImageAttachment(uri, isWorkspace):
  method = resolveAttachMethod(isWorkspace)
  fname  = attach_<ms>.<ext from mime>
  stageDir = File(filesDir, "usr/tmp/nativecode_attach").mkdirs()
  stageFile = stageDir / fname
  copy URI stream → stageFile   // must succeed or abort with toast

  guestRelativeTarget =
    if isWorkspace && activeProjectPath not blank:
      join(activeProjectPath as debian dir, fname)   // e.g. /home/flux/repos/x/attach_….png
    else:
      /home/flux/$fname

  hostTarget = ProjectPathResolver.resolve(ctx, guestRelativeTarget, method)
    // if guestRelativeTarget is full path starting with /, resolve maps under rootfs

  if method == "proot":
    ensure parent dirs (mkdirs app UID)
    stageFile.copyTo(hostTarget, overwrite=true)
    verify hostTarget.exists && length > 0
  else: // chroot
    if !RootShell.isRootAvailable():
      // Fallback path (document + toast clearly)
      // Keep file in stageDir; guest path becomes /mnt/host-tmp/nativecode_attach/$fname
      // Only valid if session mounts host-tmp; else fail hard with toast
      guestPath = "/mnt/host-tmp/nativecode_attach/$fname"
      clipboard + toast WARN "chroot needs root for /home/flux; using bind path"
      return
    rootCmd = """
      mkdir -p $(dirname $CHROOT_PATH/$rel) &&
      cp -f $stageFile $CHROOT_PATH/$rel &&
      chown flux:flux $CHROOT_PATH/$rel 2>/dev/null || chown 1000:1000 … || true &&
      chmod 644 $CHROOT_PATH/$rel
    """
    // use RootShell.captureResult; check exit 0
    // verify via root: test -f $CHROOT_PATH/$rel && stat size
    guestPath = guestRelativeTarget  // always Debian path starting with /

  clipboard guestPath
  toast "Copied: $guestPath"  (or short form)
  Log.i("ImageAttach", method, hostTarget, guestPath, bytes)
```

**`rel`** = guest path without leading slash, e.g. `home/flux/attach_….png`.

### A5.3 Root copy helper (recommended)

Add a small API so MainActivity does not re-invent su argv:

```text
// RootShell or ProjectPathResolver companion
fun RootShell.copyIntoChroot(hostSrc: File, guestAbsPath: String): CaptureResult
  // guestAbsPath e.g. /home/flux/attach_x.png
  // dest = CHROOT_PATH + guestAbsPath
  // mkdir -p, cp -f, chown flux, chmod 644
```

Reuse patterns from:

- Onboarding chroot script stage: `mkdir -p $chrootTmpPath && cp …`
- `runScriptInTerminal` mode `chroot_guest` stageCmd (same tree, different purpose)

### A5.4 Failure UX

| Condition | UI |
|-----------|-----|
| URI read fail | Toast error; no clipboard overwrite |
| Proot write fail | Toast + log |
| Chroot no root | Prefer explicit toast; optional bind fallback path |
| Chroot root cp fail | Toast with exit code; keep staged file for debug |
| Zero-byte file | Treat as fail |

Do **not** claim `/home/flux/…` on clipboard unless the file is verified at the corresponding host path (proot: `File.exists`; chroot: root `test -f` or successful cp exit 0).

### A5.5 Optional enhancements (same PR if cheap; else phase 2)

1. **Paste into active session:** after success, `session.write(guestPath + " ")` or bracket path for shell.
2. **Delete stage after successful proot/chroot install** to save space (keep on failure).
3. **MIME → extension** map (`image/jpeg`→`jpg`, `image/png`→`png`, `image/webp`→`webp`); reject non-images.
4. **Unify launchers** into one method taking `AttachTarget { APP_TERM, WORKSPACE }`.

---

## A6. Files to touch (Task A)

| File | Change |
|------|--------|
| `MainActivity.kt` | Rewrite `handleImageAttachment`; use method + project path; better toast/log |
| `RootShellService.kt` (`RootShell`) | Optional `copyIntoChroot(hostSrc, guestAbsPath)` |
| `ProjectPathResolver.kt` | Optional helpers: `guestAttachDir`, `stageAttachDir(ctx)` — keep SSOT |
| Tests (if any JVM unit) | Path join + method selection pure functions |

**No asset/script changes required** for attach.

---

## A7. Acceptance criteria (Task A)

1. **Proot app terminal:** attach → file exists under proot `home/flux`; clipboard `/home/flux/attach_*`; OpenCode/read finds it.  
2. **Chroot app terminal (root granted):** attach → file exists under `$CHROOT_PATH/home/flux`; clipboard `/home/flux/attach_*`; OpenCode finds it.  
3. **Chroot without root:** no false `/home/flux` path; either bind fallback `/mnt/host-tmp/…` with clear toast **or** hard fail.  
4. **Workspace + project path:** file lands in project dir under correct method’s rootfs; clipboard uses debian project path.  
5. **Method switch:** after Settings Isolation → chroot, next attach uses chroot pipeline (not stale proot path).  
6. No SELinux crash; no blocking UI thread on root `cp` (background + main-thread toast).

---

# Task B — System Scripts → Repairs rehaul

## B1. User request (from screenshots + text)

1. Settings hub card **“SYSTEM SCRIPTS”** → rename to **“REPAIRS”** (label + subtitle).  
2. Destination page rehaul to match [`ui_design.md`](../project/ui_design.md).  
3. **In-app back button** on the page (like Chroot Settings / Proot Settings headers).  
4. **Proot / Chroot run actions:**  
   - Not “both always on every card” as equal primary chrome, **or**  
   - **Tabbed** host context, **or**  
   - Buttons only (no whole-card click for mode).  
5. **Show chroot actions only when root is available** — same spirit as Chroot Settings (`RootShell.isRootAvailable()` / `applyNoRootChrootCardUi` pattern).

---

## B2. Current code (facts)

### B2.1 Hub entry

- `buildScriptsSectionButton()` in `buildSettingsHubLayout()`.
- Title: `SYSTEM SCRIPTS` / sub: `Run installation, configuration, or control scripts`.
- Navigates `ID_SCRIPTS` via `pageStack` + `navigateToPage`.
- Icon: `ic_history` (history icon — poor semantic fit for “repairs”).

### B2.2 Page builder `buildScriptsLayout()`

Sections today:

| Section | Scripts | Actions on card |
|---------|---------|-----------------|
| Host (Native Termux) | `setup_termux.sh`, `flux_install.sh`, `start_gui.sh`, `stop_gui.sh` | `RUN ON HOST` → mode `host` |
| Guest (Debian) | `setup_debian_family.sh`, `setup_customization_debian.sh`, `setup_hw_accel_debian.sh`, `setup_cli_tools.sh` | **both** `RUN IN PROOT` + `RUN IN CHROOT` on every card |
| Chroot host | `setup_debian13_chroot.sh`, `uninstall_debian13_chroot.sh` | `RUN CHROOT SCRIPT (ROOT)` → `chroot_host` |

### B2.3 UI debt vs design system

| Current | Design rule |
|---------|-------------|
| Header plain `TextView` 20sp, **no back button** | Match Chroot Settings: back + icon + title + mono subtitle |
| Cards `roundedBg(…, dp(12))` | **0px radius only** |
| Buttons `cornerRadiusDp = 4` | **0px** |
| Mixed white text on green | Primary: green fill + dark text (`NC.ON_PRIMARY` / `#0A0A0A`) |
| Chroot always listed | Gate on root like chroot settings page |
| No loading/disabled root state | Async root probe + badges |

### B2.4 Runner path (keep behavior)

`runScriptInTerminal(name, mode)` already implements:

| mode | Behavior |
|------|----------|
| `host` | Host bash via package env |
| `proot` | `LinuxCommandBuilder.build(…, user=root)` guest script from `filesDir/home/$script` |
| `chroot_guest` | Root: stage to `$CHROOT_PATH/tmp/$script`, run via `run_debian13_root.sh` or mount+chroot |
| `chroot_host` | Root: `su -c "sh $scriptPath"` for install/uninstall scripts |

**Repairs rehaul must not break these modes** — only UI, gating, naming, navigation chrome.

### B2.5 Back navigation today

- System/predictive back: `onBackPressed` if `scriptsScrollView` visible → `ID_SETTINGS`.
- **No** in-layout back on scripts page (unlike `ID_CHROOT_SETTINGS` which uses `pageBackBtn` in header).
- Script install subpage has `scriptInstallBackBtn` → `onBackPressed()`.

---

## B3. Product naming

| Old | New |
|-----|-----|
| SYSTEM SCRIPTS (hub) | **REPAIRS** |
| Subtitle hub | e.g. `Re-run host, guest, and chroot fix scripts` |
| Page title | **Repairs** or **REPAIRS** (match hub casing convention of other pages: `CHROOT SETTINGS` style → **`REPAIRS`**) |
| Subtitle page | `// RE-RUN SETUP & FIX SCRIPTS` (mono, `ON_SURF_VAR`) |
| Script runner page | unchanged titles via `scriptRunnerTitle` |

IDs stay `ID_SCRIPTS` / `ID_SCRIPT_INSTALL` (internal) to avoid wide renames unless a pure rename is desired — **prefer keep IDs**, change user-visible strings only.

---

## B4. UX structure (recommended)

### B4.1 Preferred layout: **header + segment tabs + card list**

```text
┌──────────────────────────────────────────────┐
│ [←]  🔧  REPAIRS                             │  cyber header (same as chroot settings)
│ // RE-RUN SETUP & FIX SCRIPTS                │
│ ──────────────────────────────────────────── │
│ ┌──────────┬───────────┬──────────────────┐  │  segment control (sharp, 0 radius)
│ │  HOST    │   GUEST   │  CHROOT*         │  │  *tab only if rootAvailable
│ └──────────┴───────────┴──────────────────┘  │
│                                              │
│ ┌──────────────────────────────────────────┐ │  script card (extrusion 6dp)
│ │ setup_termux.sh                    HOST  │ │
│ │ Setup basic environment…                 │ │
│ │                    [ RUN ]               │ │  single primary button
│ └──────────────────────────────────────────┘ │
│ …                                            │
└──────────────────────────────────────────────┘
```

**Tab rules:**

| Tab | Visible when | Contents |
|-----|--------------|----------|
| **HOST** | always | host scripts; each card **one** `RUN` → mode `host` |
| **GUEST** | always | guest scripts; **one** primary action that uses **current** isolation method (`LinuxCommandBuilder.currentMethod`) → `proot` or `chroot_guest` |
| **CHROOT** | **only if** `RootShell.isRootAvailable()` | install/uninstall chroot host scripts; `RUN` → `chroot_host` |

**Guest tab action labeling:**

- If global method proot: button `RUN IN PROOT` (or `RUN` + mono badge `PROOT`).
- If global method chroot: button `RUN IN CHROOT` (badge `CHROOT`).
- Optional secondary text link: “Also run on other method…” only if that method’s rootfs is present **and** (for chroot) root available — **not** two equal primary greens.

**Rationale vs “both buttons on every card”:**

- Matches user ask: mode selected by **button/tab**, not ambiguous dual CTA.
- Aligns guest run with Settings Isolation (same SSOT as terminals).
- Chroot install/uninstall isolated behind root gate (like Chroot Settings).

### B4.2 Alternative (if tabs feel heavy)

Keep three vertical sections, but:

1. Add page back header.  
2. Guest cards: **single** button from current method.  
3. Entire “Chroot (KernelSU)” section `visibility = GONE` when `!rootOk`.  
4. When root checking: show mono `ROOT …` pulse row (mirror chroot settings badge).

**Plan default = B4.1 tabs** (clearer, matches “or make it tabbed”).

### B4.3 Root gating (mirror Chroot Settings)

```text
on page show / onResume repairs:
  setRootBadge(CHECKING)
  bg thread: rootOk = RootShell.isRootAvailable()
  main:
    if rootOk → show CHROOT tab (or section)
    else → hide CHROOT tab; if was selected, snap to GUEST or HOST
```

Do **not** use `File.exists("/system/bin/su")` — use `RootShell` only (KSU/Magisk hide su).

Optional: if root ok but chroot dir missing, still show CHROOT tab (install script is how you get a rootfs).

### B4.4 In-app back button

Copy pattern from `buildChrootSettingsLayout` / proot settings:

```text
headerTitleRow:
  pageBackBtn (ic_arrow_back) → onBackPressed() / navigateToPage(ID_SETTINGS)
  icon (prefer ic_refresh or repair-like; avoid ic_history if better icon exists)
  title REPAIRS
subtitle mono
divider
```

Ensure:

- `pageStack` push on hub → repairs (already).
- `onBackPressed` branch for `scriptsScrollView` remains correct.
- `backBtn` global visibility still `pageStack.size > 1` (optional dual chrome: page-local back is enough if global bar hidden on nested — match chroot settings behavior).

### B4.5 Card / button chrome (ui_design.md)

| Element | Spec |
|---------|------|
| Page bg | `NC.BG` |
| Cards | `cyberBrutalistBg` fill `SURFACE_LOW`/`SURFACE_CONTAINER`, stroke `OUTLINE_VAR`, offset 6, **radius 0**, right face `OUTLINE_VAR`, bottom `SURFACE_BRIGHT` |
| Press | offset 6→2, translate 4px (existing touch pattern) |
| Primary RUN | fill `PRIMARY` / `PRIMARY_CON`, text `ON_PRIMARY` dark |
| Destructive (uninstall) | error tokens (`ERROR` stroke, not primary green) |
| Script name | mono 14–15, `ON_SURFACE` |
| Description | body/mono 11–12, `ON_SURF_VAR` |
| Tab selected | green border or filled slab; unselected dim surface |
| No rounded `dp(12)` / `cornerRadiusDp = 4` leftovers |

### B4.6 Script inventory (no behavioral change)

**HOST**

| Script | Purpose |
|--------|---------|
| `setup_termux.sh` | Host env/dirs |
| `flux_install.sh` | One-click proot + family |
| `start_gui.sh` | Start desktop/display |
| `stop_gui.sh` | Stop GUI |

**GUEST**

| Script | Purpose |
|--------|---------|
| `setup_debian_family.sh` | users / VNC |
| `setup_customization_debian.sh` | themes/packages |
| `setup_hw_accel_debian.sh` | Turnip/VirGL (`FLUX_GPU`) |
| `setup_cli_tools.sh` | NVM/Node/AI CLIs |

**CHROOT (root only UI)**

| Script | Purpose |
|--------|---------|
| `setup_debian13_chroot.sh` | Install rootfs |
| `uninstall_debian13_chroot.sh` | Uninstall + unmount |

Deploy paths stay: assets `scripts/…` vs `scripts/chroot/…` as today.

---

## B5. Files to touch (Task B)

| File | Change |
|------|--------|
| `MainActivity.kt` | Hub rename; rewrite `buildScriptsLayout` (+ maybe split `buildRepairsLayout`); tabs/state; root probe; card builder; back header; keep `runScriptInTerminal` |
| Drawables | Optional better icon than `ic_history` if available (`ic_refresh`, `ic_reset_thick`) |
| Strings | User-visible only (no `strings.xml` requirement if code-built UI) |

**Do not** change script shell contents unless a separate repair bug is found.

---

## B6. Acceptance criteria (Task B)

1. Hub shows **REPAIRS** (not SYSTEM SCRIPTS); opens same feature surface.  
2. Page has **visible in-app back** → Settings hub.  
3. UI sharp 0px, two-tone extrusion, NC palette (no 12dp rounded script cards).  
4. **Without root:** no CHROOT tab/section; guest/host still usable.  
5. **With root:** CHROOT tab shows install/uninstall; run still uses `chroot_host`.  
6. Guest run uses **one** CTA tied to current `linux_method` (`proot` vs `chroot_guest`).  
7. Script runner page + titles + deploy paths unchanged in behavior.  
8. System back and page back both return to Settings without stack corruption.

---

# 3. Shared constraints & SSOT

## 3.1 Isolation method map (settings vs project)

```text
                    prefs["linux_method"]
                            │
                            ▼
              LinuxCommandBuilder.currentMethod
                 │                    │
     App terminal / Settings     Project open:
     Isolation toggle            applyProjectIsolation /
                                 addAndOpenProject
                                        │
                                        ▼
                              activeProjectMethod
                              Project.linuxMethod
```

| Feature | Method to use |
|---------|----------------|
| Image attach (app term) | `LinuxCommandBuilder.currentMethod` |
| Image attach (workspace) | `activeProjectMethod` (fallback global) |
| Guest repair script CTA | `LinuxCommandBuilder.currentMethod` |
| Proot rootfs host path | `ProjectPathResolver.prootRootfsDir` |
| Chroot rootfs host path | `ChrootCommandBuilder.CHROOT_PATH` |
| Root capability | `RootShell.isRootAvailable()` only |

## 3.2 Correct chroot paths cheat-sheet

| Purpose | Host path | Guest path |
|---------|-----------|------------|
| Guest home | `$CHROOT_PATH/home/flux` | `/home/flux` |
| Attach target (default) | `$CHROOT_PATH/home/flux/attach_*.ext` | `/home/flux/attach_*.ext` |
| Project file | `$CHROOT_PATH` + project debian path | project debian path |
| Script stage (repairs guest) | `$CHROOT_PATH/tmp/$script` | `/tmp/$script` |
| Host-tmp bind | `filesDir/usr/tmp` | `/mnt/host-tmp` |
| App stage for attach | `filesDir/usr/tmp/nativecode_attach/` | (via root cp) → home **or** fallback `/mnt/host-tmp/nativecode_attach/` |

## 3.3 Proot paths cheat-sheet

| Purpose | Host path | Guest path |
|---------|-----------|------------|
| Guest home | `…/containers/debian/rootfs/home/flux` | `/home/flux` |
| Attach | same tree under home/flux | `/home/flux/attach_*.ext` |
| Scripts run | often invoked via host `filesDir/home/$script` path **inside** proot command | as today |

---

# 4. Implementation order

| Step | Work | Depends |
|------|------|---------|
| 1 | **Task A** — rewrite attach pipeline + root copy helper | none |
| 2 | Manual verify attach on proot + chroot devices | 1 |
| 3 | **Task B** — hub rename + repairs layout/tabs/root gate/back | none (parallel after 1 if two people) |
| 4 | Manual verify repairs host/guest/chroot gating | 3 |
| 5 | Regression: script runner titles, chroot uninstall deep-link, isolation toggle | 1–4 |

**Recommended single PR or two stacked PRs:**  
- PR1: image attach chroot fix (higher user pain from screenshots).  
- PR2: repairs UI rehaul.

---

# 5. Test matrix

## 5.1 Image attach

| # | Setup | Action | Expected |
|---|-------|--------|----------|
| A1 | proot, app terminal | attach PNG | `/home/flux/attach_*.png` exists in proot rootfs; tool opens it |
| A2 | chroot + root, app terminal | attach PNG | file under `$CHROOT_PATH/home/flux/`; guest path `/home/flux/…` works in OpenCode |
| A3 | chroot, **no** root grant | attach | no fake home path; toast explains; or bind path only if mounts up |
| A4 | workspace project `/home/flux/repos/x` on chroot | attach | file under project dir on chroot rootfs; clipboard project path |
| A5 | same project on proot | attach | under proot rootfs project dir |
| A6 | switch Isolation proot→chroot | attach again | uses chroot pipeline |
| A7 | large image | attach | no ANR; bg copy |

## 5.2 Repairs

| # | Setup | Action | Expected |
|---|-------|--------|----------|
| B1 | any | open Settings → REPAIRS | new name; page opens |
| B2 | any | page back | Settings hub |
| B3 | no root | repairs | no CHROOT tab; host+guest OK |
| B4 | root | repairs | CHROOT tab; uninstall uses error styling |
| B5 | method=proot | guest RUN | `runScriptInTerminal(..., "proot")` |
| B6 | method=chroot + root | guest RUN | mode `chroot_guest`; script in `$CHROOT_PATH/tmp` |
| B7 | run host script | finishes | toast; back works |
| B8 | visual | — | 0 radius, extrusion, dark/green tokens |

---

# 6. Out of scope

- Changing OpenCode / third-party CLI internals.  
- New multi-image album picker.  
- Auto-uploading images to remote.  
- Rewriting shell scripts under `assets/scripts/`.  
- Merging Chroot Settings install into Repairs (keep both; repairs = re-run scripts).  
- Proot/chroot storage meters (already separate pages).  
- Onboarding Requirements page (separate feature).

---

# 7. Approval gates

Before implementation:

1. Confirm **Task A** guest path policy: always prefer `/home/flux/…` via root `cp` on chroot; bind fallback only if no root.  
2. Confirm **Task B** default UX: **tabs** (HOST / GUEST / CHROOT) vs sections-only.  
3. Confirm guest RUN follows **global** `linux_method` (not a per-card dual button).  
4. Confirm hub string exactly **REPAIRS** (vs “Repair Scripts”).

**Stop.** No code changes in this planning task.

---

## Appendix A — Bug anchor (attach)

```2881:2897:app/src/main/java/com/ivarna/nativecode/MainActivity.kt
// handleImageAttachment — writes guestHomeDir via app File I/O;
// chroot home is not app-writable; isWorkspace ignored.
```

## Appendix B — Path anchor (chroot)

```10:12:app/src/main/java/com/ivarna/nativecode/terminal/ChrootCommandBuilder.kt
const val CHROOT_PATH = "/data/local/tmp/chrootDebian13"
```

```57:64:app/src/main/java/com/ivarna/nativecode/terminal/ProjectPathResolver.kt
fun guestHomeDir(ctx, method):
  chroot → File(CHROOT_PATH, "home/flux")
  else   → File(prootRootfsDir, "home/flux")
```

## Appendix C — Root gate anchor (chroot settings)

```7273:… MainActivity.kt
val rootOk = RootShell.isRootAvailable()
// applyNoRootChrootCardUi when false
```

Repairs page must reuse the same probe semantics.
