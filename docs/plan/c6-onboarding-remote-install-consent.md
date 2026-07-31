# C6 — Onboarding install inventory + consent (proot & chroot)

**Date:** 2026-07-31  
**Status:** plan only — **do not implement until approved**  
**Policy:** Play Device & Network Abuse / external & interpreted code (§4.7); checklist **C6**  
**Out of scope this plan:** **C5** (AI report UI) — deferred  
**Related:**  
- `docs/policy/Google_Play_Store_Policy_Compliance_Checklist.md` (C6, B11)  
- `docs/plan/onboarding-cli-tools-idempotent-main-setup.md` (step H folded into main setup)  
- `docs/plan/chroot-onboarding-chain-like-proot.md` (E→H parity)  
- `docs/project/ui_design.md` (cyber-brutalist)  
- `docs/privacy-policy.md`  

**Primary code:**  
| Path | Role |
|------|------|
| `app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt` | Pages, phase lists, proot/chroot chains, step H |
| `app/src/main/assets/scripts/setup_cli_tools.sh` | Guest AI CLI remote installers (unchanged logic; gated by app) |
| `app/src/main/assets/scripts/setup_debian_family.sh` | Guest apt base (XFCE, sudo, flux user) |
| `app/src/main/assets/scripts/setup_hw_accel_debian.sh` | GPU (may download turnip/mesa tarballs) |
| `app/src/main/assets/scripts/setup_customization_debian.sh` | Themes; some curl (oh-my-zsh etc.) |
| `app/src/main/assets/scripts/flux_install.sh` | Proot Debian rootfs (asset / download + SHA) |
| `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh` | Chroot base rootfs (local / download + SHA) |
| `app/src/main/java/com/ivarna/nativecode/MainActivity.kt` | Scripts UI re-run of `setup_cli_tools.sh` (post-onboarding path) |

---

## 1. Goal

Close **C6 risk for first-run onboarding** by making remote / substantial installs:

1. **Visible before they run** (inventory of what will be installed)  
2. **Explicitly agreed** (user action, not silent auto)  
3. **Split** so high-risk remote AI installers are **optional** (default **off** or **Skip**)  
4. **Identical policy UX for proot and chroot** (same consent screens; different backend chains only)  
5. **Honest product copy**: work runs in **guest Debian** (proot/chroot), not as host APK update; still network + third-party scripts when user opts in  

### Success definition (policy)

| Before | After |
|--------|--------|
| Isolation → **Configure & Install** → full A–H / R0–H **always** runs step **H** (`setup_cli_tools.sh` curl/npm) | Isolation → **Install inventory + consent** → Environment Setup runs **base phases only** unless user opted into AI remotes |
| No pre-flight list of packages/tools | Scrollable inventory: base + optional AI + method-specific notes |
| “Virtual guest” only in privacy/docs | Same language on consent screen + progress + complete page |

**Does not claim:** “Device & Network Abuse N/A because proot.” Isolation **mitigates host abuse**; **does not** erase app-orchestrated remote exec. Consent + opt-in is the C6 fix we implement.

---

## 2. Non-goals

| Out | Why |
|-----|-----|
| C5 AI report UI | Explicitly deferred |
| Removing ability to run AI CLIs forever | Product keeps optional path |
| Host process remote dex/so | Already forbidden — keep as invariant, no new work |
| Full marketplace redesign | Separate confirm later (see §11) |
| Splitting Play vs sideload product flavors (S2) | Optional future; this plan is **S1+S3** single APK |
| Pinning every vendor `curl \| bash` body | Impossible to fully pin live vendor scripts; inventory + opt-in is the control |
| Rewriting all guest scripts for apt-only AI | Too large; gate script invocation instead |

---

## 3. Assumptions (user-confirmed baseline)

Treat as **already true** for this plan; verify in impl PR only if broken:

1. **Never inject remote code into host process** — no downloaded dex/so into `com.ivarna.nativecode` runtime. Guest-only scripts + proot/chroot exec.  
2. **Integrity where we control artifacts** — e.g. rootfs `ROOTFS_SHA256` in `flux_install.sh` / `setup_debian13_chroot.sh`; prefer app-packaged `assets/rootfs/debian_13_rootfs.tar.xz` over download.  
3. **Listing + reviewer notes match real behavior (C8 path)** — ops copy must describe optional AI installers + guest isolation after this UX ships.  

