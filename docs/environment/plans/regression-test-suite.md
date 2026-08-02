# Regression test suite — NativeCode proot/chroot performance work

**Purpose:** After every plan task, prove proot (and chroot) still achieve **development, package, and AI CLI** work — and that performance changes did not break stability or functionality.

| Field | Value |
|-------|--------|
| **Location** | `docs/environment/plans/suite/` |
| **Runner** | `suite/run_regression.sh` |
| **Results** | `suite/results/<timestamp>/` (gitignored if under device; local host path configurable) |
| **Envs under test** | `chroot` · `proot-distro` (compat) · `proot-fast` (when implemented) |

---

## 1. Priority levels

| Level | Meaning | Gate |
|-------|---------|------|
| **P0** | Must pass or **block merge / stop task** | correctness + smoke |
| **P1** | Should pass for “everyday NativeCode” claim | dev + packages + AI |
| **P2** | Extended / optional (network, GPU, long compile) | report only |

---

## 2. Environments & entry commands

Assumes ADB root (KernelSU) and helpers on device.

| Env ID | Entry (template) |
|--------|------------------|
| `chroot` | `busybox chroot /data/local/tmp/chrootDebian13 /bin/su - flux -c '…'` |
| `proot-distro` | `/system/bin/su u0_aXXX -c '/data/local/tmp/nativecode_proot.sh cmd "…"'` |
| `proot-fast` | `/system/bin/su u0_aXXX -c '/data/local/tmp/nativecode_proot_fast.sh exec -- …'` |

Package must be `com.zenithblue.nativecode`. Suite auto-detects app UID.

---

## 3. Full test catalog

### 3.1 P0 — Stability & core identity

| ID | Name | What | Pass criteria |
|----|------|------|---------------|
| **P0-01** | host_package | Prefix/env is NativeCode | no `com.termux` as active package |
| **P0-02** | proot_binary_elf | `proot` is real ELF | not a leftover shell wrapper |
| **P0-03** | env_true | run `true` | exit 0 |
| **P0-04** | env_echo | `echo regression-ok` | stdout contains token |
| **P0-05** | env_id_user | `id -un` / `whoami` | flux (or root for rootcmd) |
| **P0-06** | env_pwd_home | `pwd` under home | `/home/flux` (or documented cwd) |
| **P0-07** | env_sh_dash | `/bin/sh -c 'echo shok'` | works; sh is dash preferred |
| **P0-08** | env_tmp_write | write+read `$TMPDIR` or `/tmp` | round-trip OK |
| **P0-09** | env_proc_sys | `test -r /proc/self/status` | OK |
| **P0-10** | env_dns_or_skip | `getent hosts` / `ping -c1` | pass or **SKIP** if offline |
| **P0-11** | distro_still_works | original `nativecode_proot.sh cmd true` | exit 0 (always) |
| **P0-12** | chroot_still_works | chroot true path | exit 0 if chroot present else SKIP |
| **P0-13** | no_termux_default | proot rootfs path | contains `com.zenithblue.nativecode` |
| **P0-14** | kill_clean | after true, no stuck runaway proot storm | process count sanity |

### 3.2 P0 — Filesystem & packages (minimal)

| ID | Name | What | Pass criteria |
|----|------|------|---------------|
| **P0-20** | fs_touch | create file in `$HOME` | success |
| **P0-21** | fs_mkdir_rm | mkdir/rmdir | success |
| **P0-22** | apt_get_version | `apt-get --version` | runs |
| **P0-23** | dpkg_version | `dpkg --version` | runs |
| **P0-24** | apt_update | `apt-get update` | exit 0 if network else SKIP |
| **P0-25** | apt_install_hello | install `hello` (or `cowsay`) then remove | install+run+purge OK if network |

### 3.3 P1 — Development toolchain

| ID | Name | What | Pass criteria |
|----|------|------|---------------|
| **P1-01** | cc_present | `cc` or `gcc` or `clang` | at least one or SKIP+note |
| **P1-02** | compile_hello_c | compile+run fixture `hello.c` | prints expected string |
| **P1-03** | make_present | `make -v` | OK or SKIP |
| **P1-04** | cmake_present | `cmake --version` | OK or SKIP |
| **P1-05** | git_init_commit | git init, config local, commit file | commit hash exists |
| **P1-06** | git_status | `git status` clean after commit | OK |
| **P1-07** | python3_print | `python3 -c 'print(42)'` | `42` |
| **P1-08** | python3_venv | create venv, pip install nothing / ensurepip | venv python works |
| **P1-09** | node_present | `node -v` / `npm -v` | OK or SKIP |
| **P1-10** | node_hello | `node -e "console.log('ok')"` | ok |
| **P1-11** | rustc_or_skip | `rustc -V` | OK or SKIP |
| **P1-12** | go_or_skip | `go version` | OK or SKIP |
| **P1-13** | pkg_config | `pkg-config --version` | OK or SKIP |
| **P1-14** | shared_lib_link | compile tiny .so + main if cc present | runs |
| **P1-15** | tar_gzip_roundtrip | tar czf / xz of tree, extract | content match |
| **P1-16** | ssh_keygen | `ssh-keygen -t ed25519 -N '' -f /tmp/t` | key files exist |
| **P1-17** | curl_https | `curl -fsSIL https://example.com` | 200/301/302 or SKIP offline |
| **P1-18** | openssl_version | `openssl version` | OK or SKIP |

### 3.4 P1 — AI / agent CLI related

> Goal: tools used in NativeCode AI workflows can **start, print version/help, and run a trivial non-network or offline-safe action**.  
> Full cloud auth is **P2** (secrets not in suite).

