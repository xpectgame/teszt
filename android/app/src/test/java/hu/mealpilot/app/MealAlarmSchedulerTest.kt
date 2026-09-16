package hu.mealpilot.app

import hu.mealpilot.app.notify.MealAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A napi összefoglaló időzítése óraátállításkor.
 *
 * A korábbi kód 24 órát adott hozzá, ha a mai időpont már elmúlt. Az óraátállítás
 * napján viszont a nap 23 vagy 25 órás, tehát a „holnap ugyanekkor" nem 86 400 000
 * ezredmásodperc. Magyarországon ez évente kétszer fordul elő.
 */
class MealAlarmSchedulerTest {

    private val budapest = ZoneId.of("Europe/Budapest")

    private fun localHourOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(budapest).hour

    private fun at(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun `the summary keeps its hour when the clocks go back`() {
        // 2026-10-25-én hajnalban áll vissza az óra. Az előző este 21 órakor a mai
        // 20 óra már elmúlt, tehát a holnapit kell időzíteni.
        val now = at("2026-10-24T21:00:00+02:00[Europe/Budapest]")
        val next = MealAlarmScheduler.nextDailySummaryAt(20, now, budapest)

        assertEquals("Az összefoglaló 20 órakor szóljon, ne 19-kor", 20, localHourOf(next))
        assertEquals(at("2026-10-25T20:00:00+01:00[Europe/Budapest]"), next)
    }

    @Test
    fun `the summary keeps its hour when the clocks go forward`() {
        // 2026-03-29-én hajnalban ugrik előre az óra.
        val now = at("2026-03-28T21:00:00+01:00[Europe/Budapest]")
        val next = MealAlarmScheduler.nextDailySummaryAt(20, now, budapest)

        assertEquals("Az összefoglaló 20 órakor szóljon, ne 21-kor", 20, localHourOf(next))
        assertEquals(at("2026-03-29T20:00:00+02:00[Europe/Budapest]"), next)
    }

    @Test
    fun `an hour still ahead today is not pushed to tomorrow`() {
        val now = at("2026-06-10T09:00:00+02:00[Europe/Budapest]")
        val next = MealAlarmScheduler.nextDailySummaryAt(20, now, budapest)
        assertEquals(at("2026-06-10T20:00:00+02:00[Europe/Budapest]"), next)
    }

    @Test
    fun `an hour that does not exist is moved forward, not skipped`() {
        // Tavasszal a 2 és 3 óra közötti óra kimarad. Aki hajnali 2-re kérte az
        // összefoglalót, akkor is kapjon egyet — a legközelebbi létező pillanatban.
        val now = at("2026-03-28T23:00:00+01:00[Europe/Budapest]")
        val next = MealAlarmScheduler.nextDailySummaryAt(2, now, budapest)

        assertEquals("A kimaradt óra helyett a következő létező", 3, localHourOf(next))
        assertEquals(at("2026-03-29T03:00:00+02:00[Europe/Budapest]"), next)
    }

    @Test
    fun `the hour is clamped instead of throwing`() {
        val now = at("2026-06-10T09:00:00+02:00[Europe/Budapest]")
        assertEquals(at("2026-06-10T23:00:00+02:00[Europe/Budapest]"),
            MealAlarmScheduler.nextDailySummaryAt(99, now, budapest))
        assertEquals(at("2026-06-11T00:00:00+02:00[Europe/Budapest]"),
            MealAlarmScheduler.nextDailySummaryAt(-5, now, budapest))
    }
}
