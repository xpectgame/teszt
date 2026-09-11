# MealPilot — kiadási checklista

Amit a kód már tud, és amit neked kell elintézned ahhoz, hogy eladható legyen.

---

## 1. Ami a kódban készen van

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
| Jogi oldalak | `privacy.html`, `terms.html` a repó gyökerében — a domain még beállítandó |

**Termékazonosító a kódban:** `mealpilot_premium_monthly`
(`app/src/main/java/hu/mealpilot/app/billing/BillingGateway.kt`)

**Az ingyenes sáv jelenlegi korlátai** (`Tiers.FREE`): havi 1 AI-étrend, havi 10 üzenet,
legfeljebb 3 napos terv, nap-átírás és beszélgetésből indított módosítás nincs.
Ezek egy helyen állíthatók.

---

## 2. Amit még meg KELL csinálni, mielőtt élesben pénzt kérsz

### 2.1 Backend proxy — ez a legfontosabb
Ma a modellhívás a felhasználó saját API kulcsán fut. Fizetős termékhez ez nem működik:
a felhasználó nem fog kulcsot szerezni. Kell egy vékony szerver, ami:

- a te Anthropic kulcsoddal hív, a kulcs sosem hagyja el a szervert;
- ellenőrzi, hogy a hívónak van-e érvényes előfizetése;
- számolja a kvótát **szerveroldalon** (a mostani helyi számláló megkerülhető);
- naplózza a token- és költségfelhasználást felhasználónként.

A kód elő van készítve: a `MealAi` interfész mögé egy `BackendMealAi` kerül, a felület és
az üzleti logika nem változik.

### 2.2 Előfizetés szerveroldali igazolása
A kliens ma maga dönti el, hogy előfizető-e. Éles rendszerben ezt a
**Play Developer API `purchases.subscriptions.v2.get`** hívással kell ellenőrizni a
szerveren, és érdemes bekötni a **Real-time developer notifications**-t (Pub/Sub), hogy a
lemondás, visszatérítés és felfüggesztés azonnal érvényesüljön.

### 2.3 Kiadási aláírás
A mostani build **debug kulccsal** van aláírva, és az azonosítója `hu.mealpilot.app.debug`.
Kiadáshoz kell:
- saját release keystore (biztonságos helyen, verziókezelésen kívül);
- `signingConfigs.release` + `buildTypes.release`;
- a `applicationIdSuffix = ".debug"` csak a debug buildre vonatkozik — ezt ellenőrizd;
- **Play App Signing** bekapcsolása (a Play őrzi az aláíró kulcsot).

### 2.4 Release build ellenőrzése
A release build `minifyEnabled = true`. Fordítás után **próbáld ki a release APK-t/AAB-t
valódi eszközön** — az obfuszkáció a reflexiót használó részeket (Anthropic SDK, Jackson,
kotlinx.serialization) elronthatja. A keep szabályok készen vannak, de ezt meg kell nézni.

---

## 3. Play Console teendők

### 3.1 Fiók és app
- [ ] Google Play fejlesztői fiók (egyszeri 25 USD)
- [ ] Új alkalmazás létrehozása, csomagnév véglegesítése (`hu.mealpilot.app`)
- [ ] Play App Signing bekapcsolása

### 3.2 Előfizetési termék
- [ ] Monetizálás → Előfizetések → új termék
- [ ] **Termékazonosító pontosan:** `mealpilot_premium_monthly`
- [ ] Alapcsomag: havi, automatikus megújulással
- [ ] Ár beállítása (javaslat: 1 990–2 990 Ft/hó)
- [ ] Opcionális: ingyenes próbaidőszak vagy bevezető ár
- [ ] Termék **aktiválása** — enélkül az appban „nem elérhető" jelenik meg

### 3.3 Bolti megjelenés
- [ ] Alkalmazás neve, rövid leírás (80 karakter), teljes leírás
- [ ] Ikon 512×512 PNG
- [ ] Funkciógrafika 1024×500
- [ ] Legalább 2, maximum 8 telefonos képernyőkép
- [ ] Kategória: Egészség és fitnesz
- [ ] Nyelv: magyar (elsődleges)

