# AI CLI Tools — Browser Login Hub

**Date:** 2026-07-30  
**Status:** implemented 2026-07-30  
**Scope:** Dedicated Settings page to sign in to every AI CLI from `setup_cli_tools.sh` via **Android browser** (device-code / OAuth URL stream), same UX family as GitHub Connect.  
**Out of scope (v1):** Full TUI remotes for every provider; multi-account switcher; host-Termux tools; writing paid API keys for third-party gateways beyond documented env paths.

**Design SSOT:** `docs/project/ui_design.md` (cyber-brutalist)  
**Token SSOT:** `app/.../DesignTokens.kt` (`NC.*`)  
**Compile:** `:app:compileDebugKotlin`  
**Prior art:** `docs/plan/github-connect-gh-cli-proot-chroot.md` + `com.zenithblue.nativecode.github.*`

**Source tools** (`app/src/main/assets/scripts/setup_cli_tools.sh`):

| Bin | Package / install | Onboarding |
|-----|-------------------|------------|
| `opencode` | npm `opencode-ai` / opencode.ai install | yes |
| `codex` | npm `@openai/codex` | yes (hidden on chroot UI) |
| `qwen` | npm `@qwen-code/qwen-code` | yes |
| `agy` | antigravity.google/cli/install.sh | yes |
| `claude` | claude.ai/install.sh | yes |
| `grok` | x.ai/cli/install.sh | yes |
| `kiro-cli` / `kiro` | cli.kiro.dev/install | yes |

---

## 0. Goals

| Goal | Meaning |
|------|---------|
| **Settings entry** | Hub row: **AI CLI LOGIN** → dedicated page (like REPAIRS / PROOT SETTINGS) |
| **Dedicated page** | List all AI CLIs for **active method** (proot\|chroot); status badge; LOGIN / RE-AUTH / LOGOUT |
| **Browser login** | Prefer non-TUI flows: stream guest login cmd → parse URL + OTP → open Android browser → clipboard → poll until credentials land in guest home |
| **Per isolation** | proot ≠ chroot rootfs; auth files under flux home per method |
| **Cancelable** | Reuse `ShellJob` / session cancel like GH |
| **Decoupled** | Package `com.zenithblue.nativecode.cliauth` — thin UI in `MainActivity` |

---

## 1. Research — how each tool authenticates (browser)

### 1.1 Claude Code (`claude`)

| Item | Detail |
|------|--------|
| Browser | First `claude` or `/login` opens claude.ai OAuth; local callback or paste code |
| Headless-friendly | **`claude setup-token`** — same OAuth in browser, prints long-lived token to stdout (not auto-saved) |
| Store | Linux: `~/.claude/.credentials.json` (0600); env: `CLAUDE_CODE_OAUTH_TOKEN`, `ANTHROPIC_API_KEY` |
| Logout | `/logout` in TUI or delete credentials |
| **v1 strategy** | Stream `claude setup-token`; parse `https://…` → open browser; capture `sk-ant-oat…` / token line → write `~/.claude/.credentials.json` *or* export via `~/.config/fluxlinux/cli-auth.env` + ensure sourced; probe via credentials file or short `claude -p` |
| Risk | setup-token is portable env token, not full credentials.json OAuth pair; still valid for CLI use per docs |

### 1.2 OpenAI Codex (`codex`)

| Item | Detail |
|------|--------|
| Browser | ChatGPT OAuth (`codex login`) or **device code** |
| Headless | **`codex login --device-auth`** → URL `https://auth.openai.com/codex/device` + user code |
| Status | `codex login status` |
| Logout | `codex logout` |
| **v1 strategy** | **Best match to GH.** Stream device-auth; OTP + Open Browser; poll `codex login status` until authenticated |
| Note | Device auth may need ChatGPT workspace setting “Device code login” enabled |

### 1.3 Qwen Code (`qwen`)

| Item | Detail |
|------|--------|
| Browser OAuth | **Discontinued** free Qwen OAuth (2026-04-15) |
| Current | Interactive `/auth` → Alibaba ModelStudio / API keys; headless via `settings.json` + env (`BAILIAN_CODING_PLAN_API_KEY`, etc.) |
| **v1 strategy** | **Not pure browser.** Card: OPEN DOCS (bailian console) + paste API key → write `~/.qwen/settings.json` + env; status = key present / modelProviders set |
| UX | Label: “API KEY (browser for key only)” |

### 1.4 OpenCode (`opencode`)

| Item | Detail |
|------|--------|
| Browser | Provider-dependent: `opencode auth login` / TUI `/connect` — API keys + some OAuth (e.g. GitHub Copilot, ChatGPT plugins) |
| Store | `~/.local/share/opencode/auth.json` |
| List | `opencode auth list` / `ls` |
| Logout | `opencode auth logout` |
| **v1 strategy** | Stream `opencode auth login` if URL printed; else **guided terminal session** + post-check `opencode auth list`. Secondary: paste API key for common providers (Anthropic/OpenAI) into auth.json via docs shape if stable |

