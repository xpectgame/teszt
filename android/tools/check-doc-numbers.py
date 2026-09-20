#!/usr/bin/env python3
"""A dokumentációban ÁLLÍTOTT számok egyeznek-e a kóddal.

Miért kell: a README és az áruházi szöveg tele van kézzel írt számokkal — hány teszt
fut, hány recept van a bankban, hány achievement. Ezek állítások a kódról, és a kód
változik. A szám nem romlik el látványosan: a dokumentum attól kezdve magabiztosan
hazudik, és senki nem keresi, mert a mondat körülötte igaz maradt.

Ez a projektben már kétszer megtörtént: a README „75 unit teszt"-et írt, mire 237 lett,
és az áruházi szövegek karakterszámai is elcsúsztak egy mondat átírásától (azt a
`check-store-listing.py` fogja meg azóta).

Amit néz:
  - a README `:core` tesztszáma a ténylegesen megírt `@Test`-ek számával;
  - a receptbank három darabszáma a `RecipeBank.kt` tartalmával;
  - az achievementek száma az `Achievement` felsorolással.

A hibaüzenet MEGMONDJA a helyes számot, hogy a javítás egy mozdulat legyen.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
README = ROOT / 'android/README.md'
CORE_TESTS = ROOT / 'android/core/src/test/kotlin'
BANK = ROOT / 'android/core/src/main/kotlin/hu/mealpilot/core/ai/RecipeBank.kt'
ACHIEVEMENTS = ROOT / 'android/core/src/main/kotlin/hu/mealpilot/core/achievements/Achievements.kt'


def core_test_count():
    return sum(
        len(re.findall(r'^\s*@Test\b', path.read_text(encoding='utf-8'), re.M))
        for path in CORE_TESTS.rglob('*.kt')
    )


def bank_counts():
    """Hány sablon van a három listában. A listák a `val NÉV` sorokkal kezdődnek."""
    text = BANK.read_text(encoding='utf-8')
    out = {}
    names = ['BREAKFASTS', 'MAINS', 'SNACKS']
    for index, name in enumerate(names):
        start = text.index(f'val {name}: List<RecipeTemplate>')
        end = len(text)
        for other in names[index + 1:]:
            marker = f'val {other}: List<RecipeTemplate>'
            if marker in text:
                end = min(end, text.index(marker))
        # A `data class RecipeTemplate(` deklaráció a fájl VÉGÉN van, a SNACKS után:
        # azt nem szabad sablonnak számolni. Ez a szkript első változatát el is
        # rontotta — 18 helyett 19 nassolnivalót jelentett.
        out[name] = len(re.findall(r'(?<!class )\bRecipeTemplate\(', text[start:end]))
    return out


def achievement_count():
    """Az `AchievementCatalog.all` lista hossza."""
    text = ACHIEVEMENTS.read_text(encoding='utf-8')
    start = text.index('val all: List<Achievement> = listOf(')
    end = text.index('\n    )', start)
    return len(re.findall(r'^\s+Achievement\(', text[start:end], re.M))


def main():
    readme = README.read_text(encoding='utf-8')
    problems = []

    def expect(pattern, actual, what):
        match = re.search(pattern, readme)
        if not match:
            problems.append(
                f'{what}: a README-ben nincs meg a keresett mondat ({pattern!r}). '
                'Ha a szöveg szándékosan változott, igazítsd a mintát is.')
            return
        claimed = int(match.group(1))
        if claimed != actual:
            problems.append(f'{what}: a README {claimed}-t ír, a valóság {actual}.')

    expect(r'\*\*(\d+) unit teszt fut rá zölden\*\*', core_test_count(), ':core tesztszám')

    counts = bank_counts()
    expect(r'(\d+) reggelit,', counts['BREAKFASTS'], 'receptbank / reggeli')
    expect(r'(\d+) főételt', counts['MAINS'], 'receptbank / főétel')
    expect(r'(\d+) nassolnivalót', counts['SNACKS'], 'receptbank / nassolnivaló')

    # Ha ez elszáll, az is hiba: egy csendben kihagyott ellenőrzés rosszabb, mint
    # a hiányzó — azt hinnénk, néz valamit.
    achievements = achievement_count()
    expect(r'\| (\d+) achievement', achievements, 'achievementek száma')

    if problems:
        print('A dokumentáció számai nem egyeznek a kóddal:', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        print('\nÍrd át a README-ben a számot a valódira. A dokumentum addig egy '
              'ellenőrizhetetlen állítást hordoz a kódról.', file=sys.stderr)
        return 1

    print(f'Rendben: a README számai egyeznek a kóddal ({core_test_count()} :core teszt, '
          f"{counts['BREAKFASTS']}/{counts['MAINS']}/{counts['SNACKS']} recept"
          f', {achievements} achievement).')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
