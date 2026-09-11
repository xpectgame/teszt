/**
 * GENERÁLT FÁJL — ne szerkeszd kézzel.
 *
 * Forrás: a repó `mealpilot/` könyvtára. Újragenerálás:
 *
 *     cd backend && npm run pages
 *
 * A `pages.test.ts` elbukik, ha a kettő szétcsúszik, és a deploy is
 * újragenerálja, mielőtt kiküldi.
 */

export interface StaticPage {
  readonly contentType: string
  readonly body: string
}

export const PAGES: Record<string, StaticPage> = {
  'assets/site.css': {
    contentType: 'text/css; charset=utf-8',
    body: `/* MealPilot — közös stílus a jogi és tájékoztató oldalakhoz.
   Szándékosan egyetlen fájl, külső betűtípus és szkript nélkül: ezek az oldalak
   akkor is be kell töltsenek, ha a hálózat lassú vagy egy CDN éppen nem elérhető. */

:root {
  --bg: #fdfdfb;
  --fg: #1a1d17;
  --muted: #6b7263;
  --accent: #2f6b3e;
  --line: #e3e6dd;
  --card: #f5f7f1;
  --warn-bg: #fbeeec;
  --warn-line: #a32e22;
  --draft-bg: #fdf8e8;
  --draft-line: #c9a227;
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #101310;
    --fg: #e7ebe3;
    --muted: #929a8b;
    --accent: #96d6a2;
    --line: #262b24;
    --card: #171b16;
    --warn-bg: #2c1713;
    --warn-line: #ff9a88;
    --draft-bg: #2a2412;
    --draft-line: #c9a227;
  }
}

* { box-sizing: border-box; }

body {
  max-width: 46rem;
  margin: 0 auto;
  padding: 2rem 1.25rem 5rem;
  font: 16px/1.65 Georgia, "Times New Roman", serif;
  color: var(--fg);
  background: var(--bg);
}

h1 {
  font-size: 1.9rem;
  line-height: 1.15;
  margin: 0 0 .4rem;
  font-family: system-ui, -apple-system, sans-serif;
}

h2 {
  font-size: 1.05rem;
  margin: 2.2rem 0 .6rem;
  font-family: system-ui, -apple-system, sans-serif;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--accent);
}

h3 {
  font-size: 1rem;
  font-family: system-ui, -apple-system, sans-serif;
  margin: 1.4rem 0 .4rem;
}

a { color: var(--accent); }

ul { padding-left: 1.3rem; }
li { margin-bottom: .45rem; }

.meta {
  color: var(--muted);
  font-size: .9rem;
  margin-bottom: 2rem;
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: .4rem 1.1rem;
  padding-bottom: 1.4rem;
  margin-bottom: 1.6rem;
  border-bottom: 1px solid var(--line);
  font-family: system-ui, -apple-system, sans-serif;
  font-size: .88rem;
}

.nav strong { margin-right: auto; }
.nav a { text-decoration: none; }
.nav a:hover { text-decoration: underline; }

.draft {
  border-left: 3px solid var(--draft-line);
  background: var(--draft-bg);
  padding: .8rem 1rem;
  margin: 1.5rem 0;
  font-size: .92rem;
}

.warn {
  border-left: 3px solid var(--warn-line);
  background: var(--warn-bg);
  padding: .9rem 1rem;
  margin: 1.5rem 0;
}

.warn h2 { margin-top: 0; }

.card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: .6rem;
  padding: 1.1rem 1.2rem;
  margin: 1.2rem 0;
}

.card h3 { margin-top: 0; }

.lead {
  font-size: 1.1rem;
  color: var(--fg);
}

footer {
  margin-top: 4rem;
  padding-top: 1.2rem;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: .85rem;
  font-family: system-ui, -apple-system, sans-serif;
}

footer a { color: var(--muted); }

code {
  font-family: ui-monospace, "SFMono-Regular", Menlo, monospace;
  font-size: .9em;
  background: var(--card);
  padding: .1em .35em;
  border-radius: .25em;
}
`,
  },
  'delete-data.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Adatok törlése</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
</nav>

<h1>Adatok törlése</h1>
<p class="meta">MealPilot mobilalkalmazás</p>

<div class="card">
  <h3>A rövid válasz</h3>
  <p>Az alkalmazásban: <strong>Beállítások → Jogi tudnivalók és adatok → Minden adat
  törlése</strong>. Ez egy lépésben törli az étrendedet, az étkezési és testsúlynaplódat,
  a profilodat, a beállításaidat és a beszélgetést. Nincs visszavonás.</p>
  <p>Ugyanezt éri el az alkalmazás eltávolítása is, ha a készülék biztonsági mentése nincs
  bekapcsolva.</p>
</div>

<h2>Mi hol van, és mi törlődik</h2>
<ul>
  <li><strong>A készülékeden:</strong> az étrend, a naplók, a testadatok, a beállítások és
    a beszélgetés. Ezeket a fenti gomb azonnal és véglegesen törli.</li>
  <li><strong>A kiszolgálónkon:</strong> nem tárolunk étrendet, naplót vagy testadatot.
    Csak a telepítés véletlen azonosítójának lenyomata, az előfizetés állapota, a
    felhasznált keret és a hibajelentések vannak ott — ezek nem alkalmasak arra, hogy
    téged mint személyt azonosítsanak. A „Minden adat törlése" a telepítési azonosítót is
    eldobja, tehát az új azonosító már nem köthető a korábbihoz.</li>
  <li><strong>Amit te küldtél be:</strong> ha használtad a jelentés gombot, a bejelentés
    szövege nálunk marad — ez a lényege. Ennek törlését e-mailben kérheted.</li>
</ul>

<h2>Megőrzési idő</h2>
<ul>
  <li>Hibajelentések és használati számlálók: legfeljebb 12 hónap.</li>
  <li>Számlázáshoz és visszaélés-megelőzéshez tartozó kerettel kapcsolatos adatok:
    legfeljebb 24 hónap.</li>
  <li>Bejelentések: amíg a hiba kivizsgálása tart, legfeljebb 24 hónap.</li>
</ul>

<h2>Ha e-mailben kéred</h2>
<p>Írj a <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a> címre. Mivel nem
vezetünk fiókot, a kéréshez meg kell adnod, mihez tartozik: a jelentés elküldése után
kapott visszaigazolást, vagy az alkalmazásbeli bejelentés hozzávetőleges időpontját.
Legkésőbb 30 napon belül válaszolunk.</p>

<h2>Az előfizetésed</h2>
<p>Az adatok törlése <strong>nem</strong> mondja le az előfizetést — az a Google-fiókodhoz
tartozik. Lemondás: Google Play → Fizetések és előfizetések → Előfizetések.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="support.html">Támogatás</a> ·
  <a href="index.html">Főoldal</a>
</footer>

</body>
</html>
`,
  },
  'index.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — kalóriadeficites étrendtervező</title>
<meta name="description" content="A MealPilot a testadataid alapján kalóriadeficites étrendet állít össze, bevásárlólistát ír hozzá, és emlékeztet, mikor mit egyél.">
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
</nav>

<h1>MealPilot</h1>
<p class="lead">Kalóriadeficites étrend a saját testadataid alapján — bevásárlólistával,
emlékeztetőkkel és naplózással.</p>

<h2>Mit csinál</h2>
<ul>
  <li>A testsúlyodból, magasságodból, korodból és mozgásszintedből kiszámolja a napi
    kalória- és makrócélodat, és megmondja, milyen ütemben fogysz vele.</li>
  <li>Összeállít egy 3, 7, 14 vagy 30 napos étrendet, valódi, itthon beszerezhető
    alapanyagokból, részletes tápértékkel.</li>
  <li>Az étrendből bevásárlólistát ír, polcok szerint csoportosítva, a mennyiségeket
    összevonva.</li>
  <li>Emlékeztet, mikor mit egyél, és naplózza, hogy mit ettél valójában.</li>
  <li>Beszélgetve lehet módosítani rajta: étkezési időpontokat átállítani, napokat
    cserélni, allergiát hozzáadni, egy napot átíratni.</li>
</ul>

<div class="card">
  <h3>Allergiák és kizárások</h3>
  <p>Az első indításkor kiválaszthatod, mit nem ehetsz — a 14 uniós allergén, a glutén-,
  laktóz- és kazeinmentesség, a vegetáriánus és vegán étrend, valamint a fruktóz-,
  hisztamin- és FODMAP-érzékenység is szerepel közte. A kiválasztott alapanyagok nem
  kerülhetnek az étrendbe, és az app gépi ellenőrzéssel is átnézi a kész tervet.</p>
  <p><strong>Ez nem helyettesíti a csomagolás elolvasását.</strong> Súlyos allergia esetén
  a tényleges összetételt minden esetben ellenőrizd.</p>
</div>

<div class="warn">
  <h2>Nem orvosi tanács</h2>
  <p>Az étrendeket gépi tervező állítja össze a megadott adataid alapján, és ezek
  tartalmazhatnak hibát. Az alkalmazás tájékoztató jellegű, nem alkalmas betegség
  megelőzésére, diagnosztizálására vagy kezelésére. Betegség, terhesség, szoptatás,
  evészavar vagy rendszeres gyógyszerszedés esetén a diétát orvossal kell egyeztetni.
  Az alkalmazás 18 éven felülieknek készült.</p>
</div>

<h2>Mi ingyenes, mi fizetős</h2>
<p>Ami a telefonodon fut, az ingyenes marad: a naplózás, a bevásárlólista, az
emlékeztetők és a beépített receptekből készülő étrend. Havonta egy
tervezés és tíz üzenet is belefér.</p>
<p>A teljes csomag előfizetéssel jár: korlátlan tervezés és beszélgetés, 30 napos tervek,
és az egyes napok átíratása. Az előfizetést a Google Play kezeli, automatikusan megújul,
és bármikor lemondható a Play → Előfizetések menüpontban.</p>

<h2>Gépi tervezés</h2>
<p>Az étrendeket és a beszélgetés válaszait nyelvi modell állítja elő. Ez gyors és
rugalmas, de tévedhet — ezért minden tervnél és fogásnál van <strong>jelentés</strong>
gomb az alkalmazásban. Ha valami félrement, azon az úton jut el hozzánk.</p>

<h2>Kapcsolat</h2>
<p>Kérdés, hibajelentés, adatkezelési kérés:
<a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>.
Részletek a <a href="support.html">támogatási oldalon</a>.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="support.html">Támogatás</a>
</footer>

</body>
</html>
`,
  },
  'privacy.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Adatkezelési tájékoztató</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
