# NativeCode Chroot SSOT component

**Status:** Implemented (**v2.2**); helper ADB smoke PASS; UI M1–M10 needs signed APK  
**Date:** 2026-08-01  
**Plan (hub):** [`docs/plan/chroot-ssot-shell-runner.md`](../plan/chroot-ssot-shell-runner.md)  
**Guest env (v2.1 PATH):** [`docs/plan/chroot-ssot-guest-env-fix.md`](../plan/chroot-ssot-guest-env-fix.md) — **partial**; superseded for TTY/git  
**TTY / workdir / git (v2.2):** [`docs/plan/chroot-ssot-interactive-tty-and-git.md`](../plan/chroot-ssot-interactive-tty-and-git.md)  
**Crash rules:** [`chroot-adb-device-crash-postmortem.md`](./chroot-adb-device-crash-postmortem.md)  
**ADB guide:** [`adb-shell-access.md`](./adb-shell-access.md)  

**Hard constraints**
- **Proot untouched** — only chroot.
- Live ADB: no mount storms, no nested-runner loops, host `timeout` always.
- Mounts **idempotent**; devpts heal is **one** umount+remount when `ptmxmode=000` only.

---

## 1. What this component is

One on-device shell helper that owns:

1. **Busybox resolve** (KSU/Magisk/system — never app busybox)  
2. **Idempotent mounts** (+ controlled devpts heal)  
3. **chroot(2) entry** as `flux` or `root`  
4. **Safe guest command modes** (login / sh / exec / b64)  
5. **Guest env contract** (`env -i` + Debian PATH; TTY-safe b64)

Kotlin and other scripts **must not** re-implement mounts or raw `busybox chroot` for session/exec.

| Item | Value |
|------|--------|
| Asset (source) | `app/src/main/assets/scripts/chroot/nativecode_chroot.sh` |
| Version stamp | `nativecode-chroot v2.2` (must match `ChrootCommandBuilder.CHROOT_HELPER_VERSION`) |
| On-device path | `/data/local/tmp/nativecode_chroot.sh` |
| Rootfs | `/data/local/tmp/chrootDebian13` |
| Host tmp bridge | `/data/data/com.ivarna.nativecode/files/usr/tmp` → guest `/mnt/host-tmp` |
| Guest sticky tmp | `$CHROOT/tmp` disk-backed `1777` — **never** full-bind host over `/tmp` |
| Default user | `flux` (uid 1000, shell zsh) |
| Session host exec | `/system/bin/sh` + WINCH trap (SELinux) |

### Version history (helper stamp)

| Stamp | What |
|-------|------|
| v1 | SSOT hub: mounts + login/sh/exec/b64 |
| v2.1 | Guest `env -i` + Debian PATH + absolute `/usr/bin/base64` |
| **v2.2** | TTY-safe b64 (no stdin pipe); `login --workdir`; devpts `ptmxmode` heal; Kotlin `'` → b64 |

---

## 2. Public CLI

```text
nativecode_chroot.sh version
nativecode_chroot.sh mount [--x11]
nativecode_chroot.sh login [--user flux|root] [--shell zsh|bash] [--workdir PATH]
nativecode_chroot.sh sh    [--user flux|root] -- 'shell string'
nativecode_chroot.sh exec  [--user flux|root] -- CMD [ARGS...]
nativecode_chroot.sh b64   [--user flux|root] -- BASE64_PAYLOAD
```

### Env

| Var | Default |
|-----|---------|
| `NC_CHROOT` | `/data/local/tmp/chrootDebian13` |
| `NC_PACKAGE` | `com.ivarna.nativecode` |
| `NC_HOST_TMP` | `/data/data/$NC_PACKAGE/files/usr/tmp` |
| `NC_PREFIX` | `/data/data/$NC_PACKAGE/files/usr` |
| `NC_BB` | auto-detect |
| `NC_SHELL` | default login shell hint (`zsh`) |

### Semantics

| Mode | Use |
|------|-----|
| `mount` | Ensure binds only; safe to call often after idempotent impl |
| `login` | Interactive login shell; optional `--workdir` (project workspace cwd) |
| `sh` | One shell string as user (re-encodes to b64 when host `base64` exists) |
| `exec` | Argv-preserving command (AI `launch_tool` bare path) |
| `b64` | Kotlin complex cmds / git / `RootShell` — no quote hell |

### Guest env contract (v2.2)

All guest entry modes go through `guest_chroot_env`:

