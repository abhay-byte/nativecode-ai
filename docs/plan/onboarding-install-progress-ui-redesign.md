# Onboarding install UI redesign: progress-first + logo complete

**Date:** 2026-07-30  
**Status:** implemented  
**Scope:** Environment Setup page (page 4, proot + chroot) + Setup Successful page (page 5)  
**Design system:** `docs/project/ui_design.md` (Obsidian Terminal / Cyber-Brutalist)  
**Primary file:** `app/src/main/java/com/zenithblue/nativecode/OnboardingActivity.kt`  
**Related:**  
- `DesignTokens.kt` (`NC` palette already mirrors design tokens)  
- `R.drawable.logo_highres` (1024×1024 WebP — already used on intro page)  
- Install chains: `runDebianBaseSetup()` proot A–H + chroot base + E–H  
- Scripts stream text only today: `flux_install.sh`, `setup_debian13_chroot.sh`, `runShellCommand`, `RootShell.executeScriptAsset`  
**Screenshots (current):** setup log terminal dominant; complete page green check square  

---

## Problem (observed)

### Environment Setup (page 4) — both proot & chroot

1. **Terminal-first UI**  
   `buildDebianBasePage()` builds a full-height Cyber-Brutalist console card (`[ SETUP LOG ]` + monospaced `baseLogText` in `ScrollView`, weight `1f`). Logs are the visual center of the page.

2. **Progress is decorative, not informative**  
   - Horizontal `ProgressBar` is only **6dp** tall, lives in a small status card above the console.  
   - Starts **indeterminate**; rarely becomes determinate.  
   - Only explicit updates: `progress = 0` (chroot root fail) or `progress = 100` (finish).  
   - No overall **%**, no step index, no download bytes / total.

3. **Status line is log-like**  
   `baseStatusText` shows the latest step string, but every status + raw shell stream also dumps into the always-visible log (`updateBaseStatus` + `runShellCommand` append). Dense, noisy, hard to scan for non-power users.

4. **Same layout for proot and chroot**  
   One page builder; method only changes backend chain. Redesign must stay **shared UI**, dual backend.

### Complete page (page 5)

5. **Generic check icon**  
   `buildCompletePage()` hero uses sharp box + `R.drawable.ic_check_circle` (tinted primary) + `pulseView`. Feels like a material snack, not brand.  
   Intro already has a high-quality branded treatment with `R.drawable.logo_highres` in a Cyber-Brutalist card — complete page should match brand weight.

---

## Goals

1. **Progress-first install page** for proot and chroot: no terminal visible by default.  
2. **Big determinate progress** with clear **overall %** (0–100) and human-readable detail (step name, optional download size).  
3. **Logs on demand** via explicit control (“View setup log” / “Hide setup log”); full stream still captured.  
4. **Complete page** centers **app logo** (`logo_highres`) instead of check circle; tighter Cyber-Brutalist polish.  
5. Strict adherence to **ui_design.md**: sharp 0px corners, two-tone extrusion shadows, Terminal Green accents, mono labels, `#FAFAFA` body text hierarchy via existing `NC` tokens.  
6. **No behavior change** to install success/failure gates, script order, soft-fail CLI tools, `linux_method` persistence, or `auto_start_setup` deep-link.

---

## Non-goals

- Rewriting install scripts’ package logic or changing step order.  
- Real-time apt package-level progress for every dpkg line (optional later).  
- Compose migration (keep programmatic View builders like rest of onboarding).  
- Redesign of intro / slideshow / requirements / isolation pages (except reusing logo pattern).  
- New fonts packaging if Space Grotesk / Geist not already loaded — use existing typefaces + mono; note font gap if any.  
- Changing SplashActivity progress UI.

---

## Design system constraints (must implement)

From `docs/project/ui_design.md`:

