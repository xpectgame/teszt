package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DailyTarget
import hu.mealpilot.core.model.DietRestriction
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
        restrictions: Set<DietRestriction> = emptySet(),
        /**
         * KÖTELEZŐ, szándékosan alapértelmezés nélkül.
         *
         * Volt rá alapértelmezés, és a hívó elfelejtette átadni: az angol nyelvű tervre
         * a magyar ellenőrzés futott, ami minden angol ételnevet hibának jelölt, és
         * javító kört csinált abból, ami helyes volt. A fordító semmit nem szólt.
         *
         * Egy nyelvi alapértelmezés itt nem kényelem, hanem csapda: pont az a hívó
         * nem veszi észre a hiányt, aki elrontja.
         */
        language: AppLanguage,
    ): List<String> {
        val problems = mutableListOf<String>()
        // A hibalista a JAVÍTÓ PROMPTBA megy, tehát a terv nyelvén kell lennie —
        // magyar hibaüzenetből a modell angol tervnél a nyelvre is következtetne.
        val english = language == AppLanguage.EN
        fun s(hungarian: String, englishText: String) = if (english) englishText else hungarian

        if (plan.days.isEmpty()) {
            return listOf(s("A válasz egyetlen napot sem tartalmaz.", "The response contains no days at all."))
        }

        // A kizárások mennek elöl: ezek egészségügyi kockázatot jelentenek,
        // és a javító promptban is ezeket lássa először a modell.
        problems += RestrictionChecker.check(plan, restrictions, language)

        // Az angol maradványok. A rendszerprompt kéri a magyar szöveget, de a kérés nem
        // garancia — ez az ellenőrzés az, ami miatt a hibás terv nem jut ki a felhasználóig.
        problems += LanguageChecker.check(plan, language)
        if (plan.days.size != expectedDays) {
            problems += s(
                "$expectedDays napot kértem, de ${plan.days.size} érkezett.",
                "I asked for $expectedDays days but got ${plan.days.size}.",
            )
        }
        val duplicateIndexes = plan.days.groupingBy { it.dayIndex }.eachCount().filterValues { it > 1 }
        if (duplicateIndexes.isNotEmpty()) {
            problems += s(
                "Ismétlődő day_index értékek: ${duplicateIndexes.keys.joinToString()}.",
                "Duplicate day_index values: ${duplicateIndexes.keys.joinToString()}.",
            )
        }

        for (day in plan.days) {
            val label = s("${day.dayIndex}. nap", "Day ${day.dayIndex}")
            if (day.meals.isEmpty()) {
                problems += s("$label: nincs benne egyetlen étkezés sem.", "$label: it has no meals at all.")
                continue
            }
            if (day.meals.size != expectedMealsPerDay) {
                problems += s(
                    "$label: $expectedMealsPerDay étkezést kértem, ${day.meals.size} érkezett.",
                    "$label: I asked for $expectedMealsPerDay meals but got ${day.meals.size}.",
                )
            }

            val total = Nutrients.sum(day.meals.map { it.nutrition.toNutrients() })
            val kcalDiff = total.kcal - target.kcal
            if (abs(kcalDiff) > target.kcal * KCAL_TOLERANCE) {
                val sign = if (kcalDiff > 0) "+" else ""
                problems += s(
                    "$label: ${total.kcal.roundToInt()} kcal a ${target.kcal} kcal cél helyett " +
                        "($sign${kcalDiff.roundToInt()} kcal).",
                    "$label: ${total.kcal.roundToInt()} kcal instead of the ${target.kcal} kcal target " +
                        "($sign${kcalDiff.roundToInt()} kcal).",
                )
            }
            if (total.proteinG < target.proteinG * (1 - PROTEIN_TOLERANCE)) {
                problems += s(
                    "$label: csak ${total.proteinG.roundToInt()} g fehérje a ${target.proteinG} g cél helyett.",
                    "$label: only ${total.proteinG.roundToInt()} g protein instead of the ${target.proteinG} g target.",
                )
            }

            for (meal in day.meals) {
                val n = meal.nutrition.toNutrients()
                if (meal.name.isBlank()) {
                    problems += s("$label: névtelen étkezés (${meal.slot}).", "$label: unnamed meal (${meal.slot}).")
                }
                if (n.kcal <= 0) {
                    problems += s(
                        "$label / ${meal.name}: hiányzik a kalóriaérték.",
                        "$label / ${meal.name}: the calorie value is missing.",
                    )
                } else if (!n.isConsistent()) {
                    problems += s(
                        "$label / ${meal.name}: a makrók ${n.kcalFromMacros.roundToInt()} kcal-t adnak ki, " +
                            "de ${n.kcal.roundToInt()} kcal van megadva.",
                        "$label / ${meal.name}: the macros add up to ${n.kcalFromMacros.roundToInt()} kcal, " +
                            "but ${n.kcal.roundToInt()} kcal is given.",
                    )
                }
                if (meal.ingredients.isEmpty()) {
                    problems += s(
                        "$label / ${meal.name}: nincsenek hozzávalók.",
                        "$label / ${meal.name}: it has no ingredients.",
                    )
                } else if (meal.ingredients.any { it.quantity <= 0 && !it.pantryStaple }) {
                    problems += s(
                        "$label / ${meal.name}: van 0 mennyiségű hozzávaló.",
                        "$label / ${meal.name}: an ingredient has a quantity of 0.",
                    )
                }
                if (!TIME_REGEX.matches(meal.time)) {
                    problems += s(
                        "$label / ${meal.name}: hibás időformátum (${meal.time}), HH:mm kell.",
                        "$label / ${meal.name}: bad time format (${meal.time}), HH:mm is required.",
                    )
                }
            }
        }
        return problems
    }

    private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
}
