# Terminal auto-resize + CLI tool launch failures

**Date:** 2026-07-29  
**Status:** partial ship — launch PATH fixed; **two residuals open** (see §13)  
**Device:** `192.168.1.52:33221` (KernelSU; port may change), `linux_method=chroot`, chroot `/data/local/tmp/chrootDebian13`  
**Scope:**

1. PTY/TUI resize when IME, extra keys, bottom nav, or layout chrome changes.  
2. Direct CLI tool buttons (main Terminal page + Project Workspace) failing to start.  
3. **NEW residual:** Codex freeze (shell + button).  
4. **NEW residual:** OpenCode button-only no-resize (shell→opencode OK; other tools resize OK).

**Out of scope:** font zoom UX, multi-window, private API auth for tools (auth missing is documented cause, not app bug to invent).

**ADB safety:** device `192.168.1.52:33221` only. Read-only / bind-mount-if-missing. **No** kill of user/app processes, **no** rm/destructive ops that can crash Android.

---

## 0. User symptoms

### 0.1 Original (pre-fix)

| Path | Observed |
|------|----------|
| Main Terminal → Debian Shell → run `opencode` | Resizes correctly; tools **work** |
| Main Terminal → **tool button** | Often no launch / no resize |
| Project Workspace terminal | Same |

### 0.2 Post-install residual (user + ADB 2026-07-29)

| Path | Observed |
|------|----------|
| Button → tools **except codex** | **Launch works** (PATH fix) |
| Button / shell → **codex** | Freeze / unusable (also in Debian shell) |
| Button → **opencode** | Starts TUI; **does not resize** (IME / chrome) |
| Shell → type `opencode` | Resizes **fine** (control) |
| Button → **other** CLIs | Resize **fine** |
| Wrapper file on device | **Missing** until manually root-written; app cannot create it |

---

## 1. ADB root/chroot verification (2026-07-29)

### 1.1 Environment

```text
su → uid=0 (KernelSU)
chroot marker: /data/local/tmp/chrootDebian13/.flux_configured
prefs: linux_method = chroot
launch_tool host:  /data/data/com.zenithblue.nativecode/files/usr/tmp/launch_tool.sh
launch_tool guest: /tmp/launch_tool.sh (copied at session start)
```

### 1.2 Where tools actually live

| Tool | Binary location (chroot, user flux) | On launch_tool PATH? |
|------|-------------------------------------|----------------------|
| **opencode** | `~/.nvm/versions/node/v26.5.0/bin/opencode` | **NO** |
| **codex** | `~/.nvm/versions/node/v26.5.0/bin/codex` | **NO** |
| **qwen** | `~/.nvm/versions/node/v26.5.0/bin/qwen` | **NO** |
| **node** | `~/.nvm/versions/node/v26.5.0/bin/node` | **NO** |
| agy | `~/.local/bin/agy` | yes |
| claude | `~/.local/bin/claude` → `~/.local/share/claude/...` | yes |
| grok | `~/.local/bin/grok` | yes |
| kiro-cli | `~/.local/bin/kiro-cli` | yes |

Proot rootfs same layout (nvm v26.5.0 has opencode/codex/qwen; `.local/bin` has agy/claude/grok/kiro).

### 1.3 PATH SSOT that interactive shell uses

Guest file (written by `setup_cli_tools.sh`):

```text
/home/flux/.config/fluxlinux/cli-tools.env
```

Adds:

- `$HOME/.local/bin`, `$HOME/bin`, `$HOME/.cargo/bin`
- **Latest** `$NVM_DIR/versions/node/v*/bin` (fast path, no full nvm load required for bin)
- Optional full `nvm.sh`

Login `su - flux` / interactive zsh sources this via zshrc → **all tools found**.

### 1.4 `launch_tool.sh` as deployed (broken)

```sh
#!/bin/zsh
export PATH=/home/flux/.local/bin:/opt/nodejs/bin:/usr/local/bin:/usr/bin:/bin:/sbin:/usr/local/sbin
# … oh-my-zsh source …
exec "$@"
```

**Missing:** `~/.nvm/versions/node/*/bin` and **no** `source cli-tools.env`.

### 1.5 ADB probe results (smoking gun)

