# Plan: Fix Pinch-to-Zoom in Terminal Views

## Root Cause

**All three `TerminalViewClient` implementations in `MainActivity.kt` use a no-op `onScale`:**

```kotlin
// Lines 1549, 2564, 3737 — identical pattern in all 3 terminals
override fun onScale(scale: Float): Float = scale
```

This just returns the raw accumulated scale factor unchanged. It **never calls `setTextSize`**, so the view never redraws at a new font size. Pinch gesture fires correctly (the infrastructure works: `GestureAndScaleRecognizer` → `ScaleGestureDetector` → `TerminalView.onScale` → `mClient.onScale`) but the client throws the value away.

## How Reference (`termux-app`) Does It

### Flow
```
ScaleGestureDetector.onScale()
  → GestureAndScaleRecognizer.mListener.onScale(focusX, focusY, scaleFactor)
    → TerminalView.mScaleFactor *= scale
    → TerminalView.mClient.onScale(mScaleFactor)   ← client MUST reset scale and call setTextSize
      → TermuxTerminalViewClient.onScale()
        → if (|scale - 1.0| > 0.1) changeFontSize(increase)
        → return 1.0f   ← CRITICAL: resets mScaleFactor so it doesn't drift unboundedly
```

### Reference `onScale` (TermuxTerminalViewClient.java:163)
```java
@Override
public float onScale(float scale) {
    if (scale < 0.9f || scale > 1.1f) {
        boolean increase = scale > 1.f;
        changeFontSize(increase);   // calls setTextSize on the view
        return 1.0f;                // MUST reset to 1.0 — TerminalView multiplies this back into mScaleFactor
    }
    return scale;
}
```

`changeFontSize(increase)` → `preferences.changeFontSize(increase)` → clamps to `[MIN_FONTSIZE, MAX_FONTSIZE]` → calls `terminalView.setTextSize(newSize)`.

### Key constraint: return `1.0f` after acting
`TerminalView` does `mScaleFactor = mClient.onScale(mScaleFactor)`. If client returns `scale` (our bug), `mScaleFactor` keeps accumulating. Return `1.0f` to reset it each gesture step.

## Our Bug Summary

| Location | Line | Issue |
|----------|------|-------|
| `initTerminalView()` viewClient | 2564 | `onScale` returns `scale`, never calls `setTextSize` |
| workspace viewClient | 3737 | same |
| scriptInstall viewClient | 1549 | same |

Initial font size is hardcoded `40` (`setTextSize(40)`). No min/max clamping. No persistence.

## Fix Plan

### Step 1 — Add font size state variables (per terminal or shared)

```kotlin
// In MainActivity class body
private var termFontSize = 40
private var workspaceFontSize = 40
private var scriptFontSize = 40

private val MIN_FONT_SIZE = 10
private val MAX_FONT_SIZE = 72
```

### Step 2 — Implement `onScale` correctly in each viewClient

Pattern (same for all 3, using the relevant view and size var):

```kotlin
override fun onScale(scale: Float): Float {
    if (scale < 0.9f || scale > 1.1f) {
        termFontSize = (termFontSize * scale).toInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        terminalView.setTextSize(termFontSize)
        return 1.0f   // reset mScaleFactor
    }
    return scale
}
```

**Three locations to change:**
- Line ~2564: `viewClient` in `initTerminalView()` — uses `terminalView`, `termFontSize`
- Line ~3737: `workspaceViewClient` in workspace init — uses `workspaceTerminalView`, `workspaceFontSize`
- Line ~1549: `scriptViewClient` in script install init — uses `scriptInstallTerminalView`, `scriptFontSize`

### Step 3 — Update initial `setTextSize` calls to use the state vars

```kotlin
// Line ~2555
terminalView.setTextSize(termFontSize)        // was: setTextSize(40)

// Line ~3730
workspaceTerminalView.setTextSize(workspaceFontSize)

// Line ~1518
scriptInstallTerminalView.setTextSize(scriptFontSize)
```

### Step 4 (optional) — SharedPreferences persistence

Not required for the bug fix but mirrors reference behavior. Add after zoom:
```kotlin
getSharedPreferences("terminal_prefs", MODE_PRIVATE)
    .edit().putInt("font_size", termFontSize).apply()
```
And restore in `onCreate`. Only do this if persistence across sessions is desired.

## Files to Change

| File | Changes |
|------|---------|
| `app/src/main/java/com/ivarna/nativecode/MainActivity.kt` | Add 3 font size vars + MIN/MAX constants, fix 3 `onScale` impls, update 3 `setTextSize` calls |

**Total: 1 file, ~12 lines changed/added.**

## Verification

1. Build + install app
2. Open any terminal
3. Pinch out (spread fingers) → text should get larger
4. Pinch in (close fingers) → text should get smaller
5. Hit MIN (10sp) and MAX (72sp) limits — should stop, not crash
6. Rotate device — font size should be preserved (it's in-memory; add SharedPrefs for cross-session)

## Why Gesture Infrastructure is Already Fine

- `GestureAndScaleRecognizer.java` in our `termux-app` submodule has `ScaleGestureDetector` with `setQuickScaleEnabled(false)` ✓
- `TerminalView.java` routes `onScale` to `mClient` ✓
- `isFocusableInTouchMode` is `false` on workspace terminal — pinch may conflict; may need to set `true` or intercept touch at parent. Check if workspace terminal needs zoom at all.
