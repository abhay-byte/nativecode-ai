# Directory sticky search + Project git branches + Git Diff summary

**Date:** 2026-07-31  
**Status:** implemented 2026-07-31 — device smoke still recommended  
**Out of scope (v1):** branch checkout UI, commit/stage/discard rewrite, full git blame, host-Termux git (guest only), P2-17 checksums, Marketplace consent (done).

**Design SSOT:** `docs/project/ui_design.md` (cyber-brutalist / Obsidian Terminal)  
**Token SSOT:** `app/.../DesignTokens.kt` (`NC.*`)  
**Compile policy:** `:app:compileDebugKotlin` only unless user asks for APK.

**User refs (NativeCode app screenshots):**
1. **Directory** — search scrolls away with tree → must stick under top bar  
2. **Project Settings / CONFIG** — if git repo, show **current branch** + **all local branches**  
3. **Git Diff** — not-a-repo empty state; if repo, **summary card** (LOC + file counts) above existing file list  

**Primary code (today):**

| Path | Role |
|------|------|
| `MainActivity.kt` `buildProjectDirTreeLayout` / `openProjectDirTree` | Directory layout; search **inside** `ScrollView` |
| `MainActivity.kt` `openProjectSettings` | Name / terminal / icon / remove only — **no git** |
| `MainActivity.kt` `buildProjectGitDiffLayout` / `openProjectGitDiff` / `refreshGitDiffTree` | Diff list via `git status --porcelain` |
| `MainActivity.kt` `loadDiffForFile` / `showDiffViewer` | Per-file `git diff HEAD` (same ProcessBuilder pattern) |
| `terminal/ProjectManager.kt` | `hasGitCheckout`, `detectRepoMethod`, clone |
| `terminal/ProjectPathResolver.kt` | host path for `.git` under proot/chroot rootfs |
| `terminal/LinuxCommandBuilder.kt` | `build(ctx, cmd, method=…)` → proot \| chroot |
| `terminal/ShellCommandRunner.kt` | `runCaptureExit` / `runStreamed` (prefer over raw `ProcessBuilder`) |
| `activeProjectPath` + `activeProjectMethod` | project guest path + isolation SSOT |

---

## 0. Goals (user intent → product)

| # | Goal | Meaning |
|---|------|---------|
| **G1** | Sticky Directory search | Search bar **does not scroll**. Fixed under project top bar; **only** file tree scrolls. |
| **G2** | Settings git branches | If project is a git repo under its isolation method: card with **current branch** + list of **local branches**. If not git: hide card (or single muted “Not a git repository”). |
| **G3** | Diff: not git | If not initialized: empty state **“NOT A GIT REPOSITORY”** / “Project has no git init” — no fake “NO CHANGES”. |
| **G4** | Diff: summary card | When git: top card with **LOC +/−**, counts: **modified / deleted / created (added) / untracked** (files+dirs from porcelain). Then existing file rows. |
| **G5** | Same git logic | Reuse porcelain parse + guest `git` via `LinuxCommandBuilder` + method. Both **proot** and **chroot**. |
| **G6** | Clean architecture | Logic **out of** `MainActivity` mega-file where practical: pure models + guest command helpers + thin UI binders. No break of open/diff/settings flows. |

### Success definition

| Surface | Before | After |
|---------|--------|-------|
| Directory | Header+search scroll with tree | Top bar sticky; title+search sticky; tree only scrolls |
| Settings | Name / term / icon / remove | + Git Branches card when repo |
| Git Diff (no `.git`) | “NO CHANGES” or error | Explicit **not a git repo** card |
| Git Diff (repo, clean) | “NO CHANGES DETECTED” | Summary zeros **or** keep clean card + summary 0 |
| Git Diff (repo, dirty) | File rows only | Summary card **then** same rows |
| Isolation | Diff often uses `currentMethod` only | Always **`activeProjectMethod`** (fallback `currentMethod`) |

---

## 1. Problem summary (today)

### 1.1 Directory — search not sticky

