# Project git clone: method mismatch (proot vs chroot empty tree)

**Date:** 2026-07-29  
**Status:** implemented (method on build/clone/ensureDir; host .git verify; activeProjectMethod + open recovery)  
**Scope:** Create/Import Project **git clone** lands in wrong rootfs vs project `linuxMethod` → Workspace terminal + Directory show empty `~/repos/<name>`.  
**Out of scope:** multi-remote clone, private-token UI, full proot↔chroot data sync product.

**Related:**
- `ProjectManager.cloneRepo` / `ensureDir`
- `LinuxCommandBuilder.currentMethod` + `build()`
- `ProjectPathResolver.resolve`
- Create UI: `projectCreateSelectedMethod` in `MainActivity`
- Paths SSOT: proot `filesDir/usr/var/lib/proot-distro/containers/debian/rootfs`, chroot `/data/local/tmp/chrootDebian13`

---

## 1. Symptom (user + screenshots)

| Surface | Observed |
|---------|----------|
| Clone overlay | Progress lines (“Cloning…”, etc.), exit 0 → project opens |
| Workspace terminal | `~/repos/fluxlinux`, `ls` empty |
| Directory tab | **NO FILES IN DIRECTORY** — host path under **proot** rootfs |

Clone UI “worked”; filesystem for the **project’s selected method** is empty.

---

## 2. ADB evidence (device `192.168.1.52:44789`, 2026-07-29)

### 2.1 Host trees under `home/flux/repos/`

| Path | fluxlinux files | mkm files |
|------|-----------------|-----------|
| **proot** `…/debian/rootfs/home/flux/repos/` | **0** (empty dir only) | **257** full clone |
| **chroot** `/data/local/tmp/chrootDebian13/home/flux/repos/` | **405** full clone | **0** (empty dir only) |

Timestamps both ~08:25–08:26 — same session window as create flow.

### 2.2 `nativecode_prefs` / `projects_json`

```json
[
  {
    "name": "Fluxlinux",
    "path": "/home/flux/repos/fluxlinux",
    "linuxMethod": "proot"
  },
  {
    "name": "MKM",
    "path": "/home/flux/repos/mkm",
    "linuxMethod": "chroot"
  }
]
```

Global: `linux_method` = **proot** (after last open).

### 2.3 Inversion map (smoking gun)

| Project | Saved method | Where clone actually is | Where UI looks |
|---------|--------------|-------------------------|----------------|
| **Fluxlinux** | proot | **chroot** (full) | proot → empty |
| **MKM** | chroot | **proot** (full) | chroot → empty |

Not “clone failed.” Clone **succeeded into the wrong isolation rootfs** relative to the project’s stored method.

### 2.4 Empty dirs are not the clone

Empty `fluxlinux` (proot) and `mkm` (chroot) match **`ensureDir(path)` after open** (mkdir under *then*-active method), not a failed git checkout.

---

## 3. Root cause

### 3.1 Clone ignores project method

`ProjectManager.cloneRepo`:

```kotlin
val gitCmd = "mkdir -p ~/repos && cd ~/repos && git clone --progress $gitUrl 2>&1"
val (args, envMap) = LinuxCommandBuilder.build(ctx, gitCmd)
// → always uses LinuxCommandBuilder.currentMethod
```

`LinuxCommandBuilder.build` has **no method parameter** — only global `currentMethod`.

### 3.2 Create flow sets method *after* clone

`MainActivity` create button:

1. `method = projectCreateSelectedMethod`  (UI chip: PRoot / Chroot)  
2. `ProjectManager.cloneRepo(...)`          ← **global** `currentMethod`  
3. on exit 0 → `addAndOpenProject(..., method)`  
4. **then** `LinuxCommandBuilder.currentMethod = method` + save project with that method  

So clone always targets **whatever global isolation was before create**, not the chip the user just selected.

### 3.3 Why it looks “inverted” for both projects

Typical sequence:

