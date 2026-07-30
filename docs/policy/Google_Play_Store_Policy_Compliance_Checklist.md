# NativeCode — Google Play Policy Compliance Checklist

**App:** NativeCode (`com.ivarna.nativecode`)  
**Scope:** Codebase + packaging audit against [Google Play Store Policy Compliance Guide](https://raw.githubusercontent.com/abhay-byte/abhay-kb/refs/heads/main/Google_Play_Store_Policy_Compliance_Guide.md) (compiled July 25, 2026; DPP effective May 27, 2026).  
**Audit date:** 2026-07-31 (rev: B1–B8 followed; B9 Accessibility fully removed)  
**Primary tree:** `app/` (+ merged modules `termux-x11`, Termux deps)

> This is an internal engineering checklist, not legal advice. Re-verify against live [Policy Center](https://support.google.com/googleplay/android-developer/topic/9858052) before submission. Status is based on **repository evidence only** — Play Console steps that live outside git are marked **Console** when unknown.

**Status legend**

| Status | Meaning |
|--------|---------|
| **FOLLOWED** | Repo / design clearly satisfies the rule |
| **PARTIAL** | Some work done, incomplete, risky, or Console-only gap |
| **NOT FOLLOWED** | Missing or actively conflicting with policy as implemented |

---

## Summary scorecard

| Bucket | Count (approx.) | Highest-risk items |
|--------|-----------------|--------------------|
| Followed | ~28 | API 36, 64-bit, AAB path, identity/verification/closed-test (ops confirmed), no ads, no install-packages, media via picker only, FGS for terminal/AI BG, NativeCode branding |
| Partial | ~12 | Privacy policy (in-app + GitHub; Console pending), remote scripts, FileProvider, AI controls, listing/IARC, Financial Features, module exports |
| Not followed | ~9 | Data safety, consent UX, FGS Console/property, AI report UI, auto remote installers, POST_NOTIFICATIONS, store package |

**Ship-blocking before production (must fix or formally justify):**

1. Privacy policy live on default branch + Play Console URL field (in-app card done)  
2. Play Console Data safety form (accurate)  
3. Device & Network Abuse story for rootfs / npm / curl installers / marketplace scripts  
4. FGS `specialUse` property + Console declaration (runtime justified; declaration still required)  
5. IARC / target audience (Console)  
6. Financial Features declaration (mandatory even if “none”)  

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
| **Caveat** | Path config is overly broad (**PARTIAL** B12). |
| **How to keep fixed** | Keep `exported=false`; tighten paths. |

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
| **How to keep fixed** | Keep notifications branded **NativeCode**; only start FGS when user starts sessions; stop when sessions end. Still complete Play Console FGS declaration + special-use subtype property (see C4) before production if not already filed. |

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
| **Status** | **PARTIAL** (collection exists; disclosure incomplete — see C1/C2) |
| **Evidence** | GitHub device OAuth (`GitHubCliService`); AI CLI browser/device auth (`CliAuthService`); tokens written into guest `hosts.yml` / env; SharedPreferences for prefs; network catalog fetch (`MarketplaceClient`). |
| **Reason** | Even “local-only” apps that auth to third parties still collect/process account identifiers and tokens. Data safety + privacy policy must match. |
| **How to fix** | Inventory data types (tokens, usernames, device codes, crash logs if any). Map to Data safety. Add in-app privacy entry (Settings). Disclose third-party destinations (GitHub, AI vendors, GitHub raw marketplace, rootfs CDN). |

---

### B11. External / interpreted code execution (core product risk)

| Field | Detail |
|-------|--------|
| **Guide §** | §1 External code execution, §4.7 Device & Network Abuse |
| **Status** | **PARTIAL** — inherent product tension |
| **Evidence** | - Ships native loaders (`libproot.so`, etc.). - Downloads Debian rootfs (`flux_install.sh`, chroot setup → GitHub releases). - `setup_cli_tools.sh` runs `curl \| bash`, `npm install -g`, nvm installer. - Marketplace downloads `install.sh` from remote raw GitHub and executes in guest. - User terminal can run arbitrary commands. |
| **Reason** | Policy bans downloading **dex/JAR/native .so** from outside Play. **Interpreters** (JS/Python/shell) loaded at runtime are allowed **only if** they cannot violate other policies. Terminal/IDE apps historically exist on Play but face high scrutiny; dynamic **native** payloads and silent remote script execution are rejection magnets. |
| **How to fix (mitigation stack — not a guarantee)** | 1) **Disclosure:** listing + privacy policy: “downloads Linux packages and AI CLIs at user request.” 2) **User initiation:** never silent background install of tools; require explicit taps in onboarding/marketplace. 3) **Pin integrity:** checksum/signature rootfs and critical installers; prefer app-packaged rootfs over network when size allows. 4) **No host-app DEX/SO updates** from network. 5) **Marketplace:** sign scripts or ship catalog in-app; HTTPS only; no remote native `.so` injection into host process. 6) **Reviewer notes + demo video** showing developer-tool nature. 7) **Accept residual risk** or distribute non-Play channels if policy enforcement is untenable. |

