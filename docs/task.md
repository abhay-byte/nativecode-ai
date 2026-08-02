# Task: Fix Git Diff Viewer

## Status: PLANNED — not started

## What's Broken

The git diff viewer (`showDiffViewer` → `loadDiffForFile` → `renderDiffLines`) shows wrong or garbled output.

Root causes identified via code analysis of `MainActivity.kt`:

| # | Bug | Location | Severity |
|---|-----|----------|----------|
| A | `GIT_PAGER` not set → git spawns `less`, hangs or outputs ANSI escape codes | `loadDiffForFile:1728` env block | **Critical** |
| B | PRoot/zsh startup noise lines leak into `lines` list → parser sees garbage | `loadDiffForFile:1744` empty-check logic | **Critical** |
| C | `git diff HEAD -- file` misses staged-only changes (new file after `git add`, before first commit) | `loadDiffForFile:1726` | High |
| D | `\ No newline at end of file` handled as a context code row | `renderDiffLines:1839` else branch | Minor |

## Files Involved

- `app/src/main/java/com/zenithblue/nativecode/MainActivity.kt`
  - `loadDiffForFile()` — lines 1713–1777
  - `renderDiffLines()` — lines 1779–1974

## Execution Plan

### Step 1 — Test proot git diff raw output (before any code change)

Write and run a test script via `adb shell run-as` to confirm:
- What exact lines proot emits before the diff body
- Whether `git diff HEAD` hangs without `GIT_PAGER=cat`
- What the clean filtered output looks like

```bash
# adb shell run-as com.zenithblue.nativecode sh -c '...'
# Set GIT_PAGER=cat, TERM=dumb, GIT_TERMINAL_PROMPT=0
# Run: cd <project> && git diff HEAD -- <file>
# Capture raw lines, identify noise prefix pattern
```

### Step 2 — Fix env vars in both ProcessBuilder blocks

In `loadDiffForFile` (lines ~1728 and ~1747), add to both `env` maps:

```kotlin
env["GIT_PAGER"]            = "cat"
env["GIT_TERMINAL_PROMPT"]  = "0"
env["TERM"]                 = "dumb"
```

### Step 3 — Filter proot noise from output lines

After reading lines from the process, strip non-diff lines before the empty check.
Valid diff lines start with: `diff `, `index `, `--- `, `+++ `, `@@`, `+`, `-`, ` `, `\`, `new file`, `deleted file`, `Binary`.

```kotlin
val filteredLines = lines.filter { line ->
    line.startsWith("diff ") || line.startsWith("index ") ||
    line.startsWith("--- ") || line.startsWith("+++ ") ||
    line.startsWith("@@") || line.startsWith("+") ||
    line.startsWith("-") || line.startsWith(" ") ||
    line.startsWith("\\") || line.startsWith("new file") ||
    line.startsWith("deleted file") || line.startsWith("Binary")
}
// replace `lines` with `filteredLines` in empty-check and renderDiffLines call
```

### Step 4 — Fix git command for staged-only files

Change the primary git command from:
```kotlin
val gitCmd = "cd $activeProjectPath && git diff HEAD -- \"$name\""
```
To (handles both staged and unstaged vs HEAD; falls back to `--cached` for staged-only):
```kotlin
val gitCmd = "cd $activeProjectPath && { git diff HEAD -- \"$name\" 2>/dev/null; git diff --cached -- \"$name\" 2>/dev/null; } | sort -u"
```
Actually simpler — `git diff HEAD` alone already shows staged+unstaged vs last commit.
The real gap is **new repos with no commits**. Fix:
```kotlin
val gitCmd = "cd $activeProjectPath && (git rev-parse HEAD >/dev/null 2>&1 && git diff HEAD -- \"$name\" 2>/dev/null || git diff --cached -- \"$name\" 2>/dev/null)"
```

### Step 5 — Fix parser: skip `\ No newline at end of file`

In `renderDiffLines` else branch (~line 1839), add before the `code` assignment:
```kotlin
if (line.startsWith("\\")) continue  // "\ No newline at end of file"
```

## Test Criteria

After fix, the diff viewer must:
1. Show `+` lines (green) and `-` lines (red) with correct line numbers
2. Show context lines around changes
3. Not hang or show empty for files with staged changes
4. Not show proot startup text as diff content
5. Show "No changes detected" only when there truly are none
