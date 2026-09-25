#!/usr/bin/env python3
"""Periksa bentuk panel native XyDesk dari berkas snapshot 32-bit.

`XyDesk Control Panel.exe --panel-snapshot <berkas.bmp>` menggambar panel apa
adanya (jalur gambar yang sama dengan yang dipakai jendela berlapis) ke berkas
32-bit. Skrip ini membaca pikselnya dan membuktikan hal-hal yang tidak bisa
dibuktikan oleh uji angka biasa:

1. sudut panel benar-benar dipotong sebagai busur radius yang diminta —
   bukan kotak, dan bukan potongan kotak seperti `SetWindowRgn`,
2. busurnya dihaluskan (ada piksel dengan cakupan sebagian di tepi busur),
3. TIDAK ada bayangan luar: semua piksel di luar bentuk tembus pandang penuh
   (permintaan pemilik: panel bersih tanpa bayangan),
4. tepi lurus tetap tajam, bagian dalam opak, dan sidebar punya warna bidang
   sendiri yang berbeda dari daerah isi.

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
    parser.add_argument("--panel-width", type=int, default=960)
    parser.add_argument("--panel-height", type=int, default=600)
    parser.add_argument("--margin", type=int, default=2, help="margin tepi halus (default 2, tanpa bayangan)")
    parser.add_argument("--radius", type=int, default=16, help="radius sudut (default 16)")
    parser.add_argument("--sidebar-width", type=int, default=208)
    parser.add_argument("--background", default="14,16,22", help="warna daerah isi 'R,G,B' (default 14,16,22)")
    parser.add_argument("--sidebar", default="24,27,36", help="warna bidang sidebar 'R,G,B' (default 24,27,36)")
    args = parser.parse_args()

    bitmap = Bitmap(args.bitmap)
    margin = args.margin
    panel_x, panel_y = margin, margin
    panel_w, panel_h = args.panel_width, args.panel_height
    radius = args.radius
    background = tuple(int(part) for part in args.background.split(","))
    sidebar = tuple(int(part) for part in args.sidebar.split(","))

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
    corners = [
        ("kiri atas", (panel_x + radius, panel_y + radius), (-1, -1)),
        ("kanan atas", (panel_x + panel_w - radius, panel_y + radius), (1, -1)),
        ("kiri bawah", (panel_x + radius, panel_y + panel_h - radius), (-1, 1)),
        ("kanan bawah", (panel_x + panel_w - radius, panel_y + panel_h - radius), (1, 1)),
    ]
    measured: list[float] = []
    smooth_evidence: list[int] = []
    for name, (cx, cy), (dx, dy) in corners:
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
        # Tanpa bayangan, alpha parsial murni dari cakupan busur: nilainya di
        # antara 0 dan 255 (bukan salah satu ekstrem) — itulah tepi halus.
        check(
            len(partial) >= 1 and 20 <= partial[0] <= 240,
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
        check(alpha == 0, f"pojok {name} tembus pandang penuh (alpha {alpha} = 0, bukan kotak)")

    # ── 2. Tanpa bayangan: luar bentuk kosong sama sekali ──
    mid_x = panel_x + panel_w // 2
    mid_y = panel_y + panel_h // 2
    for distance in (1, 2):
        outside = bitmap.rgba(mid_x, panel_y - distance)[3]
        check(outside == 0, f"tanpa bayangan: {distance} px di atas panel kosong (alpha {outside} = 0)")
    outside_side = bitmap.rgba(panel_x - 1, mid_y)[3]
    check(outside_side == 0, f"tanpa bayangan: 1 px di kiri panel kosong (alpha {outside_side} = 0)")
    check(bitmap.rgba(0, 0)[3] == 0, "pojok terjauh jendela benar-benar kosong")

    # ── 3. Tepi lurus tajam, isi opak, sidebar beda bidang ──
    inside_top = bitmap.rgba(mid_x, panel_y + 1)[3]
    check(inside_top == 255, f"tepi atas: piksel pertama di dalam panel opak (alpha {inside_top})")

    side = bitmap.rgba(panel_x + 10, mid_y)
    check(side[3] == 255, f"sidebar opak (alpha {side[3]})")
    check(
        tuple(side[:3]) == sidebar,
        f"sidebar berwarna bidangnya {sidebar} (terukur {tuple(side[:3])})",
    )
    gap_y = panel_y + 244  # celah antar kartu identitas di bagian Status
    gap = bitmap.rgba(mid_x, gap_y)
    check(
        tuple(gap[:3]) == background,
        f"celah daerah isi berwarna latar {background} (terukur {tuple(gap[:3])})",
    )
    check(
        tuple(side[:3]) != tuple(gap[:3]),
        "sidebar dan daerah isi dua bidang berbeda",
    )
    card = bitmap.rgba(mid_x, mid_y)
    check(card[3] == 255, f"bidang isi di tengah panel opak (alpha {card[3]})")
    check(
        tuple(card[:3]) != background,
        f"kartu isi terpisah dari latar (terukur {tuple(card[:3])}, latar {background})",
    )

    print("\n".join(report))
    print(f"  info  radius sudut terukur: {[round(value, 1) for value in measured]}")
    if problems:
        print(f"\nBENTUK PANEL GAGAL: {len(problems)} masalah")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("\nLulus: bentuk panel sesuai — sudut busur custom, tepi halus, tanpa bayangan.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
