# GitHub Connect via `gh` CLI (proot & chroot)

**Date:** 2026-07-30  
**Status:** implemented 2026-07-30 · **CLI verified** host `gh 2.96.0` 2026-07-30  

**Scope:** Connect-to-GitHub banner on **Create / Import Project**; install + device-code auth via guest `gh`; repo dropdown populate; Settings Hub GitHub account card. Full support for **proot** and **chroot**.  
**Out of scope (v1):** GitLab/Bitbucket; SSH key generation UI; full PR/issues page (`github_cli_operations` design); PAT paste-as-primary path; host-Termux `gh` (auth lives in **Debian guest** only); multi-account switcher.

**Design SSOT:** `docs/project/ui_design.md` (cyber-brutalist / Obsidian Terminal)  
**Product SSOT:** `docs/project/problem_statement.md` — clone via authenticated GitHub account; GitHub-only  
**Token SSOT:** `app/.../DesignTokens.kt` (`NC.*`)  
**Compile policy:** `:app:compileDebugKotlin` only unless user asks for APK.

**Related existing work:**
- `ProjectManager.cloneRepo` / create UI (`buildProjectCreateLayout`)
- `LinuxCommandBuilder` + `ProotCommandBuilder` / `ChrootCommandBuilder`
- `ShellCommandRunner` (capture / stream)
- `PackageInstallRunner.buildRootExec` (root apt path for proot+chroot)
- Stub `buildAuthCard()` mock on Git Operations page (replace or ignore in v1)
- Icons: `R.drawable.ic_search` (user-requested banner icon), `ic_git` / `ic_git_thick`

**User screenshot ref:** Create / Import Project form (name, icon, optional GitHub URL, isolation chips, create button). Banner goes **above** name card, under sticky title bar.

---

## 0. Goals (user intent → product)

| Goal | Meaning |
|------|---------|
| **Banner CONNECT** | Top of Create/Import: “CONNECT TO GITHUB” + primary button. Icon = **`ic_search`** (explicit user choice; not invent new GitHub mark for v1). |
| **Install `gh` if missing** | Inside **selected isolation** Debian guest: `apt` install as root (`sudo apt install -y gh` / root shell). |
| **Auth if needed** | `gh auth status` → if not logged in, device/web flow: stream OTP, copy to Android clipboard, open system browser, wait until logged in. **Cancel** available whole time. |
| **Repo dropdown** | After login, list user’s repos via `gh`; Spinner/dropdown **above** “GITHUB REPOSITORY URL”; select fills URL field; create/import unchanged. |
| **Settings card** | Settings Hub: show username if logged in, or **CONNECT / AUTH** button if not. Same service for proot + chroot (per-method state). |
| **Decoupled** | No mega-logic in `MainActivity`. New package service + small UI builders. Reuse shell builders / runner / root apt path. |

---

## 1. Problem summary (today)

### 1.1 Create / Import Project

| Surface | Current |
|---------|---------|
| UI | `MainActivity.buildProjectCreateLayout()` — name, icon, optional URL, proot/chroot chips, CTA |
| Clone | `ProjectManager.cloneRepo` → `LinuxCommandBuilder.build(git clone…)` as default guest user **`flux`**, method from chip |
| Auth | None. Public HTTPS only. Private → clone fails |
| Stub | `buildAuthCard()` on Git Operations is **static mock** (“ABCD-1234”, empty Open Browser) — not wired |

### 1.2 Shell reality (must drive design)

| Mode | Guest rootfs | Non-root guest cmd | Root guest cmd (apt) |
|------|--------------|--------------------|----------------------|
| **proot** | `filesDir/usr/var/lib/proot-distro/containers/debian/rootfs` | `ProotCommandBuilder` → `proot-distro login debian --user flux -- zsh -c '…'` | Prefer **`PackageInstallRunner.buildRootExec`**: `login … --user root -- /bin/bash -lc '…'` |
| **chroot** | `/data/local/tmp/chrootDebian13` | `ChrootCommandBuilder.build(…, user=flux)` via `su` + busybox chroot | Same builder with `user=root` (or `run_debian13_root.sh` path) |

