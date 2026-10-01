#!/usr/bin/env python3
"""Penjaga ARB: kunci id/en harus sama, dan tiap kunci dipakai lewat
context.tr('kunci') atau dirujuk di strings.dart. Keluar 1 bila ada masalah."""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ARB = ROOT / "lib/l10n/arb"


def keys(name: str) -> set[str]:
    data = json.loads((ARB / name).read_text(encoding="utf-8"))
    return {k for k in data if not k.startswith("@")}


def main() -> int:
    en, idn = keys("app_en.arb"), keys("app_id.arb")
    problems: list[str] = []
    for k in sorted(en - idn):
        problems.append(f"hanya di en: {k}")
    for k in sorted(idn - en):
        problems.append(f"hanya di id: {k}")

    used: set[str] = set()
    pat = re.compile(r"""tr\(\s*['"]([A-Za-z0-9_]+)['"]""")
    for f in (ROOT / "lib").rglob("*.dart"):
        if "/l10n/" in f.as_posix() and f.name != "strings.dart":
            continue
        text = f.read_text(encoding="utf-8")
        used.update(pat.findall(text))
        used.update(re.findall(r"\bl\.([A-Za-z0-9_]+)\b", text))
    for k in sorted(en - used):
        problems.append(f"tidak terpakai: {k}")

    for p in problems:
        print(p)
    print(f"{len(en)} kunci en, {len(used & en)} terpakai, {len(problems)} masalah")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
