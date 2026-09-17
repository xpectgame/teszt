package hu.mealpilot.core

import hu.mealpilot.core.achievements.AchievementCatalog
import hu.mealpilot.core.achievements.AchievementEngine
import hu.mealpilot.core.achievements.AchievementStats
import hu.mealpilot.core.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEngineTest {

    @Test
    fun `nothing is unlocked on an empty account`() {
        val states = AchievementEngine.evaluate(AchievementStats())
        assertEquals(AchievementCatalog.all.size, states.size)
        assertTrue(states.none { it.unlocked })
        assertTrue(states.all { it.ratio == 0f })
    }

    @Test
    fun `streak achievements unlock at their thresholds`() {
        val states = AchievementEngine.evaluate(AchievementStats(longestLogStreak = 7))
            .associateBy { it.achievement.key }
        assertTrue(states.getValue("streak_3").unlocked)
        assertTrue(states.getValue("streak_7").unlocked)
        assertFalse(states.getValue("streak_30").unlocked)
        assertEquals(7f / 30f, states.getValue("streak_30").ratio, 0.001f)
    }

    @Test
    fun `weight loss achievements use tenths of a kilo`() {
        val states = AchievementEngine.evaluate(AchievementStats(kgLost = 5.4))
            .associateBy { it.achievement.key }
        assertTrue(states.getValue("lost_1kg").unlocked)
        assertTrue(states.getValue("lost_5kg").unlocked)
        assertFalse(states.getValue("lost_10kg").unlocked)
        assertEquals(54, states.getValue("lost_10kg").progress)
    }

    @Test
    fun `progress is clamped to the goal`() {
        val state = AchievementEngine.evaluate(AchievementStats(distinctRecipesEaten = 900))
            .first { it.achievement.key == "recipes_25" }
        assertEquals(25, state.progress)
        assertEquals(1f, state.ratio, 0.0001f)
    }

    @Test
    fun `negative or reversed values never produce negative progress`() {
        val state = AchievementEngine.evaluate(AchievementStats(kgLost = -2.0))
            .first { it.achievement.key == "lost_1kg" }
        assertEquals(0, state.progress)
        assertFalse(state.unlocked)
    }

    @Test
    fun `newly unlocked skips the ones already granted`() {
        val stats = AchievementStats(plansGenerated = 1, daysLogged = 1, weightEntries = 5)
        val first = AchievementEngine.newlyUnlocked(stats, emptySet()).map { it.key }
        assertEquals(setOf("first_plan", "first_log", "weigh_in_5"), first.toSet())

        val second = AchievementEngine.newlyUnlocked(stats, first.toSet())
        assertTrue(second.isEmpty())
    }

    @Test
    fun `catalog keys are unique and lookupable`() {
        val keys = AchievementCatalog.all.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        keys.forEach { assertNotNull(AchievementCatalog.byKey(it)) }
        assertTrue(AchievementCatalog.all.all { it.goal > 0 })
    }

    /**
     * Az angol szövegek nem díszek: az app angolul is fut, és az achievementek
     * szövege nem az erőforrásokból jön, hanem innen. Ezek a mezők sokáig ott
     * voltak, csak épp senki nem olvasta őket — a felület és az értesítés is a
     * magyar mezőt írta ki, akárhogy állt a nyelv.
     */
    @Test
    fun `every achievement really is bilingual`() {
        AchievementCatalog.all.forEach { achievement ->
            val key = achievement.key
            assertTrue("$key: üres angol cím", achievement.titleEn.isNotBlank())
            assertTrue("$key: üres angol leírás", achievement.descriptionEn.isNotBlank())
            assertTrue("$key: üres magyar cím", achievement.titleHu.isNotBlank())
            assertTrue("$key: üres magyar leírás", achievement.descriptionHu.isNotBlank())
            // Az azonos szöveg majdnem biztosan másolás: a magyar maradt bent.
            assertNotEquals("$key: a két cím azonos", achievement.titleHu, achievement.titleEn)
            assertNotEquals(
                "$key: a két leírás azonos",
                achievement.descriptionHu,
                achievement.descriptionEn,
            )
        }
    }

    @Test
    fun `the language argument decides which text comes back`() {
        val protein = AchievementCatalog.byKey("protein_7")!!

        assertEquals("Fehérjebajnok", protein.title(AppLanguage.HU))
        assertEquals("Protein champion", protein.title(AppLanguage.EN))
        assertEquals("7 napon teljesült a fehérjecél", protein.description(AppLanguage.HU))
        assertEquals("Protein goal hit on 7 days", protein.description(AppLanguage.EN))
    }
}
