package hu.mealpilot.core

import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionCheckerTest {

    private fun hits(name: String, vararg restrictions: DietRestriction) =
        RestrictionChecker.violations(name, restrictions.toSet()).map { it.restriction }

    @Test
    fun `gluten is caught in hungarian compound words`() {
        assertEquals(listOf(DietRestriction.GLUTEN), hits("búzaliszt", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("teljes kiőrlésű kenyér", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("durum tészta", DietRestriction.GLUTEN))
    }

    @Test
    fun `free-from products are not flagged`() {
        assertTrue(hits("gluténmentes tészta", DietRestriction.GLUTEN).isEmpty())
        assertTrue(hits("laktózmentes tej", DietRestriction.LACTOSE).isEmpty())
        assertTrue(hits("növényi tejszín", DietRestriction.LACTOSE).isEmpty())
    }

    @Test
    fun `casein is stricter than lactose - lactose-free milk still counts`() {
        assertTrue(hits("laktózmentes tej", DietRestriction.LACTOSE).isEmpty())
        assertEquals(
            listOf(DietRestriction.MILK_PROTEIN),
            hits("laktózmentes tej", DietRestriction.MILK_PROTEIN),
        )
        assertEquals(
            listOf(DietRestriction.MILK_PROTEIN),
            hits("laktózmentes tejföl", DietRestriction.MILK_PROTEIN),
        )
    }

    @Test
    fun `dairy words are caught by their stem`() {
        listOf("tejföl", "tejszín", "trappista sajt", "görög joghurt", "vaj", "túró").forEach {
            assertEquals("$it legyen találat", 1, hits(it, DietRestriction.LACTOSE).size)
        }
    }

    @Test
    fun `peanut butter is flagged for peanut allergy`() {
        assertEquals(listOf(DietRestriction.PEANUT), hits("mogyoróvaj", DietRestriction.PEANUT))
        assertEquals(listOf(DietRestriction.PEANUT), hits("földimogyoró", DietRestriction.PEANUT))
    }

    @Test
    fun `fish names are caught but unrelated ingredients are not`() {
        assertEquals(listOf(DietRestriction.FISH), hits("lazacfilé", DietRestriction.FISH))
        assertEquals(listOf(DietRestriction.FISH), hits("tonhal konzerv", DietRestriction.FISH))
        assertTrue(hits("saláta", DietRestriction.FISH).isEmpty())
        assertTrue(hits("hagyma", DietRestriction.FISH).isEmpty())
    }

    @Test
    fun `word-stem matching does not misfire on lookalike ingredients`() {
        // A „bor" kulcsszó régen eltalálta volna a borsót, a borsot és a borjút is.
        assertTrue(hits("borsó", DietRestriction.NO_ALCOHOL).isEmpty())
        assertTrue(hits("őrölt bors", DietRestriction.NO_ALCOHOL).isEmpty())
        assertTrue(hits("borjúhús", DietRestriction.NO_ALCOHOL).isEmpty())
        // ...a babérlevél pedig a babot.
        assertTrue(hits("babérlevél", DietRestriction.FODMAP).isEmpty())
        assertEquals(listOf(DietRestriction.FODMAP), hits("fehér bab", DietRestriction.FODMAP))
        // ...viszont a valódi bor igen.
        assertEquals(listOf(DietRestriction.NO_ALCOHOL), hits("száraz vörösbor", DietRestriction.NO_ALCOHOL))
    }

    @Test
    fun `pork words are caught for the no-pork choice`() {
        assertEquals(listOf(DietRestriction.NO_PORK), hits("sertéskaraj", DietRestriction.NO_PORK))
        assertEquals(listOf(DietRestriction.NO_PORK), hits("füstölt szalonna", DietRestriction.NO_PORK))
        assertTrue(hits("csirkemell", DietRestriction.NO_PORK).isEmpty())
    }

    @Test
    fun `an ingredient can violate several restrictions at once`() {
        val result = RestrictionChecker.violations(
            "sajtos-tejfölös csirke",
            setOf(DietRestriction.LACTOSE, DietRestriction.NO_POULTRY, DietRestriction.GLUTEN),
        )
        assertEquals(
            setOf(DietRestriction.LACTOSE, DietRestriction.NO_POULTRY),
            result.map { it.restriction }.toSet(),
        )
    }

    @Test
    fun `no restrictions means no problems`() {
        assertTrue(RestrictionChecker.violations("búzaliszt", emptySet()).isEmpty())
        assertTrue(RestrictionChecker.check(plan("búzaliszt"), emptySet()).isEmpty())
    }

    @Test
    fun `plan check reports the day, the meal and the rule`() {
        val problems = RestrictionChecker.check(plan("búzaliszt"), setOf(DietRestriction.GLUTEN))
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("0. nap"))
        assertTrue(problems[0].contains("Reggeli tál"))
        assertTrue(problems[0].contains("búzaliszt"))
        assertTrue(problems[0].contains("Glutén"))
    }

    @Test
    fun `a suspicious meal name is caught even when the ingredients look clean`() {
        val sneaky = AiPlanResponse(
            days = listOf(
                AiDay(
                    dayIndex = 2,
                    meals = listOf(
                        AiMeal(
                            name = "Rántott sajt rizzsel",
                            ingredients = listOf(AiIngredient(name = "rizs", quantity = 80.0, unit = "g")),
                        )
                    ),
                )
            )
        )
        val problems = RestrictionChecker.check(sneaky, setOf(DietRestriction.LACTOSE))
        assertTrue(problems.any { it.contains("fogásnév") })
    }

    @Test
    fun `vegan style implies dairy, egg and meat exclusions without ticking them`() {
        val vegan = UserProfile(dietStyle = DietStyle.VEGAN)
        assertTrue(DietRestriction.MILK_PROTEIN in vegan.effectiveRestrictions)
        assertTrue(DietRestriction.EGG in vegan.effectiveRestrictions)
        assertTrue(DietRestriction.NO_POULTRY in vegan.effectiveRestrictions)
        assertFalse(DietRestriction.GLUTEN in vegan.effectiveRestrictions)

        val problems = RestrictionChecker.check(plan("görög joghurt"), vegan.effectiveRestrictions)
        assertTrue(problems.isNotEmpty())
    }

    @Test
    fun `vegetarian keeps dairy but drops meat and fish`() {
        val vegetarian = UserProfile(dietStyle = DietStyle.VEGETARIAN)
        assertFalse(DietRestriction.MILK_PROTEIN in vegetarian.effectiveRestrictions)
        assertTrue(DietRestriction.FISH in vegetarian.effectiveRestrictions)
        assertTrue(RestrictionChecker.check(plan("görög joghurt"), vegetarian.effectiveRestrictions).isEmpty())
        assertTrue(RestrictionChecker.check(plan("lazacfilé"), vegetarian.effectiveRestrictions).isNotEmpty())
    }

    @Test
    fun `ticked restrictions add to the ones implied by the style`() {
        val profile = UserProfile(
            dietStyle = DietStyle.VEGETARIAN,
            restrictions = setOf(DietRestriction.GLUTEN, DietRestriction.TREE_NUT),
        )
        assertTrue(DietRestriction.GLUTEN in profile.effectiveRestrictions)
        assertTrue(DietRestriction.FISH in profile.effectiveRestrictions)
        assertEquals(
            DietRestriction.impliedBy(DietStyle.VEGETARIAN).size + 2,
            profile.effectiveRestrictions.size,
        )
    }

    @Test
    fun `every restriction has keywords and a rule`() {
        DietRestriction.entries.forEach {
            assertTrue("${it.name}: hiányzik a kulcsszó", it.keywords.isNotEmpty())
            assertTrue("${it.name}: hiányzik a szabály", it.rule.isNotBlank())
            assertTrue("${it.name}: hiányzik a magyar név", it.hu.isNotBlank())
        }
        assertEquals(DietRestriction.entries.size, DietRestriction.byGroup().values.sumOf { it.size })
    }

    private fun plan(ingredient: String) = AiPlanResponse(
        days = listOf(
            AiDay(
                dayIndex = 0,
                meals = listOf(
                    AiMeal(
                        name = "Reggeli tál",
                        ingredients = listOf(AiIngredient(name = ingredient, quantity = 100.0, unit = "g")),
                    )
                ),
            )
        )
    )
}
