#!/usr/bin/env python3
"""Gerbang probe panel XyDesk.

Membaca JSON hasil `XyDesk.exe --panel-probe` (ditulis oleh EXE yang benar-benar
dikompilasi, tanpa membuka jendela) dan memastikan:
- urutan hasil hitTest titik sampel sama persis dengan daftar --expect,
- ukuran jendela/panel sesuai kontrak desain,
- shadowMargin 0 (panel menempel tepi jendela, tanpa bayangan).
"""
import argparse
import json
import sys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", required=True)
    parser.add_argument("--expect", required=True,
                        help="Daftar target hit yang diharapkan, dipisah koma.")
    parser.add_argument("--panel-width", type=int, default=900)
    parser.add_argument("--panel-height", type=int, default=560)
    args = parser.parse_args()

    with open(args.json, "r", encoding="utf-8") as handle:
        probe = json.load(handle)

    failures = []
    expected = [item.strip() for item in args.expect.split(",") if item.strip()]
    hits = probe.get("hits")
    if hits != expected:
        failures.append(f"hits tidak sesuai:\n  dapat   : {hits}\n  harapan : {expected}")
    if probe.get("panelWidth") != args.panel_width:
        failures.append(f"panelWidth {probe.get('panelWidth')} != {args.panel_width}")
    if probe.get("panelHeight") != args.panel_height:
        failures.append(f"panelHeight {probe.get('panelHeight')} != {args.panel_height}")
    if probe.get("windowWidth") != args.panel_width or probe.get("windowHeight") != args.panel_height:
        failures.append(
            f"window {probe.get('windowWidth')}x{probe.get('windowHeight')} != "
            f"{args.panel_width}x{args.panel_height} (shadowMargin harus 0)")
    if probe.get("shadowMargin") != 0:
        failures.append(f"shadowMargin {probe.get('shadowMargin')} != 0")

    if failures:
        print("GAGAL: probe panel tidak sesuai kontrak")
        for item in failures:
            print(f"- {item}")
        return 1
    print(f"OK: probe panel cocok kontrak ({len(expected)} titik sampel)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
