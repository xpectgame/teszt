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
| 17 achievement, értesítéssel | ✅ kész, unit-tesztelt |
| Offline sablontervező (AI kulcs nélkül is működik) | ✅ kész |

---

## Amit tudni kell a build előtt

A `:core` modul (minden számítás, AI-séma, promptok, bevásárlólista, achievementek)
tiszta Kotlin, és **237 unit teszt fut rá zölden**. Az app modul fordítását és az APK
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
A receptbank (`:core`, `RecipeBank.kt`) 22 reggelit, 43 főételt és 18 nassolnivalót hoz
magyar, olasz, indiai, thai, japán, koreai, mexikói, marokkói, török, perzsa, spanyol,
vietnami és más konyhákból, mindegyiket recepttel és kétnyelvű hozzávalólistával.

A méret nem hiúság: a sablonokat ugyanaz a kizárásszűrő rostálja, mint a modell válaszát,
és ha nem marad belőlük semmi, a tervezés HIBÁVAL áll meg. A régi, háromreggelis bank
mindhárom reggelijére tett tejterméket, vagyis egy vegán felhasználó biztosan ebbe futott
bele. A `RecipeBankCoverageTest` ezért minden kizárásra és étrendi stílusra megméri,
mindkét nyelven, hány sablon marad kiadható — a legszűkebb profilnál is kilenc.

**Adatbázis-migrációk.** A migrációk a felhasználó telefonján futnak le először, és a
Room eltérés esetén kivétellel áll meg — az app el sem indul. A `:app` tesztek ezt nem
fogják meg: ott a Room üres adatbázisra egyben építi a sémát, a migráció le sem fut.
A `tools/check-room-migration.py` ezért a verziókezelt `<N-1>.json` sémára futtatja rá
a `AppDatabase.kt`-ből kiszedett utasításokat, és a kapott oszlopokat, indexeket és
idegen kulcsokat veti össze azzal, amit az `<N>.json` vár — ugyanazokkal a PRAGMA-kkal,
amiket a Room is használ.

**Adatkivitel.** A Beállítások jogi szakaszában, az adattörlés mellett: minden, amit az
app a felhasználóról tárol, egyetlen JSON-ban — profil, súly- és étkezésnapló, tervek a
receptekkel és hozzávalókkal, kedvencek, achievementek. Titok nincs benne, és ez nem egy
elfelejthető szűrés kérdése: az `ExportRepository` nem is fér hozzá a kulcstárolóhoz,
csak az adatbázis DAO-ihoz és a profilhoz. A fájl a gyorsítótár `export/` könyvtárába
kerül, és csak ez az egy könyvtár van megosztásra engedve (`file_paths.xml`).

**Hosszú műveletek megszakítása.** A globális haladássávon — az alsó navigáció fölött,
minden fülön — ott a Mégse. Ez azért ott van, és nem a Terv képernyő párbeszédében,
mert egy beszélgetésből indított átírás akár harminc egymás utáni modellhívás, és a
felhasználó közben bárhol lehet az appban; korábban csak várni lehetett. A napokat
átíró ciklus `ensureActive()`-ot hív: a `refineDay` a hibát `Result`-ba csomagolja,
tehát megszakítás után a ciklus különben nyugodtan végigdarálta volna mind a harminc
napot, csak épp mindet hibára.

**Kapuk a dokumentáció és a felület körül.** A README kézzel írt számait
(`check-doc-numbers.py`) és az ikonvezérlők nevét (`check-accessibility.py`) a CI
ellenőrzi. Az előbbi azonnal talált két elavult állítást: „75 unit teszt" 237 helyett,
és „22 achievement" 17 helyett. Az utóbbi szándékosan NEM tiltja a
`contentDescription = null`-t: egy szöveg mellett álló ikonnál az a helyes érték, és az
appban mind a tizenkilenc ilyen hely indokolt. Az a hiba, ha egy vezérlőnek csak ikonja
van, és az sem mondja meg, mit csinál.

