# Chroot Settings: Kill Stale Processes (detect → kill → verify)

**Date:** 2026-07-28  
**Status:** implemented  
**Scope:** On **Chroot Settings** page, add a dedicated **Processes** card that:
1. **Detects** every host process whose root is the Debian chroot (`/proc/PID/root` → `CHROOT_PATH`)
2. Lets user **kill all** of them (root `kill -9`)
3. **Re-scans** and reports verify result (0 left / residual PIDs)

**Out of scope:** unmount binds, uninstall rootfs, auto-kill on app exit, proot process management (proot dies with app), per-PID selective kill UI (v1 = kill all only), mount-list UI.

**Related:**
- [`settings-chroot-card-storage-uninstall.md`](./settings-chroot-card-storage-uninstall.md) — page chrome; this adds a second card on same page
- Uninstall SSOT today: `app/src/main/assets/scripts/chroot/uninstall_debian13_chroot.sh` §1 “Kill Stale Processes”
- Path: `ChrootCommandBuilder.CHROOT_PATH` = `/data/local/tmp/chrootDebian13`
- Root I/O: `RootShell.capture` / `execute` / `isRootAvailable`
- UI: `showBrutalistConfirmDialog`, `glassCard`, `sectionHeader`, `cyberBrutalistBg`, size-panel loading strip pattern

---

## 1. Problem

### 1.1 Why chroot shells outlive the app (proot does not)

| Mode | Process tree | When app process dies |
|------|--------------|------------------------|
| **proot** | App UID, under app’s process / proot namespaces | Guest shells typically die with app / session finish |
| **chroot** | App → `sh` → **`su`** → `busybox chroot` → guest `su`/`zsh` | Guest PIDs are **real host processes** under rootfs root. Killing the TerminalSession often only finishes the **direct** shell PID; nested `su`/chroot children can **orphan** and keep running |

Concrete user report:
- Open many chroot terminals
- Force-close / swipe away app
- Reopen app → UI shows no sessions, but **guest shells still alive** on device
- They hold open files, CPU, ports, and block clean uninstall / remount

Uninstall already knows how to clean them (script §1). Settings has **no** first-class “reap orphans” control without full uninstall.

### 1.2 Detection primitive (proven in uninstall)

From `uninstall_debian13_chroot.sh` (must stay behavioral SSOT):

```sh
DEBIANPATH="/data/local/tmp/chrootDebian13"
for pid_dir in /proc/[0-9]*; do
    if [ -d "$pid_dir" ]; then
        PID=$(basename "$pid_dir")
        ROOT=$(readlink "$pid_dir/root" 2>/dev/null)
        if [ "$ROOT" = "$DEBIANPATH" ]; then
            kill -9 "$PID" 2>/dev/null
        fi
    fi
done
```

Meaning of match:
- `readlink /proc/$PID/root` equals **exactly** `CHROOT_PATH`
- That is the chroot jail root for processes started via `busybox chroot $CHROOT_PATH …`
- Needs **root** to inspect other UIDs’ `/proc/*/root` and to `kill -9` them

**Not** used for v1:
- Matching cwd under rootfs only (false positives possible)
- Parsing `cmdline` for “chroot” (misses long-running daemons without that string)
- Guest-side `ps` (needs mounts; misses host-view orphans)

### 1.3 Why a Settings card (not only onDestroy)

| Approach | Enough? |
|----------|---------|
| `session.finishIfRunning()` in `onDestroy` | No — only app-held session PIDs; orphans already detached |
| Kill on every cold start | Surprising; may kill intentional background work user left in chroot |
| **Explicit card: detect + confirm + kill + verify** | Yes — user-controlled, auditable, reuses uninstall logic |

---

## 2. Goals

1. Chroot Settings page gains a **Processes** card (second card under Storage & Manage).  
2. Show live **count** of chroot-rooted processes (+ optional short sample list).  
3. **SCAN / REFRESH** re-probes without killing.  
4. **KILL ALL** → brutalist confirm → root kill loop → **verify re-scan** → UI status.  
5. **SSOT kill logic** shared with uninstall (no divergent copy-paste forever).  
6. Reuse existing UI primitives; extract **small Kotlin + shell modules** so MainActivity does not grow another 400-line blob of shell strings.  
7. No root / no chroot → clear empty / disabled states (same spirit as storage card).  
8. Do **not** unmount, do **not** delete rootfs, do **not** touch proot.

