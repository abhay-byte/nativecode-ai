# Settings: Graphical Desktop — VIEW LOGS + COPY (safe reuse of script-install page)

**Date:** 2026-08-02  
**Status:** implemented (architecture revised after break-risk review)  
**Scope:** Settings Hub **Graphical Desktop** card gains **VIEW LOGS**. Opens reusable terminal log surface (script-install page chrome) with **COPY**. Capture start/stop script stdout without holding shared `isScriptRunning` for the whole XFCE lifetime.  
**Implemented:** `MainActivity.kt` — streamed start/stop capture to `cacheDir/gui_desktop.log` (512 KB ring), VIEW LOGS beside STOP after healthy start, short host `cat` on `ID_SCRIPT_INSTALL` via **/system/bin/sh** (not libbash), COPY on script-install top bar; isolation layers untouched (B + D shipped).  
**Review polish (2026-08-02):** STOP/VIEW LOGS only after first start log line; cancel (`-1`) does not steal stop flip; fail toast + auto-open log; `deployScripts` + `ensureBootstrapExtracted` before start; `ShellCommandRunner` linker64 also for `/data/app` host tools; X11 opens on healthy line (not blind 500ms).  
**Out of scope:** redesign Settings Hub; auto logcat of guest XFCE; KDE; changing main/workspace PTY sessions; rewriting start_gui scripts; **any change to isolation mode, LinuxCommandBuilder / ProotCommandBuilder / ChrootCommandBuilder / nativecode_chroot.sh, or proot↔chroot guest runners**.

**Related:**
- [`settings-xfce-chroot-gui-launch.md`](./settings-xfce-chroot-gui-launch.md) — proot/chroot GUI host scripts
- [`chroot-ssot-shell-runner.md`](./chroot-ssot-shell-runner.md) — guest SSOT; GUI host stack out of that path
- [`docs/environment/nativecode-chroot-ssot.md`](../environment/nativecode-chroot-ssot.md) — helper contract; proot untouched
- [`settings-chroot-card-storage-uninstall.md`](./settings-chroot-card-storage-uninstall.md)
- [`docs/start-gui-debug.md`](../start-gui-debug.md)
- UI: [`docs/project/ui_design.md`](../project/ui_design.md)

**Primary files:**

| Path | Role |
|------|------|
| `MainActivity.kt` | Card VIEW LOGS; GUI log capture; open log page; COPY on script-install top bar |
| `terminal/ShellCommandRunner.kt` | Prefer `runStreamed` / `runStreamedCancelable` for GUI (already exists) |
| GUI assets | Unchanged behavior; `$1` defaults to `debian` |
| `secondaryButton` / `copyToClipboard` | Reuse |

---

## 0. Break-risk verification (mandatory read)

Reviewed shared terminal / script-runner / marketplace state. **Naive plan “startGui → runScriptInTerminal” is unsafe.** Use §0.4 revised design.

### 0.1 Shared components map

| Component | Used by | GUI plan impact |
|-----------|---------|-----------------|
| `scriptInstallLayout` / `scriptInstallTerminalView` | System Scripts, Chroot uninstall, CLI setup, guest run dialogs | Safe if only **opened for short cat/tail** or finished snapshot — not if start_gui owns session for hours |
| `scriptInstallSession` | Same | **One slot** — new run replaces prior session |
| `isScriptRunning` | Script install finish hook; **back** on script page; **marketplace** install/uninstall; leave marketplace | **Critical** — must not stay true for whole XFCE session |
| `ID_SCRIPT_INSTALL` / `pageStack` | Script runners | OK if VIEW LOGS only push when user asks |
| `mpInstallerSession` / `mpInstallerTerminalView` | Marketplace only | Separate TerminalView; shares **flag** `isScriptRunning` only |
| `terminalView` + `sessionsList` | Home / AI tools terminal | **Independent** — not touched by script install |
| `workspaceTerminalView` | Project workspace terminal | **Independent** |
| `scriptFontSize` / zoom | Script + marketplace + optional global | COPY button only; font path already includes `scriptInstallTerminalView` |
| `onDestroy` | finishes `scriptInstallSession` + `mpInstallerSession` + `sessionsList` | No change |
| `HostCommandBuilder` | Host scripts + ShellCommandRunner env | GUI already uses bash+script path today |
| `ShellCommandRunner.run` | Current startGui/stopGui | Keep pattern; switch to streamed capture |

