package hu.mealpilot.app

import android.content.Context
import hu.mealpilot.app.data.ai.AnthropicMealAi
import hu.mealpilot.app.data.ai.OfflineMealAi
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.prefs.SecureKeyStore
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.StatsRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import hu.mealpilot.core.ai.MealAi

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

    val statsRepository: StatsRepository by lazy {
        StatsRepository(
            tracking = trackingRepository,
            planDao = database.planDao(),
            shoppingDao = database.shoppingDao(),
            achievementDao = database.achievementDao(),
        )
    }

    private val anthropicAi: MealAi by lazy {
        AnthropicMealAi(secureKeyStore) { settings.currentSettings() }
    }

    private val offlineAi: MealAi by lazy { OfflineMealAi() }

    /** Ha van API kulcs, az AI tervez; ha nincs, a beépített offline sablontervező ugrik be. */
    fun mealAi(forceOffline: Boolean = false): MealAi =
        if (forceOffline || !secureKeyStore.hasApiKey()) offlineAi else anthropicAi

    val hasApiKey: Boolean get() = secureKeyStore.hasApiKey()
}
