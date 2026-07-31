#!/usr/bin/env python3
"""Generate NativeCode Play Store listing images (1080x1920 portrait).

Design system: docs/project/ui_design.md (Cyber-Brutalist / Obsidian / Terminal Green)
Plan:          docs/plan/storelisting-images.md
Input:         docs/screenshots/v1/*.png, app/src/main/res/mipmap-nodpi/logo.webp
Output:        docs/storelisting/1_hero.png ... 8_cta.png
"""

import os
import math
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps

W, H = 1080, 1920
BG = (10, 10, 10)
SURFACE = (18, 18, 18)
CONTAINER = (30, 30, 30)
TEXT = (250, 250, 250)
GREEN = (61, 220, 132)
GREEN_DIM = (60, 74, 63)
BRIGHT = (57, 57, 57)
MUTED = (150, 150, 150)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "docs", "screenshots", "v1")
OUT = os.path.join(ROOT, "docs", "storelisting")
LOGO = os.path.join(ROOT, "app", "src", "main", "res", "mipmap-nodpi", "logo.webp")
FONTS = "/tmp/opencode/fonts"

FALLBACK_HEAD = None
for cand in ["/usr/share/fonts/truetype/fira/FiraSans-Bold.ttf",
             "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
    if os.path.exists(cand):
        FALLBACK_HEAD = cand
        break
FALLBACK_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"


def font_head(size, weight=700):
    path = os.path.join(FONTS, "SpaceGrotesk.ttf")
    if os.path.exists(path):
        f = ImageFont.truetype(path, size)
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
        return f
    return ImageFont.truetype(FALLBACK_HEAD, size)


def font_mono(size):
    path = os.path.join(FONTS, "JetBrainsMono-Bold.ttf")
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.truetype(FALLBACK_MONO, size)


def text_bbox(draw, x, y, text, fnt, fill):
    bb = draw.textbbox((x, y), text, font=fnt)
    draw.text((x, y), text, font=fnt, fill=fill)
    return bb


def draw_grid(draw):
    for gx in range(0, W + 1, 96):
        draw.line([(gx, 0), (gx, H)], fill=(19, 19, 19), width=1)
    for gy in range(0, H + 1, 96):
        draw.line([(0, gy), (W, gy)], fill=(19, 19, 19), width=1)


def draw_scanlines(img):
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    for y in range(0, H, 6):
        d.line([(0, y), (W, y)], fill=(255, 255, 255, 5), width=1)
    img.alpha_composite(overlay)
    return img


def draw_corners(draw, x, y, w, h, color=GREEN, L=28):
    for (cx, cy, sx, sy) in [(x, y, 1, 1), (x + w, y, -1, 1),
                             (x, y + h, 1, -1), (x + w, y + h, -1, -1)]:
        draw.line([(cx, cy), (cx + sx * L, cy)], fill=color, width=3)
        draw.line([(cx, cy), (cx, cy + sy * L)], fill=color, width=3)


def draw_chip(draw, x, y, text, fnt, fill=SURFACE, border=GREEN):
    bb = draw.textbbox((0, 0), text, font=fnt)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    padx, pady = 18, 10
    cw, ch = tw + padx * 2, th + pady * 2
    draw.rectangle([x, y, x + cw, y + ch], fill=fill, outline=border, width=1)
    draw.text((x + padx, y + pady - bb[1]), text, font=fnt, fill=TEXT)
    return (x, y, x + cw, y + ch)


def glow(img, cx, cy, radius=700, alpha=26):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
               fill=(GREEN[0], GREEN[1], GREEN[2], alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(120))
    img.alpha_composite(layer)


def hard_shadow(draw, x, y, w, h, off=10):
    right = off
    bottom = off
    rx = x + w
    ry = y + h
    draw.polygon([(rx, y), (rx + right, y), (rx + right, ry + bottom), (rx, ry + bottom),
                  (rx, ry), (rx + right, ry), (rx + right, ry + bottom), (rx, ry + bottom)],
                 fill=GREEN_DIM)
    draw.rectangle([x + right, y + h, x + w + right, y + h + bottom], fill=BRIGHT)


def left_shadow(draw, x, y, w, h, off=8):
    draw.rectangle([x - off, y, x, y + h], fill=BRIGHT)
    draw.polygon([(x - off, y + h), (x - off, y + h + off), (x + w, y + h + off), (x + w, y + h)],
                 fill=GREEN_DIM)


def load_shot(name):
    img = Image.open(os.path.join(SHOTS, name)).convert("RGB")
    img = ImageOps.exif_transpose(img)
    return img


def card(img, draw, x, y, w, h, shot_name, focus="top", border=GREEN, shadow=True, frame=False, frame_out=False):
    """Place screenshot into sharp card at (x,y,w,h). Focus: top|center|bottom."""
    if shadow:
        hard_shadow(draw, x, y, w, h)
    shot = load_shot(shot_name)
    sw, sh = shot.size
    scale = max(w / sw, h / sh)
    nw, nh = int(sw * scale), int(sh * scale)
    shot = shot.resize((nw, nh), Image.LANCZOS)
    if focus == "top":
        crop_y = 0
    elif focus == "bottom":
        crop_y = nh - h
    else:
        crop_y = (nh - h) // 2
    shot = shot.crop((0, crop_y, nw, crop_y + h))
    paste = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    paste.paste(shot, (x, y))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rectangle([x, y, x + w, y + h], fill=255)
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), mask)
    img.alpha_composite(paste)
    if frame:
        d2 = ImageDraw.Draw(img)
        if frame_out:
            d2.rectangle([x - 2, y - 2, x + w + 2, y + h + 2], outline=border, width=2)
            d2.rectangle([x - 1, y - 1, x + w + 1, y + h + 1], outline=(255, 255, 255, 18), width=1)
        else:
            d2.rectangle([x, y, x + w, y + h], outline=border, width=2)
            d2.rectangle([x + 1, y + 1, x + w - 1, y + h - 1], outline=(255, 255, 255, 18), width=1)
    return (x, y, x + w, y + h)


