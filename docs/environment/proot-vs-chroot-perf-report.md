# NativeCode: proot vs chroot performance report

Full report of the **2026-07-29** baseline on a connected device: environment inventory, **CPU / RAM / storage / GPU (Turnip)** results, how each test was run, and how to **re-run safely**.

| Field | Value |
|-------|--------|
| **App package** | `com.ivarna.nativecode` (**not** stock `com.termux`) |
| **Device** | OnePlus 13R / **CPH2691** (model prop), Adreno **750** |
| **ADB (wireless)** | `192.168.1.78:41417` (port changes after reboot) |
| **ADB (USB example)** | serial `d30a1726` |
| **Root** | KernelSU (`adb shell` as `uid=0`) |
| **Guests** | Debian **13 trixie** · user **`flux`** · GPU mode **`turnip`** |
| **Date** | 2026-07-29 |
| **Related docs** | `adb-shell-access.md`, `proot-vs-chroot-baseline.md` |
| **App code SSOT** | `TermuxHostPaths.kt`, `ProotCommandBuilder.kt`, `ChrootCommandBuilder.kt`, `setup_hw_accel_debian.sh` |

---

## 1. Environment under test

### 1.1 Two Linux guests (same distro, separate trees)

| | **proot** | **chroot** |
|--|-----------|------------|
| **Rootfs** | `/data/data/com.ivarna.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs` | `/data/local/tmp/chrootDebian13` |
| **Distro name** | `proot-distro` container name: `debian` | path fixed by app |
| **Privilege** | App UID (e.g. `u0_a510`) + userspace proot | Real root + `busybox chroot` |
| **Enter as** | `flux` via `proot-distro login debian --user flux` | `flux` via `su - flux` inside chroot |
| **Kernel (`uname -r`)** | Fake: `6.17.0-PRoot-Distro` | Real: `6.1.174-gf870a38a2536` |
| **App prefs (sample)** | `proot_dir_present=true`, ~5.9 GB | `chroot_dir_present=true`, ~8.4 GB |
| **Marker** | rootfs present | `.flux_configured` |
| **GPU mode file** | `/etc/fluxlinux/gpu_mode` → `turnip` | same |

### 1.2 Host (embedded Termux prefix)

| Item | Path |
|------|------|
| Package | `com.ivarna.nativecode` |
| Prefix | `/data/data/com.ivarna.nativecode/files/usr` |
| Host home | `/data/data/com.ivarna.nativecode/files/home` |
| Host env SSOT | `$PREFIX/etc/fluxlinux-host.env` |
| proot binary | `$PREFIX/bin/proot` + APK `libproot.so` / `libloader.so` |
| proot-distro | `$PREFIX/bin/proot-distro` (Python) |

**Critical:** `proot-distro` reads `TERMUX_APP__PACKAGE_NAME`, `TERMUX__PREFIX`, `TERMUX__HOME` at import time. If missing, it defaults to **`com.termux`** and login fails or binds wrong paths.

### 1.3 Device prefs (sample from session)

From `/data/data/com.ivarna.nativecode/shared_prefs/nativecode_prefs.xml`:

- `linux_method` = `chroot` (at time of inspect)
- `proot_dir_present` / `chroot_dir_present` = true
- `flux_gpu` = `turnip`, `flux_gpu_vendor` = `adreno/snapdragon`
- Projects can pin `linuxMethod` per project (`proot` vs `chroot`) — trees are **not** shared

---

## 2. Executive scoreboard

Higher is better unless noted. **proot/chroot** = ratio (1.0 = equal).

