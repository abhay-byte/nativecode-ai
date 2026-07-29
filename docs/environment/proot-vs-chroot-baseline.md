# Baseline: proot vs chroot (NativeCode)

> **Full report (detailed steps, GPU/Turnip, re-test guide):**  
> [`proot-vs-chroot-perf-report.md`](./proot-vs-chroot-perf-report.md)

**Device:** CPH2691 (Adreno 750), ADB `192.168.1.78:41417`  
**Date:** 2026-07-29  
**Package:** `com.ivarna.nativecode` only (never stock `com.termux`)  
**Guests:** Debian 13 trixie · user `flux` · GPU mode `turnip`  
**Method:** `bench_quick.sh` — sysbench, stress-ng, mbw, dd, fio (where possible), vulkaninfo, eglinfo, **glmark2-es2 single scene `-b build` only**

| Env | How entered |
|-----|-------------|
| **chroot** | root + `busybox chroot /data/local/tmp/chrootDebian13 /bin/su - flux` |
| **proot** | app UID `u0_a510` + `/data/local/tmp/nativecode_proot.sh` (sources `fluxlinux-host.env`) |

---

## Scoreboard (higher better unless noted)

| Metric | **chroot** | **proot** | proot / chroot | Notes |
|--------|------------|-----------|----------------|-------|
| **CPU sysbench** events/s (prime=20k, 8 thr) | **8574** | 5563 | **0.65×** | proot CPU intercept tax |
| **CPU p95 latency** ms (lower better) | **1.79** | 1.86 | ~same | |
| **stress-ng cpu** bogo ops/s real | ~2139 | ~2114 | ~1.0× | 20s; closer than sysbench |
| **sysbench memory** MiB/s | **31482** | 1990 | **0.06×** | **largest gap** |
| **mbw MEMCPY** MiB/s avg | **15794** | 14161 | 0.90× | user memcpy, milder |
| **mbw DUMB** MiB/s | 21105 | 21168 | ~1.0× | |
| **mbw MCBLOCK** MiB/s | 52571 | 54663 | ~1.0× | |
| **dd write** 256 MiB fdatasync | **1.7 GB/s** | 1.2 GB/s | 0.71× | |
| **dd read** 256 MiB | **5.0 GB/s** | 2.0 GB/s | **0.40×** | cache/path sensitive |
| **fio seq write** | n/a (see below) | 1158 MiB/s | — | chroot: no SYSV shm |
| **fio seq read** | n/a | 1118 MiB/s | — | |
| **fio rand 4k R/W IOPS** | n/a | ~795 / ~794 | — | 15s time_based |
| **Vulkan device** | Turnip Adreno 750 | Turnip Adreno 750 | same | ICD forced |
| **EGL renderer (surfaceless)** | zink→Turnip | zink→Turnip | same | need zink env |
| **glmark2-es2 `-b build` score** | **725** | 229 | **0.32×** | single scene only |

---

## Bottlenecks for **proot** (ranked)

### 1. Memory bandwidth path (sysbench) — primary

sysbench memory is **~16× slower** under proot (1990 vs 31482 MiB/s).  
mbw is only ~10% slower → pure userspace memcpy is fine; **proot’s syscall/page translation on large sequential memory ops** (and how sysbench threads hit them) is the killer.

**Impact:** large copies, compression, language runtimes, heavy `malloc` churn, builds with big working sets.

### 2. GPU / GL via Zink+Turnip — large

Same Turnip ICD and zink renderer string, but **glmark `build` score ~3.2× lower** (229 vs 725).  
Extra cost: proot on every guest syscall around EGL/Vulkan + buffer mapping through the proot layer (not missing GPU — GPU is detected correctly).

**Impact:** GUI (XFCE), browsers, glmark, any GL/Vulkan client rate.

### 3. CPU (sysbench-style) — medium

