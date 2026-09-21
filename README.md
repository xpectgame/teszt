# MealPilot

Kalóriadeficites étrendtervező Androidra: a testadataid alapján kiszámolja a napi
kalória- és makrócélt, összeállít egy étrendet, bevásárlólistát ír hozzá, emlékeztet az
étkezésekre, és követi, mit ettél.

| Hol | Mi van benne |
|---|---|
| [`android/`](android/) | az alkalmazás — Kotlin, Jetpack Compose, Room ([README](android/README.md)) |
| [`backend/`](backend/) | a kiszolgáló — Cloudflare Workers + D1 ([README](backend/README.md)) |
| [`mealpilot/`](mealpilot/) | a nyilvános oldalak: adatkezelés, feltételek, támogatás, adattörlés (magyarul a gyökérben, angolul az [`en/`](mealpilot/en/) alatt) |
| [`marketing/`](marketing/) | bolti ikon és funkciógrafikák, újragyártható forrással |
| [`docs/`](docs/) | [élesítés lépésről lépésre](docs/DEPLOY.md), [bolti szövegek](docs/STORE-LISTING.md), [kiadási checklista](docs/LAUNCH-CHECKLIST.md), [monetizáció](docs/MONETIZATION.md), [domain](docs/DOMAIN.md), [designer brief](docs/DESIGN-BRIEF.md) |

Az app **magyarul és angolul** is megy: az első indításkor kérdez, később a
Beállításokban váltható. A nyelv nem csak a felületet állítja át — az étrend, a
fogásnevek, a hozzávalók és a bevásárlólista is a választott nyelven készül.

A **már elkészült terv nem fordítódik le**: a saját nyelvén marad. Ezért a terv a
nyelvét magával viszi (`plans.language`), és a későbbi szerkesztés — a fogáscsere és
a chates átírás — ezen a nyelven ír bele, nem azon, amire a felhasználó azóta
átkapcsolt. Enélkül a bevásárlólista ugyanazt a hozzávalót két sorban hozta
(„Paradicsom 300 g" és „Tomato 150 g"), mert az összevonás a névre megy. A migráció
előtt készült terveknél a mező üres: ott nincs mit tudni, és marad a felület nyelve.

A legfrissebb, telefonra telepíthető APK a
[Actions](../../actions) legutóbbi zöld futásának *Artifacts* szekciójában van.

## Jogi / egészségügyi megjegyzés

Az étrendeket gépi tervező állítja össze, és tartalmazhatnak hibát. Az alkalmazás
tájékoztató jellegű, **nem orvosi tanács**, és 18 éven felülieknek készült.