| Metric | **chroot** | **proot** | Ratio | Winner |
|--------|------------|-----------|-------|--------|
| CPU sysbench events/s (prime 20k, 8 thr) | **8574.40** | 5563.31 | 0.65× | chroot |
| CPU p95 latency ms (lower better) | **1.79** | 1.86 | ~1× | ~tie |
| stress-ng cpu bogo ops/s (real, 20s) | ~**2139** | ~2114 | ~1.0× | ~tie |
| sysbench memory MiB/s | **31481.94** | 1989.90 | **0.06×** | chroot |
| mbw MEMCPY avg MiB/s | **15793.89** | 14161.17 | 0.90× | chroot |
| mbw DUMB avg MiB/s | 21105.40 | **21168.22** | ~1× | ~tie |
| mbw MCBLOCK avg MiB/s | 52571.05 | **54663.48** | ~1× | ~tie |
| dd write 256 MiB (fdatasync) | **1.7 GB/s** | 1.2 GB/s | 0.71× | chroot |
| dd read 256 MiB | **5.0 GB/s** | 2.0 GB/s | **0.40×** | chroot |
| fio seq write | n/a (shmget) | **1158 MiB/s** | — | proot only |
| fio seq read | n/a | **1118 MiB/s** | — | proot only |
| fio rand 4k read/write IOPS | n/a | ~795 / ~794 | — | proot only |
| Vulkan device | Turnip Adreno 750 | Turnip Adreno 750 | same | — |
| EGL surfaceless renderer | zink→Turnip | zink→Turnip | same | — |
| glmark2-es2 **`-b build`** score | **725** | 229 | **0.32×** | chroot |

### Proot bottlenecks (ranked)

1. **Memory (sysbench path)** — ~16× slower: largest gap  
2. **GPU client (glmark)** — ~3.2× slower at same Turnip ICD  
3. **Disk read (dd)** — ~2.5× slower  
4. **CPU (sysbench)** — ~35% slower  
5. **Not missing** — Turnip detection, light memcpy (mbw), stress-ng cpu ≈ parity  

---

## 3. Detailed results by subsystem

### 3.1 CPU

#### Tooling

| Tool | Command (both envs) |
|------|---------------------|
| sysbench | `sysbench cpu --cpu-max-prime=20000 --threads=$(nproc) run` |
| stress-ng | `stress-ng --cpu $(nproc) --timeout 20s --metrics-brief` |

#### Results

**chroot**

```text
sysbench 1.0.20
threads: 8
Prime numbers limit: 20000
events per second:  8574.40
total time:         10.0011s
95th percentile:    1.79 ms

stress-ng cpu 20s (8 hogs):
  bogo ops/s (real time) ≈ 2139
```

**proot**

```text
events per second:  5563.31
total time:         ~10s
95th percentile:    1.86 ms

stress-ng cpu 20s:
  bogo ops/s (real time) ≈ 2114
```

#### Interpretation

- **sysbench** stresses compute with frequent bookkeeping → proot syscall translation hurts (~35%).  
- **stress-ng** pure-ish CPU loops → nearly equal.  
- Use sysbench as “CPU + proot tax”; stress-ng as “raw core work”.

---

### 3.2 Memory (RAM)

#### Tooling

| Tool | Command |
|------|---------|
| sysbench | `sysbench memory --memory-block-size=1M --memory-total-size=2G --threads=$(nproc) run` |
| mbw | `mbw -n 5 64` (64 MiB buffer, 5 runs; AVG lines) |

#### Results

| | chroot | proot |
|--|--------|-------|
| sysbench transfer | **2048 MiB in ~0.06s → 31482 MiB/s** | 2048 MiB → **1990 MiB/s** |
| mbw MEMCPY AVG | 15794 MiB/s | 14161 MiB/s |
| mbw DUMB AVG | 21105 MiB/s | 21168 MiB/s |
| mbw MCBLOCK AVG | 52571 MiB/s | 54663 MiB/s |

#### Interpretation

- **sysbench memory** is the headline bottleneck for proot (syscall/page path on large multi-threaded blocks).  
- **mbw** pure userspace memcpy is close → RAM hardware is fine; **proot virtualization** is the tax.  
- Impacts: large copies, compression, big builds, language GC, heavy editors.

---

### 3.3 Storage (disk)

#### Tooling

| Tool | Command |
|------|---------|
| dd write | `dd if=/dev/zero of=$WORKDIR/dd.bin bs=1M count=256 conv=fdatasync` |
| dd read | `dd if=$WORKDIR/dd.bin of=/dev/null bs=1M` |
| fio seq write | `fio --name=seqw --directory=$W --rw=write --bs=1M --size=256M --numjobs=1 --iodepth=1 --direct=1 --ioengine=sync --thread --group_reporting` |
| fio seq read | same with `--rw=read` |
| fio rand | `fio ... --rw=randrw --bs=4k --size=64M --runtime=15 --time_based ...` |

