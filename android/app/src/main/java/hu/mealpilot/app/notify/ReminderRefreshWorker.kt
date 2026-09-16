package hu.mealpilot.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hu.mealpilot.app.appContainer
import java.util.concurrent.TimeUnit

/**
 * Rendszeresen újratölti az ébresztéssort és a napi összefoglalót.
 *
 * Erre azért van szükség, mert egyszerre csak 36 órára időzítünk előre, és mert a rendszer
 * egy alkalmazás-frissítés, időzóna-váltás vagy erőforrás-takarékos leállítás után eldobhatja
 * a beállított ébresztéseket.
 */
class ReminderRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ReminderCoordinator.refreshAll(applicationContext)
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "reminder_refresh_periodic"
        private const val ONE_SHOT_NAME = "reminder_refresh_now"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderRefreshWorker>(8, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderRefreshWorker>().build(),
            )
        }
    }
}

/** Egy helyen dönti el, mit kell éppen időzíteni. */
object ReminderCoordinator {

    suspend fun refreshAll(context: Context) {
        val container = context.appContainer
        val settings = container.settings.currentSettings()

        if (settings.remindersEnabled) {
            val now = System.currentTimeMillis()
            val meals = container.database.mealDao()
                .scheduledBetween(now, now + MealAlarmScheduler.HORIZON_MILLIS)
            MealAlarmScheduler.syncMeals(context, meals, settings.reminderLeadMinutes)
        } else {
            // A kikapcsolás a MÁR kitett ébresztőkre is vonatkozik. Enélkül a
            // felhasználó a kapcsoló átbillentése után még 36 órán át kapott
            // emlékeztetőket — pontosan az a fajta hiba, amitől az ember letörli
            // az appot, és közben biztos benne, hogy jól állította be.
            MealAlarmScheduler.cancelAllMeals(context)
        }

        if (settings.dailySummaryEnabled) {
            MealAlarmScheduler.scheduleDailySummary(context, settings.dailySummaryHour)
        } else {
            MealAlarmScheduler.cancelDailySummary(context)
        }
    }
}