---

## 3. Non-goals

- Killing processes whose root is **not** exactly `CHROOT_PATH` (e.g. host `su` parent only).  
- Auto-kill when leaving terminal or on app `onDestroy` (optional later).  
- Per-process checkboxes / kill-one.  
- Unmounting after kill (uninstall still does that).  
- Fixing TerminalSession so finish always reaps chroot tree (harder; separate plan).  
- Play-store policy copy beyond short warn that kill is destructive to open shells.

---

## 4. UX / UI

### 4.1 Page layout (after change)

```text
← CHROOT SETTINGS
// ROOT-LEVEL DEBIAN — OUTSIDE APP STORAGE

┌─ Storage & Manage ───────────────────────┐
│  (existing: warn, STATUS, ROOT, size,    │
│   path, INSTALL / UNINSTALL)             │
└──────────────────────────────────────────┘

┌─ Processes ──────────────────────────────┐
│  CHROOT PROCESSES              [SCAN]    │
│  [progress while scanning]               │
│  7  running                              │
│  root=/data/local/tmp/chrootDebian13     │
│                                          │
│  // orphans survive app close (unlike    │
│  // proot). Kill before uninstall if     │
│  // stuck.                               │
│                                          │
│  SAMPLE (optional, up to 5 lines)        │
│  12345  zsh                              │
│  12346  sshd                             │
│  …                                       │
│                                          │
│  [ KILL ALL CHROOT PROCESSES ]           │
│  last: killed 7 · verified 0 remaining   │
└──────────────────────────────────────────┘
```

### 4.2 Placement rules

| Rule | Detail |
|------|--------|
| Where | Same `buildChrootSettingsPage()` scroll content, **after** `buildChrootSettingsContentCard()`, **new** `buildChrootProcessesCard()` |
| Visibility | Show card when root is available **or** when we want education; **prefer always show** card, disable kill when no root |
| When NOT INSTALLED / no root | Count `—` or `0`; kill disabled; hint “Root required” / “No chroot rootfs” |
| Destructive CTA | `NC.ERROR` outline style (same family as UNINSTALL) |
| Confirm | `showBrutalistConfirmDialog` (already reusable) — do **not** Material AlertDialog |

### 4.3 States

| State | Badge / number | Kill button | Hint |
|-------|----------------|-------------|------|
| Loading | … | disabled | SCANNING |
| Root denied | — | disabled | Root required |
| Dir missing | 0 | disabled | No chroot rootfs |
| Clean (0 procs) | **0** | disabled (or enabled no-op) | No chroot processes |
| Dirty (N>0) | **N** | enabled | `N process(es) use chroot root` |
| Kill in progress | N (stale ok) | disabled + spinner | Killing… |
| Verified clean | 0 | disabled | Killed K · verified 0 remaining |
| Residual after kill | R>0 | enabled | Killed K · **R still alive** — retry |

### 4.4 Copy

| Element | Text |
|---------|------|
| Card title | `Processes` |
| Metric | `CHROOT PROCESSES` |
| CTA | `KILL ALL CHROOT PROCESSES` |
| Confirm title | `KILL CHROOT PROCESSES?` |
| Confirm body | `Sends SIGKILL to every process whose root is CHROOT_PATH.\n\nOpen chroot shells and guest daemons will die. Host Android processes are not targeted.\n\nRootfs and mounts stay (use Uninstall to remove).` |
| Confirm OK | `KILL ALL` |

---

## 5. Architecture (decouple — mandatory)

Goal: **one kill/list algorithm**, three consumers (Settings UI, uninstall script, future onDestroy optional).

