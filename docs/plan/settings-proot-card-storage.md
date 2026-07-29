# Settings: Proot Settings (hub card + size-only detail page)

**Date:** 2026-07-28  
**Status:** implemented  
**Scope:** Settings Hub **nav row above Chroot Settings** → dedicated **Proot Settings** page that shows **Debian proot rootfs size only** (no install / uninstall / root probe).  
**Out of scope:** proot install/uninstall CTAs, root access UI, wipe/reset, cache cleanup, proot↔chroot switch (already on Isolation card), Play-policy copy polish.

**Related:**
- [`docs/plan/settings-chroot-card-storage-uninstall.md`](./settings-chroot-card-storage-uninstall.md) — chroot pattern (hub row + detail page); **this plan is size-only subset**
- [`docs/plan/proot-debian-rootfs-local-install.md`](./proot-debian-rootfs-local-install.md) — proot lives *inside* app files
- [`docs/project/ui_design.md`](../project/ui_design.md) — Cyber-Brutalist / `NC` tokens
- Path SSOT (today): `ProjectPathResolver` →  
  `{filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs`

**Primary files (planned touch):**

| Path | Role |
|------|------|
| `MainActivity.kt` | Hub nav row (above chroot); `ID_PROOT_SETTINGS` page; size panel + REFRESH; prefs cache |
| `ProjectPathResolver.kt` | Optional helpers: `prootRootfsDir(ctx)`, `isProotInstalled(ctx)` |
| `ProotCommandBuilder.kt` or small const | Optional `PROOT_ROOTFS_REL` / path helper (keep SSOT) |

---

## 1. Problem

### 1.1 Proot storage is invisible / confusing

| Mode | Rootfs location | Survives app uninstall? | Needs root to measure? |
|------|-----------------|-------------------------|------------------------|
| **proot** | App sandbox under `filesDir` | **No** (deleted with app data) | **No** |
| **chroot** | `/data/local/tmp/chrootDebian13` | Yes | Yes (`du` as root) |

Users already have **Chroot Settings** for external rootfs size. There is **no** Settings surface for proot Debian footprint. Android “App storage” lumps bootstrap + proot + cache together; not a clear “Debian proot size.”

### 1.2 Settings Hub layout (current)

`buildSettingsHubLayout()` order today:

1. Header  
2. Graphical Desktop  
3. Terminal Settings  
4. Linux Isolation Mode (proot/chroot toggle)  
5. **Chroot Settings** nav row → `ID_CHROOT_SETTINGS`  
6. System Scripts  
7. Re-run Onboarding  

**Target order:** insert **Proot Settings** nav row **immediately above** Chroot Settings:

5. **Proot Settings** → `ID_PROOT_SETTINGS` *(new)*  
6. **Chroot Settings**  
7. System Scripts  
8. Re-run Onboarding  

### 1.3 Product constraint (this plan)

Detail page is **size-only** — intentionally thinner than chroot:

| Chroot detail | Proot detail (this plan) |
|---------------|--------------------------|
| STATUS badge | Optional minimal status only if free; **prefer omit** |
| ROOT ACCESS | **Omit** (not required) |
| LINUX STORAGE + REFRESH | **Yes** (main content) |
| HOST PATH | **Yes** (read-only path string; needed to trust the number) |
| INSTALL / UNINSTALL | **Omit** |

User: *“similar to chroot only show there size of proot nothing else.”*  
Interpret: page chrome matches chroot (header, back, brutalist card), body = storage metric (+ path so size is auditable). No management CTAs.

---

## 2. Correct path (identify first)

### 2.1 Canonical Debian proot rootfs (measure this)

Already used app-wide:

```text
{Context.filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs
```

Examples:

| Context | Absolute path pattern |
|---------|----------------------|
| App private | `/data/user/0/com.ivarna.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs` |
| Code | `File(ctx.filesDir, "usr/var/lib/proot-distro/containers/debian/rootfs")` |
| Guest home | `.../rootfs/home/flux` (`ProjectPathResolver.guestHomeDir`) |

Installed when `flux_install` / `proot-distro install` creates container `debian` (`DISTRO_ROOTFS="$PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"`, `DISTRO=debian`).

### 2.2 Related dirs (do **not** mix into primary number)

| Path under `filesDir` | Role | Count in primary size? |
|----------------------|------|------------------------|
| `usr/var/lib/proot-distro/containers/debian/rootfs` | **Actual Debian filesystem** | **Yes — primary** |
| `usr/var/lib/proot-distro/containers/debian/` (non-rootfs) | Container metadata | No (tiny) |
| `usr/var/lib/proot-distro/cache/` | Rootfs archive cache | No — separate cache |
| `usr/` bootstrap (bin, lib, proot-distro package) | Host Termux-style prefix | No — not “Debian proot” |
| `home/` host scripts | App home | No |

**Primary metric label:** `LINUX STORAGE` / hint `Debian proot rootfs` = **`du` of rootfs only**.

Optional later (out of scope): second line “Cache” for `proot-distro/cache`.

