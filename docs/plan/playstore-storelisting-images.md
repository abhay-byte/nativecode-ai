# Plan: Google Play Store Listing Screenshots (NativeCode)

**Status:** PLAN ONLY — do not generate until explicit go  
**Date:** 2026-08-01  
**Source shots:** `docs/screenshots/v1/`  
**Design SSOT:** `docs/project/ui_design.md` (Obsidian Terminal / cyber-brutalist)  
**Out root:** `docs/playstore/storelisting/`

| Folder | Method | Ship role |
|--------|--------|-----------|
| `python/` | Pillow only | Deterministic baseline |
| `ai/` | Image gen + real shots as refs | High-polish A/B arm |
| `hybrid/` | Python chrome + AI FX + real UI faces | Default Play candidate |

Folders exist (empty of assets; README stubs only). **No PNGs / scripts generated yet.**

---

## 1. Goal & audience

Maximize Play listing **visit → install CVR** for:

| Persona | Message axis |
|---------|----------------|
| Vibe coders | Code / ship on phone |
| Phone AI coders | Agents in pocket |
| Agentic developers | OpenCode · AGY · Codex · Claude Code |
| Project creators | Workspace · files · git |
| Entrepreneurs / hustlers | Build & ship anywhere |

**Primary promise:** *AI coding + real Linux on your phone.*  
**Product name:** NativeCode  
**Tagline:** Portable Linux & AI Developer Environment

---

## 2. Conversion research (stats → design rules)

### Benchmarks

| Finding | Number | Source class |
|---------|--------|--------------|
| Optimized screenshots lift installs | **~+18% CVR** (common ASO cite) | AppGuardians / ASO blogs |
| First **3 frames** conversion weight | **~70%** (pattern from 1,200+ top listings) | AppFollow 2026 ASO screenshots |
| Users who scroll screenshots | **~13%** scroll; **&lt;18%** see slot 2 | AppAgent creative ASO notes |
| Store page CVR band | **~4%–32%** by category | SplitMetrics / industry cites |
| Entertainment median CVR | **~8.6%** | SplitMetrics |
| Video watch-to-end | **~8%**; ~**7s** attention | AppAgent |
| Famous creative win | Up to **~30% CVR** after shot opt (Rovio/Angry Birds) | SplitMetrics case cite |
| A/B detectability | Need volume for **5–8%** lift; small 3% often noise | AppFollow testing hygiene |

### Design rules (from stats)

1. **Frame 01 sells alone** — outcome headline + logo + name + tagline (most users never scroll).
2. **01–03 = 70% weight** — hero · AI agents · projects only; densest proof.
3. **Benefit copy, not feature lists** — thumbnail-legible type (≥48–72px @ 1080w).
4. **Multi-UI per frame** — 2–4 real screenshots; proof density.
5. **Android portrait mockups** — sharp cyber-brutalist chrome, not iOS.
6. **Use all 8 slots** — later frames convert the ~13% who scroll + build trust.
7. **A/B later** — ship hybrid default; experiment vs python after volume.

---

## 3. Specs (all pipelines)

| Item | Value |
|------|--------|
| Orientation | **Portrait only** |
| Count | **8 per folder** |
| Canvas | **1080 × 1920** (9:16 phone) |
| Format | PNG, sRGB |
| Corners | **0px** sharp |
| Elevation | Two-tone hard L-shadow (`#3C4A3F` right / `#393939` bottom) |
| BG | `#131313` |
| Primary | `#3DDC84` / `#60f99e` |
| Text | `#FAFAFA` |
| Headlines | Space Grotesk (fallback Fira Sans Bold) |
| Labels | JetBrains Mono |

### Screenshot inventory (`docs/screenshots/v1`)

| File | Use |
|------|-----|
| `home.png` | Dashboard |
| `all_projects.png` | Project list |
| `project_workspace.png` | Workspace |
| `project_directory.png` | Files |
| `project_config.png` | Project settings |
| `git_diff.png` | Git / diff |
| `workspace_agent.png` | Agent flow |
| `terminal.png` | Debian shell |
| `terminal_opencode.png` | OpenCode |
| `terminal_agy.png` | AGY |
| `terminal_codex.png` | Codex |
| `terminal_claude_code.png` | Claude Code |
| `xfce4_display.png` | XFCE |
| `marketplace.png` | Marketplace |
| `software_management.png` | Packages |
| `settings.png` | Settings |
| `repairs.png` | Repairs |
| Logo | `app/src/main/res/drawable/logo_highres.webp` |

