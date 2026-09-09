package hu.mealpilot.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import hu.mealpilot.app.data.local.MealEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Az étkezési emlékeztetők időzítése.
 *
 * Az ébresztéseket előre 36 órára tesszük ki, és egy háttérmunka rendszeresen újratölti őket
 * ([ReminderRefreshWorker]). Így nem kell több száz ébresztést fenntartani egy hónapos tervhez,
 * viszont a telefon kikapcsolt állapotában eltelt idő után is helyreáll a sor.
 */
object MealAlarmScheduler {

    private const val TAG = "MealAlarmScheduler"

    /** Meddig előre időzítünk egyszerre. */
    val HORIZON_MILLIS: Long = TimeUnit.HOURS.toMillis(36)

    /** A pontos ébresztés hiányában ekkora ablakban szólal meg az emlékeztető. */
    private val INEXACT_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(10)

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    }

    fun scheduleMeals(context: Context, meals: List<MealEntity>, leadMinutes: Int) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val exact = canScheduleExact(context)
        val now = System.currentTimeMillis()

        for (meal in meals) {
            val triggerAt = meal.scheduledAtMillis - TimeUnit.MINUTES.toMillis(leadMinutes.toLong())
            if (triggerAt <= now) continue
            val pendingIntent = reminderIntent(context, meal.id)
            try {
                if (exact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        INEXACT_WINDOW_MILLIS,
                        pendingIntent,
                    )
                }
            } catch (security: SecurityException) {
                // A pontos ébresztés engedélye menet közben is visszavonható.
                Log.w(TAG, "Pontos ébresztés megtagadva, ablakos ébresztésre váltok.", security)
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MILLIS, pendingIntent,
                )
            }
        }
    }

    fun cancelMeal(context: Context, mealId: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(reminderIntent(context, mealId))
    }

    /** Napi összefoglaló a megadott órában — mindig a következő előfordulásra. */
    fun scheduleDailySummary(context: Context, hour: Int) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var trigger = LocalDate.now(zone).atTime(LocalTime.of(hour.coerceIn(0, 23), 0))
            .atZone(zone).toInstant().toEpochMilli()
        if (trigger <= now) trigger += TimeUnit.DAYS.toMillis(1)

        val pendingIntent = summaryIntent(context)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } catch (security: SecurityException) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, trigger, TimeUnit.MINUTES.toMillis(30), pendingIntent,
            )
        }
    }

    fun cancelDailySummary(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(summaryIntent(context))
    }

    private fun reminderIntent(context: Context, mealId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            Notifications.mealNotificationId(mealId),
            Intent(context, MealReminderReceiver::class.java).apply {
                action = MealReminderReceiver.ACTION_REMIND
                putExtra(MealReminderReceiver.EXTRA_MEAL_ID, mealId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun summaryIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            Notifications.ID_DAILY_SUMMARY,
            Intent(context, MealReminderReceiver::class.java).apply {
                action = MealReminderReceiver.ACTION_DAILY_SUMMARY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
