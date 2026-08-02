# NativeCode: proot vs chroot performance report (MediaTek / Mali)

Full **non-GPU** baseline on a weaker MediaTek device: environment inventory, **CPU / RAM / storage** results, bottleneck ranking, how each test was run, and how to **re-run**.

| Field | Value |
|-------|--------|
| **App package** | `com.zenithblue.nativecode` (**not** stock `com.termux`) |
| **Device** | POCO **duchamp** · model `2311DRK48I` (POCO X6 Pro family) |
| **SoC** | MediaTek **MT6897** (Dimensity 8300-class) · platform `mt6897` |
| **GPU (host)** | **Mali** (`ro.hardware.vulkan=mali`, `ro.hardware.egl=meow`) — **no Turnip** |
| **App GPU mode** | `flux_gpu=virgl`, `flux_gpu_vendor=mali` |
| **RAM** | ~7.5 GiB (`MemTotal` 7496928 kB) |
| **CPU** | 8 cores · max freq clusters ~2.2 / 3.2 / 3.35 GHz |
| **Android** | 16 (API 36) · kernel `6.1.134-android14-11-…` |
| **ADB** | wireless `192.168.1.52:43055` (port changes after reboot) |
| **Root** | KernelSU (`adb shell` as `uid=0`) |
| **App UID** | `u0_a415` / `10415` |
| **Guests** | Debian **13 trixie** · user **`flux`** |
| **Date** | 2026-07-29 |
| **Scope** | CPU · memory · disk only — **GPU intentionally skipped** (Mali, no Turnip) |
| **Related** | Snapdragon/Turnip report: [`proot-vs-chroot-perf-report.md`](./proot-vs-chroot-perf-report.md) · ADB: [`adb-shell-access.md`](./adb-shell-access.md) |

---

## 1. Environment under test

### 1.1 Two Linux guests (same distro family, separate trees)

| | **proot** | **chroot** |
|--|-----------|------------|
| **Rootfs** | `/data/data/com.zenithblue.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs` (~6.0 G) | `/data/local/tmp/chrootDebian13` (~8.3 G) |
| **Privilege** | App UID `u0_a415` + userspace proot | Real root + `busybox chroot` |
| **Enter as** | `flux` via `proot-distro login debian --user flux` | `flux` via `su - flux` inside chroot |
| **Kernel (`uname -r`)** | Fake: `6.17.0-PRoot-Distro` | Real: `6.1.134-android14-11-g246384388afb` |
| **Prefs (session)** | `proot_dir_present=true`, `proot_size_bytes≈5.3 GB` | `chroot_dir_present=true`, `chroot_size_bytes≈8.4 GB` |
| **linux_method** | `proot` (at test time) | also installed |
| **GPU mode file / prefs** | host prefs: **virgl + mali** | same app |

### 1.2 Host (embedded Termux prefix)

| Item | Path |
|------|------|
| Package | `com.zenithblue.nativecode` |
| Prefix | `/data/data/com.zenithblue.nativecode/files/usr` |
| Host home | `/data/data/com.zenithblue.nativecode/files/home` |
| Host env SSOT | `$PREFIX/etc/fluxlinux-host.env` |
| proot binary | `$PREFIX/bin/proot` |
| proot-distro | `$PREFIX/bin/proot-distro` |

**Critical:** always source `fluxlinux-host.env` and set `TERMUX_APP__PACKAGE_NAME=com.zenithblue.nativecode` before `proot-distro`. Missing → defaults to **`com.termux`**.

### 1.3 Device helpers used this run

| Path | Role |
|------|------|
| `/data/local/tmp/nativecode_proot.sh` | App-UID proot-distro wrapper (`login` / `root` / `cmd` / `rootcmd`) |
| `/data/local/tmp/bench_nongpu.sh` | Non-GPU suite: sysbench, stress-ng, mbw, dd, fio |
| Guest copies | `/tmp/bench_nongpu.sh` in chroot + proot rootfs |

### 1.4 GPU — why skipped

