package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.StatsRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * A feloldott achievementről meg is kell tudnia a felhasználónak.
 *
 * A feloldás öt helyről indulhat, és közülük négy eldobta a frissen feloldottak
 * listáját: az achievement némán oldódott fel, és mivel a következő híváskor már a
 * „feloldottak" között volt, a `newlyUnlocked` soha többé nem hozta elő. A `notified`
 * oszlop pontosan erre való, csak eddig SENKI nem olvasta.
 *
 * Amit itt mérünk: a ki nem hirdetett achievement kiderül az adatbázisból — attól
 * függetlenül, hogy a feloldó hívási hely törődött-e a visszakapott listával.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AchievementAnnouncementTest {

    private lateinit var db: AppDatabase
    private lateinit var stats: StatsRepository
    private lateinit var tracking: TrackingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tracking = TrackingRepository(db.mealDao(), db.mealLogDao(), db.weightLogDao())
        stats = StatsRepository(tracking, db.planDao(), db.shoppingDao(), db.achievementDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedLoggedMeal(): Long {
        val today = LocalDate.now().toEpochDay()
        val planId = db.planDao().insert(
            PlanEntity(
                title = "Terv", summary = "", startEpochDay = today, dayCount = 1,
                createdAtMillis = 0, requestText = "", targetKcal = 2000,
                targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
            )
        )
        val mealId = db.mealDao().insert(
            MealEntity(
                planId = planId, dayIndex = 0, epochDay = today,
                slot = "LUNCH", timeText = "12:30", scheduledAtMillis = 111L,
                name = "Lecsó", prepMinutes = 20,
                nutrients = NutrientsColumns(kcal = 2000.0, proteinG = 150.0, carbsG = 200.0, fatG = 60.0),
            )
        )
        tracking.logPlannedMeal(mealId, LogStatus.EATEN)
        return planId
    }

    @Test
    fun `an unlock nobody announced stays pending`() = runTest {
        seedLoggedMeal()

        // Pontosan az, ami az appon belül történik: a visszaadott lista a földre esik.
        stats.refreshAndCollectNew(2000, 150)

        val pending = stats.observeUnannounced().first()
        assertTrue("Kell lennie ki nem hirdetett achievementnek", pending.isNotEmpty())
        assertTrue(
            "Az első naplózás achievementjének köztük kell lennie: ${pending.map { it.key }}",
            pending.any { it.key == "first_log" },
        )
    }

    @Test
    fun `once announced it does not come back`() = runTest {
        seedLoggedMeal()
        stats.refreshAndCollectNew(2000, 150)

        val pending = stats.observeUnannounced().first()
        pending.forEach { stats.markNotified(it.key) }

        assertEquals(
            "A kihirdetett achievement nem jöhet elő újra",
            emptyList<String>(),
            stats.observeUnannounced().first().map { it.key },
        )
    }

    @Test
    fun `a second refresh does not resurrect an already announced one`() = runTest {
        seedLoggedMeal()
        stats.refreshAndCollectNew(2000, 150)
        stats.observeUnannounced().first().forEach { stats.markNotified(it.key) }

        // A `newlyUnlocked` a másodszorra már üres — pont ezért nem lehet RÁ bízni az
        // értesítést, és pont ezért nem szabad, hogy ez a kör újra kihirdesse.
        assertTrue(stats.refreshAndCollectNew(2000, 150).isEmpty())
        assertTrue(stats.observeUnannounced().first().isEmpty())
    }
}
