# Terminal: Debian Shell + Debian Shell Rooted (proot + chroot)

**Date:** 2026-07-30  
**Scope:** Terminal page UI + session launch user (`flux` vs `root`)  
**Related:** `docs/plan/proot-cli-zsh-and-chroot-hide-codex.md`, `ChrootCommandBuilder.kt`, `ProotCommandBuilder.kt`, `LinuxCommandBuilder.kt`  
**UI ref:** Terminal tool selector — section `DEBIAN SHELL // SYSTEM SHELL` (Image #1)

---

## Summary

| Item | Decision |
|------|----------|
| Current shell card | Keep — **Debian Shell** → guest user **`flux`** |
| New card | **Debian Shell Rooted** → guest user **`root`** |
| Methods | Both **`proot`** and **`chroot`** (same cards; method from `linux_method` / `LinuxCommandBuilder.currentMethod`) |
| AI tool cards | Unchanged (still run as `flux` via `launch_tool.sh`) |
| Workspace hub | Same pair of shell cards (parity with Terminal page) |

**Goal:** User can open a normal Debian login as `flux` **or** an interactive Debian login as `root`, on either isolation method, from the Terminal page (and matching workspace “new terminal” entry points).

---

## Problem

1. Terminal page `DEBIAN SHELL` has a **single** card: type `"shell"` → always guest **`flux`**.
2. Launch path never selects root for interactive sessions:
   - `createNewTerminalSession(type)` → `buildToolShellCommand` → `LinuxCommandBuilder.build(..., user = "flux")` (default).
3. Root is already supported **for guest scripts** (`user = "root"` on scripts page / onboarding) but **not exposed** as a Terminal card.
4. Users need root shell for `apt`, system edits, chroot maintenance, without typing `sudo` as flux (and for proot “root” identity for package ops).

---

## Current behavior (as-is)

### UI — Terminal selector (`MainActivity.buildTerminalToolSelectorView`)

```text
DEBIAN SHELL          // SYSTEM SHELL
┌─────────────────┐
│  shell.png      │
│  Debian Shell   │
│  Debian Shell   │
└─────────────────┘

FREE CLI TOOLS …
PAID CLI TOOLS …
```

```kotlin
// ~1909
val shellTools = listOf(
    TermToolDef("shell", "Debian Shell", "Debian Shell")
)
```

### Launch chain

| Step | Code | Behavior |
|------|------|----------|
| Tap card | `createNewTerminalSession(tool.type)` | `type = "shell"` |
| Guest cmd | `ChrootCommandBuilder.buildToolShellCommand(ctx, "shell", workDir=null)` | `"exec zsh"` (interactive sentinel) |
| Build argv | `LinuxCommandBuilder.build(ctx, shellCmd)` | **`user` default `"flux"`** |
| Proot | `ProotCommandBuilder.build(..., user)` | `proot-distro login debian --shared-tmp --user flux` |
| Chroot | `ChrootCommandBuilder.build(..., user)` | mounts + `busybox chroot … /bin/su - flux` |

### Interactive detection (both builders)

`shellCmd` is interactive when:

```text
shellCmd == "exec zsh" || shellCmd == "/bin/bash --login" || shellCmd.isBlank()
```

Interactive → full login shell as `$user` (no extra `-c` payload).

### Root already works for non-UI paths

| Path | How root is used |
|------|------------------|
| Scripts page guest run (proot) | `LinuxCommandBuilder.build(..., user = "root")` |
| Scripts page guest run (chroot) | `su - root -c …` / `run_debian13_root.sh` |
| Onboarding chroot provision | `user = "root"` in setup runners |
| Chroot non-interactive + root | Prefer `/data/local/tmp/run_debian13_root.sh` if present |

**Gap:** no Terminal card passes `user = "root"` for interactive login.

### Env note (chroot)

`ChrootCommandBuilder.build` sets outer env:

```kotlin
envMap["HOME"] = "/home/flux"  // hardcoded
```

Guest login shell from `su - $user` sets correct guest `HOME` (`/home/flux` or `/root`). Outer `HOME` is mostly host-side; still fix for consistency when `user == "root"` → `/root` (and avoid confusion if anything reads outer env).

---

## Desired UX

### Terminal page — DEBIAN SHELL section

Two cards side-by-side (same 2-column grid as CLI tools):

```text
DEBIAN SHELL                    // SYSTEM SHELL
┌──────────────────┐  ┌──────────────────┐
│  shell.png       │  │  shell.png (root │
│  Debian Shell    │  │   tint / badge)  │
│  User: flux      │  │  Debian Shell    │
│                  │  │  Rooted          │
│                  │  │  User: root      │
└──────────────────┘  └──────────────────┘
```

| Field | Normal | Rooted |
|-------|--------|--------|
| `type` | `"shell"` | `"shell-root"` |
| Label | `Debian Shell` | `Debian Shell Rooted` (or `Debian Shell` + desc `Rooted`) |
| Desc | `User: flux` (or keep `Debian Shell`) | `User: root` / `Rooted` |
| Guest user | `flux` | `root` |
| HOME (guest) | `/home/flux` | `/root` |

**Show for both methods.** No hide on proot or chroot (unlike Codex-in-chroot).

### Semantics of “rooted”

| Method | What “root” means | Notes |
|--------|-------------------|--------|
| **chroot** | Real guest uid 0 via host `su` + `chroot` + `su - root` | Needs working KernelSU/Magisk (same as chroot method already). Fail → toast + optional `RootShellService` check. |
| **proot** | proot-distro **fake root** (`--user root`) | No device root required. Useful for apt/as-root guest ops under proot. Not host Android root. |

Card subtitle should not claim “KernelSU” for proot; use **User: root** only.

### Sidebar / tabs

When session type is `shell-root`:

- Sidebar label: **`Debian Shell Rooted`** (not generic “Debian Shell”).
- Tab/dropdown entries same string.

### Workspace hub (parity)

`buildWorkspaceAiToolsHub` / `WorkspaceAiToolDef` currently has one `"shell"` entry at end of AI tools. Add **`shell-root`** next to it (or small “SYSTEM SHELL” row). Same launch rules as Terminal.

`showNewTerminalDropdown` (workspace `+`): add **Debian Shell Rooted** next to Debian Shell.

---

## Design decisions

### D1 — New type string, not a flag on `"shell"`

Use type **`"shell-root"`** (kebab, matches `claude-code` / `qwen-code`).

| Why |
|-----|
| `terminalSessionTypes` / workspace tab names already store `type` strings |
| Sidebar `when (type)` already maps types → labels |
| Filters (codex) stay independent |
| No prefs migration |

### D2 — Pass `user` through launch helpers (minimal API change)

Do **not** overload `buildToolShellCommand` return value with user.

Recommended:

```kotlin
fun sessionUserForType(type: String): String =
    if (type == "shell-root") "root" else "flux"

// createNewTerminalSession / createWorkspaceTerminalTab:
val user = sessionUserForType(type)
val shellCmd = ChrootCommandBuilder.buildToolShellCommand(this, type, workDir)
val (args, envMap) = LinuxCommandBuilder.build(this, shellCmd, user = user)
```

For `shell-root`, treat like `shell` in `buildToolShellCommand` (interactive `exec zsh`), but **default workDir**:

| type | workDir null default |
|------|----------------------|
| `shell` | `/home/flux` (today) |
| `shell-root` | `/root` |

```kotlin
// buildToolShellCommand
if (type == "shell" || type == "shell-root") {
    val home = if (type == "shell-root") "/root" else "/home/flux"
    val dir = workDir?.takeIf { it.isNotBlank() } ?: home
    return if (workDir.isNullOrBlank()) {
        "exec zsh"  // login path uses su -/proot --user; HOME from login
    } else {
        "mkdir -p $dir && cd $dir && exec zsh"
    }
}
```

Interactive with blank workDir stays **`"exec zsh"`** so builders take the **login** branch (`su - $user` / `proot-distro login --user $user`) — correct profile/PATH for root vs flux.

### D3 — Icon

| Option | Choice |
|--------|--------|
| A — Reuse `assets/images/cli/shell.png` for both | **Default** (zero new art) |
| B — Same asset + red/error color filter on rooted card | **Recommended** for visual distinction |
| C — New `shell-root.png` | Optional later |

Asset load path today: `"${tool.type}.png"` → for `shell-root` **must fallback** to `shell.png` (file does not exist).

```kotlin
val filename = when (tool.type) {
    "qwen-code" -> "qwen-code.webp"
    "shell-root" -> "shell.png"
    else -> "${tool.type}.png"
}
// optional: if type == shell-root) iconView.setColorFilter(NC.ERROR or amber)
```

Same fix in: terminal cards, sidebar list, workspace hub, dropdown icons.

### D4 — Root availability gate (chroot only)

| Method | On tap `shell-root` |
|--------|---------------------|
| proot | Always allow (fake root) |
| chroot | If `RootShellService` / `su` unavailable → Toast: `Root required for rooted chroot shell` and return |

Optional soft warning once: rooted shell can break guest packages if misused — skip for v1 (power users only).

### D5 — AI tools stay flux

`opencode`, `claude-code`, etc. keep `user = "flux"` and `launch_tool.sh`. Rooted is **shell-only** product surface.

### D6 — No onboarding / rootfs changes for v1

- Guest already has `root` account (Debian default).
- `flux` user created by `setup_debian_family.sh`.
- Root HOME `/root` exists in rootfs.
- No new setup scripts required.

---

## Implementation plan

### Phase 1 — Command builders (backend)

#### 1.1 `ChrootCommandBuilder.kt`

| Change | Detail |
|--------|--------|
| `buildToolShellCommand` | Treat `shell-root` like interactive shell; default dir `/root` when workDir set for project cwd edge cases |
| `build(..., user)` | Set `envMap["HOME"]` = `if (user == "root") "/root" else "/home/flux"` |
| Interactive root | Existing: `… chroot … /bin/su - $user` already correct when `user=root` |
| Non-interactive root | Existing `run_debian13_root.sh` branch OK (scripts path) |

**Verify** interactive root command shape:

```text
/system/bin/su -c "<mounts>; exec busybox chroot $CHROOT_PATH /bin/su - root"
```

Login shell as root → uid 0, HOME=/root, root’s shell from passwd (bash/zsh).

If root’s login shell is bash but flux uses zsh: acceptable. Optional later: force `zsh -l` for both; **out of scope** unless broken on device.

#### 1.2 `ProotCommandBuilder.kt`

| Change | Detail |
|--------|--------|
| None required if `user` already interpolated | Already: `--user $user` |
| Confirm | `proot-distro login debian --shared-tmp --user root` works on device (smoke) |

Interactive:

```text
exec python $prootDistro login debian --shared-tmp --user root
```

#### 1.3 `LinuxCommandBuilder.kt`

No API change. Call sites must pass `user`. Document default remains `flux`.

Optional helper (either in `LinuxCommandBuilder` or `MainActivity`):

```kotlin
fun sessionUserForType(type: String): String =
    when (type) {
        "shell-root" -> "root"
        else -> "flux"
    }
```

Prefer **single SSOT** in `LinuxCommandBuilder` or small `TerminalSessionTypes` object so UI + launch cannot drift.

---

### Phase 2 — Terminal page UI (`MainActivity.kt`)

#### 2.1 Shell tools list

```kotlin
val shellTools = listOf(
    TermToolDef("shell", "Debian Shell", "User: flux"),
    TermToolDef("shell-root", "Debian Shell Rooted", "User: root"),
)
// still: addSection("DEBIAN SHELL", "// SYSTEM SHELL", shellTools)
```

Grid already `chunked(2)` → one row, two cards (matches Image #1 layout language).

#### 2.2 Icon filename + optional root tint

In `makeToolCard` (terminal selector): map `shell-root` → `shell.png`; apply distinct color filter or small “ROOT” badge TextView under desc.

#### 2.3 `createNewTerminalSession`

```kotlin
private fun createNewTerminalSession(type: String = "shell") {
    // existing codex/chroot guard…
    // optional: shell-root + chroot + !rootAvailable → toast return

    val shellCmd = ChrootCommandBuilder.buildToolShellCommand(this, type, workDir = null)
    val user = LinuxCommandBuilder.sessionUserForType(type) // or local helper
    val (args, envMap) = LinuxCommandBuilder.build(this, shellCmd, user = user)
    // rest unchanged; terminalSessionTypes.add(type)
}
```

#### 2.4 Sidebar labels (`updateSidebarTerminalsList`)

```kotlin
val toolLabel = when (type) {
    "opencode" -> "opencode"
    // …
    "shell-root" -> "Debian Shell Rooted"
    else -> "Debian Shell"
}
```

Icon load: same `shell-root` → `shell.png` fallback.

#### 2.5 `refreshToolCardsForMethod`

No filter for `shell-root` (always show). Rebuild already covers method switch; both cards remain.

---

### Phase 3 — Workspace + dropdown (parity)

| Surface | Change |
|---------|--------|
| `WorkspaceAiToolDef` list | Add `WorkspaceAiToolDef("shell-root", "Debian Shell Rooted", "User: root")` next to shell |
| `createWorkspaceTerminalTab` | Same `user = sessionUserForType(type)`; chroot root gate |
| `showNewTerminalDropdown` | Add `Pair("Debian Shell Rooted", "shell-root")` after Debian Shell |
| Project workDir + shell-root | If opening from project hub with `activeProjectPath`, decide: **still login as root at `/root`** for pure shell-root, **or** `cd` into project as root. **v1 recommendation:** interactive shell-root ignores project cwd (login at `/root`); tools keep project cwd as flux. Document in done criteria. |

**Rationale for v1:** Root shell is for system admin, not project coding. Avoid writing root-owned files into `/home/flux/repos/…`.

If product wants project-cwd root later:

```text
mkdir -p $activeProjectPath && cd $activeProjectPath && exec zsh
+ user=root
```

— non-interactive branch in builders (not bare login). Defer.

---

### Phase 4 — Guards & polish

| Item | Behavior |
|------|----------|
| Max tabs | Existing 10-tab limit applies to both types |
| Codex guard | Unrelated; leave as-is |
| Heavy tool toast | Do not toast for shell-root (not heavy AI) |
| Empty selector | After close last session, both shell cards visible |
| Settings method switch | Both cards remain; next launch uses new method + correct user |

---

## Files to touch

| File | Change |
|------|--------|
| `app/.../terminal/LinuxCommandBuilder.kt` | Optional `sessionUserForType()` helper |
| `app/.../terminal/ChrootCommandBuilder.kt` | `shell-root` in `buildToolShellCommand`; HOME by user |
| `app/.../terminal/ProotCommandBuilder.kt` | Smoke-only; no code if user already works |
| `app/.../MainActivity.kt` | Cards, icons, create session/tab, sidebar, dropdown, root gate |
| Assets | None required (reuse `shell.png`) |
| Guest scripts / onboarding | None |

---

## Call-site matrix (must all pass `user`)

| Call site | Today | After |
|-----------|-------|-------|
| `createNewTerminalSession` | `build(..., default flux)` | `user = sessionUserForType(type)` |
| `createWorkspaceTerminalTab` | same | same |
| Scripts page proot guest | already `user = "root"` when needed | unchanged |
| AI tools via shellCmd only | flux default | still flux (`type != shell-root`) |

Grep after impl:

```text
LinuxCommandBuilder.build(
createNewTerminalSession
createWorkspaceTerminalTab
buildToolShellCommand
"shell-root"
```

Ensure no interactive shell launch remains that ignores type for user.

---

## Implementation order

1. **Helper + builders** — `sessionUserForType`, `buildToolShellCommand` shell-root, chroot HOME  
2. **`createNewTerminalSession` + sidebar** — wired launch + labels  
3. **Terminal selector UI** — second card + icon fallback  
4. **Workspace hub + dropdown** — parity  
5. **Chroot root gate** — toast if no su  
6. **Manual test matrix**  

---

## Test matrix

### Proot (`linux_method=proot`)

| # | Action | Expect |
|---|--------|--------|
| P1 | Terminal → Debian Shell | `id -un` → `flux`; `pwd` → `/home/flux` (or flux home) |
| P2 | Terminal → Debian Shell Rooted | `id -un` → `root`; `pwd` → `/root` |
| P3 | Rooted: `apt-get update` (or `whoami`) | works under proot fake root |
| P4 | Free/Paid AI cards | still flux; no regression |
| P5 | Sidebar shows correct labels for both sessions | flux vs Rooted |
| P6 | Close sessions → selector shows **two** DEBIAN SHELL cards | |

### Chroot (`linux_method=chroot`, device rooted)

| # | Action | Expect |
|---|--------|--------|
| C1 | Debian Shell | flux login works (regression) |
| C2 | Debian Shell Rooted | `id -un` → `root`; real uid 0 |
| C3 | Rooted: write under `/etc` (touch file) | succeeds (then remove) |
| C4 | No su / root denied | Toast; no dead session |
| C5 | Codex still hidden in chroot | regression of prior plan |
| C6 | Mounts still applied for root login | `/proc`, `/dev`, etc. OK |

### Workspace

| # | Action | Expect |
|---|--------|--------|
| W1 | Hub / dropdown → both shells | same uid checks as P1/P2 or C1/C2 |
| W2 | Project open + shell-root | lands as root at `/root` (v1), not project dir |

### Method switch

| # | Action | Expect |
|---|--------|--------|
| M1 | proot shell-root open → switch settings to chroot → new shell-root | new session uses chroot root path |
| M2 | Cards always both visible on Terminal page | |

### ADB smoke (optional)

```sh
# proot root interactive (host app uid)
# expect: id = root
… proot-distro login debian --shared-tmp --user root -- id -un

# chroot root
su -c 'busybox chroot /data/local/tmp/chrootDebian13 /bin/su - root -c "id -un"'
# expect: root
```

---

## Risks

| Risk | Mitigation |
|------|------------|
| proot `--user root` missing / wrong passwd | Smoke P2; fallback document `proot-distro login … -0` only if needed |
| Root interactive shell is bash not zsh | Accept v1; optional force zsh later |
| Users create root-owned files in flux home | v1: shell-root does not auto-cd to project |
| Chroot without root still shows card | Gate on tap + toast; card stays (discoverability) |
| `shell-root.png` 404 | Explicit map to `shell.png` |
| Sidebar `else -> Debian Shell` swallows unknown | Explicit `"shell-root"` branch |
| Outer env HOME=/home/flux breaks something for root | Set HOME by user in chroot builder |
| SELinux / su path differences | Reuse exact chroot interactive path as flux (only `$user` changes) |

---

## Out of scope

- Rooted launch for AI CLI tools  
- Changing default shell user for all sessions  
- KernelSU install UX  
- New rootfs user provisioning  
- proot-fast launcher wiring  
- Persistent “always open as root” setting  

---

## Done definition

- [x] Terminal `DEBIAN SHELL` shows **two** cards: normal + rooted  
- [x] Normal → guest **`flux`** on **proot and chroot**  
- [x] Rooted → guest **`root`** on **proot and chroot**  
- [x] Sidebar / dropdown / workspace list labels correct for `shell-root`  
- [x] Icons load (fallback to `shell.png`)  
- [x] AI tool cards unchanged (still flux)  
- [x] Chroot without su: rooted card fails gracefully (toast)  
- [x] No regression: Codex hide-in-chroot, launch_tool tools, flux shell login  
- [x] Plan file: `docs/plan/terminal-debian-shell-rooted.md`  

### Implementation notes (2026-07-30)

| Item | Change |
|------|--------|
| User SSOT | `LinuxCommandBuilder.sessionUserForType` |
| Interactive root | `build(..., user=root)` → proot `--user root` / chroot `su - root` |
| UI | Terminal + workspace + dropdown; red tint on shell-root icon |
| Gate | chroot + `shell-root` → `RootShell.isRootAvailable()` async toast |
| Workspace | shell-root uses `workDir=null` (login `/root`) |

---

## Suggested PR title / commit (when implementing)

```text
feat(terminal): add Debian Shell Rooted card for proot and chroot
```

---

## Appendix A — Minimal code sketch

```kotlin
// LinuxCommandBuilder.kt
fun sessionUserForType(type: String): String =
    if (type == "shell-root") "root" else "flux"

// createNewTerminalSession
val shellCmd = ChrootCommandBuilder.buildToolShellCommand(this, type, null)
val user = LinuxCommandBuilder.sessionUserForType(type)
if (type == "shell-root"
    && LinuxCommandBuilder.currentMethod == "chroot"
    && !RootShellService.getInstance(this).isRootAvailable() // actual API name in RootShellService
) {
    Toast.makeText(this, "Root required for rooted chroot shell", Toast.LENGTH_SHORT).show()
    return
}
val (args, envMap) = LinuxCommandBuilder.build(this, shellCmd, user = user)
```

```kotlin
// shellTools
TermToolDef("shell", "Debian Shell", "User: flux"),
TermToolDef("shell-root", "Debian Shell Rooted", "User: root"),
```

---

## Appendix B — Why not `sudo -i` as flux?

| Approach | Drawback |
|----------|----------|
| Card runs flux then `sudo -i` | Extra password/NOPASSWD dependency; prompt noise; not true root login env |
| Direct `--user root` / `su - root` | Matches scripts page; clean HOME/profile; one hop |

Product: **direct root login** for rooted card.
