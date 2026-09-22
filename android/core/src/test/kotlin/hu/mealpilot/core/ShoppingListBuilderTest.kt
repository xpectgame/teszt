package hu.mealpilot.core

import hu.mealpilot.core.ai.Aisle
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.shopping.ShoppingListBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListBuilderTest {

    private fun ing(
        name: String, qty: Double, unit: String = "g",
        aisle: String = "EGYEB", staple: Boolean = false, note: String = "",
    ) = AiIngredient(name = name, quantity = qty, unit = unit, aisle = aisle, pantryStaple = staple, note = note)

    @Test
    fun `identical ingredients are merged`() {
        val list = ShoppingListBuilder.build(
            listOf(
                ing("csirkemell", 150.0, "g", "HUS_HAL"),
                ing("csirkemell", 200.0, "g", "HUS_HAL"),
            )
        )
        assertEquals(1, list.size)
        assertEquals(350.0, list[0].quantity, 0.01)
        assertEquals(2, list[0].usedInMeals)
        assertEquals(Aisle.HUS_HAL, list[0].aisle)
    }

    @Test
    fun `mixed weight units are converted to grams`() {
        val list = ShoppingListBuilder.build(
            listOf(
                ing("burgonya", 1.5, "kg", "ZOLDSEG_GYUMOLCS"),
                ing("burgonya", 30.0, "dkg", "ZOLDSEG_GYUMOLCS"),
                ing("burgonya", 200.0, "g", "ZOLDSEG_GYUMOLCS"),
            )
        )
        assertEquals(1, list.size)
        assertEquals(2000.0, list[0].quantity, 0.01)
        assertEquals("2 kg", list[0].displayQuantity())
    }

    @Test
    fun `volume units are converted to millilitres`() {
        val list = ShoppingListBuilder.build(listOf(ing("tej", 1.0, "l"), ing("tej", 2.0, "dl")))
        assertEquals(1200.0, list[0].quantity, 0.01)
        assertEquals("1.2 l", list[0].displayQuantity())
    }

    @Test
    fun `names are case and whitespace insensitive`() {
        val list = ShoppingListBuilder.build(listOf(ing(" Tojás ", 4.0, "db"), ing("tojás", 2.0, "db")))
        assertEquals(1, list.size)
        assertEquals("Tojás", list[0].name)
        assertEquals("6 db", list[0].displayQuantity())
        // És ugyanaz a formázás fut a bevásárlólista képernyőjén is — korábban ott egy
        // külön, tesztelt nélküli másolat állt, ami az egységet nyersen írta ki.
        assertEquals("6 pcs", list[0].displayQuantity(AppLanguage.EN))
    }

    @Test
    fun `the same ingredient in incompatible units stays separate`() {
        val list = ShoppingListBuilder.build(listOf(ing("tojás", 4.0, "db"), ing("tojás", 100.0, "g")))
        assertEquals(2, list.size)
    }

    @Test
    fun `pantry staples are skipped by default but can be included`() {
        val items = listOf(ing("só", 1.0, "csipet", "FUSZER", staple = true), ing("rizs", 80.0))
        assertEquals(1, ShoppingListBuilder.build(items).size)
        assertEquals(2, ShoppingListBuilder.build(items, includePantryStaples = true).size)
    }

    @Test
    fun `blank names are dropped`() {
        assertTrue(ShoppingListBuilder.build(listOf(ing("   ", 100.0))).isEmpty())
    }

    @Test
    fun `notes are collected without duplicates`() {
        val list = ShoppingListBuilder.build(
            listOf(
                ing("paradicsom", 200.0, note = "koktél"),
                ing("paradicsom", 100.0, note = "koktél"),
                ing("paradicsom", 100.0, note = "érett"),
            )
        )
        assertEquals(listOf("koktél", "érett"), list[0].notes)
    }

    @Test
    fun `items are grouped and ordered by aisle`() {
        val grouped = ShoppingListBuilder.buildGrouped(
            listOf(
                ing("kenyér", 500.0, "g", "PEKARU"),
                ing("alma", 3.0, "db", "ZOLDSEG_GYUMOLCS"),
                ing("sajt", 200.0, "g", "TEJTERMEK"),
            )
        )
        assertEquals(3, grouped.size)
        assertEquals(
            listOf(Aisle.ZOLDSEG_GYUMOLCS, Aisle.TEJTERMEK, Aisle.PEKARU),
            grouped.keys.toList(),
        )
    }

    @Test
    fun `unknown aisle falls back to other`() {
        val list = ShoppingListBuilder.build(listOf(ing("valami", 1.0, "db", "NINCS_ILYEN")))
        assertEquals(Aisle.EGYEB, list[0].aisle)
        assertNull(list.firstOrNull { it.aisle == Aisle.HUS_HAL })
    }
}
