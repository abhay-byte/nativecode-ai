# X11 display UI, settings, notification rehaul + NativeCode integration

**Date:** 2026-07-30  
**Status:** implemented 2026-07-30 (+ NC Settings Hub → X11 Settings button)  
**Scope:** Termux:X11 UI/UX embedded in NativeCode (`:termux-x11` library → `com.zenithblue.nativecode`). Extra-keys bar default ON + fix missing icons; display chrome (back + keyboard) per `docs/project/ui_design.md`; system Back → NativeCode home; flatten X11 prefs to one sectioned page; rebrand notification; decide architecture (keep module vs merge).  
**Out of scope:** APK assembly / install; XFCE guest scripts; proot/chroot start path; rewriting Lorie/X server JNI; full Compose rewrite of X render surface; Play-store packaging beyond branding strings.

**Design SSOT:** `docs/project/ui_design.md` (Obsidian Terminal / cyber-brutalist)  
**Token SSOT (app):** `app/.../DesignTokens.kt` (`NC.*`)  
**Screenshots (user):**
1. X11 extra-keys row: text keys visible; arrow / settings / keyboard slots empty  
2. X11 Preferences root: nested `Pointer` / `Keyboard` / `Other` + `Version 1.03.01` (stock Material dark)

**Related:**
- Module: `termux-x11/` (`com.android.library`, namespace `com.termux.x11`)
- Host: `app` `implementation(project(":termux-x11"))` — **same APK / same applicationId**
- Launch: `am start -n com.zenithblue.nativecode/com.termux.x11.MainActivity`
- Scripts: `start_gui.sh`, `chroot/start_gui_chroot.sh` start Loader + open X11 activity

**Compile policy (mandatory for impl agent):**
- Run `:termux-x11:compileDebugJavaWithJavac` / Kotlin if any + `:app:compileDebugKotlin` + `:app:compileReleaseKotlin`
- **Never** assemble APK / install unless user explicitly asks
- Fix any `e:` before reporting done

---

## 1. Problem summary

### 1.1 Extra keys “off” / incomplete chrome

| Symptom | Root cause (code) |
|---------|-------------------|
| User expects extra-keys bar **on by default** | Prefs default `showAdditionalKbd=true`, `additionalKbdVisible=true` — but UX still feels off; startup calls `toggleExtraKeys(false, false)` in `MainActivity.onCreate` and bar only appears when `LorieView.connected()`. After connect, `clientConnectedStateChanged` → `setTerminalToolbarView()` should show if prefs true; verify + force show when `showAdditionalKbd` true and force `additionalKbdVisible=true` on first connect if never user-toggled. |
| Missing icons on UP/LEFT/DOWN/RIGHT/PREFERENCES/KEYBOARD | `ExtraKeysView.setIcon()` uses `button.setForeground(icon)` with white vectors. On many Android/Material button styles, **foreground is invisible or clipped** (empty key). Screenshot matches: text keys OK, icon keys blank. |
| No dedicated soft-keyboard control on display chrome | Soft IME only via extra-key `KEYBOARD` or Back action (`backButtonAction` default = `toggle soft keyboard`). User wants **always-visible** keyboard button next to back. |

### 1.2 Back navigation wrong for NativeCode product

| Control | Current | Desired |
|---------|---------|---------|
| Overlay `back_to_home_button` | Starts `com.zenithblue.nativecode.MainActivity` with `REORDER_TO_FRONT` — OK functionally; UI is stock `ic_menu_revert` + semi-black square — **not** ui_design |
| System / gesture Back | `TouchInputHandler` → `backButtonAction` default **`toggle soft keyboard`** (does **not** leave X11) | **Return to NativeCode app** (same as overlay back) |
| `MainActivity.onBackPressed()` | `super.onBackPressed()` only | Align with “go home” helper |

### 1.3 Settings: nested Termux UI, not product UI

Root screen (`preferences.xml` `main`) is only links:

- Output (not always obvious in screenshot)
- Pointer / Keyboard / Other → **sub-screens**
- Version summary

User wants:
1. **Remove nested category navigation** (no drill-down pages for Pointer/Keyboard/Other)
2. **One scrollable page**, content grouped by **sections**
3. **Version only as footer metadata** (keep showing version string; not a navigable category)
4. Entire prefs chrome follows **ui_design.md**