```text
busybox chroot $NC_CHROOT /usr/bin/env -i PATH=… HOME=… USER=… TERM=… … <guest binary>
```

#### PATH / env (since v2.1)

| Rule | Detail |
|------|--------|
| Clean env | `env -i` — **never** inherit Android `PATH` (`/system/bin:…`) or `LD_*` |
| Debian PATH (root) | `/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin` |
| Debian PATH (flux) | `/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo/bin:/opt/nodejs/bin:` + root PATH |
| Also set | `HOME` `USER` `LOGNAME` `TERM` `LANG` `LC_ALL` `TMPDIR=/tmp` `XDG_RUNTIME_DIR=/tmp` `DEBIAN_FRONTEND=noninteractive`; flux also `NVM_DIR` |
| Outer ProcessBuilder | Android PATH for host `su` / `sh` only — **not** inside chroot |
| Proot | **unchanged** — already `env -i` + Debian PATH |

Without PATH contract: root `b64` dies (`base64: command not found`), SoftMgr empty, rooted profile `id` fails.

#### TTY / `guest_b64` (v2.2 — critical)

| Rule | Detail |
|------|--------|
| Decode | absolute `/usr/bin/base64` then `/bin/base64` — never bare `base64` |
| **No stdin pipe** | **Forbidden:** `{ decode; } \| /bin/bash` — steals stdin; breaks TUI (`/dev/tty`, bubbletea, grok ENXIO) |
| **Required shape** | decode payload → guest temp file `/tmp/.nc_b64_$$` → `bash --noprofile --norc $file` → `rm` (preserves fds 0/1/2) |
| `guest_sh` | host-encode then `guest_b64` when possible |
| Capture cmds | SoftMgr / git / apt — no TTY needed; same path OK |
| Interactive tools | TerminalSession PTY must reach guest; use `login` / `exec` / TTY-safe `b64` |

#### Login workdir (v2.2)

| Rule | Detail |
|------|--------|
| Flag | `login --workdir /home/flux/repos/…` (no single quotes in path) |
| Behavior | `cd` then interactive shell (`zsh -l` / `bash -l`) under single `su -` / root |
| Kotlin | `mkdir -p DIR && cd DIR && exec zsh` → `login --user … --workdir DIR` (not non-interactive b64) |
| Workspace | flux project shell uses workdir; shell-root often `workDir=null` → home login |

#### Kotlin simple vs b64 (v2.2)

| Rule | Detail |
|------|--------|
| `isSimpleGuestCmd` | reject `$` `` ` `` `"` `'` `\n` `\` — **including single quote** |
| Why `'` | git paths use `cd '/path'`; nested `su -c '…'` + `sh -- '…'` breaks → false **NOT A GIT REPOSITORY** |
| Force b64 | all `GitGuestCommands.*`, complex scripts, quoted paths |
| Tools | bare `/tmp/launch_tool.sh TOOL` → helper `exec`; with cwd → TTY-safe `b64` |

### Compat wrappers (optional)

| Old | Forwards to |
|-----|-------------|
| `run_debian13_root.sh …` | `nativecode_chroot.sh sh --user root -- '…'` |
| `enter_debian13.sh` | `login --user flux` |
| `enter_debian13_root.sh` | `login --user root` |

---

## 3. Mount policy (SSOT)

Order (skip if target already in `/proc/mounts`, except devpts heal):

1. Soft remount `/data` `dev,suid` (`/system/bin/mount` then busybox)  
2. bind `/dev`, `/sys`  
3. `proc` on `$CH/proc`  
4. **`ensure_devpts`** on `$CH/dev/pts` (see below)  
5. `tmpfs` 512M on `$CH/dev/shm`  
6. Sticky `$CH/tmp`: if wrong mount → umount once; `chmod 1777`  
7. bind `NC_HOST_TMP` → `$CH/mnt/host-tmp`  
8. bind `/sdcard` → `$CH/sdcard`  
9. copy `launch_tool.sh` host-tmp → guest `/tmp` if present  
10. `--x11` only: bind `$PREFIX/tmp/.X11-unix` → `$CH/tmp/.X11-unix`

**Never** bind host tmp onto guest `/tmp` (breaks apt/`_apt` mkstemp).

### devpts (v2.2)

| Rule | Detail |
|------|--------|
| Target opts | `newinstance,ptmxmode=0666,mode=0620` (fallback without `newinstance`) |
| Heal | if `$CH/dev/pts/ptmx` mode is `0`/`000` (or missing) → **one** umount + remount with opts |
| Do not use `test -w` as root | root often “passes” on mode `000` — check numeric mode (`stat -c '%a'`) |
| Host `/dev/ptmx` | usually OK via `/dev` bind (`crw-rw-rw-`); still heal guest `pts/ptmx` for apps that open it |

