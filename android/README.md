# MealPilot — AI-vezérelt kalóriadeficites étrendtervező (Android)

Natív Android app, ami a testadataid alapján kiszámolja a napi kalória- és makrókeretet,
az Anthropic Claude modelljével összeállít egy tetszőleges hosszú (1 nap – 1 hónap) étrendet,
emlékeztet minden étkezésre, bevásárlólistát ír, naplózza mit ettél,
és achievementekkel jutalmazza a kitartást.

Az egész **magyar nyelvű**, és **szabad szöveggel testreszabható**: „laktózérzékeny vagyok,
nem eszem halat, hétköznap max 20 perc főzés, olcsó alapanyagok” — az AI ezt minden napra
alkalmazza, és egy már kész napot is át tudsz íratni egy mondattal.

---

## Mit tud

| Funkció | Állapot |
|---|---|
| Profil → BMR → TDEE → biztonságos deficit → makrócélok | ✅ kész, unit-tesztelt |
| Allergia- és érzékenységfelmérés, gépi ellenőrzéssel | ✅ kész, unit-tesztelt |
| AI étrend 1–30 napra, szabad szöveges kéréssel | ✅ kész |
| Terv minőség-ellenőrzés + automatikus javító kör | ✅ kész, unit-tesztelt |
| Nap átírása szavakkal („az ebéd legyen hidegen vihető”) | ✅ kész |
| Étkezési emlékeztetők, értesítésből egy koppintással naplózás | ✅ kész |
| Esti összefoglaló értesítés | ✅ kész |
| Bevásárlólista (összevont mennyiségek, bolti polcok szerint) | ✅ kész, unit-tesztelt |
| Étkezés- és súlynapló, haladáskövetés | ✅ kész |
| 22 achievement, értesítéssel | ✅ kész, unit-tesztelt |
| Offline sablontervező (AI kulcs nélkül is működik) | ✅ kész |

---

## Amit tudni kell a build előtt

A `:core` modul (minden számítás, AI-séma, promptok, bevásárlólista, achievementek)
tiszta Kotlin, és **75 unit teszt fut rá zölden**. Az app modul fordítását és az APK
építését a GitHub Actions végzi (`.github/workflows/android.yml`).

**A legegyszerűbb telepítés: nem kell hozzá Android Studio.**
Nyisd meg a repó *Actions* fülét → az „Android build" utolsó futása → *Artifacts* →
`mealpilot-debug-apk`. Csomagold ki, másold a telefonra, és nyisd meg a fájlt.
A telefon az ismeretlen forrás engedélyezését fogja kérni.

Helyben:

```bash
cd android
./gradlew :core:test          # a számítási mag tesztjei
./gradlew :app:assembleDebug  # APK: app/build/outputs/apk/debug/
./gradlew installDebug        # USB-n csatlakoztatott telefonra
```

Android Studio-ban egyszerűen nyisd meg az `android/` mappát.

Követelmények: JDK 17, Android SDK 35, minSdk 26 (Android 8.0).

---

## A tervezőmotor

A felhasználó nem konfigurál semmit: nincs modellválasztó, nincs kulcsbeviteli mező,
és a felület nem beszél „AI"-ról. Aki az appot használja, étrendet akar, nem tervezőmotort.
Egyetlen őszinte sor marad, a Névjegyben és a terv fejlécében: *„Az étrendeket gépi tervező
állítja össze a megadott adataid alapján."* Ez a Play Áruház generatív AI-ra vonatkozó
szabályzata és az EU AI Act átláthatósági elve miatt is kell, és egészségügyi témánál
egyszerűen tisztességes.

**Fejlesztéshez** a saját Anthropic kulcsod adható meg: Beállítások → Névjegy →
koppints hétszer a verziószámra. Ekkor előjön a fejlesztői rész a kulccsal, a
modellválasztással és az alaposság-szinttel. A kulcs `EncryptedSharedPreferences`-ben,
a felhőmentésből kizárva marad.

Ha a tervezőszolgáltatás nem érhető el, a beépített **sablontervező** ugrik be, és a napi
kerethez méretezett étrendet ad — csak a szabad szöveges kéréseket nem veszi figyelembe.

**Költség.** Alapértelmezés a `claude-sonnet-5` — ez tartja jól a kalóriakeretet elfogadható
áron. Egy hét étrend nagyjából egy hívás (~0,14 USD); egy hónapos terv 5 hívásra bomlik.
Saját kulcs mellett a fejlesztői részben átválthatsz Opusra vagy Haikura, illetve
állíthatod az „Alaposság” szintet. Backenden a modellt a szerver választja
(`wrangler.toml` → `PLAN_MODEL`), a kliens nem szólhat bele.

### A bolti út: saját backend

Nyilvános kiadásnál az API kulcs nem kerülhet az appba — egy fizető felhasználó nem fog
kulcsot szerezni, és amíg a jogosultságot a kliens dönti el, egy módosított app hazudhat
róla. Ezért a `MealAi` mögött két megvalósítás van:

| | mikor fut | kulcs | kvóta |
|---|---|---|---|
| `BackendMealAi` | ha a build tartalmaz backend címet | a szerveren | a szerver dönti el |
| `AnthropicMealAi` | ha megadtál saját kulcsot (fejlesztői rész) | a telefonon | nincs |
| `OfflineMealAi` | ha egyik sincs | — | — |

A sorrend: **saját kulcs → backend → offline**. Aki szándékosan megadta a sajátját, a
saját számlájára és kvóta nélkül dolgozzon; mindenki más a backenden megy.

A backend címe fordításkor kerül a buildbe:

```bash
MEALPILOT_BACKEND_URL=https://mealpilot-backend.pelda.workers.dev ./gradlew :app:assembleRelease
```

A darabolás, a tápérték-ellenőrzés, az adagok igazítása és a javító kör mindkét úton a
telefonon fut (`StreamingMealAi`) — azokhoz nem kell szerver. A backend csak azt teszi
hozzá, amit a kliens nem tud hitelesen: a kulcsot, a rendszerpromptot, az
előfizetés-ellenőrzést és a kvótát. Részletek: [`../backend/README.md`](../backend/README.md).

---

## Felépítés

```
android/
├── core/                       tiszta Kotlin — nincs Android függősége, tesztelhető
│   ├── model/                  profil, tápanyagok, napi célok
│   ├── energy/                 BMR/TDEE/deficit, kalóriakeret
│   ├── ai/                     JSON séma, promptok, tűrő parser, minőség-ellenőrzés
│   ├── shopping/               hozzávaló-összevonás, mértékegység-normalizálás
│   └── achievements/           achievement katalógus + kiértékelő
└── app/                        Android: Compose UI, Room, WorkManager, Anthropic SDK
    ├── data/local/             Room entitások, DAO-k
    ├── data/prefs/             DataStore + titkosított kulcstároló
    ├── data/ai/                tervezés menete, Anthropic SDK, backend és offline tartalék
    ├── data/remote/            a saját backend HTTP kliense
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

**A mozgás egyetlen kérdés, nem egy napló.** Volt edzésnaplózás MET-táblával és
pulzusalapú becsléssel; kikerült. Nem azért, mert rossz volt, hanem mert egy étrendtervező
appban fél funkció maradt: senki nem vezet két naplót párhuzamosan, és pont az étrend elől
vette el a helyet. Helyette a profil mozgásszint-kérdése fedi le a teljes napi mozgást, az
edzést is beleértve — ez a klasszikus szorzós módszer, és a napi keret pontossága szempontjából
alig marad el egy pontatlanul vezetett edzésnaplótól.

**Egy allergiát nem bízunk a modell jólneveltségére.** A bekapcsoláskori felmérésben
kipipált allergiák, intoleranciák és étrendi döntések nemcsak a promptba kerülnek be
kiemelt szabályként: a kész tervet gépileg is átnézzük ellenük, magyar alapanyag-kulcsszavak
alapján. Ha bármi átcsúszna, a terv nem jut el hozzád, hanem újratervezés indul a konkrét
találattal. A szűrő szándékosan a szigor felé téved — egy fölösleges újratervezés olcsó,
egy átcsúszott allergén nem. Két finomság, ami ebből következik: a kazeinallergia szigorúbb
a laktózérzékenységnél (a laktózmentes tej is tiltott marad), a vegán stílus pedig
automatikusan kizárja a tejet és a tojást akkor is, ha külön nem jelölted be.

**Az elcsúszott napot kiszámoljuk, nem újrakérjük.** Ha egy nap 2200 kcal-ra jön ki az
1932-es cél helyett, arányosan visszaveszünk az adagokból — pontosan úgy, ahogy egy
dietetikus is tenné. Ez azonnali és ingyenes, szemben egy újabb modellhívással, ami percekbe
és pénzbe kerül. A darabra mért hozzávalókhoz (tojás, gerezd fokhagyma) nem nyúlunk, mert a
„2,1 db tojás" használhatatlan utasítás, és ha a skálázás a fehérjét vinné a cél alá, akkor
marad a valódi újratervezés.

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
- Terv exportálása PDF-be, bevásárlólista megosztása
- Widget és Wear OS emlékeztető
- Összeomlás-jelentés és termékanalitika
- Play Integrity API, ha az ingyenes sáv csapolása gonddá válik
- Instrumentált UI tesztek

---

## Jogi / egészségügyi megjegyzés

Az app tájékoztató jellegű, **nem orvosi tanács**. Betegség, terhesség, szoptatás vagy
rendszeres gyógyszerszedés esetén a diétát orvossal kell egyeztetni. Az app nem enged
az alapanyagcsere alá menő kalóriabevitelt javasolni.
