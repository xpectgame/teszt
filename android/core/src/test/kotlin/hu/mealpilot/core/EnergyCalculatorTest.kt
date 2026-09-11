package hu.mealpilot.core

import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.ActivityLevel
import hu.mealpilot.core.model.MacroPreset
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EnergyCalculatorTest {

    private val male = UserProfile(
        sex = Sex.MALE, ageYears = 35, heightCm = 180.0, weightKg = 90.0,
        activityLevel = ActivityLevel.LIGHT, targetRateKgPerWeek = 0.5,
    )

    @Test
    fun `mifflin st jeor matches the published formula for men`() {
        // 10*90 + 6.25*180 - 5*35 + 5 = 900 + 1125 - 175 + 5 = 1855
        assertEquals(1855.0, EnergyCalculator.bmrMifflinStJeor(male), 0.01)
    }

    @Test
    fun `mifflin st jeor matches the published formula for women`() {
        val female = male.copy(sex = Sex.FEMALE, weightKg = 70.0, heightCm = 165.0, ageYears = 30)
        // 10*70 + 6.25*165 - 5*30 - 161 = 700 + 1031.25 - 150 - 161 = 1420.25
        assertEquals(1420.25, EnergyCalculator.bmrMifflinStJeor(female), 0.01)
    }

    @Test
    fun `katch mcardle is used when body fat is known`() {
        val withFat = male.copy(bodyFatPercent = 25.0)
        // LBM = 90 * 0.75 = 67.5 -> 370 + 21.6*67.5 = 1828
        assertEquals(1828.0, EnergyCalculator.bmr(withFat), 0.01)
        assertEquals(EnergyCalculator.bmrKatchMcArdle(67.5), EnergyCalculator.bmr(withFat), 0.01)
    }

    @Test
    fun `half a kilo per week maps to a 550 kcal deficit`() {
        val budget = EnergyCalculator.budget(male)
        assertEquals(550, budget.requestedDeficit)
        assertEquals(550, budget.appliedDeficit)
        assertTrue(budget.warnings.isEmpty())
        // TDEE = 1855 * 1.375 = 2550.6 -> cél 2001
        assertEquals(2551, budget.tdee)
        assertEquals(2001, budget.target.kcal)
    }

    @Test
    fun `aggressive rates are capped at a quarter of tdee`() {
        val budget = EnergyCalculator.budget(male.copy(targetRateKgPerWeek = 1.0))
        assertEquals(1100, budget.requestedDeficit)
        assertTrue("A deficitet korlátozni kell", budget.wasCapped)
        assertEquals(638, budget.appliedDeficit) // a TDEE 25%-a
        assertTrue(budget.warnings.any { it.contains("agresszív") })
    }

    @Test
    fun `target never drops below the calorie floor`() {
        val small = UserProfile(
            sex = Sex.FEMALE, ageYears = 45, heightCm = 158.0, weightKg = 55.0,
            activityLevel = ActivityLevel.SEDENTARY, targetRateKgPerWeek = 1.0,
        )
        val budget = EnergyCalculator.budget(small)
        assertTrue(
            "A cél (${budget.target.kcal}) nem mehet az alapanyagcsere (${budget.bmr}) alá",
            budget.target.kcal >= budget.bmr,
        )
        assertTrue(budget.target.kcal >= 1200)
    }

    @Test
    fun `rate above one kilo per week is clamped with a warning`() {
        val budget = EnergyCalculator.budget(male.copy(targetRateKgPerWeek = 1.4))
        assertTrue(budget.warnings.any { it.contains("1,0 kg/hétre") })
        assertTrue(budget.requestedDeficit <= 1100)
    }

    @Test
    fun `macro split adds up to the calorie target`() {
        val budget = EnergyCalculator.budget(male.copy(macroPreset = MacroPreset.HIGH_PROTEIN))
        val t = budget.target
        val fromMacros = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
        assertTrue(
            "A makrók ($fromMacros kcal) essenek a cél (${t.kcal}) közelébe",
            abs(fromMacros - t.kcal) <= 12,
        )
        assertTrue("A fehérje ne legyen kevesebb 2 g/ttkg-nál", t.proteinG >= 170)
    }

    @Test
    fun `protein and fat are scaled down when the budget is very small`() {
        val tiny = UserProfile(sex = Sex.FEMALE, ageYears = 60, heightCm = 150.0, weightKg = 120.0)
        val target = EnergyCalculator.macroTarget(tiny, 1300)
        assertTrue("A szénhidrát nem lehet negatív", target.carbsG >= 0)
        assertTrue(target.proteinG * 4 + target.fatG * 9 <= 1300)
    }

    @Test
    fun `days to target is derived from the applied deficit`() {
        val profile = male.copy(targetWeightKg = 80.0)
        val budget = EnergyCalculator.budget(profile)
        // 10 kg * 7700 / 550 = 140 nap
        assertEquals(140, EnergyCalculator.daysToTarget(profile, budget))
        assertEquals(null, EnergyCalculator.daysToTarget(male, budget))
    }
}