---

### B12. FileProvider paths too broad

| Field | Detail |
|-------|--------|
| **Guide §** | Security / malware review (related §5.3) |
| **Status** | **PARTIAL** |
| **Evidence** | `res/xml/file_paths.xml` includes `<root-path name="root" path="." />` and full external/files/cache trees. |
| **Reason** | `root-path` can expose device-wide paths via granted URIs — oversharing signal for security review. |
| **How to fix** | Restrict to exact dirs needed (e.g. cache APK share path, project export). Remove `root-path` unless proven necessary. |

---

### B13. `allowBackup="true"` with secrets in app storage

| Field | Detail |
|-------|--------|
| **Guide §** | §4 Privacy / security practices disclosure |
| **Status** | **PARTIAL** |
| **Evidence** | Manifest `android:allowBackup="true"`. Tokens may live under app files / guest home. |
| **Reason** | Backup can copy auth material to cloud backups depending on OS rules; must match privacy claims. |
| **How to fix** | Prefer `allowBackup="false"` or exclude sensitive paths via `dataExtractionRules` / `fullBackupContent`. Document backup behavior in privacy policy. |

---

### B14. termux-x11 exported surfaces

| Field | Detail |
|-------|--------|
| **Guide §** | Security / deception hygiene |
| **Status** | **PARTIAL** |
| **Evidence** | X11 `MainActivity` exported; preferences exported; `LoriePreferences$Receiver` exported. (KeyInterceptor removed.) |
| **Reason** | Exported components can be started by other apps; increases attack surface and review questions. |
| **How to fix** | Set `exported=false` where not required; protect receivers with permissions; disable unused launcher entries from library manifests via manifest merger `tools:node="remove"` if they create dual-launcher confusion. |

---

### B15. `WRITE_SECURE_SETTINGS` / privileged permissions in modules

| Field | Detail |
|-------|--------|
| **Guide §** | §4.6, §4.8 Unauthorized system functionality |
| **Status** | **PARTIAL** |
| **Evidence** | termux-x11 no longer declares `WRITE_SECURE_SETTINGS`. Vendored `termux-app` tree may still list privileged perms depending on packaging (`com.github.termux:termux-app` AAR vs local). |
| **Reason** | Harmless if not granted, but noisy manifests can confuse review. Must not use system-looking notifications for ads (N/A) or imitate OS. |
| **How to fix** | Strip unused permissions with merger rules. Document any secure-settings use. Ensure notifications clearly branded NativeCode (not system). |

---

### B16. AI features without AI-Generated Content controls

| Field | Detail |
|-------|--------|
| **Guide §** | §3.14 AI-Generated Content |
| **Status** | **PARTIAL** |
| **Evidence** | App provisions Claude, Codex, OpenCode, Grok, Agy, etc. AI runs in guest CLI; app does not host a first-party model UI with moderation. No in-app “report offensive AI output” flow found. |
| **Reason** | Policy requires AI generators to prevent restricted content and include **in-app reporting/flagging** for offensive outputs when the app provides AI generation. Integration surface may be argued as “launcher for third-party CLIs,” but risk remains if Play treats the app as an AI product. |
| **How to fix** | 1) Add Settings: “Report AI safety issue” (email/form URL). 2) Onboarding disclaimer: outputs from third-party CLIs; user responsible; links to vendor ToS. 3) Do not ship unrestricted local image “undress” tools. 4) Document age audience 18+. 5) If hosting model inference in-app later, add filters + report UI. |

---

