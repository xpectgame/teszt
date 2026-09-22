package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.prefs.SecureKeyStore
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A kulcstároló ürítése — ezen áll a „Minden adat törlése".
 *
 * A törlés eddig csak az API kulcsot vette le. A TELEPÍTÉSI AZONOSÍTÓ — az egyetlen
 * dolog, ami ezt a készüléket a szerverhez köti — ott maradt, pedig az `installId`
 * leírása azt ígéri, hogy eltűnik és újat kap.
 *
 * Nem csak ígéret kérdése volt: a helyi kvótaszámlálók a törléssel nullázódnak, tehát
 * az app három ingyenes tervet mutatott, a szerver viszont a régi azonosítót látta
 * elhasznált kerettel, és minden kérést elutasított.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SecureKeyStoreClearTest {

    private fun store() = SecureKeyStore(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `clear takes both the API key and the install id`() {
        val keys = store()
        keys.setApiKey("sk-ant-teszt-kulcs-1234567890")
        val installId = keys.installId()
        assertTrue("Kell legyen azonosító a törlés előtt", installId.isNotBlank())

        keys.clear()

        assertNull("Az API kulcsnak el kell tűnnie", keys.apiKey())
        assertNotEquals(
            "A telepítési azonosítónak is el kell tűnnie — enélkül a szerver továbbra " +
                "is ugyanazt a felhasználót látja, elhasznált próbakerettel",
            installId,
            keys.installId(),
        )
    }

    @Test
    fun `the new install id is usable right away`() {
        // Nem elég, hogy más: kell is, hogy legyen. Üres azonosítóval a backend 401-et
        // adna, és az app a törlés után használhatatlan lenne.
        val keys = store()
        keys.installId()
        keys.clear()

        val fresh = keys.installId()
        assertTrue("Az új azonosító nem lehet üres", fresh.isNotBlank())
        assertTrue("És a formátumának is stimmelnie kell", Regex("^[A-Za-z0-9_-]{22,128}$").matches(fresh))
    }
}