| Item | Value |
|------|--------|
| Vendor | MediaTek Mali (not Adreno) |
| Turnip / freedreno | **Not applicable** — no kgsl/Turnip stack on this SoC |
| App setting | `virgl` (software/remote GL path), not `turnip` |
| This report | **No** glmark / eglinfo / vulkaninfo / Turnip env |

GPU comparison belongs only on Adreno+Turnip devices (see Snapdragon report).

---

## 2. Executive scoreboard

Higher is better unless noted. **proot/chroot** = ratio (1.0 = equal).

| Metric | **chroot** | **proot** | Ratio | Winner |
|--------|------------|-----------|-------|--------|
| CPU sysbench events/s (prime 20k, 8 thr) | **5870.66** | 5869.23 | **1.00×** | tie |
| CPU p95 latency ms (lower better) | **2.07** | 2.07 | 1.00× | tie |
| stress-ng cpu bogo ops/s (real, 20s) | **1559.10** | 1540.83 | 0.99× | ~tie |
| sysbench memory MiB/s | **45268.21** | 39987.20 | **0.88×** | chroot |
| mbw MEMCPY avg MiB/s (primary run) | 9021.45† | **14415.06** | — | noisy |
| mbw MEMCPY avg (chroot re-run) | **11275.55** | (proot 14415) | ~1.3× proot | ~tie/noise |
| mbw DUMB avg MiB/s (primary / re-run) | 14560 / **15675** | **16900.81** | ~1× | ~tie |
| mbw MCBLOCK avg MiB/s (primary / re-run) | 18849 / **23356** | **23043.13** | ~1× | ~tie |
| dd write 256 MiB (fdatasync) | **1.1 GB/s** | 988 MB/s | **0.90×** | chroot |
| dd read 256 MiB | **6.8 GB/s** | 3.3 GB/s | **0.49×** | chroot |
| fio seq write | n/a (`shmget`) | **1219 MiB/s** | — | proot only |
| fio seq read | n/a | **1497 MiB/s** | — | proot only |
| fio rand 4k R/W IOPS | n/a | **~9339 / ~9352** | — | proot only |
| GPU / glmark | **skipped** | **skipped** | — | Mali, no Turnip |

† Primary chroot MEMCPY had one cold/outlier sample (4025 MiB/s); use re-run AVG for interpretation.

### Proot bottlenecks on this device (ranked)

1. **Disk sequential read (dd)** — ~**2.1× slower** (3.3 vs 6.8 GB/s) — **primary**  
2. **Disk sequential write (dd)** — ~**10% slower** (988 MB/s vs 1.1 GB/s)  
3. **sysbench memory** — ~**12% slower** (0.88×) — mild, **not** the 16× tax seen on Adreno device  
4. **CPU** — **no meaningful gap** (sysbench + stress-ng ≈ parity)  
5. **Userspace memcpy (mbw)** — **not a bottleneck** (noise / thermal; often equal or proot higher)  
6. **GPU** — **not measured** (Mali / virgl; no Turnip)

### Headline vs Snapdragon (CPH2691 / Adreno 750) baseline

| Subsystem | MediaTek (this report) | Snapdragon (prior report) |
|-----------|------------------------|---------------------------|
| CPU sysbench proot/chroot | **~1.00×** | **0.65×** |
| sysbench memory | **0.88×** | **0.06× (~16× slower)** |
| dd read | **0.49×** | **0.40×** |
| dd write | **0.90×** | **0.71×** |
| GPU glmark | n/a (Mali) | proot **0.32×** (Turnip) |

**Takeaway:** on this MT6897 set, proot’s expensive gaps shrink to **storage (esp. read)**; CPU/RAM virtualization tax is small. Do **not** assume Snapdragon-style memory tax on every device.

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
events per second:  5870.66
total time:         10.0013s
total events:       58729
latency avg / p95:  1.36 / 2.07 ms

stress-ng cpu 20s (8 hogs):
  bogo ops: 31230
  bogo ops/s (real time): 1559.10
  usr time: 149.02s  sys: 0.22s
```

**proot**

```text
events per second:  5869.23
total time:         10.0014s
total events:       58707
latency avg / p95:  1.36 / 2.07 ms

