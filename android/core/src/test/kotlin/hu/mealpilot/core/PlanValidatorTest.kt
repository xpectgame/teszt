package hu.mealpilot.core

import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiNutrition
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.PlanValidator
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DailyTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {

    private val target = DailyTarget(kcal = 2000, proteinG = 160, carbsG = 175, fatG = 67, fiberG = 28)

    /** Négy egyforma étkezés, amik együtt pontosan a célt adják ki. */
    private fun okDay(index: Int) = AiDay(
        dayIndex = index,
        title = "Nap $index",
        meals = List(4) { i ->
            AiMeal(
                slot = MealSlot.forMealsPerDay(4)[i].name,
                time = listOf("07:30", "12:30", "16:00", "19:30")[i],
                name = "Fogás $index-$i",
                ingredients = listOf(AiIngredient(name = "alapanyag", quantity = 100.0, unit = "g")),
                nutrition = AiNutrition(kcal = 500.0, proteinG = 40.0, carbsG = 44.0, fatG = 16.75),
            )
        },
    )

    @Test
    fun `a well formed plan has no problems`() {
        val plan = AiPlanResponse(days = List(3) { okDay(it) })
        assertTrue(PlanValidator.validate(plan, target, expectedDays = 3, expectedMealsPerDay = 4, language = AppLanguage.HU).isEmpty())
    }

    @Test
    fun `an empty plan is reported`() {
        val problems = PlanValidator.validate(AiPlanResponse(), target, 7, 4, language = AppLanguage.HU)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("egyetlen napot sem"))
    }

    @Test
    fun `missing days are reported`() {
        val plan = AiPlanResponse(days = List(5) { okDay(it) })
        val problems = PlanValidator.validate(plan, target, 7, 4, language = AppLanguage.HU)
        assertTrue(problems.any { it.contains("7 napot kértem") })
    }

    @Test
    fun `duplicate day indexes are reported`() {
        val plan = AiPlanResponse(days = listOf(okDay(0), okDay(0)))
        assertTrue(PlanValidator.validate(plan, target, 2, 4, language = AppLanguage.HU).any { it.contains("Ismétlődő") })
    }

    @Test
    fun `calorie overshoot beyond the tolerance is reported`() {
        val day = okDay(0).let { d ->
            d.copy(meals = d.meals.map { it.copy(nutrition = it.nutrition.copy(kcal = 650.0, carbsG = 81.5)) })
        }
        val problems = PlanValidator.validate(AiPlanResponse(days = listOf(day)), target, 1, 4, language = AppLanguage.HU)
        assertTrue(problems.any { it.contains("2600 kcal a 2000 kcal cél helyett") })
    }

    @Test
    fun `low protein is reported`() {
        val day = okDay(0).let { d ->
            d.copy(meals = d.meals.map {
                it.copy(nutrition = it.nutrition.copy(proteinG = 10.0, carbsG = 74.0))
            })
        }
        val problems = PlanValidator.validate(AiPlanResponse(days = listOf(day)), target, 1, 4, language = AppLanguage.HU)
        assertTrue(problems.any { it.contains("fehérje") })
    }

    @Test
    fun `macro and calorie mismatch inside one meal is caught`() {
        val day = okDay(0).let { d ->
            d.copy(meals = d.meals.mapIndexed { i, m ->
                if (i == 0) m.copy(nutrition = m.nutrition.copy(proteinG = 5.0, carbsG = 5.0, fatG = 5.0)) else m
            })
        }
        val problems = PlanValidator.validate(AiPlanResponse(days = listOf(day)), target, 1, 4, language = AppLanguage.HU)
        assertTrue(problems.any { it.contains("makrók") })
    }

    @Test
    fun `structural problems are caught`() {
        val broken = AiDay(
            dayIndex = 0,
            meals = listOf(
                AiMeal(name = "", time = "25:99", ingredients = emptyList(), nutrition = AiNutrition(kcal = 0.0)),
            ),
        )
        val problems = PlanValidator.validate(AiPlanResponse(days = listOf(broken)), target, 1, 4, language = AppLanguage.HU)
        assertTrue(problems.any { it.contains("névtelen") })
        assertTrue(problems.any { it.contains("hibás időformátum") })
        assertTrue(problems.any { it.contains("hiányzik a kalóriaérték") })
        assertTrue(problems.any { it.contains("nincsenek hozzávalók") })
        assertTrue(problems.any { it.contains("4 étkezést kértem") })
    }

    @Test
    fun `day summaries sum the meals`() {
        val summaries = PlanValidator.daySummaries(AiPlanResponse(days = listOf(okDay(0))))
        assertEquals(1, summaries.size)
        assertEquals(2000.0, summaries[0].nutrients.kcal, 0.01)
        assertEquals(160.0, summaries[0].nutrients.proteinG, 0.01)
        assertEquals(4, summaries[0].mealCount)
    }

    /** Ugyanaz a hibátlan nap, angolul — ahogy egy angol nyelvű terv néz ki. */
    private fun englishDay(index: Int) = AiDay(
        dayIndex = index,
        title = "Day $index",
        meals = List(4) { i ->
            AiMeal(
                slot = MealSlot.forMealsPerDay(4)[i].name,
                time = listOf("07:30", "12:30", "16:00", "19:30")[i],
                name = listOf("Chicken breast with sweet potato", "Greek yogurt bowl",
                    "Peanut butter toast", "Cottage cheese salad")[i],
                description = "Add olive oil and serve with brown rice.",
                ingredients = listOf(AiIngredient(name = "rolled oats", quantity = 100.0, unit = "g")),
                nutrition = AiNutrition(kcal = 500.0, proteinG = 40.0, carbsG = 44.0, fatG = 16.75),
            )
        },
    )

    @Test
    fun `a correct english plan passes when the language is passed through`() {
        val plan = AiPlanResponse(planTitle = "Weekly plan", days = List(3) { englishDay(it) })
        val problems = PlanValidator.validate(
            plan, target, expectedDays = 3, expectedMealsPerDay = 4, language = AppLanguage.EN,
        )
        assertTrue("A hibátlan angol terv nem hibás: $problems", problems.isEmpty())
    }

    @Test
    fun `forgetting the language turns a correct english plan into a pile of errors`() {
        // Ez a teszt azt írja le, MIÉRT kötelező átadni a nyelvet. Az alapértelmezés a
        // magyar: ilyenkor az angolmaradvány-kereső egy hibátlan ANGOL tervre fut rá, és
        // minden angol ételnevet hibának jelöl. A hívó ebből javító kört csinál, ami
        // magyarra íratná át a helyes angol tervet — pénzért, körbe-körbe.
        val plan = AiPlanResponse(planTitle = "Weekly plan", days = List(3) { englishDay(it) })
        val problems = PlanValidator.validate(plan, target, expectedDays = 3, expectedMealsPerDay = 4, language = AppLanguage.HU)
        assertTrue(
            "Az alapértelmezett nyelvvel az angol terv hibásnak látszik — ezért kell átadni",
            problems.isNotEmpty(),
        )
        assertTrue("A találatok angol ételnevek", problems.any { it.contains("cottage cheese") })
    }

    @Test
    fun `the problem list speaks the language of the plan`() {
        // A hibalista a JAVÍTÓ PROMPTBA megy. Magyar hibaüzenetből a modell az angol
        // tervnél a válasz nyelvére is következtetne.
        val over = AiPlanResponse(days = listOf(englishDay(0).let { day ->
            day.copy(meals = day.meals.map { it.copy(nutrition = it.nutrition.copy(kcal = 800.0)) })
        }))
        val problems = PlanValidator.validate(
            over, target, expectedDays = 1, expectedMealsPerDay = 4, language = AppLanguage.EN,
        )
        assertTrue("Legyen kalóriahiba: $problems", problems.any { it.contains("kcal") })
        assertTrue(
            "Angol tervnél angol hibaüzenet kell: $problems",
            problems.any { it.contains("instead of the") },
        )
        assertTrue("Magyar szöveg nem kerülhet bele: $problems", problems.none { it.contains(". nap") })
    }
}
