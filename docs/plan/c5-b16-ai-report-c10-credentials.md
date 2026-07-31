# C5 / B16 — AI report UI + third-party ToS · C10 — credentials clear UX

**Date:** 2026-07-31  
**Status:** implemented (code) — device verify §10 remaining  
**Policy:** Play §3.14 AI-Generated Content (**C5**, **B16**); §4.1 / deletion UX (**C10**)  
**Related:**  
- `docs/policy/Google_Play_Store_Policy_Compliance_Checklist.md` (C5, B16, C10)  
- `docs/plan/c6-onboarding-remote-install-consent.md` (AI suite gate; deferred C5)  
- `docs/plan/ai-cli-tools-browser-login.md` (Settings → AI CLI tools)  
- `docs/privacy-policy.md` §8 AI-generated content, §9 deletion  
- `docs/project/ui_design.md` (cyber-brutalist)  

**Primary code (existing):**  
| Path | Role |
|------|------|
| `MainActivity.kt` | Settings hub, `ID_CLI_AUTH` page, GH logout, `showBrutalistConfirmDialog`, `settingsHubNavRow`, `glassCard`, `primaryButton` / `secondaryButton` |
| `cliauth/CliToolCatalog.kt` | Tool defs (`id`, `displayName`, `consoleUrl`, …) |
| `cliauth/CliAuthService.kt` | Per-tool `logout`, env clear, `invalidateCache` |
| `cliauth/AiCliProvisionState.kt` | Suite provision flag + `clearAiCliProvisioned` |
| `github/GitHubCliService.kt` | `logout` (guest `gh auth logout`) |
| `OnboardingActivity.kt` | Plan consent copy (third-party scripts); no AI safety report page yet |
| `docs/privacy-policy.md` | Text mentions report-to-vendor + deletion table |

---

## 1. Goal

### 1.1 C5 / B16 — Report + disclaimer

1. **Settings hub card** → dedicated **AI Safety & Report** page (not buried in Privacy Policy browser).  
2. **In-app report path** to developer: **`mailto:zenithblue.dev@gmail.com`** with prefilled subject/body (app version, isolation method, tool id, user description).  
3. **Per-vendor report / safety links** for every tool in `CliToolCatalog` (open browser).  
4. **Third-party ToS / AUP disclaimer** on that page (and short pointer on AI CLI tools page).  
5. Frame honestly: NativeCode **launches** third-party guest CLIs; models/outputs are **vendor** responsibility; app still provides a **reporting entry** so Play §3.14 is satisfied for launcher-style AI.

### 1.2 C10 — Clear / sign-out UX (delta only)

**Already present (do not rebuild):**  
| Surface | Behavior |
|---------|----------|
| Settings → **GitHub Account** card | `LOGOUT` → `GitHubCliService.logout` + cache invalidate |
| Settings → **AI CLI tools** per-tool card | `LOGOUT` when signed in → `CliAuthService.logout` (guest cmd + env key wipe) |
| Privacy policy §9 | Clear storage / uninstall / disconnect table |

**Still required for C10 “followed”:**  
1. **AI CLI tools page** — explicit **Clear all AI credentials** (current `linux_method`) with `showBrutalistConfirmDialog`.  
2. **AI CLI tools page** — optional **Clear AI suite flag** (host prefs only; does not uninstall bins) + note that full wipe = Android clear storage / proot-chroot uninstall.  
3. **AI Safety page or Privacy card** — short **“How to delete data”** bullets pointing at GH logout, AI logout/clear, clear storage, uninstall.  
4. Privacy policy contact email → **`zenithblue.dev@gmail.com`** (align with report).  

### Success definition

| Before | After |
|--------|--------|
| No report/flag UI | Settings → AI Safety & Report page with mailto + vendor links |
| No per-vendor safety URLs in product | Catalog SSOT drives UI rows |
| C10 gap = “no clear guidance” | Clear-all AI creds on AI tools page; data-deletion copy visible |
| Privacy contact GitHub-only | + developer email |
| Checklist C5 / B16 / C10 open | Mark FOLLOWED / mitigated after device verify |

