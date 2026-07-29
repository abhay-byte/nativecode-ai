
# Google Play Store Policy Compliance Validation

## 1. Quick Pre-Submission Checklist

| Area | Requirement | Status | Note |
|---|---|---|---|
| Build format | AAB format | **[?]** | Validated at build time via Google Play publishing step. |
| Target API level | Target API 36 | **[PASS]** | `targetSdk` and `compileSdk` updated to 36 across all `build.gradle.kts` and `gradle.properties`. |
| 64-bit support | 64-bit binaries | **[PASS]** | NDK builds configure `arm64-v8a`. JNI libs are 64-bit. |
| Developer account | Identity verification | **[?]** | Action required in Play Console. |
| Android Developer Verification | Package + Signing Key | **[?]** | Action required in Play Console. |
| Closed testing | 12 testers, 14 days | **[?]** | Action required in Play Console. |
| Privacy Policy | URL in console + in-app | **[?]** | Action required in Play Console / App UI. |
| Data safety form | Completed in console | **[?]** | Action required in Play Console. |
| Account deletion | N/A | **[PASS]** | Termux does not have a native account system. |
| Content rating | IARC questionnaire | **[?]** | Action required in Play Console. |
| Target audience declaration | Accurate age targeting | **[?]** | Action required in Play Console. |
| Ads | Non-deceptive/appropriate | **[PASS]** | No ad SDKs found in code. |
| Payments | Play Billing used | **[PASS]** | No Play Billing integration found; free app. |
| External code execution | No downloading executable code | **[FAIL]** | Termux fundamentally executes external code/scripts. Risk of Device and Network Abuse policy violation. Play Store grants exceptions for terminal/IDE apps, but scrutiny is high. |
| Installing other apps | No hostile downloading | **[WARNING]**| `REQUEST_INSTALL_PACKAGES` is used. Justification needed as core feature (package manager). |
| Disabling device security | No bypassing Play Protect | **[PASS]** | No such code found. |

## Permissions Validation

| Permission | Status | Note |
|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | **[WARNING]** | Present in `termux-app`. Requires core functionality justification (Terminal emulator / file management). |
| `REQUEST_INSTALL_PACKAGES` | **[WARNING]** | Present in `app` and `termux-app`. Requires justification (Package management in terminal). |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | **[WARNING]** | Present in `app`. Requires justification in Play Console for Android 14+. |
| `READ_MEDIA_IMAGES` | **[WARNING]** | Present in `app`. Google prefers Photo Picker for Android 13+. |

## Conclusion
Code-level attributes (Target SDK 36, 64-bit architecture) are compliant. However, Termux's core nature (executing arbitrary code, managing files via `MANAGE_EXTERNAL_STORAGE`, and installing packages via `REQUEST_INSTALL_PACKAGES`) triggers strict Play Store policy reviews. These require manual justification in the Google Play Console.

