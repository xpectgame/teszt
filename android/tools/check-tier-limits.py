#!/usr/bin/env python3
"""A csomagkorlátok egyezésének ellenőrzése a kliens és a szerver között.

A számok KÉT helyen élnek: a kliensben (`core/billing/Tiers.kt`) és a szerveren
(`backend/src/limits.ts`). Mindkét fájl kommentje azt írja, hogy a másikkal egyeznie
kell — de eddig semmi nem ellenőrizte. A rendszerpromptoknál van erre őr
(SystemPromptSyncTest), a korlátoknál nem volt.

A döntést a szerver hozza; a kliens ugyanezekből írja ki, mit kap a felhasználó, és
ebből vágja le a kért terv hosszát is. Ha a kettő szétcsúszik, a felhasználó olyat kér,
amit a kliens megenged és a szerver elutasít — minden egyes alkalommal, érthetetlen
hibával. Ez pont az a fajta hiba, amit senki nem keres a saját kódjában, mert
mindkét oldal magában helyes.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KT = ROOT / 'android/core/src/main/kotlin/hu/mealpilot/core/billing/Tiers.kt'
TS = ROOT / 'backend/src/limits.ts'

# Amit összevetünk. A kliens nem ismeri a tokenplafont (nem is kell neki: az a
# szerver kemény korlátja), ezért az kimarad.
FIELDS = ('aiPlans', 'chatMessages', 'maxPlanDays', 'canRefineDays')


def kotlin_tiers(text: str) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    for tier in ('FREE', 'PREMIUM'):
        m = re.search(rf'val {tier} = TierLimits\((.*?)\n    \)', text, re.S)
        if not m:
            sys.exit(f'Nem találom a(z) {tier} csomagot itt: {KT}')
        out[tier] = dict(re.findall(r'(\w+)\s*=\s*(-?\w+)', m.group(1)))
    return out


def typescript_tiers(text: str) -> dict[str, dict[str, str]]:
    m = re.search(r'DEFAULT_LIMITS:\s*Record<Tier, TierLimits>\s*=\s*\{(.*?)\n\}', text, re.S)
    if not m:
        sys.exit(f'Nem találom a DEFAULT_LIMITS blokkot itt: {TS}')
    body = m.group(1)
    out: dict[str, dict[str, str]] = {}
    for tier in ('FREE', 'PREMIUM'):
        t = re.search(rf'\n  {tier}:\s*\{{(.*?)\n  \}}', body, re.S)
        if not t:
            sys.exit(f'Nem találom a(z) {tier} csomagot itt: {TS}')
        # A számokban lehet aláhúzásos tagolás (60_000), azt kivesszük.
        out[tier] = {
            k: v.replace('_', '')
            for k, v in re.findall(r'(\w+)\s*:\s*(-?[\w_]+)', t.group(1))
        }
    return out


def main() -> int:
    kt = kotlin_tiers(KT.read_text(encoding='utf-8'))
    ts = typescript_tiers(TS.read_text(encoding='utf-8'))

    problems = []
    for tier in ('FREE', 'PREMIUM'):
        for field in FIELDS:
            client = kt[tier].get(field)
            server = ts[tier].get(field)
            if client is None:
                problems.append(f'{tier}.{field}: hiányzik a kliensből ({KT.name})')
            elif server is None:
                problems.append(f'{tier}.{field}: hiányzik a szerverből ({TS.name})')
            elif client != server:
                problems.append(
                    f'{tier}.{field}: a kliens {client}, a szerver {server} — '
                    'a felhasználó mást látna, mint amit kap'
                )

    if problems:
        print('A csomagkorlátok szétcsúsztak a kliens és a szerver között:\n')
        for p in problems:
            print(f'  {p}')
        print(f'\n{len(problems)} eltérés. A döntést a szerver hozza, a kliens csak kiír —')
        print('de a kért terv hosszát a kliens vágja le, tehát az eltérés kérésenként hibát ad.')
        return 1

    checked = len(FIELDS) * 2
    print(f'Rendben: {checked} csomagkorlát egyezik a kliens és a szerver között.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
