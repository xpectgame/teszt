package hu.mealpilot.app

import hu.mealpilot.app.notify.MealAlarmScheduler
import hu.mealpilot.app.notify.MealAlarmScheduler.PlannedMeal
import hu.mealpilot.app.notify.ScheduledAlarms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Az ébresztéssor összehangolása.
 *
 * A korábbi kód csak KITETT ébresztőket, visszavonni soha nem tudott: az
 * AlarmManagerben nincs „töröld mindet", és nem is mondja meg, mit tettünk ki. Ennek
 * két látható következménye volt, és mindkettő ugyanúgy néz ki kívülről: az app
 * olyankor is értesít, amikor nem kellene.
 */
class AlarmPlanTest {

    private val now = 1_700_000_000_000L
    private fun minutes(count: Long) = TimeUnit.MINUTES.toMillis(count)
    private fun hours(count: Long) = TimeUnit.HOURS.toMillis(count)

    private fun entry(mealId: Long, triggerAt: Long, snoozed: Boolean = false) =
        ScheduledAlarms.Entry(mealId, triggerAt, snoozed)

    @Test
    fun `a kikapcsolás a már kitett ébresztőket is visszavonja`() {
        // Üres terv = nincs mit kitenni. Ez a kikapcsolás esete: aki átbillenti a
        // kapcsolót, most akar csendet, nem 36 óra múlva.
        val plan = MealAlarmScheduler.alarmPlan(
            meals = emptyList(),
            leadMinutes = 15,
            held = listOf(entry(1, now + hours(2)), entry(2, now + hours(20))),
            nowMillis = now,
        )

        assertEquals(listOf(1L, 2L), plan.cancel)
        assertEquals(emptyList<ScheduledAlarms.Entry>(), plan.keep)
        assertEquals(emptyList<ScheduledAlarms.Entry>(), plan.place)
    }

    @Test
    fun `a másik napra csúsztatott étkezés régi ébresztője megszűnik`() {
        // A napcsere (vagy egy nap újratervezése) kiviszi az étkezést a 36 órás
        // ablakból. A korábbi kód ilyenkor nem tett ki újat — de a régit sem vonta
        // vissza, tehát az emlékeztető a régi időpontjában szólalt meg.
        val plan = MealAlarmScheduler.alarmPlan(
            meals = listOf(PlannedMeal(mealId = 7, scheduledAtMillis = now + hours(5))),
            leadMinutes = 0,
            held = listOf(entry(7, now + hours(5)), entry(9, now + hours(3))),
            nowMillis = now,
        )

        assertEquals("A kivitt étkezés ébresztője megy", listOf(9L), plan.cancel)
        assertEquals(listOf(7L), plan.place.map { it.mealId })
    }

    @Test
    fun `a halasztott ébresztő túléli az összehangolást`() {
        // A „később" gomb után az étkezés SAJÁT időpontja már elmúlt, tehát a tervből
        // nem lehet visszaszámolni. Ha ezt is visszavonnánk, a halasztás azt
        // jelentené, hogy soha többé nem szól — csendben.
        val snoozed = entry(4, now + minutes(12), snoozed = true)
        val plan = MealAlarmScheduler.alarmPlan(
            meals = emptyList(),
            leadMinutes = 15,
            held = listOf(snoozed),
            nowMillis = now,
        )

        assertEquals(emptyList<Long>(), plan.cancel)
        assertEquals(listOf(snoozed), plan.keep)
    }

    @Test
    fun `a lejárt halasztás nem marad örökre a nyilvántartásban`() {
        val plan = MealAlarmScheduler.alarmPlan(
            meals = emptyList(),
            leadMinutes = 15,
            held = listOf(entry(4, now - minutes(1), snoozed = true)),
            nowMillis = now,
        )

        assertEquals(emptyList<Long>(), plan.cancel)
        assertEquals(emptyList<ScheduledAlarms.Entry>(), plan.keep)
    }

    @Test
    fun `a lejárt bejegyzésre nincs mit visszavonni`() {
        // Az elsült ébresztő már nem létezik; csak a nyilvántartásból kell kikerülnie.
        val plan = MealAlarmScheduler.alarmPlan(
            meals = emptyList(),
            leadMinutes = 0,
            held = listOf(entry(3, now - hours(1))),
            nowMillis = now,
        )

        assertEquals(emptyList<Long>(), plan.cancel)
        assertEquals(emptyList<ScheduledAlarms.Entry>(), plan.keep)
    }

    @Test
    fun `az előrehozás a kitett időpontban látszik`() {
        val plan = MealAlarmScheduler.alarmPlan(
            meals = listOf(PlannedMeal(mealId = 1, scheduledAtMillis = now + hours(2))),
            leadMinutes = 30,
            held = emptyList(),
            nowMillis = now,
        )

        assertEquals(now + hours(2) - minutes(30), plan.place.single().triggerAtMillis)
        // A nyilvántartásba ugyanaz kerül, amit kitettünk: ebből lesz a későbbi
        // visszavonás.
        assertEquals(plan.place, plan.keep)
    }

    @Test
    fun `a múltba eső időpontra nem teszünk ki ébresztőt`() {
        val plan = MealAlarmScheduler.alarmPlan(
            meals = listOf(
                PlannedMeal(mealId = 1, scheduledAtMillis = now + minutes(10)),
                PlannedMeal(mealId = 2, scheduledAtMillis = now + hours(4)),
            ),
            leadMinutes = 30,
            held = emptyList(),
            nowMillis = now,
        )

        assertEquals("A 10 perc múlva esedékes már elmúlt 30 perces előrehozással", listOf(2L), plan.place.map { it.mealId })
    }

    @Test
    fun `a nyilvántartás sora oda-vissza olvasható`() {
        for (entry in listOf(entry(1, now), entry(2, now + 5, snoozed = true), entry(0, 0))) {
            assertEquals(entry, ScheduledAlarms.decode(ScheduledAlarms.encode(entry)))
        }
    }

    @Test
    fun `a sérült sor nem dönti el az egészet`() {
        // A nyilvántartás a telefonon él, és túlél egy frissítést is. Egy régi vagy
        // sérült sor miatt nem maradhat el az összehangolás.
        for (raw in listOf("", "1", "1|2", "a|b|c", "1|2|3|4")) {
            assertTrue("nem értelmezhető sor: $raw", ScheduledAlarms.decode(raw) == null)
        }
    }
}
