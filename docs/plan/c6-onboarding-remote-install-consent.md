# C6 — Onboarding install inventory + consent + AI tools gate (proot & chroot)

**Date:** 2026-07-31  
**Status:** implemented (2026-07-31) — verify on device §13  
**Policy:** Play Device & Network Abuse / external & interpreted code (§4.7); checklist **C6**  
**Out of scope this plan:** **C5** (AI report UI) — deferred  
**Related:**  
- `docs/policy/Google_Play_Store_Policy_Compliance_Checklist.md` (C6, B11)  
- `docs/plan/onboarding-cli-tools-idempotent-main-setup.md` (step H folded into main setup)  
- `docs/plan/chroot-onboarding-chain-like-proot.md` (E→H parity)  
- `docs/plan/terminal-workspace-tool-sections.md` (Free / Paid / Debian Shell sections)  
- `docs/plan/terminal-debian-shell-rooted.md` (shell + shell-root session types)  
- `docs/plan/ai-cli-tools-browser-login.md` (Settings → AI CLI LOGIN)  
- `docs/project/ui_design.md` (cyber-brutalist)  
- `docs/privacy-policy.md`  

**Primary code:**  
| Path | Role |
|------|------|
| `OnboardingActivity.kt` | Pages, phase lists, proot/chroot chains, step H gate, prefs |
| `MainActivity.kt` | Terminal tool selector, workspace hub tools, Settings AI CLI page, Scripts |
| `cliauth/CliToolCatalog.kt` | AI tool defs + `terminalType` keys |
| `cliauth/CliAuthService.kt` | Probe installed/logged-in per tool/method |
| `cliauth/CliAuthModels.kt` | `CliToolStatus` |
| `assets/scripts/setup_cli_tools.sh` | Guest AI CLI remote installers |
| `setup_debian_family.sh` / `setup_hw_accel_debian.sh` / `setup_customization_debian.sh` | Base guest chain |
| `flux_install.sh` / `chroot/setup_debian13_chroot.sh` | Rootfs (asset / download + SHA) |
| `LinuxCommandBuilder` / `ChrootCommandBuilder` | Session command build for tool types |

---

## 1. Goal

### 1.1 Onboarding (C6 core)

1. **Visible before run** — inventory of what will be installed  
2. **Explicit agree** — checkbox + CTA, not silent auto  
3. **AI remotes optional** — default **OFF**; step **H** only if opted in  
4. **Same consent UX** for proot and chroot  
5. **Honest copy** — guest Debian only; not host APK update  

### 1.2 Post-onboarding product (new — required)

6. If AI CLIs **not** installed from onboarding (or never provisioned):  
   - **Settings → AI tools (CLI auth hub):** show **Install AI CLI tools** action (consent + run `setup_cli_tools.sh` for current `linux_method`)  
   - **Terminal page** tool picker: show **only** Debian Shell + Debian Shell Rooted  
   - **Project workspace hub** tool picker: same — **only** those two shells  
7. Applies to **both proot and chroot** (codex hide-on-chroot rule still applies once AI tools shown)  
8. After successful install: set provisioned flag, **rebuild** terminal + workspace selectors, refresh AI settings cards  

### Success definition

| Before | After |
|--------|--------|
| Onboarding always runs step H | H only if AI opt-in on plan page |
| No install inventory | Plan page lists base / custom / AI |
| Terminal always shows Free + Paid + Shell | If AI not provisioned → **Shell section only** |
| Workspace same full AI grid | If AI not provisioned → **Shell only** |
| AI settings only login/logout probes | + **Install AI tools** when not provisioned |
| Complete page hardcodes AI READY | Truthful READY / SKIPPED / PARTIAL |

**Does not claim:** Device & Network Abuse N/A because virtual guest. Guest isolation is **framing + mitigation**, not exemption.

---

## 2. Non-goals

| Out | Why |
|-----|-----|
| C5 AI report UI | Deferred |
| Removing AI forever | Optional install path remains |
| Host remote dex/so | Already forbidden |
| Marketplace confirm UX | Follow-up C6.1 |
| Play vs sideload split (S2) | Single APK S1+S3 |
| Per-tool selective install UI v1 | One script installs the suite; v1 is all-or-nothing provision |
| Hiding shell tools when AI missing | Shells always available |

