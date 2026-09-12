# MealPilot — design vászon

Az app képernyőterve. A szerkeszthető vászon itt él:
**https://claude.ai/code/artifact/ef327a63-1ec4-4046-991b-9fc5a0e55744**

Ebben a könyvtárban a **forrás** van: minden `.dc.html` egy artboard, a `canvas.json`
pedig az elrendezés (oldalak, pozíciók, jegyzetek). Ezeket kell szerkeszteni — a
publikált oldal ezekből készül újra.

| Oldal | Artboardok |
|---|---|
| Képernyők | Ma, Étrend, Bevásárlás, Beszéljünk, Étkezés részletei, Én |
| Belépés, csomag, értesítés | Nyelvválasztás, Kizárások, Csomag, Értesítések |
| Alapok, sötét, angol | Alapok (színek, tipográfia, forma, komponensek), Ma sötétben, Ma angolul |

A `Main.dc.html` a belépési artboard (a „Ma" képernyő) — ez a vászon fókuszált nézete.

## Mennyi valósult meg

A vászon **be van kötve** az appba. A megfeleltetés:

| Vászon | Kód |
|---|---|
| Szín (világos + sötét) | `ui/theme/Color.kt` — `Plate`, `MealColors` |
| Tipográfia | `ui/theme/Type.kt` — a betűk a `res/font/` alatt utaznak |
| Forma (30 / 26 / 19 / 16) | `ui/theme/Shape.kt` — `PlateShapes`, `PlateShape` |
| Ismétlődő elemek | `ui/components/Plate.kt` |
| Ma | `ui/screens/TodayScreen.kt` |
| Étrend | `ui/screens/PlanScreen.kt` |
| Bevásárlás | `ui/screens/ShoppingScreen.kt` |
| Beszéljünk | `ui/screens/ChatScreen.kt` |
| Étkezés részletei | `ui/screens/MealDetailScreen.kt` |
| Én | `ui/screens/ProfileScreen.kt` |
| Csomag | `ui/screens/PaywallScreen.kt` |
| Nyelvválasztás | `ui/screens/LanguageScreen.kt` |
| Kizárások, Értesítések | `ui/components/ProfileForm.kt`, `ui/screens/SettingsScreen.kt` |

Ami a vásznon **nincs**, és ezért csak a témát kapta meg (szín, betű, forma),
egyedi elrendezést nem: a Beállítások többi része és az Adatfelvétel.

A Material 3 dinamikus szín szándékosan ki van kapcsolva. Ez a vászon
létjogosultsága: bekapcsolva minden telefon a saját háttérképéből színezné az
appot, tehát a megtervezett paletta sehol nem látszana.

## Az irány

**C — „Tányér".** Krémszín alap, mély zöld hős doboz, narancs-agyag akcent, nagy
lekerekítések (30 / 26 / 19 / 16). Az étkezéseket szín különbözteti meg: reggeli narancs,
tízórai borostyán, ebéd zöld, uzsonna tégla, vacsora szilva.

A tipográfia szándékosan felnőtt — Newsreader címek, Archivo felület: a lágy formák így
nem csúsznak át gyerekesbe.

A `docs/DESIGN-BRIEF.md` három dolgot nevezett meg problémának, ezekre válaszol:
rögzített paletta a Material 3 dinamikus szín helyett (ami minden telefonon máshogy néz
ki), valódi hierarchia a „minden blokk ugyanolyan kártya" helyett, és ikonok emoji helyett.

### Az irány két ára, és mit tettem ellene

1. **Kevesebb fér a képernyőre.** A nagy lapkák miatt 3–4 étkezés látszik egyszerre, nem 6.
   Ezért került a három makró a zöld hős dobozba: aki számol, ne kelljen görgetnie érte.
2. **Könnyen átcsúszik gyerekesbe.** Ellenszer: serif címek, visszafogott árnyék, semmi
   emoji. A játékosságot az étkezésszínek adják, nem a betűk.

A korábbi A és B irányvázlat kikerült a vászonról — a döntés megszületett. A git
történetében megmaradtak.

## Mi minta és mi valódi

Valódi: a képernyők szerkezete, a sorok, a gombok, a feliratok — az `android/app/.../ui/`
kódjából. Minta: az ételnevek, a számok és a dátumok.

Szándékosan nincs rajzolva telefon-állapotsáv és billentyűzet: azokat a rendszer teszi a
layout fölé, egy odarajzolt duplán látszana.
