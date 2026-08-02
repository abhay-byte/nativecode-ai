# Plan: Image Attach → Copy Base64 to Clipboard

## Problem

Current flow:
1. User taps attach (📎) button → picks image from gallery
2. Image copied to `filesDir` → chip shown in toolbar
3. User taps chip → **copies file path** (e.g. `/data/data/.../files/attach_123.jpg`) to clipboard
4. Toast says "Path copied"

Desired flow:
1. User taps attach button → picks image
2. Image copied to `filesDir` (keep existing storage logic)
3. User taps chip → **reads file as bytes → Base64 encodes → copies `data:<mime>;base64,<b64>` to clipboard**
4. Toast says "Base64 copied"

---

## Root Cause

`MainActivity.kt:1269–1273` — `chipTv.setOnClickListener` does:

```kotlin
cm.setPrimaryClip(ClipData.newPlainText("image_path", path))
Toast.makeText(..., "Path copied", ...).show()
```

Should instead read the file, Base64-encode, and set that as clipboard text.

---

## Implementation Plan

### File to change

**Single file**: `app/src/main/java/com/zenithblue/nativecode/MainActivity.kt`

### Change surface

Only `refreshAttachChip()` needs editing — specifically the `chipTv.setOnClickListener` lambda.

`android.util.Base64` is **already imported** (line 13). No new deps.

### New click handler logic

```kotlin
setOnClickListener {
    try {
        val mime = contentResolver.getType(Uri.fromFile(File(path)))
            ?: "image/jpeg"
        val bytes = File(path).readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUri = "data:$mime;base64,$b64"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("image_base64", dataUri))
        Toast.makeText(this@MainActivity, "Base64 copied", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("ImageAttach", "Failed to encode base64", e)
        Toast.makeText(this@MainActivity, "Failed to encode image", Toast.LENGTH_SHORT).show()
    }
}
```

**Note on MIME**: `contentResolver.getType(Uri.fromFile(...))` returns null for file:// URIs on API 24+. Better approach: store MIME at `handleImageAttachment` time (already knows it from `contentResolver.getType(uri)`) and pass it into `refreshAttachChip`, or re-derive from extension:

```kotlin
val mime = when (File(path).extension.lowercase()) {
    "png"  -> "image/png"
    "webp" -> "image/webp"
    "gif"  -> "image/gif"
    else   -> "image/jpeg"
}
```

This is simpler and more reliable.

### Also update hint text

Line ~1293, hint text:
```kotlin
text = "tap chip → copy path"
// change to:
text = "tap chip → copy base64"
```

---

## Affected Lines in MainActivity.kt

| Line | Current | Change |
|------|---------|--------|
| 1269 | `// tap to copy path to clipboard` | `// tap to copy base64 to clipboard` |
| 1271 | `cm.setPrimaryClip(ClipData.newPlainText("image_path", path))` | replace with Base64 encode + set |
| 1272 | `Toast.makeText(..., "Path copied", ...)` | `"Base64 copied"` |
| ~1293 | `text = "tap chip → copy path"` | `text = "tap chip → copy base64"` |

---

## Edge Cases

| Case | Handling |
|------|----------|
| Large image (>5MB) | Base64 of 5MB = ~6.7MB string. Android clipboard has no hard limit but some apps truncate. Acceptable — user's choice to attach large image. |
| File deleted between attach and tap | `File(path).readBytes()` throws → caught, toast "Failed to encode image" |
| MIME unknown extension | Fallback to `image/jpeg` |

---

## Testing

1. Attach small PNG → tap chip → paste in terminal → should see `data:image/png;base64,iVBOR...`
2. Attach JPEG → paste → `data:image/jpeg;base64,...`
3. Attach WEBP → paste → `data:image/webp;base64,...`
4. Verify hint text changed to "tap chip → copy base64"
5. Verify toast says "Base64 copied"

---

## Not in scope

- Workspace attach chip (same function `refreshAttachChip` with `isWorkspace=true`) — **same fix applies automatically**, both paths share the same chip rendering code
- Storing base64 eagerly at attach time (unnecessary; lazy on tap is fine)
- UI for base64 preview
