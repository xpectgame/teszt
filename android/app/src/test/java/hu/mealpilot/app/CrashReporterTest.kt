package hu.mealpilot.app

import hu.mealpilot.app.data.telemetry.CrashReporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Az adatkezelési tájékoztató azt ígéri, hogy a hiba HELYE megy el, és hogy étrend,
 * napló és testadat nem hagyja el a készüléket. Ez a teszt azt a mondatot őrzi.
 *
 * A korábbi megvalósítás `printStackTrace`-t használt, aminek a kimenete a kivétel és
 * minden okozója `toString()`-jével kezdődik — az pedig szabad szöveg. Ma nincs olyan
 * út, ahol felhasználói adat kerülne bele, de egy jogi ígéret nem támaszkodhat arra,
 * hogy éppen egyik sem teszi.
 */
class CrashReporterTest {

    private val thread = Thread("teszt-szál")

    @Test
    fun `the rendered stack never carries the exception message`() {
        val secret = "78,5 kg és a mai vacsorám"
        val error = IllegalStateException(secret)

        val rendered = CrashReporter.renderStack(thread, error)

        assertFalse("A kivétel üzenete nem kerülhet a jelentésbe", rendered.contains(secret))
        assertFalse("Még részletében sem", rendered.contains("78,5"))
        assertTrue("A kivétel OSZTÁLYA viszont kell", rendered.contains("IllegalStateException"))
        assertTrue("És az is, hol történt", rendered.contains("hu.mealpilot"))
    }

    @Test
    fun `a cause chain is listed by class, never by message`() {
        val root = IllegalArgumentException("For input string: \"78,5\"")
        val middle = RuntimeException("a felhasználó étrendje: rántott csirke", root)
        val top = IllegalStateException("legfelső üzenet", middle)

        val rendered = CrashReporter.renderStack(thread, top)

        for (leak in listOf("78,5", "rántott csirke", "legfelső üzenet", "For input string")) {
            assertFalse("Kiszivárgott: $leak", rendered.contains(leak))
        }
        assertTrue(rendered.contains("IllegalArgumentException"))
        assertTrue(rendered.contains("RuntimeException"))
        assertTrue(rendered.contains("IllegalStateException"))
    }

    @Test
    fun `the report stays useful for finding the bug`() {
        val error = IllegalStateException("akármi")
        val rendered = CrashReporter.renderStack(thread, error)

        assertTrue("A szál neve segít a diagnózisban", rendered.contains("teszt-szál"))
        // Osztály.függvény:sor alakú sorok — ez a „hiba helye".
        assertTrue(
            "Legyen benne legalább egy helymegjelölés",
            rendered.lineSequence().any { Regex("""\S+\.\S+:\d+""").containsMatchIn(it) },
        )
    }

    @Test
    fun `a deep cause chain cannot blow up the report`() {
        // Mély okozó-lánc. Korlát nélkül a jelentés a tárhelyet és a feltöltést is
        // megfeküdné — és egy körkörös láncnál a bejárás meg sem állna.
        var error: Throwable = IllegalStateException("alap")
        repeat(50) { error = RuntimeException("réteg", error) }

        val rendered = CrashReporter.renderStack(thread, error)
        assertTrue("A lánc korlátozva van", rendered.lineSequence().count { it.startsWith("caused by:") } <= 5)
    }
}
