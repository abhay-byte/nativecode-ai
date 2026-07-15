# GUI Launch Diagnosis

## Execution Order

1. `MainActivity.runFirstTimeSetup()` deploys assets.
2. Host setup: `setup_termux.sh` runs through a one-command compatibility bind because Termux packages embed `/data/data/com.termux`; app terminal remains native.
3. Guest setup: `flux_install.sh` uses PRoot only to install Debian and run `setup_debian_family.sh` in it.
4. GPU setup: `setup_hw_accel_debian.sh` runs in Debian through PRoot.
5. Desktop customization: `setup_customization_debian.sh` runs in Debian through PRoot.
6. `setup_complete` is created only after every command exits zero.
7. Start button runs `start_gui.sh`; stop button runs `stop_gui.sh`.

## Device Result: 2026-07-14

`start_gui.sh` started PulseAudio, VirGL, and Termux:X11. XKB warnings were non-fatal.

Launch failed because `/usr/bin/startxfce4` was absent. The script then downloaded selected Debian packages and installed them using `dpkg --force-depends`. This bypassed APT dependency resolution and left the guest unable to execute `/usr/bin/bash` through PRoot.

## Fix

`start_gui.sh` no longer modifies a broken guest with partial `.deb` installs. It fails with an actionable setup message. Setup now checks each script exit status, so a failed guest setup cannot create `setup_complete`.

`flux_install.sh` now detects the actual container path (`$PREFIX/var/lib/proot-distro/...`) instead of the unused `$HOME/.local/share` path. Previously it could skip the guest configuration flow after an incorrect install-state decision.

On the test device, `am start` for the Termux:X11 activity aborted from the app sandbox. The X11 loader already starts the server, so the redundant activity launch was removed.

The host setup no longer mass-rewrites ELF RUNPATH values. `patchelf` raised `Bus error` on the test device; explicit runtime library paths are already supplied by the app and scripts.

Host terminal tweaks are currently excluded from automatic installation. They are optional and can be run manually after the base desktop succeeds.

The automatic host setup does not install `zsh`, `fastfetch`, or `git`; they belong to the optional terminal-tweaks stage.

The host installer must not inject its `LD_LIBRARY_PATH` into PRoot guest processes. Debian's loader then resolves incompatible host libraries and cannot execute guest Bash. The package launcher is left unmodified so the guest uses its own runtime.

App terminal and runtime run directly in the app's native Termux environment. PRoot is limited to the one host-package compatibility bind, Debian installation, and Debian guest commands.

## Custom GPU Package Build Blocker

The custom-prefix build for `virglrenderer-android` is blocked by its `angle-android` dependency under the builder's Python 3.14. ANGLE's `jinja_template.py` contains a literal `%` in an `argparse` help string; Python 3.14 validates it as a formatting directive and raises `ValueError: badly formed help string`. Multiple source-path patches did not reach the generated GN input path. `virglrenderer-android`, `mesa-zink`, and `termux-x11-nightly` are excluded from the required bootstrap until the upstream builder/runtime compatibility issue is resolved.

XFCE launch now treats VirGL as optional and continues with software rendering when `virgl_test_server_android` is absent.

## Termux:X11 Loader Blocker

The custom `termux-x11-nightly` package builds successfully but is metadata only; it depends on `xkeyboard-config` and does not include `libexec/termux-x11/loader.apk`. Neither the package-builder output nor the current app bootstrap contains that loader APK. The loader must be built or obtained from the Termux:X11 Android application project, then bundled at `usr/libexec/termux-x11/loader.apk` with read-only permissions. Do not ship or test a bootstrap claiming X11 readiness before that artifact is present.

## Recovery

Remove the broken Debian PRoot container, then rerun application setup. Existing affected installs need recovery because their rootfs was already modified by the old fallback.