```text
projectDirTreeContainer
└ col (VERTICAL)
   ├ projectDirTreeTopBar          // sticky (outside ScrollView) ✓
   └ projectDirTreeScrollView
      └ projectDirTreeLayout
         ├ dirHeader (DIRECTORY + search + divider)  // scrolls ✗
         └ workspaceDirTreeLayout (tree rows)
```

`openProjectDirTree()` rebuilds header+search **into** `projectDirTreeLayout` (child of `ScrollView`). Scrolling long trees moves search off-screen (screenshot #1).

### 1.2 Project Settings — no git

`openProjectSettings()` builds: header → name card → terminal card → icon card → remove.  
No branch query. `ProjectManager.hasGitCheckout` already exists but unused here.

### 1.3 Git Diff — porcelain list only; no repo gate; no summary

`refreshGitDiffTree()`:

```kotlin
val gitCmd = "cd $activeProjectPath && git status --porcelain"
val (lcArgs, lcEnv) = LinuxCommandBuilder.build(this, gitCmd) // method defaults to currentMethod
// ProcessBuilder ad-hoc (not ShellCommandRunner)
// filter porcelain → rows or "NO CHANGES DETECTED"
```

Gaps:

| Gap | Impact |
|-----|--------|
| No `git rev-parse --is-inside-work-tree` | Non-git project → empty / error / misleading clean |
| No summary (LOC / counts) | User request #3 missing |
| `method` not `activeProjectMethod` | Wrong rootfs if global method ≠ project method |
| Duplicated ProcessBuilder + env | Harder to reuse for branches / numstat |
| All UI parse in MainActivity | ~200 LOC block, not testable |

**Keep:** porcelain XY filter, status badge colors (MOD/ADD/DEL/NEW), row → `showDiffViewer`, “NO CHANGES” card visual, PROOT/CHROOT badge on header.

### 1.4 Shell reality (must drive design)

| Mode | Guest rootfs | Non-root guest cmd |
|------|--------------|--------------------|
| **proot** | `filesDir/.../proot-distro/containers/debian/rootfs` | `ProotCommandBuilder` → flux |
| **chroot** | `/data/local/tmp/chrootDebian13` | `ChrootCommandBuilder` → flux |

- Git lives **in guest** under project path (`/home/flux/repos/...`).  
- Host `.git` check: `ProjectManager.hasGitCheckout(ctx, path, method)` via `ProjectPathResolver`.  
- Prefer **host `.git`** for fast “is repo?”; **confirm + branch/status** always via guest `git` (works if host FS lag / bind).  
- Project method = `activeProjectMethod` (persisted with project). **Never** assume global `LinuxCommandBuilder.currentMethod` alone.

---

## 2. Architecture

```text
┌──────────────────────────────────────────────────────────────────┐
│ UI (MainActivity — thin)                                         │
│  Directory: sticky header shell + scroll tree only               │
│  Settings: add Git Branches card (bind model)                    │
│  Git Diff: not-git state | summary card | existing rows          │
└─────────────────────────────┬────────────────────────────────────┘
                              │ method = activeProjectMethod
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ com.zenithblue.nativecode.git (NEW, decoupled)                       │
│  GitRepoService     — public API: isRepo, branches, statusSummary│
│  GitGuestCommands   — pure shell strings (no Android UI)         │
│  GitModels          — BranchInfo, StatusEntry, DiffSummary       │
│  GitPorcelainParse  — pure parse porcelain + numstat             │
└─────────────────────────────┬────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
 LinuxCommandBuilder    ShellCommandRunner    ProjectManager
  (method, flux)         runCaptureExit        hasGitCheckout
```

**Do not** grow another 400-line block only in `MainActivity`.  
**Do** leave view construction (cyber-brutalist cards) in MainActivity or tiny private helpers (`buildGitSummaryCard(summary)`, `buildGitBranchesCard(info)`).

### 2.1 Method parameter mandatory

```kotlin
fun isGitRepo(ctx: Context, projectPath: String, method: String): Boolean
fun listBranches(ctx: Context, projectPath: String, method: String): BranchInfo?
fun loadDiffSummary(ctx: Context, projectPath: String, method: String): DiffSummaryResult
```

Call sites:

```kotlin
val method = activeProjectMethod.ifBlank { LinuxCommandBuilder.currentMethod }
```

Also fix `loadDiffForFile` / discard cmds in same PR if touch-adjacent: pass `method` (correctness, not feature creep).

---

## 3. Feature A — Sticky Directory search

### 3.1 Layout target

```text
projectDirTreeContainer
└ col (VERTICAL, MATCH)
   ├ projectDirTreeTopBar                    // existing back + title
   ├ projectDirStickyHeader (VERTICAL)       // NEW permanent child of col
   │    ├ title row: DIRECTORY | // FILE TREE
   │    ├ searchCard (EditText + clear)
   │    └ divider
   └ projectDirTreeScrollView (weight=1)
      └ workspaceDirTreeLayout ONLY          // tree / search results
```

### 3.2 Code changes

| Function | Change |
|----------|--------|
| `buildProjectDirTreeLayout()` | Create `projectDirStickyHeader` once; add to `col` between topBar and ScrollView; keep refs for search `EditText` if needed |
| `openProjectDirTree()` | **Stop** putting header+search into scroll content. Only clear/rebuild `workspaceDirTreeLayout`. Re-bind search text = `dirSearchQuery`. ScrollView `scrollTo(0,0)` still OK |
| Fields | Optional: `projectDirSearchEt: EditText?`, `projectDirStickyHeader: LinearLayout` |

### 3.3 Behaviour preserve

- `dirSearchQuery` TextWatcher → `refreshWorkspaceDirTree()` unchanged  
- Clear button visibility  
- IME: existing “drop IME from directory search…” behaviour elsewhere — keep  
- Search results path `renderSearchDirectoryResults` unchanged  
- File open / create / long-press menus unchanged  

### 3.4 Edge cases

| Case | Handle |
|------|--------|
| Rotate / font scale | Header wrap OK; monospace 12f |
| Keyboard open | ScrollView resizes under sticky header (standard LinearLayout weight) |
| Rebuild on every open | Prefer **not** recreate sticky header every open — only update title bar + search text. If current pattern is full rebuild, either keep sticky outside and only refresh tree, or recreate sticky but **always** outside ScrollView |

### 3.5 UI tokens

- Search card: keep `#0A0A0A` fill, `NC.SURFACE_HIGH` stroke (existing)  
- Padding: `dp(16)` horizontal match page  

---

## 4. Feature B — Project Settings: git branches

### 4.1 Placement

Insert **after** terminal settings card, **before** project icon card (or after icon — prefer **after terminal**, before icon):

```text
SETTINGS header
PROJECT NAME
TERMINAL SETTINGS
GIT BRANCHES          ← NEW (only if repo or always with empty state)
PROJECT ICON
SAVE / REMOVE
```

### 4.2 Detection

**Fast path (UI thread safe):**  
`ProjectManager.hasGitCheckout(this, activeProjectPath, method)` → host `.git` dir.

**Authoritative path (bg thread):** guest

```sh
cd '<path>' && git rev-parse --is-inside-work-tree 2>/dev/null
```

Use host for show/hide skeleton; confirm branches via guest. If host false but guest true (rare), still show after load.

### 4.3 Guest commands (`GitGuestCommands`)

```sh
# inside projectPath, quoted safely
git rev-parse --is-inside-work-tree
git branch --show-current
git branch --format='%(refname:short)'
# optional HEAD detached:
git rev-parse --abbrev-ref HEAD
```

Single capture script (one proot/chroot spawn):

```sh
cd <quotedPath> || exit 2
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo '__NOGIT__'
  exit 0
fi
echo '__HEAD__'
git branch --show-current 2>/dev/null || git rev-parse --abbrev-ref HEAD
echo '__BRANCHES__'
git branch --format='%(refname:short)'
```

### 4.4 Models

```kotlin
data class BranchInfo(
  val isRepo: Boolean,
  val current: String?,          // null if detached unknown
  val branches: List<String>,    // local only v1
  val detached: Boolean = false
)
```

### 4.5 UI card (cyber-brutalist)

- Title row: icon `ic_git` / `ic_git_thick` if present else `ic_project_config` + **GIT BRANCHES**  
- Sub: `// LOCAL REFS`  
- Badge: current branch name, primary fill (e.g. `NC.PRIMARY` text on `NC.SURFACE_LOWEST` stroke)  
- List: monospace rows, current branch **highlighted** (left accent `NC.PRIMARY`, label `HEAD` or `*`)  
- Loading: “Loading branches…”  
- Not repo: either **omit card** (recommended) or muted text “Not a git repository — init or clone first.”  
  **Decision for v1: omit card if not repo** (settings stay clean for local-only folders).

### 4.6 Non-goals Settings

| Out | Why |
|-----|-----|
| Checkout branch tap | Scope creep; needs dirty-tree safety |
| Remote branches | `git branch -r` later |
| Create branch | Later |
| Git config user.name | Later |

---

## 5. Feature C — Git Diff: repo gate + summary card

### 5.1 Flow

```text
openProjectGitDiff()
  → header (GIT DIFF + method badge)  // keep
  → workspaceGitDiffLayout
  → refreshGitDiffTree()  // rewrite body

refreshGitDiffTree():
  show loading
  bg: GitRepoService.loadDiffSummary(path, method)
  ui:
    if !isRepo → notGitCard
    else → summaryCard(summary) + (cleanCard | fileRows)
```

### 5.2 Guest command pack (one spawn preferred)

```sh
cd <quotedPath> || exit 2
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo '__NOGIT__'
  exit 0
fi
echo '__STATUS__'
git status --porcelain -uall
echo '__NUMSTAT__'
# all tracked changes vs HEAD (staged + unstaged unified vs HEAD)
git diff --numstat HEAD 2>/dev/null
# untracked file line counts (cap N files for perf)
echo '__UNTRACKED_LOC__'
git ls-files --others --exclude-standard -z | head -z -n 40 | ...
# simpler v1: skip per-file wc; only count untracked files from porcelain
```

**v1 LOC strategy (recommended):**

| Source | LOC |
|--------|-----|
| Tracked changes | sum of `git diff --numstat HEAD` cols 1–2 (`-` binary → 0) |
| Untracked | **file count only** in summary; LOC optional = 0 or “—” for untracked |
| Rationale | Fast; avoids N× `wc -l` in proot; matches “numbers of changes loc” for real diffs |

If product insists untracked LOC: optional second phase `wc -l` for ≤20 untracked files, cap timeout.

### 5.3 Porcelain classification (keep existing filter + extend)

Existing filter:

```text
len > 3 && line[2]==' ' && XY in MADRCU?! 
```

Counts (per path, index+worktree collapse):

| Bucket | Rule (simplified) |
|--------|-------------------|
| **modified** | either X or Y is `M` (and not pure untracked) |
| **deleted** | X or Y is `D` |
| **added / created** | X or Y is `A`, or rename/copy target; **or** status `??` counted separately as untracked not “created” |
| **untracked** | `??` (files and dirs — porcelain lists `dir/` for untracked dirs when `-uall` or default for empty?) Use `git status --porcelain` as today; count `??` lines as untracked entries |
| **renamed** | `R` — show in list as today; optional count on summary |

**User wording map:**

| User | Field |
|------|--------|
| files updated / modified | `modified` |
| deleted | `deleted` |
| created new | `added` (staged new / `A`) |
| untracked files/dir | `untracked` |
| changes loc | `linesAdded` / `linesDeleted` |

### 5.4 Models

```kotlin
data class GitStatusEntry(
  val xy: String,          // 2-char
  val path: String,        // display path (rename: take right side)
  val statusChar: Char,    // for badge color (existing when)
  val statusLabel: String  // MOD/ADD/DEL/NEW/...
)

data class DiffSummary(
  val isRepo: Boolean,
  val modified: Int,
  val added: Int,
  val deleted: Int,
  val untracked: Int,
  val renamed: Int,
  val linesAdded: Int,
  val linesDeleted: Int,
  val entries: List<GitStatusEntry>
)

sealed class DiffSummaryResult {
  data class Ok(val summary: DiffSummary) : DiffSummaryResult()
  data class NotGit(val message: String = "Project is not a git repository") : DiffSummaryResult()
  data class Error(val message: String) : DiffSummaryResult()
}
```

### 5.5 Summary card UI

Cyber-brutalist card (`cyberBrutalistBg`, offset 8, sharp corners):

```text
┌─────────────────────────────────────────┐
│  DIFF SUMMARY                    PROOT  │  // or omit method (header already has it)
│  +128  /  −34  LOC                      │  // PRIMARY / ERROR
│  ─────────────────────────────────────  │
│  MOD 3   ADD 1   DEL 0   NEW 2          │  // badges like file rows
│  Untracked: 2 paths                     │
└─────────────────────────────────────────┘
```

Then:

- If `entries.isEmpty()` → existing **NO CHANGES DETECTED** card (keep visual)  
- Else → existing rows (reuse accent/badge/chevron) → `showDiffViewer(file, ID_PROJECT_GIT_DIFF)`

### 5.6 Not-git empty state

Same visual language as clean card, but:

- Icon: warning or git icon (not check)  
- Title: **NOT A GIT REPOSITORY**  
- Sub: `Run git init or clone a repo in this project`  
- Color: `NC.ON_SURF_VAR` / tertiary, not error-red (not a failure)

### 5.7 Refactor `refreshGitDiffTree`

1. Extract parse of porcelain lines → `GitPorcelainParse.parseStatusLines`  
2. Extract row builder → keep private in MainActivity `addGitDiffRow(layout, entry)` to avoid moving all NC/dp deps  
3. Replace ProcessBuilder with:

```kotlin
val method = activeProjectMethod.ifBlank { LinuxCommandBuilder.currentMethod }
val result = GitRepoService.loadDiffSummary(this, activeProjectPath, method)
```

### 5.8 `loadDiffForFile` alignment (same PR if cheap)

Pass `method = activeProjectMethod` into `LinuxCommandBuilder.build`. Prefer `ShellCommandRunner.runCaptureExit` + `GIT_PAGER=cat` via env map (ShellCommandRunner.applyEnvironment must still allow extra env — verify; if not, set in builder env HashMap before run).

**Note:** current code mutates ProcessBuilder env after build. `ShellCommandRunner` uses `applyEnvironment` — ensure `GIT_PAGER`/`GIT_TERMINAL_PROMPT` set. If runner does not accept arbitrary env, extend `runCaptureExit` env map (already `envMap: Map?`) — **callers put GIT_* in envMap**.

```kotlin
val env = HashMap(lcEnv)
env["GIT_PAGER"] = "cat"
env["GIT_TERMINAL_PROMPT"] = "0"
env["TERM"] = "dumb"
ShellCommandRunner.runCaptureExit(ctx, args, env)
```

---

## 6. Package layout (new files)

```text
app/src/main/java/com/zenithblue/nativecode/git/
  GitModels.kt           // BranchInfo, DiffSummary, GitStatusEntry, DiffSummaryResult
  GitGuestCommands.kt    // pure strings: branchesScript(path), statusSummaryScript(path)
  GitPorcelainParse.kt   // pure functions, unit-testable later
  GitRepoService.kt      // Context + LinuxCommandBuilder + ShellCommandRunner
```

**No new Gradle modules.** Mirror `github/` package style.

### 6.1 `GitGuestCommands` sketch

```kotlin
object GitGuestCommands {
  fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

  fun branchesBundle(projectPath: String): String { ... }
  fun statusSummaryBundle(projectPath: String): String { ... }
  fun isInsideWorkTree(projectPath: String): String =
    "cd ${shellQuote(projectPath)} && git rev-parse --is-inside-work-tree"
}
```

### 6.2 `GitPorcelainParse` sketch

```kotlin
fun parsePorcelain(lines: List<String>): List<GitStatusEntry>
fun countBuckets(entries: List<GitStatusEntry>): Counts
fun parseNumstat(lines: List<String>): Pair<Int, Int>  // added, deleted
fun parseBundleOutput(raw: String): DiffSummaryResult
```

Badge labels/colors stay in UI layer (NC colors) — parse only returns statusChar / path / xy.

---

## 7. MainActivity touch map (minimal)

| Location | Action |
|----------|--------|
| Fields near dir tree | sticky header refs |
| `buildProjectDirTreeLayout` | restructure col |
| `openProjectDirTree` | sticky vs scroll split |
| `openProjectSettings` | async branch card |
| `openProjectGitDiff` | unchanged shell; body via refresh |
| `refreshGitDiffTree` | service + summary + not-git |
| `loadDiffForFile` | method + runner (optional but recommended) |
| imports | `com.zenithblue.nativecode.git.*` |

**Do not touch:** Marketplace, onboarding, bottom nav IDs, project create clone (unless shared quote helper — can move `shellQuote` to GitGuestCommands and leave ProjectManager private quote).

---

## 8. Proot + chroot matrix

| Scenario | Expected |
|----------|----------|
| proot project, git, dirty | Summary + rows via proot guest |
| chroot project, git, dirty | Same via chroot guest |
| proot project, no git | Settings: no branch card; Diff: NOT A GIT REPOSITORY |
| Project method chroot, global method proot | Commands use **project** method |
| Empty repo (init only) | Repo yes; summary 0; NO CHANGES card |
| Untracked only | untracked > 0; LOC may be 0; NEW rows |
| Binary only changes | numstat `-` → 0 LOC; still list file |

---

## 9. Threading / UX

| Step | Thread |
|------|--------|
| Host `hasGitCheckout` | Main OK (filesystem) |
| Guest git bundle | `executor` / bg |
| Bind UI | `mainHandler` |
| Settings open | Show card shell “…” then fill |
| Diff open | “Loading diffs…” then swap |

Cancel: v1 no cancel (status is fast). If stuck, next open overwrites layout.

---

## 10. Non-goals / won’t break

| Preserve | How |
|----------|-----|
| File tree expand/search/open | Only move search out of scroll |
| Diff row → file viewer | Same `showDiffViewer` |
| Discard / other git ops elsewhere | Untouched unless method fix |
| Software Manager / shells | No shared state |
| Bottom nav DIRECTORY / DIFF / CONFIG | Same IDs |
| `dirSearchQuery` persistence in session | Same field |

---

## 11. Implementation order (phased)

### Phase 1 — Extract git service (no UI behaviour change)

1. Add `git/` package: models, commands, parse, service  
2. Reimplement `refreshGitDiffTree` data path via service **same UI** (rows only)  
3. Force `activeProjectMethod`  
4. Compile  

### Phase 2 — Diff UX

1. Not-git card  
2. Summary card  
3. Clean card when empty entries  

### Phase 3 — Settings branches

1. Branch card UI + `listBranches`  
2. Loading / omit if not repo  

### Phase 4 — Directory sticky

1. Layout restructure  
2. Verify search still filters tree  

**Why this order:** service first reduces risk; sticky layout is independent and can ship last or first (user-visible easy win — may do Phase 4 anytime as isolated PR-step).

**Suggested ship order if single session:** Phase 4 → Phase 1 → Phase 2 → Phase 3 (user sees sticky immediately; git features share service).

---

## 12. File checklist

| File | Status |
|------|--------|
| `docs/plan/directory-sticky-search-git-branch-diff-summary.md` | this plan |
| `app/.../git/GitModels.kt` | NEW |
| `app/.../git/GitGuestCommands.kt` | NEW |
| `app/.../git/GitPorcelainParse.kt` | NEW |
| `app/.../git/GitRepoService.kt` | NEW |
| `app/.../MainActivity.kt` | EDIT (dir layout, settings, diff) |
| Tests | optional later: pure parse unit tests (not required v1) |

---

## 13. Verification

### 13.1 Compile

```text
:app:compileDebugKotlin
```

### 13.2 Manual device

| # | Steps | Expect |
|---|-------|--------|
| D1 | Open project → Directory → scroll long tree | Search stays under top bar |
| D2 | Type search / clear | Filter works; sticky |
| S1 | Settings on non-git folder | No branch card |
| S2 | Settings on git repo | Current + all local branches |
| S3 | proot vs chroot projects | Correct branches per rootfs |
| G1 | Diff non-git | NOT A GIT REPOSITORY |
| G2 | Diff clean repo | Summary 0 + NO CHANGES |
| G3 | Modify file | MOD count + LOC +/− + row |
| G4 | Add untracked | NEW/untracked count + row |
| G5 | Delete tracked | DEL + row |
| G6 | Tap row | Diff viewer still works |
| G7 | Project chroot while global proot | Still correct guest git |

---

## 14. Risk register

| Risk | Mitigation |
|------|------------|
| Sticky header double-built every open | Build once in `buildProjectDirTreeLayout`; open only refreshes tree |
| Search EditText recreated → lost focus | Keep single EditText in sticky; only setText if query changed |
| `git diff --numstat HEAD` slow large repo | Already one process; timeout later if needed |
| Double-count staged+unstaged | Use only `git diff --numstat HEAD` (not sum of cached+uncached) |
| Path with spaces/quotes | `GitGuestCommands.shellQuote` |
| MainActivity size | New package keeps net MainActivity growth small |
| Method mismatch regression | Centralize method in service calls; fix loadDiffForFile |

---

## 15. Approval gates

Before implement, confirm:

1. **Settings not-git:** omit card (recommended) vs always show “not git”?  
2. **Untracked LOC:** file count only (recommended) vs `wc -l` sample?  
3. **Branch list:** local only (recommended) vs include remotes?  
4. **Checkout on branch tap:** no (v1) — display only?  

Defaults if silent approval of this plan as written: **omit / count-only / local-only / display-only**.

---

## 16. Implementation pseudocode (reference)

### Sticky dir

```kotlin
// buildProjectDirTreeLayout
col.addView(projectDirTreeTopBar)
col.addView(buildDirStickyHeader()) // title + search + divider
col.addView(projectDirTreeScrollView)
projectDirTreeScrollView.addView(workspaceDirTreeLayout)

// openProjectDirTree
updateProjectSubpageTopBar(...)
searchEt.setText(dirSearchQuery)
workspaceDirTreeLayout.removeAllViews()
refreshWorkspaceDirTree()
```

### Diff refresh

```kotlin
executor.execute {
  val method = activeProjectMethod.ifBlank { LinuxCommandBuilder.currentMethod }
  val result = GitRepoService.loadDiffSummary(this, activeProjectPath, method)
  mainHandler.post {
    workspaceGitDiffLayout.removeAllViews()
    when (result) {
      is NotGit -> addNotGitCard()
      is Error -> addError(result.message)
      is Ok -> {
        addSummaryCard(result.summary)
        if (result.summary.entries.isEmpty()) addNoChangesCard()
        else result.summary.entries.forEach { addRow(it) }
      }
    }
  }
}
```

---

## 17. Done criteria

- [ ] Plan approved  
- [ ] `git/` package compiled in  
- [ ] Directory search sticky on device/emulator  
- [ ] Settings shows branches when git (proot + chroot)  
- [ ] Diff not-git / summary / rows correct both methods  
- [ ] `loadDiffForFile` uses project method  
- [ ] `:app:compileDebugKotlin` green  
- [ ] No regression: open file from tree, open diff row, remove project  

---

**Next step after approval:** implement per §11 (Phase 4 → 1 → 2 → 3), compile, report.