---

## 2. Non-goals

| Out | Why |
|-----|-----|
| Hosting model inference / moderation filters | App is CLI launcher, not chat product |
| Server-side report intake API | mailto + vendor forms only for v1 |
| Per-message flag inside terminal transcript | No first-party chat UI; overkill |
| Uninstalling AI package bins from Settings v1 | Separate “remove suite” script; v1 = creds + provision flag |
| Changing GH OAuth / CLI login flows | Already shipped |
| Marketplace / rootfs C6 residual | Separate |
| Age gate UI | Console IARC already set; copy only if needed |

---

## 3. Research — vendor report / safety URLs (2026-07)

Sources: public vendor support / legal pages (verify once more at implement time).

| Tool id | Product | Report / safety | ToS / AUP |
|---------|---------|-----------------|-----------|
| `claude` | Claude Code (Anthropic) | Form: `https://claude.com/form/anthropic-content-reporting` · safety: `usersafety@anthropic.com` · help: [Reporting content](https://support.claude.com/en/articles/7996906-reporting-blocking-and-removing-content-from-claude) | `https://www.anthropic.com/legal/aup` · terms: `https://www.anthropic.com/legal/consumer-terms` |
| `codex` | OpenAI Codex | Form: `https://openai.com/form/report-content/` | `https://openai.com/policies/usage-policies` · terms: `https://openai.com/policies/terms-of-use` |
| `grok` | Grok / xAI | In-product Report Issue; email: `support@x.ai` (AUP) | `https://x.ai/legal/terms-of-service` · `https://x.ai/legal/acceptable-use-policy` |
| `agy` | Antigravity (Google OAuth) | General: `https://reportcontent.google.com` · likeness: `https://reportcontent.google.com/forms/ai-likeness-abuse` | Generative AI: `https://policies.google.com/terms/generative-ai` |
| `qwen` | Qwen Code (Alibaba Cloud) | Console/support for Bailian / Model Studio (no single public “report AI” form found) — use console URL + mailto fallback to developer with tool tagged `qwen` | Alibaba Cloud terms linked from console; store `consoleUrl` as WEB + generic “use vendor account support” |
| `opencode` | OpenCode | No first-class public report form found — `https://opencode.ai` + GitHub project issues if linked from site; always offer NativeCode mailto | Site terms if present; else disclaimer-only |
| `kiro` | Kiro CLI | AWS / Kiro app support (`https://app.kiro.dev`); no dedicated public gen-AI report form found — mailto developer + WEB to app | AWS customer agreement / Kiro product terms via app |

**NativeCode report (required):**  
- **Email:** `zenithblue.dev@gmail.com`  
- **Subject template:** `[NativeCode AI Report] {toolId} · {appVersion}`  
- **Body template (prefill):**  
  ```
  App version: {versionName} ({versionCode})
  Isolation: {proot|chroot}
  Tool: {displayName} ({id})
  Category: {user pick: harmful / illegal / privacy / other}
  Description:
  {user text}

  Note: Outputs come from third-party CLI vendors. This report is to the app developer.
  Vendor report links: Settings → AI Safety & Report.
  ```

**Intent:** `ACTION_SENDTO` + `mailto:` URI with `subject` / `body` query params; fallback Toast if no mail app.

---

## 4. Existing code inventory (reuse)

### 4.1 Settings navigation pattern (reuse as-is)

- `settingsHubNavRow(icon, title, subtitle, pageId)` — Software Manager / Marketplace style.  
- Dedicated page: `ScrollView` + top back bar → `navigateToPage(ID_SETTINGS, false)`.  
- IDs free after `ID_CLI_AUTH = 19` → use **`ID_AI_SAFETY = 20`**.

### 4.2 Cards / dialogs

- `glassCard()`, `sectionHeader()`, `infoRow()`, `textBadge()`, `spacer()`, `primaryButton` / `secondaryButton`.  
- `showBrutalistConfirmDialog(...)` for destructive clear-all.  
- `buildPrivacyPolicyCard()` external browser pattern for WEB links.  
- `CliAuthService.openBrowser(ctx, url)` already exists.

### 4.3 Auth / clear already wired

- GH: `performGithubLogout()` / `GitHubCliService.logout`.  
- CLI: `performCliToolLogout(def)` / `CliAuthService.logout` (clears env keys; tool-specific guest logout cmds for codex/opencode/kiro/claude).  
- Cache: `CliAuthService.invalidateCache(method)`.  
- Suite flag: `AiCliProvisionState.clearAiCliProvisioned(ctx, method)`.

### 4.4 Gaps

| Gap | Fix in this plan |
|-----|------------------|
| No safety catalog fields | New `AiVendorSafetyCatalog` (or extend `CliToolDef`) |
| No report page / hub row | New page + nav row |
| No mailto report composer | Small helper `ReportMailHelper` / method on catalog |
| No clear-all AI creds | Button on AI CLI page |
| No in-app “how to wipe data” | Section on AI Safety page |
| Privacy contact email | Doc + optional Settings line |

---

## 5. Architecture — decoupled reusable pieces

Prefer **new small files** under `cliauth/` (and thin MainActivity glue). Avoid growing `MainActivity.kt` (already ~17k LOC) with URL strings.

```
cliauth/
  CliToolCatalog.kt          # keep tools; optional thin link to safety by id
  AiVendorSafetyCatalog.kt   # NEW — SSOT report/ToS per toolId + nativecode contact
  ReportMailHelper.kt        # NEW — build mailto Uri, start activity, version resolve
  CredentialClearService.kt  # NEW — clearAllAiCredentials(method), optional suite flag
  (existing) CliAuthService  # keep single-tool logout; CredentialClear calls it
```

### 5.1 `AiVendorSafetyCatalog`

```kotlin
data class VendorSafetyLinks(
    val toolId: String,           // matches CliToolDef.id; "nativecode" for app row
    val displayName: String,
    val reportUrl: String? = null,      // https form
    val reportMailto: String? = null,   // e.g. support@x.ai
    val tosUrl: String? = null,
    val aupUrl: String? = null,
    val notes: String = ""              // short mono subtitle
)

object AiVendorSafetyCatalog {
    const val NATIVECODE_REPORT_EMAIL = "zenithblue.dev@gmail.com"
    val ALL: List<VendorSafetyLinks>
    fun forTool(toolId: String): VendorSafetyLinks?
    fun forMethod(method: String): List<VendorSafetyLinks> // hide codex on chroot via CliToolCatalog.forMethod
}
```

**Rule:** UI never hardcodes vendor URLs. Change catalog → page rebuilds.

### 5.2 `ReportMailHelper`

```kotlin
object ReportMailHelper {
    fun openNativeCodeReport(
        ctx: Context,
        toolId: String?,
        category: String,
        description: String
    ): Boolean  // false if no activity

    fun openVendorMailto(ctx: Context, address: String, toolLabel: String): Boolean
}
```

- Resolve version via `PackageManager.getPackageInfo`.  
- Isolation via `LinuxCommandBuilder.currentMethod`.  
- No network, no logging of description to disk (privacy); optional local SharedPreferences counter “last_report_at” only if we want analytics-free rate hint — **skip v1**.

### 5.3 `CredentialClearService`

```kotlin
object CredentialClearService {
    /** Logout every tool in CliToolCatalog.forMethod; invalidate cache. */
    fun clearAllAiCredentials(ctx: Context, method: String, onDone: (ok: Boolean, msg: String) -> Unit)

    /** Host prefs only — hides AI launchers until re-install. */
    fun clearAiSuiteProvisionFlag(ctx: Context, method: String)

    // Does NOT wipe rootfs / projects.
}
```

Implementation: sequential or parallel `CliAuthService.logout` per id; aggregate result on main handler. Reuse executor patterns from `CliAuthService`.

### 5.4 UI builders (MainActivity private or small extract)

If extract later: `ui/SettingsPageChrome.kt` — not required for v1.  
v1 stays MainActivity methods mirroring Proot/Chroot settings:

| Method | Role |
|--------|------|
| `buildAiSafetySectionButton()` | Hub nav row |
| `buildAiSafetyPage()` | Scroll content once |
| `paintAiSafetyVendorList()` | Rebuild rows from catalog |
| `showAiReportComposer(toolId?)` | Category + description → mailto |
| AI CLI page: `buildClearCredentialsCard()` | Clear all + suite flag |

**Shared visual atoms only** — already exist (`glassCard`, buttons, confirm dialog).

---

## 6. UX wireframes

### 6.1 Settings hub order (suggested)

```
… GUI / Terminal / Environment …
GitHub Account              (existing card w/ LOGOUT)
AI CLI TOOLS                (existing → ID_CLI_AUTH)
AI SAFETY & REPORT          (NEW → ID_AI_SAFETY)
Proot Settings …
…
Privacy Policy              (existing external md)
```

Nav row copy:  
- **Title:** `AI SAFETY & REPORT`  
- **Subtitle:** `Flag output · vendor ToS · contact`  
- **Icon:** `ic_shield_thick` (already used by Privacy)

### 6.2 Page: AI Safety & Report (`ID_AI_SAFETY`)

```
[←] AI SAFETY & REPORT          [PROOT|CHROOT badge]

┌ glassCard — DISCLAIMER ─────────────────────────────────┐
│ Third-party AI CLIs generate outputs under their own    │
│ terms. NativeCode does not host the model. You are      │
│ responsible for prompts and use. Age: adult/pro use.    │
└─────────────────────────────────────────────────────────┘

┌ glassCard — REPORT TO NATIVECODE ───────────────────────┐
│ Email: zenithblue.dev@gmail.com                         │
│ [ REPORT ISSUE ]  → composer sheet → mailto             │
│ Use for: app bugs that surface AI tools; safety concern │
│ you want developer awareness of. Prefer vendor form     │
│ when the issue is model output itself.                  │
└─────────────────────────────────────────────────────────┘

┌ glassCard — REPORT / ToS BY VENDOR ─────────────────────┐
│ for each tool in CliToolCatalog.forMethod(method):      │
│   Claude Code                                           │
│   · Anthropic content form                              │
│   [ REPORT ] [ ToS ] [ AUP ]     (buttons that exist)   │
│   …                                                     │
│ Grok: REPORT opens mailto:support@x.ai if no form URL   │
└─────────────────────────────────────────────────────────┘

┌ glassCard — YOUR DATA / SIGN-OUT (C10 pointer) ─────────┐
│ · GitHub: Settings hub → LOGOUT                         │
│ · AI keys/sessions: AI CLI tools → LOGOUT / CLEAR ALL   │
│ · Full wipe: Android App info → Clear storage           │
│ · Uninstall removes app private data                    │
│ · Chroot on shared storage: Chroot Settings uninstall   │
│ [ OPEN AI CLI TOOLS ]  [ PRIVACY POLICY ]               │
└─────────────────────────────────────────────────────────┘
```

**Composer sheet** (brutalist dialog, not Material):  
- Spinner/chips: `Harmful content` / `Illegal content` / `Privacy` / `Other`  
- Multi-line description (optional but encouraged)  
- Tool dropdown optional (All / each catalog tool)  
- Confirm → `ReportMailHelper.openNativeCodeReport`

### 6.3 Page: AI CLI tools (`ID_CLI_AUTH`) — C10 delta

Keep install card + per-tool login/logout.

Add **after** install card / before tool list:

```
┌ glassCard — CREDENTIALS ────────────────────────────────┐
│ Sign-out removes local tokens/keys for this isolation.  │
│ Does not delete Debian packages or projects.            │
│ [ CLEAR ALL AI CREDENTIALS ]  (destructive confirm)     │
│ [ CLEAR SUITE FLAG ] (optional secondary)               │
│   → AiCliProvisionState.clear; rebuild terminal tools   │
│ [ AI SAFETY & REPORT ] → push ID_AI_SAFETY              │
└─────────────────────────────────────────────────────────┘
```

**Clear all confirm message:**  
> Logs out every AI CLI for **{METHOD}** and deletes stored API keys under guest `cli-auth.env` for those tools. GitHub login is separate. Continue?

**Clear suite flag confirm:**  
> Hides Free/Paid AI launchers until you Install AI CLI tools again. Bins may still exist in guest. Continue?

After clear: `refreshCliAuthPage(force=true)`, `refreshToolCardsForMethod()` / whatever rebuilds terminal+workspace (same as post-install path used by C6).

### 6.4 Onboarding (light touch — optional same PR)

Plan page already mentions third-party scripts. Add **one line** under AI checkbox:

> AI outputs are from third-party vendors. Report: Settings → AI Safety after setup.

No new onboarding page required for C5.

---

## 7. File change list

| File | Change |
|------|--------|
| `cliauth/AiVendorSafetyCatalog.kt` | **NEW** SSOT links + email const |
| `cliauth/ReportMailHelper.kt` | **NEW** mailto builder |
| `cliauth/CredentialClearService.kt` | **NEW** clear-all + suite flag |
| `cliauth/CliToolCatalog.kt` | Optional: no structural change if safety is separate by id |
| `MainActivity.kt` | `ID_AI_SAFETY`, hub row, page build, clear card on CLI auth, navigate wiring, back stack |
| `docs/privacy-policy.md` | Contact email; §8/§9 cross-link Settings paths |
| `docs/policy/Google_Play_Store_Policy_Compliance_Checklist.md` | After ship: C5/B16/C10 status + evidence |
| `OnboardingActivity.kt` | Optional one-line disclaimer |

**No** new Activities if pattern stays single-activity pages (consistent with Proot/Chroot/CLI auth).

---

## 8. Implementation phases

### Phase A — Catalog + helpers (no UI)
1. Add `AiVendorSafetyCatalog` with research URLs.  
2. Add `ReportMailHelper` unit-smoke via manual device (no network test harness required).  
3. Add `CredentialClearService` calling existing `CliAuthService.logout`.

### Phase B — AI Safety page
1. `ID_AI_SAFETY`, `buildAiSafetyPage`, hub `settingsHubNavRow`.  
2. Disclaimer + NativeCode report + vendor list + data pointer.  
3. Composer → mailto.

### Phase C — C10 on AI CLI page
1. Credentials card: clear all + suite flag + deep link to safety page.  
2. Wire rebuild of terminal/workspace after suite clear (reuse C6 refresh).  

### Phase D — Docs
1. Privacy contact + path names.  
2. Checklist evidence after device pass.

### Phase E — Verify
1. Compile `:app:compileDebugKotlin`.  
2. Device checklist §10.

---

## 9. Acceptance criteria

### C5 / B16
- [ ] Settings shows **AI SAFETY & REPORT** card/row.  
- [ ] Page opens; back returns to Settings.  
- [ ] Disclaimer visible without scroll-past-legal trap (above fold OK).  
- [ ] **REPORT ISSUE** opens mail app to `zenithblue.dev@gmail.com` with version + method + text.  
- [ ] Each visible tool has at least one of: REPORT URL, REPORT mailto, or explicit “use vendor account” note + NativeCode mailto still available.  
- [ ] ToS/AUP buttons open browser where URLs exist.  
- [ ] Chroot hides Codex row (same as catalog).  
- [ ] No crash if no browser / no mail client (Toast).

### C10
- [ ] GitHub LOGOUT still works (regression).  
- [ ] Per-tool LOGOUT still works (regression).  
- [ ] **CLEAR ALL AI CREDENTIALS** confirms, logs out all tools for method, probes show signed out.  
- [ ] **CLEAR SUITE FLAG** hides AI launchers in Terminal/Workspace until reinstall.  
- [ ] Data deletion guidance visible in-app (Safety page).  
- [ ] Privacy policy lists developer email.

### Non-functional
- [ ] Vendor URLs only in `AiVendorSafetyCatalog`.  
- [ ] MainActivity adds page chrome only; no URL spaghetti.  
- [ ] Cyber-brutalist only (`showBrutalistConfirmDialog`, glass cards).

---

## 10. Device test script

1. Cold open → Settings → AI SAFETY & REPORT → read disclaimer.  
2. Report Issue → pick category → send → confirm draft mail fields.  
3. Tap Claude REPORT → browser form; Codex REPORT → OpenAI form; Grok → mail or AUP.  
4. AI CLI tools → login one tool → CLEAR ALL → confirm signed out.  
5. CLEAR SUITE FLAG → Terminal shows shells only.  
6. Install AI suite again → launchers return.  
7. GitHub connect → logout still OK.  
8. Rotate method proot↔chroot → safety list filters codex; credentials clear is method-scoped.

---

## 11. Policy checklist update (post-implement)

| Item | Target status | Evidence line |
|------|---------------|---------------|
| **C5** | FOLLOWED (code) | Settings → AI Safety & Report; mailto `zenithblue.dev@gmail.com`; vendor forms |
| **B16** | FOLLOWED / MITIGATED | Report UI + ToS disclaimer; still third-party generators |
| **C10** | FOLLOWED (code) | GH logout (existing); AI clear-all; in-app deletion guidance; privacy email |

Part E checkboxes 12–13 when verified.

---

## 12. Risks / residual

| Risk | Mitigation |
|------|------------|
| Play still wants in-session flag on every AI response | Document launcher model in reviewer notes; report entry is best-effort for CLI surface |
| Vendor URLs rot | Single catalog file; re-check before Play submit |
| mailto body length limits | Truncate description ~2–4k chars |
| Clear-all incomplete for tools without logout cmd | Always wipe env keys; guest leftover session files noted in UI |
| User confuses suite flag clear with uninstall | Copy explicitly: bins may remain |

---

## 13. Open decisions (defaults if silent)

| # | Question | Default |
|---|----------|---------|
| D1 | Separate Activity vs page-in-MainActivity | **Page** (`ID_AI_SAFETY`) |
| D2 | Extend `CliToolDef` vs separate safety catalog | **Separate catalog** (decoupled) |
| D3 | Clear suite flag on same card as clear creds | **Yes** (two buttons) |
| D4 | Onboarding full ToS page | **No** — one line only |
| D5 | Log reports locally | **No** v1 |
| D6 | Report email | **`zenithblue.dev@gmail.com`** (user-specified) |

---

## 14. Effort estimate

| Phase | Size |
|-------|------|
| A helpers/catalog | S |
| B safety page | M |
| C clear-all on AI page | S |
| D docs | S |
| E device verify | S |

Total ~0.5–1 day focused impl + device pass.

---

## 15. Implementation order (when approved)

1. Catalog + helpers  
2. Safety page + hub row  
3. AI CLI credentials card  
4. Privacy policy email  
5. Compile + device §10  
6. Checklist mark FOLLOWED  

**Implemented:** catalog/helpers + AI Safety page + CLI credentials card + privacy/onboarding/checklist. Device §10 still manual.

