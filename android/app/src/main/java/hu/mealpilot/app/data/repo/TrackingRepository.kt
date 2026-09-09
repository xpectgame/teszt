package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.local.ActivityLogDao
import hu.mealpilot.app.data.local.ActivityLogEntity
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealDao
import hu.mealpilot.app.data.local.MealLogDao
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.WeightLogDao
import hu.mealpilot.app.data.local.WeightLogEntity
import hu.mealpilot.core.energy.ExerciseCalculator
import hu.mealpilot.core.energy.MetTable
import hu.mealpilot.core.model.Nutrients
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Étkezés-, mozgás- és súlynapló. */
class TrackingRepository(
    private val mealDao: MealDao,
    private val mealLogDao: MealLogDao,
    private val activityLogDao: ActivityLogDao,
    private val weightLogDao: WeightLogDao,
) {

    fun observeMealLogs(date: LocalDate): Flow<List<MealLogEntity>> =
        mealLogDao.observeDay(date.toEpochDay())

    fun observeActivityLogs(date: LocalDate): Flow<List<ActivityLogEntity>> =
        activityLogDao.observeDay(date.toEpochDay())

    fun observeActivityRange(from: LocalDate, to: LocalDate): Flow<List<ActivityLogEntity>> =
        activityLogDao.observeRange(from.toEpochDay(), to.toEpochDay())

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

    /**
     * Mozgás naplózása. A kalóriabecslést a [ExerciseCalculator] adja: ha van átlagpulzus,
     * a pulzus alapú képlet fut, egyébként a felhasználó saját alapanyagcseréjére skálázott MET.
     */
    suspend fun logActivity(
        profile: UserProfile,
        date: LocalDate,
        exerciseKey: String,
        minutes: Int,
        avgHeartRate: Int? = null,
        note: String = "",
    ): ExerciseCalculator.Result? {
        val exercise = MetTable.byKey(exerciseKey) ?: return null
        val result = ExerciseCalculator.estimate(profile, exercise, minutes, avgHeartRate)
        activityLogDao.insert(
            ActivityLogEntity(
                epochDay = date.toEpochDay(),
                loggedAtMillis = System.currentTimeMillis(),
                exerciseKey = exercise.key,
                label = exercise.hu,
                minutes = minutes,
                met = result.met,
                avgHeartRate = avgHeartRate,
                kcalGross = result.kcalGross,
                kcalNet = result.kcalNet,
                method = result.method.name,
                note = note,
            )
        )
        return result
    }

    suspend fun deleteActivity(id: Long) = activityLogDao.delete(id)

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
    suspend fun allActivityLogs(): List<ActivityLogEntity> = activityLogDao.all()
    suspend fun allWeights(): List<WeightLogEntity> = weightLogDao.all()
}
