# Bolti grafikák

Feltölthető fájlok a Play Console-hoz. A szövegek külön vannak:
[`../docs/STORE-LISTING.md`](../docs/STORE-LISTING.md).

| Fájl | Méret | Hova |
|---|---|---|
| `icon-512.png` | 512×512 | Play Console → App icon |
| `feature-graphic-hu.png` | 1024×500 | a magyar listához → Feature graphic |
| `feature-graphic-en.png` | 1024×500 | az angol listához → Feature graphic |

A Play az ikont **átlátszóság és saját lekerekítés nélkül** kéri — ezek tele
négyzetek, a lekerekítést a bolt maga rakja rá.

## Újragyártás

A képek HTML-ből készülnek (`src/`), Chromiummal renderelve. Ha átírsz egy szöveget
vagy egy színt, futtasd újra:

```bash
cd marketing/src
node shot.mjs '[["feature-hu.html",".banner","../feature-graphic-hu.png",1024,500],
                ["feature-en.html",".banner","../feature-graphic-en.png",1024,500],
                ["icon.html",".icon","../icon-512.png",512,900]]'
```

A `shot.mjs` a megadott elemről csinál képet — nem az ablakról. Ez azért számít, mert
így a kép mérete **pontosan** az elem mérete, nem a nézetablaké; egy elcsúszott
elrendezés nem tud észrevétlenül belefolyni a feltöltött fájlba.

Amit a szkript kiír: az elem tényleges mérete, és figyelmeztetés, ha a tartalom
túlcsordul. A dekoratív gyűrűk szándékosan lógnak ki a bannerből, azokra a
figyelmeztetés nem vonatkozik.

A `src/_mark.svg` a MealPilot jele: tányér felülnézetből, rajta levél. **Ugyanez van
az app ikonjában** (`android/app/src/main/res/drawable/ic_launcher_foreground.xml`) —
ha az egyiket átírod, írd át a másikat is, különben a boltban és a telefonon más ikon
lenne.

## Amit nem lehet előre legyártani

A **képernyőképeket** valódi appból kell kivágni, valódi adatokkal. Melyik képernyők,
milyen sorrendben, és mire figyelj: [`../docs/STORE-LISTING.md`](../docs/STORE-LISTING.md)
vége.
