#!/usr/bin/env python3
"""NativeCode Play Store listing frames — pure Python/Pillow.

Portrait 1080x1920. Cyber-brutalist (docs/project/ui_design.md).
Text zone top, phone zone bottom — no overlap.
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ── paths ──────────────────────────────────────────────────────────
ROOT = Path(__file__).resolve().parents[4]  # termux-lib
SHOTS = ROOT / "docs" / "screenshots" / "v1"
LOGO = ROOT / "app" / "src" / "main" / "res" / "drawable" / "logo_highres.webp"
OUT = Path(__file__).resolve().parent
FONT_DIR = Path("/tmp/storelisting-fonts")
W, H = 1080, 1920

# ── brand ──────────────────────────────────────────────────────────
BG = (0x13, 0x13, 0x13, 255)
SURFACE = (0x12, 0x12, 0x12, 255)
SURFACE_LOW = (0x1C, 0x1B, 0x1B, 255)
SURFACE_BRIGHT = (0x39, 0x39, 0x39, 255)
OUTLINE_VAR = (0x3C, 0x4A, 0x3F, 255)
PRIMARY = (0x3D, 0xDC, 0x84, 255)
PRIMARY_SOFT = (0x60, 0xF9, 0x9E, 255)
TEXT = (0xFA, 0xFA, 0xFA, 255)
TEXT_DIM = (0xBB, 0xCB, 0xBC, 255)
ON_PRIMARY = (0x0A, 0x0A, 0x0A, 255)
GRID = (0x3C, 0x4A, 0x3F, 40)
NOISE_SEED = 42


def font(path_candidates, size: int) -> ImageFont.FreeTypeFont:
    for p in path_candidates:
        if p and Path(p).exists():
            try:
                return ImageFont.truetype(str(p), size)
            except OSError:
                continue
    return ImageFont.load_default()


def f_head(size: int) -> ImageFont.FreeTypeFont:
    return font(
        [
            FONT_DIR / "SpaceGrotesk.ttf",
            "/usr/share/fonts/TTF/FiraSans-Bold.ttf",
            "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
        ],
        size,
    )


def f_body(size: int) -> ImageFont.FreeTypeFont:
    return font(
        [
            "/usr/share/fonts/TTF/FiraSans-Book.ttf",
            "/usr/share/fonts/TTF/FiraSans-Bold.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
        ],
        size,
    )


def f_mono(size: int) -> ImageFont.FreeTypeFont:
    return font(
        [
            "/usr/share/fonts/TTF/JetBrainsMono-Bold.ttf",
            "/usr/share/fonts/TTF/JetBrainsMono-Medium.ttf",
            "/usr/share/fonts/TTF/DejaVuSansMono-Bold.ttf",
        ],
        size,
    )


def load_shot(name: str) -> Image.Image:
    p = SHOTS / name
    im = Image.open(p).convert("RGBA")
    return im


def load_logo(size: int = 280) -> Image.Image:
    im = Image.open(LOGO).convert("RGBA")
    im.thumbnail((size, size), Image.Resampling.LANCZOS)
    return im


# ── textures / effects ─────────────────────────────────────────────

def noise_layer(w: int, h: int, alpha: int = 18, seed: int = NOISE_SEED) -> Image.Image:
    rng = np.random.default_rng(seed)
    # sparse noise at half res, upscale
    hw, hh = w // 2, h // 2
    v = rng.integers(0, 256, size=(hh, hw), dtype=np.uint8)
    a = rng.integers(0, alpha + 1, size=(hh, hw), dtype=np.uint8)
    arr = np.zeros((hh, hw, 4), dtype=np.uint8)
    arr[..., 0] = v
    arr[..., 1] = v
    arr[..., 2] = v
    arr[..., 3] = a
    n = Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)
    return n.filter(ImageFilter.GaussianBlur(0.8))


def grid_layer(w: int, h: int, step: int = 32) -> Image.Image:
    g = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    c = (OUTLINE_VAR[0], OUTLINE_VAR[1], OUTLINE_VAR[2], 28)
    for x in range(0, w, step):
        d.line([(x, 0), (x, h)], fill=c, width=1)
    for y in range(0, h, step):
        d.line([(0, y), (w, y)], fill=c, width=1)
    c2 = (PRIMARY[0], PRIMARY[1], PRIMARY[2], 18)
    for x in range(0, w, step * 4):
        d.line([(x, 0), (x, h)], fill=c2, width=1)
    for y in range(0, h, step * 4):
        d.line([(0, y), (w, y)], fill=c2, width=1)
    return g


def radial_glow(
    w: int, h: int, cx: float, cy: float, radius: float, color: tuple, peak_a: int = 70
) -> Image.Image:
    # low-res glow, upscale for speed
    scale = 4
    sw, sh = w // scale, h // scale
    ys = np.arange(sh, dtype=np.float32)
    xs = np.arange(sw, dtype=np.float32)
    xx, yy = np.meshgrid(xs, ys)
    dx = (xx - cx / scale) / (radius / scale)
    dy = (yy - cy / scale) / (radius / scale)
    dist = np.sqrt(dx * dx + dy * dy)
    mask = np.clip(1.0 - dist, 0, 1) ** 2
    a = (mask * peak_a).astype(np.uint8)
    r, g, b = color[:3]
    arr = np.zeros((sh, sw, 4), dtype=np.uint8)
    arr[..., 0] = r
    arr[..., 1] = g
    arr[..., 2] = b
    arr[..., 3] = a
    return Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)


def scanlines(w: int, h: int, gap: int = 4, a: int = 12) -> Image.Image:
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for y in range(0, h, gap):
        d.line([(0, y), (w, y)], fill=(0, 0, 0, a), width=1)
    return layer


def corner_brackets(draw: ImageDraw.ImageDraw, box, color=PRIMARY, arm=28, thick=3):
    x0, y0, x1, y1 = box
    # TL
    draw.line([(x0, y0 + arm), (x0, y0), (x0 + arm, y0)], fill=color, width=thick)
    # TR
    draw.line([(x1 - arm, y0), (x1, y0), (x1, y0 + arm)], fill=color, width=thick)
    # BL
    draw.line([(x0, y1 - arm), (x0, y1), (x0 + arm, y1)], fill=color, width=thick)
    # BR
    draw.line([(x1 - arm, y1), (x1, y1), (x1, y1 - arm)], fill=color, width=thick)


def hard_shadow(canvas: Image.Image, rect, offset: int = 10):
    """Two-tone L-shape shadow under a rectangle (x0,y0,x1,y1)."""
    x0, y0, x1, y1 = rect
    d = ImageDraw.Draw(canvas)
    # bottom face
    d.rectangle([x0 + offset, y1, x1 + offset, y1 + offset], fill=SURFACE_BRIGHT)
    # right face
    d.rectangle([x1, y0 + offset, x1 + offset, y1 + offset], fill=OUTLINE_VAR)


def make_base(variant: int = 0) -> Image.Image:
    im = Image.new("RGBA", (W, H), BG)
    # subtle vertical gradient
    a_col = (np.linspace(0, 40, H, dtype=np.float32)).astype(np.uint8)
    arr = np.zeros((H, W, 4), dtype=np.uint8)
    arr[..., 3] = a_col[:, None]
    im = Image.alpha_composite(im, Image.fromarray(arr, "RGBA"))

    # glows (different per variant)
    positions = [
        (W * 0.5, H * 0.22, 520, 55),
        (W * 0.2, H * 0.75, 400, 35),
        (W * 0.85, H * 0.55, 380, 30),
    ]
    for i, (cx, cy, rad, peak) in enumerate(positions):
        shift = (variant * 37 + i * 19) % 80
        im = Image.alpha_composite(
            im,
            radial_glow(W, H, cx + shift, cy - shift // 2, rad, PRIMARY, peak),
        )

    im = Image.alpha_composite(im, grid_layer(W, H, 36))
    im = Image.alpha_composite(im, noise_layer(W, H, 16, NOISE_SEED + variant))
    im = Image.alpha_composite(im, scanlines(W, H, 5, 10))

    # top green accent bar
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W, 6], fill=PRIMARY)
    # bottom accent
    d.rectangle([0, H - 6, W, H], fill=PRIMARY)
    return im


def text_size(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont):
    b = draw.textbbox((0, 0), text, font=font)
    return b[2] - b[0], b[3] - b[1]


def draw_centered_text(draw, y, text, font, fill=TEXT, max_w=W - 96):
    tw, th = text_size(draw, text, font)
    x = (W - tw) // 2
    draw.text((x, y), text, font=font, fill=fill)
    return th


def wrap_lines(draw, text, font, max_w):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        tw, _ = text_size(draw, trial, font)
        if tw <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def draw_header(
    im: Image.Image,
    headline: str,
    sub: str,
    chips: list[str] | None = None,
    top_pad: int = 48,
) -> int:
    """Draw header in reserved top zone. Returns y where content may start."""
    d = ImageDraw.Draw(im)
    y = top_pad + 16

    # mono kicker
    kick = f_mono(18)
    k = "NATIVECODE  ·  PLAY LISTING"
    tw, th = text_size(d, k, kick)
    d.text(((W - tw) // 2, y), k, font=kick, fill=PRIMARY_SOFT)
    y += th + 28

    # headline — up to 2 lines
    hf = f_head(56 if len(headline) > 22 else 64)
    lines = wrap_lines(d, headline, hf, W - 80)
    for line in lines[:2]:
        tw, th = text_size(d, line, hf)
        d.text(((W - tw) // 2, y), line, font=hf, fill=TEXT)
        y += th + 8
    y += 12

    # sub
    if sub:
        bf = f_body(26)
        for line in wrap_lines(d, sub, bf, W - 100)[:3]:
            tw, th = text_size(d, line, bf)
            d.text(((W - tw) // 2, y), line, font=bf, fill=TEXT_DIM)
            y += th + 6
        y += 16

    # chips
    if chips:
        mf = f_mono(16)
        chip_h = 36
        gap = 10
        # measure row
        widths = []
        for c in chips:
            tw, _ = text_size(d, c, mf)
            widths.append(tw + 28)
        total = sum(widths) + gap * (len(chips) - 1)
        x = (W - total) // 2
        for c, cw in zip(chips, widths):
            # hard chip
            d.rectangle([x, y, x + cw, y + chip_h], fill=SURFACE_LOW)
            d.rectangle([x, y, x + cw, y + chip_h], outline=PRIMARY, width=2)
            # inner highlight top
            d.line([(x + 1, y + 1), (x + cw - 2, y + 1)], fill=(255, 255, 255, 30))
            tw, th = text_size(d, c, mf)
            d.text(
                (x + (cw - tw) // 2, y + (chip_h - th) // 2 - 1),
                c,
                font=mf,
                fill=PRIMARY_SOFT,
            )
            x += cw + gap
        y += chip_h + 28
    else:
        y += 12

    # separator
    d.line([(64, y), (W - 64, y)], fill=(PRIMARY[0], PRIMARY[1], PRIMARY[2], 80), width=2)
    y += 28
    return y


def cover_crop(im: Image.Image, tw: int, th: int) -> Image.Image:
    iw, ih = im.size
    scale = max(tw / iw, th / ih)
    nw, nh = int(iw * scale), int(ih * scale)
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - tw) // 2
    top = max(0, (nh - th) // 8)  # bias slightly top for status bars
    return im.crop((left, top, left + tw, top + th))


def phone_frame(
    shot: Image.Image,
    phone_w: int,
    phone_h: int,
    bezel: int = 10,
    top_bar: int = 18,
) -> Image.Image:
    """Return phone image with bezel + screen content."""
    out = Image.new("RGBA", (phone_w, phone_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    # outer body
    d.rectangle([0, 0, phone_w - 1, phone_h - 1], fill=(0x0E, 0x0E, 0x0E, 255))
    d.rectangle([0, 0, phone_w - 1, phone_h - 1], outline=PRIMARY, width=3)
    # inner screen area
    sx0, sy0 = bezel, bezel + top_bar // 2
    sx1, sy1 = phone_w - bezel, phone_h - bezel
    sw, sh = sx1 - sx0, sy1 - sy0
    screen = cover_crop(shot, sw, sh)
    out.paste(screen, (sx0, sy0), screen if screen.mode == "RGBA" else None)
    # top speaker bar
    bar_w = phone_w // 4
    d.rectangle(
        [(phone_w - bar_w) // 2, 8, (phone_w + bar_w) // 2, 14],
        fill=SURFACE_BRIGHT,
    )
    # inner top highlight
    d.line([(2, 2), (phone_w - 3, 2)], fill=(255, 255, 255, 35), width=1)
    d.line([(2, 2), (2, phone_h - 3)], fill=(255, 255, 255, 25), width=1)
    return out


def paste_phone(canvas: Image.Image, phone: Image.Image, x: int, y: int, shadow: int = 10):
    pw, ph = phone.size
    hard_shadow(canvas, (x, y, x + pw, y + ph), offset=shadow)
    canvas.paste(phone, (x, y), phone)


def label_under(draw, x, y, w, text, font):
    tw, th = text_size(draw, text, font)
    draw.text((x + (w - tw) // 2, y), text, font=font, fill=PRIMARY_SOFT)


# ── layouts ────────────────────────────────────────────────────────

def layout_hero(im: Image.Image):
    y0 = draw_header(
        im,
        "VIBE CODE ON YOUR PHONE",
        "Portable Linux & AI Developer Environment",
        chips=["AGENTIC", "DEBIAN", "ON-DEVICE AI"],
    )
    # logo center
    logo = load_logo(300)
    lx = (W - logo.size[0]) // 2
    ly = y0 + 20
    # glow behind logo
    im.alpha_composite(
        radial_glow(W, H, W / 2, ly + logo.size[1] / 2, 280, PRIMARY, 90)
    )
    # name under logo zone reserved — place faded phones BEHIND logo area bottom
    # Background collage phones (behind, dimmed) in lower 55%
    shots = [
        load_shot("home.png"),
        load_shot("project_workspace.png"),
        load_shot("terminal_opencode.png"),
        load_shot("terminal.png"),
    ]
    phone_h = 720
    phone_w = int(phone_h * 9 / 19.5)
    gap = 16
    total_w = phone_w * 4 + gap * 3
    start_x = (W - total_w) // 2
    py = H - phone_h - 80

    # draw phones first (behind)
    for i, s in enumerate(shots):
        ph = phone_frame(s, phone_w, phone_h, bezel=8, top_bar=14)
        # dim
        ph = Image.blend(
            ph, Image.new("RGBA", ph.size, (0x13, 0x13, 0x13, 180)), 0.25
        )
        px = start_x + i * (phone_w + gap)
        # slight vertical stagger
        paste_phone(im, ph, px, py + (i % 2) * 24 - 12, shadow=8)

    # logo + brand on top of phones
    im.paste(logo, (lx, ly), logo)
    d = ImageDraw.Draw(im)
    name_f = f_head(72)
    name = "NativeCode"
    tw, th = text_size(d, name, name_f)
    ny = ly + logo.size[1] + 16
    # dark plate under name so no overlap bleed
    pad = 16
    plate = Image.new("RGBA", (tw + pad * 2, th + pad * 2), (0x13, 0x13, 0x13, 210))
    im.paste(plate, ((W - tw) // 2 - pad, ny - pad // 2), plate)
    d = ImageDraw.Draw(im)
    d.text(((W - tw) // 2, ny), name, font=name_f, fill=TEXT)

    tag_f = f_mono(20)
    tag = "AI CODING  ·  LINUX  ·  POCKET"
    tw2, th2 = text_size(d, tag, tag_f)
    d.text(((W - tw2) // 2, ny + th + 12), tag, font=tag_f, fill=PRIMARY)

    # corner brackets around logo+name block
    corner_brackets(
        d,
        (lx - 24, ly - 16, lx + logo.size[0] + 24, ny + th + th2 + 28),
        PRIMARY,
        36,
        3,
    )


def layout_grid4(
    im: Image.Image,
    headline: str,
    sub: str,
    items: list[tuple[str, str]],
    chips: list[str] | None = None,
):
    y0 = draw_header(im, headline, sub, chips=chips)
    # 2x2 phones in remaining space
    margin_x = 48
    margin_b = 56
    avail_h = H - y0 - margin_b
    avail_w = W - margin_x * 2
    gap = 20
    cell_w = (avail_w - gap) // 2
    # phone aspect ~9:19.5 → height from width
    phone_w = cell_w - 8
    phone_h = int(phone_w * 19.5 / 9)
    # if too tall, scale down
    max_ph = (avail_h - gap - 40) // 2  # room for labels
    if phone_h > max_ph:
        phone_h = max_ph
        phone_w = int(phone_h * 9 / 19.5)

    total_block_h = phone_h * 2 + gap + 36 * 2
    start_y = y0 + max(0, (avail_h - total_block_h) // 2)
    start_x = (W - (phone_w * 2 + gap)) // 2

    d = ImageDraw.Draw(im)
    lf = f_mono(18)
    for i, (fname, label) in enumerate(items[:4]):
        col, row = i % 2, i // 2
        x = start_x + col * (phone_w + gap)
        y = start_y + row * (phone_h + gap + 36)
        shot = load_shot(fname)
        ph = phone_frame(shot, phone_w, phone_h)
        paste_phone(im, ph, x, y, shadow=8)
        label_under(d, x, y + phone_h + 8, phone_w, label, lf)


def layout_three(
    im: Image.Image,
    headline: str,
    sub: str,
    items: list[tuple[str, str]],
    chips: list[str] | None = None,
):
    y0 = draw_header(im, headline, sub, chips=chips)
    margin_b = 56
    avail_h = H - y0 - margin_b
    gap = 16
    n = min(3, len(items))
    phone_w = (W - 64 - gap * (n - 1)) // n
    phone_h = int(phone_w * 19.5 / 9)
    max_ph = avail_h - 48
    if phone_h > max_ph:
        phone_h = max_ph
        phone_w = int(phone_h * 9 / 19.5)
    total_w = phone_w * n + gap * (n - 1)
    start_x = (W - total_w) // 2
    start_y = y0 + max(0, (avail_h - phone_h - 40) // 2)

    d = ImageDraw.Draw(im)
    lf = f_mono(16)
    for i, (fname, label) in enumerate(items[:n]):
        x = start_x + i * (phone_w + gap)
        shot = load_shot(fname)
        ph = phone_frame(shot, phone_w, phone_h)
        paste_phone(im, ph, x, start_y, shadow=8)
        label_under(d, x, start_y + phone_h + 10, phone_w, label, lf)


def layout_feature_side(
    im: Image.Image,
    headline: str,
    sub: str,
    main: str,
    sides: list[tuple[str, str]],
    chips: list[str] | None = None,
):
    """Large center phone + smaller side phones."""
    y0 = draw_header(im, headline, sub, chips=chips)
    margin_b = 56
    avail_h = H - y0 - margin_b
    main_h = int(avail_h * 0.92)
    main_w = int(main_h * 9 / 19.5)
    side_h = int(main_h * 0.72)
    side_w = int(side_h * 9 / 19.5)

    gap = 18
    total_w = main_w + side_w + gap  # one side stack on right
    # if 2 sides, stack them right
    start_x = (W - (main_w + gap + side_w)) // 2
    my = y0 + (avail_h - main_h) // 2

    main_shot = load_shot(main)
    paste_phone(im, phone_frame(main_shot, main_w, main_h), start_x, my, shadow=12)

    d = ImageDraw.Draw(im)
    lf = f_mono(15)
    sx = start_x + main_w + gap
    for i, (fname, label) in enumerate(sides[:2]):
        sy = my + i * (side_h + 28)
        if sy + side_h > H - 40:
            break
        paste_phone(im, phone_frame(load_shot(fname), side_w, side_h), sx, sy, shadow=8)
        label_under(d, sx, sy + side_h + 6, side_w, label, lf)

    label_under(d, start_x, my + main_h + 8, main_w, "DEBIAN SHELL", lf)


def layout_debian_ai(im: Image.Image):
    y0 = draw_header(
        im,
        "REAL DEBIAN + AI CLIs",
        "Rooted power shell. Agents one tap away.",
        chips=["DEBIAN", "OPENCODE", "CODEX", "CLAUDE", "AGY"],
    )
    margin_b = 48
    avail_h = H - y0 - margin_b
    # top: large terminal
    main_h = int(avail_h * 0.52)
    main_w = int(main_h * 9 / 19.5)
    mx = (W - main_w) // 2
    my = y0 + 8
    # Prefer active shell session (opencode) as hero shell face; tools grid is busier
    paste_phone(im, phone_frame(load_shot("terminal_opencode.png"), main_w, main_h), mx, my, 10)

    # bottom row: 4 AI tools
    row_y = my + main_h + 36
    items = [
        ("terminal_opencode.png", "OPENCODE"),
        ("terminal_agy.png", "AGY"),
        ("terminal_codex.png", "CODEX"),
        ("terminal_claude_code.png", "CLAUDE"),
    ]
    gap = 12
    n = 4
    phone_w = (W - 56 - gap * (n - 1)) // n
    phone_h = min(int(phone_w * 19.5 / 9), H - row_y - 50)
    phone_w = int(phone_h * 9 / 19.5)
    total = phone_w * n + gap * (n - 1)
    sx = (W - total) // 2
    d = ImageDraw.Draw(im)
    lf = f_mono(13)
    for i, (f, lab) in enumerate(items):
        x = sx + i * (phone_w + gap)
        paste_phone(im, phone_frame(load_shot(f), phone_w, phone_h), x, row_y, 6)
        label_under(d, x, row_y + phone_h + 6, phone_w, lab, lf)


def layout_xfce(im: Image.Image):
    y0 = draw_header(
        im,
        "FULL XFCE DESKTOP",
        "Linux GUI on Android — GPU-ready",
        chips=["XFCE4", "X11", "MARKETPLACE"],
    )
    margin_b = 56
    avail_h = H - y0 - margin_b
    main_h = int(avail_h * 0.88)
    main_w = int(main_h * 9 / 19.5)
    side_h = int(main_h * 0.55)
    side_w = int(side_h * 9 / 19.5)
    gap = 20
    total = main_w + gap + side_w
    sx = (W - total) // 2
    my = y0 + (avail_h - main_h) // 2
    paste_phone(
        im, phone_frame(load_shot("xfce4_display.png"), main_w, main_h), sx, my, 12
    )
    sy = my + (main_h - side_h) // 2
    paste_phone(
        im,
        phone_frame(load_shot("marketplace.png"), side_w, side_h),
        sx + main_w + gap,
        sy,
        8,
    )
    d = ImageDraw.Draw(im)
    lf = f_mono(16)
    label_under(d, sx, my + main_h + 8, main_w, "XFCE DESKTOP", lf)
    label_under(d, sx + main_w + gap, sy + side_h + 8, side_w, "MARKETPLACE", lf)


# ── frames ─────────────────────────────────────────────────────────

FRAMES = [
    ("01_hero", "hero", 0),
    ("02_ai_agents", "ai", 1),
    ("03_projects", "projects", 2),
    ("04_debian_ai", "debian", 3),
    ("05_agentic", "agentic", 4),
    ("06_xfce", "xfce", 5),
    ("07_ship", "ship", 6),
    ("08_control", "control", 7),
]


def render_frame(stem: str, kind: str, variant: int) -> Image.Image:
    im = make_base(variant)
    if kind == "hero":
        layout_hero(im)
    elif kind == "ai":
        layout_grid4(
            im,
            "4 AI CODING AGENTS",
            "Run on device — no laptop required",
            [
                ("terminal_opencode.png", "OPENCODE"),
                ("terminal_agy.png", "AGY"),
                ("terminal_codex.png", "CODEX"),
                ("terminal_claude_code.png", "CLAUDE CODE"),
            ],
            chips=["ON-DEVICE", "AGENTIC", "VIBE CODE"],
        )
    elif kind == "projects":
        layout_grid4(
            im,
            "FULL PROJECT CONTROL",
            "Workspace · files · git diff · config",
            [
                ("project_workspace.png", "WORKSPACE"),
                ("project_directory.png", "DIRECTORY"),
                ("git_diff.png", "GIT DIFF"),
                ("project_config.png", "CONFIG"),
            ],
            chips=["PROJECTS", "GIT", "FILES"],
        )
    elif kind == "debian":
        layout_debian_ai(im)
    elif kind == "agentic":
        layout_three(
            im,
            "AGENTIC DEV IN YOUR POCKET",
            "Home → workspace agent → ship",
            [
                ("home.png", "HOME"),
                ("workspace_agent.png", "AGENT"),
                ("all_projects.png", "PROJECTS"),
            ],
            chips=["FLOW", "AGENT", "SHIP"],
        )
    elif kind == "xfce":
        layout_xfce(im)
    elif kind == "ship":
        layout_three(
            im,
            "SHIP FROM ANYWHERE",
            "Marketplace · packages · git — hustler stack",
            [
                ("marketplace.png", "MARKETPLACE"),
                ("software_management.png", "PACKAGES"),
                ("git_diff.png", "GIT"),
            ],
            chips=["HUSTLE", "INSTALL", "SHIP"],
        )
    elif kind == "control":
        layout_three(
            im,
            "BUILT FOR BUILDERS",
            "Settings · repairs · you own the stack",
            [
                ("settings.png", "SETTINGS"),
                ("repairs.png", "REPAIRS"),
                ("home.png", "DASHBOARD"),
            ],
            chips=["CONTROL", "TRUST", "POWER"],
        )
    else:
        raise ValueError(kind)

    # final light vignette (vectorized)
    scale = 4
    sw, sh = W // scale, H // scale
    ys = np.linspace(-1, 1, sh, dtype=np.float32)
    xs = np.linspace(-1, 1, sw, dtype=np.float32)
    xx, yy = np.meshgrid(xs, ys)
    edge = np.maximum(np.abs(xx), np.abs(yy))
    a = np.where(edge > 0.75, ((edge - 0.75) / 0.25) ** 2 * 90, 0).astype(np.uint8)
    arr = np.zeros((sh, sw, 4), dtype=np.uint8)
    arr[..., 3] = a
    vig = Image.fromarray(arr, "RGBA").resize((W, H), Image.Resampling.BILINEAR)
    im = Image.alpha_composite(im, vig)
    return im.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    assert SHOTS.is_dir(), SHOTS
    assert LOGO.is_file(), LOGO
    for stem, kind, var in FRAMES:
        print(f"render {stem}…")
        img = render_frame(stem, kind, var)
        path = OUT / f"{stem}.png"
        img.save(path, "PNG", optimize=True)
        print(f"  → {path} {img.size}")
    print("done", OUT)


if __name__ == "__main__":
    main()
