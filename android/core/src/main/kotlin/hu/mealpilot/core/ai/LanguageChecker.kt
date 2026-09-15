package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage

/**
 * Angol maradványokat keres a magyar tervben.
 *
 * Miért kell, ha a rendszerprompt amúgy is magyar szöveget kér: mert a prompt csak kérés,
 * nem garancia. A modell a gyakorlatban elhagy egy-egy angol ételnevet ("cottage cheese",
 * "greek yogurt"), és ez pont azon a felületen látszik, amiért valaki magyar appot
 * választ. A talált hibák a javító körbe mennek, ugyanoda, ahová a tápértékhibák —
 * így a felhasználóhoz már a javított terv jut el.
 *
 * Szándékosan CSAK olyan szavakat keresünk, amelyeknek van bevett magyar alakja. A
 * magyarban meghonosodott jövevényszavakat (smoothie, wok, quinoa, chia, müzli) nem
 * jelöljük hibának, mert egy magyar étlapon is így szerepelnének.
 *
 * A nyelvtani hibákat (rossz toldalék, torz szóalak) ez NEM tudja elkapni — azokra a
 * rendszerprompt szabályai valók. Itt az a cél, ami gépileg eldönthető.
 */
object LanguageChecker {

    /**
     * Angol kifejezés → a magyar alak, amit a javító prompt kér majd.
     *
     * A hosszabb kifejezések elöl: a "cottage cheese" találatot ne bontsa meg a
     * külön "cheese" szabály.
     */
    private val ENGLISH_TERMS: List<Pair<String, String>> = listOf(
        "cottage cheese" to "túró",
        "greek yogurt" to "görög joghurt",
        "greek yoghurt" to "görög joghurt",
        "chicken breast" to "csirkemell",
        "sweet potato" to "édesburgonya",
        "peanut butter" to "mogyoróvaj",
        "cream cheese" to "krémsajt",
        "ground beef" to "darált marhahús",
        "olive oil" to "olívaolaj",
        "whole wheat" to "teljes kiőrlésű",
        "wholemeal" to "teljes kiőrlésű",
        "brown rice" to "barna rizs",
        "egg white" to "tojásfehérje",
        "rolled oats" to "zabpehely",
        "oatmeal" to "zabpehely",
        "bell pepper" to "paprika",
        "side dish" to "köret",
        "skimmed milk" to "sovány tej",
        "cooking spray" to "olajspray",
        "baking sheet" to "tepsi",
        "breakfast" to "reggeli",
        "lunch" to "ebéd",
        "dinner" to "vacsora",
        "snack" to "nassolnivaló",
        "serving" to "adag",
        "tablespoon" to "evőkanál",
        "teaspoon" to "teáskanál",
        "chopped" to "aprított",
        "sliced" to "szeletelt",
        "boneless" to "csont nélküli",
        "skinless" to "bőr nélküli",
    )

    /**
     * Angol mondatra utaló szerkezeti szavak. Ezek önmagukban nem ételnevek, de ha
     * kettő is előfordul egy mezőben, akkor ott egy egész angol mondat maradt bent.
     *
     * A magyar szavakba ágyazott véletlen egyezést a szóhatár zárja ki (a "for" nem
     * talál a "forró" szóban).
     */
    private val ENGLISH_MARKERS = listOf(
        "the", "with", "and", "until", "then", "your", "into", "over",
        "minutes", "season", "serve", "cook", "heat", "add",
    )

    private fun boundary(term: String) =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private val TERM_PATTERNS: List<Triple<Regex, String, String>> =
        ENGLISH_TERMS.map { (english, hungarian) -> Triple(boundary(english), english, hungarian) }

    private val MARKER_PATTERNS: List<Regex> = ENGLISH_MARKERS.map { boundary(it) }

    /** Egy mező szövege és az, hogy hol találtuk — a javító prompt ezt mondja vissza. */
    data class Finding(val where: String, val term: String, val suggestion: String)

    /** Egyetlen szövegdarab vizsgálata. Kívülről is hívható (becslés, beszélgetés). */
    fun findingsIn(text: String, where: String): List<Finding> {
        if (text.isBlank()) return emptyList()
        val found = mutableListOf<Finding>()
        for ((pattern, english, hungarian) in TERM_PATTERNS) {
            if (pattern.containsMatchIn(text)) found += Finding(where, english, hungarian)
        }
        // Egész angol mondat: legalább két szerkezeti szó. Egy önmagában lehet véletlen
        // (márkanév, idegen fogásnév), kettő már mondat.
        if (found.isEmpty() && MARKER_PATTERNS.count { it.containsMatchIn(text) } >= 2) {
            found += Finding(where, text.trim().take(60), "írd át magyarra")
        }
        return found
    }

    /**
     * A teljes terv átvizsgálása. Üres lista, ha a terv nyelve nem magyar — az angol
     * tervben az angol szöveg a helyes.
     */
    fun check(plan: AiPlanResponse, language: AppLanguage = AppLanguage.DEFAULT): List<String> {
        if (language == AppLanguage.EN) return emptyList()

        val findings = mutableListOf<Finding>()
        findings += findingsIn(plan.planTitle, "a terv címe")
        findings += findingsIn(plan.summary, "az összefoglaló")
        plan.coachNotes.forEach { findings += findingsIn(it, "a tippek") }

        plan.days.forEach { day ->
            val dayLabel = "a(z) ${day.dayIndex + 1}. nap"
            findings += findingsIn(day.title, "$dayLabel címe")
            findings += findingsIn(day.note, "$dayLabel megjegyzése")
            day.meals.forEach { meal ->
                val mealLabel = "$dayLabel – ${meal.name.ifBlank { meal.slot }}"
                findings += findingsIn(meal.name, "$mealLabel neve")
                findings += findingsIn(meal.description, "$mealLabel leírása")
                meal.recipeSteps.forEach { findings += findingsIn(it, "$mealLabel lépései") }
                meal.ingredients.forEach { findings += findingsIn(it.name, "$mealLabel hozzávalói") }
            }
        }

        // Ugyanaz az angol szó tíz fogásban tíz sort adna, és kiszorítaná a többi hibát
        // a javító promptból. Szavanként egy sor elég: a modell így is érti.
        return findings
            .distinctBy { it.term.lowercase() }
            .take(8)
            .map { finding ->
                if (finding.suggestion == "írd át magyarra") {
                    "Angol szöveg maradt a tervben (${finding.where}): \"${finding.term}\" — írd át magyarra."
                } else {
                    "Angol szó a magyar tervben (${finding.where}): \"${finding.term}\" — " +
                        "magyarul: \"${finding.suggestion}\"."
                }
            }
    }
}