### 0.2 Why `runScriptInTerminal(start_gui*)` breaks things

**Scripts block until XFCE exits:**

```sh
# start_gui.sh — proot path ends with:
proot-distro login "$DISTRO" … startxfce4   # blocks for desktop lifetime

# start_gui_chroot.sh → start_debian13_gui.sh:
exec dbus-launch --exit-with-session startxfce4   # blocks until logout
```

If Settings START uses `runScriptInTerminal`:

| Shared flag / UX | Failure mode |
|------------------|--------------|
| `isScriptRunning = true` until XFCE ends | **Marketplace** install/uninstall blocked (`mpBusy \|\| isScriptRunning`) for entire desktop session |
| Back on script page | `if (isScriptRunning) return` — user stuck if they open VIEW LOGS mid-run |
| Marketplace leave | Same flag blocks leaving installer mid-run (OK for real installs; wrong for long GUI) |
| Toast `"$scriptName Finished!"` | Only when desktop dies — spam / wrong semantics |
| CLI setup / chroot uninstall / System Scripts | Cannot start while desktop “script” still running |
| `stopGui` via second `runScriptInTerminal` | Must kill first session; races; stop is short but start still holds flag until dead |

**Today’s Settings path is safe for other components:**  
`startGui()` uses `executor` + `ShellCommandRunner.run` — **does not** set `isScriptRunning`. Marketplace and main terminals keep working while XFCE is up.

**System Scripts → start_gui.sh already has this footgun** if user runs GUI from Scripts page (pre-existing). Do **not** make Settings copy that footgun as the default path.

### 0.3 What is safe / not shared

| Surface | Shared with GUI plan? | Break risk if COPY / VIEW LOGS only |
|---------|----------------------|-------------------------------------|
| Main terminal (`initTerminalView`, tool tabs) | No session share | **None** |
| Project workspace terminal | No | **None** |
| Marketplace installer page | Shares `isScriptRunning` only | **High** if GUI holds flag; **None** if GUI uses streamed capture |
| Script install page | Session + flag | **Low** if VIEW LOGS uses short `cat` / snapshot session |
| Top-bar insets (`scriptInstallLayout.getChildAt(0)`) | Padding only | **None** if COPY is extra child in same topBar LinearLayout |
| Global zoom | Updates all terminal views | **None** |

### 0.4 Revised architecture (non-breaking) — **REQUIRED**

**Do not** route long-lived `start_gui*` / chroot start through `runScriptInTerminal` for Settings START.

```
START/STOP (Settings)
    │
    ├─ keep: FGS, X11 Activity delay, card flip
    ├─ deployScripts + bash script path (same as today)
    ├─ ShellCommandRunner.runStreamedCancelable (or runStreamed)
    │       onLine  → append GuiLogBuffer + optional file
    │       onDone  → record exit; enable VIEW LOGS
    └─ does NOT set isScriptRunning

VIEW LOGS
    │
    ├─ write buffer → filesDir/cache/gui_desktop.log (if not already)
    └─ short host session on script-install page:
           bash -c 'cat …/gui_desktop.log'
       OR open page + bind read-only TextView (prefer TerminalView cat for one chrome)
       openLogPage = true only for this short job
       isScriptRunning true only for cat duration (ms)

COPY (script-install top bar)
    │
    └─ transcriptOf(scriptInstallSession) → clipboard
       (also works for uninstall/setup — additive, safe)
```

**Optional live tail while start is running:**  
Background stream writes file; VIEW LOGS can `tail -n +1 -f log` with cancel on back — only if implemented carefully (cancel job on page leave). v1: static `cat` of buffer after lines arrive is enough (user can re-open VIEW LOGS).

**Stop script** is short (~seconds of pkill). Can use either streamed capture (preferred, no flag) or short `runScriptInTerminal` — streamed is consistent.

