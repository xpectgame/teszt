package hu.mealpilot.core

import hu.mealpilot.core.ai.PlanChunker
import hu.mealpilot.core.ai.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {

    private val minimalPlan = """
        {
          "plan_title": "Heti terv",
          "summary": "Magas fehérje, gyors receptek.",
          "days": [
            {
              "day_index": 0,
              "title": "Hétfő",
              "meals": [
                {
                  "slot": "BREAKFAST", "time": "07:30", "name": "Túrós tál",
                  "prep_minutes": 5, "servings": 1,
                  "recipe_steps": ["Keverd össze"],
                  "ingredients": [
                    {"name": "túró", "quantity": 250, "unit": "g", "aisle": "TEJTERMEK"},
                    {"name": "só", "quantity": 1, "unit": "csipet", "aisle": "FUSZER", "pantry_staple": true}
                  ],
                  "nutrition": {"kcal": 320, "protein_g": 40, "carbs_g": 18, "fat_g": 9}
                }
              ]
            }
          ],
          "coach_notes": ["Igyál eleget."]
        }
    """.trimIndent()

    @Test
    fun `parses a plain json response`() {
        val plan = PlanParser.parsePlan(minimalPlan).getOrThrow()
        assertEquals("Heti terv", plan.planTitle)
        assertEquals(1, plan.days.size)
        assertEquals("Túrós tál", plan.days[0].meals[0].name)
        assertEquals(320.0, plan.days[0].meals[0].nutrition.kcal, 0.01)
        assertTrue(plan.days[0].meals[0].ingredients[1].pantryStaple)
    }

    @Test
    fun `strips markdown code fences`() {
        val fenced = "```json\n$minimalPlan\n```"
        assertEquals("Heti terv", PlanParser.parsePlan(fenced).getOrThrow().planTitle)
    }

    @Test
    fun `ignores chatter around the json object`() {
        val chatty = "Persze, itt a terved:\n$minimalPlan\nJó étvágyat!"
        assertEquals(1, PlanParser.parsePlan(chatty).getOrThrow().days.size)
    }

    @Test
    fun `braces inside strings do not confuse the extractor`() {
        val tricky = """{"plan_title": "A } jel és \" idézőjel", "days": []}"""
        val plan = PlanParser.parsePlan(tricky).getOrThrow()
        assertEquals("A } jel és \" idézőjel", plan.planTitle)
    }

    @Test
    fun `unknown fields are tolerated`() {
        val extra = """{"plan_title": "X", "days": [], "brand_new_field": {"a": 1}}"""
        assertEquals("X", PlanParser.parsePlan(extra).getOrThrow().planTitle)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val sparse = """{"days": [{"day_index": 2, "meals": []}]}"""
        val plan = PlanParser.parsePlan(sparse).getOrThrow()
        assertEquals("", plan.planTitle)
        assertEquals(2, plan.days[0].dayIndex)
        assertTrue(plan.coachNotes.isEmpty())
    }

    @Test
    fun `truncated json fails instead of returning half a plan`() {
        val truncated = minimalPlan.dropLast(40)
        val result = PlanParser.parsePlan(truncated)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("csonka"))
    }

    @Test
    fun `response without json fails`() {
        assertTrue(PlanParser.parsePlan("Sajnálom, nem tudok segíteni.").isFailure)
    }

    @Test
    fun `parses a single day refinement response`() {
        val raw = """{"day": {"day_index": 3, "title": "Csütörtök", "meals": []}, "explanation": "Kicseréltem az ebédet."}"""
        val response = PlanParser.parseDay(raw).getOrThrow()
        assertEquals(3, response.day.dayIndex)
        assertEquals("Kicseréltem az ebédet.", response.explanation)
    }

    @Test
    fun `chunker splits a month into weeks and keeps the remainder`() {
        val chunks = PlanChunker.chunks(30)
        assertEquals(5, chunks.size)
        assertEquals(0, chunks.first().startDayIndex)
        assertEquals(7, chunks.first().days)
        assertEquals(28, chunks.last().startDayIndex)
        assertEquals(2, chunks.last().days)
        assertEquals(30, chunks.sumOf { it.days })
        assertTrue(chunks.all { it.total == 5 })
    }

    @Test
    fun `chunker handles a single week`() {
        val chunks = PlanChunker.chunks(7)
        assertEquals(1, chunks.size)
        assertEquals(7, chunks[0].days)
    }
}
