package hu.mealpilot.core.ai

import hu.mealpilot.core.energy.EnergyBudget
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.UserProfile
import kotlin.math.roundToInt

/** Egy tervgenerálási kérés minden paramétere. */
data class PlanRequest(
    val profile: UserProfile,
    val budget: EnergyBudget,
    /** Hány napot kérünk ebben a hívásban (a hosszú terveket hetekre bontjuk). */
    val days: Int,
    /** Az első nap eltolása a teljes tervben — a modell így tud változatosságot tartani. */
    val startDayIndex: Int = 0,
    /** A teljes terv hossza napokban (kontextusnak). */
    val totalDays: Int = days,
    /** Szabad szöveges kérés a felhasználótól erre a tervre. */
    val freeText: String = "",
    /** Már felhasznált fogásnevek — ezeket ne ismételje. */
    val avoidRecipes: List<String> = emptyList(),
    /** A hét kezdőnapjának neve, hogy a hétvégére hosszabb főzés kerülhessen. */
    val startWeekdayHu: String = "hétfő",
)

object PlanPrompts {

    /**
     * Állandó rendszerprompt. Szándékosan nem tartalmaz semmi kérésfüggőt, hogy a
     * prompt cache minden hívásnál eltalálja (a változó rész a user üzenetbe megy).
     */
    val SYSTEM: String = """
Táplálkozási tervező asszisztens vagy egy magyar nyelvű mobilalkalmazásban. A feladatod
kalóriadeficites, gyakorlatban is főzhető étrendek összeállítása.

SZAKMAI SZABÁLYOK
- A napi kalória a megadott céltól legfeljebb ±5%-kal térhet el. Ez kemény korlát.
- A napi fehérje érje el a megadott célt (±10%), a zsír ne menjen a cél 80%-a alá.
- A rost napi célját törekedj elérni; a hozzáadott cukor maradjon alacsonyan.
- Soha ne javasolj a kért kalóriaszintnél alacsonyabb bevitelt, koplalást, tisztítókúrát,
  étrend-kiegészítőt vagy gyógyszert. Nem adsz orvosi tanácsot.
- Valós, hazai boltban beszerezhető alapanyagokkal dolgozz, magyar megnevezésekkel.
- A tápértékek nyers/kimért alapanyagra vonatkozzanak, és legyenek belsőleg konzisztensek:
  a fehérje×4 + szénhidrát×4 + zsír×9 essen a megadott kcal ±10%-án belül.
- Reális mennyiségeket adj (pl. "csirkemell 150 g", nem "1 adag").

GYAKORLATI SZABÁLYOK
- Változatosság: egy héten belül ugyanaz a főétel legfeljebb kétszer szerepeljen.
- Legyen ésszerű az alapanyag-újrahasznosítás: ami nagy kiszerelésben kapható, azt
  több nap használja fel, hogy ne maradjon romlandó maradék.
- A hétköznapi ebéd/vacsora max. 30 perc alatt elkészülhessen, hétvégén lehet hosszabb.
- Ha a felhasználó szabad szöveges kérést ír (utált étel, konyha, büdzsé, időkeret,
  böjt), azt MINDEN napra alkalmazd.
- A KIZÁRÁSOK szakasz mindent felülír: a kalóriacélt, a változatosságot, a büdzsét is.
  Egy kizárt alapanyag semmilyen mennyiségben, semmilyen fogásban nem jelenhet meg —
  nyomokban, ízesítőként, panírként vagy alaplében sem. Ha egy klasszikus recept
  tartalmazná, válassz másik receptet, ne pedig „elhagyható" megjegyzéssel add meg.
  Írd ki minden hozzávalónál a pontos nevet (pl. „gluténmentes tészta"), hogy
  ellenőrizhető legyen.

VÁLASZ FORMÁTUMA
Kizárólag egyetlen JSON objektummal válaszolj, magyarázó szöveg és kódkerítés nélkül.
Légy tömör: a hosszú szövegmezők csak lassítják a választ, a tápértékadatok a lényeg.
- "description": egy rövid mondat.
- "recipe_steps": legfeljebb 4 lépés, egyenként legfeljebb 12 szó.
- "summary": legfeljebb 2 mondat. "coach_notes": legfeljebb 2 tipp.
- "swap_hint": egy rövid tagmondat, vagy üres string.
Séma:
{
  "plan_title": "rövid cím",
  "summary": "legfeljebb 2 mondat a terv logikájáról",
  "days": [
    {
      "day_index": 0,
      "title": "pl. Hétfő – gyors nap",
      "note": "rövid megjegyzés a naphoz, lehet üres",
      "meals": [
        {
          "slot": "BREAKFAST|MORNING_SNACK|LUNCH|AFTERNOON_SNACK|DINNER|EVENING_SNACK",
          "time": "07:30",
          "name": "fogás neve",
          "description": "egy rövid mondat",
          "prep_minutes": 15,
          "servings": 1,
          "recipe_steps": ["rövid lépés", "rövid lépés"],
          "ingredients": [
            {
              "name": "csirkemell",
              "quantity": 150,
              "unit": "g|ml|db|ek|tk|csipet|gerezd|szelet",
              "aisle": "ZOLDSEG_GYUMOLCS|HUS_HAL|TEJTERMEK|PEKARU|SZARAZARU|FAGYASZTOTT|FUSZER|ITAL|EGYEB",
              "note": "opcionális",
              "pantry_staple": false
            }
          ],
          "nutrition": {
            "kcal": 420, "protein_g": 38, "carbs_g": 30, "fat_g": 14,
            "fiber_g": 7, "sugar_g": 5, "saturated_fat_g": 3, "sodium_mg": 480
          }
        }
      ]
    }
  ],
  "coach_notes": ["legfeljebb 2 rövid, gyakorlatias tipp"]
}

A "pantry_staple" akkor true, ha a hozzávaló jellemzően otthon van (só, bors, olaj,
ecet, alapfűszerek) — ezek nem kerülnek a bevásárlólistára.
Minden mennyiség szám legyen, ne szöveg. A "day_index" a kért tartomány szerint fusson.
""".trimIndent()

