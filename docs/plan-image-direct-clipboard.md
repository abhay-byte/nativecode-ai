# Plan: Image Attach → Direct Clipboard Copy (No Chip) + Absolute Path Fix

## Problems

### Problem 1: Chip UX
Current flow after tapping the attach button (SVG icon, `R.drawable.ic_attach_image`):
1. Image picked from gallery
2. Saved to `filesDir/attach_<ts>.<ext>`
3. Chip shown above toolbar
4. User must tap chip to copy to clipboard

Desired flow:
1. Image picked from gallery
2. Saved to correct location, immediately copy **absolute path** to clipboard
3. No chip shown — toast confirmation only

### Problem 2: Absolute Path Previously Failed
Previous attempt copied host path (`/data/data/com.ivarna.nativecode/files/attach_*.jpg`) to clipboard.
Agent inside proot-distro Debian could not open it.

**Root cause:**
- Agent runs inside proot: `proot-distro login debian --shared-tmp --user flux`
- proot maps host `filesDir/home/` → guest `/home/flux/`
- Image was saved to `filesDir/attach_*.jpg` — one level **above** the mapped dir
- Guest `/home/flux/` does NOT include files saved directly in `filesDir`
- Agent tries to read the host path, file not found inside guest

**Fix:**
- Save image to `File(filesDir, "home/attach_<ts>.<ext>")` instead of `File(filesDir, "attach_<ts>.<ext>")`
- Copy guest path `/home/flux/attach_<ts>.<ext>` to clipboard (not host path)
- Agent inside proot opens `/home/flux/attach_<ts>.jpg` — file exists

---

## Implementation Plan

### File
`app/src/main/java/com/ivarna/nativecode/MainActivity.kt`

### Change 1: Save image to `filesDir/home/` (fix path accessibility)

**Location:** `handleImageAttachment()` — around line 1231–1232

**Current:**
```kotlin
val fname = "attach_${System.currentTimeMillis()}.$ext"
val destFile = File(filesDir, fname)
```

**New:**
```kotlin
val fname = "attach_${System.currentTimeMillis()}.$ext"
val homeDir = File(filesDir, "home").also { it.mkdirs() }
val destFile = File(homeDir, fname)
```

### Change 2: Copy guest path to clipboard immediately on pick

**Location:** `handleImageAttachment()` — around line 1236–1242

**Current:**
```kotlin
val path = destFile.absolutePath
if (isWorkspace) {
    wsAttachPath = path
    refreshAttachChip(isWorkspace = true)
} else {
    termAttachPath = path
    refreshAttachChip(isWorkspace = false)
}
```

**New:**
```kotlin
// Guest path inside proot Debian: filesDir/home/ maps to /home/flux/
val guestPath = "/home/flux/$fname"
val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
cm.setPrimaryClip(ClipData.newPlainText("image_path", guestPath))
Toast.makeText(this@MainActivity, "Image path copied", Toast.LENGTH_SHORT).show()
```

No chip. No state. Stateless — each pick overwrites clipboard.

### Change 3: Remove chip infrastructure

Remove entirely:
- `termAttachPath: String?` field (line 228)
- `wsAttachPath: String?` field (line 229)
- `termAttachChip: LinearLayout?` field (line 226)
- `wsAttachChip: LinearLayout?` field (line 227)
- `refreshAttachChip()` function (lines 1249–1338) — contains all base64 encode logic, delete it all
- `termChipContainer` LinearLayout setup + `addView` (lines ~1681–1696)
- `wsChipContainerRef` LinearLayout setup + `addView` (lines ~4261–4278)

**Before deleting:** grep for any remaining references to each field/function to ensure nothing else calls them.

### Change 4: Remove Base64 import (if unused after above)

Check line 3: `import android.util.Base64` — remove if no other usage remains.

---

## Testing Plan

### Pre-implementation baseline (device required)
1. Build + install current APK
2. Terminal page → tap attach button → pick image → chip appears
3. Tap chip → paste somewhere → confirm current output format (base64 or path)

### After implementation — functional tests

| # | Action | Expected |
|---|--------|----------|
| T1 | Attach any image from gallery | Toast "Image path copied", **no chip shown** |
| T2 | Paste clipboard in terminal | Output is `/home/flux/attach_<ts>.jpg` |
| T3 | In agent: `cat /home/flux/attach_<ts>.jpg \| base64` | Agent can read file, command works |
| T4 | In agent: open the path with any tool | File exists, not empty |
| T5 | Attach again (second image) | New path overwrites clipboard, first file still on disk |
| T6 | Workspace page attach button | Same behavior (both paths use same handler) |
| T7 | Check `filesDir/home/` on device (via adb or file manager) | `attach_<ts>.jpg` present |

### Regression tests

| # | Action | Expected |
|---|--------|----------|
| R1 | Toolbar row 1 layout | No blank chip container gap above toolbar |
| R2 | Workspace toolbar layout | No blank chip container gap |
| R3 | Build has zero errors | `Base64` import removed if unused, no dead field refs |

### Context window test (key goal)
- Paste `/home/flux/attach_<ts>.jpg` into OpenCode agent
- Agent uses `Read` tool to load image
- Conversation history stays lean — path string = ~30 chars = ~8 tokens
- No context compaction triggered

---

## What Is NOT Changing
- Attach button icon (`R.drawable.ic_attach_image` SVG)
- File-save logic in `handleImageAttachment()` (copy from URI to disk) — only destination dir changes
- Workspace attach button trigger
- Any terminal/session logic

---

## Files Changed

| File | Sections affected |
|------|------------------|
| `app/src/main/java/com/ivarna/nativecode/MainActivity.kt` | line 1231 (dest dir), 1236–1242 (clipboard logic), 226–229 (remove fields), 1249–1338 (remove fn), ~1681–1696 (remove container), ~4261–4278 (remove container) |

No new files. No new dependencies.
