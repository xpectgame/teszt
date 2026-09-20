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

        for (offset in bank.indices) {
            val candidate = bank[(start + offset) % bank.size]
            val name = candidate.name.get(language)
            if (key(name) in avoid) continue
            val safe = RestrictionChecker.isSafe(
                mealName = name,
                ingredientNames = candidate.ingredients.map { it.name.get(language) },
                restrictions = restrictions,
                language = language,
            )
            if (safe) return candidate
        }
        return null
    }

    /** Az összehasonlítás alapja: a modell ugyanazt a fogást írhatja más kisbetűzéssel. */
    private fun key(name: String): String = name.trim().lowercase()
}