`$WORKDIR` = `/home/flux/bench_io` in each guest.

#### Results

| | chroot | proot |
|--|--------|-------|
| dd write | **1.7 GB/s** (0.158 s for 256 MiB) | 1.2 GB/s (0.233 s) |
| dd read | **5.0 GB/s** | 2.0 GB/s |
| fio seq write | **FAILED** `shmget: Function not implemented` | **1158 MiB/s** |
| fio seq read | FAILED (same) | **1118 MiB/s** |
| fio rand 4k R/W | FAILED | ~**795 / 794 IOPS** |

#### fio note (chroot)

Android kernel here has **no SYSV IPC** (`shmget` unsupported). Even `fio --ioengine=sync --thread` still fails in chroot.  
**proot** emulates enough that fio works. Fair disk compare between envs therefore uses **dd**, not fio.

#### Interpretation

- Proot rootfs lives under **app data** + proot path translation → slower, especially **reads**.  
- Chroot on `/data/local/tmp/chrootDebian13` is faster for package install, git, compile I/O.  
- Expectation from product side (“disk slower in proot”) **confirmed** on dd read/write.

---

### 3.4 GPU — Turnip (Adreno) in detail

#### 3.4.1 Architecture (app design)

From `setup_hw_accel_debian.sh` / `gpu-launch` / `start_gui.sh`:

| Mode | When | Path |
|------|------|------|
| **turnip** | Snapdragon / Adreno (`/dev/kgsl-3d0`) | Mesa **Turnip** Vulkan ICD + **Zink** for OpenGL |
| **virgl** | Mali / other | host `virgl_test_server` + `GALLIUM_DRIVER=virpipe` |

On this device:

```text
/etc/fluxlinux/gpu_mode → turnip
/dev/kgsl-3d0 present
ICD: /usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
Mesa Turnip: Mesa 26.2.0-devel (git-9452d1daec)  [guest install]
```

Turnip is **not** desktop DRM modesetting for primary UI; it uses **KGSL**. Fake `/dev/dri` nodes may exist for apps that probe DRI only.

#### 3.4.2 Correct environment (required for GL)

```bash
export XDG_RUNTIME_DIR=/tmp
export MESA_LOADER_DRIVER_OVERRIDE=zink
export GALLIUM_DRIVER=zink                    # without this, eglinfo often falls to llvmpipe
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
export TU_DEBUG=noconform
export MESA_VK_WSI_DEBUG=sw
export MESA_GL_VERSION_OVERRIDE=4.6
export MESA_GLES_VERSION_OVERRIDE=3.2
export MESA_NO_ERROR=1
export LIBGL_ALWAYS_SOFTWARE=0
# optional: prefer freedreno ICD
export VK_LOADER_DRIVERS_SELECT=freedreno*
```

Or:

```bash
gpu-launch <command>   # /usr/local/bin/gpu-launch — mode from /etc/fluxlinux/gpu_mode
```

#### 3.4.3 Detection results (both envs)

**Vulkan** (`vulkaninfo --summary` with ICD forced):

```text
deviceName    = Turnip Adreno (TM) 750
driverName    = turnip Mesa driver
driverInfo    = Mesa 26.2.0-devel (git-9452d1daec)
deviceType    = INTEGRATED_GPU
vendorID      = 0x5143   # Qualcomm
```

Also listed: **llvmpipe** as a software Vulkan device if ICDs not filtered — always force `VK_ICD_FILENAMES` for fair tests.

**EGL** (`eglinfo -p surfaceless` with zink env):

```text
OpenGL core profile renderer: zink Vulkan 1.4(Turnip Adreno (TM) 750 (MESA_TURNIP))
```

Without `GALLIUM_DRIVER=zink`, the same command often reported **llvmpipe** only (misleading “no GPU”).

#### 3.4.4 Render benchmark — single scene only

| Rule | Reason |
|------|--------|
| Use **`glmark2-es2 -b build --off-screen`** | One scene; short; comparable |
| Prefer **`xvfb-run -a`** | Headless X for X11 glmark |
| **Never** `glmark2-es2-drm` / `glmark2-drm` | DRM/KMS fights Android SurfaceFlinger; **hung** in session; linked to **service died / black screen** incident |

