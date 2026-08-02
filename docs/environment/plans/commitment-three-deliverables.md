# Commitment — three deliverables (onboarding-aware)

| Field | Value |
|-------|--------|
| **Status** | **IN PROGRESS** — #1 launcher + #2 optimize script **DONE**; #3 shipped rootfs bake pending |
| **Created** | 2026-07-29 |
| **Why this file** | Single place for the three concrete ship items. Tasks 2/3/6 detail *how*; this locks *what* and *when in onboarding*. |

---

## The three things

| # | Deliverable | Artifact | When it runs / applies | Task |
|---|-------------|----------|------------------------|------|
| **1** | **Fast launcher** | `nativecode_proot_fast.sh` | **Runtime** — every CLI/AI tool invocation that uses the fast path (`profile=cli` default). Not a post-install hook. | [task-02](./task-02-fast-launcher.md) |
| **2** | **Rootfs optimize script** | `optimize_debian_rootfs_perf.sh` (`--safe` default) | **Once** after proot Debian is installed / restored — onboarding post-install step (or first-boot guest optimize). | [task-03](./task-03-rootfs-hardening.md) |
| **3** | **Rootfs update** | Apply safe optimize to **live guest** + bake safe defaults into **shipped / updated rootfs** (assets/image or post-extract) so new installs start pre-tuned | After research + Task 3 regression green; refresh packaged rootfs / install path so users don’t rely only on a manual script | task-03 + [task-06](./task-06-deliverables.md) |

---

## Onboarding placement (intent)

```text
proot Debian install
        │
        ▼
[2] optimize_debian_rootfs_perf.sh --safe   ← one-shot on guest
        │
        ▼
AI CLI tools install (opencode, etc.)
        │
        ▼
ready for use
        │
        ▼
[1] nativecode_proot_fast.sh  (profile=cli)  ← how tools *run* day-to-day
        │  (compat: nativecode_proot.sh / proot-distro still default until opted in)
        ▼
[3] next rootfs ship / OTA extract already includes safe caches + conf
```

**Rules:**

- **[1]** does **not** install AI tools; it makes running them (and any CLI) faster without double-shell / excess binds.
- **[2]** runs **after** rootfs is present; safe to run **before or after** AI package install; **after** is fine if install itself is slow (re-run `--safe` once packages settle).
- **[3]** = durable rootfs content update (ldconfig caches, apt conf, dash `/bin/sh`, tmp layout, locales) so onboarding on a fresh image needs less work.
- **Never** enable `--aggressive` (`eatmydata`, force-unsafe-io) in default onboarding.
- **Never** break Turnip / virgl / chroot / flux user / `com.zenithblue.nativecode` paths.
- Default app path stays **compat** until explicit opt-in to fast launcher.

---

## Not these three (out of scope for this commitment)

| Item | Note |
|------|------|
| Replacing proot with chroot by default | Separate product choice; see task-05 |
| GPU DRM / glmark | Forbidden |
| Mass package purge from production rootfs | Forbidden without explicit plan |
| Replacing AI installers themselves | AI install stays existing onboarding; suite only *verifies* tools work |

---

## Acceptance (all three)

| Gate | Criteria |
|------|----------|
| Scripts exist | In repo assets + deployable to device `/data/local/tmp/` (launcher) / guest (optimize) |
| Regression | [regression-test-suite.md](./regression-test-suite.md) **P0 + P1** green after each of Task 2 and Task 3 |
| Onboarding doc | App/onboarding notes name: when to call optimize; when to use fast launcher for AI CLI |
| Rootfs update | Fresh extract / updated image reflects `--safe` outcomes **or** install path always runs `--safe` once |

---

## Status checklist

| # | Item | Done? |
|---|------|-------|
| 1 | Fast launcher implemented + P0/P1 | ☑ Task 2 2026-07-29 |
| 2 | Optimize script implemented + P0/P1 | ☑ Task 3 2026-07-29 (`--safe` live proot+chroot) |
| 3 | Live + shipped rootfs updated safely | ☑ live · ☐ shipped image/onboarding bake |
| — | Onboarding wire-up documented (call sites) | ☐ |

---

## Related

- Design: [`../proot-fast-launcher-design.md`](../proot-fast-launcher-design.md)
- Tasks: 02 launcher · 03 rootfs · 04 measure · 06 handoff
- Suite: [`suite/`](./suite/)