```text
# PATH as launch_tool sets it:
PATH_only opencode FAIL
PATH_only codex    FAIL
PATH_only qwen     FAIL
PATH_only agy      OK
PATH_only claude   OK
PATH_only grok     OK
PATH_only kiro-cli OK

# After prepending nvm node bin:
NVM_path opencode/codex/qwen/… all OK

# Actual script run inside chroot:
$ zsh /tmp/launch_tool.sh opencode
/tmp/launch_tool.sh:16: command not found: opencode
```

With `source /home/flux/.config/fluxlinux/cli-tools.env` (or nvm bin on PATH): all four nvm tools resolve.

### 1.6 Proot-style envInit (MainActivity string) — works when used

```sh
export PATH=.../.nvm/versions/node/v26.*/bin:... && . nvm.sh
# zsh expands v26.* → finds opencode/codex/qwen
```

**But chroot tool buttons never use envInit** — they only run `/tmp/launch_tool.sh <name>`.

### 1.7 App method on device

`linux_method=chroot` → every tool button uses `isChrootTool` branch → broken launcher. Explains “many tools not launching” on both Terminal page and Workspace (same builder).

### 1.8 Secondary launch issues (ADB + code)

| Issue | Detail |
|-------|--------|
| Stale launcher | `ensureLauncherScript`: if file exists and length > 0 → **never rewrite** even after code PATH fix |
| Full oh-my-zsh in launcher | Slow; prints insecure-compfix noise; unnecessary for `exec tool` |
| Claude without TTY (adb non-pty) | Printed “Input must be provided…” — real app PTY should be fine; not primary bug |
| Escape nesting | chroot `su -c "… su - flux -c \"cmd\""` fragile for complex cmds; prefer launcher script only after PATH fixed |
| Proot tools | Use `toolCmd` + envInit (not launch_tool) → better chance; still brittle if nvm version ≠ `v26.*` |

---

## 2. Resize: how it is supposed to work

```
IME / extra keys / nav visibility
  → View hierarchy height changes
  → TerminalView.onSizeChanged / updateSize()
  → TerminalSession.updateSize → JNI.setPtyWindowSize (TIOCSWINSZ)
  → SIGWINCH to foreground process group
  → TUI re-layout
```

Manifest: `adjustResize|stateHidden`.  
App insets: keyboard open → hide bottom nav, `contentFrame` bottom pad = `ime.bottom`.

Shell→opencode proves view+PTY+OpenCode can reflow. Direct/workspace fail delivery and/or first size.

---

## 3. Resize root causes

### 3.1 Why shell→opencode works

Interactive chroot login shell + job control → correct winsize before TUI; SIGWINCH hits foreground opencode. Wrapper `chroot_term_wrapper.sh` helps outer chain.

### 3.2 Why direct tool + workspace fail resize

| Layer | Problem |
|-------|---------|
| **Process tree** | `su -c 'launch_tool → exec opencode'` — no interactive zsh job control; SIGWINCH often never reaches TUI |
| **Wrapper** | Trap only helps if wrapper receives WINCH; script never upgraded if file exists; `"$@"` not always ideal pgrp |
| **Host UI** | Layout listeners only `requestLayout`/`invalidate` — **no** `TerminalView.updateSize()`; `setExtraKeysEnabled` never forces PTY resize |
| **Initial size race** | Tool starts on first non-zero size before chrome settles → wrong first canvas |
| **Workspace** | Second `TerminalView`; tab bar + project nav chrome; same missing force-resize |

Full prior analysis remains valid in §7–8 below.

---

## 4. CLI tool launch root cause (primary — ADB confirmed)

### 4.1 Code path

```kotlin
// MainActivity.createNewTerminalSession / createWorkspaceTerminalTab
val isChrootTool = currentMethod == "chroot" && type != "shell"
if (isChrootTool) {
    ensureLauncherScript(ctx)
    shellCmd = "/tmp/launch_tool.sh $toolName"   // workspace: "cd $path && /tmp/launch_tool.sh …"
} else {
    shellCmd = toolCmd  // includes envInit + nvm path
}
```

Chroot mount step copies host `files/usr/tmp/launch_tool.sh` → guest `/tmp/launch_tool.sh`.

