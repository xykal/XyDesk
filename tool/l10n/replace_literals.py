#!/usr/bin/env python3
"""Ganti literal string Indonesia di berkas Dart dengan `context.tr('kunci')`.

Masukan: berkas JSON berisi daftar {"key", "id", "en", "files": [...]}.
Untuk tiap entri, literal `'<id>'` (boleh terpecah jadi beberapa literal
berdampingan lintas baris, gaya `'a ' 'b'`) diganti `context.tr('<key>')`,
`const ` di depan konstruktor pada baris yang sama dibuang, lalu kunci
ditambahkan ke app_en.arb dan app_id.arb. Jalankan export_arb.py sesudahnya
untuk meregenerasi tr_keys.g.dart.

Pemakaian: python3 tool/l10n/replace_literals.py tool/l10n/batches/<nama>.json
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARB = ROOT / "lib" / "l10n" / "arb"


def literal_pattern(text: str) -> re.Pattern[str]:
    # Pecahan literal `'a ' 'b'` boleh terjadi di posisi mana pun.
    body = r"(?:'\s*')?".join(re.escape(ch) for ch in text)
    return re.compile(r"(?<![\w$])'" + body + r"'")


def strip_const(line: str) -> str:
    if "context.tr(" not in line:
        return line
    return re.sub(r"\bconst (?=[A-Z_]\w*\()", "", line, count=1)


INNER_CONST = re.compile(
    r"^(\s*\w+: )((?:TextStyle|EdgeInsets\.\w+|Icon|SizedBox|Duration|BorderRadius\.\w+)\()"
)


def fix_multiline_const(src: str) -> str:
    """`const Text(` yang badannya kini memuat context.tr(): buang const-nya,
    lalu beri `const` eksplisit pada argumen konstruktor konstan di dalamnya
    supaya prefer_const_constructors tetap terpenuhi."""
    lines = src.split("\n")
    for i, line in enumerate(lines):
        if not re.search(r"\bconst (?:[A-Z_]\w*\(|\[)$", line):
            continue
        indent = len(line) - len(line.lstrip())
        block = []
        for j in range(i + 1, len(lines)):
            nxt = lines[j]
            if nxt.strip() and len(nxt) - len(nxt.lstrip()) <= indent:
                break
            block.append(j)
        if not any("context.tr(" in lines[j] for j in block):
            continue
        lines[i] = re.sub(r"\bconst ", "", line, count=1)
        for j in block:
            m = INNER_CONST.match(lines[j])
            if m and "context" not in lines[j] and "$" not in lines[j]:
                lines[j] = f"{m.group(1)}const {lines[j][len(m.group(1)):]}"
    return "\n".join(lines)


def ensure_import(path: Path, src: str) -> str:
    """Tambahkan import l10n_bridge (penyedia `context.tr`) bila belum ada."""
    if "l10n_bridge.dart" in src or "context.tr(" not in src:
        return src
    depth = len(path.relative_to(ROOT / "lib").parts) - 1
    line = f"import '{'../' * depth}core/l10n_bridge.dart';"
    imports = [i for i, l in enumerate(src.split("\n")) if l.startswith("import ")]
    lines = src.split("\n")
    lines.insert(imports[-1] + 1 if imports else 0, line)
    return "\n".join(lines)


def apply(entry: dict, sources: dict[Path, str]) -> int:
    pat = literal_pattern(entry.get("literal", entry["id"]))
    repl = f"context.tr('{entry['key']}')"
    hits = 0
    for rel in entry["files"]:
        path = ROOT / rel
        src = sources.setdefault(path, path.read_text(encoding="utf-8"))
        new, n = pat.subn(repl, src)
        if n:
            hits += n
            sources[path] = "\n".join(strip_const(l) for l in new.split("\n"))
    return hits


def load_arb(lang: str) -> dict:
    return json.loads((ARB / f"app_{lang}.arb").read_text(encoding="utf-8"))


def save_arb(lang: str, doc: dict) -> None:
    text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    (ARB / f"app_{lang}.arb").write_text(text, encoding="utf-8")


def main() -> None:
    batch = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    en, idn = load_arb("en"), load_arb("id")
    sources: dict[Path, str] = {}
    missing: list[str] = []
    for entry in batch:
        if entry["key"] in en and en[entry["key"]] != entry["en"]:
            sys.exit(f"kunci bentrok: {entry['key']}")
        if apply(entry, sources) == 0:
            missing.append(entry["key"])
        en[entry["key"]] = entry["en"]
        idn[entry["key"]] = entry["id"].replace("\\n", "\n")
    for path, src in sources.items():
        path.write_text(ensure_import(path, fix_multiline_const(src)), encoding="utf-8")
    save_arb("en", en)
    save_arb("id", idn)
    print(f"{len(batch)} kunci; {len(sources)} berkas diubah")
    if missing:
        print("tidak ditemukan di sumber:", ", ".join(missing))


if __name__ == "__main__":
    main()