**Critical:** proot and chroot are **separate rootfs**.  
`gh` binary + `~/.config/gh` tokens are **per method**. Never assume proot login implies chroot login.

**Active method on Create page** = `projectCreateSelectedMethod` (chip), **not** necessarily global `LinuxCommandBuilder.currentMethod` until create runs.  
**Settings** = global `LinuxCommandBuilder.currentMethod` (badge PROOT|CHROOT).

### 1.3 What is already installed

| Package | Onboarding |
|---------|------------|
| `git` | Yes (`setup_debian_family`, chroot setup, `setup_cli_tools`) |
| **`gh`** | **No** — must install on demand |

Debian may or may not ship `gh` in default apt sources (depends on image). Plan must support **primary** `apt-get install -y gh` and **fallback** official GitHub CLI apt repo or `.deb` if package missing.

### 1.4 Gaps in runners

| API | Gap for this feature |
|-----|----------------------|
| `ShellCommandRunner.runStreamed` | No cancel handle; no exit-code+stdout pair helper |
| `runCapture` | Blocking; OK on bg thread; no cancel |
| Auth process | Long-lived; needs **kill on Cancel** |

---

## 2. Architecture

```text
┌──────────────────────────────────────────────────────────────────┐
│ UI (MainActivity — thin)                                         │
│  Create page: banner + repo spinner + existing URL field         │
│  Settings Hub: GitHub account card (status / connect / logout?)  │
│  Auth overlay: status log + OTP + Open browser + CANCEL          │
└─────────────────────────────┬────────────────────────────────────┘
                              │ method: "proot"|"chroot"
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ com.zenithblue.nativecode.github (NEW, decoupled)                    │
│  GitHubCliService      — SSOT state machine + public API         │
│  GhGuestCommands       — pure command strings (no Android UI)    │
│  GhAuthSession         — one cancelable auth run (Process)       │
│  GhModels              — AuthStatus, GhRepo, AuthPhase           │
└─────────────────────────────┬────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
 LinuxCommandBuilder    PackageInstallRunner   ShellCommandRunner
  (flux: status,        .buildRootExec         (+ cancelable stream)
   auth, repo list)      (root: apt install)
```

**Do not** put apt / parse / clipboard / browser inside one 500-line MainActivity block.

### 2.1 Method parameter is mandatory

Every service call:

```kotlin
fun status(ctx: Context, method: String): AuthStatus
fun ensureGhInstalled(ctx: Context, method: String, onLine, onDone)
fun startAuth(ctx: Context, method: String, callbacks): GhAuthSession
fun listRepos(ctx: Context, method: String): List<GhRepo>
fun logout(ctx: Context, method: String) // optional v1
```

Create page passes `projectCreateSelectedMethod`.  
Settings passes `LinuxCommandBuilder.currentMethod`.  
Chip change on Create → re-probe status + hide/show dropdown for that method.

### 2.2 User identity inside guest

| Operation | Guest user | Why |
|-----------|------------|-----|
| `apt-get install gh` | **root** | Needs package manager |
| `gh auth login / status / repo list` | **flux** | Clones run as flux; credentials must be in flux home |
| `git clone` (existing) | **flux** | Unchanged `ProjectManager` |

After `gh auth login` as flux, `gh` installs git credential helper for that user → private HTTPS clones work without PAT field.

**Do not** auth only as root then clone as flux (token invisible to flux).

---

## 3. `gh` command contract (guest)

**Verified on host PC:** `gh version 2.96.0 (2026-07-02)` — flags below match `gh --help` / subcommand help on this machine. Guest Debian `gh` should be same major CLI; treat minor flag gaps as install-newer-gh.

### 3.0 Official command map (host-verified)

