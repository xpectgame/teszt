package hu.mealpilot.core

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.ActivityLevel
import hu.mealpilot.core.model.MacroPreset
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        // Itt tényleg a 25%-os szabály fog, és az üzenet ezt is mondja — nem azt,
        // hogy a felhasználó kért volna valami szélsőségeset.
        val warning = budget.warnings.single { it.contains("kg/hét") }
        assertTrue(warning, warning.contains("biztonságos felső határ"))
        assertTrue("Mondja meg, mi fér bele: $warning", warning.contains("0,58 kg/hét") || warning.contains("0.58 kg/hét"))
    }

    @Test
    fun `a sedentary user on the default rate is not told their goal is aggressive`() {
        // Ülő életmódnál a napi felhasználás az alapanyagcsere 1,2-szerese, tehát a
        // kettő közé fér a TELJES mozgástér — az app alapértelmezett üteme (0,5 kg/hét)
        // ennél nagyobb deficitet kérne. Vagyis MINDEN ülő életmódú felhasználó
        // megkapja ezt az üzenetet, már az első képernyőn, a saját alapértelmezésünkre.
        //
        // A régi szöveg ezt úgy fogalmazta, hogy „a kért ütem túl agresszív" — ami a
        // felhasználót hibáztatja azért, amit nem ő állított be, és nem is igaz: heti
        // fél kiló nem agresszív cél.
        val sedentary = UserProfile(
            sex = Sex.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 62.0,
            activityLevel = ActivityLevel.SEDENTARY,
        )
        val budget = EnergyCalculator.budget(sedentary, AppLanguage.HU)

        assertTrue("Az alapértelmezett ütem 0,5 kg/hét", sedentary.targetRateKgPerWeek == 0.5)
        assertTrue("Ülő életmódnál korlátozni kell", budget.wasCapped)

        val warning = budget.warnings.single { it.contains("kg/hét") }
        assertTrue("Ne hibáztassa a felhasználót: $warning", !warning.contains("agresszív"))
        assertTrue("Nevezze meg az okot: $warning", warning.contains("alapanyagcser"))
        assertTrue("Mondja meg, mi fér bele: $warning", warning.contains("kg/hét fér bele"))
        assertTrue("Mondja meg, mit tehet: $warning", warning.contains("mozgás"))
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

    // ---------------------------------------------------------------------------
    // Hibás testadatokból nem születhet veszélyes kalóriacél. Mindhárom eset ÉLES
    // hiba volt: a súlyrögzítő kártya nem ellenőrzött tartományt, és onnan a
    // testzsír mezőbe bármi bekerülhetett.
    // ---------------------------------------------------------------------------

    private fun profile(weight: Double = 80.0, bodyFat: Double? = null) = UserProfile(
        sex = Sex.MALE,
        ageYears = 35,
        heightCm = 178.0,
        weightKg = weight,
        bodyFatPercent = bodyFat,
        activityLevel = ActivityLevel.LIGHT,
    )

    @Test
    fun `an out-of-range body fat falls back instead of producing nonsense`() {
        // 150% testzsír negatív zsírmentes tömeget adna, abból negatív alapanyagcserét,
        // végül NEGATÍV napi kalóriacélt. A képlet helyett a testzsírt hagyjuk figyelmen
        // kívül: a Mifflin-St Jeor csak a súlyt és a magasságot használja.
        assertNull(profile(bodyFat = 150.0).leanBodyMassKg)
        assertNull(profile(bodyFat = 0.0).leanBodyMassKg)
        assertNotNull(profile(bodyFat = 25.0).leanBodyMassKg)

        val broken = EnergyCalculator.budget(profile(bodyFat = 150.0))
        val sane = EnergyCalculator.budget(profile())
        assertEquals("A hibás érték nem befolyásolhatja az eredményt", sane.target.kcal, broken.target.kcal)
        assertTrue("A cél nem lehet negatív", broken.target.kcal > 0)
    }

    @Test
    fun `the daily target never drops below the safety floor`() {
        // Korábban a kód a DEFICITET nullázta le, de a célt nem emelte meg. Ha a napi
        // felhasználás maga a határ alatt volt, a felhasználó a saját klinikai minimuma
        // alatti célt kapott — miközben a figyelmeztetés az ellenkezőjét állította.
        val low = profile(weight = 45.0, bodyFat = 65.0)
        val budget = EnergyCalculator.budget(low)
        val floor = EnergyCalculator.floorKcal(low)

        assertTrue(
            "A cél (${budget.target.kcal}) nem lehet az alsó határ (${floor.toInt()}) alatt",
            budget.target.kcal >= floor.toInt(),
        )
        assertEquals("Ilyenkor nincs deficit", 0, budget.appliedDeficit)
        assertTrue("És szólni kell róla", budget.warnings.any { it.contains("ellenőrizd", ignoreCase = true) })
    }

    @Test
    fun `a healthy profile is untouched by the new floor`() {
        // A javítás nem nyúlhat a normál esethez: ott a deficit és a cél marad, ami volt.
        val budget = EnergyCalculator.budget(profile())
        assertTrue("Van érdemi deficit", budget.appliedDeficit > 400)
        assertTrue("A cél a határ fölött van", budget.target.kcal > EnergyCalculator.floorKcal(profile()).toInt())
        assertTrue("És nincs fölösleges figyelmeztetés", budget.warnings.none { it.contains("ellenőrizd", ignoreCase = true) })
    }

    @Test
    fun `the macro targets stay positive for every accepted input`() {
        for (fat in listOf(null, 3.0, 25.0, 70.0)) {
            for (weight in listOf(35.0, 80.0, 300.0)) {
                val b = EnergyCalculator.budget(profile(weight = weight, bodyFat = fat))
                assertTrue("kcal > 0 (fat=$fat, weight=$weight)", b.target.kcal > 0)
                assertTrue("fehérje > 0 (fat=$fat, weight=$weight)", b.target.proteinG > 0)
                assertTrue("zsír > 0 (fat=$fat, weight=$weight)", b.target.fatG > 0)
                assertTrue("szénhidrát >= 0 (fat=$fat, weight=$weight)", b.target.carbsG >= 0)
            }
        }
    }
}