---

## 4. Eight-frame storyboard (shared brief)

| # | Stem | Headline | Sub | Shots |
|---|------|----------|-----|-------|
| 01 | `01_hero` | VIBE CODE ON YOUR PHONE | NativeCode · Portable Linux & AI Developer Environment | Logo center; bg collage: home, workspace, opencode, terminal |
| 02 | `02_ai_agents` | 4 AI CODING AGENTS | OpenCode · AGY · Codex · Claude Code — on device | 2×2: opencode, agy, codex, claude_code |
| 03 | `03_projects` | FULL PROJECT CONTROL | Workspace · files · git · config | workspace, directory, git_diff, project_config |
| 04 | `04_debian_ai` | REAL DEBIAN + AI CLIs | Shell + agents one tap | terminal + 4 AI terminals |
| 05 | `05_agentic` | AGENTIC DEV IN YOUR POCKET | Home → agent → ship | home, workspace_agent, all_projects |
| 06 | `06_xfce` | FULL XFCE DESKTOP | Linux GUI on Android | xfce4_display, marketplace |
| 07 | `07_ship` | SHIP FROM ANYWHERE | Marketplace · packages · git | marketplace, software_management, git_diff |
| 08 | `08_control` | BUILT FOR BUILDERS | Settings · repairs · you own the stack | settings, repairs, home |

### Layout skeleton (all frames)

```
┌─────────────────────────────┐ 1080
│  HEADLINE (benefit)         │ top ~12%
│  sub / mono chips           │
│                             │
│   phone frames (2–4)        │ ~70% — real UI faces
│   hard L-shadow, 0-radius   │
│                             │
│  [chip] [chip]              │ bottom optional
└─────────────────────────────┘ 1920
```

---

## 5. Three pipelines (implement later)

### A. `python/`

- `generate_storelisting.py` — Pillow only
- Phone bezels, type, shadows, collage math
- **Pros:** exact brand, free reruns  
- **Cons:** less “ad” polish

### B. `ai/`

- `PROMPTS.md` + Imagine / image_edit per frame
- Refs: matching v1 screenshots + logo
- **Pros:** marketing impact  
- **Cons:** UI drift risk → QA against real shots

### C. `hybrid/`

- Python: canvas, type, chips, bezel grid, paste **real** screenshot faces
- AI: backgrounds, glow, lifestyle layers → `hybrid/ai_assets/`
- `generate_hybrid.py` composites both
- **Pros:** trust + polish → **default Play upload candidate**

### Ship order (when approved)

1. python baseline (8 PNGs)  
2. ai variants (8 PNGs)  
3. hybrid default (8 PNGs)  
4. Visual QA → pick hybrid (or winner) for Console  
5. Later: Play experiments hybrid vs python

---

## 6. Implementation checklist (blocked until go)

- [ ] User says go on generate  
- [ ] `python/generate_storelisting.py` + run → 8 PNGs  
- [ ] `ai/PROMPTS.md` + AI gen → 8 PNGs  
- [ ] `hybrid/generate_hybrid.py` + AI assets → 8 PNGs  
- [ ] QA: type size, contrast, multi-shot legibility, brand tokens  
- [ ] Optional contact sheet per folder  

### Explicitly out of scope this plan

Feature graphic 1024×500 · promo video · locales · tablet · live A/B setup

---

## 7. Success criteria

- 3 folders under `docs/playstore/storelisting/` with 8× 1080×1920 each when generated  
- Frame 01: logo + full name + tagline + multi-shot bg  
- Frame 02: all 4 AI tool screenshots  
- Frame 03: project suite  
- Frame 04: Debian + AI  
- Frames 05–08: agent · XFCE · ship · control  
- Design matches `ui_design.md`  
- No generation without re-approval  

---

## 8. Stop line

**Plan complete. No image generation. No more scripts. Await go.**
