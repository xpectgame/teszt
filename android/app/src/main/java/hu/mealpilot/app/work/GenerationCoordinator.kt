package hu.mealpilot.app.work

import android.util.Log
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.ai.QuotaExceededException
import hu.mealpilot.app.data.repo.PlanGenerationOutcome
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.billing.PaidFeature
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

    /**
     * Ha a felület fizetős falba ütközik, ide kerül az indok. Az AppRoot ezt figyeli, és
     * megnyitja a paywallt — így nem kell minden képernyőnek külön tudnia róla.
     */
    private val _paywallPrompt = MutableStateFlow<String?>(null)
    val paywallPrompt: StateFlow<String?> = _paywallPrompt.asStateFlow()

    fun requestPaywall(reason: String) {
        container.telemetry.record(TelemetryEvent.PAYWALL_SHOWN)
        _paywallPrompt.value = reason
    }

    fun consumePaywallPrompt() { _paywallPrompt.value = null }

    /**
     * A szerver kvóta miatti elutasítása nem hiba, hanem korlát: ilyenkor a paywallt
     * nyitjuk meg, nem egy piros üzenetet mutatunk. A helyi ellenőrzés ezt legtöbbször
     * megelőzi, de a döntés a szerveré — ez az ág az, ami tényleg érvényes.
     */
    private fun offerUpgradeIfQuota(error: Throwable?): Boolean {
        val quota = error as? QuotaExceededException ?: return false
        container.telemetry.record(TelemetryEvent.QUOTA_BLOCKED)
        if (quota.upgradeOffered) requestPaywall(quota.message ?: "Elfogyott a havi keret.")
        return quota.upgradeOffered
    }

    private var job: Job? = null

    val isBusy: Boolean get() = job?.isActive == true

    fun generatePlan(days: Int, startTomorrow: Boolean, freeText: String) {
        if (isBusy) return
        job = container.backgroundScope.launch {
            // A kvótát a munka MEGKEZDÉSE előtt nézzük meg: egy elutasított kérésért ne
            // fusson le a drága rész, és ne is fogyjon a keret.
            val entitlement = container.entitlements.current()
            entitlement.blockReason(PaidFeature.PLAN_GENERATION)?.let { reason ->
                requestPaywall(reason)
                return@launch
            }
            val allowedDays = entitlement.allowedPlanDays(days)

            try {
                container.telemetry.record(TelemetryEvent.PLAN_REQUESTED)
                begin(totalDays = allowedDays, headline = "Összeállítom az étrended")
                val profile = container.settings.currentProfile()
                val budget = EnergyCalculator.budget(profile, container.language)
                // -1 = nem volt visszaesés; 0 vagy több = ennyi nap jött a szolgáltatástól,
                // mielőtt a beépített tervező átvette.
                var daysFromService = -1
                val raw = container.planRepository.generateAndSave(
                    ai = container.mealAi(onFallback = { daysFromService = it }),
                    profile = profile,
                    budget = budget,
                    startDate = if (startTomorrow) LocalDate.now().plusDays(1) else LocalDate.now(),
                    days = allowedDays,
                    freeText = freeText,
                    language = container.language,
                    onProgress = { progress -> publish(progress, allowedDays) },
                )
                val result = raw.map { it.copy(usedFallback = daysFromService >= 0) }
                result.onSuccess {
                    ReminderRefreshWorker.refreshNow(container.appContext)
                    // A keretből csak az fogy, amiért a szolgáltatás tényleg dolgozott.
                    // Egy végig sablonból kirakott terv nem viheti el valakinek a havi
                    // egyetlen tervét — a szerver sem számolta el, mert oda el sem jutott.
                    if (daysFromService != 0) {
                        container.entitlements.recordPlanGenerated()
                        container.telemetry.record(TelemetryEvent.PLAN_GENERATED)
                    }
                }
                result.onFailure { error ->
                    container.telemetry.record(TelemetryEvent.PLAN_FAILED)
                    offerUpgradeIfQuota(error)
                }
                _lastOutcome.value = result
            } catch (error: Throwable) {
                Log.e(TAG, "A tervezés megszakadt.", error)
                container.telemetry.record(TelemetryEvent.PLAN_FAILED)
                offerUpgradeIfQuota(error)
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
            // A begin() a try-on BELÜL van. Kívül állva az innen induló kivétel
            // kezeletlenül szállt volna fel a háttérhatókörből, ami az app azonnali
            // kilépését jelenti — épp abban a pillanatban, amikor a felhasználó
            // jóváhagyta a műveletet.
            try {
                begin(totalDays = 0, headline = headline)
                val message = block { progress -> publish(progress, _status.value.totalDays) }
                _actionResult.value = message
            } catch (error: Throwable) {
                Log.e(TAG, "A művelet megszakadt.", error)
                offerUpgradeIfQuota(error)
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
