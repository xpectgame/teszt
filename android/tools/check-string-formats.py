#!/usr/bin/env python3
"""
A szövegerőforrások formátumhelyőrzőit veti össze a tényleges hívásokkal.

Miért kell: az Android lint `StringFormatMatches` szabálya a `getString` hívásokat
nézi, a Compose `stringResource` hívásait NEM. Ezen a résen már átment egy hiba, ami
a felhasználó telefonján omlasztotta össze az appot: a `meal_servings_value` %1$d-t
várt, a hívás Double-t adott. Semmi nem szólt előtte — se fordító, se lint, se teszt.

Ez a szkript azt a rést zárja be. A CI-ban fut, és nem kell hozzá se emulátor,
se Android SDK.
"""
import re
import sys
import pathlib
import collections

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
SRC = ROOT / 'app/src/main'


def placeholders(text: str) -> dict[int, str]:
    """A szöveg formátumhelyőrzői: {sorszám: konverzió}. A %% nem helyőrző."""
    text = text.replace('%%', '')
    out: dict[int, str] = {}
    positional = 0
    for m in re.finditer(r'%(?:(\d+)\$)?[-+ 0#,]*\d*(?:\.\d+)?([a-zA-Z])', text):
        index, conversion = m.group(1), m.group(2)
        if index is None:
            positional += 1
            index = positional
        out[int(index)] = conversion.lower()
    return out


def read_strings(path: pathlib.Path) -> dict[str, str]:
    xml = path.read_text(encoding='utf-8')
    return {
        m.group(1): m.group(2)
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', xml, re.S)
    }


def count_arguments(text: str, start: int) -> int:
    """
    Hány argumentum áll a megadott pozíciótól a hívás lezárásáig.

    A lezárás lehet ")" vagy "]": a lokalizált szövegforrás szögletes zárójellel hívódik
    (`strings[R.string.x, arg]`), a Compose és a Context kerekkel. Mindkettőt nézni kell,
    különben az egyik alak némán kimaradna az ellenőrzésből.

    A Kotlin megengedi a záró vesszőt a többsoros hívásokban, azt nem számoljuk
    argumentumnak — enélkül minden többsoros hívás eggyel többnek látszana.
    """
    segments = ['']
    depth = 0
    in_string = False
    escaped = False
    i = start
    while i < len(text):
        c = text[i]
        if escaped:
            escaped = False
        elif c == '\\':
            escaped = True
        elif c == '"':
            in_string = not in_string
        elif not in_string:
            if c in '([{':
                depth += 1
            elif c in ')]}':
                if depth == 0:
                    break
                depth -= 1
            elif c == ',' and depth == 0:
                segments.append('')
                i += 1
                continue
        segments[-1] += c
        i += 1
    if segments and not segments[-1].strip():
        segments.pop()
    return len(segments)


def main() -> int:
    strings = {
        'values/strings.xml': read_strings(RES / 'values/strings.xml'),
        'values-hu/strings.xml': read_strings(RES / 'values-hu/strings.xml'),
    }

    calls = collections.defaultdict(list)
    for kt in sorted(SRC.rglob('*.kt')):
        text = kt.read_text(encoding='utf-8')
        # Három hívási alak: Compose, Context és a lokalizált szövegforrás. Az utóbbi
        # szögletes zárójellel hívódik, és a minta kiterjesztése nélkül kimaradna.
        for m in re.finditer(
            r'(?:stringResource|getString)\(\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?'
            r'|strings\[\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?',
            text,
        ):
            key = m.group(1) or m.group(3)
            comma = 2 if m.group(1) else 4
            given = count_arguments(text, m.end(comma)) if m.group(comma) else 0
            line = text.count('\n', 0, m.start()) + 1
            calls[key].append((f'{kt.relative_to(ROOT)}:{line}', given))

    problems = []
    for key, uses in sorted(calls.items()):
        for language, table in strings.items():
            if key not in table:
                continue
            spec = placeholders(table[key])
            expected = max(spec) if spec else 0
            for where, given in uses:
                if given != expected:
                    problems.append(
                        f'{where}: R.string.{key} — a(z) {language} szöveg {expected} '
                        f'paramétert vár, a hívás {given}-t ad'
                    )

    # A két nyelv helyőrzőinek egyezniük kell, különben a nyelvváltás omlaszt.
    for key in sorted(set(strings['values/strings.xml']) & set(strings['values-hu/strings.xml'])):
        en = placeholders(strings['values/strings.xml'][key])
        hu = placeholders(strings['values-hu/strings.xml'][key])
        if en != hu:
            problems.append(
                f'R.string.{key} — az angol és a magyar szöveg helyőrzői eltérnek: '
                f'{sorted(en.items())} vs {sorted(hu.items())}'
            )

    if problems:
        print('Formátumhiba a szövegerőforrásokban:\n')
        for p in problems:
            print(f'  {p}')
        print(f'\n{len(problems)} hiba. Ezek futásidőben omlasztanák az appot.')
        return 1

    print(f'Rendben: {len(calls)} szövegerőforrás-hívás helyőrzői egyeznek.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
