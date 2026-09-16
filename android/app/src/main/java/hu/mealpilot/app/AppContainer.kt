package hu.mealpilot.app

import android.content.Context
import android.util.Log
import hu.mealpilot.app.billing.BillingGateway
import hu.mealpilot.app.billing.EntitlementRepository
import hu.mealpilot.app.billing.NoBillingGateway
import hu.mealpilot.app.billing.PlayBillingGateway
import hu.mealpilot.app.data.ai.AnthropicMealAi
import hu.mealpilot.app.data.ai.BackendMealAi
import hu.mealpilot.app.data.ai.OfflineMealAi
import hu.mealpilot.app.data.ai.QuotaExceededException
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.prefs.SecureKeyStore
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.app.data.repo.ChatRepository
import hu.mealpilot.app.data.remote.BackendClient
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.ReportRepository
import hu.mealpilot.app.data.telemetry.CrashReporter
import hu.mealpilot.app.data.telemetry.Telemetry
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.notify.MealAlarmScheduler
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.app.i18n.LanguageStore
import hu.mealpilot.app.data.repo.StatsRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import hu.mealpilot.app.work.GenerationCoordinator
import hu.mealpilot.core.ai.FallbackMealAi
import hu.mealpilot.core.ai.MealAi
import kotlinx.coroutines.CoroutineExceptionHandler
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

    /**
     * Az app nyelve. Nem a beállítások közt lakik, mert az Activity indulásakor —
     * a felület felépítése előtt — is tudni kell, oda pedig nem fér be egy
     * felfüggesztett olvasás.
     */
    val languageStore: LanguageStore by lazy { LanguageStore(appContext) }

    /**
     * Szövegek a felületen kívül is a választott nyelven — értesítések, munkák,
     * hibaüzenetek. Az alkalmazáskontextus a rendszer nyelvét hordozza, nem a
     * felhasználóét, ezért kell ez a réteg.
     */
    val strings: AppStrings by lazy { AppStrings(appContext) { language } }

    val language: hu.mealpilot.core.i18n.AppLanguage get() = languageStore.current()

    val planRepository: PlanRepository by lazy {
        PlanRepository(database.planDao(), database.mealDao(), database.shoppingDao(), database.mealLogDao())
    }

    val trackingRepository: TrackingRepository by lazy {
        TrackingRepository(
            mealDao = database.mealDao(),
            mealLogDao = database.mealLogDao(),
            weightLogDao = database.weightLogDao(),
        )
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            strings = strings,
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
    /**
     * A háttérmunkák közös hatóköre.
     *
     * A kivételkezelő nem a hibakezelés HELYE — minden művelet maga felel a saját
     * hibáiért, és a felhasználónak is maga válaszol. Ez a háló: egy hiányzó
     * try-catch nélküle az egész appot kilövi (a SupervisorJob csak a testvér
     * coroutine-okat védi, a folyamatot nem), méghozzá jellemzően akkor, amikor a
     * felhasználó épp elindított valamit. A hiba naplózva marad, tehát nem tűnik el.
     */
    private val backgroundErrors = CoroutineExceptionHandler { _, error ->
        Log.e("AppContainer", "Kezeletlen hiba a háttérmunkában.", error)
    }

    val backgroundScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + backgroundErrors)

    /** A hosszan futó tervezés egyetlen gazdája — minden képernyő ezt figyeli. */
    val generation: GenerationCoordinator by lazy { GenerationCoordinator(this) }

    /**
     * A fejlesztő saját buildje: a kulcs fordításkor kerül bele (MEALPILOT_OWNER_KEY).
     * A boltból telepített appban üres, tehát ott ennek nincs hatása.
     */
    val isOwnerBuild: Boolean get() = BuildConfig.OWNER_KEY.isNotBlank()

    val entitlements: EntitlementRepository by lazy {
        EntitlementRepository(appContext, ownerBuild = isOwnerBuild)
    }

    /**
     * Névtelen használati számlálók és összeomlás-jelentés.
     *
     * A felhasználó kikapcsolhatja; kikapcsolva a gyűjtés is leáll, nem csak a küldés.
     */
    val telemetry: Telemetry by lazy {
        Telemetry(appContext, backgroundScope) { settings.currentSettings().telemetryEnabled }
    }

    /**
     * A bolt. Ha a Play nem érhető el (oldalról telepített build, Play nélküli eszköz),
     * a tartalék változat fut, és az app az ingyenes sávban teljes értékűen működik.
     */
    val billing: BillingGateway by lazy {
        runCatching {
            PlayBillingGateway(appContext, strings) { subscribed, pending, expiresAt ->
                backgroundScope.launch {
                    entitlements.applyPurchaseState(subscribed, pending, expiresAt)
                }
            }.also { it.refresh() }
        }.getOrElse {
            NoBillingGateway(strings[R.string.billing_no_play])
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
                ownerKey = BuildConfig.OWNER_KEY,
                strings = strings,
            )
        }
    }

    val reportRepository: ReportRepository by lazy { ReportRepository(backendClient) }

    private val anthropicAi: MealAi by lazy {
        AnthropicMealAi(secureKeyStore, strings, { language }) { settings.currentSettings() }
    }

    private val backendAi: MealAi by lazy { BackendMealAi(requireNotNull(backendClient), strings) { language } }

    private val offlineAi: MealAi by lazy { OfflineMealAi({ language }, strings) }

    /**
     * Melyik tervező szolgálja ki a kérést.
     *
     * A saját API kulcs előrébb van a backendnél: aki szándékosan megadta a sajátját
     * (ez csak a rejtett fejlesztői részben lehetséges), az a saját számlájára és
     * kvóta nélkül dolgozzon. Mindenki más a backenden megy, kulcs nélkül.
     *
     * Mindkettő mögé beáll a beépített tervező. Ha a szolgáltatás elérhetetlen vagy
     * elfogyott a keret, a felhasználó ne egy perc várakozás után kapjon piros hibát:
     * a sablonos terv a kalóriakeretet pontosan tartja, és azonnal kész.
     */
    fun mealAi(
        forceOffline: Boolean = false,
        /** Lefut, ha a beépített tervező vette át; a paraméter a szolgáltatástól kapott napok száma. */
        onFallback: (daysFromService: Int) -> Unit = {},
    ): MealAi = when {
        forceOffline -> offlineAi
        secureKeyStore.hasApiKey() -> withFallback(anthropicAi, onFallback)
        backendClient != null -> withFallback(backendAi, onFallback)
        else -> offlineAi
    }

    /**
     * A tervezőt a beépített tartalék mögé kötve adja vissza.
     *
     * A kvótás elutasítás szándékosan NEM esik vissza: ott az előfizetést kell
     * felajánlani, nem sablontervvel elfedni a korlátot. Minden más hiba — hálózat,
     * szerverhiba, elfogyott API keret, kétszer sem sikerült terv — visszaesik.
     */
    private fun withFallback(primary: MealAi, notify: (Int) -> Unit): MealAi {
        val english = language == hu.mealpilot.core.i18n.AppLanguage.EN
        return FallbackMealAi(
            primary = primary,
            fallback = offlineAi,
            isRecoverable = { error -> error !is QuotaExceededException },
            switchMessage = if (english) {
                "Switching to the built-in planner…"
            } else {
                "Átváltás a beépített tervezőre…"
            },
            fallbackNote = if (english) {
                "Some days came from the built-in recipe bank because the planning " +
                    "service was unavailable. Regenerate the plan later for a more personal one."
            } else {
                "A terv egy része a beépített receptbankból készült, mert a tervezőszolgáltatás " +
                    "nem volt elérhető. Később újragenerálva személyre szabottabb tervet kapsz."
            },
            fallbackSummary = if (english) {
                "The planning service could not be reached, so this plan was built from the " +
                    "app's own recipe bank. Your daily calories and macros are still on target, " +
                    "but your free-text request was not taken into account."
            } else {
                "A tervezőszolgáltatás nem volt elérhető, ezért ez a terv az app beépített " +
                    "receptbankjából készült. A napi kalória és a makrók így is a te célodhoz " +
                    "vannak méretezve, de a szabad szöveges kérésedet ez a változat nem vette " +
                    "figyelembe."
            },
            onFallback = { error, daysFromService ->
                android.util.Log.w(
                    "MealAi",
                    "A tervezőszolgáltatás elakadt, a beépített tervező veszi át.",
                    error,
                )
                telemetry.record(TelemetryEvent.PLANNER_FALLBACK)
                notify(daysFromService)
            },
        )
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
        // Az ébresztők előbb: a táblák kiürítése után az emlékeztető már nem találná
        // az étkezést, a napi összefoglaló viszont továbbra is szólna — egy olyan
        // embernek, aki épp most kérte az adatai törlését.
        MealAlarmScheduler.cancelAllMeals(appContext)
        MealAlarmScheduler.cancelDailySummary(appContext)
        database.clearAllTables()
        settings.clearAll()
        entitlements.clearAll()
        telemetry.clearAll()
        languageStore.clear()
        CrashReporter.clear(appContext)
        secureKeyStore.setApiKey(null)
    }
}
