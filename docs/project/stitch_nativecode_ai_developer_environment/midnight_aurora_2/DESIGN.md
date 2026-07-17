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
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
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
    fontWeight: '450'
    lineHeight: 20px
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '450'
    lineHeight: 18px
  label-sm:
    fontFamily: Inter
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
  gutter: 16px
  margin-page: 24px
  container-padding: 12px
---

## Brand & Style
The brand personality is high-fidelity, technical, and atmospheric. It is designed for developers and power users who appreciate a focused, "dark-room" environment that minimizes eye strain while maintaining a sense of high energy through vibrant accents. 

The design system employs a **Modern Glassmorphic** style. It relies on deep, dark surfaces with subtle translucent overlays and vibrant background blurs to create a sense of three-dimensional space within a digital terminal environment. The aesthetic is "Cyber-Professional"—merging the precision of a developer tool with the polished finish of a premium SaaS product.

## Colors
The palette is rooted in a deep charcoal-purple base, providing a high-contrast foundation for electric neon accents.

- **Primary (Electric Violet):** Used for primary actions, active navigation states, and critical focus indicators.
- **Secondary (Cyan/Teal):** Used for success states, terminal prompts, and data-driven accents to provide visual variety.
- **Surface:** The deepest layer, used for main application backgrounds.
- **Surface Container:** A slightly elevated tier for cards, sidebars, and grouped content.
- **Glass/Stroke:** All borders should use a low-opacity white (10-15%) to define edges without breaking the dark immersion.

## Typography
The typography system balances readability with technical utility. 

- **UI Text:** Use **Inter** for all interface elements, headings, and descriptions. Its neutral, systematic nature ensures clarity against the dark background.
- **Technical Text:** Use **JetBrains Mono** for terminal output, code blocks, and data-heavy labels. This distinguishes user-generated or system-generated data from the UI chrome.
- **Contrast:** Maintain high contrast for code tokens (using the Primary and Secondary colors for syntax highlighting) while keeping UI labels slightly muted using medium-grey text values.

## Layout & Spacing
The layout follows a **Fluid Grid** model designed for high-density information. 

- **Spacing Rhythm:** Based on a 4px baseline. Most component padding should utilize 12px or 16px increments.
- **Terminal Layout:** The terminal and file viewer areas should maximize vertical space, using "Compact" density to fit more lines of code/logs.
- **Breakpoints:**
  - **Mobile (<768px):** Single column, collapsible sidebars, 16px margins.
  - **Desktop (>1280px):** Multi-pane layout (Sidebar | File Tree | Editor | Terminal) with fixed sidebar widths and fluid center panels.

## Elevation & Depth
Depth is expressed through **Glassmorphism** and subtle tonal shifts rather than heavy shadows.

- **Layer 0 (Base):** Deepest charcoal (#15121b).
- **Layer 1 (Panels):** Surface Container (#1d1a24) with a 1px border of `rgba(255,255,255,0.08)`.
- **Layer 2 (Overlays/Popovers):** Surface Container with a backdrop blur (12px to 20px) and a slightly brighter border.
- **Shadows:** Use a single, very soft, large-radius shadow for floating modals (0px 20px 40px rgba(0,0,0,0.4)). Do not use shadows on static layout panels.

## Shapes
The shape language is controlled and precise.

- **Containers & Cards:** Use **8px (rounded-lg)** for all major UI panels, cards, and input fields.
- **Interactive Elements:** Buttons and interactive pills use a **full-round (pill)** radius to distinguish them from structural containers.
- **Terminal:** The terminal interior maintains sharp corners or very small (2px) radii for a classic technical feel.

## Components
- **Buttons:** 
  - *Primary:* Electric Violet background, white text, pill-shaped.
  - *Ghost:* No background, 1px subtle white border, violet text on hover.
- **Input Fields:** Darker than the container background, 8px radius, primary color 2px border on focus.
- **Terminal Rows:** JetBrains Mono text. Success states in Cyan, error states in a soft Red, and prompts in Violet.
- **Cards:** Surface Container background (#1d1a24), 8px radius, 1px border.
- **Chips/Badges:** Small, uppercase JetBrains Mono text. Subdued background colors with high-saturation text for status indicators.
- **Scrollbars:** Minimalist, thin, dark grey with a violet tint, visible only on hover.