| Need | Command | Key flags / notes |
|------|---------|-------------------|
| Top-level | `gh` | `auth`, `repo`, `api`, … |
| Login | `gh auth login` | **`-w/--web`**, **`-c/--clipboard`**, `-p/--git-protocol {ssh\|https}`, `-h/--hostname`, `--insecure-storage`, `--with-token`, `-s/--scopes` |
| Status | `gh auth status` | `-h`, `-a/--active`, **`--json hosts`**, `--jq`, `-t/--show-token`. Exit **1** if auth issues (unless `--json`, then exit 0 unless fatal) |
| Logout | `gh auth logout` | `-h/--hostname`, `-u/--user` — **no `-y`**. Non-interactive: pass both host + user |
| Git helper | `gh auth setup-git` | After login so `git clone` HTTPS uses `gh` credentials |
| Token print | `gh auth token` | Debug only; never log token in UI |
| Repos | `gh repo list [<owner>]` | alias `gh repo ls`; **`-L/--limit`** (default **30**); `--json` fields incl. `nameWithOwner`, `url`, `isPrivate`, `description`, `updatedAt` |
| Whoami | `gh api user -q .login` | or parse status JSON |

**Documented login example (exact from help):**

```bash
gh auth login --web --clipboard
# help text: "Open a browser to authenticate and copy one-time OAuth code to clipboard"
```

### 3.1 Detect installed

```bash
command -v gh >/dev/null 2>&1 && gh --version
```

### 3.2 Install (root)

Primary:

```bash
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y gh
```

Fallback if `Unable to locate package gh` (order):

1. Official GitHub CLI apt source (arm64 Debian style keyring + list) then `apt-get install -y gh`
2. Or download latest `gh_*_linux_arm64.deb` from GitHub releases + `dpkg -i` + `apt-get install -f -y`

Log every line to auth overlay / toast on hard fail.

Reuse root exec:

```kotlin
val (args, env) = PackageInstallRunner.buildRootExec(ctx, guestCmd, method)
ShellCommandRunner.runStreamed(ctx, args, env, onLine, onDone)
```

### 3.3 Auth status (flux)

**Human (fail fast):**

```bash
gh auth status --hostname github.com --active 2>&1
# exit 0 = ok; exit 1 = not logged in / broken
```

**Machine-parse (preferred for app):**

```bash
gh auth status --hostname github.com --json hosts 2>&1
# With --json: exit 0 even if unauthenticated (unless fatal).
# Parse hosts["github.com"][] for active + state + login
```

Host-verified shape (example when logged in):

```json
{
  "hosts": {
    "github.com": [
      {
        "state": "success",
        "active": true,
        "host": "github.com",
        "login": "abhay-byte",
        "tokenSource": "/home/.../.config/gh/hosts.yml",
        "scopes": "gist, read:org, repo, workflow",
        "gitProtocol": "https"
      }
    ]
  }
}
```

App rules:

| Condition | Result |
|-----------|--------|
| `hosts` missing / empty / no `github.com` | not logged in |
| any entry `active==true` && `state=="success"` | logged in; `username = login` |
| `state` not success | treat as not logged in / offer re-auth |

Optional double-check:

```bash
gh api user -q .login
```

### 3.4 Login (flux) — web + clipboard (host-verified)

**Canonical guest command (v1):**

```bash
export GH_PROMPT_DISABLED=1
export BROWSER=true
# GH_BROWSER also works (precedence: GH_BROWSER then BROWSER)
# Android guest often has no secret service → prefer plain file store:
gh auth login \
  --hostname github.com \
  --git-protocol https \
  --web \
  --clipboard \
  --insecure-storage \
  2>&1
```

| Flag | Why |
|------|-----|
| `--web` / `-w` | OAuth device / browser flow (default mode is web-based) |
| `--clipboard` / `-c` | gh tries to copy OTP; **app still parses stdout** — guest clipboard ≠ Android clipboard |
| `--git-protocol https` / `-p https` | Match `git clone https://…` used by `ProjectManager` |
| `--hostname github.com` | Skip host prompt |
| `--insecure-storage` | Guest may lack keyring; token → plain file under `~/.config/gh` (flux home) |
| `GH_PROMPT_DISABLED=1` | Kill interactive menus under ProcessBuilder |
| `BROWSER=true` (or `:` ) | Prevent guest `xdg-open` hang; **app** opens system browser |

**After successful login, always:**

```bash
gh auth setup-git --hostname github.com
```

So HTTPS `git clone` uses gh credential helper for flux (private repos).

**App side while process runs:**

