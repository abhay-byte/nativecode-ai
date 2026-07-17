---
name: Midnight Aurora
colors:
  surface: '#15121b'
  surface-dim: '#15121b'
  surface-bright: '#3c3742'
  surface-container-lowest: '#100d16'
  surface-container-low: '#1d1a24'
  surface-container: '#221e28'
  surface-container-high: '#2c2833'
  surface-container-highest: '#37333e'
  on-surface: '#e8dfee'
  on-surface-variant: '#ccc3d8'
  inverse-surface: '#e8dfee'
  inverse-on-surface: '#332f39'
  outline: '#958da1'
  outline-variant: '#4a4455'
  surface-tint: '#d2bbff'
  primary: '#d2bbff'
  on-primary: '#3f008e'
  primary-container: '#7c3aed'
  on-primary-container: '#ede0ff'
  inverse-primary: '#732ee4'
  secondary: '#4cd7f6'
  on-secondary: '#003640'
  secondary-container: '#03b5d3'
  on-secondary-container: '#00424e'
  tertiary: '#ffb784'
  on-tertiary: '#4f2500'
  tertiary-container: '#a15100'
  on-tertiary-container: '#ffe0cd'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#eaddff'
  primary-fixed-dim: '#d2bbff'
  on-primary-fixed: '#25005a'
  on-primary-fixed-variant: '#5a00c6'
  secondary-fixed: '#acedff'
  secondary-fixed-dim: '#4cd7f6'
  on-secondary-fixed: '#001f26'
  on-secondary-fixed-variant: '#004e5c'
  tertiary-fixed: '#ffdcc6'
  tertiary-fixed-dim: '#ffb784'
  on-tertiary-fixed: '#301400'
  on-tertiary-fixed-variant: '#713700'
  background: '#15121b'
  on-background: '#e8dfee'
  surface-variant: '#37333e'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  code-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px
---

## Brand & Style

The design system is engineered for developers who demand high-performance, focus-intensive environments. It targets technical power users who appreciate the aesthetics of modern IDEs and sophisticated productivity tools. 

The design style is a hybrid of **Modern Corporate** and **Glassmorphism**, characterized by high-precision layouts, subtle depth, and intentional accents of vibrant color. The UI evokes a sense of "The Deep Void"—a quiet, distraction-free space where high-contrast accents guide the user's attention to critical actions and data. It balances the rigidity of professional software with the fluid, atmospheric feel of futuristic interfaces.

## Colors

The palette is anchored in **Deep Void (#0B0E14)**, providing a high-contrast foundation for text readability. 

- **Primary Accent:** Electric Violet is used for primary actions, progress indicators, and active states.
- **Secondary Accent:** Neon Cyan is reserved for data visualization, secondary highlights, and "technical" UI elements like terminal prompts.
- **Surface Hierarchy:** Depth is created through value shifts rather than heavy shadows. Surface 1 serves as the default container background, while Surface 2 is utilized for interactive hover states and active selections.
- **Semantic Colors:** Success, Danger, and Warning use vibrant, high-saturation tones to remain legible against the dark background.

## Typography

This design system employs a dual-font strategy. **Inter** handles all UI labels, navigation, and body copy, chosen for its exceptional legibility and modern, neutral profile. **JetBrains Mono** is utilized for any data-driven content, code blocks, terminal outputs, and small labels to reinforce the developer-centric narrative.

For large headlines, negative letter spacing is applied to maintain a tight, "engineered" look. Micro-labels use all-caps monospaced type to distinguish metadata from content.

## Layout & Spacing

The layout follows a **Fluid Grid** model with a base unit of 4px. All spacing increments are multiples of 4 (4, 8, 16, 24, 32, 48). 

- **Desktop:** 12-column grid with 16px gutters and 32px side margins. Panels (like sidebars and inspectors) should use fixed widths (240px - 320px) while the main editor/content area remains fluid.
- **Mobile:** 4-column grid with 16px margins.
- **Alignment:** Consistent internal padding of 16px (md) is used for containers and cards to ensure a breathable but compact density, typical of professional tools.

## Elevation & Depth

Elevation is primarily communicated through **Tonal Layering** and **Subtle Outlines**. 

- **Level 0 (Background):** #0B0E14 (Base)
- **Level 1 (Cards/Panels):** #151823 with a 1px solid border of #2D3344. No shadows are used at this level to maintain a flat, IDE-like precision.
- **Level 2 (Dropdowns/Modals):** #1E2230 with a subtle, 15% opacity primary-colored outer glow (0px 8px 24px) to simulate "lifting" from the surface.
- **Glassmorphism:** Bottom navigation bars and floating toolbars use a background blur (12px) with a semi-transparent version of Surface 1 (80% opacity) and a top-edge highlight border (1px, #FFFFFF, 10% opacity).

## Shapes

The shape language combines geometric precision with comfortable curves. 

- **Standard Containers:** Use 16px (rounded-lg) for cards and main UI panels to soften the "Deep Void" aesthetic.
- **Interactive Elements:** Buttons use a **Pill-shaped (3)** radius (24px+) to create high-contrast against the rectangular grid.
- **Small Elements:** Tooltips and tags use 4px (rounded-sm) to maintain a technical, sharp appearance.

## Components

- **Buttons:** Primary buttons are pill-shaped with an Electric Violet background and white text. Secondary buttons use a ghost style (Surface 2 background with 1px border). Hover states should increase the brightness of the accent color.
- **Input Fields:** Use Surface 1 with a bottom-only 2px border that illuminates in Neon Cyan upon focus. Use JetBrains Mono for input text.
- **Chips/Tags:** Monospaced text inside 4px rounded containers. Use low-opacity versions of semantic colors (e.g., 10% Cyan background with 100% Cyan text).
- **Cards:** Defined by #151823 background and #2D3344 borders. Cards do not use shadows unless they are being dragged or are "Active."
- **Lists:** List items use Surface 2 for hover states. Active items are marked by a 3px vertical Electric Violet "indicator bar" on the left edge.
- **Bottom Navigation:** Implement a floating glassmorphism bar with `backdrop-filter: blur(12px)`. Icons should be line-art style with Neon Cyan for the active state.
- **Code Blocks:** Darker than the base background (#050505) with 8px rounded corners and an optional language tag in the top-right corner using Label-Caps typography.