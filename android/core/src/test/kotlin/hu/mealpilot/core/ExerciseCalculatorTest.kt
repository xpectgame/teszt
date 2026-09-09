package hu.mealpilot.core

import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.energy.ExerciseCalculator
import hu.mealpilot.core.energy.MetTable
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCalculatorTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 35, heightCm = 180.0, weightKg = 90.0,
    )

    private val run10 = MetTable.byKey("run_10")!!

    @Test
    fun `standard met formula matches the textbook value`() {
        // kcal/perc = MET * 3.5 * kg / 200 = 9.8 * 3.5 * 90 / 200 = 15.435 -> 30 perc = 463 kcal
        val result = ExerciseCalculator.estimate(profile, run10, 30, preferStandardMet = true)
        assertEquals(ExerciseCalculator.Method.STANDARD_MET, result.method)
        assertEquals(463, result.kcalGross)
        // nettó: (9.8 - 1) * 3.5 * 90 / 200 * 30 = 415.8
        assertEquals(416, result.kcalNet)
    }

    @Test
    fun `personalised met uses the users own resting rate`() {
        val result = ExerciseCalculator.estimate(profile, run10, 30)
        assertEquals(ExerciseCalculator.Method.PERSONALIZED_MET, result.method)
        // BMR 1855 / 1440 = 1.2882 kcal/perc; 9.8 * 1.2882 * 30 = 378.7
        assertEquals(379, result.kcalGross)
        assertEquals(340, result.kcalNet)
    }

    @Test
    fun `personalised estimate is lower than the textbook one for a heavy adult`() {
        val standard = ExerciseCalculator.estimate(profile, run10, 45, preferStandardMet = true)
        val personal = ExerciseCalculator.estimate(profile, run10, 45)
        assertTrue(
            "A 3,5 ml/kg/min feltevés felülbecsül nehezebb felnőttnél",
            personal.kcalGross < standard.kcalGross,
        )
    }

    @Test
    fun `heart rate estimate takes priority when available`() {
        val result = ExerciseCalculator.estimate(profile, run10, 30, avgHeartRate = 150)
        assertEquals(ExerciseCalculator.Method.HEART_RATE, result.method)
        // Keytel: (-55.0969 + 0.6309*150 + 0.1988*90 + 0.2017*35) / 4.184 = 15.41 kcal/perc
        assertEquals(462, result.kcalGross)
        assertTrue(result.kcalNet < result.kcalGross)
        assertTrue(result.note.contains("150"))
    }

    @Test
    fun `implausible heart rates fall back to the met estimate`() {
        val result = ExerciseCalculator.estimate(profile, run10, 30, avgHeartRate = 20)
        assertEquals(ExerciseCalculator.Method.PERSONALIZED_MET, result.method)
    }

    @Test
    fun `net calories are never negative for resting-level activity`() {
        val stretching = MetTable.byKey("stretching")!!
        val result = ExerciseCalculator.estimate(profile.copy(weightKg = 55.0), stretching, 10)
        assertTrue(result.kcalNet >= 0)
    }

    @Test
    fun `zero minutes burns nothing`() {
        val result = ExerciseCalculator.estimate(profile, run10, 0)
        assertEquals(0, result.kcalGross)
        assertEquals(0, result.kcalNet)
    }

    @Test
    fun `female keytel coefficients differ from male`() {
        val female = profile.copy(sex = Sex.FEMALE)
        val maleRate = ExerciseCalculator.keytelKcalPerMinute(profile, 150)
        val femaleRate = ExerciseCalculator.keytelKcalPerMinute(female, 150)
        assertTrue(femaleRate > 0)
        assertTrue(femaleRate < maleRate)
    }

    @Test
    fun `max heart rate uses the tanaka formula`() {
        assertEquals(184, ExerciseCalculator.estimatedMaxHeartRate(35)) // 208 - 0.7*35 = 183.5
        assertEquals(194, ExerciseCalculator.estimatedMaxHeartRate(20))
    }

    @Test
    fun `met table lookup and search work with hungarian accents`() {
        assertNotNull(MetTable.byKey("bike_moderate"))
        assertTrue(MetTable.search("kerekpar").isNotEmpty())
        assertTrue(MetTable.search("KERÉKPÁR").isNotEmpty())
        assertTrue(MetTable.search("úszás").any { it.key.startsWith("swim") })
        assertEquals(MetTable.all.size, MetTable.search("  ").size)
    }

    @Test
    fun `resting rate matches bmr divided by minutes in a day`() {
        assertEquals(
            EnergyCalculator.bmr(profile) / 1440.0,
            ExerciseCalculator.restingKcalPerMinute(profile),
            0.0001,
        )
    }
}
