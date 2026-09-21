package hu.mealpilot.app.data.ai

import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.app.R
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.ai.RecipeBank
import hu.mealpilot.core.model.DietRestriction

/**
 * Tartalék tervező, ami internetkapcsolat nélkül is működik.
 *
 * Egy beépített receptbankból forgat étrendet, és a napi kalóriacélhoz méretezi az adagokat.
 * A szabad szöveges kéréseket nem érti, de a napi keretet és a makrókat pontosan tartja,
 * így az app a tervezőszolgáltatás elérhetetlensége esetén sem marad használhatatlan.
 */
class OfflineMealAi(
    /** Lásd [StreamingMealAi]: a nyelv függvény, mert a felhasználó menet közben vált. */
    private val languageProvider: () -> AppLanguage,
    private val strings: AppStrings,
) : MealAi {

    private val language: AppLanguage get() = languageProvider()

    override val isConfigured: Boolean = true

    /** Sablonokból nem lehet megmondani, hány kalória egy gyros. */
    override val canEstimate: Boolean = false

    override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
        onChunk: suspend (AiPlanResponse) -> Unit,
    ): Result<AiPlanResponse> = runCatching {
        // A TERV nyelve, nem a felületé: a kérésbe zárt nyelv akkor is végigviszi a
        // generálást, ha a felhasználó közben átkapcsol. Lásd [PlanRequest.language].
        val lang = request.language ?: language
        // A TERVBE kerülő szövegek a terv nyelvén; a folyamatjelző viszont a
        // FELHASZNÁLÓNAK szól, az marad a felület nyelvén.
        val planStrings = strings.forLanguage(lang)
        val slots = MealSlot.forMealsPerDay(request.profile.mealsPerDay)
        val shares = slotShares(slots)
        val target = request.budget.target

        val days = (0 until request.days).map { offset ->
            val dayIndex = request.startDayIndex + offset
            onProgress(
                GenerationProgress(
                    stage = GenerationProgress.Stage.STREAMING,
                    currentChunk = offset,
                    totalChunks = request.days,
                    message = strings[R.string.progress_day, offset + 1],
                )
            )
            AiDay(
                dayIndex = dayIndex,
                title = planStrings[R.string.offline_day_title, dayIndex + 1],
                note = planStrings[R.string.offline_day_note],
                meals = slots.mapIndexed { slotIndex, slot ->
                    safeMeal(
                        slot = slot,
                        startIndex = dayIndex * slots.size + slotIndex,
                        targetKcal = target.kcal * shares[slotIndex],
                        time = request.profile.mealTimes.getOrNull(slotIndex) ?: slot.defaultTime,
                        restrictions = request.profile.effectiveRestrictions,
                        language = lang,
                    )
                },
            )
        }

        onProgress(
            GenerationProgress(
                stage = GenerationProgress.Stage.DONE,
                currentChunk = request.days,
                totalChunks = request.days,
                message = strings[R.string.progress_done],
            )
        )

        val result = AiPlanResponse(
            planTitle = planStrings[R.string.offline_plan_title],
            summary = planStrings[R.string.offline_plan_summary],
            days = days,
            coachNotes = listOf(
                planStrings[R.string.offline_tip_water],
                planStrings[R.string.offline_tip_protein],
            ),
        )
        onChunk(result)
        result
    }

    override suspend fun chat(
        context: ChatContext,
        history: List<ChatTurn>,
        message: String,
    ): Result<AiChatResponse> = Result.failure(
        MealAiException(strings[R.string.offline_chat_needs_network])
    )

    override suspend fun estimate(description: String): Result<AiMealEstimate> = Result.failure(
        MealAiException(strings[R.string.offline_estimate_needs_network])
    )

    override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = Result.failure(
        MealAiException(strings[R.string.offline_cannot_refine])
    )

    /** A napi kalória elosztása az étkezések között. */
    private fun slotShares(slots: List<MealSlot>): List<Double> {
        val weights = slots.map { slot ->
            when (slot) {
                MealSlot.BREAKFAST -> 0.25
                MealSlot.LUNCH -> 0.35
                MealSlot.DINNER -> 0.30
                else -> 0.10
            }
        }
        val sum = weights.sum()
        return weights.map { it / sum }
    }

    /**
     * A következő olyan sablon, amit ezzel a kizárással KI IS SZABAD adni.
     *
     * A sablonos tervező eddig kizárólag a napok és a fogások sorszámából választott, a
     * profilt meg sem nézte. A modell válaszát a [RestrictionChecker] átvizsgálja, és
     * hiba esetén javító kör készül — itt viszont nincs kit megkérni a javításra: ezt a
     * tervet MI rakjuk ki, és egyenesen a felhasználóhoz kerül.
     *
     * Ez az út nem ritka: akkor lép működésbe, ha a tervezés félbeszakad (hálózat,
     * kvóta, modellhiba), tehát pont akkor, amikor a felhasználó amúgy is bosszankodik.
     * A sablonok között van „teljes kiőrlésű kenyér" és „görög joghurt dióval" is —
     * vagyis egy gluténérzékeny vagy mogyoróallergiás felhasználó azt kapta volna, amit
     * a saját profiljában kizárt.
     *
     * Ha egyetlen sablon sem felel meg, HIBÁT dobunk. Kevesebb nappal beérni rossz;
     * allergént kiadni sokkal rosszabb, és ezt a különbséget nem szabad elmosni.
     *
     * A szűrés viszont csak akkor ér valamit, ha marad utána étel. A [RecipeBank]
     * emiatt szándékosan bőséges, és a `RecipeBankCoverageTest` minden kizárásra és
     * étrendi stílusra megméri, hány sablon marad — mindkét nyelven. Korábban három
     * reggeli volt, mindhárman tejtermékkel: egy vegán felhasználó biztosan ebbe a
     * hibaágba futott.
     */
    private fun safeMeal(
        slot: MealSlot,
        startIndex: Int,
        targetKcal: Double,
        time: String,
        restrictions: Set<DietRestriction>,
        language: AppLanguage,
    ): AiMeal {
        val bank = RecipeBank.forSlot(slot)
        // Ugyanonnan indulunk, mint eddig — kizárás nélkül a választás változatlan.
        for (offset in bank.indices) {
            val candidate = bank[(startIndex + offset) % bank.size]
                .scaledTo(targetKcal = targetKcal, slot = slot, time = time, language = language)
            if (RestrictionChecker.isSafe(candidate, restrictions, language)) return candidate
        }
        throw MealAiException(strings[R.string.offline_no_safe_meal])
    }
}
