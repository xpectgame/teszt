#!/usr/bin/env python3
"""Van-e olyan CSAK IKONBÓL álló vezérlő, aminek nincs neve.

Miért ez a szabály, és miért nem a `contentDescription = null` általános tiltása:
egy szöveg MELLETT álló ikon dekoratív, és ott a `null` a HELYES érték — leírással a
képernyőolvasó kétszer mondaná ugyanazt. Az appban jelenleg tizenkilenc ilyen hely
van, és mind indokolt: vagy szöveg áll mellette, vagy a szülő `Role`-ja (RadioButton,
Checkbox) hordozza az állapotot.

Az valódi hiba, ha egy vezérlőnek CSAK ikonja van, és az sem mondja meg, mit csinál.
A képernyőolvasó ilyenkor annyit mond: „gomb”. Az ilyen gomb nem nehezen használható,
hanem használhatatlan — és ez a legkönnyebben elkövethető hiba, mert szemmel nézve
minden rendben van.

Amit néz: az `IconButton`, `IconToggleButton` és `FloatingActionButton` blokkjain
belül nincs-e `contentDescription = null`. A blokk határát zárójel-egyensúllyal
keressük, nem soronként — a Compose hívások többsoros lambdákat tartalmaznak.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java")
CONTROLS = ("IconButton", "IconToggleButton", "FilledIconButton", "FloatingActionButton")
CONTROL_RE = re.compile(r'\b(' + "|".join(CONTROLS) + r')\s*\(')


def balanced_end(source, start):
    """A `start` pozíción nyíló zárójel/kapcsos párjának indexe (a záró karakterrel)."""
    depth = 0
    i = start
    while i < len(source):
        char = source[i]
        if char in "({[":
            depth += 1
        elif char in ")}]":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return len(source)


def block_at(source, open_paren_index):
    """
    A hívás teljes szövege, a ZÁRÓ LAMBDÁVAL együtt.

    A záró lambda nélkül ez az ellenőrzés semmit nem ért: a Compose hívások épp úgy
    néznek ki, hogy `IconButton(onClick = ...) { Icon(...) }` — vagyis az ikon a
    zárójel UTÁN áll. Az első változat csak a zárójelig nézett, és a szándékos
    elrontást (a szív gomb nevének elvétele) simán átengedte.
    """
    end = balanced_end(source, open_paren_index)
    rest = source[end:]
    stripped = rest.lstrip()
    if stripped.startswith("{"):
        offset = end + (len(rest) - len(stripped))
        end = balanced_end(source, offset)
    return source[open_paren_index:end]


def main():
    problems = []
    scanned = 0
    controls = 0
    for directory, _, files in os.walk(ROOT):
        for name in sorted(files):
            if not name.endswith(".kt"):
                continue
            path = os.path.join(directory, name)
            source = open(path, encoding="utf-8").read()
            scanned += 1
            for match in CONTROL_RE.finditer(source):
                controls += 1
                block = block_at(source, match.end() - 1)
                if "contentDescription = null" in block:
                    line = source[:match.start()].count("\n") + 1
                    rel = os.path.relpath(path, ROOT)
                    problems.append(f"{rel}:{line} — {match.group(1)} leíró nélküli ikonnal")

    if problems:
        print("Névtelen, csak ikonból álló vezérlő:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        print(
            "\nA képernyőolvasó ezekre annyit mond: \u201egomb\u201d. Adj a benne lévő `Icon`-nak\n"
            "`contentDescription = stringResource(...)` értéket — azt, amit a gomb CSINÁL,\n"
            "nem azt, amit ábrázol (\u201eKedvencekhez adom\u201d, nem \u201eszív\u201d).",
            file=sys.stderr,
        )
        return 1

    print(f"Rendben: mind a(z) {controls} ikonvezérlőnek van neve ({scanned} fájl).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
