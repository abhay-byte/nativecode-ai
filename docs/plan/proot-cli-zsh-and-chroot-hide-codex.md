# Plan: Proot CLI zsh fix + hide Codex card in chroot

**Date:** 2026-07-29  
**Status:** IMPLEMENTED  
**Scope:** (1) proot mode CLI tool launch zsh breakage (2) hide Codex card in chroot only on Terminal + Project workspace hub

---

## Goals

| # | Goal | Mode |
|---|------|------|
| **A** | CLI tool cards (opencode, codex, claude, …) launch without zsh errors | **proot only** (fix launch path) |
| **B** | Codex card **hidden** on Terminal tool selector + Project workspace tool hub | **chroot only**; proot still shows Codex |

Out of scope: codex chroot freeze/auth/TUI (already documented in `docs/plan/terminal-auto-resize.md` §13.3) — product choice is **hide card**, not fix codex binary.

---

## Issue A — Proot CLI tools → zsh errors

### Symptom

Terminal / Workspace → tap free/paid CLI tool while `linux_method=proot` → session dies or prints zsh errors (glob / path / command not found) instead of TUI.

Interactive **Debian Shell** (`exec zsh` bare login via proot-distro) often still works.

### Current launch path (proot)

```
createNewTerminalSession / createWorkspaceTerminalTab
  → ChrootCommandBuilder.buildToolShellCommand(type, workDir)   // name is historical SSOT
  → LinuxCommandBuilder.build → ProotCommandBuilder.build
```

**Tool command (proot branch)** — `ChrootCommandBuilder.kt` ~209–211:

```text
export HOME=/home/flux; <toolEnvInit>; mkdir -p DIR && cd DIR && exec TOOL
```

`toolEnvInit()`:

```text
[ -f $HOME/.config/fluxlinux/cli-tools.env ] && . …;
export PATH=$HOME/.local/bin:…:$PATH;
export NVM_DIR=${NVM_DIR:-$HOME/.nvm};
for _n in $NVM_DIR/versions/node/v*/bin; do [ -d $_n ] && export PATH=$_n:$PATH; done
```

**Wrapper** — `ProotCommandBuilder.kt` ~24–26:

```text
bash -c 'exec python proot-distro login debian --shared-tmp --user flux -- zsh -c "<shellCmd>"'
```

Chroot path uses `/tmp/launch_tool.sh` (safer). Proot does **not**.

### Root causes (ranked)

| Priority | Cause | Why it breaks |
|----------|--------|----------------|
| **P0** | Host `bash -c` double-quotes expand `$HOME`, `$PATH`, `$NVM_DIR`, `$_n` **before** guest zsh runs | `zsh -c "…$HOME…"` is inside host script string → Android/host `$HOME` / empty `$NVM_DIR` rewrite guest paths → wrong PATH / missing env file |
| **P1** | zsh default `NOMATCH` on `v*/bin` | If glob has no match, zsh aborts: `zsh: no matches found: …/v*/bin` — entire `-c` script dies before `exec TOOL` |
| **P2** | Extra `zsh -c` on top of proot-distro login shell wrap | Documented in `docs/environment/proot-fast-launcher-design.md` / task-01; slow + fragile nesting |
| **P3** | No shared `launch_tool.sh` on proot | Drift vs chroot; PATH SSOT only in half-fixed env one-liner |

### Fix plan A

#### A1 — Stop host expansion of guest payload (required)

**File:** `ProotCommandBuilder.kt`

Do **not** embed `shellCmd` in double quotes for host bash.

Preferred options (pick one):

1. **Single-quote guest payload** (simple if `shellCmd` has no single quotes):
   ```kotlin
   // shellCmd must not contain single quotes, or escape ' → '\''
   "… -- zsh -c '$escapedSingle'"
   ```
2. **Base64 / env var** (robust):
   ```kotlin
   // host: NC_GUEST_CMD=$(printf %s '…' | base64) ; proot-distro … -- zsh -c 'eval "$(echo $NC_GUEST_CMD | base64 -d)"'
   ```
3. **Best long-term:** stop `zsh -c` entirely for tools — run binary via script (A2).

Also escape any remaining host-expansions of `$prootDistro` / paths only via Kotlin interpolation (already done).

#### A2 — Align proot tools with `launch_tool.sh` (recommended)

**Files:** `ChrootCommandBuilder.kt`, optionally bind/copy in proot

