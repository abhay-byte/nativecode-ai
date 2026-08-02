# Proot fast-launcher design (for review)

**Status:** design only — **not implemented** until approved  
**Date:** 2026-07-29  
**Device measured:** MediaTek MT6897 / POCO duchamp (`192.168.1.52:43055`)  
**Scope of this note:** Task 1 inventory + Task 2 design. No rootfs rewrite, no app APK change yet.  
**Safety:** proot binary restored after argv dump; no GPU/DRM tests.

Related: [`proot-vs-chroot-perf-report-mediatek.md`](./proot-vs-chroot-perf-report-mediatek.md), Snapdragon report, research brief in session.

---

## 1. Current command-line inventory

### 1.1 App / helper entry points

| Path | What it builds |
|------|----------------|
| **App** `ProotCommandBuilder.kt` | `python proot-distro login debian --shared-tmp --user flux` **or** `… -- zsh -c "$shellCmd"` |
| **ADB helper** `/data/local/tmp/nativecode_proot.sh` | sources `fluxlinux-host.env`, then: |
| · `login` | `proot-distro login debian --user flux --shared-tmp` |
| · `root` | `proot-distro login debian --shared-tmp` |
| · `cmd` | `proot-distro login debian --user flux --shared-tmp -- bash -lc "$CMD"` |
| · `rootcmd` | same as root + `bash -lc` |

**Problem in `cmd`:** proot-distro already wraps the login user’s shell as  
`[login_shell, "-c", shlex.join(login_cmd)]`.  
With flux shell = **`/bin/zsh`**, a `cmd true` becomes:

```text
proot … /bin/zsh -c "bash -lc true"
```

→ **double shell** (zsh + bash -l) on every one-shot command.

### 1.2 Live proot argv (MediaTek, 2026-07-29)

Captured by temporary `/system/bin/sh` wrapper around `$PREFIX/bin/proot` (restored after).

**Flags (always, non-minimal):**

| Arg | Role |
|-----|------|
| `--kill-on-exit` | kill guest tree on session end |
| `--link2symlink` | Android symlink compatibility |
| `--sysvipc` | SYSV IPC emulation |
| `--kernel-release=…PRoot-Distro…` | fake uname |
| `-L` | lstat fix for dpkg |
| `--change-id=10416:10416` | flux UID (proot rootfs maps flux→10416) |
| `--rootfs=…/containers/debian/rootfs` | guest tree |
| `--cwd=/home/flux` | start dir |

**Binds counted: 21** (`BIND_COUNT=21`, `ARGC=32` including inner cmd)

| # | Bind | Keep in fast path? |
|---|------|--------------------|
| 1–3 | `/dev` `/proc` `/sys` | **yes** (core) |
| 4 | `/dev/urandom:/dev/random` | **yes** |
| 5 | `sysdata/sys_empty:/sys/fs/selinux` | yes (cheap; avoids selinux probes) |
| 6 | `rootfs/tmp:/dev/shm` | yes for apps needing shm |
| 7–9 | `/data/app`, dalvik-cache ×2 | **no** for CLI/fast; Android ART only |
| 10 | app `cache` | optional |
| 11 | app `files/home` (Termux home) | optional (host home bridge) |
| 12–17 | `/apex` `/odm` `/product` `/system` `/system_ext` `/vendor` | **no** for pure Debian CLI; **yes** for Turnip/kgsl/Android GPU libs |
| 18–19 | linkerconfig ld.config.txt ×2 | with system binds |
| 20 | entire `$PREFIX` | **heavy**; only if guest must see host usr |
| 21 | `$PREFIX/tmp:/tmp` (`--shared-tmp`) | **yes** (tmpfs-friendly shared tmp) |

**Not present on this device session:** `/storage` / `/sdcard` binds (storage permission / access failed filters).  
**Not present:** fake `/proc/*` sysdata binds (code path exists; this dump only had selinux empty + shm).

