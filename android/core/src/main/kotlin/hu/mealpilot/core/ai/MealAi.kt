package hu.mealpilot.core.ai

/**
 * Az étrendet előállító AI absztrakciója.
 *
 * Külön interfész, hogy a felület és az üzleti logika ne kösse magát a szolgáltatóhoz:
 * ma a felhasználó saját Anthropic-kulcsával, később egy saját backend proxyn keresztül
 * (előfizetéses modell) ugyanez a hívás megy ki.
 */
interface MealAi {

    /** Igaz, ha a hívás konfigurálva van (van API kulcs vagy backend). */
    val isConfigured: Boolean

    suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit = {},
    ): Result<AiPlanResponse>

    suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse>
}

data class GenerationProgress(
    val stage: Stage,
    val currentChunk: Int = 0,
    val totalChunks: Int = 1,
    /** Eddig megérkezett karakterek száma — folyamatjelzőhöz. */
    val receivedChars: Int = 0,
    val message: String = "",
) {
    enum class Stage { PREPARING, STREAMING, VALIDATING, REPAIRING, DONE }

    val fraction: Float
        get() = if (totalChunks <= 0) 0f else ((currentChunk.toFloat()) / totalChunks).coerceIn(0f, 1f)
}

/**
 * Hosszú terveket heti darabokra bontunk. Egy 30 napos terv egyetlen válaszban
 * a kimeneti limitbe ütközne, és hiba esetén az egészet újra kellene kérni;
 * hetenként viszont haladás is mutatható, és a rendszerprompt cache-elve marad.
 */
object PlanChunker {

    const val DEFAULT_CHUNK_DAYS = 7

    data class Chunk(val startDayIndex: Int, val days: Int, val index: Int, val total: Int)

    fun chunks(totalDays: Int, chunkDays: Int = DEFAULT_CHUNK_DAYS): List<Chunk> {
        require(totalDays > 0) { "A terv legalább 1 napos legyen." }
        val size = chunkDays.coerceIn(1, 10)
        val count = (totalDays + size - 1) / size
        return (0 until count).map { i ->
            val start = i * size
            Chunk(
                startDayIndex = start,
                days = minOf(size, totalDays - start),
                index = i,
                total = count,
            )
        }
    }
}
