# Monetisation — Freemium (Free + Pro)

**Date:** 2026-08-01  
**Status:** plan (not implemented)  
**Model:** freemium, one premium tier  
**Principle:** **local-first free** — Debian, shell, git, GitHub, basic catalog stay free forever. Gate multi-agent concurrency, extra AI CLIs, unlimited desktop, full marketplace.

**Related:** `docs/policy/Google_Play_Store_Policy_Compliance_Checklist.md` §A4 (Billing N/A today); `docs/plan/software-manager-marketplace.md`; `ToolLauncherCatalog` free/paid split.

**Compile policy:** `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` only unless user asks assemble/install.

---

## 0. Goals

| Goal | Meaning |
|------|---------|
| **Free forever useful** | User can install Debian (PRoot), use terminal/shell, git workspace + GitHub, 1 AI CLI, limited XFCE, basic marketplace, 1 project agent workspace |
| **Pro unlocks scale** | All AI CLIs, unlimited XFCE, full marketplace, unlimited multi-project agents, 5+ parallel agent sessions |
| **Local-first** | Never paywall rootfs, shell, apt Software Manager inventory, git porcelain, GH CLI, host scripts, repairs |
| **Play-compliant** | Digital unlocks via **Google Play Billing** only; no external pay links for Pro |
| **Hard to bypass casually** | Entitlements cached + Play verify; gates at UI **and** create/launch paths (not label-only) |

**Out of scope (v1):** ads, account server, multi-tier Pro, team seats, server-side license API, root/chroot-only Pro skus, region price matrix beyond Play defaults.

---

## 1. Product matrix (SSOT)

Prices (USD, Play-managed; final SKUs may differ by region tax):

| Product | Price |
|---------|-------|
| Free | $0 forever |
| Pro monthly | $3.99 / mo |
| Pro yearly | $29.99 / yr (~$2.50/mo) |
| Pro lifetime | $19.99 one-time |

### Feature matrix

| Feature | Free | Pro |
|---------|------|-----|
| Debian PRoot install | ✓ | ✓ |
| Terminal + shell (flux/root) | ✓ | ✓ |
| Git workspace + GitHub | ✓ | ✓ |
| Software Manager (apt inventory) | ✓ | ✓ |
| Host scripts / repairs / onboarding | ✓ | ✓ |
| **1 AI CLI launcher** (pick or fixed; default **codex** on proot; **opencode** if chroot hides codex) | ✓ | ✓ |
| **All AI CLIs** (opencode, codex, claude-code, agy, qwen-code, grok, kiro; chroot still hides codex) | — | ✓ |
| XFCE4 desktop (X11 + VirGL) | **limited** session cap | **unlimited** |
| Marketplace | **basic catalog** | **full catalog** |
| Multi-project agents | **1 project** in agent workspace | **unlimited** |
| Agent sessions / parallel | **1** concurrent AI/agent session | **5+** (default 5, configurable constant) |

### “Stricter free, local mostly free” policy

**Always free (local / core):**

- PRoot/chroot install & switch  
- App terminal + Debian shell (non-AI types `shell`, `shell-root`)  
- Unlimited **non-AI** terminal tabs (optional soft cap later for battery only — not monetisation)  
- Projects as **folders/repos** for browse/git (see §3.4 nuance)  
- Git + GitHub connect  
- Software Manager  
- Marketplace **basic** tier packages  
- One AI CLI install + login + launch  

**Free limits (the paywall):**

| Limit | Free value | Pro value | Notes |
|-------|------------|-----------|--------|
| AI CLI tools unlocked | 1 | all | Install of locked tools blocked or UI-locked with upgrade sheet |
| Concurrent **AI** sessions (app + workspace combined types in `ToolLauncherCatalog.isAiToolType`) | 1 | 5 | Shell sessions do **not** count |
| Projects with **agent workspace open** (multi-project agents) | 1 “active agent project” | unlimited | Free may still create/browse many git projects; only **one** may run agent sessions / multi-tool workspace at a time |
| XFCE continuous runtime | e.g. **30 min / day** wall clock, or **single session ≤ 30 min** then soft-stop + upgrade | unlimited | Prefer **session length + daily budget** so free can “try desktop” |
| Marketplace packages | `tier=basic` only | `basic` + `pro` | Catalog field; unknown tier → basic |

**Stricter than original sketch where it helps conversion without gutting free:**

1. Free AI default: **one tool only** (not “any one of six unlocked permanently without choice” → force explicit **Free AI pick** once, or ship default codex/opencode).  
2. Free XFCE: **hard stop** after budget (not infinite limited quality).  
3. Free parallel agents: **1**, not “soft suggest”.  
4. Free marketplace: hide/lock **pro** packages at list + install runner (not cosmetic badge only).

