package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.local.AchievementDao
import hu.mealpilot.app.data.local.AchievementEntity
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.PlanDao
import hu.mealpilot.app.data.local.ShoppingDao
import hu.mealpilot.core.achievements.Achievement
import hu.mealpilot.core.achievements.AchievementEngine
import hu.mealpilot.core.achievements.AchievementState
import hu.mealpilot.core.achievements.AchievementStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

/**
 * Az achievementekhez szükséges statisztikák kiszámítása a naplókból.
 * A szabályok a :core modulban élnek, itt csak az adatok összegyűjtése történik.
 */
class StatsRepository(
    private val tracking: TrackingRepository,
    private val planDao: PlanDao,
    private val shoppingDao: ShoppingDao,
    private val achievementDao: AchievementDao,
) {

    fun observeUnlocked(): Flow<List<AchievementEntity>> = achievementDao.observeAll()

    /**
     * Az összes napló átnézése — ezért fut háttérszálon.
     *
     * A hívások a felületről jönnek (`viewModelScope`), ami a FŐ szálon folytatódik a
     * lekérdezés után. A Room maga háttérre teszi az olvasást, a csoportosítás, a
     * rendezés és a sorozatszámolás viszont a folytatás szálán futna — vagyis a
     * felületen. Ez a munka a napló hosszával nő: fél év után minden bevásárlólista-
     * pipa több ezer soron menne végig, a felhasználó szeme előtt.
     */
    suspend fun computeStats(
        targetKcal: Int,
        targetProteinG: Int,
    ): AchievementStats = withContext(Dispatchers.Default) {
        val mealLogs = tracking.allMealLogs()
        val weights = tracking.allWeights()

        val eatenByDay = mealLogs
            .filter {
                it.status == LogStatus.EATEN.name ||
                    it.status == LogStatus.REPLACED.name ||
                    it.status == LogStatus.EXTRA.name
            }
            .groupBy { it.epochDay }

        val loggedDays = eatenByDay.keys.sorted()

        val onTargetDays = eatenByDay.filter { (_, logs) ->
            val kcal = logs.sumOf { it.nutrients.kcal }
            targetKcal > 0 && abs(kcal - targetKcal) <= targetKcal * 0.10
        }.keys.sorted()

        val proteinDays = eatenByDay.count { (_, logs) ->
            targetProteinG > 0 && logs.sumOf { it.nutrients.proteinG } >= targetProteinG * 0.9
        }

        val startWeight = weights.minByOrNull { it.epochDay }?.weightKg
        val latestWeight = weights.maxByOrNull { it.epochDay }?.weightKg
        val kgLost = if (startWeight != null && latestWeight != null) startWeight - latestWeight else 0.0

        AchievementStats(
            daysLogged = loggedDays.size,
            currentLogStreak = currentStreak(loggedDays),
            longestLogStreak = longestStreak(loggedDays),
            daysOnTarget = onTargetDays.size,
            currentOnTargetStreak = currentStreak(onTargetDays),
            proteinGoalDays = proteinDays,
            weightEntries = weights.size,
            kgLost = kgLost,
            plansGenerated = planDao.count(),
            shoppingListsCompleted = shoppingDao.completedListCount(),
            distinctRecipesEaten = mealLogs
                .filter { it.status == LogStatus.EATEN.name || it.status == LogStatus.REPLACED.name }
                .map { it.name.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct().size,
        )
    }

    suspend fun evaluate(targetKcal: Int, targetProteinG: Int): List<AchievementState> =
        AchievementEngine.evaluate(computeStats(targetKcal, targetProteinG))

    /**
     * Kiértékeli az achievementeket, elmenti az újakat, és visszaadja azokat,
     * amikről még nem szólt az app.
     */
    suspend fun refreshAndCollectNew(targetKcal: Int, targetProteinG: Int): List<Achievement> {
        val stats = computeStats(targetKcal, targetProteinG)
        val already = achievementDao.unlockedKeys().toSet()
        val fresh = AchievementEngine.newlyUnlocked(stats, already)
        val now = System.currentTimeMillis()
        fresh.forEach { achievementDao.insert(AchievementEntity(it.key, now, notified = false)) }
        return fresh
    }

    suspend fun markNotified(key: String) = achievementDao.markNotified(key)

    /** A ma (vagy tegnap) záruló, megszakítás nélküli sorozat hossza. */
    private fun currentStreak(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        val today = LocalDate.now().toEpochDay()
        val set = days.toSet()
        // A ma még folyamatban lévő nap miatt tegnaptól is indulhat a sorozat.
        var cursor = if (today in set) today else if (today - 1 in set) today - 1 else return 0
        var streak = 0
        while (cursor in set) {
            streak++
            cursor--
        }
        return streak
    }

    private fun longestStreak(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] == days[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

}