### 1.4 Notification still “Termux:X11”

`buildNotification()`:

- Title `"Termux:X11"`
- Channel name from `R.string.app_name` = Termux:X11
- Color `0xFF607D8B` (Material blue-gray)
- Actions from prefs (Preferences / Exit)

Feels like a **second independent app** in the shade, even though process/package is NativeCode.

### 1.5 “Independent app” perception vs reality

**Already combined at package level:**

| Layer | State today |
|-------|-------------|
| Gradle | `:termux-x11` library included by `:app` |
| applicationId | `com.zenithblue.nativecode` only |
| Activities | `com.termux.x11.MainActivity`, `LoriePreferences` merged into host manifest |
| JNI / server | `libXlorie.so` + `loader.apk` / `app_process` CmdEntryPoint |
| Recents / task | **`taskAffinity=".MainActivity"`** + `singleInstance` → **separate task** in Recents → feels independent |
| Branding | strings/notification/icons still Termux:X11 |
| Settings theme | `Theme.AppCompat.DayNight` stock, not NC |

User question: *should we combine into NativeCode?*  
→ **Recommend: keep library module + tight UX/task/branding integration (option B below). Do not relocate all Java into `app/`.**

---

## 2. Architecture decision (combine or not)

### Option A — Full source merge into `:app`

- Move `com.termux.x11.*` under `app/src/main/java`, drop `:termux-x11` module  
- **Pros:** single module mental model  
- **Cons:** pollutes huge MainActivity tree; harder upstream sync; AIDL/JNI/loader build still special; no isolation for X server classpath  

**Reject** for this rehaul.

### Option B — Keep `:termux-x11` library; product-integrate UX (recommended)

Keep:

- X server, `LorieView`, input stack, AIDL, jniLibs, CmdEntryPoint/Loader packaging  
- Package still `com.zenithblue.nativecode` (already)

Change for “combined” **product feel**:

1. **Task:** drop separate `taskAffinity` (or set affinity equal to host); prefer `singleTop`/`singleTask` so Recents shows **one** NativeCode task when possible  
2. **Branding:** rename visible strings → **NativeCode Desktop** / **X11 Display** (not Termux:X11)  
3. **Notification:** NativeCode channel name, green accent, titles match product  
4. **Back:** always return to `com.zenithblue.nativecode.MainActivity`  
5. **Design tokens:** X11 layouts/themes consume same colors as `NC` / ui_design  
6. **No LAUNCHER** for X11 (already no LAUNCHER category on X11 activity — keep it that way)

### Option C — Embed X11 as Fragment/Compose inside host activity

- Heavy rewrite of Surface/input/lifecycle; high risk for X connection  
- **Defer** to a future epic; not this plan  

**Decision to record in plan (pending user confirm):** **Option B**.

---

## 3. Goals (acceptance)

### Display chrome (`com.termux.x11.MainActivity`)

1. **Extra keys bar visible by default** when X connected (`showAdditionalKbd` + `additionalKbdVisible` both true; first connect forces bar if user never hid it).  
2. **All icon keys render** (arrows, keyboard, settings/preferences) — no blank slots.  
3. **Top overlay cluster** (sharp, ui_design):
   - **Back** → NativeCode `MainActivity` (reorder/clear-top as appropriate)  
   - **Keyboard** → toggle Android soft IME (`InputMethodManager` / existing `toggleKeyboardVisibility`)  
4. System / gesture **Back** → same as overlay Back (leave X11 → NativeCode), **not** toggle IME.  
5. Colors/surfaces: background void `#131313` / black canvas OK for X surface; chrome uses `#1E1E1E` / `#121212`, accent `#3DDC84`, text `#FAFAFA`, **0dp radius**, two-tone extrusion on chrome buttons where feasible.

### Settings (`LoriePreferences`)

6. **Single page** — no PreferenceScreen drill-down for Output/Pointer/Keyboard/Other.  
7. **Sections** as `PreferenceCategory` (or custom section headers):
   - Output / Display  
   - Pointer  
   - Keyboard  
   - Extra keys bar  
   - Gestures & hardware actions (ex-userActions)  
   - Other  
   - **Version** (footer only: summary = `BuildConfig.VERSION_NAME`)  