---

## 3. Assumptions (user-confirmed)

1. No remote code into host process (`com.zenithblue.nativecode`)  
2. Integrity/pin for controlled downloads (rootfs SHA, etc.)  
3. Listing + reviewer notes match behavior (C8 ops)  

---

## 4. Current code state (audit)

### 4.1 Onboarding pages

| Index | Page |
|------:|------|
| 0 | Privacy |
| 1 | Intro |
| 2 | Slideshow |
| 3 | Requirements |
| 4 | Isolation (proot/chroot; customization toggle present but **disabled** in UI) |
| 5 | Environment Setup — auto `runDebianBaseSetup()` including **H always** |
| 6–7 | Complete — hardcodes Node/AI READY |

### 4.2 Chains

**Proot phases:** A–H (`prootSetupPhases`) — H = `runCliToolsSetupProot()` → guest `setup_cli_tools.sh`  

**Chroot phases:** R0, R1, E, F, G, H — H = `runCliToolsSetupChroot`  

**H always runs** today (soft-fail continue).

### 4.3 Terminal tool selector (`MainActivity.buildTerminalToolSelectorView`)

SSOT sections (see `docs/plan/terminal-workspace-tool-sections.md`):

| Order | Section | Types |
|------:|---------|-------|
| 1 | FREE CLI TOOLS | `opencode` |
| 2 | PAID CLI TOOLS | `codex`*, `agy`, `claude-code`, `qwen-code`, `grok`, `kiro` |
| 3 | DEBIAN SHELL | `shell` (flux), `shell-root` (root) |

\* `codex` filtered out when `LinuxCommandBuilder.currentMethod == "chroot"`.

`refreshTerminalToolSelector()` rebuilds on method change — **no AI-installed gate today**.

### 4.4 Workspace hub (`populateWorkspaceHubTools`)

Same Free / Paid / Shell lists and chroot codex filter.  
`refreshWorkspaceHubTools()` rebuilds host — **no AI-installed gate**.

Also: project “open with tool” lists around session create (~`Pair("Debian Shell", "shell")` …) — **must gate the same way** (any surface that offers AI tool types).

### 4.5 Settings AI tools

| Piece | Today |
|-------|--------|
| Hub card | `buildCliAuthSectionButton()` → `ID_CLI_AUTH` |
| Page | `buildCliAuthPage()` + `refreshCliAuthPage` / `paintCliAuthList` |
| Cards | `buildCliToolCard` — INSTALLED/MISSING badge, login, logout, WEB |
| Comment in code | “Install is mandatory (setup_cli_tools); never block login on probe flake” |
| Install entry | **None** on AI page — only Scripts list has `setup_cli_tools.sh` |

`CliAuthService.probeAll` already reports `installed` per tool via bin probe in guest.

### 4.6 Guest marker (script)

`setup_cli_tools.sh` uses `MARKER="flux-cli-tools"` in shell rc and writes  
`/home/flux/.config/fluxlinux/cli-tools.env` — **not** a host-side “suite installed” pref.  
App has `filesDir/setup_complete` for full onboarding only.

→ Plan introduces explicit **host prefs** for suite provision state (see §5).

---

## 5. Provisioned flag (SSOT)

### 5.1 Prefs (`nativecode_prefs`)

| Key | Type | Meaning |
|-----|------|---------|
| `enable_ai_cli_install` | bool | User chose AI during onboarding plan (intent) |
| `install_plan_accepted` | bool | Plan checkbox accepted |
| **`ai_cli_tools_provisioned`** | **bool** | **Suite install completed at least once for current guest** (success-ish) |
| `ai_cli_tools_provisioned_method` | string | `proot` \| `chroot` — method when last provisioned |
| `enable_debian_customization` | bool | Optional mirror |

### 5.2 When to set `ai_cli_tools_provisioned = true`