```text
                    ┌─────────────────────────────┐
                    │ chroot_processes.sh (asset) │  SSOT shell
                    │  list | kill | list+kill    │
                    └────────────┬────────────────┘
                                 │ staged + RootShell
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
   ChrootProcessManager   uninstall_debian13_     (future hooks)
   .kt (parse + API)      chroot.sh sources /
                          or exec same script
              │
              ▼
   MainActivity Processes card (thin UI)
```

### 5.1 Shell SSOT — new asset

**Path:** `app/src/main/assets/scripts/chroot/chroot_processes.sh`

**Contract (CLI):**

```text
chroot_processes.sh list   [CHROOT_PATH]
chroot_processes.sh kill   [CHROOT_PATH]
chroot_processes.sh reap   [CHROOT_PATH]   # kill then list (verify in one root shell)
```

Default `CHROOT_PATH=/data/local/tmp/chrootDebian13` if omitted.

**`list` stdout format (machine-parseable, one PID per line):**

```text
# chroot_processes v1
# path=/data/local/tmp/chrootDebian13
PID\tCOMM\tCMDLINE_PREFIX
12345\tzsh\t-zsh
12346\tsshd\t/usr/sbin/sshd
# count=2
```

Rules:
- Only PIDs where `readlink /proc/$pid/root` **equals** path (string exact).
- `COMM` from `/proc/$pid/comm` (truncate whitespace).
- `CMDLINE_PREFIX` from `/proc/$pid/cmdline` (tr `\0`→space, max ~80 chars); empty if unreadable.
- Footer `# count=N` always; exit 0 even if count=0.
- Must run as root (same check as uninstall).

**`kill` behavior:**
- Same scan loop as uninstall §1.
- For each match: `kill -9 "$PID"`; count attempted / failed.
- Stdout summary lines:
  ```text
  # killed=7 failed=0
  ```
- Does **not** unmount. Does **not** `rm`.
- After kill, optional short sleep `sleep 0.2` then nothing else (verify is caller’s job or `reap`).

**`reap` behavior:**
1. `kill` pass  
2. `sleep 0.3` (let zombies reparent / die)  
3. `list` pass (for verify)  
4. Exit `0` if final count=0, else exit `2` (residual) — UI can map codes.

**Implementation notes for shell:**
- Prefer pure `/system/bin/sh` + toybox/busybox; no bashisms.
- Skip self: if `$PID` equals `$$` of the script (unlikely root match) ignore.
- Do **not** kill PID 1.
- Order: collect PIDs first into a list, then kill — avoids `/proc` walk races mid-kill.
- Second pass kill optional inside `kill` for stubborn children that reappear from same parent (max 2 passes).

### 5.2 Uninstall script refactor (use SSOT)

**File:** `uninstall_debian13_chroot.sh`

Replace inline §1 loop with:

```sh
# Prefer shared helper if staged next to us or on PATH
HELPER="$(dirname "$0")/chroot_processes.sh"
if [ -f "$HELPER" ]; then
    progress "Killing chroot processes via chroot_processes.sh..."
    sh "$HELPER" kill "$DEBIANPATH" || true
else
    # Fallback: keep current inline loop for standalone adb runs
    ...
fi
```

When app stages assets for uninstall (`RootShell.executeScriptAsset` / terminal runner), stage **both** scripts into same dir (`/data/local/tmp/nativecode_scripts/` or current staging dir) so `dirname` resolves.

**Fallback:** keep duplicate loop if helper missing — uninstall must not break offline manual runs.

### 5.3 Kotlin API — new small object (decouple from MainActivity)

**New file:** `app/src/main/java/com/ivarna/nativecode/terminal/ChrootProcessManager.kt`

```kotlin
object ChrootProcessManager {
    data class Proc(val pid: Int, val comm: String, val cmdline: String)
    data class ListResult(val path: String, val processes: List<Proc>, val raw: String)
    data class KillResult(
        val killed: Int,
        val failed: Int,
        val remaining: List<Proc>,
        val verifiedClean: Boolean,
        val raw: String
    )

    fun list(path: String = ChrootCommandBuilder.CHROOT_PATH): ListResult
    fun killAll(path: String = ChrootCommandBuilder.CHROOT_PATH): KillResult  // uses reap
}
```

