package hu.mealpilot.core

import hu.mealpilot.core.progress.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A trendsúly azt hivatott megmutatni, ami a napi zaj alatt van.
 *
 * A tesztek arra mennek, amit a felhasználó észrevenne: hogy egy vízvisszatartásos
 * reggel ne írja felül az egész haladást, hogy a kihagyott hetek ne torzítsanak, és
 * hogy kevés adatból ne mondjunk magabiztos heti ütemet.
 */
class WeightTrendTest {

    private fun daily(start: Double, perDay: Double, days: Int, noise: (Int) -> Double = { 0.0 }) =
        (0 until days).map { (1000L + it) to (start + perDay * it + noise(it)) }

    @Test
    fun `the trend follows a steady loss`() {
        // Napi 8 dkg fogyás = heti 0,56 kg.
        val points = WeightTrend.series(daily(90.0, -0.08, 60))
        val weekly = WeightTrend.weeklyChangeKg(points)!!
        assertTrue("A heti ütem: $weekly", abs(weekly - (-0.56)) < 0.08)
    }

    @Test
    fun `one bad morning does not undo the trend`() {
        // Ugyanaz a fogyás, de az utolsó nap +1,8 kg vízvisszatartás.
        val clean = WeightTrend.series(daily(90.0, -0.08, 60))
        val spiked = WeightTrend.series(
            daily(90.0, -0.08, 60) { if (it == 59) 1.8 else 0.0 }
        )
        val drift = spiked.last().trendKg - clean.last().trendKg
        assertTrue("Egy rossz reggel csak ennyit mozdíthat: $drift", drift in 0.0..0.25)
        assertTrue(
            "És az irány maradjon fogyás: ${WeightTrend.weeklyChangeKg(spiked)}",
            WeightTrend.weeklyChangeKg(spiked)!! < 0,
        )
    }

    @Test
    fun `skipped days stand still instead of lying`() {
        // Mérés a 0. és a 30. napon. A köztes napokra nincs információ: a trend áll,
        // nem interpolál. Így a sorozat közepe nem állít olyat, amit nem mértünk.
        val points = WeightTrend.series(listOf(1000L to 90.0, 1030L to 87.0))
        assertEquals("Minden naptári napra jusson pont", 31, points.size)
        assertEquals(29, points.count { it.measuredKg == null })
        val middle = points.first { it.epochDay == 1015L }
        assertEquals("A köztes napokon a trend áll", points.first().trendKg, middle.trendKg, 0.0001)
    }

    @Test
    fun `too little data gives no weekly rate at all`() {
        val points = WeightTrend.series(daily(90.0, -0.1, 5))
        assertNull("Öt napból nem mondunk heti ütemet", WeightTrend.weeklyChangeKg(points))
        assertNull(WeightTrend.daysToTargetAtMeasuredRate(points, 80.0))
    }

    @Test
    fun `the target date comes from the measured rate`() {
        // Heti 0,56 kg-mal a 90-ről indulva a trend ~85,4-nél tart; 80 kg-ig még
        // nagyjából 5,4 kg, vagyis ~68 nap.
        val points = WeightTrend.series(daily(90.0, -0.08, 60))
        val days = WeightTrend.daysToTargetAtMeasuredRate(points, 80.0)!!
        assertTrue("A becsült nap: $days", days in 50..90)
    }

    @Test
    fun `no date when the trend goes the wrong way`() {
        // Hízik, de fogyni akar: ilyenkor a dátum nem óvatos becslés lenne, hanem
        // kitaláció. Ez a leghasznosabb pillanat arra, hogy NE mondjunk semmit.
        val points = WeightTrend.series(daily(90.0, 0.05, 60))
        assertNull(WeightTrend.daysToTargetAtMeasuredRate(points, 80.0))
    }

    @Test
    fun `no date when the goal is already reached`() {
        val points = WeightTrend.series(daily(90.0, -0.08, 60))
        assertNull(WeightTrend.daysToTargetAtMeasuredRate(points, 95.0))
    }

    @Test
    fun `an unreachably slow rate gives no date either`() {
        // Napi 1 grammal a 10 kg több mint huszonhét év. Egy ilyen dátum nem
        // tájékoztat, csak elveszi a kedvet.
        val points = WeightTrend.series(daily(90.0, -0.001, 60))
        assertNull(WeightTrend.daysToTargetAtMeasuredRate(points, 80.0))
    }

    @Test
    fun `an empty log is not a crash`() {
        assertEquals(emptyList<Any>(), WeightTrend.series(emptyList()))
        assertNull(WeightTrend.weeklyChangeKg(emptyList()))
        assertNull(WeightTrend.daysToTargetAtMeasuredRate(emptyList(), 80.0))
    }

    @Test
    fun `a flat stretch sits in the middle of the chart, not on its bottom edge`() {
        // Öt deka ingadozás: a grafikon nem nagyíthatja hegyvidékké, de az aljára sem
        // ragaszthatja. A régi kód csak az OSZTÓT cserélte ki egy kilóra, a nullpont
        // maradt a legkisebb érték — a vonal így a grafikon alsó 5%-ában futott.
        val (low, high) = WeightTrend.chartWindow(listOf(80.00, 80.05))
        assertEquals("Az ablak legalább egy kiló", 1.0, high - low, 1e-9)
        assertEquals("És az adatok a közepén vannak", 80.025, (low + high) / 2, 1e-9)
        val middle = (80.025 - low) / (high - low)
        assertTrue("A vonal a középső harmadban: $middle", middle in 0.33..0.67)
    }

    @Test
    fun `a real spread keeps its own window`() {
        // Ahol van mit mutatni, ott ne nagyítsunk mesterségesen: a szélső értékek
        // maradnak a grafikon szélein.
        val (low, high) = WeightTrend.chartWindow(listOf(78.0, 82.0, 80.0))
        assertEquals(78.0, low, 1e-9)
        assertEquals(82.0, high, 1e-9)
    }

    @Test
    fun `a single value still gets a whole window around it`() {
        val (low, high) = WeightTrend.chartWindow(listOf(75.0))
        assertEquals(1.0, high - low, 1e-9)
        assertEquals(75.0, (low + high) / 2, 1e-9)
    }

    @Test
    fun `no values is not a crash`() {
        val (low, high) = WeightTrend.chartWindow(emptyList())
        assertTrue("Az ablaknak magasságot kell adnia: $low..$high", high > low)
    }
}
