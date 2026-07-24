# Chroot Terminal & AI Tools Fix Plan

## Issues Summary

Two related issues affect **chroot mode only**; proot works correctly for both.

| # | Issue | Symptom | Affected |
|---|---|---|---|
| 1 | Terminal rendering broken + `clear` fails | Zsh prompt garbled, bash `clear` → "TERM environment variable not set" | Chroot interactive shell |
| 2 | AI CLI tools crash on launch | Tapping any AI tool card (opencode, codex, claude-code, etc.) exits immediately | Chroot tool launcher |

---

## Root Cause Analysis

### Common Cause: Missing Environment in `buildLinuxCommand` (Chroot Branch)

`MainActivity.kt` `buildLinuxCommand()` populates `envMap` differently for chroot vs proot.

**Proot branch** (working) sets:
- `TERM=xterm-256color`
- `HOME`, `PREFIX`, `LD_LIBRARY_PATH`
- `SSL_CERT_FILE`, `CURL_CA_BUNDLE`
- `TERMUX__*` variables

**Chroot branch** (broken) sets:
- **Only** `PATH = /system/bin:/system/xbin:/sbin:…` for the *outer* `/system/bin/sh` process
- **None** of the above variables are injected into the chroot guest environment

Chroot discards the host Android environment; the inner shell starts with a bare POSIX env. Without `TERM`, terminal capability queries fail (`tput`, `clear`, zsh theme color/powerline detection). Without `HOME`, `LANG`, `XDG_RUNTIME_DIR`, interactive CLI tools exit on initialization.

### Issue 1 Specifics — Terminal

1. **Missing `TERM`**
   - `clear` requires terminfo lookup → "TERM environment variable not set"
   - Zsh + agnosterzak theme queries terminal capabilities for powerline symbols/colors → prompt renders as broken fragments

2. **Missing `/tmp` bind mount in `buildLinuxCommand`**
   - `setup_debian13_chroot.sh` generates CLI launchers that mount `/tmp`, but `buildLinuxCommand` mount list omits it:
     ```kotlin
     "mkdir -p $CHROOT_PATH/dev/shm && busybox mount -t tmpfs …"
     ```
     No `/tmp` line present.
   - `XDG_RUNTIME_DIR=/tmp` is set in `.zshrc`, but if `/tmp` is not shared, some temp-file operations behave differently.

3. **`.zshrc` background jobs may race/hang in bare chroot env**
   - `.zshrc` runs `{ fastfetch; pokemon-colorscripts } &!`
   - Without `TERM`, `fastfetch` may fail or produce escape-sequence garbage before shell fully initializes.

### Issue 2 Specifics — AI Tools

1. **Missing `TERM` causes immediate exit**
   - All listed AI tools are interactive TUI programs (Node.js or compiled). They call `isatty()` and read `TERM` on startup; if missing they abort.

2. **NVM node binary path absent from `envInit`**
   - `createNewTerminalSession` builds a tool command with:
     ```kotlin
     val envInit = "export PATH=/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo-bin:\$PATH && export NVM_DIR=/home/flux/.nvm && [ -s /home/flux/.nvm/nvm.sh ] && . /home/flux/.nvm/nvm.sh"
     ```
   - `nvm.sh` adds the active Node version’s `bin/` to PATH, but only if it sources successfully.
   - In a non-interactive `su - user -c "…"` invocation, zsh may skip `.zshrc` loading (depending on `ZSH_*` options), so `nvm.sh` may not execute, leaving global npm packages (`opencode-ai`, `@openai/codex`, `@qwen-code/qwen-code`) unreachable.

3. **Chroot tool command does not inherit `.zshrc` PATH**
   - Proot: `proot-distro login debian --user flux -- zsh -c "…"` → zsh invoked explicitly, `.zshrc` likely loaded.
   - Chroot: `/bin/su - flux -c "…"` → runs the user’s default shell with `-c`; if default shell is zsh, `.zshrc` **may not** load for non-interactive `-c` (zsh only sources `.zshrc` for interactive shells).

