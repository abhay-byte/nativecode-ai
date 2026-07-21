# Plan: Make Row 2 Extra Buttons Match Row 1 Size

## Problem

Terminal special-keys toolbar has 2 rows:

- **Row 1** (CTRL/ALT/SHFT/ESC/TAB/ENT/BKSP/📎): full-width, fixed `44dp` height, zero padding, zero margin — buttons fill the bar edge-to-edge.
- **Row 2** (arrows/nav/symbols in a `HorizontalScrollView`): buttons use `WRAP_CONTENT` with `8dp` horizontal padding, `6dp` vertical padding, `5dp` right margin, and the row container has `6dp` horizontal + `3–5dp` vertical padding. Buttons are visually smaller with gaps between them.

## Goal

Row 2 buttons should be **same height** as row 1 (`44dp`) and have **no space** around them (no margin, no container padding) — edge-to-edge like row 1.

## Root Cause

All code is in `MainActivity.kt` — no XML layouts.

| Location | Line | Current Value | Target |
|---|---|---|---|
| `makeToolbarKeyBtn` default `marginRight` | 1298 | `dp(5)` | `0` |
| `makeToolbarKeyBtn` vertical padding | 1306 | `dp(6)` top/bottom | grow to fill `44dp` or set fixed height |
| `row2` container padding | 1471 | `dp(6), dp(3), dp(6), dp(5)` | `0,0,0,0` |
| `fScroll` (F-keys row) padding | 1544 | `dp(6), dp(2), dp(6), dp(5)` | `0,0,0,0` |
| Each `makeToolbarKeyBtn` call in row2 | 1479,1499,1516,1532,1547 | uses default `marginRight=dp(5)` | pass `marginRight=0` |
| Divider margins | 1490, 1510, 1527 | `leftMargin=dp(3), rightMargin=dp(8)` | `0,0` or remove |

## Changes Required

### 1. `makeToolbarKeyBtn` — add `height` param (non-breaking)

```kotlin
// BEFORE (line 1298)
private fun makeToolbarKeyBtn(label: String, widePad: Boolean = false, cornerRadius: Int = dp(5), marginRight: Int = dp(5)): TextView

// AFTER
private fun makeToolbarKeyBtn(label: String, widePad: Boolean = false, cornerRadius: Int = dp(5), marginRight: Int = dp(5), height: Int = WRAP): TextView
```

Change `layoutParams` line (1307):
```kotlin
// BEFORE
layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = marginRight }

// AFTER
layoutParams = LinearLayout.LayoutParams(WRAP, height).apply { rightMargin = marginRight }
```

### 2. Row 2 container — remove padding (line 1471)

```kotlin
// BEFORE
setPadding(dp(6), dp(3), dp(6), dp(5))

// AFTER
setPadding(0, 0, 0, 0)
```

### 3. All `makeToolbarKeyBtn` calls in row 2 — pass `marginRight=0, height=dp(44)`

Arrow keys (line 1479):
```kotlin
val btn = makeToolbarKeyBtn(kd.label, marginRight = 0, height = dp(44))
```

Nav keys (line 1499):
```kotlin
val btn = makeToolbarKeyBtn(kd.label, marginRight = 0, height = dp(44))
```

Sym keys (line 1516):
```kotlin
val btn = makeToolbarKeyBtn(sym, marginRight = 0, height = dp(44))
```

Fn button (line 1532):
```kotlin
val fnBtn = makeToolbarKeyBtn("Fn", marginRight = 0, height = dp(44))
```

F1–F12 keys (line 1547):
```kotlin
val btn = makeToolbarKeyBtn("F$n", marginRight = 0, height = dp(44))
```

### 4. Dividers — remove side margins (lines 1490, 1510, 1527)

```kotlin
// BEFORE
layoutParams = LinearLayout.LayoutParams(dp(1), dp(18)).apply { leftMargin = dp(3); rightMargin = dp(8) }

// AFTER
layoutParams = LinearLayout.LayoutParams(dp(1), dp(44))
```
(Stretch divider to full row height too, looks cleaner.)

### 5. F-keys scroll container — remove padding (line 1544)

```kotlin
// BEFORE
setPadding(dp(6), dp(2), dp(6), dp(5))

// AFTER
setPadding(0, 0, 0, 0)
```

## Files Changed

- `app/src/main/java/com/ivarna/nativecode/MainActivity.kt`
  - Lines: 1298, 1307, 1471, 1479, 1490, 1499, 1510, 1516, 1527, 1532, 1544, 1547

## No Changes Needed

- Row 1 buttons — already correct
- `makeModifierBtn` — only used in row 1
- XML layouts — none used for this toolbar
