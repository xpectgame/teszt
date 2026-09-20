package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.IngredientEntity
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import hu.mealpilot.core.ai.RecipeBank
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Ezt ne kérem, adj mást" — mi történik az adatbázisban.
 *
 * A választás szabályát a `:core` `MealSwapTest` méri. Itt az a kérdés, hogy a csere
 * nem tesz-e kárt a körülötte lévő dolgokban: a fogás SORÁNAK meg kell maradnia (az
 * emlékeztető és a napló arra mutat), a nap kalóriakerete nem csúszhat el, a régi
 * hozzávalók nem maradhatnak ott, és a már megevett fogást nem szabad átírni.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MealSwapRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var tracking: TrackingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(
            db.planDao(), db.mealDao(), db.shoppingDao(), db.mealLogDao(), db.favoriteDao(),
        )
        tracking = TrackingRepository(db.mealDao(), db.mealLogDao(), db.weightLogDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(vararg names: String): Pair<Long, List<Long>> {
        val planId = db.planDao().insert(
            PlanEntity(
                title = "Terv", summary = "", startEpochDay = DAY, dayCount = 1,
                createdAtMillis = 0, requestText = "", targetKcal = 2000,
                targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
            )
        )
        val slots = listOf("BREAKFAST", "LUNCH", "DINNER")
        val ids = names.mapIndexed { index, name ->
            val mealId = db.mealDao().insert(
                MealEntity(
                    planId = planId, dayIndex = 0, epochDay = DAY,
                    slot = slots[index % slots.size], timeText = "12:30",
                    scheduledAtMillis = 111L, name = name, prepMinutes = 30,
                    nutrients = NutrientsColumns(kcal = 600.0, proteinG = 40.0, carbsG = 60.0, fatG = 18.0),
                    swapHint = "Próbáld meg bulgurral.",
                )
            )
            db.mealDao().insertIngredients(
                listOf(
                    IngredientEntity(
                        mealId = mealId, name = "régi hozzávaló", quantity = 1.0,
                        unit = "db", aisle = "EGYEB",
                    )
                )
            )
            mealId
        }
        return planId to ids
    }

    @Test
    fun `the swap keeps the row, the schedule and the calorie budget`() = runTest {
        val (_, ids) = seed("Valami egészen más fogás")
        val mealId = ids[0]
        val before = db.mealDao().byId(mealId)!!

        val result = plans.swapMeal(mealId, emptySet(), AppLanguage.HU)
        assertTrue("A cserének sikerülnie kell: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val after = db.mealDao().byId(mealId)!!
        assertEquals("A sor azonosítója nem változhat", before.id, after.id)
        assertEquals("Az emlékeztető ideje nem változhat", before.scheduledAtMillis, after.scheduledAtMillis)
        assertEquals("A slot nem változhat", before.slot, after.slot)
        assertNotEquals("A fogásnak változnia kell", before.name, after.name)
        assertEquals("A nap kerete nem csúszhat el", before.nutrients.kcal, after.nutrients.kcal, 1.0)
        assertEquals("A régi cseretipp a régi fogásról szólt", "", after.swapHint)
    }

    @Test
    fun `the old ingredients are gone, the new ones are there`() = runTest {
        val (planId, ids) = seed("Valami egészen más fogás")
        plans.swapMeal(ids[0], emptySet(), AppLanguage.HU).getOrThrow()

        val ingredients = db.mealDao().mealsInRange(planId, DAY, DAY)
            .first { it.meal.id == ids[0] }.ingredients

        assertTrue("Nem maradhat ott a régi hozzávaló", ingredients.none { it.name == "régi hozzávaló" })
        assertTrue("Kell új hozzávaló", ingredients.isNotEmpty())
    }

    @Test
    fun `the swap does not duplicate another dish of the same day`() = runTest {
        // A reggeli és az ebéd más bankból jön, ezért a nap ütközése csak az ebéd és a
        // vacsora között lehetséges — pont ez a két fogás osztozik a főételbankon.
        val mains = RecipeBank.forSlot(hu.mealpilot.core.ai.MealSlot.LUNCH)
            .map { it.name.get(AppLanguage.HU) }
        // Az ebéd SZÁNDÉKOSAN az a fogás, ami a vacsora után következne a bankban:
        // enélkül a teszt akkor is átmenne, ha a nap átnézése ki lenne hagyva.
        val (planId, ids) = seed("Reggeli", mains[5], mains[4])
        val dinnerId = ids[2]

        plans.swapMeal(dinnerId, emptySet(), AppLanguage.HU).getOrThrow()

        val day = db.mealDao().mealsInRange(planId, DAY, DAY).map { it.meal.name }
        assertEquals("Nem lehet két azonos fogás egy napon", day.size, day.toSet().size)
        assertNotEquals(
            "A soron következő fogás épp az ebéd volt — azt át kellett lépni",
            mains[5],
            db.mealDao().byId(dinnerId)!!.name,
        )
    }

    @Test
    fun `the swap honours the exclusions that are in force now`() = runTest {
        // A kizárás a terv ELKÉSZÜLTE UTÁN is felvehető. A csere a MOSTANI profilt nézi.
        val (planId, ids) = seed("Valami egészen más fogás")
        val vegan = DietRestriction.impliedBy(DietStyle.VEGAN) + DietRestriction.GLUTEN

        plans.swapMeal(ids[0], vegan, AppLanguage.HU).getOrThrow()

        val meal = db.mealDao().mealsInRange(planId, DAY, DAY).first { it.meal.id == ids[0] }
        assertTrue(
            "Kizárt hozzávaló került a cserébe: ${meal.meal.name} / ${meal.ingredients.map { it.name }}",
            RestrictionChecker.isSafe(
                meal.meal.name, meal.ingredients.map { it.name }, vegan, AppLanguage.HU,
            ),
        )
    }

    @Test
    fun `a meal you already ate is not rewritten behind your back`() = runTest {
        val (_, ids) = seed("Valami egészen más fogás")
        tracking.logPlannedMeal(ids[0], LogStatus.EATEN)
        val before = db.mealDao().byId(ids[0])!!.name

        val result = plans.swapMeal(ids[0], emptySet(), AppLanguage.HU)

        assertTrue("A naplózott fogást nem szabad kicserélni", result.isFailure)
        assertEquals("És nem is szabad hozzányúlni", before, db.mealDao().byId(ids[0])!!.name)
        assertTrue(
            "Mondja meg, mit tegyen: ${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message.orEmpty().contains("vond vissza"),
        )
    }

    @Test
    fun `a missing meal is an error, not a crash`() = runTest {
        val result = plans.swapMeal(9999L, emptySet(), AppLanguage.HU)
        assertTrue(result.isFailure)
    }

    private companion object {
        const val DAY = 20_000L
    }
}
