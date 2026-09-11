package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.label
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
        language: AppLanguage = AppLanguage.DEFAULT,
    ): List<Violation> {
        val name = normalize(ingredientName)
        if (name.isBlank()) return emptyList()
        val words = name.split(NON_LETTER).filter { it.isNotBlank() }

        return restrictions.mapNotNull { restriction ->
            if (restriction.safeMarkers(language).any { name.contains(normalize(it)) }) {
                return@mapNotNull null
            }
            val exceptions = restriction.exceptions(language).map(::normalize)
            // A kivétel a TELJES névre is vonatkozhat („milk thistle"), nem csak egy szóra.
            if (exceptions.any { it.contains(' ') && name.contains(it) }) return@mapNotNull null
            val candidates = words.filterNot { word ->
                exceptions.any { word == it || word.startsWith(it) }
            }
            val hit = restriction.keywords(language).firstOrNull { keyword ->
                matches(name, candidates, normalize(keyword), language)
            }
            hit?.let { Violation(restriction, ingredientName.trim(), it) }
        }
    }

    /** A teljes terv ellenőrzése; a talált hibák a javító prompt bemenetei. */
    fun check(
        plan: AiPlanResponse,
        restrictions: Set<DietRestriction>,
        language: AppLanguage = AppLanguage.DEFAULT,
    ): List<String> {
        if (restrictions.isEmpty()) return emptyList()
        val problems = LinkedHashSet<String>()
        val english = language == AppLanguage.EN

        for (day in plan.days) {
            for (meal in day.meals) {
                for (ingredient in meal.ingredients) {
                    for (v in violations(ingredient.name, restrictions, language)) {
                        // A hibaszöveg a javító promptba megy, tehát a terv nyelvén kell lennie.
                        problems += if (english) {
                            "Day ${day.dayIndex} / ${meal.name}: \"${v.ingredient}\" breaks " +
                                "${v.restriction.label(language)}. ${v.restriction.rule(language)}"
                        } else {
                            "${day.dayIndex}. nap / ${meal.name}: " +
                                "„${v.ingredient}” ütközik ezzel: ${v.restriction.label(language)}. " +
                                v.restriction.rule(language)
                        }
                    }
                }
                // A fogás neve is árulkodó lehet, ha a hozzávalók listája hiányos.
                for (v in violations(meal.name, restrictions, language)) {
                    problems += if (english) {
                        "Day ${day.dayIndex}: the dish name \"${meal.name}\" breaks " +
                            "${v.restriction.label(language)}."
                    } else {
                        "${day.dayIndex}. nap: a(z) „${meal.name}” fogásnév ütközik ezzel: " +
                            "${v.restriction.label(language)}."
                    }
                }
            }
        }
        return problems.toList()
    }

    /**
     * Az egyezés szabálya nyelvenként MÁS, mert a két nyelv máshol hordozza a lényeget.
     *
     * A magyar összetett szó elöl: „tejföl", „csirkemell", „búzaliszt" — ott a szóeleji
     * egyezés a helyes. Az angol viszont hátul: „wholewheat", „buttermilk", „breadcrumbs"
     * — ott a szóeleji egyezés pont a lényeget hagyná ki.
     *
     * A rövid kulcsszavak külön elbánást kapnak, mert beolvadnak más szavakba: a „ham"
     * benne van a „chamomile"-ban, az „oat" a „goat"-ban, az „egg" az „eggplant"-ben.
     * Ezeknél csak a pontos szó és a többes száma számít találatnak.
     */
    private fun matches(
        fullName: String,
        words: List<String>,
        keyword: String,
        language: AppLanguage,
    ): Boolean {
        if (keyword.isBlank()) return false
        if (keyword.contains(' ')) return fullName.contains(keyword)

        if (language == AppLanguage.EN) {
            return words.any { word ->
                if (keyword.length <= 3) word == keyword || word == keyword + "s"
                else word.startsWith(keyword) || word.endsWith(keyword)
            }
        }
        return words.any { word ->
            if (keyword.length <= 2) word == keyword else word.startsWith(keyword)
        }
    }

    private val NON_LETTER = Regex("[^\\p{L}]+")

    private fun normalize(value: String): String = value.trim().lowercase()
}