</nav>

<h1>Adatkezelési tájékoztató</h1>
<p class="meta">MealPilot mobilalkalmazás · Hatályos: 2026. szeptember 11.</p>

<div class="draft">
  <strong>Tervezet.</strong> Ez a szöveg a valós működés alapján készült, de közzététel előtt
  jogi felülvizsgálatot igényel. Az adatkezelő adatait (név, cím, nyilvántartási szám) ki kell
  tölteni.
</div>

<h2>1. Az adatkezelő</h2>
<p>
  <em>[Adatkezelő neve, székhelye, nyilvántartási száma]</em><br>
  Kapcsolat: <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>
</p>

<h2>2. Milyen adatokat kezelünk</h2>
<p>Az alkalmazás nem kér regisztrációt, és nem hoz létre felhasználói fiókot. A következő
adatokat te magad adod meg, és alapértelmezetten a saját készülékeden maradnak:</p>
<ul>
  <li><strong>Egészségügyi és testadatok:</strong> biológiai nem, életkor, testmagasság,
    testsúly, testzsírszázalék, célsúly, mozgásszint, étrendi stílus, allergiák és
    intoleranciák.</li>
  <li><strong>Naplóadatok:</strong> elfogyasztott étkezések és azok tápértéke,
    testsúlymérések.</li>
  <li><strong>Az általad írt szabad szöveg:</strong> preferenciák és a beszélgetés üzenetei.</li>
  <li><strong>Alkalmazásbeállítások:</strong> emlékeztetők, megjelenítési beállítások.</li>
