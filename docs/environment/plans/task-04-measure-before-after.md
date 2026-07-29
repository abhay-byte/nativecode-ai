# Task 4 — Before/after measurement scoreboard

| Field | Value |
|-------|--------|
| **Status** | **PLANNED** |
| **Depends on** | Task 2 (+ Task 3 if applied) |
| **Devices** | MediaTek (available) · Snapdragon when online |

---

## 1. Objective

Produce a scoreboard matching baseline report style:

- chroot vs original proot-distro vs **proot-fast**
- Launch, CPU/RAM/disk, optional GPU (Turnip only), compile, apt
- Ratios vs chroot and vs original proot

---

## 2. Metrics matrix

| Metric | chroot | proot-distro | proot-fast | Notes |
|--------|--------|--------------|------------|-------|
| direct `true` ms | | | | |
| `sh -c true` ms | | | | |
| interactive shell to exit ms | | | | |
| sysbench cpu events/s | | | | prime 20k, nproc thr |
| sysbench memory MiB/s | | | | 1M block, 2G |
| stress-ng cpu bogo/s | | | | 20s |
| mbw MEMCPY/DUMB/MCBLOCK | | | | `-n 5 64` |
| dd write 256 MiB fdatasync | | | | |
| dd read 256 MiB | | | | |
| small apt install wall s | | | | e.g. `hello` |
| small C compile wall s | | | | suite fixture |
| glmark2-es2 `-b build` offscreen | | | | **Turnip only**; never DRM |

---

## 3. Method

1. Freeze device thermal (cool, charging).  
2. Run `suite/run_perf_compare.sh` (planned) for three envs.  
3. Store raw logs under guest `~/bench_results/fast-YYYYMMDD/`.  
4. Write final table into `docs/environment/proot-vs-chroot-perf-report-fast.md` (new) + append this file §5.

---

## 4. Success criteria (practical)

| Goal | Target |
|------|--------|
| One-shot launch proot-fast / chroot direct | ≤ **4×** (was ~8× distro vs chroot on MTK wall for wrapper stack) |
| One-shot proot-fast / proot-distro | ≤ **0.5×** time (2× faster) |
| sysbench cpu | no worse than distro −5% |
| dd read | no worse than distro −5%; hope mild gain via less path noise at spawn only |
| Functionality | full suite P0/P1 pass |

Note: expect **little** change to steady-state dd/sysbench-mem from launcher alone; gains are launch + fewer wrappers.

---

## 5. Improvements log / results (fill after measure)

| Date | Device | Headline | Full report |
|------|--------|----------|-------------|
| — | — | — | — |

---

## 6. Task-4 regression gates

| ID | Check |
|----|-------|
| T4-R01 | All three envs produce summary files |
| T4-R02 | No DRM glmark invoked |
| T4-R03 | Suite P0 pass after benches |
| T4-R04 | Numbers recorded in report MD |

**Status:** not run.
