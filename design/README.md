# MealPilot — design vászon

Az app képernyőterve. A szerkeszthető vászon itt él:
**https://claude.ai/code/artifact/ef327a63-1ec4-4046-991b-9fc5a0e55744**

Ebben a könyvtárban a **forrás** van: minden `.dc.html` egy artboard, a `canvas.json`
pedig az elrendezés (oldalak, pozíciók, jegyzetek). Ezeket kell szerkeszteni — a
publikált oldal ezekből készül újra.

| Oldal | Artboardok |
|---|---|
| Képernyők | Ma, Terv, Bevásárlás, Beszéljünk, Étkezés részletei, Én |
| Belépés, csomag, értesítés | Onboarding (allergia-felmérés), Csomag, Értesítések |
| Alapok és sötét | Alapok (színek, tipográfia, komponensek), Ma sötétben |
| Irányok | B — Műszerfal, C — Tányér |

A `Main.dc.html` a belépési artboard (a „Ma" képernyő) — ez a vászon fókuszált nézete.

## Az irány

**A — „Konyhai napló".** Meleg papírszín, mély erdőzöld és agyag akcent, Newsreader
(címek és nagy számok) + Archivo (felület). A kalóriagyűrű a jelkép: nyitott gyűrű,
alul réssel, a kerethez képest váltó színnel.

A `docs/DESIGN-BRIEF.md` három dolgot nevezett meg problémának, ezekre válaszol:
rögzített paletta a Material 3 dinamikus szín helyett (ami minden telefonon máshogy néz
ki), valódi hierarchia a „minden blokk ugyanolyan kártya" helyett, és ikonok emoji helyett.

A **B** és **C** artboard két alternatíva, vázlat szinten — mindkettőnél ott az indok és
az ára. Ha valamelyik jobban tetszik, abból lesz a `Main.dc.html`.

## Mi minta és mi valódi

Valódi: a képernyők szerkezete, a sorok, a gombok, a feliratok — az `android/app/.../ui/`
kódjából. Minta: az ételnevek, a számok és a dátumok.

Szándékosan nincs rajzolva telefon-állapotsáv és billentyűzet: azokat a rendszer teszi a
layout fölé, egy odarajzolt duplán látszana.
