# MealPilot — AI-vezérelt kalóriadeficites étrendtervező (Android)

Natív Android app, ami a testadataid alapján kiszámolja a napi kalória- és makrókeretet,
az Anthropic Claude modelljével összeállít egy tetszőleges hosszú (1 nap – 1 hónap) étrendet,
emlékeztet minden étkezésre, bevásárlólistát ír, naplózza mit ettél és mit mozogtál,
és achievementekkel jutalmazza a kitartást.

Az egész **magyar nyelvű**, és **szabad szöveggel testreszabható**: „laktózérzékeny vagyok,
nem eszem halat, hétköznap max 20 perc főzés, olcsó alapanyagok” — az AI ezt minden napra
alkalmazza, és egy már kész napot is át tudsz íratni egy mondattal.

---

## Mit tud

| Funkció | Állapot |
|---|---|
| Profil → BMR → TDEE → biztonságos deficit → makrócélok | ✅ kész, unit-tesztelt |
| AI étrend 1–30 napra, szabad szöveges kéréssel | ✅ kész |
| Terv minőség-ellenőrzés + automatikus javító kör | ✅ kész, unit-tesztelt |
| Nap átírása szavakkal („az ebéd legyen hidegen vihető”) | ✅ kész |
| Étkezési emlékeztetők, értesítésből egy koppintással naplózás | ✅ kész |
| Esti összefoglaló értesítés | ✅ kész |
| Bevásárlólista (összevont mennyiségek, bolti polcok szerint) | ✅ kész, unit-tesztelt |
| Étkezés- és súlynapló, haladáskövetés | ✅ kész |
| Mozgásnapló MET- és pulzusalapú kalóriabecsléssel | ✅ kész, unit-tesztelt |
| 22 achievement, értesítéssel | ✅ kész, unit-tesztelt |
| Offline sablontervező (AI kulcs nélkül is működik) | ✅ kész |

---

## Amit tudni kell a build előtt

A `:core` modul (minden számítás, AI-séma, promptok, bevásárlólista, achievementek)
tiszta Kotlin, és **59 unit teszt fut rá zölden**.

A `:app` (Android) modult **ebben a környezetben nem lehetett lefordítani**, mert a
`dl.google.com` — ahonnan az Android Gradle Plugin és az AndroidX csomagok jönnek — el van
zárva. A kód átment kézi felülvizsgálaton és statikus ellenőrzésen, de **első fordításkor
számíts apróbb javítanivalókra** (import, egy-egy Compose paraméternév). Az első
`./gradlew :app:assembleDebug` megmondja, ha van ilyen.

```bash
cd android
./gradlew :core:test          # a számítási mag tesztjei
./gradlew :app:assembleDebug  # APK: app/build/outputs/apk/debug/
```

Android Studio-ban egyszerűen nyisd meg az `android/` mappát.

Követelmények: JDK 17, Android SDK 35, minSdk 26 (Android 8.0).

---

## AI kulcs

Az app a **saját Anthropic API kulcsoddal** dolgozik (Beállítások → AI hozzáférés).
A kulcsot a `console.anthropic.com` oldalon kapod, és az eszközön marad,
`EncryptedSharedPreferences`-ben, a felhőmentésből kizárva.

Kulcs nélkül is használható: ilyenkor a beépített **offline sablontervező** áll össze
a napi kerethez méretezett étrenddé — csak a szabad szöveges kéréseket nem érti.

**Költség.** Alapértelmezés a `claude-opus-5` (a legpontosabban tartja a kalóriakeretet).
Egy hét étrend nagyjából egy hívás; egy hónapos terv 5 hívásra bomlik. Ha olcsóbb kell,
a Beállításokban átválthatsz Sonnet 5-re vagy Haiku 4.5-re, illetve lejjebb veheted az
„Alaposság” szintet.

> ⚠️ Nyilvános kiadásnál az API kulcs **nem** kerülhet az appba. Erre a `MealAi` interfész
> készült elő: ma az `AnthropicMealAi` a felhasználó kulcsával hív, előfizetéses modellhez
> ugyanez mögé egy saját backend proxy tehető, a felület és az üzleti logika érintése nélkül.

---

## Felépítés

