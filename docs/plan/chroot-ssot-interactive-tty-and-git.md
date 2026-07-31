# Plan: Chroot SSOT — interactive TTY, flux workspace shell, git diff

**Date:** 2026-07-31  
**Status:** **IMPLEMENTED v2.2 (helper + Kotlin)** — device helper smoke **PASS**; full UI needs user (signed APK install failed debug vs release)  
**Parent:** [`chroot-ssot-guest-env-fix.md`](chroot-ssot-guest-env-fix.md) (v2.1 PATH/`env -i` — **partial**; SoftMgr/root PATH class fixed)  
**SSOT map:** [`docs/environment/nativecode-chroot-ssot.md`](../environment/nativecode-chroot-ssot.md)  
**ADB safety:** [`docs/environment/adb-shell-access.md`](../environment/adb-shell-access.md) · [`chroot-adb-device-crash-postmortem.md`](../environment/chroot-adb-device-crash-postmortem.md)  
**Out of scope:** proot code/assets (working). No marketplace catalog. No git UI redesign.  
**Device:** KernelSU `Y5WWBMJVOZSK4HU8` — light ADB only (host `timeout`, no mount storm, no interactive login spam).

---

## 0. User report (this pass) vs prior plan

| # | UI symptom (chroot) | Prior plan (v2.1) | This pass finding |
|---|---------------------|-------------------|-------------------|
| U1 | Project **GIT DIFF** still **NOT A GIT REPOSITORY** | S6 “guest PATH” — claimed fixed by env -i | **Wrong mechanism for remaining fail.** Guest git works on device; **app simple-`sh` path + nested quotes** breaks status bundle |
| U2 | **Debian Shell Rooted** OK in project workspace | S5 PATH | **Confirmed OK** (interactive `login --user root`) |
| U3 | **Debian Shell flux** → *process completed / press enter* only | S4 env/login | **Not PATH.** Workspace uses non-login cmd → **`guest_b64` steals stdin** → zsh dies |
| U4 | **OpenCode** UI still broken | S3 retest after env | Tools launch via **`/tmp/launch_tool.sh` non-interactive** → same stdin pipe; also TUI needs real TTY |
| U5 | **agy** `bubbletea: could not open /dev/tty` | (folded into S3) | **stdin not a TTY** after `… \| /bin/bash` |
| U6 | **claude-code** black / instant end | — | same TTY class |
| U7 | **grok** `No such device or address (os error 6)` = ENXIO on tty open | — | same TTY class |
| U8 | “many more” AI tools crash | — | same class |

**Verdict on v2.1 guest-env work:** **Correct for PATH/base64/dpkg/root profile.** **Incorrect to mark UI ship-ready.** Interactive + git still broken for independent reasons below.

---

## 1. Device facts (light ADB — 2026-07-31)

Policy: read-only mounts/files + **one** host-timeouted helper probe class. No interactive `login` from ADB. No remount loops.

| Probe | Result |
|-------|--------|
| Helper on device | `/data/local/tmp/nativecode_chroot.sh` **v2.1** |
| Prefs | `linux_method=chroot`, project `nativecode` → `/home/flux/repos/nativecode-ai` |
| Host tree `.git` | **exists** under chroot rootfs |
| `sh --user flux -- 'whoami; git rev-parse…'` | `flux` / `1000` / `true` |
| Same status bundle via **`b64`** | `__STATUS__` / `__NUMSTAT__` / exit 0 |
| Tool bins present | `agy`, `claude`, `grok`, `opencode` on flux PATH |
| `launch_tool.sh` | guest `/tmp` + host `usr/tmp` = **v2** |
| ADB `sh` mode `tty` / `-t 0` | **`not a tty` / `STDIN_NOT_TTY`** (expected for non-PTY adb; proves capture path ≠ interactive) |
| `/proc/mounts` devpts | `devpts … mode=600,ptmxmode=000` |
| Guest `/dev/ptmx` | `crw-rw-rw-` (from `/dev` bind) OK |
| Guest `/dev/pts/ptmx` | `c---------` (**unusable**) — secondary; prefer `/dev/ptmx` |

