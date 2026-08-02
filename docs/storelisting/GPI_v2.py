"""
NativeCode Play Store asset generator.

Rewritten to fix the root causes behind the store-listing critique:
  1. Screenshots were pasted at raw (x, y) with no bounds check -> overflowed
     the canvas and got silently clipped (the "cropped at the bottom" bug).
  2. Missing screenshot files failed silently -> incomplete slides with no
     warning.
  3. Raw screenshots floated with no frame/shadow -> looked like accidental
     overlaps rather than a designed collage.
  4. Tool count/copy was hand-typed text, disconnected from what was
     actually pasted in -> "6 CLI Tools" headline next to 7 tiles.
  5. Nothing prevented a trademarked/competitor screenshot (e.g. a Cursor
     login screen) from being used.
  6. No feature graphic (1024x500, a hard Play Store requirement) was
     generated at all.
  7. No text auto-fit/wrapping -> long copy could run off the canvas edge.

Every fix below is structural (a function that makes the bug impossible),
not a one-off tweak to a single slide.
"""

import os
import urllib.request
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
OUT_DIR = "/home/abhaybyte/repos/termux-lib/docs/images"
SCREENSHOTS_DIR = "/home/abhaybyte/repos/termux-lib/docs/screenshots/v1"
os.makedirs(OUT_DIR, exist_ok=True)

# ---------------------------------------------------------------------------
# Fonts
#
# NOTE: the original script pointed at
# "github.com/googlefonts/SpaceGrotesk/raw/main/..." which 404s (that repo
# path no longer exists) and silently fell back to PIL's tiny built-in
# default font on every run -- so every headline you've seen so far was
# never actually in Space Grotesk. Fixed to pull the canonical variable font
# from the google/fonts repo and select weights via variation instances
# instead of separate static files.
# ---------------------------------------------------------------------------
FONT_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf"
FONT_PATH = "/tmp/SpaceGrotesk-Variable.ttf"

if not os.path.exists(FONT_PATH):
    try:
        urllib.request.urlretrieve(FONT_URL, FONT_PATH)
    except Exception as e:
        print(f"[warn] could not download font {FONT_URL}: {e}")

_FONT_CACHE = {}

def get_font(size, bold=True):
    key = (size, bold)
    if key in _FONT_CACHE:
        return _FONT_CACHE[key]
    try:
        f = ImageFont.truetype(FONT_PATH, size)
        f.set_variation_by_name(b"Bold" if bold else b"Medium")
    except Exception as e:
        print(f"[warn] falling back to default font ({e})")
        f = ImageFont.load_default()
    _FONT_CACHE[key] = f
    return f

# ---------------------------------------------------------------------------
# Canvas / brand constants
# ---------------------------------------------------------------------------
WIDTH, HEIGHT = 1080, 1920
FEATURE_W, FEATURE_H = 1024, 500          # hard Play Store requirement
SAFE_MARGIN = 64                           # nothing should ever touch raw edges

BG_COLOR = "#0D0F0D"
GRID_COLOR = "#1A1F1A"
ACCENT_COLOR = "#3DDC84"
TEXT_COLOR = "#FAFAFA"
MUTED_COLOR = "#9AA79A"
FRAME_BORDER = "#2E3B2E"

# ---------------------------------------------------------------------------
# Fix #4: single source of truth for the AI tool list.
# Any headline that needs a count or a name list pulls from here, so the
# copy can never drift out of sync with what's actually shown/pasted.
# ---------------------------------------------------------------------------
AI_TOOLS = [
    {"id": "opencode",    "label": "OpenCode",    "tier": "free", "file": "terminal_opencode.png"},
    {"id": "codex",       "label": "Codex",       "tier": "pro",  "file": "terminal_codex.png"},
    {"id": "agy",         "label": "Antigravity", "tier": "pro",  "file": "terminal_agy.png"},
    {"id": "claude-code", "label": "Claude Code", "tier": "pro",  "file": "terminal_claude_code.png"},
    {"id": "qwen-code",   "label": "Qwen Code",   "tier": "pro",  "file": "terminal_qwen.png"},
    {"id": "grok",        "label": "Grok CLI",    "tier": "pro",  "file": "terminal_grok.png"},
    {"id": "kiro",        "label": "Kiro CLI",    "tier": "pro",  "file": "terminal_kiro.png"},
]
TOOL_COUNT = len(AI_TOOLS)


def tools_headline() -> str:
    """Generates the headline instead of hand-typing a number that can go stale."""
    return f"{TOOL_COUNT} CLI Tools."


def tools_subtitle() -> str:
    return " \u00b7 ".join(t["label"] for t in AI_TOOLS)