| Event | Set true? |
|-------|-----------|
| Onboarding H finishes exit 0 | Yes |
| Onboarding H soft-fail (non-zero but ran) | **Partial:** set true only if probe finds **node** or ≥1 AI bin; else leave false |
| Settings Install AI success (exit 0 or probe-ok) | Yes |
| Scripts `setup_cli_tools.sh` success | Yes (same helper) |
| Onboarding AI skipped | **false** (explicit) |

### 5.3 When to set false / re-evaluate

| Event | Action |
|-------|--------|
| User uninstalls guest / wipes rootfs (proot or chroot) | Clear provisioned flag for that method |
| Method switch proot↔chroot | Gate uses **current method**: if `provisioned_method != current` → treat as **not provisioned** for UI until install or successful probe on that method |
| Cold start (optional) | If flag true, optional background probe; if **zero** tools installed, flip false and refresh UI |

### 5.4 Helper API (new)

Prefer small object (e.g. `AiCliProvisionState` in `cliauth/` or next to catalog):

```text
fun isAiCliSuiteAvailable(ctx): Boolean
  // true iff prefs say provisioned for LinuxCommandBuilder.currentMethod
  // OR (optional fallback) any CliToolCatalog tool installed via last probe cache

fun markAiCliProvisioned(ctx, method, ok: Boolean)
fun clearAiCliProvisioned(ctx, method?)

fun shouldShowAiToolLaunchers(ctx): Boolean = isAiCliSuiteAvailable(ctx)
```

**UI gate rule (product requirement):**

```text
shouldShowAiToolLaunchers == false
  → Terminal + Workspace (+ any open-with AI lists): ONLY shell + shell-root
shouldShowAiToolLaunchers == true
  → Full Free + Paid + Shell (existing), chroot still hides codex
```

Do **not** hide shells when AI missing.

---

## 6. Onboarding UX (unchanged intent from prior plan)

### 6.1 Page map

| Index | Page |
|------:|------|
| 0–3 | Privacy → Intro → Slideshow → Requirements |
| 4 | Isolation (method only; drop disabled custom toggle) |
| **5** | **Install plan + consent** (NEW) |
| **6** | Environment Setup (phases depend on flags) |
| **7** | Complete (truthful summary) |

### 6.2 Plan page sections

- **A Base** (required) — method-aware inventory  
- **B Customization** toggle (canonical)  
- **C AI CLI tools** toggle **default OFF** + tool table when on  
- Checkbox: reviewed plan  
- CTA: `I understand — start install`  

### 6.3 Chain gates

```text
after G (or F if no custom):
  if (enableAiCliInstall) {
    run H (proot or chroot)
    markAiCliProvisioned(method, success criteria §5.2)
  } else {
    log skip H
    markAiCliProvisioned false (or leave false)
  }
  finish setup
```

Phases without AI: omit H; reweight progress.

### 6.4 Complete page

Reflect SKIPPED / READY / PARTIAL for AI; do not claim AI harness if skipped.

---

## 7. Settings → AI tools: Install option (new)

### 7.1 Where

**Primary:** `ID_CLI_AUTH` page (`buildCliAuthPage`) — Settings Hub → existing AI CLI LOGIN entry.  
Rename hub subtitle if needed: **AI CLI tools** (login + install), keep cyber-brutalist card.

### 7.2 When AI suite not available (`!shouldShowAiToolLaunchers`)

Show top **Install card** above probe list:

| Element | Content |
|---------|---------|
| Title | Install AI CLI tools |
| Body | Runs `setup_cli_tools.sh` in **current** guest (proot/chroot). Network; third-party curl/npm installers. Guest-only. Not required for Debian shells. |
| Inventory | Short list from same catalog as onboarding AI table |
| Primary | `Install AI tools` |
| Secondary | optional `Learn more` → privacy / vendor note |

**On Install tap:**

1. Confirm dialog (same warning as onboarding AI section; checkbox optional if dialog is explicit)  
2. Disable button; show progress (Toast + optional inline status / log sheet)  
3. Run install for **current method**:  
   - **proot:** same path as onboarding `runCliToolsSetupProot` (extract to helper shared by Onboarding + MainActivity — **must not duplicate forever**; extract `CliToolsInstaller` or reuse MainActivity Scripts runner)  
   - **chroot:** root `copyAndRunInChroot` / existing Scripts chroot runner for `setup_cli_tools.sh`  