---

## 4. Guest user entry (proven)

### What works on device (2026-07-31)

```bash
# Non-interactive flux (SAFE pattern — do this, not nested runner)
busybox chroot /data/local/tmp/chrootDebian13 \
  /bin/su - flux -c 'whoami; id; pwd; echo HOME=$HOME'
# → flux, uid=1000, /home/flux, HOME=/home/flux, SHELL=/bin/zsh, exit 0
```

```bash
# Non-interactive root via current runner (ONE shot only today)
/data/local/tmp/run_debian13_root.sh /bin/bash --noprofile --norc -c whoami
# → root, exit 0
```

### What is broken / forbidden

| Pattern | Why |
|---------|-----|
| `run_debian13_root.sh /bin/su - flux -c 'a b c'` | Nested `su - root -c` + `CMD="$@"` → flag parse bugs / hangs |
| Loop remount/bind | Mount table stack (4 → 16 → 33); device crash history |
| Interactive `enter_*` without PTY | Hang risk under adb |
| Parallel mounters | Same |

SSOT `sh --user flux` must use **one** `chroot` + **one** `su - flux`, after `ensure_mounts` (not runner-wrapped again).

---

## 5. Kotlin integration map

### 5.1 Hub (must change)

| File | Today | After SSOT |
|------|--------|------------|
| `ChrootCommandBuilder.kt` | mountCmds + enter/run + busybox fallbacks | thin: `shellRootCommand("…/nativecode_chroot.sh …")` + WINCH + `ensureHelper` + launch_tool |
| `RootShellService.kt` | `chrootMountPrep` + inline chroot in execute/captureInChroot | `… b64 --user U -- $b64` or `sh` |
| `LinuxCommandBuilder.kt` | delegates method | **no API change** — still routes chroot → CCB |
| `ShellCommandRunner.kt` | runs argv | **no change** |

### 5.2 Auto-fixed if hub fixed (method=chroot)

These only call `LinuxCommandBuilder.build` / CCB — **no direct busybox**:

| Component | Role |
|-----------|------|
| `MainActivity` terminal / workspace sessions | interactive + tools |
| `GitRepoService` / `ProjectManager` | git / mkdir |
| `AptInventoryService` | dpkg query |
| `PackageInstallRunner` (flux shell path) | install scripts as flux |
| `PackageInstallRunner.buildRootExec` | `ChrootCommandBuilder.build(…, root)` |
| `CliAuthService` | guest shell build |
| `GitHubCliService` (LCB path) | some guest cmds |

### 5.3 Explicit RootShell chroot callers (hub change fixes)

| File | API |
|------|-----|
| `OnboardingActivity.copyAndRunInChroot` | `executeInChroot` |
| `CliToolsInstaller` | `executeInChroot` |
| `GitHubCliService` | `captureInChroot` ×3, `copyIntoChroot` ×3 |

`copyIntoChroot` = **host root file copy into tree** (not guest shell). Keep as host-root `cp`/`chown` — **not** required to go through chroot helper (no mount needed if writing into unmounted tree paths under `$CHROOT/...`). Optional later: leave as-is.

### 5.4 MainActivity special case

| Path | Today | After |
|------|--------|-------|
| `runScriptInTerminal(…, "chroot_guest")` | stage + `run_debian13_root` **or** inline mounts | stage + `nativecode_chroot.sh sh --user root -- 'bash /tmp/…'` via `shellRootCommand` |

### 5.5 Path-only (no shell entry — keep)

| File | Use |
|------|-----|
| `ProjectPathResolver` | `CHROOT_PATH`, installed check |
| `ChrootSizeManager` / `chroot_size.sh` | size |
| `ChrootProcessManager` / `chroot_processes.sh` | list/kill |
| Prefs / UI method switches | `linux_method` |

### 5.6 Setup / uninstall / GUI scripts

| File | Change |
|------|--------|
| `setup_debian13_chroot.sh` | install asset helper; stop generating 3 mount clones (or 1-line wrappers) |
| `uninstall_debian13_chroot.sh` | remove `nativecode_chroot.sh` from `LAUNCH_SCRIPTS` |
| `start_debian13_gui.sh` | `mount --x11` then guest via helper (or mount-only from helper) |
| `stop_debian13_gui.sh` | optional `sh --user root` for killall |
| `start_gui_chroot.sh` | host Pulse/X11 only; still launches root GUI stage |

