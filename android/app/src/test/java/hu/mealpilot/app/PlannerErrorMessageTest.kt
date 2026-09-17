package hu.mealpilot.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.ai.BackendMealAi
import hu.mealpilot.app.data.ai.MealAiException
import hu.mealpilot.app.data.remote.BackendClient
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Amit a felhasználó egy elbukott tervezésből lát.
 *
 * A `translate` az a pont, ahol a nyers kivételből képernyőre való szöveg lesz. Ide
 * csak keretrendszeri hibák jutnak — a saját, már megfogalmazott üzeneteink
 * `MealAiException`-ként előbb kilépnek —, és azok üzenete GÉPI szöveg. Amíg a
 * tartalék `error.message ?: …` volt, egy elromlott modellválaszból ez lett a
 * hibaüzenet a képernyőn:
 *
 *     Unexpected JSON token at offset 1247: Expected '}', but had ',' instead
 *
 * A valódi megvalósítást vizsgáljuk, nem egy tesztbeli másolatot: pont az a kérdés,
 * mit csinál az, ami éles buildben fut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "hu-rHU")
class PlannerErrorMessageTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun ai(): BackendMealAi {
        val strings = AppStrings(context) { AppLanguage.HU }
        return BackendMealAi(
            backend = BackendClient(
                baseUrl = "https://nincs-halozat.example",
                installId = { "teszt-telepites-azonosito" },
                purchaseToken = { null },
                appVersion = "0.1.0 (1)",
                strings = strings,
            ),
            strings = strings,
            language = { AppLanguage.HU },
        )
    }

    @Test
    fun `az elromlott válasz gépi hibaszövege nem jut a felhasználóhoz`() {
        val technical = SerializationException(
            "Unexpected JSON token at offset 1247: Expected '}', but had ',' instead at path: \$.days[2].meals[1]"
        )

        val shown = ai().translate(technical)

        assertTrue("MealAiException kell", shown is MealAiException)
        val message = shown.message.orEmpty()
        assertEquals(
            "A terv készítése közben valami félrement. Próbáld újra — ha többször is előfordul, jelentsd be.",
            message,
        )
        assertFalse("gépi szöveg szivárgott ki: $message", message.contains("JSON"))
        assertFalse("gépi szöveg szivárgott ki: $message", message.contains("offset"))
        // Az eredeti hiba OKKÉNT megmarad: a naplóban és az összeomlás-jelentésben kell.
        assertSame(technical, shown.cause)
    }

    @Test
    fun `a hálózati hiba a saját szövegét kapja`() {
        val shown = ai().translate(IOException("Failed to connect to /10.0.0.1:443"))

        val message = shown.message.orEmpty()
        assertFalse("gépi szöveg szivárgott ki: $message", message.contains("10.0.0.1"))
        assertTrue("üres üzenet", message.isNotBlank())
    }

    @Test
    fun `a saját, megfogalmazott üzenetünk változatlanul megy tovább`() {
        // A `MealAiException` már a felhasználónak szól — ezt nem szabad felülírni.
        val mine = MealAiException("Ezt nem sikerült kiszámolnom.")

        assertSame(mine, ai().translate(mine))
    }
}