| Token | Value / rule |
|-------|----------------|
| Background | `#131313` (`NC.BG`) |
| Cards / panels | `#121212`–`#201f1f` surface containers (`NC.SURFACE_CONTAINER` / low) |
| Primary / success | Terminal Green `#3DDC84` / `#60f99e` (`NC.PRIMARY_CON` / `NC.PRIMARY`) |
| Body text | light on dark (`NC.ON_SURFACE` ≈ design pure white intent) |
| Labels / % / step | JetBrains Mono or `Typeface.MONOSPACE` |
| Corners | **0px** everywhere (no rounded progress pill, no rounded buttons) |
| Elevation | Two-tone hard offset 6×6: right face `#3C4A3F`, bottom `#393939` via existing `cyberBrutalistBg` |
| Inner bevel | 1px top/left `#ffffff20` if drawable supports it; else keep current card stroke |
| Press | translate 4px + shadow shrink (already on primary/secondary buttons) |
| Primary CTA | green fill, dark text (`NC.ON_PRIMARY`) |
| Secondary CTA | dark fill, green border, light text |

**Progress bar visual (custom, not stock thin Material bar):**

- Track: sharp rect, height **12–16dp**, fill `NC.SURFACE_HIGH` or `#0A0A0A`, 1–2px border `NC.BORDER`.  
- Fill: solid `NC.PRIMARY_CON` (`#3DDC84`), width = percent, **no rounded corners**.  
- Optional 1px top inner highlight on fill for bevel.  
- Do **not** use circular determinate spinner as primary indicator.

**Typography hierarchy (install page):**

- Header: existing `smallHeader("Environment Setup", …)`  
- Percent: large bold **32–40sp**, primary green, center or end-aligned next to bar  
- Step title: 14–16sp mono / body, `NC.ON_SURFACE`  
- Detail subline: 12sp mono, `NC.ON_SURF_VAR` (e.g. `412 MB / 1.2 GB · 8.4 MB/s` or `Step 3 of 8`)  
- Log body (when open): 11sp mono, `NC.PRIMARY` on `NC.LOGBG` (keep current console feel)

---

## Target UX

### A. Environment Setup page (page 4)

```
┌─────────────────────────────────────────┐
│  ■ Environment Setup                    │
│                                         │
│  ┌─ progress card (extruded) ─────────┐ │
│  │  STEP 3 / 8 · PROOT                │ │
│  │                                    │ │
│  │           42%                      │ │
│  │  ████████████░░░░░░░░░░░░░░░░░░░░  │ │
│  │                                    │ │
│  │  E. Provisioning Debian guest…     │ │
│  │  Extracting rootfs · please wait   │ │
│  └────────────────────────────────────┘ │
│                                         │
│  ┌ secondary ┐                          │
│  │ View log  │  (toggle / expand)       │
│  └───────────┘                          │
│                                         │
│  ┌─ log panel (GONE by default) ──────┐ │
│  │ [ SETUP LOG ]              Close   │ │
│  │ >>> line…                          │ │
│  │ (scroll, max ~40% screen height)   │ │
│  └────────────────────────────────────┘ │
│                                         │
│  ┌ primary (disabled until 100%) ─────┐ │
│  │ Next: Complete Setup            →  │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

**States**

| State | Progress UI | Next | Log button |
|-------|-------------|------|------------|
| Running | determinate 0–99, step labels update | disabled α 0.45 | enabled |
| Success | 100%, status success string | enabled α 1 | enabled |
| Hard fail (e.g. no root, host setup throw) | freeze last %, error color on status | stays disabled; optional “Retry” later = out of scope unless easy | enabled |
| Log open | same | same | label → “Hide log” |

**Interaction**

1. Default: log panel **`View.GONE`**.  
2. Tap **View setup log** → panel **`VISIBLE`**, auto-scroll to bottom on new lines; button becomes **Hide setup log**.  
3. Optional: secondary icon `ic_terminal` + label (secondary button style from design system).  
4. While log closed, still append to `baseLogText` (or a `StringBuilder` buffer if view not built yet) so open always has full history.  
5. Do **not** put raw stream into the big status title — status title = short human phase only.

### B. Complete page (page 5)

```
┌─────────────────────────────────────────┐
│                                         │
│   ┌─ extruded logo card ──────────┐     │
│   │         [ logo_highres ]      │     │
│   └───────────────────────────────┘     │
│                                         │
│        Setup Successful!                │
│   Linux container & AI harness …        │
│                                         │
│   ┌ [ PROVISIONED COMPONENTS ] ───────┐ │
│   │ Guest OS … READY                  │ │
│   │ Dev Runtime … READY               │ │
│   │ AI Tools … READY                  │ │
│   └───────────────────────────────────┘ │
│                                         │
│   ┌ Launch Environment → ─────────────┐ │
│   └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

