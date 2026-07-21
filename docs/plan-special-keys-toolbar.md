# Plan: Special Keys Toolbar + Image Attacher

## Current State

Both `buildKeyboardToolbar()` (App Terminal) and `buildWorkspaceKeyboardToolbar()` (Project Workspace) are dummy:
- Only 8 keys: `Tab`, `Ctrl`, `Alt`, `Esc`, `/`, `|`, `~`, `-`
- `Ctrl` / `Alt` do **nothing special** — they just write the string literal
- No arrow keys, Enter, Backspace, Home/End, PageUp/Down, F-keys
- `buildWorkspaceAttachBar()` / `attachBar` are inert buttons (no click handler)
- No image picker, no base64 injection

---

## Key Injection — How It Works (from termux-app source)

### For special keys (keycode-based)
`TerminalExtraKeys.onTerminalExtraKeyButtonClick()` in `TerminalExtraKeys.java`:
```java
Integer keyCode = PRIMARY_KEY_CODES_FOR_STRINGS.get(key); // e.g. "ESC" -> KeyEvent.KEYCODE_ESCAPE
int metaState = 0;
if (ctrlDown) metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
if (altDown)  metaState |= KeyEvent.META_ALT_ON  | KeyEvent.META_ALT_LEFT_ON;
KeyEvent keyEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState);
mTerminalView.onKeyDown(keyCode, keyEvent);  // <-- the correct injection point
```

### For printable chars (text-based)
```java
mTerminalView.inputCodePoint(codePoint, ctrlDown, altDown);
```

### Key → KeyCode mapping (from ExtraKeysConstants.java)
| Key string | KeyEvent constant |
|---|---|
| ESC | KEYCODE_ESCAPE |
| TAB | KEYCODE_TAB |
| ENTER | KEYCODE_ENTER |
| BKSP | KEYCODE_DEL |
| DEL | KEYCODE_FORWARD_DEL |
| UP/DOWN/LEFT/RIGHT | KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT |
| HOME | KEYCODE_MOVE_HOME |
| END | KEYCODE_MOVE_END |
| PGUP | KEYCODE_PAGE_UP |
| PGDN | KEYCODE_PAGE_DOWN |
| F1–F12 | KEYCODE_F1–F12 |
| INS | KEYCODE_INSERT |
| SPACE | KEYCODE_SPACE |

### Modifier keys (CTRL, ALT, SHIFT)
- These are **stateful toggle buttons** — they are active until next non-modifier key is pressed
- Long press = **lock** (stays active until pressed again)
- Visual: active state shown with different background/text color

---

## Image Attachment — How CLI Tools Accept Images

### opencode
- TUI: paste image from clipboard via `Ctrl+V` (reads clipboard as file path or raw image)
- CLI: `opencode run -f image.png "prompt"`
- **Strategy for terminal injection**: write the shell command `opencode -f /tmp/attached_img.png` — save image to temp file, then write the command referencing it

### General approach (works with any AI CLI tool)
1. User selects image from Android gallery (via `ActivityResultLauncher` + `Intent.ACTION_OPEN_DOCUMENT`)
2. App copies image to a temp file (e.g., `/data/data/.../files/attach_temp.jpg` or accessible path like `/sdcard/Download/attach_temp.jpg`)
3. App writes a shell command to the terminal: `cat /path/to/image | base64` or directly writes a file reference path
4. For opencode specifically: saves to `/tmp/img.png` (accessible in proot/debian env), then injects `--file /tmp/img.png` into the prompt

### Base64 approach (generic — works in any REPL/CLI accepting stdin)
```bash
# Write base64 of image, then reference it
base64 /tmp/img.png
# or pipe it to a tool
cat /tmp/img.png | opencode run -f /dev/stdin "describe this"
```

---

## UI Design

### Combined toolbar — single compact row(s) replacing two separate bars

**Row 1: Modifier + core keys** (always visible, fixed)
```
[⎈ CTRL] [⎇ ALT] [⇧ SHIFT] | [⎋ ESC] [↹ TAB] [↲ ENTER] [⌫ BKSP] | [📎 IMG]
```

