# MealPilot

Kalóriadeficites étrendtervező Androidra: a testadataid alapján kiszámolja a napi
kalória- és makrócélt, összeállít egy étrendet, bevásárlólistát ír hozzá, emlékeztet az
étkezésekre, és követi, mit ettél.

| Hol | Mi van benne |
|---|---|
| [`android/`](android/) | az alkalmazás — Kotlin, Jetpack Compose, Room ([README](android/README.md)) |
| [`backend/`](backend/) | a kiszolgáló — Cloudflare Workers + D1 ([README](backend/README.md)) |
| [`mealpilot/`](mealpilot/) | a nyilvános oldalak: adatkezelés, feltételek, támogatás, adattörlés |
| [`docs/`](docs/) | [kiadási checklista](docs/LAUNCH-CHECKLIST.md), [monetizáció](docs/MONETIZATION.md), [domain](docs/DOMAIN.md), [designer brief](docs/DESIGN-BRIEF.md) |

A legfrissebb, telefonra telepíthető APK a
[Actions](../../actions) legutóbbi zöld futásának *Artifacts* szekciójában van.

## Jogi / egészségügyi megjegyzés

Az étrendeket gépi tervező állítja össze, és tartalmazhatnak hibát. Az alkalmazás
tájékoztató jellegű, **nem orvosi tanács**, és 18 éven felülieknek készült.