1. Stream stdout/stderr to overlay log.
2. Regex OTP: `\b([A-Z0-9]{4}-[A-Z0-9]{4})\b` (device code). Prefer lines mentioning code / clipboard.
3. Copy to **Android** `ClipboardManager` (do not trust guest `--clipboard` alone).
4. `Intent.ACTION_VIEW` → `https://github.com/login/device` (append `?user_code=` if present in output).
5. Keep process until exit 0, Cancel, or timeout (~10 min).
6. On exit 0 → `setup-git` → `auth status --json hosts` → success UI.

**Cancel:** `process.destroyForcibly()` via cancelable runner.

**Alt (not primary):** `--with-token` stdin PAT — out of scope v1 unless web fails on device.

### 3.5 List repos (flux)

```bash
# default limit is 30 — always set -L
gh repo list --limit 100 \
  --json nameWithOwner,url,isPrivate,description,updatedAt
```

Host-verified item shape:

```json
[{
  "description": "...",
  "isPrivate": false,
  "nameWithOwner": "abhay-byte/nativecode-marketplace",
  "updatedAt": "2026-07-30T08:26:48Z",
  "url": "https://github.com/abhay-byte/nativecode-marketplace"
}]
```

- Dropdown label: `nameWithOwner` (+ private marker if `isPrivate`).
- On select → set URL field to `url` (HTTPS; `git clone` accepts without `.git`).
- Optional later: `gh repo list <org>` for org repos; v1 = authenticated user only (omit owner arg).

### 3.6 Logout (Settings)

```bash
# Non-interactive: BOTH flags required (no -y in 2.96)
gh auth logout --hostname github.com --user "$USERNAME"
```

If username unknown, probe status JSON first. Revoke on github.com is user-manual (gh does not revoke remote token).

### 3.7 Env vars that matter (from `gh help environment`)

| Var | Use in guest |
|-----|----------------|
| `GH_PROMPT_DISABLED` | non-interactive |
| `BROWSER` / `GH_BROWSER` | stub so app owns browser open |
| `GH_CONFIG_DIR` | optional override; default `$HOME/.config/gh` |
| `GH_TOKEN` / `GITHUB_TOKEN` | **do not set** during normal login (would short-circuit device flow) |
| `GH_HOST` | optional force github.com |
| `NO_COLOR=1` | cleaner log parse |
| `GH_SPINNER_DISABLED=1` | less ANSI noise in stream |

Config path per method: flux home under that rootfs → proot vs chroot stay isolated automatically.

## 4. UI surfaces

### 4.1 Create / Import Project — banner (new card 0)

**Placement:** first child of `projectCreateLayout` (above PROJECT NAME). Sticky topBar unchanged.

```text
┌─────────────────────────────────────────────┐
│ [ic_search]  CONNECT TO GITHUB              │
│              Not connected · PROOT          │
│                         [ CONNECT ]         │
└─────────────────────────────────────────────┘
```

| State | Subtitle | Button |
|-------|----------|--------|
| Checking… | “Checking gh…” | disabled |
| No gh / not auth | “Not connected · {METHOD}” | **CONNECT** |
| Auth in progress | “Waiting for browser…” | opens overlay if dismissed |
| Logged in | `@{username} · {METHOD}` | **REFRESH** or hide CONNECT; optional **DISCONNECT** later |
| Error | short error | **RETRY** |

**CONNECT** → launch auth flow for **current chip method** (if chroot selected but rootfs missing → toast + force proot already exists; if chroot + no root → reuse existing chip gate).

After success return to same page (overlay dismiss); do **not** leave Create.

### 4.2 Repo dropdown (above URL card)

Only visible when `status.loggedIn` for selected method.

```text
┌─────────────────────────────────────────────┐
│ YOUR REPOSITORIES                           │
│ [ Spinner: Select a repository…          ▼] │
│   owner/app-one                             │
│   owner/lib-two  (private)                  │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│ GITHUB REPOSITORY URL (OPTIONAL)            │
│ [ https://github.com/...                  ] │
└─────────────────────────────────────────────┘
```

- Selecting spinner → fill URL (+ optional auto-fill **PROJECT NAME** from repo short name if name still default/empty — **optional**, document as UX nicety; default: fill URL only).  
- Manual URL still allowed (public clone without login).  
- Method chip change → clear spinner adapter if method differs; re-fetch if already logged in on new method.

