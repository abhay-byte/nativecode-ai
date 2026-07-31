# Plan: Fix chroot SSOT guest env (PATH / base64 / login)

**Date:** 2026-07-31  
**Status:** **ADB SMOKE PASS (v2.1)** — light device probe OK; UI M1–M7 still user-side  
 
**Scope:** Repair **chroot** paths that broke after `nativecode_chroot.sh` hub (SSOT)  
**Out of scope:** **proot** (do not touch — working). No marketplace catalog changes. No git UI redesign.  
**Safety:** Live device stress forbidden — see [`docs/environment/chroot-adb-device-crash-postmortem.md`](../environment/chroot-adb-device-crash-postmortem.md)

---

## Review pass 2 (2026-07-31) — re-check local tree

**Files:** `nativecode_chroot.sh` (v2.1), `ChrootCommandBuilder.kt` (version only), `nativecode-chroot-ssot.md` (guest env v2.1 + brace note).  
**Proot:** `git diff --name-only` → **no proot** (G6 pass).  
**Host sanity:** brace decode pipeline runs payload (`echo HELLO_OK` → `HELLO_OK`).

### Verdict (correctly done?)

| Question | Answer |
|----------|--------|
| Plan intent implemented? | **Yes** |
| Prior blocker R-B1 fixed? | **Yes** — `{ decode; } \| /bin/bash` in both root + flux |
| Version stamp consistent? | **Yes** — helper header, `VERSION_STR`, Kotlin `CHROOT_HELPER_VERSION`, SSOT doc = **`nativecode-chroot v2.1`** |
| Scope leak (proot / SoftMgr / git UI)? | **No** |
| Safe to treat as code-complete? | **Yes** |
| Safe to ship without device? | **No** — smoke M1–M7 still required |

**Overall:** **Correctly done** for code+docs. Remaining = device only.

### Checklist vs plan §3

| Item | Status | Evidence |
|------|--------|----------|
| `GUEST_PATH_ROOT` + flux bins | **OK** | `guest_path_for_user` |
| `guest_chroot_env` = chroot + `/usr/bin/env -i` | **OK** | lines ~213–217 |
| login / sh fallback / exec / b64 all use it | **OK** | guest_* wired |
| Absolute base64 paths | **OK** | `/usr/bin/base64` then `/bin/base64` |
| Decode always feeds bash (R-B1) | **OK** | `_run="{ echo $_b64 \| …; } \| /bin/bash"` |
| Kotlin only version bump | **OK** | `v1` → `v2.1` |
| Outer Android PATH kept for host su | **OK** | envMap untouched |
| SSOT doc contract | **OK** | v2.1 + brace row |
| Device M1–M7 / light ADB | **Pending** | — |

### Findings (pass 2)

| ID | Sev | Finding | Correct? |
|----|-----|---------|----------|
| R-B1 | was blocker | brace group in `guest_b64` root + flux | **Fixed correctly** |
| R2-OK1 | ok | env -i drops Android PATH — addresses S1–S6 class | **Yes** |
| R2-OK2 | ok | stamp `v2.1` forces restage after broken v2 | **Yes** |
| R2-OK3 | ok | zero proot diff | **Yes** |
| R2-N1 | nit | `guest_sh` still re-b64 via host | Acceptable after R-B1 |
| R2-N2 | nit | plan body §3.4/§6 still mention bare `v2` in places | Docs drift only; code is v2.1 |
| R2-P1 | pending | no device smoke this pass | Block ship only |

### R-B1 closed form (tree)

```sh
_run="{ echo $_b64 | /usr/bin/base64 -d 2>/dev/null || echo $_b64 | /bin/base64 -d; } | /bin/bash"
guest_chroot_env /bin/bash --noprofile --norc -c "$_run"          # root
guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "$_run"  # flux
```

### Device smoke (2026-07-31) — light ADB

**Staged:** `adb push` helper → `/data/local/tmp/nativecode_chroot.sh`  
**Forbidden patterns avoided:** no mount storm loop, no nested runner spam, host `timeout` only.

