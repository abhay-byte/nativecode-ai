# Plan: Landscape Mode Navigation Rail (Left-Side Nav)

## Status: RESEARCH COMPLETE — Ready to implement

---

## Context

**App**: NativeCode (`com.ivarna.nativecode`) — pure View-based Android (no Compose)  
**File**: `app/src/main/java/com/ivarna/nativecode/MainActivity.kt` (~5480 lines)  
**Material**: `com.google.android.material:material:1.11.0`

### Two Navigation Sets
| Set | Portrait widget | Landscape widget | Controls |
|-----|----------------|-----------------|---------|
| **Global** (Home/Projects/Terminal/Settings) | `bottomNavigation` (BottomNavigationView) | `globalNavRail` (NavigationRailView) | `setupBottomNavigationListener()` / `setupGlobalNavRailListener()` |
| **Project** (Workspace/Directory/Diff/Settings) | `projectBottomNavigation` (BottomNavigationView) | `projectNavRail` (NavigationRailView) | `setupProjectBottomNavigationListener()` / `setupProjectNavRailListener()` |

### Current Layout Structure
```
rootLayout (LinearLayout)
├── sideNavContainer (FrameLayout)  ← LEFT SIDE in landscape (HORIZONTAL orientation)
│   ├── globalNavRail (NavigationRailView)
│   └── projectNavRail (NavigationRailView)
└── mainContentLayout (LinearLayout)
    ├── unifiedHeader
    ├── contentFrame
    └── bottomNavContainer (FrameLayout)  ← BOTTOM in portrait (VERTICAL orientation)
        ├── bottomNavigation
        └── projectBottomNavigation
```

**Portrait**: `rootLayout` = VERTICAL → `sideNavContainer` GONE, `bottomNavContainer` VISIBLE  
**Landscape**: `rootLayout` = HORIZONTAL → `sideNavContainer` VISIBLE (left), `bottomNavContainer` GONE

---

## Problem Analysis

The architecture is correct but the previous session made partial changes. Potential issues:

1. **NavRail layout params** — `NavigationRailView` needs `WRAP_CONTENT` width and `MATCH_PARENT` height inside `FrameLayout`
2. **Selection sync on rotation** — Selected item in NavRail must silently sync from BottomNav state (and vice versa) without triggering double-navigation
3. **Inset handling in landscape** — `sideNavContainer` absorbs left system bar inset; `mainContentLayout` must NOT double-add left padding
4. **NavRail label visibility** — Should use `LABEL_VISIBILITY_LABELED` for usability in vertical rail
5. **`bottomNavContainer` visibility** — Must be GONE in landscape even when `showGlobalNav` or `showProjectNav` is true (L823 — currently correct, verify no regression)
6. **Rotation listener** — `updateNavigationVisibility()` wired via `addOnLayoutChangeListener` at L880; verify fires correctly on device rotation

---

## Research Findings

### Material Design 3 Guidelines
- **Compact width** (phone portrait) → `BottomNavigationBar` ✓
- **Landscape phone** → `NavigationRail` on **left** (leading edge) ✓
- NavigationRail must NEVER be placed horizontally
- Rail handles its own left system bar inset

### Android View-based Notes (Material 1.11.0)
- `NavigationRailView` is stable in Material 1.11.0
- `labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED` — shows labels always
- Sync pattern: `setOnItemSelectedListener(null)` → set `selectedItemId` → restore listener
- `addOnLayoutChangeListener` on root is the correct hook for orientation in code-only UI (no XML)

---

## Implementation Steps

### Step 0 — Pre-flight screenshot (verify current state)
```bash
adb shell screencap -p /sdcard/before_landscape.png && adb pull /sdcard/before_landscape.png
```

### Step 1 — Fix `buildRootLayout()` NavRail layout params + labels (MainActivity.kt L1071–1122)

