package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Mégsem", aztán rögtön újra.
 *
 * A megszakítás a felületen azonnali, a munka viszont csak a következő felfüggesztési
 * pontján áll meg — egy hálózati olvasásban ez másodpercekbe is telhet. Ha közben a
 * felhasználó újraindította a tervezést, a régi munka késve lefutó lezárása az ÚJ munka
 * állapotát nullázta és az előtérben futó szolgáltatását állította le.
 *
 * Valódi idővel fut (`runBlocking`), nem virtuálissal: a koordinátor a saját háttér-
 * hatókörében dolgozik, amit a tesztóra nem lát.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GenerationRestartTest {

    @Test
    fun `a megszakított munka késői lezárása nem állítja le az újat`() = runBlocking {
        val container = AppContainer(ApplicationProvider.getApplicationContext<Context>())
        val generation = container.generation
        val release = CompletableDeferred<Unit>()
        val oldBlockDone = CompletableDeferred<Unit>()

        generation.runAction("régi") {
            try {
                // A hálózati olvasás, ami a megszakításra nem áll meg azonnal.
                withContext(NonCancellable) { release.await() }
                "régi kész"
            } finally {
                oldBlockDone.complete(Unit)
            }
        }
        withTimeout(5_000) { generation.status.first { it.running && it.headline == "régi" } }

        generation.cancel()
        assertFalse("A Mégsem a felületen azonnali", generation.status.value.running)

        generation.runAction("új") { awaitCancellation() }
        withTimeout(5_000) { generation.status.first { it.running && it.headline == "új" } }
        generation.consumeActionResult()

        release.complete(Unit)
        withTimeout(5_000) { oldBlockDone.await() }
        // A koordinátor lezárása közvetlenül a blokk után fut; ennyi idő bőven elég.
        delay(300)

        val status = generation.status.value
        assertTrue("Az új tervezésnek tovább kell futnia, látható állapottal", status.running)
        assertEquals("új", status.headline)
        assertEquals(
            "A régi munka eredménye nem írhat rá az újéra",
            null,
            generation.actionResult.value,
        )
        assertTrue(generation.isBusy)

        generation.cancel()
    }
}
