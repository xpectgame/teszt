package hu.mealpilot.core.achievements

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.Localized
import hu.mealpilot.core.i18n.label

/**
 * Az achievementek kiértékeléséhez szükséges összesített statisztika.
 * Az adatbázisból számoljuk ki, az achievement-logika maga tiszta függvény marad.
 */
data class AchievementStats(
    /** Napok száma, ahol legalább egy étkezés naplózva lett. */
    val daysLogged: Int = 0,
    /** Aktuális, megszakítás nélküli naplózási sorozat napokban. */
    val currentLogStreak: Int = 0,
    val longestLogStreak: Int = 0,
    /** Napok, ahol a bevitt kalória a cél ±10%-án belül volt. */
    val daysOnTarget: Int = 0,
    val currentOnTargetStreak: Int = 0,
    /** Napok, ahol a fehérjecél teljesült. */
    val proteinGoalDays: Int = 0,
    val weightEntries: Int = 0,
    /** Az induló súlyhoz képest ledolgozott kilók (pozitív szám). */
    val kgLost: Double = 0.0,
    val plansGenerated: Int = 0,
    val shoppingListsCompleted: Int = 0,
    /** Különböző elkészített fogások száma. */
    val distinctRecipesEaten: Int = 0,
)

enum class AchievementTier(override val hu: String, override val en: String) : Localized {
    BRONZE("Bronz", "Bronze"), SILVER("Ezüst", "Silver"), GOLD("Arany", "Gold")
}

/**
 * A nyelvi mezők neve SZÁNDÉKOSAN `titleHu` és `titleEn`, nem `title` és `titleEn`.
 *
 * Amíg a magyar mező `title`-nek hívták, minden hívási hely simán kiírta — a
 * profilképernyő és az achievement-értesítés is —, és egy angolul használó
 * felhasználónak magyarul jelent meg, hogy „Fehérjebajnok". A hiba nem látszott:
 * a `title` mező létezik, fordul, és magyarul helyes szöveget ad.
 *
 * Így viszont a nyelv nélküli kiírás FORDÍTÁSI HIBA. A [title] és a [description]
 * függvényt kell hívni, ami nyelvet kér.
 */
data class Achievement(
    val key: String,
    val titleHu: String,
    val descriptionHu: String,
    val titleEn: String,
    val descriptionEn: String,
    val emoji: String,
    val tier: AchievementTier,
    val goal: Int,
    /** A jelenlegi állás kiolvasása a statisztikából (skálázott egész). */
    val progressOf: (AchievementStats) -> Int,
) : Localized {
    override val hu: String get() = titleHu
    override val en: String get() = titleEn

    fun title(language: AppLanguage): String = label(language)

    fun description(language: AppLanguage): String =
        if (language == AppLanguage.EN) descriptionEn else descriptionHu
}

data class AchievementState(
    val achievement: Achievement,
    val progress: Int,
    val unlocked: Boolean,
) {
    val ratio: Float get() = if (achievement.goal <= 0) 1f else (progress.toFloat() / achievement.goal).coerceIn(0f, 1f)
}

object AchievementCatalog {