**globalNavRail** (L1071–1094): add `labelVisibilityMode`
```kotlin
globalNavRail = NavigationRailView(this).apply {
    layoutParams = FrameLayout.LayoutParams(WRAP, MATCH)
    setBackgroundColor(Color.parseColor("#120F16"))
    itemIconTintList = tintList
    itemTextColor = tintList
    labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED  // ADD
    // ... menu items + listener unchanged ...
}
```

**projectNavRail** (L1096–1119): same `labelVisibilityMode` addition

### Step 2 — Fix `updateNavigationVisibility()` — insets + selection sync (L795–855)

**Inset fix** (landscape branch):
```kotlin
// LANDSCAPE
sideNavContainer.setPadding(bars.left, bars.top, 0, bars.bottom)
mainContentLayout.setPadding(0, bars.top, bars.right, bars.bottom)
bottomNavContainer.setPadding(0, 0, 0, 0)

// PORTRAIT
sideNavContainer.setPadding(0, 0, 0, 0)
mainContentLayout.setPadding(0, bars.top, 0, 0)
bottomNavContainer.setPadding(bars.left, 0, bars.right, bars.bottom)
```

**Selection sync** — add at end of `updateNavigationVisibility()`, after visibility is set:
```kotlin
val currentPage = if (pageStack.isNotEmpty()) pageStack.peek() else ID_HOME

if (isLandscape && showGlobalNav && ::globalNavRail.isInitialized) {
    globalNavRail.setOnItemSelectedListener(null)
    globalNavRail.selectedItemId = currentPage
    setupGlobalNavRailListener()
}
if (isLandscape && showProjectNav && ::projectNavRail.isInitialized) {
    projectNavRail.setOnItemSelectedListener(null)
    projectNavRail.selectedItemId = currentPage
    setupProjectNavRailListener()
}
if (!isLandscape && showGlobalNav && ::bottomNavigation.isInitialized) {
    bottomNavigation.setOnItemSelectedListener(null)
    bottomNavigation.selectedItemId = currentPage
    setupBottomNavigationListener()
}
if (!isLandscape && showProjectNav && ::projectBottomNavigation.isInitialized) {
    projectBottomNavigation.setOnItemSelectedListener(null)
    projectBottomNavigation.selectedItemId = currentPage
    setupProjectBottomNavigationListener()
}
```

### Step 3 — Build release APK
```bash
cd /home/abhay/repos/termux-lib
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Step 4 — Install on device
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.ivarna.nativecode/.MainActivity
```

### Step 5 — Device verification

**Global nav:**
1. Portrait → BottomNav at bottom (Home/Projects/Terminal/Settings) ✓
2. Rotate landscape → NavRail on LEFT, BottomNav GONE ✓
3. Tap items → correct pages, correct highlight ✓
4. Rotate back → BottomNav shows same selection ✓

**Project nav:**
1. Open project → portrait → project BottomNav at bottom ✓
2. Rotate landscape → project NavRail on LEFT ✓
3. Tap items → correct pages ✓

**Screenshot capture:**
```bash
adb shell screencap -p /sdcard/after_global_landscape.png && adb pull /sdcard/after_global_landscape.png
adb shell screencap -p /sdcard/after_project_landscape.png && adb pull /sdcard/after_project_landscape.png
```

---

## Files to Modify

| File | Lines | Change |
|------|-------|--------|
| `MainActivity.kt` | L1071–1094 | Add `labelVisibilityMode` to `globalNavRail` |
| `MainActivity.kt` | L1096–1119 | Add `labelVisibilityMode` to `projectNavRail` |
| `MainActivity.kt` | L840–854 | Fix inset handling (portrait vs landscape) |
| `MainActivity.kt` | L855 (new) | Add selection sync block at end of `updateNavigationVisibility()` |

---

## Risk / Notes

- No new dependencies — `NavigationRailView` already imported and used
- `labelVisibilityMode` requires API 26+ — `minSdk=26` ✓
- Release build uses `signingConfig = debug` — fine for device testing
- `isMinifyEnabled = false` — no ProGuard issues
- `onConfigurationChanged` is NOT overridden — layout change listener handles rotation ✓

---

## Approval Gate

> **Awaiting user approval before implementing Steps 1–5.**