**Conclusion:** Device git + bins OK. Failures are **app→helper entry modes** (interactive classification, b64 stdin, simple-cmd quoting), not missing rootfs packages.

---

## 2. Root causes (code-backed)

### 2.1 BLOCKER B1 — `guest_b64` steals stdin (kills all TUI / “almost interactive” cmds)

**File:** `app/src/main/assets/scripts/chroot/nativecode_chroot.sh` ≈ L275–284

```sh
_run="{ echo $_b64 | /usr/bin/base64 -d 2>/dev/null || echo $_b64 | /bin/base64 -d; } | /bin/bash"
guest_chroot_env /bin/bash … -c "$_run"   # or su - flux -c "$_run"
```

Pipe makes **bash’s stdin = decode stream**, not the TerminalSession PTY.

| Consumer | Path | Needs stdin=TTY? | Result |
|----------|------|------------------|--------|
| SoftMgr / apt / git **capture** | ProcessBuilder, no PTY | No | OK after v2.1 PATH |
| AI tools | TerminalSession PTY → helper `sh`/`b64` → `launch_tool` → tool | **Yes** | bubbletea / ENXIO / black |
| Workspace flux `mkdir…&&exec zsh` | same | **Yes** | instant “process completed” |

`guest_sh` always re-encodes to b64 when host `base64` exists → **all** non-login cmds hit B1.

**Fix direction:** decode **without** replacing stdin:

```sh
# Preferred shape (preserve fds 0/1/2):
_payload=$( { echo "$_b64" | /usr/bin/base64 -d 2>/dev/null || echo "$_b64" | /bin/base64 -d; } )
# Then either:
#   (A) /bin/bash -c "$_payload"     # careful with quotes in payload
#   (B) write temp under guest sticky /tmp, exec bash that file, rm
#   (C) printf %s "$_payload" | … NO — that re-steals stdin
```

**Recommend (B)** for correctness with arbitrary payloads:

```sh
_tf="/tmp/.nc_b64_$$"
{ echo "$_b64" | /usr/bin/base64 -d 2>/dev/null || echo "$_b64" | /bin/base64 -d; } > "$_tf" || die "b64 decode"
# root:
guest_chroot_env /bin/bash --noprofile --norc "$_tf"
# flux:
guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash "$_tf"
# cleanup best-effort after (or trap in wrapper)
```

Note: `bash /path` (script file) keeps stdin = inherited TTY.  
**Do not** use `| bash` or `bash -s`.

Capture-only callers remain correct with (B).

---

### 2.2 BLOCKER B2 — Workspace flux shell never uses `login` mode

**Kotlin interactive test is too narrow:**

```kotlin
// ChrootCommandBuilder.build
val isInteractive =
    shellCmd == "exec zsh" || shellCmd == "/bin/bash --login" || shellCmd.isBlank()
```

**Home terminal** (`startNewTerminalSession`): `workDir = null` → `"exec zsh"` → **login** OK.

**Project workspace** (`startWorkspaceTerminalTab`):

```kotlin
val workDir = if (type == "shell-root") null else activeProjectPath
// shell-root → null → "exec zsh" → interactive → login --user root --shell bash  ✅ U2
// flux shell → project path → "mkdir -p $dir && cd $dir && exec zsh" → NOT interactive → b64  ❌ U3
// AI tools → "mkdir -p $dir && cd $dir && /tmp/launch_tool.sh $tool" → b64  ❌ U4–U8
```

**Fix direction (Kotlin + optional helper):**

1. **Expand interactive detection** OR dedicated builder modes:
   - Interactive shell: always `login` (or `exec` shell) then **cd** inside guest, not host-side `mkdir&&cd&&exec` as non-login payload.
   - Suggested guest login post-hook or login cmd:

