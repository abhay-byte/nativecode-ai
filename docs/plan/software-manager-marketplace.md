# Software Manager + Marketplace (proot & chroot)

**Date:** 2026-07-30  
**Status:** implemented 2026-07-30 (v1: SM + MP pages, public catalog repo, demo packages)  

**Scope:** Two Settings Hub pages — **Software Manager** (installed Debian apt inventory, category-wise) and **Marketplace** (curated install catalog from a public GitHub repo). Full support for **proot** and **chroot**. Local tracking of marketplace installs (component vs X11-launchable app). Demo catalog + decoupled install scripts.  
**Out of scope (v1):** Play Store packaging of guest packages; full Debian GUI package browser (10k+ pkgs); building packages from source on-device; merging into onboarding; automatic system-wide upgrades of all apt packages; iOS-style “App Store” payments.

**Design SSOT:** `docs/project/ui_design.md` (Obsidian Terminal / cyber-brutalist)  
**Token SSOT:** `app/.../DesignTokens.kt` (`NC.*`)  
**Compile policy:** `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` only; never assemble/install APK unless user asks.

---

## 0. Goals (user intent → product)

| Goal | Meaning |
|------|---------|
| **Software Manager** | Show **everything currently installed via apt** in the active Debian guest, **grouped by category/section**. Include size, version. Works for **proot and chroot**. |
| **Marketplace** | Curated store of **extra** packages that are awkward as plain `apt install` alone (scripts, multi-step, prebuilts: glmark2, blender, box64, FEX, …). Categories, install progress, post-install **size**. |
| **Two pages** | Separate surfaces; both reachable from **Settings Hub** buttons. |
| **Track marketplace** | Know what came from Marketplace; mark each as **component** (library/runtime, not launched from NC UI) vs **app** (can launch on X11). |
| **Decoupled scripts** | One package = one script family. **No** mega-script that installs many apps. Dependencies = other marketplace packages (or thin apt atoms), resolved by the runner. |
| **Catalog lives in new public repo** | `gh repo create` public; app fetches catalog + scripts over HTTPS (raw.githubusercontent / releases). |

---

## 1. Problem summary

### 1.1 Today

| Need | Current state |
|------|----------------|
| See installed guest packages | No in-app UI. User must open Debian shell + `dpkg -l` / `apt list --installed`. |
| Install hard packages (box64, FEX, Blender) | Ad-hoc shell or giant onboarding scripts (`setup_cli_tools.sh`, GPU setup, etc.). Not productized. |
| Know what NativeCode “owns” vs base Debian | No registry of marketplace installs. |
| Launch X11 app after install | Manual `start_gui` + shell; no “Open glmark2” from UI. |
| proot vs chroot | Install paths differ (`ProotCommandBuilder` / `ChrootCommandBuilder`); no unified package UX. |

### 1.2 Settings Hub (current order)

`buildSettingsHubLayout()` today:

1. Header  
2. Graphical Desktop  
3. Terminal Settings  
4. Linux Isolation Mode  
5. Proot Settings  
6. Chroot Settings  
7. X11 Settings  
8. System Scripts (Repairs)  
9. Re-run Onboarding  

**Target:** insert **Software Manager** + **Marketplace** after environment/storage cluster (after Chroot Settings, before or after X11):

| Order | Row |
|------:|-----|
| … | Proot / Chroot Settings |
| **new** | **Software Manager** → `ID_SOFTWARE_MANAGER` |
| **new** | **Marketplace** → `ID_MARKETPLACE` |
| … | X11 Settings / Scripts / Onboarding |

### 1.3 Isolation SSOT

| Mode | Guest rootfs | Install as | Runner |
|------|--------------|------------|--------|
| **proot** | `{filesDir}/usr/var/lib/proot-distro/containers/debian/rootfs` | root inside proot (`proot-distro login debian --user root -- …`) | `ProotCommandBuilder` / host bash + proot-distro |
| **chroot** | `/data/local/tmp/chrootDebian13` | root via `su` + busybox chroot | `ChrootCommandBuilder` (`user=root`) |

