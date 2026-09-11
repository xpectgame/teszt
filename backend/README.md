# MealPilot backend

Vékony proxy az Anthropic Messages API elé. Egyetlen dolga van: **az API kulcs a
szerveren maradjon**, és a hozzáférésről ne a telefon döntsön.

Amit megold:

| | |
|---|---|
| Kulcs | az Anthropic kulcs titok a Workeren, sosem kerül ki az appba |
| Rendszerprompt | a szerveren él, a kliens csak adatot küld — így a hívás nem alakítható át általános asszisztenssé |
| Előfizetés | a Play Developer API mondja meg, ki előfizető, nem az app |
| Kvóta | naptári hónaponként, **szerveroldalon**, előfizetéshez kötve |
| Tokenplafon | kemény, megkerülhetetlen havi korlát — ez a költségvédelem |
| Könyvelés | minden hívás tokenje és becsült költsége naplózva |
| Lemondás | Play valós idejű értesítések (RTDN), azonnali érvényesüléssel |
| Jelentés | „jelentsd ezt a tervet" bejelentések gyűjtése |
| Összeomlás | az appból érkező hibajelentések, ujjlenyomat szerint csoportosíthatóan |
| Statisztika | névtelen napi eseményszámlálók |
| Tulajdonosi kulcs | a fejlesztő saját buildje kvóta nélkül dolgozik |

Futtatókörnyezet: **Cloudflare Workers + D1**. Nincs szerver, amit karban kell tartani,
a streamelés működik, és a forgalom nagyságrendjén ez ingyenes vagy fillérekbe kerül.

---

## Telepítés nulláról

```bash
cd backend
npm install
npx wrangler login

# adatbázis
npx wrangler d1 create mealpilot
# az eredményül kapott database_id-t írd be a wrangler.toml-ba
npm run db:remote

# titkok
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put PLAY_SERVICE_ACCOUNT_JSON   # a szolgáltatásfiók JSON-ja, egy sorban
npx wrangler secret put RTDN_SHARED_SECRET          # bármilyen hosszú véletlen szöveg
npx wrangler secret put OWNER_KEY                  # openssl rand -hex 32

npm run deploy
```

A deploy után kapott URL-t (`https://mealpilot-backend.<felhasználó>.workers.dev`) írd be
az app buildjébe:

```bash
MEALPILOT_BACKEND_URL=https://mealpilot-backend.pelda.workers.dev ./gradlew assembleRelease
```

Ha nincs beállítva backend URL, az app ugyanúgy fut, csak a felhasználó saját
Anthropic-kulcsát kéri (fejlesztői mód) vagy az offline tervezőt használja.

---

## Play szolgáltatásfiók

1. Google Cloud Console → új projekt (vagy a meglévő) → **IAM** → Szolgáltatásfiókok →
   új fiók, JSON kulcs letöltése.
2. Play Console → **Felhasználók és jogosultságok** → a szolgáltatásfiók e-mail címének
   meghívása, „Pénzügyi adatok megtekintése" és „Rendelések kezelése" jogosultsággal.
3. Google Cloud Console → **API-k** → *Google Play Android Developer API* engedélyezése.
4. A letöltött JSON tartalma megy a `PLAY_SERVICE_ACCOUNT_JSON` titokba.

A jogosultság megadása után a Play oldalán **akár 24 óra**, amíg érvénybe lép.

## Valós idejű értesítések (RTDN)

1. Google Cloud Console → Pub/Sub → új téma, pl. `mealpilot-rtdn`.
2. A témára adj **Pub/Sub Publisher** jogot ennek a fióknak:
   `google-play-developer-notifications@system.gserviceaccount.com`
3. Play Console → az app → **Monetizálás beállítása** → *Valós idejű fejlesztői
   értesítések* → a téma teljes neve.
4. Pub/Sub → a témához **push feliratkozás**, a végpont:
   `https://<a te workered>/v1/play/rtdn?secret=<RTDN_SHARED_SECRET>`

Enélkül a lemondás és a visszatérítés csak a következő ellenőrzésnél (legfeljebb 6 óra)
derül ki. Ez nem katasztrófa, de a visszatérített vásárlás addig kiszolgálást kap.

---

## Végpontok

| Végpont | Mit csinál |
|---|---|
| `POST /v1/session` | jogosultság és a hónapból hátralévő keret |
| `POST /v1/generate` | tervezés, nap-átírás, beszélgetés — NDJSON stream |
| `POST /v1/report` | „jelentsd ezt a tervet" bejelentés |
| `POST /v1/telemetry` | összeomlások és napi számlálók |
| `POST /v1/play/rtdn` | Play értesítések (Pub/Sub push) |
| `GET /healthz` | életjel |

Minden `/v1` hívás fejlécei:

```
Authorization: Bearer <telepítési azonosító>
X-Play-Purchase-Token: <a Play vásárlási tokenje>   (ha van előfizetés)
X-Owner-Key: <az OWNER_KEY>                         (csak a fejlesztő saját buildjében)
X-App-Version: 0.1.0 (build 42)
```

Az `X-Owner-Key` mindent megelőz: aki küldi, kvóta nélkül dolgozik. A bolti buildbe nem
kerül bele (a Gradle a release buildből alapból kihagyja). Ha kiszivárogna, elég a titkot
lecserélni — a régi azonnal érvénytelen.