---

## 4. Current state (problem)

### 4.1 Page map today

| Index | Page | Action |
|------:|------|--------|
| 0 | Privacy | Accept → prefs |
| 1 | Intro | Brand |
| 2 | Slideshow | Features |
| 3 | Requirements | Storage gates |
| 4 | Isolation | proot / chroot + **customization toggle** (currently non-interactive/`isEnabled=false` in UI — flag still used in chain) |
| 5 | Environment Setup | **Auto-starts** `runDebianBaseSetup()` on enter / from isolation Next |
| 6–7 | Complete | Marker + summary (hardcodes AI tools “READY”) |

### 4.2 Install chains (always include H today)

**Proot** `prootSetupPhases()`:

| ID | Label | Work |
|----|-------|------|
| A | Preparing directories | Host dirs |
| B | Extracting bootstrap assets | Termux-style bootstrap |
| C | Deploying host configs | Scripts, rootfs asset deploy |
| D | Initializing host environment | `setup_termux.sh` |
| E | Provisioning Debian guest | `flux_install.sh` + `setup_debian_family.sh` |
| F | Hardware acceleration | `setup_hw_accel_debian.sh` (+ possible mesa/turnip download) |
| G | Customizing guest | `setup_customization_debian.sh` if `enableDebianCustomization` |
| **H** | **Installing AI CLI tools** | **`setup_cli_tools.sh` always** |

**Chroot** `chrootSetupPhases()`:

| ID | Label | Work |
|----|-------|------|
| R0 | Checking root access | RootShell |
| R1 | Installing Debian chroot base | `setup_debian13_chroot.sh` |
| E | Provisioning Debian packages | `setup_debian_family.sh` |
| F | Hardware acceleration | same guest script |
| G | Customizing guest | same optional |
| **H** | **Installing AI CLI tools** | **same always** |

### 4.3 Why C6 flags this

`setup_cli_tools.sh` (guest, as root, tools as `flux`):

- `curl … \| bash` → nvm  
- `npm install -g` → opencode-ai, @openai/codex, @qwen-code/qwen-code  
- `curl … \| bash` → opencode.ai, antigravity, claude.ai, x.ai, kiro  

Plus secondary remotes in G/F/rootfs download paths. **Step H is the loudest automated remote-exec surface** because onboarding always runs it with soft-fail continue.

---

## 5. Policy product framing (copy constraints)

Use consistently on consent UI, logs, complete page, and reviewer notes:

**Do say:**

- Installs run **inside a Debian guest** (proot user-space or chroot), not as an update to the Play-installed APK.  
- Optional AI tools are **third-party installers** fetched over the network at your request; not reviewed by Google as part of this app binary.  
- You can **skip** AI tools and install later from Settings / Scripts.  
- Base environment uses packaged/local rootfs when available; SHA verified when download fallback runs.

**Do not say:**

- “Immune to Device & Network Abuse because virtual.”  
- “Google reviews every CLI we install.”  
- “No network / no third parties” if AI opt-in is on.

---

## 6. Target UX flow

### 6.1 New page map

| Index | Page | Notes |
|------:|------|--------|
| 0 | Privacy | Unchanged |
| 1 | Intro | Unchanged |
| 2 | Slideshow | Unchanged |
| 3 | Requirements | Unchanged |
| 4 | Isolation | proot / chroot; **restore working customization toggle** (see §7.3) |
| **5** | **Install plan + consent** | **NEW** — inventory + AI opt-in + primary CTA |
| **6** | Environment Setup | Same progress UI; **phases depend on consent prefs** |
| **7** | Complete | Summary reflects what actually ran |

Deep-link / Settings “reinstall env” that jumps to setup must **still hit consent** if first-time flags missing, or re-show a slim confirm if re-running full chain (see §9).

### 6.2 Page 5 — Install plan + consent (detail)

**Layout** (cyber-brutalist per `ui_design.md`):