### 4.2 Why “command not found”

1. Tools installed under **nvm node bin**, not only `~/.local/bin`.  
2. Launcher hardcodes short PATH → nvm tools invisible.  
3. Interactive shell loads `cli-tools.env` → works when user types tool name.  
4. Same bug on **Terminal page** and **Workspace** (shared chroot branch).

### 4.3 Why shell then type `opencode` works

Login shell PATH includes nvm via zshrc / `cli-tools.env`. Binary exists and runs.

---

## 5. Goals

1. Tool buttons launch the same binaries interactive shell finds (chroot + proot).  
2. Any terminal surface resizes PTY on IME / extra keys / nav / orientation / font.  
3. Direct TUI tools reflow like shell→tool.  
4. Launcher script content is versioned/refreshed (no stale forever).  
5. No regression for interactive Debian shell.

---

## 6. Fix plan — CLI tool launch

### 6.1 Rewrite `launch_tool.sh` (SSOT guest launcher)

**File:** `ChrootCommandBuilder.ensureLauncherScript`

```sh
#!/bin/zsh
# launch_tool.sh — force-rewritten by app (content hash / version header)
export HOME=/home/flux
export TERM="${TERM:-xterm-256color}"
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
export XDG_RUNTIME_DIR=/tmp
export TMPDIR=/tmp
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"

# Minimal base PATH
export PATH="$HOME/.local/bin:$HOME/bin:$HOME/.cargo/bin:/opt/nodejs/bin:/usr/local/bin:/usr/bin:/bin:/sbin"

# SSOT: same env as interactive setup_cli_tools
if [ -f "$HOME/.config/fluxlinux/cli-tools.env" ]; then
  # shellcheck disable=SC1091
  . "$HOME/.config/fluxlinux/cli-tools.env"
fi

# Fallback if cli-tools.env missing: latest nvm node bin
if [ -d "$NVM_DIR/versions/node" ]; then
  _n=$(ls -1d "$NVM_DIR/versions/node"/v* 2>/dev/null | sort -V | tail -1)
  [ -n "$_n" ] && [ -d "$_n/bin" ] && export PATH="$_n/bin:$PATH"
  unset _n
fi

# Do NOT source full oh-my-zsh for tool exec (noise + delay)
if [ $# -lt 1 ]; then
  echo "launch_tool: missing command" >&2
  exit 127
fi
if ! command -v "$1" >/dev/null 2>&1; then
  echo "launch_tool: command not found: $1 (PATH=$PATH)" >&2
  exit 127
fi
exec "$@"
```

### 6.2 Always refresh launcher (and term wrapper)

```kotlin
// Replace exists-and-nonempty early return with content comparison:
val desired = "…"
if (f.readText() != desired) {
  f.writeText(desired)
  // su chmod 755
}
// Or version banner: # nativecode-launch-tool v2
```

Same for `ensureTermWrapper`.

### 6.3 Proot / shared envInit

Unify tool env for **both** methods:

```kotlin
// Prefer guest one-liner used by everyone:
val toolEnv = "[ -f \$HOME/.config/fluxlinux/cli-tools.env ] && . \$HOME/.config/fluxlinux/cli-tools.env; " +
  "export PATH=\$HOME/.local/bin:\$PATH"
// proot: zsh -c "$toolEnv; cd …; exec opencode"
// chroot: launch_tool already sources cli-tools.env
```

Drop hardcoded `v26.*` only path (breaks on node upgrades).

### 6.4 Workspace + main: same helper

Extract `buildToolShellCommand(type, workDir)` used by:

- `createNewTerminalSession`
- `createWorkspaceTerminalTab`

Avoid drift (today workspace duplicates chroot branch).

### 6.5 Fail loud in terminal

If tool exit 127 / “command not found”, session already dies — ensure stderr visible (user sees red line). Optional toast on quick exit.

### 6.6 ADB re-check after fix

```sh
# as root, mounts up:
busybox chroot $CHROOT /bin/su - flux -c '/tmp/launch_tool.sh opencode'
# expect TUI or at least not "command not found"
# same for codex qwen claude agy grok kiro-cli
```