</ul>
<p>Az allergiákra és az egészségi állapotra vonatkozó adatok a GDPR 9. cikke szerinti
különleges kategóriájú adatnak minősülnek. Ezeket kizárólag a te kifejezett hozzájárulásod
alapján kezeljük, az étrend összeállítása céljából.</p>

<h2>3. Hol tároljuk</h2>
<p>Minden fent felsorolt adat a készüléked helyi adatbázisában tárolódik. Az étrendedről,
a naplóidról és a testadataidról <strong>nem készítünk szerveroldali másolatot</strong>, és
nem vezetünk felhasználói fiókot: nincs regisztráció, nincs jelszó, nincs e-mail-cím. Ha a
készülék biztonsági mentése be van kapcsolva, az adatok a saját Google-fiókod mentésébe
kerülhetnek — ez a te beállításod, nem a miénk.</p>
<p>A tervezést egy saját kiszolgálónk közvetíti (lásd 4. pont). Ez a kiszolgáló a következőket
tárolja, kizárólag a visszaélés megelőzése és a számlázás ellenőrzése céljából:</p>
<ul>
  <li>a telepítés véletlen azonosítójának egyirányú lenyomata (nem azonosít téged, és az
    adatok törlésekor vagy az app eltávolításakor újat kap);</li>
  <li>a Google Play vásárlási tokenjének egyirányú lenyomata és az előfizetés állapota;</li>
  <li>hívásonként az időpont, a feladat típusa és a felhasznált tokenek száma;</li>
  <li>havi összesítés a felhasznált keretről;</li>
  <li>hibajelentések és napi összesített használati számlálók (lásd 3/a. pont).</li>
