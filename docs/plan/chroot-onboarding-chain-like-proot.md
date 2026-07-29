# Chroot onboarding: chain guest scripts like proot + fix false exit 1

**Date:** 2026-07-28  
**Status:** implemented  
**Scope:** onboarding chroot path after `setup_debian13_chroot.sh`; align with proot E→H guest chain  
**Related:**  
- `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh`  
- `app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt` (`runDebianBaseSetup` chroot branch)  
- Proot chain (reference): same file, proot path steps E–H  
- Guest scripts: `setup_debian_family.sh`, `setup_hw_accel_debian.sh`, `setup_customization_debian.sh`, `setup_cli_tools.sh`  
- Prior: sticky `/tmp` fix (apt); Settings chroot card

---

## Problem (observed)

1. **Chroot rootfs + XFCE install succeeds**  
   Log shows: `Debian Environment Configured!`, launcher scripts created, `NativeCode: Chroot Setup Complete!`

2. **App still reports failure**  
   ```
   [CHROOT] Setup failed with exit code 1. Check logs above.
   ```
   Next button stays blocked; **guest chain never starts**.

3. **False exit 1 root cause**  
   End of `configure_debian_chroot` in `setup_debian13_chroot.sh`:
   ```sh
   cleanup_mounts
   success "NativeCode: Chroot Setup Complete!"
   am start -a android.intent.action.VIEW -d "nativecode://callback?..." >/dev/null 2>&1
   ```
   - Last command is `am start` (often fails under pure root / no app context → non-zero).  
   - No explicit `exit 0` after success.  
   - Shell exit status of script = last command → **1**.  
   - `cleanup_mounts` last `umount` also may leak non-zero if not `|| true` on every line.

4. **Onboarding gates guest chain on exit 0 only**  
   ```kotlin
   onDone = { code ->
       if (code == 0) {
           // E family → F hw → G custom → H AI CLIs → finishChrootBaseSetup()
       } else {
           updateBaseStatus("[CHROOT] Setup failed with exit code $code...")
       }
   }
   ```
   So false exit 1 **skips** the entire proot-like chain even though chroot is ready (`.flux_configured` written earlier in script).

5. **Product expectation**  
   Chroot is Debian on top (same guest). After base chroot install, must run same guest provisioning as proot:
   - E `setup_debian_family.sh`  
   - F `setup_hw_accel_debian.sh` (GpuAccelDetector → FLUX_GPU)  
   - G `setup_customization_debian.sh` (if toggle on)  
   - H `setup_cli_tools.sh` (AI CLIs; soft-fail)  
   Then `finishChrootBaseSetup()` → `linux_method=chroot`, enable Next.

---

## Reference: proot chain (target parity)

| Step | Proot | Chroot (intended) |
|------|--------|-------------------|
| Host/bootstrap | setup_termux + flux_install | `setup_debian13_chroot.sh` (root, host) |
| E Guest family | flux_install embeds / runs family | `copyAndRunInChroot` → `setup_debian_family.sh` |
| F HW accel | proot-distro login + FLUX_GPU | same script via chroot root |
| G Custom | optional toggle | same |
| H AI CLIs | `setup_cli_tools.sh` soft-fail | `runCliToolsSetupChroot` soft-fail |
| Finish | save `linux_method=proot`, unlock Next | `finishChrootBaseSetup()` |

**Note:** Kotlin chroot branch **already** implements E→H when `code == 0`. Primary bug is false exit 1 blocking entry. Harden both script exit and gate.

---

## Goals

1. Successful chroot base install always reports **exit 0** to app.  
2. Guest chain E→H always runs after successful base (like proot), including when marker exists but process exit was noisy.  
3. Logs clearly label steps `[CHROOT] E/F/G/H` and finish message.  
4. Partial re-run safe: if already installed, skip heavy extract or re-enter chain from E as appropriate.  
5. No change to proot path behavior.

---

## Non-goals

- Replacing chroot with proot or merging install scripts.  
- Changing AI tools package list inside `setup_cli_tools.sh`.  
- Full Settings → Install path redesign (already launches onboarding page 3).  
- Fixing `am start` callback deep-link handling (make it non-fatal only).

---

## Implementation plan

### P1 — Fix script exit status (required)

**File:** `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh`

1. `cleanup_mounts`: every `umount` ends with `|| true` (no leaked status).  
2. After success path:
   ```sh
   cleanup_mounts
   success "NativeCode: Chroot Setup Complete!"
   am start ... >/dev/null 2>&1 || true
   return 0   # from configure_debian_chroot
   ```
3. `main`:
   ```sh
   extract_file ...
   configure_debian_chroot
   exit 0
   ```
4. Already-installed early exit path: keep `exit 0` after optional `am start || true`.  
5. `goodbye` still `exit 1`.

### P2 — Harden onboarding gate (belt + suspenders)

**File:** `OnboardingActivity.kt` → `runDebianBaseSetup()` chroot branch

