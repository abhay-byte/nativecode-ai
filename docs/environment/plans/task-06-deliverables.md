# Task 6 — Deliverables & recommendation matrix

| Field | Value |
|-------|--------|
| **Status** | **PLANNED** |
| **Depends on** | Tasks 2–5 |

---

## 1. Objective

Ship / document:

1. Updated helpers (`nativecode_proot.sh` comments + `nativecode_proot_fast.sh`).
2. Rootfs optimize script (`--safe` default).
3. Short before/after scoreboard (Task 4 report).
4. Recommendation matrix (when chroot vs optimized proot).
5. Regression suite green on target device(s).

---

## 2. Deliverable checklist

| Deliverable | Location | Done? |
|-------------|----------|-------|
| Fast launcher script | assets + `/data/local/tmp/` | ☐ |
| Rootfs optimize script | assets | ☐ |
| Perf report (fast) | `docs/environment/proot-vs-chroot-perf-report-fast.md` | ☐ |
| Design + plans | `docs/environment/plans/` | partial (plans only) |
| Regression suite | `docs/environment/plans/suite/` | **scripts defined** |
| App default switch | only if product asks | ☐ optional |

---

## 3. Recommendation matrix (fill with Task 4 numbers)

| Workload | Prefer | Why |
|----------|--------|-----|
| Rootless only | proot-fast | no KernelSU |
| Light CLI / scripts | proot-fast | launch tax fixed |
| AI CLI (opencode, etc.) | proot-fast if suite P1 AI pass | |
| apt / many small files | chroot if available | dd/read + metadata |
| Large multi-thread mem / big builds | chroot on high-tax SoCs | sysbench-mem |
| Turnip heavy GUI | chroot or proot-fast gpu-turnip | measure |
| Mali / virgl GUI | proot-fast gpu-virgl | no Turnip |
| Full distro features / debugging | proot-distro compat | parity |

---

## 4. Compatibility guarantees

- Default user path remains **proot-distro** until product flips flag.
- Fast path is **opt-in** env or explicit script.
- Turnip/virgl env blocks unchanged in meaning.
- No DRM/KMS glmark in any shipped script.

---

## 5. Improvements log (final)

| Date | Item | Notes |
|------|------|-------|
| — | — | — |

---

## 6. Task-6 regression gates

| ID | Check |
|----|-------|
| T6-R01 | Suite P0 + P1 pass on MediaTek |
| T6-R02 | Suite P0 pass on Snapdragon when available |
| T6-R03 | Docs linked from `docs/environment/README.md` |
| T6-R04 | Recommendation matrix complete |

**Status:** not run.
