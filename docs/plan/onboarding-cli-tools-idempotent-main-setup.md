# Onboarding: idempotent AI CLI tools + fold into main Environment Setup

**Date:** 2026-07-28  
**Status:** implemented (code done; device verify open)  
**Scope:** rewrite guest `setup_cli_tools.sh` (idempotent installs, correct zsh PATH); remove separate onboarding AI-tools page; run CLI install as final step of main Environment Setup (proot + chroot)  
**Related plans:**
- [`gpu-accel-vendor-detect-turnip-virgl.md`](./gpu-accel-vendor-detect-turnip-virgl.md) — GPU mode detect + Turnip URL pin (same session family)
- [`proot-debian-rootfs-local-install.md`](./proot-debian-rootfs-local-install.md) — local rootfs package path
- [`host-scripts-tweaks-flux-install.md`](./host-scripts-tweaks-flux-install.md) — flux_install / host script cleanup  

**Primary files:**
| Path | Role |
|------|------|
| `app/src/main/assets/scripts/setup_cli_tools.sh` | Guest script: NVM/Node + AI CLIs as `flux` |
| `app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt` | Onboarding pages + setup pipeline |
| `app/src/main/java/com/ivarna/nativecode/MainActivity.kt` | Manual script runner still lists `setup_cli_tools.sh` (unchanged behavior; copy into guest `/tmp`) |
| `app/src/main/assets/scripts/setup_customization_debian.sh` | Writes optimized `.zshrc` (PATH base); CLI script re-wires after |

---

## Problem (before)

### A. `setup_cli_tools.sh` — install-only, fragile

1. **No skip-if-present**  
   Every onboarding re-run (or Scripts UI re-run) did full `apt-get update`, always `nvm install 26`, always every `npm install -g`, always every curl installer. Wasteful, network-heavy, non-idempotent.

2. **Silent partial failure**  
   Each tool: fail → `WARN` → continue → final `Complete!` with exit 0. No summary of what actually works. No post-install `command -v` / version probe.

3. **Harder to recover than look**  
   No `set -euo pipefail`, no root guard, no `DEBIAN_FRONTEND=noninteractive` (apt could hang/prompt under proot).

4. **Shell PATH wrong for zsh**  
   - Flux default shell becomes **zsh** after customization (`chsh` / passwd patch).  
   - Script only appended NVM + PATH to `.bashrc` / `.zshrc` with simple grep guards.  
   - **Non-interactive** `zsh -c` / `su -c` does **not** load `.zshrc` — only `.zshenv` (and login: `.zprofile`).  
   - App launchers use Kotlin `envInit` (explicit NVM + path globs); interactive zsh sessions after installers often lost PATH when installers rewrote `.zshrc` or when only bashrc was correct.  
   - Customization script **overwrites** `.zshrc` with a template that has `~/.local/bin` but **no NVM** — order-of-steps mattered; CLI page after customize helped, but re-wiring was weak.

5. **Split `su - flux` blocks**  
   NVM/npm in one login shell; curl installers in a second. PATH / NVM state not shared; harder to reason about.

6. **opencode only via npm**  
   Official also ships `curl -fsSL https://opencode.ai/install | bash` (native binary). npm package `opencode-ai` still valid but musl-sensitive on glibc Debian.

7. **kiro binary name**  
   MainActivity launches `kiro-cli`; some installers only put `kiro` on PATH → false “installed” vs launcher mismatch.

8. **Musl loader**  
   Soft-fail + arm64-only symlink; acceptable but weak if package missing.

### B. Onboarding UX — separate AI tools page

**Old page map (indices):**

| Index | Page |
|------:|------|
| 0 | Brand intro |
| 1 | Feature slideshow |
| 2 | Isolation method (proot / chroot) |
| 3 | Debian base extraction (A–G) |
| 4 | **AI CLI Tools Setup** (separate log + Next) |
| 5 | Complete |

**Flow pain:**
- User finished long base install → Next → **another** full page wait for NVM/npm/curl tools.  
- Felt like “setup done” then surprise second install.  
- CLI progress lived only on page 4; leaving early left tools half-installed with confusing state.  
- `baseNextBtn` = “Next: AI CLI Tools” gated only base success; CLI started only after user tapped Next.

**Proot base steps (old A–G only):** dirs → bootstrap → host configs → `setup_termux` → `flux_install` debian → HW accel → optional customize → unlock Next.