### 0.5 Pre-existing hazards (document; not introduced by this feature)

| Hazard | Notes |
|--------|--------|
| `start_gui.sh` line `am force-stop "$PKG"` | Can kill app if `am` allows; chroot start script **avoids** force-stop. Pre-existing for Scripts + current Settings. Out of scope unless fixing GUI scripts separately. |
| System Scripts host `start_gui.sh` | Already long-running under `isScriptRunning` | Separate cleanup later: mark GUI scripts “background / no lock” or warn in Scripts UI |
| `DISTRO=${1:-debian}` | HostCommandBuilder passes **no** `$1`; default `debian` OK. Old Settings passed `"debian"` argv — redundant |

### 0.6 Regression checklist (must pass after impl)

| # | Scenario | Must still work |
|---|----------|-----------------|
| R1 | Main terminal tools / multi-tab sessions while XFCE running | Sessions alive; no finish from GUI start |
| R2 | Project workspace terminal while XFCE running | Independent PTY |
| R3 | Marketplace install while XFCE running | **Not** blocked by `isScriptRunning` |
| R4 | Chroot uninstall / setup_cli_tools while XFCE running | Can start script page (may still conflict with X11/CPU; not flag-blocked) |
| R5 | System Scripts non-GUI script | `runScriptInTerminal` default `openLogPage` behavior unchanged |
| R6 | Back from script page after setup finishes | Back visible; pop stack |
| R7 | Marketplace install back-block while **install** running | Still blocks only for mp/script real runs |
| R8 | Font zoom | All terminals including script page |
| R9 | `onDestroy` | Still finishes script + mp + main sessions |
| R10 | STOP while start stream still writing | Cancel `ShellJob` then run stop stream; buffer keeps both sections |

### 0.7 Verdict

| Approach | Safe? |
|----------|-------|
| A. Settings start → `runScriptInTerminal(start_gui*)` | **NO** — holds `isScriptRunning` for desktop lifetime; breaks marketplace + back + other scripts |
| B. Stream capture + short VIEW LOGS `cat` on same page chrome | **YES** — preserves other terminals and marketplace |
| C. New Activity only for GUI logs | Safe but violates “reuse components” |
| D. COPY only on script-install top bar | **YES** — additive |

**Ship B + D.** Reject A for Settings path.


---

## 0A. Isolation mode + runner SSOT — follow only, **zero changes**

Global isolation and proot/chroot runners are **frozen** for this feature. VIEW LOGS / COPY only **read** method and **reuse** existing GUI host script branch. No edits to builders, helper, or guest enter paths.

### 0A.1 Global isolation (SSOT)

| Item | Source | Rule for this feature |
|------|--------|------------------------|
| Pref key | `nativecode_prefs` → `linux_method` (`proot` \| `chroot`) | **Read only** — same as current `startGui()` / `stopGui()` |
| In-memory | `LinuxCommandBuilder.currentMethod` | **Do not write** from GUI log code; isolation toggle / project open already own it |
| Env badge helpers | `currentLinuxEnv()` | Optional for log header string only |
| Settings isolation card | proot/chroot toggle UI | **No changes** |

```kotlin
// Existing branch — keep exactly this selection logic (or call same helper)
val method = prefs.getString("linux_method", "proot") ?: "proot"
// start:  chroot → start_gui_chroot.sh  else start_gui.sh
// stop:   chroot → stop_gui_chroot.sh   else stop_gui.sh
```

Do **not** invent a third path, merge scripts, or pick script from `LinuxCommandBuilder.build`.

### 0A.2 Runner layers (do not mix)

```text
Guest interactive / tools / git / marketplace guest
  └─ LinuxCommandBuilder.build(method)
        ├─ proot  → ProotCommandBuilder   (proot-distro login …)
        └─ chroot → ChrootCommandBuilder  → nativecode_chroot.sh (SSOT v2.2)

Host scripts (setup_termux, flux_install, GUI host stack)
  └─ bash + $HOME/<script>.sh via ShellCommandRunner / HostCommandBuilder
        (NOT LinuxCommandBuilder)

Graphical Desktop (already implemented)
  └─ host GUI orchestrator scripts (app uid) only:
        proot:  start_gui.sh / stop_gui.sh
        chroot: start_gui_chroot.sh / stop_gui_chroot.sh
                └─ (internal) root start_debian13_gui.sh / stop_debian13_gui.sh
```