---

## 7. Fix plan — auto-resize

### 7.1 Host: always push view size to PTY

```kotlin
fun forceTerminalResize(tv: TerminalView?) {
    tv ?: return
    tv.post {
        tv.updateSize()
        tv.onScreenUpdated()
    }
}
```

| Trigger | Call |
|---------|------|
| Container `OnLayoutChangeListener` | `forceTerminalResize` (not only requestLayout) |
| `setExtraKeysEnabled` | both terminal views after VISIBLE/GONE |
| IME insets change | if page is terminal or workspace |
| `switchTerminalSession` / `switchWorkspaceTab` | post after attach |
| New session create | post 1 frame after visible |
| Font zoom | already via setTextSize — verify both views |

### 7.2 Chroot SIGWINCH delivery

Rewrite wrapper (versioned):

```sh
#!/system/bin/sh
trap 'kill -WINCH -$$ 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH
exec "$@"
```

If still weak: after `updateSize`, optional `Os.kill(shellPid, SIGWINCH)` (needs public pid getter on `TerminalSession`).

### 7.3 Settled first size

Attach/start session after view width/height > 0 and terminal container VISIBLE (GlobalLayout once). Avoid first size = full screen before chrome.

### 7.4 Workspace parity

Same force-resize + tool helper as main terminal; tab bar / bottom nav / extra keys all trigger resize.

---

## 8. Implementation order

1. **Fix launch_tool + force rewrite** (6.1–6.2) — unblocks opencode/codex/qwen immediately.  
2. **Unify tool command builder** main + workspace (6.3–6.4).  
3. **Host force-resize pipeline** (7.1).  
4. **Chroot WINCH wrapper refresh** (7.2).  
5. **Settled attach size** (7.3) + workspace audit (7.4).  
6. ADB matrix + user UI matrix (§9).

---

## 9. Test matrix

### 9.1 Launch (chroot + proot if both present)

| # | Action | Expect |
|---|--------|--------|
| L1 | Shell → `command -v opencode codex qwen` | paths under nvm or local |
| L2 | Main → button opencode | starts TUI (not instant exit / not found) |
| L3 | Main → codex, qwen, claude, agy, grok, kiro | same |
| L4 | Workspace → same buttons in project dir | cwd project; tools start |
| L5 | ADB: `launch_tool.sh opencode` after reinstall | no `command not found` |

### 9.2 Resize

| # | Action | Expect |
|---|--------|--------|
| R1 | Shell → opencode → keyboard | reflow (regression) |
| R2 | Button opencode → keyboard / extra keys | reflow |
| R3 | Workspace opencode → keyboard / extra keys / bottom nav | reflow, no clip |
| R4 | Font zoom + rotate | cols/rows update |

---

## 10. Files to touch

| File | Change |
|------|--------|
| `ChrootCommandBuilder.kt` | launch_tool content + always refresh; term wrapper refresh |
| `MainActivity.kt` | shared tool cmd helper; force resize; layout/IME/extra-keys; settle attach |
| Optional `TerminalSession.java` | `getPid()` for explicit WINCH |
| Optional small helper | `TerminalResizeHelper` / `ToolLaunchCommands` |
| This plan | status → implemented after ship |

---

## 11. Risks

| Risk | Mitigation |
|------|------------|
| `cli-tools.env` missing on old installs | Fallback nvm dir scan in launch_tool |
| Sorting `v*` needs `sort`/`ls` in guest | already in Debian; pure shell loop if needed |
| Sourcing nvm.sh slow | prefer fast path in cli-tools.env only |
| WINCH kill broad | only from wrapper process group |
| Double-resize flicker | TerminalView already no-ops if cols/rows unchanged |

---

## 12. Summary

| Question | Answer |
|----------|--------|
| Why shell tools work? | Login PATH via `cli-tools.env` / zshrc includes **nvm node bin** |
| Why buttons fail? | Chroot uses `launch_tool.sh` with **short PATH** → opencode/codex/qwen **command not found** (ADB proven) |
| Why both Terminal + Workspace? | Same `isChrootTool` + `launch_tool` path |
| Why resize works only shell→TUI? | Job control + settled size; direct tree loses SIGWINCH; host rarely calls `updateSize` |
| Primary fix order | **1) fix launcher PATH** **2) force view→PTY resize** **3) WINCH wrapper** |

