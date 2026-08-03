# Onboarding feature slides UI redesign

**Date:** 2026-08-03  
**Status:** implemented (local)  
**Scope:** Feature slideshow only (`buildSlideshowPage()` / page 2)  
**Primary file:** `app/src/main/java/com/zenithblue/nativecode/OnboardingActivity.kt`  
**Assets:** keep existing `res/drawable/img_slide_*.png` (1132×1636) — images are good  
**Design system:** `docs/project/ui_design.md` (Obsidian Terminal / Cyber-Brutalist)  
**Note:** Applied plan + external diff onto local tree (7 slides incl. MARKETPLACE). Replaced letterbox/aspect-measure patch.

---

## 1. Problem (from device screenshot + code)

User report: **slide images perfect; UI broken** — image placement wrong, describing text too small.

### Observed UI (screenshot)

| Zone | What user sees |
|------|----------------|
| Hero | Full mock card (traffic lights, `WORKSPACE` title, file tree, status bar) sits inside another cyber-brutalist frame |
| Placement | Mock looks “inset / floating” with uneven dark gutters; not a confident full-width hero |
| Meta | `[ WORKSPACE — 01 / 07 ]` + title + body under image |
| Type | Body copy is hard to read; hierarchy weak vs mock chrome |

### Current code path

`OnboardingActivity.buildSlideshowPage()` (~L433–L760):

1. Page: `smallHeader("Core Capabilities")` + weight-1 `contentFrame` + dots + Back/Continue.
2. Card: vertical `LinearLayout` + `cyberBrutalistBg` + pad 8dp (+ shadow extrusion).
3. **Hero:** weight-1 `heroSlot` → custom `imageFrame.onMeasure` forces asset aspect `1132/1636` → centers in slot → `ImageView` `FIT_XY`.
4. **Meta (wrap):** tag 11sp · title 17sp · desc **13sp** · `maxLines=3` ellipsize.
5. Recent local diff *reduced* type vs prior (title 18→17, desc 14→13) while fighting crop via letterbox.

### Root causes

| # | Cause | Effect |
|---|--------|--------|
| R1 | **Portrait assets + stacked chrome** | 1132×1636 (~0.69 w/h) needs ~1.45× width in height. After header (~38dp) + meta (~90–110dp) + dots + buttons + pads, hero height often **less than width/aspect** → measure shrinks width → side letterbox OR tiny mock. |
| R2 | **Double frame** | Asset already has window chrome + PREVIEW pill. App adds outer card, inner stroke box, 8dp pad → mock feels nested/misplaced. |
| R3 | **Custom measure + FIT_XY** | Aspect box centers in slot; empty `#0a0a0a` bars read as “wrong placement.” FIT_XY only safe if measure always exact; layout path is fragile. |
| R4 | **Meta starved** | Vertical budget given to “show full mock” → meta type shrunk; desc below design `body-md` (16px) / `body-lg` (18px). |
| R5 | **Page header cost** | `smallHeader` uses 20sp + 16dp bottom pad — steals hero space on short phones. |
| R6 | **Hardcoded aspect** | `1132f/1636f` not from drawable intrinsics; breaks if assets re-exported. |

**Not in scope to “fix”:** regenerating slide art, install/setup pages, swipe/dots behavior, slide copy content (except optional shorten if still clipping after type bump).

---

## 2. Goals

1. **Hero placement:** mock fills card width edge-to-edge (front face), no side gutters; no crop of traffic lights / PREVIEW / home pill / tree root.
2. **Readable meta:** title + description match design hierarchy; desc no longer “caption-sized.”
3. **All 7 slides** share one layout (workspace, AI, dev, XFCE, Debian, marketplace, git).
4. **Keep assets** as-is (`img_slide_workspace` … `img_slide_git` + marketplace).
5. **Cyber-Brutalist** tokens only (`NC.*`, 0 radius, extrusion shadow, mono labels).
6. **No behavior change** outside slideshow chrome (slide order, navigation, swipe, Continue → requirements).

---

## 3. Non-goals