**Internals:**
- Prefer stage + run asset `scripts/chroot/chroot_processes.sh` via existing `RootShell` staging (`executeScriptAsset` path or shared stage helper).
- **Alternative (lighter):** embed minimal list/kill as `RootShell.capture` one-liners that **call the same script** after stage — never reimplement PID logic in Kotlin.
- Parse `# count=`, PID lines, `# killed=`.
- **Thread rule:** all methods blocking; call only from `executor` BG thread (document).
- On no root: return empty list / `verifiedClean=false` with raw error; do not throw.

**Optional RootShell helpers (only if staging is awkward today):**
- Expose `RootShell.stageAsset(context, name): String?` as `internal`/`public` if currently private — reuse for both uninstall and process manager.
- Do **not** add a second su discovery path.

### 5.4 UI layer — MainActivity thin; reuse components

| Reuse (existing) | Role |
|------------------|------|
| `glassCard()` | Card chrome |
| `sectionHeader(icon, title, color)` | “Processes” header |
| `cyberBrutalistBg(...)` + press touch | SCAN / KILL buttons |
| `showBrutalistConfirmDialog(...)` | Kill confirm |
| Loading strip pattern (ProgressBar + SCANNING) | Same as chroot size / proot size |
| Meta row / badge style from chroot status | Optional status chip CLEAN / DIRTY |
| `RootShell.isRootAvailable()` | Gate |
| `ChrootCommandBuilder.CHROOT_PATH` | Path label |
| `mainHandler` + `executor` | Async |

| New UI builders (keep local private funs unless 2nd consumer appears) | |
|---------------------------------------------------------------------|--|
| `buildChrootProcessesCard(): LinearLayout` | Card body |
| `refreshChrootProcessesCard(force)` | Scan |
| `confirmAndKillChrootProcesses()` | Confirm + kill |
| `applyChrootProcessUi(list/kill result)` | Bind views |

**Do not** invent a new dialog system.  
**Do not** run kill through `runScriptInTerminal` full-screen terminal for v1 — silent root + UI status is enough (faster, stays on Settings). Optional “show log” expand later.

### 5.5 Prefs (optional light cache)

| Key | Type | Meaning |
|-----|------|---------|
| `chroot_proc_count` | Int | Last count (−1 unknown) |
| `chroot_proc_last_ms` | Long | Last scan time |

On page open: paint cached count dimmed, then auto-scan (same pattern as size).  
No need to cache full PID list.

---

## 6. Detection / kill algorithm (normative)

### 6.1 List

```
require root
path = CHROOT_PATH
pids = []
for each /proc/[0-9]*:
  if not dir: continue
  pid = basename
  if pid == 1: continue
  root = readlink(/proc/pid/root) or continue
  if root == path:
    pids += {pid, comm, cmdline}
emit sorted by pid
```

### 6.2 Kill

```
require root
snapshot = list()
for each pid in snapshot (descending optional):
  kill -9 pid
sleep 0.3
remaining = list()
return {killed=|snapshot|-ish, remaining, verifiedClean = remaining.empty}
```

**Two-pass (recommended in script):**
1. Collect all matching PIDs  
2. Kill all  
3. Sleep  
4. Collect again; kill residuals once  
5. Final list for verify  

### 6.3 What gets killed / what does not

| Killed | Not killed |
|--------|------------|
| Guest `zsh`, `bash`, `sshd`, `apt`, GUI helpers **with root=`CHROOT_PATH`** | Host Zygote, system_server |
| Nested `su` **inside** chroot if root still CHROOT_PATH | Host-side `su` parent if its `/proc/root` is still `/` |
| Any daemon started inside chroot | Proot guest under app filesDir |

If a host wrapper still shows root `/` but children are chroot-rooted, **children die**; wrapper may exit on its own when child pipe closes. Good enough for v1.

### 6.4 Relation to open TerminalSessions in app

After kill:
- In-app `TerminalSession` for chroot may fire `onSessionFinished` when PTY peer dies — existing session cleanup should run.
- Plan: **no extra** session-list coupling required for v1; if sessions linger in UI as “running” after kill, follow-up: listen and prune. Note as acceptance edge-case to retest.

