# Implementation Plan: Fix Terminal, Navigation, Keyboard & Foreground Services

This plan outlines the surgical changes required to resolve five outstanding UX, navigation, and service issues in the application.

---

## 1. Keyboard Suppression on Home Page
### Problem
The soft keyboard pops up unconditionally on the Home page when the app opens.

### Root Cause
1. In `MainActivity.kt`'s `navigateToPage()`, navigation to `ID_TERMINAL` requests focus and calls `InputMethodManager.showSoftInput()` directly.
2. In `initTerminalView()`, a delayed task (`postDelayed`) triggers `showSoftInput()` if `pageStack.peek() == ID_TERMINAL`.
3. The `TerminalView` is set to `isFocusable = true` and `isFocusableInTouchMode = true` at creation. Even when `View.GONE`, on some devices/Android versions, having focusable views in the hierarchy triggers immediate focus/IME setup at activity startup.

### Plan
1. **Build-Time Focus Configuration**: 
   - Modify `buildTerminalLayout()` to set `terminalView.isFocusable = false` and `terminalView.isFocusableInTouchMode = false` initially.
2. **Dynamic Focus on Navigation**:
   - In `navigateToPage(id)` under `ID_TERMINAL` block:
     - Set `terminalView.isFocusable = true` and `terminalView.isFocusableInTouchMode = true`.
     - Request focus via `terminalView.requestFocus()`.
     - **Remove** the unconditional `imm.showSoftInput()` call. Keyboard should only show when the user taps on the terminal screen.
   - When navigating *away* from `ID_TERMINAL` (in the hide/cleanup section of `navigateToPage`):
     - Set `terminalView.isFocusable = false` and `terminalView.isFocusableInTouchMode = false`.
     - Clear focus or hide the keyboard explicitly.
3. **Clean Up initTerminalView()**:
   - Delete the `postDelayed` runnable in `initTerminalView()` that calls `showSoftInput` after 1000ms.
   - Depend entirely on `TerminalViewClient.onSingleTapUp()` to trigger `showSoftInput()`.

---

## 2. Navigation & Back Button Fixes (Placement + Hierarchy)
### Problem
- The back button in the unified top bar is shown in pages where it shouldn't be.
- Back navigation does not correctly return to the last page.
- Direct navigations to sub-pages bypass `pageStack` tracking, causing incorrect historical records.

### Root Cause
- The global `backBtn` is defined once inside `unifiedHeader` and toggled using `backBtn.visibility`.
- Many direct navigations (e.g. settings buttons, edit project, file viewer) call `navigateToPage()` directly without pushing to `pageStack`. Only `bottomNavigation.setOnItemSelectedListener` updates the stack.
- When `onBackPressed()` is called, it pops the stack, but the stack state is out-of-sync with active visibility.

### Plan
1. **Automate Stack Pushing**:
   - Update `navigateToPage(id)` to automatically maintain history:
     ```kotlin
     if (pageStack.isEmpty() || pageStack.peek() != id) {
         pageStack.push(id)
     }
     ```
   - Adjust `bottomNavigation`'s listener to avoid duplicate pushing (since `navigateToPage` will now handle it).
2. **Scaffold Local Top Bars**:
   - Hide the `unifiedHeader` completely for pages that should have local headers:
     - `ID_TERMINAL` (App Terminal page)
     - `ID_SCRIPT_INSTALL`
     - `ID_PROJECT_CREATE`
   - Build local headers inside their respective builders (`buildTerminalLayout()`, `buildScriptInstallLayout()`, `buildProjectCreateLayout()`) and handle back clicks by delegating to `onBackPressed()`.

---

## 3. Separate Terminal Top Bar Controls
### Problem
The back button, sessions menu button (☰), and `+` button to open a new terminal are embedded in the global top bar on the terminal page instead of a terminal-specific header.

### Plan
1. **Strip from Unified Header**:
   - Remove `backBtn`, `menuBtn`, and `addTerminalBtn` from `unifiedHeader` in `buildRootLayout()`.
2. **Build Terminal Top Bar**:
   - Inside `buildTerminalLayout()`, instantiate a dedicated terminal horizontal header bar (`terminalTopBar`) with:
     - `backTv` (◀) -> calls `onBackPressed()`
     - `menuTv` (☰) -> toggles sidebar drawer
     - `titleTv` ("Terminal Workspace")
     - `addTv` (+) -> calls `createNewTerminalSession()`
   - Add this `terminalTopBar` as the first child of `terminalWorkspaceLayout`.
