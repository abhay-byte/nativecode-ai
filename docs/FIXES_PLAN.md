# Fix Plan & Verification Status — NativeCode App

All line references are in `app/src/main/java/com/zenithblue/nativecode/MainActivity.kt` unless noted.

---

## 🛠️ Issues and Status

### Issue 1 — Keyboard Pops Up on Home Page
* **Status:** Resolved ✅
* **Fix details:** Added `window.decorView.clearFocus()` on page change, set `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS` on home scroll view, and configured `windowSoftInputMode="adjustResize|stateHidden"` in manifest.

### Issue 2 — Back Button in Top Bar (unifiedHeader) Behavior
* **Status:** Resolved & Removed ✅
* **Fix details:** The redundant global back button was removed from `unifiedHeader`. Main pages now rely on bottom navigation and system-wide back gestures.

### Issue 3 — Terminal Page Top Bar Buttons
* **Status:** Resolved & Restored ✅
* **Fix details:** Terminal and add terminal buttons were restored to `unifiedHeader` for direct access from home/settings, and a dedicated Terminal item was added to the bottom navigation bar.

### Issue 4 — Foreground Service & Notification
* **Status:** Resolved ✅
* **Fix details:** Changed services to `START_STICKY`, added foreground service permissions, set small icons to `R.drawable.ic_terminal`, and passed `PROJECT_PATH` extra to restore project context on notification tap.

### Issue 5 — System Back Navigation (Terminal & Workspace)
* **Status:** Resolved ✅
* **Fix details:** Replaced deprecated `onBackPressed()` overrides with `OnBackPressedDispatcher` callback registration. Removed the exit confirmation override from the terminal page so it correctly goes back to Home.

### Issue 6 — Status Bar and Bottom Nav Bar Insets
* **Status:** Resolved ✅
* **Fix details:** Applied correct top and bottom padding dynamically inside window insets listener to respect notch, status bar, and keyboard insets across all views.

### Issue 7 — Navigation Icons Not Rendering
* **Status:** Resolved ✅
* **Fix details:** Replaced all system `android.R.drawable` icon references in `BottomNavigationView` with custom vector drawables (`ic_home`, `ic_folder`, `ic_terminal`, `ic_settings`) and created a matching `ColorStateList` with `NC.PRIMARY` and `NC.OUTLINE` colors.