### 1.5 Antigravity (`agy`)

| Item | Detail |
|------|--------|
| Browser | First run / Google OAuth; may require paste code back into CLI |
| Store | OS keyring (libsecret) — **fragile in proot**; file fallback under `~/.gemini/antigravity-cli/` |
| **v1 strategy** | Stream interactive login; open URL; if “paste code” appears, show OTP input on Android → write to process stdin; document keyring deps; probe version + token files |
| Risk | Keyring may not persist in guest — status may be false-negative |

### 1.6 Grok Build (`grok`)

| Item | Detail |
|------|--------|
| Browser | First `grok` launch opens xAI / accounts OAuth (SuperGrok / Premium+) |
| Headless | `export XAI_API_KEY=xai-…` |
| **v1 strategy** | Prefer stream first-run if URL printed; else API key paste → write env file; probe credentials / env |
| Store | under `~/.grok/` (vendor-specific) + env |

### 1.7 Kiro CLI (`kiro-cli` / `kiro`)

| Item | Detail |
|------|--------|
| Browser | `kiro-cli login` — local browser or **device code** on remote/headless |
| Alt | `KIRO_API_KEY` skips browser |
| **v1 strategy** | Stream `kiro-cli login` (prefer non-interactive flags if any); parse device URL/code; open browser; poll until logged in; API key fallback |

---

## 2. Architecture

```text
┌─────────────────────────────────────────────────────────────┐
│ Settings Hub                                                 │
│  [ AI CLI LOGIN ]  →  page ID_CLI_AUTH                       │
│  (GitHub card stays separate — already shipped)              │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ CliAuth page (ScrollView)                                    │
│  header: AI CLI TOOLS · method badge PROOT|CHROOT            │
│  card per tool: name · bin · installed · signed-in · actions │
│  LOGIN opens overlay (device/URL) or key dialog or term tab  │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ com.zenithblue.nativecode.cliauth                                │
│  CliToolCatalog     — tool defs + strategies                 │
│  CliAuthModels      — status, phase, session listener        │
│  CliGuestCommands   — pure guest shell strings               │
│  CliAuthService     — probe / login / logout / inject        │
│  CliAuthSession     — cancel + active process                │
└─────────────────────────────┬───────────────────────────────┘
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
 LinuxCommandBuilder    ShellCommandRunner    ProjectPathResolver
 (flux PATH/nvm)        cancelable stream     guest home files
```

### 2.1 Login strategy enum

```kotlin
enum class CliLoginStrategy {
  DEVICE_CODE,      // codex, kiro (when prints code)
  STREAM_URL,       // parse https from stdout, open browser, wait exit/file
  API_KEY_FORM,     // qwen primary; grok/kiro fallback
  TERMINAL_GUIDED   // opencode / agy hard interactive fallback
}
```

### 2.2 Status probe (per tool)

| Tool | Probe |
|------|-------|
| claude | file `~/.claude/.credentials.json` non-empty **or** `CLAUDE_CODE_OAUTH_TOKEN` in env file; optional `command -v claude` |
| codex | `codex login status` parse logged-in |
| qwen | `~/.qwen/settings.json` has key / env `BAILIAN_*` / `DASHSCOPE_*` |
| opencode | `opencode auth list` non-empty / auth.json |
| agy | token path or `agy` health if available |
| grok | `XAI_API_KEY` set **or** `~/.grok` auth files |
| kiro | `kiro-cli` status if any; else `KIRO_API_KEY` / known token path |

Always: `command -v <bin>` with nvm PATH (same as `setup_cli_tools` / `flux_has`).

### 2.3 Auth overlay (shared with GH UX)

Reuse GH overlay pattern (not same class — keep packages clean):

- Phase label, scroll log, OTP chip, OPEN BROWSER, CANCEL  
- `CliAuthService.openBrowser(url)` via `Intent.ACTION_VIEW`  
- Clipboard OTP  

### 2.4 Env injection file

Write `~/.config/fluxlinux/cli-auth.env` (flux-owned, 0600) for portable tokens:

```bash
# Managed by CliAuthService — do not commit
export CLAUDE_CODE_OAUTH_TOKEN='…'
export XAI_API_KEY='…'
export KIRO_API_KEY='…'
export BAILIAN_CODING_PLAN_API_KEY='…'
```

Source from existing `cli-tools.env` block or append marker in `setup_cli_tools` **only if missing** — v1 can `source` from probe wrapper:

```bash
[ -f "$HOME/.config/fluxlinux/cli-auth.env" ] && . "$HOME/.config/fluxlinux/cli-auth.env"
```

