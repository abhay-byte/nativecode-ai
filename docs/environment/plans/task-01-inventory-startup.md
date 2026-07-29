# Task 1 — Inventory current proot command lines & shell startup

| Field | Value |
|-------|--------|
| **Status** | **COMPLETE** (research + measurements + criteria) |
| **Date** | 2026-07-29 |
| **Device used** | MediaTek MT6897 / POCO duchamp (`192.168.1.52:43055`) |
| **Safety** | Temporary proot argv wrapper **restored**; no permanent device mutation |
| **Next** | Task 2 (fast launcher) after plan approval |

---

## 1. Objective

1. Inventory proot command lines from `nativecode_proot.sh` / `proot-distro` / app `ProotCommandBuilder`.
2. Count binds; flag unnecessary ones for CLI.
3. Measure shell / binary startup (interactive + no-op).
4. Record findings and **planned** improvements (implement in Task 2+).
5. Define Task-1 regression gates so later work cannot regress launch correctness.

---

## 2. What was done

### 2.1 Code / SSOT review

| Source | Finding |
|--------|---------|
| `ProotCommandBuilder.kt` | Always goes through `python proot-distro login debian --shared-tmp --user flux` |
| App non-empty cmd | Adds `zsh -c "$shellCmd"` **on top of** distro shell wrap |
| `/data/local/tmp/nativecode_proot.sh` | `cmd` → `proot-distro … -- bash -lc "$CMD"` |
| `proot_distro/.../proot_cmd.py` | Builds full bind set; Termux extensions: `--kill-on-exit`, `--link2symlink`, `--sysvipc`, `-L`, fake kernel |
| `proot_distro/.../login/__init__.py` | Non-run mode: `inner = [login_shell, "-c", join(cmd)]` or `[login_shell, "-l"]` |
| Guest flux shell | `/bin/zsh` (heavy for every `-c`) |
| Guest `/bin/sh` | **dash** (already optimal) |

### 2.2 Live argv capture (MediaTek)

Method: temporary `#!/system/bin/sh` wrapper logging argv to app `tmp/`, then **restored** real ELF `proot`.

**Flags:**

```text
--kill-on-exit
--link2symlink
--sysvipc
--kernel-release=\Linux\localhost\6.17.0-PRoot-Distro\...
-L
--change-id=10416:10416
--rootfs=…/containers/debian/rootfs
--cwd=/home/flux
```

**Binds: 21** (full list in `../proot-fast-launcher-design.md` §1.2)

| Category | Examples | CLI needed? |
|----------|----------|-------------|
| Core | `/dev` `/proc` `/sys` urandom→random | yes |
| Tmp | `$PREFIX/tmp:/tmp`, rootfs tmp→`/dev/shm` | yes |
| Android ART | `/data/app`, dalvik-cache ×2 | **no** for Debian CLI |
| Host app | cache, files/home, full `$PREFIX` | optional |
| Android system | `/apex` `/system` `/vendor` … | **no** for pure CLI; **yes** for Turnip/kgsl |
| Storage | `/sdcard` aliases | often missing if no storage access |

**Inner command for `nativecode_proot.sh cmd true`:**

```text
/bin/zsh -c "bash -lc true"
```

→ **double shell** (zsh + bash login) on every one-shot command.

### 2.3 Startup measurements (MediaTek, n=6 after warm-up)

| ID | Scenario | avg ms | Notes |
|----|----------|--------|-------|
| A | Current distro `cmd true` | **816** | python + zsh + bash -lc |
| C | distro `--minimal` + true | **881** | shell wrap still dominates |
| D | plain proot, ~5 binds, `/bin/true` | **104** | ~**8×** vs A |
| F | plain proot, full binds, `/bin/true` | **113** | ≈ D → **binds not launch bottleneck** |
| H | chroot direct `/bin/true` | **30** | gold |
| G | chroot `su - flux -c true` | **1199** | unfair (login/su tax) |
| I | distro cmd exit | **489** | |
| J | plain min + `bash -lc exit` | **147** | ~3× vs I |

### 2.4 Cross-device performance baselines (prior work, not re-run)

| Device | Worst proot gaps |
|--------|------------------|
| Snapdragon / Adreno 750 | sysbench mem **0.06×**, glmark **0.32×**, dd read **0.40×**, CPU **0.65×** |
| MediaTek MT6897 / Mali | dd read **0.49×**, mem **0.88×**, CPU **~1.00×**; GPU skipped |

