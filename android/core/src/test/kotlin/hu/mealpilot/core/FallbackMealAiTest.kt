package hu.mealpilot.core

import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.FallbackMealAi
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackMealAiTest {

    private val emptyContext = ChatContext(
        profileSummary = "",
        targetSummary = "",
        planSummary = "",
        todaySummary = "",
        recentProgress = "",
        restrictionKeys = emptyList(),
        availableDayCount = 0,
    )

    private val profile = UserProfile()
    private val request = PlanRequest(
        profile = profile,
        budget = EnergyCalculator.budget(profile),
        days = 7,
    )

    /** Sablonos tervező: annyi napot ad, amennyit kérnek, a kért eltolástól. */
    private class Templates : MealAi {
        override val isConfigured = true
        override val canEstimate = false
        override suspend fun generatePlan(
            request: PlanRequest,
            onProgress: (GenerationProgress) -> Unit,
            onChunk: suspend (AiPlanResponse) -> Unit,
        ): Result<AiPlanResponse> {
            val plan = AiPlanResponse(
                planTitle = "Sablon",
                summary = "sablonból",
                days = (0 until request.days).map { AiDay(dayIndex = request.startDayIndex + it) },
                coachNotes = listOf("Igyál vizet."),
            )
            onChunk(plan)
            return Result.success(plan)
        }

        override suspend fun refineDay(request: PlanRequest, currentDayJson: String, instruction: String) =
            Result.failure<AiDayResponse>(IllegalStateException("sablon"))

        override suspend fun chat(context: ChatContext, history: List<ChatTurn>, message: String) =
            Result.failure<AiChatResponse>(IllegalStateException("sablon"))

        override suspend fun estimate(description: String) =
            Result.failure<AiMealEstimate>(IllegalStateException("sablon"))
    }

    /** Elsődleges tervező, ami [deliver] nap után elhasal a megadott hibával. */
    private class Flaky(private val deliver: Int, private val error: Throwable) : MealAi {
        override val isConfigured = true
        override val canEstimate = true
        override suspend fun generatePlan(
            request: PlanRequest,
            onProgress: (GenerationProgress) -> Unit,
            onChunk: suspend (AiPlanResponse) -> Unit,
        ): Result<AiPlanResponse> {
            if (deliver > 0) {
                onChunk(
                    AiPlanResponse(
                        planTitle = "AI terv",
                        summary = "személyre szabva",
                        days = (0 until deliver).map { AiDay(dayIndex = request.startDayIndex + it) },
                        coachNotes = listOf("AI tipp"),
                    )
                )
            }
            return Result.failure(error)
        }

        override suspend fun refineDay(request: PlanRequest, currentDayJson: String, instruction: String) =
            Result.failure<AiDayResponse>(error)

        override suspend fun chat(context: ChatContext, history: List<ChatTurn>, message: String) =
            Result.failure<AiChatResponse>(error)

        override suspend fun estimate(description: String) = Result.failure<AiMealEstimate>(error)
    }

    private fun fallback(
        primary: MealAi,
        recoverable: (Throwable) -> Boolean = { true },
        onFallback: (Throwable, Int) -> Unit = { _, _ -> },
    ) = FallbackMealAi(
        primary = primary,
        fallback = Templates(),
        isRecoverable = recoverable,
        switchMessage = "Átváltás a beépített tervezőre…",
        fallbackNote = "A hiányzó napok sablonból készültek.",
        fallbackSummary = "sablon összefoglaló",
        shortPlanNote = { delivered, requested -> "Csak $delivered nap készült el a kért $requested-ból." },
        onFallback = onFallback,
    )

    /** Sablonos tervező, ami szintén nemet mond — pl. a kizárások kimerítik a bankot. */
    private class NoTemplates : MealAi {
        override val isConfigured = true
        override val canEstimate = false
        override suspend fun generatePlan(
            request: PlanRequest,
            onProgress: (GenerationProgress) -> Unit,
            onChunk: suspend (AiPlanResponse) -> Unit,
        ): Result<AiPlanResponse> =
            Result.failure(IllegalStateException("a kizárások kimerítették a sablonbankot"))

        override suspend fun refineDay(request: PlanRequest, currentDayJson: String, instruction: String) =
            Result.failure<AiDayResponse>(IllegalStateException("sablon"))

        override suspend fun chat(context: ChatContext, history: List<ChatTurn>, message: String) =
            Result.failure<AiChatResponse>(IllegalStateException("sablon"))

        override suspend fun estimate(description: String) =
            Result.failure<AiMealEstimate>(IllegalStateException("sablon"))
    }

    @Test
    fun `a total failure is answered by the built-in planner`() = runTest {
        var notified: Throwable? = null
        var deliveredByPrimary = -1
        val result = fallback(
            primary = Flaky(0, RuntimeException("nincs keret")),
            onFallback = { error, days -> notified = error; deliveredByPrimary = days },
        )
            .generatePlan(request)

        val plan = result.getOrThrow()
        assertEquals(7, plan.days.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), plan.days.map { it.dayIndex })
        assertEquals("nincs keret", notified?.message)
        // Egyetlen nap sem jött a szolgáltatástól — a havi keretből sem fogyhat semmi.
        assertEquals(0, deliveredByPrimary)
        assertTrue(plan.coachNotes.contains("A hiányzó napok sablonból készültek."))
        assertEquals("sablon összefoglaló", plan.summary)
    }

    @Test
    fun `an interrupted plan is completed from where it stopped`() = runTest {
        val chunks = mutableListOf<AiPlanResponse>()
        val result = fallback(Flaky(3, RuntimeException("megszakadt")))
            .generatePlan(request, onChunk = { chunks += it })

        val plan = result.getOrThrow()
        // A pótlás nem kezdi elölről és nem is duplázza a napokat.
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), plan.days.map { it.dayIndex })
        assertEquals(2, chunks.size)
        assertEquals(listOf(3, 4, 5, 6), chunks[1].days.map { it.dayIndex })
        // Az első szakasz az AI-tól jött, tehát az ő összefoglalója marad.
        assertEquals("személyre szabva", plan.summary)
        assertTrue(plan.coachNotes.contains("A hiányzó napok sablonból készültek."))
    }

    @Test
    fun `a quota refusal is never answered with a template plan`() = runTest {
        var notified = false
        val quota = IllegalStateException("elfogyott a havi keret")
        val result = fallback(
            primary = Flaky(0, quota),
            recoverable = { false },
            onFallback = { _, _ -> notified = true },
        ).generatePlan(request)

        assertTrue(result.isFailure)
        assertEquals(quota, result.exceptionOrNull())
        assertFalse(notified)
    }

    @Test
    fun `a successful plan passes through untouched`() = runTest {
        val plan = fallback(Templates()).generatePlan(request).getOrThrow()
        assertEquals(7, plan.days.size)
        assertFalse(plan.coachNotes.contains("A hiányzó napok sablonból készültek."))
    }

    @Test
    fun `chat and day rewrites do not fall back`() = runTest {
        val ai = fallback(Flaky(0, RuntimeException("nincs hálózat")))
        assertEquals("nincs hálózat", ai.chat(emptyContext, emptyList(), "szia").exceptionOrNull()?.message)
        assertEquals("nincs hálózat", ai.refineDay(request, "{}", "kevesebb szénhidrát").exceptionOrNull()?.message)
    }

    /**
     * A tartalék is elbukhat: a sablonbank akkor mond nemet, ha a kizárások kimerítik.
     * Ilyenkor rövidebb terv megy ki — és ezt ki kell mondani, különben a felhasználó
     * csak annyit lát, hogy hét nap helyett három jött, magyarázat nélkül.
     */
    @Test
    fun `a truncated plan says why it is short`() = runTest {
        val ai = FallbackMealAi(
            primary = Flaky(3, RuntimeException("megszakadt")),
            fallback = NoTemplates(),
            isRecoverable = { true },
            switchMessage = "Átváltás…",
            fallbackNote = "A hiányzó napok sablonból készültek.",
            fallbackSummary = "sablon összefoglaló",
            shortPlanNote = { delivered, requested -> "Csak $delivered nap készült el a kért $requested-ból." },
        )

        val plan = ai.generatePlan(request).getOrThrow()

        assertEquals(3, plan.days.size)
        assertTrue(
            "a rövidülés magyarázat nélkül maradt: ${plan.coachNotes}",
            plan.coachNotes.contains("Csak 3 nap készült el a kért 7-ból."),
        )
    }

    @Test
    fun `a plan that is short even after the templates says so`() = runTest {
        // A sablonos tervező csak annyit ad, amennyit kérnek — itt viszont eleve
        // kevesebbet kap, mint a hiány, tehát a terv így is rövid marad.
        val ai = FallbackMealAi(
            primary = Flaky(2, RuntimeException("megszakadt")),
            fallback = object : MealAi by NoTemplates() {
                override suspend fun generatePlan(
                    request: PlanRequest,
                    onProgress: (GenerationProgress) -> Unit,
                    onChunk: suspend (AiPlanResponse) -> Unit,
                ): Result<AiPlanResponse> {
                    val plan = AiPlanResponse(
                        days = listOf(AiDay(dayIndex = request.startDayIndex)),
                        coachNotes = emptyList(),
                    )
                    onChunk(plan)
                    return Result.success(plan)
                }
            },
            isRecoverable = { true },
            switchMessage = "Átváltás…",
            fallbackNote = "A hiányzó napok sablonból készültek.",
            fallbackSummary = "sablon összefoglaló",
            shortPlanNote = { delivered, requested -> "Csak $delivered nap készült el a kért $requested-ból." },
        )

        val plan = ai.generatePlan(request).getOrThrow()

        assertEquals(3, plan.days.size)
        assertTrue(plan.coachNotes.contains("Csak 3 nap készült el a kért 7-ból."))
    }

    @Test
    fun `a complete plan carries no shortness note`() = runTest {
        val plan = fallback(Flaky(3, RuntimeException("megszakadt"))).generatePlan(request).getOrThrow()

        assertEquals(7, plan.days.size)
        assertTrue(plan.coachNotes.none { it.startsWith("Csak ") })
    }
}