1. **Header** — `Install plan` + method badge `PROOT` or `CHROOT`  
2. **Intro blurb** (2–3 lines) — guest Debian only; no host APK self-update; network for packages/tools as listed  
3. **Section A — Base environment (required)**  
   - Not a skip toggle  
   - Expandable or always-visible bullet inventory (method-aware; see §6.3)  
4. **Section B — Guest desktop customization (optional)**  
   - Toggle bound to `enableDebianCustomization` (default **on** or match current product default **true**, but **user-editable here**, not locked)  
   - Subtext: themes/shell; may fetch font/theme assets / oh-my-zsh (disclose if script still curls)  
5. **Section C — AI CLI tools (optional, high-risk remote)**  
   - Toggle **`enableAiCliInstall` default `false`**  
   - When off: clear “Skipped — install later from Settings → Scripts”  
   - When on: full tool table + warning card  
6. **Warning card** (always visible when AI on; muted when off)  
   - Remote `curl`/`npm` installers  
   - Third-party ToS / network  
   - Guest-only execution  
7. **Primary button** — `I understand — start install`  
   - Enabled only after user has scrolled inventory **or** explicit checkbox `I have reviewed what will be installed` (prefer **checkbox** — reliable, no scroll-spy flakiness)  
8. **Secondary** — `Back` → isolation  

**Checkbox copy (required to enable primary):**

> I have reviewed this install plan. Base setup will run in the guest environment. Optional AI tools run only if I enabled them.

**Primary CTA** must **not** start install until checkbox checked. Persist:

```text
nativecode_prefs:
  install_plan_accepted = true
  enable_ai_cli_install = <bool>
  enable_debian_customization = <bool>   // optional mirror of in-memory flag
  linux_method_pending = proot|chroot   // already selectedIsolationMethod
```

Then `showPage(6)` + start `runDebianBaseSetup()` once.

### 6.3 Inventory content (SSOT lists)

Keep lists as **Kotlin constants or small data class** in `OnboardingActivity` (or `InstallPlanCatalog.kt`) so UI and complete-page summary share one source. Do **not** scrape live scripts at runtime for v1.

#### Shared base (both methods)

| Item | Source / how |
|------|----------------|
| Debian 13 (Trixie) guest OS | Rootfs archive (app asset preferred; download fallback + SHA256) |
| User `flux` + sudo | `setup_debian_family.sh` |
| XFCE4 desktop stack + TigerVNC bits | apt via family script |
| git | apt |
| GPU accel config | `setup_hw_accel_debian.sh` (vendor detect; may download Turnip/Mesa tarball from pinned GitHub release URLs) |

#### Proot-only base rows

| Item | Source |
|------|--------|
| Host bootstrap / prefix | Steps A–D, `setup_termux.sh` |
| proot-distro Debian container | `flux_install.sh` + packaged rootfs |
| Host helper scripts | `start_gui.sh`, `stop_gui.sh`, etc. under app files |

#### Chroot-only base rows

| Item | Source |
|------|--------|
| Superuser (KernelSU/Magisk) | R0 |
| Debian chroot under `/data/local/tmp/chrootDebian13` (or current path SSOT) | `setup_debian13_chroot.sh` |
| Rootfs extract + SHA when downloaded | same script pins |

#### Optional customization rows (if toggle on)

| Item | Note |
|------|------|
| Desktop theme / shell polish | `setup_customization_debian.sh` |
| Optional network assets (fonts, oh-my-zsh, etc.) | Disclose generically: “may download theme/font assets” |

#### Optional AI CLI rows (if toggle on)

| Tool / component | Method | Typical source |
|------------------|--------|----------------|
| curl, wget, git, build-essential, python3, musl | apt | Debian mirrors |
| NVM + Node.js (major pin in script, e.g. 26) | curl install script + nvm | nvm-sh GitHub |
| opencode | npm and/or curl | npm / opencode.ai |
| codex | npm `@openai/codex` | npm |
| qwen-code | npm | npm |
| claude (Claude Code) | curl \| bash | claude.ai install |
| grok CLI | curl \| bash | x.ai |
| agy (Antigravity) | curl \| bash | antigravity.google |
| kiro-cli | curl install | cli.kiro.dev |

