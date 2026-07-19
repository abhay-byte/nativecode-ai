# UI Changes Plan & Implementation

## 1. Project Settings Page (Configure Screen)
- **Problem:** App settings page was not full height, layout was cluttered, and the input box was confusing.
- **Change:** 
  - Removed the `configIconInput` input field from the view hierarchy.
  - Removed the text label: "Project Icon (Link, Emoji, or File)".
  - Expanded the Icon Preview container size to 120dp x 120dp.
  - Made the Icon Preview circular using `roundedBg` with `clipToOutline = true` and `ViewOutlineProvider.BACKGROUND`.
  - Centered the "Browse" secondary button directly below the circular preview.
  - Aligned "Save Configuration" and "Remove Project" to span full-width.
  - Updated the image picker handling: the file selection still updates the in-memory `configIconInput`, ensuring that "Save Configuration" correctly captures the chosen icon path.

## 2. Project Bottom Navigation (Git Diff Tab)
- **Problem:** The text "Git Diff" was too long, and a custom material-style git-diff icon was requested.
- **Change:**
  - Renamed bottom navigation tab title from `"Git Diff"` to `"Diff"`.
  - Replaced the icon path in `app/src/main/res/drawable/ic_git.xml` with a material git-diff SVG from [svgrepo.com/download/508074/git-diff.svg](https://www.svgrepo.com/download/508074/git-diff.svg).