### B17. Content rating / target audience / store metadata

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §7.1–7.2, §10 |
| **Status** | **PARTIAL** (Console) |
| **Evidence** | No IARC answers or Play listing assets committed as final store package. |
| **Reason** | Required before production; must be truthful (developer tool, network access, user-generated shell activity). |
| **How to fix** | Complete IARC; set target age 18+ (recommended); prepare title ≤30 chars, 512 icon, 1024×500 feature graphic, ≥2 screenshots; no keyword spam / fake “#1” claims. |

---

### B18. Financial Features declaration (account-level)

| Field | Detail |
|-------|--------|
| **Guide §** | §13 Recency — mandatory even if zero financial features |
| **Status** | **PARTIAL** (Console) |
| **Evidence** | App is not a loan/crypto exchange, but declaration is account-wide. |
| **Reason** | Incomplete Financial Features declaration can block **all** updates. |
| **How to fix** | In Play Console App content, complete Financial Features form (“no financial features” if accurate). |

---

### B19. Third-party API / installer ToS compliance

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Third-party API ToS, §4.7 |
| **Status** | **PARTIAL** |
| **Evidence** | curl installers for Claude/Grok/OpenCode/Agy; GitHub API OAuth; npm global installs. |
| **Reason** | Play requires not using APIs in ways that violate **those** ToS (classic example: YouTube ad stripping). AI CLI install/ToS must be respected (auth methods, redistribution). |
| **How to fix** | Use official install paths only; do not redistribute proprietary CLI binaries inside the AAB if licenses forbid; OAuth apps registered properly; document that tools are optional third-party. |

---

### B20. Release hardening incomplete

| Field | Detail |
|-------|--------|
| **Guide §** | Malware/riskware review quality (indirect §5.3) |
| **Status** | **PARTIAL** |
| **Evidence** | `isMinifyEnabled = false`; keystore passwords present in `app/build.gradle.kts` (repo security issue). |
| **Reason** | Not a named Play policy checkbox, but weak release hygiene increases compromise risk and review red flags. |
| **How to fix** | Move secrets to `local.properties` / CI secrets (never commit). Enable R8 carefully around Termux/native. Rotate any leaked keystore passwords immediately. |

---

# Part C — NOT FOLLOWED

Missing or clearly non-compliant as of this audit.

---

### C1. Privacy Policy (public URL + in-app)

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §4.1 User Data policy |
| **Status** | **PARTIAL** — in-repo policy + Settings Hub card; Console URL still required after publish |
| **Evidence** | `docs/privacy-policy.md`; Settings Hub **PRIVACY POLICY** opens `https://github.com/abhay-byte/nativecode-ai/blob/master/docs/privacy-policy.md` via `Intent.ACTION_VIEW`. Live only after push to default branch. Play Console privacy field not set from repo. |
| **Reason** | Play requires public HTTPS policy linked in Console **and** in-app. GitHub-hosted markdown satisfies interim public URL once pushed; dedicated domain/HTML still preferred for production. |
| **How to fix (remaining)** | 1) Push `docs/privacy-policy.md` to `master`. 2) Paste same URL in Play Console → App content → Privacy policy. 3) Optional: host HTML copy on own domain. 4) Keep in sync with Data safety (C2). |

---

### C2. Data safety form completed & accurate

| Field | Detail |
|-------|--------|
| **Guide §** | §1, §4.2 |
| **Status** | **NOT FOLLOWED** (no evidence of completion; data collection exists) |
| **Evidence** | Network auth + remote downloads exist; form answers not stored in repo. |
| **Reason** | Required for every app; must match privacy policy and actual SDK/code behavior. |
| **How to fix** | In Play Console Data safety declare at least:  
| | - **Account info** (GitHub username/token handling — even if token stays on device, collection/processing rules apply as configured)  
| | - **App activity / files** if applicable  
| | - **Network** transmissions to GitHub, AI vendors, raw script hosts, rootfs CDN  
| | - Security practices (encryption in transit HTTPS)  
| | - Data deletion (uninstall / clear storage)  
| | Update form whenever marketplace hosts or auth vendors change. |

---

### C3. In-app prominent privacy entry / consent UX for non-obvious data

| Field | Detail |
|-------|--------|
| **Guide §** | §4.3 Prominent disclosure & consent |
| **Status** | **NOT FOLLOWED** |
| **Evidence** | Onboarding focuses on isolation method + setup progress; no privacy acceptance screen; no disclosure before OAuth/token storage. |
| **Reason** | When collection may exceed user expectation (tokens written into guest FS, remote script execution), Play expects clear disclosure + affirmative consent before collection begins. |
| **How to fix** | Add first-run screen: what is stored locally, what leaves the device, third-party logins optional. Require Accept before continuing. Re-show for major policy changes. |

