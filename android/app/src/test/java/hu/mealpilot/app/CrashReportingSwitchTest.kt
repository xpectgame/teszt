package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.telemetry.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Kikapcsolva az alkalmazás nem is gyűjti ezeket, nem csak a küldést hagyja el."
 *
 * Ez a mondat szó szerint benne van az adatkezelési tájékoztató 3a. pontjában, a
 * támogatási oldalon, és az angol változatokban is. A napi számlálóknál állt is: a
 * `Telemetry.record` megnézi a kapcsolót. Az összeomlás-jelentés viszont a
 * kapcsolótól FÜGGETLENÜL fájlba írt — a takarítást egyedül a napi háttérmunka
 * végezte, az pedig backend nélküli buildben le sem futott.
 *
 * Ez a teszt azt a mondatot őrzi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CrashReportingSwitchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val thread = Thread("teszt-szál")

    @Before
    fun setUp() {
        CrashReporter.clearAll(context)
    }

    @Test
    fun `with the switch on a crash is collected`() {
        // A kiindulás: alapértéken gyűjtünk, különben a kikapcsolás nem bizonyítana semmit.
        CrashReporter.setEnabled(context, true)

        CrashReporter.write(context, thread, IllegalStateException("valami"))

        assertEquals(1, CrashReporter.pending(context).size)
    }

    @Test
    fun `with the switch off nothing is written at all`() {
        CrashReporter.setEnabled(context, false)

        CrashReporter.write(context, thread, IllegalStateException("valami"))

        assertTrue(
            "Kikapcsolva nem gyűjtünk — a tájékoztató ezt szó szerint ígéri",
            CrashReporter.pending(context).isEmpty(),
        )
    }

    @Test
    fun `switching off also drops what was already collected`() {
        CrashReporter.setEnabled(context, true)
        CrashReporter.write(context, thread, IllegalStateException("valami"))
        assertEquals(1, CrashReporter.pending(context).size)

        // Aki most kapcsolta ki, az most akar csendet — nem a következő háttérmunka után.
        CrashReporter.setEnabled(context, false)

        assertTrue(CrashReporter.pending(context).isEmpty())
    }

    @Test
    fun `switching back on collects again`() {
        CrashReporter.setEnabled(context, false)
        CrashReporter.setEnabled(context, true)

        CrashReporter.write(context, thread, IllegalStateException("valami"))

        assertEquals(1, CrashReporter.pending(context).size)
    }

    @Test
    fun `the default is on - the same as the setting's default`() {
        // Friss telepítés: a kapcsoló alapértéke `telemetryEnabled = true`. Ha a tükör
        // alapértéke ettől eltérne, az első indítás összeomlásairól nem tudnánk meg
        // semmit — vagy fordítva, egy kikapcsolt felhasználóról gyűjtenénk.
        assertTrue(CrashReporter.isEnabled(context))
    }
}
