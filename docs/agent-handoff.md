# XFCE Custom-Prefix Recovery Handoff

Updated: 2026-07-15

## Goal

Make `com.ivarna.nativecode` install Debian and launch XFCE from its embedded native terminal/runtime. The host prefix is:

```text
/data/data/com.ivarna.nativecode/files/usr
```

Do not install or mix packages built for `/data/data/com.termux/files/usr`.

## Current Repository Changes

Modified project files:

```text
app/src/main/assets/scripts/flux_install.sh
app/src/main/assets/scripts/setup_termux.sh
app/src/main/assets/scripts/start_gui.sh
app/src/main/java/com/ivarna/nativecode/MainActivity.kt
docs/start-gui-debug.md
docs/agent-handoff.md
```

Do not revert unrelated existing changes in the separate `termux-packages` checkout. It is intentionally dirty.

## Correct Runtime Model

1. Android app process runs the native terminal and host scripts.
2. The host runtime is under the custom app prefix.
3. PRoot is only for installing/login into the Debian guest.
4. Debian scripts run inside PRoot.
5. `start_gui.sh` starts host audio/X11 services, then starts XFCE inside Debian.

## MainActivity Setup Order

`MainActivity.runFirstTimeSetup()` currently does:

1. Extract `app/src/main/assets/bootstrap.tar`.
2. Deploy asset scripts to `files/home` and `files/home/scripts`.
3. Run `setup_termux.sh`.
4. Run `flux_install.sh debian <base64 setup_debian_family.sh>`.
5. Run `setup_hw_accel_debian.sh` in Debian through `proot-distro login`.
6. Run `setup_customization_debian.sh` in Debian through `proot-distro login`.
7. Create `files/setup_complete` only if every preceding command exits `0`.

`termux_tweaks.sh` is deliberately excluded from automatic setup. It is optional, downloads external content, and previously caused unrelated setup failures.

## Fixed Source Problems

### False-success setup

`runShellCommand()` now returns process exit status. Each required setup stage is wrapped in `check(...) == 0`; `setup_complete` cannot be created after a failed stage.

### Broken Debian fallback install

Old `start_gui.sh` downloaded selected Debian `.deb` files and ran:

```sh
dpkg -i --force-depends /tmp/debs/*.deb
```

This left Debian unable to execute `/usr/bin/bash`. Removed. `start_gui.sh` now fails with `XFCE setup incomplete` instead of changing a broken guest.

### Wrong PRoot container path

`flux_install.sh` checked obsolete `$HOME/.local/share/proot-distro`. Fixed to:

```text
$PREFIX/var/lib/proot-distro/containers/<distro>/rootfs
```

### Host tweaks launched under PRoot

Removed automatic `termux_tweaks.sh` stage. It must not run inside host-package PRoot compatibility setup.

### Host ELF rewriting

Removed mass `patchelf --set-rpath` loop. On test device it crashed with `Bus error`.

### PRoot guest library injection

Removed patch which forced host `LD_LIBRARY_PATH` into guest PRoot environment. It caused Debian `/usr/bin/bash` execution failure.

### Redundant X11 activity launch

Removed `am start -n com.termux.x11/com.termux.x11.MainActivity` from `start_gui.sh`. It aborted in the app sandbox. The loader process starts X11 directly.

### VirGL fallback

`start_gui.sh` now starts `virgl_test_server_android` only if present. Otherwise it logs software-rendering fallback.

## Device Failures Observed

Device serial:

```text
Y5WWBMJVOZSK4HU8
```

### 1. Official Termux packages cannot install natively

Native host `apt-get install` failed:

```text
dpkg: error processing archive .../libbz2_1.0.8-8_aarch64.deb (--unpack):
error creating directory "./data/data/com.termux". Permission denied
```

Cause: official Termux `.deb` payloads target `com.termux`. A compatibility PRoot bind can work temporarily, but it is not a valid long-term solution because package runtime paths and future package installs are mixed.

### 2. Original host setup `patchelf` failure

```text
setup_termux.sh: line 101: Bus error patchelf --set-rpath ...
```

Mass ELF RUNPATH replacement removed.

### 3. PRoot guest startup failure

```text
proot error: execve("/usr/bin/bash"): No such file or directory
```

Caused by passing custom host `LD_LIBRARY_PATH` into Debian guest. Removed from custom `proot-distro` patching.

### 4. Termux:X11 app/sandbox calls

`am force-stop` and `am start` printed `Aborted` in test logs. XKB warnings were non-fatal. Removing activity start prevents it from blocking later guest logic.

## Custom Termux Package Builds

The custom package builder is configured for the correct namespace:

```text
TERMUX_APP__PACKAGE_NAME="com.ivarna.nativecode"
```

Source checkouts:

```text
/home/abhay/repos/termux-packages
/home/abhay/repos/termux-lib/termux-packages
```

Active Docker builder mount:

```text
/home/abhay/repos/termux-lib/termux-packages -> /home/builder/termux-packages
```

Important: use the active mounted checkout when checking output:

```sh
docker exec termux-package-builder bash -lc 'cd /home/builder/termux-packages && ls output'
```

The sibling `/home/abhay/repos/termux-packages` has a different output directory and pre-existing uncommitted changes.

### Verified Custom Package Payload

Custom `bash` package payload begins with:

```text
./data/data/com.ivarna.nativecode/files/usr/bin/bash
```

This proves the builder is producing the desired custom prefix.

### Successful Builds

The following packages were built successfully in the active builder checkout:

```text
pulseaudio_17.0-1_aarch64.deb
proot-distro_5.4.1_all.deb
termux-x11-nightly_1.03.01-5_all.deb
```

Core custom packages/dependencies also exist from prior builds, including `bash`, `dpkg`, `python`, `proot`, `procps`, `psmisc`, `curl`, `termux-am`, `termux-tools`, and many libraries.

### Important Artifact Caveat

`termux-x11-nightly` is metadata only:

```text
Depends: xkeyboard-config
Provides: termux-x11
```

It does **not** contain:

```text
usr/libexec/termux-x11/loader.apk
```

The current app `bootstrap.tar` also has no `loader.apk`. Do not claim X11 works until this artifact is bundled.

## GPU / VirGL Build Blocker

Do not retry these without a deliberate builder fix:

```text
angle-android
virglrenderer-android
mesa-zink
```

`virglrenderer-android` depends on `angle-android`.

Builder has Python 3.14. ANGLE fails after a long GN/Ninja build:

```text
TypeError: %i format: a real number is required, not dict
ValueError: badly formed help string
```

Failing upstream file:

```text
build/android/gyp/jinja_template.py
help="GN-list of files that get {% include %}'ed."
```

Attempts were made to escape `%` as `%%` in package build hooks. The active GN cache path changed and repeated builds still used unpatched source. This is documented in `docs/start-gui-debug.md`.

Current application behavior deliberately degrades to software rendering when `virgl_test_server_android` is absent.

## Termux:X11 Loader Requirement

Official source was cloned for investigation:

```text
/home/abhay/repos/termux-x11
```

Need obtain/build the loader APK from the Termux:X11 Android project or a compatible release, then package it into bootstrap at:

```text
data/data/com.ivarna.nativecode/files/usr/libexec/termux-x11/loader.apk
```

At runtime it must be read-only before `app_process` uses it:

```sh
chmod 0400 "$PREFIX/libexec/termux-x11/loader.apk"
```

`start_gui.sh` launches class:

```text
com.termux.x11.Loader
```

Do not blindly replace `com.termux` strings inside loader-related scripts or APKs. The Java class name must remain `com.termux.x11.Loader`.

## Current Host Setup Script Contract

`setup_termux.sh` no longer runs `apt-get`, `dpkg`, `patchelf`, package downloads, or ELF/text prefix rewriting.

It validates bundled host dependencies:

```text
proot-distro
pulseaudio
$PREFIX/libexec/termux-x11/loader.apk
```

Therefore bootstrap assembly is the next required implementation step.

## Bootstrap Requirement

Current app asset:

```text
app/src/main/assets/bootstrap.tar
```

It is approximately 544 MB and contains an old mixed runtime. It must be replaced only after all required custom-prefix artifacts are present.

Required bootstrap content:

```text
usr/bin/bash
usr/bin/python
usr/bin/proot
usr/bin/proot-distro
usr/bin/pulseaudio
usr/bin/pkill
usr/lib/pulseaudio/modules/module-native-protocol-tcp.so
usr/libexec/termux-x11/loader.apk
usr/share/X11/xkb or compatible xkeyboard-config data
```

All paths inside built packages must use:

```text
data/data/com.ivarna.nativecode/files/usr
```

## Next Steps

1. Inspect `/home/abhay/repos/termux-x11` Gradle modules and build its Android loader APK or extract a compatible loader from an official release.
2. Confirm loader works with `com.termux.x11.Loader` and Android 14 read-only dex requirement.
3. Assemble a new bootstrap from the custom `.deb` payloads plus loader APK. Do not use official `com.termux` packages.
4. Add a reproducible bootstrap assembly script to this repository. It should unpack selected `.deb` payloads, verify custom prefix, add loader APK, and create `app/src/main/assets/bootstrap.tar`.
5. Verify required bootstrap paths before building APK.
6. Build APK: `./gradlew assembleDebug`.
7. Uninstall old device app data: `adb -s Y5WWBMJVOZSK4HU8 uninstall com.ivarna.nativecode`.
8. Install fresh APK: `adb -s Y5WWBMJVOZSK4HU8 install app/build/outputs/apk/debug/app-debug.apk`.
9. Run setup. Validate per stage before creating `setup_complete`.
10. Start GUI, take screenshot, stop GUI, verify PulseAudio/X11/guest processes terminate.

## Validation Commands

Project scripts:

```sh
bash -n app/src/main/assets/scripts/*.sh
./gradlew assembleDebug
git diff --check
```

Check custom package payload:

```sh
docker exec termux-package-builder bash -lc '
  f=/home/builder/termux-packages/output/bash_5.3.9-1_aarch64.deb
  d=$(ar t "$f" | grep "^data.tar" | head -1)
  ar p "$f" "$d" | tar -tf - | head
'
```

Check bootstrap required files:

```sh
tar -tf app/src/main/assets/bootstrap.tar | grep -E \
  "usr/bin/(bash|python|proot|proot-distro|pulseaudio)$|libexec/termux-x11/loader.apk"
```

Check app device state:

```sh
adb -s Y5WWBMJVOZSK4HU8 shell \
  'run-as com.ivarna.nativecode sh -c "test -f files/setup_complete && echo complete || echo incomplete"'
```

## Context7 Finding

Official Termux Packages documentation explicitly states that custom `TERMUX_APP__PACKAGE_NAME` builds cannot safely use official `com.termux` repositories. All packages must be rebuilt/hosted for the custom package namespace. This confirms why native `apt-get` against official Termux repositories failed.