### 5.7 Out of scope for v1 helper

- Internal setup `$BB chroot` during first install (bootstrap before helper exists) — OK to keep raw chroot in setup **until** helper is staged mid-script  
- Proot entire tree  
- `DirectoryScanner` (host paths only)

---

## 6. Wire diagram (target)

```text
App / services
  │
  ├─ LinuxCommandBuilder ──► ChrootCommandBuilder.build
  │                              │
  │                              ├─ ensureLauncherScript (host)
  │                              ├─ ensureHelperScript → stage asset
  │                              └─ RootShell.shellRootCommand(
  │                                   "exec /data/local/tmp/nativecode_chroot.sh …")
  │                                      │
  │                                      └─ /system/bin/sh -c 'WINCH; su -c helper…'
  │
  ├─ RootShell.executeInChroot / captureInChroot
  │       └─ shellRoot path: helper b64|sh
  │
  ├─ MainActivity chroot_guest
  │       └─ helper sh --user root
  │
  └─ GUI start_debian13_gui
          └─ helper mount --x11 + sh/login flux

nativecode_chroot.sh
  ensure_mounts (idempotent) + ensure_devpts heal
  guest_chroot_env → env -i + Debian PATH + chroot
  login [--workdir] | exec | b64→tempfile→bash (TTY-safe)
  root: bash/zsh; flux: su - $user  (single layer)
```

---

## 7. Kotlin thin builder (v2.2 shape)

