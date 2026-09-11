# MealPilot — kiadási checklista

Amit a kód már tud, és amit neked kell elintézned ahhoz, hogy eladható legyen.

---

## 1. Ami a kódban készen van

### Az appban

| | |
|---|---|
| Csomagmodell | `core/billing/Tiers.kt` — ingyenes és teljes csomag, korlátok egy helyen |
| Kvóta | havi számlálók, naptári hónaponként nullázódnak, `EntitlementRepository` |
| Kényszerítés | tervezés, beszélgetés, beszélgetésből indított műveletek, terv hossza, nap átírása |
| Paywall | `PaywallScreen` — előnyök, ár a boltból, előfizetés, visszaállítás, jogi linkek |
| Play Billing | `PlayBillingGateway` — termékadatok, vásárlás, nyugtázás, visszaállítás |
| Bolt nélkül | `NoBillingGateway` — az app ilyenkor is fut, csak nem lehet előfizetni |
| Teszt kapcsoló | fejlesztői részben bekapcsolható a teljes csomag vásárlás nélkül |
| Jogi felületek | feltételek, adatkezelés, kapcsolat, „minden adat törlése" |
| **Elfogadás rögzítése** | az onboarding végén kötelező pipa, a verzió és az időpont eltárolva |
| **Tartalom jelentése** | terv, fogás és beszélgetés jelenthető; backend hiányában e-mailre esik vissza |
| **Backend kliens** | `BackendMealAi` — kulcs nélküli működés, a szerver dönt a jogosultságról |
| **Kiadási aláírás** | `signingConfigs.release`, a kulcs a repón kívülről (fájl vagy környezeti változó) |

### A backendben (`backend/`)

| | |
|---|---|
| Futtatókörnyezet | Cloudflare Workers + D1 — nincs karbantartandó szerver |
| Kulcs | az Anthropic kulcs titok a Workeren, sosem kerül ki az appba |
| Rendszerprompt | a szerveren él; a kliens csak adatot küld, így a hívás nem alakítható át |
| Előfizetés | Play Developer API (`purchases.subscriptionsv2`), 6 órás gyorsítótárral |
| Lemondás | Play valós idejű értesítések (RTDN) végpont, Pub/Sub pusholva |
| Kvóta | naptári hónaponként, szerveroldalon, az ELŐFIZETÉSHEZ kötve |
| Tokenplafon | kemény havi korlát — ez az, amit egy módosított kliens sem tud megkerülni |
| Könyvelés | hívásonként token és becsült költség a `requests` táblában |
| Bejelentések | a `reports` tábla gyűjti a jelentett terveket |

**Termékazonosító a kódban:** `mealpilot_premium_monthly`
(`app/src/main/java/hu/mealpilot/app/billing/BillingGateway.kt`)

**Az ingyenes sáv korlátai** (`Tiers.FREE` és `backend/src/limits.ts`): havi 1 étrend,
havi 10 üzenet, legfeljebb 3 napos terv, nap-átírás nincs. A kliens csak kiírja őket,
a döntést a szerver hozza — a két helyen ugyanazok a számok legyenek.

---

## 2. Backend üzembe helyezése

Részletes lépések: [`backend/README.md`](../backend/README.md). Röviden:

```bash
cd backend
npm install
npx wrangler login
npx wrangler d1 create mealpilot        # a database_id-t írd a wrangler.toml-ba
npm run db:remote
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put PLAY_SERVICE_ACCOUNT_JSON
npx wrangler secret put RTDN_SHARED_SECRET
npm run deploy
```

Aztán az app buildjébe:

```bash
MEALPILOT_BACKEND_URL=https://mealpilot-backend.pelda.workers.dev ./gradlew :app:bundleRelease
```

**Ha ez a változó nincs beállítva, az APK-ban nincs backend**, és a felhasználó offline
sablonokat kap. Kiadás előtt ezt ellenőrizd le a Beállítások → Névjegy hétszeri
megérintésével előjövő fejlesztői részben: ott kiírja, melyik tervező fut.

### Amire a backend NEM elég önmagában

A telepítési azonosítót a telefon generálja, tehát az ingyenes sávot elvileg lehet új
azonosítókkal csapolni. Ma ez ellen a szűk ingyenes keret és a tokenplafon véd. Ha a
napló alapján valaki tényleg csapolja, a következő lépés a **Play Integrity API**.

