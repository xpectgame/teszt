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

    // -------------------------------------------------------------------------
    // Fehérje: a csere a kalóriát tartja, de a nap fehérjecélját is tartania kell
    // -------------------------------------------------------------------------

    @Test
    fun `the swap does not quietly halve the protein`() {
        // Egy 600 kcal-s, 50 g fehérjés főétel helyére nem jöhet olyan, ami
        // ugyanazon a kalórián a fehérje felét hozza. A napi összeg így esne ki
        // abból a sávból, amit a modell tervein a PlanValidator számon kér.
        val template = MealSwap.next(
            slot = MealSlot.LUNCH,
            currentName = "Valami egészen más fogás",
            currentKcal = 600.0,
            currentProteinG = 50.0,
            language = AppLanguage.HU,
        )
        assertNotNull(template)
        val scaled = template!!.nutrition.proteinG * template.scaleFactorFor(600.0)
        assertTrue(
            "Csak ${scaled.toInt()} g fehérje maradt 50 g helyett (${template.name.get(AppLanguage.HU)})",
            scaled >= 50.0 * MealSwap.MIN_PROTEIN_RATIO,
        )
    }

    @Test
    fun `the protein floor holds for every dish in the bank`() {
        // Nem egy szerencsés esetet mérünk: MINDEN főétel cseréjének tartania kell.
        for (language in AppLanguage.entries) {
            for (current in RecipeBank.forSlot(MealSlot.LUNCH)) {
                val kcal = current.nutrition.kcal
                val protein = current.nutrition.proteinG
                val replacement = MealSwap.next(
                    slot = MealSlot.LUNCH,
                    currentName = current.name.get(language),
                    currentKcal = kcal,
                    currentProteinG = protein,
                    language = language,
                )
                assertNotNull(replacement)
                val scaled = replacement!!.nutrition.proteinG * replacement.scaleFactorFor(kcal)
                assertTrue(
                    "${current.name.get(language)} → ${replacement.name.get(language)} " +
                        "($language): ${scaled.toInt()} g fehérje ${protein.toInt()} g helyett",
                    scaled >= protein * MealSwap.MIN_PROTEIN_RATIO,
                )
            }
        }
    }

    @Test
    fun `a weak swap beats a button that does nothing`() {
        // Ha a kizárások miatt EGY sablon sem tartja a fehérjekorlátot, akkor sem
        // hibázunk le: a legtöbb fehérjét hozó biztonságos sablon jön. Egy kicsit
        // gyengébb csere még mindig jobb, mint egy gomb, ami nem csinál semmit.
        val absurd = MealSwap.next(
            slot = MealSlot.AFTERNOON_SNACK,
            currentName = "Nem létező fogás",
            currentKcal = 200.0,
            currentProteinG = 900.0,
            language = AppLanguage.HU,
        )
        assertNotNull("Inkább gyengébb csere, mint semmi", absurd)
    }

    @Test
    fun `without a protein reference the rotation is unchanged`() {
        // A régi viselkedés megmarad ott, ahol nincs mihez mérni: a soron következő
        // biztonságos sablon jön, nem a legfehérjésebb.
        val mains = RecipeBank.forSlot(MealSlot.LUNCH)
        val current = mains[0].name.get(AppLanguage.HU)
        val next = MealSwap.next(MealSlot.LUNCH, current, language = AppLanguage.HU)
        assertNotNull(next)
        assertTrue(
            "A rotáció szerint a következő elem jöjjön: ${next!!.name.get(AppLanguage.HU)}",
            next.name.get(AppLanguage.HU) == mains[1].name.get(AppLanguage.HU),
        )
    }

}