**Chroot base:** root check → `setup_debian13_chroot` → family → HW → optional customize → `finishChrootBaseSetup()` → unlock Next.

CLI ran later via `runCliToolsSetup()` on its own page.

---

## Goals

1. **Idempotent guest CLI script** — skip installed tools; re-run safe; report ok/skip/fail.  
2. **Correct zsh PATH** — always load AI/NVM path via `.zshenv` (+ login/interactive rc files); survive installer rewrites.  
3. **Single Environment Setup page** — base + AI CLIs in one log; Step **H** at end; Next goes straight to Complete.  
4. **Proot + chroot parity** for Step H.  
5. **Soft-fail CLI** — network/tool flakiness must not block finishing onboarding (user can re-run script from Scripts UI).  
6. **No APK packaging of CLI binaries** — install via links inside proot/chroot (same policy as Turnip: direct download URLs in guest, not assets).

---

## Solution overview

```text
Onboarding
  intro → slideshow → isolation
       → Environment Setup (ONE page)
            A…G base (existing)
            H  setup_cli_tools.sh   ← NEW end of main pipeline
       → Complete

Guest script setup_cli_tools.sh
  root: apt (missing only) + musl
  write ~/.config/fluxlinux/cli-tools.env
  wire .zshenv / .zprofile / .zshrc / .bashrc / .profile + /etc/profile.d
  as flux: NVM/Node (skip if v26), npm globals (skip if bin), curl CLIs (skip if bin)
  re-wire shells after installers
  verify + summary
```

---

## Detailed changes

### 1. `setup_cli_tools.sh` rewrite

**Location:** `app/src/main/assets/scripts/setup_cli_tools.sh`

#### 1.1 Hardening

| Item | Behavior |
|------|----------|
| Shebang / mode | `bash`, `set -euo pipefail` |
| Root | require `id -u == 0` |
| User | require `flux` (or `FLUX_USER`) exists with home |
| Apt | `DEBIAN_FRONTEND=noninteractive`; install **only missing** packages (`dpkg -s`) |
| Defaults | `NODE_MAJOR=26`, `NVM_VERSION=v0.40.5`, overridable via env |

#### 1.2 Helpers

- `pkg_ok` / `ensure_pkgs` — selective apt  
- `as_flux` — `su -s /bin/bash - flux -c '…'` (predictable bash for install body even if login shell is zsh)  
- `flux_has <cmd>` — PATH includes `~/.local/bin`, `~/bin`, `~/.cargo/bin`, NVM default node bin, optional `nvm.sh`  
- `record_ok` / `record_skip` / `record_fail` — summary lists  

#### 1.3 Musl

- Install `musl` if missing  
- Ensure `/lib/ld-musl-aarch64.so.1` symlink when possible (arm64)  
- Soft warn if unavailable  

#### 1.4 PATH / NVM env file (zsh-correct)

**Single source of truth:**

```text
/home/flux/.config/fluxlinux/cli-tools.env
```

Contents (conceptually):
- Prepend `$HOME/.local/bin`, `$HOME/bin`, `$HOME/.cargo/bin`
- `NVM_DIR=$HOME/.nvm`
- Fast path: latest `$NVM_DIR/versions/node/v*/bin` on PATH (works **without** full nvm load — important for non-interactive)
- Source `nvm.sh` when present (full npm/nvm)
- bash_completion only under bash  

**Idempotent wiring** via marker `# flux-cli-tools` … `# flux-cli-tools-end` into:

| File | Why |
|------|-----|
| `~/.zshenv` | **Always** loaded by zsh (incl. non-interactive `zsh -c`) |
| `~/.zprofile` | Login zsh (`su - flux`) |
| `~/.zshrc` | Interactive zsh (after oh-my-zsh); block rewritten so it stays at end |
| `~/.bashrc` / `~/.profile` | bash fallbacks |
| `/etc/profile.d/flux-cli-tools.sh` | any login that sources profile.d |

**Re-wire after installers** — curl installers and Oh My Zsh style tools may rewrite `.zshrc`; script runs `ensure_source_line` again at end so PATH block is restored.

#### 1.5 NVM + Node

- Install NVM only if `nvm.sh` missing  
- Skip `nvm install` if active `node` already matches `v${NODE_MAJOR}.*`  
- Else `nvm install` + `alias default` + `use`  

