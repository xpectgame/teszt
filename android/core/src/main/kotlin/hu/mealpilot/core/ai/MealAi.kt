package hu.mealpilot.core.ai

/**
 * Az étrendet előállító és a felhasználóval beszélgető AI absztrakciója.
 *
 * Külön interfész, hogy a felület és az üzleti logika ne kösse magát a szolgáltatóhoz:
 * ma a felhasználó saját Anthropic-kulcsával, később egy saját backend proxyn keresztül
 * (előfizetéses modell) ugyanezek a hívások mennek ki.
 */
interface MealAi {

    /** Igaz, ha a hívás konfigurálva van (van API kulcs vagy backend). */
    val isConfigured: Boolean

    /**
     * Tud-e egyáltalán megbecsülni egy szavakkal leírt étkezést.
     *
     * Ez NEM ugyanaz, mint az [isConfigured]: a beépített sablontervező konfigurálva van
     * — tervet tud adni hálózat nélkül is —, de arról fogalma sincs, mit evett a
     * felhasználó. A felület ebből dönti el, hogy megmutatja-e a mezőt; egy sosem működő
     * gomb rosszabb, mint a hiányzó funkció.
     */
    val canEstimate: Boolean

    /**
     * @param onChunk minden elkészült szakasz után lefut, még a teljes terv megérkezése
     *   előtt. Erre épül az, hogy az első napok azonnal használhatók, miközben a többi
     *   még töltődik — egy hónapos tervnél ez perceket takarít meg a felhasználónak.
     */
    suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit = {},
        onChunk: suspend (AiPlanResponse) -> Unit = {},
    ): Result<AiPlanResponse>

    suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse>

    /** Szabad beszélgetés, felismert művelettel. */
    suspend fun chat(
        context: ChatContext,
        history: List<ChatTurn>,
        message: String,
    ): Result<AiChatResponse>

    /**
     * Szavakkal leírt étkezés tápértékének megbecslése.
     *
     * A naplózás a leggyakoribb művelet az appban, és eddig négy számot kért kézzel
     * attól, aki épp evett valamit. Aki tudja fejből a gyros makróit, az nem ezt az
     * appot használja.
     */
    suspend fun estimate(description: String): Result<AiMealEstimate>
}

data class GenerationProgress(
    val stage: Stage,
    val currentChunk: Int = 0,
    val totalChunks: Int = 1,
    /** Eddig megérkezett karakterek száma — folyamatjelzőhöz. */
    val receivedChars: Int = 0,
    /** Hány nap van már készen és menthető. */
    val daysReady: Int = 0,
    val message: String = "",
) {
    enum class Stage { PREPARING, STREAMING, VALIDATING, REPAIRING, DONE }

    val fraction: Float
        get() = if (totalChunks <= 0) 0f else ((currentChunk.toFloat()) / totalChunks).coerceIn(0f, 1f)
}

/**
 * Hosszú terveket szakaszokra bontunk. Egy 30 napos terv egyetlen válaszban a kimeneti
 * limitbe ütközne, és hiba esetén az egészet újra kellene kérni.
 *
 * Az első szakasz szándékosan rövidebb: az a cél, hogy a felhasználó másodpercek alatt
 * lásson használható napokat, ne percek múlva egy kész hónapot. A többi szakasz hosszabb,
 * mert azokra már nem vár aktívan.
 */
object PlanChunker {

    const val FIRST_CHUNK_DAYS = 3
    const val DEFAULT_CHUNK_DAYS = 7

    data class Chunk(val startDayIndex: Int, val days: Int, val index: Int, val total: Int)

    fun chunks(
        totalDays: Int,
        firstChunkDays: Int = FIRST_CHUNK_DAYS,
        chunkDays: Int = DEFAULT_CHUNK_DAYS,
    ): List<Chunk> {
        require(totalDays > 0) { "A terv legalább 1 napos legyen." }
        val first = firstChunkDays.coerceIn(1, 10)
        val rest = chunkDays.coerceIn(1, 10)

        val sizes = mutableListOf<Int>()
        var remaining = totalDays
        sizes += minOf(first, remaining)
        remaining -= sizes.first()
        while (remaining > 0) {
            val size = minOf(rest, remaining)
            sizes += size
            remaining -= size
        }

        var start = 0
        return sizes.mapIndexed { index, size ->
            Chunk(startDayIndex = start, days = size, index = index, total = sizes.size)
                .also { start += size }
        }
    }
}