**Approval gate:** implement after user OK on this plan.

---

## 13. Residual bugs — ADB re-verify after APK install (2026-07-29)

**Device:** `192.168.1.52:33221`, prefs `linux_method=chroot`, project `/home/flux/repos/fluxlinux`.  
**Confirmed shipped:** host + guest `launch_tool.sh` = **nativecode-launch-tool v2** (sources `cli-tools.env` + nvm fallback).

### 13.1 Smoking gun: WINCH wrapper never installs (app SELinux/path perms)

| Check | Result |
|-------|--------|
| `/data/local/tmp` mode | `drwxrwx--x shell shell` |
| App uid write (`u0_a415` / 10415) | **`Permission denied`** |
| `ensureTermWrapper()` | Writes via app `File.writeText` → **always fails** |
| Live process tree for button opencode | **No** `chroot_term_wrapper.sh` in chain |
| Actual session parent | `/system/bin/sh -c "su -c … chroot … su - flux -c '/tmp/launch_tool.sh opencode'"` |

Button opencode process tree (read-only `ps`/`/proc`):

```text
com.zenithblue.nativecode
 └─ /system/bin/sh -c "su -c '…mounts…; exec busybox chroot … /bin/su - flux -c \"… launch_tool.sh opencode\"'"
     └─ /bin/su - flux -c … launch_tool.sh opencode
         └─ opencode   (fd 0/1 → /dev/pts/N)
```

`TerminalSession` only PTY-`TIOCSWINSZ`s the master and tracks **mShellPid = outer sh**. Kernel SIGWINCH to FG pgrp often does **not** reach OpenCode without a forwarding wrapper (or proper job control like interactive login zsh).

### 13.2 Why only OpenCode button fails resize

| Observation | Detail |
|-------------|--------|
| Binary | `opencode` → 171MB aarch64 ELF (`opencode-ai/bin/opencode.exe`) |
| Live RSS | ~600MB per instance (two running under app: ~1.2GB) |
| TUI | Heavy sequence set: DECRQM 1016/2026/2027/2031, opentui, alternate screen — **WINCH-driven layout** |
| Shell→opencode | Login `su - flux` zsh has job control; FG job gets tty WINCH → **resize OK** |
| Button→other tools | Resized OK (likely re-query size / simpler TUI / less strict WINCH) |
| Host `forceTerminalResize` | Present in APK code path; still insufficient if guest never gets SIGWINCH |

**Root cause (residual):** wrapper path broken on device (cannot write `/data/local/tmp` as app) → no WINCH forward → OpenCode alone sensitive enough to show the bug.

### 13.3 Codex freeze — not PATH; binary + auth + cold start

| Check | Result |
|-------|--------|
| `command -v codex` | `/home/flux/.nvm/versions/node/v26.5.0/bin/codex` → `codex.js` |
| Native binary | `@openai/codex-linux-arm64` … `/vendor/aarch64-unknown-linux-musl/bin/codex` **257MB** static musl |
| `codex --version` | OK ~2s |
| `codex --help` / `codex doctor` | OK |
| `codex doctor` auth | **✗ no Codex credentials** (`~/.codex/auth.json` missing; no `config.toml`) |
| No-TTY interactive | `stdin is not a terminal` / `TERM=dumb` refuse |
| Pseudo-TTY `script` + TERM=xterm-256color | TUI starts (escape sequences) then sits; ~15s+ “blank-ish” feel |
| MemAvailable while 2× opencode live | ~2.3GB free of 7.5GB — loading 257MB + TUI is heavy |

**Root cause (likely stack):**

1. **Primary UX freeze:** unauthenticated Codex TUI / login wall (doctor: no credentials) looks hung.  
2. **Secondary:** 257MB native cold load (seconds) + memory pressure if OpenCode already resident.  
3. **Not primary:** launch PATH / “command not found” (fixed for other tools; codex resolves).  
4. **Possible extra:** sandbox/bwrap + websocket fail note in doctor (HTTPS fallback may still work).

Codex is **not** “command not found” on current install.