Docs:

| Doc | Constraint we honor |
|-----|---------------------|
| [`docs/environment/nativecode-chroot-ssot.md`](../environment/nativecode-chroot-ssot.md) | Chroot guest enter = helper only; **proot untouched**; sticky `/tmp`; host tmp = `/mnt/host-tmp` |
| [`docs/plan/chroot-ssot-shell-runner.md`](./chroot-ssot-shell-runner.md) | **Out of scope for SSOT plan:** “GUI host stack (Pulse/X11) stays in `start_gui_chroot.sh`” — we keep that split |
| [`docs/plan/settings-xfce-chroot-gui-launch.md`](./settings-xfce-chroot-gui-launch.md) | Settings branches on `linux_method`; proot scripts unchanged; chroot no `proot-distro` / no `pkill proot` on stop |

### 0A.3 Freeze list — **must not modify**

| Path / API | Why frozen |
|------------|------------|
| `LinuxCommandBuilder.kt` | Isolation dispatch SSOT |
| `ProotCommandBuilder.kt` | Proot guest runner SSOT |
| `ChrootCommandBuilder.kt` | Chroot Kotlin thin client + paths |
| `assets/scripts/chroot/nativecode_chroot.sh` | On-device chroot SSOT helper v2.2 |
| `CHROOT_PATH` / `CHROOT_HELPER` / version stamp | Path contract |
| `Proot` guest scripts / `nativecode_proot_fast` if present | Proot path |
| `start_gui.sh` / `stop_gui.sh` | Proot GUI SSOT (behavior) |
| `start_gui_chroot.sh` / `stop_gui_chroot.sh` / `start_debian13_gui.sh` / `stop_debian13_gui.sh` | Chroot GUI host/root SSOT |
| Isolation toggle / prefs write for `linux_method` | Settings isolation card |
| Guest enter used by terminals, git, SoftMgr, CLI | Unrelated surfaces |

**Allowed touch (this feature only):**

| Path | Allowed change |
|------|----------------|
| `MainActivity.kt` | Card VIEW LOGS; stream capture of **existing** start/stop argv; short host `cat` log page; COPY on script-install bar |
| `ShellCommandRunner` | **No API change required** — already has `runStreamed*`; optional use only |
| New small log buffer helper (optional new file under `terminal/` or private in MainActivity) | Host-only log file; **no** chroot/proot enter |

### 0A.4 Correct GUI call shape (unchanged selection)

```text
prefs linux_method
    │
    ├─ proot  → ShellCommandRunner + bash $HOME/start_gui.sh  [debian]
    │           stop  → stop_gui.sh
    │           (internally proot-distro — script owns it; we do not call ProotCommandBuilder)
    │
    └─ chroot → ShellCommandRunner + bash $HOME/start_gui_chroot.sh
                stop  → stop_gui_chroot.sh
                (internally su + start_debian13_gui — script owns mounts/XFCE;
                 we do NOT call ChrootCommandBuilder / nativecode_chroot for Settings START)
```

VIEW LOGS display session:

```text
Host only: bash -c 'cat <cache>/gui_desktop.log'
  → HostCommandBuilder env / plain host argv
  → NEVER LinuxCommandBuilder.build (would enter guest)
  → NEVER method-specific guest cat
```

Log **header** may record method for humans:

```text
=== START method=chroot script=start_gui_chroot.sh ===
```

That is metadata only — not a runner change.

### 0A.5 Explicit anti-patterns (reject in review)

