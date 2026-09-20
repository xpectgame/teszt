package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.AppDatabase
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.app.data.repo.ChatRepository
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.TrackingRepository
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.coroutines.test.runTest
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
 * A beszélgetés tárolása.
 *
 * Ez a modul eddig teszt nélkül volt, és pont itt született a hiba, ami miatt a
 * beszélgetés körözni kezdett: a lefuttatott művelet eredménye sehol nem került be az
 * előzménybe, tehát a modell a következő körben azt látta, hogy nem történt semmi.
 */
/**
 * A tesztek üres `Application`-nel futnak, nem a `MealPilotApp`-pal.
 *
 * A valódi induló osztály WorkManager-munkákat ütemez, értesítési csatornákat hoz létre
 * és telemetriát indít — a Robolectric alatt ezek közül az első rögtön el is száll
 * („WorkManager is not initialized properly"). Ezeknek a teszteknek nincs is szükségük
 * rájuk: a saját függőségeiket maguk építik fel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(
            strings = AppStrings(context) { AppLanguage.HU },
            chatDao = db.chatDao(),
            planRepository = PlanRepository(db.planDao(), db.mealDao(), db.shoppingDao(), db.mealLogDao(), db.favoriteDao()),
            tracking = TrackingRepository(db.mealDao(), db.mealLogDao(), db.weightLogDao()),
            settings = SettingsRepository(context),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a lefuttatott művelet eredménye bekerül a beszélgetésbe`() = runTest {
        // EZ a mai hiba regressziós tesztje. Az eredmény korábban csak egy elszálló
        // snackbarba ment: a felhasználó látta egy pillanatig, az előzmény viszont
        // semmit nem őrzött meg róla.
        repository.recordOutcome("Átírtam 1 napot.")

        val messages = db.chatDao().all()
        assertEquals(1, messages.size)
        assertEquals("Átírtam 1 napot.", messages.single().body)
        assertEquals(ChatTurn.Role.ASSISTANT.name, messages.single().role)
    }

    @Test
    fun `az eredmény a modellnek küldött előzményben is ott van`() = runTest {
        // Nem elég ELTÁROLNI: a következő kérésnél a modellnek is látnia kell, különben
        // ugyanúgy azt hiszi, hogy nem futott le semmi.
        val ai = FakeMealAi()
        repository.send(ai, "írd át a vacsorát", AppLanguage.HU)
        repository.recordOutcome("Átírtam 1 napot.")
        repository.send(ai, "és a holnapit?", AppLanguage.HU)

        assertTrue(
            "Az eredménynek szerepelnie kell a modellnek átadott előzményben.",
            ai.lastHistory.any { it.text == "Átírtam 1 napot." },
        )
    }

    @Test
    fun `az üres eredmény nem ír be üres buborékot`() = runTest {
        repository.recordOutcome("   ")
        assertEquals(0, db.chatDao().all().size)
    }

    @Test
    fun `a felismert művelet jóváhagyásra vár`() = runTest {
        repository.send(FakeMealAi.withAction(), "írd át a mai vacsorát", AppLanguage.HU)

        val assistant = db.chatDao().all().last()
        assertTrue("A művelet nem hajtódhat végre magától.", assistant.pendingAction)
        assertEquals("Átírom a mai vacsorát.", assistant.actionLabel)
    }

    @Test
    fun `az új kérés elévülteti a korábbi ajánlatot`() = runTest {
        // Különben egy régi, félreértett ajánlat gombja ott maradna a beszélgetésben,
        // és napokkal később még mindig át tudná írni a tervet.
        repository.send(FakeMealAi.withAction(), "írd át a mai vacsorát", AppLanguage.HU)
        repository.send(FakeMealAi(), "mégsem, inkább mást kérdezek", AppLanguage.HU)

        assertTrue(
            "Új kérés után egyetlen korábbi ajánlat sem maradhat érvényben.",
            db.chatDao().all().none { it.pendingAction },
        )
    }

    @Test
    fun `a válasz nélküli kör is hagy nyomot`() = runTest {
        // Hálózati hiba esetén se maradjon a beszélgetés némán: a felhasználó
        // különben nem tudja, elment-e egyáltalán az üzenete.
        val failing = FakeMealAi(Result.failure(IllegalStateException("nincs hálózat")))
        val result = repository.send(failing, "szia", AppLanguage.HU)

        assertTrue(result.isFailure)
        val messages = db.chatDao().all()
        assertEquals(2, messages.size)
        assertEquals("nincs hálózat", messages.last().body)
        assertFalse(messages.last().pendingAction)
    }
}
