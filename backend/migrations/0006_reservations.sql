-- Folyamatban lévő hívások foglalásai.
--
-- A /v1/generate a kvótát eddig ELŐRE OLVASTA ki, és a hívás UTÁN könyvelt. A kettő
-- között percek telhetnek el (egy tervszakasz streamelése), és ezalatt minden további
-- kérés ugyanazt a régi számlálót látta. Egyszerre indított kérések így mind
-- átmentek: három ingyenes terv helyett annyi, ahány kérés egyszerre futott, és a
-- tokenplafont — ami a számla kemény korlátja — ugyanígy túl lehetett lépni. A napi
-- közös mennyezet, ami a valódi felső korlát, pontosan ugyanezzel a réssel működött.
--
-- A foglalás egyetlen feltételes beszúrás: a motor egy utasításon belül nézi meg a
-- már elhasznált és a még folyamatban lévő mennyiséget, és csak akkor ír, ha belefér.
-- Ugyanaz a minta, mint a /v1/report napi korlátjánál.
--
-- A foglalás LEJÁR (`created_at` alapján). Ha egy hívás könyvelése valamiért elmarad
-- (a Worker leáll a hívás közben), a beragadt foglalás nem eheti meg örökre a keretet:
-- a próbaidőszak sosem fordul, tehát ott egy örök foglalás végleg elvenné a felhasználó
-- ingyenes terveit.
CREATE TABLE IF NOT EXISTS reservations (
  id            TEXT PRIMARY KEY,
  subject       TEXT NOT NULL,
  period        TEXT NOT NULL,
  plans         INTEGER NOT NULL DEFAULT 0,
  messages      INTEGER NOT NULL DEFAULT 0,
  output_tokens INTEGER NOT NULL DEFAULT 0,
  created_at    INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reservations_subject ON reservations(subject, period, created_at);
