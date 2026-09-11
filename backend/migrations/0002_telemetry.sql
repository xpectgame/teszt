-- Összeomlások és névtelen használati számlálók.
--
-- Nincs benne személyes adat: a felhasználót csak a telepítés véletlen azonosítójának
-- lenyomata azonosítja, és az étrend, a napló meg a testadatok soha nem kerülnek ide.

CREATE TABLE IF NOT EXISTS crashes (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL,
  app_version  TEXT,
  android_api  INTEGER,
  device       TEXT,
  -- A kivétel osztálya és üzenete külön, hogy csoportosítani lehessen rá.
  exception    TEXT NOT NULL,
  message      TEXT,
  stack        TEXT NOT NULL,
  -- Az azonos hibák egy ujjlenyomatot kapnak: enélkül egy hiba száz sornak látszik.
  fingerprint  TEXT NOT NULL,
  happened_at  INTEGER NOT NULL,
  received_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_crashes_fingerprint ON crashes(fingerprint, happened_at);
CREATE INDEX IF NOT EXISTS idx_crashes_time ON crashes(happened_at);

-- Napi bontású, összesített eseményszámlálók. Nem eseménynapló: nincs időbélyeg
-- eseményenként, nincs sorrend, nincs mit visszafejteni belőle egy emberre.
CREATE TABLE IF NOT EXISTS events (
  day          TEXT NOT NULL,        -- "YYYY-MM-DD"
  name         TEXT NOT NULL,
  app_version  TEXT NOT NULL DEFAULT '',
  count        INTEGER NOT NULL DEFAULT 0,
  users        INTEGER NOT NULL DEFAULT 0,
  updated_at   INTEGER NOT NULL,
  PRIMARY KEY (day, name, app_version)
);

CREATE INDEX IF NOT EXISTS idx_events_day ON events(day);
