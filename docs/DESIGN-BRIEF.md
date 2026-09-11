# MealPilot — design brief

Android app, magyar nyelvű. A kód működik, a felület viszont fejlesztői minőségű.
Ez a brief azt írja le, mit kell megtervezni és milyen keretek között.

---

## 1. Mi ez

Kalóriadeficites étrendtervező és -követő. A felhasználó megadja a testadatait, az app
kiszámolja a napi kalória- és makrócélját, majd összeállít egy 1–30 napos étrendet
recepttel és tápértékkel. Emlékeztet minden étkezésre, bevásárlólistát ír a
hozzávalókból, naplózza mit evett és mit mozgott, és követi a súlyát.

A terveket gépi tervező állítja össze. **Ezt nem hangsúlyozzuk** (lásd 3. pont), de nem is
titkoljuk: egy semleges mondat marad róla a Névjegyben és a terv fejlécében.

**Kinek:** magyar felhasználó, aki fogyni akar, de nem akar kalóriát számolni. Nem
fitneszrajongó. Nem akar tanulni egy új appot. Reggel fél szemmel nézi, hogy mit kell enni.

**A mérce:** ha a naplózás három koppintás, két hét múlva senki nem csinálja. Minden
tervezési döntést ehhez mérünk.

---

## 2. A legfontosabb képernyő

A **Ma** képernyő. Ezt nyitja meg a felhasználó naponta ötször, jellemzően fél kézzel,
menet közben. Két kérdésre kell egy pillantás alatt válaszolnia:

1. Mennyi fér még bele ma?
2. Mi a következő étkezés, és megettem-e már?

Minden más képernyő ennél ritkábban kell.

---

## 3. Amit kifejezetten kerülni kell

A mostani felület úgy néz ki, mint egy generált app. Ezt a benyomást kell megszüntetni.
Konkrétan ezek okozzák:

| Most | Miért rossz |
|---|---|
| Minden blokk azonos kártya, azonos sarokkerekítéssel, azonos árnyékkal | Nincs hierarchia — minden ugyanolyan fontosnak látszik, tehát semmi sem fontos |
| Minden választás `FilterChip` | Chip-tenger; a chip nem univerzális beviteli elem |
| Emoji ikon helyett | Platformonként más, olcsó hatású (ezt már lecseréltük ikonra, de a szemlélet maradhatott) |
| Material 3 dinamikus szín | Minden telefonon máshogy néz ki, tehát sehogy. Nincs saját arculat |
| Alapértelmezett tipográfia | Semmilyen |
| Nincs mozgás sehol | Nem érződik valódi terméknek |

**Kell helyette:** saját paletta és tipográfia, valódi hierarchia (nem minden kártya),
egy felismerhető vizuális motívum, és célzott mikroanimáció ott, ahol visszajelzés kell.

A napi kalóriagyűrű a kézenfekvő **jelkép** — ez az app egyetlen igazi vizuális ötlete,
érdemes rá építeni az arculatot. De ha van jobb ötleted, nyitottak vagyunk.

**Amit ne csinálj:** ne legyen hatalmas, görgetést igénylő hero a Ma képernyő tetején.
A tartalom az érték, nem a nyitókép.

---

## 4. Képernyők és állapotok

Minden képernyőhöz kérünk **üres, töltődő, hibás és teli** állapotot. A töltődő állapot
itt nem formalitás: a terv szakaszosan készül el, tehát valóban létezik olyan helyzet,
hogy 3 nap kész és 27 még töltődik.

### 4.1 Onboarding
Első indításkor. Egy görgethető űrlap:
- név (opcionális), biológiai nem, életkor, magasság, testsúly, célsúly, testzsír %
- napi mozgásszint (5 fokozat, hosszú magyar címkékkel)
- fogyás üteme (csúszka, 0,1–1,0 kg/hét)
- étrendi stílus (6 opció)
- **allergia- és érzékenységfelmérés: 24 tétel, 6 csoportban** — ez a leghosszabb blokk,
  és ez kíván a legtöbb tervezői figyelmet. Jelenleg chipek egymás alatt, ami hosszú
  és nehezen átlátható. Néhány tétel automatikusan bekapcsolt és nem kapcsolható ki
  (vegán stílusnál a tej), ezt valahogy jelezni kell.
