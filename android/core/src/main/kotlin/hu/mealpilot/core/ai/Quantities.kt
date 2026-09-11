package hu.mealpilot.core.ai

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Emberi léptékű mennyiségek.
 *
 * A modell pontos számot ad — „178 g csirkemell" —, de senki nem mér ki 178 grammot.
 * A bolti kiszerelés, a konyhamérleg és a fejben számolás mind ötösével megy, ezért a
 * hozzávalók mennyiségét a legközelebbi értelmes lépcsőre kerekítjük.
 *
 * A tápértékeket NEM érinti: ott a pontos szám a lényeg, abból jön ki a napi keret.
 * Egy 178 → 180 grammos igazítás a kalóriákon néhány tized százalék, a ±5%-os
 * tűréshatáron belül marad.
 */
object Quantities {

    /** Ez alatt nem kerekítünk ötösével: 3 g fahéjból nem lesz 5 g. */
    private const val FIVE_FROM = 10.0

    /**
     * @param unit a hozzávaló mértékegysége, ahogy a modell adta
     * @return a kerekített mennyiség; ismeretlen mértékegységnél az eredeti
     */
    fun humanize(quantity: Double, unit: String): Double {
        if (quantity <= 0) return quantity
        return when (unit.trim().lowercase()) {
            // Tömeg és térfogat kicsiben: ötösével, de a csipetnyi mennyiségek maradnak.
            "g", "gramm", "ml" ->
                if (quantity >= FIVE_FROM) step(quantity, 5.0) else step(quantity, 1.0)

            // Nagyobb egységek: ott az 5 már fél kiló lenne.
            "dkg", "dl", "cl" -> step(quantity, 0.5)
            "kg", "l", "liter" -> step(quantity, 0.05)

            // Amit darabra mérünk, azt nem kerekítjük tovább: a fél tojás értelmes,
            // a 0,8 tojás nem — de ezt a modell már eleve egészben adja.
            "db", "gerezd", "szelet", "fej", "csokor" -> step(quantity, 0.5)
            "ek", "tk", "kávéskanál", "evőkanál" -> step(quantity, 0.5)
            "csipet" -> step(quantity, 1.0)

            else -> quantity
        }
    }

    fun humanize(ingredient: AiIngredient): AiIngredient {
        val rounded = humanize(ingredient.quantity, ingredient.unit)
        return if (abs(rounded - ingredient.quantity) < 1e-9) ingredient
        else ingredient.copy(quantity = rounded)
    }

    /** A legközelebbi lépcső, de sosem nulla: 2 g fűszerből ne legyen „0 g". */
    private fun step(value: Double, size: Double): Double {
        val snapped = (value / size).roundToInt() * size
        val safe = if (snapped <= 0) size else snapped
        // A lebegőpontos maradékot levágjuk, különben 0.30000000000000004 lesz belőle.
        return (safe * 100).roundToInt() / 100.0
    }
}