| Probe | Result |
|-------|--------|
| `version` | `nativecode-chroot v2.1` |
| root `sh`: `command -v base64`, `dpkg-query` | `/usr/bin/base64`, `/usr/bin/dpkg-query` |
| root base64 round-trip | `test` |
| root `b64` mode (R-B1) | `B64_OK` + `/usr/bin/dpkg-query` |
| flux `sh`: git / whoami / id -u | `/usr/bin/git`, `flux`, `1000` |

**ADB guest env class:** **PASS** (S1/S2/S5/S6 mechanism fixed on device).

### Remaining (user UI only)

1. UI M1–M7 chroot after app install/open (ensureHelper restages if needed).  
2. M7 proot regression glance.  
3. Mark plan + SSOT **shipped** after UI pass.

**Parent SSOT plan:** [`docs/plan/chroot-ssot-shell-runner.md`](chroot-ssot-shell-runner.md)  
**SSOT map:** [`docs/environment/nativecode-chroot-ssot.md`](../environment/nativecode-chroot-ssot.md)

**Related code (read-only research):**
- `app/src/main/assets/scripts/chroot/nativecode_chroot.sh` — `guest_login` / `guest_sh` / `guest_b64` / `guest_exec`
- `app/src/main/java/.../terminal/ChrootCommandBuilder.kt` — outer `envMap["PATH"]` Android, thin helper invoke
- `app/src/main/java/.../RootShellService.kt` — `executeInChroot` / `captureInChroot` → `b64`
- Consumers: `AptInventoryService`, `PackageInstallRunner.buildRootExec`, `GitRepoService`, `MainActivity` tool/shell sessions
- Proot **reference only** (do not edit): `nativecode_proot_fast.sh` `build_guest_env` + `env -i`

**Compile policy:** `:app:compileDebugKotlin` only unless user asks APK.  
**Device policy:** max **one** light helper probe per approval; host `timeout`; no mount storm.

---

## 0. User symptoms (device UI) → code paths

| # | UI symptom | Call chain (chroot) | Mode |
|---|------------|---------------------|------|
| S1 | Software Manager: `0 packages · chroot`, “No packages loaded. Tap REFRESH.” | `refreshSoftwareManagerPage` → `AptInventoryService.scan` → `LinuxCommandBuilder.build(…, user=root)` → `ChrootCommandBuilder` → helper **`b64` or `sh`** | non-interactive **root** |
| S2 | Marketplace uninstall/install: `plan: blender` then `/bin/bash: line 1: base64: command not found` | `PackageInstallRunner` → `buildRootExec(…, chroot)` → `ChrootCommandBuilder.build(user=root)` → helper **`b64`** (complex script) | non-interactive **root** |
| S3 | OpenCode TUI garbled mouse/ANSI in project terminal | `buildToolShellCommand(opencode)` → `ChrootCommandBuilder` → helper **`sh`→`guest_sh`→`guest_b64`** + launch_tool | flux tool session |
| S4 | Debian Shell **flux** crash / unusable | interactive `exec zsh` → helper **`login --user flux --shell zsh`** | interactive **flux** |
| S5 | Debian Shell **Rooted**: `bash: id: command not found` + `[: : integer expression expected` then `root@localhost:/#` | interactive → helper **`login --user root --shell bash`** | interactive **root** |
| S6 | Project GIT DIFF: “NOT A GIT REPOSITORY” despite init | `GitRepoService.loadDiffSummary` → guest git (after host `.git` probe often fails — app **cannot** read `/data/local/tmp/chroot…` without root) | non-interactive **flux** |

**Proot:** same UI surfaces use `ProotCommandBuilder` / `nativecode_proot_fast.sh` with **clean `env -i` + Debian PATH** — not broken. Do not change proot.

---

## 1. Root cause (research conclusion)

### 1.1 Single bug class: **Android host env leaks into guest**

`ChrootCommandBuilder.build` sets **outer** ProcessBuilder env (correct for finding `su` / system `sh`):