```text
# Option A — helper flag:
login --user flux --shell zsh --workdir /home/flux/repos/foo

# Option B — pure Kotlin still "interactive" + env:
# isInteractive if shellCmd matches ^(mkdir -p .+ && cd .+ && )?exec (zsh|bash)
# and rootInner stays login; pass NC_WORKDIR for guest_login to cd
```

2. **Tool sessions** must not use capture-style `b64|bash`. After B1 fix, `sh`/`b64` with TTY-preserving decode is enough **if** TerminalSession PTY is inherited through `su -c`/`chroot`.  
   Prefer **`exec` mode** for tools:

```text
sh $HELPER exec --user flux -- /tmp/launch_tool.sh opencode
# with workdir: login/sh that cds then exec
```

   `guest_exec` already exists — wire Kotlin tool path to **`exec`** (or `sh` with fixed b64), not accidental simple/`b64` pipe.

---

### 2.3 BLOCKER B3 — Git DIFF: `isSimpleGuestCmd` allows `'`; status bundle uses simple `sh`

**Files:**

- `GitGuestCommands.statusSummaryBundle` — after expand: `cd '/path' || exit 2; if ! git …` → **has `'`**, **no `$`**
- `isInsideWorkTree` — same
- `ChrootCommandBuilder.isSimpleGuestCmd` — rejects `$` `` ` `` `"` `\n` `\` only — **not `'`**

So status summary takes:

```text
exec sh $HELPER sh --user flux -- 'cd '\''/home/flux/…'\'' || exit 2; …'
```

Then `RootShell.shellRootCommand` wraps again in `su -c '…'`. Nested `'\''` stacks break or alter the guest string → `cd` fails (**exit 2** → UI NotGit) or `git` never sees repo (**__NOGIT__**).

**ADB proof:** same logical bundle via helper **`b64`** → `__STATUS__`/`__NUMSTAT__` OK.  
**Host `.git` exists.** `hasGitCheckout` for chroot almost always **false** (app uid cannot read `/data/local/tmp/chroot…`) → guest probe is mandatory → B3 is the remaining S6.

**UI always shows same title** for any `NotGit` (ignores reason string) — confuses exit 2 vs true non-repo.

**Fix direction:**

1. **`isSimpleGuestCmd`:** also reject `'\''` (and preferably `;` if ever embedding becomes fragile — optional).  
   → forces **Kotlin-side b64** for all `GitGuestCommands.*` (and any quoted path cmd).
2. After B1, b64 capture keeps working (no TTY needed).
3. Optional UX: surface `NotGit.message` under title (“path not found” vs “no git”).
4. Optional: `hasGitCheckout` via one `RootShell` stat for chroot (perf only; not required if guest probe solid).

---

### 2.4 SECONDARY S1 — devpts `ptmxmode=000`

```text
devpts on $CH/dev/pts (mode=600,ptmxmode=000)
/dev/pts/ptmx → c---------
```

Many stacks open `/dev/ptmx` (works via `/dev` bind). Some open `/dev/pts/ptmx` or create peer pts — fail.

**Fix (helper `ensure_mounts`):**

```sh
# Prefer explicit options when mounting fresh:
mount_type_if_missing devpts devpts "$NC_CHROOT/dev/pts" "newinstance,ptmxmode=0666,mode=0620,gid=5"
# If already mounted with bad opts: one-time remount or umount+remount (careful; only if not busy)
# Ensure /dev/ptmx usable; optional symlink pts/ptmx → ../ptmx if needed
```

**Constraint:** no mount storm — fix once in helper; `is_mounted` currently **skips** remount → **stuck bad opts until umount**. Need controlled heal:

```sh
# if mounted but ptmxmode bad (optional check), umount pts once then remount with opts
```

Do this only inside `ensure_mounts` on helper invoke, not from ADB loops.

---