def headline(draw, text, y, size=58, fill=TEXT, x=72, weight=700):
    fnt = font_head(size, weight)
    bb = text_bbox(draw, x, y, text, fnt, fill)
    return bb


def sub(draw, text, y, size=30, fill=MUTED, x=74):
    fnt = font_mono(size)
    return text_bbox(draw, x, y, text, fnt, fill)


def mono_label(draw, text, y, size=24, fill=GREEN, x=74):
    fnt = font_mono(size)
    return text_bbox(draw, x, y, text, fnt, fill)


def base_canvas():
    img = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(img)
    draw_grid(d)
    img = draw_scanlines(img)
    return img, ImageDraw.Draw(img)


def chip_row(draw, labels, y, x=72, fnt_size=22):
    cx = x
    maxy = y
    for lab in labels:
        bb = draw.textbbox((0, 0), lab, font=font_mono(fnt_size))
        cw = (bb[2] - bb[0]) + 36
        ch = (bb[3] - bb[1]) + 20
        cx, cy, cw2, ch2 = draw_chip(draw, cx, y, lab, font_mono(fnt_size))
        cx = cw2 + 14
        maxy = max(maxy, ch2)
    return maxy


def get_logo():
    return Image.open(LOGO).convert("RGBA")


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.convert("RGB").save(os.path.join(OUT, name), "PNG")
    print("saved", name)


