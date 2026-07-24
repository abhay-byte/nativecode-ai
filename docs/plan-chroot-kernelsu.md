# Plan: KernelSU Root Shell + Chroot Debian 13 + Proot/Chroot Toggle

> Status: DRAFT — awaiting approval before any code changes.

---

## Overview

Three independent but connected workstreams:

| # | Workstream | New Files / Changed Files |
|---|-----------|--------------------------|
| 1 | **RootShellService** — KernelSU root shell access + script runner | `RootShellService.kt` (new), `AndroidManifest.xml` |
| 2 | **Chroot Onboarding** — add chroot Debian 13 install card to OnboardingActivity isolation page | `OnboardingActivity.kt`, `assets/scripts/chroot/setup_debian13_chroot.sh` (new) |
| 3 | **Proot/Chroot Toggle + switch-case everywhere** — persistent toggle, settings card, all 23 call sites | `MainActivity.kt` |

---

## Workstream 1 — RootShellService (KernelSU)

### Goal
Give the app a typed Kotlin object to execute shell scripts as root via KernelSU's `su`, with live stdout/stderr streaming.

### Files

#### `RootShellService.kt` (new)
```kotlin
object RootShell {
    fun isRootAvailable(): Boolean
    // tries: su -c id, checks uid=0 in output

    fun execute(cmd: String, onLine: (String)->Unit, onDone: (Int)->Unit)
    // ProcessBuilder("su", "-c", cmd), streams stdout+stderr line-by-line
    // callbacks dispatched on Dispatchers.Main

    fun executeScript(scriptPath: String, onLine: (String)->Unit, onDone: (Int)->Unit)
    // su -c "sh <scriptPath>"

    fun executeScriptAsset(assetName: String, ctx: Context, onLine: (String)->Unit, onDone: (Int)->Unit)
    // copies asset to /data/local/tmp/nativecode_scripts/
    // chmod +x, then calls executeScript()
}
```
Uses `CoroutineScope(Dispatchers.IO)` internally.

#### `AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.ACCESS_SUPERUSER" />
```

### Test
- KernelSU allowlist `com.ivarna.nativecode`
- `RootShell.isRootAvailable()` → true
- `RootShell.execute("id")` → logcat shows `uid=0(root)`

---

## Workstream 2 — Chroot Debian 13 Onboarding

### Goal
Add **CHROOT (Root Required)** option card alongside **PROOT (RECOMMENDED)** on `buildIsolationPage()`. Selecting it runs `setup_debian13_chroot.sh` via `RootShell`.

### New Assets
- `assets/scripts/chroot/setup_debian13_chroot.sh` — full setup script (provided by user)
- `assets/scripts/chroot/setup_customization_debian_chroot.sh` — customization adapted for `/data/local/tmp/chrootDebian13`
- `assets/scripts/chroot/setup_cli_tools_chroot.sh` — CLI tools adapted for chroot path

### `OnboardingActivity.kt` changes

**`buildIsolationPage()`:**
- Add `var selectedIsolationMethod = "proot"` field
- Add `chrootCard` below `prootCard` (same visual structure)
  - Title: `CHROOT`, Badge: `ROOT REQUIRED`
  - Desc: "Kernel-level isolation via Linux chroot(2). Requires KernelSU/Magisk root. Max compatibility + performance."
  - Click → selectedIsolationMethod = "chroot", highlight chrootCard, unhighlight prootCard

**`runDebianBaseSetup()`:**
```kotlin
if (selectedIsolationMethod == "chroot") {
    RootShell.executeScriptAsset("scripts/chroot/setup_debian13_chroot.sh", this,
        onLine = { updateBaseStatus(it) },
        onDone = { code ->
            if (code == 0) {
                getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                    .edit().putString("linux_method", "chroot").apply()
                // advance to next page
            }
        }
    )
} else {
    // existing proot flow unchanged
    getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        .edit().putString("linux_method", "proot").apply()
}
```

**`runCliToolsSetup()`:**  
Same branch — if chroot, use `setup_cli_tools_chroot.sh` run inside chroot via `RootShell`.

**`deployScripts()`:**  
Copy chroot scripts when method == chroot.

---

## Workstream 3 — Settings Toggle + Switch-Case Throughout App

### 3a. SharedPrefs key
```kotlin
const val PREF_LINUX_METHOD = "linux_method"  // "proot" | "chroot"
// default = "proot"
```
Read into `var currentLinuxMethod: String` in `MainActivity.onCreate()`.

### 3b. `buildLinuxCommand()` helper (new private fun in MainActivity)
```kotlin
private fun buildLinuxCommand(
    shellCmd: String,
    user: String = "flux",
): Pair<Array<String>, HashMap<String, String>> {
    return when (currentLinuxMethod) {
        "chroot" -> {
            val chrootPath = "/data/local/tmp/chrootDebian13"
            val inner = "busybox chroot $chrootPath /bin/su - $user -c \"$shellCmd\""
            arrayOf("su", "-c", inner) to HashMap(System.getenv())
        }
        else -> { // proot
            val nld = applicationInfo.nativeLibraryDir
            val shell = File(nld, "libbash.so").absolutePath
            val args = arrayOf(shell, "-c",
                "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian --shared-tmp --user $user -- zsh -c \"$shellCmd\"")
            val env = HashMap(System.getenv()).apply {
                put("PD_PROOT_BIN", File(nld, "libproot.so").absolutePath)
                put("PROOT_LOADER", File(nld, "libloader.so").absolutePath)
                put("LD_LIBRARY_PATH", "/data/data/com.ivarna.nativecode/files/usr/lib")
                // ... all existing proot env vars
            }
            args to env
        }
    }
}
```

Also `buildLinuxShellArgs(type: String): Array<String>` for TerminalSession variant (plain shell vs tool).

### 3c. All 23 proot call sites → use `buildLinuxCommand()`

| Line(s) | Function | Refactor |
|---------|----------|---------|
| 1527–1530 | `createNewTerminalSession` | `buildLinuxCommand(toolCmd)` |
| 3423, 3430 | `buildScriptCard` run-in-debian | `buildLinuxCommand(scriptCmd)` |
| 4527, 4530 | git diff untracked | `buildLinuxCommand(gitCmd)` |
| 4564 | git diff untracked 2 | `buildLinuxCommand(untrackedCmd)` |
| 4817, 4820 | discard changes | `buildLinuxCommand(discardCmd)` |
| 4878, 4881 | git commit | `buildLinuxCommand(commitCmd)` |
| 5641, 5677 | project create (git clone/mkdir) | `buildLinuxCommand(...)` |
| 7229 | project dir tree git | `buildLinuxCommand(gitCmd)` |
| 7274, 7278 | project dir tree mkdir | `buildLinuxCommand("mkdir -p $path")` |
| 7981, 7984 | project workspace git | `buildLinuxCommand(gitCmd)` |
| 8251, 8254 | tool selector | `buildLinuxCommand(toolCmd)` |

Dynamic labels (lines 4954, 5504, 8923+):
```kotlin
val methodLabel = if (currentLinuxMethod == "chroot") "Chroot (Debian Trixie)" else "PRoot (Debian Trixie)"
```

Guest home path (line 2564):
```kotlin
val guestHomeDir = if (currentLinuxMethod == "chroot")
    File("/data/local/tmp/chrootDebian13/home/flux")