### `POST /v1/generate`

```json
{ "task": "PLAN", "prompt": "…", "days": 7, "chunk_index": 0, "is_retry": false }
```

`task`: `PLAN` | `DAY` | `CHAT`. A `days` a **teljes** terv hossza, nem a mostani
szakaszé — ebből ellenőrzi a szerver, hogy a csomag engedi-e. A `chunk_index` a
tervszakasz sorszáma, az `is_retry` pedig a javító kör jelzése: csak a
`chunk_index: 0` + `is_retry: false` hívás számít új tervnek a kvótában.

A válasz soronként egy JSON objektum:

```
{"type":"start","request_id":"…","allowed_days":3}
{"type":"delta","text":"{\"plan_title\""}
{"type":"done","usage":{"input_tokens":1840,"output_tokens":8912}}
```

Hiba esetén `{"type":"error","code":"UPSTREAM","message":"…"}`. Kvótaelutasításnál a
válasz **nem** stream, hanem HTTP 402 és egy JSON, amiben a `code` a `PLAN_QUOTA`,
`MESSAGE_QUOTA`, `TOKEN_CAP`, `PREMIUM_ONLY` vagy `PLAN_TOO_LONG` valamelyike.
A túl hosszú tervet a szerver **elutasítja**, nem vágja le csendben: a promptot a
kliens írja, tehát a rövidítést nem tudná kikényszeríteni — csak azt hinné, hogy
megtette. Az app ebből paywallt nyit, nem hibaüzenetet mutat.

---

## Korlátok egy helyen

`src/limits.ts`. Ezek a számok **tükrözik** a kliens `core/billing/Tiers.kt` értékeit.
A kliens csak azért ismeri őket, hogy a felületen kiírja, mennyi maradt — a döntést
mindig a szerver hozza. Ha itt változtatsz, a Kotlin oldalt is írd át, különben a
felhasználó mást lát, mint amit kap.

A `prompts.ts` a kliens `PlanPrompts.kt` és `ChatPrompts.kt` rendszerpromptjaiból
készül. Ha ott módosítasz, futtasd újra:

```bash
python3 tools/gen-prompts.py       # a repó gyökeréből
```

A `SystemPromptSyncTest` Kotlin-teszt elbukik, ha a kettő szétcsúszik.

---

## Amit ez a szerver szándékosan NEM csinál

- **Nem tárol étrendet, profilt, testsúlyt.** A kérés szövege átmegy rajta, de nem
  íródik le; csak a tokenszám és a becsült költség marad meg. A jelentett tervek a
  kivétel — azokat a felhasználó küldi be szándékosan.
- **Nem vezet fiókot.** Nincs regisztráció, nincs e-mail cím. A telepítési azonosító egy
  véletlen szám a telefonon.
- **Nem igazolja, hogy a hívó tényleg a MealPilot app.** Az ingyenes sávot elvileg lehet
  új telepítési azonosítókkal csapolni. A védelem ma a szűk ingyenes keret és a
  tokenplafon. Ha ez gonddá válik, a következő lépés a **Play Integrity API**: a kliens
  küld egy integritás-tokent, a szerver ellenőrzi, és csak valódi, boltból telepített
  appot szolgál ki.

## Költség

A `requests` táblában minden hívás mellett ott a becsült költség (USD milliomod részben):

```bash
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT substr(datetime(created_at/1000,'unixepoch'),1,7) AS ho, \
          count(*) AS hivas, sum(cost_micros)/1000000.0 AS usd \
   FROM requests GROUP BY ho ORDER BY ho DESC"
```

A legdrágább felhasználók:

```bash
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT subject, sum(cost_micros)/1000000.0 AS usd, count(*) AS hivas \
   FROM requests GROUP BY subject ORDER BY usd DESC LIMIT 20"
```

A bejelentések:

```bash
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT created_at, kind, reason, detail FROM reports WHERE handled = 0 ORDER BY created_at DESC"
```

## Összeomlások és statisztika

A leggyakoribb hibák, csoportosítva:

```bash
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT fingerprint, exception, count(*) AS db, max(happened_at) AS utoljara, \
          count(DISTINCT user_id) AS erintett \
   FROM crashes GROUP BY fingerprint ORDER BY db DESC LIMIT 20"
```

Egy konkrét hiba teljes hívási lánca:

```bash
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT app_version, device, android_api, stack FROM crashes \
   WHERE fingerprint = '...' ORDER BY happened_at DESC LIMIT 1"
```

A release build obfuszkált, tehát a hívási lánc olvashatatlan lesz. Visszafejtéshez tedd
el minden kiadás `mapping.txt`-jét, és használd az R8 `retrace` eszközét.

A tölcsér (hányan jutnak el az onboardingtól a tervig és az előfizetésig):

```bash
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT day, name, sum(count) AS db, sum(users) AS kuldok \
   FROM events WHERE day >= date('now','-14 day') GROUP BY day, name ORDER BY day DESC, db DESC"
```

Az `events` tábla szándékosan **nem eseménynapló**: nincs időbélyeg eseményenként és
nincs sorrend, tehát egy ember napirendjét nem lehet visszaolvasni belőle. A felhasználó
a Beállításokban ki is kapcsolhatja az egészet, és akkor az app nem is gyűjti.