8. Theme follows ui_design (dark surface, green switches/progress, sharp list, mono labels where possible).  
9. Keep **all functional prefs** (do not delete Output/Pointer/… settings); only remove **nested navigation**.

### Notification

10. Title/content rebranded to NativeCode Desktop (or similar).  
11. Channel name not “Termux:X11”.  
12. Accent uses Terminal Green (`#3DDC84` / `NC.PRIMARY_CON`).  
13. Default actions remain useful (e.g. Preferences + Exit / return to app); strings not “Termux”.

### Process / product

14. X11 does **not** appear as a second launcher app.  
15. Recents: prefer single NativeCode task (affinity/launchMode tweak).  
16. Compile green (debug+release Kotlin/Java); **no APK**.

---

## 4. Design system mapping (ui_design.md → X11 Views)

X11 is **Views/Java**, not Compose. Mirror tokens already in `NC`:

| Token | Hex | X11 use |
|-------|-----|---------|
| background / surface | `#131313` | Prefs window, chrome bars |
| surface-container | `#201f1f` / `#1E1E1E` | Buttons secondary, cards |
| primary-container | `#3DDC84` | Primary actions, switch track on, notification color |
| on-primary (on green) | `#0A0A0A` / `#00391c` | Text on primary buttons |
| on-surface | `#FAFAFA` / `#e5e2e1` | Labels, extra-key text |
| outline-variant | `#3C4A3F` | Extrusion right face |
| surface-bright | `#393939` | Extrusion bottom face |
| Shape | **0dp** | All chrome buttons, prefs cards |
| Type | JetBrains Mono labels if font available in module; else system monospace for keycaps |

**Buttons (chrome):**
- Secondary style for Back / Keyboard: bg `#1E1E1E`, 1px stroke `#3DDC84`, icon `#FAFAFA`, optional two-tone 6px L-shadow if layout allows without covering X content  
- Pressed: translate + shrink shadow per design doc  

**Extra keys bar:**
- Bar bg `#0A0A0A` / `#131313`  
- Key text `#FAFAFA`  
- Active special (CTRL/ALT): fill `#3DDC84`, text `#0A0A0A`  
- Icons tint `#FAFAFA` (or active green)  

**Prefs:**
- Theme overlay: dark Material preference parent restyled; category titles mono green or on-surface-variant  
- Avoid rounded Material3 cards; force 0 corner radius  

---

## 5. Implementation plan (phased)

### Phase 0 — Guardrails

- Branch: e.g. `x11-ui-rehaul` (impl agent only after approval)  
- No APK tasks  
- Smoke compile before and after each phase  

### Phase 1 — Extra keys default ON + missing icons

**Files:**
- `termux-x11/.../MainActivity.java` — show path  
- `termux-x11/.../ExtraKeysView.java` — icon rendering  
- Optional: `TermuxX11ExtraKeys.java` defaults (already double-row with KEYBOARD/PREFERENCES)

**1.1 Default visibility**

- Ensure prefs defaults remain:
  - `showAdditionalKbd = true`
  - `additionalKbdVisible = true`
- On **successful connect** (`clientConnectedStateChanged` / after `tryConnect` succeeds):
  - If `showAdditionalKbd`: call `toggleExtraKeys(true, false)` **or** only `setTerminalToolbarView()` with visibility true  
- Remove or replace misleading `toggleExtraKeys(false, false)` in `onCreate` with `setTerminalToolbarView()` only  
- Document: swipe-down still toggles bar via `swipeDownAction` default  

**1.2 Fix missing icons**

Root bug: `setIcon()` → `button.setForeground(icon)`.

**Replace with robust path:**

```text
// preferred
Drawable d = AppCompatResources.getDrawable(context, id);
d = DrawableCompat.wrap(d).mutate();
DrawableCompat.setTint(d, mButtonTextColor);
button.setText(""); // or null
button.setCompoundDrawablesWithIntrinsicBounds(null, d, null, null);
// OR center via LayerDrawable / ImageSpan
// OR ImageButton for icon-only keys
```

Also:
- Set `button.setGravity(Gravity.CENTER)`  
- Scale icons to ~18–20dp for 37.5dp row height  
- Ensure vectors keep white fill **or** tint at runtime (tint wins)  
- Fallback: if drawable null, use Unicode display map (`←↑→↓`, `⌨`, `⚙`) so key never blank  