Changes vs current:

- **Remove** `iconBox` + `ic_check_circle` + green tint.  
- **Add** centered logo like intro: `logo_highres`, FIT_CENTER, size ~120–140dp inside extruded card ~160–180dp (0px corners, two-tone shadow).  
- **Optional:** soft pulse on logo card only if subtle; prefer static for “finished hardware” feel.  
- Title stays “Setup Successful!” primary green, slightly tighter letter-spacing.  
- Subtitle mono, `ON_SURF_VAR`.  
- Summary card unchanged structurally; ensure READY badges remain sharp chips.  
- Spacers keep vertical balance (hero not cramped against CTA).

---

## Progress model (shared, method-aware)

Stock UI cannot know % from free-form shell lines. Introduce an explicit **weighted phase model** in Kotlin; map install milestones → overall 0–100.

### Overall formula

```
overall = floor( sum(completed_phase_weights) + current_phase_weight * phase_fraction )
```

- `phase_fraction ∈ [0,1]` from sub-progress (download bytes, tar extract estimate) or 0→1 snap at phase start/end when no sub-progress.  
- Cap at 99 until final success handler sets 100 and unlocks Next.

### Proot phases (aligned with `runDebianBaseSetup` comments A–H)

| ID | Label (UI) | Weight | Sub-progress source |
|----|------------|--------|---------------------|
| A | Preparing directories | 3 | snap complete |
| B | Extracting bootstrap assets | 12 | optional: copy/extract byte counter if easy; else snap mid/end |
| C | Deploying host configs | 5 | snap |
| D | Initializing host environment | 12 | indeterminate within phase (script logs only) |
| E | Provisioning Debian guest | 30 | download+extract if parseable; else step sub-milestones in `flux_install` |
| F | Hardware acceleration | 10 | snap on start/end |
| G | Guest customization | 10 | 0 if skipped (redistribute or mark complete instantly) |
| H | AI CLI tools | 18 | snap; soft-fail still advances to 100 |
| DONE | Complete | — | force 100 |

Weights sum ≈ 100. Tune in one table constant so design QA can rebalance without hunting call sites.

### Chroot phases

| ID | Label (UI) | Weight | Sub-progress source |
|----|------------|--------|---------------------|
| R0 | Checking root access | 2 | snap |
| R1 | Base chroot install (`setup_debian13_chroot.sh`) | 35 | download/extract if parseable; else log milestones |
| E | Debian family packages | 20 | snap boundaries |
| F | Hardware acceleration | 10 | snap |
| G | Customization | 10 | skip-instant if off |
| H | AI CLI tools | 18 | soft-fail → finish |
| DONE | Complete | — | force 100 |

### Mapping to code hooks (minimal intrusion)

Add helpers (names illustrative):

```kotlin
private fun setSetupPhase(phaseId: String, label: String, fraction: Float = 0f)
private fun setSetupPhaseFraction(fraction: Float) // download mid-phase
private fun completeSetupPhase(phaseId: String)
private fun finishSetupProgressSuccess()
private fun failSetupProgress(message: String)
```

Call sites:

1. **Proot** — at each existing `updateBaseStatus("A. …")` etc., also `setSetupPhase(...)`.  
2. **Chroot** — after root check; before `executeScriptAsset`; each E/F/G/H `updateBaseStatus`; `finishChrootBaseSetup` → 100.  
3. **Errors** — keep log + status error text; leave bar at last % (do not fake 100).

