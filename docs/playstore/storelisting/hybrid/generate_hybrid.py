#!/usr/bin/env python3
"""Hybrid storelisting: AI textured backgrounds + Python layout (real UI faces)."""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]  # storelisting/
PY = ROOT / "python"
sys.path.insert(0, str(PY))

import generate_storelisting as g  # noqa: E402

OUT = Path(__file__).resolve().parent
ASSETS = OUT / "ai_assets"
BGS = ["bg_a.jpg", "bg_b.jpg", "bg_c.jpg"]


def ai_base(variant: int) -> Image.Image:
    bg_path = ASSETS / BGS[variant % len(BGS)]
    bg = Image.open(bg_path).convert("RGBA").resize((g.W, g.H), Image.Resampling.LANCZOS)
    # darken so text/UI pop, keep texture
    bg = ImageEnhance.Brightness(bg).enhance(0.45)
    bg = ImageEnhance.Contrast(bg).enhance(1.15)
    # blend with brand void
    void = Image.new("RGBA", (g.W, g.H), g.BG)
    base = Image.blend(void, bg, 0.72)
    # green glow overlays from python
    positions = [
        (g.W * 0.5, g.H * 0.18, 480, 50),
        (g.W * 0.15, g.H * 0.8, 360, 28),
        (g.W * 0.9, g.H * 0.5, 340, 24),
    ]
    for i, (cx, cy, rad, peak) in enumerate(positions):
        shift = (variant * 29 + i * 13) % 60
        base = Image.alpha_composite(
            base, g.radial_glow(g.W, g.H, cx + shift, cy, rad, g.PRIMARY, peak)
        )
    base = Image.alpha_composite(base, g.noise_layer(g.W, g.H, 14, g.NOISE_SEED + variant + 10))
    base = Image.alpha_composite(base, g.scanlines(g.W, g.H, 5, 14))
    # faint grid on top of AI texture
    base = Image.alpha_composite(base, g.grid_layer(g.W, g.H, 40))
    draw = ImageDraw.Draw(base)
    draw.rectangle([0, 0, g.W, 6], fill=g.PRIMARY)
    draw.rectangle([0, g.H - 6, g.W, g.H], fill=g.PRIMARY)
    return base


def render(stem: str, kind: str, variant: int) -> Image.Image:
    # monkey-patch make_base for this render
    original = g.make_base
    g.make_base = lambda v=0: ai_base(variant)
    try:
        im = g.render_frame(stem, kind, variant)
    finally:
        g.make_base = original
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for stem, kind, var in g.FRAMES:
        print(f"hybrid {stem}…")
        img = render(stem, kind, var)
        # slight extra sharpen for marketing punch
        img = ImageEnhance.Sharpness(img).enhance(1.08)
        path = OUT / f"{stem}.png"
        img.save(path, "PNG", optimize=True)
        print(f"  → {path}")
    print("done", OUT)


if __name__ == "__main__":
    main()