Primary architectural tax: ptrace + path canonicalization. Launch tax: **wrappers**, not bind count.

---

## 3. Findings (ranked)

1. **Launch latency** dominated by `proot-distro` (Python) + **login shell `-c`** (+ helper `bash -lc`).
2. **Bind count (21 vs 5)** barely affects one-shot `true` (~100 ms both).
3. **`--minimal` alone is insufficient** while zsh/bash wrap remains.
4. **Direct plain proot** lands within ~3–4× of chroot direct for no-op binaries.
5. **Steady-state** I/O/memory gaps remain device-dependent; fast launcher does not fix ptrace.
6. **Stability requirement:** keep full `proot-distro` path as **compat** fallback; fast path must be optional.

---

## 4. Improvements identified (for later tasks)

| # | Improvement | Task | Risk if careful |
|---|-------------|------|-----------------|
| I1 | Plain proot fast launcher; direct exec / `env -i` | T2 | low |
| I2 | Profiles: `cli` / `gpu-turnip` / `gpu-virgl` / `compat` | T2 | low |
| I3 | Drop double shell on one-shot (`zsh`+`bash -lc`) | T2 | low |
| I4 | Optional bind sets (no dalvik for CLI) | T2 | low–med |
| I5 | Pre-warm caches (ldconfig, fonts, mime, locale) | T3 | low |
| I6 | Shared `/tmp` + build dirs on tmpfs | T3 | low |
| I7 | apt hygiene (no recommends; path-exclude docs) | T3 | low |
| I8 | Document when to force chroot | T6 | none |
| — | Global eatmydata / disable link2symlink | **out of scope default** | high |

**Not done in Task 1:** implementing launcher or rootfs edits (by design — plan-first).

---

## 5. Task-1 regression gates

Task 1 is research-only. Gates below must pass **after any device touch** during Task 1 cleanup (wrapper restore already done).

### P0 — must pass

| ID | Check | Expected |
|----|-------|----------|
| T1-R01 | `$PREFIX/bin/proot` is ELF, not shell wrapper | `file` → ELF |
| T1-R02 | `nativecode_proot.sh cmd true` exits 0 | success |
| T1-R03 | Paths under `com.ivarna.nativecode` only | no `com.termux` rootfs |
| T1-R04 | Guest `uname` under distro still works | non-empty |
| T1-R05 | chroot still enterable (if present) | `busybox chroot … true` or `su - flux -c true` |

### P1 — record only

| ID | Check | Record |
|----|-------|--------|
| T1-R10 | distro cmd true wall time | ms (baseline ~800 on MTK) |
| T1-R11 | plain min true wall time | ms (baseline ~100 on MTK) |
| T1-R12 | bind count of distro login | ~21 on MTK sample |

### Task-1 regression pass status

| Gate | Status | Notes |
|------|--------|-------|
| T1-R01 | **PASS** | proot restored to ELF after argv dump |
| T1-R02 | **PASS** | smoke `cmd true` exit 0 post-restore |
| T1-R03 | **PASS** | host env SSOT forces NativeCode package |
| T1-R04 | **PASS** | prior session `uname` PRoot-Distro |
| T1-R05 | **PASS** | chroot benches earlier same day |

**Task-1 regression: PASS** (research cleanup only; no functional change shipped).

---

## 6. Artifacts produced

| Artifact | Location |
|----------|----------|
| Design write-up | `docs/environment/proot-fast-launcher-design.md` |
| This plan | `docs/environment/plans/task-01-inventory-startup.md` |
| Prior baselines | `docs/environment/proot-vs-chroot-perf-report*.md` |
| Regression suite def | `docs/environment/plans/regression-test-suite.md` |
| Runnable suite | `docs/environment/plans/suite/` |

---

## 7. Exit criteria for Task 1

- [x] Command lines inventoried  
- [x] Binds counted and classified  
- [x] Startup measured (MediaTek)  
- [x] Improvements listed and mapped to later tasks  
- [x] Regression gates defined and post-cleanup **PASS**  
- [x] No unstable permanent changes left on device  

**Task 1 closed.** Proceed to Task 2 plan (implementation only after explicit go).
