#!/usr/bin/env python3
"""A D1 migrációk tényleg lefutnak-e, sorban, egy valódi SQLite motoron.

Miért kell: a migrációk az ÉLES adatbázison futnak le, kézzel indított deploy közben.
Egy elgépelt oszlopnév vagy egy nem támogatott utasítás ott derülne ki — a legrosszabb
pillanatban, és félig lefutott állapotot hagyva maga után. A vitest-tesztek a SQL-t
csak SZÖVEGKÉNT nézik (megvan-e benne a `SELECT COUNT(*)`), azt nem, hogy a motor
egyáltalán elfogadja-e.

Nem kell hozzá semmi: a Python beépített `sqlite3` modulja ugyanazt a motort adja, ami
a D1 alatt is fut. A séma-eltéréseket ez nem fedi le (a D1 nem 1:1 SQLite), de a
szintaktikai és a szerkezeti hibákat igen — és azok a gyakoriak.

Amit ellenőriz:
  - minden migráció lefut, sorrendben, tiszta adatbázison;
  - a 0004 után tényleg nincs `crashes.message` oszlop;
  - a 0005 indexei tényleg létrejönnek;
  - és hogy a visszaélés elleni, FELTÉTELES beszúrás valóban korlátoz — nem csak a
    SQL szövege tartalmazza a feltételt, hanem a motor be is tartja.
"""
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / 'backend/migrations'

CAPPED_INSERT = """
INSERT INTO reports (id, user_id, kind, reason, detail, payload, app_version, created_at)
SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8
WHERE (SELECT COUNT(*) FROM reports WHERE user_id = ?2 AND created_at > ?9) < ?10
"""


def main() -> int:
    files = sorted(MIGRATIONS.glob('*.sql'))
    if not files:
        print(f'Nem találok migrációt itt: {MIGRATIONS}', file=sys.stderr)
        return 1

    con = sqlite3.connect(':memory:')
    for path in files:
        try:
            con.executescript(path.read_text(encoding='utf-8'))
        except sqlite3.Error as error:
            print(f'A(z) {path.name} migráció nem fut le: {error}', file=sys.stderr)
            print('\nEz az ÉLES adatbázison derülne ki, deploy közben.', file=sys.stderr)
            return 1

    problems = []

    columns = {row[1] for row in con.execute('PRAGMA table_info(crashes)')}
    if 'message' in columns:
        problems.append('a `crashes.message` oszlop még megvan — a 0004 migráció nem vitte el')

    for table, index in (('reports', 'idx_reports_user'), ('crashes', 'idx_crashes_user')):
        names = {row[1] for row in con.execute(f'PRAGMA index_list({table})')}
        if index not in names:
            problems.append(f'hiányzik a(z) {index} index — a napi korlát számlálása végigolvasná a táblát')

    # A feltételes beszúrás: korlát = 2, három próbálkozás, két sornak szabad maradnia.
    now = 1_700_000_000_000
    for i in range(3):
        con.execute(
            CAPPED_INSERT,
            (f'id{i}', 'u', 'PLAN', 'X', None, None, '1.0', now, now - 86_400_000, 2),
        )
    rows = con.execute("SELECT COUNT(*) FROM reports WHERE user_id = 'u'").fetchone()[0]
    if rows != 2:
        problems.append(
            f'a feltételes beszúrás nem korlátoz: korlát 2, három próbálkozás után {rows} sor '
            '— a napi keret csak a SQL szövegében létezne'
        )

    if problems:
        print('A migrációk lefutnak, de az eredmény nem az elvárt:\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        return 1

    print(f'Rendben: mind a {len(files)} migráció lefut sorrendben, és a visszaélés elleni '
          'korlátot a motor be is tartja.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
