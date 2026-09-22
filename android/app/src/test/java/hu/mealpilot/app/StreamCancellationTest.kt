package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.remote.BackendClient
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A „Mégsem" tényleg megállítja-e a tervet.
 *
 * A gomb eddig csak a felületet állította meg. Az olvasó ciklus blokkoló hívás volt
 * egy nem felfüggeszthető metódusban: a korutin megszakítása nem szakította félbe,
 * tehát a válasz a végéig befolyt. A szerver ebből nem vette észre, hogy a kliens
 * elment — pedig a kapcsolat bontása az EGYETLEN pont, ahol egy megszakított terv
 * költsége tényleg megáll. A felhasználó lemondta, a számla megjött.
 *
 * Itt a ciklus viselkedését mérjük, hálózat nélkül: a folyam egy memóriabeli puffer,
 * a kérdés csak az, hogy a ciklus megáll-e, amint senki nem várja a választ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StreamCancellationTest {

    private fun client(): BackendClient {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return BackendClient(
            baseUrl = "https://example.invalid",
            installId = { "teszt-telepites-azonosito-123456" },
            purchaseToken = { null },
            appVersion = "0.0.0 (1)",
            strings = AppStrings(context) { AppLanguage.HU },
        )
    }

    /** Néhány `delta` sor, a végén `done` — ugyanaz az alak, amit a szerver küld. */
    private fun stream(deltas: Int): Buffer = Buffer().apply {
        repeat(deltas) { writeUtf8("""{"type":"delta","text":"darab"}""" + "\n") }
        writeUtf8("""{"type":"done"}""" + "\n")
    }

    @Test
    fun `the whole stream is read when nobody cancels`() = runTest {
        val text = client().readStream(stream(4)) {}
        assertEquals("darabdarabdarabdarab", text)
    }

    @Test
    fun `the reading stops as soon as nobody is waiting for the answer`() = runTest {
        val job = Job()
        var chunksRead = 0

        val error = runCatching {
            withContext(job) {
                client().readStream(stream(50)) {
                    chunksRead++
                    // A felhasználó a második darab után nyomja meg a „Mégsem"-et.
                    if (chunksRead == 2) job.cancel()
                }
            }
        }.exceptionOrNull()

        assertTrue("A megszakításnak ki kell látszania: $error", error is CancellationException)
        assertTrue(
            "A ciklusnak azonnal meg kell állnia, nem az ötvenedik darab után: $chunksRead",
            chunksRead < 5,
        )
    }
}