3. **Handle Sidebar Locking**:
   - Update drawer lock configuration: Only unlock the sidebar drawer when `ID_TERMINAL` is active.

---

## 4. Foreground Services & Terminal Count Notifications
### Problem
Need foreground services to keep terminals active in the background for both App Terminal and Project Terminal screens. Notifications must display open session counts and return the user to the correct terminal page on click.

### Plan
1. **Define Services**:
   - Create `AppTerminalService.kt` for app-wide terminals.
   - Create `ProjectTerminalService.kt` for project workspace terminals.
2. **Implement Notification Updates**:
   - Add utility methods in `MainActivity` to start, update, and stop these services.
   - Send the current terminal session counts in intents:
     - When session lists update (`sessionsList.size` or `workspaceSessions.size`), notify/update the respective service.
     - When counts drop to 0, stop the service.
3. **Intent Deep Linking**:
   - Setup `PendingIntent`s in notifications pointing to `MainActivity`.
   - Pass extras: `EXTRA_TARGET_PAGE = ID_TERMINAL` or `EXTRA_TARGET_PAGE = ID_PROJECT_WORKSPACE`.
   - Implement `onNewIntent()` in `MainActivity` to extract the extra and execute `navigateToPage(targetPage)`.
   - In `AndroidManifest.xml`, configure `MainActivity` with `android:launchMode="singleTask"`.

---

## 5. System Back Navigation & Confirmation Dialog
### Problem
- System back click inside a project terminal page closes the application.
- System back click on home/root pages should request confirmation before exiting.

### Root Cause
- `onBackPressed()` checks `isInitialized` but falls back to `super.onBackPressed()` prematurely if stack matching fails.
- Workspace screens are not mapped explicitly in the visibility matching block in `onBackPressed()`.

### Plan
1. **Explicit View-Based Nav Mapping**:
   - Rewrite `onBackPressed()` to check the active screen visibility directly:
     ```kotlin
     override fun onBackPressed() {
         when {
             // Project screens -> go back to workspace
             projectSettingsScrollView.visibility == View.VISIBLE -> navigateToPage(ID_PROJECT_WORKSPACE)
             projectDirTreeScrollView.visibility == View.VISIBLE -> navigateToPage(ID_PROJECT_WORKSPACE)
             projectGitDiffScrollView.visibility == View.VISIBLE -> navigateToPage(ID_PROJECT_WORKSPACE)
             
             // Workspace -> go back to Projects List page
             projectWorkspaceLayout.visibility == View.VISIBLE -> navigateToPage(ID_PROJECTS_LIST)
             
             // Terminal screens
             terminalWorkspaceLayout.visibility == View.VISIBLE -> navigateToPage(ID_HOME)
             scriptInstallLayout.visibility == View.VISIBLE -> {
                 if (!isScriptRunning) navigateToPage(ID_SCRIPTS)
             }
             
             // Script lists & viewers
             scriptsScrollView.visibility == View.VISIBLE -> navigateToPage(ID_SETTINGS)
             fileViewerScrollView.visibility == View.VISIBLE -> navigateToPage(ID_FILES)
             diffViewerScrollView.visibility == View.VISIBLE -> navigateToPage(ID_GIT)
             
             // Base pages (Home, Projects List, Settings) -> Confirm Exit
             homeScrollView.visibility == View.VISIBLE || 
             projectsListScrollView.visibility == View.VISIBLE || 
             settingsHubScrollView.visibility == View.VISIBLE -> showExitConfirmDialog()
             
             // Fallback
             else -> showExitConfirmDialog()
         }
     }
     ```
2. **Build Confirmation Dialog**:
   - Implement `showExitConfirmDialog()`:
     ```kotlin
     private fun showExitConfirmDialog() {
         android.app.AlertDialog.Builder(this)
             .setTitle("Exit FluxLinux?")
             .setMessage("Terminal sessions will keep running in the background.")
             .setPositiveButton("Exit") { _, _ -> finish() }
             .setNegativeButton("Cancel", null)
             .show()
     }
     ```