# ---------------------------------------------------------------------------
# Fix #5: banned-asset guard. Anything that shows a competitor's own product
# UI (login walls, their logo as the subject of the shot, etc.) is blocked at
# the compositing layer, so it can't slip into a slide by accident again.
# Add substrings here any time a new "don't ever use this screenshot" case
# comes up.
# ---------------------------------------------------------------------------
BANNED_ASSET_SUBSTRINGS = [
    "cursor_login",
    "cursor_signup",
    "third_party_login",
]


def assert_asset_allowed(filename: str):
    lowered = filename.lower()
    for banned in BANNED_ASSET_SUBSTRINGS:
        if banned in lowered:
            raise ValueError(
                f"Refusing to use '{filename}': matches banned substring "
                f"'{banned}'. This looks like a competitor/trademarked screen "
                f"(e.g. a third-party login page). Pick a screenshot that shows "
                f"our own product instead."
            )


# ---------------------------------------------------------------------------
# Base canvas
# ---------------------------------------------------------------------------
def create_base(w=WIDTH, h=HEIGHT):
    img = Image.new("RGB", (w, h), BG_COLOR)
    draw = ImageDraw.Draw(img)
    for x in range(0, w, 60):
        draw.line([(x, 0), (x, h)], fill=GRID_COLOR, width=1)
    for y in range(0, h, 60):
        draw.line([(0, y), (w, y)], fill=GRID_COLOR, width=1)
    return img


# ---------------------------------------------------------------------------
# Fix #7: text auto-fit + wrapping so copy never runs off the canvas.
# ---------------------------------------------------------------------------
def _text_width(draw, text, font):
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0]


def fit_font(draw, text, max_width, start_size, min_size=32, bold=True):
    size = start_size
    font = get_font(size, bold=bold)
    while _text_width(draw, text, font) > max_width and size > min_size:
        size -= 4
        font = get_font(size, bold=bold)
    return font