**Row 2: Scrollable special keys** (horizontally scrollable)
```
[←] [↑] [↓] [→] | [⇱ HOME] [⇲ END] [⇑ PGUP] [⇓ PGDN] | [/] [|] [~] [-] [_] [\] ["] ['] [;] [:]
```

**F-keys row** (only on swipe-up or toggle button — saves space)
```
[F1] [F2] [F3] [F4] [F5] [F6] [F7] [F8] [F9] [F10] [F11] [F12]
```

### Modifier key behavior
- CTRL/ALT/SHIFT: single tap = **one-shot** (applies to next key, then resets)
- Long press = **lock** (stays active, shown with accent color + indicator dot)
- Active modifier highlights in accent color

### Image attach button (📎)
- Compact icon button on Row 1 right side
- Tap → Android photo picker (`ActivityResultLauncher<PickVisualMediaRequest>`)
- On image picked: copies to accessible path → injects shell command into terminal

---

## Implementation Plan

### Phase 1: Key injection helpers

Create `fun injectKey(terminalView: TerminalView?, session: TerminalSession?, keyName: String, ctrl: Boolean, alt: Boolean, shift: Boolean)`:
```kotlin
val keyCodes = mapOf(
    "ESC" to KeyEvent.KEYCODE_ESCAPE,
    "TAB" to KeyEvent.KEYCODE_TAB,
    "ENTER" to KeyEvent.KEYCODE_ENTER,
    "BKSP" to KeyEvent.KEYCODE_DEL,
    "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
    "UP" to KeyEvent.KEYCODE_DPAD_UP,
    "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
    "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
    "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
    "HOME" to KeyEvent.KEYCODE_MOVE_HOME,
    "END" to KeyEvent.KEYCODE_MOVE_END,
    "PGUP" to KeyEvent.KEYCODE_PAGE_UP,
    "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN,
    "INS" to KeyEvent.KEYCODE_INSERT,
    "F1" to KeyEvent.KEYCODE_F1,
    // ... F2-F12
)
val keyCode = keyCodes[keyName]
if (keyCode != null) {
    var meta = 0
    if (ctrl)  meta = meta or (KeyEvent.META_CTRL_ON  or KeyEvent.META_CTRL_LEFT_ON)
    if (alt)   meta = meta or (KeyEvent.META_ALT_ON   or KeyEvent.META_ALT_LEFT_ON)
    if (shift) meta = meta or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
    val ev = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta)
    terminalView?.onKeyDown(keyCode, ev)
} else {
    // printable char
    keyName.codePoints().forEach { cp ->
        terminalView?.inputCodePoint(cp, ctrl, alt)
    }
}
```

### Phase 2: Modifier state management

```kotlin
data class ModifierState(
    var ctrlActive: Boolean = false,
    var ctrlLocked: Boolean = false,
    var altActive: Boolean = false,
    var altLocked: Boolean = false,
    var shiftActive: Boolean = false,
    var shiftLocked: Boolean = false
)
```
- Single shot: `active=true, locked=false` → consumed after next non-modifier key press
- Locked: `active=true, locked=true` → stays until pressed again

### Phase 3: Rebuild toolbar UI

Replace both `buildKeyboardToolbar()` and `buildWorkspaceKeyboardToolbar()` with a shared `buildSpecialKeysToolbar(terminalViewRef, sessionRef, modState)` that returns a `LinearLayout` with:
- 2 rows (top fixed row + bottom scrollable row)
- Toggle F-keys row via `[Fn]` button
- Height: ~80-90dp total (40-45dp per row), compact pill-style buttons

**Button style:**
- Normal: `roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(6))`, text `NC.ON_SURF_VAR`
- Active modifier: `roundedBg(NC.PRIMARY.withAlpha(0x33), NC.PRIMARY, dp(6))`, text `NC.PRIMARY`
- Locked modifier: `roundedBg(NC.PRIMARY, NC.PRIMARY, dp(6))`, text white + lock dot

