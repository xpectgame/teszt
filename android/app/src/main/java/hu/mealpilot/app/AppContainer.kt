package hu.mealpilot.app

import android.content.Context
import hu.mealpilot.app.billing.BillingGateway
import hu.mealpilot.app.billing.EntitlementRepository
import hu.mealpilot.app.billing.NoBillingGateway
import hu.mealpilot.app.billing.PlayBillingGateway
import hu.mealpilot.app.data.ai.AnthropicMealAi
import hu.mealpilot.app.data.ai.BackendMealAi
import hu.mealpilot.app.data.ai.OfflineMealAi
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.prefs.SecureKeyStore
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.app.data.repo.ChatRepository
import hu.mealpilot.app.data.remote.BackendClient
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.ReportRepository
import hu.mealpilot.app.data.repo.StatsRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import hu.mealpilot.app.work.GenerationCoordinator
import hu.mealpilot.core.ai.MealAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Kézi függőséginjektálás. Az app mérete ennyit még bőven elbír, cserébe nincs
 * annotációfeldolgozó a fordítási időben, és a receiverekből is elérhető minden.
 */
class AppContainer(context: Context) {

    /** Az alkalmazás kontextusa — a háttérmunkák ütemezéséhez kell. */
    val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val planRepository: PlanRepository by lazy {
        PlanRepository(database.planDao(), database.mealDao(), database.shoppingDao())
    }

    val trackingRepository: TrackingRepository by lazy {
        TrackingRepository(
            mealDao = database.mealDao(),
            mealLogDao = database.mealLogDao(),
            activityLogDao = database.activityLogDao(),
            weightLogDao = database.weightLogDao(),
        )
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            chatDao = database.chatDao(),
            planRepository = planRepository,
            tracking = trackingRepository,
            settings = settings,
        )
    }

    /**
     * Alkalmazás-élettartamú scope a háttérben futó tervezéshez. A ViewModel scope-ja
     * eltűnhet, ha a felhasználó elnavigál — a félig kész terv generálását viszont nem
     * szabad emiatt megszakítani.
     */
    val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A hosszan futó tervezés egyetlen gazdája — minden képernyő ezt figyeli. */
    val generation: GenerationCoordinator by lazy { GenerationCoordinator(this) }

    val entitlements: EntitlementRepository by lazy { EntitlementRepository(appContext) }

    /**
     * A bolt. Ha a Play nem érhető el (oldalról telepített build, Play nélküli eszköz),
     * a tartalék változat fut, és az app az ingyenes sávban teljes értékűen működik.
     */
    val billing: BillingGateway by lazy {
        runCatching {
            PlayBillingGateway(appContext) { subscribed, pending, expiresAt ->
                backgroundScope.launch {
                    entitlements.applyPurchaseState(subscribed, pending, expiresAt)
                }
            }.also { it.refresh() }
        }.getOrElse {
            NoBillingGateway("A Google Play fizetés ezen az eszközön nem érhető el.")
        }
    }

    val statsRepository: StatsRepository by lazy {
        StatsRepository(
            tracking = trackingRepository,
            planDao = database.planDao(),
            shoppingDao = database.shoppingDao(),
            achievementDao = database.achievementDao(),
        )
    }

    /**
     * A saját backend, ha a build tartalmazza a címét.
     *
     * Ez a bolti út: az Anthropic kulcs a szerveren marad, a kvótát és az előfizetést is
     * a szerver dönti el. Cím nélküli buildben null — ilyenkor az app a saját kulcsos
     * vagy az offline útra esik vissza, tehát fejlesztés közben is fut.
     */
    val backendClient: BackendClient? by lazy {
        BuildConfig.BACKEND_URL.takeIf { it.isNotBlank() }?.let { url ->
            BackendClient(
                baseUrl = url,
                installId = { secureKeyStore.installId() },
                // A vásárlás tokenjét a bolt adja; ebből igazolja a szerver a jogosultságot.
                purchaseToken = { billing.state.value.purchaseToken },
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
    }

    val reportRepository: ReportRepository by lazy { ReportRepository(backendClient) }

    private val anthropicAi: MealAi by lazy {
        AnthropicMealAi(secureKeyStore) { settings.currentSettings() }
    }

    private val backendAi: MealAi by lazy { BackendMealAi(requireNotNull(backendClient)) }

    private val offlineAi: MealAi by lazy { OfflineMealAi() }

    /**
     * Melyik tervező szolgálja ki a kérést.
     *
     * A saját API kulcs előrébb van a backendnél: aki szándékosan megadta a sajátját
     * (ez csak a rejtett fejlesztői részben lehetséges), az a saját számlájára és
     * kvóta nélkül dolgozzon. Mindenki más a backenden megy, kulcs nélkül.
     */
    fun mealAi(forceOffline: Boolean = false): MealAi = when {
        forceOffline -> offlineAi
        secureKeyStore.hasApiKey() -> anthropicAi
        backendClient != null -> backendAi
        else -> offlineAi
    }

    val hasApiKey: Boolean get() = secureKeyStore.hasApiKey()

    /** Igaz, ha az app valódi tervezővel dolgozik (nem a beépített sablonokkal). */
    val hasPlanner: Boolean get() = secureKeyStore.hasApiKey() || backendClient != null

    /**
     * Minden helyben tárolt adat törlése.
     *
     * Az app nem vezet fiókot, minden a készüléken van — de a felhasználónak akkor is
     * joga van egy gombbal mindent eltüntetni, és a Play is elvárja, hogy legyen rá mód.
     * Az előfizetést ez nem mondja le: az a Google fiókhoz tartozik.
     */
    suspend fun wipeAllData() {
        database.clearAllTables()
        settings.clearAll()
        entitlements.clearAll()
        secureKeyStore.setApiKey(null)
    }
}
