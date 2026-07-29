# Task 3 — Rootfs hardening for speed (safe)

| Field | Value |
|-------|--------|
| **Status** | **DONE** (script + proot/chroot `--safe` + regression PASS) |
| **Depends on** | Task 2 preferred (measure both paths) |
| **Risk target** | Disposable-container safe defaults only; opt-in for aggressive flags |
| **Device** | MediaTek duchamp `192.168.1.52:43055` · app `u0_a415` |
| **Date** | 2026-07-29 |

---

## 1. Objective

One-shot (or post-install) script inside Debian trixie guest to:

- Pre-generate caches (ldconfig, fonts, mime, locales needed only).
- Confirm `/bin/sh` → dash (or busybox ash).
- Align `/tmp` / `/var/tmp` / build caches with tmpfs or host shared tmp.
- Reduce path probes (docs/man exclude, apt no-recommends) **without** removing required runtime packages.

---

## 2. Deliverables

| Item | Path | Status |
|------|------|--------|
| Guest script | `app/src/main/assets/scripts/optimize_debian_rootfs_perf.sh` | **done** |
| Device / suite | `/data/local/tmp/optimize_debian_rootfs_perf.sh` · `plans/suite/` copy | **done** |
| Modes | `--safe` (default) · `--aggressive` (opt-in) · `--dry-run` · `--quiet` | **done** |
| Log | §6 Improvements | **done** |

### How to run

```bash
# proot (root inside guest)
nativecode_proot_fast.sh root-exec -- sh /tmp/optimize_debian_rootfs_perf.sh --safe

# chroot
busybox chroot $CHROOT env PATH=/usr/sbin:/usr/bin:/sbin:/bin \
  sh /tmp/optimize_debian_rootfs_perf.sh --safe
```

Stage script into shared tmp first (`$PREFIX/tmp` for proot).

**Onboarding:** call `--safe` once after rootfs install; **never** default `--aggressive`.

---

## 3. Safe mode (`--safe`) checklist — implemented

| Action | Result on proot guest |
|--------|------------------------|
| `ldconfig` | OK |
| `update-mime-database` / `fc-cache` | OK |
| `locale-gen` / C.UTF-8 | OK (appended C.UTF-8 to locale.gen) |
| `update-ca-certificates` | OK |
| apt conf `99nativecode-perf` | CHG (no-recommends, Languages none) |
| dpkg path-exclude man/doc/locale | CHG (`99nativecode-path-exclude`) |
| `policy-rc.d` → 101 | CHG |
| `/tmp` mode 1777 | CHG (was 700 on shared tmp) |
| `/bin/sh` → dash | OK (already dash) |
| flux shell | **unchanged** `/bin/zsh` |
| Mesa/GPU packages | **kept** (logged, not removed) |

## 4. Aggressive mode (`--aggressive`) — opt-in only

| Action | Warning |
|--------|---------|
| `eatmydata` note if installed | can lose data on crash |
| `force-unsafe-io` | same |
| `git core.fsync=none` | data risk |

**Not run** on device in this task (safe only). Never enable in default onboarding.

---

## 5. Implementation steps (completed)

1. Authored script with `--safe` / `--aggressive` / PATH fix for bare chroot.  
2. Ran under **proot** and **chroot** (`--safe`).  
3. Regression suite P0 + P1 all envs.  
4. Measured cold `true` / `python3 -c pass` / `apt-get update`.  
5. Logged improvements.

---

## 6. Improvements log

| Date | Mode | Change | Before | After | Regression |
|------|------|--------|--------|-------|------------|
| 2026-07-29 | `--safe` proot | apt/dpkg conf, policy-rc.d, ldconfig, caches, `/tmp` 1777 | `/tmp` mode 700; no NC apt/dpkg conf; no policy-rc.d | conf files present; `/tmp` `drwxrwxrwt`; mesa kept; flux zsh kept | **PASS** T3-R01..R07 + suite |
| 2026-07-29 | `--safe` chroot | same | similar | summary changed=10 failed=0 | chroot suite green |

### Micro-benchmarks (proot-fast, on-device, n=3)

| Metric | Before | After | Note |
|--------|--------|-------|------|
| `exec -- true` mean | **275 ms** | **240 ms** | launch already Task-2 bound; small noise |
| `python3 -c pass` mean | **318 ms** | **368 ms** | within noise; not slower in a meaningful way |
| `apt-get update` | **~8.5 s** exit 0 | **~8.4 s** exit 0 | DNS to deb.debian.org flaky (`Temporary failure resolving`); apt still exit 0 using old indexes + `_apt` proot warn |

**Honest takeaway:** Task 3 is **hygiene + first-run cache + smaller future apt I/O**, not another 17× launch win. Launch already fixed in Task 2. Path-exclude helps **future** package installs (does not strip already-installed man/doc).

### Suite results

`docs/environment/plans/suite/results/20260729T144025Z`

| Env | p0 | p1_dev | p1_ai |
|-----|----|--------|-------|
| proot-fast | 13/0 | 11 pass / 5 skip | 10 pass / 5 skip |
| proot-distro | 13/0 | 11 / 5 | 10 / 5 |
| chroot | 13/0 | 12 / 4 | 10 / 5 |
| host P0 | 7/0 | — | — |

**REGRESSION OVERALL: PASS** (~336 s wall; chroot p1_ai slowest).

---

## 7. Task-3 regression gates

| ID | Check | Result |
|----|-------|--------|
| T3-R01 | `/bin/sh` still dash | **PASS** (`dash`) |
| T3-R02 | `ldconfig` succeeds | **PASS** |
| T3-R03 | `python3 -c 'print(1)'` works | **PASS** |
| T3-R04 | `apt-get update` works (network) | **PASS** exit 0 (DNS flaky; old indexes) |
| T3-R05 | flux home + shell still valid | **PASS** `flux … /bin/zsh` + home |
| T3-R06 | Suite P0 green | **PASS** all envs |
| T3-R07 | No removal of GPU Mesa packages | **PASS** `mesa-vulkan-drivers` kept |

---

## 8. Follow-ups

- Commitment #3: bake `--safe` outcomes into **shipped** rootfs image / onboarding hook.
- Fix guest DNS / `_apt` proot perms for reliable `apt-get update`.
- Optional `python3-venv` package for P1-08.
- Do **not** ship `--aggressive` in onboarding.