**Active mode** = `LinuxCommandBuilder.currentMethod` (`prefs "linux_method"`). Both pages always operate on **current** method; show badge **PROOT** or **CHROOT** in header. Switching method does not auto-migrate installs (each rootfs independent).

---

## 2. Architecture overview

```text
┌─────────────────────────────────────────────────────────────┐
│ NativeCode app (com.ivarna.nativecode)                      │
│  Settings Hub                                                │
│    ├─ Software Manager page  ──► AptInventoryService         │
│    │                              (dpkg-query in guest)      │
│    └─ Marketplace page       ──► MarketplaceClient           │
│                                   (fetch catalog + scripts)  │
│                              ──► PackageInstallRunner        │
│                                   (dep resolve + stream log) │
│                              ──► InstallRegistry (local JSON)│
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ GitHub public repo: nativecode-marketplace (new)             │
│  catalog.json + packages/<id>/{package.json,install,uninstall}│
└─────────────────────────────────────────────────────────────┘
                            │ scripts executed inside guest
                            ▼
              proot Debian  OR  chroot Debian13
```

### 2.1 Layers

| Layer | Responsibility |
|-------|----------------|
| **UI** | Two pages + optional detail; NC tokens; install progress stream |
| **AptInventoryService** | Query guest apt/dpkg; map Section → categories; sizes |
| **MarketplaceClient** | Fetch/cache catalog; download scripts; verify checksum optional |
| **PackageInstallRunner** | Topo-sort deps; run install/uninstall in guest as root; update registry |
| **InstallRegistry** | Persist marketplace installs (id, kind, size, env, version, timestamps) |
| **Catalog repo** | Human-editable packages; CI optional later |

### 2.2 Component vs app

| `kind` | Meaning | Software Manager | Marketplace | Home / GUI |
|--------|---------|------------------|-------------|------------|
| **`component`** | Runtime / lib / toolchain (box64, FEX, mesa bits). Not primary launch target in NC UI. | Listed under apt categories if apt-backed; badge “MP” | Install / uninstall | No Launch button |
| **`app`** | User-facing program, preferably X11 (`glmark2`, `blender`). | Same + Launch if registry says app + installed | Install / uninstall + **Launch** | Optional future “Installed Apps” strip (v2) |

Registry always stores `kind` from catalog at install time (snapshot). Catalog may change later; installed kind does not silently flip without reinstall.

---

## 3. External catalog repo (new public GitHub repo)

### 3.1 Create (impl agent / maintainer)

```bash
# From host with gh auth (owner today: abhay-byte)
gh repo create abhay-byte/nativecode-marketplace \
  --public \
  --description "NativeCode Marketplace catalog + install scripts (proot/chroot Debian)" \
  --clone
```

**Canonical remote:** `https://github.com/abhay-byte/nativecode-marketplace`  
**Raw base (default branch `main`):**  
`https://raw.githubusercontent.com/abhay-byte/nativecode-marketplace/main/`

App constant (overrideable via prefs for testing):

```text
MARKETPLACE_REPO_OWNER=abhay-byte
MARKETPLACE_REPO_NAME=nativecode-marketplace
MARKETPLACE_REF=main
```

### 3.2 Repo layout

```text
nativecode-marketplace/
├── README.md
├── LICENSE
├── catalog.json                 # SSOT index (categories + package stubs)
├── schema/
│   └── catalog.schema.json      # optional JSON Schema for CI
├── lib/
│   └── nc_mp_common.sh          # shared helpers sourced by package scripts
└── packages/
    ├── mesa-utils/              # component (demo)
    │   ├── package.json
    │   ├── install.sh
    │   └── uninstall.sh
    ├── box64/                   # component
    │   ├── package.json
    │   ├── install.sh
    │   └── uninstall.sh
    ├── fex-emu/                 # component
    │   ├── package.json
    │   ├── install.sh
    │   └── uninstall.sh
    ├── glmark2/                 # app (X11)
    │   ├── package.json
    │   ├── install.sh
    │   └── uninstall.sh
    └── blender/                 # app (X11; heavy)
        ├── package.json
        ├── install.sh
        └── uninstall.sh
```

### 3.3 `catalog.json` schema (v1)