| Anti-pattern | Why |
|--------------|-----|
| `LinuxCommandBuilder.build(..., "startxfce4")` for Settings START | Wrong layer; bypasses host Pulse/X11 orchestrator |
| `ChrootCommandBuilder` / helper for VIEW LOGS | Log is host file; no guest enter |
| Unify proot+chroot into one start script in this PR | Out of scope; breaks XFCE SSOT split |
| Change sticky `/tmp` or X11 bind in debian13 GUI scripts | Isolation/mount contract |
| Stop chroot with `stop_gui.sh` (pkill proot) | Forbidden by xfce-chroot plan |
| Start proot with `start_gui_chroot.sh` | Wrong rootfs |
| Write `linux_method` from log UI | Isolation ownership elsewhere |
| “Fix” `nativecode_chroot.sh` while adding logs | Scope creep; SSOT frozen |

### 0A.6 Compliance checklist (impl + review)

- [ ] `startGui`/`stopGui` still choose scripts **only** from `linux_method` (same 4 names as today)
- [ ] Diff does **not** include `LinuxCommandBuilder` / `ProotCommandBuilder` / `ChrootCommandBuilder` / `nativecode_chroot.sh`
- [ ] Diff does **not** change proot/chroot GUI asset behavior (unless pre-approved separate bugfix)
- [ ] VIEW LOGS uses host `cat` (or host stream buffer UI) — no guest `login`/`sh`/`b64`
- [ ] Log capture uses same argv family as today: `bash script.absolutePath ["debian"]` + `ShellCommandRunner` host env
- [ ] Card subtitle may still reflect method (already does); no new isolation control
- [ ] Chroot preflight toast via `ProjectPathResolver.isChrootInstalled()` stays as-is


## 1. Problem

### 1.1 Card has no log surface

| State | Card actions today |
|-------|--------------------|
| Idle | START only |
| Running | STOP only |

No UI log for Pulse/X11/proot/chroot failures.

### 1.2 Output swallowed

```kotlin
ShellCommandRunner.run(...)  // drains stdout, returns exit only
```

Script-install page already used elsewhere; Settings GUI path never feeds it.

---

## 2. Goals

1. **VIEW LOGS** on Graphical Desktop card (under START/STOP).  
2. Opens **existing** script-install terminal chrome (`ID_SCRIPT_INSTALL`) with log content.  
3. **COPY** on that page top bar (shared win for all script runs).  
4. Capture start/stop stdout into buffer/file.  
5. **Zero impact** on main terminal, workspace terminal, marketplace flag semantics during desktop uptime.  
6. Proot + chroot script selection unchanged.  
7. FGS + delayed X11 Activity unchanged.  
8. Default START stays on Settings (no auto-navigate).

### Non-goals

- Fix System Scripts long-running start_gui lock (follow-up).  
- Remove `am force-stop` from start_gui.sh (follow-up).  
- Persistent log across process death beyond cache file (nice-to-have: keep file).

---

## 3. UX / UI

### 3.1 Card

```
┌ GRAPHICAL DESKTOP ─────────────────────┐
│ method subtitle                        │
│ [ START XFCE … ]  or  [ STOP XFCE … ]   │
│ [ VIEW LOGS ]     secondary, MATCH     │
└────────────────────────────────────────┘
```

- `secondaryButton("  VIEW LOGS")`, `topMargin = dp(10)`.  
- Enabled when buffer/file non-empty or stream active.  
- Disabled + toast `"No desktop log yet"` when empty.

### 3.2 Log page

```
[←] Desktop log / title              [COPY]
TerminalView (cat of gui_desktop.log)
```

COPY: `transcriptOf(session)` → `copyToClipboard` → Toast.

---

## 4. Architecture (safe)

### 4.1 `GuiDesktopLog` (small, decoupled)

Prefer private helpers in MainActivity first; extract object if >~60 lines.

```kotlin
// filesDir/cache/gui_desktop.log  OR filesDir/home/.nativecode/gui_desktop.log
object GuiDesktopLog {
    fun logFile(ctx: Context): File
    fun clear()
    fun append(line: String)           // ring-cap e.g. 512 KB
    fun snapshot(): String
    fun header(action: String, script: String, method: String)
}
```

### 4.2 `startGui` / `stopGui` (capture, no script-page lock)

