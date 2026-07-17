# UI/UX Design System Specification: NativeCode (Kotlin / Jetpack Compose)

This design system and interface architecture covers all application surfaces built on top of the native Kotlin code framework. Each page is mapped against its layout structure, key interactive features, local assets, design source screenshots, and HTML prototype paths.

---

## 1. App Launch & Container Setup Flows

### Page 1: Architecture Selection (`architecture_selection`)
* **Purpose**: Target container configuration (Proot vs Chroot virtualization).
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/architecture_selection/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/architecture_selection/screen.png`
* **Layout Structure**: 
  * Header layout with brand identifier (`NativeCode`) and version status.
  * Virtualization mode selection panel (Proot vs Chroot) containing target system metrics (permission level, speed overhead, storage access).
  * System architecture configurations (ARM64 detection, target partition directory mapping).
* **Key Interactive Features**: Toggle switches between rootless PRoot and root-required CHROOT virtualization. Action buttons trigger Android architecture verification routines.
* **Navigation Path**: App Launch -> Choose Architecture -> Proceed to setup.

### Page 2: Environment Setup (`environment_setup`)
* **Purpose**: Installation wizard for Debian rootfs container, libraries, and AI coding packages.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/environment_setup/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/environment_setup/screen.png`
* **Layout Structure**:
  * Real-time progress tracker card with dynamically updated status strings and percentage bar fills.
  * AI Tool Integrations Grid: Individual selectable cards detailing Claude Code, Codex CLI, Aider, Cline, Gemini, and OpenHands packages.
  * Primary launch trigger action button at bottom-right (initially disabled, shifts to glow state on extraction completion).
* **Key Interactive Features**:
  * Multiselect tool card toggles. Selected cards feature a colored highlight border and checkmark state indicator.
  * Active gradient fill shift keyframe animation on the setup progress bar.
* **Navigation Path**: Architecture Setup -> Install Progress -> Home Dashboard.

---

## 2. Workspaces & IDE Context Panels

### Page 3: Home Dashboard (`home_dashboard`)
* **Purpose**: Primary terminal launchpad, system status screen, and recent project catalog.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/home_dashboard/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/home_dashboard/screen.png`
* **Layout Structure**:
  * Header row showing terminal diagnostic status, host configuration properties, and settings link.
  * Environment Status widgets: Storage utilization, package integrity indicator, CPU core loads, and running process counts.
  * Recent Projects list containing direct paths, git branch state, and modification metadata.
  * Primary system triggers: Launch Debian Terminal and Open Graphic XFCE Server.
* **Key Interactive Features**: Grid lists with ripple feedback. Launch buttons initialize terminal execution background processes in Kotlin.
* **Navigation Path**: Bottom navigation primary target. Opens project contexts.

### Page 4: File Explorer Drawer & Workspace Sidebar (`file_explorer` / `terminal_with_project_sidebar`)
* **Purpose**: Local project directory tree navigation and context workspace switching.
* **HTML Prototype Paths**: 
  * `stitch_nativecode_ai_developer_environment/file_explorer/code.html`
  * `stitch_nativecode_ai_developer_environment/terminal_with_project_sidebar/code.html`
* **Design Screenshots**: 
  * `stitch_nativecode_ai_developer_environment/file_explorer/screen.png`
  * `stitch_nativecode_ai_developer_environment/terminal_with_project_sidebar/screen.png`
* **Layout Structure**:
  * Sidebar file list detailing directory folders, file extensions, and modified tags (green color highlights for untracked, yellow highlights for modifications).
  * Side-rail panel containing target action buttons: Create project directory, clone new repository, or access quick terminal.
  * Top navigation panel detailing branch metadata, file count summaries, and target action tools.
* **Key Interactive Features**:
  * Folders fold/unfold on single tap with spring animation.
  * Quick-create directory input triggers dynamically when clicking action buttons.
* **Local Assets**:
  * `stitch_nativecode_ai_developer_environment/file_explorer/user_avatar.png` (User details image element in drawer header).

### Page 5: File Viewer / Code View Editor (`file_viewer_area`)
* **Purpose**: Read/write code viewer for modified project files.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/file_viewer_area/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/file_viewer_area/screen.png`
* **Layout Structure**:
  * File tab layout toolbar showing open files (e.g. `main.rs`, `config.json`).
  * Structured lines code editor with line numbering and syntax-specific color highlights.
  * Quick Actions float row: Save changes, formatting, stage modifications, close file.