```json
{
  "schema_version": 1,
  "generated_at": "2026-07-30T00:00:00Z",
  "min_app_version": 1,
  "categories": [
    { "id": "compat",   "title": "Compatibility", "order": 10, "description": "x86_64 translation / runtimes" },
    { "id": "graphics", "title": "Graphics",      "order": 20, "description": "GPU, benchmarks, display tools" },
    { "id": "apps",     "title": "Apps",          "order": 30, "description": "Launchable desktop applications" },
    { "id": "devtools", "title": "Dev tools",     "order": 40, "description": "Compilers and build helpers" }
  ],
  "packages": [
    {
      "id": "glmark2",
      "name": "glmark2",
      "kind": "app",
      "category": "graphics",
      "summary": "OpenGL ES 2.0 benchmark",
      "description": "Runs under X11 / EGL. Good GPU smoke test after Turnip/virgl setup.",
      "version": "1.0.0",
      "arch": ["aarch64", "arm"],
      "env": ["proot", "chroot"],
      "deps": ["mesa-utils"],
      "apt_provides": ["glmark2"],
      "size_hint_mb": 8,
      "icon": "packages/glmark2/icon.png",
      "homepage": "https://github.com/glmark2/glmark2",
      "script_path": "packages/glmark2",
      "launch": {
        "type": "x11",
        "command": "glmark2",
        "workdir": "/home/flux",
        "needs_display": true
      },
      "sha256_install": null
    }
  ]
}
```

#### Field rules

| Field | Required | Notes |
|-------|----------|-------|
| `id` | yes | Stable slug; registry key |
| `kind` | yes | `app` \| `component` |
| `category` | yes | Must exist in `categories[]` |
| `deps` | yes | List of **marketplace package ids** only (not apt names) |
| `apt_provides` | no | Debian package names this install is expected to leave on disk (for SM matching) |
| `env` | yes | Subset of `proot`/`chroot` supported |
| `script_path` | yes | Directory under repo containing install/uninstall |
| `launch` | apps only | How NC launches after install |
| `size_hint_mb` | no | Catalog estimate; real size measured post-install |

### 3.4 Per-package `package.json`

Mirrors catalog entry for the package (source of truth can be either; **catalog.json is what the app downloads first**). Package folder may hold extra notes.

### 3.5 Script contract (critical: decoupled)

Every package script:

1. **Installs only itself** (plus Debian apt packages that are **direct** package content — not sibling marketplace products).
2. **Does not** `apt install blender box64 fex` in one script.
3. **Does not** call other package `install.sh` files — the **app runner** installs `deps` first.
4. Is **idempotent** (re-run = no-op or repair).
5. Sources `lib/nc_mp_common.sh` when available (copied into guest with the script).
6. Runs as **root inside guest**.
7. Emits machine-readable progress lines the app can parse:

```text
NC_MP_STATUS=start id=glmark2
NC_MP_STATUS=apt_update
NC_MP_STATUS=apt_install pkgs=glmark2
NC_MP_STATUS=done id=glmark2 exit=0
NC_MP_SIZE_BYTES=8388608
```

#### Shared helpers (`lib/nc_mp_common.sh`)

```bash
# expected API
nc_mp_require_root
nc_mp_pkg_ok <deb>          # dpkg -s
nc_mp_apt_install <debs…>   # only listed debs
nc_mp_apt_remove <debs…>
nc_mp_download <url> <path> # curl/wget with retries
nc_mp_mark_bin <path>       # chmod +x; optional update-alternatives
nc_mp_emit_size <paths…>    # du -sb → NC_MP_SIZE_BYTES
```

#### install.sh skeleton

```bash
#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=../../lib/nc_mp_common.sh
. "$(dirname "$0")/../../lib/nc_mp_common.sh" 2>/dev/null || true
nc_mp_require_root
echo "NC_MP_STATUS=start id=glmark2"
if nc_mp_pkg_ok glmark2; then
  echo "NC_MP_STATUS=skip already_installed"
else
  nc_mp_apt_install glmark2
fi
nc_mp_emit_size /usr/bin/glmark2
echo "NC_MP_STATUS=done id=glmark2 exit=0"
```

