package hu.mealpilot.core.ai

import hu.mealpilot.core.model.DailyTarget
import hu.mealpilot.core.model.Nutrients
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kalóriaeltérés javítása számolással, újratervezés helyett.
 *
 * Ha egy nap 2200 kcal-ra jött ki 1932 helyett, azt nem érdemes az egész hét
 * újragenerálásával orvosolni: elég arányosan visszavenni az adagokból, pontosan úgy,
 * ahogy ezt egy dietetikus is tenné. Ez azonnali, determinisztikus és ingyenes,
 * szemben egy újabb modellhívással, ami percekbe és pénzbe kerül.
 *
 * Csak arányos eltérésre való. Ha a nap szerkezetileg hibás (hiányzó hozzávaló, hibás
 * időpont), vagy a skálázás a fehérjét vinné a cél alá, akkor null jön vissza, és marad
 * a valódi újratervezés.
 */
object PlanRepair {

    /** Ennél nagyobb korrekció már nem hihető: ott a modell tényleg mást tervezett. */
    const val MIN_SCALE = 0.75
    const val MAX_SCALE = 1.30

    /** Ekkora eltérés alatt nem nyúlunk a naphoz. */
    const val DEAD_ZONE = 0.02

    /** A skálázás után a fehérje nem eshet a cél ennyiszerese alá. */
    const val MIN_PROTEIN_RATIO = 0.85

    /** Mértékegységek, amiket folytonosnak tekintünk, tehát átméretezhetők. */
    private val CONTINUOUS_UNITS = setOf("g", "gramm", "dkg", "kg", "ml", "dl", "cl", "l", "liter")

    data class Result(
        val plan: AiPlanResponse,
        /** Mely napokat kellett átméretezni, és milyen szorzóval. */
        val adjusted: Map<Int, Double>,
    )

    fun normalize(plan: AiPlanResponse, target: DailyTarget): Result {
        val adjusted = mutableMapOf<Int, Double>()
        val days = plan.days.map { day ->
            val factor = scaleFactor(day, target) ?: return@map day
            adjusted[day.dayIndex] = factor
            scaleDay(day, factor)
        }
        return Result(plan.copy(days = days), adjusted)
    }

    /** A naphoz tartozó szorzó, vagy null, ha nem szabad vagy nem érdemes skálázni. */
    fun scaleFactor(day: AiDay, target: DailyTarget): Double? {
        if (day.meals.isEmpty() || target.kcal <= 0) return null
        val total = Nutrients.sum(day.meals.map { it.nutrition.toNutrients() })
        if (total.kcal <= 0) return null

        val factor = target.kcal / total.kcal
        if (abs(factor - 1.0) <= DEAD_ZONE) return null
        if (factor < MIN_SCALE || factor > MAX_SCALE) return null
        // Lefelé skálázva a fehérje is csökken — ha ezzel a cél alá esne, inkább újratervezés kell.
        if (total.proteinG * factor < target.proteinG * MIN_PROTEIN_RATIO) return null
        return factor
    }

    fun scaleDay(day: AiDay, factor: Double): AiDay =
        day.copy(meals = day.meals.map { scaleMeal(it, factor) })

    private fun scaleMeal(meal: AiMeal, factor: Double): AiMeal = meal.copy(
        nutrition = meal.nutrition.let { n ->
            AiNutrition(
                kcal = round1(n.kcal * factor),
                proteinG = round1(n.proteinG * factor),
                carbsG = round1(n.carbsG * factor),
                fatG = round1(n.fatG * factor),
                fiberG = round1(n.fiberG * factor),
                sugarG = round1(n.sugarG * factor),
                saturatedFatG = round1(n.saturatedFatG * factor),
                sodiumMg = round1(n.sodiumMg * factor),
            )
        },
        ingredients = meal.ingredients.map { scaleIngredient(it, factor) },
    )

    /**
     * A darabra mért hozzávalókat (tojás, gerezd fokhagyma) nem skálázzuk: „2,1 db tojás"
     * használhatatlan utasítás. A tömeg és térfogat viszont szabadon igazítható.
     */
    private fun scaleIngredient(ingredient: AiIngredient, factor: Double): AiIngredient {
        if (ingredient.pantryStaple) return ingredient
        val unit = ingredient.unit.trim().lowercase()
        if (unit !in CONTINUOUS_UNITS) return ingredient
        val scaled = ingredient.quantity * factor
        val rounded = when {
            unit == "g" || unit == "gramm" || unit == "ml" ->
                if (scaled >= 50) (scaled / 5).roundToInt() * 5.0 else scaled.roundToInt().toDouble()
            else -> round2(scaled)
        }
        return ingredient.copy(quantity = rounded.coerceAtLeast(if (unit == "g" || unit == "ml") 1.0 else 0.01))
    }

    private fun round1(value: Double) = (value * 10).roundToInt() / 10.0
    private fun round2(value: Double) = (value * 100).roundToInt() / 100.0
}
