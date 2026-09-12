package hu.mealpilot.app.data.ai

import android.util.Log
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.ChatTurn
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
 * A tervezés menete, a szolgáltatótól függetlenül.
 *
 * A darabolás, az ellenőrzés, az adagok igazítása és a javító kör mind itt van, mert
 * ezek nem attól függnek, hogy a modellhívás a felhasználó kulcsán vagy a saját
 * backendünkön megy ki. Az örökösnek egyetlen dolga van: adjon vissza szöveget egy
 * kérésre, és streamelje a közben érkező karakterek számát.
 */
abstract class StreamingMealAi(
    protected val strings: AppStrings,
    /**
     * A nyelv FÜGGVÉNYKÉNT, nem értékként.
     *
     * A felhasználó menet közben is válthat nyelvet, és a tervezők hosszú életű
     * példányok: egy befagyasztott érték a váltás után a régi nyelven tervezne.
     * Értékként az lenne a másik megoldás, hogy minden híváshoz új példány készül —
     * az viszont a modellklienst is újraépítené, kapcsolatkészlettel együtt.
     */
    private val languageProvider: () -> AppLanguage,
) : MealAi {

    protected val language: AppLanguage get() = languageProvider()

    /** Melyik rendszerprompttal és mekkora kerettel dolgozik a hívás. */
    enum class AiTask(val maxOutputTokens: Long) {
        /** Egy tervszakasz (néhány nap) legenerálása. */
        PLAN(24_000),

        /** Egyetlen nap átírása. */
        DAY(6_000),

        /** Beszélgetés — rövid válasz, kicsi keret. */
        CHAT(2_000),
    }

    /**
     * Egy hívás a modellhez.
     *
     * @param planDays a TELJES terv hossza napokban — a backend ebből ellenőrzi, hogy
     *   a csomag engedi-e. Nem a mostani szakasz hossza.
     * @param chunkIndex a tervszakasz sorszáma; csak a 0. számít új tervnek.
     * @param isRetry javító kör: ugyanazt a szakaszt kéri újra, tehát nem új terv.
     * @param onChars minden beérkezett szakasz után az eddigi karakterszámmal hívódik.
     */
    protected abstract suspend fun call(
        task: AiTask,
        userText: String,
        planDays: Int = 0,
        chunkIndex: Int = 0,
        isRetry: Boolean = false,
        onChars: (Int) -> Unit = {},
    ): String

    /** A nyers kivételből a felületen megjeleníthető, magyar üzenetű hiba. */
    protected abstract fun translate(error: Throwable): Throwable

    final override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
        onChunk: suspend (AiPlanResponse) -> Unit,
    ): Result<AiPlanResponse> = withContext(Dispatchers.IO) {
        try {
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
                        message = if (chunk.total > 1) {
                            strings[R.string.progress_week, chunk.index + 1]
                        } else {
                            strings[R.string.progress_plan]
                        },
                    )
                )

                val chunkRequest = request.copy(
                    days = chunk.days,
                    startDayIndex = request.startDayIndex + chunk.startDayIndex,
                    totalDays = request.days,
                    avoidRecipes = usedNames.toList(),
                )

                val chunkPlan = generateChunk(chunkRequest, chunk, onProgress)
                allDays += chunkPlan.days
                coachNotes += chunkPlan.coachNotes
                usedNames += chunkPlan.days.flatMap { day -> day.meals.map { it.name } }
                if (title.isBlank()) title = chunkPlan.planTitle
                if (summary.isBlank()) summary = chunkPlan.summary

                // A kész szakaszt azonnal kiadjuk, hogy a felhasználó már használhassa,
                // miközben a többi nap még készül.
                onChunk(chunkPlan)
                onProgress(
                    GenerationProgress(
                        stage = GenerationProgress.Stage.STREAMING,
                        currentChunk = chunk.index + 1,
                        totalChunks = chunk.total,
                        daysReady = allDays.size,
                        message = if (chunk.index + 1 < chunk.total) {
                            strings[R.string.progress_days_ready, allDays.size]
                        } else {
                            strings[R.string.progress_done]
                        },
                    )
                )
            }

            onProgress(
                GenerationProgress(
                    stage = GenerationProgress.Stage.DONE,
                    currentChunk = chunks.size,
                    totalChunks = chunks.size,
                    daysReady = allDays.size,
                    message = strings[R.string.progress_done],
                )
            )

            Result.success(
                AiPlanResponse(
                    planTitle = title.ifBlank { strings[R.string.plan_title] },
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

    /** Egy szakasz legenerálása, szükség esetén egy javító körrel. */
    private suspend fun generateChunk(
        chunkRequest: PlanRequest,
        chunk: PlanChunker.Chunk,
        onProgress: (GenerationProgress) -> Unit,
    ): AiPlanResponse {
        val basePrompt = PlanPrompts.userPrompt(chunkRequest, language)
        val expectedMeals = MealSlot.forMealsPerDay(chunkRequest.profile.mealsPerDay).size

        var prompt = basePrompt
        var lastProblems: List<String> = emptyList()

        repeat(MAX_ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            val stage = if (attempt == 0) GenerationProgress.Stage.STREAMING else GenerationProgress.Stage.REPAIRING
            val label = strings[if (attempt == 0) R.string.progress_recipes else R.string.progress_tuning]

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
            val raw = call(
                task = AiTask.PLAN,
                userText = prompt,
                planDays = chunkRequest.totalDays,
                chunkIndex = chunk.index,
                // A javító kör ugyanazt a szakaszt kéri újra — a kvótának nem új terv.
                isRetry = attempt > 0,
            ) { chars -> report(chars) }

            onProgress(
                GenerationProgress(
                    stage = GenerationProgress.Stage.VALIDATING,
                    currentChunk = chunk.index,
                    totalChunks = chunk.total,
                    message = strings[R.string.progress_validating],
                )
            )

            val parsed = PlanParser.parsePlan(raw)
            val rawPlan = parsed.getOrElse { error ->
                lastProblems = listOf(error.message ?: strings[R.string.error_unparsable_json])
                prompt = basePrompt + "\n\n" + PlanPrompts.repairPrompt(lastProblems, language)
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

            Log.i(TAG, "A(z) ${chunk.index + 1}. szakasz nem ment át az ellenőrzésen: $problems")
            lastProblems = problems
            prompt = basePrompt + "\n\n" + PlanPrompts.repairPrompt(problems, language)
        }

        throw PlanQualityException(
            problems = lastProblems,
            message = buildString {
                append(strings[R.string.error_plan_quality])
                if (lastProblems.isNotEmpty()) {
                    append(" ")
                    append(strings[R.string.error_plan_quality_details])
                    append(" ")
                    append(lastProblems.take(3).joinToString("; "))
                }
            },
        )
    }

    final override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = withContext(Dispatchers.IO) {
        try {
            val raw = call(
                task = AiTask.DAY,
                userText = PlanPrompts.refineDayPrompt(request, currentDayJson, instruction, language),
                planDays = 1,
            )
            val parsed = PlanParser.parseDay(raw).getOrThrow()
            // Ugyanaz a kerekítés, mint a tervezésnél — egy átírt nap se adjon 178 grammot.
            Result.success(parsed.copy(day = PlanRepair.humanizeDay(parsed.day)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(translate(error))
        }
    }

    final override suspend fun chat(
        context: ChatContext,
        history: List<ChatTurn>,
        message: String,
    ): Result<AiChatResponse> = withContext(Dispatchers.IO) {
        try {
            val raw = call(
                task = AiTask.CHAT,
                userText = ChatPrompts.userPrompt(context, history, message, language),
            )
            Result.success(PlanParser.parseChat(raw).getOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(translate(error))
        }
    }

    protected companion object {
        const val TAG = "MealAi"

        /** Egy első próbálkozás + egy javító kör. Több kör már csak pénzt égetne. */
        const val MAX_ATTEMPTS = 2
    }
}

open class MealAiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MissingApiKeyException(message: String) : MealAiException(message)

class EmptyResponseException(message: String) : MealAiException(message)

class PlanQualityException(val problems: List<String>, message: String) : MealAiException(message)

/**
 * A szerver visszautasította a kérést, mert elfogyott a keret.
 *
 * A felület ebből tudja, hogy nem hibát kell mutatni, hanem az előfizetést felajánlani.
 */
class QuotaExceededException(
    val code: String,
    message: String,
    val upgradeOffered: Boolean,
) : MealAiException(message)