---

## 3. Kiadási aláírás

A kulcs soha nem kerül a repóba. Két út, a build mindkettőt elfogadja:

```bash
# 1. helyi fájl: android/keystore.properties (gitignore-olva)
storeFile=/home/te/mealpilot-release.jks
storePassword=...
keyAlias=mealpilot
keyPassword=...

# 2. környezeti változók (CI-hez)
MEALPILOT_KEYSTORE=/path/mealpilot-release.jks
MEALPILOT_KEYSTORE_PASSWORD=...
MEALPILOT_KEY_ALIAS=mealpilot
MEALPILOT_KEY_PASSWORD=...
```

Ha egyik sincs, az `assembleRelease` **aláíratlan** APK-t ad. Fordul, de a Play elutasítja.

Kulcs készítése:

```bash
keytool -genkeypair -v -keystore mealpilot-release.jks -alias mealpilot \
  -keyalg RSA -keysize 4096 -validity 10000
```

Ezt a fájlt tedd biztonságos helyre, és készíts róla mentést. **Play App Signing**
bekapcsolásával a Google őrzi az aláíró kulcsot, és ez a fájl csak a feltöltéshez kell —
így egy elveszett kulcs sem zárja ki a frissítésekből.

- [ ] Release keystore elkészítve, mentve
- [ ] `keystore.properties` kitöltve (és NINCS verziókezelésben)
- [ ] Play App Signing bekapcsolva
- [ ] `applicationIdSuffix = ".debug"` csak a debug buildre vonatkozik (ellenőrizve)

### Release build kipróbálása

A release build `minifyEnabled = true`. Fordítás után **próbáld ki valódi eszközön** —
az obfuszkáció a reflexiót használó részeket (Anthropic SDK, Jackson,
kotlinx.serialization, OkHttp) elronthatja. A keep szabályok készen vannak, de ezt látni kell.