Optional small patch to `setup_cli_tools.sh` `cli-tools.env` to always source `cli-auth.env`.

---

## 3. UI spec

### 3.1 Settings hub button

- Title: `AI CLI LOGIN`  
- Sub: `Browser / device-code sign-in for coding agents`  
- Icon: existing AI / build icon if no dedicated mark  
- Position: after **GitHub Account** card, before Proot Settings  

### 3.2 Page layout

```
┌──────────────────────────────────────┐
│ ← AI CLI TOOLS          [PROOT]      │
│ Sign in via Android browser          │
│ Auth is per isolation (proot≠chroot) │
├──────────────────────────────────────┤
│ ┌ Claude Code          [INSTALLED]  │
│ │ claude · SIGNED IN · user@…        │
│ │ [RE-AUTH] [LOGOUT]                 │
│ └────────────────────────────────────│
│ ┌ Codex                [MISSING]     │
│ │ codex · NOT LOGGED IN              │
│ │ [LOGIN]                            │
│ └────────────────────────────────────│
│ … opencode, qwen, agy, grok, kiro    │
└──────────────────────────────────────┘
```

- Chroot: hide **codex** card (match terminal selector).  
- Missing binary: LOGIN disabled + hint “Run onboarding CLI tools” / open REPAIRS.  
- Refresh on page show + after login/logout.

### 3.3 Login flows by strategy

| Strategy | UI |
|----------|-----|
| DEVICE_CODE | Overlay: stream log, OTP, OPEN BROWSER, CANCEL |
| STREAM_URL | Same; URL open; wait process / file probe |
| API_KEY_FORM | Dialog: masked EditText + OPEN CONSOLE (browser) + SAVE |
| TERMINAL_GUIDED | Toast + `createNewTerminalSession(type)` with login command if we can; else open shell with pretyped command |

---

## 4. Implementation steps

1. **Models + catalog** — `CliToolId`, strategies, display names, bins, credential paths  
2. **CliGuestCommands** — PATH/nvm bootstrap + status/login/logout strings per tool  
3. **CliAuthService** — probe all, login session state machine, file writes to guest home, browser/clipboard helpers  
4. **MainActivity**  
   - `ID_CLI_AUTH` page id + stack/back  
   - `buildCliAuthPage()` / section button  
   - Overlay + API key dialog  
5. **setup_cli_tools.sh** (small) — source `cli-auth.env` from `cli-tools.env`  
6. **Compile** `:app:compileDebugKotlin`  

---

## 5. Risk / reality matrix

| Tool | Browser quality on Android guest | Confidence |
|------|----------------------------------|------------|
| codex | High (device-auth) | ★★★★★ |
| kiro | High if device-code prints | ★★★★ |
| claude | Medium (setup-token stream) | ★★★★ |
| grok | Medium (URL stream or API key) | ★★★ |
| agy | Medium–low (keyring + paste) | ★★ |
| opencode | Medium–low (interactive providers) | ★★ |
| qwen | API key only (no usable OAuth) | ★★★ (form) |

Never invent OAuth `client_id` reverse-engineering for vendors (unlike GH which documents public gh device flow). Prefer vendor CLI output + documented env files.

---

## 6. Test plan (device)

| # | Case |
|---|------|
| T1 | Settings → AI CLI LOGIN opens page; method badge matches global |
| T2 | Proot: all tools listed; installed badges match PATH |
| T3 | Codex LOGIN: code + browser; status flips SIGNED IN |
| T4 | Claude LOGIN: browser; token/credentials; re-probe OK |
| T5 | Qwen: paste key; settings.json written; SIGNED IN |
| T6 | Logout clears probe |
| T7 | Cancel mid-login kills job |
| T8 | Chroot: no codex card; other tools independent of proot |
| T9 | Missing binary: LOGIN disabled |

---

## 7. Files

| Path | Action |
|------|--------|
| `docs/plan/ai-cli-tools-browser-login.md` | this plan |
| `app/.../cliauth/CliToolCatalog.kt` | NEW |
| `app/.../cliauth/CliAuthModels.kt` | NEW |
| `app/.../cliauth/CliGuestCommands.kt` | NEW |
| `app/.../cliauth/CliAuthSession.kt` | NEW |
| `app/.../cliauth/CliAuthService.kt` | NEW |
| `app/.../MainActivity.kt` | page + hub button + overlay |
| `app/src/main/assets/scripts/setup_cli_tools.sh` | source cli-auth.env |

---

## 8. Done when

- [x] Plan checked in  
- [x] Hub button + dedicated page  
- [x] All 7 tools listed with probe  
- [x] codex device-auth + claude setup-token + qwen API key paths work  
- [x] Remaining tools: best-effort stream / terminal guided / API key  
- [x] `:app:compileDebugKotlin` green (2026-07-30)  

