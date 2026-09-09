package hu.mealpilot.app.data.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import hu.mealpilot.app.data.prefs.AiEffort
import hu.mealpilot.app.data.prefs.AiModel
import hu.mealpilot.app.data.prefs.AppSettings
import hu.mealpilot.app.data.prefs.SecureKeyStore
import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.PlanChunker
import hu.mealpilot.core.ai.PlanParser
import hu.mealpilot.core.ai.PlanPrompts
import hu.mealpilot.core.ai.PlanRepair
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.ai.PlanValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * A tervet a hivatalos Anthropic Java SDK-n keresztül generálja.
 *
 * A hosszú terveket [PlanChunker] szerint hetekre bontja: egy hónapnyi recept egyetlen
 * válaszban a kimeneti limitbe futna, hetenként viszont mutatható a haladás, egy hibás
 * hét külön újrakérhető, és az állandó rendszerprompt cache-elve marad a hívások között.
 */
class AnthropicMealAi(
    private val keyStore: SecureKeyStore,
    private val settingsProvider: suspend () -> AppSettings,
) : MealAi {

    override val isConfigured: Boolean get() = keyStore.hasApiKey()

    @Volatile private var cachedClient: AnthropicClient? = null
    @Volatile private var cachedKey: String? = null

    private fun client(apiKey: String): AnthropicClient {
        cachedClient?.takeIf { cachedKey == apiKey }?.let { return it }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build().also {
            cachedClient = it
            cachedKey = apiKey
        }
    }

    override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
    ): Result<AiPlanResponse> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.apiKey()
            ?: return@withContext Result.failure(MissingApiKeyException())

        try {
            val settings = settingsProvider()
            val client = client(apiKey)
            val chunks = PlanChunker.chunks(request.days)

            val allDays = mutableListOf<AiDay>()
            val coachNotes = mutableListOf<String>()
            val usedNames = request.avoidRecipes.toMutableList()
            var title = ""
            var summary = ""

            for (chunk in chunks) {
                coroutineContext.ensureActive()
                onProgress(
                    GenerationProgress(
                        stage = GenerationProgress.Stage.PREPARING,
                        currentChunk = chunk.index,
                        totalChunks = chunk.total,
                        message = if (chunk.total > 1) "${chunk.index + 1}. hét összeállítása…" else "Terv összeállítása…",
                    )
                )

                val chunkRequest = request.copy(
                    days = chunk.days,
                    startDayIndex = request.startDayIndex + chunk.startDayIndex,
                    totalDays = request.days,
                    avoidRecipes = usedNames.toList(),
                )

                val chunkPlan = generateChunk(client, settings, chunkRequest, chunk, onProgress)
                allDays += chunkPlan.days
                coachNotes += chunkPlan.coachNotes
                usedNames += chunkPlan.days.flatMap { day -> day.meals.map { it.name } }
                if (title.isBlank()) title = chunkPlan.planTitle
                if (summary.isBlank()) summary = chunkPlan.summary
            }

            onProgress(
                GenerationProgress(
                    stage = GenerationProgress.Stage.DONE,
                    currentChunk = chunks.size,
                    totalChunks = chunks.size,
                    message = "Kész.",
                )
            )

            Result.success(
                AiPlanResponse(
                    planTitle = title.ifBlank { "Étrend" },
                    summary = summary,
                    days = allDays.sortedBy { it.dayIndex },
                    coachNotes = coachNotes.distinct().take(6),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(translate(error))
        }
    }

    /** Egy hét legenerálása, szükség esetén egy javító körrel. */
    private suspend fun generateChunk(
        client: AnthropicClient,
        settings: AppSettings,
        chunkRequest: PlanRequest,
        chunk: PlanChunker.Chunk,
        onProgress: (GenerationProgress) -> Unit,
    ): AiPlanResponse {
        val basePrompt = PlanPrompts.userPrompt(chunkRequest)
        val expectedMeals = MealSlot.forMealsPerDay(chunkRequest.profile.mealsPerDay).size

        var prompt = basePrompt
        var lastProblems: List<String> = emptyList()

        repeat(MAX_ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            val stage = if (attempt == 0) GenerationProgress.Stage.STREAMING else GenerationProgress.Stage.REPAIRING
            val label = if (attempt == 0) "Receptek írása…" else "A kalóriakeret finomhangolása…"

            fun report(chars: Int) = onProgress(
                GenerationProgress(
                    stage = stage,
                    currentChunk = chunk.index,
                    totalChunks = chunk.total,
                    receivedChars = chars,
                    message = label,
                )
            )

            // A jelzést a hívás ELŐTT is kiadjuk. A modell a válasz első karaktere előtt
            // hosszan gondolkodhat, és addig a korábbi üzenet ragadna kint — pont az kelti
            // azt a benyomást, hogy az app megállt.
            report(0)
            val raw = callModel(client, settings, prompt) { chars -> report(chars) }

            onProgress(
                GenerationProgress(
                    stage = GenerationProgress.Stage.VALIDATING,
                    currentChunk = chunk.index,
                    totalChunks = chunk.total,
                    message = "Ellenőrzés…",
                )
            )

            val parsed = PlanParser.parsePlan(raw)
            val rawPlan = parsed.getOrElse { error ->
                lastProblems = listOf(error.message ?: "A válasz nem volt értelmezhető JSON.")
                prompt = basePrompt + "\n\n" + PlanPrompts.repairPrompt(lastProblems)
                return@repeat
            }

            // Az arányos kalóriaeltérést kiszámoljuk, nem újrakérjük. Egy 10%-kal elcsúszott
            // nap miatt az egész hetet újragenerálni percekbe és pénzbe kerülne, miközben az
            // adagok arányos igazítása ugyanazt az eredményt adja azonnal.
            val repaired = PlanRepair.normalize(rawPlan, chunkRequest.budget.target)
            if (repaired.adjusted.isNotEmpty()) {
                Log.i(TAG, "Adagok igazítva a kerethez: ${repaired.adjusted}")
            }
            val plan = repaired.plan

            val problems = PlanValidator.validate(
                plan = plan,
                target = chunkRequest.budget.target,
                expectedDays = chunkRequest.days,
                expectedMealsPerDay = expectedMeals,
                restrictions = chunkRequest.profile.effectiveRestrictions,
            )
            if (problems.isEmpty()) return plan

            Log.i(TAG, "A(z) ${chunk.index + 1}. hét nem ment át az ellenőrzésen: $problems")
            lastProblems = problems
            prompt = basePrompt + "\n\n" + PlanPrompts.repairPrompt(problems)
        }

        throw PlanQualityException(lastProblems)
    }

    override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.apiKey() ?: return@withContext Result.failure(MissingApiKeyException())
        try {
            val raw = callModel(
                client = client(apiKey),
                settings = settingsProvider(),
                userText = PlanPrompts.refineDayPrompt(request, currentDayJson, instruction),
                onChars = {},
            )
            Result.success(PlanParser.parseDay(raw).getOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(translate(error))
        }
    }

    /**
     * Egy hívás a Messages API-ra, streamelve. A streamelés itt nem kényelmi kérdés:
     * nagy max_tokens mellett a nem streamelt kérés a HTTP időkorlátba futna.
     */
    private suspend fun callModel(
        client: AnthropicClient,
        settings: AppSettings,
        userText: String,
        onChars: (Int) -> Unit,
    ): String {
        val builder = MessageCreateParams.builder()
            .model(settings.model.id)
            .maxTokens(MAX_OUTPUT_TOKENS)
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(PlanPrompts.SYSTEM)
                        // A rendszerprompt minden hívásnál azonos, ezért cache-elhető:
                        // a heti darabok és a javító körök után is olcsóbb lesz.
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()
                )
            )
            .addUserMessage(userText)

        // A Haiku 4.5 nem fogadja el az effort paramétert, ezért csak a nagyobb modelleknél adjuk meg.
        if (settings.model != AiModel.HAIKU) {
            builder.outputConfig(
                OutputConfig.builder().effort(settings.effort.toApiEffort()).build()
            )
        }

        val text = StringBuilder()
        client.messages().createStreaming(builder.build()).use { response ->
            // Optional.stream() csak Java 9-től van; Androidon iterátorral és orElse(null)-lal
            // maradunk a Java 8-as felületen, amit a desugaring biztosan lefed.
            val events = response.stream().iterator()
            while (events.hasNext()) {
                val delta = events.next().contentBlockDelta().orElse(null) ?: continue
                val textDelta = delta.delta().text().orElse(null) ?: continue
                text.append(textDelta.text())
                onChars(text.length)
            }
        }
        coroutineContext.ensureActive()
        if (text.isBlank()) throw EmptyResponseException()
        return text.toString()
    }

    private fun AiEffort.toApiEffort(): OutputConfig.Effort = when (this) {
        AiEffort.LOW -> OutputConfig.Effort.LOW
        AiEffort.MEDIUM -> OutputConfig.Effort.MEDIUM
        AiEffort.HIGH -> OutputConfig.Effort.HIGH
    }

    /** A nyers kivételekből a felületen megjeleníthető, magyar üzenetű hibát csinál. */
    private fun translate(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is MealAiException -> error
        is UnauthorizedException -> MealAiException(
            "Az API kulcs érvénytelen vagy lejárt. Nézd meg a Beállításokban.", error
        )
        is RateLimitException -> MealAiException(
            "Túl sok kérés ment ki egymás után. Várj egy percet, aztán próbáld újra.", error
        )
        is BadRequestException -> MealAiException(
            "A kérést az API visszautasította: ${error.message ?: "ismeretlen ok"}", error
        )
        is AnthropicServiceException -> MealAiException(
            "Az AI szolgáltatás hibát adott (${error.errorType().map { it.toString() }.orElse("ismeretlen")}). " +
                "Próbáld újra kicsit később.",
            error,
        )
        is java.io.IOException -> MealAiException(
            "Nem sikerült elérni az AI szolgáltatást. Ellenőrizd az internetkapcsolatot.", error
        )
        else -> MealAiException(error.message ?: "Ismeretlen hiba a terv készítése közben.", error)
    }

    private companion object {
        const val TAG = "AnthropicMealAi"
        const val MAX_OUTPUT_TOKENS = 24_000L

        /** Egy első próbálkozás + egy javító kör. Több kör már csak pénzt égetne. */
        const val MAX_ATTEMPTS = 2
    }
}

open class MealAiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MissingApiKeyException : MealAiException(
    "Nincs beállítva API kulcs. A Beállításokban add meg az Anthropic kulcsodat, " +
        "vagy használd a beépített offline tervezőt."
)

class EmptyResponseException : MealAiException("Az AI üres választ küldött. Próbáld újra.")

class PlanQualityException(val problems: List<String>) : MealAiException(
    buildString {
        append("A terv kétszer sem felelt meg a céloknak. ")
        if (problems.isNotEmpty()) {
            append("Az utolsó hibák: ")
            append(problems.take(3).joinToString("; "))
        }
    }
)