    val all: List<Achievement> = listOf(
        Achievement("first_plan", "Első terv", "Készíts el egy étrendet",
            "First plan", "Create a meal plan", "🗂️",
            AchievementTier.BRONZE, 1) { it.plansGenerated },
        Achievement("planner_5", "Tervezőmester", "Készíts 5 étrendet",
            "Planner", "Create 5 meal plans", "🧭",
            AchievementTier.SILVER, 5) { it.plansGenerated },

        Achievement("first_log", "Első falat", "Naplózd az első étkezésed",
            "First entry", "Log your first meal", "🍽️",
            AchievementTier.BRONZE, 1) { it.daysLogged },
        Achievement("streak_3", "Beindult", "3 nap egymás után naplózva",
            "Rolling", "3 days logged in a row", "🔥",
            AchievementTier.BRONZE, 3) { it.longestLogStreak },
        Achievement("streak_7", "Egy teljes hét", "7 nap egymás után naplózva",
            "A full week", "7 days logged in a row", "🔥",
            AchievementTier.SILVER, 7) { it.longestLogStreak },
        Achievement("streak_30", "Egy hónap kitartás", "30 nap egymás után naplózva",
            "A month of it", "30 days logged in a row", "🏆",
            AchievementTier.GOLD, 30) { it.longestLogStreak },

        Achievement("target_1", "Célon", "Egy nap a kalóriacélon belül",
            "On target", "One day inside your calorie goal", "🎯",
            AchievementTier.BRONZE, 1) { it.daysOnTarget },
        Achievement("target_10", "Pontos tíz", "10 nap a kalóriacélon belül",
            "Ten on the nose", "10 days inside your calorie goal", "🎯",
            AchievementTier.SILVER, 10) { it.daysOnTarget },
        Achievement("target_streak_7", "Hibátlan hét", "7 egymást követő nap a célon belül",
            "Flawless week", "7 days in a row inside your goal", "💎",
            AchievementTier.GOLD, 7) { it.currentOnTargetStreak },

        Achievement("protein_7", "Fehérjebajnok", "7 napon teljesült a fehérjecél",
            "Protein champion", "Protein goal hit on 7 days", "🥩",
            AchievementTier.SILVER, 7) { it.proteinGoalDays },

        Achievement("weigh_in_5", "Mérleg barátja", "5 súlymérés rögzítve",
            "Friend of the scales", "5 weigh-ins recorded", "⚖️",
            AchievementTier.BRONZE, 5) { it.weightEntries },
        Achievement("lost_1kg", "Első kiló", "1 kg ledolgozva",
            "First kilo", "1 kg down", "📉",
            AchievementTier.BRONZE, 10) { (it.kgLost * 10).toInt() },
        Achievement("lost_5kg", "Öt kiló", "5 kg ledolgozva",
            "Five kilos", "5 kg down", "📉",
            AchievementTier.SILVER, 50) { (it.kgLost * 10).toInt() },
        Achievement("lost_10kg", "Tíz kiló", "10 kg ledolgozva",
            "Ten kilos", "10 kg down", "🥇",
            AchievementTier.GOLD, 100) { (it.kgLost * 10).toInt() },

        Achievement("shopping_3", "Bevásárló", "3 bevásárlólista kipipálva",
            "Shopper", "3 shopping lists ticked off", "🛒",
            AchievementTier.BRONZE, 3) { it.shoppingListsCompleted },
        Achievement("recipes_25", "Konyhatündér", "25 különböző fogás elkészítve",
            "Kitchen hand", "25 different dishes cooked", "👩‍🍳",
            AchievementTier.SILVER, 25) { it.distinctRecipesEaten },
        Achievement("recipes_60", "Séf", "60 különböző fogás elkészítve",
            "Chef", "60 different dishes cooked", "🍳",
            AchievementTier.GOLD, 60) { it.distinctRecipesEaten },
    )

    fun byKey(key: String): Achievement? = all.firstOrNull { it.key == key }
}

object AchievementEngine {

    fun evaluate(stats: AchievementStats): List<AchievementState> =
        AchievementCatalog.all.map { achievement ->
            val progress = achievement.progressOf(stats).coerceAtLeast(0)
            AchievementState(achievement, progress.coerceAtMost(achievement.goal), progress >= achievement.goal)
        }

    /**
     * A most megszerzett achievementek — azok, amik teljesültek, de még nincsenek
     * a korábban feloldottak között. Ezekre küldünk értesítést.
     */
    fun newlyUnlocked(stats: AchievementStats, alreadyUnlockedKeys: Set<String>): List<Achievement> =
        evaluate(stats).filter { it.unlocked && it.achievement.key !in alreadyUnlockedKeys }
            .map(AchievementState::achievement)
}
