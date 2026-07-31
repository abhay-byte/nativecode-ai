# Postmortem: Device crash during chroot ADB research (2026-07-31)

**Status:** HARD RULE for all agents  
**Device:** KernelSU phone (`Y5WWBMJVOZSK4HU8`)  
**Outcome:** Device hard-crashed / needed reboot. User: “u killed device.”  
**Scope of this doc:** Why it died + **forbidden** ADB/chroot patterns so it never happens again.

---

## 1. What happened

While researching chroot shell SSOT (single runner), an agent ran multi-step ADB shells that:

1. Invoked `/data/local/tmp/run_debian13_root.sh` repeatedly (each run re-does **all** bind mounts).
2. Nested guest `su` chains: host root → `busybox chroot` → `su - root -c` → `su - flux -c …`.
3. Stacked several of those in **one** long `adb shell '…; …; …'` without reliable per-step timeouts.
4. Some steps hung on interactive/`su -` login paths; agent retried / left background jobs; ADB later went `device not found` and the phone crashed.

**This was agent test abuse — not a normal app-user path.** App sessions mount once and run one terminal; they do not spam remount/bind + nested `su` in a loop from ADB.

---

## 2. Why the device died (root causes)

### 2.1 Mount storm (primary suspect)

`run_debian13_root.sh` (and clones in `enter_debian13*.sh`, setup, GUI scripts) on **every** invocation:

- remount `/data` (`dev,suid`)
- bind `/dev`, `/sys`, `proc`, `devpts`
- mount **new** `tmpfs` on `…/dev/shm` (512M)
- umount/rebind sticky `/tmp` policy
- bind host tmp → `mnt/host-tmp`
- bind `/sdcard`

Device `/proc/mounts` already showed **duplicate** chroot binds after a few runner calls (same mount points stacked many times). On Android/KSU this can:

- exhaust mount table / kernel resources  
- thrash FUSE (`/sdcard`) and dm-crypt/f2fs under `/data`  
- hang `umount` / `mount` in uninterruptible state  
- freeze system_server / vold / surfaceflinger → **hard reboot or black screen**

### 2.2 Nested `su` inside chroot (secondary)

Broken / dangerous patterns observed:

```text
run_debian13_root.sh /bin/su - flux -c 'whoami; id; …'
```

Runner always does:

```sh
busybox chroot $DEBIANPATH /bin/su - root -c "$CMD"
```

So the above becomes:

```text
chroot → su - root -c "/bin/su - flux -c whoami; id; …"
```

Problems:

| Issue | Effect |
|--------|--------|
| `CMD="$@"` then `su -c "$CMD"` | Multi-word `-c` payloads split; `su` sees bogus flags (`invalid option -- 'x'`, `-- 'v'`) |
| Nested login `su -` | Can block on PAM/tty/session; no PTY → hang |
| Hang under ADB | Agent timeouts leave orphans; remounts pile up |
| `uid=0` + `whoami=flux` weirdness | Confirms broken user switch / quote path — not trustworthy identity |

Nested interactive-style `su` **inside** chroot from ADB is a freeze risk, not a valid probe.

### 2.3 No hard per-command budget

Several agent invocations:

- used one giant multi-echo `adb shell`  
- mixed hanging probes with mount-heavy runner  
- backgrounded past default timeout while mounts kept stacking  

Even `timeout 5` is useless if **`timeout` is missing** on the Android shell, or if the hang is in unkillable `D` state on mount.

### 2.4 What was **not** the cause

- **Not** proot (untouched; do not “fix” by also thrashing proot).  
- **Not** a single normal `true` / `whoami` as root.  
- **Not** reading files under `/data/local/tmp/chrootDebian13` without chroot.  
- App `TerminalSession` alone did not cause this crash batch (crash window = agent ADB research).

---

## 3. Forbidden forever (agents + humans)

**DO NOT** from ADB (or scripts) unless user explicitly approves a controlled bench and serial is disposable:

1. **Loop** `run_debian13_root.sh` / `enter_debian13*.sh` / full mount prep more than **once** per session without user OK.  
2. **Nested** guest su:  
   `run_… /bin/su - … -c '… su - …'` or double `su -` inside one chroot.  
3. **Interactive** enter scripts without a real PTY + user watching:  
   `enter_debian13.sh`, `enter_debian13_root.sh`, `su - flux` with no `-c`.  
4. **Parallel** adb shells all mounting the same chroot.  
5. **Long** compound scripts that remount on every step.  
6. **Kill -9** random root processes / `chroot_processes.sh kill` during research (separate footgun).  
7. **Remount** `/data` repeatedly in a loop.  
8. **Stress** `/sdcard` bind + FUSE while thrashing mounts.  
9. Background hung chroot jobs and “retry” without checking `adb devices` + device health first.

