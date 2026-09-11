# Monetizáció — hogyan folyik a pénz, és mit nem kell előre kifizetni

Ez a leírás két kérdésre válaszol:

1. **Hogyan lesz pénz?** Ki fizet kinek, mikor, mennyit, és mi marad nálad.
2. **Mennyit kell előre kifizetned?** Pontosan mit, mikor, és mi az a mechanizmus,
   ami miatt nem érhet meglepetés.

A számok a mai modellárakon készültek (Sonnet 5: 2 USD bemenet / 10 USD kimenet millió
tokenenként). Ha az árak változnak, a `backend/src/anthropic.ts` `PRICES` tábláját kell
frissíteni — a könyvelés innen dolgozik.

---

## 1. A bevétel útja

```
felhasználó  ──Play Billing──▶  Google  ──15% jutalék levonva──▶  a te bankszámlád
                                   │
                                   └── a jogosultságot a backend a Google-tól kérdezi meg
```

1. A felhasználó az appban előfizet (`PaywallScreen` → Play Billing). A bankkártyáját
   **te sosem látod**: a fizetés a Google Play-ben történik, a Google fiókjával.
2. A Google levonja az előfizetésekre vonatkozó **15%** szolgáltatási díjat.
3. A maradékot havonta utalja a Play Console-ban megadott bankszámlára (jellemzően a
   következő hónap közepén).
4. Az EU-ban a Google az eladó (seller of record), tehát az áfát ő kezeli. A saját
   jövedelemadózásod ettől még a te dolgod — ebben könyvelőt kérdezz, nem engem.

**Nem kell hozzá:** fizetési szolgáltató, merchant account, számlázóprogram az
előfizetésekhez, áfaregisztráció országonként.

1 990 Ft/hó (~5 USD) listaáron:

| | |
|---|---|
| Amit a felhasználó fizet | ~5,00 USD |
| Google jutaléka (15%) | −0,75 USD |
| **Ami hozzád kerül** | **~4,25 USD** |
| Amibe maximum kerülhet (tokenplafon) | −4,00 USD |
| **Legrosszabb eset egy előfizetőn** | **+0,25 USD** |
| Tipikus eset egy előfizetőn | +3,3–3,7 USD |

A lényeg a „legrosszabb eset" sor: **egyetlen előfizető sem tud veszteséget termelni**,
akkor sem, ha az appot átírják és gépiesen hajtják a szervert.

---

## 2. A költség útja

```
app  ──▶  a te Workered  ──▶  Anthropic API
           │                   (előre feltöltött keretből vonódik)
           ├── jogosultság a Google-tól (6 órás gyorsítótár)
           ├── kvóta és tokenplafon, szerveroldalon
           └── minden hívás könyvelve: token + becsült költség
```

Három tétel van, és csak az egyik változó:

| Tétel | Mennyi | Mikor |
|---|---|---|
| Google Play fejlesztői fiók | **25 USD, egyszer, örökre** | regisztrációkor |
| Cloudflare Workers + D1 | **0 USD** | az ingyenes keretben (napi 100 000 kérés) |
| Saját domain | **0 USD** | nem kell: a jogi oldalakat a Worker szolgálja ki |
| Anthropic API | **amennyit feltöltesz** | előre, kredit formájában |

Tehát az **induló fix költség 25 USD**, plusz amennyi kreditet magadnak veszel.

---

## 3. Miért nem érhet meglepetés

Öt egymásra rakott fék van, és ezek közül **négy nem a jószándékon múlik**.

### 3.1 Az Anthropic API előre fizetős

A Console-ban **kreditet töltesz fel**, és abból fogy. Nem utólagos számla: ha a keret
elfogy, a hívások elutasításra kerülnek. Nem tud „összejönni" egy 800 dolláros számla,
mert nincs olyan mechanizmus, ami a feltöltött összegnél többet vonna le.

Amit állíts be indulás előtt:

- **Automatikus újratöltés (auto-reload): kikapcsolva**, vagy ha bekapcsolod, havi
  felső határral. Ez az egyetlen kapcsoló, ami ki tudja nyitni a keretet.
- **Spend limit** a workspace-en, a kényelem kedvéért — így akkor is kapsz jelzést, ha
  még van kredit.

### 3.2 A tokenplafon a szerveren van, nem az appban

A darabszám-korlátokat (havi 1 terv, 10 üzenet) egy módosított kliens megpróbálhatná
megkerülni. A kimeneti tokenplafont nem: azt a `backend/src/limits.ts` számolja, minden
hívás előtt, a D1-ben tárolt havi összeg alapján.

| Csomag | Tokenplafon / hó | Felső határ Sonnet 5-ön |
|---|---|---|
| Ingyenes | 80 000 | ~0,80 USD |
| Prémium | 400 000 | ~4,00 USD |
| Tulajdonosi (a te telefonod) | 1 500 000 | ~15,00 USD |

A tulajdonosi sáv is kap plafont. Nem azért, mert magadban nem bízol, hanem mert egy
elszabadult ciklus vagy egy kiszivárgott `OWNER_KEY` ugyanúgy költ.

### 3.3 Az ingyenes sáv szándékosan szűk

Havi **1 étrend**, legfeljebb **3 napos**, **10 üzenet**, nap-átírás nincs. Ez pont
annyi, hogy valaki eldönthesse, kell-e neki — de nem annyi, hogy ingyen használja.

### 3.4 A modell és a plafonok kódkiadás nélkül állíthatók

Mind környezeti változó a `backend/wrangler.toml`-ben:

```toml
PLAN_MODEL  = "claude-sonnet-5"   # → "claude-haiku-4-5" nagyjából felezi a költséget
FREE_OUTPUT_TOKEN_CAP    = "80000"
PREMIUM_OUTPUT_TOKEN_CAP = "400000"
```

Ha valami elszalad, egy `npx wrangler deploy` alatt átállítható. **Nem kell app-frissítés,
nem kell Play-átvizsgálás, nem kell megvárni, hogy a felhasználók frissítsenek.**

### 3.5 Minden hívás könyvelve van

Nem becsülni kell, hanem megnézni:

```bash
cd backend

# Ez a hónap összes költsége
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT sum(cost_micros)/1000000.0 AS usd, count(*) AS hivas FROM requests \
   WHERE created_at >= strftime('%s','now','start of month')*1000"

# Kik a legdrágább felhasználók
npx wrangler d1 execute mealpilot --remote --command \
  "SELECT subject, sum(cost_micros)/1000000.0 AS usd, count(*) AS hivas \
   FROM requests GROUP BY subject ORDER BY usd DESC LIMIT 20"
```

---

## 4. Mennyit fogsz fizetni, szakaszonként

### 0. szakasz — fejlesztés, még senki nem használja

| | |
|---|---|
| Play fejlesztői fiók | 25 USD, egyszer |
| Anthropic kredit | amennyit feltöltesz; **10–20 USD bőven elég** a kipróbáláshoz |
| Cloudflare | 0 USD |
| **Összesen** | **~35–45 USD egyszeri**, és utána nem fogy tovább magától |

Te a tulajdonosi kulccsal dolgozol, tehát kvóta nélkül — a tokenplafonod 1,5 millió, ami
havi ~15 USD felső határ. Valós fejlesztéshez ennek a töredéke megy el.

### 1. szakasz — zárt teszt

A Play a személyes fejlesztői fiókoknál zárt tesztet vár el éles kiadás előtt
(a mai szabály szerint 12 tesztelő, 14 napon át — ellenőrizd a Play Console-ban, mert ez
a feltétel időnként változik).

12 tesztelő, mind ingyenes sávon:

| | |
|---|---|
| Tipikus | 12 × ~0,11 USD ≈ **1,3 USD / hó** |
| Legrosszabb eset (mind kimaxolja) | 12 × 0,80 USD = **9,6 USD / hó** |

A **licenctesztelőknek** felvett fiókok ráadásul valódi terhelés nélkül próbálhatják
végig a vásárlást — tehát a fizetési folyamatot ingyen teszteled.

### 2. szakasz — éles, de még nincs előfizető

Ez az egyetlen szakasz, ahol tényleg fizetsz anélkül, hogy bevételed lenne. 100 ingyenes
felhasználónál:

| | |
|---|---|
| Tipikus | ~11 USD / hó |
| Legrosszabb eset | 80 USD / hó |

Ha ez sok, nem kell megvárni a katasztrófát: a `FREE_OUTPUT_TOKEN_CAP` lejjebb vihető,
vagy a `PLAN_MODEL` átállítható Haikura. Mindkettő egy deploy.

### 3. szakasz — előfizetőkkel

Minden előfizető **legalább 0,25 USD** hasznot hoz (a legrosszabb esetben), tipikusan
**3,3–3,7 USD**-t. Az ingyenes sáv költségét a fizetők fedezik:

| Ingyenes felhasználó | Tipikus havi költségük | Ennyi előfizető fedezi |
|---|---|---|
| 100 | ~11 USD | 3–4 |
| 500 | ~55 USD | 15–17 |
| 1 000 | ~110 USD | 30–33 |

Vagyis nagyjából **3%-os fizetős arány fölött a dolog önfenntartó**. Ez a mobilappoknál
reális, de nem garantált — ezért van a 4. szakasz.

---

## 5. Ha rosszul sül el

Sorrendben, a legkevésbé fájdalmastól:

1. **Modellváltás Haikura** — a költség nagyjából feleződik, az étrendek minősége
   kevésbé romlik, mint gondolnád (a feladat erősen strukturált).
2. **Az ingyenes tokenplafon csökkentése** — pl. 80 000 helyett 40 000.
3. **Az ingyenes sáv szűkítése** — pl. az első terv 3 nap helyett 2, vagy a
   beszélgetés csak előfizetőknek.
4. **A kredit kifogyása** — ez a végső fék, és nem töri el az appot: a tervezés
   automatikusan a **beépített receptbankra esik vissza** (`FallbackMealAi`). A
   felhasználó kap egy tervet, ami a kalóriakeretét pontosan tartja, és egy
   megjegyzést arról, hogy ez sablonból készült. A napló, a bevásárlólista, a meglévő
   tervek és a statisztika érintetlenül működnek, mert minden adat a telefonon van.
   Egy végig sablonból kirakott terv ráadásul **nem fogyasztja a felhasználó havi
   keretét** — nem fizettethetjük meg vele a mi kimaradásunkat.

Egyik lépéshez sem kell app-kiadás, és egyik sem visszafordíthatatlan.

---

## 6. Amit indulás előtt állíts be

- [ ] Anthropic Console → **auto-reload kikapcsolva** (vagy havi limittel)
- [ ] Anthropic Console → **spend limit** a workspace-en
- [ ] Anthropic Console → **e-mail értesítés** alacsony egyenlegre
- [ ] Az első feltöltés legyen kicsi (10–20 USD). Nem tudod elveszíteni: ha fogy,
      feltöltöd, ha nem fogy, ott marad.
- [ ] Play Console → bankszámla és adóadatok megadva (enélkül a Google nem tud utalni)
- [ ] Az első éles hét után nézd meg a `requests` táblát, és a *valódi* számból
      számolj tovább, ne ebből a dokumentumból

---

## 7. Ami még hiányzik, és ide tartozik

- **Próbaidőszak.** A Play tud ingyenes próbát és bevezető árat; ma egyik sincs
  beállítva. A próba növeli a konverziót, de az ingyenes sáv költségét is.
