# B12 — FileProvider path hardening (narrow share surface)

**Date:** 2026-07-31  
**Status:** **implemented** 2026-07-31 (code); device matrix §6 optional smoke  
**Policy:** Play security / malware review (checklist **B12**, related §5.3 oversharing)  
**Related:**  
- `docs/policy/Google_Play_Store_Policy_Compliance_Checklist.md` § B12  
- `docs/plan/image-attach-chroot-and-repairs-scripts-rehaul.md` (existing **inbound** stage pattern; reuse pattern, different direction)  
- `app/src/main/res/xml/file_paths.xml`  
- `app/src/main/AndroidManifest.xml` (FileProvider provider)  
- `MainActivity.kt` (`getFileUri` / open-with / HTML / APK)  
- `terminal/ProjectPathResolver.kt` (proot vs chroot host roots)

**Primary code (current):**

| Path | Role |
|------|------|
| `app/src/main/res/xml/file_paths.xml` | Allowed host trees for FileProvider |
| `app/src/main/AndroidManifest.xml` L20–28 | `androidx.core.content.FileProvider`, authority `${applicationId}.fileprovider`, `exported=false`, `grantUriPermissions=true` |
| `MainActivity.getFileUri` ~6241 | Sole app wrapper around `FileProvider.getUriForFile`; fallback `Uri.fromFile` |
| `MainActivity.openHtmlFile` ~6202 | External browser for HTML |
| `MainActivity.installApkFile` ~6215 | `ACTION_VIEW` + `application/vnd.android.package-archive` |
| `MainActivity.openExternalFile` ~6228 | Generic “Open with” (image/video/binary) |
| File viewer cards ~5742–6166 | Buttons that call the three helpers above |

---

## 1. Goal

Tighten FileProvider so Play/security review no longer sees **device-wide** (`root-path`) or **entire external storage** (`external-path path="."`) as shareable, **without breaking**:

1. File viewer → open image/video/HTML/generic externally  
2. File viewer → INSTALL APK (if kept)  
3. Proot project files under app `filesDir` rootfs  
4. Chroot project files under `/data/local/tmp/chrootDebian13/...`  

**Mechanism:** stage a **copy** (or hardlink when same FS + allowed) into a **dedicated app-private share dir**, then hand that path to FileProvider.

### Success definition

| Before | After |
|--------|--------|
| `file_paths.xml` has `<root-path path="."/>` + broad external/files/cache | No `root-path`; no whole-external; only `files/share/` (and optional `cache/share/`) |
| `getUriForFile` accepts any absolute path | Only files under share staging (or already under share dir) |
| Open-with works for proot via broad paths | Open-with still works via stage-copy |
| Open-with works for chroot via `root-path` | Open-with still works via stage-copy into app storage |
| B12 PARTIAL | B12 FOLLOWED after device verify |

---

## 2. Non-goals (do **not** touch)

These flows **do not use** FileProvider outbound today. Changing them is **out of scope** and high regression risk.

| Surface | How it works today | Why out of scope |
|---------|-------------------|------------------|
| **Project icon picker** | `GetContent("image/*")` → copy into `filesDir/icon_*.png` or store `content://` string | **Inbound** picker; not FileProvider export |
| **Terminal / workspace image attach** | `GetContent` → stage `filesDir/usr/tmp/nativecode_attach/` → proot copy or chroot `RootShell.copyIntoChroot` | **Inbound** only; already has staging SSOT in `ProjectPathResolver.stageAttachDir` |
| **Directory page / dir tree** | `DirectoryScanner` + `ProjectPathResolver.resolve` → host `File.listFiles` / search | Direct filesystem UI; no URI grant |
| **Git diff / project git tree** | Guest shell: `cd $activeProjectPath && git diff …` via `LinuxCommandBuilder` / stream | No FileProvider; text from process stdout |
| **In-app file viewer preview** | `BitmapFactory.decodeFile`, text read, markdown card | Local `File` IO only |
| **Marketplace package staging** | Cache under app dirs + chroot `/tmp` copy | Separate pipeline |
| **REQUEST_INSTALL_PACKAGES** | Not in scope of path width; APK open uses package installer intent | Policy MUwS is separate from B12; do not add new install-permission |
| **termux-app share provider** | Vendored module; not NativeCode FileProvider | Do not edit unless packaging forces merge |

**Rule for implementer:** only edit:

1. `file_paths.xml`  
2. `getFileUri` (+ small private helpers next to it)  
3. Checklist B12 status after verify  
4. Optional: one-line comment near FileProvider in manifest  