### Phase 4: Image Attacher

#### Picker setup
```kotlin
// Register at activity level (onCreate)
val imagePickerLauncher = registerForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri ->
    uri?.let { handleImageAttachment(it) }
}
```

#### handleImageAttachment
```kotlin
fun handleImageAttachment(uri: Uri) {
    // 1. Copy to accessible temp location
    val destFile = File(filesDir, "attach_temp.${getExtension(uri)}")
    contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output -> input.copyTo(output) }
    }
    
    // 2. Copy into proot environment if needed
    //    Path accessible from debian proot: /data/data/com.ivarna.nativecode/files/attach_temp.jpg
    //    Or use: cp to /sdcard/Download/ for simpler path
    
    // 3. Inject file path into terminal
    val cmd = "\n--file ${destFile.absolutePath} "  // for opencode
    // OR: type path as text for user to complete the command
    activeTerminalSession()?.write(destFile.absolutePath)
}
```

#### What gets injected
- For opencode: the image path (user pastes into prompt or it's auto-appended)
- For generic CLI: shows a dialog "Image saved to: [path]" + copies path to clipboard
- UI shows a small thumbnail chip in the attach bar after selection

### Phase 5: Merge attach bar into toolbar

Replace separate `attachBar` (bottom bar with huge dummy button) with:
- `📎` icon button in Row 1 of keyboard toolbar (right-aligned)
- After image picked, show a removable chip above toolbar:
  ```
  [🖼 attach_temp.jpg ×]
  ```
- Chip click → copy path to clipboard; ×  → remove attachment

---

## Files to Modify

| File | Change |
|---|---|
| `MainActivity.kt` | Replace `buildKeyboardToolbar()`, `buildWorkspaceKeyboardToolbar()`, `buildWorkspaceAttachBar()`, `attachBar` block; add `injectKey()` helper; add `ModifierState`; add image picker launcher; add `handleImageAttachment()` |
| `AndroidManifest.xml` | Add `READ_MEDIA_IMAGES` permission (API 33+) / `READ_EXTERNAL_STORAGE` (API <33) |

No new files needed. No new dependencies needed (`ActivityResultContracts.PickVisualMedia` is in `androidx.activity` already present).

---

## Compact UI Sketch

```
┌─────────────────────────────────────────────────────┐
│ [⎈CTRL] [⎇ALT] [⇧SHF] │ [⎋ESC] [↹TAB] [↲ENT] [⌫]│[📎]│  ← Row 1 (fixed)
├─────────────────────────────────────────────────────┤
│ ← [←][↑][↓][→] [⇱][⇲][⇑][⇓] [/][|][~][-][_][\]→ │  ← Row 2 (scroll)
└─────────────────────────────────────────────────────┘
```

Total toolbar height: ~88dp (2 rows × 44dp).
Replaces: ~44dp keyboard toolbar + ~60dp attach bar = ~104dp → saves ~16dp + adds full functionality.

---

## Permissions Required

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```
`PickVisualMedia` (Photo Picker API, Android 11+) handles permissions automatically.
For older APIs, fallback to `ACTION_GET_CONTENT` with manual permission check.

---

## Testing Checklist

- [ ] CTRL+C sends SIGINT (kills running process)
- [ ] CTRL+L clears screen
- [ ] ALT+. recalls last argument (bash)
- [ ] Arrow keys navigate command history
- [ ] TAB triggers autocompletion
- [ ] ENTER sends command
- [ ] BKSP deletes character
- [ ] Modifier one-shot: CTRL → C → ctrl deactivates automatically
- [ ] Modifier lock: long-press CTRL → stays active → press again → unlocks
- [ ] Image picker opens Android photo picker
- [ ] Picked image copied to accessible path
- [ ] Path injected into terminal / shown as chip
- [ ] Works on both App Terminal and Workspace Terminal
- [ ] F-keys toggle row shows/hides correctly
- [ ] No crash on rotation / page navigation
