package hu.mealpilot.core

import hu.mealpilot.core.ai.Units
import hu.mealpilot.core.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `hungarian codes are shown as they are`() {
        assertEquals("db", Units.label("db", 2.0, AppLanguage.HU))
        assertEquals("ek", Units.label("ek", 1.0, AppLanguage.HU))
        assertEquals("g", Units.label("g", 200.0, AppLanguage.HU))
    }

    @Test
    fun `english plans do not show hungarian unit codes`() {
        // A tárolt kód magyar marad — csak a felirat fordul.
        assertEquals("pcs", Units.label("db", 2.0, AppLanguage.EN))
        assertEquals("tbsp", Units.label("ek", 1.0, AppLanguage.EN))
        assertEquals("tsp", Units.label("tk", 1.0, AppLanguage.EN))
    }

    @Test
    fun `english word units follow the count`() {
        assertEquals("clove", Units.label("gerezd", 1.0, AppLanguage.EN))
        assertEquals("cloves", Units.label("gerezd", 2.0, AppLanguage.EN))
        assertEquals("slice", Units.label("szelet", 1.0, AppLanguage.EN))
        assertEquals("slices", Units.label("szelet", 3.0, AppLanguage.EN))
        assertEquals("pinch", Units.label("csipet", 1.0, AppLanguage.EN))
        assertEquals("pinches", Units.label("csipet", 2.0, AppLanguage.EN))
    }

    @Test
    fun `metric abbreviations never take a plural`() {
        for (q in listOf(1.0, 2.0, 200.0)) {
            assertEquals("g", Units.label("g", q, AppLanguage.EN))
            assertEquals("ml", Units.label("ml", q, AppLanguage.EN))
            assertEquals("kg", Units.label("kg", q, AppLanguage.EN))
        }
    }

    @Test
    fun `an unknown unit is passed through untouched`() {
        assertEquals("marék", Units.label("marék", 2.0, AppLanguage.EN))
    }
}