**Inner command examples:**

| Entry | Inner argv |
|-------|------------|
| `nativecode_proot.sh cmd true` | `/bin/zsh` `-c` `bash -lc true` |
| `proot-distro login … -- /bin/true` | `/bin/zsh` `-c` `/bin/true` |
| Interactive login (no cmd) | `/bin/zsh` `-l` (login shell) |

### 1.3 Shell / rootfs facts (proot debian)

| Item | Value |
|------|--------|
| `/bin/sh` | **dash** (already good) |
| flux shell | **`/bin/zsh`** (heavy for every `-c`) |
| flux uid (proot passwd) | **10416:10416** (matches app-adjacent mapping) |
| flux uid (chroot passwd) | **1000:100** (different mapping — normal) |

---

## 2. Startup measurements (MediaTek)

Method: 1 warm-up + 6 timed samples, wall ms via `date +%s%N`. Outer `/system/bin/su u0_a415` included for proot paths (fair for ADB; app path skips `su` cost partially).

| ID | Scenario | avg ms | min–max | Notes |
|----|----------|--------|---------|-------|
| **A** | Current `nativecode_proot.sh cmd true` | **816** | 596–966 | python distro + zsh + bash -lc |
| **C** | `proot-distro login --minimal … -- /bin/true` | **881** | 844–928 | still zsh `-c`; minimal ≠ fast |
| **D** | **Plain proot, 5 binds, direct `/bin/true`** | **104** | 87–119 | ~**8×** vs A |
| **E** | Plain min + `/bin/sh -c true` | **94** | 66–123 | dash |
| **F** | Plain **full** bind set, direct `/bin/true` | **113** | 91–119 | ≈ D → **binds not launch bottleneck** |
| **H** | **chroot direct `/bin/true`** | **30** | 20–37 | gold |
| **G** | chroot `su - flux -c true` | **1199** | 1149–1263 | login/su tax (unfair “chroot”) |
| **I** | distro `cmd exit` (bash -lc exit via wrapper) | **489** | 293–643 | |
| **J** | plain min + `bash -lc exit` | **147** | 109–180 | ~**3×** vs I |
| **K** | chroot `su - flux -c 'bash -lc exit'` | **2065** | 1969–2145 | heavy profile path |

**Sample B** (`proot-distro login -- /bin/true`) produced broken negative timestamps under nested quoting; treat as ≈ A/C (~0.8–0.9 s), not used in ratios.

### 2.1 Interpretation (launch path)

1. **Dominant cost is not bind count** for one-shot binaries (D≈F).  
2. **Dominant cost is stack:** Python `proot-distro` + **login shell `-c`** (+ extra `bash -lc` from helper).  
3. **Direct plain proot** gets within ~3–4× of chroot direct (104 ms vs 30 ms). Remaining gap is hard ptrace tax.  
4. **`--minimal` alone does not fix launch** while shell wrapper remains.  
5. **Do not compare** “chroot via `su - flux -l`” to “proot direct” — both sides must use the same entry style.

---

## 3. Proposed minimal “fast launcher” (for review)

### 3.1 Goals

- Optional **performance profile** (does not replace default `proot-distro` until proven).  
- **Rootless** (app UID only).  
- Refuse wrong package (`com.termux` paths).  
- Modes: `exec` (default), `sh` (dash -c), `login` (interactive zsh/bash), `root-exec`.  
- GPU profiles: `none` | `turnip` | `virgl` (env only; binds escalate for GPU).  
- Preserve flux identity (`--change-id` from guest passwd).

### 3.2 Proposed file

| Deliverable | Path |
|-------------|------|
| New helper | `/data/local/tmp/nativecode_proot_fast.sh` (device) **and** repo asset e.g. `app/src/main/assets/scripts/nativecode_proot_fast.sh` |
| Later app hook | optional `ProotCommandBuilder` flag `useFastLauncher=true` — **not in first PR** |
| Keep | existing `nativecode_proot.sh` / proot-distro for install, login UX, full compatibility |