**1.3 Style extra keys to ui_design**

- Update default colors in `ExtraKeysView` to NC palette  
- Toolbar pager background `#131313`  
- Optional later: raise row height slightly for touch targets (44dp) — only if layout doesn’t break X insets  

**Verify:** connect to X, bar visible, all 8+ keys per row show glyph/icon.

### Phase 2 — Display chrome: Back + Keyboard + system Back

**Files:**
- `termux-x11/src/main/res/layout/main_activity.xml`  
- New drawables: `bg_nc_chrome_btn.xml`, `ic_nc_back.xml`, `ic_nc_keyboard.xml` (sharp vectors)  
- `MainActivity.java`  
- `TouchInputHandler.java` (back action default)  
- `Prefs.java` + `preferences.xml` default for `backButtonAction`  
- strings

**2.1 Layout**

Replace lone `back_to_home_button` with horizontal `LinearLayout` top-start:

```text
[ Back ] [ Keyboard ]
```

- Size ~40–44dp, margin 16dp  
- Secondary chrome style (section 4)  
- contentDescription: “Back to NativeCode”, “Show keyboard”  

**2.2 Click handlers**

```text
goToNativeCodeHome():
  Intent → Class.forName("com.zenithblue.nativecode.MainActivity")
  flags: FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP
  // optional CLEAR_TOP if stack wrong
  startActivity; do not finish() X11 by default (session stays; user can resume from notification / Settings START)

keyboardButton:
  MainActivity.toggleKeyboardVisibility(this)
```

**2.3 System Back**

- Change default `backButtonAction` from `"toggle soft keyboard"` → **new action** `"return to app"` (or map `"exit"` carefully — exit currently `finish()` only, may not bring NativeCode front).  
- Prefer **new** user-action: `"return to nativecode"` / `"return to app"` that calls same `goToNativeCodeHome()`.  
- Wire in `TouchInputHandler.extractUserActionFromPreferences`  
- Override `OnBackPressedCallback` (API 33+) / `onBackPressed` to call `goToNativeCodeHome()` when connected (and when not connected).  
- Mouse-as-right-click BACK source path **unchanged** (still right-click).  

**2.4 Optional:** keep Back-as-IME available in prefs list for power users, default is return-to-app.

### Phase 3 — Flatten settings + ui_design theme

**Files:**
- `res/xml/preferences.xml` — major restructure  
- `LoriePreferences.java` — fragment root only; simplify `OnPreferenceStartFragmentCallback` (may become unused for main tree)  
- `res/values/styles.xml` + new `themes_nc_x11.xml` / colors  
- `res/layout/*preference*` restyle if needed  
- strings titles for categories  

**3.1 preferences.xml shape**

```xml
<PreferenceScreen key="main" title="X11 Settings">
  <PreferenceCategory title="Output"> … all output prefs … </PreferenceCategory>
  <PreferenceCategory title="Pointer"> … </PreferenceCategory>
  <PreferenceCategory title="Keyboard"> … include showAdditionalKbd … </PreferenceCategory>
  <PreferenceCategory title="Extra keys bar"> … ekbar prefs … </PreferenceCategory>
  <PreferenceCategory title="Gestures & actions"> … userActions lists … </PreferenceCategory>
  <PreferenceCategory title="Other"> … clipboard, notification permission, secondary display … </PreferenceCategory>
  <PreferenceCategory title="About">
    <Preference key="version" title="Version" summary="1.03.01" selectable="false"/>
  </PreferenceCategory>
</PreferenceScreen>
```

- Remove nested `PreferenceScreen` children used as navigation  
- Keep **hidden** prefs that must remain in DataStore (`additionalKbdVisible`) with `isPreferenceVisible=false` still under Keyboard category or unlisted via code  
- `extra_keys_config` dialog behavior in `LoriePreferences` stays  
- `requestNotificationPermission` stays under Other  

**3.2 LoriePreferences code**

- `setPreferencesFromResource(..., "main")` only  
- Drop fragment navigation for output/pointer/kbd/other/ekbar/userActions **or** keep fragment API only if deep-link needed (prefer drop)  
- Action bar: back arrow, title “X11 Settings”, dark surface  
- Theme activity: `Theme.NativeCode.X11.Preferences` (new)  

**3.3 Theme**