Do **not** refactor file viewer, dir tree, git diff, pickers, or attach for “cleanup.”

---

## 3. Research — path map (verified 2026-07-31)

### 3.1 Guest vs host (SSOT)

| Concept | Guest (Debian) | Host Android |
|---------|----------------|--------------|
| Projects (clone target) | `/home/flux/repos/<name>` | method-specific |
| Proot rootfs | `/` | `{filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs` (`ProjectPathResolver.PROOT_ROOTFS_REL`) |
| Proot repos | `/home/flux/repos/…` | `{filesDir}/…/rootfs/home/flux/repos/…` |
| Chroot rootfs | `/` | **`/data/local/tmp/chrootDebian13`** (`ChrootCommandBuilder.CHROOT_PATH`) |
| Chroot repos | `/home/flux/repos/…` | `/data/local/tmp/chrootDebian13/home/flux/repos/…` |
| Active project default | `/home/flux/repos/my-android-app` | resolved via `activeProjectHostDir()` → `ProjectPathResolver.resolve` |

**Correction vs informal naming:** chroot is **not** `/temp/chroot`; it is **`/data/local/tmp/chrootDebian13`**. Proot repos are **not** `filesDir/repos` flat; they sit **inside** the proot rootfs tree under app `filesDir`.

### 3.2 Current `file_paths.xml`

```xml
<paths>
    <external-path name="external_files" path="." />  <!-- entire shared external storage -->
    <files-path     name="internal_files" path="." />  <!-- all of getFilesDir() -->
    <cache-path     name="cache_files" path="." />     <!-- all of getCacheDir() -->
    <root-path      name="root" path="." />            <!-- entire device FS -->
</paths>
```

| Entry | Risk | Why it exists (inferred) |
|-------|------|--------------------------|
| `root-path` | **Critical** | So `getUriForFile` accepts **chroot** paths under `/data/local/tmp/…` (outside app sandbox) |
| `external-path .` | High | Lazy catch-all for SD/Downloads; not required if we stage |
| `files-path .` | Medium | Covers entire proot rootfs + icons + secrets under `filesDir` if wrong file shared |
| `cache-path .` | Medium | Same overbreadth for cache |

### 3.3 Call graph (outbound FileProvider only)

```
File viewer open (showFileViewer → renderFileViewerContent)
  ├─ image/gif card  → openExternalFile(file, "image/*")
  ├─ video card      → openExternalFile(file, "video/*")
  ├─ html card       → openHtmlFile(file)
  ├─ apk card        → installApkFile(file)
  └─ generic open    → openExternalFile(file)
        └─ getFileUri(file)
              ├─ FileProvider.getUriForFile(ctx, "$packageName.fileprovider", file)
              └─ catch → Uri.fromFile(file)   // broken on modern API; must not rely on
```

**Grep confirmed:** no other `FileProvider.getUriForFile` in `app/src` outside `MainActivity.getFileUri`.

### 3.4 Adjacent flows that must keep working (no code change)

#### A. Image picker (project icon)

| Step | Code | Storage |
|------|------|---------|
| Launch | `projectIconPickerLauncher.launch("image/*")` | — |
| Result | `GetContent` → `contentResolver.openInputStream` | copy → `File(filesDir, "icon_*.png")` |
| Fallback | `takePersistableUriPermission` + store URI string | `content://` in prefs field |
| Preview | `updateIconPreview` / `projectIconCache` | path or content URI |

**B12 impact if implemented correctly:** none (does not call `getFileUri`).

#### B. Image attach (terminal / workspace)

| Step | Code | Storage |
|------|------|---------|
| Pick | separate `GetContent` launchers ~503–507 | content URI |
| Stage | `ProjectPathResolver.stageAttachDir` = `filesDir/usr/tmp/nativecode_attach` | app-private |
| Proot | `stageFile.copyTo(ProjectPathResolver.resolve(...))` | under proot rootfs |
| Chroot | `RootShell.copyIntoChroot` or bind fallback `/mnt/host-tmp/nativecode_attach/...` | chroot or bind |

**B12 impact:** none. **Do not** repurpose `nativecode_attach` as FileProvider share dir (different lifecycle, guest-visible bind). Use a **separate** share dir (e.g. `filesDir/share/export/`).

#### C. Directory page

| Step | Code |
|------|------|
| Host root | `activeProjectHostDir()` → `ProjectPathResolver.resolve(activeProjectPath, method)` |
| List/search | `DirectoryScanner.list` / `search` on host `File` |
| Open file | navigates to file viewer with path string → local read |