### 4.3 Auth overlay (full-screen, cancelable)

Reuse patterns from **clone progress overlay** in create flow (log scroll, status bar, header).

| Element | Behavior |
|---------|----------|
| Title | `GitHub auth · {method}` |
| Phase line | Installing gh… / Checking status… / One-time code / Waiting browser… / Done / Failed |
| Log | streamed lines (mono) |
| OTP row | code badge + “Copied” + **OPEN BROWSER** (re-fire Intent) |
| **CANCEL** | kills session, returns to Create or Settings |
| Success | auto-dismiss 400ms or “DONE” button |

Phases enum:

```text
CHECK_GH → INSTALL_GH (if needed) → CHECK_AUTH →
  (if ok) DONE
  else LOGIN_STREAM → WAIT_BROWSER → VERIFY → DONE | FAILED | CANCELLED
```

### 4.4 Settings Hub — GitHub card

Insert after **Environment** (isolation) or after Marketplace cluster — recommend **after Environment card** so account is near method switch:

```text
… Environment (proot/chroot)
… GitHub Account   ← NEW
… Proot Settings
… Chroot Settings
…
```

Card content:

| State | UI |
|-------|-----|
| Loading | “Checking…” |
| Logged out | “Not signed in · {METHOD}” + **CONNECT WITH GITHUB** |
| Logged in | `@{username}` + green badge LOGGED IN + optional **RE-AUTH** / **LOGOUT** |
| Method note | Small mono line: “Auth is per isolation (proot ≠ chroot)” |

On Environment method switch, re-call `GitHubCliService.status` for new method (no cross-copy of tokens).

**Do not** navigate to a separate page unless overlay needs stack; card can launch same auth overlay as Create.

### 4.5 Git Operations stub

`buildAuthCard()` mock: leave as-is **or** replace later with real status deep-link. **Not** required for this plan’s acceptance. Note tech debt only.

### 4.6 Visual tokens

- Cards: existing `cyberBrutalistBg` / NC surfaces (match Create cards).  
- Primary button: existing `primaryButton`.  
- Icon: `R.drawable.ic_search` tinted `NC.PRIMARY` on banner.  
- Method badge: reuse style from Software Manager PROOT/CHROOT chip if present.

---

## 5. Shell runner extensions (small, reusable)

Add to `ShellCommandRunner` (or sibling `CancelableShellJob`):

```kotlin
class ShellJob(
  val process: Process,
  fun cancel() { process.destroyForcibly() }
)

fun runStreamedCancelable(
  ctx, cmd, envMap,
  onLine, onDone
): ShellJob
```

- Keep existing `runStreamed` for clone (backward compatible).  
- Auth + long apt install use cancelable API.  
- Optional: `runCaptureExit(ctx, cmd, env): Pair<Int, String>` for status parse (exit code + body).

**Proot root install:** keep using `PackageInstallRunner.buildRootExec` — already correct.  
**Flux commands:** `LinuxCommandBuilder.build(ctx, cmd, user = "flux", method = method)`.

Consider adding overload if proot currently wraps flux cmds in `zsh -c` (OK for `gh`). Prefer `/bin/bash -lc` for non-interactive reliability **only if** zsh profile causes issues — test both; default keep builder as-is for consistency with clone.

---

## 6. Service API detail

### 6.1 Models

```kotlin
data class GhAuthStatus(
  val method: String,
  val ghInstalled: Boolean,
  val loggedIn: Boolean,
  val username: String?,
  val raw: String = ""
)

data class GhRepo(
  val nameWithOwner: String,
  val url: String,
  val isPrivate: Boolean,
  val description: String? = null
)

enum class GhAuthPhase {
  IDLE, CHECK_GH, INSTALL_GH, CHECK_AUTH, LOGIN, WAIT_BROWSER, VERIFY, SUCCESS, FAILED, CANCELLED
}
```

### 6.2 GitHubCliService (SSOT)

