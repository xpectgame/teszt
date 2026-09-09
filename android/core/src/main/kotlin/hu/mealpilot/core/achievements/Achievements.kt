package hu.mealpilot.core.achievements

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
    val workouts: Int = 0,
    val activeMinutes: Int = 0,
    val currentWorkoutWeekStreak: Int = 0,
    val weightEntries: Int = 0,
    /** Az induló súlyhoz képest ledolgozott kilók (pozitív szám). */
    val kgLost: Double = 0.0,
    val plansGenerated: Int = 0,
    val shoppingListsCompleted: Int = 0,
    /** Különböző elkészített fogások száma. */
    val distinctRecipesEaten: Int = 0,
)

enum class AchievementTier(val hu: String) { BRONZE("Bronz"), SILVER("Ezüst"), GOLD("Arany") }

data class Achievement(
    val key: String,
    val title: String,
    val description: String,
    val emoji: String,
    val tier: AchievementTier,
    val goal: Int,
    /** A jelenlegi állás kiolvasása a statisztikából (skálázott egész). */
    val progressOf: (AchievementStats) -> Int,
)

data class AchievementState(
    val achievement: Achievement,
    val progress: Int,
    val unlocked: Boolean,
) {
    val ratio: Float get() = if (achievement.goal <= 0) 1f else (progress.toFloat() / achievement.goal).coerceIn(0f, 1f)
}

object AchievementCatalog {

    val all: List<Achievement> = listOf(
        Achievement("first_plan", "Első terv", "Készíts el egy étrendet", "🗂️",
            AchievementTier.BRONZE, 1) { it.plansGenerated },
        Achievement("planner_5", "Tervezőmester", "Készíts 5 étrendet", "🧭",
            AchievementTier.SILVER, 5) { it.plansGenerated },

        Achievement("first_log", "Első falat", "Naplózd az első étkezésed", "🍽️",
            AchievementTier.BRONZE, 1) { it.daysLogged },
        Achievement("streak_3", "Beindult", "3 nap egymás után naplózva", "🔥",
            AchievementTier.BRONZE, 3) { it.longestLogStreak },
        Achievement("streak_7", "Egy teljes hét", "7 nap egymás után naplózva", "🔥",
            AchievementTier.SILVER, 7) { it.longestLogStreak },
        Achievement("streak_30", "Egy hónap kitartás", "30 nap egymás után naplózva", "🏆",
            AchievementTier.GOLD, 30) { it.longestLogStreak },

        Achievement("target_1", "Célon", "Egy nap a kalóriacélon belül", "🎯",
            AchievementTier.BRONZE, 1) { it.daysOnTarget },
        Achievement("target_10", "Pontos tíz", "10 nap a kalóriacélon belül", "🎯",
            AchievementTier.SILVER, 10) { it.daysOnTarget },
        Achievement("target_streak_7", "Hibátlan hét", "7 egymást követő nap a célon belül", "💎",
            AchievementTier.GOLD, 7) { it.currentOnTargetStreak },

        Achievement("protein_7", "Fehérjebajnok", "7 napon teljesült a fehérjecél", "🥩",
            AchievementTier.SILVER, 7) { it.proteinGoalDays },

        Achievement("first_workout", "Mozgásba lendülve", "Naplózd az első edzésed", "👟",
            AchievementTier.BRONZE, 1) { it.workouts },
        Achievement("workouts_20", "Rendszeres", "20 naplózott edzés", "🏋️",
            AchievementTier.SILVER, 20) { it.workouts },
        Achievement("minutes_500", "500 perc mozgás", "Gyűjts össze 500 aktív percet", "⏱️",
            AchievementTier.SILVER, 500) { it.activeMinutes },
        Achievement("minutes_2000", "2000 perc mozgás", "Gyűjts össze 2000 aktív percet", "⚡",
            AchievementTier.GOLD, 2000) { it.activeMinutes },
        Achievement("workout_weeks_4", "Négy aktív hét", "4 egymást követő héten legalább 2 edzés", "📅",
            AchievementTier.GOLD, 4) { it.currentWorkoutWeekStreak },

        Achievement("weigh_in_5", "Mérleg barátja", "5 súlymérés rögzítve", "⚖️",
            AchievementTier.BRONZE, 5) { it.weightEntries },
        Achievement("lost_1kg", "Első kiló", "1 kg ledolgozva", "📉",
            AchievementTier.BRONZE, 10) { (it.kgLost * 10).toInt() },
        Achievement("lost_5kg", "Öt kiló", "5 kg ledolgozva", "📉",
            AchievementTier.SILVER, 50) { (it.kgLost * 10).toInt() },
        Achievement("lost_10kg", "Tíz kiló", "10 kg ledolgozva", "🥇",
            AchievementTier.GOLD, 100) { (it.kgLost * 10).toInt() },

        Achievement("shopping_3", "Bevásárló", "3 bevásárlólista kipipálva", "🛒",
            AchievementTier.BRONZE, 3) { it.shoppingListsCompleted },
        Achievement("recipes_25", "Konyhatündér", "25 különböző fogás elkészítve", "👩‍🍳",
            AchievementTier.SILVER, 25) { it.distinctRecipesEaten },
        Achievement("recipes_60", "Séf", "60 különböző fogás elkészítve", "🍳",
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