stress-ng cpu 20s:
  bogo ops: 30873
  bogo ops/s (real time): 1540.83
  usr time: 148.63s  sys: 0.20s
```

#### Interpretation

- Integer prime workload and stress-ng CPU hogs are **statistically equal** under proot vs chroot.  
- On the stronger Snapdragon unit, sysbench paid ~35% proot tax; **here the tax is ~0%**.  
- Absolute chroot throughput is lower than CPH2691 (5871 vs 8574 events/s) — weaker SoC / lower sustained clocks, as expected.  
- For pure compute (compiles dominated by CPU, crypto, numeric), **either guest is fine** on this phone.

---

### 3.2 Memory (RAM)

#### Tooling

| Tool | Command |
|------|---------|
| sysbench | `sysbench memory --memory-block-size=1M --memory-total-size=2G --threads=$(nproc) run` |
| mbw | `mbw -n 5 64` (64 MiB buffer, 5 runs; report AVG lines) |

#### Results

| | chroot | proot |
|--|--------|-------|
| sysbench transfer | **2048 MiB in 0.0426 s → 45268 MiB/s** | 2048 MiB in 0.0492 s → **39987 MiB/s** |
| sysbench p95 latency | 0.25 ms | 0.21 ms |
| mbw MEMCPY AVG (run 1) | 9021 MiB/s (outlier sample) | **14415 MiB/s** |
| mbw MEMCPY AVG (chroot re-run) | **11276 MiB/s** | — |
| mbw DUMB AVG (re-run / run 1) | 15675 / 14560 | **16901** |
| mbw MCBLOCK AVG (re-run / run 1) | 23356 / 18849 | **23043** |

#### Interpretation

- **sysbench memory** is only **~12% slower** under proot — mild, not catastrophic.  
- On Adreno/Snapdragon baseline, the same test was **~16×** slower under proot. That gap is **device- and stack-dependent**, not universal.  
- **mbw** (userspace memcpy) shows high run-to-run variance (thermal / frequency / page cache); proot is not systematically worse.  
- Host has ~7.5 GiB RAM and only ~3 GiB available at probe time — keep benches short and avoid multi-GB parallel I/O if the UI is in use.  
- **Impact:** large multi-threaded memory ops slightly favor chroot; everyday CLI / editors unlikely to feel it.

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
| dd write | **1.1 GB/s** (256 MiB in 0.2385 s) | **988 MB/s** (0.2718 s) |
| dd read | **6.8 GB/s** (0.0394 s) | **3.3 GB/s** (0.0808 s) |
| fio seq write | **FAILED** `shmget: Function not implemented` | **1219 MiB/s** (210 ms for 256 MiB) |
| fio seq read | FAILED (same) | **1497 MiB/s** (171 ms) |
| fio rand 4k read IOPS | FAILED | **~9339** (~36.5 MiB/s) |
| fio rand 4k write IOPS | FAILED | **~9352** (~36.5 MiB/s) |

#### fio note (chroot)

Same Android kernel limitation as the Snapdragon unit: **no SYSV IPC** (`shmget` unsupported). Even `fio --ioengine=sync --thread` fails in chroot.  
**proot** emulates enough that fio runs. Fair **cross-env** disk compare uses **dd**, not fio.

#### Interpretation

- **dd sequential read** is the clear proot pain point (**~2.1× slower**). Rootfs under app data + proot path translation / open-read bookkeeping.  
- Write gap is smaller (~10%).  
- Expect slower: package installs, `git checkout` large trees, compile object reads, unpacking archives — more than pure CPU work.  
- fio numbers (proot-only) are useful absolute disk-ish rates on this UFS, not for env ratio.

---

### 3.4 GPU — not tested

| Reason | Detail |
|--------|--------|
| SoC GPU | Mali (not Adreno) |
| Turnip | Requires freedreno/kgsl path — **unavailable** |
| App mode | `virgl` — different stack from Turnip zink |
| Safety / scope | User requested full CPU/RAM/disk analysis **except GPU** |

Do not run `glmark2-*-drm` on device. If virgl/llvmpipe GL is later needed, document separately; do not mix with Turnip methodology.

---

## 4. Bottleneck analysis (complete)

### 4.1 Where proot loses (this device)

| Rank | Bottleneck | Evidence | Practical impact |
|------|------------|----------|------------------|
| **1** | **Sequential disk read** | dd read **0.49×** chroot | Package ops, source trees, cold cache reads |
| **2** | **Sequential disk write** | dd write **0.90×** | Installs, build artifacts — mild |
| **3** | **sysbench-style multi-thread memory** | **0.88×** | Heavy alloc/copy loops — mild |
| **4** | CPU intercept | **~1.00×** | Negligible for prime/stress workloads |
| **5** | Userspace memcpy | mbw ≈ parity | Not a product concern |

### 4.2 Where proot is fine

- CPU-bound work (sysbench, stress-ng)  
- Light/medium memory bandwidth (mbw)  
- Interactive CLI when not thrashing disk  

### 4.3 Where chroot still wins product-wise

| Workload | Prefer |
|----------|--------|
| Heavy package / git / compile I/O | **chroot** |
| Rootless / no KernelSU | **proot** (CPU OK here) |
| AI tools / TUI under load | either; chroot snappier on cold I/O |
| GPU-heavy desktop | **not characterized** on Mali; use virgl path or host apps |

### 4.4 Absolute device class (vs prior Snapdragon phone)

| Metric (chroot) | MediaTek MT6897 | Snapdragon Adreno 750 unit |
|-----------------|-----------------|----------------------------|
| sysbench CPU events/s | ~5871 | ~8574 |
| sysbench mem MiB/s | ~45268 | ~31482 |
| stress-ng bogo/s | ~1559 | ~2139 |
| dd write | 1.1 GB/s | 1.7 GB/s |
| dd read | 6.8 GB/s | 5.0 GB/s |
| RAM | ~7.5 GiB | higher class phone |

MediaTek unit is the **weaker compute** device; disk read cache rates still high. Proot **relative** tax is **smaller** on CPU/RAM here than on the flagship Snapdragon sample.

---

## 5. How tests were performed (step-by-step)

### 5.1 Connect

```bash
adb connect 192.168.1.52:43055
adb -s 192.168.1.52:43055 shell id   # expect uid=0 with KernelSU
```

### 5.2 Install helpers on device

`nativecode_proot.sh` and `bench_nongpu.sh` were written to `/data/local/tmp/` (see §7 for full scripts).

### 5.3 Mount chroot

```bash
DEBIANPATH=/data/local/tmp/chrootDebian13
BB=/data/adb/ksu/bin/busybox
$BB mount -o remount,dev,suid /data 2>/dev/null
$BB mount --bind /dev  $DEBIANPATH/dev
$BB mount --bind /sys  $DEBIANPATH/sys
$BB mount -t proc proc $DEBIANPATH/proc
$BB mount -t devpts devpts $DEBIANPATH/dev/pts
mkdir -p $DEBIANPATH/dev/shm
$BB mount -t tmpfs -o size=512M,mode=1777 tmpfs $DEBIANPATH/dev/shm
# resolv for apt
cp /etc/resolv.conf $DEBIANPATH/etc/resolv.conf 2>/dev/null || \
  echo "nameserver 8.8.8.8" > $DEBIANPATH/etc/resolv.conf