### 2.3 Marker / presence

| Check | Meaning |
|-------|---------|
| `rootfs/` exists and non-empty (e.g. `bin` or `etc` present) | Proot rootfs present |
| Optional: `rootfs/.flux_configured` if guest chain wrote it | “Configured” (nice-to-have; not required for size) |
| Missing dir | Show `—` / `0` / “Not installed” hint under size |

**No root required.** App can `File.exists()` + walk/`du` on own `filesDir`.

### 2.4 No bind-mount inflation (unlike chroot)

Host path for proot is plain files. Guest runtime binds (`/sdcard`, tmp) are **not** stored under `containers/debian/rootfs` as the chroot host path does with live mounts.

→ Use plain **`du -sb <rootfs>`** (or Java walk sum). **Do not** apply chroot exclude list (`sdcard`/`mnt`/…).

If a future bind were ever persisted under rootfs (unlikely), re-evaluate; not needed now.

---

## 3. Goals

1. Hub: **Proot Settings** nav row **above** Chroot Settings (same chrome as chroot/scripts rows).  
2. Detail page: header **PROOT SETTINGS**, back stack, one content card focused on **size + REFRESH + host path**.  
3. Measure **complete rootfs tree** (all of `.../debian/rootfs`, recursive).  
4. No root / no su. Measure on background thread; never block UI.  
5. Prefs cache last bytes + timestamp so reopen is instant; REFRESH remeasures.  
6. UI matches NC / sharp 0px / monospace metrics (same as chroot size panel).  
7. Proot path unchanged; chroot page unchanged.

---

## 4. Non-goals

- Install / uninstall / wipe proot from this page.  
- Root access row.  
- Mount status, package lists, disk free on device.  
- Changing Isolation Mode card.  
- Measuring whole `filesDir` or bootstrap `usr/` as “proot size.”  
- Auto-delete cache.

---

## 5. UX / UI

### 5.1 Hub row

| Field | Value |
|-------|--------|
| Title | `PROOT SETTINGS` |
| Subtitle | `Debian rootfs size (app storage)` |
| Icon | `ic_storage` (or same as chroot) |
| Position | **Above** `buildChrootSettingsSectionButton()` |
| Click | push `ID_PROOT_SETTINGS` → `navigateToPage` |

### 5.2 Detail page structure

```text
← PROOT SETTINGS
// DEBIAN PROOT — APP STORAGE

┌─ Storage & size ─────────────────────────┐
│  LINUX STORAGE                    REFRESH │
│  [progress while scanning]                │
│  6.2  GB                                  │
│  Debian proot rootfs · measured in-app    │
│                                           │
│  HOST PATH                                │
│  …/files/usr/var/lib/proot-distro/        │
│       containers/debian/rootfs            │
└───────────────────────────────────────────┘
```

**Omit:** STATUS / ROOT ACCESS / INSTALL / UNINSTALL / warn strip about “survives uninstall” (proot does **not** survive app uninstall — do not copy chroot warning).

Optional one-line info (only if needed for clarity):  
`// Removed when app data is cleared` — not a full warn strip unless design wants it.

### 5.3 Empty / missing rootfs

| State | Size UI |
|-------|---------|
| Dir missing | `—` + hint `Proot rootfs not found` |
| Dir empty / partial | Show measured bytes (may be tiny) + same path |
| Measure fail | `—` + `Size probe failed` |

### 5.4 Visual tokens

Same as chroot size panel: `NC.SURFACE_*`, `PRIMARY` number, monospace, 0px radius, REFRESH outline control.

---

## 6. Implementation plan

### P1 — Path helpers (SSOT)

**File:** `ProjectPathResolver.kt` (preferred)

```kotlin
fun prootRootfsDir(ctx: Context): File =
    File(ctx.filesDir, "usr/var/lib/proot-distro/containers/debian/rootfs")

fun isProotRootfsPresent(ctx: Context): Boolean =
    prootRootfsDir(ctx).let { it.isDirectory && (File(it, "etc").exists() || File(it, "bin").exists()) }
```

Reuse in resolve/toDebianPath/guestHomeDir later if desired (refactor optional; not required for this feature).

Constants: keep relative path string in **one** place only.

### P2 — Page id + navigation

**File:** `MainActivity.kt`

1. `private val ID_PROOT_SETTINGS = 15` (next free after chroot `14`).  
2. `prootSettingsScrollView` + build in `onCreate` layout stack (mirror chroot).  
3. `navigateToPage` / `hideAll` / back stack: show/hide proot page like chroot.  
4. Hub: `buildProotSettingsSectionButton()` **before** chroot row + spacer.

### P3 — Detail page UI (size only)

`buildProotSettingsPage()` / `buildProotSettingsCard()`:

- Header: back + `PROOT SETTINGS`  
- Card section title: `Storage`  
- Size panel: title `LINUX STORAGE`, REFRESH, loading strip, large value + unit, hint  
- Path row: `HOST PATH` + full absolute path (scrollable / multi-line if needed)  
- **No** other rows or buttons  

