package hu.mealpilot.app

import hu.mealpilot.app.ui.screens.nextSelectedDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * A „Ma" képernyő napválasztása.
 *
 * A dátum korábban a ViewModel születésekor rögzült, és a képernyő nem életciklus-
 * tudatosan gyűjt — aki nyitva hagyta az appot éjfélkor, másnap is a tegnapot látta,
 * és a terven kívül felvitt étkezés is oda került.
 *
 * A null itt azt jelenti, hogy „ma", és az éjfélkor magától továbblép. A lapozás
 * ezért nem egyszerű összeadás: attól függ, visszaértünk-e a mai napra.
 */
class TodayDaySelectionTest {

    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun `stepping away from today selects an absolute day`() {
        assertEquals(today.minusDays(1), nextSelectedDay(null, -1, today))
        assertEquals(today.plusDays(1), nextSelectedDay(null, 1, today))
    }

    @Test
    fun `stepping back onto today hands control back to the clock`() {
        // Null = „ma", és onnantól éjfélkor megint magától lép. Ha itt a mai dátumot
        // adnánk vissza, a nap újra beragadna.
        assertNull(nextSelectedDay(today.minusDays(1), 1, today))
        assertNull(nextSelectedDay(today.plusDays(1), -1, today))
    }

    @Test
    fun `a day far from today keeps stepping from where the user is`() {
        val threeBack = today.minusDays(3)
        assertEquals(today.minusDays(4), nextSelectedDay(threeBack, -1, today))
        assertEquals(today.minusDays(2), nextSelectedDay(threeBack, 1, today))
    }

    @Test
    fun `a bigger jump also lands on today correctly`() {
        assertNull(nextSelectedDay(today.minusDays(7), 7, today))
        assertEquals(today.plusDays(2), nextSelectedDay(today.minusDays(5), 7, today))
    }

    @Test
    fun `month and year boundaries are calendar dates, not arithmetic`() {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 1), nextSelectedDay(newYearsEve, 1, today))
        val marchFirst = LocalDate.of(2028, 3, 1)
        // 2028 szökőév: február 29. létezik.
        assertEquals(LocalDate.of(2028, 2, 29), nextSelectedDay(marchFirst, -1, today))
    }
}
