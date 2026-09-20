package hu.mealpilot.core

import hu.mealpilot.core.ai.PlanPrompts
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kétszer főzés: eljut-e a tervezőhöz, és nem oltja-e ki az ismétlést tiltó lista.
 *
 * Ugyanaz a csapda, mint a kedvenceknél. A hosszú terveket hetekre bontjuk, és minden
 * további hét megkapja az addig felhasznált fogások nevét „ezeket ne ismételd"
 * felirattal. A hét UTOLSÓ napjának maradéka így a következő hét első napjára soha nem
 * jöhetne át — a kapcsoló bekapcsolva is csendben félig működne.
 */
class BatchCookingPromptTest {

    private fun prompt(
        batchCooking: Boolean,
        avoid: List<String> = emptyList(),
        language: AppLanguage = AppLanguage.HU,
    ): String {
        val profile = UserProfile(batchCooking = batchCooking)
        return PlanPrompts.userPrompt(
            PlanRequest(
                profile = profile,
                budget = EnergyCalculator.budget(profile, language),
                days = 7,
                startDayIndex = 7,
                totalDays = 14,
                avoidRecipes = avoid,
            ),
            language,
        )
    }

    @Test
    fun `the rule reaches the planner in both languages`() {
        val hu = prompt(batchCooking = true)
        assertTrue("Legyen fejléc: $hu", hu.contains("KÉTSZER FŐZÉS"))
        assertTrue("Mondja meg, mennyi az újramelegítés", hu.contains("prep_minutes"))
        assertTrue("Mondja meg, mi legyen a hozzávalókkal", hu.contains("bevásárlólista"))

        val en = prompt(batchCooking = true, language = AppLanguage.EN)
        assertTrue("Legyen angol fejléc: $en", en.contains("COOK ONCE, EAT TWICE"))
        assertTrue(en.contains("prep_minutes"))
    }

    @Test
    fun `the do-not-repeat list gets an explicit exception for leftovers`() {
        val text = prompt(batchCooking = true, avoid = listOf("Gombás rizottó"))
        val avoidBlock = text.substringAfter("MÁR SZEREPELT FOGÁSOK")
        assertTrue("Az ismétlést tiltó blokknak meg kell lennie", avoidBlock.isNotBlank())
        assertTrue(
            "A maradék kivételét ki kell mondani, különben a heteken át nem jön át: $avoidBlock",
            avoidBlock.contains("Kivétel") && avoidBlock.contains("maradék"),
        )
    }

    @Test
    fun `switched off, nothing changes`() {
        val text = prompt(batchCooking = false, avoid = listOf("Gombás rizottó"))
        assertFalse(text.contains("KÉTSZER FŐZÉS"))
        assertFalse("Kikapcsolva ne lazítsuk az ismétlés tiltását", text.contains("Kivétel"))
        assertTrue(text.contains("MÁR SZEREPELT FOGÁSOK"))
    }

    @Test
    fun `the default profile does not turn it on by itself`() {
        assertFalse("Ez a felhasználó döntése", UserProfile().batchCooking)
    }
}