Footer under AI table:

> Installers are third-party. NativeCode runs them only inside the guest as user `flux` (script as root for setup). You can refuse this section and still use the terminal/desktop.

### 6.4 Environment Setup page (index 6)

- **Do not** change progress card visual system.  
- **Do** change phase lists:

**Proot without AI:**

`A B C D E F [G if custom] ` — **no H**  
Reweight so total still ~100 (redistribute H’s 18 into E/F or drop and renormalize).

**Proot with AI:**

Keep current A–H (G conditional).

**Chroot without AI:**

`R0 R1 E F [G] ` — **no H**

**Chroot with AI:**

Current list including H.

**Code gate (both chains):** after G (or F if no G):

```text
if (enableAiCliInstall) {
  enterSetupPhase("H", ...)
  run setup_cli_tools (proot or chroot)
} else {
  log "H skipped — AI CLI install not selected"
}
finish success / finishChrootBaseSetup()
```

Soft-fail behavior for H when selected: **keep** current “continue on non-zero” so one bad vendor doesn’t brick onboarding.

Initial status string today: `"Initializing full environment setup (base + AI CLIs)…"` → make dynamic:

- with AI: `… (base + optional AI CLIs)`  
- without: `… (base environment only)`

### 6.5 Complete page

Today hardcodes:

- Dev Runtime Node.js / NVM READY  
- AI Tools opencode & codex READY  

**Change to truth table:**

| Condition | Summary rows |
|-----------|----------------|
| Base always | Guest OS Debian 13 READY; Isolation method READY |
| Customization on & ran | Desktop customization READY / SKIPPED |
| AI on & H exit 0 | AI CLIs READY (or “partial — see log”) |
| AI on & soft fail | AI CLIs PARTIAL |
| AI off | AI CLIs SKIPPED — install later |

Subtitle: stop claiming “AI harness fully provisioned” when AI skipped.

---

## 7. Implementation design

### 7.1 State fields (`OnboardingActivity`)

| Field | Default | Persist key |
|-------|---------|-------------|
| `selectedIsolationMethod` | `"proot"` | existing `linux_method` after success |
| `enableDebianCustomization` | `true` | `enable_debian_customization` |
| **`enableAiCliInstall`** | **`false`** | **`enable_ai_cli_install`** |
| `installPlanAccepted` | false | `install_plan_accepted` (session + durable) |
| `isDebianBaseSetupStarted` | false | in-memory only (existing) |

### 7.2 Functions to add / change

| Function | Change |
|----------|--------|
| `showPage` | Insert consent page index 5; shift setup→6, complete→7; fix all `showPage(n)` call sites and deep-link `target_page` |
| **`buildInstallPlanPage()`** | **New** UI §6.2 |
| `buildIsolationPage` | Next → page 5 (plan), **not** start setup; enable customization toggle **or** move toggle only to plan page (prefer **single** place: plan page) |
| `buildDebianBasePage` | Dynamic phase list; don’t assume AI |
| `prootSetupPhases()` / `chrootSetupPhases()` | Overloads or filter: `includeAi: Boolean`, `includeCustom: Boolean` |
| `beginSetupPhases` | Pass flags from prefs/fields |
| `runDebianBaseSetup` proot branch | Gate H |
| `runDebianBaseSetup` chroot branch | Gate H (same) |
| `runCliToolsSetupProot` / chroot H runner | Call only if opted in |
| `buildCompletePage` | Conditional summary |
| Deep-link path (~line 167) | If jumps to setup, ensure plan accepted or redirect to plan |

### 7.3 Customization toggle placement

**Problem:** Isolation page toggle is currently disabled (`isEnabled = false`).  

**Plan decision:**  

- **Remove** dead toggle from isolation **or** re-enable it and sync to plan page.  
- **Canonical control:** Install plan page Section B only (one SSOT).  
- Isolation page stays method pick only → cleaner.

### 7.4 Settings / Scripts re-entry (post-onboarding)