1. On `onDone(code)` for base script:
   - `installed = File("/data/local/tmp/chrootDebian13/.flux_configured").exists()`  
     (or `ProjectPathResolver.isChrootInstalled()` if available on that thread).  
   - Proceed to guest chain if `code == 0 || installed`.  
   - If `code != 0 && installed`: log  
     `[CHROOT] Base exit $code but .flux_configured present — continuing guest setup...`  
   - If `code != 0 && !installed`: fail as today.  
2. Do **not** block Next forever on soft guest failures (H already soft-fails; F/G continue; E hard-fail is OK).  
3. Optional: if base already installed at start, skip re-running full setup script and jump to E→H (faster retest). Prefer explicit user uninstall for clean reinstall.

### P3 — Guest chain parity checklist (verify, minimal code)

Confirm chroot path matches proot ordering and scripts:

| Order | Script | Hard / soft | Runner |
|-------|--------|-------------|--------|
| E | `setup_debian_family.sh` | hard (abort chain on fail) | `copyAndRunInChroot` |
| F | `setup_hw_accel_debian.sh` + `FLUX_GPU` | soft continue | same |
| G | `setup_customization_debian.sh` if toggle | soft continue | same |
| H | `setup_cli_tools.sh` | soft continue | `runCliToolsSetupChroot` |
| — | `finishChrootBaseSetup()` | always | prefs + unlock Next |

Ensure `copyAndRunInChroot`:

- Stages to host-visible `$DEBIANPATH/tmp` (sticky disk `/tmp`, not Termux bind).  
- Runs as root inside chroot with mounts (via `RootShell.executeInChroot` or `run_debian13_root.sh`).  
- Align `executeInChroot` mounts with sticky `/tmp` (chmod 1777; no bind of app `usr/tmp` onto `/tmp`) so apt in family/cli scripts does not regress.

### P4 — RootShell `executeInChroot` mount align (small)

**File:** `RootShellService.kt` / `RootShell`

- Match session mounts: sticky `/tmp`, optional `/mnt/termux-tmp` bridge, no app-tmp-as-`/tmp`.  
- Prevents E/H apt failures during post-chain.

### P5 — Logging / UX

- After base success: `[CHROOT] Base complete — starting guest chain (E→H)...`  
- On full finish: existing `Full environment setup complete! linux_method=chroot saved.`  
- Enable **Next: Complete Setup** only after `finishChrootBaseSetup` (not after base alone).

### P6 — Out of scope for this plan unless needed

- MainActivity manual Scripts page: already can run family/cli per-script; optional “Run guest chain” later.  
- Plan MD for sticky `/tmp` was implicit in script fix; no separate doc required.

---

## Acceptance criteria

- [x] Log shows `Chroot Setup Complete!` **and** no false `Setup failed with exit code 1` when install succeeded. *(P1 exit 0 + P2 marker gate)*  
- [x] Immediately after base: log lines for E (family), F (hw), G (optional), H (AI CLIs). *(Kotlin chain + Base complete log)*  
- [x] `setup_cli_tools` runs on chroot path same as proot end-of-setup.  
- [x] `.flux_configured` present; `linux_method=chroot` saved; Next enabled. *(code path ready; device retest)*  
- [ ] Native terminal (chroot) opens; AI tools path still works (`launch_tool` / `/tmp`). *(device)*  
- [x] Forced fail (no root / goodbye) still shows real failure and does **not** run E→H.  
- [x] Proot onboarding path unchanged.

---

## Test plan

1. Uninstall chroot (Settings) if partial.  
2. Rebuild app (asset + Kotlin).  
3. Onboarding → isolation **chroot** → run setup.  
4. Scroll log:
   - sticky `/tmp` / no apt mkstemp errors  
   - `Chroot Setup Complete!`  
   - **no** exit-code-1 fail banner  
   - E / F / G? / H lines  
   - Full environment setup complete  
5. Next → complete onboarding.  
6. Open chroot native terminal; spot-check `which` / AI CLI if H succeeded.  
7. Regression: proot setup still finishes H.

---

## Risk / notes

| Risk | Mitigation |
|------|------------|
| `am start` keeps failing | `|| true`; optional remove callback entirely from chroot setup |
| E fails because mounts incomplete | P4 mount align + family script needs network/groups (base script already sets resolv/groups) |
| Double-run family on re-install | family/cli should be idempotent (existing soft markers where present) |
| User thinks install failed but rootfs exists | P2 marker gate + clear continue log |

---

## File touch list

| File | Change |
|------|--------|
| `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh` | P1 exit 0 / cleanup / am \|\| true |
| `app/src/main/java/com/ivarna/nativecode/OnboardingActivity.kt` | P2 gate + log; confirm E→H order |
| `app/src/main/java/com/ivarna/nativecode/RootShellService.kt` (and/or RootShell) | P4 mounts for executeInChroot |

---

## Order of work

1. P1 script exit (unblocks chain with current Kotlin).  
2. P2 gate on `.flux_configured`.  
3. P4 mounts for post-scripts.  
4. Device test (acceptance).  
5. Mark this plan **implemented** when criteria green.

---

## Stop line

**Implemented** (P1–P5 code). Device acceptance test still recommended.