| Method | Threading | Notes |
|--------|-----------|-------|
| `probeStatus(ctx, method, cb)` | bg → main | installed? + username |
| `ensureInstalled(ctx, method, onLine, onDone)` | stream | root apt |
| `connect(ctx, method, listener): GhAuthSession` | full machine | install→auth→verify |
| `listRepos(ctx, method, cb)` | bg → main | requires login |
| `openDeviceBrowser(ctx, code?)` | main | Intent |
| `copyCode(ctx, code)` | main | clipboard |

Cache last `GhAuthStatus` per method in memory (+ optional prefs mirror `gh_user_proot` / `gh_user_chroot` for fast UI paint; always re-verify with real `gh` before clone-sensitive actions).

### 6.3 GhGuestCommands (pure strings)

Centralize shell snippets so UI never concatenates apt flags. Easy to unit-test strings later.

---

## 7. End-to-end flows

### 7.1 Create page CONNECT (happy path)

```text
User taps CONNECT (method=proot)
  → overlay open, phase CHECK_GH
  → flux: command -v gh  → missing
  → phase INSTALL_GH, root: apt-get install -y gh  → ok
  → phase CHECK_AUTH, flux: gh auth status → not logged in
  → phase LOGIN, flux: gh auth login --web …
  → parse OTP ABCD-1234 → clipboard + show + OPEN BROWSER
  → user completes device login on phone browser
  → process exit 0 → VERIFY username → SUCCESS
  → dismiss overlay; banner shows @user
  → listRepos → fill spinner
  → user picks repo → URL field filled
  → CREATE / IMPORT → existing ProjectManager.cloneRepo (now has credentials)
```

### 7.2 Already logged in

```text
Open Create → probeStatus → banner @user → auto listRepos → spinner ready
```

### 7.3 Cancel mid-auth

```text
CANCEL → destroy process → phase CANCELLED → overlay gone → banner unchanged (not connected)
```

### 7.4 Switch chip proot → chroot

```text
If chroot not installed / no root → existing gates
Else probeStatus(chroot):
  if no gh / not auth → banner “Not connected · CHROOT”, hide spinner
  if auth → load chroot repos (independent list)
```

### 7.5 Settings CONNECT

Same `GitHubCliService.connect` with global method; on success refresh card only.

### 7.6 Clone private without CONNECT

Manual URL still allowed; failure toast unchanged (“Check URL or network”). Banner remains optional path.

---

## 8. Files to add / touch

### 8.1 New

| Path | Role |
|------|------|
| `app/.../github/GitHubCliService.kt` | SSOT orchestration |
| `app/.../github/GhGuestCommands.kt` | Command strings |
| `app/.../github/GhModels.kt` | Data + phases |
| `app/.../github/GhAuthSession.kt` | Cancelable session handle |
| `docs/plan/github-connect-gh-cli-proot-chroot.md` | This plan |

### 8.2 Modify

| Path | Change |
|------|--------|
| `ShellCommandRunner.kt` | Cancelable stream (+ optional exit capture) |
| `MainActivity.kt` | Banner, spinner, overlay, settings card; **thin** glue only |
| (optional) `PackageInstallRunner.kt` | No change if `buildRootExec` reused as-is |

### 8.3 Do **not** invent

- Second root checker (use `RootShell` only where chroot needs root for install mounts — apt install already goes through chroot builder which mounts).  
- Host-side `gh` for auth.  
- New full Activity unless overlay inside contentFrame is insufficient (prefer overlay like clone).

---

## 9. Edge cases & risks

| Risk | Mitigation |
|------|------------|
| `gh` not in Debian mirrors | Fallback GitHub apt repo / arm64 deb |
| Interactive `gh auth login` hangs | Non-interactive flags + `GH_PROMPT_DISABLED`; timeout + cancel |
| Browser never opens from guest | App opens device URL; set `BROWSER=true` or `:` so guest doesn’t block on xdg-open |
| Clipboard flag ignored | App always copies parsed OTP |
| Auth as root, clone as flux | **Always flux for auth** |
| Guest keyring missing | `--insecure-storage` on login; token in `~/.config/gh` |
| `gh auth logout` needs user | Status JSON `login` first; flags `-h` + `-u` (no `-y`) |
| Forgot `setup-git` | Private clone 401 → always run after login |
| proot vs chroot token split | Per-method status; UI badge method |
| SELinux / su fail chroot | Existing chroot root gate; toast “need root for chroot gh install” |
| Network offline | Clear error in overlay; no infinite wait |
| OTP regex false positive | Require context line containing “one-time code” / “enter code” if possible |
| Large repo list | Limit 100; spinner OK |
| Concurrent CONNECT double-tap | Disable button while session active |
| Process leak | Cancel on activity destroy / overlay dismiss |