- Reuse `ensureLauncherScript(ctx)` for **both** methods.
- Proot tool command becomes:
  ```text
  mkdir -p DIR && cd DIR && /path/to/launch_tool.sh TOOL
  ```
  or copy script into guest via proot-distro shared-tmp (`/tmp/launch_tool.sh` if shared-tmp maps app tmp).

- `launch_tool.sh` already:
  - sets HOME/TERM/PATH
  - sources `cli-tools.env`
  - resolves nvm with `ls | sort -V | tail` (**no zsh NOMATCH glob**)
  - `exec "$@"`

If shared-tmp is reliable for proot (current `useSharedTmp=true`), write host `files/usr/tmp/launch_tool.sh` and run `/tmp/launch_tool.sh $tool` inside guest (same as chroot).

#### A3 — Harden `toolEnvInit` if one-liner kept

Only needed if A2 deferred:

```zsh
# null_glob or (N) so missing nvm does not abort
setopt NULL_GLOB
for _n in $NVM_DIR/versions/node/v*/bin; do
  [ -d "$_n" ] && PATH="$_n:$PATH"
done
```

Or drop for-loop; rely on `cli-tools.env` + `.zshenv` from `setup_cli_tools.sh`.

#### A4 — Prefer non-interactive exec over interactive zsh for tools

- Login shell (`type=shell`): keep bare `proot-distro login …` (no `zsh -c`).
- Tools: direct `exec tool` via `launch_tool` / `bash -lc` **or** `zsh -fc` with fixed quoting — never load full oh-my-zsh for tool start.
- Optional: set `FLUX_QUIET_SHELL=1` in tool env (already in fast launcher) so guest `.zshrc` skips fastfetch noise if any zsh init remains.

#### A5 — Optional: proot-fast path later

`nativecode_proot_fast.sh exec --profile cli -- TOOL` avoids proot-distro + zsh wrap. Out of this plan unless already wired into `ProotCommandBuilder`; keep as follow-up (task-02).

### A acceptance

| Check | Expect |
|-------|--------|
| proot → opencode / claude / qwen / agy / grok / kiro / codex cards | TUI or tool help; **no** `zsh: no matches found` |
| proot → Debian Shell | interactive zsh still works |
| Host log / session text | no Android `$HOME` paths inside guest cmd |
| ADB: same as button | `proot-distro login debian --user flux -- /tmp/launch_tool.sh opencode` works |

---

## Issue B — Hide Codex card in chroot only

### Why

Chroot codex: heavy musl binary (~257MB), cold start, unauthenticated TUI looks frozen (`docs/plan/terminal-auto-resize.md` §13.3). Product ask: **do not show Codex card in chroot**. Keep card in **proot**.

### Surfaces (must match Image #1 + workspace)

| UI | Builder | List today |
|----|---------|------------|
| **Main Terminal** tool selector | `buildTerminalToolSelectorView()` ~1887–2020 | `paidTools` includes `codex` first |
| **Project workspace** hub | workspace hub `aiTools` ~11414+ | includes `codex` |

Both use `createNewTerminalSession(type)` / `createWorkspaceTerminalTab(type)` — hide card only; no need to ban type string if unreachable from UI. Optional hard-block if type==codex && chroot (toast).

### Current method SSOT

- Global: `LinuxCommandBuilder.currentMethod` ← prefs `linux_method`
- Project open switches method with project (`activeProjectMethod`)

Gate: `LinuxCommandBuilder.currentMethod == "chroot"` (same as session builders).

### Fix plan B

#### B1 — Filter lists by method

**File:** `MainActivity.kt`

Terminal selector:

```kotlin
val paidTools = listOf(
    TermToolDef("codex", "codex", "OpenAI Codex"),
    // …
).let { list ->
    if (LinuxCommandBuilder.currentMethod == "chroot")
        list.filter { it.type != "codex" }
    else list
}
```

Workspace hub:

```kotlin
val aiTools = listOf(…, AiToolDef("codex", …), …)
    .filter { LinuxCommandBuilder.currentMethod != "chroot" || it.type != "codex" }
```

#### B2 — Rebuild selector on method switch

`buildTerminalToolSelectorView()` builds once. If user switches Settings proot↔chroot without process death, selector can stay stale.

- On method change (settings env cards / project open that sets `linux_method`):
  - rebuild `terminalToolSelectorScrollView` **or**
  - hide/show codex child by tag
