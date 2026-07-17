# UI Design Specification: Android Linux Installer & AI Developer Environment (Kotlin / Jetpack Compose)

## 1. Visual Layout & Panels
The application is built using native Kotlin with Jetpack Compose. It uses a clean, hardware-accelerated grid dashboard designed for high-density information layout on tablets and mobile screens.

```
+-------------------------------------------------------------+
| [hamburger] Project Name (main-branch)         [sync] [run] |
+------------------+------------------------------------------+
|                  |                                          |
|  FILE EXPLORER   |  DEBIAN TERMINAL / AI HARNESS PANEL      |
|                  |  $ git diff                              |
|  [+] src         |  diff --git a/src/main.rs b/src/main.rs   |
|   |-- main.rs    |  - println!("hello");                    |
|   |-- config.rs  |  + println!("hello world");              |
|  [+] assets      |  $ _                                     |
|                  |                                          |
|                  |                                          |
|                  |                                          |
+------------------+------------------------------------------+
|  [folder] Open   |  [git] Diff  |  [terminal] Bash  | [ai]  |
+------------------+------------------------------------------+
```

### Panels & Layout States
* **File Explorer Sidebar**: Left-aligned, collapsible sliding drawer built using Compose `NavigationDrawer`. Displays directory tree, file actions, and modified markers (green for untracked, yellow for modified).
* **Terminal Core Workspace**: Persistent terminal emulator. The view is managed via an AndroidView wrapper around the native terminal surface (`TerminalView` in Kotlin). Fast-switch tab system alternates between host Debian Bash, AI Harness inputs, and active processes.
* **Navigation Dock**: Fixed bottom toolbar containing system-level toggles (Open Project, Git Diff, Terminal Shell, AI Agents) driven by Compose `NavigationBar`.

---

## 2. Interactive Animations & Transitions
Transitions prioritize raw responsiveness with a physical spring coefficient using Compose animation APIs.

* **Sidebar Toggle**: Uses a 200ms `spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)` transition. Slides from left, applying a subtle shadow over the terminal workspace.
* **Active Status Ripples**: Terminal connection and background AI processing statuses blink using an infinite transition pulse animation (opacity loops from 0.4f to 1.0f at 1500ms intervals).
* **Image Drag-and-Drop / Ingestion**: Dragging an image from the host or clipboard into the terminal displays a target boundary overlay (dashed border with a 200ms fade-in overlay implemented via `AnimatedVisibility`). Dropping collapses it with a spring-bounce scale down.

---

## 3. UI Theme Presets
Themes configure terminal ANSI values, editor token matching, UI background shades, and syntax highlights. Implemented via Jetpack Compose `MaterialTheme` color schemes.

### Theme Colors Matrix
| Theme Name | Primary Accent | Core Background | Sidebar Base | Terminal Background | Accent Highlight |
|---|---|---|---|---|---|
| **Terminal Obsidian (Dark)** | `#0D1117` | `#161B22` | `#0D1117` | `#010409` | `#58A6FF` (GitHub Blue) |
| **Cyber Tokyo (Neon Dark)** | `#1a0f30` | `#241442` | `#11052C` | `#0a0417` | `#FF007F` (Neon Pink) |
| **Dracula Classic (Dark)** | `#282A36` | `#44475A` | `#1E1F29` | `#282A36` | `#BD93F9` (Purple) |
| **Nordic Frost (Light)** | `#ECEFF4` | `#D8DEE9` | `#E5E9F0` | `#FFF` | `#88C0D0` (Ice Blue) |
| **Solarized Clean (Light)** | `#FDF6E3` | `#EEE8D5` | `#FDF6E3` | `#FFF` | `#268BD2` (Classic Blue) |

---

## 4. App Launcher Icon Design
The app icon represents native Linux architecture nested directly on top of Android internals.

```
       _______________________
      |   _________________   |
      |  |                 |  |
      |  |   >_    [GNU]   |  |  <-- Debian/Linux Terminal Shell Indicator
      |  |_________________|  |
      |                       |
      |        ( ^ _ ^ )      |  <-- Nested Tux Penguin Icon
      |_______________________|
```

* **Visual Element**: A circular-rect (squircle) frame representing the Android app container. The background features a dark obsidian gradient. Nested in the center is a metallic Tux penguin silhouette holding a neon-green Terminal prompt symbol (`>_`).
* **Foreground**: Debian Swirl logo subtly printed on the back shell of the penguin.
* **Palette**: Midnight black base (`#0D1117`), Matte white penguin belly, Neon Green terminal cursor (`#39FF14`), and Debian Red swirl (`#D70A53`).
