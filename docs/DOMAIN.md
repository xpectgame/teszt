# Domain és a nyilvános oldalak

A Play kötelezően kér egy **nyilvánosan elérhető adatvédelmi URL-t**, és a generatív AI
nyilatkozathoz meg a támogatáshoz is jól jön egy rendes oldal. Ez a dokumentum leírja,
mi van most, és hogyan kerül saját domainre.

---

## Ami kész van

A teljes oldal a `mealpilot/` könyvtárban van, önállóan:

```
mealpilot/
├── index.html          mit tud az app, mi ingyenes, AI-jelzés, egészségügyi figyelmeztetés
├── privacy.html        adatkezelési tájékoztató
├── terms.html          felhasználási feltételek
├── support.html        támogatás és gyakori kérdések
├── delete-data.html    adattörlés — a Play „Data deletion" URL-je
└── assets/site.css     egyetlen stíluslap, külső betűtípus és szkript nélkül
```

Nincs benne külső hivatkozás: se CDN, se webfont, se analitika. Ezek az oldalak akkor is
betöltenek, ha valami más éppen nem érhető el — egy adatvédelmi tájékoztatónál ez nem
apróság, mert a Play ellenőrzi, hogy a cím valóban él-e.

Az app a címet **fordításkor** kapja meg, tehát a domainváltáshoz nem kell kódot írni:

```bash
MEALPILOT_SITE_URL=https://<a te domained> ./gradlew :app:bundleRelease
```

Ha nem adod meg, az alapértelmezés a lenti 1. változat.

---

## 1. változat: most rögtön, ingyen (ez az alapértelmezés)

A repó GitHub Pages-oldala a `CNAME` szerint a `hernadicsaba.hu` domainen szolgál ki. Az
új könyvtár ezen belül azonnal elérhető lesz:

```
https://hernadicsaba.hu/mealpilot/privacy.html
https://hernadicsaba.hu/mealpilot/terms.html
https://hernadicsaba.hu/mealpilot/delete-data.html
https://hernadicsaba.hu/mealpilot/support.html
```

**Feltétel:** a Pages a `main` ágról épül, tehát ez csak **a `main`-be merge után** él.
Amíg a munka a `claude/...` ágon van, a címek 404-et adnak.

Ez működik, és a Play elfogadja. Két hátránya van: a domain egy másik projekt nevét
viseli, és a két oldal sorsa össze van kötve.

---

## 2. változat: saját domain

### a) Domain vásárlása

Ezt neked kell megtenned — fizetés és regisztrátori fiók kell hozzá. Magyar `.hu` domain
nagyjából 3–5 000 Ft/év, `.com` 10–15 USD/év. Bármelyik regisztrátor jó (pl. Rackhost,
Nethely, Cloudflare Registrar, Namecheap).

Névválasztásnál nézd meg, hogy a Play Áruházban nincs-e már hasonló nevű app, és hogy a
név nem ütközik-e védjegybe — egy névütközés miatti elutasítás sokkal drágább, mint egy
keresés.

### b) Külön repó a weboldalnak

**Egy GitHub Pages-oldalhoz egy egyéni domain tartozhat.** Mivel ez a repó már a
`hernadicsaba.hu`-t szolgálja ki, a MealPilot domainje **nem tehető ugyanide** — az
felülírná a másik oldalt.

Tehát:

1. Hozz létre egy új, nyilvános repót, pl. `mealpilot-site`.
2. Másold bele a `mealpilot/` könyvtár **tartalmát** a repó gyökerébe (az `index.html`
   legyen a gyökérben, ne alkönyvtárban).
3. A repó gyökerébe tegyél egy `CNAME` fájlt, amiben egyetlen sor a domained:
   ```
   mealpilot.hu
   ```
4. Settings → Pages → Source: `main` ág, `/ (root)`.
5. Settings → Pages → Custom domain: írd be a domaint, majd pipáld ki az
   **Enforce HTTPS** kapcsolót (a tanúsítvány kiállítása pár perc).

### c) DNS rekordok

Csúcsdomainhez (`mealpilot.hu`) **négy A rekord** kell — a GitHub Pages ezeket a címeket
használja:

| Típus | Név | Érték |
|---|---|---|
| A | `@` | `185.199.108.153` |
| A | `@` | `185.199.109.153` |
| A | `@` | `185.199.110.153` |
| A | `@` | `185.199.111.153` |

És a `www` alnévhez egy CNAME:

| Típus | Név | Érték |
|---|---|---|
| CNAME | `www` | `<felhasználóneved>.github.io.` |

IPv6-ot is támogató regisztrátornál érdemes a négy AAAA rekordot is felvenni
(`2606:50c0:8000::153`, `…8001::153`, `…8002::153`, `…8003::153`).

A DNS terjedése pár perctől pár óráig tart. Amíg nem áll össze, a GitHub Pages a
beállításnál hibát jelez — ez normális, nem kell újra elmenteni.

### d) Az app átállítása

```bash
MEALPILOT_SITE_URL=https://mealpilot.hu \
MEALPILOT_BACKEND_URL=https://... \
  ./gradlew :app:bundleRelease
```

Ellenőrizd az appban: Beállítások → Jogi tudnivalók és adatok, és nyisd meg mind a négy
linket. Ha valamelyik 404, azt a Play is meg fogja találni.

### e) Play Console

- Adatvédelmi tájékoztató URL: `https://<domain>/privacy.html`
- Adattörlési URL: `https://<domain>/delete-data.html`
- Támogatási webhely: `https://<domain>/support.html`

---

## Amit még ki kell tölteni a szövegekben

Mindkét jogi szövegben van egy `[…]` helyőrző, és mindkettő tetején ott a
**„Tervezet"** doboz. Ezeket kiadás előtt el kell intézni:

- **`privacy.html`, 1. pont:** az adatkezelő neve, székhelye, nyilvántartási száma.
  Magánszemélyként is kötelező név és elérhetőség; egyéni vállalkozóként vagy cégként a
  nyilvántartási/cégjegyzékszám is.
- **`terms.html`, 1. pont:** a szolgáltató neve, székhelye, adószáma.
- A „Tervezet" dobozt akkor vedd ki mindkettőből, ha egy hozzáértő átnézte.

A szövegek a **valós működést** írják le — nem sablonból másoltam őket —, de
egészségügyi témában és előfizetéses modellnél a jogi átnézés nem formalitás. Két dolog
miatt különösen: az egészségügyi adat a GDPR 9. cikke szerint különleges kategória, és a
fogyasztói elállás/visszatérítés szabályai mást mondanak, mint amit a Google Play alapból
biztosít.