### Download % (detailed “how much down”)

Today scripts use plain `wget`/`curl` without machine-readable progress lines that Kotlin reliably parses.

**Preferred (phased implementation):**

**P1 — UI + step weights (required)**  
Overall % from phase table is enough for a professional page even without byte counts.

**P2 — Byte-level when available (strongly recommended)**  
1. Prefer **Kotlin-side** copy/download when the app already deploys rootfs (`deployRootfs` / asset open) — wrap `InputStream` with counted stream → `setSetupPhaseFraction(read/total)`.  
2. For shell wget: parse lines like `\d+%` if `--progress=dot` / curl `-#` later; treat as best-effort, ignore parse fails.  
3. Detail line format:  
   - download: `Downloaded 412 MB / 1.2 GB`  
   - unknown: `Working…` or last short status  
   - complete phase: clear detail or show phase name only  

**P3 — Script markers (optional, only if P2 insufficient)**  
Emit stable tags scripts already can add without user-facing noise:

```text
FLUX_PROGRESS phase=E fraction=0.42 detail=412MB/1200MB
```

Parse in `updateBaseStatus` / `onLine` once; ignore unknown lines. Keep out of scope for v1 if P1+P2 enough.

**Do not block redesign on perfect apt %.**

---

## Implementation plan

### P0 — Inventory & state fields (read + small struct)

**File:** `OnboardingActivity.kt`

Current page-4 fields:

```kotlin
baseStatusText, baseProgressBar, baseLogText, baseLogScroll, baseNextBtn
```

Add / replace:

```kotlin
// Progress UI
private lateinit var setupPercentText: TextView      // "42%"
private lateinit var setupPhaseMetaText: TextView     // "STEP 3 / 8 · PROOT"
private lateinit var setupStepTitleText: TextView     // short phase title
private lateinit var setupDetailText: TextView        // download / sub detail
private lateinit var setupProgressTrack: View         // or FrameLayout host
private lateinit var setupProgressFill: View
// Log UI
private lateinit var setupLogPanel: View
private lateinit var setupLogToggleBtn: View
private var setupLogVisible = false
private val setupLogBuffer = StringBuilder()          // if needed before views init
// Model
private var setupOverallPercent = 0
private var setupCurrentPhaseWeight = 0
private var setupCompletedWeight = 0
```

Keep `baseLogText` / `baseLogScroll` for log panel content.  
Deprecate visual role of thin `baseProgressBar` (remove or hide).  
`baseStatusText` can map to `setupStepTitleText` or remain alias.

### P1 — Rebuild `buildDebianBasePage()` (UI only)

**File:** `OnboardingActivity.kt` → `buildDebianBasePage`

1. Keep `smallHeader("Environment Setup", R.drawable.ic_storage)`.  
2. **Progress card** (`cyberBrutalistBg`, 0px, padding 18–24dp):  
   - Top row: `setupPhaseMetaText` mono 11–12sp primary/on-surf-var.  
   - Large `setupPercentText` “0%”.  
   - Custom bar: `FrameLayout` track + fill `View` with layout weight or `updateProgressBarWidth(percent)`.  
   - `setupStepTitleText` + `setupDetailText`.  
3. **Log toggle:** `secondaryButton("View setup log")` or icon+label; full width or left-aligned.  
4. **Log panel:** reuse existing console card structure but:  
   - `layoutParams` height max ~240–320dp or weight only when visible  
   - `visibility = GONE` default  
   - header `[ SETUP LOG ]` + optional close  
5. **Next:** existing `primaryButton("Next: Complete Setup", …)` disabled.  
6. Spacing: 16–24dp between blocks (unit-4 / unit-6); page padding 16–20dp mobile margin.  
7. Ensure `isDebianBaseSetupStarted` / auto-start still works after view rebuild (`lateinit` re-init each `showPage(4)` — same as today).

