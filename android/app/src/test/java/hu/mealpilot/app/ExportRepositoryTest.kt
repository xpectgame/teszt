package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.local.IngredientEntity
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.local.WeightLogEntity
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.app.data.repo.ExportRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Add ki az adataimat."
 *
 * Két dolog számít: hogy MINDEN benne legyen, amit a felhasználóról tárolunk, és hogy
 * SEMMI olyan ne legyen benne, aminek egy megosztható fájlban nincs helye. A második a
 * veszélyesebb: a kivitel egy megosztási szándékkal indul, tehát egy félreküldött
 * e-maillel bárhová eljuthat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExportRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var export: ExportRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        export = ExportRepository(
            settings = SettingsRepository(context),
            planDao = db.planDao(),
            mealDao = db.mealDao(),
            mealLogDao = db.mealLogDao(),
            weightLogDao = db.weightLogDao(),
            favoriteDao = db.favoriteDao(),
            achievementDao = db.achievementDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        val planId = db.planDao().insert(
            PlanEntity(
                title = "Áprilisi terv", summary = "Egy hét", startEpochDay = DAY, dayCount = 2,
                createdAtMillis = 0, requestText = "olcsó alapanyagok", targetKcal = 2100,
                targetProteinG = 150, targetCarbsG = 210, targetFatG = 65, targetFiberG = 30,
            )
        )
        val mealId = db.mealDao().insert(
            MealEntity(
                planId = planId, dayIndex = 0, epochDay = DAY, slot = "LUNCH",
                timeText = "12:30", scheduledAtMillis = 0, name = "Lecsó",
                recipeStepsJson = """["Pirítsd meg a hagymát."]""",
                nutrients = NutrientsColumns(kcal = 600.0, proteinG = 25.0),
            )
        )
        db.mealDao().insertIngredients(
            listOf(
                IngredientEntity(
                    mealId = mealId, name = "paprika", quantity = 300.0, unit = "g",
                    aisle = "ZOLDSEG_GYUMOLCS",
                )
            )
        )
        db.weightLogDao().upsert(WeightLogEntity(epochDay = DAY, weightKg = 88.4))
        db.mealLogDao().insert(
            MealLogEntity(
                mealId = mealId, planId = planId, epochDay = DAY, loggedAtMillis = 1L,
                status = LogStatus.EATEN.name, name = "Lecsó",
                nutrients = NutrientsColumns(kcal = 600.0),
            )
        )
    }

    private suspend fun exported(): JsonObject =
        Json.parseToJsonElement(export.buildJson()).jsonObject

    @Test
    fun `everything the app stores is in the file`() = runTest {
        seed()
        val json = exported()

        assertEquals("mealpilot-export-1", json["format"]!!.jsonPrimitive.content)
        assertTrue("Kell profil", json["profile"] != null)
        assertEquals(1, json["weight_logs"]!!.jsonArray.size)
        assertEquals(1, json["meal_logs"]!!.jsonArray.size)
        assertEquals(1, json["plans"]!!.jsonArray.size)

        val plan = json["plans"]!!.jsonArray[0].jsonObject
        assertEquals("Áprilisi terv", plan["title"]!!.jsonPrimitive.content)
        val meal = plan["meals"]!!.jsonArray[0].jsonObject
        assertEquals("Lecsó", meal["name"]!!.jsonPrimitive.content)
        assertEquals("A recept is menjen vele", 1, meal["recipe_steps"]!!.jsonArray.size)
        assertEquals("A hozzávalók is", 1, meal["ingredients"]!!.jsonArray.size)
    }

    @Test
    fun `no key, token or password can reach a shareable file`() = runTest {
        seed()
        val text = export.buildJson().lowercase()
        for (forbidden in listOf("apikey", "api_key", "sk-ant", "token", "secret", "password", "jelszó")) {
            assertFalse(
                "Titoknak tűnő mező került a megosztható fájlba: $forbidden",
                text.contains(forbidden),
            )
        }
    }

    @Test
    fun `an empty app exports an empty but valid file`() = runTest {
        // Ez az az eset, amikor a legkönnyebb elszállni: nincs terv, nincs napló.
        val json = exported()
        assertEquals(0, json["plans"]!!.jsonArray.size)
        assertEquals(0, json["weight_logs"]!!.jsonArray.size)
        assertTrue(json["profile"] != null)
    }

    @Test
    fun `the file name carries the date so exports do not overwrite each other`() {
        val name = export.fileName(1_700_000_000_000L)
        assertTrue(name, name.startsWith("mealpilot-adatok-"))
        assertTrue(name, name.endsWith(".json"))
        assertTrue("Legyen benne a dátum: $name", Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(name))
    }

    private companion object {
        const val DAY = 20_000L
    }
}
