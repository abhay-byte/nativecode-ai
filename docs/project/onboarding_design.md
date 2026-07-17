# Onboarding Flow Design Specification: NativeCode (Kotlin / Android Views)

This specification defines the plan for the six-page onboarding wizard designed to configure the Debian guest sandbox, install required CLI packages, and bootstrap the local developer environment.

---

## 1. Page Details & Interactive Wireframes

### Page 1: Brand Intro (`onboarding_intro`)
* **Purpose**: Core brand exposure, logo reveal, and app tagline.
* **Layout Structure**:
  * **Center Brand Layout**: Giant obsidian circular-rect frame with a metallic Tux penguin silhouette holding a neon-green Terminal prompt cursor (`>_`).
  * **Brand Typography**: Oversized bold title: `NativeCode` in Electric Violet (`#d2bbff`).
  * **Tagline**: `Portable Linux & AI Developer Environment` in low-contrast grey (`#ccc3d8`).
  * **Bottom Actions**: Single large glowing primary button: `Get Started`.

### Page 2: Feature Slideshow (`onboarding_slideshow`)
* **Purpose**: Interactive slide show displaying application capabilities with slide/fade transitions.
* **Featured Capabilities**:
  1. **Bypass Android W^X restriction**: User-space PRoot mapping via JNI libraries (`libproot.so`).
  2. **Cohesive Git Integration**: Staged diff views, commits, branch trees, and GitHub CLI operations.
  3. **Graphical Desktop**: Integrated Termux X11 graphic display with GPU hardware acceleration.
  4. **AI Harness**: Local workspace runner for Claude Code, Aider, Cline, and Codex CLI.
* **Interactive Elements**: Animated progress dots indicating active slide; swipe gestures to proceed.

### Page 3: Architecture Isolation Mode (`onboarding_isolation`)
* **Purpose**: Choose isolation level (PRoot vs Chroot virtualization).
* **Layout Structure**:
  * **Card A: PRoot (Recommended)**: Active, highlighted card. Description: *User-space virtualization, no root access required, secure SDK 36 W^X bypass*.
  * **Card B: Chroot (In Development)**: Disabled card with a glowing badge showing *Coming Soon / In Dev*. Description: *Direct kernel-space execution, requires full root access*.
  * **Key Animations**: Spring bounce on selecting Card A; pulse animation on the "Coming Soon" badge.

### Page 4: Debian Base Environment Extraction (`onboarding_base_setup`)
* **Purpose**: Extract bootstrap assets and provision Debian (moved from Home Dashboard).
* **Layout Structure**:
  * **Active Status Card**: Displays the step currently running (e.g. *A. Preparing Directories...*, *B. Extracting Bootstrap...*).
  * **Gradient Progress Bar**: Linear bar animating with a violet-to-cyan gradient shift.
  * **Live Console View**: Monospaced console output block (`#050505` bg, cyan text) displaying extraction output and `proot-distro` logs.

### Page 5: AI CLI Tools Provisioning (`onboarding_cli_setup`)
* **Purpose**: Run Node.js and AI tool installation script inside Debian container.
* **Execution Script**:
  1. Download and install NVM:
     `curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.5/install.sh | bash`
  2. Source NVM and install Node.js v26.
  3. Install global utilities:
     `npm install -g opencode-ai`
     `npm install -g @openai/codex`
  * **Live Console View**: Shows live stdout/stderr logs of the installation script running inside the Debian container.

### Page 6: Setup Complete (`onboarding_complete`)
* **Purpose**: Post-onboarding verification.
* **Layout Structure**:
  * **Success Icon**: Pulse-animated neon checkmark.
  * **Environment Summary**: Detail listings for Debian OS status, Node.js version, and active global CLI packages.
  * **Launch Button**: Enters the main Home Dashboard environment.

---

## 2. Animation & UX Strategy
* **Pulse Status Indicators**: Infinite pulsing alpha animations on indicators.
* **Spring Sliding Panels**: Smooth transition during next/prev button clicks.
* **Glassmorphism Theme**: Obsidian background (`#0B0E14`) with glass-like semi-transparent cards (`#151823` fill, `#2D3344` border).