```bash
MEALPILOT_BACKEND_URL=https://... ./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

- [ ] A release APK elindul
- [ ] Készít tervet (tehát a backend hívás túlélte az obfuszkációt)
- [ ] Működik a beszélgetés és a jelentés gomb

---

## 4. Play Console teendők

### 4.1 Fiók és app
- [ ] Google Play fejlesztői fiók (egyszeri 25 USD)
- [ ] Új alkalmazás, csomagnév véglegesítve (`hu.mealpilot.app`)
- [ ] Play App Signing bekapcsolva

### 4.2 Előfizetési termék
- [ ] Monetizálás → Előfizetések → új termék
- [ ] **Termékazonosító pontosan:** `mealpilot_premium_monthly`
- [ ] Alapcsomag: havi, automatikus megújulással
- [ ] Ár (javaslat: 1 990–2 990 Ft/hó)
- [ ] Opcionális: ingyenes próbaidőszak vagy bevezető ár
- [ ] Termék **aktiválása** — enélkül az appban „nem elérhető" jelenik meg

### 4.3 Szolgáltatásfiók a backendhez
- [ ] Google Cloud projekt, szolgáltatásfiók, JSON kulcs
- [ ] A fiók meghívva a Play Console-ba (pénzügyi adatok + rendelések kezelése)
- [ ] *Google Play Android Developer API* engedélyezve
- [ ] `PLAY_SERVICE_ACCOUNT_JSON` beállítva a Workeren
- [ ] Pub/Sub téma + push feliratkozás a `/v1/play/rtdn?secret=...` végpontra

A jogosultság megadása után a Play oldalán **akár 24 óra**, amíg élesedik.

### 4.4 Bolti megjelenés
- [ ] Név, rövid leírás (80 karakter), teljes leírás
- [ ] Ikon 512×512 PNG
- [ ] Funkciógrafika 1024×500
- [ ] Legalább 2, legfeljebb 8 telefonos képernyőkép
- [ ] Kategória: Egészség és fitnesz
- [ ] Nyelv: magyar (elsődleges)

### 4.5 Kötelező nyilatkozatok
- [ ] **Adatvédelmi tájékoztató URL.** A `privacy.html` és a `terms.html` a repó
      gyökerében van, de **csak a `main` ágra merge után kerül ki**, mert a GitHub Pages
      onnan épül. A Pages jelenleg a `hernadicsaba.hu` domainen szolgál ki, ami egy másik
      projekthez tartozik — **kiadás előtt MealPilot néven birtokolt domain kell**, és a
      `LegalLinks.SITE` konstanst is át kell írni (`PaywallScreen.kt`).
- [ ] **Adatbiztonság (Data safety) űrlap.** A jelenlegi működés szerinti válaszok:
  - Gyűjtünk adatot? **Igen** — „Egészség és fitnesz" (testadatok, étkezés, mozgás)
  - Megosztjuk harmadik féllel? **Igen** — a tervezőszolgáltatóval (Anthropic), a
    szolgáltatás nyújtásához. A saját backend a kérést továbbítja, de nem tárolja:
    csak tokenszám és becsült költség marad meg.
  - Titkosított továbbítás? **Igen** (HTTPS)
  - Kérhető a törlés? **Igen** — az appban egy gombbal
  - Kötelező a gyűjtés? **Igen**, a funkció működéséhez
- [ ] **Tartalom besorolása** (IARC kérdőív)
- [ ] **Célközönség:** 18+
- [ ] **Egészségügyi app nyilatkozat:** nem egészségügyi szolgáltató
- [ ] **Generatív AI tartalom:** jelöld be. A visszajelzési út **megvan a kódban**:
      Étrend → „Hibás vagy zavaró? Jelentsd.", a fogás lapján „Jelentem ezt a fogást",
      a beszélgetésben hosszan nyomva az üzenetre. A bejelentések a backend `reports`
      táblájába futnak, backend nélkül e-mailre.

### 4.6 Tesztelés
- [ ] Belső tesztelési sáv, néhány tesztelővel
- [ ] **Licenctesztelők** felvétele (Beállítások → Licenctesztelés) — csak így lehet a
      fizetést valódi terhelés nélkül végigpróbálni
- [ ] Teljes vásárlási folyamat: vásárlás, lemondás, visszaállítás, függőben lévő fizetés
- [ ] A lemondás tényleg elveszi-e a prémiumot (RTDN → `subscriptions` tábla)
- [ ] Zárt tesztelés legalább néhány napig, mielőtt élesbe megy

---

## 5. Árazás — nagyságrendek

Becslés a jelenlegi modellárakon, egy hét étrend ≈ 2k bemeneti + ~9k kimeneti token.

| Modell | Egy heti terv | Aktív felhasználó / hó |
|---|---|---|
| Sonnet 5 (alapértelmezés) | ~0,14 USD | ~0,5–0,7 USD |
| Opus 5 | ~0,70 USD | ~1,3–1,8 USD |
| Haiku 4.5 | ~0,05 USD | ~0,25 USD |

1 990 Ft/hó (~5 USD) mellett, a Play 15%-os jutaléka után is **70–90% bruttó fedezet**.
A tényleges számokat ne becsüld, hanem nézd meg — a backend minden hívást könyvel:

```bash
cd backend && npx wrangler d1 execute mealpilot --remote --command \
  "SELECT subject, sum(cost_micros)/1000000.0 AS usd, count(*) AS hivas \
   FROM requests GROUP BY subject ORDER BY usd DESC LIMIT 20"
```

---

## 6. Ami még hiányzik a kódból

- [ ] Összeomlás- és hibajelentés (pl. Crashlytics vagy Sentry) — enélkül vakon repülsz
- [ ] Alapvető termékanalitika (hány terv készül, hol morzsolódnak le a felhasználók)
- [ ] Előfizetői élmény finomhangolása: emlékeztető a próbaidőszak végéről
- [ ] Play Integrity API, ha az ingyenes sáv csapolása gonddá válik

---

## 7. Sorrend, amit javaslok

1. Backend deploy + szolgáltatásfiók + RTDN (2. és 4.3 pont)
2. Release keystore, aláírt release build kipróbálása valódi eszközön (3. pont)
3. Saját domain a jogi oldalaknak, `LegalLinks.SITE` átírása, merge a `main` ágra
4. Play Console: termék létrehozása, licenctesztelők, teljes vásárlás végigpróbálása
5. Jogi szövegek felülvizsgálata és az adatkezelő adatainak kitöltése
6. Hiányzó funkciók (6. pont)
7. Zárt teszt → éles

A jogi szövegek (`privacy.html`, `terms.html`) **tervezetek**. A működést pontosan írják
le, de közzététel előtt nézesd át valakivel, aki ért hozzá — egészségügyi témában és
előfizetéses modellnél ez nem formalitás.
