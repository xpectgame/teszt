package hu.mealpilot.core

import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.Quantities
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A mennyiségek attól lesznek használhatók, hogy ki lehet őket mérni. A 178 gramm
 * matematikailag pontos, a konyhában viszont értelmezhetetlen.
 */
class QuantitiesTest {

    @Test
    fun `a grammot otosevel kerekiti`() {
        assertEquals(180.0, Quantities.humanize(178.0, "g"), 0.001)
        assertEquals(180.0, Quantities.humanize(182.0, "g"), 0.001)
        assertEquals(75.0, Quantities.humanize(73.0, "g"), 0.001)
        assertEquals(250.0, Quantities.humanize(248.0, "gramm"), 0.001)
        assertEquals(200.0, Quantities.humanize(200.0, "ml"), 0.001)
    }

    @Test
    fun `a csipetnyi mennyisegek nem duzzadnak fel`() {
        // 3 g fahéjból nem lehet 5 g — az már más íz.
        assertEquals(3.0, Quantities.humanize(3.0, "g"), 0.001)
        assertEquals(2.0, Quantities.humanize(2.4, "g"), 0.001)
        assertEquals(8.0, Quantities.humanize(7.6, "g"), 0.001)
    }

    @Test
    fun `nulla sosem lesz beloole`() {
        // A kerekítés nem tüntethet el egy hozzávalót a receptből.
        assertEquals(1.0, Quantities.humanize(0.4, "g"), 0.001)
        assertEquals(0.5, Quantities.humanize(0.2, "ek"), 0.001)
    }

    @Test
    fun `a nagyobb egysegeknel nem otosevel lepunk`() {
        // 5 kg-os lépcső fél kilós adagoknál nevetséges lenne.
        assertEquals(0.85, Quantities.humanize(0.84, "kg"), 0.001)
        assertEquals(1.45, Quantities.humanize(1.43, "l"), 0.001)
        assertEquals(1.4, Quantities.humanize(1.4, "l"), 0.001)
        assertEquals(18.0, Quantities.humanize(17.8, "dkg"), 0.001)
        assertEquals(2.5, Quantities.humanize(2.6, "dl"), 0.001)
    }

    @Test
    fun `a darabra mert dolgok felben maradnak`() {
        assertEquals(2.0, Quantities.humanize(2.0, "db"), 0.001)
        assertEquals(1.5, Quantities.humanize(1.6, "db"), 0.001)
        assertEquals(3.0, Quantities.humanize(2.8, "gerezd"), 0.001)
    }

    @Test
    fun `ismeretlen mertekegyseget nem bantunk`() {
        assertEquals(1.23, Quantities.humanize(1.23, "marék"), 0.001)
    }

    @Test
    fun `a hozzavalot ugyanazzal a szabállyal kerekiti`() {
        val i = AiIngredient(name = "csirkemell", quantity = 178.0, unit = "g")
        assertEquals(180.0, Quantities.humanize(i).quantity, 0.001)
        // Ami már kerek, az ugyanaz a példány marad — felesleges másolat nélkül.
        val round = AiIngredient(name = "rizs", quantity = 70.0, unit = "g")
        assertEquals(round, Quantities.humanize(round))
    }
}