#### uninstall.sh

Mirror of install: remove **only** that package’s artifacts; do **not** remove deps (other packages may need them). Optional later: “remove unused deps” GC.

### 3.6 Dependency model

```text
blender ──deps──► box64          (example if using x86_64 binary)
   │
   └──deps──► mesa-utils         (or thinner graphics components)

glmark2 ──deps──► mesa-utils

fex-emu ──deps──► (none)

box64   ──deps──► (none)

mesa-utils ──deps──► (none)   # pure apt atom as marketplace component
```

**Runner algorithm:**

1. Load catalog package `P`.  
2. Build directed graph from `deps` (recursive).  
3. Cycle → fail with clear error.  
4. Topological order; skip already installed (registry or `detect` command).  
5. For each package: download scripts → copy into guest `/tmp/nc-mp/<id>/` → run `install.sh`.  
6. On failure: stop; leave partial installs marked `failed` (retryable).  
7. On success: write registry + measured size.

**No single script may install “the whole stack.”**

### 3.7 proot vs chroot script variants

**Prefer one guest-side script** when logic is pure Debian (`apt`, paths under `/usr`).

Use **env-specific** only when required:

| Case | Approach |
|------|----------|
| Pure apt | Single `install.sh` |
| Needs host download into rootfs | Script still guest-side; or host pre-stage file then guest unpack |
| chroot needs bind-mount quirk | Handle in **runner** (mounts already done by `ChrootCommandBuilder`), not package script |
| proot network slower | Same script; runner shows longer timeout |

Optional override in `package.json`:

```json
"scripts": {
  "install": "install.sh",
  "install_proot": "install_proot.sh",
  "install_chroot": "install_chroot.sh"
}
```

Default = `install.sh` for both.

---

## 4. Demo catalog (v1 ship list)

Ship **at least** these packages in the public repo on day one.

### 4.1 Components

| id | category | What script does | Why decoupled |
|----|----------|------------------|---------------|
| `mesa-utils` | graphics | `apt install mesa-utils` | Shared dep for GL tools |
| `box64` | compat | Install box64 (apt if available, else official aarch64 package/tarball) | Needed by some x86 apps |
| `fex-emu` | compat | Install FEX (documented aarch64 method) | Alternate translation layer |

### 4.2 Apps

| id | category | deps | Launch | Notes |
|----|----------|------|--------|-------|
| `glmark2` | graphics | `[mesa-utils]` | `glmark2` on X11 | Light GPU benchmark |
| `blender` | apps | `[box64]` **or** native arm package if Debian has it | `blender` on X11 | Heavy; script must stay blender-only; box64 as separate dep if needed |

Exact install method (apt vs GitHub release) is package-script detail; plan requires **working** install on aarch64 Debian 13 guest used by NativeCode.

### 4.3 Optional stretch (same pattern, not required for v1)

- `geany` / `mousepad` (simple X11 editor apps)  
- `virgl-tools` component  
- `ffmpeg` component  

---

## 5. Software Manager page

### 5.1 Purpose

Inventory of **apt/dpkg-installed packages** in the **current** guest (proot or chroot), **category-wise**.

### 5.2 Data source (guest commands)

Primary (fast, structured):

```bash
dpkg-query -W -f='${db:Status-Abbrev}\t${Package}\t${Version}\t${Installed-Size}\t${Section}\t${binary:Package}\n'
```

Filter to installed: status prefix `ii` (and maybe `hi`).

**Installed-Size** is KiB in dpkg → UI converts to human (`12.4 MB`).

**Section** examples: `utils`, `devel`, `x11`, `libs`, `admin`, `net`, `science`, … → UI **categories**.

Fallback if dpkg-query fails: `apt list --installed 2>/dev/null`.

### 5.3 Category grouping (UI)

| UI category | Map from dpkg `Section` (examples) |
|-------------|-------------------------------------|
| System | admin, base, kernel |
| Libraries | libs, oldlibs, libdevel |
| Development | devel, debug, interpreters, rust, python, java |
| Networking | net, web, mail |
| Graphics / X11 | x11, graphics, sound, video |
| Utils | utils, shells, editors, text, doc |
| Games | games |
| Science | science, math |
| Other | empty / unknown |

