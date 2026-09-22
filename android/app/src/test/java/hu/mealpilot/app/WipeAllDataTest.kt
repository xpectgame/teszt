package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.PlanEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Minden adat törlése" — tényleg minden.
 *
 * A gomb mögött nem egy tábla áll, hanem nyolc különböző tároló, és mindegyik külön
 * sorban törlődik. Egy kimaradt sor szemmel nézve semmiben nem látszik: a képernyők
 * üresek lesznek, a hiányzó tároló viszont ott marad.
 *
 * Így maradt bent a TELEPÍTÉSI AZONOSÍTÓ — az egyetlen dolog, ami ezt a készüléket a
 * szerverhez köti. A helyi kvótaszámlálók nullázódtak, tehát az app három ingyenes
 * tervet mutatott, a szerver viszont a régi azonosítót látta, és minden kérést
 * elutasított.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WipeAllDataTest {

    private lateinit var container: AppContainer

    // Az adatbázist NEM zárjuk le a teszt végén: az `AppDatabase.get` folyamatszintű
    // példányt ad, és egy lezárt példány a következő tesztnek is az maradna. Erre nincs
    // is szükség — minden teszt a törléssel VÉGZŐDIK, tehát a következő üres állapotot
    // talál.
    @Before
    fun setUp() {
        container = AppContainer(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `the wipe takes the install id with it`() = runTest {
        val before = container.secureKeyStore.installId()
        assertTrue("Kell legyen azonosító a törlés előtt", before.isNotBlank())

        container.wipeAllData()

        assertNotEquals(
            "A telepítési azonosítónak el kell tűnnie — enélkül a szerver továbbra is " +
                "ugyanazt a felhasználót látja, elhasznált próbakerettel",
            before,
            container.secureKeyStore.installId(),
        )
    }

    @Test
    fun `the wipe takes the API key with it`() = runTest {
        container.secureKeyStore.setApiKey("sk-ant-teszt-kulcs-1234567890")
        assertTrue(container.secureKeyStore.hasApiKey())

        container.wipeAllData()

        assertNull(container.secureKeyStore.apiKey())
    }

    @Test
    fun `the wipe empties the database`() = runTest {
        container.database.planDao().insert(
            PlanEntity(
                title = "Terv", summary = "", startEpochDay = 20_000L, dayCount = 1,
                createdAtMillis = 0, requestText = "", targetKcal = 2000,
                targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
            )
        )
        assertEquals(1, container.database.planDao().count())

        container.wipeAllData()

        assertEquals(0, container.database.planDao().count())
    }

    @Test
    fun `the wipe resets the local quota counters`() = runTest {
        container.entitlements.recordPlanGenerated()
        assertTrue(container.entitlements.current().usage.aiPlans > 0)

        container.wipeAllData()

        assertEquals(0, container.entitlements.current().usage.aiPlans)
    }

    @Test
    fun `the wipe forgets the chosen language`() = runTest {
        container.languageStore.set(hu.mealpilot.core.i18n.AppLanguage.EN)
        assertTrue(container.languageStore.isChosen)

        container.wipeAllData()

        assertTrue("A nyelvválasztás is adat", !container.languageStore.isChosen)
    }

    @Test
    fun `the wipe clears the saved profile`() = runTest {
        val profile = container.settings.currentProfile()
        container.settings.saveProfile(profile.copy(name = "Teszt Elek", weightKg = 91.5))
        assertEquals("Teszt Elek", container.settings.currentProfile().name)

        container.wipeAllData()

        assertEquals("", container.settings.profile.first().name)
    }
}
