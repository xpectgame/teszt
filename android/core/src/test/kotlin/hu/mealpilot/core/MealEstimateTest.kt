package hu.mealpilot.core

import hu.mealpilot.core.ai.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A becslés értelmezése. A modell válaszát nem mi írjuk, tehát a kerítés itt van:
 * kódkerítés, magyarázó szöveg és hiányzó mezők mellett is működnie kell.
 */
class MealEstimateTest {

    @Test
    fun `parses a plain estimate`() {
        val parsed = PlanParser.parseEstimate(
            """{"name":"Gyrosos pita","kcal":720,"protein_g":34,"carbs_g":78,"fat_g":30,
               "assumption":"Egy közepes adaggal."}"""
        ).getOrThrow()

        assertEquals("Gyrosos pita", parsed.name)
        assertEquals(720.0, parsed.kcal, 0.001)
        assertEquals(34.0, parsed.proteinG, 0.001)
        assertTrue(parsed.isUsable)
    }

    @Test
    fun `survives a code fence and a chatty preamble`() {
        val parsed = PlanParser.parseEstimate(
            """
            Persze, itt a becslés:
            ```json
            {"name":"Túrós csusza","kcal":610,"protein_g":26,"carbs_g":64,"fat_g":27}
            ```
            """.trimIndent()
        ).getOrThrow()

        assertEquals("Túrós csusza", parsed.name)
        assertEquals(610.0, parsed.kcal, 0.001)
        // A hiányzó mező nem hiba: alapértékkel jön.
        assertEquals("", parsed.assumption)
    }

    @Test
    fun `an empty name or zero calories is not usable`() {
        // A prompt ezzel jelzi, hogy a szöveg nem étel. A hívó ebből csinál hibát —
        // egy üres mezőhalmazból a felhasználó nem tudná, mi történt.
        val notFood = PlanParser.parseEstimate("""{"name":"","kcal":0}""").getOrThrow()
        assertFalse(notFood.isUsable)

        val noCalories = PlanParser.parseEstimate("""{"name":"Levegő","kcal":0}""").getOrThrow()
        assertFalse(noCalories.isUsable)
    }

    @Test
    fun `an unknown field does not break the parse`() {
        // A modell időnként ad egy mezőt, amit nem kértünk. Ettől még a többi jó.
        val parsed = PlanParser.parseEstimate(
            """{"name":"Alma","kcal":95,"carbs_g":25,"confidence":"high"}"""
        ).getOrThrow()

        assertEquals("Alma", parsed.name)
        assertTrue(parsed.isUsable)
    }

    @Test
    fun `a response without JSON fails instead of returning zeros`() {
        val result = PlanParser.parseEstimate("Sajnos ezt nem tudom megmondani.")
        assertTrue(result.isFailure)
    }
}