Sections list is dynamic: **group by actual Section string**, with friendly titles for known ones; unknown sections still appear as their own group.

### 5.4 UI layout (cyber-brutalist)

```text
┌──────────────────────────────────────────┐
│ ← SOFTWARE MANAGER          [PROOT|CHROOT]│
│ // INSTALLED APT PACKAGES                │
│ ───────────────────────────────────────  │
│ [Search……………]  [All|Marketplace|System]  │
│                                          │
│ GRAPHICS / X11                     12    │
│ ┌──────────────────────────────────────┐ │
│ │ glmark2     2.0-1     8.2 MB   [MP]  │ │
│ │ mesa-utils  …         1.1 MB   [MP]  │ │
│ └──────────────────────────────────────┘ │
│ LIBRARIES                           340  │
│ ┌──────────────────────────────────────┐ │
│ │ libfoo1     …         400 KB         │ │
│ └──────────────────────────────────────┘ │
│ …                                        │
│ Footer: N packages · total size S        │
└──────────────────────────────────────────┘
```

| Element | Behavior |
|---------|----------|
| **Env badge** | Current `linux_method` |
| **Search** | Filter package name / summary |
| **Chips** | All · Marketplace-only (registry) · System (not in registry) |
| **Row** | name, version, size; **[MP]** badge if `InstallRegistry` has id or `apt_provides` match |
| **Tap row** | Detail sheet: description if known, size, source (apt / marketplace), Uninstall (if MP), Launch (if app) |
| **Refresh** | Re-run dpkg-query |
| **Empty / error** | Guest not installed / no root (chroot) → CTA to Proot/Chroot settings |

### 5.5 Size totals

- Per-package: dpkg Installed-Size  
- Category subtotal  
- Grand total in footer  
- Optional: compare to rootfs `du` (already on Proot/Chroot settings pages) — do not block SM on full `du`

### 5.6 Execution path

```text
UI → AptInventoryService.scan(method)
   → ShellCommandRunner + Proot/ChrootCommandBuilder (root or flux read-only)
   → parse TSV → group → bind to Recycler-like LinearLayout sections
```

Prefer **read-only as flux** if dpkg readable; use **root** if needed. Cache last scan in prefs/JSON with timestamp; show “Last scan · 2m ago”.

---

## 6. Marketplace page

### 6.1 Purpose

Browse curated catalog by **category**, install/uninstall with progress, see size after install, launch apps.

### 6.2 UI layout

