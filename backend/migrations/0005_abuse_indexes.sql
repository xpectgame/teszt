-- Felhasználónkénti napi korlát a bejelentésekhez és az összeomlás-jelentésekhez.
--
-- A /v1/report és a /v1/telemetry végpontot bárki elérheti, aki generál magának egy
-- telepítési azonosítót — az nem jogosultság, csak formátum. A tervezésnél ezt a
-- kimeneti tokenplafon fogja meg, ezeknél a végpontoknál viszont semmi nem volt:
-- korlátlan számú, egyenként több kilobájtos sor mehetett a D1-be. Nem a tartalom a
-- baj, hanem a MENNYISÉG: a napi írási keret kimerítése az egész szolgáltatást
-- megbénítaná — a könyvelést és a kvótaszámlálást is, tehát a fizető felhasználókat.
--
-- A korlátot maga a beszúrás tartja be (egyetlen utasítás, külön kérdezés nélkül),
-- ehhez viszont indexre van szükség: e nélkül a számlálás végigolvasná a táblát,
-- vagyis pont az elárasztás tenné egyre drágábbá minden további kérést.
CREATE INDEX IF NOT EXISTS idx_reports_user ON reports(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_crashes_user ON crashes(user_id, received_at);
