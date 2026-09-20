#!/usr/bin/env python3
"""A Play áruházi szövegek karakterszámai egyeznek-e a valósággal.

Miért kell: a `STORE-LISTING.md` minden blokkja alatt ott a hossza és a Play limitje
(„*2 503 karakter — a limit 4 000*"). A Play a hosszakat szigorúan veszi: ami nem fér
bele, azt a feltöltésnél utasítja vissza — nem a szerkesztésnél, hanem akkor, amikor
a kiadás már indulna.

A szám kézzel írt állítás egy szövegről, ami mellette áll. Egyetlen mondat átírása
elrontja, és semmi nem jelzi: a dokumentum attól kezdve magabiztosan hazudik. Pont
ez történt, amikor az „alapanyagcsere alá nem enged" mondat kikerült belőle.

Amit néz, minden ```-blokkra, ami alatt karakterszámos sor áll:
  - a kiírt karakterszám megegyezik-e a blokk tényleges hosszával;
  - és belefér-e a mellé írt limitbe.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LISTING = ROOT / 'docs/STORE-LISTING.md'

# „*2 503 karakter — a limit 4 000*" — a számokban nem törhető szóköz is lehet.
NOTE = re.compile(r'^\*([\d\s  ]+) karakter — a limit ([\d\s  ]+)\*$')


def number(text):
    return int(re.sub(r'[\s  ]', '', text))


def blocks(lines):
    """(kezdősor, hossz, kiírt hossz, limit) minden karakterszámmal jelölt blokkra."""
    i = 0
    while i < len(lines):
        if lines[i].strip() != '```':
            i += 1
            continue
        j = i + 1
        while j < len(lines) and lines[j].strip() != '```':
            j += 1
        if j >= len(lines):
            break
        note = NOTE.match(lines[j + 1].strip()) if j + 1 < len(lines) else None
        if note:
            body = '\n'.join(lines[i + 1:j])
            yield i + 2, len(body), number(note.group(1)), number(note.group(2))
        i = j + 1


def main():
    if not LISTING.exists():
        print(f'Nincs meg: {LISTING}', file=sys.stderr)
        return 1

    lines = LISTING.read_text(encoding='utf-8').split('\n')
    found = list(blocks(lines))
    if not found:
        print('A STORE-LISTING.md-ben egyetlen karakterszámmal jelölt blokk sincs — '
              'vagy a jelölés formátuma változott meg.', file=sys.stderr)
        return 1

    problems = []
    for line, actual, claimed, limit in found:
        if actual != claimed:
            problems.append(
                f'{LISTING.name}:{line} — a blokk {actual} karakter, '
                f'de {claimed} van alá írva.')
        if actual > limit:
            problems.append(
                f'{LISTING.name}:{line} — a blokk {actual} karakter, '
                f'a Play limitje {limit}. Ezt a Play visszautasítja.')

    if problems:
        print('Elcsúszott áruházi szöveghossz:', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        print('\nÍrd át a blokk alatti karakterszámot a valódira, vagy rövidítsd a szöveget.',
              file=sys.stderr)
        return 1

    print(f'Rendben: mind a {len(found)} áruházi szövegblokk hossza egyezik a kiírt '
          'karakterszámmal, és belefér a Play limitjébe.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
