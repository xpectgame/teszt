package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * Ez a teszt a VALÓDI konténeren fut, nem egy külön összerakott másolaton — és rögtön
 * talált is egy összeomlást: a `clearAllTables` nem felfüggeszthető hívás, a Room
 * pedig a fő szálon kivételt dob rá. A gomb hívója a `viewModelScope`, ami a fő
 * szálon folytatódik, tehát a „Minden adat törlése" nem törölt, hanem elszállt.
 *
 * Így maradt bent a TELEPÍTÉSI AZONOSÍTÓ — az egyetlen dolog, ami ezt a készüléket a
 * szerverhez köti. A helyi kvótaszámlálók nullázódtak, tehát az app három ingyenes
 * tervet mutatott, a szerver viszont a régi azonosítót látta, és minden kérést
 * elutasított.
 *
 * EGYETLEN teszt, sok állítással, szándékosan: a konténer folyamatszintű tárolókat
 * nyit (Room, DataStore), és azok metódusonként újranyitva egymásba érnének. Egy
 * törlés, utána minden tárolót megnézünk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WipeAllDataTest {

    @Test
    fun `the wipe leaves nothing behind in any store`() = runTest {
        val container = AppContainer(ApplicationProvider.getApplicationContext<Context>())

        val installId = container.secureKeyStore.installId()
        container.secureKeyStore.setApiKey("sk-ant-teszt-kulcs-1234567890")
        container.database.planDao().insert(
            PlanEntity(
                title = "Terv", summary = "", startEpochDay = 20_000L, dayCount = 1,
                createdAtMillis = 0, requestText = "", targetKcal = 2000,
                targetProteinG = 150, targetCarbsG = 200, targetFatG = 60, targetFiberG = 30,
            )
        )
        container.entitlements.recordPlanGenerated()
        container.languageStore.set(AppLanguage.EN)
        container.settings.saveProfile(container.settings.currentProfile().copy(name = "Teszt Elek"))

        assertTrue("A kiindulás nem lehet üres", installId.isNotBlank())
        assertEquals(1, container.database.planDao().count())
        assertTrue(container.entitlements.current().usage.aiPlans > 0)

        container.wipeAllData()

        assertNotEquals(
            "A telepítési azonosítónak el kell tűnnie — enélkül a szerver továbbra is " +
                "ugyanazt a felhasználót látja, elhasznált próbakerettel",
            installId,
            container.secureKeyStore.installId(),
        )
        assertNull("Az API kulcs", container.secureKeyStore.apiKey())
        assertEquals("Az adatbázis", 0, container.database.planDao().count())
        assertEquals("A helyi kvótaszámláló", 0, container.entitlements.current().usage.aiPlans)
        assertTrue("A nyelvválasztás is adat", !container.languageStore.isChosen)
        assertEquals("A profil", "", container.settings.profile.first().name)
    }
}
