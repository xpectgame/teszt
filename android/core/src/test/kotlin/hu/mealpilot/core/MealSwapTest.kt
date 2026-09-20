package hu.mealpilot.core

import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.MealSwap
import hu.mealpilot.core.ai.RecipeBank
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fogáscsere választási szabálya.
 *
 * A három dolog, amit el lehet rontani, és amit a felhasználó azonnal észrevenne:
 * ugyanazt adni vissza, a nap másik fogását adni vissza, vagy olyat adni, amit a
 * felhasználó kizárt.
 */
class MealSwapTest {

    private fun nameOf(
        slot: MealSlot,
        current: String,
        avoid: List<String> = emptyList(),
        restrictions: Set<DietRestriction> = emptySet(),
        language: AppLanguage = AppLanguage.HU,
    ): String? = MealSwap.next(slot, current, avoid, restrictions, language)
        ?.name?.get(language)

    @Test
    fun `the swap never returns the same dish`() {
        for (language in AppLanguage.entries) {
            for (slot in listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.AFTERNOON_SNACK)) {
                for (template in RecipeBank.forSlot(slot)) {
                    val current = template.name.get(language)
                    val replacement = nameOf(slot, current, language = language)
                    assertNotNull("$current ($language): nincs csere", replacement)
                    assertNotEquals("$language: önmagára cserélt", current, replacement)
                }
            }
        }
    }

    @Test
    fun `the swap avoids the other dishes of the day`() {
        val mains = RecipeBank.forSlot(MealSlot.LUNCH).map { it.name.get(AppLanguage.HU) }
        val current = mains[0]
        // A nap többi fogása: a bank első feléből minden.
        val avoid = mains.drop(1).take(20)
        val replacement = nameOf(MealSlot.LUNCH, current, avoid)

        assertNotNull(replacement)
        assertTrue("A nap másik fogását adta vissza: $replacement", replacement !in avoid)
        assertNotEquals(current, replacement)
    }

    @Test
    fun `the swap respects the exclusions`() {
        val vegan = DietRestriction.impliedBy(DietStyle.VEGAN) + DietRestriction.GLUTEN
        for (language in AppLanguage.entries) {
            for (slot in listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.AFTERNOON_SNACK)) {
                val current = RecipeBank.forSlot(slot).first().name.get(language)
                val template = MealSwap.next(slot, current, emptyList(), vegan, language)
                assertNotNull("$slot ($language): egy vegán, gluténmentes csere sincs", template)
                assertTrue(
                    "$slot ($language): kizárt hozzávaló a cserében (${template!!.name.get(language)})",
                    RestrictionChecker.isSafe(
                        template.name.get(language),
                        template.ingredients.map { it.name.get(language) },
                        vegan,
                        language,
                    ),
                )
            }
        }
    }

    @Test
    fun `tapping again keeps moving through the bank`() {
        // Ismételt csere ne ugyanazt a párost dobálja oda-vissza.
        var current = RecipeBank.forSlot(MealSlot.LUNCH).first().name.get(AppLanguage.HU)
        val seen = mutableListOf(current)
        repeat(5) {
            current = nameOf(MealSlot.LUNCH, current)!!
            assertTrue("Ismétlés az $it. csere után: $current", current !in seen)
            seen += current
        }
    }

    @Test
    fun `a dish the model wrote can also be swapped`() {
        // A tervek nagy része MODELLTŐL jön, tehát a mostani fogás nincs a bankban.
        // A csere ilyenkor is működjön, és ugyanarra a névre mindig ugyanazt adja.
        val first = nameOf(MealSlot.DINNER, "Csirkés cézársaláta pirított magvakkal")
        val second = nameOf(MealSlot.DINNER, "Csirkés cézársaláta pirított magvakkal")
        assertNotNull(first)
        assertTrue("Ugyanarra a névre ugyanaz jöjjön", first == second)
    }

    @Test
    fun `nothing fits is a real answer, not a wrong dish`() {
        // Ha a nap többi fogása MINDENT lefed, nincs mit ajánlani. Ilyenkor a null a
        // helyes válasz — a felületnek ezt kell megmondania, nem kitalálnia valamit.
        val snacks = RecipeBank.forSlot(MealSlot.AFTERNOON_SNACK).map { it.name.get(AppLanguage.HU) }
        assertNull(MealSwap.next(MealSlot.AFTERNOON_SNACK, snacks.first(), snacks, emptySet(), AppLanguage.HU))
    }
}