```

Or use `/data/local/tmp/enter_debian13.sh` for interactive `su - flux`.

### 5.4 Install benchmark packages (both guests)

**chroot:**

```bash
busybox chroot /data/local/tmp/chrootDebian13 /bin/bash -lc \
  'export DEBIAN_FRONTEND=noninteractive
   apt-get update -qq
   apt-get install -y sysbench stress-ng mbw fio'
```

**proot (app UID — not root):**

```bash
/system/bin/su u0_a415 -c \
  '/data/local/tmp/nativecode_proot.sh rootcmd \
   "export DEBIAN_FRONTEND=noninteractive; apt-get update -qq && apt-get install -y sysbench stress-ng mbw fio"'
```

### 5.5 Stage bench script into guests

```bash
cp /data/local/tmp/bench_nongpu.sh /data/local/tmp/chrootDebian13/tmp/
cp /data/local/tmp/bench_nongpu.sh \
  /data/data/com.zenithblue.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs/tmp/
```

### 5.6 Run chroot suite

```bash
busybox chroot /data/local/tmp/chrootDebian13 /bin/su - flux -c \
  'BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_nongpu.sh chroot /home/flux/bench_results'
```

### 5.7 Run proot suite

```bash
/system/bin/su u0_a415 -c \
  '/data/local/tmp/nativecode_proot.sh cmd \
   "BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_nongpu.sh proot /home/flux/bench_results"'