```text
envMap["PATH"] = "/system/bin:/system/xbin:/sbin:" + host PATH
envMap["HOME"] = /home/flux|/root   # guest-shaped, but still outer process
```

Chain:

```text
/system/bin/sh -c 'WINCH; su -c "… nativecode_chroot.sh …"'
  → helper (Android env)
    → busybox chroot $ROOT <guest binary>
      → guest bash/su **inherits Android PATH**
```

Inside chroot, `/system/bin` is **not** mounted. So:

| Command | Result |
|---------|--------|
| bare `base64` | **command not found** (S2; also kills root `b64` decode for S1) |
| bare `id` in `/etc/profile` | **command not found** → empty → `[: : integer expression expected` (S5) |
| bare `dpkg-query` / `git` | fail when PATH is Android-only (S1, S6) |

### 1.2 Why root is worse than flux

| Entry | Code today | Env hygiene |
|-------|------------|-------------|
| **root** `guest_b64` / `guest_sh` fallback | `chroot … /bin/bash --noprofile --norc -c "…"` | **no** login; **keeps Android PATH** |
| **root** `guest_login` | `chroot … /bin/bash --login` | login scripts run **with** inherited Android PATH first → `id` fails (S5) |
| **flux** `guest_b64` | `chroot … /bin/su - flux -s /bin/bash -c "…"` | `su -` *may* reset PATH (PAM/login.defs) — device-dependent |
| **flux** `guest_login` | `chroot … /bin/su - flux -s /bin/zsh` | usually better; still inherits outer env until `su` clears it |

Marketplace error is **exact** proof of root non-interactive path:

```text
/bin/bash: line 1: base64: command not found
```

That line is **`guest_b64`**:

```sh
# nativecode_chroot.sh today
exec $BB chroot "$NC_CHROOT" /bin/bash --noprofile --norc -c \
  "echo $_b64 | base64 -d | /bin/bash"
```

Decode never runs → install/uninstall script never executes → UI may still show “Uninstall complete” (exit handling UX — secondary).

### 1.3 Proot already does the right thing (reference only)

`nativecode_proot_fast.sh`:

```text
build_guest_env → PATH=/usr/local/sbin:…:/bin + flux bins
run_proot /usr/bin/env -i $ENV_ARGS …
```

Chroot SSOT **never** clears env before chroot. That is the gap.

### 1.4 Secondary issues (same file / same class)

| ID | Issue | Impact |
|----|-------|--------|
| R2 | `guest_b64` uses bare `base64` not `/usr/bin/base64` | fails even with partial PATH |
| R3 | `guest_sh` always re-encodes via **host** base64 → `guest_b64` | every `sh` mode depends on guest `base64` |
| R4 | Helper version still `nativecode-chroot v1` | devices keep stale helper until version bump or force stage |
| R5 | Host `hasGitCheckout` false for chroot (app uid cannot list `/data/local/tmp/chrootDebian13/…`) | always falls through to guest probe — so guest PATH fix is mandatory for S6 |
| R6 | OpenCode mouse CSI (`^[[<0;…M`) | may be TERM/mouse after env fix; re-check; only fix if still broken (WINCH / `TERM` / `COLORTERM`) |

### 1.5 Not the cause (already fixed / out of scope)

| Claim | Status |
|-------|--------|
| Helper never staged | Prior review fixed `ensureHelper` + onboarding deploy + bootstrap in `buildChrootHelperCmd` |
| `exec $HELPER` without `sh` | Prior review: `exec sh $HELPER` |
| Proot broken | User + research: proot OK — **do not touch** |
| Mounts missing | Sessions enter and print guest prompts; mounts OK enough for login |

---

## 2. Goals