4. On finish: probe tools; `markAiCliProvisioned`; `refreshCliAuthPage(force)`; **`refreshTerminalToolSelector()`**; **`refreshWorkspaceHubTools()`**; Toast result  

### 7.3 When suite already available

- Hide primary Install card **or** show secondary `Re-install / repair AI tools` (idempotent script — OK)  
- Keep existing per-tool INSTALLED/MISSING + login UI  
- Update copy: remove “Install is mandatory” assumption in `buildCliToolCard` state lines when suite missing  

### 7.4 Scripts path

Settings → Repairs/Scripts → `setup_cli_tools.sh` remains.  
**Add same confirm dialog** before run; on success call `markAiCliProvisioned` + refresh selectors (shared completion hook).

---

## 8. Terminal + Project workspace visibility (new)

### 8.1 Terminal — `buildTerminalToolSelectorView`

```text
if (!AiCliProvisionState.shouldShowAiToolLaunchers(this)) {
  // Only section 3
  addSection("DEBIAN SHELL", "// SYSTEM SHELL", shellTools)
  // Optional empty-state banner above shells:
  // "AI CLI tools not installed — Settings → AI CLI tools → Install"
} else {
  addSection FREE
  addSection PAID   // chroot: filter codex
  addSection DEBIAN SHELL
}
```

Banner (when AI hidden): secondary cyber-brutalist strip; tap → navigate `ID_CLI_AUTH` (push stack).

### 8.2 Workspace hub — `populateWorkspaceHubTools`

**Identical gate** as terminal (shared helper to avoid drift):

```text
fun terminalShellTools(): List<...>
fun terminalFreeTools(method): List<...>
fun terminalPaidTools(method): List<...>
fun applyAiVisibility(showAi): sections
```

Refactor recommended: one `ToolLauncherCatalog` used by both builders.

### 8.3 Other launch surfaces (must audit in impl)

| Surface | File / area | Action if !showAi |
|---------|-------------|-------------------|
| Terminal empty-state selector | `buildTerminalToolSelectorView` | Shell only |
| Workspace hub | `populateWorkspaceHubTools` | Shell only |
| Project “open tool” / new tab lists | ~pairs list near workspace session create | Filter to shell + shell-root only |
| Any overflow menu listing AI types | grep `opencode`/`claude-code` in MainActivity | Same filter |

`createNewTerminalSession` / `createWorkspaceTerminalTab`: if type is AI and !showAi, Toast “Install AI CLI tools in Settings” and abort (defense in depth).

### 8.4 Method switch

On `linux_method` change (existing refresh paths): re-evaluate `shouldShowAiToolLaunchers` for new method → rebuild both selectors.

### 8.5 Proot vs chroot parity

| | proot | chroot |
|--|-------|--------|
| Shell only when not provisioned | Yes | Yes |
| shell-root | Yes (root via proot root or chroot root as today) | Yes; keep root-required toast if no su |
| codex when AI shown | Yes | Hidden (existing) |
| Install from Settings | Guest proot script | Guest chroot script as root |

---

## 9. Shared installer extraction (impl note)

Today onboarding owns proot/chroot CLI runners; MainActivity Scripts has generic script runner.

**Plan:** extract:

```text
object CliToolsInstaller {
  fun run(ctx, method, onLine, onDone: (exitCode) -> Unit)
  // deploys assets/scripts/setup_cli_tools.sh into guest /tmp and executes
}
```

Used by:

1. OnboardingActivity step H  
2. Settings AI Install button  
3. Scripts completion hook (optional wrap)  

Prevents three divergent paths.

Optional script guard:

```bash
# setup_cli_tools.sh top
if [ "${NC_SKIP_AI_CLI:-0}" = "1" ]; then exit 0; fi
```

---

## 10. Policy copy constraints

**Do say:** guest Debian; optional third-party installers; skip = shells only; install later in Settings.  
**Do not say:** immune to Play external-code policy; Google reviews vendor CLIs.