**Scores (`-b build` only):**

| Env | glmark2 Score | GL_RENDERER |
|-----|---------------|-------------|
| **chroot** | **725** | zink Vulkan 1.4 (Turnip Adreno 750) |
| **proot** | **229** | same string |

Same GPU and driver string → gap is **proot overhead on GPU client path** (syscalls, buffer mapping), not “Turnip missing in proot”.

#### 3.4.5 What we did **not** use for final scores

- Full default glmark suite (long; can hang under Xvfb)  
- `glmark2-*-drm` (unsafe on daily-driver phone)  
- `vkcube` without display (WSI fail headless — expected)  
- Unigine / proprietary suites  

---

## 4. How the tests were performed (session procedure)

### Phase A — Reach both environments

1. Connect device (`adb connect` wireless or USB).  
2. Confirm package and rootfs:

```bash
adb shell 'pm path com.ivarna.nativecode'
adb shell 'ls /data/local/tmp/chrootDebian13/.flux_configured'
adb shell 'ls /data/data/com.ivarna.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs/etc/os-release'
```

3. **Chroot enter** (root):

```bash
CHROOT=/data/local/tmp/chrootDebian13
busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true
busybox mount --bind /dev  $CHROOT/dev  >/dev/null 2>&1 || true
busybox mount --bind /sys  $CHROOT/sys  >/dev/null 2>&1 || true
busybox mount -t proc proc $CHROOT/proc >/dev/null 2>&1 || true
busybox mount -t devpts devpts $CHROOT/dev/pts >/dev/null 2>&1 || true
busybox chroot $CHROOT /bin/su - flux
```

4. **Proot enter** (must be **NativeCode** env + **app UID**):

```bash
# Deploy/use /data/local/tmp/nativecode_proot.sh which:
#  - sources fluxlinux-host.env
#  - sets TERMUX_APP__PACKAGE_NAME=com.ivarna.nativecode
#  - sets TERMUX__PREFIX / TERMUX__HOME
#  - sets PD_PROOT_BIN / PROOT_LOADER from this APK's lib dir
#  - refuses */com.termux/* PREFIX
/system/bin/su u0_a510 -c '/data/local/tmp/nativecode_proot.sh cmd "whoami; id; cat /etc/os-release | head -3"'
```

Healthy proot check:

```text
OK com.ivarna.nativecode /data/data/com.ivarna.nativecode/files/usr ...
uid=…(flux)
PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
# no com.termux data paths in printenv
```

### Phase B — Install benchmark packages (once per guest)

```bash
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y sysbench mbw fio stress-ng \
  vulkan-tools mesa-utils-extra \
  glmark2-es2 glmark2 xvfb
# Do NOT install/run glmark2-*-drm on a phone that also runs Android UI
```

- **chroot:** `busybox chroot … /bin/bash -lc 'apt-get …'` as root  
- **proot:** `nativecode_proot.sh rootcmd 'apt-get …'` as app UID  

### Phase C — Automated suite (`bench_quick.sh`)

Script on device: `/data/local/tmp/bench_quick.sh`  
Copied into guests; writes:

- `/home/flux/bench_results/<label>.summary.txt`  
- `/home/flux/bench_results/<label>.log`  

Order inside script:

1. Identity: `uname`, `id`, `nproc`, meminfo, `gpu_mode`  
2. CPU: sysbench + stress-ng  
3. Memory: sysbench + mbw  
4. Disk: fio (if works) + dd; cleanup temp files only  
5. GPU: apply Turnip env → vulkaninfo → eglinfo surfaceless → **glmark2-es2 `-b build` only** under `xvfb-run -a`  

### Phase D — Run suite in each env

**chroot:**

```bash
busybox chroot /data/local/tmp/chrootDebian13 /bin/su - flux -c \
  'export BENCH_WORKDIR=/home/flux/bench_io XDG_RUNTIME_DIR=/tmp
   bash /tmp/bench_quick.sh chroot /home/flux/bench_results'
```

**proot:**