### 13.4 Other ADB notes

- Host `launch_tool` v2 present under app `files/usr/tmp`; guest `/tmp/launch_tool.sh` matched after session copy.  
- `chroot_term_wrapper.sh` **absent** until root wrote a test copy; app cannot create it.  
- Logcat: OpenCode advanced DECRQM modes; IME insets fire (`ime:[0,0,0,834]`); unrelated `EISDIR` once when opening a path that is a directory.  
- **Safety:** probes used mount-if-missing + read-only file/process inspect; no process kills, no destructive deletes of chroot/app data.

---

## 14. Fix plan — residuals only

### 14.1 Force-install WINCH wrapper via `su` (blocks OpenCode button resize)

**File:** `ChrootCommandBuilder.ensureTermWrapper`

- Do **not** rely on app write to `/data/local/tmp` (mode `770 shell:shell` → denied).  
- Write + chmod exclusively through `/system/bin/su -c`:

```sh
# host-side pattern (escape carefully so $$ stays literal for the script body)
su -c 'cat > /data/local/tmp/chroot_term_wrapper.sh << "EOF"
#!/system/bin/sh
# nativecode-chroot-term-wrapper v2
trap "kill -WINCH -$$ 2>/dev/null; kill -WINCH 0 2>/dev/null" WINCH
exec "$@"
EOF
chmod 755 /data/local/tmp/chroot_term_wrapper.sh'
```

- Verify after write: file exists, content contains `kill -WINCH -$$` (not a frozen numeric PID).  
- Prefer content-hash compare still, but **read** via `su cat` if app cannot read either.  
- Optional: also place wrapper under `ctx.filesDir` and `su cp` into `/data/local/tmp`.

### 14.2 Prove session uses wrapper

After open button opencode:

```text
/proc/<shellpid>/cmdline should start with …/chroot_term_wrapper.sh
# not only /system/bin/sh -c
```

### 14.3 OpenCode-specific host belt-and-suspenders

| Step | Why |
|------|-----|
| Keep `forceTerminalResize` on IME / extra keys / layout | Host already ships this |
| After `updateSize`, optional `Os.kill(session.pid, SIGWINCH)` | `TerminalSession` already exposes pid (`getPid()` / `mShellPid`) — hits wrapper first, which must forward |
| Settled attach already present | Keep; OpenCode first paint at wrong size is worse than other tools |
| Do **not** kill leftover opencode processes from ADB | User rule; memory pressure is a note only |

### 14.4 Codex — make failure legible; reduce false “freeze”

| Step | Action |
|------|--------|
| **A** | Document: need `codex login` or API key env; doctor already says no auth |
| **B** | Ensure button/shell always pass real PTY + `TERM=xterm-256color` (already in envMap) — avoid dumb TERM path |
| **C** | Optional: on first codex launch toast “Loading Codex (~250MB)…” so cold start ≠ freeze |
| **D** | Optional: if exit quick or doctor auth fail, surface stderr line in terminal (already) |
| **E** | Do **not** auto-login or invent keys (out of scope) |
| **F** | Optional onboarding note / settings link: “Codex requires login once in shell: `codex login`” |

No PATH rewrite needed for codex on current device.

### 14.5 Implementation order (residuals)

1. **`ensureTermWrapper` via su write** (14.1) — unblocks OpenCode button SIGWINCH.  
2. Verify process tree uses wrapper (14.2).  
3. Optional explicit `Os.kill(pid, SIGWINCH)` after `updateSize` for chroot sessions (14.3).  
4. Codex UX notes/toast + docs only unless new ADB shows different hang (14.4).  
5. User matrix: R2 opencode button IME; L3 codex after `codex login`.

### 14.6 Test matrix (residual)

| # | Action | Expect |
|---|--------|--------|
| W1 | After app open session: `ls -la /data/local/tmp/chroot_term_wrapper.sh` | exists, v2, literal `$$` |
| W2 | Button opencode → `/proc` tree | wrapper is sessionExec |
| R2b | Button opencode → open keyboard / extra keys | reflow like shell→opencode |
| R2c | Button other tool | still reflow (no regression) |
| C1 | Shell `codex doctor` | auth line documents missing login |
| C2 | After user `codex login`, shell + button codex | TUI usable (not app PATH bug) |

