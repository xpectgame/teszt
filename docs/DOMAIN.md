# Hol lakjanak a jogi oldalak

A Play **kötelezően kér egy nyilvánosan elérhető adatvédelmi URL-t**, és a felülvizsgálat
során meg is nyitja. Tehát URL muszáj — de **saját weboldal és domain nem kell**.

---

## A rövid válasz: a backend szolgálja ki őket

A Worker, amit úgyis deployolsz, a jogi oldalakat is kiadja:

```
https://mealpilot-backend.<felhasználó>.workers.dev/privacy
https://mealpilot-backend.<felhasználó>.workers.dev/terms
https://mealpilot-backend.<felhasználó>.workers.dev/support
https://mealpilot-backend.<felhasználó>.workers.dev/delete-data
```

Ezt kapod vele:

- **Nincs külön tárhely és nincs domain.** Egy deploy, egy hely.
- **Nem tud szétcsúszni.** A szöveg ugyanazzal a paranccsal frissül, mint a kód.
- **Az app magától tudja a címet.** Ha megadod a `MEALPILOT_BACKEND_URL`-t, a jogi
  linkek is oda mutatnak — nem kell külön beállítani semmit.
- **Nem kell hozzá weboldal.** A repó GitHub Pages-oldala akár ki is kapcsolható.

### Hogyan működik

A `mealpilot/` könyvtár a forrás. A `npm run pages` beépíti a Workerbe
(`backend/src/pages.ts`), és a `npm run deploy` ezt magától megteszi, mielőtt kiküldi.
Ha a `mealpilot/` alatt módosítasz, de elfelejted a generálást, a
`backend/test/pages.test.ts` elbukik — tehát elavult jogi szöveg nem tud kimenni.

Az oldalak méretben elhanyagolhatók (~27 kB), és nincs bennük egyetlen külső hivatkozás
sem: se CDN, se webfont, se analitika. Egy adatvédelmi tájékoztatónál ez nem szépészeti
kérdés, mert a Play ellenőrzi, hogy a cím tényleg betölt-e.

### Play Console

- Adatvédelmi tájékoztató URL: `https://<worker>/privacy`
- Adattörlési URL: `https://<worker>/delete-data`
- Támogatási webhely: `https://<worker>/support`

---

## Mi szól mégis a saját domain mellett

Semmi kötelező. Két dolog miatt lehet később mégis érdemes:

1. **Kinézet.** A `valami.workers.dev` cím a bolti adatlapon látszik, és nem túl bizalmat
   keltő egy fizetős egészségügyi appnál.
2. **Hordozhatóság.** Ha egyszer elköltöznél a Cloudflare-ről, saját domainnel a régi
   linkek nem halnak meg.

Ha megveszed (`.hu` ~3–5 000 Ft/év, `.com` ~10–15 USD/év), a Cloudflare-en egy
**Custom Domain** hozzárendelés a Workerhez, és kész — nem kell se külön repó, se DNS
bütykölés, ha a domain amúgy is a Cloudflare-nél van:

```
Workers & Pages → mealpilot-backend → Settings → Domains & Routes → Add → Custom domain
```

Utána az appban:

```bash
MEALPILOT_SITE_URL=https://mealpilot.hu \
MEALPILOT_BACKEND_URL=https://mealpilot.hu \
  ./gradlew :app:bundleRelease
```

Névválasztásnál nézd meg, hogy a Play Áruházban nincs-e már hasonló nevű app, és hogy a
név nem ütközik-e védjegybe — egy névütközés miatti elutasítás sokkal drágább, mint egy
keresés.

---

## Tartalék: GitHub Pages

Ha valamiért mégsem a Worker szolgálná ki őket, a `mealpilot/` könyvtár önmagában is egy
kész statikus oldal, és a repó Pages-kiadása közvetlenül ki tudja adni:

```
https://<felhasznalo>.github.io/<repo>/mealpilot/privacy.html
https://<felhasznalo>.github.io/<repo>/mealpilot/en/privacy.html
```

Ez **nincs beégetve az appba**, és szándékosan nincs: egy beégetett tartalék cím csendben
túlélné a kiadást, és az appból egy olyan névtérbe mutató jogi linkek mennének ki, aminek
semmi köze a MealPilothoz. Ha ezt akarod használni, add meg kézzel:

```bash
MEALPILOT_SITE_URL=https://<felhasznalo>.github.io/<repo>/mealpilot ./gradlew :app:assembleRelease
```

Cím nélkül a jogi gombok letiltva jelennek meg, a **kiadási build pedig el sem készül** —
a Play kötelezően kéri az adatkezelési és adattörlési URL-t, és egy halott link
elutasítást jelent.

A korábbi `hernadicsaba.hu` egyéni domain a `CNAME` fájllal együtt megszűnt. Ha a
Pages-oldalt teljesen ki akarod kapcsolni: Settings → Pages → Source: *None*.

---

## Amit még ki kell tölteni a szövegekben

Mindkét jogi szövegben van egy `[…]` helyőrző, és mindkettő tetején ott a
**„Tervezet"** doboz:

- **`mealpilot/privacy.html`, 1. pont:** az adatkezelő neve, székhelye, nyilvántartási
  száma. Magánszemélyként is kötelező név és elérhetőség; egyéni vállalkozóként vagy
  cégként a nyilvántartási/cégjegyzékszám is.
- **`mealpilot/terms.html`, 1. pont:** a szolgáltató neve, székhelye, adószáma.
- A „Tervezet" dobozt akkor vedd ki mindkettőből, ha egy hozzáértő átnézte.

A szövegek a **valós működést** írják le — nem sablonból másoltam őket —, de
egészségügyi témában és előfizetéses modellnél a jogi átnézés nem formalitás. Két dolog
miatt különösen: az egészségügyi adat a GDPR 9. cikke szerint különleges kategória, és a
fogyasztói elállás/visszatérítés szabályai mást mondanak, mint amit a Google Play alapból
biztosít.

Szerkesztés után **ne felejtsd el**: `cd backend && npm run pages`.
