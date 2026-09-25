#!/usr/bin/env python3
"""Gerbang teks panel: pastikan tidak ada mojibake di dalam EXE panel.

Latar belakangnya nyata. `main.cpp` UTF-8 memuat tanda baca non-ASCII yang
tampil ke pengguna (·, —, …). MSVC membaca berkas itu sebagai codepage sistem
kalau tidak diberi `/utf-8`, jadi build Windows pernah mengirim jendela dengan
"Panel host Windows Â· tanpa terminal" dan tooltip tray "XyDesk Host â€” klik
kanan…" — sementara kompilasi mingw di Linux terlihat benar, sehingga
kesalahan ini lolos dari semua uji gambar.

Pemeriksaannya dua arah:

1. setiap teks yang diharapkan ada di dalam EXE (dibaca UTF-16, sesuai cara
   compiler menyimpan literal `L"…"`), dan
2. tidak ada penanda mojibake di string mana pun yang dibaca dari EXE —
   hanya pada rentetan karakter UTF-16 yang memang teks, bukan pada byte
   biner (ikon dan kode ikut terbaca kalau disapu mentah, dan itu pernah
   menghasilkan tuduhan palsu).

Dipanggil dari `build.yml` (job lint installer) setelah panel dikompilasi.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Teks yang HARUS utuh di dalam EXE. Sengaja memuat tanda baca non-ASCII:
# itulah bagian yang paling mudah rusak karena codepage.
EXPECTED = (
    "Panel host Windows · tanpa terminal",
    "Tutup = sembunyi ke tray, host tetap jalan. Dobel-klik judul = perbesar.",
    "Tab pindah tombol · Enter menjalankan · Esc menyembunyikan.",
    "XyDesk Host — klik kanan untuk kontrol",
    "Menyalakan host…",
    "Host aktif sebagai user Windows ini",
)

# Penanda mojibake: karakter UTF-8 yang terbaca sebagai Windows-1252.
MOJIBAKE = ("Â", "â€", "Ã©", "Ã¨", "Ã·")

# Rentetan UTF-16 dianggap teks bila setiap unitnya karakter yang masuk akal
# untuk teks antarmuka: ASCII tercetak, Latin-1 tambahan, atau tanda baca
# tipografis. Panjang minimum 6 huruf supaya derau biner tidak lolos.
_TEXT_RANGES = ((0x20, 0x7E), (0x2010, 0x203A), (0x00A0, 0x024F))
MIN_RUN = 6


def is_text_unit(unit: int) -> bool:
    if unit == 0x000A or unit == 0x0009:
        return True
    return any(low <= unit <= high for low, high in _TEXT_RANGES)


def text_runs(data: bytes) -> list[str]:
    """Kumpulkan rentetan UTF-16LE yang benar-benar berbentuk teks."""
    units = [data[i] | (data[i + 1] << 8) for i in range(0, len(data) - 1, 2)]
    runs: list[str] = []
    current: list[str] = []
    for unit in units:
        if is_text_unit(unit):
            current.append(chr(unit))
            continue
        if len(current) >= MIN_RUN:
            runs.append("".join(current))
        current = []
    if len(current) >= MIN_RUN:
        runs.append("".join(current))
    return runs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("executable", type=Path, help="XyDesk Control Panel.exe hasil build")
    args = parser.parse_args()

    data = args.executable.read_bytes()
    if not data:
        print(f"TEKS PANEL GAGAL: {args.executable} kosong")
        return 1

    problems: list[str] = []

    for text in EXPECTED:
        if data.count(text.encode("utf-16-le")) == 0:
            problems.append(f"teks tidak ditemukan di EXE: {text!r}")

    runs = text_runs(data)
    for marker in MOJIBAKE:
        hits = [run for run in runs if marker in run]
        if hits:
            contoh = hits[0][:70]
            problems.append(
                f"mojibake {marker!r} di {len(hits)} teks, mis. {contoh!r} — "
                "kompilasi ulang dengan /utf-8"
            )

    if problems:
        print("TEKS PANEL GAGAL:")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print(f"Lulus: {len(EXPECTED)} teks panel utuh, {len(runs)} rentetan teks bebas mojibake.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