### 3.4 Kötelező nyilatkozatok
- [ ] **Adatvédelmi tájékoztató URL.** A `privacy.html` és a `terms.html` a repó gyökerében
      van, de **csak a `main` ágra merge után kerül ki**, mert a GitHub Pages onnan épül.
      A repó Pages-oldala jelenleg a `hernadicsaba.hu` domainen szolgál ki, ami egy másik
      projekthez tartozik — **kiadás előtt olyan domain kell, amit a MealPilot néven
      birtokolsz**, és a `LegalLinks.SITE` konstanst is át kell írni
      (`PaywallScreen.kt`).
- [ ] **Adatbiztonság (Data safety) űrlap.** A jelenlegi működés szerinti válaszok:
  - Gyűjtünk adatot? **Igen** — „Egészség és fitnesz" kategória (testadatok, étkezés, mozgás)
  - Megosztjuk harmadik féllel? **Igen** — a tervezőszolgáltatóval (Anthropic), a szolgáltatás
    nyújtásához
  - Titkosított továbbítás? **Igen** (HTTPS)
  - Kérhető a törlés? **Igen** — az appban egy gombbal
  - Kötelező a gyűjtés? **Igen**, a funkció működéséhez
- [ ] **Tartalom besorolása** (IARC kérdőív)
- [ ] **Célközönség:** 18+
- [ ] **Kormányzati/egészségügyi app nyilatkozat:** nem egészségügyi szolgáltató
- [ ] **Generatív AI tartalom:** az app tartalmaz generált tartalmat — jelöld be, és
      biztosíts visszajelzési lehetőséget a problémás tervekre *(ez a funkció még hiányzik a
      kódból, lásd 5. pont)*

### 3.5 Tesztelés
- [ ] Belső tesztelési sáv, néhány tesztelővel
- [ ] **Licenctesztelők** felvétele (Beállítások → Licenctesztelés) — csak így lehet a
      fizetést valódi terhelés nélkül végigpróbálni
- [ ] A teljes vásárlási folyamat kipróbálása: vásárlás, lemondás, visszaállítás,
      függőben lévő fizetés
- [ ] Zárt tesztelés legalább néhány napig, mielőtt élesbe megy

---

## 4. Árazás — nagyságrendek

Becslés a jelenlegi modellárakon, egy hét étrend ≈ 2k bemeneti + ~9k kimeneti token.

| Modell | Egy heti terv | Aktív felhasználó / hó |
|---|---|---|
| Sonnet 5 (alapértelmezés) | ~0,10 USD | ~0,5–0,7 USD |
| Opus 5 | ~0,30 USD | ~1,3–1,8 USD |
| Haiku 4.5 | ~0,05 USD | ~0,25 USD |

1 990 Ft/hó (~5 USD) mellett, a Play 15%-os jutaléka után is **70–90% bruttó fedezet**.
A kockázat nem az egy főre jutó költség, hanem a kvótát megkerülő visszaélés — ezért fontos
a szerveroldali kvóta (2.1).

---

## 5. Ami még hiányzik a kódból

- [ ] **„Jelentsd ezt a tervet" gomb** — a Play a generatív AI funkcióknál elvárja a
      visszajelzési utat, és neked is ez lesz az egyetlen jelzés arról, ha a tervező hibázik
- [ ] Összeomlás- és hibajelentés (pl. Crashlytics vagy Sentry) — enélkül vakon repülsz
- [ ] Alapvető termékanalitika (hány terv készül, hol morzsolódnak le a felhasználók)
- [ ] Onboarding végén a feltételek és az adatkezelés elfogadásának rögzítése
- [ ] Előfizetői élmény finomhangolása: emlékeztető a próbaidőszak végéről

---

## 6. Sorrend, amit javaslok

1. Backend proxy + szerveroldali kvóta és előfizetés-ellenőrzés (2.1, 2.2)
2. Release aláírás és release build tesztelése valódi eszközön (2.3, 2.4)
3. Play Console: termék létrehozása, licenctesztelők, a teljes vásárlás végigpróbálása
4. Jogi szövegek felülvizsgálata és az adatkezelő adatainak kitöltése
5. Hiányzó funkciók (5. pont), különösen a jelentés gomb
6. Zárt teszt → éles

A jogi szövegek (`privacy.html`, `terms.html`) **tervezetek**. A működést pontosan írják le,
de közzététel előtt nézesd át valakivel, aki ért hozzá — egészségügyi témában és
előfizetéses modellnél ez nem formalitás.
