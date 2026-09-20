package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.IngredientEntity
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.PlanRepository
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
 * Egy tervhez TÖBB bevásárlólista tartozhat.
 *
 * A képernyő két nézetet kínál — „hét" és „egész terv" —, és mindkettő SAJÁT sorokat
 * tárol a `shopping_items` táblában, külön (tól, ig) tartománnyal. A tervet módosító
 * műveletek viszont egyetlen tartományt építettek újra (a teljes tervét), a másik
 * pedig némán a régi hozzávalókat mutatta tovább.
 *
 * Ez az a képernyő, amit a felhasználó a boltban néz. Ott nem derül ki, hogy a lista
 * egy tegnapi tervváltozatra vonatkozik — csak az, hogy rossz dolgokat vett.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShoppingListRebuildTest {

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

    private suspend fun insertPlan(): Long = db.planDao().insert(
        PlanEntity(
            title = "terv", summary = "", startEpochDay = DAY, dayCount = 7,
            createdAtMillis = 0, requestText = "", targetKcal = 2000,
            targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
        )
    )

    private suspend fun insertMeal(planId: Long, dayIndex: Int, name: String, ingredient: String) {
        val mealId = db.mealDao().insert(
            MealEntity(
                planId = planId, dayIndex = dayIndex, epochDay = DAY + dayIndex,
                slot = "LUNCH", timeText = "12:00", scheduledAtMillis = 0, name = name,
            )
        )
        db.mealDao().insertIngredients(
            listOf(
                IngredientEntity(
                    mealId = mealId, name = ingredient, quantity = 100.0,
                    unit = "g", aisle = "HUS_HAL",
                )
            )
        )
    }

    private suspend fun namesIn(planId: Long, from: Long, to: Long): List<String> =
        db.shoppingDao().itemsInRange(planId, from, to).map { it.name }.sorted()

    @Test
    fun `a nap átírása a HETI listát is frissíti`() = runTest {
        val planId = insertPlan()
        insertMeal(planId, 0, "Csirke", "csirkemell")
        insertMeal(planId, 1, "Lazac", "lazacfilé")

        // A felhasználó mindkét nézetet megnyitotta, tehát mindkét lista létezik.
        plans.rebuildShoppingList(planId, DAY, DAY + 6)          // egész terv
        plans.rebuildShoppingList(planId, DAY, DAY + 1)          // „hét" nézet
        assertTrue("Csirkemell" in namesIn(planId, DAY, DAY + 1))

        // A 0. nap átírása: a csirke helyett pulyka lesz.
        db.mealDao().deleteDay(planId, 0)
        insertMeal(planId, 0, "Pulyka", "pulykamell")
        plans.rebuildShoppingLists(planId)

        assertEquals(
            "a heti lista a régi hozzávalót mutatta tovább",
            listOf("Lazacfilé", "Pulykamell"),
            namesIn(planId, DAY, DAY + 1),
        )
        assertEquals(
            listOf("Lazacfilé", "Pulykamell"),
            namesIn(planId, DAY, DAY + 6),
        )
    }

    @Test
    fun `a napcsere is frissíti a mentett listákat`() = runTest {
        val planId = insertPlan()
        insertMeal(planId, 0, "Csirke", "csirkemell")
        insertMeal(planId, 5, "Lazac", "lazacfilé")

        plans.rebuildShoppingList(planId, DAY, DAY + 1)
        assertEquals(listOf("Csirkemell"), namesIn(planId, DAY, DAY + 1))

        assertTrue(plans.swapDays(planId, 0, 5))

        assertEquals(
            "a csere után a heti listán a másik nap hozzávalója kell álljon",
            listOf("Lazacfilé"),
            namesIn(planId, DAY, DAY + 1),
        )
    }

    @Test
    fun `az újraépítés megtartja a kipipált tételeket`() = runTest {
        val planId = insertPlan()
        insertMeal(planId, 0, "Csirke", "csirkemell")
        plans.rebuildShoppingList(planId, DAY, DAY + 6)

        val item = db.shoppingDao().itemsInRange(planId, DAY, DAY + 6).single()
        db.shoppingDao().setChecked(item.id, true)

        plans.rebuildShoppingLists(planId)

        assertEquals(listOf("Csirkemell"), db.shoppingDao().checkedNames(planId, DAY, DAY + 6))
    }

    private companion object {
        const val DAY = 20_000L
    }
}