---

## 11. Marketplace (follow-up C6.1)

Not in this plan. Per-package confirm later.

---

## 12. Implementation order (when approved)

| Step | Work | Est. |
|-----:|------|------|
| 1 | `AiCliProvisionState` prefs + helpers | S |
| 2 | `InstallPlanCatalog` + `buildInstallPlanPage` + page renumber | M |
| 3 | Gate H onboarding proot+chroot; mark prefs | M |
| 4 | Complete page truthful summary | S |
| 5 | Extract `CliToolsInstaller` (or shared runner) | M |
| 6 | Settings AI page Install card + confirm + refresh | M |
| 7 | Terminal + workspace gate + banner + shared catalog | M |
| 8 | Audit other AI launch lists + session create guard | S |
| 9 | Scripts confirm + mark provisioned | S |
| 10 | Clear flag on guest wipe / method-aware gate | S |
| 11 | Checklist C6 note + device test §13 | S |

**No commit/push** until user asks.

---

## 13. Testing plan

### 13.1 Onboarding AI off (proot)

1. Fresh data → plan → AI off → install base  
2. Complete: AI SKIPPED; `ai_cli_tools_provisioned=false`  
3. Terminal: **only** Debian Shell + Rooted (+ install banner)  
4. Workspace hub: **only** shells  
5. Settings AI: Install card visible; tool probes mostly MISSING  

### 13.2 Settings install (proot)

1. From 13.1 → Install AI tools → confirm → success  
2. Flag true; Terminal shows Free+Paid+Shell; workspace same  
3. AI login cards usable  

### 13.3 Onboarding AI on (proot)

1. AI on → H runs → flag true → full tool grids  

### 13.4 Chroot AI off / on

Same as 13.1–13.3 with chroot; rooted shell still gated by root availability; no codex when AI shown.

### 13.5 Method switch

1. Provisioned only on proot; switch to chroot empty guest → shell-only until chroot install  
2. Install on chroot → chroot full grid  

### 13.6 Defense

1. Deep-link / debug call `createNewTerminalSession("claude-code")` when not provisioned → blocked Toast  
2. Guest wipe → flag cleared → shell-only again  

### 13.7 Regression

- Customization off skips G  
- Progress without H  
- Existing AI login flows when provisioned  
- `refreshTerminalToolSelector` / `refreshWorkspaceHubTools` on method change  

---

## 14. Acceptance criteria

1. Onboarding never runs H unless AI opt-in + plan accepted.  
2. Default first-run = base only, AI off.  
3. If not provisioned: Terminal **and** Workspace show **only** `shell` + `shell-root` (proot and chroot).  
4. Settings AI page offers **Install AI CLI tools** when not provisioned; runs guest installer with confirm.  
5. After install success: flag set; both selectors show full Free/Paid/Shell (codex rule intact).  
6. Complete page truthful.  
7. No host code inject; guest-only remotes.  
8. Shared catalog prevents Terminal/Workspace drift.  
9. C5 not implemented.  

---

## 15. Risk register

| Risk | Mitigation |
|------|------------|
| Flag true but bins missing | Optional probe reconcile on AI page open |
| Flag false but user installed via raw terminal | Settings Install / probe-any-tool fallback optional |
| Three install entry points diverge | `CliToolsInstaller` single path |
| Users confused why AI cards gone | Banner + Settings install CTA |
| Reviewer still dislikes remote AI | Default off + explicit Settings install |

---

## 16. Decision log

| Decision | Choice |
|----------|--------|
| Strategy | S1 AI default off + S3 consent |
| Consent alone, H always | **Rejected** |
| Virtual = policy N/A | **Rejected** as exemption |
| AI default | **Off** |
| Post-skip install | **Settings AI tools** (primary) + Scripts |
| Terminal/workspace without AI | **Shell + shell-root only** |
| Per-tool install v1 | **No** — suite script |
| C5 | Deferred |
| Marketplace | C6.1 later |

---

## 17. Stop

Plan only — **no code in this step.**  
Approve or request edits → then implement §12.