Wire views: `prootSizeValueTv`, `prootSizeUnitTv`, `prootSizeHintTv`, `prootRefreshBtn`, `prootLoadingRow`, `prootPathTv`.

### P4 — Measure complete rootfs

**Background** (`executor`):

```text
path = ProjectPathResolver.prootRootfsDir(this).absolutePath
if !dir → bytes = null
else:
  # App can read own files — no RootShell
  Runtime / ProcessBuilder:  du -sb <path> | cut -f1
  # Fallback if du missing: recursive File walk sum of file lengths
```

| Step | Detail |
|------|--------|
| Tool | Prefer `du -sb` (toybox/busybox on device often available in PATH via app `usr/bin` or system) |
| Fallback | Java `walkTopDown().filter { isFile }.sumOf { length() }` — slow but correct |
| Thread | Always BG; post result to main |
| Complete dir | Entire `rootfs` tree — **no** exclude of guest `home`/`var`/`usr` |

**Do not** use RootShell for proot size (unnecessary; can fail without root grant).

Reuse `formatStorageBytes(bytes)` from chroot card.

### P5 — Prefs cache

| Pref key | Type | Meaning |
|----------|------|---------|
| `proot_size_bytes` | Long | Last measured size (−1 = unknown) |
| `proot_dir_present` | Boolean | Rootfs dir present |
| `proot_last_check_ms` | Long | Last measure time |

On page open: show cached value immediately if present, then soft-refresh (or only on first open + REFRESH — match chroot pattern of measure on enter).

Clear prefs if user clears app data (automatic).

### P6 — Logging / edge

- Log measure path + bytes (debug).  
- Guard concurrent measure (`prootMeasuring` flag like chroot).  
- Very large trees: keep spinner until `du` finishes; no ANR.

---

## 7. File touch list

| File | Change |
|------|--------|
| `MainActivity.kt` | Hub row above chroot; `ID_PROOT_SETTINGS`; page + card; measure; prefs; nav visibility |
| `ProjectPathResolver.kt` | `prootRootfsDir` / `isProotRootfsPresent` (small) |
| *(optional)* `ProotCommandBuilder.kt` | Export relative path constant if cleaner |

**No** script assets, **no** RootShell changes, **no** Onboarding changes.

---

## 8. Order of work

1. P1 path helpers.  
2. P2 page id + hub row position.  
3. P3 UI shell (static `—` + path string).  
4. P4 measure + format + loading.  
5. P5 prefs cache.  
6. Device test (acceptance).  
7. Mark this plan **implemented** when green.

---

## 9. Acceptance criteria

- [x] Settings Hub shows **PROOT SETTINGS** row **above** CHROOT SETTINGS.  
- [x] Tap opens detail page with back; title **PROOT SETTINGS**.  
- [x] Path shown equals  
  `{filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs`.  
- [x] Size is recursive total of that rootfs only (not bootstrap, not chroot, not whole app).  
- [x] REFRESH remeasures; loading state visible during scan.  
- [x] No install / uninstall / root badge on this page.  
- [x] Missing proot rootfs → clear empty state, no crash.  
- [x] Chroot settings page behavior unchanged.  
- [x] Isolation mode card unchanged.

---

## 10. Test plan

1. Device with completed proot onboarding (Debian guest present).  
2. Settings Hub → confirm row order: … Isolation → **Proot** → Chroot → Scripts …  
3. Open Proot Settings → path correct → size in GB range of real rootfs (typically several GB, not full device /sdcard).  
4. Spot-check: `adb shell run-as … du -sb …/containers/debian/rootfs` ≈ UI.  
5. REFRESH twice; value stable.  
6. Clear app data / no proot → empty state.  
7. Open Chroot Settings still works.

---

## 11. Risk / notes

| Risk | Mitigation |
|------|------------|
| Slow `du` on large rootfs | BG thread + spinner; cache last value |
| `du` binary missing | Java walk fallback |
| User confuses proot size with full app storage | Path + hint “Debian proot rootfs”; subtitle on hub |
| Wrong path (cache vs rootfs) | SSOT in `ProjectPathResolver`; document §2 |
| Copy-paste chroot `du` excludes | **Do not** — proot host path has no live `/sdcard` bind inflation |
| Future multi-distro | Hardcode `debian` for now (app only ships debian) |

---

## 12. Comparison with chroot settings

| | Proot (this plan) | Chroot (existing) |
|--|-------------------|-------------------|
| Hub position | Above chroot | Below proot (new) |
| Path | App `filesDir` … `/debian/rootfs` | `/data/local/tmp/chrootDebian13` |
| Measure | App process `du` / walk | RootShell `du` (exclude binds) |
| Size meaning | Full guest rootfs on disk | Full host rootfs minus binds |
| Install/Uninstall | No | Yes |
| Root row | No | Yes |

---

## Stop line

**Implemented** — hub row + size-only page + `du`/walk measure + prefs cache.
