package hu.mealpilot.core

import hu.mealpilot.core.ai.ChatActionType
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptsTest {

    private val context = ChatContext(
        profileSummary = "Férfi, 35 év, 180 cm, 85 kg.",
        targetSummary = "Napi cél: 1932 kcal, 187 g fehérje.",
        planSummary = "Őszi magas fehérje hét, 7 nap, a mai nap a 2.",
        todaySummary = "Eddig 1160 kcal, 104 g fehérje.",
        recentProgress = "14 nap alatt 3,4 kg fogyás.",
        restrictions = listOf("GLUTEN", "LACTOSE"),
        availableDayCount = 7,
    )

    @Test
    fun `the prompt carries the context the model needs to act`() {
        val prompt = ChatPrompts.userPrompt(context, emptyList(), "Írd át az egész hetet olcsóbbra")
        assertTrue(prompt.contains("1932 kcal"))
        assertTrue(prompt.contains("Őszi magas fehérje hét"))
        assertTrue(prompt.contains("GLUTEN, LACTOSE"))
        assertTrue(prompt.contains("Írd át az egész hetet olcsóbbra"))
    }

    @Test
    fun `history is included but trimmed to the recent turns`() {
        val history = (1..20).map { ChatTurn(ChatTurn.Role.USER, "üzenet $it") }
        val prompt = ChatPrompts.userPrompt(context, history, "és most?")
        assertTrue("A legutóbbi megmarad", prompt.contains("üzenet 20"))
        assertTrue("A régi kiesik", !prompt.contains("üzenet 1\n"))
    }

    @Test
    fun `an empty history does not leave a dangling section`() {
        val prompt = ChatPrompts.userPrompt(context, emptyList(), "szia")
        assertTrue(!prompt.contains("KORÁBBI BESZÉLGETÉS"))
    }

    @Test
    fun `a plain answer parses with no action`() {
        val raw = """{"reply": "A fehérje segít megőrizni az izomtömeget.", "action": {"type": "NONE"}}"""
        val response = PlanParser.parseChat(raw).getOrThrow()
        assertEquals(ChatActionType.NONE, response.action.actionType)
        assertTrue(response.reply.contains("fehérje"))
    }

    @Test
    fun `a whole-plan rewrite is recognised`() {
        val raw = """
            {"reply": "Rendben, olcsóbb alapanyagokra váltok.",
             "action": {"type": "REGENERATE_PLAN", "instruction": "olcsó, szezonális alapanyagok",
                        "confirm_label": "Újratervezem az egész hetet olcsóbb alapanyagokkal."}}
        """.trimIndent()
        val response = PlanParser.parseChat(raw).getOrThrow()
        assertEquals(ChatActionType.REGENERATE_PLAN, response.action.actionType)
        assertEquals("olcsó, szezonális alapanyagok", response.action.instruction)
        assertTrue(response.action.confirmLabel.isNotBlank())
    }

    @Test
    fun `specific days are recognised with their indexes`() {
        val raw = """{"reply":"Ok.","action":{"type":"REGENERATE_DAYS","day_indexes":[2,3],"instruction":"hidegen vihető ebéd"}}"""
        val response = PlanParser.parseChat(raw).getOrThrow()
        assertEquals(ChatActionType.REGENERATE_DAYS, response.action.actionType)
        assertEquals(listOf(2, 3), response.action.dayIndexes)
    }

    @Test
    fun `unknown action types degrade to no action instead of failing`() {
        val raw = """{"reply":"?","action":{"type":"DELETE_EVERYTHING"}}"""
        val response = PlanParser.parseChat(raw).getOrThrow()
        assertEquals(ChatActionType.NONE, response.action.actionType)
    }

    @Test
    fun `a missing action block is treated as a plain answer`() {
        val response = PlanParser.parseChat("""{"reply":"Csak beszélgetünk."}""").getOrThrow()
        assertEquals(ChatActionType.NONE, response.action.actionType)
    }

    @Test
    fun `chat responses tolerate code fences and chatter`() {
        val raw = "Persze!\n```json\n{\"reply\":\"Ok\",\"action\":{\"type\":\"ADJUST_RATE\",\"rate_kg_per_week\":0.4}}\n```"
        val response = PlanParser.parseChat(raw).getOrThrow()
        assertEquals(ChatActionType.ADJUST_RATE, response.action.actionType)
        assertEquals(0.4, response.action.rateKgPerWeek, 0.001)
    }
}
