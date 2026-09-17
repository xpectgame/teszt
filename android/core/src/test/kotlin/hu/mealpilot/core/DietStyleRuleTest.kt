package hu.mealpilot.core

import hu.mealpilot.core.ai.PlanPrompts
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietStyle
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Az étrendi stílus eddig puszta CÍMKEKÉNT ment a promptba: a modell annyit látott,
 * hogy „Vegán", a többit neki kellett kitalálnia.
 *
 * A gépi ellenőrzés kizárásokat is származtat a stílusból, de vannak dolgok, amiket
 * kulcsszóval nem lehet elkapni. A méz a legjobb példa: a vegán étrendből kimarad, de
 * nincs olyan allergia-kizárás, ami lefedné — a fruktózos lista elkapja ugyan, de azt
 * egy vegán nem jelöli be. Amit a szűrő nem tud megfogni, azt legalább ki kell mondani.
 */
class DietStyleRuleTest {

    private fun prompt(style: DietStyle, language: AppLanguage = AppLanguage.HU): String {
        val profile = UserProfile(dietStyle = style)
        return PlanPrompts.userPrompt(
            PlanRequest(
                profile = profile,
                budget = EnergyCalculator.budget(profile, language),
                days = 3,
                startDayIndex = 0,
                totalDays = 3,
            ),
            language,
        )
    }

    @Test
    fun `the vegan prompt spells out honey`() {
        val hu = prompt(DietStyle.VEGAN)
        assertTrue("A méznek szerepelnie kell: $hu", hu.contains("MÉZ") || hu.contains("méz"))
        val en = prompt(DietStyle.VEGAN, AppLanguage.EN)
        assertTrue("Honey must be named: $en", en.contains("HONEY") || en.contains("honey"))
    }

    @Test
    fun `every restricting style carries a rule in both languages`() {
        for (style in listOf(DietStyle.VEGETARIAN, DietStyle.VEGAN, DietStyle.PESCATARIAN,
                             DietStyle.LOW_CARB, DietStyle.MEDITERRANEAN)) {
            assertTrue("$style magyar szabálya hiányzik", style.rule(AppLanguage.HU).isNotBlank())
            assertTrue("$style angol szabálya hiányzik", style.rule(AppLanguage.EN).isNotBlank())
            assertTrue("$style szabálya nem jut el a promptba", prompt(style).contains(style.rule(AppLanguage.HU)))
        }
    }

    @Test
    fun `the omnivore style adds no noise`() {
        // Aki nem korlátoz, annak ne kerüljön fölösleges sor a promptba.
        assertTrue(DietStyle.OMNIVORE.rule(AppLanguage.HU).isBlank())
    }
}
