#!/usr/bin/env python3
"""Tényleg lefutott-e minden teszt, amit megírtunk.

Miért kell: a Gradle `test` feladata ZÖLD, ha egyetlen tesztet sem talál. Ha egy
forráskönyvtár elmozdul, egy változat átnevezésre kerül, vagy a feladat kimarad a
munkafolyamatból, a CI továbbra is sikert jelent — csak éppen semmit nem bizonyít.

Ez nem elméleti veszély ebben a projektben: az :app tesztjei sokáig NEM futottak a
CI-ban, és a lépés saját megjegyzése mondja ki, hogy addig „a CI csak azt bizonyította,
hogy a kód lefordul". Három hiba élt akkor ott, ahová a tesztek nem értek el.

Ez a szkript a MEGÍRT tesztek számát veti össze a TÉNYLEG lefuttatottal, a Gradle
JUnit XML-jeiből. A két számnak egyeznie kell.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# modul → (tesztforrások, a Gradle eredménygyökere)
#
# A gyökeret nézzük, nem a feladat nevét (`test`, `testDebugUnitTest`): egy feladat
# átnevezése ne HAMIS riasztás legyen, csak a tesztek tényleges elmaradása.
MODULES = {
    ':core': (ROOT / 'core/src/test', ROOT / 'core/build/test-results'),
    ':app': (ROOT / 'app/src/test', ROOT / 'app/build/test-results'),
}

TEST_ANNOTATION = re.compile(r'^\s*@Test\b', re.M)
SUITE = re.compile(r'<testsuite\b[^>]*>')
ATTR = re.compile(r'(\w+)="([^"]*)"')


def declared(source: Path) -> int:
    return sum(
        len(TEST_ANNOTATION.findall(kt.read_text(encoding='utf-8')))
        for kt in source.rglob('*.kt')
    )


def executed(results: Path) -> tuple[int, int]:
    """(lefuttatott, kihagyott) a JUnit XML-ekből."""
    total = skipped = 0
    for xml in sorted(results.rglob('*.xml')):
        m = SUITE.search(xml.read_text(encoding='utf-8'))
        if not m:
            continue
        attrs = dict(ATTR.findall(m.group(0)))
        total += int(attrs.get('tests', 0))
        skipped += int(attrs.get('skipped', 0))
    return total, skipped


def main() -> int:
    problems = []
    summary = []
    for module, (source, results) in MODULES.items():
        if not source.exists():
            problems.append(f'{module}: nincs tesztforrás itt: {source.relative_to(ROOT)}')
            continue
        want = declared(source)
        if not results.exists():
            # Ha a hiba mégis a mi feltevésünk (a könyvtár helye), azt egy körben
            # lehessen javítani: kiírjuk, mi VAN a modul build könyvtárában.
            build = results.parent
            found = (
                sorted(str(p.relative_to(ROOT)) for p in build.glob('*test*'))
                if build.exists() else []
            )
            problems.append(
                f'{module}: {want} tesztet írtunk, de nincs eredménykönyvtár '
                f'({results.relative_to(ROOT)}) — a tesztek EL SEM INDULTAK'
                + (f'; a build alatt ezt találtam: {", ".join(found)}' if found
                   else '; a build könyvtár is üres vagy hiányzik')
            )
            continue
        ran, skipped = executed(results)
        if ran < want:
            problems.append(
                f'{module}: {want} tesztet írtunk, {ran} futott le'
                + (f' ({skipped} kihagyva)' if skipped else '')
                + ' — valamelyik osztály nem jutott el a futtatóig'
            )
        elif skipped:
            problems.append(f'{module}: {skipped} teszt kihagyva — a kihagyott teszt nem bizonyít semmit')
        else:
            summary.append(f'{module}: {ran}')

    if problems:
        print('A tesztek nem futottak le mind:\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        print(
            '\nA Gradle `test` feladata akkor is ZÖLD, ha egyetlen tesztet sem talál.\n'
            'Ha egy forráskönyvtár elmozdult vagy a feladat kimaradt, a CI sikert jelent,\n'
            'miközben semmit nem bizonyít.',
            file=sys.stderr,
        )
        return 1

    print('Rendben: minden megírt teszt lefutott — ' + ', '.join(summary) + '.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