| # | Goal |
|---|------|
| G1 | **All** guest entry modes set a **Debian PATH** (and safe HOME/USER/TERM) before any guest binary runs |
| G2 | `guest_b64` decode works as **root** without relying on host PATH (absolute `base64` + PATH) |
| G3 | Software Manager chroot scan returns packages; marketplace install/uninstall run real scripts |
| G4 | Rooted + flux interactive login: no `id: command not found` / empty `[:` from profile |
| G5 | Project git diff works via guest probe under chroot method |
| G6 | Proot code paths **unchanged** |
| G7 | Bump helper version stamp so `ensureHelperScript` redeploys automatically |

---

## 3. Fix design (SSOT helper only + tiny Kotlin if needed)

### 3.1 Primary: `nativecode_chroot.sh` guest env SSOT

Add shared helpers (mirror proot intent, chroot-shaped):

```sh
# Canonical Debian PATH inside rootfs (no /system)
GUEST_PATH_ROOT="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

guest_home_for_user() {
  case "$USER_NAME" in root) echo /root ;; *) echo /home/flux ;; esac
}

# Env block applied at chroot boundary (login + non-interactive).
# Prefer: busybox env -i … chroot …  OR  export then chroot with clean PATH.
apply_guest_env_exports() {
  export PATH="$GUEST_PATH_ROOT"
  # flux interactive/tools: prepend user bins (match launch_tool / proot)
  if [ "$USER_NAME" != "root" ]; then
    export PATH="/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo/bin:/opt/nodejs/bin:$PATH"
    export HOME=/home/flux USER=flux LOGNAME=flux
    export NVM_DIR=/home/flux/.nvm
  else
    export HOME=/root USER=root LOGNAME=root
  fi
  export TERM="${TERM:-xterm-256color}"
  export LANG="${LANG:-en_US.UTF-8}"
  export LC_ALL="${LC_ALL:-en_US.UTF-8}"
  export TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp
  export DEBIAN_FRONTEND=noninteractive
  # Drop host Android pollution that breaks guest dynamic linker / tools
  unset LD_LIBRARY_PATH LD_PRELOAD ANDROID_ROOT ANDROID_DATA 2>/dev/null || true
}
```

**Preferred entry pattern** (robust):

```sh
# root non-interactive example
apply_guest_env_exports
exec $BB chroot "$NC_CHROOT" /usr/bin/env -i \
  PATH="$PATH" HOME="$HOME" USER="$USER" LOGNAME="$LOGNAME" \
  TERM="$TERM" LANG="$LANG" LC_ALL="$LC_ALL" \
  TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive \
  /bin/bash --noprofile --norc -c '…'
```

If guest `/usr/bin/env` missing (should not on Debian 13), fall back to `export` + chroot only.

### 3.2 Fix `guest_b64` (blocker for S1/S2)

```sh
guest_b64() {
  _b64="$1"
  [ -n "$_b64" ] || die "b64 requires payload"
  apply_guest_env_exports
  # Absolute decode — never depend on PATH for bootstrap
  _dec='echo '"$_b64"' | /usr/bin/base64 -d 2>/dev/null || echo '"$_b64"' | /bin/base64 -d'
  if [ "$USER_NAME" = "root" ]; then
    exec $BB chroot "$NC_CHROOT" /usr/bin/env -i \
      PATH="$GUEST_PATH_ROOT" HOME=/root USER=root LOGNAME=root \
      TERM="${TERM:-xterm-256color}" LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 \
      TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive \
      /bin/bash --noprofile --norc -c "$_dec | /bin/bash"
  else
    # su - still OK; env -i on outer of su is wrong — set PATH for su child via env
    exec $BB chroot "$NC_CHROOT" /usr/bin/env -i \
      PATH="/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo/bin:/opt/nodejs/bin:$GUEST_PATH_ROOT" \
      HOME=/home/flux USER=flux LOGNAME=flux NVM_DIR=/home/flux/.nvm \
      TERM="${TERM:-xterm-256color}" LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 \
      TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive \
      /bin/su - flux -s /bin/bash -c "$_dec | /bin/bash"
  fi
}
```

**Note:** payload is base64 alphabet only — safe to embed unquoted in the outer -c string. Keep as today.