def make_hero():
    img, d = base_canvas()
    glow(img, W // 2, H // 2, 900, 30)

    d.rectangle([0, 0, W, 80], fill=(14, 14, 14))
    mono_label(d, "// AI DEV ENVIRONMENT FOR ANDROID", 26, size=24)

    logo = get_logo()
    lw = 220
    lh = int(logo.size[1] * lw / logo.size[0])
    logo = logo.resize((lw, lh), Image.LANCZOS)
    img.paste(logo, ((W - lw) // 2, 150), logo)

    name_fnt = font_head(84, 700)
    nm = "NativeCode"
    bb = d.textbbox((0, 0), nm, font=name_fnt)
    nw = bb[2] - bb[0]
    d.text(((W - nw) // 2, 380), nm, font=name_fnt, fill=TEXT)

    tag = "VIBE CODE ON YOUR PHONE"
    tag_fnt = font_head(38, 700)
    bb = d.textbbox((0, 0), tag, font=tag_fnt)
    tw = bb[2] - bb[0]
    d.text(((W - tw) // 2, 510), tag, font=tag_fnt, fill=GREEN)

    sub_fnt = font_mono(24)
    s1 = "Full Linux + AI coding agents in your pocket."
    s2 = "No PC needed."
    for i, s in enumerate([s1, s2]):
        bb = d.textbbox((0, 0), s, font=sub_fnt)
        sw = bb[2] - bb[0]
        d.text(((W - sw) // 2, 590 + i * 40), s, font=sub_fnt, fill=MUTED)

    cta_fnt = font_mono(26)
    cta = "GET NATIVECODE"
    bb = d.textbbox((0, 0), cta, font=cta_fnt)
    cw = bb[2] - bb[0] + 72
    ch = 72
    cx, cy = (W - cw) // 2, 690
    hard_shadow(d, cx, cy, cw, ch, off=8)
    d.rectangle([cx, cy, cx + cw, cy + ch], fill=GREEN)
    d.text((cx + 36, cy + (ch - (bb[3] - bb[1])) // 2 - bb[1]), cta, font=cta_fnt, fill=(10, 10, 10))

    c1 = card(img, d, 40, 820, 1000, 1250, "home.png", focus="top")
    save(img, "1_hero.png")


def rotated_card(img, cx, cy, w, h, shot_name, angle, focus="center", dim=0.8):
    shot = load_shot(shot_name)
    sw, sh = shot.size
    scale = max(w / sw, h / sh)
    nw, nh = int(sw * scale), int(sh * scale)
    shot = shot.resize((nw, nh), Image.LANCZOS)
    x0 = (nw - w) // 2
    y0 = (nh - h) // 2 if focus == "center" else 0
    shot = shot.crop((x0, y0, x0 + w, y0 + h))
    shot = ImageEnhance.Brightness(shot).enhance(dim)
    shot = shot.rotate(angle, expand=True, resample=Image.BICUBIC, fillcolor=BG)
    rw, rh = shot.size
    px, py = int(cx - rw / 2), int(cy - rh / 2)
    shot = shot.convert("RGBA")
    img.paste(shot, (px, py), shot)


def make_ai():
    img, d = base_canvas()
    glow(img, 540, 700, 800, 22)
    mono_label(d, "// AI CLI TOOLS ON DEVICE", 60)
    headline(d, "6 CLI TOOLS.", 112, size=62)
    headline(d, "ONE POCKET.", 186, size=62, fill=GREEN)
    sub(d, "Claude Code · Codex · OpenCode · Agy · Grok · Qwen", 292)

    card(img, d, 62, 450, 955, 2100, "project_workspace.png", focus="top")
    save(img, "2_ai_agents.png")


def make_projects():
    img, d = base_canvas()
    glow(img, 540, 650, 800, 22)
    mono_label(d, "// AGENTIC PROJECT WORKSPACE", 60)
    headline(d, "BUILD PROJECTS", 112, size=62)
    headline(d, "ANYWHERE.", 186, size=62, fill=GREEN)
    sub(d, "Project files · visual git diff", 292)

    left_shadow(d, 30, 493, 430, 1760)
    card(img, d, 30, 493, 430, 1760, "project_directory.png", focus="top", frame=False, shadow=False)
    left_shadow(d, 250, 405, 510, 1760)
    card(img, d, 250, 405, 510, 1760, "git_diff.png", focus="top", frame=False, shadow=False)
    save(img, "3_projects.png")


def make_shell():
    img, d = base_canvas()
    glow(img, 540, 600, 800, 22)
    mono_label(d, "// PORTABLE AI WORKSTATION", 60)
    headline(d, "RUN YOUR CLI TOOLS", 112, size=62)
    headline(d, "ANYWHERE.", 186, size=62, fill=GREEN)
    sub(d, "Full Debian 13 shell · 6 AI CLIs · no PC", 292)

    card(img, d, 81, 450, 955, 2100, "terminal_codex_new.png", focus="top", frame=False)
    card(img, d, 43, 1027, 955, 2100, "terminal_claude_code_new2.png", focus="top", frame=False)
    save(img, "4_linux_shell.png")


def make_marketplace():
    img, d = base_canvas()
    glow(img, 540, 600, 800, 22)
    mono_label(d, "// SOFTWARE MARKETPLACE", 70)
    headline(d, "INSTALL ANYTHING.", 130, size=64)
    headline(d, "INSTANTLY.", 210, size=64, fill=GREEN)
    sub(d, "Curated catalog · one tap · no PC required", 330)

    card(img, d, 62, 450, 955, 2100, "marketplace.png", focus="top")
    save(img, "5_marketplace.png")


def make_agent_workspace():
    img, d = base_canvas()
    glow(img, 540, 600, 800, 22)
    mono_label(d, "// WORKSPACE AGENT", 70)
    headline(d, "YOUR AGENT.", 130, size=64)
    headline(d, "ALWAYS ON.", 210, size=64, fill=GREEN)
    sub(d, "Long-running sessions · multi-repo tree", 330)

    card(img, d, 62, 450, 955, 2100, "workspace_agent.png", focus="top")
    save(img, "6_agent_workspace.png")


def make_desktop():
    img, d = base_canvas()
    glow(img, 540, 600, 800, 22)
    mono_label(d, "// GITHUB INTEGRATION", 60)
    headline(d, "GITHUB IN", 112, size=62)
    headline(d, "YOUR POCKET.", 186, size=62, fill=GREEN)
    sub(d, "Import projects · gh CLI · clone & push", 292)

    card(img, d, 62, 450, 955, 2100, "github_connect.png", focus="top")
    save(img, "7_desktop.png")


def make_cta():
    img, d = base_canvas()
    glow(img, 540, 700, 900, 30)
    mono_label(d, "// RUN GUI IDE · START CODING TODAY", 60)
    headline(d, "CODE ANYWHERE.", 112, size=62)
    headline(d, "SHIP EVERYTHING.", 186, size=62, fill=GREEN)
    sub(d, "Full GUI IDE · on device and fully local", 292)

    cta = "DOWNLOAD NATIVECODE"
    cta_fnt = font_mono(32)
    bb = d.textbbox((0, 0), cta, font=cta_fnt)
    cw = bb[2] - bb[0] + 90
    ch = 78
    cx, cy = (W - cw) // 2, 396
    hard_shadow(d, cx, cy, cw, ch, off=10)
    d.rectangle([cx, cy, cx + cw, cy + ch], fill=GREEN)
    d.text((cx + 45, cy + (ch - (bb[3] - bb[1])) // 2 - bb[1]), cta, font=cta_fnt, fill=(10, 10, 10))

    card(img, d, 62, 540, 955, 2100, "xfce4_cursor.png", focus="center")
    save(img, "8_cta.png")


if __name__ == "__main__":
    make_hero()
    make_ai()
    make_projects()
    make_shell()
    make_marketplace()
    make_agent_workspace()
    make_desktop()
    make_cta()
