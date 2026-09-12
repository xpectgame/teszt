# Élesítés lépésről lépésre

Ez az a sorrend, amiben tényleg működik. Minden lépés végén van egy **ellenőrzés** —
ha az nem jön be, ne menj tovább, mert a következő lépés úgyis elhasal.

Amit előre készíts oda:
- egy **Google-fiók**, amivel a Play Console-ba és a Google Cloudba belépsz;
- egy **Cloudflare-fiók** (ingyenes is elég);
- egy **Anthropic API kulcs** (console.anthropic.com);
- **25 USD** a Play fejlesztői fiókhoz, egyszer.

Az egész két-három óra, ha minden simán megy. A 4. és az 5. szakasz között a Google
oldalán **akár 24 óra** átfutás van — arra tervezz.

---

## 0. Mielőtt bármit elindítanál: a költségkorlát

Ez az egyetlen hely, ahol pénzt tudsz veszíteni, ezért ez van elöl.

1. Lépj be: **console.anthropic.com** → *Settings* → *Billing*.
2. **Automatikus újratöltés (auto-reload): KAPCSOLD KI.** Ez az egyetlen kapcsoló,
   ami a feltöltött keretnél többet tud elkölteni.
3. Tölts fel **10–20 dollárt**. Nem vész el: ha nem fogy, ott marad.
4. *Settings* → *Limits* → állíts be **spend limitet** és e-mail értesítést alacsony
   egyenlegre.
5. *API keys* → **Create key**. Másold ki — **csak egyszer mutatja meg.**

> **Ellenőrzés:** a Billing oldalon látszik az egyenleg, és az auto-reload „off".

Miért így: az Anthropic API előre fizetős. Ha a keret elfogy, a hívások elutasításra
kerülnek — nem érkezik utólag számla. Részletek: [`MONETIZATION.md`](MONETIZATION.md).

---

## 1. A backend élesítése (Cloudflare Worker)

```bash
cd backend
npm install
npx wrangler login          # böngészőben megerősíted
```

### 1.1 Adatbázis

```bash
npx wrangler d1 create mealpilot
```

A parancs kiír egy blokkot, benne egy `database_id = "..."` sorral. **Másold be a
`backend/wrangler.toml` fájlba**, a `PASTE_D1_DATABASE_ID_HERE` helyére.

Utána a séma:

```bash
npm run db:remote
```

> **Ellenőrzés:**
> ```bash
> npx wrangler d1 execute mealpilot --remote --command "SELECT name FROM sqlite_master WHERE type='table'"
> ```
> Látnod kell a `requests`, `usage`, `subscriptions`, `reports`, `crashes`, `events`
> táblákat.

### 1.2 Titkok

```bash
npx wrangler secret put ANTHROPIC_API_KEY       # a 0. lépésben kapott kulcs
npx wrangler secret put RTDN_SHARED_SECRET      # openssl rand -hex 32
npx wrangler secret put OWNER_KEY               # openssl rand -hex 32
```

A `RTDN_SHARED_SECRET` és az `OWNER_KEY` bármilyen hosszú véletlen szöveg lehet —
**mentsd el mindkettőt**, később kell. A `PLAY_SERVICE_ACCOUNT_JSON` majd a 3. lépésben.

> Ezek a titkok **soha nem kerülnek a repóba.** A `wrangler secret put` a Cloudflare
> oldalán tárolja őket; a kódban csak a nevük szerepel.

### 1.3 Deploy

```bash
npm run deploy
```

A végén kiír egy címet: `https://mealpilot-backend.<valami>.workers.dev`.
**Ez a backend URL — írd fel, mindenhol ez kell.**

> **Ellenőrzés:**
> ```bash
> curl https://mealpilot-backend.<valami>.workers.dev/healthz
> curl https://mealpilot-backend.<valami>.workers.dev/privacy | head -5
> curl https://mealpilot-backend.<valami>.workers.dev/en/privacy | head -5
> ```
> Az első `ok`-ot ad, a másik kettő a jogi oldal HTML-jét — magyarul és angolul.
> **Innentől a Play-hez kért jogi URL-ek élnek.**

---

## 2. Play fejlesztői fiók

1. **play.google.com/console** → regisztráció, **25 USD**, egyszer.
2. Személyes fiókoknál a Google **személyazonosság-ellenőrzést** kér — ez pár napig
   is eltarthat, ezért érdemes itt kezdeni, és közben csinálni a többit.
3. Töltsd ki a **bankszámlát és az adóadatokat** (*Setup → Payments profile*).
   Enélkül a Google nem tud utalni, akkor sem, ha van bevételed.

> **Ellenőrzés:** a Console főoldalán a fiók státusza „aktív", nem „ellenőrzés alatt".

---

## 3. Előfizetés és szolgáltatásfiók

### 3.1 Az alkalmazás létrehozása

Play Console → **Create app**. Csomagnév: **`hu.mealpilot.app`** — pontosan ez, mert a
backend ezt ellenőrzi (`ANDROID_PACKAGE` a `wrangler.toml`-ben).

### 3.2 Az előfizetés terméke

*Monetize → Subscriptions → Create subscription*:

| Mező | Érték |
|---|---|
| Product ID | **`mealpilot_premium_monthly`** — pontosan ez, a kód ezt keresi |
| Név | MealPilot teljes csomag |
| Számlázási időszak | havi |
| Ár | 1 990 Ft (és a többi országra a Google javaslata) |

> **Ellenőrzés:** a termék státusza „Active". Amíg „Draft", az appban nem jelenik meg ár.

### 3.3 Szolgáltatásfiók (ettől tudja a backend, ki fizetett)