Optional hardening: if `/usr/bin/base64` missing, decode on **host** with toybox/busybox and pass via temp file under sticky `/tmp` (only if absolute guest base64 fails on a device).

### 3.3 Fix `guest_sh` / `guest_exec` / `guest_login`

| Mode | Change |
|------|--------|
| `guest_sh` | `apply_guest_env` + env -i before chroot; prefer **direct** `-c "$_cmd"` when already simple **or** keep host-b64→guest_b64 **after** b64 fix (either OK; prefer one path) |
| `guest_exec` | env -i + chroot argv; flux: `su -c 'exec …'` with guest PATH |
| `guest_login` root | env -i + `bash -l` or `su - root` so profile sees Debian PATH **before** first `id` |
| `guest_login` flux | env -i minimal + `su - flux -s zsh` (or pass PATH in env that `su -` preserves — verify; if `su -` wipes, rely on login.defs which is fine **if** no Android PATH left for pre-su scripts) |

**Critical for S5:** root login must **not** enter bash with `PATH=/system/bin:…`.

Recommended root login:

```sh
exec $BB chroot "$NC_CHROOT" /usr/bin/env -i \
  PATH="$GUEST_PATH_ROOT" HOME=/root USER=root LOGNAME=root \
  TERM=… LANG=… TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp \
  /bin/bash --login
```

### 3.4 Version stamp (mandatory)

```text
# nativecode-chroot v2.1
VERSION_STR="nativecode-chroot v2.1"
```

Kotlin:

```text
ChrootCommandBuilder.CHROOT_HELPER_VERSION = "nativecode-chroot v2.1"
```

Forces `ensureHelperScript` to restage on next chroot session (v2.1 > any stale v1/v2).

### 3.5 Kotlin changes (minimal)

| File | Change | Why |
|------|--------|-----|
| `ChrootCommandBuilder.kt` | bump `CHROOT_HELPER_VERSION` only | restage |
| `ChrootCommandBuilder.kt` | **optional:** keep outer Android PATH (required for `su`) — **do not** put guest PATH on outer process | outer is Android |
| `nativecode-chroot-ssot.md` | note guest env contract | docs |
| **Proot / ProotCommandBuilder** | **none** | G6 |

No consumer changes required if helper is correct: Apt / PackageInstall / Git / shells already call hub.

### 3.6 Optional follow-ups (not blockers)

| Item | When |
|------|------|
| Marketplace “Uninstall complete” on failed exit | if still lies after b64 fix |
| OpenCode mouse CSI | only if TUI still broken after PATH fix |
| Host `hasGitCheckout` via `RootShell` for chroot | perf/UX; guest probe sufficient after fix |
| `guest_sh` skip re-b64 when Kotlin already chose `sh` simple | less work; not required if b64 solid |

---

## 4. Failure → fix mapping

| Symptom | Mechanism | Fix |
|---------|-----------|-----|
| S1 empty packages | root `b64`/`sh` cannot run `dpkg-query` (PATH / base64) | §3.1–3.2 |
| S2 base64 not found | `guest_b64` bare `base64` under Android PATH | absolute `/usr/bin/base64` + env -i |
| S3 OpenCode garble | often bad env / partial start; retest after §3 | env + TERM; then mouse if needed |
| S4 flux shell crash | login/env/PATH or zsh rc under bad PATH | `guest_login` flux + clean env |
| S5 root `id` / `[:` | `/etc/profile` runs `id` on Android PATH | root login env -i Debian PATH |
| S6 not a git repo | guest git probe fails (or host probe always false) | flux non-interactive PATH + b64 |

---

## 5. Implementation steps

| Step | Work | Gate |
|------|------|------|
| 0 | User approves this plan | approval |
| 1 | Edit `nativecode_chroot.sh`: `apply_guest_env` / env -i / absolute base64 / login | code review |
| 2 | Bump helper + Kotlin version to **v2.1** (after R-B1) | compile |
| 3 | Update `docs/environment/nativecode-chroot-ssot.md` guest env contract (short) | docs |
| 4 | `:app:compileDebugKotlin` | exit 0 |
| 5 | Safe device smoke (one-shot, approved) | §6 |
| 6 | Manual UI: S1–S6 on chroot only | checklist |
| 7 | **Do not** modify proot assets/builders | verify `git diff` proot clean |