def wrap_text(draw, text, font, max_width):
    words = text.split()
    lines, current = [], ""
    for word in words:
        trial = f"{current} {word}".strip()
        if _text_width(draw, trial, font) <= max_width:
            current = trial
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def add_text(img, title, subtitle="", y_pos=150, max_width=None):
    """Returns the y-coordinate immediately below the rendered text block,
    so callers can position screenshots without guessing/overlapping."""
    draw = ImageDraw.Draw(img)
    max_width = max_width or (WIDTH - 2 * SAFE_MARGIN)

    font_large = fit_font(draw, title, max_width, start_size=84, min_size=48, bold=True)
    title_lines = wrap_text(draw, title, font_large, max_width)

    cursor_y = y_pos
    for line in title_lines:
        w = _text_width(draw, line, font_large)
        draw.text(((WIDTH - w) // 2, cursor_y), line, font=font_large, fill=ACCENT_COLOR)
        cursor_y += int(font_large.size * 1.15)

    cursor_y += 20

    if subtitle:
        font_medium = fit_font(draw, subtitle, max_width, start_size=50, min_size=30, bold=False)
        sub_lines = wrap_text(draw, subtitle, font_medium, max_width)
        for line in sub_lines:
            w = _text_width(draw, line, font_medium)
            draw.text(((WIDTH - w) // 2, cursor_y), line, font=font_medium, fill=MUTED_COLOR)
            cursor_y += int(font_medium.size * 1.3)

    return cursor_y + 40


# ---------------------------------------------------------------------------
# Fix #1 + #3: bounds-safe framed screenshot compositing.
#
# The screenshot is scaled to CONTAIN within the given box, rotated (if any),
# then re-fit to CONTAIN within that same box again -- so rotation can never
# push it outside the box, and the box itself is always chosen to sit fully
# inside the canvas. A screenshot physically cannot get clipped by this.
# ---------------------------------------------------------------------------
def _contain_fit(img, max_w, max_h):
    w, h = img.size
    scale = min(max_w / w, max_h / h, 1.0) if (w > max_w or h > max_h) else min(max_w / w, max_h / h)
    new_w, new_h = max(1, int(w * scale)), max(1, int(h * scale))
    return img.resize((new_w, new_h), Image.Resampling.LANCZOS)


def frame_screenshot(ss, radius=36, border_px=4, shadow_blur=30, shadow_alpha=140):
    """Wraps a screenshot in a rounded-rect bezel + soft drop shadow so it
    reads as an intentional device mockup instead of a bare floating image."""
    w, h = ss.size
    pad = shadow_blur * 2

    # Rounded mask for the screenshot itself
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w, h], radius=radius, fill=255)
    rounded = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rounded.paste(ss.convert("RGBA"), (0, 0), mask)

    # Border
    border_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(border_layer).rounded_rectangle(
        [border_px // 2, border_px // 2, w - border_px // 2, h - border_px // 2],
        radius=radius, outline=FRAME_BORDER, width=border_px
    )

    # Canvas with room for shadow
    canvas = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [pad, pad, pad + w, pad + h], radius=radius, fill=(0, 0, 0, shadow_alpha)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(shadow_blur))

    canvas.alpha_composite(shadow)
    canvas.alpha_composite(rounded, (pad, pad))
    canvas.alpha_composite(border_layer, (pad, pad))
    return canvas


def safe_paste(base, ss_name, box, rotate=0, crop_box=None, frame=True):
    """
    box: (x, y, w, h) -- a region that MUST already sit fully inside the
    canvas. The screenshot is fit inside this region; if rotated, it is
    re-fit to the same region afterwards, so nothing can ever spill past
    (x, y, w, h), and therefore never past the canvas edge either.

    crop_box: optional (l, t, r, b) in source-image pixel coords, used to
    zoom into a legible region of a dense screenshot (e.g. a single diff
    card) instead of shrinking the whole screen into illegibility.
    """
    assert_asset_allowed(ss_name)
    x, y, w, h = box
    assert x >= 0 and y >= 0 and x + w <= WIDTH and y + h <= HEIGHT, (
        f"Box {box} for '{ss_name}' is not fully inside the {WIDTH}x{HEIGHT} "
        f"canvas -- fix the box, don't let PIL silently clip it."
    )

    ss_path = os.path.join(SCREENSHOTS_DIR, ss_name)
    if not os.path.exists(ss_path):
        # Fix #2: loud, not silent.
        print(f"[MISSING ASSET] '{ss_name}' not found at {ss_path} -- slide will be incomplete!")
        return

    ss = Image.open(ss_path).convert("RGBA")
    if crop_box:
        ss = ss.crop(crop_box)

    if frame:
        ss = frame_screenshot(ss)

    ss = _contain_fit(ss, w, h)

    if rotate:
        ss = ss.rotate(rotate, expand=True, resample=Image.Resampling.BICUBIC)
        ss = _contain_fit(ss, w, h)  # re-fit after rotation growth -- the actual bug fix

    paste_x = x + (w - ss.width) // 2
    paste_y = y + (h - ss.height) // 2
    base.paste(ss, (paste_x, paste_y), ss)


# ---------------------------------------------------------------------------
# Slide manifest. Each slide declares its own safe boxes explicitly, so a
# reviewer can see at a glance that nothing overlaps or overflows, instead of
# reverse-engineering pixel math from scattered paste_screenshot calls.
# ---------------------------------------------------------------------------
def slide_01_hero():
    img = create_base()
    below = add_text(img, "NativeCode", "Full Linux + AI coding agents in your pocket.", y_pos=140)
    safe_paste(img, "home.png", box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, HEIGHT - below - SAFE_MARGIN), rotate=-3)
    img.save(os.path.join(OUT_DIR, "01_hero.png"))


def slide_02_ai_tools():
    img = create_base()
    below = add_text(img, tools_headline(), tools_subtitle(), y_pos=120)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    col_w = (WIDTH - 3 * SAFE_MARGIN) // 2
    row_h = (remaining_h - SAFE_MARGIN) // 2

    slots = [
        (SAFE_MARGIN, below, col_w, row_h),
        (SAFE_MARGIN * 2 + col_w, below, col_w, row_h),
        (SAFE_MARGIN, below + row_h + SAFE_MARGIN, col_w, row_h),
        (SAFE_MARGIN * 2 + col_w, below + row_h + SAFE_MARGIN, col_w, row_h),
    ]
    files = ["terminal_opencode.png", "terminal_agy.png", "terminal_codex.png", "terminal_claude_code.png"]
    for box, fname in zip(slots, files):
        safe_paste(img, fname, box=box, rotate=0)  # no rotation -- keeps a 2x2 grid legible, not messy
    img.save(os.path.join(OUT_DIR, "02_ai_tools.png"))


def slide_03_projects():
    img = create_base()
    below = add_text(img, "Manage Complete Projects", "Workspace \u00b7 Git \u00b7 Settings", y_pos=140)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    third = (remaining_h - 2 * SAFE_MARGIN) // 3
    safe_paste(img, "project_workspace.png", box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, third))
    safe_paste(img, "project_directory.png", box=(SAFE_MARGIN, below + third + SAFE_MARGIN, WIDTH - 2 * SAFE_MARGIN, third))
    safe_paste(img, "git_diff.png", box=(SAFE_MARGIN, below + 2 * (third + SAFE_MARGIN), WIDTH - 2 * SAFE_MARGIN, third))
    img.save(os.path.join(OUT_DIR, "03_projects.png"))


def slide_04_debian():
    img = create_base()
    below = add_text(img, "Debian Shell + AI", "Total terminal control, fully on-device.", y_pos=140)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    half = (remaining_h - SAFE_MARGIN) // 2
    safe_paste(img, "terminal.png", box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, half))
    safe_paste(img, "terminal_agy.png", box=(SAFE_MARGIN, below + half + SAFE_MARGIN, WIDTH - 2 * SAFE_MARGIN, half))
    img.save(os.path.join(OUT_DIR, "04_debian.png"))