---

### C4. FGS `specialUse` subtype property + Console FGS declaration

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 Foreground Services policy |
| **Status** | **NOT FOLLOWED** (manifest property / Console package incomplete) |
| **Evidence** | Services use `specialUse` but no `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` (or documented equivalent) found; no Console declaration artifacts. |
| **Reason** | `specialUse` without justification is a common rejection. |
| **How to fix** | ```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="Keeps user-initiated Linux terminal / X11 developer sessions alive with ongoing notification" />
```  
Also complete Play Console “Foreground service permissions” declaration with short video of notification + stop path. |

---

### C5. AI-Generated Content — reporting mechanism

| Field | Detail |
|-------|--------|
| **Guide §** | §3.14 |
| **Status** | **NOT FOLLOWED** |
| **Evidence** | No report/flag UI for offensive AI outputs. |
| **Reason** | Required for apps that provide AI generation features. Even if AI is third-party CLI, Play may classify NativeCode as providing AI capabilities. |
| **How to fix** | Settings → “Report AI content concern” → opens form/email with app version, tool name, optional description. Link vendor safety pages. Keep logs of reports for internal response. |

---

### C6. Silent / automated remote executable provisioning in guest (policy conflict)

| Field | Detail |
|-------|--------|
| **Guide §** | §4.7 No external executable code; interpreted code may not be used to violate policies |
| **Status** | **NOT FOLLOWED** relative to strict reading when onboarding auto-runs curl/npm installers |
| **Evidence** | `setup_cli_tools.sh`: `curl -fsSL … \| bash`, npm globals, nvm install; rootfs download from GitHub releases; marketplace `install.sh` download+exec. |
| **Reason** | Onboarding can pull and run substantial remote software without a Play-reviewed binary set. This is the largest **Device and Network Abuse** risk for terminal apps on Play. |
| **How to fix (choose strategy)** | **Strategy S1 — Play-safe mode:** Default onboarding installs only packages **bundled or apt from Debian mirrors** user explicitly confirms; AI CLIs opt-in later with full warning; pin hashes.  
| | **Strategy S2 — Split distribution:** Publish a reduced Play build (no curl-to-bash AI installers, no remote marketplace exec); full tooling via alternative distribution.  
| | **Strategy S3 — Justify as IDE:** User-initiated only, clear UI, no background updates of host app code, detailed reviewer notes — still residual rejection risk.  
| | Implement integrity checks; never download host-process `.so`/dex. |

---

### C7. `POST_NOTIFICATIONS` runtime permission for main app FGS

| Field | Detail |
|-------|--------|
| **Guide §** | FGS policy (user-visible ongoing notification) + Android 13+ behavior |
| **Status** | **NOT FOLLOWED** in main app code |
| **Evidence** | No `POST_NOTIFICATIONS` in app manifest; no `requestPermissions` in app Kotlin. termux-x11 declares the permission. FGS posts ongoing notifications. |
| **Reason** | Without notification permission, FGS notifications may be hidden → policy expects perceptible, stoppable FGS work. |
| **How to fix** | Declare `POST_NOTIFICATIONS` in app manifest; request at runtime before starting FGS; explain why (“terminal session running”). |

---

### C8. Store listing accuracy / reviewer-facing materials (not prepared in-repo)

| Field | Detail |
|-------|--------|
| **Guide §** | §7 Store Listing, §5.1 Behavior transparency |
| **Status** | **NOT FOLLOWED** (as a submission package) |
| **Evidence** | No final short/full description, screenshots pack, or “notes for reviewers” checked into `docs/policy`. |
| **Reason** | Listing must accurately describe root/proot, network downloads, AI tools. Hidden features or different reviewer paths violate Behavior Transparency. |
| **How to fix** | Write listing copy matching real UX. Provide reviewer credentials/instructions: how to complete onboarding on arm64 device, storage needs, that rootfs download is required, test account if any. App must behave the same for reviewers and users. |

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
| **Status** | **NOT FOLLOWED** for user-facing deletion guidance |
| **Evidence** | Users can uninstall, but Settings does not clearly explain how to wipe GitHub tokens / guest data / AI creds. |
| **Reason** | Privacy policy must describe deletion; best practice is in-app “Sign out / clear credentials.” |
| **How to fix** | Add Sign out for GitHub and each CLI; “Clear guest data” with confirm; document uninstall path in privacy policy. |