- Prefer rebuild helper: `refreshTerminalToolSelector()` called from method switch + when showing terminal tab with empty sessions.

Workspace hub: rebuild when opening project workspace / method change (same filter).

#### B3 — Sidebar / existing sessions

- Do **not** kill existing codex tabs if user switched method mid-session (edge case).
- New launches only: no card → no new codex session in chroot.
- Optional: if `type=="codex" && chroot`, toast “Codex unavailable in chroot” and return (defense in depth).

#### B4 — Do not hide on proot

Verify both UIs still show Codex when `linux_method=proot`.

### B acceptance

| Check | Expect |
|-------|--------|
| chroot → Terminal page PAID CLI | no Codex card; others remain |
| chroot → Project workspace hub | no Codex card |
| proot → both UIs | Codex still present |
| Switch chroot→proot (or reverse) | cards update without app reinstall |
| chroot launch paths | opencode etc. unchanged |

---

## Implementation order

1. **A1 + A2** — proot tool launch robust (quoting + launch_tool)  
2. **A3** only if one-liner still used  
3. **B1 + B2** — filter + refresh UI  
4. **B3** optional guard  
5. Manual test matrix below  

No onboarding / setup_cli_tools changes required for B. A may only need launcher script already written by `ensureLauncherScript`.

---

## Files to touch

| File | Change |
|------|--------|
| `app/.../terminal/ProotCommandBuilder.kt` | Safe guest cmd quoting; optional drop bare `zsh -c` for tools |
| `app/.../terminal/ChrootCommandBuilder.kt` | Proot branch use `launch_tool.sh` / harden `toolEnvInit` |
| `app/.../MainActivity.kt` | Filter codex in terminal + workspace lists; refresh on method change; optional guard |

Scripts: none required if `launch_tool.sh` + shared-tmp sufficient.

---

## Test matrix

### Proot (A)

1. Settings → proot.  
2. Terminal → each card: shell, opencode, codex, agy, claude-code, qwen-code, grok, kiro.  
3. Project (proot method) → workspace hub same tools.  
4. Confirm no zsh glob / host-path leakage in first lines.  
5. Regression: interactive Debian Shell prompt + oh-my-zsh still OK.

### Chroot (B + no A regression)

1. Settings → chroot.  
2. Terminal selector: **Codex absent**; other paid/free + shell present.  
3. Project chroot workspace hub: **Codex absent**.  
4. Launch opencode / shell still work (existing launch_tool path).  
5. Switch back to proot: Codex cards return.

### ADB smoke (optional)

```sh
# proot tool (after fix — example)
# expect PATH via launch_tool, not zsh nomatch
su u0_aXXX -c '… proot-distro login debian --shared-tmp --user flux -- /tmp/launch_tool.sh opencode --version'
```

---

## Risks

| Risk | Mitigation |
|------|------------|
| shared-tmp does not expose host `launch_tool.sh` in proot guest `/tmp` | Write via proot login once, or pass absolute guest path under bound home |
| Single-quote escape misses `'` in workDir | Restrict workDir to safe paths; or base64 payload |
| Tool selector not rebuilt | Explicit refresh on method change |
| User expects codex in chroot later | Re-enable card when chroot codex TUI/auth fixed; keep filter one place |

---

## Done definition

- [x] Proot CLI tool cards start tools without zsh errors  
- [x] Proot Debian Shell unchanged  
- [x] Chroot: Codex card hidden on Terminal + Project workspace terminal hub  
- [x] Proot: Codex card still shown  
- [x] Method switch refreshes cards  
- [x] No chroot launch_tool regression for non-codex tools  

### Implementation notes (2026-07-29)

| Item | Change |
|------|--------|
| A1 | `ProotCommandBuilder`: single-quote guest `zsh -c` payload; escape `'` |
| A2 | `ChrootCommandBuilder.buildToolShellCommand`: proot + chroot both use `/tmp/launch_tool.sh` + `ensureLauncherScript` |
| A3 | `toolEnvInit` kept hardened (`setopt NULL_GLOB`) as fallback only |
| B1 | Filter `codex` when `currentMethod==chroot` on terminal selector, workspace hub, + dropdown |
| B2 | `refreshToolCardsForMethod()` on method switch / project open / terminal empty show |
| B3 | Guard toast if `type==codex` && chroot on create session/tab |
