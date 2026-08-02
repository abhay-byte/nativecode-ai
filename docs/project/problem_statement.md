# Problem Statement: Android Linux Installer & AI Developer Environment (Kotlin)

## 1. Background & Context
Developing complex applications directly on mobile devices is historically constrained by hardware security policies, lack of full desktop operating system tools, and absent support for modern AI-assisted software engineering harnesses. Developers require a portable, native environment capable of executing a full Linux operating system on Android, integrated with local development features and standard AI coding assistants. This system is implemented as a native Kotlin Android application.

## 2. Problem Statement
Android developers and mobile-first engineers lack a cohesive, integrated environment on their devices that combines a fully functional Linux guest OS (Debian) with modern software development workflows (Git, file trees, diffing) and AI CLI agentic capabilities. The system must bypass platform restrictions, provision a native Linux shell, expose standard project tools, and allow terminal-level image ingestion/sharing.

## 3. Scope & Key Features
The proposed application is a Debian Linux installer on Android containing:
* **Project Directory Management**: Open local folders, create new projects, and view file directories directly in the GUI.
* **Debian Terminal**: Seamless access to a sandboxed Debian terminal running inside a `proot` container.
* **Integrated CLI Tools & Shell Access**: Triggering specialized commands and utilities scoped to the current project context.
* **Git Operations**: 
  * View file modifications and live git diffs within the project workspace.
  * Clone repositories directly into the project directory using URL or a authenticated GitHub account. Only GitHub is supported.
* **Terminal Media Capabilities**: Ability to copy images directly to the CLI tools.
* **AI Tool Integration**: Dedicated setups and harnesses for modern agentic workflows (e.g. Claude Code, Aider, Cline, etc.).

## 4. Architecture & System Constraints
* **Platform Security**: Target SDK 36 compatibility. Bypasses Android 10+ W^X (Write XOR Execute) platform restrictions by packaging critical execution binaries (`proot`, `bash`, `loader`) as `.so` libraries (e.g., `libproot.so`) inside the `jniLibs` package directory. Uses `useLegacyPackaging = true` to force extraction into the read-only, executable `nativeLibraryDir` path.
* **Path Interception**: Utilizing `proot` to map application paths (`/data/data/com.zenithblue.nativecode`) to default terminal schemas.
* **Authentication**: Token and SSH-based GitHub-only authentication for repository management.