1. Global = **chroot** (or leftover). User creates **Fluxlinux**, chip **PRoot**.  
   - Clone → chroot rootfs (full).  
   - Project saved as **proot**.  
   - `ensureDir` creates empty proot `…/fluxlinux`.  
2. Global now **proot**. User creates **MKM**, chip **Chroot**.  
   - Clone → proot rootfs (full).  
   - Project saved as **chroot**.  
   - `ensureDir` creates empty chroot `…/mkm`.  

Matches ADB exactly. “Worked before” when only one method existed / chip always matched global.

### 3.4 Secondary gaps

| Gap | Effect |
|-----|--------|
| No post-clone host verify | exit 0 + empty target still opens project |
| `ensureDir` after open | Creates **empty** dir on wrong rootfs → masks “missing clone” |
| Directory resolve uses `currentMethod` | After open, method is switched to project method → reads empty tree |
| Terminal cwd is guest path | Same guest path, different rootfs bind → empty `ls` |
| `cloneRepo` hardcodes `~/repos` | OK if HOME=flux; path string still `/home/flux/repos/<repoName>` — fine, not the bug |

---

## 4. Goals

1. **Clone into the isolation method selected on Create/Import** (or explicit `method` arg), not ambient global.  
2. **`ensureDir` / path resolve for that project** use the same method.  
3. **Fail closed:** if target host path has no `.git` after clone, do not open project as success.  
4. **Optional recovery:** if clone exists only on the *other* rootfs, detect + offer move/copy or auto-repair method (device already has good data).  
5. No regression for non-git create (name only → mkdir in correct method).

---

## 5. Fix plan (implementation)

### 5.1 API: method override on command build

**File:** `LinuxCommandBuilder.kt`

```kotlin
fun build(
    ctx: Context,
    shellCmd: String,
    user: String = "flux",
    useSharedTmp: Boolean = true,
    method: String = currentMethod   // NEW — explicit override
): Pair<Array<String>, HashMap<String, String>> {
    return when (method) {
        "chroot" -> ChrootCommandBuilder.build(ctx, shellCmd, user)
        else -> ProotCommandBuilder.build(ctx, shellCmd, user, useSharedTmp)
    }
}
```

Do **not** mutate `currentMethod` inside `build`.

### 5.2 ProjectManager: clone + ensureDir take method

**File:** `ProjectManager.kt`

```kotlin
fun cloneRepo(
    ctx: Context,
    gitUrl: String,
    method: String = LinuxCommandBuilder.currentMethod,
    onProgress: (String) -> Unit,
    onDone: (Int) -> Unit
) {
    val gitCmd = "mkdir -p ~/repos && cd ~/repos && git clone --progress $gitUrl 2>&1"
    val (args, envMap) = LinuxCommandBuilder.build(ctx, gitCmd, method = method)
    ShellCommandRunner.runStreamed(ctx, args, envMap, onLine = onProgress, onDone = onDone)
}

fun ensureDir(ctx: Context, path: String, method: String = LinuxCommandBuilder.currentMethod) {
    val (args, envMap) = LinuxCommandBuilder.build(ctx, "mkdir -p $path", method = method)
    // ...
}
```

Optional: pass **target dir** explicitly:

```sh
mkdir -p /home/flux/repos && git clone --progress <url> /home/flux/repos/<repoName>
```

so clone path is not HOME-dependent (`~` expand edge cases).

### 5.3 MainActivity create: pass method + switch before clone (belt)

**Order:**

1. Read `method = projectCreateSelectedMethod`.  
2. Optionally set `LinuxCommandBuilder.currentMethod = method` **before** clone (prefs too) so any nested call is consistent.  
3. `cloneRepo(..., method = method, ...)`.  
4. On exit 0: **host verify** then `addAndOpenProject`.

```kotlin
// after exitCode == 0
val hostDir = ProjectPathResolver.resolve(this, path, method)
if (!File(hostDir, ".git").isDirectory) {
    // fail: toast + stay on create; do not open empty project
    return
}
addAndOpenProject(name, icon, path, method)
```