4. **Missing `HOME` / `XDG_RUNTIME_DIR`**
   - Tools write config/cache to `$HOME/.config` or `$XDG_RUNTIME_DIR`; absent variables cause I/O errors or crashes.

---

## Proposed Fix Plan

### Phase 1 — Unify Chroot Environment (`buildLinuxCommand`)

**File:** `app/src/main/java/com/ivarna/nativecode/MainActivity.kt`

In the `"chroot"` branch of `buildLinuxCommand`, add the same baseline env vars that proot receives:

```kotlin
"chroot" -> {
    // … existing mountCmds …

    envMap["TERM"] = "xterm-256color"
    envMap["HOME"] = "/home/flux"
    envMap["LANG"] = "en_US.UTF-8"
    envMap["LC_ALL"] = "en_US.UTF-8"
    envMap["XDG_RUNTIME_DIR"] = "/tmp"
    envMap["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin"

    // Add /tmp bind mount (mirrors CLI launcher script)
    val mountCmds = listOf(
        "busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true",
        "busybox mount --bind /dev $CHROOT_PATH/dev >/dev/null 2>&1 || true",
        "busybox mount --bind /sys $CHROOT_PATH/sys >/dev/null 2>&1 || true",
        "busybox mount -t proc proc $CHROOT_PATH/proc >/dev/null 2>&1 || true",
        "busybox mount -t devpts devpts $CHROOT_PATH/dev/pts >/dev/null 2>&1 || true",
        "mkdir -p $CHROOT_PATH/dev/shm && busybox mount -t tmpfs -o size=512M tmpfs $CHROOT_PATH/dev/shm >/dev/null 2>&1 || true",
        "mkdir -p $CHROOT_PATH/tmp && busybox mount --bind /data/data/com.ivarna.nativecode/files/usr/tmp $CHROOT_PATH/tmp >/dev/null 2>&1 || true"
    ).joinToString("; ")

    // … rest of cmd construction unchanged …
}
```

**Why:** Injecting env vars into `envMap` makes the **outer** `/system/bin/sh` session process carry them. Because the final command chain is `sh -c "su -c 'chroot … su - user -c \"…\"'"`, the environment is propagated through the `su` / `chroot` boundary as inherited variables.

> **Verification step:** After change, run `env` inside chroot shell and confirm `TERM`, `HOME`, `LANG`, `XDG_RUNTIME_DIR` are present.

### Phase 2 — Harden AI Tool Command for Chroot

**File:** `app/src/main/java/com/ivarna/nativecode/MainActivity.kt`

The `envInit` string (line ~1622) must guarantee the Node/npm global bin directory is on PATH even when `nvm.sh` fails or is skipped.

**Option A (recommended):** Inline the NVM node binary path into `envInit`:

```kotlin
val envInit = """
    export PATH=/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo/bin:/home/flux/.nvm/versions/node/v26.*/bin:/usr/local/bin:/usr/bin:/bin:\$PATH
    export NVM_DIR=/home/flux/.nvm
    [ -s /home/flux/.nvm/nvm.sh ] && . /home/flux/.nvm/nvm.sh
""".trimIndent().replace("\n", " ")
```

> `v26.*` glob expands in bash/zsh. If Node version may vary, use `$(ls -d /home/flux/.nvm/versions/node/v*/bin 2>/dev/null | tail -1)` instead.

**Option B:** Pre-compute the exact Node path at setup time and store it in a marker file (e.g. `/home/flux/.nvm/active_node_bin`), then source that file.

**Why:** Prepending the literal NVM bin path removes the dependency on `.zshrc` / `nvm.sh` being sourced in a non-interactive `-c` shell.

### Phase 3 — Harden `.zshrc` for Chroot

**File:** `app/src/main/assets/scripts/setup_customization_debian.sh`