#### 1.6 npm globals (skip if bin present)

| Package | Binary |
|---------|--------|
| `opencode-ai` | `opencode` |
| `@openai/codex` | `codex` |
| `@qwen-code/qwen-code` | `qwen` |

Fallback: if `opencode` still missing → `curl -fsSL https://opencode.ai/install \| bash`.

#### 1.7 curl installers (skip if bin present)

| Tool | Binary | URL |
|------|--------|-----|
| Antigravity | `agy` | `https://antigravity.google/cli/install.sh` |
| Claude Code | `claude` | `https://claude.ai/install.sh` |
| Grok CLI | `grok` | `https://x.ai/cli/install.sh` |
| Kiro | `kiro-cli` | `https://cli.kiro.dev/install` |

If only `kiro` exists → symlink `~/.local/bin/kiro-cli` → `kiro` for MainActivity launchers.

#### 1.8 Verify + exit

- Print version/`--version` for: `node npm opencode codex qwen agy claude grok kiro-cli kiro`  
- Summary: `ok=[…] skip=[…] fail=[…]`  
- Exit **1** only if `node` missing (critical); individual CLI fails are non-fatal to script end of soft tools  

#### 1.9 Issues intentionally deferred

| Item | Notes |
|------|-------|
| Network retry | Still single attempt per tool |
| Multi-arch musl | arm64-centric loader symlink |
| Strict fail-all | Soft per-tool; only node hard-fails |
| MainActivity script blurb | Still may say “Aider, Claude, Cline” — cosmetic |
| Tool version pins | Always latest from npm / install scripts |

---

### 2. OnboardingActivity — fold CLI into main setup

**Location:** `app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt`

#### 2.1 New page map

| Index | Page | Notes |
|------:|------|--------|
| 0 | Intro | unchanged |
| 1 | Slideshow | unchanged |
| 2 | Isolation | proot / chroot |
| 3 | **Environment Setup** | base A–G + **H AI CLIs**; one log |
| 4 | Complete | was index 5 |
| 5 | Complete (legacy) | `target_page=5` still opens Complete |

Removed: dedicated AI CLI Tools page UI (`buildCliSetupPage`, `cliStatusText`, `cliProgressBar`, `cliLog*`, `cliNextBtn`, `isCliToolsSetupStarted`, `updateCliStatus`, old `runCliToolsSetup`).

#### 2.2 UI copy on setup page

- Header: **Environment Setup** (was “Base Environment Setup”)  
- Console title: **`[ SETUP LOG ]`** (was EXTRACTION LOG)  
- Status seed: “Initializing full environment setup (base + AI CLIs)…”  
- Next button: **“Next: Complete Setup”** → `showPage(4)`  
- Enabled only after full pipeline (including H) succeeds enough to finish  

#### 2.3 Proot pipeline (end)

After G (customize or skip):

```text
H. Installing AI CLI tools (NVM, Node, opencode, codex, claude, …)
  → deploy assets/scripts/setup_cli_tools.sh → usr/tmp
  → proot-distro login debian --shared-tmp -- bash /tmp/setup_cli_tools.sh
  → log streams into base console (runShellCommand always appends baseLogText)
  → non-zero: status warn, still continue
Persist linux_method=proot
Full Environment Setup Successful!
Unlock Next
```

Helper: `runCliToolsSetupProot(): Int`

#### 2.4 Chroot pipeline (end)

After G (customize or skip), **before** `finishChrootBaseSetup()`:

```text
runCliToolsSetupChroot {
  copyAndRunInChroot(setup_cli_tools.sh)
  status ok or soft-fail
  finishChrootBaseSetup()  // unlocks Next, saves linux_method=chroot
}
```

Helper: `runCliToolsSetupChroot(onDone: () -> Unit)`

#### 2.5 `runShellCommand` simplification

- Removed `isCliSetup` flag (no second console).  
- Always tee process output to `baseLogText` / `baseLogScroll`.  
- Signature: `runShellCommand(cmd, forceHostSetup = false)`.

#### 2.6 Kotlin launcher env (unchanged, still relevant)

MainActivity still builds tool sessions with explicit:

```text
PATH=…/.local/bin:…/.nvm/versions/node/v26.*/bin:…
NVM_DIR=… ; . nvm.sh
```

