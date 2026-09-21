package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.ai.RecipeBank
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A kész terv a SAJÁT nyelvén marad.
 *
 * Ezt az app ki is mondja a nyelvválasztónál („a már elkészült terv nem fordítódik
 * le"), a szerkesztés viszont sokáig a MOSTANI felületnyelven írt bele. A tünetet a
 * felhasználó a boltban látta: a bevásárlólista a nevekre von össze, tehát ugyanaz a
 * hozzávaló két sorban állt — „Paradicsom 300 g" és „Tomato 150 g".
 *
 * Amit itt mérünk, az nem a fordítás minősége, hanem az, hogy melyik nyelv dönt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanLanguageTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(
            db.planDao(), db.mealDao(), db.shoppingDao(), db.mealLogDao(), db.favoriteDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    /** Egy egynapos terv egyetlen ebéddel, a megadott tervnyelvvel. */
    private suspend fun seed(planLanguage: String): Long {
        val planId = db.planDao().insert(
            PlanEntity(
                title = "Terv", summary = "", startEpochDay = DAY, dayCount = 1,
                createdAtMillis = 0, requestText = "", targetKcal = 2000,
                targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
                language = planLanguage,
            )
        )
        return db.mealDao().insert(
            MealEntity(
                planId = planId, dayIndex = 0, epochDay = DAY,
                slot = "LUNCH", timeText = "12:30", scheduledAtMillis = 111L,
                name = "Valami egészen más fogás", prepMinutes = 30,
                nutrients = NutrientsColumns(kcal = 600.0, proteinG = 40.0, carbsG = 60.0, fatG = 18.0),
            )
        )
    }

    private fun mainsIn(language: AppLanguage): List<String> =
        RecipeBank.forSlot(MealSlot.LUNCH).map { it.name.get(language) }

    @Test
    fun `a Hungarian plan stays Hungarian even if the app is switched to English`() = runTest {
        val mealId = seed(planLanguage = AppLanguage.HU.name)

        // A FELÜLET nyelve angol — a tervé nem.
        plans.swapMeal(mealId, emptySet(), AppLanguage.EN).getOrThrow()

        val name = db.mealDao().byId(mealId)!!.name
        assertTrue(
            "A magyar tervbe magyar fogásnak kell kerülnie, nem „$name”-nak",
            name in mainsIn(AppLanguage.HU),
        )
    }

    @Test
    fun `an English plan stays English even if the app is switched to Hungarian`() = runTest {
        val mealId = seed(planLanguage = AppLanguage.EN.name)

        plans.swapMeal(mealId, emptySet(), AppLanguage.HU).getOrThrow()

        val name = db.mealDao().byId(mealId)!!.name
        assertTrue(
            "Az angol tervbe angol fogásnak kell kerülnie, nem „$name”-nak",
            name in mainsIn(AppLanguage.EN),
        )
    }

    @Test
    fun `a plan from before the migration keeps the old behaviour`() = runTest {
        // Üres nyelv: a régi tervekről nem tudjuk, min készültek. Kitalálni rosszabb
        // lenne, mint bevallani — ott marad a felület nyelve, ahogy eddig is volt.
        val mealId = seed(planLanguage = "")

        plans.swapMeal(mealId, emptySet(), AppLanguage.EN).getOrThrow()

        val name = db.mealDao().byId(mealId)!!.name
        assertTrue(
            "Nyelv nélküli tervnél a felület nyelve dönt, itt mégis „$name” lett",
            name in mainsIn(AppLanguage.EN),
        )
    }

    @Test
    fun `an unknown language name is not trusted either`() = runTest {
        // Egy lefokozott vagy elrontott mező nem dönthet el semmit: a `valueOf` itt
        // kivételt dobott volna, és a csere a felhasználó szeme előtt hibázik el.
        val mealId = seed(planLanguage = "KLINGON")

        val result = plans.swapMeal(mealId, emptySet(), AppLanguage.EN)

        assertTrue("Az ismeretlen nyelv nem lehet összeomlás: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(
            "Ilyenkor a felület nyelve dönt",
            db.mealDao().byId(mealId)!!.name in mainsIn(AppLanguage.EN),
        )
    }

    @Test
    fun `the day rewrite asks the model in the plan's language`() = runTest {
        val mealId = seed(planLanguage = AppLanguage.HU.name)
        val planId = db.mealDao().byId(mealId)!!.planId
        val ai = LanguageSpy()

        val profile = UserProfile()
        plans.refineDay(
            ai = ai,
            profile = profile,
            budget = EnergyCalculator.budget(profile, AppLanguage.EN),
            planId = planId,
            dayIndex = 0,
            instruction = "legyen könnyebb",
            // A felület angol, a terv magyar.
            language = AppLanguage.EN,
        )

        assertEquals(
            "A modellnek a TERV nyelvén kell írnia a tervbe",
            AppLanguage.HU,
            ai.lastRequest?.language,
        )
    }

    /** Csak azt jegyzi fel, milyen kéréssel hívták; a választ nem is adja meg. */
    private class LanguageSpy : MealAi {
        var lastRequest: PlanRequest? = null
            private set

        override val isConfigured = true
        override val canEstimate = true

        override suspend fun generatePlan(
            request: PlanRequest,
            onProgress: (GenerationProgress) -> Unit,
            onChunk: suspend (AiPlanResponse) -> Unit,
        ): Result<AiPlanResponse> = Result.failure(UnsupportedOperationException("nem ez a teszt tárgya"))

        override suspend fun refineDay(
            request: PlanRequest,
            currentDayJson: String,
            instruction: String,
        ): Result<AiDayResponse> {
            lastRequest = request
            return Result.failure(UnsupportedOperationException("a válasz itt nem számít"))
        }

        override suspend fun chat(
            context: ChatContext,
            history: List<ChatTurn>,
            message: String,
        ): Result<AiChatResponse> = Result.failure(UnsupportedOperationException("nem ez a teszt tárgya"))

        override suspend fun estimate(description: String): Result<AiMealEstimate> =
            Result.failure(UnsupportedOperationException("nem ez a teszt tárgya"))
    }

    private companion object {
        const val DAY = 20_000L
    }
}