### 2.5 SECONDARY S2 — OpenCode mouse/ANSI after TTY works

If after B1 tools start but OpenCode still paints mouse CSI garbage: separate follow-up (termcap / `TERM` / mouse mode). **Out of this plan’s must-ship** until TTY fixed.

---

## 3. Architecture after fix (target)

```text
TerminalSession(/system/bin/sh + WINCH)
  └─ su -c
       └─ sh nativecode_chroot.sh
            ├─ login [--workdir DIR]     # interactive shells; PTY inherited
            ├─ exec -- CMD…             # AI tools via launch_tool (PTY)
            ├─ b64 / sh                 # capture + complex scripts; decode to temp; PTY preserved if present
            └─ mount                    # idempotent; heal devpts opts once
```

**Proot:** unchanged. Still `proot-distro login … -- zsh -c '…'` with real guest argv; no b64 pipe.

---

## 4. Implementation steps (approval-gated)

| Step | Work | Files | Gate |
|------|------|-------|------|
| 0 | User approves this plan | — | **stop until yes** |
| 1 | **B1** Rewrite `guest_b64` (temp script file; no stdin pipe). Keep absolute base64 + brace-less decode. Cleanup trap. | `nativecode_chroot.sh` | host unit: decode still runs payload; stdin of nested `bash script` is not the payload stream |
| 2 | **B1** Confirm `guest_sh` → b64 still OK for capture | same | — |
| 3 | **B3** `isSimpleGuestCmd` reject `'` (and `` ` `` already) | `ChrootCommandBuilder.kt` | git bundle always Kotlin-b64 |
| 4 | **B2** Interactive shell detection + workdir | `ChrootCommandBuilder.kt` + helper `login --workdir` **or** NC_WORKDIR | workspace flux uses login |
| 5 | **B2** Tool path: prefer `exec -- /tmp/launch_tool.sh $tool` (+ cd workdir) | `ChrootCommandBuilder.buildToolShellCommand` + `build()` | tools not accidental simple string |
| 6 | **S1** devpts options + one-shot heal if `ptmxmode=000` | `nativecode_chroot.sh` | light ADB ls `/dev/pts/ptmx` perms |
| 7 | Bump helper stamp **`nativecode-chroot v2.2`** + `CHROOT_HELPER_VERSION` | helper + Kotlin + SSOT doc | force restage |
| 8 | Update SSOT doc: guest entry modes, TTY contract, workdir | `nativecode-chroot-ssot.md` | — |
| 9 | Update parent guest-env plan status → **superseded partial** | `chroot-ssot-guest-env-fix.md` | — |
| 10 | `:app:compileDebugKotlin` | — | exit 0 |
| 11 | Optional APK install **only if user asks** | — | — |
| 12 | Safe ADB smoke (table §6) — max few host-timeouted one-shots | — | pass |
| 13 | Manual UI matrix §7 | — | pass |
| 14 | Commit/PR **only if user asks** | — | — |

**Version:** v2.1 = PATH/env. **v2.2** = TTY + interactive + git simple-cmd + devpts.

---

## 5. Detailed code sketches

### 5.1 `guest_b64` (TTY-safe)

```sh
guest_b64() {
  _b64="$1"
  [ -n "$_b64" ] || die "b64 requires payload"
  # Decode on host side of chroot? Prefer inside guest after env -i so paths are Debian.
  # Implementation: pass b64 into guest as argv; guest writes temp then bash file.
  _runner='_b="$1"; _f=/tmp/.nc_b64_$$; 
    { echo "$_b" | /usr/bin/base64 -d 2>/dev/null || echo "$_b" | /bin/base64 -d; } >"$_f" || exit 2;
    chmod 700 "$_f"; 
    if [ -t 0 ]; then /bin/bash --noprofile --norc "$_f"; _e=$?; else /bin/bash --noprofile --norc "$_f"; _e=$?; fi;
    rm -f "$_f"; exit $_e'
  if [ "$USER_NAME" = "root" ]; then
    guest_chroot_env /bin/bash --noprofile --norc -c "$_runner" bash "$_b64"
  else
    # su -c only gets a string — embed b64 (alphabet-safe) or use su with -- and bash -c
    guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "$_runner" -- "$_b64"
    # If su -c drops extra args, use: -c "_b='$_b64'; eval runner"
  fi
}
```

**Implementation note:** `su -c` often does **not** forward `"$@"` after `-c string`. Safer embed:

```sh
# b64 alphabet only — safe unquoted or single-quoted
guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "
  _b='$_b64'
  _f=/tmp/.nc_b64_\$\$
  { echo \$_b | /usr/bin/base64 -d 2>/dev/null || echo \$_b | /bin/base64 -d; } >\$_f || exit 2
  /bin/bash --noprofile --norc \$_f
  _e=\$?; rm -f \$_f; exit \$_e
"
```

Root path analogous without `su`.

### 5.2 Interactive + workdir

**Helper parse:** `--workdir PATH` → `LOGIN_WORKDIR`.

**guest_login flux:**

```sh
if [ -n "${LOGIN_WORKDIR:-}" ]; then
  guest_chroot_env /bin/su - "$USER_NAME" -s /bin/zsh -c "cd $(sq "$LOGIN_WORKDIR") 2>/dev/null; exec /bin/zsh -l"
else
  guest_chroot_env /bin/su - "$USER_NAME" -s /bin/zsh
fi
```

Careful: `zsh -l` after `cd` may re-read profiles; test once. Alternative: `su -` then rely on zshrc, with `export` + `cd` in `-c` before interactive:

```sh
-c 'cd /path || true; exec zsh -i'
```

**Kotlin:**

```kotlin
// shell + workDir:
"exec sh $CHROOT_HELPER login --user flux --shell zsh --workdir ${shellSingleQuote(workDir)}"
// tools:
"exec sh $CHROOT_HELPER exec --user flux -- /tmp/launch_tool.sh $tool"
// with workdir for tools: sh -c 'cd … && exec /tmp/launch_tool.sh …' via fixed b64 OR helper --workdir + exec
```

### 5.3 `isSimpleGuestCmd`

```kotlin
'$', '`', '"', '\'', '\n', '\r', '\\' -> return false
```

Forces b64 for GitGuestCommands (all use `shellQuote`).

### 5.4 Tool command builder

```kotlin
fun buildToolShellCommand(...): String {
  if (type == "shell" || type == "shell-root") {
    // Prefer sentinel recognized as interactive:
    // "LOGIN" or keep "exec zsh" for no workdir
    // with workdir: "LOGIN:$dir" parsed in build()
  }
  ensureLauncherScript(ctx)
  val tool = toolBinaryName(type)
  // Sentinel for exec mode:
  return if (workDir.isNullOrBlank()) "EXEC:/tmp/launch_tool.sh $tool"
  else "EXEC_CD:$dir:/tmp/launch_tool.sh $tool"
}
```

Or avoid new sentinels: detect in `build()` if command starts with `/tmp/launch_tool.sh` or contains `launch_tool.sh` → use helper `exec`.

---

## 6. Safe ADB verification (after implement + stage v2.2)

**Rules:** host `timeout`; ≤ few commands; no `login` interactive from agent; no remount spam.

```bash
SERIAL=Y5WWBMJVOZSK4HU8   # or from adb devices
H=/data/local/tmp/nativecode_chroot.sh

# After app open once (ensureHelper) or adb push asset:
timeout 12 adb -s "$SERIAL" shell "sh $H version"
# expect: nativecode-chroot v2.2

# B3 class — git via b64 (app path)
timeout 20 adb -s "$SERIAL" shell "sh $H b64 --user flux -- \$(printf '%s' \"cd '/home/flux/repos/nativecode-ai'||exit 2; git rev-parse --is-inside-work-tree\" | base64 | tr -d '\n')"
# expect: true

# B1 class — prove payload bash does not consume stdin as script stream:
# (optional) encode 'read x; echo got' only under user-approved PTY test — skip in agent default

# S1 class — pts permissions (read-only):
timeout 10 adb -s "$SERIAL" shell "ls -la /data/local/tmp/chrootDebian13/dev/pts/ptmx /data/local/tmp/chrootDebian13/dev/ptmx"
# expect pts/ptmx not c---------
```

**Forbidden:** loops of helper mount, nested `su` stress, parallel chroot jobs, interactive adb `login` unattended.

---

## 7. Manual UI matrix (chroot)

| ID | Action | Pass criteria |
|----|--------|----------------|
| M1 | SoftMgr REFRESH | packages > 0 (regression of v2.1) |
| M2 | Marketplace trivial op | no `base64: command not found` |
| M3 | Debian Shell **Rooted** (home + project) | prompt; `whoami` root; no instant exit |
| M4 | Debian Shell **flux** **from project workspace** | stable zsh prompt in project cwd; `pwd` under repo |
| M5 | GIT DIFF on `nativecode-ai` | not “NOT A GIT REPOSITORY”; empty → “NO CHANGES” OK |
| M6 | Launch **agy** | no bubbletea `/dev/tty` error; TUI runs |
| M7 | Launch **grok** | no os error 6; TUI or clear auth UI |
| M8 | Launch **claude-code** | not black instant-death; UI paints |
| M9 | Launch **opencode** | TUI usable (mouse junk → file follow-up only) |
| M10 | Same M3–M9 under **proot** | no regression (no code touch) |

---

## 8. File change list

| File | Action |
|------|--------|
| `app/src/main/assets/scripts/chroot/nativecode_chroot.sh` | **EDIT** — TTY-safe `guest_b64`; optional `--workdir`; devpts opts/heal; version **v2.2** |
| `app/src/main/java/.../terminal/ChrootCommandBuilder.kt` | **EDIT** — version; `isSimpleGuestCmd` + `'`; interactive/workdir; tool → exec |
| `docs/environment/nativecode-chroot-ssot.md` | **EDIT** — TTY contract + modes + v2.2 |
| `docs/plan/chroot-ssot-guest-env-fix.md` | **EDIT** — status superseded/partial |
| `docs/plan/chroot-ssot-interactive-tty-and-git.md` | **this plan** |
| proot / ProotCommandBuilder | **NO TOUCH** |

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Temp file race/leaks under `/tmp` | unique `$$`; `rm -f` always; sticky `/tmp` already ensured |
| `su -c` embedding breaks on exotic payloads | b64 alphabet only in embed path |
| devpts remount disrupts live PTYs | heal only when `ptmxmode` bad; single umount if idle |
| Tool `exec` skips zshrc PATH | `launch_tool.sh` already sets PATH/nvm — keep |
| Over-wide `isSimpleGuestCmd` always b64 | slight overhead; safer |
| ADB agent stress | hard limits + postmortem |

---

## 10. Explicit non-goals

- No proot edits  
- No Kotlin mount re-clone  
- No nested legacy `run_debian13_root` + flux  
- No OpenCode mouse deep-dive until TTY fixed  
- No git porcelain UI redesign  
- No commit/PR unless asked  

---

## 11. One-line diagnosis

**v2.1 fixed guest PATH; remaining breaks are (1) `guest_b64` piping payload into bash so TUIs lose `/dev/tty`, (2) project flux shell classified non-interactive (`mkdir&&cd&&exec zsh`), (3) git status taking “simple” `sh` with nested single-quotes so the guest probe fails despite a real `.git`.**

---

## 12. Approval gate

Reply **approve** (or approve steps 1–N) to implement.  
Default order: **B1 → B3 → B2 → S1 → version → compile → light ADB → UI**.  
No APK install / no commit unless requested.