Current `.zshrc` has:
```bash
{ fastfetch --config termux; pokemon-colorscripts --no-title -r 1,2,3 } &!
```

In a chroot with no `TERM`, these background jobs may print errors/garbage into the PTY before the prompt stabilizes. Add guard:

```bash
if [ -n "\$TERM" ] && [ -t 1 ]; then
    { fastfetch --config termux 2>/dev/null; pokemon-colorscripts --no-title -r 1,2,3 2>/dev/null } &!
fi
```

Also ensure `export TERM="${TERM:-xterm-256color}"` is at the top of `.zshrc` as a defensive fallback.

### Phase 4 — Verify Default Shell in Chroot

**File:** `app/src/main/assets/scripts/setup_customization_debian.sh`

The setup script runs:
```bash
chsh -s /bin/zsh "$CUSTOM_USER" 2>/dev/null
```

`chsh` may silently fail if `/etc/shells` does not list `/bin/zsh`, or if PAM restrictions apply inside the chroot. Add explicit verification and fallback:

```bash
# Force zsh as default shell
if grep -q "^/bin/zsh" /etc/shells 2>/dev/null; then
    chsh -s /bin/zsh "$CUSTOM_USER" 2>/dev/null || true
    # Verify
    if [ "$(grep "^${CUSTOM_USER}:" /etc/passwd | cut -d: -f7)" != "/bin/zsh" ]; then
        # Direct /etc/passwd edit fallback
        sed -i "s|^${CUSTOM_USER}:[^:]*:[^:]*:[^:]*:[^:]*:[^:]*:|${CUSTOM_USER}:x:1000:100::/home/${CUSTOM_USER}:/bin/zsh|" /etc/passwd 2>/dev/null || true
    fi
else
    echo "WARN: /bin/zsh not in /etc/shells, falling back to bash"
fi
```

> If zsh cannot be the default shell, the chroot tool launcher must fall back to `bash -c` instead of relying on `su - user -c` using the broken shell.

### Phase 5 — Regression Test Checklist

| Step | Action | Expected Result |
|---|---|---|
| 1 | Install/reset chroot Debian 13 | Setup completes without error |
| 2 | Open Terminal tab → "Debian Shell" | Zsh prompt renders correctly (agnosterzak powerline, no fragments) |
| 3 | Type `clear` | Screen clears, no "TERM environment variable not set" |
| 4 | Type `env \| grep -E 'TERM\|HOME\|LANG'` | All three vars present with correct values |
| 5 | Type `bash` then `clear` | Bash `clear` also works |
| 6 | Tap "opencode" card | Terminal opens, opencode TUI loads, no immediate exit |
| 7 | Tap "claude-code" card | claude-code TUI loads |
| 8 | Tap "codex" card | codex TUI loads |
| 9 | Switch back to proot mode | All above steps still work (no regression) |

---

## Files to Modify

1. `app/src/main/java/com/ivarna/nativecode/MainActivity.kt`
   - `buildLinuxCommand` chroot branch: add env vars + `/tmp` mount
   - `createNewTerminalSession`: update `envInit` string

2. `app/src/main/assets/scripts/setup_customization_debian.sh`
   - `.zshrc` template: add `TERM` fallback + guard background visuals
   - Shell change section: add `/etc/shells` verification + `/etc/passwd` fallback

---

## Notes / Risks

- `envMap` changes affect only the outer PTY process. If `su` inside chroot scrubs env (PAM `env_reset`), vars may still be lost. Mitigation: also export them inside the command string (`escapedCmd` prefix) as a belt-and-suspenders approach if testing reveals scrubbing.
- Node version `v26.*` glob is fragile if Node 27+ is installed later. Consider recording exact path during `setup_cli_tools.sh`.
- `setup_customization_debian.sh` is run inside both proot and chroot; changes must not break proot.
- Do **not** modify proot branch logic — it already works.
