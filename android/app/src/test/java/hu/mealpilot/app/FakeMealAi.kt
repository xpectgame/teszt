package hu.mealpilot.app

import hu.mealpilot.core.ai.AiChatAction
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.PlanRequest

/**
 * Tervező, ami azt adja vissza, amit a teszt előír.
 *
 * Nem hálózatra megy: a beszélgetés tárolását akarjuk vizsgálni, nem a modellt.
 * A [lastPrompt] azért van, hogy az is ellenőrizhető legyen, mi ment ki.
 */
class FakeMealAi(
    private val reply: Result<AiChatResponse> = Result.success(AiChatResponse(reply = "Rendben.")),
) : MealAi {

    var lastMessage: String? = null
        private set
    var lastHistory: List<ChatTurn> = emptyList()
        private set

    override val isConfigured = true
    override val canEstimate = true

    override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
        onChunk: suspend (AiPlanResponse) -> Unit,
    ): Result<AiPlanResponse> = Result.failure(UnsupportedOperationException("nem ez a teszt tárgya"))

    override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = Result.failure(UnsupportedOperationException("nem ez a teszt tárgya"))

    override suspend fun chat(
        context: ChatContext,
        history: List<ChatTurn>,
        message: String,
    ): Result<AiChatResponse> {
        lastMessage = message
        lastHistory = history
        return reply
    }

    override suspend fun estimate(description: String): Result<AiMealEstimate> =
        Result.failure(UnsupportedOperationException("nem ez a teszt tárgya"))

    companion object {
        /** Válasz felismert művelettel — ilyenkor jelenik meg a jóváhagyó kártya. */
        fun withAction(label: String = "Átírom a mai vacsorát."): FakeMealAi = FakeMealAi(
            Result.success(
                AiChatResponse(
                    reply = "Átírom a vacsorát valami könnyebbre.",
                    action = AiChatAction(
                        type = "REGENERATE_DAYS",
                        dayIndexes = listOf(0),
                        instruction = "csak a vacsorát cseréld le",
                        confirmLabel = label,
                    ),
                )
            )
        )
    }
}