**Looser on local** vs aggressive freemium apps:

- Do **not** limit shell tabs, rootfs size, apt, clone count, or git ops.  
- Do **not** require Pro for chroot/root.  
- Do **not** watermark terminal.

---

## 2. Code reality (as-of 2026-08-01)

| Area | Current state | Gate hook |
|------|---------------|-----------|
| Billing | **None.** No Play Billing dep. Policy A4 “N/A free utility” | New module + SKUs |
| AI free/paid UI labels | `ToolLauncherCatalog.FREE` = opencode only; `PAID` = codex, agy, claude-code, qwen-code, grok, kiro | Align FREE set to **1 Pro-excluded free tool**; product matrix said codex free — **conflict** with catalog (opencode free, codex paid). Resolve in §3.1 |
| AI suite flag | `AiCliProvisionState` prefs keys | Gate install + launcher visibility |
| Projects | `MainActivity.getProjects/saveProjects/addAndOpenProject` — unbounded list in `nativecode_prefs` | Cap agent project / create path |
| Sessions | `sessionsList` (app), `workspaceSessions` (project); FGS count only | Cap when `isAiToolType` |
| XFCE | `startGui()` / `stopGui()`; `BackgroundService` FGS — no timer | Session budget tracker + auto `stopGui` |
| Marketplace | `MpPackage` has `experimental`, no tier; `PackageInstallRunner` / `MarketplaceClient` | Add `tier`, filter + block install |
| Settings | Hub rows only | Pro status + manage subscription + restore |

**MainActivity size:** gates must live in small SSOT objects (`billing/`, `entitlements/`), not only deep in 18k-line UI.

---

## 3. Architecture

```text
┌────────────────────────────────────────────────────────────┐
│ UI (MainActivity / Settings / sheets)                      │
│  UpgradeSheet · Pro badge · locked tool cards · restore    │
└───────────────────────────┬────────────────────────────────┘
                            │ FeatureGate.can(X)
┌───────────────────────────▼────────────────────────────────┐
│ EntitlementStore  (prefs + memory cache)                   │
│  isPro, productId, expiryMs, source=PLAY|DEBUG|LEGACY      │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│ BillingRepository  (Play Billing Library 7.x)              │
│  query products · launch flow · acknowledge · restore      │
│  SKUs: pro_monthly · pro_yearly · pro_lifetime             │
└────────────────────────────────────────────────────────────┘

Feature consumers (all call FeatureGate / EntitlementStore):
  ToolLauncherCatalog / refreshToolCards
  createNewTerminalSession (AI types)
  addAndOpenProject / openProjectWorkspace (agent project)
  startGui / BackgroundService tick
  MarketplaceClient filter + PackageInstallRunner.prepareInstall
  CliToolsInstaller / Settings AI install list
```

### 3.1 Packages / files (new)

```text
app/src/main/java/com/zenithblue/nativecode/
  billing/
    BillingSku.kt              # product IDs, type SUB vs INAPP
    BillingRepository.kt       # BillingClient wrapper
    BillingConnection.kt       # connect / reconnect
    PurchaseVerifier.kt        # local ack + optional signature (Play)
  entitlements/
    EntitlementStore.kt        # isProEffective(), refresh from purchases
    FeatureGate.kt             # canUseAiTool, maxAiSessions, canStartXfce, …
    FeatureLimits.kt           # FREE_* / PRO_* constants
    FreeAiToolPicker.kt        # free-tier single AI choice prefs
    XfceSessionBudget.kt       # remaining free desktop time
  ui/ (or MainActivity helpers)
    UpgradeBottomSheet.kt      # pricing + buy + restore
```

Gradle:

```kotlin
// app/build.gradle.kts
implementation("com.android.billingclient:billing-ktx:7.1.1") // pin current stable at impl time
```

Play Console (manual, not code):

| SKU | Type | Base plan |
|-----|------|-----------|
| `nativecode_pro_monthly` | subscription | $3.99/mo |
| `nativecode_pro_yearly` | subscription | $29.99/yr |
| `nativecode_pro_lifetime` | one-time managed product | $19.99 |

`isPro` = any active sub **or** owned lifetime.

Debug: `BuildConfig.DEBUG` force-Pro flag via hidden Settings long-press or `adb` prefs — never in release without billing.

### 3.2 FeatureGate API (sketch)