- Re-author SVG/PNG slides or change 1132×1636 content.
- Compose migration / ViewPager2 rewrite (optional later).
- Auto-play carousel.
- Landscape-specific redesign (phone portrait first; landscape must not crash).
- Changing intro, requirements, isolation, plan, setup, complete pages.

---

## 4. Chosen layout: **width-first hero + reserved meta band**

Tradeoff: pure “never crop, letterbox in slot” (current) loses size and placement. Pure `CENTER_CROP` (older) slices chrome.

**Chosen middle path:**

```
┌─ page (pad 12 horizontal, 8 vertical) ─────────────────┐
│  compact page label (optional, slim)                     │
│  ┌─ card (MATCH width, weight 1) ─────────────────────┐  │
│  │  ┌─ hero (MATCH × weight 1, clip) ───────────────┐ │  │
│  │  │  ImageView MATCH/MATCH                          │ │  │
│  │  │  scaleType = FIT_CENTER (or MATRIX fit-width)   │ │  │
│  │  │  bg #0e0e0e only if residual letterbox top/bot  │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  │  ┌─ meta band (WRAP, minHeight, NO weight) ────────┐ │  │
│  │  │  category mono row                               │ │  │
│  │  │  icon + title                                    │ │  │
│  │  │  description (2–4 lines, larger)                 │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └────────────────────────────────────────────────────┘  │
│  dots                                                     │
│  [ Back ] [ Continue ]                                    │
└───────────────────────────────────────────────────────────┘
```

### 4.1 Image placement rules

1. **Width-first:** hero `LayoutParams(MATCH, 0, 1f)`; `ImageView` MATCH×MATCH.
2. **Scale:** `ImageView.ScaleType.FIT_CENTER` (preserve aspect, no stretch). Prefer this over custom `onMeasure` + `FIT_XY`.
3. **Padding on image:** **0** inside hero. Card front-face padding only on meta band (or card pad 0 top/sides for hero flush; keep bottom/side pad for shadow face if LayerDrawable requires it).
4. **Remove** hardcoded `slideAspectWOverH` custom `FrameLayout` — use intrinsic drawable aspect via FIT_CENTER.
5. **Inner stroke box** around hero: optional 1dp border *on hero container only if* it does not inset image more than 1dp; avoid double “picture frame.” Prefer border on outer card only.
6. **If short phone still letterboxes top/bottom:** acceptable thin bars (same `#0e0e0e` as `SURFACE_LOWEST`); **side** letterbox should be rare because width is MATCH and asset is taller than wide.
7. **Safety against crop of chrome:** FIT_CENTER never crops. If mock is too small on short devices, reclaim space (below) before any crop.

### 4.2 Vertical budget reclamation (so image stays large)

| Change | Saving (approx) |
|--------|------------------|
| Slim or remove `smallHeader("Core Capabilities")` → mono label `CAPABILITIES` 12–13sp, 8dp bottom | ~20–28dp |
| Page pad 16→12 / top 8 | ~8–12dp |
| Meta internal pad tighten but **type larger** (net: fixed ~108–120dp meta, not weight) | stable |
| Dots margin top 4 / bottom 8 | small |
| Card pad: top/sides 0 for hero flush; meta pad horizontal 12, vertical 12 | better placement |

Target on ~720–800dp tall phones: hero ≥ **52–58%** of content column.

### 4.3 Optional upgrade (if still cramped after §4.1–4.2)

**Gradient meta overlay** on bottom ~28% of hero:

- Full-bleed image (even more height).
- Semi-opaque gradient `#00000000 → #E6000000` under text.
- Tag/title/desc drawn on overlay with **larger** type (high contrast on dark mock).

Use only if stacked meta still forces hero &lt; ~45% height on min supported device. Default plan = **stacked reserved band** first (clearer a11y, no text-on-tree risk).

---

## 5. Typography (must implement)

Align to `ui_design.md` mobile scale:

