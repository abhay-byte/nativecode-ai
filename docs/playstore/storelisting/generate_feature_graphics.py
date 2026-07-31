#!/usr/bin/env python3
"""Play Store feature graphics 1024x500 — one per pipeline (python / ai / hybrid).

No text overlapping screenshots. Brand left, product right, textures + effects.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[3]  # termux-lib
SHOTS = ROOT / "docs" / "screenshots" / "v1"
LOGO = ROOT / "app" / "src" / "main" / "res" / "drawable" / "logo_highres.webp"
STORE = Path(__file__).resolve().parent
FONT_SG = Path("/tmp/storelisting-fonts/SpaceGrotesk.ttf")
FW, FH = 1024, 500

PRIMARY = (0x3D, 0xDC, 0x84, 255)
PRIMARY_SOFT = (0x60, 0xF9, 0x9E, 255)
TEXT = (0xFA, 0xFA, 0xFA, 255)
TEXT_DIM = (0xBB, 0xCB, 0xBC, 255)
BG = (0x13, 0x13, 0x13, 255)
SURFACE_BRIGHT = (0x39, 0x39, 0x39, 255)
OUTLINE_VAR = (0x3C, 0x4A, 0x3F, 255)


def font(path, size, fallback="/usr/share/fonts/TTF/FiraSans-Bold.ttf"):
    for p in (path, fallback, "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"):
        if p and Path(p).exists():
            try:
                return ImageFont.truetype(str(p), size)
            except OSError:
                pass
    return ImageFont.load_default()


def f_head(s):
    return font(FONT_SG, s)


def f_mono(s):
    return font("/usr/share/fonts/TTF/JetBrainsMono-Bold.ttf", s, "/usr/share/fonts/TTF/DejaVuSansMono-Bold.ttf")


def f_body(s):
    return font("/usr/share/fonts/TTF/FiraSans-Book.ttf", s, "/usr/share/fonts/TTF/DejaVuSans.ttf")


def text_size(d, t, f):
    b = d.textbbox((0, 0), t, font=f)
    return b[2] - b[0], b[3] - b[1]


def radial(w, h, cx, cy, r, color, peak=70):
    scale = 2
    sw, sh = w // scale, h // scale
    ys = np.arange(sh, dtype=np.float32)
    xs = np.arange(sw, dtype=np.float32)
    xx, yy = np.meshgrid(xs, ys)
    dist = np.sqrt(((xx - cx / scale) / (r / scale)) ** 2 + ((yy - cy / scale) / (r / scale)) ** 2)
    a = (np.clip(1 - dist, 0, 1) ** 2 * peak).astype(np.uint8)
    arr = np.zeros((sh, sw, 4), dtype=np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3] = *color[:3], a
    return Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)


def noise(w, h, alpha=16, seed=7):
    rng = np.random.default_rng(seed)
    hw, hh = w // 2, h // 2
    v = rng.integers(0, 256, (hh, hw), dtype=np.uint8)
    a = rng.integers(0, alpha + 1, (hh, hw), dtype=np.uint8)
    arr = np.dstack([v, v, v, a])
    return Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)


def grid(w, h, step=28):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = (0x3C, 0x4A, 0x3F, 30)
    for x in range(0, w, step):
        d.line([(x, 0), (x, h)], fill=c)
    for y in range(0, h, step):
        d.line([(0, y), (w, y)], fill=c)
    return im


def cover_crop(im, tw, th):
    iw, ih = im.size
    scale = max(tw / iw, th / ih)
    nw, nh = int(iw * scale), int(ih * scale)
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left, top = (nw - tw) // 2, max(0, (nh - th) // 8)
    return im.crop((left, top, left + tw, top + th))


def phone(shot_name, pw, ph, bezel=6):
    shot = Image.open(SHOTS / shot_name).convert("RGBA")
    out = Image.new("RGBA", (pw, ph), (0x0E, 0x0E, 0x0E, 255))
    d = ImageDraw.Draw(out)
    d.rectangle([0, 0, pw - 1, ph - 1], outline=PRIMARY, width=2)
    sx0, sy0 = bezel, bezel + 6
    sw, sh = pw - bezel * 2, ph - bezel * 2 - 6
    screen = cover_crop(shot, sw, sh)
    out.paste(screen, (sx0, sy0), screen)
    return out


def hard_shadow(canvas, x, y, pw, ph, off=6):
    d = ImageDraw.Draw(canvas)
    d.rectangle([x + off, y + ph, x + pw + off, y + ph + off], fill=SURFACE_BRIGHT)
    d.rectangle([x + pw, y + off, x + pw + off, y + ph + off], fill=OUTLINE_VAR)


def base_python():
    im = Image.new("RGBA", (FW, FH), BG)
    a = np.linspace(0, 35, FH, dtype=np.float32).astype(np.uint8)
    arr = np.zeros((FH, FW, 4), dtype=np.uint8)
    arr[..., 3] = a[:, None]
    im = Image.alpha_composite(im, Image.fromarray(arr, "RGBA"))
    im = Image.alpha_composite(im, radial(FW, FH, 200, 250, 320, PRIMARY, 55))
    im = Image.alpha_composite(im, radial(FW, FH, 850, 200, 280, PRIMARY, 40))
    im = Image.alpha_composite(im, grid(FW, FH))
    im = Image.alpha_composite(im, noise(FW, FH, 14, 3))
    # scanlines
    d = ImageDraw.Draw(im)
    for y in range(0, FH, 4):
        d.line([(0, y), (FW, y)], fill=(0, 0, 0, 12))
    d.rectangle([0, 0, FW, 4], fill=PRIMARY)
    d.rectangle([0, FH - 4, FW, FH], fill=PRIMARY)
    return im


def base_ai():
    bg = Image.open(STORE / "ai" / "ai_assets" / "fg_base.jpg").convert("RGBA")
    bg = bg.resize((FW, FH), Image.Resampling.LANCZOS)
    bg = ImageEnhance.Brightness(bg).enhance(0.5)
    bg = ImageEnhance.Color(bg).enhance(1.2)
    void = Image.new("RGBA", (FW, FH), BG)
    im = Image.blend(void, bg, 0.85)
    im = Image.alpha_composite(im, radial(FW, FH, 180, 250, 300, PRIMARY, 60))
    im = Image.alpha_composite(im, noise(FW, FH, 20, 11))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, FW, 5], fill=PRIMARY)
    d.rectangle([0, FH - 5, FW, FH], fill=PRIMARY)
    return im


def base_hybrid():
    # mix python void + ai banner texture
    py = base_python()
    ai = base_ai()
    return Image.blend(py, ai, 0.55)


def draw_brand_left(im: Image.Image, style: str):
    """Left half: logo + name + tagline. Right reserved for phones."""
    d = ImageDraw.Draw(im)
    # left content max x = 480 (safe zone before phones)
    left_max = 470

    logo = Image.open(LOGO).convert("RGBA")
    logo.thumbnail((110, 110), Image.Resampling.LANCZOS)
    lx, ly = 48, 48
    im.paste(logo, (lx, ly), logo)

    # name
    nf = f_head(48)
    name = "NativeCode"
    d.text((48, 170), name, font=nf, fill=TEXT)

    # tagline — wrap within left zone
    tf = f_body(22)
    tag = "Vibe code on your phone"
    d.text((48, 230), tag, font=tf, fill=PRIMARY_SOFT)

    sf = f_mono(16)
    sub = "Portable Linux & AI Dev Env"
    d.text((48, 268), sub, font=sf, fill=TEXT_DIM)

    # chips
    chips = ["AI AGENTS", "DEBIAN", "POCKET"]
    mf = f_mono(13)
    x, y = 48, 320
    for c in chips:
        tw, th = text_size(d, c, mf)
        cw, ch = tw + 20, 28
        if x + cw > left_max:
            break
        d.rectangle([x, y, x + cw, y + ch], outline=PRIMARY, width=2, fill=(0x1C, 0x1B, 0x1B, 220))
        d.text((x + 10, y + 6), c, font=mf, fill=PRIMARY_SOFT)
        x += cw + 8

    # style badge
    d.text((48, FH - 48), f"FEATURE  ·  {style.upper()}", font=f_mono(12), fill=(0x60, 0xF9, 0x9E, 160))


def draw_phones_right(im: Image.Image, shots: list[str]):
    """Phones only on right half — no text over them."""
    # 3 phones fan
    ph = 400
    pw = int(ph * 9 / 19.5)
    base_x = 500
    base_y = (FH - ph) // 2 + 10
    offsets = [(0, 20), (pw // 2 + 12, 0), (pw + 24, 28)]
    for i, (fname, (ox, oy)) in enumerate(zip(shots[:3], offsets)):
        p = phone(fname, pw, ph)
        x, y = base_x + ox, base_y + oy
        # clip if overflow
        if x + pw > FW - 8:
            continue
        hard_shadow(im, x, y, pw, ph, 5)
        im.paste(p, (x, y), p)


def make_fg(style: str) -> Image.Image:
    if style == "python":
        im = base_python()
    elif style == "ai":
        im = base_ai()
    else:
        im = base_hybrid()

    shots = ["home.png", "terminal_opencode.png", "project_workspace.png"]
    draw_phones_right(im, shots)
    draw_brand_left(im, style)

    # vignette edges
    vig = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    # left/right soft
    arr = np.zeros((FH, FW, 4), dtype=np.uint8)
    for x in range(40):
        a = int(60 * (1 - x / 40))
        arr[:, x, 3] = a
        arr[:, FW - 1 - x, 3] = a
    vig = Image.fromarray(arr, "RGBA")
    im = Image.alpha_composite(im, vig)
    return im.convert("RGB")


def main():
    for style in ("python", "ai", "hybrid"):
        out_dir = STORE / style
        out_dir.mkdir(parents=True, exist_ok=True)
        img = make_fg(style)
        path = out_dir / "feature_graphic_1024x500.png"
        img.save(path, "PNG", optimize=True)
        print(f"→ {path} {img.size}")


if __name__ == "__main__":
    main()
