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

Három dolgot néz:

1. a darabszám-korlátok egyeznek-e a kliens és a szerver között;
2. a `wrangler.toml` tokenplafonjai egyeznek-e a `limits.ts` alapértékeivel — mert a
   telepített érték felülírja a kódot, és a kódban álló szám (meg a mellette álló
   költségbecslés) így soha nem futott, mégis annak tűnt;
3. a KIADÁSI DOKUMENTUMOK ugyanazokat a számokat írják-e. A `LAUNCH-CHECKLIST.md` és a
   `MONETIZATION.md` a két fájl, amiből az árazás és a zárt teszt tervezhető — amíg
   „havi 1 étrend, 10 üzenet" állt bennük, a valóság 3 étrend és 20 üzenet volt,
   egyszeri kerettel.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KT = ROOT / 'android/core/src/main/kotlin/hu/mealpilot/core/billing/Tiers.kt'
TS = ROOT / 'backend/src/limits.ts'
WRANGLER = ROOT / 'backend/wrangler.toml'
CHECKLIST = ROOT / 'docs/LAUNCH-CHECKLIST.md'
MONETIZATION = ROOT / 'docs/MONETIZATION.md'

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
    # Az OWNER is kell: a tokenplafonját ugyanúgy felülírja a wrangler.toml.
    for tier in ('FREE', 'PREMIUM', 'OWNER'):
        t = re.search(rf'\n  {tier}:\s*\{{(.*?)\n  \}}', body, re.S)
        if not t:
            sys.exit(f'Nem találom a(z) {tier} csomagot itt: {TS}')
        # A számokban lehet aláhúzásos tagolás (60_000), azt kivesszük.
        out[tier] = {
            k: v.replace('_', '')
            for k, v in re.findall(r'(\w+)\s*:\s*(-?[\w_]+)', t.group(1))
        }
    return out


def token_caps(text: str) -> dict[str, str]:
    """A wrangler.toml-ben beállított kimeneti tokenplafonok."""
    return {
        tier: value
        for tier, value in re.findall(r'(FREE|PREMIUM|OWNER)_OUTPUT_TOKEN_CAP\s*=\s*"(\d+)"', text)
    }


def documented_free_tier(path: Path) -> tuple[str, str, str]:
    """A dokumentumban leírt ingyenes korlátok: étrend, üzenet, terv hossza."""
    text = path.read_text(encoding='utf-8')
    m = re.search(r'\*\*?(\d+)\*\*? étrend', text) or re.search(r'(\d+) étrend', text)
    n = re.search(r'\*\*?(\d+)\*\*? üzenet', text) or re.search(r'(\d+) üzenet', text)
    d = re.search(r'\*\*?(\d+) napos\*\*?', text) or re.search(r'(\d+) napos terv', text)
    if not (m and n and d):
        sys.exit(
            f'Nem találom az ingyenes sáv számait itt: {path}\n'
            'A mondatnak tartalmaznia kell, hogy hány étrend, hány üzenet és hány napos terv jár.'
        )
    return m.group(1), n.group(1), d.group(1)


def main() -> int:
    kt = kotlin_tiers(KT.read_text(encoding='utf-8'))
    ts = typescript_tiers(TS.read_text(encoding='utf-8'))
    caps = token_caps(WRANGLER.read_text(encoding='utf-8'))

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

    # A telepített tokenplafon felülírja a kódban állót, tehát a kettőnek egyeznie kell:
    # különben a kódban lévő szám és a mellette álló költségbecslés sosem fut le.
    for tier in ('FREE', 'PREMIUM', 'OWNER'):
        deployed = caps.get(tier)
        in_code = ts.get(tier, {}).get('outputTokenCap')
        if deployed is None:
            problems.append(f'{tier}_OUTPUT_TOKEN_CAP: hiányzik a {WRANGLER.name}-ből')
        elif in_code is None:
            problems.append(f'{tier}.outputTokenCap: hiányzik a {TS.name}-ből')
        elif deployed != in_code:
            problems.append(
                f'{tier}.outputTokenCap: a kódban {in_code}, a telepítésben {deployed} — '
                'a telepített érték nyer, a kódban álló szám sosem fut'
            )

    # A kiadási dokumentumok ugyanazt írják-e, mint a kód.
    for doc in (CHECKLIST, MONETIZATION):
        plans, messages, days = documented_free_tier(doc)
        for name, documented, actual in (
            ('étrend', plans, kt['FREE']['aiPlans']),
            ('üzenet', messages, kt['FREE']['chatMessages']),
            ('terv hossza', days, kt['FREE']['maxPlanDays']),
        ):
            if documented != actual:
                problems.append(
                    f'{doc.name}: az ingyenes sáv „{name}" értéke {documented}, '
                    f'a kódban {actual} — ebből a fájlból tervezed az árazást'
                )

    if problems:
        print('A csomagkorlátok szétcsúsztak:\n')
        for p in problems:
            print(f'  {p}')
        print(f'\n{len(problems)} eltérés. A döntést a szerver hozza, a kliens csak kiír —')
        print('de a kért terv hosszát a kliens vágja le, tehát az eltérés kérésenként hibát ad.')
        print('A dokumentumokból pedig az árazás és a zárt teszt tervezhető: ott a rossz')
        print('szám nem hibaüzenet, hanem rossz üzleti döntés.')
        return 1

    checked = len(FIELDS) * 2
    print(
        f'Rendben: {checked} csomagkorlát egyezik a kliens és a szerver között, '
        f'{len(caps)} tokenplafon a kóddal, és a két kiadási dokumentum is ugyanazt írja.'
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
