package hu.mealpilot.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Egy VALÓDI, adatokkal teli régi adatbázis túléli-e a frissítést.
 *
 * MIÉRT NEM ELÉG A TÖBBI TESZT. Az összes eddigi Room-teszt `inMemoryDatabaseBuilder`-rel
 * indul: ott a Room a LEGÚJABB sémát egy lépésben hozza létre, a migráció le sem fut.
 * Vagyis a migrációkat eddig semmi nem próbálta ki — és a `check-room-migration.py` is
 * csak a szerkezetet veti össze, nem a Room valódi indulását.
 *
 * MIÉRT SZÁMÍT. A migráció a FELHASZNÁLÓ telefonján fut le először. A Room indításkor
 * összeveti a kapott sémát a várttal, és eltérés esetén `IllegalStateException`-nel áll
 * meg: az app el sem indul. Nem hibás működés — használhatatlan app, és pont azoknál,
 * akik frissítenek. Aki újonnan telepít, annak a Room egyben építi a sémát, ott minden
 * rendben van, tehát a hiba a fejlesztőnél SOHA nem jelentkezik.
 *
 * AMIT EZ MEGFOG, ÉS A SZERKEZETI ELLENŐRZÉS NEM: a `room_master_table` azonosító
 * hash-ének kezelését, a tényleges Room-indulást, és azt, hogy a régi sorok olvashatók
 * maradnak-e a migráció után.
 *
 * A régi sémát a VERZIÓKEZELT `3.json`-ból építi, nem bemásolt DDL-ből: a bemásolt séma
 * a következő verziónál csendben elavulna, és a teszt egy nem létező múltat ellenőrizne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DatabaseMigrationSmokeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.getDatabasePath(TEST_DB).delete()
    }

    @Test
    fun `a populated v3 database survives the upgrade and stays readable`() {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        file.delete()

        val schema = JSONObject(readSchema(3)).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            createStatements(schema).forEach(db::execSQL)
            // A Room ebben a sorban tárolja, MELYIK sémához tartozik a fájl. Enélkül
            // „Room cannot verify the data integrity" hibával áll meg, tehát a teszt
            // nem is jutna el a migrációig.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(schema.getString("identityHash")),
            )
            insertRealisticData(db)
            db.version = schema.getInt("version")
        }

        // Ugyanaz az építő, amit az éles app is használ — csak más fájlnévvel és a
        // folyamatszintű példány megkerülésével. A migrációlista NINCS bemásolva.
        val opened = AppDatabase.builder(context, TEST_DB).build().also { database = it }

        runBlocking {
            // 1. A régi adatok megmaradtak és olvashatók.
            val plan = opened.planDao().activePlan()
            assertNotNull("A frissítés nem vihette el a tervet", plan)
            assertEquals("Régi terv", plan!!.title)
            assertEquals(2100, plan.targetKcal)
            // A nyelv mezőt a 4 → 5 migráció adta hozzá. A RÉGI tervről nem tudjuk,
            // milyen nyelven készült, és kitalálni rosszabb lenne, mint bevallani:
            // üresen marad, és ott marad a korábbi viselkedés (a felület nyelve).
            assertEquals("A régi terv nyelve nem található ki", "", plan.language)

            val meals = opened.mealDao().mealsInRange(plan.id, DAY, DAY + 2)
            assertEquals(1, meals.size)
            assertEquals("Lecsó", meals[0].meal.name)
            assertEquals("A hozzávalók is megvannak", 1, meals[0].ingredients.size)

            val weights = opened.weightLogDao().all()
            assertEquals(1, weights.size)
            assertEquals(88.4, weights[0].weightKg, 0.01)

            // 2. Az ÚJ táblák is működnek — nem csak léteznek.
            assertEquals(0, opened.favoriteDao().count())
            opened.favoriteDao().insert(
                hu.mealpilot.app.data.local.FavoriteMealEntity(
                    name = "Lecsó", nameKey = "lecsó", slot = "LUNCH", addedAtMillis = 1L,
                )
            )
            assertEquals(1, opened.favoriteDao().count())
            assertNotNull(opened.favoriteDao().idOf("lecsó"))
            // A kedvenc nyelve az 5 → 6 migrációval jött. Az újonnan megjelölt kedvenc
            // üres nyelvvel is megtalálható MINDKÉT nyelvhez — ez a korábbi viselkedés,
            // és a régi soroknál ez a helyes.
            assertEquals(
                listOf("Lecsó"),
                opened.favoriteDao().recentNames(hu.mealpilot.core.i18n.AppLanguage.EN.name, 10),
            )
        }
    }

    @Test
    fun `a fresh install builds the newest schema without any migration`() {
        // A másik út: új telepítőnél a Room egyben építi a sémát. Ez eddig is működött,
        // de a két utat egyszerre kell épen tartani — ez a teszt mondja meg, ha a
        // migráció javítása közben az új telepítést törnénk el.
        val opened = AppDatabase.builder(context, TEST_DB).build().also { database = it }
        runBlocking {
            assertEquals(0, opened.planDao().count())
            assertEquals(0, opened.favoriteDao().count())
            assertTrue(opened.openHelper.readableDatabase.version >= 4)
        }
    }

    private fun insertRealisticData(db: SQLiteDatabase) {
        val planId = db.insertOrThrow("plans", null, ContentValues().apply {
            put("title", "Régi terv"); put("summary", ""); put("startEpochDay", DAY)
            put("dayCount", 3); put("createdAtMillis", 1L); put("requestText", "")
            put("targetKcal", 2100); put("targetProteinG", 150); put("targetCarbsG", 210)
            put("targetFatG", 65); put("targetFiberG", 30); put("coachNotesJson", "[]")
            put("isActive", 1)
        })
        val mealId = db.insertOrThrow("meals", null, ContentValues().apply {
            put("planId", planId); put("dayIndex", 0); put("epochDay", DAY)
            put("slot", "LUNCH"); put("timeText", "12:30"); put("scheduledAtMillis", 0L)
            put("name", "Lecsó"); put("description", ""); put("prepMinutes", 30)
            put("servings", 1.0); put("recipeStepsJson", "[]"); put("swapHint", "")
            listOf(
                "n_kcal" to 600.0, "n_proteinG" to 25.0, "n_carbsG" to 60.0,
                "n_fatG" to 20.0, "n_fiberG" to 8.0, "n_sugarG" to 10.0,
                "n_saturatedFatG" to 5.0, "n_sodiumMg" to 800.0,
            ).forEach { (column, value) -> put(column, value) }
        })
        db.insertOrThrow("ingredients", null, ContentValues().apply {
            put("mealId", mealId); put("name", "paprika"); put("quantity", 300.0)
            put("unit", "g"); put("aisle", "ZOLDSEG_GYUMOLCS"); put("note", "")
            put("pantryStaple", 0)
        })
        db.insertOrThrow("weight_logs", null, ContentValues().apply {
            put("epochDay", DAY); put("weightKg", 88.4); put("note", "")
        })
    }

    /** A séma `createSql` utasításai, a Room saját alakjában. */
    private fun createStatements(schema: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            out += entity.getString("createSql").replace("\${TABLE_NAME}", table)
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                out += indices.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", table)
            }
        }
        return out
    }

    private fun readSchema(version: Int): String =
        checkNotNull(
            javaClass.classLoader?.getResourceAsStream(
                "hu.mealpilot.app.data.local.AppDatabase/$version.json"
            )
        ) {
            "Nincs a teszt osztályútján a(z) $version.json séma. A build.gradle.kts " +
                "`sourceSets.test.resources` beállítása hiányzik vagy elromlott."
        }.bufferedReader().use { it.readText() }

    private companion object {
        const val TEST_DB = "migration-smoke.db"
        const val DAY = 20_000L
    }
}
