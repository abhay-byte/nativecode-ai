# Terminal + Workspace: categorized tool sections (Debian Shell last)

**Date:** 2026-07-30  
**Scope:** UI only — `MainActivity` tool selector layouts  
**Related:** `docs/plan/terminal-debian-shell-rooted.md`

---

## Goal

| Surface | Change |
|---------|--------|
| **Terminal** | Section order: Free → Paid → **Debian Shell last** |
| **Project workspace hub** | Same 3 categories + headers as Terminal (not flat “SELECT AI TOOL” grid) |

## Section SSOT

| Order | Title | Subtitle | Cards |
|------:|-------|----------|-------|
| 1 | FREE CLI TOOLS | // OPEN SOURCE / FREE | opencode |
| 2 | PAID CLI TOOLS | // PRO / SUBSCRIPTION | codex*, agy, claude-code, qwen-code, grok, kiro |
| 3 | DEBIAN SHELL | // SYSTEM SHELL | Debian Shell (flux), Debian Shell Rooted (root) |

\* codex hidden when `linux_method=chroot` (existing).

## Implementation

1. `buildTerminalToolSelectorView` — call `addSection` in order free → paid → shell  
2. `populateWorkspaceHubTools` — same sections + headers/dividers; click → `createWorkspaceTerminalTab`  
3. Drop workspace top title `SELECT AI TOOL` / `// LAUNCH WORKSPACE` (sections own headers)

## Done

- [x] Terminal: Debian Shell bottom  
- [x] Workspace: Free / Paid / Debian Shell sections  
- [x] chroot codex filter unchanged  
