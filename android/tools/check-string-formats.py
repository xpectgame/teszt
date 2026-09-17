#!/usr/bin/env python3
"""
A szövegerőforrások formátumhelyőrzőit veti össze a tényleges hívásokkal.

Miért kell: az Android lint `StringFormatMatches` szabálya a `getString` hívásokat
nézi, a Compose `stringResource` hívásait NEM. Ezen a résen már átment egy hiba, ami
a felhasználó telefonján omlasztotta össze az appot: a `meal_servings_value` %1$d-t
várt, a hívás Double-t adott. Semmi nem szólt előtte — se fordító, se lint, se teszt.

Ez a szkript azt a rést zárja be. A CI-ban fut, és nem kell hozzá se emulátor,
se Android SDK.

AMIT LEFED: `stringResource`, `getString`, `strings[...]`, `strings.get(...)`, a
darabszámos alakok, ÉS a fájlban írt rövidítő függvények (`fun text(resId: Int,
vararg args: Any) = strings.get(resId, *args)`). Az utolsó sokáig kimaradt — pedig
pont oda kerülnek a szórásoperátort igénylő, tehát HELYŐRZŐS hívások.
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


def read_plurals(path: pathlib.Path) -> dict[str, dict[str, str]]:
    """
    A darabszámtól függő szövegek: {név: {mennyiség: szöveg}}.

    Ezeket ugyanúgy ellenőrizni kell, mint a sima szövegeket — sőt jobban: itt egy
    nyelven belül is több alak van, és ha az egyikből kimarad egy helyőrző, csak az
    a darabszám omlaszt, ami ritkábban fordul elő. Pont az a fajta hiba, ami
    teszteléskor nem jön elő.
    """
    xml = path.read_text(encoding='utf-8')
    out: dict[str, dict[str, str]] = {}
    for block in re.finditer(r'<plurals name="([^"]+)"[^>]*>(.*?)</plurals>', xml, re.S):
        items = {
            i.group(1): i.group(2)
            for i in re.finditer(r'<item quantity="([^"]+)"[^>]*>(.*?)</item>', block.group(2), re.S)
        }
        out[block.group(1)] = items
    return out


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
    plurals = {
        'values/strings.xml': read_plurals(RES / 'values/strings.xml'),
        'values-hu/strings.xml': read_plurals(RES / 'values-hu/strings.xml'),
    }

    calls = collections.defaultdict(list)
    plural_calls = collections.defaultdict(list)
    for kt in sorted(SRC.rglob('*.kt')):
        text = kt.read_text(encoding='utf-8')
        # Négy hívási alak: Compose, Context és a lokalizált szövegforrás kétféleképpen.
        # Az utóbbi szögletes zárójellel hívódik — DE ahol szórásoperátor kell (*args),
        # ott az indexelő alak nem használható, és a hívás `.get(...)`-re vált. Ez a
        # minta sokáig kimaradt, tehát azok a hívások ellenőrizetlenül mentek át.
        #
        # ÖTÖDIK alak: a fájlban ÍRT rövidítő függvény. A `ChatScreen`-ben például
        #
        #     fun text(resId: Int, vararg args: Any) = container.strings.get(resId, *args)
        #
        # áll, és tizennégy hívás megy rajta keresztül. Ezeket a szkript nem látta,
        # mert a mintái közvetlenül az `R.string.` elé néztek — vagyis pont ott maradt
        # rés, ahol a szórásoperátor miatt a legtöbb HELYŐRZŐS hívás van. Egy három
        # helyőrzős szöveg két argumentummal hívva zölden átment, futásidőben viszont
        # `MissingFormatArgumentException`.
        wrappers = set(
            re.findall(
                r'fun\s+([A-Za-z0-9_]+)\s*\(\s*\w+\s*:\s*Int\s*,\s*vararg\s+\w+\s*:\s*Any\s*\)'
                r'[^=\n]*=\s*[\w.]*(?:strings\.get|getString)\(',
                text,
            )
        )
        wrapper_alternatives = ''.join(
            r'|\b%s\(\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?' % re.escape(name)
            for name in sorted(wrappers)
        )

        for m in re.finditer(
            r'(?:stringResource|getString)\(\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?'
            r'|strings\[\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?'
            r'|strings\.get\(\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?'
            + wrapper_alternatives,
            text,
        ):
            # Az első csoport nyer, amelyik illeszkedett; a kulcs és a vesszője egymás
            # mellett áll, tehát a kulcs csoportszáma után közvetlenül a vessző jön.
            index = next(i for i in range(1, (m.lastindex or 0) + 1, 2) if m.group(i))
            key = m.group(index)
            comma = index + 1
            given = count_arguments(text, m.end(comma)) if m.group(comma) else 0
            line = text.count('\n', 0, m.start()) + 1
            calls[key].append((f'{kt.relative_to(ROOT)}:{line}', given))

        # A darabszámos alakok. Itt az ELSŐ argumentum a nyelvtani alakot választja ki,
        # és nem helyőrző — ezért eggyel kevesebbet számolunk.
        for m in re.finditer(
            r'(?:pluralStringResource|getQuantityString)\(\s*R\.plurals\.([A-Za-z0-9_]+)\s*(,)?'
            r'|\.quantity\(\s*R\.plurals\.([A-Za-z0-9_]+)\s*(,)?',
            text,
        ):
            key = m.group(1) or m.group(3)
            comma = 2 if m.group(1) else 4
            given = max(0, count_arguments(text, m.end(comma)) - 1) if m.group(comma) else 0
            line = text.count('\n', 0, m.start()) + 1
            plural_calls[key].append((f'{kt.relative_to(ROOT)}:{line}', given))

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

    # Ugyanez a darabszámos szövegekre, minden nyelvtani alakra külön.
    for key, uses in sorted(plural_calls.items()):
        for language, table in plurals.items():
            if key not in table:
                problems.append(f'R.plurals.{key} — hiányzik a(z) {language} fájlból')
                continue
            for quantity, value in sorted(table[key].items()):
                spec = placeholders(value)
                expected = max(spec) if spec else 0
                for where, given in uses:
                    if given != expected:
                        problems.append(
                            f'{where}: R.plurals.{key} ({quantity}) — a(z) {language} szöveg '
                            f'{expected} paramétert vár, a hívás {given}-t ad'
                        )
            # Egy nyelven belül is egyeznie kell: ha az „one" alakból kimarad egy
            # helyőrző, csak az az egy darabszám omlaszt.
            specs = {q: placeholders(v) for q, v in table[key].items()}
            if len(set(map(str, map(sorted, (s.items() for s in specs.values()))))) > 1:
                problems.append(
                    f'R.plurals.{key} — a(z) {language} nyelvtani alakok helyőrzői eltérnek: '
                    + ', '.join(f'{q}: {sorted(v.items())}' for q, v in sorted(specs.items()))
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

    # KULCSOKAT és HÍVÁSOKAT külön írunk ki. A kettő nem ugyanaz, és amíg csak a
    # kulcsok száma látszott „hívás" néven, a szám nem volt mihez mérni: új hívás
    # hozzáadásakor nem mozdult, ha a kulcs már szerepelt máshol. Egy őr, aminek a
    # számát nem lehet ellenőrizni, pont annyit ér, mintha nem is írna ki semmit.
    call_sites = sum(len(uses) for uses in calls.values())
    plural_sites = sum(len(uses) for uses in plural_calls.values())
    print(
        f'Rendben: {call_sites} szöveghívás ({len(calls)} kulcs) és '
        f'{plural_sites} darabszámos hívás ({len(plural_calls)} kulcs) helyőrzői egyeznek.'
    )
    return 0


if __name__ == '__main__':
    sys.exit(main())
