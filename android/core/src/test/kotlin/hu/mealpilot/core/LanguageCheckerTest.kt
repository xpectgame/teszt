package hu.mealpilot.core

import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.LanguageChecker
import hu.mealpilot.core.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A nyelvi ellenőrzés két irányban hibázhat, és a második a veszélyesebb: ha jó magyar
 * szöveget jelöl hibásnak, minden terv fölöslegesen fut egy javító kört, ami időbe és
 * pénzbe kerül. Ezért a „tiszta terv" esetek itt legalább olyan fontosak, mint a
 * találatok.
 */
class LanguageCheckerTest {

    private fun plan(
        title: String = "Heti étrend",
        summary: String = "Kiegyensúlyozott hét, sok fehérjével.",
        mealName: String = "Rántotta pirítóssal",
        description: String = "Gyors, laktató reggeli.",
        steps: List<String> = listOf("Süsd meg a tojást."),
        ingredients: List<String> = listOf("tojás"),
    ) = AiPlanResponse(
        planTitle = title,
        summary = summary,
        days = listOf(
            AiDay(
                dayIndex = 0,
                title = "Hétfő",
                meals = listOf(
                    AiMeal(
                        slot = "BREAKFAST",
                        name = mealName,
                        description = description,
                        recipeSteps = steps,
                        ingredients = ingredients.map { AiIngredient(name = it) },
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `a tiszta magyar terv nem ad hibát`() {
        assertEquals(emptyList<String>(), LanguageChecker.check(plan()))
    }

    @Test
    fun `az angol ételnevet megtalálja és megmondja a magyar alakját`() {
        val problems = LanguageChecker.check(plan(ingredients = listOf("cottage cheese", "zabpehely")))
        assertEquals(1, problems.size)
        assertTrue(problems[0], problems[0].contains("cottage cheese"))
        assertTrue("A javításnak benne kell lennie", problems[0].contains("túró"))
    }

    @Test
    fun `a hosszabb kifejezés nyer a rövidebb fölött`() {
        // A "cottage cheese" nem jelenhet meg külön "cheese" találatként is: a javító
        // prompt egy fogalomra egy sort kapjon.
        val problems = LanguageChecker.check(plan(mealName = "Cottage cheese tál"))
        assertEquals(1, problems.size)
    }

    @Test
    fun `a meghonosodott jövevényszavakat nem jelöli hibának`() {
        // Ezek egy magyar étlapon is így szerepelnének. Ha ezeket megfognánk, minden
        // smoothie-s terv fölösleges javító kört futna.
        val clean = plan(
            mealName = "Bogyós smoothie chia maggal",
            description = "Wokban pirított quinoa, müzlivel.",
            ingredients = listOf("quinoa", "chia mag", "müzli"),
        )
        assertEquals(emptyList<String>(), LanguageChecker.check(clean))
    }

    @Test
    fun `a magyar szavakba ágyazott angol részlet nem találat`() {
        // Szóhatár nélkül a "the" beletalálna a "tehén"-be, az "and" az "andalúz"-ba,
        // a "cook" a "cookie"-ba. Ez a teszt őrzi a szóhatárt.
        val clean = plan(
            description = "Tehéntúróval és andalúz fűszerrel.",
            steps = listOf("Forrald fel a vizet.", "Pirítsd meg a serpenyőben."),
            ingredients = listOf("tehéntúró", "forró paprika"),
        )
        assertEquals(emptyList<String>(), LanguageChecker.check(clean))
    }

    @Test
    fun `az egész angol mondatot akkor is elkapja, ha nincs benne ismert ételnév`() {
        val problems = LanguageChecker.check(
            plan(steps = listOf("Cook the rice and season with pepper.")),
        )
        assertEquals(1, problems.size)
        assertTrue(problems[0], problems[0].contains("írd át magyarra"))
    }

    @Test
    fun `egyetlen idegen szó önmagában nem elég egy mondathoz`() {
        // Egy szó lehet márkanév vagy meghonosodott fogásnév. Kettő már angol mondat.
        assertEquals(emptyList<String>(), LanguageChecker.check(plan(mealName = "Cook & Go saláta")))
    }

    @Test
    fun `az angol tervben az angol szöveg helyes`() {
        val english = plan(
            title = "Weekly plan",
            summary = "A balanced week with plenty of protein.",
            mealName = "Cottage cheese bowl",
            ingredients = listOf("cottage cheese"),
        )
        assertEquals(emptyList<String>(), LanguageChecker.check(english, AppLanguage.EN))
    }

    @Test
    fun `ugyanaz a szó sok fogásban egyetlen sort ad`() {
        // Különben egyetlen visszatérő hiba kiszorítaná a többit a javító promptból.
        val repeated = AiPlanResponse(
            planTitle = "Heti étrend",
            days = (0..6).map { index ->
                AiDay(
                    dayIndex = index,
                    title = "Nap",
                    meals = listOf(AiMeal(name = "Cottage cheese tál")),
                )
            },
        )
        assertEquals(1, LanguageChecker.check(repeated).size)
    }

    @Test
    fun `a hibalista nem nő a javító prompt fölé`() {
        val messy = plan(
            mealName = "Chicken breast with sweet potato",
            description = "Greek yogurt and peanut butter on wholemeal toast.",
            steps = listOf("Add olive oil, then serve with brown rice."),
            ingredients = listOf("egg white", "rolled oats", "bell pepper", "ground beef", "cream cheese"),
        )
        val problems = LanguageChecker.check(messy)
        assertTrue("Legyen találat", problems.isNotEmpty())
        assertTrue("Legfeljebb 8 sor, különben elnyomja a tápértékhibákat", problems.size <= 8)
    }
}
