#!/usr/bin/env python3
"""A jogi szövegek verziója egyezik-e azzal, ami az oldalakon áll.

Miért kell: a `LegalLinks.VERSION` dönti el, hogy a felhasználónak újra el kell-e
fogadnia a feltételeket. Ha a szöveg megváltozik, de a verzió marad, akkor a
Beállítások jogi szakasza továbbra is azt írja ki, hogy „elfogadva ekkor és ekkor" —
egy olyan szövegre, amit a felhasználó soha nem látott. Vita esetén pont ez lenne az
egyetlen bizonyíték, és pont az lenne hamis.

Ez pontosan az a fajta hiba, amit senki nem keres: a `privacy.html` átírása után az
ember a szövegre figyel, nem egy Kotlin konstansra egy másik modulban.

Amit néz:
  - a magyar és az angol adatkezelési tájékoztató és felhasználási feltételek
    hatálybalépési dátuma megegyezik-e egymással;
  - és megegyezik-e a `LegalLinks.VERSION` értékével.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEGAL_LINKS = ROOT / 'android/app/src/main/java/hu/mealpilot/app/ui/screens/PaywallScreen.kt'

HU_MONTHS = {
    'január': 1, 'február': 2, 'március': 3, 'április': 4, 'május': 5, 'június': 6,
    'július': 7, 'augusztus': 8, 'szeptember': 9, 'október': 10, 'november': 11, 'december': 12,
}
EN_MONTHS = {
    'January': 1, 'February': 2, 'March': 3, 'April': 4, 'May': 5, 'June': 6,
    'July': 7, 'August': 8, 'September': 9, 'October': 10, 'November': 11, 'December': 12,
}

PAGES = {
    'mealpilot/privacy.html': 'hu',
    'mealpilot/terms.html': 'hu',
    'mealpilot/en/privacy.html': 'en',
    'mealpilot/en/terms.html': 'en',
}


def page_date(path: Path, language: str) -> str:
    text = path.read_text(encoding='utf-8')
    if language == 'hu':
        m = re.search(r'Hatályos:\s*(\d{4})\.\s*([a-záéíóöőúüű]+)\s*(\d{1,2})\.', text)
        if not m:
            sys.exit(f'Nem találom a hatálybalépési dátumot itt: {path}')
        year, month_name, day = m.group(1), m.group(2), m.group(3)
        month = HU_MONTHS.get(month_name)
        if month is None:
            sys.exit(f'Ismeretlen hónapnév ({month_name}) itt: {path}')
    else:
        m = re.search(r'In force:\s*(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})', text)
        if not m:
            sys.exit(f'Nem találom a hatálybalépési dátumot itt: {path}')
        day, month_name, year = m.group(1), m.group(2), m.group(3)
        month = EN_MONTHS.get(month_name)
        if month is None:
            sys.exit(f'Ismeretlen hónapnév ({month_name}) itt: {path}')
    return f'{int(year):04d}-{month:02d}-{int(day):02d}'


def declared_version() -> str:
    text = LEGAL_LINKS.read_text(encoding='utf-8')
    m = re.search(r'const val VERSION\s*=\s*"([^"]+)"', text)
    if not m:
        sys.exit(f'Nem találom a LegalLinks.VERSION értékét itt: {LEGAL_LINKS}')
    return m.group(1)


def main() -> int:
    version = declared_version()
    problems = []
    for relative, language in PAGES.items():
        path = ROOT / relative
        if not path.exists():
            problems.append(f'{relative}: hiányzik a fájl')
            continue
        found = page_date(path, language)
        if found != version:
            problems.append(
                f'{relative}: az oldalon {found} a hatálybalépés, a LegalLinks.VERSION viszont {version}'
            )

    if problems:
        print('A jogi szövegek verziója szétcsúszott:\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        print(
            '\nHa a szöveg érdemben változott, EMELD a LegalLinks.VERSION értékét, és írd át\n'
            'mind a négy oldal hatálybalépési dátumát ugyanarra. Ettől kér az app újra\n'
            'elfogadást — enélkül a Beállítások egy olyan szövegre írja ki, hogy „elfogadva",\n'
            'amit a felhasználó soha nem látott.\n'
            'Ha a változás csak elírás javítása volt, hagyd a verziót, és az oldalak\n'
            'dátumát se írd át.',
            file=sys.stderr,
        )
        return 1

    print(f'Rendben: mind a {len(PAGES)} jogi oldal hatálybalépése {version}, '
          'ugyanaz, mint a LegalLinks.VERSION.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
