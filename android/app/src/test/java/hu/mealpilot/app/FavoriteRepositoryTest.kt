package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.IngredientEntity
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.FavoriteRepository
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kedvencek: mit jelent az, hogy egy fogás „kedvenc".
 *
 * A legfontosabb döntés, amit ez a teszt őriz: a kedvenc MÁSOLAT, nem hivatkozás. A
 * tervek törölhetők, és a fogásaik a tervvel EGYÜTT szűnnek meg (`ForeignKey.CASCADE`).
 * Ha a kedvenc a `meals` sorára mutatna, a következő új terv elvinné az összeset —
 * csendben, mert a felület egyszerűen üres listát mutatna. Ez pont az a hibafajta,
 * amit senki nem keres, mert a művelet, ami okozza (új terv készítése), teljesen
 * ártatlannak látszik.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FavoriteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var favorites: FavoriteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        favorites = FavoriteRepository(db.favoriteDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertPlan(): Long = db.planDao().insert(
        PlanEntity(
            title = "Terv", summary = "", startEpochDay = DAY, dayCount = 3,
            createdAtMillis = 0, requestText = "", targetKcal = 2000,
            targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
        )
    )

    private suspend fun insertMeal(
        planId: Long,
        name: String,
        dayIndex: Int = 0,
        ingredientNames: List<String> = listOf("csirkemell", "barna rizs"),
    ): MealWithIngredients {
        val meal = MealEntity(
            planId = planId, dayIndex = dayIndex, epochDay = DAY + dayIndex,
            slot = "LUNCH", timeText = "12:30", scheduledAtMillis = 0, name = name,
            description = "Egyszerű fogás.", prepMinutes = 25,
            recipeStepsJson = """["Süsd meg.","Tálald."]""",
            nutrients = NutrientsColumns(kcal = 610.0, proteinG = 52.0, carbsG = 62.0, fatG = 16.0),
        )
        val mealId = db.mealDao().insert(meal)
        val ingredients = ingredientNames.map {
            IngredientEntity(
                mealId = mealId, name = it, quantity = 100.0, unit = "g", aisle = "SZARAZARU",
            )
        }
        db.mealDao().insertIngredients(ingredients)
        return MealWithIngredients(meal.copy(id = mealId), ingredients)
    }

    @Test
    fun `a favourite keeps the whole recipe, not just the name`() = runTest {
        val meal = insertMeal(insertPlan(), "Grillcsirke barna rizzsel")
        assertTrue(favorites.toggle(meal, NOW, AppLanguage.HU))

        val saved = db.favoriteDao().idOf("grillcsirke barna rizzsel")
        assertNotNull("Be kellett kerülnie", saved)
        val entry = favorites.byId(saved!!)!!
        assertEquals("Grillcsirke barna rizzsel", entry.favorite.name)
        assertEquals("Egyszerű fogás.", entry.favorite.description)
        assertEquals(25, entry.favorite.prepMinutes)
        assertEquals(610.0, entry.favorite.nutrients.kcal, 0.01)
        assertTrue("A recept is átjön", entry.favorite.recipeStepsJson.contains("Süsd meg."))
        assertEquals(2, entry.ingredients.size)
        assertTrue(entry.ingredients.any { it.name == "csirkemell" })
    }

    @Test
    fun `deleting the plan does not delete the favourite`() = runTest {
        // EZ a lényeg. A `meals` sorai a tervvel együtt törlődnek; a kedvencnek túl
        // kell élnie, különben minden új terv kisöpörné az egészet.
        val planId = insertPlan()
        val meal = insertMeal(planId, "Shakshuka")
        favorites.toggle(meal, NOW, AppLanguage.HU)
        assertEquals(1, favorites.count())

        db.planDao().delete(planId)

        assertEquals("A terv törlése nem viheti el a kedvencet", 1, favorites.count())
        val entry = favorites.byId(db.favoriteDao().idOf("shakshuka")!!)!!
        assertEquals("Shakshuka", entry.favorite.name)
        assertTrue("A hozzávalók is maradjanak", entry.ingredients.isNotEmpty())
    }

    @Test
    fun `the same dish on another day is already a favourite`() = runTest {
        // A hét két napján ugyanaz az étel két külön `meals` sor. Ha az azonosító
        // döntene, a szív a másik napon üres lenne, és a lista tele lenne ismétléssel.
        val planId = insertPlan()
        val monday = insertMeal(planId, "Shakshuka", dayIndex = 0)
        val friday = insertMeal(planId, "shakshuka ", dayIndex = 4)

        assertTrue(favorites.toggle(monday, NOW, AppLanguage.HU))
        assertTrue("A pénteki ugyanaz az étel", favorites.isFavorite(friday.meal.name))
        assertEquals(1, favorites.count())

        // És a péntekin koppintva LEKERÜL, nem duplázódik.
        assertFalse(favorites.toggle(friday, NOW, AppLanguage.HU))
        assertEquals(0, favorites.count())
    }

    @Test
    fun `toggling twice leaves nothing behind`() = runTest {
        val meal = insertMeal(insertPlan(), "Chana masala")
        favorites.toggle(meal, NOW, AppLanguage.HU)
        favorites.toggle(meal, NOW, AppLanguage.HU)

        assertEquals(0, favorites.count())
        // A hozzávalók a kaszkádon keresztül mennek — ha nem mennek, árva sorok
        // gyűlnek, és a következő megjelölés duplán hozná őket.
        assertEquals("Nem maradhat árva hozzávaló", 0, db.favoriteDao().ingredientCount())
    }

    @Test
    fun `the planner gets the newest favourites first`() = runTest {
        val planId = insertPlan()
        favorites.toggle(insertMeal(planId, "Első", dayIndex = 0), NOW, AppLanguage.HU)
        favorites.toggle(insertMeal(planId, "Második", dayIndex = 1), NOW + 1000, AppLanguage.HU)
        favorites.toggle(insertMeal(planId, "Harmadik", dayIndex = 2), NOW + 2000, AppLanguage.HU)

        assertEquals(
            listOf("Harmadik", "Második", "Első"),
            favorites.namesForPlanning(AppLanguage.HU),
        )
        assertEquals(
            listOf("Harmadik", "Második"),
            favorites.namesForPlanning(AppLanguage.HU, limit = 2),
        )
    }

    @Test
    fun `an English plan does not get the Hungarian favourites' names`() = runTest {
        // A kedvenc neve SZÓ SZERINT bemegy a promptba („ezek közül tegyél be
        // néhányat"). Nyelvszűrés nélkül a magyar nevek egy angol kérésbe is
        // bekerültek, magyarul — és az angol tervben nincs semmi, ami a magyar
        // maradványt elkapná: a LanguageChecker szándékosan csak fordítva néz.
        val planId = insertPlan()
        favorites.toggle(insertMeal(planId, "Rakott krumpli", dayIndex = 0), NOW, AppLanguage.HU)
        favorites.toggle(insertMeal(planId, "Chicken salad", dayIndex = 1), NOW + 1000, AppLanguage.EN)

        assertEquals(listOf("Rakott krumpli"), favorites.namesForPlanning(AppLanguage.HU))
        assertEquals(listOf("Chicken salad"), favorites.namesForPlanning(AppLanguage.EN))
    }

    @Test
    fun `a favourite from before the migration still counts in both languages`() = runTest {
        // A migráció előtt megjelölt kedvencekről nem tudjuk, milyen nyelvűek.
        // Kitalálni rosszabb lenne, mint bevallani: ott marad a korábbi viselkedés.
        db.favoriteDao().insert(
            hu.mealpilot.app.data.local.FavoriteMealEntity(
                name = "Régi kedvenc", nameKey = "régi kedvenc", slot = "LUNCH",
                language = "", addedAtMillis = NOW,
            )
        )

        assertEquals(listOf("Régi kedvenc"), favorites.namesForPlanning(AppLanguage.HU))
        assertEquals(listOf("Régi kedvenc"), favorites.namesForPlanning(AppLanguage.EN))
    }

    private companion object {
        const val DAY = 20_000L
        const val NOW = 1_700_000_000_000L
    }
}