    fun userPrompt(request: PlanRequest): String {
        val p = request.profile
        val t = request.budget.target
        val sb = StringBuilder()

        sb.appendLine("PROFIL")
        sb.appendLine("- Nem: ${if (p.sex.name == "MALE") "férfi" else "nő"}, életkor: ${p.ageYears} év")
        sb.appendLine("- Magasság: ${p.heightCm.roundToInt()} cm, testsúly: ${"%.1f".format(p.weightKg)} kg" +
            (p.bodyFatPercent?.let { ", testzsír: ${"%.1f".format(it)}%" } ?: ""))
        sb.appendLine("- BMI: ${"%.1f".format(p.bmi)}")
        p.targetWeightKg?.let { sb.appendLine("- Célsúly: ${"%.1f".format(it)} kg") }
        sb.appendLine("- Napi mozgásszint edzés nélkül: ${p.activityLevel.hu}")
        sb.appendLine("- Étrendi stílus: ${p.dietStyle.hu}")
        sb.appendLine()

        val restrictions = p.effectiveRestrictions
        if (restrictions.isNotEmpty()) {
            val strict = restrictions.filter { it.severity == DietRestriction.Severity.STRICT }
            val choices = restrictions.filter { it.severity == DietRestriction.Severity.PREFERENCE }
            sb.appendLine("KIZÁRÁSOK — EZ MINDENT FELÜLÍR")
            if (strict.isNotEmpty()) {
                sb.appendLine("Allergia / intolerancia (egészségügyi kockázat, nulla tolerancia):")
                strict.forEach { sb.appendLine("- ${it.hu}: ${it.rule}") }
            }
            if (choices.isNotEmpty()) {
                sb.appendLine("Étrendi döntés:")
                choices.forEach { sb.appendLine("- ${it.hu}: ${it.rule}") }
            }
            sb.appendLine("Mielőtt válaszolsz, nézd át saját magad minden hozzávalóját e lista ellen.")
            sb.appendLine()
        }

        sb.appendLine("NAPI CÉLOK (minden napra ezek érvényesek)")
        sb.appendLine("- Kalória: ${t.kcal} kcal (max. ±5% eltérés)")
        sb.appendLine("- Fehérje: ${t.proteinG} g | Szénhidrát: ${t.carbsG} g | Zsír: ${t.fatG} g | Rost: min. ${t.fiberG} g")
        sb.appendLine("- Alapanyagcsere: ${request.budget.bmr} kcal, teljes napi felhasználás: ${request.budget.tdee} kcal")
        sb.appendLine("- Alkalmazott deficit: ${request.budget.appliedDeficit} kcal/nap " +
            "(≈ ${"%.2f".format(request.budget.expectedRateKgPerWeek)} kg/hét)")
        sb.appendLine()

        val slots = MealSlot.forMealsPerDay(p.mealsPerDay)
        sb.appendLine("ÉTKEZÉSEK NAPONTA: ${slots.size}")
        slots.forEachIndexed { i, slot ->
            val time = p.mealTimes.getOrNull(i) ?: slot.defaultTime
            sb.appendLine("- ${slot.name} (${slot.hu}) — ${time}")
        }
        sb.appendLine("Használd pontosan ezeket a slot-okat és időpontokat.")
        sb.appendLine()

        sb.appendLine("KÉRT TARTOMÁNY")
        sb.appendLine("- Generálj ${request.days} napot, day_index = " +
            "${request.startDayIndex}-től ${request.startDayIndex + request.days - 1}-ig.")
        sb.appendLine("- A teljes terv ${request.totalDays} napos, ez a ${request.startDayIndex / 7 + 1}. hét.")
        sb.appendLine("- Az első nap ${request.startWeekdayHu}.")
        sb.appendLine()

        if (p.preferences.isNotBlank()) {
            sb.appendLine("ÁLLANDÓ PREFERENCIÁK (a profilból)")
            sb.appendLine(p.preferences.trim())
            sb.appendLine()
        }
        if (request.freeText.isNotBlank()) {
            sb.appendLine("KÜLÖN KÉRÉS ERRE A TERVRE")
            sb.appendLine(request.freeText.trim())
            sb.appendLine()
        }
        if (request.avoidRecipes.isNotEmpty()) {
            sb.appendLine("MÁR SZEREPELT FOGÁSOK (ezeket ne ismételd)")
            sb.appendLine(request.avoidRecipes.distinct().take(60).joinToString(", "))
            sb.appendLine()
        }

        sb.append("Válaszolj a rendszerprompt szerinti JSON objektummal, semmi mással.")
        return sb.toString()
    }