else
    File(filesDir, "usr/var/lib/proot-distro/containers/debian/rootfs/home/flux")
```

### 3d. Settings page additions

**`buildSettingsHubLayout()`** — insert at top, before existing cards:
```kotlin
if (isLinuxMethodToggleVisible()) {
    settingsHubLayout.addView(buildLinuxMethodToggleCard())
    settingsHubLayout.addView(spacer(16))
} else {
    settingsHubLayout.addView(buildRootEfficiencyCard())
    settingsHubLayout.addView(spacer(16))
}
```

**`isLinuxMethodToggleVisible()`:**
```kotlin
private fun isLinuxMethodToggleVisible(): Boolean =
    File("/data/local/tmp/chrootDebian13/.flux_configured").exists()
```

**`buildRootEfficiencyCard(): View`** (new):
- Header: "ROOT ENVIRONMENT"
- Root status chip: async `RootShell.isRootAvailable()` → green "KernelSU Active" / red "No Root"
- "Run Benchmark" button → `RootShell.execute("time dd if=/dev/zero of=/dev/null bs=1M count=100")` → shows result inline
- "Install Chroot Debian 13" button → starts `OnboardingActivity` with `EXTRA_FORCE_CHROOT = true` (skips to page 2, preselects chroot)

**`buildLinuxMethodToggleCard(): View`** (new):
- Header: "LINUX ENVIRONMENT"
- Segmented toggle: `[ PROOT ]  [ CHROOT ]`
- Reads `nativecode_prefs.linux_method` on create
- On toggle → writes pref, sets `currentLinuxMethod`, shows toast "Restart terminal sessions to apply"

---

## Chroot Command Reference

| Action | PRoot | Chroot |
|--------|-------|--------|
| Shell | `proot-distro login debian --shared-tmp --user flux` | `su -c "busybox chroot /data/local/tmp/chrootDebian13 /bin/su - flux"` |
| Run cmd | `proot-distro login debian --shared-tmp --user flux -- zsh -c "CMD"` | `su -c "busybox chroot /data/local/tmp/chrootDebian13 /bin/bash -c 'CMD'"` |
| Home dir | `.../proot-distro/containers/debian/rootfs/home/flux` | `/data/local/tmp/chrootDebian13/home/flux` |

---

## Implementation Order

```
1. assets/scripts/chroot/setup_debian13_chroot.sh  (copy from user's script)
2. RootShellService.kt                              (new singleton)
3. AndroidManifest.xml                             (ACCESS_SUPERUSER)
4. OnboardingActivity.kt                           (chroot card + runDebianBaseSetup branch)
5. MainActivity.kt — add currentLinuxMethod + buildLinuxCommand()
6. MainActivity.kt — refactor all 23 proot call sites
7. MainActivity.kt — add buildRootEfficiencyCard() + buildLinuxMethodToggleCard()
8. MainActivity.kt — update buildSettingsHubLayout() to insert new cards
9. Build release APK + adb install -r
10. Test on device (KernelSU allowlisted)
```

---

## Notes / Risks

- KernelSU must allowlist `com.ivarna.nativecode` — user action required once
- Chroot mounts (`/dev`, `/proc`, `/sys`) are set up inside `setup_debian13_chroot.sh` and `start_debian13.sh` — app does NOT duplicate this
- X11 GUI launch for chroot uses `/data/local/tmp/start_debian13_gui.sh` via `RootShell.executeScript()`
- Default is always **proot** — toggle only appears after chroot is installed; existing users unaffected
- `busybox` path in chroot commands is detected dynamically in `setup_debian13_chroot.sh`; app uses the generated `start_debian13.sh` which already has `BB=` baked in
