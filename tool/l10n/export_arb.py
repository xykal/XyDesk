#!/usr/bin/env python3
"""Ekspor kStrings (lib/l10n/strings.dart) ke ARB per bahasa + peta kunci.

Dipakai sekali untuk migrasi; setelah itu ARB adalah sumber kebenaran dan
skrip ini hanya untuk regenerasi `tr_keys.g.dart` dari app_en.arb.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARB = ROOT / 'lib' / 'l10n' / 'arb'
KEYS_OUT = ROOT / 'lib' / 'l10n' / 'tr_keys.g.dart'
PLACEHOLDER = re.compile(r'\{([a-zA-Z_]\w*)\}')


def parse_kstrings(src: str) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    key = None
    lines = src.splitlines()
    joined: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if re.match(r"^    '[a-z]{2}':$", line):
            line = line + ' ' + lines[i + 1].strip()
            i += 1
        joined.append(line)
        i += 1
    for line in joined:
        m = re.match(r"^  '([a-zA-Z][a-zA-Z0-9_]*)': \{$", line)
        if m:
            key = m.group(1)
            out[key] = {}
            continue
        m = re.match(r"""^    '([a-z]{2})': (?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)"),$""", line)
        if m and key:
            raw = m.group(2) if m.group(2) is not None else m.group(3)
            out[key][m.group(1)] = raw.replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n')
    return out


def write_arb(lang: str, entries: dict[str, str], template: bool) -> None:
    doc: dict[str, object] = {'@@locale': lang}
    for k, v in entries.items():
        doc[k] = v
        if template:
            ph = PLACEHOLDER.findall(v)
            if ph:
                doc['@' + k] = {'placeholders': {p: {'type': 'String'} for p in ph}}
    (ARB / f'app_{lang}.arb').write_text(json.dumps(doc, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def write_keys(en: dict[str, str]) -> None:
    lines = [
        '// GENERATED oleh tool/l10n/export_arb.py dari lib/l10n/arb/app_en.arb.',
        '// Jangan sunting manual; jalankan ulang skripnya.',
        "import 'gen/app_localizations.dart';",
        '',
        'typedef TrGetter = String Function(AppLocalizations l);',
        '',
        '/// Kunci `context.tr(...)` -> getter kelas hasil gen-l10n.',
        'final Map<String, TrGetter> trKeys = <String, TrGetter>{',
    ]
    for k, v in en.items():
        if PLACEHOLDER.search(v):
            continue
        lines.append(f"  '{k}': (l) => l.{k},")
    lines.append('};')
    KEYS_OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def main() -> None:
    ARB.mkdir(parents=True, exist_ok=True)
    if (ARB / 'app_en.arb').exists() and '--from-dart' not in sys.argv:
        en = json.loads((ARB / 'app_en.arb').read_text(encoding='utf-8'))
        write_keys({k: v for k, v in en.items() if not k.startswith('@')})
        print('tr_keys.g.dart diperbarui dari app_en.arb')
        return
    table = parse_kstrings((ROOT / 'lib' / 'l10n' / 'strings.dart').read_text(encoding='utf-8'))
    langs = sorted({l for v in table.values() for l in v})
    for lang in langs:
        entries = {k: v[lang] for k, v in table.items() if lang in v}
        write_arb(lang, entries, template=(lang == 'en'))
    en = {k: v['en'] for k, v in table.items() if 'en' in v}
    missing = [k for k, v in table.items() if 'en' not in v]
    if missing:
        sys.exit(f'kunci tanpa en: {missing}')
    write_keys(en)
    print(f'{len(table)} kunci -> {len(langs)} ARB: {", ".join(langs)}')


if __name__ == '__main__':
    main()