- windowBackground `#131313`  
- colorPrimary / secondary `#3DDC84`  
- textColorPrimary `#FAFAFA`  
- preferenceCategoryStyle: JetBrains-like / caps mono if available  
- switchPreferenceCompatStyle: green thumb/track when on  
- No rounded Material preference cards (override layouts to 0 radius)  

**3.4 Version**

- Footer only; summary = `BuildConfig.VERSION_NAME` (already)  
- Not clickable  

### Phase 4 — Notification rehaul

**Files:** `MainActivity.buildNotification`, strings, channel id, optional small icon tint

| Field | From | To |
|-------|------|-----|
| Content title | Termux:X11 | NativeCode Desktop (or “X11 session”) |
| Content text | Pull down… | “Desktop session running” / keep short |
| Channel id/name | app_name Termux | `nativecode_x11` / “NativeCode Desktop” |
| Color | `0xFF607D8B` | `0xFF3DDC84` |
| Small icon | ic_x11_icon | keep or NC monochrome |
| Actions | Preferences / Exit | Preferences / **Open app** (or Exit) — strings without Termux |

- On pause cancel notification remains; on resume re-post (existing)  
- POST_NOTIFICATIONS request flow unchanged  

### Phase 5 — Task / “combined app” product feel

**Files:** `termux-x11/src/main/AndroidManifest.xml`, maybe host manifest merge rules

1. Remove or neutralize `android:taskAffinity=".MainActivity"` on X11 `MainActivity` so it joins default affinity (NativeCode).  
2. Revisit `launchMode="singleInstance"` → prefer `singleTask` or `singleTop` so Back stack integrates with host; **test** X reconnect / Loader handshake after change (risk area).  
3. `LoriePreferences`: `excludeFromRecents=true` already — keep; theme update only.  
4. strings: `app_name` for library resources used in notification → product name (careful: only UI strings; do not break package name `com.termux.x11` class paths).  
5. Document for agents: **module stays**; product is one app.

**Risk note:** `singleInstance` + separate affinity was intentional for X lifecycle. Phase 5 must be **device-tested** for:

- start_gui → X connects  
- Back to NativeCode → session still alive  
- Notification reopen X  
- Second START XFCE does not spawn zombie tasks  

If affinity change breaks session, **fallback:** keep affinity but fix branding + notification + back so only Recents still shows two entries (document as known limitation).

### Phase 6 — Stub / connected empty state buttons

`main_activity.xml` stub (Not connected / PREFERENCES / HELP / EXIT):

- Restyle to secondary/primary NC buttons  
- EXIT / PREFERENCES labels without excessive spaces  
- Optional: hide HELP or point to NativeCode docs  

### Phase 7 — Verification (compile only + checklist)

```text
./gradlew :termux-x11:compileDebugJavaWithJavac :app:compileDebugKotlin :app:compileReleaseKotlin --offline
```

**Manual device checklist (user / later):**

- [ ] Fresh install prefs: extra keys visible after XFCE start  
- [ ] Arrows + keyboard + settings icons visible  
- [ ] Top Keyboard toggles Android IME  
- [ ] Top Back → NativeCode main  
- [ ] System Back → NativeCode main  
- [ ] Prefs: one scrolling page, sections, version footer  
- [ ] Notification says NativeCode*, green accent  
- [ ] Recents behavior acceptable  
- [ ] Stop GUI still kills session  

---

## 6. File map (impl targets)

| Path | Change |
|------|--------|
| `termux-x11/.../MainActivity.java` | chrome handlers, home helper, notification, extra-keys show on connect, back |
| `termux-x11/.../input/TouchInputHandler.java` | new return-to-app action; default back |
| `termux-x11/.../Prefs.java` | default `backButtonAction` |
| `termux-x11/.../LoriePreferences.java` | single-screen prefs; theme; less fragment nav |
| `termux-x11/.../extrakeys/ExtraKeysView.java` | icon draw fix + NC colors |
| `termux-x11/.../utils/TermuxX11ExtraKeys.java` | only if default JSON needs tweak |
| `termux-x11/src/main/res/layout/main_activity.xml` | back + keyboard chrome |
| `termux-x11/src/main/res/xml/preferences.xml` | flatten + categories |
| `termux-x11/src/main/res/values/styles.xml` (+ colors/themes) | NC prefs theme |
| `termux-x11/src/main/res/values/strings.xml` | branding, new action labels |
| `termux-x11/src/main/res/drawable/*` | chrome btn bg, icons, tint-safe vectors |
| `termux-x11/src/main/AndroidManifest.xml` | affinity/launchMode (Phase 5) |
| `app/.../DesignTokens.kt` | **reference only** — optionally extract shared colors.xml later |
| `docs/project/ui_design.md` | SSOT (no edit required) |

