# Host scripts: remove `termux_tweaks` + fix `flux_install` one-click

**Date:** 2026-07-28  
**Scope:** host asset scripts and app wiring (scripts page + install path)  
**Related:** `docs/plan/host-setup-termux-package-env.md` (package path SSOT)

---

## Summary

| Item | Decision | Result |
|------|----------|--------|
| `termux_tweaks.sh` | **Delete** — not required for product | Asset + UI/deploy wiring removed |
| `flux_install.sh` | **Keep + harden** for scripts-page testing | Default `debian` + local setup; onboarding base64 path unchanged |

---

## 1. `termux_tweaks.sh` — removed

### Why

- Cosmetic only: Oh My Zsh, host colors, fastfetch, font check, `chsh`.
- Not part of automatic onboarding (already excluded; caused past setup noise).
- Real guest polish lives in `setup_customization_debian.sh` (Debian proot/chroot path).
- Hard network deps + missing host packages (`zsh`/`git`/`fastfetch` not installed by `setup_termux.sh`).
- Font step was a no-op (never shipped a font into the script path).
- Marker `~/.fluxlinux/termux_tweaks.done` written but never checked.

### Code / asset changes

| Location | Change |
|----------|--------|
| `app/src/main/assets/scripts/termux_tweaks.sh` | **Deleted** |
| `MainActivity` host scripts list | Removed card |
| `MainActivity.deployScripts()` | Removed from deploy array + `tweaks` asset branch |
| `MainActivity` script run asset resolver | Removed `tweaks` branch |
| `OnboardingActivity.deployScripts()` | Same deploy + branch removal |

### Install path

Install never ran tweaks after earlier handoff work. After this change there is **zero** `termux_tweaks` / `tweaks` reference under `app/`.

### Device note

Old installs may still have leftover `files/home/termux_tweaks.sh` or `~/.fluxlinux/termux_tweaks.done`. Harmless; clear app data or delete manually if desired.

### Optional docs still mentioning tweaks (stale)

- `docs/README.md` (old setup step list)
- `docs/agent-handoff.md` (historical “excluded” note)
- `progress.md` / other session notes

Not updated in this pass; treat as historical.

---

## 2. `flux_install.sh` — one-click + review fixes

### Problem

Scripts page runs host scripts **with no args** (`RUN ON HOST` → `HostCommandBuilder` → script path only).

Old behavior:

```text
DISTRO=$1          # empty
SETUP_B64=$2       # empty
→ proot-distro install "" / broken container path
→ no guest configuration
```

Onboarding was fine: `flux_install.sh debian <base64(setup_debian_family.sh)>`.

### Goals

1. **One-click scripts page:** install Debian proot + run `setup_debian_family.sh` (user `flux`, XFCE bits, VNC xstartup).
2. **Keep onboarding contract:** `$1` distro + `$2` base64 setup payload still work.
3. **Path SSOT:** source `fluxlinux-host.env`; never stock `com.termux` defaults as primary.

### Behavior after fix

```text
Usage:
  flux_install.sh                      # one-click: debian + local setup_debian_family.sh
  flux_install.sh debian               # same, install only if no local setup found
  flux_install.sh debian <BASE64>      # onboarding: decode payload → guest setup
  flux_install.sh termux [BASE64]      # legacy host-native branch (rarely used)
```

**Setup resolution order:**

1. If `$2` non-empty and not `null` → base64-decode to `$TMPDIR/flux_setup_temp.sh`.
2. Else if local file found → copy to same temp path:
   - `$(dirname $0)/setup_debian_family.sh`
   - `$HOME/setup_debian_family.sh`
   - `$HOME/scripts/setup_debian_family.sh`
3. Else → install only (log + skip configure).

**Guest run (non-`termux` distro):**

```text
$PREFIX/bin/python $PREFIX/bin/proot-distro login $DISTRO --shared-tmp -- \
  bash -c "bash /tmp/flux_setup_temp.sh $DISTRO"
```

Host writes setup under `$PREFIX/tmp` (`TMPDIR`); `--shared-tmp` exposes it as guest `/tmp`.