---

## 7. Implementation plan (ordered)

### P0 — Shared shell asset

1. Add `chroot_processes.sh` with `list|kill|reap`.  
2. Manual adb smoke: root `sh chroot_processes.sh list` with open chroot shell → count ≥ 1.  
3. `reap` → count 0.

### P1 — Uninstall uses helper

1. Stage helper beside uninstall when app runs uninstall.  
2. Refactor uninstall §1 to call helper with inline fallback.  
3. Confirm full uninstall still works (kill + umount + rm).

### P2 — `ChrootProcessManager.kt`

1. Stage asset via RootShell (reuse/private stage if needed).  
2. `list()` / `killAll()` parse API.  
3. Unit-free; log raw on failure.

### P3 — Chroot Settings UI card

1. `buildChrootProcessesCard()` after storage card.  
2. Wire views: count, unit/label, hint, sample list container, SCAN, KILL, loading.  
3. Page enter + SCAN → `list()`.  
4. KILL → `showBrutalistConfirmDialog` → BG `killAll()` → apply verify UI.  
5. Disable paths when no root / measuring / count==0.

### P4 — Polish / prefs / edge

1. Cache last count.  
2. Toast or inline error if root lost mid-op.  
3. Cap sample list to 5 rows + “+N more”.  
4. Ensure kill does not navigate away from settings.

### P5 — Device acceptance + mark plan implemented

---

## 8. File touch list

| File | Change |
|------|--------|
| **NEW** `assets/scripts/chroot/chroot_processes.sh` | SSOT list/kill/reap |
| `assets/scripts/chroot/uninstall_debian13_chroot.sh` | Call helper; keep fallback |
| **NEW** `terminal/ChrootProcessManager.kt` | Kotlin API over script |
| `RootShellService.kt` | Only if need public `stageAsset` / stage-dir constant |
| `MainActivity.kt` | Processes card + thin glue; **no** raw PID loops |
| `docs/plan/chroot-settings-kill-processes.md` | This plan → implemented later |

**No** Onboarding, **no** proot settings, **no** ChrootCommandBuilder mount changes.

---

## 9. Reusable component inventory (do / don’t invent)

### Must reuse

| Component | Location | Use for |
|-----------|----------|---------|
| `showBrutalistConfirmDialog` | MainActivity | Kill confirm |
| `glassCard` / `sectionHeader` | MainActivity | Card chrome |
| `cyberBrutalistBg` + ACTION_DOWN press | MainActivity | Buttons |
| `RootShell.capture` / `execute` / `isRootAvailable` | RootShell | Root I/O |
| `ChrootCommandBuilder.CHROOT_PATH` | terminal | Path SSOT |
| Loading ProgressBar strip | chroot/proot size panels | SCAN/KILL progress |
| Destructive button styling | UNINSTALL CTA | KILL ALL CTA |

### Must create (small, focused)

| Component | Why new |
|-----------|---------|
| `chroot_processes.sh` | Shell SSOT; uninstall + UI |
| `ChrootProcessManager` | Parse + API boundary; keeps MainActivity thin |

### Explicitly do **not** create

| Avoid | Why |
|-------|-----|
| Full “ProcessManagerActivity” | Overkill for one card |
| New Material dialog theme | Design system forbids |
| Kotlin reimplementation of `/proc` walk | Diverges from uninstall; root file access from app UID is incomplete |
| Separate Settings hub row | Card lives **inside** Chroot Settings |

---

## 10. MainActivity integration sketch (thin)

```kotlin
// buildChrootSettingsPage:
pageLayout.addView(buildChrootSettingsContentCard())
pageLayout.addView(spacer(16))
pageLayout.addView(buildChrootProcessesCard())

// on ID_CHROOT_SETTINGS navigate:
refreshChrootSettingsCard(force = false)
refreshChrootProcessesCard(force = false)

// kill:
showBrutalistConfirmDialog(..., destructive = true) {
  setChrootProcLoading(true)
  executor.execute {
    val result = ChrootProcessManager.killAll()
    mainHandler.post {
      setChrootProcLoading(false)
      applyChrootProcessUi(result)
    }
  }
}
```