* **Key Interactive Features**: Cursor position indicator, live syntax parsing updates, and tab selection transitions.
* **Local Assets**:
  * `stitch_nativecode_ai_developer_environment/file_viewer_area/logo_demo.png` (Used for workspace headers).
  * `stitch_nativecode_ai_developer_environment/file_viewer_area/screenshot_demo.png` (Inline previews of design outputs).

---

## 3. Git Operations & Repository Integration

### Page 6: Git Operations Hub (`git_operations`)
* **Purpose**: Branch operations, remote configuration, and committing modifications.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/git_operations/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/git_operations/screen.png`
* **Layout Structure**:
  * Branch management widget showing local and remote branch track lists.
  * Commit builder panel containing input area for descriptions and staging checks.
  * Remote configurations showing sync statuses (behind/ahead count lists).
* **Key Interactive Features**: Branch drop-down pickers. Commit submit triggers git-push processes asynchronously.
* **Local Assets**:
  * `stitch_nativecode_ai_developer_environment/git_operations/pr_avatar.png` (Avatar icon for pull requests and author lists).

### Page 7: GitHub CLI Operations (`github_cli_operations`)
* **Purpose**: Native integration for managing GitHub PRs, issues, and accounts.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/github_cli_operations/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/github_cli_operations/screen.png`
* **Layout Structure**:
  * Account credential indicator showing authenticated users, scopes, and key configurations.
  * Pull Requests panel listing open reviews, statuses (green check for passes, red for failing checks).
  * Issues list detail containing query searches and filter tabs.
* **Key Interactive Features**:
  * Toggle scopes, click cards to view pull request details or load comments.
* **Local Assets**:
  * `stitch_nativecode_ai_developer_environment/github_cli_operations/pr_avatar.png` (Collaborator profile photos inside discussions and lists).

### Page 8: Code Diff Viewer (`code_diff_viewer`)
* **Purpose**: Live file modification visualizer comparing local adjustments to git branch heads.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/code_diff_viewer/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/code_diff_viewer/screen.png`
* **Layout Structure**:
  * Line-by-line comparison grid utilizing standard colors (green overlays for insertions, red overlays for removals).
  * File navigation path header and revision metadata display.
* **Key Interactive Features**: Horizontal and vertical scroll linking, line-staging actions.

---

## 4. Settings & Dev Utilities

### Page 9: Terminal & AI Agent Console (`terminal_ai_agent`)
* **Purpose**: Command shell prompt input and interactive agent session executions.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/terminal_ai_agent/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/terminal_ai_agent/screen.png`
* **Layout Structure**:
  * Monospaced text canvas running a terminal screen session.
  * Bottom floating agent input tray for executing custom CLI prompt runs (e.g. `claude` commands).
  * Panel detailing active host environment variables and process states.
* **Key Interactive Features**: Full screen terminal scaling, interactive prompts with command auto-completions, and drag-and-drop targets for file attachments.

### Page 10: Settings Hub (`settings_hub`)
* **Purpose**: Configuration management for container configurations, hardware virtualization, and keyboard setups.
* **HTML Prototype Path**: `stitch_nativecode_ai_developer_environment/settings_hub/code.html`
* **Design Screenshot**: `stitch_nativecode_ai_developer_environment/settings_hub/screen.png`
* **Layout Structure**:
  * Setting sections: Display configs (resolutions, VirGL sockets), storage directories, keyboard macros, and AI API credentials.
  * System utilities panel for cleanups and logs.
* **Key Interactive Features**: Toggle switches, slider bars for hardware configurations (CPU threads, RAM size limits).
* **Local Assets**:
  * `stitch_nativecode_ai_developer_environment/settings_hub/settings_avatar.png` (User details icon in page sidebar).