</ul>
<p><strong>A kérés szövegét — tehát az étrendedet, az adataidat és az üzeneteidet — a
kiszolgáló nem írja le.</strong> Kivétel, ha te magad küldesz be bejelentést a „jelentés"
gombbal: ilyenkor a kifogásolt terv vagy fogás szövege is elmentésre kerül, hogy meg tudjuk
nézni, mi ment félre.</p>

<h2>3/a. Hibajelentés és névtelen statisztika</h2>
<p>Ha az alkalmazás összeomlik, a hiba leírása (a kivétel típusa, a hívási lánc, az
alkalmazás verziója, az Android verziója és a készülék típusa) a következő indításkor
elküldésre kerül a kiszolgálónkra. Emellett napi bontásban megszámoljuk, hogy hány terv,
naplóbejegyzés, üzenet és bejelentés készült.</p>
<p>Ez <strong>összesített darabszám</strong>, nem eseménynapló: nem tároljuk, mikor mi
történt, csak azt, hogy aznap hányszor. Étrend, étkezési napló, testsúly, allergia és a
beszélgetés tartalma <strong>nem</strong> kerül bele. Az adatokat nem osztjuk meg
hirdetőkkel vagy analitikai szolgáltatókkal; nem üzemeltetünk harmadik féltől származó
nyomkövetőt.</p>
<p>Ez a <strong>Beállításokban bármikor kikapcsolható</strong> („Hibajelentés és névtelen
statisztika"). Kikapcsolva az alkalmazás nem is gyűjti ezeket, nem csak a küldést hagyja
abba. A kezelés jogalapja a jogos érdek (a szolgáltatás hibamentes működtetése), amely
ellen ezzel a kapcsolóval tiltakozhatsz.</p>

<h2>4. Mi hagyja el a készüléket</h2>
<p>Étrend készítésekor és a beszélgetés használatakor a következő adatok kimennek a saját
kiszolgálónkon keresztül a tervezést végző szolgáltatóhoz (<strong>Anthropic PBC</strong>,
Egyesült Államok), hogy a terv elkészülhessen:</p>
<ul>
  <li>biológiai nem, életkor, testmagasság, testsúly, testzsírszázalék, célsúly, mozgásszint;</li>
  <li>a kiszámított kalória- és makrócélok;</li>
  <li>az étrendi kizárásaid és a szabad szöveges kéréseid;</li>
  <li>a beszélgetés üzenetei és a hozzájuk tartozó rövid összefoglaló a tervedről és a mai
    naplódról.</li>
</ul>
<p>A <strong>neved, e-mail-címed és pontos naplótörténeted nem kerül elküldésre.</strong>
Az adattovábbítás jogalapja a szerződés teljesítése (a szolgáltatás nyújtása), a különleges
kategóriájú adatok esetén a kifejezett hozzájárulásod.</p>
<p>Az Anthropic adatkezeléséről:
  <a href="https://www.anthropic.com/legal/privacy">anthropic.com/legal/privacy</a>.
  Az adattovábbítás az Egyesült Államokba az EU-USA adatvédelmi keret, illetve általános
  szerződési feltételek alapján történik.</p>

<h2>5. Fizetés</h2>
<p>Az előfizetést a Google Play kezeli. Bankkártya- és fizetési adatokhoz nem férünk hozzá,
azokat nem tároljuk. A kiszolgálónk a Google Play fejlesztői felületén keresztül azt az
információt kérdezi le, hogy az adott vásárlás érvényes-e, és ezt az állapotot tárolja
(lásd 3. pont). A Google adatkezeléséről:
  <a href="https://policies.google.com/privacy">policies.google.com/privacy</a>.</p>

<h2>6. Értesítések</h2>
<p>Az étkezési emlékeztetők a készüléken készülnek, nem szerverről érkeznek. Az értesítési
engedély bármikor visszavonható a rendszerbeállításokban.</p>

<h2>7. Meddig őrizzük meg</h2>
<p>A készüléken lévő adatok addig maradnak meg, amíg te nem törlöd őket. Az alkalmazás
eltávolítása minden helyi adatot töröl. Az alkalmazáson belül a <em>Beállítások → Jogi
tudnivalók és adatok → Minden adat törlése</em> ponttal bármikor egy lépésben törölhetsz
mindent.</p>
<p>A kiszolgálón tárolt adatok megőrzési ideje:</p>
<ul>
  <li>hibajelentések és használati számlálók: legfeljebb 12 hónap;</li>
  <li>a havi kerettel és az előfizetés állapotával kapcsolatos adatok: legfeljebb 24 hónap;</li>
  <li>az általad beküldött bejelentések: a kivizsgálás idejéig, legfeljebb 24 hónap.</li>
</ul>
<p>Részletes útmutató: <a href="delete-data.html">Adatok törlése</a>.</p>

<h2>8. Jogaid</h2>
<p>A GDPR alapján jogod van a hozzáféréshez, helyesbítéshez, törléshez, az adatkezelés
korlátozásához, az adathordozhatósághoz és a hozzájárulás visszavonásához. Mivel az adataid
a saját készülékeden vannak, ezek nagy részét közvetlenül te gyakorolod az alkalmazásban.
Bármilyen kérdéssel fordulj hozzánk a fenti e-mail-címen. Panasszal a Nemzeti Adatvédelmi és
Információszabadság Hatósághoz fordulhatsz (<a href="https://naih.hu">naih.hu</a>).</p>

<h2>9. Gyermekek</h2>
<p>Az alkalmazás 18 éven aluliak számára nem készült, és nem is gyűjtünk tudatosan adatot
tőlük.</p>

<h2>10. Változások</h2>
<p>A tájékoztató módosítása esetén a hatálybalépés dátumát frissítjük, és lényeges változásról
az alkalmazásban is tájékoztatunk.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="support.html">Támogatás</a>
</footer>

</body>
</html>
`,
  },
  'support.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Támogatás</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
</nav>

<h1>Támogatás</h1>
<p class="meta">Írj bátran — egy ember olvassa, nem egy ügyfélszolgálati rendszer.</p>

<div class="card">
  <h3>Kapcsolat</h3>
  <p><a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a></p>
  <p>Ha hibát jelentesz, segít, ha leírod: milyen telefonod van, melyik alkalmazásverziót
  használod (Beállítások → Névjegy), és mit csináltál, amikor a hiba történt.</p>
</div>

<h2>Gyakori kérdések</h2>

<h3>Hibás egy fogás vagy egy tápérték. Mit tegyek?</h3>
<p>Használd az alkalmazásban a jelentés gombot: az étrend lapján a terv alatt, a fogás
részletes nézetében alul, a beszélgetésben pedig hosszan nyomva az üzenetre. Így a
kifogásolt szöveg is elérhetővé válik, és látjuk, mi ment félre. Ez a leggyorsabb út.</p>

<h3>Hogyan mondom le az előfizetést?</h3>
<p>Google Play alkalmazás → profilkép → Fizetések és előfizetések → Előfizetések →
MealPilot → Előfizetés lemondása. A már kifizetett időszak végéig a teljes csomag
megmarad. Az alkalmazás Beállítások oldalán is van egy gomb, ami ide vezet.</p>

<h3>Visszatérítést szeretnék.</h3>
<p>A vásárlást a Google Play kezeli, így a visszatérítést is: Google Play →
Rendelési előzmények → az adott tétel → Probléma bejelentése. Ha ez nem vezet
eredményre, írj nekünk, és megnézzük, mit tehetünk.</p>

<h3>Új telefonom lett. Átjönnek az adataim?</h3>
<p>Az étrend, a napló és a testadatok a készüléken vannak, és a Google biztonsági
mentésével átkerülhetnek. Az előfizetésed a Google-fiókodhoz tartozik, tehát az új
telefonon is él: nyisd meg a Beállításokat, és nyomd meg a „Vásárlás visszaállítása"
gombot.</p>

<h3>Az étrend nem veszi figyelembe az allergiámat.</h3>
<p>Ez súlyos hiba — kérjük, jelentsd az alkalmazásból, az „Olyat ajánlott, amit kizártam"
okkal. Addig is: a kizárásokat a Beállítások → Allergiák és kizárások alatt tudod
ellenőrizni, és a következő terv már azokkal készül. <strong>Az alapanyagok tényleges
összetételét a csomagoláson mindig ellenőrizd</strong> — az alkalmazás ezt nem tudja
helyetted megtenni.</p>

<h3>Lassú a tervezés.</h3>
<p>Egy hetes terv néhány tíz másodperc, egy hónapos több perc is lehet, mert szakaszokban
készül. Az első napok azonnal használhatók, a többi a háttérben töltődik — nyugodtan
kiléphetsz az alkalmazásból közben, a munka nem szakad meg.</p>

<h3>Nem akarom, hogy hibajelentést küldjön.</h3>
<p>Beállítások → Jogi tudnivalók és adatok → „Hibajelentés és névtelen statisztika"
kapcsoló. Kikapcsolva az alkalmazás nem is gyűjti ezeket.</p>

<h3>Törölni szeretném minden adatomat.</h3>
<p>Lásd az <a href="delete-data.html">adattörlési oldalt</a>.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="index.html">Főoldal</a>
</footer>

</body>
</html>
`,
  },
  'terms.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Felhasználási feltételek</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
