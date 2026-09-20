package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.PlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Mi történik a naplóval, ha a tervezett fogás megszűnik.
 *
 * A naplóbejegyzés nem a TERVRŐL szól, hanem arról, amit a felhasználó tényleg
 * megevett. Egy újratervezés vagy egy új terv viszont törli a fogásokat, és a
 * bejegyzés egy nem létező sorra mutatott tovább. Az ilyen bejegyzés SEHOL nem
 * látszott — a napi lista csak a fogáshoz nem tartozó sorokat mutatja terven kívüli
 * tételként —, a kalóriákat viszont tovább számolta. A nap összesítője így magasabb
 * volt, mint amit a lista indokolt, és ugyanaz az étel még egyszer bejelölhető volt
 * megevettként.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MealLogDetachTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(db.planDao(), db.mealDao(), db.shoppingDao(), db.mealLogDao(), db.favoriteDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertPlan(title: String, active: Boolean = true): Long =
        db.planDao().insert(
            PlanEntity(
                title = title, summary = "", startEpochDay = DAY, dayCount = 3,
                createdAtMillis = 0, requestText = "", targetKcal = 2000,
                targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
                isActive = active,
            )
        )

    private suspend fun insertMeal(planId: Long, dayIndex: Int, name: String): Long =
        db.mealDao().insert(
            MealEntity(
                planId = planId, dayIndex = dayIndex, epochDay = DAY + dayIndex,
                slot = "BREAKFAST", timeText = "08:00", scheduledAtMillis = 0, name = name,
            )
        )

    private suspend fun log(mealId: Long?, planId: Long?, status: LogStatus, name: String, kcal: Double) {
        db.mealLogDao().insert(
            MealLogEntity(
                mealId = mealId, planId = planId, epochDay = DAY, loggedAtMillis = 0,
                status = status.name, name = name,
                nutrients = NutrientsColumns(kcal = kcal),
            )
        )
    }

    /** Amit a napi képernyő terven kívüli tételként ki tud írni. */
    private suspend fun visibleExtras(): List<String> =
        db.mealLogDao().all().filter { it.mealId == null }.map { it.name }

    private suspend fun totalKcal(): Double = db.mealLogDao().all().sumOf { it.nutrients.kcal }

    @Test
    fun `a nap újratervezése után a megevett étel nem tűnik el a listáról`() = runTest {
        val planId = insertPlan("terv")
        val breakfast = insertMeal(planId, 0, "Rántotta")
        val lunch = insertMeal(planId, 0, "Csirkemell")
        log(breakfast, planId, LogStatus.EATEN, "Rántotta", 500.0)
        log(lunch, planId, LogStatus.SKIPPED, "Csirkemell", 0.0)
        log(null, null, LogStatus.EXTRA, "Csoki", 200.0)

        // Ez történik a nap újratervezésekor, mielőtt az új fogások bekerülnének.
        db.mealLogDao().detachDay(planId, 0)
        db.mealLogDao().deleteUneatenForDay(planId, 0)
        db.mealDao().deleteDay(planId, 0)

        assertEquals(
            "A megevett reggeli terven kívüli tételként marad látható",
            listOf("Rántotta", "Csoki"),
            visibleExtras().sortedDescending(),
        )
        assertEquals("A kalóriák nem vesznek el", 700.0, totalKcal(), 0.001)
        assertEquals(
            "Egy nem létező fogás kihagyása nem információ",
            emptyList<String>(),
            db.mealLogDao().all().filter { it.status == LogStatus.SKIPPED.name }.map { it.name },
        )
    }

    @Test
    fun `nem marad olyan bejegyzés, ami nem létező fogásra mutat`() = runTest {
        val planId = insertPlan("terv")
        val mealId = insertMeal(planId, 0, "Rántotta")
        log(mealId, planId, LogStatus.EATEN, "Rántotta", 500.0)

        db.mealLogDao().detachDay(planId, 0)
        db.mealLogDao().deleteUneatenForDay(planId, 0)
        db.mealDao().deleteDay(planId, 0)

        val mealIds = db.mealDao().allForPlan(planId).map { it.id }.toSet()
        val orphans = db.mealLogDao().all().filter { it.mealId != null && it.mealId !in mealIds }
        assertEquals("Árva bejegyzés nem maradhat", emptyList<MealLogEntity>(), orphans)
    }

    @Test
    fun `az újra bejelölés nem duplázza meg a napot`() = runTest {
        // A hiba lényege: a láthatatlan bejegyzés miatt a fogás „nem naplózottnak"
        // látszott, és a felhasználó újra rákoppintott.
        val planId = insertPlan("terv")
        val old = insertMeal(planId, 0, "Rántotta")
        log(old, planId, LogStatus.EATEN, "Rántotta", 500.0)

        db.mealLogDao().detachDay(planId, 0)
        db.mealLogDao().deleteUneatenForDay(planId, 0)
        db.mealDao().deleteDay(planId, 0)
        val fresh = insertMeal(planId, 0, "Zabkása")

        // Az új fogás naplózatlan — és ezt most már a lista is mutatja, mert a régi
        // bejegyzés terven kívüli tételként ott van mellette.
        assertNull(db.mealLogDao().all().firstOrNull { it.mealId == fresh })
        assertEquals(listOf("Rántotta"), visibleExtras())
    }

    @Test
    fun `új terv átvételekor a régi terv bejegyzései leválnak, az újé nem`() = runTest {
        val oldPlan = insertPlan("régi")
        val newPlan = insertPlan("új")
        val oldMeal = insertMeal(oldPlan, 0, "Rántotta")
        val newMeal = insertMeal(newPlan, 0, "Zabkása")
        log(oldMeal, oldPlan, LogStatus.EATEN, "Rántotta", 500.0)
        log(newMeal, newPlan, LogStatus.EATEN, "Zabkása", 400.0)

        db.mealLogDao().detachOtherPlans(newPlan)
        db.mealLogDao().deleteUneatenForOtherPlans(newPlan)

        assertEquals(listOf("Rántotta"), visibleExtras())
        assertEquals(
            "Az új terv bejegyzése a fogásához kötve marad",
            newMeal as Long?,
            db.mealLogDao().all().first { it.name == "Zabkása" }.mealId,
        )
        assertEquals(900.0, totalKcal(), 0.001)
    }

    @Test
    fun `egy terv törlése a többi terv naplóját nem bántja`() = runTest {
        val keep = insertPlan("marad")
        val doomed = insertPlan("törlendő", active = false)
        val keptMeal = insertMeal(keep, 0, "Zabkása")
        val doomedMeal = insertMeal(doomed, 0, "Rántotta")
        log(keptMeal, keep, LogStatus.EATEN, "Zabkása", 400.0)
        log(doomedMeal, doomed, LogStatus.EATEN, "Rántotta", 500.0)

        plans.deletePlan(doomed)

        assertEquals(listOf("Rántotta"), visibleExtras())
        assertEquals(keptMeal as Long?, db.mealLogDao().all().first { it.name == "Zabkása" }.mealId)
        assertEquals(900.0, totalKcal(), 0.001)
    }

    private companion object {
        const val DAY = 20_000L
    }
}
