# W^X Bypass for Target SDK 36 on Android

## Problem
Android 10+ and Target SDK 36 enforce strict Write XOR Execute (W^X) memory protection policies. The app could not execute `proot`, `bash`, or the `proot` loader from writable app directories (`/data/data/com.zenithblue.nativecode/files/...`), causing `Permission denied` on host and guest executions.

## Fix Implementation

### 1. Package Binaries in `jniLibs`
Renamed critical executables to conform to the shared library format and packaged them in the app's native libraries directory (`app/src/main/jniLibs/arm64-v8a/`):
- `proot` -> `libproot.so`
- `bash` -> `libbash.so`
- `loader` -> `libloader.so`
- `loader32` -> `libloader32.so`

Set `useLegacyPackaging = true` in `app/build.gradle.kts` to force the Android package manager to extract them into `applicationInfo.nativeLibraryDir` (read-only and executable).

### 2. Update Path and Environment Variables
Modified `MainActivity.kt` and runtime executions to:
- Execute `libbash.so` and `libproot.so` directly from `nativeLibraryDir`.
- Export `PD_PROOT_BIN` pointing to `libproot.so` so `proot-distro` (Python CLI) targets the executable binary.
- Export `PROOT_LOADER` pointing to `libloader.so` so `proot` routes guest system call translation through the executable loader path.
- Prepend `nativeLibraryDir` to `PATH` in shell executions.

### 3. Patch `proot-distro` Pass-through
Modified `proot_distro/commands/login/__init__.py` dynamically after extraction to include `"PROOT_LOADER"` in the allowed environment variables list. This ensures the guest environment successfully inherits the correct path to the executable host loader.