- makróbeállítás, étkezésszám (2–6), étkezési időpontok
- élő előnézet a kiszámolt napi keretről, benne figyelmeztetésekkel
  (pl. „a kért ütem túl agresszív, mérsékeltem")

Ez most egyetlen hosszú lap. Lehet több lépés is, ha az jobb — de a felhasználó lássa,
hol tart és mennyi van hátra.

### 4.2 Ma
- dátumváltó (előző/következő nap)
- **napi kalóriakeret gyűrű**: bevitt / keret, a maradék nagy számmal középen,
  alatta a mozgással szerzett többlet. A gyűrű színe vált: zöld → sárga (90% felett)
  → piros (105% felett)
- 4 makrósáv: fehérje, szénhidrát, zsír, rost — mind „aktuális / cél g"
- mozgás belépő sor (mai elégetett kcal → Mozgás képernyő)
- étkezéslista: idő, étkezés típusa, fogás neve, kcal + 3 makró, és **két gyorsgomb:
  megettem / kihagytam**. Naplózott állapotban a sor kinézete változik (megevett /
  kihagyott / még nincs eldöntve), és „vissza" gombot kap.

Állapotok: nincs terv · erre a napra nincs étkezés · minden naplózva · túllépett keret.

### 4.3 Terv
- terv fejléc: cím, 2 mondatos összefoglaló, 3 adatcímke (napi kcal, hossz, fehérje),
  1–4 tipp, és a semleges mondat a gépi tervezőről
- **napkártyák**: összecsukva a dátum és a napi kcal/cél; kinyitva a nap étkezései és
  egy „Írd át szavakkal" gomb
- generálás párbeszéd: hossz (3 / 7 / 14 / 30 nap), holnaptól induljon kapcsoló,
  szabad szöveges mező („mit vegyek figyelembe?"), és a folyamatjelző
- **részlegesen kész terv**: a lista alján látszódjon, hogy még töltődnek napok

### 4.4 Bevásárlás
- tartományváltó: következő 7 nap / teljes terv
- haladás: „3 / 18 megvan" + sáv
- polcok szerint csoportosított lista (9 kategória), soronként pipa, név, mennyiség,
  és opcionális megjegyzés

Ezt boltban, egy kézzel használják. A pipálható terület legyen nagy.

### 4.5 Beszéljünk (új)
Chat felület:
- üzenetbuborékok (felhasználó / asszisztens)
- üres állapotban rövid bemutatkozás + 4 indító javaslat
- **javasolt művelet kártya**: ha az asszisztens felismer egy kérést („írd át az egész
  hetet olcsóbbra"), egy megerősítő blokk jelenik meg a válasz alatt: mit fog csinálni,
  „Csináld" / „Mégse". Ez a képernyő legfontosabb eleme — ide fut be az app minden
  szerkesztési képessége.
- „gondolkodom…" és „dolgozom rajta…" állapot, utóbbi hosszan (akár percekig) tarthat
- beviteli sáv alul, több soros meződdel és küldés gombbal

### 4.6 Én
- kalóriakeret összefoglaló: alapanyagcsere, napi felhasználás, napi cél, deficit
  + makrók + várható ütem + figyelmeztetések
- súly: beviteli mezők, „mai súly rögzítése", egyszerű oszlopdiagram az utolsó 30 mérésről,
  és a „eddig X kg-ot fogytál" sor
- **achievementek**: 22 darab, ikon + cím + leírás + haladás. Feloldott/zárolt állapot,
  három fokozat (bronz / ezüst / arany). Jelenleg egyszerű lista — lehet ennél jobb.

### 4.7 Mozgás
- 3 adatcímke (ma elégetve, 7 nap perc, 7 nap kcal)
- felvitel: keresőmező, ~45 mozgásfajta közül választás, perc, opcionális átlagpulzus
- **élő becslés**, amint gépel: „331 kcal többlet", alatta a módszer megnevezése
- mai mozgások listája, törléssel

### 4.8 Étkezés részletei
- fejléc: étkezés típusa + időpont, fogás neve, leírás
- 3 adatcímke (kcal, elkészítési idő, adag)
- részletes tápérték táblázat (7 sor + a makrókból számolt energia)
- hozzávalók polconként csoportosítva
- elkészítés lépései (max 4)
- „megettem" / „kihagytam"

### 4.9 Beállítások
Emlékeztetők (kapcsolók, két csúszka), mozgás beszámítása (csúszka), profil (a 4.1-es
űrlap újra), Névjegy. A modellválasztás és a hozzáférési kulcs rejtett fejlesztői rész.

### 4.10 Értesítések
Ezek a rendszer felületén jelennek meg, de a szövegek és az akciógombok a miénk:
- **étkezési emlékeztető**: „Ebéd: Grillcsirke barna rizzsel" + tápérték,
  három gomb: Megettem / Kihagytam / +15 perc
- esti összefoglaló: „Szép nap! A kereten belül maradtál"
- achievement feloldás

---

## 5. Technikai keretek

Ezek nem javaslatok, hanem kötöttségek.

- **Android**, Jetpack Compose, **Material 3** komponenskészlet. Ami nincs benne a
  Material 3-ban, azt meg kell építeni — jelezd, ha valami egyedi komponenst tervezel.
- **minSdk 26** (Android 8.0), célplatform Android 15.
- **Világos és sötét téma is kell.** Nem elég az egyiket megtervezni és invertálni.
- Jelenleg dinamikus szín van bekapcsolva (a telefon háttérképéből veszi a palettát).
  **Javasoljuk kikapcsolni** egy saját paletta javára — de ez a te döntésed, indokold meg.
- **Referencia szélesség 360 dp.** Ezen a szélességen kell működnie, nem 390-en.
- **Magyar szövegek**: hosszabbak az angolnál, és az összetett szavak nem törnek szépen.
  Pl. „Enyhén aktív (napi séta, álló munka)", „FODMAP-érzékenység",
  „Túrós-zabpelyhes tál áfonyával". Tervezz hosszú címkékre.
- **Dinamikus betűméret**: a felhasználó felnagyíthatja a rendszerbetűt. 200%-nál is
  működnie kell — ne legyen fix magasságú sor, amibe nem fér bele a szöveg.
- **Érintési célpont min. 48 dp.**
- **Egykezes használat**: a gyakori műveletek (naplózás, pipálás) legyenek elérhetők
  a képernyő alsó kétharmadában.
- Ikonok: Material Symbols, vagy saját készlet — ha saját, akkor **teljes** készlet kell,
  nem részleges.

---

## 6. Tartalmi kötöttségek

- Az app **nem orvosi tanács**. Ez a mondat megjelenik az onboardingon, a terv fejlécében
  és a Névjegyben — nem lehet elrejteni, de nem is kell ijesztőnek lennie.
- Egy semleges mondat marad arról, hogy a terveket gépi tervező állítja össze.
  **Ne tervezz „AI" jelvényt, csillogó ikont vagy gradienst emiatt.**
- Allergiák: ha egy kizárás aktív, annak egyértelműen látszódnia kell. Ez az egyetlen
  hely, ahol a félreértésnek egészségügyi következménye van.

---

## 7. Hangnem

Tárgyilagos, közvetlen, tegeződő. Nem lelkesítő, nem szigorú.

- Jó: „Ma 240 kcal-lal léptél túl." / „7 nap egymás után naplózva."
- Rossz: „Hajrá, meg tudod csinálni! 💪" / „Figyelem! Túllépted a keretet!"

A felhasználó felnőtt, aki tudja, mit csinál. Az app segít, nem bíztat és nem szid.

---

## 8. Leadás

- **Figma fájl** megosztható linkkel
- **Design tokenek**: szín (világos + sötét), tipográfiai skála, térköz-skála,
  sarokkerekítés, árnyék/elevation szintek — névvel, mert ezek egy az egyben átmennek
  a Compose témába
- **Komponensek** variánsokkal és állapotokkal (nyugalmi, lenyomott, letiltott, hibás)
- **Ikonkészlet** SVG-ben
- **Mozgás**: hol animálunk, mennyi ideig, milyen görbével. Elég egy rövid leírás
  komponensenként, nem kell prototípus.
- Minden képernyő **világos és sötét** változatban, 360 dp szélességen

Kérdés esetén a működő app APK-ként letölthető, és a kód is elérhető — mindkettőt szívesen
megmutatjuk, hogy lásd az összes valós állapotot.
