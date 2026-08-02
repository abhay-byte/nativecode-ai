# Proot performance plans & regression suite

**Goal:** make NativeCode proot (`com.zenithblue.nativecode`) closer to chroot for launch, CLI, packages, builds, and AI tools — **without** losing functionality or stability.

**Rule:** after each task, run the [regression suite](./regression-test-suite.md). Only promote changes that pass **P0** gates.

| Doc | Status | Purpose |
|-----|--------|---------|
| [**commitment-three-deliverables.md**](./commitment-three-deliverables.md) | **COMMITTED** | The 3 ship items: fast launcher · optimize script · rootfs update (+ onboarding placement) |
| [task-01-inventory-startup.md](./task-01-inventory-startup.md) | **DONE** (findings + pass criteria) | Inventory proot argv, measure startup, define improvements |
| [task-02-fast-launcher.md](./task-02-fast-launcher.md) | **DONE** | Minimal plain-proot launcher + regression PASS (~17× launch) |
| [task-03-rootfs-hardening.md](./task-03-rootfs-hardening.md) | **DONE** | optimize script `--safe` on proot+chroot + suite PASS |
| [task-04-measure-before-after.md](./task-04-measure-before-after.md) | PLANNED | Scoreboard vs chroot and original proot |
| [task-05-hard-limits-next.md](./task-05-hard-limits-next.md) | PLANNED | Remaining limits + next experiments |
| [task-06-deliverables.md](./task-06-deliverables.md) | PLANNED | Scripts, matrix, docs handoff |
| [regression-test-suite.md](./regression-test-suite.md) | **DEFINED** | Full suite: stability, packages, dev, AI CLIs |
| [suite/](./suite/) | **SCRIPTS** | Runnable host/guest regression tests |

**Related environment docs:**  
`../proot-fast-launcher-design.md`, `../proot-vs-chroot-perf-report*.md`, `../adb-shell-access.md`

**Constraints (all tasks):**

- Package: `com.zenithblue.nativecode` only (never default to `com.termux`)
- Rootless proot must keep working
- KernelSU chroot path must keep working
- Turnip / virgl GPU modes must not regress
- No DRM/KMS glmark; no black-screen experiments
- Prefer optional “performance profile” over replacing defaults
- Do not ship unsafe defaults (`eatmydata`, force-unsafe-io) without explicit opt-in
