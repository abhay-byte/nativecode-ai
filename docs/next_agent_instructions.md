# Next Agent Instructions: Bypassing Target SDK 36 W^X for PRoot Guest Execution

## Current State
- `targetSdk` and `compileSdk` are set to `36`.
- App-level scripts and terminal launcher launch via `/system/bin/linker64` and `libtermux-exec.so` (`LD_PRELOAD`) which bypasses host W^X restrictions for processes started directly by the JVM.
- First-time setup fails at **Step D (Initializing Host Environment)** with:
  `proot error: execve("/data/data/com.zenithblue.nativecode/files/usr/bin/bash"): Permission denied`

## Root Cause of Step D Failure
1. `proot` is a host-side binary launched via the linker (`/system/bin/linker64 /data/data/.../proot`).
2. When `proot` attempts to execute the guest binary (`/data/data/com.zenithblue.nativecode/files/usr/bin/bash`), it forks and calls `execvp` on the host to execute the guest binary (or the unbundled loader at `/data/data/com.zenithblue.nativecode/files/usr/libexec/proot/loader`).
3. Since both `bash` and `loader` reside in `/data/data/com.zenithblue.nativecode/files/` (a writable app directory), Android blocks direct execution of these binaries with `Permission denied` under Target SDK 36.
4. Preloading `libtermux-exec.so` inside the child process *should* intercept `execvp`/`execve` and wrap it, but it fails under Target SDK 36 because:
   - `proot`'s child process executes in a restricted untrusted_app SELinux context where `execve` on writable files is strictly disallowed regardless of preload helpers, OR
   - The intercepted call is bypassed.

## Proposed Solutions
1. **Package Binaries in APK `jniLibs` (Recommended / Standard W^X Bypass)**
   - Android permits execution of native library files (`.so`) located in the app's native library directory (e.g., `/data/app/.../lib/arm64/`). This directory is read-only.
   - You can rename critical host binaries (`proot`, `bash`, and the `proot` loader) to match the `lib*.so` format (e.g., `libproot.so`, `libbash.so`, `libloader.so`) and place them in the app's `jniLibs/arm64-v8a` directory in the Android project.
   - When the APK is built and installed, Android will extract these to the read-only `/data/data/com.zenithblue.nativecode/lib/` directory.
   - Modify the execution commands in `MainActivity.kt` and scripts to run these binaries directly from the app's `applicationInfo.nativeLibraryDir` path instead of `/data/data/.../files/usr/bin/`.

2. **Patch PRoot to Prepend Linker internally**
   - Alternatively, modify the compiled `proot` to prepend `/system/bin/linker64` to the guest binary (or loader) execution path inside its fork/exec loop, ensuring host calls always go through the dynamic linker.
