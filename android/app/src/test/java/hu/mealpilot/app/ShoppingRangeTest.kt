package hu.mealpilot.app

import hu.mealpilot.app.ui.screens.ShoppingRange
import hu.mealpilot.app.ui.screens.rangeBounds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A hét nézet a terv hete, nem a mától számított 7 nap.
 *
 * A gördülő ablak minden nap MÁSIK listát jelentett: a pipák csak ugyanazon a
 * tartományon belül öröklődnek, tehát a hétfőn kipipált lista kedden üresen állt.
 */
class ShoppingRangeTest {

    private val start = 20_000L
    private val end = start + 29

    @Test
    fun `the week list stays the same for every day of that week`() {
        val monday = rangeBounds(start, end, ShoppingRange.WEEK, today = start)
        for (day in 1..6) {
            assertEquals("A(z) ${day + 1}. napon is ugyanaz a lista", monday, rangeBounds(start, end, ShoppingRange.WEEK, today = start + day))
        }
        assertEquals(start to start + 6, monday)
    }

    @Test
    fun `the eighth day opens the plan's second week`() {
        assertEquals(start + 7 to start + 13, rangeBounds(start, end, ShoppingRange.WEEK, today = start + 7))
    }

    @Test
    fun `the last week is cut at the end of the plan`() {
        assertEquals(start + 28 to end, rangeBounds(start, end, ShoppingRange.WEEK, today = start + 29))
    }

    @Test
    fun `before the plan starts it shows the first week, after it the last`() {
        assertEquals(start to start + 6, rangeBounds(start, end, ShoppingRange.WEEK, today = start - 1))
        assertEquals(start + 28 to end, rangeBounds(start, end, ShoppingRange.WEEK, today = end + 10))
    }

    @Test
    fun `the whole plan is the whole plan`() {
        assertEquals(start to end, rangeBounds(start, end, ShoppingRange.ALL, today = start + 12))
    }
}