| ID | Name | What | Pass criteria |
|----|------|------|---------------|
| **P1-30** | which_common | resolve `python3`, `bash`, `git` | all found |
| **P1-31** | pip_list_or_skip | `python3 -m pip --version` | OK or SKIP |
| **P1-32** | opencode_or_skip | `opencode --version` or `opencode -h` | exit 0 or SKIP if not installed |
| **P1-33** | codex_or_skip | `codex --help` / `codex -V` | OK or SKIP |
| **P1-34** | claude_or_skip | `claude --help` / version | OK or SKIP |
| **P1-35** | aider_or_skip | `aider --help` | OK or SKIP |
| **P1-36** | llm_or_skip | `llm --help` / `llm -v` | OK or SKIP |
| **P1-37** | ollama_or_skip | `ollama --version` | OK or SKIP |
| **P1-38** | npx_help_or_skip | `npx --yes --help` or `npm exec --help` | OK or SKIP |
| **P1-39** | ripgrep_or_grep | `rg --version` or `grep` | can search file content |
| **P1-40** | jq_or_python_json | parse JSON | OK |
| **P1-41** | tmux_or_skip | `tmux -V` | OK or SKIP |
| **P1-42** | editor_or_skip | `nvim -h` / `vim --version` / `nano -V` | one OK or SKIP |
| **P1-43** | ai_script_smoke | run `suite/guest/ai_offline_smoke.sh` | exit 0 (pure local python) |
| **P1-44** | path_no_host_leak_critical | critical bins resolve inside guest | guest paths |

### 3.5 P1 — Desktop / GPU soft checks (no DRM)

| ID | Name | What | Pass criteria |
|----|------|------|---------------|
| **P1-50** | display_env_or_skip | `echo $DISPLAY` under gui helper | informational |
| **P1-51** | mesa_icd_files | list `/usr/share/vulkan/icd.d` if present | no crash |
| **P1-52** | no_drm_glmark | suite must **not** invoke `glmark2-*-drm` | static check on scripts |
| **P1-53** | glmark_offscreen_or_skip | Turnip only: `glmark2-es2 -b build --off-screen` under xvfb | SKIP on Mali / no glmark |

### 3.6 P2 — Extended (optional long)

| ID | Name | What |
|----|------|------|
| **P2-01** | sysbench_cpu | full sysbench cpu |
| **P2-02** | sysbench_mem | full sysbench memory |
| **P2-03** | dd_256 | dd read/write 256 MiB |
| **P2-04** | compile_small_project | multi-file make |
| **P2-05** | apt_install_build_essential | if missing |
| **P2-06** | pip_install_requests | network |
| **P2-07** | npm_init_local | local package.json |
| **P2-08** | concurrent_procs | 4× `true` parallel |
| **P2-09** | large_untar | many-small-files tarball stress |
| **P2-10** | interactive_login_timeout | expect/script login exit |

---

## 4. When to run

| Event | Suite |
|-------|--------|
| End of Task 1 (cleanup) | P0 only (manual or `./run_regression.sh --p0`) |
| End of Task 2 (fast launcher) | **P0 + P1** on proot-distro **and** proot-fast |
| End of Task 3 (rootfs) | **P0 + P1** |
| End of Task 4 (bench) | P0 smoke after benches + P2 perf optional |
| End of Task 5–6 | full P0+P1; P2 as time allows |
| Any device script push | at least P0 |

---

## 5. How to run

### From host (recommended)

```bash
# MediaTek example
export NC_ADB_SERIAL=192.168.1.52:43055
export NC_APP_UID=u0_a415          # optional auto-detect
export NC_ENVS="proot-distro,chroot"  # add proot-fast when ready

cd docs/environment/plans/suite
./run_regression.sh --p0
./run_regression.sh --p0 --p1
./run_regression.sh --all            # includes P2 if tools present
```

### On device (if scripts pushed)

```bash
sh /data/local/tmp/nc_suite/run_regression.sh --p0
```

### Results

```text
suite/results/<UTC-timestamp>/
  summary.md
  summary.json
  env-proot-distro.log
  env-chroot.log
  env-proot-fast.log
```

Exit code: **0** if all executed P0 pass (SKIP ok); **1** if any P0 FAIL; **2** if harness error.

---

## 6. Pass / fail / skip rules

| Result | Meaning |
|--------|---------|
| **PASS** | assertion held |
| **FAIL** | assertion failed — block task |
| **SKIP** | dependency missing or offline — not a fail |
| **XFAIL** | known env limitation (e.g. fio shmget in chroot) — counted separately |

Offline mode: set `NC_OFFLINE=1` to force network tests SKIP.

---

## 7. Coverage map vs user goals

| User goal | Tests |
|-----------|--------|
| Proot faster, not unstable | P0-01…14 + launch timing in perf helper |
| Development works | P1-01…18 |
| Packages work | P0-22…25, P2-05 |
| AI tool CLIs work | P1-30…44 |
| No GPU disaster | P1-52, ban DRM in scripts |
| chroot not broken | P0-12 |
| Compat path remains | P0-11 |

---

## 8. Maintenance

- Add new AI binaries as **SKIP-friendly** version/help checks.
- Never add DRM glmark.
- Keep fixtures under `suite/guest/fixtures/` tiny.
- Update this MD when adding IDs; keep IDs stable.

---

## 9. Initial baseline status (pre-Task-2)

| Suite | Status |
|-------|--------|
| Scripts authored | **yes** (this commit) |
| Executed on device | **no** (stop per plan: create suite only) |
| P0 expected on current MediaTek | should pass for proot-distro + chroot (manual smoke already OK for true) |

After Task 2 implementation, first official run must attach `summary.md` here or under `results/`.
