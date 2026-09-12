package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage

/**
 * A mértékegység megjelenítése.
 *
 * A TÁROLT érték kód, nem felirat: a modell angol tervben is `db`, `ek`, `gerezd`
 * alakban adja vissza (így írja elő a rendszerprompt), mert a bevásárlólista
 * összevonása és a mennyiségek kerekítése ezekre illeszkedik. Ha a kód a terv
 * nyelvével változna, egy nyelvváltás után a régi tervek egységei nem illeszkednének.
 *
 * Megjelenítéskor viszont le kell fordítani, különben az angol felhasználó
 * „2 db eggs" és „1 ek olive oil" feliratokat lát.
 */
object Units {

    /**
     * @param quantity a többes szám eldöntéséhez kell — „1 clove", de „2 cloves".
     *   A rövidítések (g, ml, tbsp) angolul sem kapnak többes számot.
     */
    fun label(unit: String, quantity: Double, language: AppLanguage): String {
        val code = unit.trim().lowercase()
        if (language != AppLanguage.EN) return unit.trim()

        val plural = quantity != 1.0
        return when (code) {
            "db" -> "pcs"
            "ek" -> "tbsp"
            "tk" -> "tsp"
            "evőkanál" -> "tbsp"
            "kávéskanál", "teáskanál" -> "tsp"
            "csipet" -> if (plural) "pinches" else "pinch"
            "gerezd" -> if (plural) "cloves" else "clove"
            "szelet" -> if (plural) "slices" else "slice"
            "fej" -> if (plural) "heads" else "head"
            "csokor" -> if (plural) "bunches" else "bunch"
            "liter" -> "l"
            // A metrikus rövidítések nemzetköziek: g, ml, kg, l, dkg, dl, cl.
            else -> unit.trim()
        }
    }
}