~**35%** fewer events/s. Continuous integer work with frequent syscalls pays proot tax; stress-ng bogo was nearly equal (different workload mix).

### 4. Storage — medium (read worse)

| | chroot | proot |
|--|--------|-------|
| dd write | 1.7 GB/s | 1.2 GB/s (−29%) |
| dd read | 5.0 GB/s | 2.0 GB/s (−60%) |

Rootfs on app data (`…/proot-distro/containers/debian/rootfs`) + proot path translation. Expect slower package install, git, compile object I/O vs chroot on `/data/local/tmp/chrootDebian13`.

**fio:** works under proot (emulated shm); **fails in chroot** on this Android kernel (`shmget: Function not implemented` — no SYSV IPC). Disk comparison for chroot uses **dd only**.

### 5. Not a bottleneck

- Turnip **detection** (both see Adreno 750 + turnip Mesa)  
- mbw dumb/mcblock ≈ parity  
- stress-ng cpu ≈ parity  

---

## Correct Turnip usage (both envs)

```bash
export MESA_LOADER_DRIVER_OVERRIDE=zink
export GALLIUM_DRIVER=zink          # without this, eglinfo → llvmpipe
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
export TU_DEBUG=noconform
export MESA_VK_WSI_DEBUG=sw
export MESA_GL_VERSION_OVERRIDE=4.6
export MESA_GLES_VERSION_OVERRIDE=3.2
# or: gpu-launch <cmd>
```

**Do not** use `glmark2-*-drm` on device (KMS hang).  
**Do** use single scene: `glmark2-es2 -b build --off-screen` under `xvfb-run -a` + turnip env.

---

## Correct proot host (NativeCode)

```bash
# SSOT
. /data/data/com.ivarna.nativecode/files/usr/etc/fluxlinux-host.env
export TERMUX_APP__PACKAGE_NAME=com.ivarna.nativecode
export TERMUX__PREFIX=/data/data/com.ivarna.nativecode/files/usr
export TERMUX__HOME=/data/data/com.ivarna.nativecode/files/home
# then proot-distro as app UID
/system/bin/su u0_a510 -c /data/local/tmp/nativecode_proot.sh …
```

Missing those → defaults to **`com.termux`** paths. See `docs/environment/adb-shell-access.md`.

---

## Interpretation for NativeCode product

| Workload | Prefer |
|----------|--------|
| Heavy RAM / build / large file I/O / desktop GL | **chroot** (KernelSU) |
| Rootless install, no su | **proot** (acceptable CPU for light CLI; expect slower I/O + GUI) |
| AI tools / TUI (opencode, etc.) | both OK; chroot snappier under load |
| GPU-heavy GUI | chroot; proot still Turnip-capable but ~⅓ glmark build score |

**Proot bottleneck summary:** not “no GPU” and not “disk only” — **syscall virtualization hits memory-heavy and GPU-client paths hardest**, then sequential disk read, then CPU-bound sysbench.

---

## Raw summaries on device

```text
chroot: /data/local/tmp/chrootDebian13/home/flux/bench_results/chroot.summary.txt
proot:  …/proot-distro/containers/debian/rootfs/home/flux/bench_results/proot.summary.txt
scripts: /data/local/tmp/bench_quick.sh , /data/local/tmp/nativecode_proot.sh
```

Re-run:

```bash
# chroot
busybox chroot /data/local/tmp/chrootDebian13 /bin/su - flux -c \
  'BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_quick.sh chroot /home/flux/bench_results'

# proot (nativecode)
/system/bin/su u0_a510 -c \
  '/data/local/tmp/nativecode_proot.sh cmd "BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_quick.sh proot /home/flux/bench_results"'
```

---

## Tools installed (guest)

`sysbench` `mbw` `fio` `stress-ng` `vulkan-tools` `mesa-utils-extra` `glmark2-es2` `xvfb`  
(from Debian trixie; Turnip/Mesa from app HW accel setup.)