```kotlin
// ChrootCommandBuilder — live shape (abbrev)
const val CHROOT_HELPER = "/data/local/tmp/nativecode_chroot.sh"
const val CHROOT_HELPER_VERSION = "nativecode-chroot v2.2"

fun build(ctx, shellCmd, user): Pair<Array<String>, HashMap<…>> {
  ensureLauncherScript(ctx)
  ensureHelperScript(ctx) // restage if stamp mismatch

  val workdir = parseInteractiveWorkdir(shellCmd) // mkdir&&cd&&exec zsh
  val tool = parseToolExec(shellCmd)               // launch_tool.sh …
  val rootInner = when {
    interactive || workdir != null ->
      "exec $CHROOT_HELPER login --user $u --shell …" +
        (workdir?.let { " --workdir '$it'" } ?: "")
    tool != null && tool.dir == null ->
      "exec $CHROOT_HELPER exec --user $u -- ${tool.argv}"
    tool != null ->
      "exec $CHROOT_HELPER b64 --user $u -- ${b64("cd … && exec ${tool.argv}")}"
    isSimpleGuestCmd(shellCmd) ->  // no $ ` " ' \n \
      "exec $CHROOT_HELPER sh --user $u -- '${esc(shellCmd)}'"
    else ->
      "exec $CHROOT_HELPER b64 --user $u -- ${b64(shellCmd)}"
  }
  val winch = "trap '…WINCH…' WINCH; ${RootShell.shellRootCommand(rootInner)}"
  return arrayOf(SESSION_EXEC, "-c", winch) to outerEnv(user) // Android PATH outer only
}
```

`RootShell.executeInChroot` / `captureInChroot`: still `helper b64 --user U -- $payload` (TTY-safe decode on guest).

---

## 8. Device test log (safe)

**Device:** `Y5WWBMJVOZSK4HU8` · `2311DRK48I` · KernelSU root ADB  

### 8.1 Rules used

- Host `timeout` on every adb  
- Prefer read-only  
- At most one mount-capable command per step; stop if stacking  
- No interactive enter; no runner+flux nest  

### 8.2 Results

| ID | Test | Result |
|----|------|--------|
| T0 | `adb devices` + `id` | online, uid=0 |
| T1 | `.flux_configured`, bash, zsh, home/flux, tmp, host-tmp path | all present |
| T2 | passwd | `root:0` bash; `flux:1000:100:/home/flux:/bin/zsh` |
| T3 | `/data/local/tmp` writable | OK (helper stage) |
| T4 | sticky tmp host mode | `drwxrwxrwt` |
| T5 | `run_debian13_root.sh /bin/true` once | exit 0 |
| T6 | runner `bash -c whoami` once | `root` exit 0 |
| T7 | compound root guest (os-release, zsh, apt, git, launch_tool, /tmp, host-tmp) | all OK; Debian 13 |
| T8 | **flux login non-interactive** direct chroot `su - flux -c` | **flux / 1000 / HOME=/home/flux / zsh / FLUX_OK** |
| T9 | mount count evolution | post-reboot **4** → after light tests **16** → after one more runner **33** |
| T10 | nested `run_… /bin/su - flux -c multi-word` | broken earlier (`invalid option`); **do not retest** |

### 8.3 Crash incident

Agent multi-runner + nested su → hard crash. Documented in postmortem. **Do not regress.**

### 8.4 Safe flux probe (copy for future agents)

```bash
timeout 15 adb shell '
BB=/data/adb/ksu/bin/busybox
CH=/data/local/tmp/chrootDebian13
# only mount missing (optional)
grep -q " $CH/proc " /proc/mounts || $BB mount -t proc proc $CH/proc
grep -q " $CH/dev " /proc/mounts || $BB mount --bind /dev $CH/dev
$BB chroot $CH /bin/su - flux -c "whoami; id -u; pwd; echo HOME=\$HOME; echo FLUX_OK"
'
```

### 8.5 Safe root probe (legacy runner — once)

```bash
timeout 12 adb shell '/data/local/tmp/run_debian13_root.sh /bin/true'
```

Preferred helper probes (no legacy runner):

```bash
H=/data/local/tmp/nativecode_chroot.sh
timeout 12 adb shell "sh $H version"   # expect: nativecode-chroot v2.2
timeout 12 adb shell "sh $H sh --user root -- true"
timeout 12 adb shell "sh $H sh --user flux -- 'whoami; id -u'"
# git / b64 (alphabet payload only):
# timeout 20 adb shell "sh $H b64 --user flux -- \$B64"
# workdir login (short; use host timeout; avoid unattended interactive):
# timeout 12 adb shell -tt "printf 'pwd; exit\n' | sh $H login --user flux --shell bash --workdir /home/flux/repos/…"
```

### 8.6 v2.2 smoke (2026-07-31 / 2026-08-01)

| ID | Test | Result |
|----|------|--------|
| V0 | helper version | `nativecode-chroot v2.2` |
| V1 | flux git status via **b64** | `__STATUS__` / `__NUMSTAT__` (no `__NOGIT__`) |
| V2 | b64 under `adb -tt` | **`STDIN_TTY`** (pipe-era was always not-a-tty) |
| V3 | devpts after `mount` | `ptmxmode=666`; pts/ptmx `crw-rw-rw-` |
| V4 | `login --workdir` + `pwd` | project path + `flux` |
| V5 | `exec` + `launch_tool.sh agy --help` | help text, exit 0 |
| V6 | b64 + `launch_tool.sh grok --help` | help text, exit 0 |
| V7 | open `/dev/tty` under adb | may still ENXIO (no controlling tty in adb chain); app TerminalSession differs |
| V8 | debug APK install | **blocked** release sig on device — UI matrix manual after signed install |

---

## 9. Implementation checklist

- [x] Add `assets/scripts/chroot/nativecode_chroot.sh` (idempotent mounts + modes)  
- [x] `ChrootCommandBuilder`: helper constants, `ensureHelperScript`, thin `build`  
- [x] `RootShell`: `executeInChroot` / `captureInChroot` → helper; drop `chrootMountPrep`  
- [x] `MainActivity` chroot_guest → helper  
- [x] setup install helper + uninstall list  
- [x] GUI optional `mount --x11`  
- [x] **v2.1:** guest `env -i` + Debian PATH + absolute base64  
- [x] **v2.2:** TTY-safe `guest_b64`; `login --workdir`; devpts heal; Kotlin `'` / workdir / tool exec  
- [x] compileDebugKotlin (v2.2)  
- [x] Light ADB smoke V0–V6  
- [x] Confirm **zero** proot asset diffs for this work  
- [ ] Manual UI M1–M10 after **signed** APK (git DIFF, flux project shell, AI TUIs)  
- [ ] Mark **shipped** after UI pass  

---

## 10. Readiness answer

| Question | Answer |
|----------|--------|
| Enough info to build component? | **Yes** |
| All wire points mapped? | **Yes** (§5) |
| Guest env contract documented? | **Yes** (§2 v2.2) |
| Safe patterns proven on device? | **Yes** (helper V0–V6) |
| Code in tree? | **Yes** (v2.2 helper + CCB; may be uncommitted) |
| Device smoke done? | **Helper ADB PASS**; **UI pending** signed install |

---

*SSOT chroot component design + evidence for future agents. Proot out of scope.*
