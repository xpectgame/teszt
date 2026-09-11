package hu.mealpilot.app.work

import android.util.Log
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.repo.PlanGenerationOutcome
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.energy.EnergyCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A hosszan futó tervezés egyetlen gazdája.
 *
 * Három dolgot old meg, amit korábban semmi nem:
 * 1. a munka nem a képernyőhöz tartozik, hanem az alkalmazáshoz — fülváltás, visszalépés
 *    vagy képernyőzár nem szakítja meg;
 * 2. amíg fut, egy előtérben futó szolgáltatás tartja életben a folyamatot, és értesítésen
 *    mutatja a haladást, tehát akkor is látod, ha közben mást csinálsz a telefonon;
 * 3. az állapotot bárhonnan lehet figyelni, így a felület minden fülön ugyanazt mutatja.
 */
class GenerationCoordinator(private val container: AppContainer) {

    data class Status(
        val running: Boolean = false,
        val daysReady: Int = 0,
        val totalDays: Int = 0,
        val headline: String = "",
        val detail: String = "",
        /** null, ha nem tudjuk, mennyi van hátra. */
        val fraction: Float? = null,
        val startedAtMillis: Long = 0L,
    ) {
        /** Van már használható nap, tehát a felhasználó elkezdheti nézni a tervet. */
        val hasUsableDays: Boolean get() = daysReady > 0
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Az utolsó befejezett generálás eredménye — a felület ebből mutat visszajelzést. */
    private val _lastOutcome = MutableStateFlow<Result<PlanGenerationOutcome>?>(null)
    val lastOutcome: StateFlow<Result<PlanGenerationOutcome>?> = _lastOutcome.asStateFlow()

    fun consumeOutcome() { _lastOutcome.value = null }

    private var job: Job? = null

    val isBusy: Boolean get() = job?.isActive == true

    fun generatePlan(days: Int, startTomorrow: Boolean, freeText: String) {
        if (isBusy) return
        job = container.backgroundScope.launch {
            begin(totalDays = days, headline = "Összeállítom az étrended")
            try {
                val profile = container.settings.currentProfile()
                val budget = EnergyCalculator.budget(profile)
                val result = container.planRepository.generateAndSave(
                    ai = container.mealAi(),
                    profile = profile,
                    budget = budget,
                    startDate = if (startTomorrow) LocalDate.now().plusDays(1) else LocalDate.now(),
                    days = days,
                    freeText = freeText,
                    onProgress = { progress -> publish(progress, days) },
                )
                result.onSuccess { ReminderRefreshWorker.refreshNow(container.appContext) }
                _lastOutcome.value = result
            } catch (error: Throwable) {
                Log.e(TAG, "A tervezés megszakadt.", error)
                _lastOutcome.value = Result.failure(error)
            } finally {
                end()
            }
        }
    }

    /**
     * Bármilyen egyéb hosszú művelet (pl. a beszélgetésből indított újratervezés).
     * A [block] visszatérési szövege kerül a felhasználóhoz.
     */
    fun runAction(headline: String, block: suspend (progress: (GenerationProgress) -> Unit) -> String) {
        if (isBusy) return
        job = container.backgroundScope.launch {
            begin(totalDays = 0, headline = headline)
            try {
                val message = block { progress -> publish(progress, _status.value.totalDays) }
                _actionResult.value = message
            } catch (error: Throwable) {
                Log.e(TAG, "A művelet megszakadt.", error)
                _actionResult.value = error.message ?: "Nem sikerült."
            } finally {
                end()
            }
        }
    }

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult.asStateFlow()

    fun consumeActionResult() { _actionResult.value = null }

    fun cancel() {
        job?.cancel()
        job = null
        end()
    }

    private fun begin(totalDays: Int, headline: String) {
        _status.value = Status(
            running = true,
            totalDays = totalDays,
            headline = headline,
            detail = "Indulás…",
            startedAtMillis = System.currentTimeMillis(),
        )
        GenerationService.start(container.appContext)
    }

    private fun end() {
        _status.value = Status()
        GenerationService.stop(container.appContext)
    }

    private fun publish(progress: GenerationProgress, totalDays: Int) {
        val current = _status.value
        _status.value = current.copy(
            daysReady = maxOf(current.daysReady, progress.daysReady),
            totalDays = if (totalDays > 0) totalDays else current.totalDays,
            detail = reassure(progress, current),
            fraction = when {
                current.totalDays > 0 && progress.daysReady > 0 ->
                    (progress.daysReady.toFloat() / current.totalDays).coerceIn(0f, 1f)
                progress.totalChunks > 1 -> progress.fraction
                else -> null
            },
        )
    }

    /**
     * Megnyugtató, de őszinte állapotszöveg.
     *
     * Egy hosszú műveletnél a legrosszabb, ha a felhasználó nem tudja, halad-e egyáltalán.
     * Ezért mindig azt mondjuk, ami éppen történik, és ha van már használható nap, azt is,
     * hogy nem kell tovább várnia.
     */
    private fun reassure(progress: GenerationProgress, current: Status): String {
        val total = current.totalDays
        val ready = maxOf(current.daysReady, progress.daysReady)
        val elapsedSec = (System.currentTimeMillis() - current.startedAtMillis) / 1000

        return when {
            ready in 1 until total ->
                "$ready nap már használható — a maradék ${total - ready} napon dolgozom."
            ready >= total && total > 0 -> "Utolsó simítások…"
            progress.stage == GenerationProgress.Stage.VALIDATING -> "Ellenőrzöm a tápértékeket…"
            progress.stage == GenerationProgress.Stage.REPAIRING -> "Igazítok a kalóriakeretre…"
            progress.receivedChars > 2000 -> "Írom a recepteket… mindjárt megvan az első pár nap."
            elapsedSec > 25 -> "Még gondolkodom az első napokon — pár másodperc."
            else -> "Összeválogatom az alapanyagokat…"
        }
    }

    private companion object {
        const val TAG = "GenerationCoordinator"
    }
}