```text
┌──────────────────────────────────────────┐
│ ← MARKETPLACE               [PROOT|CHROOT]│
│ // CURATED COMPONENTS & APPS             │
│ ───────────────────────────────────────  │
│ [Update catalog]  Last sync: …           │
│                                          │
│ Categories (horizontal chips)            │
│ [All] [Compat] [Graphics] [Apps] …       │
│                                          │
│ COMPATIBILITY                            │
│ ┌──────────────────────────────────────┐ │
│ │ box64     component   ~15 MB         │ │
│ │ Run x86_64 binaries                  │ │
│ │ [INSTALLED 14.2 MB] or [INSTALL]     │ │
│ └──────────────────────────────────────┘ │
│ APPS                                     │
│ ┌──────────────────────────────────────┐ │
│ │ glmark2   app         ~8 MB          │ │
│ │ [LAUNCH] [UNINSTALL] or [INSTALL]    │ │
│ └──────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

Detail page / expand:

- Full description, deps list (clickable → scroll to dep)  
- Supported env (proot/chroot)  
- Version, homepage link  
- Install log panel (streamed)  
- Measured size after install  

### 6.3 Install UX

1. User taps INSTALL.  
2. Resolve deps → confirmation dialog: “Will install: mesa-utils → glmark2”.  
3. Full-screen or bottom log sheet (reuse patterns from onboarding / script install if present).  
4. Stream `ShellCommandRunner.runStreamed` lines.  
5. Success → registry write; toast; row becomes INSTALLED + size.  
6. Failure → keep log; RETRY.

### 6.4 Launch (apps only)

| Step | Action |
|------|--------|
| 1 | Ensure X11 server path exists (`start_gui.sh` / chroot GUI start) if display not up — **or** document “start Graphical Desktop first” v1 simpler |
| 2 | Run guest command as `flux`: `launch.command` with `DISPLAY` / Wayland env used by existing GUI scripts |
| 3 | Bring X11 activity to front (`com.termux.x11.MainActivity`) |

**v1 recommendation:** If Graphical Desktop not running, toast: “Start Graphical Desktop from Settings first”, then still allow launch attempt. v2: auto-start GUI chain.

### 6.5 Catalog sync

| Event | Behavior |
|-------|----------|
| Page open | Use cache if age < 6h; else background refresh |
| **Update catalog** button | Force fetch `catalog.json` |
| Offline | Show cache; disable install if scripts missing |
| Cache path | `{filesDir}/marketplace/cache/catalog.json` + scripts under `cache/packages/<id>/` |

Optional integrity: GitHub API release asset with signed checksums (later).

---

## 7. Local Install Registry

### 7.1 Path

```text
{filesDir}/marketplace/registry.json
```

Separate files optional: `registry-proot.json` / `registry-chroot.json` **or** one file keyed by env:

```json
{
  "schema_version": 1,
  "entries": {
    "proot:glmark2": {
      "id": "glmark2",
      "env": "proot",
      "kind": "app",
      "version": "1.0.0",
      "installed_at": "2026-07-30T12:00:00Z",
      "size_bytes": 8388608,
      "apt_provides": ["glmark2"],
      "launch": { "type": "x11", "command": "glmark2" },
      "source": "marketplace",
      "state": "installed"
    },
    "proot:mesa-utils": {
      "id": "mesa-utils",
      "env": "proot",
      "kind": "component",
      "version": "1.0.0",
      "installed_at": "…",
      "size_bytes": 1100000,
      "apt_provides": ["mesa-utils"],
      "source": "marketplace",
      "state": "installed"
    }
  }
}
```

### 7.2 Rules

| Rule | Detail |
|------|--------|
| Key | `"$env:$id"` |
| Marketplace-only | Pure apt packages user installed in shell do **not** get registry rows unless detected by optional “import” (out of scope v1) |
| SM badge | Registry hit **or** `apt_provides` package present + registry |
| Uninstall | Run package `uninstall.sh` → delete registry key; **do not** auto-remove deps |
| Detect orphan | SM can show apt pkg without registry (normal Debian) |

---

## 8. App code structure (planned)

### 8.1 New packages / files under `app/`

```text
app/src/main/java/com/ivarna/nativecode/
  marketplace/
    MarketplaceModels.kt       # data classes (Catalog, Package, Category, Kind)
    MarketplaceClient.kt       # HTTP fetch + disk cache
    InstallRegistry.kt         # read/write registry.json
    AptInventoryService.kt     # dpkg-query parse + section group
    PackageInstallRunner.kt    # dep sort + execute install/uninstall
    MarketplacePaths.kt        # filesDir helpers
  MainActivity.kt              # pages + hub buttons (or extract later)