1. Play Console → *Setup → API access* → **Link** egy Google Cloud projekthez.
2. Google Cloud Console → *IAM → Service Accounts* → **Create service account**.
3. A fióknál *Keys* → **Add key → JSON** → letölt egy JSON fájlt.
4. Vissza a Play Console-ba: *API access* → a szolgáltatásfióknál **Grant access**,
   jogosultság: **View financial data** és **Manage orders and subscriptions**.
5. A Google Cloud Console-ban engedélyezd a **Google Play Android Developer API**-t.
6. A JSON-t add át a Workernek, **egy sorban**:

```bash
cat ~/Downloads/<a-letöltött>.json | tr -d '\n' | npx wrangler secret put PLAY_SERVICE_ACCOUNT_JSON
```

> **A jogosultság megadása után a Google oldalán akár 24 óra, amíg élesedik.** Ha addig
> hibát kapsz az előfizetés-ellenőrzésnél, az normális — várj, ne kezdj el javítgatni.

### 3.4 Lemondások valós időben (RTDN)

1. Google Cloud Console → *Pub/Sub* → **Create topic**, pl. `mealpilot-rtdn`.
2. A témához **Create subscription**, típus: **Push**, cél:
   `https://<a te workered>/v1/play/rtdn?secret=<RTDN_SHARED_SECRET>`
3. Play Console → *Monetize → Monetization setup* → **Real-time developer
   notifications** → a téma neve.

> Enélkül is működik minden, csak a lemondás 6 órát késik (addig él a gyorsítótár).

---

## 4. Az app buildje és feltöltése

```bash
cd android

# Kiadási kulcs, egyszer az életben. ŐRIZD MEG — enélkül soha többé nem tudsz
# frissítést kiadni ugyanahhoz az apphoz.
keytool -genkeypair -v -keystore mealpilot-release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias mealpilot
```

Csinálj egy `android/keystore.properties` fájlt (ez **gitignore-olt**, nem kerül be):

```properties
storeFile=mealpilot-release.jks
storePassword=<amit megadtál>
keyAlias=mealpilot
keyPassword=<amit megadtál>
```

Aztán a build:

```bash
MEALPILOT_BACKEND_URL=https://mealpilot-backend.<valami>.workers.dev \
  ./gradlew :app:bundleRelease
```

Az eredmény: `android/app/build/outputs/bundle/release/app-release.aab`.

> **Ha a build azt mondja, „A kiadási buildhez kell a jogi oldalak címe" — akkor
> lemaradt a `MEALPILOT_BACKEND_URL`.** Ez szándékos védelem: cím nélkül a jogi linkek
> sehova nem vinnének, és a Play elutasítaná.
>
> **Az `OWNER_KEY` szándékosan NEM kerül bele a kiadási buildbe.** Csak akkor, ha
> külön kéred (`MEALPILOT_OWNER_KEY_IN_RELEASE=1`) — a boltba feltöltött csomagba
> soha ne tedd bele.

Mentsd el a `android/app/build/outputs/mapping/release/mapping.txt` fájlt minden
kiadáshoz, és töltsd fel a Play Console-ba: enélkül az összeomlás-jelentések
olvashatatlanok.

---

## 5. Bolti megjelenés és nyilatkozatok

A szövegek készen vannak: [`STORE-LISTING.md`](STORE-LISTING.md) — magyarul és angolul,
másolható formában.

Az URL-ek (a te workered címével):

| Mező | Magyar | Angol |
|---|---|---|
| Adatvédelmi tájékoztató | `<worker>/privacy` | `<worker>/en/privacy` |
| Adattörlés | `<worker>/delete-data` | `<worker>/en/delete-data` |
| Támogatás | `<worker>/support` | `<worker>/en/support` |

A kötelező nyilatkozatok (adatbiztonsági űrlap, tartalombesorolás, generatív AI)
kitöltött válaszai: [`LAUNCH-CHECKLIST.md`](LAUNCH-CHECKLIST.md) 4.5 pont.

---

## 6. Zárt teszt, majd éles

1. *Testing → Closed testing* → új kiadás, töltsd fel az `.aab`-t.
2. Vegyél fel **legalább 12 tesztelőt** (e-mail-cím szerint), és futtasd a tesztet
   **14 napon át megszakítás nélkül**. Ez ma a személyes fiókok feltétele éles kiadás
   előtt — a Play Console megmutatja a pontos állapotot, mert a szabály időnként
   változik.
3. *Setup → License testing*: vedd fel a saját címedet. Így **valódi terhelés nélkül**
   tudod végigpróbálni a vásárlást.
4. Amit a teszt alatt nézz meg:
   - vásárlás, lemondás, visszaállítás („Korábbi vásárlás visszaállítása");
   - a lemondás tényleg elveszi-e a prémiumot (RTDN → `subscriptions` tábla);
   - az étrend mindkét nyelven;
   - és **a valódi költség**:
     ```bash
     cd backend && npx wrangler d1 execute mealpilot --remote --command \
       "SELECT sum(cost_micros)/1000000.0 AS usd, count(*) AS hivas FROM requests"
     ```
5. Ha ez megvan: *Production → Create new release*.

---

## Ha valami elromlik

| Tünet | Hol nézd |
|---|---|
| Az app „nem sikerült elérni a szolgáltatást" | `npx wrangler tail` — élő naplók |
| Az előfizetés nem aktiválódik | a 3.3 jogosultság még nem élesedett (24 óra) |
| Elfogyott a keret | `console.anthropic.com` → Billing; az app addig sablontervet ad |
| Túl sokba kerül | `PLAN_MODEL` → `claude-haiku-4-5`, vagy kisebb `FREE_OUTPUT_TOKEN_CAP`, aztán `npm run deploy` |

Egyik javításhoz sem kell app-frissítés: a modell és a plafonok környezeti változók.
