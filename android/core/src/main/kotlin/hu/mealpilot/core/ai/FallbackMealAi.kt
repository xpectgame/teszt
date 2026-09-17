package hu.mealpilot.core.ai

import kotlin.coroutines.cancellation.CancellationException

/**
 * Két tervező egymás mögé kötve: ha az elsődleges elakad, a beépített fejezi be a tervet.
 *
 * Enélkül egy kifogyott API-keret vagy egy leálló szolgáltatás azt jelentené, hogy a
 * felhasználó egy perc várakozás után egy piros hibaüzenetet kap — pedig a sablonos
 * tervező ugyanazt a kalóriakeretet pontosan eltalálja, és azonnal válaszol. Egy kevésbé
 * személyre szabott étrend használható; a hibaüzenet nem az.
 *
 * A helyettesítés NEM csendes: a pótolt napok külön megjegyzést kapnak, mert egy sablonból
 * kirakott napot a felhasználónak jogában áll megkülönböztetni attól, amit kért.
 *
 * Két dologra szándékosan nem terjed ki:
 *
 *  - **A kvótás elutasításra.** Ha a szerver azért mondott nemet, mert elfogyott a havi
 *    keret, akkor az előfizetést kell felajánlani, nem sablontervet adni. A [isRecoverable]
 *    döntést a hívó hozza meg, mert a kivételtípusok az app modulban élnek.
 *  - **A beszélgetésre és a nap átírására.** Ott a sablonos változat nem tud jobbat adni
 *    egy őszinte hibaüzenetnél, a hívás pedig olcsó megismételni.
 */