```bash
cp /data/local/tmp/bench_quick.sh \
  /data/data/com.ivarna.nativecode/files/usr/tmp/bench_quick.sh
/system/bin/su u0_a510 -c \
  '/data/local/tmp/nativecode_proot.sh cmd "export BENCH_WORKDIR=/home/flux/bench_io XDG_RUNTIME_DIR=/tmp; bash /tmp/bench_quick.sh proot /home/flux/bench_results"'
```

### Phase E — Manual GPU checks (optional, before suite)

```bash
# inside guest, with Turnip env applied
vulkaninfo --summary | grep -E 'deviceName|driverName|driverInfo'
eglinfo -p surfaceless 2>&1 | grep 'OpenGL core profile renderer'
timeout 90 xvfb-run -a glmark2-es2 -b build --off-screen
# other single scenes if needed: -b texture  -b shading  -b desktop
```

---

## 5. How to re-test (safe procedure)

### 5.1 Safety rules (mandatory)

| Do | Do not |
|----|--------|
| Single-scene glmark only (`-b build`) | Full glmark default suite unattended |
| Xvfb + Turnip env for GL | **`glmark2-*-drm`** on live Android |
| Short stress-ng (≤20–30s) | Multi-hour stress while daily-driving |
| Cleanup only bench files under `~/bench_io` | `rm -rf` rootfs / app data |
| Use **nativecode** proot wrapper | Bare `proot-distro` as root → falls back to **com.termux** |
| One env at a time for fair compare | DRM/KMS tools while SurfaceFlinger is running |

**Incident note (same day):** after heavy GPU/DRM experiments, the device showed many **service died** messages and black screen; later ADB still saw the device with SurfaceFlinger/zygote **restarting** and empty **binderfs**. Prefer **reboot** recovery; never re-run DRM glmark on a phone used for normal Android UI.

### 5.2 Prerequisites

```bash
# PC
adb devices   # or: adb connect <ip>:<port>
# If: failed to create pty master → fix host:
#   sudo mount -o remount,gid=5,mode=620,ptmxmode=666 /dev/pts
#   adb kill-server && adb start-server
# Non-interactive fallback:
#   adb exec-out id
```

On device: KernelSU root for chroot; app UID for proot; both rootfs installed; packages from §4 Phase B.

### 5.3 One-shot re-run checklist

```bash
SERIAL=<serial-or-ip:port>
APP_UID=$(adb -s $SERIAL shell 'stat -c %U /data/data/com.ivarna.nativecode' | tr -d '\r')

# 1) Push scripts (if missing)
adb -s $SERIAL push docs/environment/scripts/bench_quick.sh /data/local/tmp/bench_quick.sh
# nativecode_proot.sh: bake LIBDIR from:
#   adb shell 'find /data/app -path "*com.ivarna.nativecode*" -name libproot.so'

# 2) Chroot mounts + bench
adb -s $SERIAL shell 'CHROOT=/data/local/tmp/chrootDebian13
  busybox mount --bind /dev $CHROOT/dev 2>/dev/null
  busybox mount --bind /sys $CHROOT/sys 2>/dev/null
  busybox mount -t proc proc $CHROOT/proc 2>/dev/null
  cp -f /data/local/tmp/bench_quick.sh $CHROOT/tmp/
  busybox chroot $CHROOT /bin/su - flux -c \
    "BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_quick.sh chroot /home/flux/bench_results"'

# 3) Proot bench
adb -s $SERIAL shell "cp -f /data/local/tmp/bench_quick.sh \
  /data/data/com.ivarna.nativecode/files/usr/tmp/bench_quick.sh
  /system/bin/su $APP_UID -c '/data/local/tmp/nativecode_proot.sh cmd \
    \"BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_quick.sh proot /home/flux/bench_results\"'"

# 4) Pull summaries
adb -s $SERIAL shell 'cat /data/local/tmp/chrootDebian13/home/flux/bench_results/chroot.summary.txt'
adb -s $SERIAL shell 'cat /data/data/com.ivarna.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs/home/flux/bench_results/proot.summary.txt'
```

### 5.4 Manual commands (copy-paste inside guest)

