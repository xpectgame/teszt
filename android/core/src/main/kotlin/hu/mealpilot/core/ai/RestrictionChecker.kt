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
            if (restriction.safeMarkers(language).any { name.contains(normalizeKeyword(it)) }) {
                return@mapNotNull null
            }
            val exceptions = restriction.exceptions(language).map(::normalizeKeyword)
            // A kivétel a TELJES névre is vonatkozhat („milk thistle"), nem csak egy szóra.
            if (exceptions.any { it.contains(' ') && name.contains(it) }) return@mapNotNull null
            val candidates = words.filterNot { word ->
                exceptions.any { word == it || word.startsWith(it) }
            }
            val hit = restriction.keywords(language).firstOrNull { keyword ->
                matches(name, candidates, normalizeKeyword(keyword), language)
            } ?: return@mapNotNull null
            // A MENTESSÉG jelölése erősebb a kulcsszónál: a „soy-free dressing" nem szója,
            // a „tejmentes margarin" nem tejtermék. Eddig ez kizárólag azoknál a
            // kizárásoknál működött, amelyeknél valaki kézzel felvette a jelölést — a
            // gluténnál igen, a szójánál, a földimogyorónál és a tojásnál nem. Ez a
            // szabály a kulcsszóból képzi a mentes alakot, tehát mindegyiknél működik.
            if (declaredFree(name, normalizeKeyword(hit), language)) return@mapNotNull null
            Violation(restriction, ingredientName.trim(), hit)
        }
    }

    /**
     * Kiszolgálható-e ez a fogás ezekkel a kizárásokkal?
     *
     * A [check] a MODELL válaszát vizsgálja, és a hibából javító kör lesz. Ez a
     * függvény ott kell, ahol nincs kit megkérni a javításra: a beépített, sablonos
     * tervező maga rakja ki a fogásokat, tehát neki előre kell tudnia, melyiket nem
     * szabad kiadnia.
     */
    fun isSafe(meal: AiMeal, restrictions: Set<DietRestriction>, language: AppLanguage): Boolean =
        isSafe(meal.name, meal.ingredients.map { it.name }, restrictions, language)

    /**
     * Ugyanaz, csak nevekből.
     *
     * A MÁR ELMENTETT terv fogásai nem [AiMeal]-ként élnek, hanem az adatbázis saját
     * alakjában. Ez az alak teszi lehetővé, hogy egy utólag felvett allergiát a meglévő
     * tervre is rá lehessen futtatni — a :core modul így sem lát az adatbázisra.
     */
    fun isSafe(
        mealName: String,
        ingredientNames: List<String>,
        restrictions: Set<DietRestriction>,
        language: AppLanguage,
    ): Boolean {
        if (restrictions.isEmpty()) return true
        if (violations(mealName, restrictions, language).isNotEmpty()) return false
        return ingredientNames.none { violations(it, restrictions, language).isNotEmpty() }
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
     * Az angol összetett szó hátul viszi a lényeget: „wholewheat", „buttermilk",
     * „breadcrumbs" — ott a szóvégi egyezés kell.
     *
     * A magyar MINDKÉT irányban összetesz, és ezt korábban félreértettük. A „tejföl" és
     * a „búzaliszt" elöl hordozza az allergént, de a „krémsajt", a „juhtúró" és a
     * „zsírszalonna" hátul — ezeket a csak szóeleji egyezés némán átengedte.
     *
     * A másik magyar sajátosság a TŐVÉGI NYÚLÁS: toldalékoláskor a szóvégi a → á,
     * e → é. A „tészta" benne volt a gluténlistában, a „húsleves tésztával" mégis
     * átcsúszott, mert a „tésztával" nem a „tészta" karaktersorral kezdődik. Ugyanez
     * érinti a hagymát, az almát, a csirkét és minden más a/e végű kulcsszót.
     *
     * A rövid kulcsszavak külön elbánást kapnak, mert beolvadnak más szavakba: a „ham"
     * benne van a „chamomile"-ban, az „oat" a „goat"-ban, az „egg" az „eggplant"-ben.
     * Ezeknél csak a pontos szó és a többes száma számít találatnak.
     *
     * ISMERT KORLÁT: a hasonuló toldalékokat (kolbász → kolbásszal) ez nem fedi le.
     * Azokra a kulcsszólista bővítése való, nem a szabály további lazítása — a
     * lazítás fals riasztást hozna, ami minden tervet fölösleges javító körbe küld.
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

        // A két karakternél rövidebb kulcsszó bármibe beleolvadna, ezért pontos egyezés.
        if (keyword.length <= 2) return words.any { it == keyword }

        val lengthened = lengthenedStem(keyword)
        return words.any { word ->
            word.startsWith(keyword) ||
                (lengthened != null && word.startsWith(lengthened)) ||
                // A szóvégi egyezés HÁROM betűtől fut. Négyes küszöbbel a magyar
                // legfontosabb rövid allergénszavai kimaradtak a összetételekből:
                // a „kecsketej", a „bivalytej" és a „teavaj" nem számított
                // tejterméknek, az „akácméz" nem fruktóznak, a „tarisznyarák" nem
                // ráknak. Mind hétköznapi szó, és mind némán csúszott át.
                //
                // A kettes küszöb már valóban veszélyes lenne (a „bor" minden
                // -bor végűre illeszkedne), a hármas viszont csak néhány ismert
                // kivételt igényel — azok a kizárásoknál fel vannak sorolva.
                (keyword.length >= 3 && word.endsWith(keyword))
        }
    }

    /**
     * A kulcsszó toldalékolt töve, ha a magyar tővégi nyúlás érinti: tészta → tésztá,
     * körte → körté. Null, ha a szó nem a-ra vagy e-re végződik.
     */
    /**
     * „X-mentes" alak a MEGTALÁLT kulcsszóra.
     *
     * Szándékosan a találatot adó kulcsszóhoz kötve, nem általános „free" kereséssel:
     * a „sugar-free milk" továbbra is tejtermék, mert nem a „milk" van mentesnek
     * jelölve. Így a szabály nem tud véletlenül elnyelni egy valódi találatot.
     */
    private fun declaredFree(name: String, keyword: String, language: AppLanguage): Boolean =
        if (language == AppLanguage.EN) {
            name.contains("$keyword-free") || name.contains("$keyword free") || name.contains("${keyword}free")
        } else {
            name.contains("${keyword}mentes")
        }

    private fun lengthenedStem(keyword: String): String? = when (keyword.last()) {
        'a' -> keyword.dropLast(1) + 'á'
        'e' -> keyword.dropLast(1) + 'é'
        else -> null
    }

    private val NON_LETTER = Regex("[^\\p{L}]+")

    /** A vizsgált hozzávalónév: a széleken lévő szóköz nem hordoz jelentést. */
    private fun normalize(value: String): String = value.trim().lowercase()

    /**
     * A kulcsszó. Itt SZÁNDÉKOSAN nincs trim: a szóköz a kulcsszó része lehet, és
     * pont az különbözteti meg a hasonló szavakat.
     *
     * A „rántott " (panírozott) szóközzel áll, mert a „rántotta" (tojásétel) ugyanazzal
     * a hét betűvel kezdődik. A gluténmentes jelölők közötti „gm " ugyanígy: szóköz
     * nélkül a rövidítés beolvadna más szavakba — és az a veszélyesebb irány, mert egy
     * téves biztonsági jelölő ELREJTI az allergént.
     *
     * Ez korábban trimmelt, és mindkét szándék némán elveszett.
     */
    private fun normalizeKeyword(value: String): String = value.lowercase()
}