```

### 5.8 Collect summaries

| Env | Path on device |
|-----|----------------|
| chroot | `/data/local/tmp/chrootDebian13/home/flux/bench_results/chroot.summary.txt` |
| proot | `…/proot-distro/containers/debian/rootfs/home/flux/bench_results/proot.summary.txt` |
| full logs | same dir, `*.full.log` |

```bash
adb -s 192.168.1.52:43055 pull \
  /data/local/tmp/chrootDebian13/home/flux/bench_results/chroot.summary.txt .
```

### 5.9 What the script runs (in order)

1. Header: `uname`, `nproc`, date  
2. `sysbench cpu --cpu-max-prime=20000 --threads=$(nproc) run`  
3. `sysbench memory --memory-block-size=1M --memory-total-size=2G --threads=$(nproc) run`  
4. `stress-ng --cpu $(nproc) --timeout 20s --metrics-brief`  
5. `mbw -n 5 64`  
6. `dd` write 256 MiB `conv=fdatasync`  
7. `dd` read 256 MiB to `/dev/null`  
8. `fio` seq write / seq read / randrw 4k (15 s) — may fail in chroot  

**Not run:** any GPU, DRM, glmark, eglinfo, vulkaninfo.

---

## 6. How to re-test (checklist)

### Preconditions

- [ ] Wireless or USB ADB; `adb devices` shows device  
- [ ] KernelSU root in `adb shell`  
- [ ] `com.zenithblue.nativecode` installed; both rootfs trees present  
- [ ] Screen usable / not mid-crash recovery (benches are lighter without GPU, still warm the SoC)  
- [ ] Prefer charging + cool phone; avoid thermal throttling mid-run  

### Quick re-run

```bash
SERIAL=192.168.1.52:43055   # update if port changed
adb connect "$SERIAL"

# chroot
adb -s "$SERIAL" shell 'BB=/data/adb/ksu/bin/busybox; D=/data/local/tmp/chrootDebian13
  $BB mount --bind /dev $D/dev; $BB mount --bind /sys $D/sys
  $BB mount -t proc proc $D/proc; $BB mount -t devpts devpts $D/dev/pts
  $BB chroot $D /bin/su - flux -c \
    "BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_nongpu.sh chroot /home/flux/bench_results"'

# proot
adb -s "$SERIAL" shell '/system/bin/su u0_a415 -c \
  "/data/local/tmp/nativecode_proot.sh cmd \
   \"BENCH_WORKDIR=/home/flux/bench_io bash /tmp/bench_nongpu.sh proot /home/flux/bench_results\""'
```

### Sanity checks before trusting numbers

| Check | Expect |
|-------|--------|
| proot `uname -r` | contains `PRoot` |
| chroot `uname -r` | real Android kernel string |
| proot package name | paths under `com.zenithblue.nativecode` only |
| `nproc` | 8 on this device |
| fio in chroot | `shmget` fail is **normal** |
| GPU tools | **do not** require for this report |

### Optional single-metric drills

```bash
# CPU only
sysbench cpu --cpu-max-prime=20000 --threads=$(nproc) run

# Memory only
sysbench memory --memory-block-size=1M --memory-total-size=2G --threads=$(nproc) run
mbw -n 5 64

