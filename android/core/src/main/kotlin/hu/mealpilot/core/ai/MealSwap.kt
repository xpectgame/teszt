package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietRestriction
import kotlin.math.abs

/**
 * „Ezt ne kérem, adj mást." — melyik sablon jöjjön egy fogás helyére.
 *
 * MIÉRT A BANKBÓL, ÉS NEM A MODELLTŐL. A csere azonnal kell: a felhasználó a reggeli
 * előtt áll a konyhában, nem húsz másodpercet akar várni egy hálózati hívásra, ami
 * ráadásul a havi keretéből is fogy, és repülőgépes üzemmódban el sem indul. A
 * [RecipeBank] 83 fogása erre elég, és a csere így INGYENES, AZONNALI és offline is
 * működik. Aki egészen mást akar, annak ott a beszélgetés.
 *
 * MIT KERÜL. A mostani fogást (különben „kicseréltem ugyanarra"), a nap többi fogását
 * (különben kétszer ugyanaz a vacsora), és mindent, ami a felhasználó MOSTANI
 * kizárásaiba ütközik — nem azokba, amik a terv készítésekor éltek.
 *
 * MIÉRT NEM VÉLETLENSZERŰ. Ismételt koppintásra körbejár a bankon, mindig a mostani
 * fogás utáni elemtől indulva. Véletlennel ugyanaz a fogás kétszer egymás után is
 * kijöhetne, és a felhasználó azt hinné, elromlott a gomb.
 *
 * MIÉRT NEM ELÉG A KALÓRIA. A csere a mostani fogás kalóriájára méretez, tehát a nap
 * KERETE nem csúszik el — a fehérje viszont igen. Egy 600 kcal-s csirkés fogást egy
 * 600 kcal-s tésztásra cserélve 25 g fehérje tűnhet el, és a nap kiesik abból a
 * sávból, amit a [PlanValidator] a modell tervein SZÁMON KÉR. A csere egyenesen az
 * adatbázisba ír, tehát oda semmilyen ellenőrzés nem fut rá: ha itt nem figyelünk,
 * sehol nem figyel senki.
 *
 * Ezért a választás először azok közül válogat, amelyek a mostani fogás fehérjéjének
 * legalább [MIN_PROTEIN_RATIO] részét megtartják. Ha ilyen nincs, nem hibázunk le —
 * a legtöbb fehérjét hozó biztonságos sablon jön —, mert egy kicsit gyengébb csere
 * még mindig jobb, mint egy gomb, ami nem csinál semmit.
 */
object MealSwap {

    /**
     * A következő kiadható sablon, vagy null, ha egy sem fér bele.
     *
     * A null valódi lehetőség: szűk kizárásoknál és rövid banknál előfordulhat, hogy
     * a nap többi fogásán kívül nem marad más. Ilyenkor szólni kell, nem kitalálni
     * valamit.
     */
    fun next(
        slot: MealSlot,
        currentName: String,
        avoidNames: List<String> = emptyList(),
        restrictions: Set<DietRestriction> = emptySet(),
        language: AppLanguage = AppLanguage.DEFAULT,
        /** A cserélendő fogás kalóriája — erre méretezünk. 0, ha nem ismert. */
        currentKcal: Double = 0.0,
        /** A cserélendő fogás fehérjéje. 0, ha nem ismert: ilyenkor nincs mit őrizni. */
        currentProteinG: Double = 0.0,
    ): RecipeTemplate? {
        val bank = RecipeBank.forSlot(slot)
        if (bank.isEmpty()) return null

        val current = key(currentName)
        val avoid = avoidNames.map(::key).toSet() + current

        // Ahonnan indulunk: a mostani fogás UTÁNI elem, ha a bankban van. Ha nincs (a
        // fogást a modell írta), akkor a nevéből képzett, állandó kezdőpont — így
        // ugyanarra a fogásra mindig ugyanaz a sorrend jön, nem ugrál találomra.
        val position = bank.indexOfFirst { key(it.name.get(language)) == current }
        val start = if (position >= 0) position + 1 else abs(current.hashCode()) % bank.size

        val safeInOrder = (0 until bank.size)
            .map { bank[(start + it) % bank.size] }
            .filter { candidate ->
                val name = candidate.name.get(language)
                key(name) !in avoid && RestrictionChecker.isSafe(
                    mealName = name,
                    ingredientNames = candidate.ingredients.map { it.name.get(language) },
                    restrictions = restrictions,
                    language = language,
                )
            }
        if (safeInOrder.isEmpty()) return null

        // Fehérjekorlát csak akkor, ha van mihez mérni.
        if (currentKcal <= 0 || currentProteinG <= 0) return safeInOrder.first()

        val floor = currentProteinG * MIN_PROTEIN_RATIO
        return safeInOrder.firstOrNull { proteinAfterScaling(it, currentKcal) >= floor }
            ?: safeInOrder.maxByOrNull { proteinAfterScaling(it, currentKcal) }
    }

    /** Mennyi fehérje marad, ha ezt a sablont a kért kalóriaszintre méretezzük. */
    private fun proteinAfterScaling(template: RecipeTemplate, targetKcal: Double): Double =
        template.nutrition.proteinG * template.scaleFactorFor(targetKcal)

    /**
     * A cserének a mostani fogás fehérjéjének legalább ennyi részét meg kell tartania.
     *
     * Nem önkényes szám: a [PlanValidator] a napi fehérjét a cél 85%-áig fogadja el
     * ([PlanValidator.PROTEIN_TOLERANCE]). Ha minden egyes fogás tartja ezt az arányt,
     * a nap összege sem eshet ki a sávból.
     */
    const val MIN_PROTEIN_RATIO = 1.0 - PlanValidator.PROTEIN_TOLERANCE

    /** Az összehasonlítás alapja: a modell ugyanazt a fogást írhatja más kisbetűzéssel. */
    private fun key(name: String): String = name.trim().lowercase()
}