    /** Egyetlen nap újratervezése szabad szöveges kérés alapján. */
    fun refineDayPrompt(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): String = buildString {
        val t = request.budget.target
        appendLine("Az alábbi napot kell átalakítanod a felhasználó kérése szerint.")
        appendLine()
        appendLine("JELENLEGI NAP (JSON)")
        appendLine(currentDayJson)
        appendLine()
        appendLine("A FELHASZNÁLÓ KÉRÉSE")
        appendLine(instruction.trim())
        appendLine()
        appendLine("SZABÁLYOK")
        appendLine("- A nap kalóriája maradjon ${t.kcal} kcal ±5%, a fehérje ${t.proteinG} g ±10%.")
        appendLine("- Tartsd meg a day_index értékét és az étkezések slot/időpont szerkezetét,")
        appendLine("  hacsak a kérés kifejezetten mást nem mond.")
        appendLine("- Csak azt változtasd, amit a kérés érint.")
        val restrictions = request.profile.effectiveRestrictions
        if (restrictions.isNotEmpty()) {
            appendLine("- KIZÁRÁSOK (mindent felülírnak): " +
                restrictions.joinToString("; ") { "${it.hu} — ${it.rule}" })
        }
        if (request.profile.preferences.isNotBlank()) {
            appendLine("- Állandó preferenciák: ${request.profile.preferences.trim()}")
        }
        appendLine()
        appendLine("Válaszod kizárólag ez a JSON objektum legyen:")
        appendLine("""{ "day": { ...a rendszerprompt szerinti nap objektum... }, "explanation": "1-2 mondat, mit változtattál" }""")
    }

    /**
     * Javító kör. A javító kérés önálló üzenetként megy ki (a teljes eredeti prompt után
     * fűzve), nem beszélgetés-folytatásként: így a rendszerprompt cache-e érvényben marad,
     * és nem kell visszaküldeni a hibás, sokszor több tízezer karakteres választ.
     */
    fun repairPrompt(problems: List<String>): String = buildString {
        appendLine("FIGYELEM — az előző próbálkozás ezekbe a hibákba futott, ezeket most kerüld el:")
        problems.take(12).forEach { appendLine("- $it") }
        appendLine()
        appendLine("Számold ki étkezésenként a makrókból az energiát (fehérje×4 + szénhidrát×4 + zsír×9),")
        appendLine("add össze naponta, és csak akkor válaszolj, ha a napi összeg a kért kereten belül van.")
        appendLine("A válasz továbbra is kizárólag egyetlen JSON objektum legyen, kódkerítés nélkül.")
    }
}