**Do not touch for this plan:** `loader.apk` packaging, `libXlorie.so`, `CmdEntryPoint`, start_gui scripts (except if notification action package flags need align — unlikely).

---

## 7. Preference section inventory (flatten map)

| Section | Keys (keep) |
|---------|-------------|
| Output | displayResolutionMode, displayScale, displayResolutionExact, displayResolutionCustom, displayFilteringMode, adjustResolution, displayStretch, Reseed, PIP, fullscreen, forceOrientation, hideCutout, keepScreenOn |
| Pointer | touchMode, scaleTouchpad, showStylus*, showMouseHelper, pointerCapture, transformCapturedPointer, capturedPointerSpeedFactor, tapToMove, ignoreGamepadEvents |
| Keyboard | showAdditionalKbd, additionalKbdVisible (hidden), showIMEWhileExternalConnected, preferScancodes, hardwareKbdScancodesWorkaround, dexMetaKeyCapture, enableAccessibilityService*, pauseKeyInterceptingWithEsc, filterOutWinkey, enforceCharBasedInput |
| Extra keys bar | adjustHeightForEK, useTermuxEKBarBehaviour, opacityEKBar, extra_keys_config |
| Gestures & actions | swipeUp/Down, volumeUp/Down, **backButtonAction** (default return-to-app), notification*, mediaKeysAction |
| Other | clipboardEnable, requestNotificationPermission, storeSecondaryDisplayPreferencesSeparately |
| About | version |

---

## 8. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Icon fix still blank on OEM | Unicode fallback in same `setIcon` path |
| Changing launchMode kills X connection | Phase 5 isolated; device test; revert affinity if needed |
| Flattening prefs breaks `fragment=` deep links / CLI `CHANGE_PREFERENCE` | Keep `Prefs` DataStore keys identical; Receiver path untouched |
| Theme breaks preference widgets | Test Switch/SeekBar/List dialogs on API 26 + 34 |
| `Class.forName("com.zenithblue.nativecode.MainActivity")` fragile | Prefer constant / `BuildConfig` host activity class string in one place |
| Notification permission denials | Existing request path; don’t spam |

---

## 9. Explicit non-goals

- Building/signing APK  
- Moving X11 into Compose host  
- Renaming Java package `com.termux.x11` (breaks Loader / scripts / AIDL)  
- Changing XFCE start/stop scripts  
- Removing power-user prefs  

---

## 10. Recommended decision log (confirm before impl)

| # | Decision | Recommendation |
|---|----------|----------------|
| D1 | Combine into NativeCode? | **Yes for UX/task/branding; keep `:termux-x11` library** (Option B) |
| D2 | System Back | **Return to NativeCode** (not toggle IME) |
| D3 | Extra keys default | **ON** when connected |
| D4 | Prefs structure | **Single page + sections**; version footer only |
| D5 | Notification brand | **NativeCode Desktop** + green accent |
| D6 | Finish X11 on Back? | **No** — leave session; user Stop XFCE from Settings |
| D7 | APK during work | **Never** unless asked |

---

## 11. Implementation order (when approved)

1. Phase 1 icons + default bar  
2. Phase 2 chrome + Back semantics  
3. Phase 3 prefs flatten + theme  
4. Phase 4 notification  
5. Phase 5 affinity/launchMode (optional gate after device test)  
6. Phase 6 stub polish  
7. Phase 7 compile + checklist  

**Stop after plan** — no code until user approves.

---

## 12. Open questions for user (optional)

1. Notification action 2: **Exit (finish)** vs **Open NativeCode** vs both?  
2. Recents: OK to risk `singleInstance` change, or branding-only if session flaky?  
3. Exact product string: “NativeCode Desktop” vs “X11” vs “Graphical Desktop”?  
4. On Back: keep X process running (recommended) or also stop XFCE?

---

**End of plan. No implementation performed.**