**Helper:** `private fun applySetupProgressUi(percent: Int)` on main thread:

- Clamp 0–100  
- Set percent text  
- Resize fill width: `post { fill.layoutParams.width = (track.width * percent / 100f).toInt() }`  
- Content description for a11y: `"Setup progress $percent percent"`

### P2 — Wire phase model into proot + chroot runners

**File:** `OnboardingActivity.kt` → `runDebianBaseSetup`, `finishChrootBaseSetup`, CLI helpers

1. Define `object SetupPhases` or private maps for proot/chroot weights + display labels.  
2. At each milestone `updateBaseStatus(...)`, call phase API so % advances.  
3. On success paths that currently set `progress = 100`, call `finishSetupProgressSuccess()` + enable Next (same as today).  
4. On chroot root failure: `failSetupProgress(...)`, progress 0 or leave low, Next disabled.  
5. On proot `catch`: error status, do not enable Next.  
6. **Log path:**  
   - `updateBaseStatus`: short title to step text; full `>>> msg` only into log buffer/view.  
   - `runShellCommand` / `onLine`: **log only** (do not replace big step title with every apt line). Optionally throttle title updates if line matches known patterns.  
7. Isolation method badge in meta: `PROOT` vs `CHROOT` from `selectedIsolationMethod`.

### P3 — Download / sub-progress (best effort)

1. Audit `deployRootfs` / bootstrap tar copy paths for counted streams.  
2. If rootfs already in assets/files, show fraction while copying.  
3. Document that network wget inside guest/host scripts may stay phase-flat until optional `FLUX_PROGRESS` markers.  
4. Detail line updates only on main thread; rate-limit UI (e.g. max 10 Hz) if streaming bytes.

### P4 — Complete page logo hero

**File:** `OnboardingActivity.kt` → `buildCompletePage`

1. Replace check `iconBox` block with logo card pattern from `buildIntroPage` (lines ~165–187).  
2. Resource: `R.drawable.logo_highres` (not low-res mipmap).  
3. No `setColorFilter` on logo (preserve brand colors).  
4. ScaleType `FIT_CENTER`; sharp extruded container; primary stroke optional (intro uses border; success may use `NC.PRIMARY` stroke lightly for celebration — pick one, match design: border `NC.BORDER` or `NC.PRIMARY` 1px). Prefer **primary stroke** for success emphasis.  
5. Remove `pulseView(iconBox)` or pulse logo card once slowly — default **no pulse** for calmer finish.  
6. Keep summary rows + Launch CTA + `setup_complete` marker write.  
7. Vertical rhythm: top spacer / logo / title / subtitle / summary / bottom spacer / button.

### P5 — Polish & edge cases

| Case | Behavior |
|------|----------|
| User opens log mid-install | Panel shows full history; auto-scroll |
| User rotates / recreation | Activity not config-special today; accept rebuild; setup flag may restart — **do not change** process lifecycle unless already handled |
| `showPage(4)` called twice | Existing `isDebianBaseSetupStarted` prevents double start from isolation Next; deep-link same |
| Log buffer growth | Cap at e.g. last 200k chars or 5000 lines to avoid OOM on huge apt output |
| Accessibility | Percent + step announced; log button contentDescription |
| Dark nav/status bars | Unchanged |
| Error strings | Use `NC.ERROR` for hard-fail step title |

### P6 — Manual test matrix

| # | Path | Expect |
|---|------|--------|
| 1 | Fresh proot full install | No log visible; % climbs through A–H; Next at 100 |
| 2 | Fresh chroot (root granted) | Same UI; meta shows CHROOT; chain E–H |
| 3 | Chroot no root | Error status; Next disabled; log optional |
| 4 | Toggle View log mid-run | Log appears with history; Hide works |
| 5 | Soft-fail CLI tools (H exit ≠0) | Still reaches 100 + Next (existing soft-fail) |
| 6 | Complete page | Center logo highres; no check circle; Launch works |
| 7 | Settings `auto_start_setup` + `target_page=4` | New UI, auto-run chain |
| 8 | Skip customization toggle off | G weight skipped cleanly; % still reaches 100 |
| 9 | Design check | 0px corners, green bar, extruded cards, secondary log btn |

