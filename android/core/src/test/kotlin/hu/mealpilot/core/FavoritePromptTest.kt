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
 * A kedvencek eljutnak-e a tervezőhöz — és nem oltja-e ki őket az ismétlést tiltó lista.
 *
 * Ez a két lista egymás ellen dolgozik, és a konfliktus némán dől el. A hosszú terveket
 * hetekre bontjuk, és minden további hét megkapja az addig felhasznált fogások nevét
 * „ezeket ne ismételd" felirattal. Egy kedvenc, ami az első héten szerepelt, a másodikba
 * így SOHA nem kerülhetne be — pedig a felhasználó épp azért jelölte meg, hogy újra
 * megkapja. A szív ikon ettől nem hibásodna meg látványosan: egyszerűen nem csinálna
 * semmit, és senki nem tudná megmondani, miért.
 */
class FavoritePromptTest {

    private fun prompt(
        favorites: List<String>,
        avoid: List<String> = emptyList(),
        language: AppLanguage = AppLanguage.HU,
    ): String {
        val profile = UserProfile()
        return PlanPrompts.userPrompt(
            PlanRequest(
                profile = profile,
                budget = EnergyCalculator.budget(profile, language),
                days = 7,
                startDayIndex = 7,
                totalDays = 14,
                avoidRecipes = avoid,
                favoriteRecipes = favorites,
            ),
            language,
        )
    }

    @Test
    fun `the favourites reach the planner in both languages`() {
        val hu = prompt(listOf("Shakshuka", "Chana masala"))
        assertTrue("Legyen fejléc: $hu", hu.contains("KEDVENCEI"))
        assertTrue(hu.contains("Shakshuka"))
        assertTrue(hu.contains("Chana masala"))

        val en = prompt(listOf("Shakshuka"), language = AppLanguage.EN)
        assertTrue("Legyen angol fejléc: $en", en.contains("FAVOURITES"))
        assertTrue(en.contains("Shakshuka"))
    }

    @Test
    fun `a favourite is not silently cancelled by the do-not-repeat list`() {
        // A második hét promptja: a Shakshuka már szerepelt, DE kedvenc is.
        val text = prompt(
            favorites = listOf("Shakshuka"),
            avoid = listOf("Shakshuka", "Gombás rizottó"),
        )
        val avoidBlock = text.substringAfter("MÁR SZEREPELT FOGÁSOK", "")
        assertTrue("Az ismétlést tiltó blokknak meg kell lennie: $text", avoidBlock.isNotBlank())
        assertFalse(
            "A kedvenc nem kerülhet a tiltólistára: $avoidBlock",
            avoidBlock.lineSequence().take(2).any { it.contains("Shakshuka") },
        )
        assertTrue("A többi tiltás maradjon: $avoidBlock", avoidBlock.contains("Gombás rizottó"))
    }

    @Test
    fun `the match ignores case`() {
        val text = prompt(favorites = listOf("shakshuka"), avoid = listOf("Shakshuka"))
        // Ha a tiltólistán csak ez az egy név volt, a blokknak el kell tűnnie.
        assertFalse("Üres tiltólistát ne írjunk ki: $text", text.contains("MÁR SZEREPELT FOGÁSOK"))
    }

    @Test
    fun `without favourites nothing changes`() {
        val text = prompt(favorites = emptyList(), avoid = listOf("Gombás rizottó"))
        assertFalse(text.contains("KEDVENCEI"))
        assertTrue(text.contains("MÁR SZEREPELT FOGÁSOK"))
        assertTrue(text.contains("Gombás rizottó"))
    }
}
