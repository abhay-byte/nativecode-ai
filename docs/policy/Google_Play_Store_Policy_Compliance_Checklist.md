# NativeCode — Google Play Policy Compliance Checklist

**App:** NativeCode (`com.ivarna.nativecode`)  
**Scope:** Codebase + packaging audit against [Google Play Store Policy Compliance Guide](https://raw.githubusercontent.com/abhay-byte/abhay-kb/refs/heads/main/Google_Play_Store_Policy_Compliance_Guide.md) (compiled July 25, 2026; DPP effective May 27, 2026).  
**Audit date:** 2026-07-31 (rev: **B11 residual ACCEPTED** after Play research; C5/B16/C10 shipped; Console ops prior)  
**Primary tree:** `app/` (+ merged modules `termux-x11`, Termux deps)

> This is an internal engineering checklist, not legal advice. Re-verify against live [Policy Center](https://support.google.com/googleplay/android-developer/topic/9858052) before submission. **Play Console ops** (privacy URL, Data safety, FGS declaration, IARC, listing, Financial Features, App content) treated as **done per developer** as of this rev. Remaining items are **code / residual product risk**.

**Status legend**

| Status | Meaning |
|--------|---------|
| **FOLLOWED** | Repo / design clearly satisfies the rule |
| **PARTIAL** | Some work done, incomplete, or residual product risk |
| **NOT FOLLOWED** | Missing or actively conflicting with policy as implemented |
| **MITIGATED** | Risk reduced (e.g. user-initiated + default-off); not eliminated |

---

## Summary scorecard

| Bucket | Count (approx.) | Highest-risk items |
|--------|-----------------|--------------------|
| Followed | ~48 | Console ops; C1–C5, C7–C12; C10; B13–B15; B19; B20 optional; A1–A28 |
| Partial / mitigated | ~1 | **B16** launcher residual |
| Not followed | ~0 | — |
| Accepted residual | B11 + C6 | Guest terminal/log + user-tap guest scripts — **OK** (Play research 2026-07-31; peer apps on store) |

**Shipped this rev (code):** Settings → AI Safety & Report (mailto `zenithblue.dev@gmail.com` + vendor ToS/report); AI CLI **CLEAR ALL** / suite flag; privacy §8–9 + email; nested back stack + `currentPageId` so Settings bottom nav returns correctly.

**Console / ops — DONE (developer confirmed):** privacy URL, Data safety, FGS declaration+video, IARC/audience, Financial Features, store listing, App content aggregate.

### What's left (open residual)

| ID | Topic | Status | Priority | Notes |
|----|--------|--------|----------|-------|
| **B11** | Guest shell + guest-side package/scripts | **MITIGATED / ACCEPTED** | closed residual | Terminal log + user-run guest install **OK**. No Play rejection pattern for this alone (UserLAnd/Andronix/Termux-class). Not host DEX/SO. |
| **C6** | Remote guest provisioning | **MITIGATED / ACCEPTED** | closed residual | AI default OFF + consent; rootfs assets; network only after user tap. |
| **B12** | FileProvider path width | **FOLLOWED** | — | Share-only `file_paths.xml` + stage `getFileUri` (device smoke optional). |
| **B13** | `allowBackup` + secrets in app storage | **FOLLOWED** | — | `allowBackup="false"` (2026-07-31). |
| **B14** | termux-x11 exported activities/receivers | **FOLLOWED** | — | X11 MainActivity / LoriePreferences / Receiver `exported=false` (NativeCode-only). |
| **B15** | Privileged / noisy perms in modules | **FOLLOWED** | — | Merged APK has no WRITE_SECURE / REQUEST_INSTALL (see B15). |
| **B16** | AI gen-content controls | **MITIGATED / FOLLOWED (code)** | residual | Report UI done; no per-message terminal flags (launcher model). |
| **B19** | Third-party CLI installer ToS | **FOLLOWED** | — | Official vendor install + official login only (see B19). |
| **B20** | Release hardening (R8 off, secrets in Gradle) | **FOLLOWED (optional)** | — | Optional hygiene; accepted deferred for this rev. |
| — | Device smoke C5/C10 | optional | before ship | Mail app, vendor link, clear-all, suite flag, CLI→Safety→back nav. |
| **P2-21** | Marketplace first-use consent | **FOLLOWED (code)** | — | `MarketplaceConsent` + dialog before open; full script signing still optional later. |

**Done / not open:** C5 · C10 · B12 · **B13** allowBackup=false · **B14** X11 not exported · **B15** merged perms clean · B19 · B20 optional · C1–C4 · C7–C9 · C11–C12 · Console · **B11/C6 ACCEPTED**.

---

# Part A — FOLLOWED

Items below are **followed** relative to the guide and current code.

---

### A1. Target API level (API 36)

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §11.4 Target API Level Policy |
| **Status** | **FOLLOWED** |
| **Evidence** | `app/build.gradle.kts`: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` |
| **Reason** | Meets current floor and the Aug 31, 2026 API 36 requirement for new apps/updates. |
| **How to keep fixed** | On every major Android release, bump `compileSdk`/`targetSdk` within 1 year. Re-test W^X/proot, FGS, photo picker, notifications. |

---

### A2. 64-bit native binaries

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §11.4 64-bit requirement |
| **Status** | **FOLLOWED** |
| **Evidence** | `app/src/main/jniLibs/arm64-v8a/` only (`libproot.so`, `libbash.so`, `libloader.so`, `libloader32.so`). Terminal emulator NDK filters `arm64-v8a`. |
| **Reason** | Native code ships 64-bit ABIs; no 32-only-only package. |
| **How to keep fixed** | Never ship `armeabi-v7a`-only builds to Play. If multi-ABI later, always include `arm64-v8a` (and `x86_64` if emulators matter). |

---

### A3. No ad SDKs / ads policy N/A

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Ads, §6.2 Ads, §4.8 Ad Fraud |
| **Status** | **FOLLOWED** (as non-ad app) |
| **Evidence** | `app/build.gradle.kts` dependencies: AppCompat, Material, Compose, Termux, termux-x11 — no AdMob, mediation, or ad networks. |
| **Reason** | No ad surfaces → ads placement/deception rules do not apply operationally. |
| **How to keep fixed** | Do not add ad SDKs without Ads policy review, Families rules, and Data safety updates. |

---

### A4. Payments / Play Billing N/A (free utility)

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Payments, §6.1–6.3 |
| **Status** | **FOLLOWED** (for current free model) |
| **Evidence** | No Play Billing library; no IAP UI; marketplace installs guest packages, not paid digital goods. |
| **Reason** | No digital goods/subscriptions sold → Billing requirement not triggered. |
| **How to fix if monetizing later** | Use Play Billing for any digital unlock/subscription; no external payment links for digital goods unless in an alternative-billing program. Update Data safety + listing. |

---

### A5. Restricted content categories — not applicable

| Field | Detail |
|-------|--------|
| **Guide §** | §3.1–3.13 (CSAM, sexual content, hate, violence, gambling, drugs, financial loans, health claims, crypto mining, etc.) |
| **Status** | **FOLLOWED** (product is a developer environment, not those categories) |
| **Evidence** | App purpose: Debian proot/chroot terminal, git, AI coding CLIs (`docs/project/problem_statement.md`). No UGC social feed, gambling, loan, or CSAM surfaces. |
| **Reason** | Core product does not publish restricted content categories as first-party content. |
| **How to keep fixed** | Do not add features that sell marijuana, real-money gambling, weapons instructions, etc. Store listing must stay developer-tool accurate (no child-attracting theming if not Families-ready). |

---

### A6. Account deletion requirement — N/A (no first-party account)

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Account deletion, §4.5 |
| **Status** | **FOLLOWED** (N/A) |
| **Evidence** | No NativeCode account registration server. GitHub device OAuth and AI CLI logins are third-party accounts. |
| **Reason** | Policy applies when **the app** creates accounts. Third-party OAuth alone does not create a Play “account deletion” obligation for a NativeCode account. |
| **How to fix if adding accounts later** | Ship in-app delete + web delete URL in Console; document retention in privacy policy. |

---

### A7. No Play Protect / security disable prompts

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Disabling device security, §4.8 MUwS |
| **Status** | **FOLLOWED** |
| **Evidence** | No code paths instructing users to turn off Play Protect, unknown-sources tricks for the host app itself, or fake “device infected” UI. |
| **Reason** | MUwS social-engineering patterns not present. |
| **How to keep fixed** | Never gate features on “disable Play Protect.” If unknown-sources needed for a justified installer use case, use system UI only + clear user consent. |

---

### A8. No self-update of the APK outside Play

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 Device & Network Abuse — no self-updating outside Play |
| **Status** | **FOLLOWED** (host app) |
| **Evidence** | `AppUpgrade.kt` restages **assets/scripts** on `versionCode` change after Play/install update — does not download a new APK/AAB or replace the installed package. |
| **Reason** | Host app update path is standard install/Play update, not sideload self-update. |
| **How to keep fixed** | Never add APK download + `PackageInstaller` for NativeCode itself. Keep `REQUEST_INSTALL_PACKAGES` out of self-update flows. |

---

### A9. Component export hygiene (main app)

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 / security hygiene (related to device abuse & malware review) |
| **Status** | **FOLLOWED** for `app` manifest components |
| **Evidence** | `AndroidManifest.xml`: `MainActivity`/`OnboardingActivity` `exported=false`; only `SplashActivity` launcher `exported=true`; services `exported=false`; `FileProvider` `exported=false`. |
| **Reason** | Reduces attack surface and “riskware” signals from exported surfaces. |
| **How to keep fixed** | Audit merged manifests after dependency upgrades; keep only intentional exports. |

---

### A10. No SMS / Call Log / Location / QUERY_ALL_PACKAGES in app manifest

| Field | Detail |
|-------|--------|
| **Guide §** | §4.6 Restricted permissions |
| **Status** | **FOLLOWED** (app-level declared perms) |
| **Evidence** | App declares: INTERNET, NETWORK_STATE, FGS, FGS_SPECIAL_USE, ACCESS_SUPERUSER — not SMS/call/location/query-all/install-packages/media. |
| **Reason** | Avoids highest-friction permission reviews. |
| **How to keep fixed** | Do not add these without core-functionality justification + Console declarations. |

---

### A11. No sale of personal data

| Field | Detail |
|-------|--------|
| **Guide §** | §4.4 Selling data prohibited |
| **Status** | **FOLLOWED** (as designed) |
| **Evidence** | No analytics/ad SDKs that sell data; tokens used for GitHub/AI auth only. |
| **Reason** | No data brokerage path observed. |
| **How to keep fixed** | Any future analytics must be disclosed; never sell personal data. |

---

### A12. Minimum functionality / not a spam shell

| Field | Detail |
|-------|--------|
| **Guide §** | §8 Spam & Minimum Functionality |
| **Status** | **FOLLOWED** (product intent) |
| **Evidence** | Onboarding provisions Debian; terminal, projects, git, marketplace, AI CLI auth, X11 path exist in code. |
| **Reason** | App is a full developer environment, not a static wallpaper/text stub. |
| **How to keep fixed** | Ensure production builds do not crash on cold start; closed testing must show real utility for reviewers. |

---

### A13. Intellectual property — first-party branding direction

| Field | Detail |
|-------|--------|
| **Guide §** | §3.15 IP, §5.2 Impersonation |
| **Status** | **FOLLOWED** for package identity (`com.ivarna.nativecode`, label NativeCode) |
| **Evidence** | Own package name and launcher label; not “Termux Official” naming. |
| **Reason** | Distinct identity reduces impersonation risk. |
| **Caveat** | Internal path names may still use `fluxlinux-*` as SSOT (not product brand). See A27. |
| **How to keep fixed** | Do not use Google/Termux/Claude/etc. trademarks in a way that implies official affiliation. |

---

### A14. No on-device cryptomining / no real-money gambling features

| Field | Detail |
|-------|--------|
| **Guide §** | §3.10, §3.13 |
| **Status** | **FOLLOWED** |
| **Evidence** | No mining or wagering modules in app code. |
| **How to keep fixed** | Marketplace catalog must not promote mining packages as a core feature; filter if needed. |

---

### A15. FileProvider not exported

| Field | Detail |
|-------|--------|
| **Guide §** | Security / malware review hygiene |
| **Status** | **FOLLOWED** (export flag) |
| **Evidence** | `FileProvider` `android:exported="false"` + `grantUriPermissions="true"`. |
| **Caveat** | Paths narrowed in **B12** (`share/export/` only). |
| **How to keep fixed** | Keep `exported=false`; never re-broaden `file_paths.xml`. |

---

### A16. Families policy — not targeting children (intended)

| Field | Detail |
|-------|--------|
| **Guide §** | §9 Families |
| **Status** | **FOLLOWED** if Console target audience is 18+ / not children |
| **Evidence** | Developer tooling; no child-directed UX. |
| **Reason** | Families rules apply when children are targeted. |
| **How to keep fixed** | Declare non-child audience in Console; avoid child-attracting listing art; do not market to kids. |

---

### A17. No deceptive “official government / breathalyzer” claims in code

| Field | Detail |
|-------|--------|
| **Guide §** | §5.1 Deceptive Behavior |
| **Status** | **FOLLOWED** at code level |
| **Evidence** | Product claims are Linux-on-Android / AI CLI environment. |
| **How to keep fixed** | Listing copy must match real capabilities (no “full unrestricted root for all devices” if proot-only for most users). |

---

### A18. Host app does not disable FLAG_SECURE of other apps / no container of third-party Android apps

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 FLAG_SECURE, On-device Android Containers |
| **Status** | **FOLLOWED** |
| **Evidence** | Linux guest (proot/chroot), not an Android app-cloning container. |
| **How to keep fixed** | If multi-instance Android containers are ever added, honor `REQUIRE_SECURE_ENV`. |

---

### A19. Lint/build allows release packaging path

| Field | Detail |
|-------|--------|
| **Guide §** | §11.4 technical readiness (indirect) |
| **Status** | **FOLLOWED** for ability to build with target 36 |
| **Evidence** | Gradle configured for application module; signingConfig present for release. |
| **Caveat** | Secrets in Gradle are a security smell, not a Play policy ID — fix operationally. |

---

### A20. No WebView + untrusted JS bridge pattern in main app

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 WebView + JavaScript Interface on untrusted content |
| **Status** | **FOLLOWED** (main app) |
| **Evidence** | Auth flows open external browser (`CliAuthService.openBrowser`, GitHub device flow); no main-app WebView bridge found for untrusted pages. |
| **How to keep fixed** | Prefer Custom Tabs / external browser; never `addJavascriptInterface` on non-https or untrusted URLs. |

---


### A21. Android App Bundle (.aab) + Play App Signing

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §11.4 |
| **Status** | **FOLLOWED** |
| **Evidence** | App module builds release AAB/APK; Play distribution uses AAB + Play App Signing (ops confirmed). |
| **Reason** | Meets Play packaging requirements for new app submissions. |
| **How to keep fixed** | Always upload AAB; never production-sideload host APK as store package. CI: `:app:bundleRelease`. |

---

### A22. Developer account identity verification

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §11.1 |
| **Status** | **FOLLOWED** |
| **Evidence** | Play Console identity verification completed (ops confirmed). |
| **Reason** | Developer Program account verification satisfied. |
| **How to keep fixed** | Keep org/personal docs current if account type changes. |

---

### A23. Android Developer Verification (package + signing key)

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §11.2 |
| **Status** | **FOLLOWED** |
| **Evidence** | Package `com.ivarna.nativecode` + release signing key registered (ops confirmed). |
| **Reason** | Meets platform Developer Verification ahead of Sept 2026 regional enforcement. |
| **How to keep fixed** | Re-register if applicationId or upload/signing key rotates. |

---

### A24. Closed testing (12 testers / 14 days)

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §11.3 |
| **Status** | **FOLLOWED** |
| **Evidence** | Closed testing track requirements met (ops confirmed). |
| **Reason** | Personal-account production gate satisfied (or org-exempt path applied). |
| **How to keep fixed** | Keep a closed track for major releases; re-run if account policy changes. |

---

### A25. No `REQUEST_INSTALL_PACKAGES` (removed)

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Hostile downloaders, §4.6, §4.8(b) |
| **Status** | **FOLLOWED** |
| **Evidence** | Removed from `app/src/main/AndroidManifest.xml` (2026-07-31). No `PackageInstaller` usage in app Kotlin. Guest apt/deb installs are not Android APK installs. |
| **Reason** | Avoids restricted-permission review and hostile-downloader classification. |
| **How to keep fixed** | Do not re-add unless product becomes a genuine user-initiated APK installer with Console declaration. |

---

### A26. Foreground services for terminal / project / AI agent sessions

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 FGS policy |
| **Status** | **FOLLOWED** (core feature justified) |
| **Evidence** | `BackgroundService`, `AppTerminalService`, `ProjectTerminalService` use `foregroundServiceType="specialUse"` so AI agents and terminal sessions keep running in background with ongoing notifications. |
| **Reason** | Core product need: long-running interactive/agent processes must not be killed when UI backgrounds. User-perceptible via ongoing notification; sessions user-initiated. |
| **How to keep fixed** | Keep notifications branded **NativeCode**; only start FGS when user starts sessions; stop when sessions end. Console FGS declaration done (C4). |

---

### A27. Public brand = NativeCode (user-visible)

| Field | Detail |
|-------|--------|
| **Guide §** | §5.1, §5.2, §7.1 |
| **Status** | **FOLLOWED** |
| **Evidence** | Manifest label `NativeCode`. `BackgroundService` title/channel updated to NativeCode (was FluxLinux). Terminal FGS titles already NativeCode-neutral. |
| **Reason** | Store identity matches runtime notifications. |
| **How to keep fixed** | Keep internal paths (`fluxlinux-host.env`, guest config dirs) as technical SSOT if needed, but never reintroduce FluxLinux as product name in UI/notifications/listing. |

---

### A28. No broad media/storage permissions (picker only)

| Field | Detail |
|-------|--------|
| **Guide §** | §4.6 Photos/Video |
| **Status** | **FOLLOWED** |
| **Evidence** | `READ_MEDIA_IMAGES` and `READ_EXTERNAL_STORAGE` removed from app manifest. Image attach uses `ActivityResultContracts.GetContent()` only. No runtime media permission requests in app code. |
| **Reason** | System picker is sufficient; no Photos/Videos permission declaration needed. |
| **How to keep fixed** | Prefer Photo Picker / GetContent; never re-add broad media access without core-functionality proof. |

---

# Part B — PARTIALLY FOLLOWED

Incomplete, risky, or Console-dependent items.

---

### B1–B8 — MOVED TO FOLLOWED (A21–A28)

| Old ID | New | Status |
|--------|-----|--------|
| B1 AAB + Play App Signing | A21 | **FOLLOWED** (ops) |
| B2 Developer identity | A22 | **FOLLOWED** (ops) |
| B3 Android Developer Verification | A23 | **FOLLOWED** (ops) |
| B4 Closed testing 12×14d | A24 | **FOLLOWED** (ops) |
| B5 `REQUEST_INSTALL_PACKAGES` | A25 | **FOLLOWED** — **removed** from manifest |
| B6 FGS `specialUse` | A26 | **FOLLOWED** — required for terminal/AI agents in background |
| B7 Branding FluxLinux vs NativeCode | A27 | **FOLLOWED** — user-visible NativeCode |
| B8 Media permissions | A28 | **FOLLOWED** — dropped; `GetContent` only |

---

### B9. Accessibility / KeyInterceptor — RESOLVED (removed)

| Field | Detail |
|-------|--------|
| **Guide §** | §4.6 Accessibility API |
| **Status** | **FOLLOWED** |
| **Evidence** | termux-x11: no `AccessibilityService` in manifest; `KeyInterceptor` deleted; a11y prefs removed from X11 settings; `WRITE_SECURE_SETTINGS` removed from termux-x11; no `accessibility_service_config`. |
| **Reason** | Optional desktop Meta-key capture not required for terminal/AI/GUI; removing eliminates high-risk Accessibility surface. |
| **How to keep fixed** | Do not re-add AccessibilityService for keyboard capture. Use in-app key handling only. |

### B10. Data practices (tokens, network auth) without complete disclosures

| Field | Detail |
|-------|--------|
| **Guide §** | §4.1–4.3 Privacy, Data safety, prominent disclosure |
| **Status** | **FOLLOWED** (ops: Data safety + privacy policy + in-app entry; see C1–C3) |
| **Evidence** | GitHub device OAuth (`GitHubCliService`); AI CLI browser/device auth (`CliAuthService`); tokens in guest env; SharedPreferences; marketplace catalog. Console Data safety + public privacy URL filled; Settings privacy card; onboarding privacy page. |
| **Reason** | Third-party auth still processes tokens — disclosed via Console + policy. |
| **How to keep fixed** | Re-open Data safety when vendors/destinations change; keep `docs/privacy-policy.md` aligned. |

---

### B11. External / interpreted code execution (core product risk)

| Field | Detail |
|-------|--------|
| **Guide §** | §1 External code execution, §4.7 Device & Network Abuse |
| **Status** | **MITIGATED / ACCEPTED** — re-audited 2026-07-31; residual accepted after Play research (no rejections found for terminal log UI + user-initiated guest installs alone; peer apps on store) |
| **Evidence (accurate)** | **Rootfs:** shipped in APK as `assets/rootfs/debian_13_rootfs.tar.xz`; onboarding copies to app home (`deployDebianRootfsFromAssets`); `flux_install.sh` / chroot setup **prefer local archive** (SHA pin). Network download is **fallback only** if asset missing. **Host natives:** `libproot.so` / loaders from Play package `jniLibs` / bootstrap — **not** downloaded at runtime. **Guest only after user action:** apt package installs inside proot/chroot; Marketplace `install.sh` from GitHub raw on explicit install tap; AI `setup_cli_tools.sh` curl/npm **opt-in** (C6). **Isolation:** proot guest rootfs under app files; not host-process DEX injection. **Disclosure:** privacy + onboarding consent; installs are tap/confirm driven. **Residual (accepted):** free terminal + user-tap guest scripts/logs — same class as UserLAnd/Andronix/Termux-style apps still on Play. |
| **Reason** | Policy cares most about **(1) host app code updates outside Play** (dex/JAR/.so into the Android process) and **(2) silent/automatic remote code without user action**. This app does neither. Guest Linux packages/scripts + install logs are IDE-class residual, accepted for ship. |
| **How to keep fixed** | 1) Keep primary rootfs path = assets only; avoid relying on download fallback in production. 2) Keep AI/marketplace opt-in + confirm. 3) **Never** download host `.so`/dex (issue 1). 4) **Never** silent auto-exec of remote scripts (issue 2). 5) Optionally pin marketplace script hashes. 6) Reviewer notes: bundled rootfs + guest-only network packages. |

---

### B12. FileProvider paths too broad

| Field | Detail |
|-------|--------|
| **Guide §** | Security / malware review (related §5.3) |
| **Status** | **FOLLOWED** (implemented 2026-07-31) |
| **Evidence** | `file_paths.xml` share-only: `files-path`/`cache-path` → `share/export/` only — **no** `root-path`, no full external/files/cache. `MainActivity.getFileUri` stages copies under `filesDir/share/export/` (timestamp-unique names, 200 MB cap, 24 h purge) then `FileProvider.getUriForFile`; no `Uri.fromFile` fallback. Covers proot (in `filesDir`) and chroot (`/data/local/tmp/chrootDebian13/...`) open-with without device-wide roots. |
| **Reason** | Was oversharing via `root-path` + broad trees; stage-copy narrows grant surface to staged files only. |
| **How to keep fixed** | Never re-add `root-path` or `external-path path="."`. All outbound share must go through stage dir. Do not repurpose attach dir as share. |

---

### B13. `allowBackup="true"` with secrets in app storage

| Field | Detail |
|-------|--------|
| **Guide §** | §4 Privacy / security practices disclosure |
| **Status** | **FOLLOWED** (2026-07-31) |
| **Evidence** | `app/src/main/AndroidManifest.xml` → `android:allowBackup="false"`. Tokens stay on device; no full app-data cloud backup. |
| **Reason** | Prevents backup of guest auth material / prefs. |
| **How to keep fixed** | Keep `allowBackup="false"` unless exclusions + privacy update land first. |

---

### B14. termux-x11 exported surfaces

| Field | Detail |
|-------|--------|
| **Guide §** | Security / deception hygiene |
| **Status** | **FOLLOWED** (2026-07-31) |
| **Evidence** | `termux-x11/.../AndroidManifest.xml`: `MainActivity`, `LoriePreferences`, `LoriePreferences$Receiver` all `android:exported="false"`. NativeCode starts them via same-package explicit Intents only. |
| **Reason** | Other apps must not launch desktop/prefs or fire CHANGE_PREFERENCE. |
| **How to keep fixed** | Keep `exported=false`; never re-export for external Termux package control unless product requires it. |

---

### B15. `WRITE_SECURE_SETTINGS` / privileged permissions in modules

| Field | Detail |
|-------|--------|
| **Guide §** | §4.6, §4.8 Unauthorized system functionality |
| **Status** | **FOLLOWED** (re-check 2026-07-31 — shipping merged manifest clean) |
| **Evidence** | Merged app release manifest perms only: INTERNET, ACCESS_NETWORK_STATE, FGS, FGS_SPECIAL_USE, POST_NOTIFICATIONS, ACCESS_SUPERUSER, VIBRATE. **No** `WRITE_SECURE_SETTINGS` / `REQUEST_INSTALL_PACKAGES` / `MANAGE_EXTERNAL_STORAGE` in final package. termux-x11 declares only FGS/INTERNET/POST_NOTIFICATIONS. Local `termux-app/` tree has privileged perms but is **not** the merged packaging path (deps are termux-shared / terminal AARs). |
| **Reason** | Old audit feared termux-app full-app perms; code shows they do not land in NativeCode APK. |
| **How to keep fixed** | Re-check merged manifest after dependency bumps; never re-add privileged perms to app/x11 manifests. |

---

### B16. AI features without AI-Generated Content controls

| Field | Detail |
|-------|--------|
| **Guide §** | §3.14 AI-Generated Content |
| **Status** | **MITIGATED / FOLLOWED (code)** — launcher model + in-app report + ToS |
| **Evidence** | Settings → **AI Safety & Report** (`ID_AI_SAFETY`): disclaimer, mailto `zenithblue.dev@gmail.com`, per-vendor REPORT/ToS/AUP via `AiVendorSafetyCatalog`; onboarding one-line report pointer. Still third-party CLI generators (no first-party model host). Plan: `docs/plan/c5-b16-ai-report-c10-credentials.md`. |
| **Reason** | In-app reporting + vendor links satisfy launcher-style AI surface; residual if Play expects per-message flags inside terminal transcripts. |
| **How to keep fixed** | Re-check vendor URLs before Play submit; do not host unrestricted gen models without filters + report UI. |

---

### B17. Content rating / target audience / store metadata

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §7.1–7.2, §10 |
| **Status** | **FOLLOWED** (ops: Console IARC / target audience / listing assets) |
| **Evidence** | Developer confirmed Console content rating, audience, and store listing materials complete. |
| **Reason** | Required Console metadata satisfied for current submission path. |
| **How to keep fixed** | Re-run IARC if product category changes; keep listing truthful (proot/chroot, optional AI, network). |

---

### B18. Financial Features declaration (account-level)

| Field | Detail |
|-------|--------|
| **Guide §** | §13 Recency — mandatory even if zero financial features |
| **Status** | **FOLLOWED** (ops: Console form completed) |
| **Evidence** | Developer confirmed Financial Features declaration filed (no financial features, if accurate). |
| **Reason** | Account-wide form no longer blocking updates for this reason. |
| **How to keep fixed** | Revisit if monetization / finance-related features ship. |

---

### B19. Third-party API / installer ToS compliance

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Third-party API ToS, §4.7 |
| **Status** | **FOLLOWED** (verified 2026-07-31) |
| **Evidence** | **Install (guest only, user opt-in C6):** `setup_cli_tools.sh` uses vendor official paths only — curl `claude.ai/install.sh`, `x.ai/cli/install.sh`, `antigravity.google/cli/install.sh`, `cli.kiro.dev/install`, `opencode.ai/install`; npm `@openai/codex`, `opencode-ai`, `@qwen-code/qwen-code`. No proprietary CLI bins in AAB. **Login:** `CliAuthService` + `CliToolCatalog` — vendor CLI commands only (`claude setup-token`, `codex login --device-auth`, `kiro-cli login`, `opencode auth login`, API keys / terminal guided); browser opened for vendor URL; no private API scrape. Optional third-party; ToS/report via B16 catalog. |
| **Reason** | Official install + official login paths satisfy third-party ToS / Play §1 API rules for this product shape. |
| **How to keep fixed** | Never mirror/fork installers or ship proprietary bins in AAB; keep login = vendor CLI/browser/device flows only. |

---

### B20. Release hardening incomplete

| Field | Detail |
|-------|--------|
| **Guide §** | Malware/riskware review quality (indirect §5.3) |
| **Status** | **FOLLOWED (optional)** — deferred / accepted 2026-07-31; not a Play policy ship-blocker |
| **Evidence** | `isMinifyEnabled = false`; keystore passwords in `app/build.gradle.kts` (repo hygiene, not Play checklist requirement). |
| **Reason** | Optional engineering hygiene; product owner marked done/deferred. |
| **How to fix later** | Move secrets to `local.properties` / CI secrets; optional R8; rotate leaked keystore passwords. |

---

# Part C — NOT FOLLOWED

Missing or clearly non-compliant as of this audit.

---

### C1. Privacy Policy (public URL + in-app)

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §4.1 User Data policy |
| **Status** | **FOLLOWED** (ops: Console privacy URL + Data safety filled; in-app link + `docs/privacy-policy.md`) |
| **Evidence** | GitHub policy live; Settings Hub card; Play Console privacy URL set by developer; onboarding privacy page (C3). |
| **Reason** | Mandatory public policy + in-app entry satisfied. |
| **How to keep fixed** | Keep `docs/privacy-policy.md` in sync with Data safety when features change. |

---

### C2. Data safety form completed & accurate

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §4.2 |
| **Status** | **FOLLOWED** (ops: completed in Play Console by developer) |
| **Evidence** | Developer confirmed Console Data safety form filled to match local-first policy. |
| **How to keep fixed** | Re-open form when auth vendors / network destinations change. |

### C3. In-app prominent privacy entry / consent UX for non-obvious data

| Field | Detail |
|-------|--------|
| **Guide §** | §4.3 Prominent disclosure & consent |
| **Status** | **FOLLOWED** — first onboarding page; local-first / open-source disclosure + Accept |
| **Evidence** | `OnboardingActivity` page 0 `buildPrivacyPage()`; pref `privacy_accepted`; cyber-brutalist UI per `docs/project/ui_design.md`. Messaging: no NativeCode cloud collection; data stays in proot/chroot. |
| **How to keep fixed** | Re-show if major privacy policy version changes (bump pref key). |

### C4. FGS `specialUse` subtype property + Console FGS declaration

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 Foreground Services policy |
| **Status** | **FOLLOWED** (code + Console declaration) |
| **Evidence** | Manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` on BackgroundService / AppTerminalService / ProjectTerminalService; solid notif icon; IMPORTANCE_DEFAULT; POST_NOTIFICATIONS; `docs/policy/fgs-special-use-declaration.md` + demo; developer confirmed Console FGS form + video submitted. |
| **How to keep fixed** | Keep subtype text accurate; re-upload demo if FGS behavior changes. |

### C5. AI-Generated Content — reporting mechanism

| Field | Detail |
|-------|--------|
| **Guide §** | §3.14 |
| **Status** | **FOLLOWED (code)** |
| **Evidence** | Settings hub → **AI Safety & Report** (`ID_AI_SAFETY`); `ReportMailHelper` → `zenithblue.dev@gmail.com`; vendor forms/ToS in `AiVendorSafetyCatalog`; composer (category + tool + description); onboarding one-line pointer; nested back via `navigateBackFromSubpage` + `currentPageId` (bottom nav restored on Settings). Plan: `docs/plan/c5-b16-ai-report-c10-credentials.md`. |
| **Reason** | Dedicated in-app report path + vendor links for third-party CLI AI surface. |
| **How to keep fixed** | Keep email + catalog URLs current; smoke-test mailto + one vendor form each release. |

---

### C6. Silent / automated remote executable provisioning in guest (policy conflict)

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 No external executable code; interpreted code may not be used to violate policies |
| **Status** | **MITIGATED / ACCEPTED** — AI suite S1+S3; rootfs asset-local; residual user-tap guest network **OK** (research 2026-07-31) |
| **Evidence** | AI: default OFF, plan inventory+consent, H gated, Settings install, shell-only launchers until provisioned (`AiCliProvisionState`, `CliToolsInstaller`). **Rootfs not network-primary:** `assets/rootfs/debian_13_rootfs.tar.xz` + deploy on setup. Guest apt + Marketplace scripts still network **after user tap**. See `docs/plan/c6-onboarding-remote-install-consent.md`. |
| **Reason** | Silent/default remote AI install was the hot path — mitigated. Remaining user-tap guest package flow is accepted residual (not silent host updates). |
| **How to keep fixed** | Keep AI opt-in; keep install confirms; **never** silent auto remote exec; never host `.so`/dex download. |

---

### C7. `POST_NOTIFICATIONS` runtime permission for main app FGS

| Field | Detail |
|-------|--------|
| **Guide §** | FGS policy (user-visible ongoing notification) + Android 13+ behavior |
| **Status** | **FOLLOWED** (code) |
| **Evidence** | `app` manifest declares `POST_NOTIFICATIONS`; `MainActivity` requests at runtime (`REQ_POST_NOTIFICATIONS`); FGS services post ongoing notifications. |
| **Reason** | Runtime permission supports perceptible FGS notifications on API 33+. |
| **How to keep fixed** | Request before starting FGS when not granted; never hide stop action on ongoing notifs. |

---

### C8. Store listing accuracy / reviewer-facing materials

| Field | Detail |
|-------|--------|
| **Guide §** | §7 Store Listing, §5.1 Behavior transparency |
| **Status** | **FOLLOWED** (ops: Console listing complete) |
| **Evidence** | Developer confirmed Play listing copy, graphics, and reviewer-facing notes prepared in Console. Draft notes also in Part G of this doc. |
| **Reason** | Listing / transparency materials no longer open Console work for this rev. |
| **How to keep fixed** | Keep listing aligned with onboarding (optional AI, proot/chroot, network rootfs). Same path for reviewers and users. |

---

### C9. Hostile downloader / APK install permission — RESOLVED

| Field | Detail |
|-------|--------|
| **Guide §** | §4.8(b) Hostile Downloaders |
| **Status** | **FOLLOWED** (see A25) |
| **Evidence** | `REQUEST_INSTALL_PACKAGES` removed from app manifest. No host APK install flow. |
| **Reason** | App no longer claims Android package-install capability. |
| **How to keep fixed** | Do not re-add without full Console justification. |

---

### C10. No dedicated account-deletion / data-clear guidance in product UX

| Field | Detail |
|-------|--------|
| **Guide §** | §4.5 (N/A for accounts) + §4.1 retention/deletion in privacy policy |
| **Status** | **FOLLOWED (code)** |
| **Evidence** | GH LOGOUT (existing); AI per-tool LOGOUT; **CLEAR ALL AI CREDENTIALS** + **CLEAR SUITE FLAG** on AI CLI tools (`CredentialClearService`); AI Safety page data-deletion bullets; privacy policy §9 table + `zenithblue.dev@gmail.com`. |
| **Reason** | In-app sign-out/clear + deletion guidance; full wipe still Android clear storage / uninstall. |
| **How to keep fixed** | Regression-test GH + AI logout + stack back (CLI ↔ Safety ↔ Settings nav). |

---

### C11. Play Console App content checklist

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Quick checklist aggregate |
| **Status** | **FOLLOWED** (ops: Console App content completed) |
| **Evidence** | Developer confirmed App content forms complete: privacy URL, Data safety, IARC, target audience, Financial features, ads (none), FGS declaration, N/A categories as applicable. |
| **How to keep fixed** | Re-open affected forms when product/data/FGS change; use this checklist as internal tracker for code residual. |

---

### C12. Notification / brand identity — RESOLVED

| Field | Detail |
|-------|--------|
| **Guide §** | §5.1, §4.8(d) |
| **Status** | **FOLLOWED** (see A27) |
| **Evidence** | `BackgroundService` uses “NativeCode is running” and `NativeCodeBackgroundServiceChannel`. |
| **Reason** | Runtime identity matches store label. |
| **How to keep fixed** | No FluxLinux product strings in UI/notifications/listing. |

---

# Part D — Section-by-section mapping (guide §0–§13)

Quick map of the guide’s structure to this audit.

| Guide section | Overall | Notes |
|---------------|---------|-------|
| §0 How to use | — | Use this checklist before each release |
| §1 Quick pre-submission | Mostly green | Console forms done; B11 residual accepted; C5/C10 code shipped |
| §2 Master links | — | Keep bookmarked |
| §3 Restricted content | Followed | Not in those businesses; watch AI §3.14 |
| §3.14 AI content | Followed (code) / mitigated | **C5** report UI + **B16** ToS/disclaimer; launcher residual |
| §3.15–3.16 IP / UGC | Followed / N/A | Shell UGC is local user content; no hosted social UGC |
| §4 Privacy/data/permissions | Followed | C1–C3 + C10 clear/sign-out UX + privacy email |
| §4.7 Device & network abuse | Mitigated / accepted | C6 AI opt-in; B11 guest residual accepted (user-tap only) |
| §4.8 MUwS / hostile downloaders | Followed | No REQUEST_INSTALL (C9/A25) |
| §5 Deception / malware | Followed / residual | Branding fixed; no intentional malware |
| §6 Monetization/ads | Followed | Free, no ads |
| §7 Store listing | Followed (ops) | Console listing complete (C8) |
| §8 Spam / functionality | Followed | Real product |
| §9 Families | Followed | Audience declared in Console |
| §10 Age-restricted | N/A | Not dating/gambling |
| §11 Account/technical | Followed | API/64-bit/AAB/verify/testing OK (ops) |
| §12 Enforcement | — | Plan appeals contact; 30-day fix windows for many issues |
| §13 Recency (2026) | Followed (ops) | Dev verification + Financial Features + API 36 |

---

# Part E — Prioritized remediation plan

## P0 — Blockers (before closed testing → production)

| # | Action | Owner | Done |
|---|--------|-------|------|
| 1 | Publish privacy policy URL; link in-app + Console | Legal/dev | ☑ |
| 2 | Complete Data safety form to match reality | Dev | ☑ |
| 3 | Complete Financial Features + IARC + Target audience | Dev | ☑ |
| 4 | Play-safe AI onboarding (C6 S1+S3: default off + consent + Settings install) | Eng | ☑ |
| 5 | ~~Remove `REQUEST_INSTALL_PACKAGES`~~ | Eng | ☑ |
| 6 | FGS specialUse property + Console declaration + `POST_NOTIFICATIONS` | Eng | ☑ |
| 7 | Document/demo for reviewers (rootfs size, arm64, network) | Dev | ☑ |
| 8 | ~~Document Accessibility~~ — service removed (B9) | Eng | ☑ |
| 9 | ~~NativeCode branding in BackgroundService~~ | Eng | ☑ |
| 10 | ~~Drop unused media/storage permissions~~ | Eng | ☑ |

## P1 — High (before production launch)

| # | Action | Done |
|---|--------|------|
| 9 | ~~Document brand~~ → A27 | ☑ |
| 10 | ~~Drop media perms~~ → A28 | ☑ |
| 11 | Tighten FileProvider paths; drop `root-path` (**B12**) | ☑ code |
| 12 | AI report/flag entry + third-party ToS disclaimers (**C5 / B16**) | ☑ code (+ nav fix) |
| 13 | Sign-out / clear credentials UX (**C10**) | ☑ code |
| 14 | First-run privacy consent | ☑ |
| 15 | AAB + Play App Signing + closed test 12×14 if personal account | ☑ |
| 16 | Android Developer Verification registration | ☑ |

## P2 — Hardening

| # | Action | Done |
|---|--------|------|
| 17 | Rootfs/script integrity (checksums) | ☐ |
| 18 | Manifest merger: strip unused exported components/permissions | ☑ B14 exports false + B15 perms clean |
| 19 | Secrets out of Gradle; keystore rotation if leaked | ☑ deferred (B20 optional) |
| 20 | `allowBackup` / data extraction rules | ☑ B13 `allowBackup=false` |
| 21 | Marketplace script signing or in-app packaging | ☑ first-use consent (full script signing still optional later) |

---

# Part F — Evidence index (code references)

| Area | Path |
|------|------|
| Manifest / permissions / FGS | `app/src/main/AndroidManifest.xml` (no REQUEST_INSTALL / no READ_MEDIA) |
| SDK levels / signing | `app/build.gradle.kts` |
| JNI 64-bit | `app/src/main/jniLibs/arm64-v8a/` |
| Asset restage on upgrade | `app/src/main/java/.../AppUpgrade.kt` |
| FGS notifications | `BackgroundService.kt`, `AppTerminalService.kt`, `ProjectTerminalService.kt` |
| Image pickers | `MainActivity.kt` (`GetContent`) |
| GitHub OAuth | `github/GitHubCliService.kt` |
| AI CLI auth | `cliauth/CliAuthService.kt` |
| AI safety / report / clear | `cliauth/AiVendorSafetyCatalog.kt`, `ReportMailHelper.kt`, `CredentialClearService.kt`; Settings pages in `MainActivity.kt` |
| Marketplace remote scripts | `marketplace/MarketplaceClient.kt` |
| Rootfs download | `assets/scripts/flux_install.sh`, `assets/scripts/chroot/setup_debian13_chroot.sh` |
| curl/npm AI installers | `assets/scripts/setup_cli_tools.sh` |
| FileProvider paths | `app/src/main/res/xml/file_paths.xml` |
| Accessibility | **removed** from termux-x11 (no service) |
| Product scope | `docs/project/problem_statement.md` |
| Older short checklist | `docs/playstore/Google_Play_Store_Policy_Compliance_Checklist.md` |
| Source guide | [abhay-kb Google_Play_Store_Policy_Compliance_Guide.md](https://raw.githubusercontent.com/abhay-byte/abhay-kb/refs/heads/main/Google_Play_Store_Policy_Compliance_Guide.md) |

---

# Part G — Suggested Play Console “notes for reviewers” (draft)

> NativeCode is a developer environment that runs a Debian Linux guest via proot (rootless) or optional chroot (device root).  
> First launch downloads or extracts a Debian rootfs and may install optional AI CLI tools **only during onboarding / user-initiated setup**.  
> The app does not display ads and does not sell digital goods.  
> Foreground services keep user-started terminal sessions alive; stop by ending sessions / force-stop.  
> No Accessibility service is used.  
> Test on **arm64** device with **≥8 GB free storage** and network access.  
> No special login required for core terminal; GitHub/AI logins are optional third-party OAuth/device flows.

---

*End of checklist. Re-run this audit after policy changes or before each Play release.*
)