```kotlin
object FeatureLimits {
    const val FREE_AI_TOOLS = 1
    const val FREE_MAX_AI_SESSIONS = 1
    const val FREE_AGENT_PROJECTS = 1
    const val FREE_XFCE_SESSION_MS = 30 * 60_000L
    const val FREE_XFCE_DAILY_MS = 30 * 60_000L
    const val PRO_MAX_AI_SESSIONS = 5
}

object FeatureGate {
    fun isPro(ctx: Context): Boolean
    fun allowedAiToolIds(ctx: Context): Set<String>
    fun canLaunchAiTool(ctx: Context, type: String): GateResult
    fun canOpenAiSession(ctx: Context, currentAiSessionCount: Int): GateResult
    fun canActivateAgentProject(ctx: Context, projectPath: String, agentProjectCount: Int): GateResult
    fun canStartXfce(ctx: Context): GateResult
    fun marketplaceVisible(pkg: MpPackage, ctx: Context): Boolean
    fun canInstallMarketplace(pkg: MpPackage, ctx: Context): GateResult
}

sealed class GateResult {
    data object Allow : GateResult()
    data class Deny(val reason: String, val upsell: UpsellKind) : GateResult()
}
```

All Deny paths open **one** shared Upgrade sheet (copy varies by `UpsellKind`).

### 3.3 AI CLI alignment

**Today:** free UI = opencode; paid = rest.  
**Matrix:** free = 1 CLI e.g. codex; Pro = all 6+.

**Decision (recommended):**

| Policy | Value |
|--------|--------|
| Free default tool | **opencode** on all methods (OSS, works chroot+proot). Alternate: user picks once from `{opencode, codex}` on proot; chroot only opencode if codex hidden |
| Pro | full `CliToolCatalog.forMethod(method)` |
| Install scripts | Free may still run suite installer **but** launchers + auth UI for locked tools show lock; optional: skip installing locked bins to save space |

Refactor `ToolLauncherCatalog`:

- `freeTools(ctx)` → tools in free allowance  
- `proOnlyTools(method)` → rest  
- UI sections: `FREE CLI` / `PRO CLI` (rename from “PAID CLI TOOLS // PRO / SUBSCRIPTION” which today means vendor SaaS, not NativeCode Pro — **rename to avoid confusion**)

Suggested labels:

| Section | Subtitle |
|---------|----------|
| FREE CLI TOOLS | // INCLUDED |
| PRO CLI TOOLS | // NATIVECODE PRO |
| SHELL | unchanged |

Vendor “paid product” wording moves to tool subtitle only.

### 3.4 Multi-project agents (nuance)

“1 project free / unlimited Pro” maps poorly if every git folder is a “project”.

**Definition for gating:**

- **Agent project** = project that has **≥1 AI workspace session** or is marked `agentEnabled` in project JSON.  
- Free: at most **one** such project. Opening AI tool on a second project → Deny + upsell.  
- Free: unlimited projects that only use shell/git/files.  

Hooks:

- `createNewTerminalSession(type)` when `ToolLauncherCatalog.isAiToolType(type)`  
- `addAndOpenProject` — no block  
- Workspace tool cards — same as app terminal  

Optional later: free max **N total projects** — **not** in v1 (local-first).

### 3.5 Parallel agent sessions

Count:

```text
aiCount = sessionsList.count { isAiToolType(it.type) }
        + workspaceSessions.count { isAiToolType(it.type) }
```

Before create:

- Free: `aiCount >= 1` → Deny  
- Pro: `aiCount >= 5` → soft Deny (“max 5 on Pro”) or allow with warning — default **hard 5** for stability  

Shell sessions unlimited.

### 3.6 XFCE session cap

| Piece | Behavior |
|-------|----------|
| Free start | `canStartXfce` if remaining daily budget > 0 |
| While running | `XfceSessionBudget` ticks (AlarmManager / FGS-safe handler in `BackgroundService`) |
| Budget hit | auto `stopGui()`, notification “Free desktop time used — Upgrade for unlimited” |
| Pro | no tick / infinite |
| Persistence | prefs: `xfce_day_key`, `xfce_used_ms`, `xfce_session_start_ms` |

Defaults: **30 min/day** total free (stricter, clear). Document in UI before first START XFCE.

### 3.7 Marketplace basic vs full

Extend catalog schema (`nativecode-marketplace` repo + app parser):

```json
{
  "id": "blender",
  "tier": "pro",
  ...
}
```

| `tier` | Free | Pro |
|--------|------|-----|
| `basic` or missing | show + install | show + install |
| `pro` | show locked row or hide (pref: **show locked** for conversion) | install |
| `experimental` | unchanged flag; can stack with tier |

App:

- `MpPackage.tier: MarketplaceTier`  
- `MarketplaceClient.parseCatalog`  
- Filter in list adapter + `PackageInstallRunner.prepareInstall` hard check  

**Basic catalog seed (suggested):** small/dev utilities, glmark2-class demos, docs tools.  
**Pro catalog:** heavy/emulation/GPU apps (box64, FEX, Blender, large runtimes).

Software Manager (apt list) stays fully free.

### 3.8 Entitlement refresh

| Event | Action |
|-------|--------|
| App start | `BillingRepository.queryPurchases` → `EntitlementStore` |
| Purchase | acknowledge + set Pro |
| Restore | same query |
| Sub expire | Play returns inactive → Free limits apply next gate check |
| Offline | last known Pro cache with `cacheExpiry` (e.g. 7 days grace) then degrade to Free |

No custom license server in v1.

---

## 4. UX surfaces

| Surface | Behavior |
|---------|----------|
| Settings Hub | **NativeCode Pro** card: status, price teaser, Manage / Upgrade / Restore |
| Locked AI card | lock icon; tap → Upgrade sheet (feature = AI_TOOL) |
| 2nd AI session | toast/sheet before session create |
| 2nd agent project | sheet when launching AI in another project |
| XFCE start | if low budget, confirm remaining minutes; if 0, Upgrade |
| XFCE end | system notif + optional in-app banner |
| Marketplace pro pkg | badge PRO; CTA Upgrade |
| Onboarding | do **not** hard-sell mid-install; optional soft “Pro unlocks all AI CLIs” on AI consent page only |
| Play subscription center | deep link `https://play.google.com/store/account/subscriptions` for manage |

Upgrade sheet copy: matrix bullets + 3 price buttons (mo / yr / lifetime) + Restore + Privacy/Terms links.

---

## 5. Policy / store / legal

| Item | Action |
|------|--------|
| Checklist A4 | Flip to **Play Billing required**; evidence = BillingClient + SKUs |
| Data safety | Add purchase / Google Play billing if required by form |
| Privacy policy | Note subscription status processed by Google; no card data in app |
| Listing | Free app + in-app products; declare subscriptions |
| External payments | **Forbidden** for digital Pro unlock |
| Refunds | Play-managed |

---

## 6. Implementation phases (PR plan)

### PR1 — Entitlement core (no Play yet)

- `FeatureLimits`, `FeatureGate`, `EntitlementStore` with `DEBUG` / prefs override `force_pro`  
- Wire gates (no-op Deny UI → Toast “Pro required (billing soon)”) behind `BuildConfig` flag `MONETISATION_ENFORCE=false` default  
- Unit-style pure Kotlin tests for gate math if test source exists; else manual checklist  

**Files:** new `entitlements/*`; touch session create, startGui, add project agent path, marketplace install, tool cards.

### PR2 — Align AI free/pro catalog

- Resolve free tool SSOT (`opencode` default + optional picker)  
- Rename UI sections FREE vs NATIVECODE PRO  
- Gate install/auth for locked tools  

**Files:** `ToolLauncherCatalog.kt`, `MainActivity` tool sections, `CliToolsInstaller` / Settings AI list, `AiCliProvisionState` optional.

### PR3 — Session + agent project + XFCE budgets

- AI session counter gate  
- Agent project single-slot free  
- `XfceSessionBudget` + `BackgroundService` timer + auto stop  

**Files:** `MainActivity` session create, `BackgroundService`, new budget class.

### PR4 — Marketplace tiers

- Schema `tier` in marketplace repo  
- Parser + UI lock + install hard deny  
- Tag existing catalog packages  

**Files:** marketplace module; external catalog repo.

### PR5 — Play Billing + Upgrade UI

- Billing dependency, SKUs, purchase/restore  
- Settings Pro card + Upgrade sheet  
- `MONETISATION_ENFORCE=true` for release  
- Update policy docs A4 + privacy  

**Files:** `billing/*`, `app/build.gradle.kts`, Settings hub, privacy/checklist.

### PR6 — Polish / analytics-free QA

- Restore purchase edge cases  
- Grace period offline  
- Copy review, empty states  
- Manual QA matrix (§7)  

**Deps:** PR1 → PR2/PR3/PR4 parallel after PR1 → PR5 needs PR1 + Play Console SKUs → PR6.

---

## 7. Manual QA matrix

| # | Case | Free expect | Pro expect |
|---|------|-------------|------------|
| 1 | Fresh install, Debian + shell | works | works |
| 2 | Git clone + GH login | works | works |
| 3 | Launch free AI only | works | works |
| 4 | Launch second AI tool | upgrade | works |
| 5 | Two AI sessions | blocked | up to 5 |
| 6 | Shell tabs ×10 | works | works |
| 7 | AI in project A then B | block B agent | works |
| 8 | XFCE 0 budget | block | unlimited |
| 9 | XFCE mid-session expire | auto stop | n/a |
| 10 | Marketplace basic install | works | works |
| 11 | Marketplace pro install | blocked | works |
| 12 | Buy monthly | → Pro | — |
| 13 | Expire / cancel sub | → Free limits | — |
| 14 | Lifetime purchase | permanent Pro | — |
| 15 | Restore after reinstall | Pro returns | — |
| 16 | Airplane mode + cached Pro | grace works | — |
| 17 | chroot: codex hidden | still hidden | still hidden |

---

## 8. Key decisions

| # | Decision | Rationale |
|---|----------|-----------|
| K1 | Local core always free | Trust + Play “utility” narrative + conversion from real use |
| K2 | Gate AI **count/concurrency**, not shell | Monetise agent product, not Linux |
| K3 | Free = 1 AI tool (default opencode; optional codex on proot) | Matches “1 CLI free”; chroot-safe |
| K4 | Projects unlimited; **agent projects** capped | Avoid punishing multi-repo git |
| K5 | XFCE time budget not “degraded quality” | VirGL quality hard to tier; time is honest |
| K6 | Marketplace `tier` field | Server/catalog-side flexibility without app release |
| K7 | Play Billing only | Policy §6.1 |
| K8 | Lifetime SKU at $19.99 | Cheap land; yearly for recurring; monthly for trial intent |
| K9 | SSOT `FeatureGate` | MainActivity too large for scattered checks |
| K10 | Rename “PAID CLI” UI | Avoid vendor SaaS vs NativeCode Pro confusion |

---

## 9. Open questions (need product call)

1. **Free AI tool:** fixed opencode vs user-pick once vs fixed codex (proot-only)?  
2. **XFCE budget:** 30 min/day vs 30 min/session vs 60 min/week?  
3. **Marketplace pro packages:** show locked vs hide entirely?  
4. **Lifetime install of locked AI:** allow silent install of all bins vs free installs only free tool?  
5. **Pro session cap 5:** hard limit or soft?  
6. **Grandfather:** existing users all Free with promo lifetime?  
7. **Price final:** confirm $3.99 / $29.99 / $19.99 vs regional experiments?

---

## 10. Risks

| Risk | Mitigation |
|------|------------|
| Root/shell bypass of marketplace scripts | Accept guest freedom; gate **app UI install path**; don’t claim secure DRM |
| Users install AI CLIs via apt/npm in shell | Expected; Pro sells **integrated launchers, multi-session UX, desktop time, catalog** — not crypto lock on bins |
| Play policy rejection | Billing only, clear subscription disclosure, no external pay |
| MainActivity merge conflicts | Thin gate calls; avoid large refactors in same PR as billing |
| Offline false Free | 7-day Pro cache grace |
| Lifetime vs sub double-own | Prefer highest entitlement; don’t double charge UX |

---

## 11. Success metrics (post-ship)

- Free → Pro conversion rate (7d / 30d)  
- Which upsell surface converts (AI lock vs XFCE vs marketplace vs sessions)  
- Free XFCE budget hit rate  
- Refund rate / sub cancel at day 1  

(No analytics SDK required for v1; Play Console + optional later.)

---

## 12. PR Plan (summary)

| PR | Title | Depends |
|----|-------|---------|
| 1 | Entitlements + FeatureGate scaffold | — |
| 2 | AI free/pro catalog + launcher gates | 1 |
| 3 | AI session / agent project / XFCE budget | 1 |
| 4 | Marketplace tier field + install gate | 1 |
| 5 | Play Billing + Upgrade UI + policy docs | 1–4 (SKUs parallel) |
| 6 | QA polish, grace period, enforce flag on | 5 |

---

## 13. Suggested constant defaults (v1 ship)

```text
FREE_AI_TOOL_IDS        = { opencode }   // or picker
FREE_MAX_AI_SESSIONS    = 1
PRO_MAX_AI_SESSIONS     = 5
FREE_AGENT_PROJECTS     = 1
FREE_XFCE_DAILY_MS      = 30 * 60 * 1000
PRO_GRACE_OFFLINE_MS    = 7 * 24 * 60 * 60 * 1000
SKU_MONTHLY             = nativecode_pro_monthly
SKU_YEARLY              = nativecode_pro_yearly
SKU_LIFETIME            = nativecode_pro_lifetime
```

---

*End of plan. Implementation starts only after user approves open questions §9 and PR1.*
