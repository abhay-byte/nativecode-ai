# Store Listing Image Plan — NativeCode

Status: Ready to implement
Target: Google Play Store phone screenshots, 1080×1920 portrait PNG (up to 8 allowed per device)
Output dir: `docs/storelisting/`
Design source: `docs/project/ui_design.md` (Cyber-Brutalist, Obsidian, Terminal Green)

## 1. Goal

Drive installs from vibe coders, phone-first AI devs, entrepreneurs and hustlers. Every image must answer in < 3 seconds: "What is this?" + "Why do I care?" — with real product UI, large readable text, zero overlap.

## 2. Conversion research (sources)

- screenkit.tools/guides/google-play-screenshot-best-practices
- screenhance.com/play-store-screenshots
- appradar.com/blog/android-app-screenshot-sizes-and-guidelines-for-google-play

Findings applied:
1. **First screenshot is the promise** — static, no video needed; hero = logo + full name + tagline + product screenshots behind. Users decide in search results on the first 1–2 screenshots.
2. **1080×1920 portrait** is the practical default for phone listings.
3. **Real UI large enough to inspect** — tiny phone mockups inside huge decoration lose installs. Decoration must not shrink the product.
4. **Short benefit headline per screenshot** (≤ 6 words ideal), big type, one accent color; headline sells, UI proves.
5. **Focused set of 8 beats repetition** — each image = one distinct claim; no repeated screenshots across set except deliberate hero reuse.
6. **Copy localized-friendly** — leave generous margins, keep text short.
7. **No transparency overlap** — text never covers UI; fixed layout zones (text zone top, UI zone bottom).
8. **Visual consistency** — one campaign look: obsidian bg, Terminal Green accent, Space Grotesk headlines, JetBrains Mono labels, sharp edges.

## 3. Target audience

- Vibe coders — "AI writes it, I ship it"
- Phone-first developers — no PC, code from pocket
- Entrepreneurs / hustlers — build MVPs anywhere
- Students / tinkerers — full Linux on Android

Message pillars: AI on device, full Linux, projects anywhere, easy setup, no PC required.

## 4. Design system (from ui_design.md)

| Token | Value |
|---|---|
| Background | `#0A0A0A` |
| Surface | `#121212`, container `#1E1E1E`, bright `#393939` |
| Text | `#FAFAFA` |
| Accent (Terminal Green) | `#3DDC84` |
| Accent dim | `#3C4A3F` |
| Headline | Space Grotesk 700 (48–72px) |
| Labels/data | JetBrains Mono (20–28px) |
| Corners | 0px (sharp) |
| Elevation | two-tone hard shadows (right `#3C4A3F`, bottom `#393939`), 1px inner top/left highlight `#FFFFFF20` |
| Bg texture | 12px grid lines `#131313`, scanlines `#FFFFFF05`, corner brackets, code-glyph watermark `#1E1E1E` |
| Glow | radial Terminal Green `#3DDC84` 8–12% opacity, clipped behind UI zone |

Layout zones (1080×1920):
- Top zone 0–560px: headline + sub + mono label (never under 64px safe top margin)
- UI zone 640–1920px: phone screenshot cards (real UI, min 45% of canvas)
- Chips row: JetBrains Mono, sharp rectangles, `#1E1E1E` bg, green border 1px

## 5. The 8 images

Screenshot pool: `docs/screenshots/v1/*.png` (1264×2780, aspect ≈ 9:20 — cards mask-crop, keep top-weighted or center focus).

### 1. `1_hero.png` — THE PROMISE
- Center: logo.webp (mipmap-nodpi, white/green on transparent) + full name "NativeCode"
- Tagline: "VIBE CODE ON YOUR PHONE" (headline, green)
- Sub: "Full Linux + AI coding agents in your pocket. No PC needed."
- Mono label top: `// AI DEV ENVIRONMENT FOR ANDROID`
- Behind: 3 blurred/dimmed screenshots (home, terminal, marketplace) angled + green glow
- CTA chip: "GET NATIVECODE" (green button style)

### 2. `2_ai_agents.png` — "7 CLI TOOLS. ONE POCKET."
- Headline: "7 CLI TOOLS. ONE POCKET."
- Sub: "Claude Code · Codex · OpenCode · Agy · Grok · Qwen · Kiro"
- Grid 2×2: terminal_opencode, terminal_codex, terminal_agy, terminal_claude_code
- Chips row: opencode · codex · agy · claude-code · grok · qwen · kiro

### 3. `3_projects.png` — "BUILD PROJECTS ANYWHERE"
- Headline: "BUILD PROJECTS ANYWHERE"
- Sub: "Workspace · files · git diff · project config"
- Grid 2×2: project_workspace, project_directory, git_diff, project_config

### 4. `4_linux_shell.png` — "A FULL LINUX PC IN YOUR POCKET"
- Headline: "A FULL LINUX PC IN YOUR POCKET"
- Sub: "Debian 13 · apt · bash · root terminal"
- Big card top: terminal (Debian shell)
- Bottom strip 4 small: opencode, codex, agy, claude (reuse = same campaign claim "AI runs on the shell")

### 5. `5_marketplace.png` — "INSTALL ANYTHING. INSTANTLY."
- Headline: "INSTALL ANYTHING. INSTANTLY."
- Sub: "Curated catalog · one tap · no PC"
- Grid: marketplace (large) + software_management + repairs (small side)
- Mono chips: `marketplace` `software-manager` `repairs`

### 6. `6_agent_workspace.png` — "YOUR AGENT. ALWAYS ON."
- Headline: "YOUR AGENT. ALWAYS ON."
- Sub: "Long-running workspace sessions on-device"
- Cards: workspace_agent (large) + all_projects (small)

### 7. `7_desktop.png` — "DESKTOP LINUX GUI"
- Headline: "DESKTOP LINUX GUI"
- Sub: "XFCE4 · Termux-X11 · VirGL GPU"
- Cards: xfce4_display (large) + settings + home (small row)

### 8. `8_cta.png` — CLOSER
- Headline: "CODE ANYWHERE. SHIP EVERYTHING."
- Sub: "No PC · No root (PRoot) · 10 GB free"
- Cards: home + marketplace + workspace_agent strip (small)
- Big green CTA button: "DOWNLOAD NATIVECODE"

## 6. Technical spec

- Canvas: 1080×1920 RGB PNG
- Screenshots scaled to card width, masked-clipped, 1px green border, two-tone offset shadow
- No text/UI overlap: enforced by zones + automated assert (text bbox vs card bbox)
- Fonts: Space Grotesk variable (700), JetBrains Mono Bold — cached in `/tmp/opencode/fonts/` (fallback Fira Sans Bold / DejaVu Sans Bold)
- Generator: `scripts/storelisting_generator.py` (Pillow ≥ 10)

## 7. Validation checklist

- [ ] All 8 files 1080×1920, PNG, no alpha issues
- [ ] Text bboxes never intersect card bboxes (script asserts)
- [ ] Headlines ≤ 6 words / ≤ 40 chars
- [ ] Screenshots ≥ 45% of canvas
- [ ] Consistent campaign look (bg, accent, fonts)
- [ ] No emoji, no transparency