`addAndOpenProject`: `ensureDir(ctx, path, method)` only if path missing (non-git create).

### 5.4 Directory / file resolve: project method SSOT

When resolving `activeProjectPath`:

```kotlin
val method = getProjects().find { it.path == activeProjectPath }?.linuxMethod
    ?: LinuxCommandBuilder.currentMethod
ProjectPathResolver.resolve(this, activeProjectPath, method)
```

Or store `activeProjectMethod` next to path/name (cleaner than scan each time). Prefer **active field** set in `addAndOpenProject` / open card click.

### 5.5 Recovery (device already has good clones)

One-shot helper (Settings or open project):

```text
if target host missing .git:
  other = opposite rootfs path
  if other has .git:
    either (A) flip project.linuxMethod to match where data is
    or (B) rsync/cp -a other → target under selected method
```

For existing device:

| Project | Recommended repair |
|---------|-------------------|
| Fluxlinux (method proot, data in chroot) | Switch method → chroot **or** copy chroot→proot |
| MKM (method chroot, data in proot) | Switch method → proot **or** copy proot→chroot |

Ship **auto-detect on open** (toast “Repo found under X; using X”) as safe default; copy is optional later.

### 5.6 Tests / ADB checklist

1. Global = proot; create project with **Chroot** + public git URL →  
   - full tree under `/data/local/tmp/chrootDebian13/home/flux/repos/<repo>`  
   - proot path **must not** be the only populated one  
   - Directory tab lists files.  
2. Global = chroot; create with **PRoot** → inverse.  
3. Non-git create → empty dir under **selected** method only.  
4. Clone fail (bad URL) → no project entry / no empty success.  
5. Re-open existing inverted projects: recovery path or manual method flip restores tree.

```sh
# proot
ls $PROOT_ROOTFS/home/flux/repos/<name> | head
# chroot
su -c "ls /data/local/tmp/chrootDebian13/home/flux/repos/<name> | head"
```

---

## 6. Files to touch

| File | Change |
|------|--------|
| `terminal/LinuxCommandBuilder.kt` | `method` param on `build` |
| `terminal/ProjectManager.kt` | `cloneRepo`/`ensureDir` take `method`; optional absolute clone path |
| `MainActivity.kt` | pass method into clone; host `.git` verify; ensureDir method; optional `activeProjectMethod` |
| `ProjectPathResolver.kt` | (optional) helper `projectHostDir(ctx, project)` |
| This plan | status → implemented after ship |

No asset script changes required for core fix.

---

## 7. Risks

| Risk | Mitigation |
|------|------------|
| Chroot clone needs root/su | Already required for chroot; show clear toast if su fails |
| Concurrent clone while global method flips | Explicit `method` arg; avoid relying on global during clone |
| Duplicate repo name across methods | OK (separate rootfs); recovery must not overwrite without confirm |
| `git clone` into existing empty dir | Prefer `git clone url dest` with non-existing dest; `rmdir` empty shell dirs first if needed |

---

## 8. Implementation order

1. `LinuxCommandBuilder.build(..., method=)`  
2. `ProjectManager.cloneRepo/ensureDir(..., method=)`  
3. MainActivity create path + post-clone `.git` check  
4. `activeProjectMethod` / resolve with project method  
5. Open-project recovery (detect other rootfs)  
6. ADB both scenarios + existing Fluxlinux/MKM repair  

**Approval gate:** implement only after user OK on this plan.

---

## 9. Summary

| Question | Answer |
|----------|--------|
| Did clone run? | **Yes** — data on disk |
| Where? | **Opposite** rootfs of project’s `linuxMethod` |
| Why? | Clone uses **global** `currentMethod`; UI method applied **after** clone |
| Why both empty in UI? | Directory/terminal resolve selected method; files live elsewhere |
| Why “worked before”? | Single method / chip matched global |

**Primary fix:** pass selected isolation method into `cloneRepo` (and build) **before** clone; verify host path; keep resolve + ensureDir on same method.
