# Task 2 — Minimal fast launcher (plain proot)

| Field | Value |
|-------|--------|
| **Status** | **DONE** (implemented + regression PASS) |
| **Depends on** | Task 1 complete |
| **Risk target** | No loss of functionality; default path unchanged |
| **Device** | MediaTek MT6897 / duchamp `192.168.1.52:43055` · app `u0_a415` |
| **Date** | 2026-07-29 |

---

## 1. Objective

Design and implement an **optional** fast path:

- Plain `proot` (not full `proot-distro login` Python stack for every command).
- Minimum binds per **profile**.
- Direct-exec target binary via clean env (no login profile) for non-interactive.
- Interactive shell still available.
- Preserve flux user, GPU env, refuse `com.termux` paths.
- Keep existing `proot-distro` + `nativecode_proot.sh` as **compat** default.

---

## 2. Deliverables

| Item | Path | Status |
|------|------|--------|
| Device helper | `/data/local/tmp/nativecode_proot_fast.sh` | **deployed** |
| Repo asset | `app/src/main/assets/scripts/nativecode_proot_fast.sh` | **done** |
| Suite copy | `docs/environment/plans/suite/nativecode_proot_fast.sh` | **done** |
| Docs update | this file §6 Improvements | **done** |
| App integration | `ProotCommandBuilder` flag | **not done** (optional later) |

---

## 3. API (shipped)

```bash
nativecode_proot_fast.sh exec   [--profile cli|gpu-turnip|gpu-virgl|compat] -- CMD [ARGS...]
nativecode_proot_fast.sh sh     [--profile ...] -- 'shell string'   # /bin/sh -c (dash)
nativecode_proot_fast.sh login  [--shell zsh|bash] [--profile ...]  # interactive -l
nativecode_proot_fast.sh root-exec -- CMD [ARGS...]
```

### Profiles

| Profile | Binds | Use |
|---------|-------|-----|
| `cli` | `/dev` `/proc` `/sys` urandom, shared `/tmp`, shm, optional `/sdcard` | default for tools/AI CLI |
| `gpu-turnip` | cli + `/system` `/vendor` `/apex` + linkerconfig + kgsl if present | Adreno Turnip |
| `gpu-virgl` | cli + X11 socket + virgl-related needs | Mali / virgl |
| `compat` | full proot-distro-equivalent bind set | bug-for-bug fallback |

### Flags

- Always: `--kill-on-exit`, `--link2symlink`, `-L`, `--change-id` from guest passwd, `--rootfs`, `--cwd`
- `--sysvipc` only if `NC_PROOT_SYSVIPC=1` or profile `compat`
- Fake kernel-release: optional (`NC_PROOT_FAKE_UNAME=1`), default off

### Guest PATH (functionality)

Without login shell, still resolve AI/dev tools:

- `$HOME/.local/bin`, `$HOME/bin`, `$HOME/.cargo/bin`, `/opt/nodejs/bin`
- Latest `$HOME/.nvm/versions/node/v*/bin` (host-side scan of rootfs; **no** `nvm.sh`)
- Standard `/usr/local/...:/usr/...:/bin`

### Env safety

```bash
TERMUX_APP__PACKAGE_NAME=com.ivarna.nativecode
# refuse PREFIX/ROOTFS containing com.termux
# refuse missing ROOTFS / proot binary
```

---

## 4. Implementation steps (completed)

1. Wrote script with strict mode + package checks.  
2. Parse flux uid/gid from guest `/etc/passwd`.  
3. Bind builders per profile.  
4. `exec` / `sh` / `login` / `root-exec`.  
5. Smoke MediaTek: all green.  
6. Regression suite P0+P1 green (proot-distro + proot-fast).  
7. Logged improvements below.

---

## 5. Non-goals (still)

- Replace default app terminal entry  
- Patch `libproot.so`  
- Disable `--link2symlink`  
- Force eatmydata  

---

## 6. Improvements log

| Date | Change | Metric before | Metric after | Regression |
|------|--------|---------------|--------------|------------|
| 2026-07-29 | Plain proot `cli` profile + direct `env -i` exec (no Python proot-distro, no `zsh -c "bash -lc"`) | `nativecode_proot.sh cmd true` **mean 3889 ms** (n=5, on-device) | `nativecode_proot_fast.sh exec -- true` **mean 222 ms** (n=5) → **~5.7%** of distro (~**17.5×** faster) | **PASS** T2-R01..R07 + suite P0/P1 |
| 2026-07-29 | `compat` profile full binds | — | `exec --profile compat -- true` mean **219 ms** (still no Python) | PASS |
| 2026-07-29 | Guest PATH: nvm default node + `~/.local/bin` without oh-my-zsh | first P1 AI: SKIP opencode/node | P1 AI: **opencode/codex/claude/npx/node PASS** | PASS re-run |
| 2026-07-29 | Suite harness: stage scripts in `$PREFIX/tmp` (shared-tmp), not rootfs `/tmp` | proot guest scripts “No such file” | proot-distro + proot-fast guest P0/P1 run | PASS |

### Launch scoreboard (MediaTek, warm, on-device `date +%s%3N`)

| Path | mean ms (n=5) | Notes |
|------|---------------|--------|
| proot-distro `cmd true` | **3889–4042** | Python + double shell |
| proot-fast `exec -- true` | **222–269** | target ≤50% of distro → **met** |
| proot-fast `exec -- node -v` | **314** | vs distro node **~4026** |
| proot-fast `sh -- true` | **186** | |
| proot-fast `compat true` | **219** | |

Ratio **fast/distro ≈ 0.057–0.067** (gate T2-R06 was ≤0.50).

### Results dir

`docs/environment/plans/suite/results/20260729T143443Z` (proot-fast P0+P1 final)  
Earlier: `…/20260729T143332Z` (distro + fast), `…/20260729T143232Z` (chroot + harness bug).

---

## 7. Task-2 regression gates

| ID | Check | Result |
|----|-------|--------|
| T2-R01 | `exec -- true` exit 0 | **PASS** |
| T2-R02 | `sh -- 'echo ok'` prints ok | **PASS** |
| T2-R03 | `login` can start and `exit` | **PASS** |
| T2-R04 | Package paths still NativeCode | **PASS** (refuse com.termux; rootfs under `com.ivarna.nativecode`) |
| T2-R05 | Original `nativecode_proot.sh cmd true` still works | **PASS** |
| T2-R06 | Launch time `exec -- true` ≤ 50% of distro `cmd true` | **PASS** (~5.7%) |
| T2-R07 | Suite P0 all green | **PASS** host P0 7/7; proot-fast p0 13/13; proot-distro p0 13/13 |

### Suite P1 (proot-fast final)

| Script | Result |
|--------|--------|
| p1_dev | SUMMARY pass=11 fail=0 skip=5 (venv/cmake/curl skips env-limited) |
| p1_ai | SUMMARY pass=10 fail=0 skip=5 (optional agents not installed) |

**REGRESSION OVERALL: PASS** (no FAIL lines).

---

## 8. Follow-ups (not Task 2)

- Wire optional `useFastLauncher` in app `ProotCommandBuilder` (approval).
- Task 3 rootfs optimize script + baked rootfs update.
- P1 curl skip under proot — investigate network / CAP if needed.
- `python3-venv` missing in guest — package/rootfs issue, not launcher.
