#!/usr/bin/env python3
"""Minden szövegnek meg kell lennie MINDKÉT nyelven.

Miért kell: az alapértelmezett `values/strings.xml` ANGOL, a magyar a `values-hu/`
alatt él. Ha egy kulcs csak az egyikben van meg, az Android nem hibázik — csendben az
alapértelmezettet adja vissza. Vagyis egy elfelejtett magyar fordítás úgy néz ki a
magyar felhasználónál, mint egy váratlan angol mondat a magyar app közepén, és
fordítási hiba, teszt vagy lint nem szól róla.

Ez a leggyakoribb elrontási mód is egyben: új szöveget írni könnyű az egyik fájlba, és
a másikat ott hagyni. A mai bejáráson három hiba szólt pontosan erről — csak más
úton: az achievementek, a tervezés folyamatnarrációja és a folyamatüzenetek mind
magyarul jelentek meg az angol appban.

Amit néz:
  - milyen nyelvek vannak az `AppLanguage`-ben, és van-e mindhez szövegfájl;
  - minden `<string>` és `<plurals>` kulcs megvan-e mindkét fájlban;
  - nincs-e üres fordítás (a fordítatlanul hagyott, üresen kitöltött kulcs rosszabb,
    mint a hiányzó: az Android nem esik vissza az alapértelmezettre).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_LANGUAGE = ROOT / 'core/src/main/kotlin/hu/mealpilot/core/i18n/AppLanguage.kt'
RES = ROOT / 'app/src/main/res'


def expected_files() -> dict:
    """
    Melyik nyelvekhez KELL szövegfájl.

    Nem beégetve, hanem az `AppLanguage`-ből: így egy harmadik nyelv felvétele nem
    tud átcsúszni úgy, hogy az erőforrásfájlja lemarad. A szabály, hogy az ANGOL az
    alapértelmezett `values/`-ban él (arra esik vissza az Android, ha nem talál
    mást), a többi nyelv pedig a saját `values-<tag>/` könyvtárában.
    """
    source = APP_LANGUAGE.read_text(encoding='utf-8')
    tags = re.findall(r'^\s*[A-Z_]+\("([a-z]{2})",', source, re.M)
    if not tags:
        sys.exit(f'Nem találom a nyelvek listáját itt: {APP_LANGUAGE}')
    files = {}
    for tag in tags:
        directory = 'values' if tag == 'en' else f'values-{tag}'
        files[f'{directory}/strings.xml'] = RES / directory / 'strings.xml'
    return files


FILES = expected_files()

STRING = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
PLURALS = re.compile(r'<plurals name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)


def read(path: Path) -> tuple[dict, dict]:
    if not path.exists():
        sys.exit(f'Nem találom: {path}')
    xml = path.read_text(encoding='utf-8')
    strings = {}
    for m in STRING.finditer(xml):
        # A `translatable="false"` kulcsot szándékosan nem fordítjuk.
        if 'translatable="false"' in m.group(2):
            continue
        strings[m.group(1)] = m.group(3).strip()
    plurals = {m.group(1): m.group(2).strip() for m in PLURALS.finditer(xml)}
    return strings, plurals


def main() -> int:
    parsed = {name: read(path) for name, path in FILES.items()}
    if len(FILES) != 2:
        # Ha egyszer lesz harmadik nyelv, ez a szkript összehasonlítási rendje
        # (alap ↔ másik) már nem elég. Inkább álljunk meg, mint hogy csendben
        # csak kettőt nézzen.
        sys.exit(
            f'{len(FILES)} nyelv van az AppLanguage-ben; a szkript kettőre készült. '
            'Bővítsd ki, mielőtt a harmadik nyelv kimegy.'
        )
    (base_name, other_name) = list(FILES)
    base_strings, base_plurals = parsed[base_name]
    other_strings, other_plurals = parsed[other_name]

    problems = []

    for kind, base, other in (
        ('string', base_strings, other_strings),
        ('plurals', base_plurals, other_plurals),
    ):
        for key in sorted(set(base) - set(other)):
            problems.append(f'{kind}/{key}: megvan a {base_name}-ben, hiányzik a {other_name}-ből')
        for key in sorted(set(other) - set(base)):
            problems.append(f'{kind}/{key}: megvan a {other_name}-ben, hiányzik a {base_name}-ből')

    for name, (strings, _) in parsed.items():
        for key, value in sorted(strings.items()):
            if not value:
                problems.append(f'string/{key}: üres szöveg a {name}-ben')

    if problems:
        print('Hiányzó vagy üres fordítás:\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        print(
            '\nAz Android a hiányzó kulcsnál CSENDBEN az alapértelmezett (angol)\n'
            'szöveget adja vissza. A magyar felhasználó tehát egy angol mondatot lát a\n'
            'magyar app közepén, és semmi nem szól róla — se fordító, se lint, se teszt.',
            file=sys.stderr,
        )
        return 1

    print(
        f'Rendben: mind a {len(base_strings)} szöveg és {len(base_plurals)} darabszámos '
        'alak megvan mindkét nyelven, üres fordítás nélkül.'
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
