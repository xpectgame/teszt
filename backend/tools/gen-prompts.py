#!/usr/bin/env python3
"""A rendszerpromptok átmásolása a kliensből a backendbe.

    python3 backend/tools/gen-prompts.py            # újragenerálja a backend/src/prompts.ts-t
    python3 backend/tools/gen-prompts.py --check    # csak ellenőriz, nem ír (a CI ezt futtatja)

Miért kell a `--check`: a `SystemPromptSyncTest` a Kotlin promptot egy BEMÁSOLT hashhez
hasonlítja. Ha valaki átírja a promptot, a teszt elpirul — és a leggyorsabb javítás az,
ha az új hasht bemásolja a tesztbe. Ettől mindkét tesztcsomag zölddé válik, miközben a
backend még a RÉGI promptot küldi a modellnek. A felhasználó ilyenkor mást kap, mint
amit a fejlesztő gondol, és semmi nem szól.

A `--check` ezt zárja be: a kliensből újragenerálja a fájlt a memóriában, és összeveti a
committolt `backend/src/prompts.ts`-szel meg a Kotlin-tesztbe írt hashekkel. Bármelyik
szétcsúszása hiba.
"""
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'backend/src/prompts.ts'
SYNC_TEST = ROOT / 'android/core/src/test/kotlin/hu/mealpilot/core/SystemPromptSyncTest.kt'

PLAN_KT = 'android/core/src/main/kotlin/hu/mealpilot/core/ai/PlanPrompts.kt'
CHAT_KT = 'android/core/src/main/kotlin/hu/mealpilot/core/ai/ChatPrompts.kt'
EST_KT = 'android/core/src/main/kotlin/hu/mealpilot/core/ai/EstimatePrompts.kt'

SOURCES = (
    ('plan', PLAN_KT, 'SYSTEM'),
    ('chat', CHAT_KT, 'SYSTEM'),
    ('estimate', EST_KT, 'SYSTEM'),
    ('plan_en', PLAN_KT, 'SYSTEM_EN'),
    ('chat_en', CHAT_KT, 'SYSTEM_EN'),
    ('estimate_en', EST_KT, 'SYSTEM_EN'),
)


def extract(path, name='SYSTEM'):
    src = (ROOT / path).read_text(encoding='utf-8')
    m = re.search(r'val %s: String = """\n(.*?)\n""".trimIndent\(\)' % name, src, re.S)
    if not m:
        sys.exit(f"nem talalom a {name} promptot: {path}")
    text = m.group(1)
    # trimIndent: kozos behuzas levagasa (itt 0, de legyunk pontosak)
    lines = text.split('\n')
    indents = [len(l) - len(l.lstrip()) for l in lines if l.strip()]
    common = min(indents) if indents else 0
    return '\n'.join(l[common:] if l.strip() else '' for l in lines)


def sha(s):
    return hashlib.sha256(s.encode('utf-8')).hexdigest()


def render(prompts):
    parts = ['''/**
 * A rendszerpromptok a SZERVEREN élnek, nem a kliensen.
 *
 * Ez nem kényelmi kérdés: a kliens csak az adatokat tartalmazó felhasználói üzenetet
 * küldi be, a modell viselkedését ez a fájl szabja meg. Így egy visszafejtett vagy
 * módosított app sem tudja a hívást általános célú asszisztenssé alakítani a te
 * Anthropic-kulcsodon — a válasz mindig étrend-JSON lesz.
 *
 * FIGYELEM: EZT A FÁJLT GÉP ÍRJA. A forrás a kliens core/ai/*Prompts.kt fájljaiban van;
 * ha ott változik valami, futtasd a backend/tools/gen-prompts.py szkriptet, és írd át a
 * SystemPromptSyncTest hasheit is — MINDKETTŐT.
 *
 * A Kotlin-teszt önmagában NEM elég őr: az a promptot egy bemásolt hashhez hasonlítja,
 * tehát az új hash bemásolásával zöldre fordul úgy is, hogy ez a fájl a régi szöveget
 * őrzi. A szétcsúszást a `gen-prompts.py --check` fogja meg, és a CI ezt futtatja.
 *
%s */

''' % ''.join(' * %-12s sha256 = %s\n' % (key + ':', sha(text)) for key, text in prompts.items())]

    for key, text in prompts.items():
        name = key.upper().replace('_EN', '') + '_SYSTEM_PROMPT' + ('_EN' if key.endswith('_en') else '')
        body = text.replace('\\', '\\\\').replace('`', '\\`').replace('${', '\\${')
        parts.append('export const %s = `%s`\n\n' % (name, body))

    parts.append('export const PROMPT_HASHES = {\n')
    for key, text in prompts.items():
        parts.append("  %s: '%s',\n" % (key, sha(text)))
    parts.append('} as const\n')
    return ''.join(parts)


def main() -> int:
    prompts = {key: extract(path, name) for key, path, name in SOURCES}
    rendered = render(prompts)

    if '--check' not in sys.argv:
        TARGET.write_text(rendered, encoding='utf-8')
        for key, text in prompts.items():
            print(key, "sha", sha(text), "chars", len(text))
        return 0

    problems = []
    committed = TARGET.read_text(encoding='utf-8') if TARGET.exists() else ''
    if committed != rendered:
        problems.append(
            f'{TARGET.relative_to(ROOT)} nem egyezik a kliensből generálttal — '
            'a backend a RÉGI promptot küldené a modellnek'
        )

    # A Kotlin-teszt bemásolt hashei: ezek a szétcsúszás másik fele.
    test_source = SYNC_TEST.read_text(encoding='utf-8') if SYNC_TEST.exists() else ''
    in_test = set(re.findall(r'"([0-9a-f]{64})"', test_source))
    for key, text in prompts.items():
        if sha(text) not in in_test:
            problems.append(
                f'{SYNC_TEST.name}: a(z) „{key}" prompt hashe ({sha(text)[:16]}…) nincs benne — '
                'a teszt egy régi szöveget őriz'
            )

    if problems:
        print('A rendszerpromptok szétcsúsztak:\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        print(
            '\nJavítás: futtasd a szkriptet --check nélkül, majd írd át a\n'
            'SystemPromptSyncTest hasheit az általa kiírtakra. MINDKETTŐ kell:\n'
            'csak a tesztet átírva zöld lesz minden, miközben a backend a régi\n'
            'promptot küldi.',
            file=sys.stderr,
        )
        return 1

    print(f'Rendben: mind a {len(prompts)} rendszerprompt egyezik a klienssel, '
          'a backenddel és a szinkrontesztbe írt hashekkel.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
