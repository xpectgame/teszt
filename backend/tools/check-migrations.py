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
    SQL szövege tartalmazza a feltételt, hanem a motor be is tartja;
  - a 0006 foglalótáblája és indexe létrejön, és a kvótafoglalás — az a SQL, amit a
    `src/db.ts` TÉNYLEG elküld, onnan kiolvasva — egyszerre érkező kéréseknél is
    betartja a keretet;
  - a felhasználó upsertje (`TOUCH_USER_SQL`, szintén a `db.ts`-ből) a RETURNING-gel
    ütközésnél is a meglévő sort adja vissza.
"""
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / 'backend/migrations'
DB_TS = ROOT / 'backend/src/db.ts'


def sql_from_db_ts(name: str) -> str:
    """Egy SQL a `db.ts`-ből kiolvasva, a `// <NAME>_BEGIN` / `_END` jelölők közül.

    Nem másolat: ha a szkript a saját példányát futtatná, a kód SQL-je bármivé
    válhatna, és ez az ellenőrzés akkor is zöld maradna.
    """
    text = DB_TS.read_text(encoding='utf-8')
    match = re.search(
        rf'// {name}_BEGIN\s*export const {name} = `(.*?)`\s*// {name}_END',
        text,
        re.S,
    )
    if not match:
        raise SystemExit(
            f'Nem találom a {name}-t a db.ts-ben a két jelölő között — a szkript nem '
            'tudja, mit kellene ellenőriznie.'
        )
    return match.group(1)


def reserve_sql() -> str:
    return sql_from_db_ts('RESERVE_SQL')

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

    names = {row[1] for row in con.execute('PRAGMA index_list(reservations)')}
    if 'idx_reservations_subject' not in names:
        problems.append('hiányzik az idx_reservations_subject index — a foglalás minden '
                        'kérésnél végigolvasná a táblát')

    problems += check_reservation(con)
    problems += check_touch_user(con)

    if problems:
        print('A migrációk lefutnak, de az eredmény nem az elvárt:\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        return 1

    print(f'Rendben: mind a {len(files)} migráció lefut sorrendben, a visszaélés elleni '
          'korlátot a motor be is tartja, és a kvótafoglalás egyszerre érkező kéréseknél is '
          'betartja a keretet.')
    return 0


def check_touch_user(con: sqlite3.Connection) -> list:
    """A felhasználó upsertje RETURNING-gel: új sornál és ütközésnél is a sort adja.

    Az ütközéses ág a fontos: ott a MEGLÉVŐ `purchase_hash`-nek kell visszajönnie,
    különben a bekötött előfizetés a következő kérésnél eltűnne.
    """
    sql = sql_from_db_ts('TOUCH_USER_SQL')
    problems = []
    try:
        first = con.execute(sql, ('felhasznalo', 1, '1.0')).fetchall()
        con.execute("UPDATE users SET purchase_hash = 'elofizetes' WHERE id = 'felhasznalo'")
        again = con.execute(sql, ('felhasznalo', 2, None)).fetchall()
    except sqlite3.Error as error:
        return [f'a touchUser SQL-je nem fut le: {error}']
    if first != [('felhasznalo', None)]:
        problems.append(f'a touchUser új felhasználónál nem a sort adja vissza: {first}')
    if again != [('felhasznalo', 'elofizetes')]:
        problems.append(f'a touchUser ütközésnél nem a meglévő előfizetést adja vissza: {again}')
    version = con.execute("SELECT app_version, last_seen_at FROM users WHERE id = 'felhasznalo'").fetchone()
    if version != ('1.0', 2):
        problems.append(f'a touchUser elrontja a verziót vagy az utolsó látogatást: {version}')
    return problems


def check_reservation(con: sqlite3.Connection) -> list:
    """A kvótafoglalás valódi motoron: egyszerre érkező kérések, határesetek."""
    sql = reserve_sql()
    now = 1_700_000_000_000
    ttl = 20 * 60 * 1000
    counter = iter(range(10_000))

    def reserve(subject, plans=0, messages=0, tokens=0, plan_limit=-1, message_limit=-1, cap=-1):
        cursor = con.execute(sql, (
            f'r{next(counter)}', subject, 'trial', plans, messages, tokens,
            now, now - ttl, plan_limit, message_limit, cap,
        ))
        return cursor.rowcount

    def usage(subject, **values):
        cols = ', '.join(values)
        marks = ', '.join('?' for _ in values)
        con.execute(
            f"INSERT INTO usage (subject, period, {cols}, updated_at) VALUES (?, 'trial', {marks}, 0)",
            (subject, *values.values()),
        )

    problems = []

    got = [reserve('user:a', plans=1, tokens=24_000, plan_limit=3, cap=10**9) for _ in range(5)]
    if got != [1, 1, 1, 0, 0]:
        problems.append(f'öt egyszerre érkező tervkérés, korlát 3: {got} — három mehet át, kettő nem')

    usage('user:b', plans=2)
    got = [reserve('user:b', plans=1, plan_limit=3) for _ in range(3)]
    if got != [1, 0, 0]:
        problems.append(f'2 elhasznált terv mellett csak egy fér még bele, itt: {got}')

    usage('user:c', plans=5)
    if reserve('user:c', plans=0, plan_limit=3) != 1:
        problems.append('egy folytatólagos szakasz (nem új terv) nem akadhat el a tervkorláton')

    usage('user:d', output_tokens=70_000)
    got = [reserve('user:d', tokens=24_000, cap=80_000) for _ in range(2)]
    if got != [1, 0]:
        problems.append(f'tokenplafon: a plafon alatt EGY hívás indulhat, a második már nem — itt: {got}')

    con.execute("INSERT INTO reservations VALUES ('lejart', 'user:e', 'trial', 9, 9, 999999, ?)", (now - ttl - 1,))
    if reserve('user:e', plans=1, tokens=24_000, plan_limit=3, cap=80_000) != 1:
        problems.append('egy lejárt, beragadt foglalás még mindig elveszi a keretet')

    usage('user:f', messages=19)
    got = [reserve('user:f', messages=1, message_limit=20) for _ in range(2)]
    if got != [1, 0]:
        problems.append(f'üzenetkorlát 20, 19 elhasználva: egy fér bele, itt: {got}')

    if reserve('global:daily', tokens=24_000) != 1:
        problems.append('korlát nélküli foglalás (a tulajdonos a közös mennyezeten) elutasítva')

    return problems


if __name__ == '__main__':
    raise SystemExit(main())
