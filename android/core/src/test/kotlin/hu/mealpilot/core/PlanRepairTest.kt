package hu.mealpilot.core

import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiNutrition
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.PlanRepair
import hu.mealpilot.core.ai.PlanValidator
import hu.mealpilot.core.model.DailyTarget
import hu.mealpilot.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRepairTest {

    private val target = DailyTarget(kcal = 2000, proteinG = 160, carbsG = 175, fatG = 67, fiberG = 28)

    /** Négy egyforma étkezés, együtt [dayKcal] kalóriával és arányos makrókkal. */
    private fun day(index: Int, dayKcal: Double, dayProtein: Double = 160.0) = AiDay(
        dayIndex = index,
        meals = List(4) {
            AiMeal(
                name = "Fogás $it",
                time = "12:00",
                ingredients = listOf(
                    AiIngredient(name = "csirkemell", quantity = 150.0, unit = "g"),
                    AiIngredient(name = "tojás", quantity = 2.0, unit = "db"),
                    AiIngredient(name = "olívaolaj", quantity = 5.0, unit = "ml", pantryStaple = true),
                ),
                nutrition = AiNutrition(
                    kcal = dayKcal / 4,
                    proteinG = dayProtein / 4,
                    carbsG = dayKcal / 4 * 0.35 / 4,
                    fatG = dayKcal / 4 * 0.25 / 9,
                    fiberG = 7.0,
                ),
            )
        },
    )

    private fun totalKcal(d: AiDay) = Nutrients.sum(d.meals.map { it.nutrition.toNutrients() }).kcal

    @Test
    fun `an overshooting day is scaled down to the target`() {
        val over = day(0, 2400.0, dayProtein = 200.0)
        val factor = PlanRepair.scaleFactor(over, target)
        assertNotNull(factor)
        val fixed = PlanRepair.scaleDay(over, factor!!)
        assertEquals(2000.0, totalKcal(fixed), 1.0)
    }

    @Test
    fun `an undershooting day is scaled up`() {
        val under = day(0, 1750.0, dayProtein = 160.0)
        val fixed = PlanRepair.normalize(AiPlanResponse(days = listOf(under)), target)
        assertEquals(2000.0, totalKcal(fixed.plan.days[0]), 1.0)
        assertTrue(fixed.adjusted.containsKey(0))
    }

    @Test
    fun `days already on target are left alone`() {
        val exact = day(0, 2010.0)
        assertNull(PlanRepair.scaleFactor(exact, target))
        val result = PlanRepair.normalize(AiPlanResponse(days = listOf(exact)), target)
        assertTrue(result.adjusted.isEmpty())
        assertEquals(exact, result.plan.days[0])
    }

    @Test
    fun `a wildly wrong day is not scaled - it needs real regeneration`() {
        assertNull(PlanRepair.scaleFactor(day(0, 3500.0, dayProtein = 250.0), target))
        assertNull(PlanRepair.scaleFactor(day(0, 900.0, dayProtein = 90.0), target))
    }

    @Test
    fun `scaling down is refused when it would starve the protein target`() {
        // 2400 kcal, de csak 150 g fehérje: 0,83-as szorzó 125 g-ra vinné, a 136 g-os alsó határ alá.
        assertNull(PlanRepair.scaleFactor(day(0, 2400.0, dayProtein = 150.0), target))
    }

    @Test
    fun `weights are rescaled but countable items are not`() {
        val over = day(0, 2400.0, dayProtein = 200.0)
        val fixed = PlanRepair.scaleDay(over, PlanRepair.scaleFactor(over, target)!!)
        val ingredients = fixed.meals[0].ingredients

        val chicken = ingredients.first { it.name == "csirkemell" }
        assertEquals(125.0, chicken.quantity, 0.01) // 150 * 0,833 = 125

        val eggs = ingredients.first { it.name == "tojás" }
        assertEquals("A darabra mért hozzávaló nem skálázódik", 2.0, eggs.quantity, 0.001)

        val oil = ingredients.first { it.name == "olívaolaj" }
        assertEquals("Az alapfűszerekhez nem nyúlunk", 5.0, oil.quantity, 0.001)
    }

    @Test
    fun `gram quantities are rounded to usable numbers`() {
        val over = day(0, 2222.0, dayProtein = 190.0)
        val fixed = PlanRepair.scaleDay(over, PlanRepair.scaleFactor(over, target)!!)
        val chicken = fixed.meals[0].ingredients.first { it.name == "csirkemell" }
        assertEquals("50 g fölött ötösével kerekítünk", 0.0, chicken.quantity % 5, 0.001)
    }

    @Test
    fun `normalising a plan makes it pass validation without a model round-trip`() {
        val plan = AiPlanResponse(days = listOf(day(0, 2300.0, 190.0), day(1, 1800.0, 165.0)))
        val before = PlanValidator.validate(plan, target, expectedDays = 2, expectedMealsPerDay = 4)
        assertTrue("Javítás előtt legyen kalóriahiba", before.any { it.contains("kcal") })

        val after = PlanValidator.validate(
            PlanRepair.normalize(plan, target).plan,
            target,
            expectedDays = 2,
            expectedMealsPerDay = 4,
        )
        assertTrue("Javítás után ne maradjon hiba: $after", after.isEmpty())
    }

    @Test
    fun `an empty or zero-calorie day is left untouched`() {
        assertNull(PlanRepair.scaleFactor(AiDay(dayIndex = 0, meals = emptyList()), target))
        assertNull(PlanRepair.scaleFactor(day(0, 0.0), target))
    }
}