</nav>

<h1>Felhasználási feltételek</h1>
<p class="meta">MealPilot mobilalkalmazás · Hatályos: 2026. szeptember 11.</p>

<div class="draft">
  <strong>Tervezet.</strong> Közzététel előtt jogi felülvizsgálatot igényel. A szolgáltató
  adatait ki kell tölteni.
</div>

<h2>1. A szolgáltatás</h2>
<p>A MealPilot egy mobilalkalmazás, amely a megadott testadataid alapján kalóriacélt számol,
étrendet állít össze, bevásárlólistát készít, és segít nyomon követni az étkezéseidet és
a testsúlyodat. Szolgáltató: <em>[név, székhely, adószám]</em>.</p>

<div class="warn">
  <h2 style="margin-top:0">2. Nem orvosi tanács</h2>
  <p>Az alkalmazás tájékoztató jellegű, és <strong>nem minősül orvosi, dietetikai vagy
  egyéb egészségügyi tanácsadásnak</strong>. Az étrendeket gépi tervező állítja össze a
  megadott adataid alapján; ezek tartalmazhatnak hibát vagy pontatlanságot. Az alkalmazás
  nem alkalmas betegség megelőzésére, diagnosztizálására vagy kezelésére.</p>
  <p>Betegség, terhesség, szoptatás, evészavar, rendszeres gyógyszerszedés vagy 18 év alatti
  életkor esetén az étrend megkezdése előtt <strong>orvossal vagy dietetikussal kell
  egyeztetni</strong>. Az allergiákra vonatkozó adatok megadása a te felelősséged, és az
  alapanyagok tényleges összetételét minden esetben ellenőrizned kell a csomagoláson.</p>