# Disk only
dd if=/dev/zero of=/home/flux/bench_io/dd.bin bs=1M count=256 conv=fdatasync
dd if=/home/flux/bench_io/dd.bin of=/dev/null bs=1M
```

---

## 7. Scripts used on device

### 7.1 `/data/local/tmp/nativecode_proot.sh`

```sh
#!/system/bin/sh
set -e
PREFIX=/data/data/com.zenithblue.nativecode/files/usr
. "$PREFIX/etc/fluxlinux-host.env"
export TERMUX_APP__PACKAGE_NAME=com.zenithblue.nativecode
export TERMUX__PREFIX="$PREFIX"
export TERMUX__HOME=/data/data/com.zenithblue.nativecode/files/home
export PREFIX TERMUX__PREFIX TERMUX__HOME
export PATH="$PREFIX/bin:$PREFIX/bin/applets:/system/bin:/system/xbin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export HOME="$TERMUX__HOME"
export TMPDIR="$PREFIX/tmp"
export PROOT_TMP_DIR="$PREFIX/tmp"
export LANG=C.UTF-8

MODE="${1:-login}"
shift || true
case "$MODE" in
  login)   exec "$PREFIX/bin/proot-distro" login debian --user flux --shared-tmp -- "$@" ;;
  root)    exec "$PREFIX/bin/proot-distro" login debian --shared-tmp -- "$@" ;;
  cmd)     exec "$PREFIX/bin/proot-distro" login debian --user flux --shared-tmp -- bash -lc "$*" ;;
  rootcmd) exec "$PREFIX/bin/proot-distro" login debian --shared-tmp -- bash -lc "$*" ;;
  *) echo "usage: $0 login|root|cmd|rootcmd ..." >&2; exit 2 ;;
esac
```

### 7.2 `/data/local/tmp/bench_nongpu.sh` (summary)

Runs in order: sysbench cpu → sysbench memory → stress-ng cpu 20s → mbw → dd write/read → fio seqw/seqr/rand.  
Writes `$OUTDIR/${LABEL}.summary.txt` and `.full.log`.

---

## 8. Raw numeric dump (primary run 2026-07-29)

### chroot.summary (excerpt)

```text
LABEL=chroot
UNAME=Linux localhost 6.1.134-android14-11-g246384388afb ... aarch64 GNU/Linux
NPROC=8
sysbench cpu events/s: 5870.66  p95: 2.07 ms
sysbench memory: 45268.21 MiB/s
stress-ng cpu bogo ops/s real: 1559.10
mbw AVG MEMCPY 9021.454  DUMB 14560.015  MCBLOCK 18849.031
dd write: 1.1 GB/s   dd read: 6.8 GB/s
fio: shmget: Function not implemented (all three)
```

### proot.summary (excerpt)

```text
LABEL=proot
UNAME=Linux localhost 6.17.0-PRoot-Distro ... aarch64 GNU/Linux
NPROC=8
sysbench cpu events/s: 5869.23  p95: 2.07 ms
sysbench memory: 39987.20 MiB/s
stress-ng cpu bogo ops/s real: 1540.83
mbw AVG MEMCPY 14415.064  DUMB 16900.813  MCBLOCK 23043.134
dd write: 988 MB/s   dd read: 3.3 GB/s
fio seq write: 1219 MiB/s
fio seq read:  1497 MiB/s
fio randrw 4k: read ~9339 IOPS / write ~9352 IOPS
```

---

## 9. Product interpretation (NativeCode)

| Question | Answer on MT6897 / Mali |
|----------|-------------------------|
| Is proot “slow CPU”? | **No** on this sample — parity with chroot |
| Is proot “slow RAM”? | **Slightly** on sysbench memory only (~12%) |
| Is proot “slow disk”? | **Yes for reads** (~2×); writes mild |
| Prefer chroot when? | KernelSU available + heavy I/O builds / large trees |
| Prefer proot when? | No root, or CPU-bound light CLI — **acceptable here** |
| GPU? | Mali → **virgl** path; **no Turnip**; not in this benchmark |

**One-line summary:** On this weaker MediaTek device, **proot’s bottleneck is storage (especially sequential read), not CPU or GPU-style intercept**; memory tax is mild unlike the Adreno baseline.