---

## File change checklist

| File | Action |
|------|--------|
| `OnboardingActivity.kt` | Rebuild page 4 UI; progress model; log toggle; page 5 logo hero; split status vs log streams |
| `DesignTokens.kt` | Only if new token needed (e.g. progress track fill); prefer reuse `NC` |
| `docs/project/ui_design.md` | No edit unless new component token documented later |
| Install scripts | Optional P3 markers only; **not required for UI ship** |
| Drawables | Reuse `logo_highres`, `ic_terminal`; no new check asset |

---

## Layout implementation notes (Android Views)

### Custom progress bar without Material rounded style

```kotlin
// Pseudocode structure
val track = FrameLayout(...).apply {
    background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, 0) // 0 radius
    layoutParams = LinearLayout.LayoutParams(MATCH, dp(14))
}
val fill = View(...).apply {
    setBackgroundColor(NC.PRIMARY_CON)
    layoutParams = FrameLayout.LayoutParams(0, MATCH) // width updated in code
}
track.addView(fill)
```

On percent change:

```kotlin
track.post {
    val w = (track.width * percent / 100f).toInt().coerceAtLeast(if (percent > 0) dp(2) else 0)
    fill.layoutParams = FrameLayout.LayoutParams(w, MATCH)
    fill.requestLayout()
}
```

Avoid `ProgressBar` indeterminate mode after start — always determinate once first phase begins.

### Log panel height

When visible, prefer fixed max height (`dp(280)`) over `weight=1` so progress card stays dominant. If keyboard N/A (no input), fine.

### Button row

Option A: log toggle full width above Next.  
Option B: log toggle secondary full width; Next primary full width (recommended — matches design stacked CTAs).

---

## Suggested implementation order

1. **P1 UI shell** with fake timer / dummy % to validate look (dev only, remove before ship).  
2. **P2 wire real phases** proot then chroot.  
3. **P4 complete logo**.  
4. **P3** download fraction if time.  
5. **P5/P6** polish + test matrix.

Estimated focus: almost all work in `OnboardingActivity.kt` (~buildDebianBasePage + helpers + call-site phase hooks + buildCompletePage).

---

## Acceptance criteria

- [ ] Install page for **proot and chroot** does **not** show terminal/log by default.  
- [ ] Large sharp green progress bar + **numeric overall %** always visible during install.  
- [ ] Current step label updates for each major phase (A–H / chroot R + E–H).  
- [ ] Detail line can show download fraction when byte progress available; otherwise sensible step detail.  
- [ ] **View setup log** reveals scrollable log; **Hide** collapses; logs still complete for debug.  
- [ ] Next remains disabled until success path; enabled at 100% with existing unlock logic.  
- [ ] Complete page shows **centered high-quality app logo** (`logo_highres`), not check circle.  
- [ ] Visual language matches Cyber-Brutalist: 0px radius, two-tone extrusion, Terminal Green primary, mono labels.  
- [ ] No regression: install scripts, soft-fail H, `linux_method`, `setup_complete`, Launch → MainActivity.

---

## Open questions (resolve at implement time if needed)

1. **Retry button** on hard fail? Out of scope unless user asks; plan assumes status + logs only.  
2. **Bottom sheet vs expand** for logs? Plan = expand panel (simpler, no dependency). Switch to Dialog if expand steals too much space on small devices.  
3. **Exact phase weights** — start with table above; tune after one real device install timing.  
4. **Font files** — if Space Grotesk not bundled, large % uses `DEFAULT_BOLD` + primary color (intro already does this for “NativeCode”).

---

## Stop point

Plan only. **No code changes** until explicit implement approval.
**Done** when this document is accepted and implementation is requested.
)