```kotlin
private var guiShellJob: ShellJob? = null

private fun startGui() {
    ensurePostNotificationsPermission()
    startNativeCodeFgs(...)
    // chroot preflight toast (existing)

    guiShellJob?.cancel()
    GuiDesktopLog.header("START", scriptName, method)
    val bash = File(nld, "libbash.so").absolutePath
    val script = File(TermuxHostPaths.HOME, scriptName)
    // Match old argv: debian as $1 for proot DISTRO default parity
    guiShellJob = ShellCommandRunner.runStreamedCancelable(
        this,
        arrayOf(bash, script.absolutePath, "debian"),
        envMap = null, // HostCommandBuilder.applyTo already in runner
        onLine = { line -> GuiDesktopLog.append(line) },
        onDone = { code ->
            GuiDesktopLog.append("[exit $code]")
            enableViewLogsBtn(true)
            if (code != 0) revertCardToStart() // recommended polish
        }
    )
    mainHandler.postDelayed({ start X11 activity }, 500)
}

private fun stopGui() {
    // broadcast + stop FGS (existing)
    guiShellJob?.cancel()
    GuiDesktopLog.header("STOP", stopScript, method)
    guiShellJob = ShellCommandRunner.runStreamedCancelable(
        this,
        arrayOf(bash, stopScript.absolutePath, "debian"),
        onLine = { GuiDesktopLog.append(it) },
        onDone = { … }
    )
}
```

**Do not** set `isScriptRunning` here.

### 4.3 VIEW LOGS → short session on shared page

```kotlin
private fun openGuiLogPage() {
    val f = GuiDesktopLog.logFile(this)
    if (!f.exists() || f.length() == 0L) {
        Toast.makeText(this, "No desktop log yet", Toast.LENGTH_SHORT).show()
        return
    }
    if (isScriptRunning) {
        // Real setup/uninstall in progress — do not steal session
        Toast.makeText(this, "Script runner busy", Toast.LENGTH_SHORT).show()
        return
    }
    // Option 1: temp host wrapper
    // runScriptInTerminal on a tiny helper "show_gui_log.sh" that cats the file
    // Option 2: runScriptInTerminalHostCmd("cat ${f.absolutePath}")
    showLogFileInScriptPage(f, title = "Graphical Desktop Log")
}
```

Implement `showLogFileInScriptPage` as thin variant of `runScriptInTerminal`:

- Builds host args: `bash -c "cat 'path'"` via HostCommandBuilder env  
- Sets title  
- Attaches session  
- `openLogPage = true`  
- **Short** → `isScriptRunning` only until cat ends  

**Do not** replace `runScriptInTerminal` defaults used by uninstall/CLI/scripts.

### 4.4 COPY (shared, safe)

In `buildScriptInstallLayout` topBar add COPY (FILE VIEWER style).

```kotlin
private fun transcriptOf(session: TerminalSession?): String =
    try { session?.emulator?.screen?.transcriptText?.trim().orEmpty() }
    catch (_: Exception) { "" }

private fun copyScriptInstallTranscript() {
    val t = transcriptOf(scriptInstallSession)
    if (t.isEmpty()) { toast nothing; return }
    copyToClipboard("Script log", t)
    toast Copied
}
```

- No change to TerminalViewClient contracts.  
- Main/workspace context menus untouched.  
- Marketplace can adopt later; not required.

### 4.5 Optional `runScriptInTerminal(..., openLogPage: Boolean = true)`

Only needed if other callers want background run. **GUI start must not use it for long jobs.**  
If added: default `true` preserves Scripts / uninstall / CLI.

### 4.6 Card finish polish (recommended)

On start stream `onDone` with `code != 0`: flip STOP→START, hide displayBtn, stop FGS.  
Avoids “STOP shown but desktop never started.”

---

## 5. What we explicitly will NOT change

| Leave alone | Why |
|-------------|-----|
| `initTerminalView` / `viewClient` | Main terminal isolation |
| `workspaceTerminalView` clients | Project terminal isolation |
| Marketplace `startMarketplaceTerminal` | Own TerminalView; only shared flag — we won't hold it |
| `onSessionFinished` hooks for `setup_cli_tools` / uninstall | Script-name gated; GUI cat title different |
| Default `runScriptInTerminal` navigation | Callers unchanged |
| Script asset behavior | No rewrite for v1 |