```bash
# --- CPU ---
sysbench cpu --cpu-max-prime=20000 --threads="$(nproc)" run
stress-ng --cpu "$(nproc)" --timeout 20s --metrics-brief

# --- RAM ---
sysbench memory --memory-block-size=1M --memory-total-size=2G --threads="$(nproc)" run
mbw -n 5 64

# --- Disk ---
W=$HOME/bench_io; mkdir -p "$W"
dd if=/dev/zero of="$W/dd.bin" bs=1M count=256 conv=fdatasync
dd if="$W/dd.bin" of=/dev/null bs=1M
rm -f "$W/dd.bin"
# fio optional (often fails in chroot):
fio --name=seqw --directory="$W" --rw=write --bs=1M --size=256M \
  --numjobs=1 --iodepth=1 --direct=1 --ioengine=sync --thread --group_reporting

# --- GPU Turnip ---
export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
export TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw
export MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLES_VERSION_OVERRIDE=3.2
export XDG_RUNTIME_DIR=/tmp
vulkaninfo --summary | head -80
eglinfo -p surfaceless 2>&1 | grep -i renderer
timeout 90 xvfb-run -a glmark2-es2 -b build --off-screen
```

### 5.5 Expected healthy signs

| Check | Healthy |
|-------|---------|
| proot package assert | `OK com.ivarna.nativecode` … no `com.termux` paths |
| Vulkan | `Turnip Adreno (TM) 750` / `turnip Mesa driver` |
| EGL | renderer contains `zink` + `Turnip` (not only llvmpipe) |
| glmark | finishes with `glmark2 Score: N` for scene `build` |
| Android UI after test | still responsive — **if not, reboot; do not re-run DRM** |

---

## 6. Product interpretation (NativeCode)

| Workload | Prefer |
|----------|--------|
| Heavy RAM, big builds, fast disk, desktop GL | **chroot** (needs root) |
| Rootless / no su | **proot** (CPU OK for light CLI; expect mem/GPU/I/O tax) |
| AI CLI tools (opencode, etc.) | both work; chroot snappier under load |
| Turnip availability | **both** detect and run Turnip correctly with proper env |

**Proot is not “no GPU”.** It is **slower GPU client + much slower sysbench-memory path + slower disk**, with the same Turnip ICD.

---

## 7. Artifacts on device (from this session)

```text
/data/local/tmp/bench_quick.sh
/data/local/tmp/bench_suite.sh          # older; avoided for final scores
/data/local/tmp/bench_suite_v2.sh       # intermediate
/data/local/tmp/nativecode_proot.sh     # NativeCode-only proot enter

chroot results:
  /data/local/tmp/chrootDebian13/home/flux/bench_results/chroot.summary.txt
  /data/local/tmp/chrootDebian13/home/flux/bench_results/chroot.log

proot results:
  …/proot-distro/containers/debian/rootfs/home/flux/bench_results/proot.summary.txt
  …/proot-distro/containers/debian/rootfs/home/flux/bench_results/proot.log
```

---

## 8. Packages installed in guests

| Package | Role |
|---------|------|
| `sysbench` | CPU + memory microbench |
| `mbw` | Memory bandwidth (memcpy methods) |
| `fio` | Disk I/O (proot only here) |
| `stress-ng` | CPU stress metrics |
| `vulkan-tools` | `vulkaninfo` |
| `mesa-utils-extra` | `eglinfo` |
| `glmark2-es2` / `glmark2` | GL score (X11 + Xvfb) |
| `xvfb` | Headless X for glmark |
| Turnip/Mesa (app HW accel) | `freedreno` ICD, zink, `gpu-launch` |

---

## 9. References

- Method inspiration: [How to Run Performance Benchmarks on Debian 12](https://www.siberoloji.com/how-to-run-performance-benchmarks-on-debian-12/) (sysbench, mbw, fio patterns adapted for Android guests).  
- App: `app/src/main/assets/scripts/setup_hw_accel_debian.sh` (Turnip / `gpu-launch`).  
- App: `docs/plan/gpu-accel-vendor-detect-turnip-virgl.md`.  
- Shell access: `docs/environment/adb-shell-access.md`.  
- Short scoreboard: `docs/environment/proot-vs-chroot-baseline.md`.

---

## 10. Changelog

| Date | Note |
|------|------|
| 2026-07-29 | Initial full report: chroot vs proot on CPH2691; Turnip glmark single-scene; documented DRM hang risk and service-died incident |