</div>

<h2>3. Ki használhatja</h2>
<p>Az alkalmazás 18. életévüket betöltött személyek számára készült. A megadott adatok
valóságtartalmáért te felelsz — hibás adatokból hibás kalóriacél következik.</p>

<h2>4. Ingyenes és fizetős csomag</h2>
<p>Az alkalmazás alapfunkciói ingyenesen használhatók: naplózás, bevásárlólista,
emlékeztetők, a beépített receptekből készülő étrend, valamint havonta
meghatározott számú tervezés és üzenet.</p>
<p>A teljes csomag előfizetéssel érhető el, és korlátlan tervezést, korlátlan beszélgetést,
hosszabb terveket és az egyes napok átíratását teszi lehetővé.</p>

<h2>5. Előfizetés, megújulás, lemondás</h2>
<ul>
  <li>Az előfizetést a Google Play kezeli, és a Google Play fiókodat terheli.</li>
  <li>Az előfizetés a futamidő végén <strong>automatikusan megújul</strong>, amíg le nem mondod.</li>
  <li>A lemondás a Google Play → Előfizetések menüpontban bármikor elvégezhető, legkésőbb
    a megújulás előtt 24 órával.</li>
  <li>A már kifizetett, folyamatban lévő időszak a lemondás után is kihasználható.</li>
  <li>A visszatérítésekre a Google Play mindenkori szabályzata irányadó. Fogyasztóként az
    elállási jogodat a vonatkozó jogszabályok szerint gyakorolhatod.</li>
  <li>Az árakat megváltoztathatjuk; a változásról a Google Play szabályai szerint előre
    értesítünk, és a változás csak a következő megújulástól lép hatályba.</li>