**Install skip:** if `$PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs` exists and has `bin/sh`, skip `proot-distro install` (still run setup if resolved).

**Hard fails:**

- Missing `$PREFIX/bin/python` or `proot-distro`
- `proot-distro install` non-zero
- base64 decode / copy fail
- guest setup non-zero

### Other script hardening

| Change | Why |
|--------|-----|
| `DISTRO="${1:-debian}"` | Scripts-page no-args |
| Absolute `$PREFIX/bin/python` + `proot-distro` | Don’t rely on ambient PATH alone |
| Re-export `PATH` / `LD_LIBRARY_PATH` after profile | Profile source can drift env |
| `mkdir -p "$TMPDIR"` | Shared-tmp write target exists |
| `set -u` | Catch unset vars; still use explicit exit codes for install/setup |

### App UI copy

Scripts page host card description updated to:

> One-click: install Debian proot + setup_debian_family (user/xfce/vnc).

Deploy already includes `setup_debian_family.sh` in `MainActivity.deployScripts()` so one-click can find the local file under `$HOME`.

### Onboarding path (unchanged contract)

```text
setup_termux.sh
  → flux_install.sh debian <base64 setup_debian_family.sh>
  → proot-distro login … setup_hw_accel_debian.sh
  → proot-distro login … setup_customization_debian.sh   (if enabled)
```

`flux_install` still only does base install + family setup when payload/local setup present. GPU/custom remain separate stages.

---

## 3. What this is not

- Does **not** reintroduce host Oh My Zsh / fastfetch / host colors.
- Does **not** replace guest `setup_customization_debian.sh` / `setup_hw_accel_debian.sh`.
- Does **not** change chroot Debian 13 flow (`setup_debian13_chroot.sh`).
- Does **not** rewrite bootstrap `.deb` paths (see package-env plan).

---

## 4. Verify checklist

### Tweaks gone

- [ ] No `termux_tweaks` under `app/`
- [ ] Scripts page host list: `setup_termux`, `flux_install`, `start_gui`, `stop_gui` only
- [ ] Fresh install log has no tweaks stage

### One-click `flux_install`

- [ ] Scripts page → **RUN ON HOST** on `flux_install.sh` with no manual args
- [ ] Log shows `DISTRO=debian` and either “Configuring from local setup” or skip install if rootfs exists
- [ ] Guest has user `flux`, sudoers drop-in, `~/.vnc/xstartup` after setup
- [ ] Onboarding still succeeds with base64 path (decode + configure)

### Paths

- [ ] Env dump shows `TERMUX_APP__PACKAGE_NAME=com.zenithblue.nativecode` and matching `PREFIX`/`HOME`
- [ ] No stock `com.termux` install target in install/login lines

---

## 5. Files touched

| Path | Action |
|------|--------|
| `app/src/main/assets/scripts/termux_tweaks.sh` | Deleted |
| `app/src/main/assets/scripts/flux_install.sh` | Rewritten (defaults + local setup + guards) |
| `app/src/main/java/.../MainActivity.kt` | Drop tweaks deploy/UI; flux_install card text |
| `app/src/main/java/.../OnboardingActivity.kt` | Drop tweaks deploy |
| `docs/plan/host-scripts-tweaks-flux-install.md` | This plan |

---

## 6. Follow-ups (optional)

1. Delete stale on-device `files/home/termux_tweaks.sh` on next `deployScripts` (explicit unlink).
2. Refresh `docs/README.md` setup list (drop tweaks step).
3. Scripts page: optional “force reinstall” flag if skip-rootfs blocks testing wipe.
4. Marker semantics: only write `~/.fluxlinux_distro_*_installed` after setup mode actually ran.

---

## 7. Superseded install source (next plan)

**Current (this doc / script):**  
`proot-distro install debian` — live pull via bundled proot-distro (OCI/registry).

**Next:** download a **pinned rootfs archive**, then:

```sh
proot-distro install ./debian_proot_rootfs.tar.xz --name debian
```

Full design: **`docs/plan/proot-debian-rootfs-local-install.md`**  
Upstream ref: [termux/proot-distro](https://github.com/termux/proot-distro) (`install` from local path / URL).
