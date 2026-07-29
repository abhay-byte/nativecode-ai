# Regression suite scripts

Runnable harness for NativeCode proot/chroot work.

## Layout

```text
suite/
  run_regression.sh      # host entrypoint (adb)
  host/p0_host.sh        # host P0 (package, proot ELF, helpers)
  guest/p0_core.sh       # guest P0 identity/fs/apt smoke
  guest/p1_dev.sh        # guest P1 development tools
  guest/p1_ai.sh         # guest P1 AI CLI version/help + offline smoke
  guest/ai_offline_smoke.sh
  guest/fixtures/hello.c
  lib/common.sh
  lib/adb_env.sh
  results/               # created at runtime
```

## Quick start

```bash
export NC_ADB_SERIAL=192.168.1.52:43055
# optional: export NC_APP_UID=u0_a415
# optional: export NC_ENVS=proot-distro,chroot
# later:    export NC_ENVS=proot-distro,chroot,proot-fast

chmod +x run_regression.sh host/*.sh guest/*.sh
./run_regression.sh --p0
./run_regression.sh --p0 --p1
```

## Docs

Full catalog & gates: [`../regression-test-suite.md`](../regression-test-suite.md)

## Safety

- Does **not** run `glmark2-*-drm`
- Does **not** delete rootfs
- Network tests SKIP when `NC_OFFLINE=1` or tools fail
