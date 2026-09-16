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
    fun `a code fence without a line break is not thrown away`() {
        // A nyitó kerítés után nem kötelező a sortörés. A régi kód a sortörésre
        // vágott, tehát az egysoros válaszból ÜRES szöveg lett, és a hívó azt a
        // félrevezető hibát kapta, hogy a válasz nem tartalmaz JSON-t — miközben egy
        // kész terv volt benne. A javító kör ilyenkor fölöslegesen fut, és pénzbe kerül.
        assertEquals("Heti terv", PlanParser.parsePlan("```$minimalPlan```").getOrThrow().planTitle)
        assertEquals("Heti terv", PlanParser.parsePlan("```json $minimalPlan```").getOrThrow().planTitle)
    }

    @Test
    fun `a truncated fenced response still reports a truncated plan`() {
        // A kerítés levétele nem moshatja el a csonka választ: a „nincs JSON" és a
        // „nincs lezárva" két külön ok, és a felhasználó az utóbbit értheti csak meg.
        val result = PlanParser.parsePlan("```json\n" + minimalPlan.dropLast(40))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("csonka"))
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
    fun `the first chunk is short so usable days appear quickly`() {
        val chunks = PlanChunker.chunks(30)
        assertEquals(PlanChunker.FIRST_CHUNK_DAYS, chunks.first().days)
        assertEquals(0, chunks.first().startDayIndex)
        assertTrue("A későbbi szakaszok hosszabbak", chunks[1].days > chunks[0].days)
    }

    @Test
    fun `chunks cover every day exactly once`() {
        listOf(1, 3, 4, 7, 14, 30).forEach { total ->
            val chunks = PlanChunker.chunks(total)
            assertEquals("$total nap", total, chunks.sumOf { it.days })
            assertTrue(chunks.all { it.total == chunks.size })
            // A szakaszok hézag és átfedés nélkül követik egymást.
            var expectedStart = 0
            chunks.forEach { chunk ->
                assertEquals(expectedStart, chunk.startDayIndex)
                expectedStart += chunk.days
            }
        }
    }

    @Test
    fun `a plan shorter than the first chunk stays a single call`() {
        val chunks = PlanChunker.chunks(2)
        assertEquals(1, chunks.size)
        assertEquals(2, chunks[0].days)
    }

    @Test
    fun `a week becomes a quick first chunk plus the rest`() {
        val chunks = PlanChunker.chunks(7)
        assertEquals(2, chunks.size)
        assertEquals(3, chunks[0].days)
        assertEquals(4, chunks[1].days)
        assertEquals(3, chunks[1].startDayIndex)
    }
}
