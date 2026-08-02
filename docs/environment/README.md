# Environment docs (NativeCode Linux guests)

| Doc | Purpose |
|-----|---------|
| [**proot-vs-chroot-perf-report.md**](./proot-vs-chroot-perf-report.md) | **Snapdragon / Adreno:** full results (CPU/RAM/disk/**GPU Turnip**), how tests were run, re-test safely |
| [**proot-vs-chroot-perf-report-mediatek.md**](./proot-vs-chroot-perf-report-mediatek.md) | **MediaTek / Mali:** CPU/RAM/disk only (no Turnip), bottlenecks, re-test guide |
| [**proot-fast-launcher-design.md**](./proot-fast-launcher-design.md) | Research: current proot argv inventory, launch timings, **fast-launcher design (pending review)** |
| [proot-vs-chroot-baseline.md](./proot-vs-chroot-baseline.md) | Short scoreboard + bottleneck summary (Adreno baseline) |
| [adb-shell-access.md](./adb-shell-access.md) | How to enter proot/chroot over ADB (NativeCode paths, Turnip env) |
| [**plans/**](./plans/) | Task plans (1–6), [3 deliverables commitment](./plans/commitment-three-deliverables.md), regression suite + scripts (`plans/suite/`) |

Package: **`com.zenithblue.nativecode`** only (never stock `com.termux` for host prefix).