---

## 10. Implementation phases

### P0 — Foundation (no UI polish)

1. `GhModels` + `GhGuestCommands`  
2. `ShellCommandRunner.runStreamedCancelable`  
3. `GitHubCliService.probeStatus` / `ensureInstalled` / `listRepos` (manual logcat test via temporary debug if needed)  
4. Compile `:app:compileDebugKotlin`

### P1 — Auth session

1. `GhAuthSession` + `connect()` state machine  
2. OTP parse, clipboard, `ACTION_VIEW` device URL  
3. Cancel + timeout  
4. Verify username  

### P2 — Create page UI

1. Banner card + wire CONNECT  
2. Auth overlay (reuse clone overlay patterns)  
3. Spinner above URL; select fills field  
4. Chip-change rebind  

### P3 — Settings card

1. Hub card + status  
2. Same connect overlay  
3. Method-switch refresh  

### P4 — Hardening

1. Install fallback if package missing  
2. Private clone smoke (device) proot + chroot  
3. Caveman QA checklist §12  

---

## 11. Acceptance criteria

| # | Criterion |
|---|-----------|
| A1 | Create page shows CONNECT banner with `ic_search` under title bar |
| A2 | No `gh` → CONNECT installs via root apt in **selected** method |
| A3 | Not logged in → OTP shown, clipboard set, system browser opens device page |
| A4 | User can CANCEL auth; process dies; no half-connected banner |
| A5 | Logged in → username on banner; repo dropdown appears **above** URL |
| A6 | Select repo → URL field = repo HTTPS URL |
| A7 | CREATE still clones via existing `ProjectManager` (public + private after auth) |
| A8 | Settings shows username or CONNECT for **current** method |
| A9 | proot auth does **not** auto-auth chroot (and vice versa) |
| A10 | Logic lives under `github/` package; MainActivity only binds UI |
| A11 | `:app:compileDebugKotlin` clean |

---

## 12. Manual test matrix (device)

| # | Setup | Steps | Expect |
|---|-------|-------|--------|
| T1 | proot, no gh | Create → CONNECT | Install log, then device code, browser |
| T2 | complete T1 | Cancel before browser done | Not logged in |
| T3 | complete auth | Spinner lists repos | Select fills URL; clone private OK |
| T4 | chroot installed + root | Switch chip → CONNECT | Separate install/auth if needed |
| T5 | proot authed, chroot not | Settings method chroot | Card “Not signed in · CHROOT” |
| T6 | offline | CONNECT | Fail with clear error |
| T7 | public URL no auth | Create without CONNECT | Clone still works |
| T8 | rotation / leave app mid-auth | Return | Overlay or recoverable state; no zombie gh |

---

## 13. Open decisions (defaults if no reply)

| Topic | Default |
|-------|---------|
| Icon | `ic_search` as requested (not custom Octocat) |
| Auto-fill project name from repo | **No** (URL only) |
| Logout in Settings v1 | **Yes** — `gh auth logout -h github.com -u <login>` after status parse |
| Max repos | 100 |
| Auth timeout | 10 minutes |
| Install fallback | GitHub official apt source after failed plain apt |
| Replace Git Operations mock card | **No** in v1 |

---

## 14. Non-goals reminder

- Full GitHub PR/Issues UI (`detailed_ui_ux` Page 7)  
- SSH agent / keygen wizard  
- Syncing tokens between proot and chroot  
- Using Android AccountManager / OAuth SDK instead of `gh` (user explicitly wants guest `gh` flow)

---

## 15. Implementation order (when approved)

1. Approve this plan  
2. P0 service + runner  
3. P1 auth  
4. P2 Create UI  
5. P3 Settings  
6. Device test T1–T8  
7. Mark status **implemented** + date in this file header  

**Stop after plan file** until user says implement.
