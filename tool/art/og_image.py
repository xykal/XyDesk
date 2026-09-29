#!/usr/bin/env python3
"""Render web/public/og-image.jpg (1200x630) dari logo + font Inter repo.

Deterministik: jalankan ulang menghasilkan gambar yang sama, sehingga
perubahan teks/brand cukup di sini, bukan di editor gambar.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
LOGO = ROOT / 'assets' / 'img' / 'logo.png'
FONTS = ROOT / 'assets' / 'fonts'
OUT = ROOT / 'web' / 'public' / 'og-image.jpg'

W, H = 1200, 630
TOP, BOTTOM = (0x4A, 0x1A, 0x7A), (0x2A, 0x0B, 0x4A)
WHITE = (255, 255, 255)
LILAC = (0xC9, 0xB3, 0xF0)

TITLE = 'XyDesk'
TAGLINE = 'Remote desktop low-latency untuk kerja dan gaming'
PLATFORMS = 'Android  ·  Windows  ·  Web'
BRAND = 'Powered by XyVerse Technology Global'


def gradient() -> Image.Image:
    img = Image.new('RGB', (W, H))
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        row = tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
        for x in range(W):
            px[x, y] = row
    return img


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / f'Inter-{name}.ttf'), size)


def fit(draw: ImageDraw.ImageDraw, text: str, name: str, size: int, max_w: int) -> ImageFont.FreeTypeFont:
    while size > 12:
        f = font(name, size)
        if draw.textlength(text, font=f) <= max_w:
            return f
        size -= 2
    return font(name, size)


def main() -> None:
    img = gradient().convert('RGBA')
    draw = ImageDraw.Draw(img)

    logo = Image.open(LOGO).convert('RGBA')
    logo.thumbnail((220, 220), Image.LANCZOS)
    lx, ly = 96, (H - logo.height) // 2 - 20
    img.alpha_composite(logo, (lx, ly))

    x = lx + logo.width + 56
    max_w = W - x - 72
    f_title = font('Bold', 96)
    f_tag = fit(draw, TAGLINE, 'Regular', 34, max_w)
    f_plat = fit(draw, PLATFORMS, 'Medium', 30, max_w)
    f_brand = fit(draw, BRAND, 'Medium', 22, W - 2 * 96)

    y = 200
    draw.text((x, y), TITLE, font=f_title, fill=WHITE)
    y += 118
    draw.text((x, y), TAGLINE, font=f_tag, fill=WHITE)
    y += 54
    draw.text((x, y), PLATFORMS, font=f_plat, fill=LILAC)
    draw.text((96, H - 64), BRAND, font=f_brand, fill=LILAC)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.convert('RGB').save(OUT, 'JPEG', quality=88, optimize=True, progressive=True)
    print(f'ditulis {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)')


if __name__ == '__main__':
    main()
