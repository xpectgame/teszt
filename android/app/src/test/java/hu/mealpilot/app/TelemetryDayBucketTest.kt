package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.telemetry.Telemetry
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Egy adag egy NAPHOZ tartozik.
 *
 * Korábban egyetlen közös számlálókészlet volt, a nap pedig egy külön mezőben, amit
 * csak akkor állítottunk be, ha üres volt. Amíg egy feltöltés nem sikerült (nincs
 * hálózat, nem futott le a háttérmunka), a KÖVETKEZŐ napok eseményei is a régi nap
 * rovatába gyűltek: a szerver három nap eseményeit egy napra könyvelte, a másik
 * kettőről meg azt hitte, hogy senki nem használta az appot.
 *
 * A napi felhasználószám pont az a szám, ami miatt a `firstToday` mező egyáltalán
 * létezik — ezt a hibát nem szabad benne hagyni.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TelemetryDayBucketTest {

    private val today = LocalDate.now().toString()

    /**
     * A számlálók mindig a megadott NAPRA mennek: a `record` tűzz-és-felejts, a
     * sorrendje egy tesztben nem garantált — itt viszont pont a nap a kérdés.
     */
    private fun TestScope.telemetry() =
        Telemetry(ApplicationProvider.getApplicationContext<Context>(), this) { true }

    @Test
    fun `today's events land in today's bucket`() = runTest {
        val telemetry = telemetry()
        telemetry.clearAll()
        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)
        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)
        telemetry.recordOn(today, TelemetryEvent.MEAL_LOGGED)

        val snapshot = telemetry.snapshot()

        assertEquals(LocalDate.now().toString(), snapshot.day)
        assertEquals(2, snapshot.counts["app_open"])
        assertEquals(1, snapshot.counts["meal_logged"])
        assertTrue("Ez az első jelentés erre a napra", snapshot.firstToday)
    }

    @Test
    fun `an unsent day does not swallow the next day's events`() = runTest {
        // Ez a hiba alakja: az első nap adagja bent ragad (nincs hálózat), és a
        // második nap eseményei is oda gyűlnek.
        val telemetry = telemetry()
        telemetry.clearAll()
        telemetry.recordOn("2026-09-20", TelemetryEvent.APP_OPEN)
        telemetry.recordOn("2026-09-21", TelemetryEvent.APP_OPEN)
        telemetry.recordOn("2026-09-21", TelemetryEvent.MEAL_LOGGED)

        val first = telemetry.snapshot()
        assertEquals("A legrégebbi nap megy előbb", "2026-09-20", first.day)
        assertEquals(1, first.counts["app_open"])
        assertEquals("A másnap eseménye nem kerülhet ide", null, first.counts["meal_logged"])

        telemetry.clear(first)

        val second = telemetry.snapshot()
        assertEquals("2026-09-21", second.day)
        assertEquals(1, second.counts["app_open"])
        assertEquals(1, second.counts["meal_logged"])
        assertTrue("Külön nap, külön felhasználószám", second.firstToday)
    }

    @Test
    fun `a day reported twice counts as one user`() = runTest {
        val telemetry = telemetry()
        telemetry.clearAll()
        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)
        val first = telemetry.snapshot()
        telemetry.clear(first)

        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)
        val second = telemetry.snapshot()

        assertEquals(first.day, second.day)
        assertFalse("Ugyanaz a nap már szerepelt a felhasználószámban", second.firstToday)
    }

    @Test
    fun `an upload that was not sent keeps its counters`() = runTest {
        val telemetry = telemetry()
        telemetry.clearAll()
        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)
        val snapshot = telemetry.snapshot()

        // A feltöltés elbukott: NEM hívjuk a `clear`-t.
        assertEquals(1, telemetry.snapshot().counts["app_open"])
        assertEquals(snapshot.day, telemetry.snapshot().day)
    }

    @Test
    fun `what arrived during the upload is not lost`() = runTest {
        val telemetry = telemetry()
        telemetry.clearAll()
        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)
        val snapshot = telemetry.snapshot()
        // A feltöltés alatt jött még egy.
        telemetry.recordOn(today, TelemetryEvent.APP_OPEN)

        telemetry.clear(snapshot)

        assertEquals("Ami a pillanatkép után jött, az marad", 1, telemetry.snapshot().counts["app_open"])
    }
}