</ul>

<h2>6. Helyes használat</h2>
<p>Nem használhatod az alkalmazást jogellenes célra, nem próbálhatod visszafejteni,
megkerülni a korlátozásait, vagy automatizált eszközökkel terhelni. A beszélgetés funkció
nem használható jogsértő, gyűlöletkeltő vagy önkárosító tartalom előállítására.</p>

<h2>7. Felelősség</h2>
<p>Az alkalmazást „adott állapotban” biztosítjuk. Nem vállalunk felelősséget azért, hogy az
étrend minden esetben hibátlan, teljes vagy az egyéni egészségi állapotodnak megfelelő. A
jogszabály által megengedett mértékig kizárjuk a felelősségünket az alkalmazás használatából
eredő közvetett károkért. Ez a korlátozás nem érinti a fogyasztókat megillető, jogszabályon
alapuló jogokat, és nem zárja ki a szándékosan vagy súlyos gondatlanságból okozott, illetve
az életet, testi épséget vagy egészséget károsító szerződésszegésért való felelősséget.</p>

<h2>8. Megszüntetés</h2>
<p>Bármikor abbahagyhatod a használatot és eltávolíthatod az alkalmazást. A feltételek súlyos
megsértése esetén a szolgáltatáshoz való hozzáférést korlátozhatjuk.</p>

<h2>9. Módosítás</h2>
<p>A feltételeket módosíthatjuk; a lényeges változásokról az alkalmazásban tájékoztatunk. A
módosítás utáni további használat a feltételek elfogadását jelenti.</p>

<h2>10. Alkalmazandó jog</h2>
<p>A feltételekre a magyar jog irányadó. Fogyasztói jogvita esetén a lakóhelyed szerinti
békéltető testülethez fordulhatsz.</p>

<h2>11. Kapcsolat</h2>
<p><a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a></p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="support.html">Támogatás</a>
</footer>

</body>
</html>
`,
  },
}
