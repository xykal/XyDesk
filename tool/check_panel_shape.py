#!/usr/bin/env python3
"""Periksa bentuk panel native XyDesk dari berkas snapshot 32-bit.

`XyDesk Control Panel.exe --panel-snapshot <berkas.bmp>` menggambar panel apa
adanya (jalur gambar yang sama dengan yang dipakai jendela berlapis) ke berkas
32-bit. Skrip ini membaca pikselnya dan membuktikan hal-hal yang tidak bisa
dibuktikan oleh uji angka biasa:

1. sudut panel benar-benar dipotong sebagai busur radius yang diminta —
   bukan kotak, dan bukan potongan kotak seperti `SetWindowRgn`,
2. busurnya dihaluskan (ada piksel dengan cakupan sebagian di tepi busur),
3. bayangan memudar menjauh dari panel dan habis di dalam margin,
4. tepi lurus tetap tajam, dan bagian dalam panel tetap opak + warnanya benar.

Dipakai CI Windows (`build.yml`, job lint installer). Tanpa dependensi selain
pustaka standar Python.
"""

from __future__ import annotations

import argparse
import math
import struct
import sys
from pathlib import Path


class Bitmap:
    def __init__(self, path: Path) -> None:
        data = path.read_bytes()
        if len(data) < 54 or data[:2] != b"BM":
            raise SystemExit(f"BMP GAGAL: {path} bukan berkas BMP")
        offset = struct.unpack_from("<I", data, 10)[0]
        width, height = struct.unpack_from("<ii", data, 18)
        bits = struct.unpack_from("<H", data, 28)[0]
        if bits != 32:
            raise SystemExit(f"BMP GAGAL: {bits} bit per piksel, harusnya 32")
        self.data = data
        self.offset = offset
        self.width = width
        self.top_down = height < 0
        self.height = abs(height)
        expected = self.offset + self.width * self.height * 4
        if len(data) < expected:
            raise SystemExit(f"BMP GAGAL: ukuran berkas {len(data)} < {expected}")

    def rgba(self, x: int, y: int) -> tuple[int, int, int, int]:
        row = y if self.top_down else self.height - 1 - y
        index = self.offset + (row * self.width + x) * 4
        blue, green, red, alpha = self.data[index : index + 4]
        return red, green, blue, alpha


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bitmap", type=Path)
    parser.add_argument("--panel-width", type=int, default=560)
    parser.add_argument("--panel-height", type=int, default=568)
    parser.add_argument("--margin", type=int, default=24, help="margin bayangan (default 24)")
    parser.add_argument("--radius", type=int, default=10, help="radius sudut (default 10)")
    parser.add_argument("--background", default="14,16,22", help="warna panel 'R,G,B' (default 14,16,22)")
    args = parser.parse_args()

    bitmap = Bitmap(args.bitmap)
    margin = args.margin
    panel_x, panel_y = margin, margin
    panel_w, panel_h = args.panel_width, args.panel_height
    radius = args.radius
    background = tuple(int(part) for part in args.background.split(","))

    problems: list[str] = []
    report: list[str] = []

    def check(condition: bool, message: str) -> None:
        if condition:
            report.append(f"  ok    {message}")
        else:
            problems.append(message)
            report.append(f"  GAGAL {message}")

    expected_width = panel_w + 2 * margin
    expected_height = panel_h + 2 * margin
    check(
        bitmap.width == expected_width and bitmap.height == expected_height,
        f"ukuran permukaan {bitmap.width}x{bitmap.height} sesuai tata letak ({expected_width}x{expected_height})",
    )

    # ── 1. Sudut dibulatkan sebagai busur radius yang diminta ──
    # Pusat busur tiap sudut, arah diagonal ke luar, dan nama sisinya.
    corners = [
        ("kiri atas", (panel_x + radius, panel_y + radius), (-1, -1)),
        ("kanan atas", (panel_x + panel_w - radius, panel_y + radius), (1, -1)),
        ("kiri bawah", (panel_x + radius, panel_y + panel_h - radius), (-1, 1)),
        ("kanan bawah", (panel_x + panel_w - radius, panel_y + panel_h - radius), (1, 1)),
    ]
    measured: list[float] = []
    smooth_evidence: list[int] = []
    for name, (cx, cy), (dx, dy) in corners:
        # Menyusuri diagonal dari pusat busur ke arah sudut: piksel penuh
        # terakhir menandai tepi busur.
        last_inside = 0.0
        partial: list[int] = []
        for step in range(0, radius + 6):
            x = int(round(cx + dx * step))
            y = int(round(cy + dy * step))
            if x < 0 or y < 0 or x >= bitmap.width or y >= bitmap.height:
                break
            alpha = bitmap.rgba(x, y)[3]
            if alpha >= 250:
                last_inside = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            elif last_inside > 0:
                partial.append(alpha)
        measured.append(last_inside)
        smooth_evidence.append(len(partial))
        check(
            abs(last_inside - radius) <= 2.0,
            f"sudut {name} membulat radius {last_inside:.1f} px (diminta {radius}±2)",
        )
        # Tanpa bayangan, piksel parsial pertama hanya cakupan busur (~0.2-0.5
        # alpha); yang penting ada piksel transisi, bukan lompatan 0->255.
        partial_floor = 130 if margin > 0 else 30
        check(
            len(partial) >= 1 and partial[0] >= partial_floor,
            f"sudut {name} punya piksel cakupan sebagian setelah busur "
            f"(alpha {partial[0] if partial else 'tidak ada'}) — tepi halus, bukan bergerigi",
        )

    spread = max(measured) - min(measured)
    check(spread <= 1.5, f"keempat sudut seragam (selisih radius terukur {spread:.1f} px)")

    # Sudut kotak: piksel paling pojok di dalam persegi panel harus tembus
    # pandang. Kalau tidak, bentuknya masih kotak.
    for name, (x, y) in (
        ("kiri atas", (panel_x, panel_y)),
        ("kanan atas", (panel_x + panel_w - 1, panel_y)),
        ("kiri bawah", (panel_x, panel_y + panel_h - 1)),
        ("kanan bawah", (panel_x + panel_w - 1, panel_y + panel_h - 1)),
    ):
        alpha = bitmap.rgba(x, y)[3]
        check(alpha < 150, f"pojok {name} tembus pandang (alpha {alpha} < 150, bukan kotak)")

    # ── 2. Tepi lurus tetap tajam, isi tetap opak ──
    mid_x = panel_x + panel_w // 2
    mid_y = panel_y + panel_h // 2
    inside_top = bitmap.rgba(mid_x, panel_y + 1)[3]
    check(inside_top == 255, f"tepi atas: piksel pertama di dalam panel opak (alpha {inside_top})")
    if margin > 0:
        outside_top = bitmap.rgba(mid_x, panel_y - 1)[3]
        check(outside_top <= 135, f"tepi atas: piksel di luar hanya bayangan (alpha {outside_top} <= 135)")
    else:
        # Tanpa bayangan: tidak ada ruang di luar panel; piksel pojok jendela
        # (di luar busur sudut) wajib benar-benar kosong.
        check(bitmap.rgba(0, 0)[3] == 0, f"tanpa bayangan: pojok kiri atas kosong (alpha {bitmap.rgba(0, 0)[3]})")
        check(bitmap.rgba(bitmap.width - 1, 0)[3] == 0, "tanpa bayangan: pojok kanan atas kosong")
        check(bitmap.rgba(0, bitmap.height - 1)[3] == 0, "tanpa bayangan: pojok kiri bawah kosong")
        check(bitmap.rgba(bitmap.width - 1, bitmap.height - 1)[3] == 0, "tanpa bayangan: pojok kanan bawah kosong")

    # Titik di padding kiri (bukan di dalam kartu/bidang isi) untuk warna latar.
    pad_x = panel_x + 10
    pad = bitmap.rgba(pad_x, mid_y)
    check(pad[3] == 255, f"bagian dalam panel opak (alpha {pad[3]})")
    check(
        tuple(pad[:3]) == background,
        f"padding panel berwarna latar {background} (terukur {tuple(pad[:3])})",
    )
    # Bidang isi: sampel di baris kartu teratas (konten halaman Status),
    # bukan di tengah panel yang memang boleh kosong.
    card = bitmap.rgba(mid_x, panel_y + panel_h // 5)
    check(card[3] == 255, f"bidang isi di kartu atas opak (alpha {card[3]})")
    check(
        tuple(card[:3]) != background,
        f"bidang isi terpisah dari latar (terukur {tuple(card[:3])}, latar {background})",
    )

    # ── 3. Bayangan memudar dan berhenti (hanya bila margin > 0) ──
    if margin > 0:
        fade = [bitmap.rgba(mid_x, panel_y - distance)[3] for distance in range(1, margin - 1)]
        check(fade[0] > 40, f"bayangan terlihat tepat di sisi panel (alpha {fade[0]} > 40)")
        check(
            all(fade[i] >= fade[i + 1] - 2 for i in range(len(fade) - 1)),
            "bayangan memudar monoton menjauh dari panel",
        )
        check(fade[-1] <= 12, f"bayangan habis di ujung margin (alpha {fade[-1]} <= 12)")
        check(bitmap.rgba(0, 0)[3] == 0, "pojok terjauh jendela benar-benar kosong")

    print("\n".join(report))
    print(f"  info  radius sudut terukur: {[round(value, 1) for value in measured]}")
    if problems:
        print(f"\nBENTUK PANEL GAGAL: {len(problems)} masalah")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    if margin > 0:
        print("\nLulus: bentuk panel sesuai — sudut busur custom, tepi halus, bayangan memudar.")
    else:
        print("\nLulus: bentuk panel sesuai — sudut busur custom, tepi halus, tanpa bayangan.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
