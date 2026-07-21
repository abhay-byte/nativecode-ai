<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->

<!-- context7 -->
Use Context7 MCP to fetch current documentation whenever the user asks about a library, framework, SDK, API, CLI tool, or cloud service -- even well-known ones like React, Next.js, Prisma, Express, Tailwind, Django, or Spring Boot. This includes API syntax, configuration, version migration, library-specific debugging, setup instructions, and CLI tool usage. Use even when you think you know the answer -- your training data may not reflect recent changes. Prefer this over web search for library docs.

Do not use for: refactoring, writing scripts from scratch, debugging business logic, code review, or general programming concepts.

## Steps

1. Always start with `resolve-library-id` using the library name and the user's question, unless the user provides an exact library ID in `/org/project` format
2. Pick the best match (ID format: `/org/project`) by: exact name match, description relevance, code snippet count, source reputation (High/Medium preferred), and benchmark score (higher is better). If results don't look right, try alternate names or queries (e.g., "next.js" not "nextjs", or rephrase the question). Use version-specific IDs when the user mentions a version
3. `query-docs` with the selected library ID and the user's full question (not single words)
4. Answer using the fetched docs
<!-- context7 -->

# Agent Instructions

MANDATORY: Use caveman + context-mode at all times. Not optional. Not sometimes. Every turn.

## Skills Available

This directory contains a release pipeline:

| Skill | Purpose |
|-------|---------|
| `todo-triage/` | Intake: manual feature/bug + GitHub issue import → `/docs/todo/todo.md` |
| `dev-cycle/` | Build: pick todo → branch → plan → impl → build & run → manual test → PR → review → merge to version branch |
| `review/` | 8-pass review (build, plan adherence, correctness, security, performance, style, tests, docs). Spawned in dev-cycle 2.8 and release 1.4. |
| `release/` | Ship: changelog → build & run → review → GitHub Release → merge to main → satisfaction check |

Use the appropriate skill based on the user's intent. Skills cross-reference each other.

---

## 1. Caveman Mode — ALWAYS ON

All communication ultra-compressed:
- No pleasantries, filler, explanations
- Shortest possible technical answer. 1-3 lines max for status/chat
- No "Here is what I did" / "I've gone ahead" / "Let me explain"
- No markdown fluff — just facts
- If 5 words works, use 5 words
- Exceptions: required artifacts (plans, reviews, changelogs, PR bodies, release notes) and code blocks. Keep those complete but tight.

---

## 2. Context-Mode MCP Tools — ALWAYS ON (STRICT ENFORCEMENT)

11 tools. Use this priority:

### GATHER & PROCESS (first choice)

| Tool | When | Why |
|------|------|-----|
| `ctx_batch_execute` | 3+ related commands + queries | One round-trip, auto-indexed |
| `ctx_execute` | Filter/count/parse/aggregate data | Think-in-Code: bytes stay in sandbox |
| `ctx_execute_file` | Analyze one file | Same, scoped to one file |

### STORE & SEARCH (second choice)

| Tool | When | Why |
|------|------|-----|
| `ctx_fetch_and_index` | Web docs, changelogs, API refs | Indexed, searchable later |
| `ctx_index` | Store content (docs, specs, output) | FTS5 knowledge base |
| `ctx_search` | Query stored content | BM25 + stem/trigram search |

### UTILITY

| Tool | When |
|------|------|
| `ctx_stats` / `ctx_doctor` / `ctx_upgrade` / `ctx_purge` / `ctx_insight` | Meta |

### Shell via ctx_execute — NOT raw Shell/Bash

STRICT RULE: Raw shell execution is prohibited for commands that produce inspectable/filterable output (tests, builds, logs, file searching, git log, directory listings). You MUST execute them inside `ctx_execute` (or `ctx_batch_execute`):

```
ctx_execute(language: "shell", code: "rtk npm test 2>&1 | grep -E 'FAIL|Error:'")
```

Raw shell/Bash tool is strictly restricted to:
- State mutations (git push, mkdir, rm, mv, chmod, npm install, docker)
- Quick status checks with minimal output (git status, pwd, whoami)

### General Routing Rules

- `ctx_execute` / `ctx_batch_execute` over raw shell when processing or viewing data
- `ctx_execute_file` over Read when analyzing a file
- `ctx_fetch_and_index` over WebFetch for web content
- `ctx_search` over re-reading raw sources
- NEVER use raw shell for `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`

---

## 3. Context7 — Documentation

| Step | Tool |
|------|------|
| 1 | `context7_resolve-library-id` — find correct library ID |
| 2 | `context7_query-docs` — query docs with full question |

Library/framework/API docs only. NOT for refactoring, debugging, code review, general programming.

---

## 4. Web Search

`websearch` / `google_search` for current events, pricing, versions, people, companies.
`ctx_fetch_and_index` + `ctx_search` preferred over raw WebFetch for doc content.

---

## 5. Approval + Prompt Protocol

Always get explicit user approval before state-changing work:
- creating branches, commits, PRs, releases, tags, or GitHub comments
- editing files, moving todo state, archiving todo files, or cleanup
- starting implementation after a plan, merging, shipping, or destructive actions

Read-only inspection can proceed without approval.

All skills use `question(...)` as pseudocode for the host's native prompt:
- OpenCode: use the available question/ask tool if present
- Claude Code: use the available ask/permission prompt if present
- Codex: use `request_user_input` when available; otherwise ask one direct chat question and stop
- Agy/other agents: use the platform's equivalent structured prompt

If no native prompt tool exists, ask a concise plain-text question and wait. Never simulate approval, infer approval from silence, or continue past an approval gate.

