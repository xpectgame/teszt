package hu.mealpilot.core.ai

import hu.mealpilot.core.model.DailyTarget
import hu.mealpilot.core.model.Nutrients
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ellenőrzi, hogy a modell válasza megfelel-e a napi céloknak.
 * A talált hibákból javító prompt készül, így a hibás terv nem jut el a felhasználóig.
 */
object PlanValidator {

    /** A napi kalória megengedett eltérése a céltól. */
    const val KCAL_TOLERANCE = 0.10

    /** A napi fehérje megengedett alulteljesítése. */
    const val PROTEIN_TOLERANCE = 0.15

    data class DaySummary(val dayIndex: Int, val nutrients: Nutrients, val mealCount: Int)

    fun daySummaries(plan: AiPlanResponse): List<DaySummary> = plan.days.map { day ->
        DaySummary(
            dayIndex = day.dayIndex,
            nutrients = Nutrients.sum(day.meals.map { it.nutrition.toNutrients() }),
            mealCount = day.meals.size,
        )
    }

    fun validate(
        plan: AiPlanResponse,
        target: DailyTarget,
        expectedDays: Int,
        expectedMealsPerDay: Int,
    ): List<String> {
        val problems = mutableListOf<String>()

        if (plan.days.isEmpty()) {
            return listOf("A válasz egyetlen napot sem tartalmaz.")
        }
        if (plan.days.size != expectedDays) {
            problems += "$expectedDays napot kértem, de ${plan.days.size} érkezett."
        }
        val duplicateIndexes = plan.days.groupingBy { it.dayIndex }.eachCount().filterValues { it > 1 }
        if (duplicateIndexes.isNotEmpty()) {
            problems += "Ismétlődő day_index értékek: ${duplicateIndexes.keys.joinToString()}."
        }

        for (day in plan.days) {
            val label = "${day.dayIndex}. nap"
            if (day.meals.isEmpty()) {
                problems += "$label: nincs benne egyetlen étkezés sem."
                continue
            }
            if (day.meals.size != expectedMealsPerDay) {
                problems += "$label: $expectedMealsPerDay étkezést kértem, ${day.meals.size} érkezett."
            }

            val total = Nutrients.sum(day.meals.map { it.nutrition.toNutrients() })
            val kcalDiff = total.kcal - target.kcal
            if (abs(kcalDiff) > target.kcal * KCAL_TOLERANCE) {
                problems += "$label: ${total.kcal.roundToInt()} kcal a ${target.kcal} kcal cél helyett " +
                    "(${if (kcalDiff > 0) "+" else ""}${kcalDiff.roundToInt()} kcal)."
            }
            if (total.proteinG < target.proteinG * (1 - PROTEIN_TOLERANCE)) {
                problems += "$label: csak ${total.proteinG.roundToInt()} g fehérje a ${target.proteinG} g cél helyett."
            }

            for (meal in day.meals) {
                val n = meal.nutrition.toNutrients()
                if (meal.name.isBlank()) {
                    problems += "$label: névtelen étkezés (${meal.slot})."
                }
                if (n.kcal <= 0) {
                    problems += "$label / ${meal.name}: hiányzik a kalóriaérték."
                } else if (!n.isConsistent()) {
                    problems += "$label / ${meal.name}: a makrók ${n.kcalFromMacros.roundToInt()} kcal-t adnak ki, " +
                        "de ${n.kcal.roundToInt()} kcal van megadva."
                }
                if (meal.ingredients.isEmpty()) {
                    problems += "$label / ${meal.name}: nincsenek hozzávalók."
                } else if (meal.ingredients.any { it.quantity <= 0 && !it.pantryStaple }) {
                    problems += "$label / ${meal.name}: van 0 mennyiségű hozzávaló."
                }
                if (!TIME_REGEX.matches(meal.time)) {
                    problems += "$label / ${meal.name}: hibás időformátum (${meal.time}), HH:mm kell."
                }
            }
        }
        return problems
    }

    private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
}