```
android/
├── core/                       tiszta Kotlin — nincs Android függősége, tesztelhető
│   ├── model/                  profil, tápanyagok, napi célok
│   ├── energy/                 BMR/TDEE/deficit, MET-tábla, edzéskalória
│   ├── ai/                     JSON séma, promptok, tűrő parser, minőség-ellenőrzés
│   ├── shopping/               hozzávaló-összevonás, mértékegység-normalizálás
│   └── achievements/           achievement katalógus + kiértékelő
└── app/                        Android: Compose UI, Room, WorkManager, Anthropic SDK
    ├── data/local/             Room entitások, DAO-k
    ├── data/prefs/             DataStore + titkosított kulcstároló
    ├── data/ai/                Anthropic SDK hívás + offline tartalék
    ├── data/repo/              terv-, napló- és statisztika-repository
    ├── notify/                 ébresztések, értesítések, háttérmunka
    └── ui/                     Compose képernyők + ViewModelek
```

Függőséginjektálás kézzel (`AppContainer`) — ekkora appnál ez kevesebb súrlódás, mint egy DI keretrendszer.

---

## Miért így — a lényeges döntések

**A deficit nem lehet akármekkora.** A kért fogyási ütemet két korlát vágja vissza:
a napi deficit nem több a TDEE 25%-ánál, és a napi cél nem megy az alapanyagcsere
(illetve férfiaknál 1500, nőknél 1200 kcal) alá. Ha a beállítás ezekbe ütközik, az app
megmondja, mennyit mérsékelt és miért. Deficitben a fehérje fix testsúlyarányos küszöb,
a szénhidrát a maradék — így az izomvesztés esélye kisebb.

**A mozgás nem duplázódik.** Az aktivitási szorzó kifejezetten az *edzés nélküli* napi
mozgást fedi, a naplózott edzések ezen felül adódnak hozzá. Alapból az elégetett kalória
50%-a írható vissza a keretbe, mert az edzésbecslések rendszeresen felülbecsülnek
(a Beállításokban állítható 0–100% között).

**Az edzéskalória három módszerrel, pontossági sorrendben.** Ha megadsz átlagpulzust,
a Keytel-regresszió (2005) fut. Ha nem, a MET-értéket nem a tankönyvi 3,5 ml/kg/min-hez,
hanem a *te* alapanyagcserédhez skálázzuk — ez nehezebb vagy idősebb felhasználónál
érdemben kevesebbet ad, mint a szokásos képlet, és közelebb van a valósághoz.
A napi keretbe csak a nyugalmi anyagcsere fölötti *többlet* számít bele.

**Az AI válaszát nem hisszük el vakon.** Minden legenerált nap átmegy egy ellenőrzésen:
stimmel-e a napi kalória és fehérje, konzisztensek-e a makrók a megadott energiával,
van-e mindenhol hozzávaló és értelmes időpont. Ha nem, egy javító kör indul a konkrét
hibalistával. Két sikertelen kör után hibaüzenetet kapsz — nem egy rossz tervet.

**A hosszú terv hetekre bomlik.** Egy hónapnyi recept egyetlen válaszban a kimeneti
limitbe futna. Hetenként viszont látod a haladást, egy hibás hét külön újrakérhető,
és az állandó rendszerprompt cache-elve marad a hívások között (olcsóbb).

**Az emlékeztetők 36 órára előre.** Egy hónapos tervhez több száz ébresztést fenntartani
pazarlás, ezért egy háttérmunka 8 óránként újratölti a sort — és újraindítás, app-frissítés
vagy időzónaváltás után is helyreáll. Ha nincs pontos-ébresztés engedély, ±10 perces
ablakkal szól (a Play Áruház ezt az engedélyt szigorúan bírálja el).

**Naplózni az értesítésből is lehet.** „Megettem” / „Kihagytam” / „+15 perc” — app
megnyitása nélkül. Ha a naplózás nem egy koppintás, senki nem csinálja két hétnél tovább.

---

## Mi hiányzik még

- Barcode-os / adatbázisos ételkeresés terven kívüli étkezéshez (most kézi kalóriabevitel)
- Health Connect integráció (lépésszám, edzések automatikus behúzása)
- Terv exportálása PDF-be, bevásárlólista megosztása
- Widget és Wear OS emlékeztető
- Backend proxy + előfizetés (a `MealAi` interfész készen áll rá)
- Instrumentált UI tesztek

---

## Jogi / egészségügyi megjegyzés

Az app tájékoztató jellegű, **nem orvosi tanács**. Betegség, terhesség, szoptatás vagy
rendszeres gyógyszerszedés esetén a diétát orvossal kell egyeztetni. Az app nem enged
az alapanyagcsere alá menő kalóriabevitelt javasolni.
