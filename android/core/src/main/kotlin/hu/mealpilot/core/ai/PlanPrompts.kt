package hu.mealpilot.core.ai

import hu.mealpilot.core.energy.EnergyBudget
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.label
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
    /** A kezdőnap neve a TERV nyelvén, hogy a hétvégére hosszabb főzés kerülhessen. */
    val startWeekday: String = "hétfő",
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
- A mennyiségek legyenek kimérhetők: grammban és milliliterben 5-tel osztható
  számokat adj (150, 180, 75), ne 178-at. Fűszernél a néhány grammos érték rendben van.

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


    /**
     * Az angol rendszerprompt. NEM fordítás: a magyar változat magyar alapanyagokra és
     * hazai boltokra hivatkozik, ez pedig angol nyelvterületre. A szabályok viszont
     * szándékosan pontosan ugyanazok, mert a tápérték-ellenőrzés mindkettőre ugyanúgy fut.
     */
    val SYSTEM_EN: String = """
You are a meal planning assistant inside a mobile app. Your job is to build
calorie-deficit meal plans that people will actually cook.

NUTRITION RULES
- Daily calories may deviate from the target by at most ±5%. This is a hard limit.
- Daily protein must reach the target (±10%); fat must not drop below 80% of target.
- Aim for the daily fibre target; keep added sugar low.
- Never suggest eating below the requested calorie level, fasting, cleanses,
  supplements or medication. You do not give medical advice.
- Use real ingredients available in an ordinary supermarket, with everyday names.
- Nutrition values refer to raw, weighed ingredients and must be internally consistent:
  protein×4 + carbs×4 + fat×9 must land within ±10% of the stated kcal.
- Give realistic amounts (e.g. "chicken breast 150 g", not "1 portion").
- Amounts must be measurable: in grams and millilitres use numbers divisible by 5
  (150, 180, 75), not 178. A few grams of a spice is fine as it is.

PRACTICAL RULES
- Variety: the same main dish may appear at most twice in a week.
- Reuse ingredients sensibly: whatever comes in a large pack should be used across
  several days so nothing perishable is left over.
- Weekday lunches and dinners must be ready in 30 minutes; weekends may take longer.
- If the user writes a free-text request (a hated food, a cuisine, a budget, a time
  limit, fasting), apply it to EVERY day.
- The EXCLUSIONS section overrides everything: the calorie target, variety and budget
  alike. An excluded ingredient must not appear in any amount, in any dish — not as a
  trace, a seasoning, a coating or a stock. If a classic recipe would contain it,
  pick a different recipe rather than marking it "optional". Spell out the exact name
  of every ingredient (e.g. "gluten-free pasta") so it can be checked.

RESPONSE FORMAT
Reply with a single JSON object and nothing else — no prose, no code fences.
Be terse: long text fields only slow the answer down; the nutrition data is the point.
- "description": one short sentence.
- "recipe_steps": at most 4 steps, each at most 12 words.
- "summary": at most 2 sentences. "coach_notes": at most 2 tips.
- "swap_hint": one short clause, or an empty string.
Schema:
{
  "plan_title": "short title",
  "summary": "at most 2 sentences on the logic of the plan",
  "days": [
    {
      "day_index": 0,
      "title": "e.g. Monday – quick day",
      "note": "short note for the day, may be empty",
      "meals": [
        {
          "slot": "BREAKFAST|MORNING_SNACK|LUNCH|AFTERNOON_SNACK|DINNER|EVENING_SNACK",
          "time": "07:30",
          "name": "dish name",
          "description": "one short sentence",
          "prep_minutes": 15,
          "servings": 1,
          "recipe_steps": ["short step", "short step"],
          "ingredients": [
            {
              "name": "chicken breast",
              "quantity": 150,
              "unit": "g|ml|db|ek|tk|csipet|gerezd|szelet",
              "aisle": "ZOLDSEG_GYUMOLCS|HUS_HAL|TEJTERMEK|PEKARU|SZARAZARU|FAGYASZTOTT|FUSZER|ITAL|EGYEB",
              "note": "optional",
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
  "coach_notes": ["at most 2 short, practical tips"]
}

The unit and aisle codes stay exactly as listed above even in English — the app matches
on them. Set "pantry_staple" to true when the ingredient is normally already at home
(salt, pepper, oil, vinegar, basic spices) — those are left off the shopping list.
Every quantity must be a number, not text. "day_index" follows the requested range.
""".trimIndent()

    fun system(language: AppLanguage): String =
        if (language == AppLanguage.EN) SYSTEM_EN else SYSTEM

    fun userPrompt(request: PlanRequest, language: AppLanguage = AppLanguage.DEFAULT): String {
        val p = request.profile
        val t = request.budget.target
        val en = language == AppLanguage.EN
        // Egyetlen builder, nyelvenként cserélt címkékkel. Két külön függvény idővel
        // szétcsúszna: az egyikbe bekerülne egy szabály, a másikba nem.
        fun s(hu: String, english: String) = if (en) english else hu
        val sb = StringBuilder()

        sb.appendLine(s("PROFIL", "PROFILE"))
        sb.appendLine(s("- Nem: ${if (p.sex.name == "MALE") "férfi" else "nő"}, életkor: ${p.ageYears} év",
            "- Sex: ${if (p.sex.name == "MALE") "male" else "female"}, age: ${p.ageYears}"))
        sb.appendLine(s(
            "- Magasság: ${p.heightCm.roundToInt()} cm, testsúly: ${"%.1f".format(p.weightKg)} kg" +
                (p.bodyFatPercent?.let { ", testzsír: ${"%.1f".format(it)}%" } ?: ""),
            "- Height: ${p.heightCm.roundToInt()} cm, weight: ${"%.1f".format(p.weightKg)} kg" +
                (p.bodyFatPercent?.let { ", body fat: ${"%.1f".format(it)}%" } ?: "")))
        sb.appendLine("- BMI: ${"%.1f".format(p.bmi)}")
        p.targetWeightKg?.let {
            sb.appendLine(s("- Célsúly: ${"%.1f".format(it)} kg", "- Target weight: ${"%.1f".format(it)} kg"))
        }
        sb.appendLine(s("- Napi mozgásszint: ${p.activityLevel.label(language)}",
            "- Daily activity: ${p.activityLevel.label(language)}"))
        sb.appendLine(s("- Étrendi stílus: ${p.dietStyle.label(language)}",
            "- Diet style: ${p.dietStyle.label(language)}"))
        sb.appendLine()

        val restrictions = p.effectiveRestrictions
        if (restrictions.isNotEmpty()) {
            val strict = restrictions.filter { it.severity == DietRestriction.Severity.STRICT }
            val choices = restrictions.filter { it.severity == DietRestriction.Severity.PREFERENCE }
            sb.appendLine(s("KIZÁRÁSOK — EZ MINDENT FELÜLÍR", "EXCLUSIONS — THESE OVERRIDE EVERYTHING"))
            if (strict.isNotEmpty()) {
                sb.appendLine(s(
                    "Allergia / intolerancia (egészségügyi kockázat, nulla tolerancia):",
                    "Allergy / intolerance (health risk, zero tolerance):"))
                strict.forEach { sb.appendLine("- ${it.label(language)}: ${it.rule(language)}") }
            }
            if (choices.isNotEmpty()) {
                sb.appendLine(s("Étrendi döntés:", "Dietary choice:"))
                choices.forEach { sb.appendLine("- ${it.label(language)}: ${it.rule(language)}") }
            }
            sb.appendLine(s(
                "Mielőtt válaszolsz, nézd át saját magad minden hozzávalóját e lista ellen.",
                "Before you answer, check every ingredient you wrote against this list yourself."))
            sb.appendLine()
        }

        sb.appendLine(s("NAPI CÉLOK (minden napra ezek érvényesek)",
            "DAILY TARGETS (they apply to every day)"))
        sb.appendLine(s("- Kalória: ${t.kcal} kcal (max. ±5% eltérés)",
            "- Calories: ${t.kcal} kcal (±5% at most)"))
        sb.appendLine(s(
            "- Fehérje: ${t.proteinG} g | Szénhidrát: ${t.carbsG} g | Zsír: ${t.fatG} g | Rost: min. ${t.fiberG} g",
            "- Protein: ${t.proteinG} g | Carbs: ${t.carbsG} g | Fat: ${t.fatG} g | Fibre: min ${t.fiberG} g"))
        sb.appendLine(s(
            "- Alapanyagcsere: ${request.budget.bmr} kcal, teljes napi felhasználás: ${request.budget.tdee} kcal",
            "- BMR: ${request.budget.bmr} kcal, total daily expenditure: ${request.budget.tdee} kcal"))
        sb.appendLine(s(
            "- Alkalmazott deficit: ${request.budget.appliedDeficit} kcal/nap " +
                "(≈ ${"%.2f".format(request.budget.expectedRateKgPerWeek)} kg/hét)",
            "- Applied deficit: ${request.budget.appliedDeficit} kcal/day " +
                "(≈ ${"%.2f".format(request.budget.expectedRateKgPerWeek)} kg/week)"))
        sb.appendLine()

        val slots = MealSlot.forMealsPerDay(p.mealsPerDay)
        sb.appendLine(s("ÉTKEZÉSEK NAPONTA: ${slots.size}", "MEALS PER DAY: ${slots.size}"))
        slots.forEachIndexed { i, slot ->
            val time = p.mealTimes.getOrNull(i) ?: slot.defaultTime
            sb.appendLine("- ${slot.name} (${slot.label(language)}) — $time")
        }
        sb.appendLine(s("Használd pontosan ezeket a slot-okat és időpontokat.",
            "Use exactly these slots and times."))
        sb.appendLine()

        sb.appendLine(s("KÉRT TARTOMÁNY", "REQUESTED RANGE"))
        sb.appendLine(s(
            "- Generálj ${request.days} napot, day_index = " +
                "${request.startDayIndex}-től ${request.startDayIndex + request.days - 1}-ig.",
            "- Generate ${request.days} days, day_index from " +
                "${request.startDayIndex} to ${request.startDayIndex + request.days - 1}."))
        sb.appendLine(s(
            "- A teljes terv ${request.totalDays} napos, ez a ${request.startDayIndex / 7 + 1}. hét.",
            "- The whole plan is ${request.totalDays} days; this is week ${request.startDayIndex / 7 + 1}."))
        sb.appendLine(s("- Az első nap ${request.startWeekday}.",
            "- The first day is a ${request.startWeekday}."))
        sb.appendLine()

        if (p.preferences.isNotBlank()) {
            sb.appendLine(s("ÁLLANDÓ PREFERENCIÁK (a profilból)", "STANDING PREFERENCES (from the profile)"))
            sb.appendLine(p.preferences.trim())
            sb.appendLine()
        }
        if (request.freeText.isNotBlank()) {
            sb.appendLine(s("KÜLÖN KÉRÉS ERRE A TERVRE", "SPECIAL REQUEST FOR THIS PLAN"))
            sb.appendLine(request.freeText.trim())
            sb.appendLine()
        }
        if (request.avoidRecipes.isNotEmpty()) {
            sb.appendLine(s("MÁR SZEREPELT FOGÁSOK (ezeket ne ismételd)",
                "DISHES ALREADY USED (do not repeat these)"))
            sb.appendLine(request.avoidRecipes.distinct().take(60).joinToString(", "))
            sb.appendLine()
        }

        sb.append(s("Válaszolj a rendszerprompt szerinti JSON objektummal, semmi mással.",
            "Reply with the JSON object from the system prompt, and nothing else."))
        return sb.toString()
    }

    /** Egyetlen nap újratervezése szabad szöveges kérés alapján. */
    fun refineDayPrompt(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
        language: AppLanguage = AppLanguage.DEFAULT,
    ): String = buildString {
        val t = request.budget.target
        val en = language == AppLanguage.EN
        fun s(hu: String, english: String) = if (en) english else hu

        appendLine(s("Az alábbi napot kell átalakítanod a felhasználó kérése szerint.",
            "Rework the day below according to the user's request."))
        appendLine()
        appendLine(s("JELENLEGI NAP (JSON)", "CURRENT DAY (JSON)"))
        appendLine(currentDayJson)
        appendLine()
        appendLine(s("A FELHASZNÁLÓ KÉRÉSE", "THE USER'S REQUEST"))
        appendLine(instruction.trim())
        appendLine()
        appendLine(s("SZABÁLYOK", "RULES"))
        appendLine(s(
            "- A nap kalóriája maradjon ${t.kcal} kcal ±5%, a fehérje ${t.proteinG} g ±10%.",
            "- Keep the day at ${t.kcal} kcal ±5% and protein at ${t.proteinG} g ±10%."))
        appendLine(s(
            "- Tartsd meg a day_index értékét és az étkezések slot/időpont szerkezetét,",
            "- Keep the day_index and the slot/time structure of the meals,"))
        appendLine(s("  hacsak a kérés kifejezetten mást nem mond.",
            "  unless the request explicitly says otherwise."))
        appendLine(s("- Csak azt változtasd, amit a kérés érint.",
            "- Change only what the request touches."))
        val restrictions = request.profile.effectiveRestrictions
        if (restrictions.isNotEmpty()) {
            appendLine(s("- KIZÁRÁSOK (mindent felülírnak): ", "- EXCLUSIONS (they override everything): ") +
                restrictions.joinToString("; ") { "${it.label(language)} — ${it.rule(language)}" })
        }
        if (request.profile.preferences.isNotBlank()) {
            appendLine(s("- Állandó preferenciák: ", "- Standing preferences: ") +
                request.profile.preferences.trim())
        }
        appendLine()
        appendLine(s("Válaszod kizárólag ez a JSON objektum legyen:",
            "Your reply must be exactly this JSON object:"))
        appendLine(s(
            """{ "day": { ...a rendszerprompt szerinti nap objektum... }, "explanation": "1-2 mondat, mit változtattál" }""",
            """{ "day": { ...the day object from the system prompt... }, "explanation": "1-2 sentences on what you changed" }"""))
    }

    /**
     * Javító kör. A javító kérés önálló üzenetként megy ki (a teljes eredeti prompt után
     * fűzve), nem beszélgetés-folytatásként: így a rendszerprompt cache-e érvényben marad,
     * és nem kell visszaküldeni a hibás, sokszor több tízezer karakteres választ.
     */
    fun repairPrompt(problems: List<String>, language: AppLanguage = AppLanguage.DEFAULT): String =
        buildString {
            val en = language == AppLanguage.EN
            fun s(hu: String, english: String) = if (en) english else hu
            appendLine(s(
                "FIGYELEM — az előző próbálkozás ezekbe a hibákba futott, ezeket most kerüld el:",
                "WARNING — the previous attempt hit these problems; avoid them now:"))
            problems.take(12).forEach { appendLine("- $it") }
            appendLine()
            appendLine(s(
                "Számold ki étkezésenként a makrókból az energiát (fehérje×4 + szénhidrát×4 + zsír×9),",
                "Work out the energy per meal from the macros (protein×4 + carbs×4 + fat×9),"))
            appendLine(s(
                "add össze naponta, és csak akkor válaszolj, ha a napi összeg a kért kereten belül van.",
                "add it up per day, and only answer once the daily total is inside the requested range."))
            appendLine(s(
                "A válasz továbbra is kizárólag egyetlen JSON objektum legyen, kódkerítés nélkül.",
                "The reply must still be a single JSON object, with no code fences."))
        }
}
