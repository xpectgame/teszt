package hu.mealpilot.core.ai

import hu.mealpilot.core.model.DietRestriction

/**
 * Gépi ellenőrzés arra, hogy a legenerált étrend nem tartalmaz-e kizárt alapanyagot.
 *
 * Egy allergiát nem szabad pusztán a prompt betartására bízni: a modell tévedhet, és
 * a tévedés következménye itt nem egy rossz recept, hanem egy allergiás reakció.
 * Ezért minden hozzávaló nevét összevetjük a kizárások kulcsszavaival, és találat esetén
 * a terv nem jut el a felhasználóig — újratervezés indul a konkrét hibával.
 *
 * A szűrő szándékosan inkább téved a szigor felé: egy fölösleges újratervezés olcsó,
 * egy átcsúszott allergén nem.
 */
object RestrictionChecker {

    data class Violation(
        val restriction: DietRestriction,
        val ingredient: String,
        val matchedKeyword: String,
    )

    /** Egyetlen hozzávaló ellenőrzése. */
    fun violations(
        ingredientName: String,
        restrictions: Set<DietRestriction>,
    ): List<Violation> {
        val name = normalize(ingredientName)
        if (name.isBlank()) return emptyList()
        val words = name.split(NON_LETTER).filter { it.isNotBlank() }

        return restrictions.mapNotNull { restriction ->
            if (restriction.safeMarkers.any { name.contains(normalize(it)) }) return@mapNotNull null
            val exceptions = restriction.exceptions.map(::normalize)
            val candidates = words.filterNot { word ->
                exceptions.any { word.startsWith(it) }
            }
            val hit = restriction.keywords.firstOrNull { keyword ->
                matches(name, candidates, normalize(keyword))
            }
            hit?.let { Violation(restriction, ingredientName.trim(), it) }
        }
    }

    /** A teljes terv ellenőrzése; a talált hibák a javító prompt bemenetei. */
    fun check(plan: AiPlanResponse, restrictions: Set<DietRestriction>): List<String> {
        if (restrictions.isEmpty()) return emptyList()
        val problems = LinkedHashSet<String>()

        for (day in plan.days) {
            for (meal in day.meals) {
                for (ingredient in meal.ingredients) {
                    for (violation in violations(ingredient.name, restrictions)) {
                        problems += "${day.dayIndex}. nap / ${meal.name}: " +
                            "„${violation.ingredient}” ütközik ezzel: ${violation.restriction.hu}. " +
                            violation.restriction.rule
                    }
                }
                // A fogás neve is árulkodó lehet, ha a hozzávalók listája hiányos.
                for (violation in violations(meal.name, restrictions)) {
                    problems += "${day.dayIndex}. nap: a(z) „${meal.name}” fogásnév ütközik ezzel: " +
                        "${violation.restriction.hu}."
                }
            }
        }
        return problems.toList()
    }

    /**
     * Szóelejű egyezés, mert a magyar összetett szavak elöl hordozzák a lényeget
     * („tejföl”, „csirkemell”, „búzaliszt”). A két karakteres kulcsszavaknál csak
     * a pontos szóegyezést fogadjuk el, különben rengeteg téves találat lenne.
     */
    private fun matches(fullName: String, words: List<String>, keyword: String): Boolean {
        if (keyword.isBlank()) return false
        if (keyword.contains(' ')) return fullName.contains(keyword)
        return words.any { word ->
            if (keyword.length <= 2) word == keyword else word.startsWith(keyword)
        }
    }

    private val NON_LETTER = Regex("[^\\p{L}]+")

    private fun normalize(value: String): String = value.trim().lowercase()
}