**B12 impact:** none. Stage-copy only runs if user taps **external** open buttons in file viewer.

#### D. Git diff

| Step | Code |
|------|------|
| Tree | `refreshGitDiffTree` — guest `git status` / porcelain-style commands |
| Viewer | `loadDiffForFile` — `git diff HEAD -- "$name"` (and untracked `--no-index`) inside guest cwd `activeProjectPath` |
| Render | parse lines → UI cards |

**B12 impact:** none. No host URI sharing.

### 3.5 Why stage-copy is required (not “files-path only”)

| Source file location | Under app `filesDir`? | After drop `root-path`, raw `getUriForFile`? |
|----------------------|----------------------|-----------------------------------------------|
| Proot repo file | Yes | Would work with broad `files-path .` — still overbroad for secrets |
| Chroot repo file | **No** (`/data/local/tmp/chrootDebian13/...`) | **Fails** without `root-path` or stage-copy |
| Attach stage / icons | Yes | Not shared via FileProvider today |

Therefore: **stage into app-private share dir** is the only approach that:

1. Removes `root-path`  
2. Keeps chroot open-with  
3. Avoids exposing entire rootfs tree via FileProvider metadata  

---

## 4. Design

### 4.1 Target `file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Outbound share only — never root-path, never full files/external -->
    <files-path name="export_share" path="share/export/" />
    <!-- optional: large temps that should not count against files quota long-term -->
    <cache-path name="export_share_cache" path="share/export/" />
</paths>
```

**Removed:** `root-path`, `external-path`, broad `files-path .`, broad `cache-path .`.

### 4.2 Share staging API (minimal, local to MainActivity or tiny helper)

Preferred: private methods on `MainActivity` to avoid new module churn; optional extract to `FileShareHelper` only if >40 lines.

```text
SHARE_REL = "share/export"

fun shareStagingDir(): File =
    File(filesDir, SHARE_REL).also { it.mkdirs() }

fun stageForShare(source: File): File {
    require(source.isFile) { "not a file" }
    val dest = File(shareStagingDir(), uniqueName(source.name))
    // Prefer copy; if same filesystem + desired later: Files.createLink
    source.copyTo(dest, overwrite = true)
    return dest
}

