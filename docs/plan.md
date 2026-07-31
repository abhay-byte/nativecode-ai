# Store Listing Images Plan

## Target Audience
Vibe coders, mobile developers, agentic developers, project creators, entrepreneurs, hustlers.

## Design Strategy
Based on high-conversion rate best practices:
- **Visuals:** Cyber-brutalist aesthetic (deep obsidian #131313 surface, terminal green #3DDC84 accents).
- **Messaging:** Focus on outcomes ("Code anywhere," "Agentic AI in your pocket").
- **Layout:** High contrast, large readable text, simple layout, background effects, textures, no overlap.

## Image Sequence
1. **The Hook:** Native code logo at center, tagline ("Your Pocket IDE"), complete name. Blurred screenshots behind.
2. **AI Power:** "AI Tools Run on Device" - Showcasing opencode, agy, codex, claude code.
3. **Project Management:** "Manage Complete Projects" - Show workspace, directory tree, git diff, settings.
4. **Shell Integration:** "Debian Shell + AI" - Terminal window running AI tools natively.
5. **Autonomy:** "Agentic Developer" - Show autonomous coding / task completion.
6. **Creation:** "Project Creator for Entrepreneurs" - Show scaffolding a new app.
7. **Accessibility:** "Vibe Code on Phone" - Easy to use, pocket coding experience.
8. **Outcome:** "Ship Faster. Build Anywhere." - Final call to action / social proof.

## Implementation (Python)
- A Python script (`generate_storelisting.py`) using `Pillow` (PIL) will generate these 8 portrait (1080x1920) images.
- Images will be saved to `/docs/images/`.
- Script will apply background colors, text layouts, and logic for screenshots.