### 3.3 Bind profiles

```text
CORE (always):
  --bind=/dev --bind=/proc --bind=/sys
  --bind=/dev/urandom:/dev/random
  --bind=$PREFIX/tmp:/tmp          # shared tmp (host tmp often tmpfs-backed)
  --bind=$ROOTFS/tmp:/dev/shm      # optional if dir exists

STORAGE (if accessible):
  --bind=/storage/emulated/0:/sdcard   # single bind, not 4 aliases

ANDROID_SYS (GPU / linker / some native tools):
  --bind=/system --bind=/vendor --bind=/apex
  (+ linkerconfig files if present)
  # skip odm/product/system_ext unless needed

HOST_PREFIX (only if guest must call host bins — prefer avoid):
  --bind=$PREFIX

TERMUX_HOME_BRIDGE (optional):
  --bind=$TERMUX__HOME             # or :/home/flux/host-home
```

| Profile | Binds | Use |
|---------|-------|-----|
| `cli` | CORE + STORAGE | package ops, compile, shell |
| `gpu-turnip` | cli + ANDROID_SYS + kgsl nodes if present | Adreno Turnip |
| `gpu-virgl` | cli + X11 socket + virtio/virgl needs | Mali / virgl |
| `compat` | full proot-distro set | bug-for-bug fallback |

### 3.4 Flags (plain proot)

```text
--kill-on-exit
--link2symlink
-L
# --sysvipc   ONLY if NC_PROOT_SYSVIPC=1 or profile needs (fio, some browsers)
# NO fake kernel-release by default (optional NC_PROOT_FAKE_UNAME=1)
--change-id=$FLUX_UID:$FLUX_GID
--rootfs=$ROOTFS
--cwd=/home/flux   # or /root for root-exec
```

### 3.5 Exec model

```text
# Non-interactive (default) — NO login shell, NO profile
env -i \
  HOME=/home/flux USER=flux LOGNAME=flux \
  PATH=/usr/local/bin:/usr/bin:/bin \
  LANG=C.UTF-8 TERM=${TERM:-xterm-256color} \
  TMPDIR=/tmp \
  ${GPU_ENV...} \
  proot <flags> <binds> \
  /usr/bin/env -i HOME=... PATH=... -- "$@"

# Or simpler: proot … /bin/true
# Interactive:
proot … /bin/zsh -l     # or bash -l if NC_SHELL=bash
```

**Never** wrap one-shot as `zsh -c "bash -lc …"`.

### 3.6 GPU env (set by launcher, not guest profile)

**Turnip (Adreno only):**

```bash
MESA_LOADER_DRIVER_OVERRIDE=zink
GALLIUM_DRIVER=zink
VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
TU_DEBUG=noconform
MESA_VK_WSI_DEBUG=sw
MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLES_VERSION_OVERRIDE=3.2
MESA_SHADER_CACHE_DIR=/tmp/mesa_shader_cache
```

**Virgl (Mali device default):** keep existing app `flux_gpu=virgl` vars; no Turnip ICD.  
**Never** launch `glmark2-*-drm`.

### 3.7 Safety checks (must keep)

```bash
# Refuse stock Termux pollution
case "$PREFIX" in
  *com.termux*) echo "refusing com.termux prefix"; exit 2 ;;
esac
[ "$TERMUX_APP__PACKAGE_NAME" = "com.zenithblue.nativecode" ] || exit 2
[ -d "$ROOTFS" ] || exit 2
# only run as app UID for rootless (warn if root)
```

### 3.8 Pseudo-API

```bash
nativecode_proot_fast.sh exec  [--profile cli|gpu-turnip|gpu-virgl|compat] -- CMD...
nativecode_proot_fast.sh sh    -- 'shell string'     # /bin/sh -c
nativecode_proot_fast.sh login [--shell zsh|bash]    # interactive -l
nativecode_proot_fast.sh root-exec -- CMD...
```

