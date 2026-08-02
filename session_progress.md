# session_progress.md - FluxLinux Development Work Log

This document tracks the detailed progress of the namespace migration (`com.termux` -> `com.zenithblue.nativecode`), the bugs encountered, diagnostic commands, and the resolution steps.

---

## 1. Context & Objective
- **Target**: Run a native Termux-like environment on Android under the package namespace `com.zenithblue.nativecode` with target SDK 28 (bypassing Android 10+ W^X limitations).
- **Core Requirement**: Eliminate the `proot` layer from the host terminal view, executing native compiled binaries (`bash`, `pkg`, `curl`, etc.) directly on the host. Maintain the Debian guest OS container (managed by `proot-distro`) inside a nested environment.

---

## 2. Work Done & Commands Executed

### Step A: Native Terminal view (Bypassing PRoot)
1. **Modified**: `MainActivity.kt`'s `initTerminalView()` method.
2. **Action**: Removed the `proot` wrapper (`-b /data/data/com.zenithblue.nativecode:/data/data/com.termux -0`) from the shell arguments. Spawned `/data/data/com.zenithblue.nativecode/files/usr/bin/bash -l` natively.
3. **Paths set**:
   - `PATH`: `/data/data/com.zenithblue.nativecode/files/usr/bin:/system/bin`
   - `HOME`: `/data/data/com.zenithblue.nativecode/files/home`
   - `PREFIX`: `/data/data/com.zenithblue.nativecode/files/usr`
   - `LD_LIBRARY_PATH`: `/data/data/com.zenithblue.nativecode/files/usr/lib`

### Step B: Core Package Prefix Correction
1. **Issue**: Core packages in the bootstrap (`bash`, `proot`, `git`, `wget`) had their `RUNPATH` (DT_RUNPATH) pointing to the old `/data/data/com.termux/files/usr/lib` prefix. This caused dynamic linker errors (e.g. `library "libtalloc.so.2" not found`).
2. **Commands**:
   - Checked `proot` RUNPATH: `readelf -d usr/bin/proot | grep RUNPATH`
   - Fixed `proot` RUNPATH: `patchelf --set-rpath /data/data/com.zenithblue.nativecode/files/usr/lib usr/bin/proot`
   - Fixed `git` RUNPATH: `patchelf --set-rpath /data/data/com.zenithblue.nativecode/files/usr/lib usr/bin/git`
   - Fixed `fastfetch` RUNPATH: `patchelf --set-rpath /data/data/com.zenithblue.nativecode/files/usr/lib usr/bin/fastfetch`
3. **Permanent fix**: Modified the host's `bootstrap_root` configuration and updated files, then re-packaged the `bootstrap.tar` and deployed to APK assets:
   - `tar -cf ../bootstrap.tar .`

### Step C: Post-Install Hook in `setup_termux.sh`
1. **Action**: Added an automated patching script to `setup_termux.sh` to fix newly downloaded packages (installed via Termux's official `apt-get` repos which are hardcoded for `com.termux`).
2. **Logic**:
   - Scans `$PREFIX/bin` and `$PREFIX/lib` (maxdepth 1) for ELF files and replaces `com.termux` -> `com.zenithblue.nativecode` in their `RUNPATH` using `patchelf`.
   - Scans text files (scripts, configs) under `$PREFIX/bin`, `$PREFIX/etc`, and `$PREFIX/libexec` and replaces the prefix string using `sed`.

---

## 3. What Went Wrong & Resolutions

### Blocker 1: SELinux Dynamic Categories (MCS) Permission Denied
- **Symptom**: Executing `bash ~/start_gui.sh` or `cat ~/start_gui.sh` inside the native terminal failed with `Permission denied`, even though the Unix permissions (`-rwxr-xr-x`) and owner (`u0_a385`) were correct.
- **Diagnosis**: 
  - Checked contexts: `ls -Z files/home/`
  - Output: `u:object_r:app_data_file:s0:c106,c257,c512,c768 start_gui.sh`
  - The dynamic MCS category set (`c106`) did not match the current app user's category (`c129`), causing SELinux to block read/execute operations.
- **Resolution**: Recreated the file using `root` (adb shell bypasses categories) and wrote it into the current app user space to assign the correct `c129` categories:
  - `cat start_gui.sh > start_gui_new.sh && mv start_gui_new.sh start_gui.sh`

### Blocker 2: Android 14 Writable DEX Restrictions (SecurityException)
- **Symptom**: `termux-x11 :0` and `am` commands crashed with `Aborted` (SIGABRT) under Android 14.
- **Diagnosis**: Checked logcat: `java.lang.SecurityException: Writable dex file '/data/data/com.zenithblue.nativecode/files/usr/libexec/termux-x11/loader.apk' is not allowed.`
- **Resolution**: Writable dex files/APKs loaded dynamically via `app_process` (like `loader.apk` for Termux:X11 and `am.apk` for the `am` command wrapper) are strictly forbidden under Android 14. Changed their permissions to read-only (`0400` / `r--------`) before execution:
  - `chmod 0400 usr/libexec/termux-x11/loader.apk`
  - `chmod 0400 usr/libexec/termux-am/am.apk`

### Blocker 3: Java Class Name Mismatch in Script Wrappers
- **Symptom**: `termux-x11` crashed with `ClassNotFoundException: com.zenithblue.nativecode.x11.Loader` and `am` crashed with `ClassNotFoundException: com.zenithblue.nativecode.termuxam.Am`.
- **Diagnosis**: The wrapper scripts were blindly patched (`sed` replacement of `com.termux` -> `com.zenithblue.nativecode`) which broke the Java class names that target pre-compiled classes inside the immutable APKs (`loader.apk` and `am.apk`).
- **Resolution**: Patched the scripts to point back to the correct pre-compiled classes:
  - `com.termux.x11.Loader` in `usr/bin/termux-x11`
  - `com.termux.termuxam.Am` in `usr/bin/am`

---

## 4. Current Verification Status
- **NATIVE SHELL**: Working! Prompts `~ $` shows `/data/data/com.zenithblue.nativecode/files/home`.
- **NATIVE TOOLS**: `pkg`, `curl`, `wget`, `fastfetch`, `ls` run successfully without dynamic linker errors or permission failures.
- **X11 DISPLAY**: `termux-x11 :0` launches without crashing and binds displays successfully.
- **XFCE4 DEB CONTAINER**: Base packages (`xfce4`, `dbus-x11`, `tigervnc`) offline installation completed.

---

## 5. Next Steps
1. Re-deploy the updated scripts (`start_gui.sh`, `stop_gui.sh`) onto the device.
2. Run `start_gui.sh debian` from the native terminal to start the XFCE desktop session.