---

## 6. Verification

### 6.1 Safe ADB (max one light run per approval)

```text
# After app opens one chroot session (stages v2 helper):
timeout 15 adb shell 'sh /data/local/tmp/nativecode_chroot.sh version'
# expect: nativecode-chroot v2.1

timeout 20 adb shell 'sh /data/local/tmp/nativecode_chroot.sh sh --user root -- "command -v base64; command -v dpkg-query; dpkg-query -W -f=\${Package} coreutils 2>/dev/null | head -1"'
# expect: /usr/bin/base64, /usr/bin/dpkg-query, coreutils (or similar)

timeout 20 adb shell 'sh /data/local/tmp/nativecode_chroot.sh sh --user flux -- "command -v git; whoami; id -u"'
# expect: git path, flux, 1000

timeout 15 adb shell 'sh /data/local/tmp/nativecode_chroot.sh sh --user root -- "echo test | /usr/bin/base64 | /usr/bin/base64 -d"'
# expect: test
```

**Forbidden:** mount loops, nested su stress, multi-minute adb shell spam.

### 6.2 App UI checklist (chroot method)

| ID | Action | Pass |
|----|--------|------|
| M1 | Software Manager → chroot → REFRESH | package count > 0 |
| M2 | Marketplace uninstall trivial / reinstall known pkg | no `base64: command not found`; script body runs |
| M3 | Debian Shell Rooted | no `id: command not found`; `echo $PATH` contains `/usr/bin` |
| M4 | Debian Shell flux | stable prompt; `whoami` → flux |
| M5 | Project with `.git` → GIT DIFF | not empty “NOT A GIT REPOSITORY” |
| M6 | OpenCode from project terminal | TUI usable; if mouse junk remains → file follow-up only |
| M7 | Same flows under **proot** | still work (regression guard; no code change) |

---

## 7. File change list

| File | Action |
|------|--------|
| `app/src/main/assets/scripts/chroot/nativecode_chroot.sh` | **EDIT** — guest env -i / PATH / absolute base64 / login |
| `app/src/main/java/.../terminal/ChrootCommandBuilder.kt` | **EDIT** — `CHROOT_HELPER_VERSION` → v2 |
| `docs/environment/nativecode-chroot-ssot.md` | **EDIT** — guest env contract + v2 note |
| `docs/plan/chroot-ssot-guest-env-fix.md` | **this plan** |
| proot scripts / `ProotCommandBuilder.kt` | **NO TOUCH** |

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| `env -i` drops needed GPU vars for GUI | GUI path uses `start_debian13_gui.sh` which already exports DISPLAY/PULSE; login for terminal does not need virgl; add optional NC_* later if GUI login regresses |
| `su -` after `env -i` still odd on some KSU | absolute base64 + PATH inside -c string as belt-and-suspenders |
| Stale helper on device | version bump v2 + ensureHelper |
| Agent over-tests ADB | hard limit one probe; host timeout |

---

## 9. Explicit non-goals

- No proot edits  
- No re-introduction of Kotlin mount clones  
- No nested `run_debian13_root` + flux  
- No broad marketplace/git feature work beyond SSOT env correctness  
- No commit/PR unless user asks  

---

## 10. One-line diagnosis

**SSOT hub mounts correctly but enters the guest with the Android process PATH; root non-interactive `guest_b64` then cannot find `base64`/`dpkg-query`/`id`, which breaks Software Manager, marketplace, rooted shell profile, and (via failed guest probes) git diff — proot is fine because it already uses `env -i` + Debian PATH.**

---

## 11. Approval gate

Implement only after user approval. Order: helper env fix → version v2 → compile → optional one-shot ADB → UI M1–M7.