---

### C11. Play Console App content checklist empty (from repo perspective)

| Field | Detail |
|-------|--------|
| **Guide §** | §1 Quick checklist aggregate |
| **Status** | **NOT FOLLOWED** until Console completed |
| **Items missing evidence** | Privacy policy URL, Data safety, IARC, Target audience, News/News apps (N/A), Health (N/A), Data deletion, Government apps (N/A), Financial features, Ads declaration, FGS declaration, Restricted permissions declarations. |
| **How to fix** | Work Console App content top-to-bottom; attach this document as internal tracker; do not request production until green. |

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
| §1 Quick pre-submission | Mixed | API/64-bit/AAB/verify/testing OK (ops); privacy/data safety open; code-exec risk |
| §2 Master links | — | Keep bookmarked |
| §3 Restricted content | Followed | Not in those businesses; watch AI §3.14 |
| §3.14 AI content | Not followed / partial | Reporting missing |
| §3.15–3.16 IP / UGC | Followed / N/A | Shell UGC is local user content; no hosted social UGC |
| §4 Privacy/data/permissions | Partial | Policy MD + Settings link; Data safety / Console URL / consent still open |
| §4.7 Device & network abuse | Partial → not followed | Dynamic download/exec is core risk |
| §4.8 MUwS / hostile downloaders | Partial | Install packages permission |
| §5 Deception / malware | Followed / residual | Branding fixed; no intentional malware |
| §6 Monetization/ads | Followed | Free, no ads |
| §7 Store listing | Not followed | Assets/copy not finalized here |
| §8 Spam / functionality | Followed | Real product |
| §9 Families | Followed if 18+ | Declare correctly |
| §10 Age-restricted | N/A | Not dating/gambling |
| §11 Account/technical | Followed | API/64-bit/AAB/verify/testing OK (ops) |
| §12 Enforcement | — | Plan appeals contact; 30-day fix windows for many issues |
| §13 Recency (2026) | Action | Dev verification Sept 2026; Financial Features form; API 36 |

---

# Part E — Prioritized remediation plan

## P0 — Blockers (before closed testing → production)

| # | Action | Owner | Done |
|---|--------|-------|------|
| 1 | Publish privacy policy URL; link in-app + Console | Legal/dev | ☐ |
| 2 | Complete Data safety form to match reality | Dev | ☐ |
| 3 | Complete Financial Features + IARC + Target audience | Dev | ☐ |
| 4 | Decide Play-safe onboarding vs full remote installers; implement user-initiated + disclosures | Eng | ☐ |
| 5 | ~~Remove `REQUEST_INSTALL_PACKAGES`~~ | Eng | ☑ |
| 6 | FGS specialUse **property** + Console declaration + `POST_NOTIFICATIONS` (runtime FGS itself OK — A26) | Eng | ☐ |
| 7 | Document/demo for reviewers (rootfs size, arm64, network) | Dev | ☐ |
| 8 | ~~Document Accessibility~~ — service removed (B9) | Eng | ☑ |
| 9 | ~~NativeCode branding in BackgroundService~~ | Eng | ☑ |
| 10 | ~~Drop unused media/storage permissions~~ | Eng | ☑ |

## P1 — High (before production launch)

| # | Action | Done |
|---|--------|------|
| 9 | ~~Document brand~~ → A27 | ☑ |
| 10 | ~~Drop media perms~~ → A28 | ☑ |
| 11 | Tighten FileProvider paths; drop `root-path` | ☐ |
| 12 | AI report/flag entry + third-party ToS disclaimers | ☐ |
| 13 | Sign-out / clear credentials UX | ☐ |
| 14 | First-run privacy consent | ☐ |
| 15 | AAB + Play App Signing + closed test 12×14 if personal account | ☐ |
| 16 | Android Developer Verification registration | ☐ |

## P2 — Hardening

| # | Action | Done |
|---|--------|------|
| 17 | Rootfs/script integrity (checksums) | ☐ |
| 18 | Manifest merger: strip unused exported components/permissions | ☐ |
| 19 | Secrets out of Gradle; keystore rotation if leaked | ☐ |
| 20 | `allowBackup` / data extraction rules | ☐ |
| 21 | Marketplace script signing or in-app packaging | ☐ |

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
