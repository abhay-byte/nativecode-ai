---
name: Obsidian Terminal
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#bbcbbc'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#869587'
  outline-variant: '#3c4a3f'
  surface-tint: '#43e188'
  primary: '#60f99e'
  on-primary: '#00391c'
  primary-container: '#3ddc84'
  on-primary-container: '#005c31'
  inverse-primary: '#006d3b'
  secondary: '#c8c6c5'
  on-secondary: '#303030'
  secondary-container: '#474746'
  on-secondary-container: '#b7b5b4'
  tertiary: '#ffd6ba'
  on-tertiary: '#4e2600'
  tertiary-container: '#ffb175'
  on-tertiary-container: '#79420e'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#66fea2'
  primary-fixed-dim: '#43e188'
  on-primary-fixed: '#00210e'
  on-primary-fixed-variant: '#00522b'
  secondary-fixed: '#e5e2e1'
  secondary-fixed-dim: '#c8c6c5'
  on-secondary-fixed: '#1b1b1c'
  on-secondary-fixed-variant: '#474746'
  tertiary-fixed: '#ffdcc4'
  tertiary-fixed-dim: '#ffb781'
  on-tertiary-fixed: '#2f1400'
  on-tertiary-fixed-variant: '#6e3905'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.2'
  body-lg:
    fontFamily: Geist
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: '1.2'
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1.2'
spacing:
  base: 4px
  unit-1: 4px
  unit-2: 8px
  unit-4: 16px
  unit-6: 24px
  unit-8: 32px
  unit-12: 48px
  gutter: 24px
  margin-mobile: 16px
  margin-desktop: 48px
---

## Brand & Style

This design system is built for high-performance environments where speed and legibility are critical. It employs a **Cyber-Brutalist** aesthetic that blends the raw energy of early computing terminals with a sophisticated, modern tactile finish. 

The personality is authoritative, precise, and unapologetically technical. It targets developers, power users, and data analysts who require a UI that feels like a physical piece of hardware. The emotional response is one of total control and "locked-in" focus. By combining deep obsidian surfaces with a vibrant green accent, the design creates a high-contrast environment that minimizes eye strain during long sessions while highlighting critical interactive elements.

## Colors

The palette is rooted in a deep "Void" foundation to maximize contrast and depth. 

- **Terminal Green (#3DDC84):** Used exclusively for primary actions, success states, and critical data points. It is the sole "light source" in the interface.
- **Surface Palette:** A tiered system of darks ranging from **#0A0A0A (Base Background)** to **#121212 (Surface)** and **#1E1E1E (Surface-Container)**. This creates logical grouping without relying on bright borders.
- **Pure White (#FAFAFA):** Reserved for all text content to ensure maximum readability against the dark surfaces.
- **Shadow Tones:** Because every surface already sits close to black, shadows are built from tones *lighter* than the surfaces they sit on — see Elevation & Depth below — rather than from pure black, which would disappear into the background.

## Typography

The typography strategy emphasizes technical precision. 

- **Headlines:** Space Grotesk provides a geometric, futuristic feel with tight tracking for a high-impact presence.
- **Body:** Geist offers a clean, neutral, and highly readable experience for documentation and long-form data.
- **Labels/Data:** JetBrains Mono is used for functional metadata, code, and status labels, reinforcing the "Terminal" aesthetic.

All text must be rendered in `#FAFAFA`. On `Terminal Green` backgrounds (like primary buttons), the text should flip to `#0A0A0A` for legibility.

## Layout & Spacing

The layout follows a **Rigid Grid** philosophy. Elements are strictly aligned to an 8px baseline grid to maintain a structured, engineering-led feel. 

- **Desktop:** 12-column grid with 24px gutters. Use heavy margins (48px+) to allow the 3D elements "room to breathe."
- **Mobile:** 4-column grid with 16px gutters and margins.
- **Spacing:** Use spacing tokens to create clear separation between extruded panels. Group related controls within common surface containers to manage visual complexity.

## Elevation & Depth

Elevation is not communicated through soft shadows or blurs, nor through a single flat shadow color — it's communicated through a **Two-Tone Physical Extrusion**. Because every surface in this system already sits close to black, a flat black shadow has nowhere to go — it disappears into the background instead of reading as depth. Elevation is instead built from two tones *lighter* than the surrounding surface, representing the two visible faces of a raised slab under a top-down light source.

- **Two-Tone Hard Shadow:** All interactive elements (buttons, cards) cast a single, solid offset shadow (6px 6px), split internally into two flat color zones with a hard edge — no blur, no gradient transition between them:
  - **Right face** (a ~4px vertical strip closest to the element): `#3C4A3F` (`outline-variant`) — a dim, green-tinted tone representing the side of the block catching ambient reflected light from the Terminal Green light source.
  - **Bottom face** (the remaining L-shaped portion of the offset): `#393939` (`surface-bright`) — a neutral, lighter tone representing the block's underside, more occluded from the light.
  - These two zones form a single contiguous slab (an L-shape: thin right face + larger bottom face). This is **not** two stacked or receding shadows — it must render as one solid shadow shape split into two color regions, never as multiple offset `box-shadow` layers.
- **Inner Highlights:** Use a 1px top and left inner border (stroke) of `#ffffff20` (low-opacity white) on components to simulate a "beveled edge" catching a top-down light source.
- **Active State:** When an element is pressed, the offset shrinks from 6px 6px to 2px 2px — both the right-face and bottom-face tones scale down together, proportionally — and the element translates 4px in both X and Y to simulate physical depression.

## Shapes

The shape language is strictly **Sharp (0px)**. 

To maintain the brutalist aesthetic and the mechanical feel of the design system, no rounded corners are permitted. This applies to buttons, cards, input fields, and checkboxes. The sharp corners reinforce the grid and emphasize the two-tone offset shadows, making the 3D effect appear more architectural and structural.

## Components

- **Buttons (Primary):** Background: `#3DDC84`; Text: `#0A0A0A`; Shadow: two-tone 6px 6px L-shape offset — right face `#3C4A3F`, bottom face `#393939`. No border. On hover, the green should slightly brighten.
- **Buttons (Secondary):** Background: `#1E1E1E`; Text: `#FAFAFA`; Border: 1px solid `#3DDC84`; Shadow: two-tone 6px 6px L-shape offset — right face `#3C4A3F`, bottom face `#393939`.
- **Cards/Panels:** Background: `#121212`; Shadow: two-tone 6px 6px L-shape offset — right face `#3C4A3F`, bottom face `#393939`. Use the 1px subtle inner highlight for the beveled effect.
- **Input Fields:** Background: `#0A0A0A`; Border: 2px solid `#1E1E1E`; Text: `#FAFAFA`. On focus, the border changes to `#3DDC84`.
- **Chips/Labels:** Small, sharp rectangles with `#1E1E1E` background and `JetBrains Mono` text.
- **Checkboxes/Radios:** Square icons only. When "Checked," the entire box fills with `#3DDC84` with a black checkmark.
- **Data Tables:** High-density, sharp rows with `1px` dividers in `#1E1E1E`. Use Terminal Green for key metrics or status indicators.
