package hu.mealpilot.core

import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.PlanPrompts
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.billing.Tiers
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.DietRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A nyelv itt nem felirat kérdése: az étrend NYELVE is ez, és a kizárás-ellenőrzés
 * kulcsszavai nyelvenként külön adatok. Ha az angol lista hiányozna vagy nem illeszkedne,
 * az allergiaszűrés angolul némán megszűnne működni — ezért ezek a tesztek.
 */
class LanguageTest {

    @Test
    fun `minden kizárásnak van angol szabálya és kulcsszava`() {
        DietRestriction.entries.forEach { r ->
            assertTrue("${r.name}: üres angol név", r.en.isNotBlank())
            assertTrue("${r.name}: üres angol szabály", r.ruleEn.isNotBlank())
            assertTrue("${r.name}: nincs angol kulcsszó", r.keywordsEn.isNotEmpty())
        }
    }

    @Test
    fun `az angol kulcsszavak a szó VÉGÉN is találnak`() {
        // Az angol összetett szó hátul hordozza a lényeget: a szóeleji egyezés
        // pont a glutént hagyná ki a „wholewheat bread"-ből.
        val hits = RestrictionChecker.violations(
            "wholewheat bread", setOf(DietRestriction.GLUTEN), AppLanguage.EN
        )
        assertEquals(1, hits.size)

        assertEquals(1, RestrictionChecker.violations(
            "buttermilk", setOf(DietRestriction.LACTOSE), AppLanguage.EN
        ).size)
    }

    @Test
    fun `a rövid kulcsszavak nem olvadnak bele más szavakba`() {
        // Ezek a valódi csapdák: a „ham" benne van a „chamomile"-ban, az „oat" a
        // „goat"-ban, az „egg" az „eggplant"-ben.
        assertTrue(RestrictionChecker.violations(
            "chamomile tea", setOf(DietRestriction.NO_PORK), AppLanguage.EN).isEmpty())
        assertTrue(RestrictionChecker.violations(
            "goat cheese", setOf(DietRestriction.GLUTEN), AppLanguage.EN).isEmpty())
        assertTrue(RestrictionChecker.violations(
            "eggplant", setOf(DietRestriction.EGG), AppLanguage.EN).isEmpty())

        // A valódi találatot viszont meg kell fognia, többes számban is.
        assertEquals(1, RestrictionChecker.violations(
            "eggs", setOf(DietRestriction.EGG), AppLanguage.EN).size)
        assertEquals(1, RestrictionChecker.violations(
            "cooked ham", setOf(DietRestriction.NO_PORK), AppLanguage.EN).size)
    }

    @Test
    fun `a kivételek a teljes néven is működnek`() {
        // A „buckwheat" gluténmentes, a „milk thistle" nem tejtermék.
        assertTrue(RestrictionChecker.violations(
            "buckwheat flour", setOf(DietRestriction.GLUTEN), AppLanguage.EN).isEmpty())
        assertTrue(RestrictionChecker.violations(
            "milk thistle tea", setOf(DietRestriction.LACTOSE), AppLanguage.EN).isEmpty())
    }

    @Test
    fun `a gluténmentes jelölés angolul is felment`() {
        assertTrue(RestrictionChecker.violations(
            "gluten-free pasta", setOf(DietRestriction.GLUTEN), AppLanguage.EN).isEmpty())
        assertTrue(RestrictionChecker.violations(
            "oat milk", setOf(DietRestriction.LACTOSE), AppLanguage.EN).isEmpty())
    }

    @Test
    fun `a magyar illesztés változatlanul a szó ELEJÉN keres`() {
        assertEquals(1, RestrictionChecker.violations(
            "búzaliszt", setOf(DietRestriction.GLUTEN), AppLanguage.HU).size)
        assertEquals(1, RestrictionChecker.violations(
            "tejföl", setOf(DietRestriction.LACTOSE), AppLanguage.HU).size)
    }

    @Test
    fun `a rendszerpromptok nyelvenként mások és nem üresek`() {
        assertFalse(PlanPrompts.system(AppLanguage.HU) == PlanPrompts.system(AppLanguage.EN))
        assertFalse(ChatPrompts.system(AppLanguage.HU) == ChatPrompts.system(AppLanguage.EN))
        assertTrue(PlanPrompts.SYSTEM_EN.contains("EXCLUSIONS"))
        assertTrue(ChatPrompts.SYSTEM_EN.contains("SET_MEAL_TIMES"))
    }

    @Test
    fun `a csomagszövegek mindkét nyelven megvannak`() {
        AppLanguage.entries.forEach { lang ->
            assertTrue(Tiers.freeBenefits(lang).isNotEmpty())
            assertTrue(Tiers.premiumBenefits(lang).isNotEmpty())
            assertTrue(Tiers.freeBenefits(lang).none { it.isBlank() })
        }
        assertFalse(Tiers.premiumBenefits(AppLanguage.HU) == Tiers.premiumBenefits(AppLanguage.EN))
    }

    @Test
    fun `a rendszernyelvből ismeretlen esetben angol lesz`() {
        assertEquals(AppLanguage.HU, AppLanguage.fromSystemTag("hu-HU"))
        assertEquals(AppLanguage.EN, AppLanguage.fromSystemTag("en-GB"))
        assertEquals(AppLanguage.EN, AppLanguage.fromSystemTag("de-DE"))
        assertEquals(AppLanguage.EN, AppLanguage.fromSystemTag(null))
        // A tárolt beállításnál viszont a magyar az alapértelmezés.
        assertEquals(AppLanguage.HU, AppLanguage.fromTag(null))
    }

    @Test
    fun `az enum címkék mindkét nyelven kitöltöttek`() {
        DietRestriction.entries.forEach {
            assertTrue(it.label(AppLanguage.EN).isNotBlank())
            assertTrue(it.label(AppLanguage.HU).isNotBlank())
        }
    }
}