---

## 4. Rootfs hardening (Task 3) — design only, run once inside guest

Script: `optimize_debian_rootfs_perf.sh` (guest, optional).

| Change | Risk | Effect |
|--------|------|--------|
| `ldconfig`; fontconfig/mime update; `locale-gen` C.UTF-8 only | low | fewer startup probes |
| Keep `/bin/sh` → dash | already true | good |
| Bind/hot `/tmp` via shared host tmp (launcher) | low | less disk I/O |
| apt: `Install-Recommends false`, path-exclude docs/man | medium | less small-file I/O |
| `policy-rc.d` exit 101 | low in container | no service spam |
| `eatmydata` for apt/builds | **unsafe for important data** — opt-in | fsync tax |
| ccache + object dir on `/tmp` | low | compile |
| **Do not** change flux login shell without UX sign-off (zsh → bash optional) | UX | faster `-c` if someone still uses shell -c |

---

## 5. Measurement plan (Task 4) — after implement

Same metrics as baselines, **plus** launch table:

| Metric | chroot | proot-distro (old) | proot-fast |
|--------|--------|--------------------|------------|
| direct `true` | | | |
| `sh -c true` | | | |
| interactive shell to prompt | | | |
| sysbench cpu / memory | | | |
| stress-ng / mbw / dd | | | |
| small `apt install` | | | |
| small C compile | | | |
| glmark build offscreen | Turnip device only | | |

Devices: MediaTek available now; Snapdragon when online.

---

## 6. Expected remaining hard limits

Even with fast launcher:

| Limit | Why |
|-------|-----|
| ptrace every syscall | architectural |
| Sequential dd read ~0.4–0.5× | path translation + app-data rootfs |
| Extreme sysbench-mem on some SoCs | device-specific |
| GPU client ~0.3× (Turnip sample) | ioctl + map under ptrace |

Next experiments **only if** launch/I/O gaps remain user-visible: lstat cache proot patch, selective seccomp, shorter rootfs path (symlink under `/data/local/tmp/pd-debian` → real rootfs — **test carefully**).

---

## 7. Recommendation matrix (preview)

| Workload | Prefer |
|----------|--------|
| Rootless only | **optimized proot** (fast launcher) |
| Heavy multi-thread RAM / big builds (Snapdragon-class tax) | **chroot** if KernelSU |
| Package ops / many small files | chroot if available; else proot + tmpfs + apt hygiene |
| Light CLI / scripts / AI TUI | optimized proot OK |
| Interactive terminal | optimized login **or** keep proot-distro for features |
| GPU GUI Turnip | optimized proot `gpu-turnip` or chroot; never DRM glmark |
| GPU Mali | virgl profile; no Turnip expectations |

---

## 8. What we will **not** do without approval

- Replace default app login path with fast launcher  
- Mutate production rootfs packages en masse  
- Disable `--link2symlink`  
- Enable global `eatmydata`  
- Patch libproot.so  
- Run DRM/KMS benchmarks  

---

## 9. Approval checklist (reply with choices)

1. **Implement** `nativecode_proot_fast.sh` on device + copy into repo assets?  
2. Default profile for CLI: **`cli`** (no `/system` binds) OK?  
3. Keep flux **zsh** for interactive only; one-shot never uses zsh?  
4. Run **rootfs optimize script** once on MediaTek proot guest (non-destructive preferences + caches only first)?  
5. After implement: re-bench launch + dd/sysbench on MediaTek; Snapdragon later?

---

## 10. One-line conclusion

**Current proot is slow to start mainly because of proot-distro + zsh/bash login wrappers (~800 ms), not because of 21 binds (~100 ms plain direct).**  
A minimal plain-proot fast launcher is the right first patch; rootfs/cache work is second; ptrace/memory gaps need chroot or deeper proot work.
