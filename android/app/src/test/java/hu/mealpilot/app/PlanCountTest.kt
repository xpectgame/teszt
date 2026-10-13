package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.ai.OfflineMealAi
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.StatsRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * A „Készíts 5 étrendet" achievement — és amit a tervek táblája nem tud.
 *
 * Minden új terv törli a korábbiakat, tehát a táblában legfeljebb egy kész terv van.
 * Az achievement eddig a sorok számából számolt, vagyis soha nem oldódhatott fel,
 * akárhány tervet készített is valaki.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanCountTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var stats: StatsRepository
    private lateinit var context: Context
    private var created = 0
    private var archivedLists = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(
            db.planDao(), db.mealDao(), db.shoppingDao(), db.mealLogDao(), db.favoriteDao(),
            onPlanCreated = { created++ },
            onShoppingListsArchived = { archivedLists += it },
        )
        stats = StatsRepository(
            TrackingRepository(db.mealDao(), db.mealLogDao(), db.weightLogDao()),
            db.planDao(), db.shoppingDao(), db.achievementDao(),
            plansCreated = { created },
            archivedShoppingLists = { archivedLists },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun generate(ai: MealAi) {
        val profile = UserProfile()
        plans.generateAndSave(
            ai = ai,
            profile = profile,
            budget = EnergyCalculator.budget(profile, AppLanguage.HU),
            startDate = LocalDate.now(),
            days = 1,
            freeText = "",
            language = AppLanguage.HU,
        ).getOrThrow()
    }

    @Test
    fun `five plans unlock the five-plan achievement even though only one is kept`() = runTest {
        val offline = OfflineMealAi({ AppLanguage.HU }, AppStrings(context) { AppLanguage.HU })
        repeat(5) { generate(offline) }

        assertEquals("A tábla csak a legutóbbit tartja meg", 1, db.planDao().count())
        val unlocked = stats.refreshAndCollectNew(targetKcal = 2000, targetProteinG = 120).map { it.key }
        assertTrue("Öt terv után a Tervezőmesternek fel kell oldódnia: $unlocked", "planner_5" in unlocked)
    }

    @Test
    fun `shopping lists ticked off under earlier plans still count`() = runTest {
        // A lista a tervhez tartozik, és vele együtt törlődik. Heti tervekkel tehát a
        // táblában mindig csak EGY lista volt — a „3 lista kipipálva" sosem jött össze.
        val offline = OfflineMealAi({ AppLanguage.HU }, AppStrings(context) { AppLanguage.HU })
        repeat(3) {
            generate(offline)
            assertTrue("A tervhez bevásárlólista is tartozik", db.shoppingDao().rangesFor(db.planDao().activePlan()!!.id).isNotEmpty())
            db.openHelper.writableDatabase.execSQL("UPDATE shopping_items SET checked = 1")
        }

        assertEquals("A táblában csak az utolsó terv listája maradt", 1, db.shoppingDao().completedListCount())
        val unlocked = stats.refreshAndCollectNew(targetKcal = 2000, targetProteinG = 120).map { it.key }
        assertTrue("Három kipipált lista után a Bevásárlónak fel kell oldódnia: $unlocked", "shopping_3" in unlocked)
    }

    @Test
    fun `deleting a plan keeps its finished shopping lists in the count`() = runTest {
        val offline = OfflineMealAi({ AppLanguage.HU }, AppStrings(context) { AppLanguage.HU })
        generate(offline)
        db.openHelper.writableDatabase.execSQL("UPDATE shopping_items SET checked = 1")
        val planId = db.planDao().activePlan()!!.id

        plans.deletePlan(planId)

        assertEquals(1, archivedLists)
    }

    @Test
    fun `a generation cancelled before its first day leaves no placeholder plan behind`() = runTest {
        try {
            generate(CancelledBeforeFirstDay)
            fail("A megszakításnak tovább kell jutnia a hívóhoz")
        } catch (expected: CancellationException) {
            // Ez a várt út.
        }

        assertEquals("Az üres „Étrend készül…” sor nem maradhat ott", 0, db.planDao().count())
        assertEquals("Megszakított tervezés nem számít elkészült tervnek", 0, created)
    }

    /** A felhasználó a „Mégsem"-re bök, mielőtt egyetlen nap is elkészült volna. */
    private object CancelledBeforeFirstDay : MealAi {
        override val isConfigured = true
        override val canEstimate = false

        override suspend fun generatePlan(
            request: PlanRequest,
            onProgress: (GenerationProgress) -> Unit,
            onChunk: suspend (AiPlanResponse) -> Unit,
        ): Result<AiPlanResponse> = throw CancellationException("Mégsem")

        override suspend fun refineDay(
            request: PlanRequest,
            currentDayJson: String,
            instruction: String,
        ): Result<AiDayResponse> = error("nem ez a teszt tárgya")

        override suspend fun chat(
            context: ChatContext,
            history: List<ChatTurn>,
            message: String,
        ): Result<AiChatResponse> = error("nem ez a teszt tárgya")

        override suspend fun estimate(description: String): Result<AiMealEstimate> =
            error("nem ez a teszt tárgya")
    }
}