class FallbackMealAi(
    private val primary: MealAi,
    private val fallback: MealAi,
    /** Igaz, ha az adott hibára szabad sablontervvel válaszolni. */
    private val isRecoverable: (Throwable) -> Boolean,
    /** A folyamatjelzőn megjelenő szöveg a váltás pillanatában. */
    private val switchMessage: String,
    /** Megjegyzés a pótolt napokhoz — ez kerül a terv tippjei közé. */
    private val fallbackNote: String,
    /** A terv összefoglalója, ha egyetlen nap sem jött meg az elsődlegestől. */
    private val fallbackSummary: String,
    /**
     * Megjegyzés arra az esetre, ha a TARTALÉK IS elbukott, de néhány nap már megvan.
     *
     * Ilyenkor rövidebb terv megy ki, mint amit a felhasználó kért. Eddig ez némán
     * történt: a képernyőn két nap jelent meg hét helyett, magyarázat nélkül. A
     * sablonbank akkor mond nemet, ha a kizárások kimerítik — vagyis pont a
     * legérzékenyebb felhasználónál, aki a legkevésbé érti, mi történt.
     */
    private val shortPlanNote: (deliveredDays: Int, requestedDays: Int) -> String =
        { delivered, requested -> "A terv $delivered napra készült el a kért $requested helyett." },
    /**
     * Értesítés a váltásról: a hiba, és hogy az elsődleges hány napot adott át előtte.
     *
     * A napok száma nem statisztika, hanem döntési alap: ha nulla, akkor a
     * szolgáltatás felé egyetlen hívás sem ment el sikeresen, tehát a felhasználó
     * havi keretéből sem fogyhat semmi.
     */
    private val onFallback: (error: Throwable, daysFromPrimary: Int) -> Unit = { _, _ -> },
) : MealAi {

    override val isConfigured: Boolean get() = primary.isConfigured || fallback.isConfigured

    /** Csak az elsődleges tud becsülni — a sablonok nem. */
    override val canEstimate: Boolean get() = primary.canEstimate

    override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
        onChunk: suspend (AiPlanResponse) -> Unit,
    ): Result<AiPlanResponse> {
        val days = mutableListOf<AiDay>()
        val notes = mutableListOf<String>()
        var title = ""
        var summary = ""

        val attempt = primary.generatePlan(
            request = request,
            onProgress = onProgress,
            onChunk = { chunk ->
                days += chunk.days
                notes += chunk.coachNotes
                if (title.isBlank()) title = chunk.planTitle
                if (summary.isBlank()) summary = chunk.summary
                onChunk(chunk)
            },
        )
        if (attempt.isSuccess) return attempt

        val error = attempt.exceptionOrNull() ?: return attempt
        if (error is CancellationException) throw error
        if (!isRecoverable(error)) return attempt

        val missing = request.days - days.size
        if (missing <= 0) {
            // Minden nap megvan, csak a lezárás bukott el. Nincs mit pótolni.
            return Result.success(assemble(days, notes, title, summary))
        }

        onFallback(error, days.size)
        onProgress(
            GenerationProgress(
                stage = GenerationProgress.Stage.STREAMING,
                currentChunk = days.size,
                totalChunks = request.days,
                daysReady = days.size,
                message = switchMessage,
            )
        )

        // A hiányzó napok a terv VÉGÉRŐL maradtak el: az elsődleges elölről haladt, és
        // a már kiadott napokat a hívó eltárolta. A pótlás tehát ott folytatódik.
        val nextIndex = (days.maxOfOrNull { it.dayIndex } ?: (request.startDayIndex - 1)) + 1
        val rest = fallback.generatePlan(
            request = request.copy(days = missing, startDayIndex = nextIndex),
            // A sablonos tervező saját szakaszjelzést ad, ami ehhez a részhez van
            // méretezve — a felhasználó felé az egész tervről szóló jelzés a helyes.
            onProgress = {},
            onChunk = { chunk ->
                val marked = chunk.copy(
                    summary = if (days.isEmpty()) fallbackSummary else chunk.summary,
                    coachNotes = listOf(fallbackNote) + chunk.coachNotes,
                )
                days += marked.days
                notes += marked.coachNotes
                if (title.isBlank()) title = marked.planTitle
                if (summary.isBlank()) summary = marked.summary
                onChunk(marked)
            },
        )

        // Ha a tartalék is elbukik, az eredeti hibát adjuk vissza: azt kell megérteni,
        // nem azt, hogy a mentőöv is kilyukadt.
        if (rest.isFailure && days.isEmpty()) return attempt

        // A tartalék elbukott, de néhány nap már megvan: rövidebb terv megy ki, mint
        // amit kértek. Ezt KIMONDJUK. A hiányzó napokat a felhasználó úgyis látja;
        // ha nem írjuk oda, miért, akkor az app tűnik hibásnak.
        if (days.size < request.days) {
            // A lista ELEJÉRE: az `assemble` hatnál többet nem visz tovább, és ez a
            // megjegyzés fontosabb minden tippnél, amit a terv mellé kaptunk.
            notes.add(0, shortPlanNote(days.size, request.days))
        }

        onProgress(
            GenerationProgress(
                stage = GenerationProgress.Stage.DONE,
                currentChunk = request.days,
                totalChunks = request.days,
                daysReady = days.size,
                message = switchMessage,
            )
        )
        return Result.success(assemble(days, notes, title, summary))
    }

    private fun assemble(
        days: List<AiDay>,
        notes: List<String>,
        title: String,
        summary: String,
    ) = AiPlanResponse(
        planTitle = title,
        summary = summary,
        days = days.sortedBy { it.dayIndex },
        coachNotes = notes.distinct().take(6),
    )

    override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = primary.refineDay(request, currentDayJson, instruction)

    override suspend fun chat(
        context: ChatContext,
        history: List<ChatTurn>,
        message: String,
    ): Result<AiChatResponse> = primary.chat(context, history, message)

    /**
     * A becslés sem esik vissza: a beépített sablonoknak fogalmuk sincs arról, mit evett
     * a felhasználó. Egy kitalált szám rosszabb, mint a bevallott kudarc — a naplóban
     * évekig ott maradna.
     */
    override suspend fun estimate(description: String): Result<AiMealEstimate> =
        primary.estimate(description)
}