So app cards work even if shell rc is wrong. Guest script PATH fixes **interactive** terminal sessions and manual `zsh`/`bash` usage inside the container.

---

## Setup step matrix (final)

| Step | Proot | Chroot | Script / action |
|-----:|-------|--------|-----------------|
| A | dirs | root check | app files / RootShell |
| B | bootstrap extract | `setup_debian13_chroot.sh` | host / root |
| C | host configs | — | `deployScripts` |
| D | `setup_termux.sh` | — | host |
| E | `flux_install` + family | `setup_debian_family.sh` | guest |
| F | HW accel (`FLUX_GPU` from `GpuAccelDetector`) | same | `setup_hw_accel_debian.sh` |
| G | optional `setup_customization_debian.sh` | same | guest |
| **H** | **`setup_cli_tools.sh`** | **same** | **guest as root → flux** |
| Done | unlock Next → Complete | same | prefs `linux_method` |

Order note: **H after G** so customization’s full `.zshrc` write happens first; CLI script then injects markers into `.zshenv`/`.zshrc` and re-applies after curl installers.

---

## Same-session related work (context)

Not the focus of this doc’s code section, but done in the same workstream:

| Work | Doc / location |
|------|----------------|
| GPU vendor detect → Turnip vs VirGL | `docs/plan/gpu-accel-vendor-detect-turnip-virgl.md` |
| `GpuAccelDetector.kt` | Android Build/props/KGSL → `FLUX_GPU` |
| Turnip/Mesa pin **26.2.0-devel-20260709** | curl URLs in guest only (no APK package of turnip tarballs) |
| `start_gui.sh` mode-aware | turnip / virgl / soft |
| Local Debian rootfs install path | `docs/plan/proot-debian-rootfs-local-install.md` |

Policy reminder (user): **in proot, use download links directly** — do not ship large driver/tool blobs in APK assets for Turnip or AI CLIs.

---

## Verification plan (device)

### Script-only re-run (guest)

```bash
# inside debian as root
bash /tmp/setup_cli_tools.sh   # first run: installs
bash /tmp/setup_cli_tools.sh   # second: mostly skip=[…]
```

Expect:
- Second run: apt “already present”, node skip, bins skip, PATH re-wire only  
- `su - flux -c 'echo $PATH'` includes `.local/bin` and node bin  
- `zsh -c 'command -v node; command -v opencode'` works (via `.zshenv`)  
- Interactive zsh: same after open terminal  

### Onboarding cold path

1. Clear app data / force onboarding.  
2. Choose proot (and separately chroot if rooted).  
3. On **Environment Setup** page, log must show **A…H** without navigating to a second install page.  
4. Next only enables after H finishes (ok or soft-fail).  
5. Complete page shows Node / AI tools READY rows.  
6. Home AI cards: opencode, codex, claude, agy, grok, kiro-cli launch without immediate exit.  

### Regression

- Customization toggle off: still runs H.  
- GPU step failure (chroot soft / proot hard): unchanged relative to prior policy.  
- Manual Scripts UI: `setup_cli_tools.sh` still deployable.  
- Legacy `target_page=5` opens Complete, not crash.  

---

## Rollback

| Piece | How |
|-------|-----|
| Guest script | Restore previous `setup_cli_tools.sh` from git |
| Onboarding | Restore separate page 4 CLI UI + old indices; remove Step H calls |
| Shell markers | Delete `# flux-cli-tools` blocks and `~/.config/fluxlinux/cli-tools.env` |

---

## Open / follow-ups

1. **Device verify** proot + chroot full onboarding (primary).  
2. Optional: retry loop on curl/npm failures.  
3. Optional: fail hard on H if product wants “all tools or no complete”.  
4. Optional: fix MainActivity script description string (“Aider, Cline” stale).  
5. Optional: write exact Node path to a small state file for launchers (avoid `v26.*` glob fragility).  
6. Optional: unit/instrumentation stubs for page index map.  

---

## Summary

| Before | After |
|--------|--------|
| CLI install always re-downloads | Skip if binary/node major present |
| PATH only half-correct for zsh | `.zshenv` + managed env file + re-wire |
| Separate onboarding page for AI tools | Step **H** end of one Environment Setup |
| Two logs / two Next gates | One setup log → Complete |
| Exit 0 even if node missing | Exit 1 if node missing; soft per-tool otherwise |

**Status:** implementation complete in tree; **device verification open**.