If device disconnects or freezes mid-test: **STOP**. Do not kill-server spam, do not re-fire mount tests. Wait for user.

---

## 4. Safe ADB rules (mandatory)

### 4.1 Preflight only (always OK if device present)

```bash
adb devices
adb shell 'id; echo ok'          # one line, no chroot
# optional read-only presence (NO chroot, NO mount):
adb shell 'ls /data/local/tmp/chrootDebian13/.flux_configured 2>/dev/null; ls /data/local/tmp/run_debian13_root.sh 2>/dev/null'
```

### 4.2 Single light chroot probe (max 1 per conversation unless user asks more)

Prefer **one** of:

```bash
# Preferred: one runner call, root, trivial command, host-side timeout
timeout 10 adb shell '/data/local/tmp/run_debian13_root.sh /bin/true'
# or identity once:
timeout 10 adb shell '/data/local/tmp/run_debian13_root.sh /bin/bash --noprofile --norc -c whoami'
```

Rules:

- **Host** `timeout` (PC), not only device `timeout`.  
- **One** mount-capable command, then stop.  
- Prefer `bash -c 'single_simple_cmd'` as **root** via runner — **not** nested `su - flux`.  
- Capture output; do not chain 8 more runner calls.  
- If exit non-zero or hang: **abort**, report, wait for user.

### 4.3 Flux user identity without nested su bomb

Prefer host-visible passwd / files:

```bash
adb shell 'grep ^flux: /data/local/tmp/chrootDebian13/etc/passwd'
```

For real flux guest exec, use **one** carefully quoted path (future SSOT helper only), never invent nested `su` from ADB in a loop.

### 4.4 After any crash / reboot

1. User confirms device healthy.  
2. Preflight only (`adb devices`, `id`).  
3. **No** chroot mount tests until user says “ready for light smoke.”  
4. Never “catch up” by replaying the failed multi-step script.

---

## 5. Code facts that misled the agent

| Fact | Risk if misused on device |
|------|---------------------------|
| Mount policy copied in **5+ places** (setup, `run_debian13_root.sh`, `enter_*`, `ChrootCommandBuilder`, `RootShell.chrootMountPrep`, GUI) | Easy to re-test “each path” = mount storm |
| Runner always `su - root -c "$CMD"` with weak quoting | Nested flux `-c` looks “like app” but is broken + hang-prone |
| `RootShell.executeInChroot` base64-pipes bash | OK for **app** one-shot; from ADB still does full mount prep each call |
| First tests already showed **duplicate mounts** in `/proc/mounts` | Should have stopped; instead more probes ran |

**Product fix (separate plan):** one SSOT `nativecode_chroot.sh` with **idempotent** mounts (check `/proc/mounts` before bind), argv-safe `exec`/`sh`/`login`, no quote-breaking `CMD="$@"`. That reduces crash risk of **app** paths; it does **not** allow agents to spam it from ADB.

---

## 6. Agent checklist (copy into future chroot work)

- [ ] Read this postmortem first  
- [ ] No device = no ADB  
- [ ] Device just rebooted / crashed → preflight only until user OK  
- [ ] Max **one** chroot/runner invocation per research turn unless user asks  
- [ ] Never nested `su` inside chroot from ADB  
- [ ] Never loop remount/bind  
- [ ] Prefer file reads under rootfs over live chroot  
- [ ] Proot: leave alone unless task is proot  
- [ ] If ADB dies or shell hangs >10s host timeout → **stop**, tell user, no retries  

---

## 7. Related paths

| Item | Path |
|------|------|
| Rootfs | `/data/local/tmp/chrootDebian13` |
| Current runners | `/data/local/tmp/run_debian13_root.sh`, `enter_debian13.sh`, `enter_debian13_root.sh` |
| Setup generator | `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh` |
| App builder | `ChrootCommandBuilder.kt` |
| Root API | `RootShellService.kt` (`executeInChroot`, `chrootMountPrep`) |
| Safe access notes | `docs/environment/adb-shell-access.md` |

Update `adb-shell-access.md` consumers: treat **inspection** as default; treat **live chroot** as rare and single-shot.

---

## 8. Apology / ownership

Crash caused by **over-aggressive agent ADB chroot testing**, not by user action.  

**Never again:** mount storms, nested guest `su`, long multi-runner ADB scripts, or retries after disconnect.

---

*Written 2026-07-31 after device crash during chroot SSOT research.*
