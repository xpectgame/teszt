#!/usr/bin/env python3
"""Az utolsó Room-migráció tényleg azt a sémát állítja-e elő, amit a Room vár.

Miért kell: a migrációk a FELHASZNÁLÓ telefonján futnak le először. A Room indításkor
összeveti a kapott sémát a várttal, és eltérés esetén `IllegalStateException`-nel áll
meg — az app el sem indul. Egy elgépelt oszlopnév vagy egy kimaradt index tehát nem
hibát okoz, hanem HASZNÁLHATATLAN appot, és pont azoknál, akik frissítenek (az új
telepítőknél a Room egyben építi a sémát, ott minden rendben van).

Eddig ezt semmi nem próbálta ki. A `:app` tesztek mind üres, in-memory adatbázisra
épülnek: ott a Room a 4-es sémát egy lépésben hozza létre, a migráció le sem fut.

Ez a szkript a repóban lévő két sémafájlból és a `AppDatabase.kt`-ből dolgozik:

  1. felépíti az ELŐZŐ verzió sémáját (`<N-1>.json` `createSql` utasításaiból);
  2. lefuttatja rá a Kotlinból kiszedett `MIGRATION_<N-1>_<N>` utasításait;
  3. és a kapott táblákat, oszlopokat, indexeket és idegen kulcsokat összeveti azzal,
     amit az `<N>.json` vár — ugyanazokkal a PRAGMA-kkal, amiket a Room is használ.

A Python beépített `sqlite3` modulja ugyanaz a motor, ami az Android alatt is fut.

KORLÁT: csak a LEGUTÓBBI lépést nézi, és csak a szerkezetet — az adatmozgatás
helyességét (`INSERT ... SELECT`) nem tudja megítélni.
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / 'android/app/schemas/hu.mealpilot.app.data.local.AppDatabase'
DB_KT = ROOT / 'android/app/src/main/java/hu/mealpilot/app/data/local/AppDatabase.kt'


def load(version):
    path = SCHEMA_DIR / f'{version}.json'
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding='utf-8'))['database']


def statements(schema):
    """A séma felépítéséhez szükséges utasítások, a Room saját `createSql`-jéből."""
    out = []
    for entity in schema['entities']:
        table = entity['tableName']
        out.append(entity['createSql'].replace('${TABLE_NAME}', table))
        for index in entity.get('indices', []):
            out.append(index['createSql'].replace('${TABLE_NAME}', table))
    for view in schema.get('views', []):
        out.append(view['createSql'].replace('${VIEW_NAME}', view['viewName']))
    return out


def migration_statements(text, from_version, to_version):
    """A `MIGRATION_x_y` objektum `execSQL` hívásai, sorrendben."""
    marker = f'MIGRATION_{from_version}_{to_version} = object : Migration({from_version}, {to_version})'
    start = text.find(marker)
    if start < 0:
        return None
    # Az objektum a következő `MIGRATION_` vagy a `@Volatile` jelölésig tart.
    rest = text[start + len(marker):]
    end = min(
        (i for i in (rest.find('\n        private val MIGRATION_'), rest.find('\n        @Volatile'))
         if i >= 0),
        default=len(rest),
    )
    body = rest[:end]

    found = []
    # Háromidézőjeles blokk (`trimIndent`-tel), illetve egyszerű, akár összefűzött sztring.
    for match in re.finditer(r'execSQL\(\s*("""(.*?)"""|((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+))', body, re.S):
        if match.group(2) is not None:
            found.append(match.group(2))
        else:
            pieces = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(3))
            found.append(''.join(p.encode().decode('unicode_escape') for p in pieces))
    return [s.strip() for s in found if s.strip()]


def structure(conn):
    """Táblák → oszlopok, indexek, idegen kulcsok. Ugyanaz, amit a Room is összevet."""
    result = {}
    tables = conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' "
        "AND name != 'room_master_table' ORDER BY name"
    ).fetchall()
    for (table,) in tables:
        columns = {
            row[1]: (row[2].upper(), bool(row[3]), row[5])  # típus, NOT NULL, pk-sorszám
            for row in conn.execute(f'PRAGMA table_info(`{table}`)')
        }
        indices = {}
        for row in conn.execute(f'PRAGMA index_list(`{table}`)'):
            name, unique = row[1], bool(row[2])
            if name.startswith('sqlite_autoindex'):
                continue
            cols = [r[2] for r in conn.execute(f'PRAGMA index_info(`{name}`)')]
            indices[name] = (unique, cols)
        keys = sorted(
            (row[2], row[3], row[4], row[5], row[6])  # cél tábla, honnan, hova, onUpdate, onDelete
            for row in conn.execute(f'PRAGMA foreign_key_list(`{table}`)')
        )
        result[table] = {'columns': columns, 'indices': indices, 'foreignKeys': keys}
    return result


def build(stmts):
    conn = sqlite3.connect(':memory:')
    for sql in stmts:
        conn.execute(sql)
    return conn


def main():
    versions = sorted(int(p.stem) for p in SCHEMA_DIR.glob('*.json'))
    if len(versions) < 2:
        print(f'Legalább két sémaverzió kell a {SCHEMA_DIR} könyvtárban.', file=sys.stderr)
        return 1
    previous, current = versions[-2], versions[-1]

    old_schema, new_schema = load(previous), load(current)
    kotlin = DB_KT.read_text(encoding='utf-8')
    steps = migration_statements(kotlin, previous, current)
    if steps is None:
        print(f'Nincs MIGRATION_{previous}_{current} az AppDatabase.kt-ben, pedig a séma '
              f'{previous}-ről {current}-re lépett.', file=sys.stderr)
        return 1
    if not steps:
        print(f'A MIGRATION_{previous}_{current} egyetlen execSQL hívást sem tartalmaz — '
              'ez vagy üres migráció, vagy a szkript nem ismerte fel az alakját.',
              file=sys.stderr)
        return 1

    if f'MIGRATION_{previous}_{current}' not in kotlin.split('addMigrations(', 1)[-1].split(')', 1)[0]:
        print(f'A MIGRATION_{previous}_{current} megvan, de nincs átadva az '
              'addMigrations(...) hívásnak — így soha nem fut le.', file=sys.stderr)
        return 1

    # 1. A régi séma, 2. a migráció rá, 3. amit a Room vár.
    migrated = build(statements(old_schema))
    for sql in steps:
        try:
            migrated.execute(sql)
        except sqlite3.Error as error:
            print(f'A migráció utasítása elszállt: {error}\n\n{sql}', file=sys.stderr)
            return 1
    expected = build(statements(new_schema))

    got, want = structure(migrated), structure(expected)
    problems = []
    for table in sorted(set(want) | set(got)):
        if table not in got:
            problems.append(f'`{table}`: a migráció után hiányzik a tábla.')
            continue
        if table not in want:
            problems.append(f'`{table}`: a migráció létrehozta, de a séma nem ismeri.')
            continue
        for part in ('columns', 'indices', 'foreignKeys'):
            if got[table][part] != want[table][part]:
                problems.append(
                    f'`{table}` / {part} eltér:\n'
                    f'      migráció után: {got[table][part]}\n'
                    f'      a Room várja:  {want[table][part]}'
                )

    if problems:
        print(f'A {previous} → {current} migráció NEM azt a sémát adja, amit a Room vár. '
              'A frissítő felhasználóknál az app el sem indulna:', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        return 1

    print(f'Rendben: a {previous} → {current} migráció {len(steps)} utasítása pontosan azt a '
          f'sémát adja, amit a Room vár ({len(want)} tábla).')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