**Haladás.** A profilból nyíló képernyő trendsúlyt mutat, nem nyers súlyt: a testsúly
naponta 1–2 kg-ot ugrál víztől és ételtől, amiben a heti fél kilós fogyás láthatatlan,
és aki 0,4 kg-mal többet mér a tegnapinál, azt hiszi, elrontotta. A simítás a Hacker's
Diet módszere, naptári napokban számolva: kihagyott napokon a trend ÁLL, nem interpolál
— nincs új információ, tehát nem is állítunk semmit. A célsúly dátuma a MÉRT ütemből
jön, nem a beállítottból; a kettő eltérése a leghasznosabb szám a képernyőn. Ha az ütem
hiányzik, rossz irányba mutat vagy két évnél messzebbre vinne, inkább nem mondunk
dátumot.

A két ábra formája a feladatából következik: a súly idősor (vonal a trendnek, pontok a
méréseknek), a napi bevitel mért érték egy célhoz képest (oszlopok egy vonatkoztatási
vonallal). A trendvonalat és a mérési pontokat a FORMÁJUK különbözteti meg, nem a
színük — a paletta zöldje és a halvány tinta protanópiában ΔE 2,0-ra van egymástól,
vagyis színnel jelölve egy vörös-zöld színtévesztőnek egyformák lennének.

**Kedvencek.** Egy fogás mellett a szívre koppintva a fogás bekerül a kedvencek közé —
a Ma képernyő sorában és a fogás saját oldalán is. A kedvenc MÁSOLAT, nem hivatkozás:
recepttel és hozzávalókkal együtt kerül át, és a terv törlése nem viszi el. (Hivatkozás
esetén minden új terv csendben kisöpörné az összeset, mert a `meals` sorai a tervvel
együtt törlődnek.) Az azonosság a néven áll, nem a fogás azonosítóján: ugyanaz az étel a
hét két napján két külön sor, a felhasználónak viszont egy étel.

A kedvencek nem csak lista: a tervező megkapja a legutóbbi huszonöt nevet, és beleszövi
őket a következő tervekbe ott, ahol beleférnek a napi célba és a kizárásokba. Ez ütközik
az „ezeket ne ismételd" listával, amit a hosszú tervek második hete kap — a kedvenc
ERŐSEBB nála, különben a szív ikon némán nem csinálna semmit.

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

**A deficit nem lehet akármekkora — de az ütemről a felhasználó dönt.** Két korlát
vágja vissza a kért ütemet: a napi deficit nem több a TDEE 25%-ánál, és a napi cél nem
megy a klinikai minimum (férfiaknál 1500, nőknél 1200 kcal) alá. Ha ezekbe ütközik, az
app megmondja, mennyit mérsékelt és miért.

Az alapanyagcsere szándékosan NINCS a korlátok között. Ülő életmódnál a napi felhasználás
annak csak 1,2-szerese, így az „alapanyagcsere alá soha nem tervezünk" szabály a
felhasználók nagy részét heti 0,3–0,4 kg-ra fogta vissza akkor is, ha ő fél kilót
állított be — a saját testadatai miatt, a saját döntése ellenében. Ha a cél az
alapanyagcsere alá kerül, az app elmondja, mivel jár (izomvesztés, lassuló anyagcsere,
tartsd magasan a fehérjét, hosszabb távon orvos). Korlátozás helyett tájékoztatás.

Deficitben a fehérje fix testsúlyarányos küszöb, a szénhidrát a maradék — így az
izomvesztés esélye kisebb. A fehérje referenciasúlya a zsírmentes tömeg, annak híján a
célsúly, annak híján a mai súly — de legfeljebb a BMI 25-höz tartozó súly: a felesleges
zsír nem kér fehérjét, és e nélkül a szénhidrátra alig maradt hely.

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
rendszeres gyógyszerszedés esetén a diétát orvossal kell egyeztetni. Az app nem javasol
a klinikai minimum (férfiaknál 1500, nőknél 1200 kcal) alatti kalóriabevitelt, és szól,
ha a cél az alapanyagcsere alá kerül.
