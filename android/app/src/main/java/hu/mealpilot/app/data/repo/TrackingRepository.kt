package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealDao
import hu.mealpilot.app.data.local.MealLogDao
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.WeightLogDao
import hu.mealpilot.app.data.local.WeightLogEntity
import hu.mealpilot.core.model.Nutrients
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Étkezés- és súlynapló. */
class TrackingRepository(
    private val mealDao: MealDao,
    private val mealLogDao: MealLogDao,
    private val weightLogDao: WeightLogDao,
) {

    fun observeMealLogs(date: LocalDate): Flow<List<MealLogEntity>> =
        mealLogDao.observeDay(date.toEpochDay())

    fun observeWeights(): Flow<List<WeightLogEntity>> = weightLogDao.observeAll()

    /** Egy tervezett étkezés megevettként naplózása. Ugyanaz az étkezés csak egyszer számít. */
    suspend fun logPlannedMeal(mealId: Long, status: LogStatus, note: String = ""): Boolean {
        val meal = mealDao.byId(mealId) ?: return false
        mealLogDao.deleteForMeal(mealId)
        mealLogDao.insert(
            MealLogEntity(
                mealId = mealId,
                planId = meal.planId,
                epochDay = meal.epochDay,
                loggedAtMillis = System.currentTimeMillis(),
                status = status.name,
                name = meal.name,
                nutrients = if (status == LogStatus.EATEN) meal.nutrients else NutrientsColumns(),
            )
        )
        return true
    }

    /**
     * A tervezett étkezés helyett mást evett a felhasználó.
     *
     * A tervet nem írjuk át — az étkezés a helyén marad, csak a naplóba az kerül, ami
     * tényleg megtörtént. Így a napi összesítő pontos lesz anélkül, hogy bármit újra
     * kellene terveztetni.
     */
    suspend fun logReplacedMeal(
        mealId: Long,
        name: String,
        nutrients: Nutrients,
        note: String = "",
    ): Boolean {
        val meal = mealDao.byId(mealId) ?: return false
        mealLogDao.deleteForMeal(mealId)
        mealLogDao.insert(
            MealLogEntity(
                mealId = mealId,
                planId = meal.planId,
                epochDay = meal.epochDay,
                loggedAtMillis = System.currentTimeMillis(),
                status = LogStatus.REPLACED.name,
                name = name.ifBlank { meal.name },
                nutrients = NutrientsColumns.from(nutrients),
                note = note,
            )
        )
        return true
    }

    /** Terven kívüli étkezés kézi felvitele. */
    suspend fun logCustomMeal(date: LocalDate, name: String, nutrients: Nutrients, note: String = "") {
        mealLogDao.insert(
            MealLogEntity(
                mealId = null,
                planId = null,
                epochDay = date.toEpochDay(),
                loggedAtMillis = System.currentTimeMillis(),
                status = LogStatus.EXTRA.name,
                name = name,
                nutrients = NutrientsColumns.from(nutrients),
                note = note,
            )
        )
    }

    suspend fun deleteMealLog(id: Long) = mealLogDao.delete(id)


    suspend fun logWeight(date: LocalDate, weightKg: Double, bodyFatPercent: Double?, note: String = "") {
        weightLogDao.upsert(
            WeightLogEntity(
                epochDay = date.toEpochDay(),
                weightKg = weightKg,
                bodyFatPercent = bodyFatPercent,
                note = note,
            )
        )
    }

    suspend fun deleteWeight(date: LocalDate) = weightLogDao.delete(date.toEpochDay())

    suspend fun latestWeight(): WeightLogEntity? = weightLogDao.latest()

    suspend fun allMealLogs(): List<MealLogEntity> = mealLogDao.all()
    suspend fun allWeights(): List<WeightLogEntity> = weightLogDao.all()
}
