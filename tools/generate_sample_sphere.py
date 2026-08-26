#!/usr/bin/env python3
"""Generate a labeled equirectangular 360 sample used as the in-app demo sphere."""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

WIDTH, HEIGHT = 2048, 1024
OUT = Path("/workspace/app/src/main/assets/sample_sphere.jpg")


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def sky_color(v: float) -> tuple[int, int, int]:
    # v=0 zenith, v=0.5 horizon, v=1 nadir
    if v < 0.48:
        t = v / 0.48
        return (
            int(lerp(28, 186, t)),
            int(lerp(72, 214, t)),
            int(lerp(158, 236, t)),
        )
    if v < 0.54:
        t = (v - 0.48) / 0.06
        return (
            int(lerp(186, 212, t)),
            int(lerp(214, 168, t)),
            int(lerp(236, 112, t)),
        )
    t = min(1.0, (v - 0.54) / 0.46)
    return (
        int(lerp(92, 38, t)),
        int(lerp(78, 52, t)),
        int(lerp(44, 28, t)),
    )


def mountain_height(u: float) -> float:
    x = u * 2 * math.pi
    return (
        0.045 * math.sin(x * 3.0)
        + 0.03 * math.sin(x * 7.0 + 0.6)
        + 0.02 * math.sin(x * 13.0 + 1.4)
        + 0.012
    )


def draw_sun(pixels) -> None:
    sun_u, sun_v = 0.18, 0.28
    sun_x, sun_y = sun_u * WIDTH, sun_v * HEIGHT
    for y in range(int(sun_y - 90), int(sun_y + 90)):
        for x in range(int(sun_x - 90), int(sun_x + 90)):
            if not (0 <= x < WIDTH and 0 <= y < HEIGHT):
                continue
            d = math.hypot(x - sun_x, y - sun_y)
            if d < 28:
                pixels[x, y] = (255, 236, 170)
            elif d < 70:
                t = (d - 28) / 42
                r, g, b = pixels[x, y]
                glow = 1.0 - t
                pixels[x, y] = (
                    min(255, int(r + 90 * glow)),
                    min(255, int(g + 60 * glow)),
                    min(255, int(b + 10 * glow)),
                )


def main() -> None:
    img = Image.new("RGB", (WIDTH, HEIGHT))
    px = img.load()
    for y in range(HEIGHT):
        v = y / (HEIGHT - 1)
        for x in range(WIDTH):
            u = x / (WIDTH - 1)
            r, g, b = sky_color(v)
            # longitude tint so yaw is obvious while looking around
            hue = 0.5 + 0.5 * math.sin(u * 2 * math.pi)
            if v < 0.52:
                r = min(255, int(r + 18 * hue))
                b = min(255, int(b + 10 * (1 - hue)))
            ridge = 0.50 - mountain_height(u)
            if ridge < v < ridge + 0.035:
                shade = int(lerp(70, 42, (v - ridge) / 0.035))
                r, g, b = shade + 18, shade + 8, shade
            px[x, y] = (r, g, b)

    draw_sun(px)

    # Equirectangular grid every 30 degrees
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    for deg in range(0, 360, 30):
        x = int(deg / 360 * WIDTH)
        alpha = 90 if deg % 90 == 0 else 40
        draw.line([(x, 0), (x, HEIGHT)], fill=(255, 255, 255, alpha), width=2 if deg % 90 == 0 else 1)
    for deg in range(-90, 91, 30):
        y = int((90 - deg) / 180 * HEIGHT)
        alpha = 110 if deg == 0 else 40
        draw.line([(0, y), (WIDTH, y)], fill=(255, 255, 255, alpha), width=2 if deg == 0 else 1)

    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 92)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 36)
    except OSError:
        font = ImageFont.load_default()
        small = font

    labels = [("N", 0.50), ("E", 0.75), ("S", 0.00), ("W", 0.25)]
    for text, u in labels:
        x = int(u * WIDTH)
        y = int(0.46 * HEIGHT)
        bbox = draw.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        draw.text((x - tw / 2 + 3, y - th / 2 + 3), text, fill=(20, 20, 16), font=font)
        draw.text((x - tw / 2, y - th / 2), text, fill=(255, 232, 160), font=font)

    caption = "LocalPhoto360  ·  drag to look around  ·  sample photosphere"
    bbox = draw.textbbox((0, 0), caption, font=small)
    tw = bbox[2] - bbox[0]
    draw.text(((WIDTH - tw) / 2, HEIGHT * 0.62), caption, fill=(250, 244, 220), font=small)

    img = img.filter(ImageFilter.SMOOTH)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, "JPEG", quality=88, optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