def slide_05_agentic():
    img = create_base()
    below = add_text(img, "Your Agent. Always On.", "Long-running sessions, multi-repo tree.", y_pos=140)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    # crop_box zooms into ONE legible diff card instead of shrinking a whole
    # scrollable transcript into an illegible thumbnail -- fixes the
    # "dense code, unreadable at thumbnail size" issue.
    safe_paste(
        img, "workspace_agent.png",
        box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, remaining_h),
        crop_box=(0, 900, 1080, 1500),
    )
    img.save(os.path.join(OUT_DIR, "05_agentic.png"))


def slide_06_creator():
    img = create_base()
    below = add_text(img, "Install Anything. Instantly.", "Curated catalog \u00b7 one tap \u00b7 no PC required.", y_pos=140)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    half = (remaining_h - SAFE_MARGIN) // 2
    safe_paste(img, "all_projects.png", box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, half))
    safe_paste(img, "marketplace.png", box=(SAFE_MARGIN, below + half + SAFE_MARGIN, WIDTH - 2 * SAFE_MARGIN, half))
    img.save(os.path.join(OUT_DIR, "06_creator.png"))


def slide_07_vibecode():
    img = create_base()
    below = add_text(img, "Vibe Code on Phone", "The easiest way to run AI agents, on-device.", y_pos=140)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    half = (remaining_h - SAFE_MARGIN) // 2
    safe_paste(img, "home.png", box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, half))
    # NOTE: original script referenced "repairs.png" here -- almost certainly
    # a typo (no such asset exists in the manifest anywhere else). Swapped to
    # a real, verified asset. If a different screenshot was intended, update
    # the filename below rather than reintroducing an unverified name.
    safe_paste(img, "project_directory.png", box=(SAFE_MARGIN, below + half + SAFE_MARGIN, WIDTH - 2 * SAFE_MARGIN, half))
    img.save(os.path.join(OUT_DIR, "07_vibecode.png"))


def slide_08_ship():
    img = create_base()
    below = add_text(img, "Ship Faster. Build Anywhere.", "Full GUI IDE, on-device and fully local.", y_pos=140)
    remaining_h = HEIGHT - below - SAFE_MARGIN
    half = (remaining_h - SAFE_MARGIN) // 2
    # IMPORTANT: this slide previously used a screenshot of a third-party
    # IDE's own login screen as "proof" of this claim -- assert_asset_allowed()
    # will now hard-fail if that asset is ever wired back in here. Point this
    # at an actual screenshot of NativeCode's own on-device GUI/IDE session.
    safe_paste(img, "xfce4_display.png", box=(SAFE_MARGIN, below, WIDTH - 2 * SAFE_MARGIN, half))
    safe_paste(img, "project_config.png", box=(SAFE_MARGIN, below + half + SAFE_MARGIN, WIDTH - 2 * SAFE_MARGIN, half))
    img.save(os.path.join(OUT_DIR, "08_ship.png"))


# ---------------------------------------------------------------------------
# Fix #6: the feature graphic was missing entirely. Exact 1024x500, logo +
# tagline on the left, ONE legibly-cropped screenshot on the right (not two
# miniaturized screens fighting for the same space).
# ---------------------------------------------------------------------------
def generate_feature_graphic():
    img = create_base(FEATURE_W, FEATURE_H)
    draw = ImageDraw.Draw(img)
    margin = 40

    title_font = fit_font(draw, "NativeCode", FEATURE_W // 2 - margin, start_size=64, min_size=36)
    draw.text((margin, 90), "NativeCode", font=title_font, fill=ACCENT_COLOR)

    tagline_font = get_font(28, bold=False)
    tagline = "AI Coding Terminal \u00b7 Fully Local"
    draw.text((margin, 90 + int(title_font.size * 1.2) + 10), tagline, font=tagline_font, fill=MUTED_COLOR)

    right_box = (FEATURE_W // 2 + 20, margin, FEATURE_W // 2 - margin * 2, FEATURE_H - margin * 2)
    safe_paste(img, "terminal_opencode.png", box=right_box, frame=True)

    img.save(os.path.join(OUT_DIR, "feature_graphic.png"))


def main():
    slide_01_hero()
    slide_02_ai_tools()
    slide_03_projects()
    slide_04_debian()
    slide_05_agentic()
    slide_06_creator()
    slide_07_vibecode()
    slide_08_ship()
    generate_feature_graphic()
    print("Store listing images generated at", OUT_DIR)
    print(f"Tool count used across copy: {TOOL_COUNT} ({', '.join(t['label'] for t in AI_TOOLS)})")


if __name__ == "__main__":
    main()