```

Prefer **not** dumping 2k lines into `MainActivity` forever — v1 may still build pages there (existing pattern: Proot/Chroot settings), with **logic** in `marketplace/` package.

### 8.2 Page IDs

```kotlin
private val ID_SOFTWARE_MANAGER = 16
private val ID_MARKETPLACE = 17
// optional:
private val ID_MARKETPLACE_DETAIL = 18
```

Wire: `navigateToPage`, `pageStack`, back → Settings Hub, `contentFrame.addView`, hide/show like Proot/Chroot pages.

### 8.3 Settings Hub buttons

Mirror `buildX11SettingsSectionButton()` / Proot row:

| Title | Subtitle |
|-------|----------|
| **SOFTWARE MANAGER** | Installed apt packages by category |
| **MARKETPLACE** | Curated components & apps |

### 8.4 Networking

- `HttpsURLConnection` or existing OkHttp if present.  
- User-Agent: `NativeCode/<version>`.  
- Timeouts: connect 15s / read 60s for scripts.  
- No credentials (public repo).

### 8.5 Permissions / Play policy

- Installs run **inside guest** (user’s Linux env), not as privileged Android package installs.  
- Document in Play compliance notes: user-initiated script download from GitHub; no silent RCE.  
- Clear UI: “Runs install script from nativecode-marketplace”.

---

## 9. Guest execution details

### 9.1 Install command shape

**proot (root):**

```text
proot-distro login debian --user root -- /bin/bash /tmp/nc-mp/<id>/install.sh
```

Built via existing host bash + `TermuxHostPaths` / `ProotCommandBuilder` patterns (may need a small `buildRootCmd` helper if only flux login exists today).

**chroot (root):**

```text
busybox chroot $CHROOT_PATH /bin/bash /tmp/nc-mp/<id>/install.sh
```

via `ChrootCommandBuilder.build(ctx, script, user = "root")` after copying scripts into guest `/tmp`.

### 9.2 Staging scripts into guest

| Mode | Staging |
|------|---------|
| proot | Write under rootfs `tmp/nc-mp/...` from host (`File(prootRootfs, "tmp/nc-mp")`) **or** `proot-distro login` + cat |
| chroot | Host writes to `/data/local/tmp/chrootDebian13/tmp/nc-mp/...` via `su` / direct if writable |

Always stage `lib/nc_mp_common.sh` next to scripts.

### 9.3 Timeouts

| Package class | Soft timeout |
|---------------|--------------|
| Small apt (mesa-utils, glmark2) | 10 min |
| box64 / fex | 20 min |
| blender | 45 min |

UI cancel: destroy process; mark `state=failed`.

---

## 10. Settings + navigation integration

### 10.1 Hub order (target)

1. Graphical Desktop  
2. Terminal Settings  
3. Linux Isolation Mode  
4. Proot Settings  
5. Chroot Settings  
6. **Software Manager** ← new  
7. **Marketplace** ← new  
8. X11 Settings  
9. System Scripts  
10. Re-run Onboarding  

### 10.2 Back stack

Same as Proot/Chroot: push ID on open; back pops to `ID_SETTINGS`.

### 10.3 Env switch mid-page

If user changes isolation while on SM/MP: refresh inventory/registry for new env; toast “Switched to chroot — packages listed for chroot”.

---

## 11. Implementation phases

### Phase 0 — Catalog repo + demo packages (can parallel app)

- [ ] `gh repo create abhay-byte/nativecode-marketplace --public`  
- [ ] Scaffold layout, `lib/nc_mp_common.sh`, `catalog.json`  
- [ ] Implement demo: `mesa-utils`, `box64`, `fex-emu`, `glmark2`, `blender` (each install/uninstall only own payload; deps declared)  
- [ ] Manual test: run install.sh inside proot guest + chroot guest via adb  

### Phase 1 — App foundation

- [ ] `marketplace/` Kotlin models + paths + registry  
- [ ] `MarketplaceClient` fetch/cache catalog  
- [ ] `AptInventoryService` dpkg scan (proot + chroot)  
- [ ] Unit-parse tests offline if feasible  

### Phase 2 — Software Manager UI

- [ ] Page + hub button  
- [ ] Category sections, search, chips, sizes  
- [ ] MP badge from registry  
- [ ] Refresh + error empty states  

### Phase 3 — Marketplace UI + install runner

- [ ] Page + hub button  
- [ ] Category chips, package cards  
- [ ] Dep resolve + install log stream  
- [ ] Uninstall + registry updates  
- [ ] Launch for `kind=app` (X11)  

### Phase 4 — Polish

- [ ] Offline cache UX  
- [ ] Size measurement consistency  
- [ ] Detail sheets  
- [ ] Docs: user-facing short help in-app  
- [ ] Compile green debug/release Kotlin  

### Phase 5 (optional later)

- [ ] Home “Installed apps” strip  
- [ ] Auto-start X11 before launch  
- [ ] Catalog CI (schema validate)  
- [ ] Versioned releases of catalog (pin app to tag)  
- [ ] Mirror apt section icons  

---

## 12. Primary files to touch (app)

| Path | Role |
|------|------|
| `MainActivity.kt` | Hub rows, page IDs, build SM + MP layouts, navigation |
| `marketplace/*.kt` | Client, registry, inventory, runner, models |
| `terminal/ProotCommandBuilder.kt` | Possibly root non-interactive helper |
| `terminal/ChrootCommandBuilder.kt` | Root script execution already mostly there |
| `terminal/ShellCommandRunner.kt` | Reuse stream; maybe timeout API |
| `terminal/LinuxCommandBuilder.kt` | Route by method |
| `AndroidManifest.xml` | Only if INTERNET not already present (likely is) |

**New repo (not in termux-lib):** `nativecode-marketplace` as above.

**Optional mirror for offline/dev:**  
`docs/marketplace-demo/` or `app/src/main/assets/marketplace/catalog.json` seed so first UI works without network; prefer real GitHub as SSOT.

---

## 13. Acceptance criteria

| # | Criterion |
|---|-----------|
| 1 | Settings Hub has **Software Manager** and **Marketplace** buttons opening **different pages** |
| 2 | SM lists **apt-installed** packages for **proot** grouped by section/category with sizes |
| 3 | SM same for **chroot** when chroot installed + root available |
| 4 | Marketplace loads catalog from **public GitHub** repo (cache + manual refresh) |
| 5 | Catalog has **≥3 components** and **≥2 apps** with decoupled scripts |
| 6 | Installing an app installs **deps first** as separate packages; no mega-script |
| 7 | After install, UI shows **measured size**; registry records **kind** (`component`/`app`) |
| 8 | App packages expose **Launch** (X11 path); components do not |
| 9 | Uninstall removes package + registry entry; leaves deps |
| 10 | Scripts idempotent; re-install safe |
| 11 | Compile debug/release Kotlin succeeds |

---

## 14. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Blender huge / no arm64 apt | Script uses realistic path (box64 + prebuilt or document unsupported arch); fail gracefully |
| FEX/box64 install brittle | Pin versions; component-only; good logs |
| dpkg-query slow on huge rootfs | Background thread; cache; show spinner |
| MainActivity bloat | Logic in `marketplace/`; UI builders can move later |
| Catalog XSS/RCE fear | Only run scripts after explicit user INSTALL; show source URL; no auto-run |
| chroot no root | SM/MP show “Root required” + link Chroot Settings |
| Network blocked | Seed assets catalog for demo; clear offline UI |

---

## 15. Non-goals reminder

- Replacing `apt` full UI  
- One script that installs “dev suite + games + gpu”  
- Tracking every manual `apt install` as marketplace  
- Shipping blender binaries inside NativeCode APK  
- Auto-migrating packages between proot and chroot  

---

## 16. Suggested commit / PR split (when implementing)

1. **repo:** `nativecode-marketplace` scaffold + demo packages  
2. **app:** registry + client + inventory (no UI)  
3. **app:** Software Manager page + hub button  
4. **app:** Marketplace page + install runner + launch  
5. **docs:** link plan status → implemented  

---

## 17. Open questions (resolve at impl start if needed)

1. **Repo owner/name final?** Default `abhay-byte/nativecode-marketplace`.  
2. **Launch v1:** require GUI already running vs auto `start_gui`? Plan default: require GUI first.  
3. **blender:** include in demo only if install path proven on device aarch64 within timeout; else ship catalog entry `status: experimental` and hide install.  
4. **Should Software Manager allow `apt remove` for non-MP packages?** v1: **no** (read-only inventory + MP uninstall only).  

---

## 18. Related docs

- Settings hub patterns: `docs/plan/settings-proot-card-storage.md`, `settings-chroot-card-storage-uninstall.md`  
- GUI launch: `docs/plan/settings-xfce-chroot-gui-launch.md`, assets `start_gui.sh`, `chroot/start_gui_chroot.sh`  
- UI tokens: `docs/project/ui_design.md`  
- Guest runners: `ProotCommandBuilder.kt`, `ChrootCommandBuilder.kt`, `ShellCommandRunner.kt`  
- X11: `docs/plan/x11-ui-settings-notification-rehaul.md`  

---

**End of plan.** Impl only after explicit user approval (create marketplace repo + app pages).