| Path | Behavior after this plan |
|------|---------------------------|
| MainActivity Scripts → `setup_cli_tools.sh` | Keep, but add **one-shot confirm dialog** before run: same short AI warning + list (reuse strings from catalog). Not full onboarding. |
| Settings chroot/proot reinstall | If product re-opens OnboardingActivity setup: show plan page again if `install_plan_accepted` false; if reinstall deliberate, show plan with previous AI pref pre-filled |

### 7.5 No silent background re-provision

Do **not** auto-run `setup_cli_tools.sh` on app upgrade or MainActivity start. Only:

1. Onboarding when opted in  
2. Explicit Scripts / future “Install AI CLIs” Settings action  

(Verify no other callers auto-fire; `CliAuthService` comments already say soft probe only.)

### 7.6 Script changes

| Script | v1 change |
|--------|-----------|
| `setup_cli_tools.sh` | **None required** if fully gated by app. Optional later: env `NC_AI_CLI_TOOLS=0` early-exit for defense in depth. |
| family / hw / custom / rootfs | No functional change for v1. Inventory text must stay honest about downloads. |
| Optional defense | `setup_cli_tools.sh`: if `NC_SKIP_AI_CLI=1` exit 0 immediately — set from Kotlin when somehow invoked wrongly |

**Recommended small script guard (optional, low cost):**

```bash
if [ "${NC_SKIP_AI_CLI:-0}" = "1" ]; then
  echo ">>> NC_SKIP_AI_CLI=1 — skipping AI CLI provisioning"
  exit 0
fi
```

### 7.7 UI tokens

Match existing onboarding:

- 0px radius, 6px shadow, `NC.*` colors  
- Monospace section headers `[ BASE ENVIRONMENT ]`  
- Primary CTA green container; secondary outline  
- Warning card: `error-container` / secondary border, not playful  

No new Compose dependency — stay View-based like current onboarding.

---

## 8. Proot vs chroot parity matrix

| Concern | Proot | Chroot | Plan |
|---------|-------|--------|------|
| Consent UI | Yes | Yes | **Same page** |
| AI default off | Yes | Yes | Same pref |
| Step H gate | `runCliToolsSetupProot` | `copyAndRunInChroot(...setup_cli_tools)` | Same `if (enableAiCliInstall)` |
| Rootfs inventory text | Asset/proot-distro wording | Chroot path + root required | Method-specific bullets only |
| Root denied | N/A | R0 fails | Plan page still shown; setup fails with existing messaging |
| Complete summary | Method badge | Method badge | Shared builder |
| linux_method write | After success `proot` | `finishChrootBaseSetup` | Unchanged timing |

---

## 9. Deep links & edge cases

| Case | Handling |
|------|----------|
| `target_page` / Settings → jump to Environment Setup | If `!install_plan_accepted` for this session/install, **redirect to plan page** first |
| User kills app mid-setup | Existing partial state; on resume if setup incomplete, resume setup **without** re-showing plan if already accepted **or** re-show plan if safer (prefer: if `isDebianBaseSetupStarted` was mid-flight, resume log page only) |
| User accepted plan, AI off, later wants AI | Scripts UI + confirm dialog |
| User accepted AI, H fails soft | Complete = PARTIAL; do not force re-onboarding |
| Storage fail on requirements | Unchanged; never reach plan |
| Chroot selected without root | Isolation already blocks; plan not reached with chroot |

---

## 10. Checklist / docs updates (same PR or follow-up)

| Doc | Update |
|-----|--------|
| `Google_Play_Store_Policy_Compliance_Checklist.md` C6 | Status → **PARTIAL** or **FOLLOWED** after ship+verify: “onboarding inventory + explicit agree; AI remote installers default off; guest-only” |
| C6 “How to fix” | Point to this plan as implemented approach **S1 (AI) + S3 (explicit user)** |
| Privacy policy | Optional one line: optional AI CLI installers run only with user consent during setup |
| Reviewer notes (C8 ops) | Bullet: first-run shows install plan; AI tools optional; guest Debian only |

**C5:** leave NOT FOLLOWED; do not implement.

---

## 11. Marketplace (related C6 surface, not blocking onboarding)

`MarketplaceClient.ensurePackageScripts` downloads `install.sh` and runs in guest.