| Element | Current | Target | Token / note |
|---------|---------|--------|--------------|
| Page label | 20sp bold header | **12–13sp** mono primary or drop | `label-sm` / compact |
| Category tag | 11sp mono | **12–13sp** mono primary | `label-sm`+ |
| Title | 17sp bold | **20–22sp** bold `ON_SURFACE` | near `headline-md` 24 → mobile 20–22 |
| Description | **13sp** | **15–16sp** `ON_SURF_VAR` | `body-md` 16px intent |
| Line spacing | 1.3 | **1.35–1.45** | readability |
| Icon next to title | 22dp | **24–26dp** | balance with larger title |
| maxLines desc | 3 | **3–4** | allow full current strings; prefer no ellipsis on Pixel-class widths |

### Description strings (keep content; optional soft wrap polish)

No rewrite required for plan approval. If after 16sp + maxLines 4 a string still ellipsizes on 360dp:

- Marketplace (longest) → trim one clause only.
- Others leave as-is.

---

## 6. Implementation steps

### Step 0 — Baseline

- [ ] Capture current slideshow screenshots for slides 01–07 (force onboarding).
- [ ] Note uncommitted slideshow diff; treat redesign as **replace** that letterbox approach, not stack hacks.

### Step 1 — Rebuild `renderSlideCard`

File: `OnboardingActivity.kt` only (unless drawable selector needed — not expected).

1. Delete custom `imageFrame` `onMeasure` aspect math and `slideAspectWOverH`.
2. Structure:

```kotlin
// card: VERTICAL, cyberBrutalistBg, clipChildren=true
// hero: FrameLayout weight=1, bg SURFACE_LOWEST, pad 0
//   ImageView MATCH/MATCH, FIT_CENTER, setImageResource(...)
// meta: VERTICAL WRAP, topMargin 0, pad (12,12,12,10)  // account shadow face on card
//   tag, headerRow, descTv  // sizes per §5
```

3. Card padding strategy:
   - Prefer `setPadding(0, 0, shadowOff, shadowOff)` so image is flush to front face top-left; meta gets its own horizontal padding.
   - Confirm `cyberBrutalistBg` LayerDrawable still draws shadow correctly with zero content pad on top/left.

4. Restore/increase type per §5 (do **not** keep 13sp desc).

5. `clipToPadding` / `clipChildren` on hero true; contentFrame still `clipChildren=false` for card shadow.

### Step 2 — Page chrome

1. Replace `smallHeader("Core Capabilities", …)` with compact mono label **or** remove and rely on category tag inside card.
2. Tighten root padding: `dp(12), dp(8), dp(12), dp(8)`.
3. contentFrame margins: top 0–4, bottom `shadowOff + 4`.
4. Dots: keep 7 rect dots; margins top 6 bottom 8.

### Step 3 — ImageView robustness

```kotlin
scaleType = ImageView.ScaleType.FIT_CENTER
adjustViewBounds = false
// optional: colorFilter null; no matrix unless needed
```

- Do not use `CENTER_CROP` (regresses chrome crop).
- Do not use `FIT_XY` (stretch risk).
- Load via existing `R.drawable.img_slide_*` mapping (all 7 types including MARKETPLACE).

### Step 4 — Density / small height

- If `resources.displayMetrics.heightPixels` (or contentFrame measured height) is very small, reduce **only**: page label, meta vertical pad, desc maxLines stay ≥3.
- Do not shrink title below 18sp or desc below 14sp as a “fix.”

### Step 5 — Verify all slides

Manual (or adb screenshot loop):

| # | Category | Asset |
|---|----------|--------|
| 01 | WORKSPACE | `img_slide_workspace` |
| 02 | AI ENGINE | `img_slide_ai` |
| 03 | RUNTIMES | `img_slide_dev` |
| 04 | DESKTOP | `img_slide_xfce` |
| 05 | LINUX OS | `img_slide_debian` |
| 06 | MARKETPLACE | `img_slide_marketplace` |
| 07 | CONTROL | `img_slide_git` |

Checks per slide:

- [ ] Traffic lights / top bar fully visible  
- [ ] PREVIEW (or equivalent) not clipped  
- [ ] No horizontal letterbox gutters  
- [ ] Title legible at arm length  
- [ ] Full desc readable without cut-off (or intentional soft wrap, no mid-word clip)  
- [ ] Swipe + dots + Back on slide 0 → intro + Continue last → requirements  

### Step 6 — Cleanup

- Remove dead comments about CENTER_CROP / aspect box.
- Keep `SlideData` / `PreviewType` as-is.
- No asset copy unless density buckets needed (currently plain `drawable/` — OK).

---

## 7. Files touched

| Path | Action |
|------|--------|
| `app/src/main/java/com/zenithblue/nativecode/OnboardingActivity.kt` | Rewrite `buildSlideshowPage` / `renderSlideCard` layout + type |
| `docs/plan/onboarding-feature-slides-ui-redesign.md` | This plan |
| `app/src/main/res/drawable/img_slide_*.png` | **No change** |
| Layout XML | None (programmatic UI) |

---

## 8. Acceptance criteria

1. On a mid-size phone (~360×800 dp class): feature slide hero is **full card width**, mock UI clearly readable, no side black bars.  
2. Description text ≥ **15sp**, title ≥ **20sp**, category ≥ **12sp** mono.  
3. No crop of mock window chrome on any of the 7 slides.  
4. Card still Cyber-Brutalist (0 radius, extrusion, `NC` colors).  
5. Navigation/swipe/dots unchanged in behavior.  
6. Build compiles; no new lint-fatal issues in touched method.

---

## 9. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Very short devices: FIT_CENTER → small image | Reclaim header/pad first; overlay meta only as fallback |
| Zero card pad breaks shadow drawable | Keep right/bottom pad = `shadowOff`; test bg on device |
| Long marketplace desc ellipsizes | maxLines 4 + 16sp; trim string only if needed |
| Landscape phone: squat hero | FIT_CENTER letterbox top/bot OK; no crash |
| Uncommitted prior patch conflicts | Single rewrite of slideshow block; drop aspect-measure code |

---

## 10. How this will be fixed (summary for you)

1. **Stop** the current “aspect-fit box centered in a tall slot” approach — that is why the mock looks misplaced (gutters + shrunken width).  
2. **Make the image full-bleed width** inside the card with `FIT_CENTER` so the whole mock is visible without stretch or chrome crop.  
3. **Reserve a fixed meta band** under the image with **larger** title/description (design-system sizes), instead of shrinking type to feed the hero.  
4. **Reclaim vertical space** from the page header and padding so the hero stays dominant without needing crop.  
5. **Do not touch** the PNG slide art.  
6. Verify all **7** slides on device after the single-file Kotlin change.

---

## 11. Approval gate

Per project protocol: **no code change until you approve this plan.**

Reply with one of:

- **Approve** — implement §6 in `OnboardingActivity.kt`  
- **Approve + overlay** — stacked first failed on short phones; jump to gradient overlay (§4.3)  
- **Change X** — adjust type sizes / keep page header / etc.

---

## Appendix A — Current type vs design

| Role | Code now | Design yaml | Plan |
|------|----------|-------------|------|
| Label | 11sp | label-sm 12 / label-md 14 | 12–13sp |
| Title | 17sp | headline-md 24 / mobile ~20 | 20–22sp |
| Body | 13sp | body-md 16 / body-lg 18 | 15–16sp |

## Appendix B — Asset map (unchanged)

```
PreviewType.PROJECT_TREE  → R.drawable.img_slide_workspace
PreviewType.AI_CLI        → R.drawable.img_slide_ai
PreviewType.DEV_SUITE     → R.drawable.img_slide_dev
PreviewType.XFCE_GUI      → R.drawable.img_slide_xfce
PreviewType.DEBIAN_ENV    → R.drawable.img_slide_debian
PreviewType.MARKETPLACE   → R.drawable.img_slide_marketplace
PreviewType.GIT_DIFF      → R.drawable.img_slide_git
```

All PNGs: **1132×1636**, portrait product mocks.