View refs (mirror size panel naming):
`chrootProcCountTv`, `chrootProcHintTv`, `chrootProcSampleBox`, `chrootProcScanBtn`, `chrootProcKillBtn`, `chrootProcLoadingRow`, `chrootProcMeasuring`.

---

## 11. Acceptance criteria

- [ ] Chroot Settings shows **Processes** card below Storage & Manage.  
- [ ] With open chroot shells (app can be killed/reopened): SCAN shows count ≥ number of guest roots.  
- [ ] Count matches manual:  
  `su -c 'for p in /proc/[0-9]*; do [ "$(readlink $p/root)" = "/data/local/tmp/chrootDebian13" ] && basename $p; done | wc -l'`  
- [ ] KILL ALL confirms → count goes to **0**; verify message shows remaining 0.  
- [ ] Residual path: if something respawns, UI shows remaining > 0 and Kill stays enabled.  
- [ ] No root → kill disabled; no crash.  
- [ ] Uninstall still kills processes (via helper or fallback) then unmounts/removes.  
- [ ] Proot sessions unaffected.  
- [ ] Host system processes unaffected (spot-check: system_server still up).  
- [ ] No unmount/delete from this card.  
- [ ] Shell logic not duplicated: uninstall + Settings share `chroot_processes.sh`.

---

## 12. Test plan

1. Device with root + chroot installed.  
2. Open 3+ chroot terminal sessions; leave a `sleep 3600` in one.  
3. Force-stop app from system settings.  
4. Reopen → Settings → Chroot Settings → Processes → SCAN → count ≥ 1 (orphans).  
5. KILL ALL → confirm → 0 remaining.  
6. Open sessions again → kill while app still holding sessions → sessions should die / finish.  
7. SCAN when clean → 0; Kill disabled or no-op.  
8. Deny root to app → card shows root required.  
9. Run uninstall from card → still succeeds end-to-end.  
10. Optional: start proot shell → chroot process count does **not** include it.

---

## 13. Risks / mitigations

| Risk | Mitigation |
|------|------------|
| Killing mid-apt corrupts dpkg | Confirm copy warns; user intentional |
| Race: new PID after list before kill | `reap` two-pass + verify list |
| `readlink` root denied without su | Require root; disable CTA |
| Helper not staged next to uninstall | Fallback inline loop; app stages both |
| Over-broad kill | Exact root path match only; never kill by name alone |
| Script output parse brittle | Stable `# count=` / `# killed=` tokens; version header |
| MainActivity bloat | Logic in `ChrootProcessManager` + shell only |
| User confuses with Uninstall | Card copy: “rootfs and mounts stay” |
| Self-kill of RootShell child | Script not chrooted; root=`/` — safe |

---

## 14. Comparison: uninstall vs this card

| | Uninstall §1–6 | Processes card |
|--|----------------|----------------|
| Kill chroot-rooted PIDs | Yes | Yes (same script) |
| Unmount under rootfs | Yes | **No** |
| `rm -rf` rootfs | Yes | **No** |
| Remove host launchers | Yes | **No** |
| UI surface | Script terminal | Settings card + verify |
| Confirm | Uninstall dialog | Kill dialog |

---

## 15. Future (not this plan)

- Auto-reap on MainActivity `onDestroy` when isolation=chroot (pref toggle).  
- Session finish reaps full process group from TerminalSession (upstream-hard).  
- Per-PID list with kill-one.  
- Mount status card (busy mounts after kill).

---

## 16. Order of work summary

1. P0 shell asset + adb verify  
2. P1 uninstall refactor  
3. P2 ChrootProcessManager  
4. P3 Settings card UI  
5. P4 prefs/polish  
6. P5 device acceptance → mark **implemented**

---

## Stop line

**Implemented** (2026-07-28): `chroot_processes.sh` + uninstall helper call + `ChrootProcessManager` + Processes card on Chroot Settings. Device acceptance still recommended (plan §12).