**This plan does not implement marketplace UX**, but C6 residual remains until:

- Per-package confirm dialog before download+exec  
- No background marketplace auto-install  

Track as **follow-up C6.1** after onboarding lands.

---

## 12. Testing plan

### 12.1 Manual — proot

1. Fresh install / clear app data  
2. Privacy → … → Isolation proot → **Install plan**  
3. Leave AI **off**, check agree → start  
4. Confirm log: **no** `setup_cli_tools` / no nvm curl  
5. Complete page: AI SKIPPED  
6. Terminal works; Scripts → CLI tools → confirm dialog → install works  

### 12.2 Manual — proot AI on

1. AI toggle on, agree → start  
2. Phase H appears; tools attempt install  
3. Complete reflects READY/PARTIAL  

### 12.3 Manual — chroot (rooted device)

1. Same as 12.1 with chroot method badge  
2. R0–R1 + E–F, no H when AI off  
3. AI on path includes H via chroot runner  

### 12.4 Regression

- Customization off skips G both methods  
- Back from plan doesn’t start setup  
- Double-enter setup doesn’t double-start (`isDebianBaseSetupStarted`)  
- Progress weights sum correctly without H  
- Deep-link / Settings reinstall doesn’t bypass plan when required  

### 12.5 Policy self-check

- [ ] No host dex/so download  
- [ ] Rootfs SHA path still used on download fallback  
- [ ] Listing notes updated (ops)  
- [ ] AI default off  

---

## 13. Implementation order (when approved)

| Step | Work | Est. |
|-----:|------|------|
| 1 | Add prefs + `enableAiCliInstall` + `InstallPlanCatalog` strings/data | S |
| 2 | `buildInstallPlanPage` UI + checkbox gate | M |
| 3 | Renumber pages; fix navigation + deep-link | S |
| 4 | Gate H in proot + chroot chains; dynamic phases | M |
| 5 | Complete page truthfulness | S |
| 6 | Scripts UI confirm dialog for `setup_cli_tools.sh` | S |
| 7 | Optional `NC_SKIP_AI_CLI` guard in script | XS |
| 8 | Checklist C6 status + short reviewer blurb snippet in `docs/policy/` | S |
| 9 | Device test proot + chroot per §12 | M |

**No commit/push** until user asks.

---

## 14. Risk register

| Risk | Mitigation |
|------|------------|
| Reviewer still rejects remote AI even when opt-in | Residual accepted (S3); default off + clear copy is best single-APK defense |
| Users miss AI tools | Complete page + Settings/Scripts path; optional later “Install AI CLIs” card |
| Inventory drifts from scripts | Catalog comment “keep in sync with setup_cli_tools.sh”; PR checklist |
| Page index breakage | Single `enum class OnboardingPage` or constants object instead of magic ints (recommended refactor while touching showPage) |
| Customization still curls silently | Disclosed under Section B; future: split remote custom bits |

---

## 15. Acceptance criteria (ship bar)

1. Neither proot nor chroot onboarding runs `setup_cli_tools.sh` unless **AI toggle on** and **plan checkbox accepted**.  
2. User always sees **method-specific inventory** before any base/AI install starts.  
3. Default first-run path is **base-only** (AI off).  
4. Complete page does not claim AI READY when skipped.  
5. Post-onboarding AI install requires explicit confirm.  
6. Guest-only / no host code inject preserved.  
7. UI matches cyber-brutalist onboarding patterns.  
8. C5 not implemented (deferred).  

---

## 16. Decision log (pre-impl)

| Decision | Choice |
|----------|--------|
| Strategy | **S1 for AI remotes** (default off) + **S3 explicit consent** for whole plan |
| Consent alone for always-on H | **Rejected** — H must be gated |
| Virtual guest = policy N/A | **Rejected as exemption**; used as **honest framing** only |
| AI default | **Off** |
| Single vs two consent screens | **One** plan page (base required + AI optional) |
| C5 | **Dropped for now** |
| Marketplace | **Follow-up C6.1** |

---

## 17. Stop

This document is the implementation plan only. **No code changes in this step.**  
Next user action: approve plan (or request edits) → then implement per §13.
