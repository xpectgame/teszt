#!/usr/bin/env python3
"""Beégetett magyar szövegeket keres az :app modulban.

Miért kell: az app két nyelven fut, és a felületi szövegek az erőforrásokból
jönnek. A HÁTTÉRBEN futó részek — a tervezés folyamatjelzője, az értesítések, a
visszaadott hibaüzenetek — viszont könnyen elkerülik ezt, mert ott nincs
`stringResource`, és egy beégetett magyar mondat magyar fejlesztői gépen
tökéletesen működik. A tervezés teljes folyamatnarrációja („Összeállítom az
étrended", „Utolsó simítások…") így maradt magyar az angol appban is.

Amit keres:

1. magyar ékezetes karaktert tartalmazó szöveg-literál;
2. ékezet NÉLKÜLI magyar mondat, egy szűk gyakorisági szólista alapján.

A második azért kell, mert az első átengedte a „Dolgozom rajta" feliratot — a
tervezés folyamatjelzőjének fejlécét a beszélgetésből indított műveleteknél. Egyetlen
ékezet sincs benne, tehát a karakteralapú szűrő vak volt rá, és angol appban is
magyarul jelent meg. A szólista szándékosan rövid és egyértelmű: olyan szavak, amik
angol felületi szövegben gyakorlatilag nem fordulnak elő.

Amit NEM jelez:
  - megjegyzések és naplóüzenetek (`Log.d/i/w/e`) — ezek fejlesztőnek szólnak;
  - a lenti, indoklással felvett fájlok.
"""
import os
import re
import sys

HUNGARIAN = set("áéíóöőúüűÁÉÍÓÖŐÚÜŰ")

# Ékezet nélküli magyar szavak, amik angol szövegben nem fordulnak elő. Kettő együtt
# már mondatot jelez. Szándékosan NINCS benne az „a", „is", „meg", „van", „de", „ha":
# ezek angolul (vagy más nyelven) is szavak, és téves riasztást adnának.
HUNGARIAN_WORDS = {
    'dolgozom', 'rajta', 'nincs', 'hogy', 'kesz', 'kell', 'lehet', 'majd', 'most',
    'volt', 'lesz', 'vagy', 'igen', 'nem', 'az', 'egy', 'mit', 'ezt', 'azt', 'mert',
    'ezek', 'ilyen', 'sem', 'mar', 'nagyon', 'tovabb', 'vissza', 'mentes',
}
WORD = re.compile(r"[A-Za-zÁÉÍÓÖŐÚÜŰáéíóöőúüű]+")


def looks_hungarian(text: str) -> bool:
    """Ékezet nélküli magyar mondat: legalább két egyértelmű magyar szó."""
    if len(text) < 4:
        return False
    words = {m.group(0).lower() for m in WORD.finditer(text)}
    return len(words & HUNGARIAN_WORDS) >= 2

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java")

# Fájl → miért szabad benne magyar szöveg. Minden tételnek indokkal kell jönnie.
ALLOWED = {
    "data/ai/OfflineMealAi.kt":
        "A receptbank tartalom, nem felirat: minden szövege `Text(magyar, angol)` "
        "párként áll, tehát a magyar fél mellett mindig ott az angol.",
    "data/repo/ChatRepository.kt":
        "A beszélgetés KONTEXTUSA a modellnek megy, a terv nyelvén — minden ág "
        "mellett ott az angol párja.",
    "AppContainer.kt":
        "A tartalék tervező szövegei, `if (english)` ágakkal.",
    "ui/screens/LanguageScreen.kt":
        "A nyelvválasztó a nyelv kiválasztása ELŐTT jelenik meg, ezért "
        "szándékosan kétnyelvű.",
    "ui/screens/SettingsScreen.kt":
        "A rejtett fejlesztői rész. Csak a fejlesztő látja, nem kerül kiadásba.",
    "data/prefs/SettingsRepository.kt":
        "A modellválasztó címkéi. Csak a rejtett fejlesztői részben jelennek meg "
        "(SettingsScreen, `if (current.developerMode)`).",
    "data/repo/PlanRepository.kt":
        "A tervgenerálás szövegei, `if (language == AppLanguage.EN)` ágakkal.",
}

LOG_CALL = re.compile(r"\bLog\.[dviwe]\s*\(")
STRING = re.compile(r'"([^"\\\n]*(?:\\.[^"\\\n]*)*)"')


def relative(path: str) -> str:
    marker = "hu/mealpilot/app/"
    index = path.replace(os.sep, "/").find(marker)
    return path.replace(os.sep, "/")[index + len(marker):] if index >= 0 else path


def scan(path: str) -> list:
    hits = []
    in_block_comment = False
    with open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            stripped = line.strip()
            if in_block_comment:
                if "*/" in stripped:
                    in_block_comment = False
                continue
            if stripped.startswith("/*"):
                if "*/" not in stripped:
                    in_block_comment = True
                continue
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if LOG_CALL.search(line):
                continue
            for match in STRING.finditer(line):
                text = match.group(1)
                if any(character in HUNGARIAN for character in text) or looks_hungarian(text):
                    hits.append((number, text[:70]))
    return hits


def main() -> int:
    problems = []
    scanned = 0
    for directory, _, files in os.walk(ROOT):
        for name in sorted(files):
            if not name.endswith(".kt"):
                continue
            path = os.path.join(directory, name)
            key = relative(path)
            if key in ALLOWED:
                continue
            scanned += 1
            for number, text in scan(path):
                problems.append(f"{key}:{number}: „{text}”")

    if problems:
        print("Beégetett magyar szöveg az :app modulban:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        print(
            "\nEzek a szövegek az angol appban is magyarul jelennének meg.\n"
            "Tedd őket erőforrásba (values/ és values-hu/), és olvasd ki\n"
            "`stringResource`-szal vagy `AppStrings`-gel. Ha a szöveg tényleg\n"
            "magyar kell legyen, vedd fel a szkript ALLOWED listájára — indoklással.",
            file=sys.stderr,
        )
        return 1

    print(f"Rendben: {scanned} fájlban nincs beégetett magyar szöveg "
          f"({len(ALLOWED)} fájl indoklással kivéve).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