---

## 15. Summary (pre-residual-impl)

| Question | Answer |
|----------|--------|
| Did launch PATH fix ship? | **Yes** — v2 launcher on host+guest; all tools except codex launch |
| Why codex still bad? | Auth missing + 257MB cold start; **not** nvm PATH |
| Why only opencode button no-resize? | WINCH wrapper **never created** (app cannot write `/data/local/tmp`); OpenCode TUI is WINCH-strict; shell path has job control |
| Primary next fix | **Write wrapper via `su`**, confirm sessionExec uses it, then retest OpenCode button resize |
| Codex next fix | User login + optional loading toast; no kill/delete debugging |

---

## 16. Residual fix shipped (2026-07-29) — permanent

### 16.1 What changed

| Item | Implementation |
|------|----------------|
| **WINCH wrapper install** | Write under **`filesDir/usr/tmp/chroot_term_wrapper.sh`** (app-owned, always writable). Session uses that path as `sessionExec` — **no dependency on `/data/local/tmp` write perms**. |
| **Host mirror** | Best-effort `su cp` → `/data/local/tmp/chroot_term_wrapper.sh` for ADB; failure non-fatal. |
| **Wrapper v3 (critical)** | **No `exec`**. Old v2 `exec "$@"` replaced the process and **dropped the WINCH trap**, so even a successful install could not forward. v3 runs `"$@"` then `exit $?` so `mShellPid` stays on the wrapper. |
| **Host SIGWINCH** | `forceTerminalResize` → `updateSize()` + `Os.kill(session.pid, SIGWINCH)` → wrapper trap → guest pgrp. |
| **Codex UX** | Toast on codex button: cold-start ~250MB + needs `codex login` once. |

### 16.2 Files

- `ChrootCommandBuilder.kt` — `ensureTermWrapper(ctx): String?`, v3 script, app-owned write + su mirror  
- `MainActivity.kt` — both session creators use returned path; `forceTerminalResize` SIGWINCH; `maybeToastHeavyToolLaunch`

### 16.3 Why permanent

1. App data path never hits `770 shell:shell` on `/data/local/tmp`.  
2. Wrapper process identity matches Termux `mShellPid` contract.  
3. Dual path: kernel TIOCSWINSZ **and** explicit `Os.kill` SIGWINCH.  
4. Codex hang is auth/size — documented in-app, not mistaken for PATH/resize.

### 16.4 Device verify (user)

| # | Action | Expect |
|---|--------|--------|
| W1 | Open any chroot session | `filesDir/…/usr/tmp/chroot_term_wrapper.sh` exists, header **v3**, body has literal `$$` (not a frozen PID) |
| W2 | Button opencode → `ps` / `/proc/<pid>/cmdline` | session shell = `…/chroot_term_wrapper.sh` |
| R2b | Button opencode → IME / extra keys | reflow (same as shell→opencode) |
| C1 | Button codex | toast + slow load; if blank, run `codex login` in shell first |

### 16.5 Status

| Track | Status |
|-------|--------|
| launch_tool PATH v2 | **Shipped** |
| WINCH wrapper permanent | **Shipped** then **hotfix 16.6** |
| Explicit SIGWINCH | **Shipped** |
| Codex toast | **Shipped** |
| Codex auth | **User action** (`codex login`) |

---

## 16.6 Hotfix — app-data exec Permission denied (2026-07-29)

**Symptom:** every chroot session:
`exec("/data/user/0/com.zenithblue.nativecode/files/usr/tmp/chroot_term_wrapper.sh"): Permission denied`

**Cause:** Android SELinux / W^X blocks `execve` of scripts under app private data. JNI `createSubprocess` does `execvp(mShellPath, argv)` — mShellPath must be a system (or native lib) binary.

**Fix:**
- `sessionExec` / `ensureTermWrapper()` → always **`/system/bin/sh`**
- WINCH trap **inlined** in `ChrootCommandBuilder.build()` on the outer `sh -c` (no separate script exec)
- Host `/data/local/tmp/chroot_term_wrapper.sh` remains ADB-only mirror, not session path