fun getFileUri(file: File): Uri {
    val staged = if (isUnderShareDir(file)) file else stageForShare(file)
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", staged)
}
```

| Detail | Choice | Why |
|--------|--------|-----|
| Unique names | `timestamp_ + sanitized original name` | Avoid collisions when opening two `index.html` from different projects |
| Overwrite | unique names → no overwrite needed | Safer concurrent opens |
| Cleanup | best-effort delete files older than N hours on next stage; or wipe share dir on app start (optional) | Prevent unbounded growth |
| Max size | soft cap toast if e.g. > 200 MB | Avoid OOM/disk fill on video/APK |
| Fallback | **no** `Uri.fromFile` | Dead on API 24+; show Toast on failure instead |
| MIME | unchanged in callers | Intent still sets type |

### 4.3 Intent flags (unchanged)

Keep:

- `FLAG_GRANT_READ_URI_PERMISSION`  
- `FLAG_ACTIVITY_NEW_TASK` where already present  
- `createChooser` for open/HTML  

Do **not** add write grants.

### 4.4 APK install path

| Option | Behavior |
|--------|----------|
| **A (default for this plan)** | Stage APK into share + existing `ACTION_VIEW` package-archive (behavior preserved) |
| B (later product) | Hide INSTALL APK button if Play MUwS risk | Out of B12; separate decision |

B12 only ensures URI path is narrow; does not change install policy story.

### 4.5 Interaction with secrets

Staging copies whatever file the user opened. That is **user-initiated** and same as today. Narrow FileProvider means a **bug** cannot mint URIs for arbitrary `/data/...` secrets outside the staged set without first copying them through app code that only runs on explicit open buttons.

---

## 5. Implementation steps (when approved)

| # | Step | Files | Notes |
|---|------|-------|-------|
| 1 | Replace `file_paths.xml` with share-only paths | `app/src/main/res/xml/file_paths.xml` | Delete root/external/broad entries |
| 2 | Implement `stageForShare` + rewrite `getFileUri` | `MainActivity.kt` ~6241 | Only entry point for outbound URIs |
| 3 | Remove `Uri.fromFile` fallback; Toast on error | same | Fail closed |
| 4 | Optional: purge old share files (age > 24h) at start of stage | same | Disk hygiene |
| 5 | Manual test matrix §6 | device proot + chroot | Must pass before checklist update |
| 6 | Mark B12 FOLLOWED in policy checklist | `docs/policy/...Checklist.md` | Evidence: share-only paths + stage |

**Explicitly do not:**

- Change `DirectoryScanner`, git diff commands, icon picker, image attach  
- Change `ProjectPathResolver` roots  
- Add `REQUEST_INSTALL_PACKAGES`  
- Touch termux FileProvider / share authorities  

---

## 6. Test matrix (device)

### 6.1 Proot

| # | Action | Expected |
|---|--------|----------|
| P1 | Open project dir tree, browse files | List works (unchanged) |
| P2 | Open image in file viewer (in-app preview) | Preview works without external app |
| P3 | Tap open external / share image | Chooser opens; external app shows image |
| P4 | Open HTML externally | Browser loads content |
| P5 | Open video externally (if present) | Player works |
| P6 | INSTALL APK from viewer (if APK present) | Installer activity starts |
| P7 | Git Diff page + file diff | Diff text renders; no crash |
| P8 | Project icon picker | Still saves icon under filesDir / content URI |
| P9 | Terminal image attach into project | Guest path set; file appears under project |
| P10 | Confirm `share/export` contains staged files after open | Stage dir non-empty briefly |

### 6.2 Chroot (root available)

| # | Action | Expected |
|---|--------|----------|
| C1 | Dir tree under chroot project | Lists `/data/local/tmp/chrootDebian13/home/flux/repos/...` host tree |
| C2 | Open image external from chroot project file | Works **after** stage (regression if stage broken) |
| C3 | HTML external from chroot path | Works |
| C4 | Git diff on chroot project | Unchanged (shell git) |
| C5 | Image attach into chroot project | Unchanged (RootShell copy) |
| C6 | **Without** stage, raw FileProvider on chroot path must **not** be required | Proves root-path removal is OK |

### 6.3 Negative / security smoke

| # | Check | Expected |
|---|-------|----------|
| N1 | `file_paths.xml` has no `root-path` | Pass |
| N2 | `file_paths.xml` has no `external-path` with `path="."` | Pass |
| N3 | Force `getFileUri` fail (e.g. missing file) | Toast, no crash |
| N4 | Large file open | Cap toast or success without OOM |

---

## 7. Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Chroot open-with regresses if someone only deletes `root-path` without staging | High if incomplete | Stage is **mandatory** in same change set |
| Disk fill from staged APKs/videos | Medium | Unique names + age purge + size toast |
| Filename collisions | Low | Timestamp prefix |
| SELinux/read deny reading chroot path as app UID | Medium on some devices | App already reads those files for in-app viewer via same UID path; if viewer works, stage copy works. If not, fail Toast (same class as today) |
| Concurrent open same name | Low | Unique staged names |
| Scope creep into attach/diff | Process | Non-goals table; review PR for file list |

---

## 8. Rollback

Single-commit revert of:

- `file_paths.xml`  
- `getFileUri` / stage helpers  

Restores previous broad paths. No DB migration.

---

## 9. Policy checklist update (after ship + verify)

| Field | New value |
|-------|-----------|
| B12 Status | **FOLLOWED** |
| Evidence | Share-only `file_paths.xml`; `getFileUri` stages under `filesDir/share/export/`; no `root-path` |
| How to keep fixed | Never re-add `root-path`; all outbound share via stage dir |

---

## 10. Effort estimate

| Work | Size |
|------|------|
| XML + `getFileUri` rewrite | S (~30–50 LOC) |
| Optional purge helper | XS |
| Device matrix §6 | M (manual, both methods) |
| Total eng | ~0.5–1 day including chroot device |

---

## 11. Decision log

| Decision | Choice | Reason |
|----------|--------|--------|
| Stage-copy vs bind FileProvider to chroot root | **Stage-copy** | Removes need for `root-path`; same UX |
| Reuse `nativecode_attach` | **No** | Guest-bind lifecycle; different purpose |
| Keep INSTALL APK | **Yes for B12** | Behavior parity; MUwS separate |
| Touch git diff / dir / picker | **No** | Zero FileProvider dependency |
| Implement now | **Yes** — implemented after user OK 2026-07-31 | Stage + narrow XML |

---

## 12. Approval gate

Implement only after explicit user OK. On implement:

1. Apply §5 steps 1–4 only  
2. Run §6 matrix  
3. Update checklist B12  
4. Do not expand scope  

**Stop here (plan authored; no code changes).**
