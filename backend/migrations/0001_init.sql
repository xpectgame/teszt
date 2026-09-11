-- MealPilot backend — kezdeti séma.
-- Minden azonosítót hashelve tárolunk: a napló és a kvóta működik tőle, de egy
-- adatbázis-kiszivárgás önmagában nem ad használható tokent senkinek.

CREATE TABLE IF NOT EXISTS users (
  id               TEXT PRIMARY KEY,   -- az install token SHA-256 hexe
  created_at       INTEGER NOT NULL,
  last_seen_at     INTEGER NOT NULL,
  app_version      TEXT,
  purchase_hash    TEXT                -- éppen ehhez a telepítéshez kötött előfizetés
);

CREATE TABLE IF NOT EXISTS subscriptions (
  purchase_hash    TEXT PRIMARY KEY,   -- a vásárlási token SHA-256 hexe
  purchase_token   TEXT NOT NULL,      -- az újraellenőrzéshez kell (Play API, RTDN)
  state            TEXT NOT NULL,      -- ACTIVE | PENDING | PAUSED | ON_HOLD | CANCELED | EXPIRED | REVOKED
  expires_at       INTEGER,
  verified_at      INTEGER NOT NULL,
  first_user_id    TEXT,               -- ehhez a telepítéshez kötöttük először
  linked_from      TEXT                -- előfizetés-csere esetén a korábbi token hashe
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_verified ON subscriptions(verified_at);

-- Használat naptári hónaponként. A `subject` prémiumnál az előfizetés, ingyenesnél a
-- telepítés — így egy előfizetésből nem lehet több készüléken többszörös kvótát csinálni.
CREATE TABLE IF NOT EXISTS usage (
  subject          TEXT NOT NULL,
  period           TEXT NOT NULL,      -- "YYYY-MM"
  plans            INTEGER NOT NULL DEFAULT 0,
  messages         INTEGER NOT NULL DEFAULT 0,
  input_tokens     INTEGER NOT NULL DEFAULT 0,
  output_tokens    INTEGER NOT NULL DEFAULT 0,
  cost_micros      INTEGER NOT NULL DEFAULT 0,
  updated_at       INTEGER NOT NULL,
  PRIMARY KEY (subject, period)
);

CREATE TABLE IF NOT EXISTS requests (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL,
  subject           TEXT NOT NULL,
  task              TEXT NOT NULL,     -- PLAN | DAY | CHAT
  model             TEXT NOT NULL,
  input_tokens      INTEGER NOT NULL DEFAULT 0,
  output_tokens     INTEGER NOT NULL DEFAULT 0,
  cache_read_tokens INTEGER NOT NULL DEFAULT 0,
  cost_micros       INTEGER NOT NULL DEFAULT 0,
  ok                INTEGER NOT NULL DEFAULT 0,
  error             TEXT,
  created_at        INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_requests_created ON requests(created_at);
CREATE INDEX IF NOT EXISTS idx_requests_user ON requests(user_id, created_at);

-- A Play a generatív AI funkcióknál elvárja, hogy a felhasználó jelenteni tudja a
-- problémás tartalmat. Ezek a bejelentések ide futnak be.
CREATE TABLE IF NOT EXISTS reports (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL,
  kind         TEXT NOT NULL,          -- PLAN | MEAL | CHAT
  reason       TEXT NOT NULL,
  detail       TEXT,
  payload      TEXT,
  app_version  TEXT,
  created_at   INTEGER NOT NULL,
  handled      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_reports_open ON reports(handled, created_at);