---

## 6. Implementation steps

1. **COPY** on script-install top bar (additive; test with uninstall log).  
2. **`GuiDesktopLog`** file/buffer + append API.  
3. **Refactor startGui/stopGui** to `runStreamedCancelable` + headers; keep FGS/X11; cancel prior job on stop.  
4. **VIEW LOGS** button + `showLogFileInScriptPage` short cat.  
5. Guard: if `isScriptRunning` (real script), toast busy.  
6. Optional: start failure reverts card.  
7. Manual matrix §0.6 + §7.

---

## 7. Testing

### Functional GUI

| # | Steps | Expect |
|---|-------|--------|
| 1 | START proot | Card STOP; X11; Settings stays |
| 2 | VIEW LOGS | Page shows start lines; short isScriptRunning then false |
| 3 | COPY | Clipboard full text |
| 4 | Back | Settings; XFCE still up |
| 5 | Marketplace install while XFCE up | **Works** (not blocked) |
| 6 | Open main terminal / project term while XFCE | Sessions OK |
| 7 | STOP | Stop lines in log; card→START |
| 8 | Chroot start/stop | Same + chroot script names in header |
| 9 | VIEW LOGS during chroot uninstall | Toast busy or wait — no session stomp |
| 10 | Cold VIEW LOGS | No desktop log yet |

### Non-regression (terminal surfaces)

| # | Surface | Expect |
|---|---------|--------|
| N1 | AI tool terminal tabs | Unchanged create/attach |
| N2 | Workspace special keys / resize | Unchanged |
| N3 | Script setup from System Scripts | Still auto-opens log page |
| N4 | Chroot uninstall finish hooks | Still run on that script name |
| N5 | Font size pinch | All terminals including script page |

---

## 8. Acceptance criteria

1. VIEW LOGS + COPY work for Graphical Desktop.  
2. Start/stop output captured (not void-drained only).  
3. **While XFCE running, `isScriptRunning` is false** (unless user separately ran a real script).  
4. Marketplace + main + workspace terminals not blocked by desktop uptime.  
5. FGS + X11 launch preserved.  
6. No new Activity; reuse script-install chrome for display.  
7. `runScriptInTerminal` default behavior for non-GUI callers unchanged.
8. Isolation SSOT frozen: no edits to Linux/Proot/Chroot builders, nativecode_chroot.sh, or GUI asset behavior; script pick remains linux_method branch only.
9. VIEW LOGS sits **beside STOP** only after healthy start (first stream line); idle card = START only.
10. VIEW LOGS cat uses `/system/bin/sh` (no libbash / libandroid-support dependency).
11. Start fail → toast + open log; cancel from STOP does not flip card early.

---

## 9. Open decisions

| # | Decision | Default |
|---|----------|---------|
| D1 | Live `tail -f` vs static cat | Static cat v1; re-open to refresh |
| D2 | Log file path | `cacheDir/gui_desktop.log` |
| D3 | Toast on every start finish | Suppress “Finished!” for GUI cat only; stream path no script toast |
| D4 | System Scripts start_gui long-lock fix | Follow-up, not this PR |

---

## 10. Summary

**Verification result:** routing Settings START through `runScriptInTerminal` **would break** marketplace and other script-runner users via long-held `isScriptRunning`, and would couple the long XFCE process to the shared script-install session.

**Safe design:** keep GUI process off the script-runner lock (streamed capture + file buffer); use script-install page only for **short** log display + shared **COPY**; leave main/workspace terminals completely alone.

**Isolation:** read `linux_method` only; same 4 host GUI scripts as today; never call guest SSOT builders for start/stop/logs (§0A).

---

## 11. Implementation order

1. COPY button (safe).  
2. GuiDesktopLog + streamed start/stop.  
3. VIEW LOGS → cat session.  
4. Regression matrix §0.6.  
5. Mark plan **implemented**.
