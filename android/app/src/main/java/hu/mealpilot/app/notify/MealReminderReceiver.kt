package hu.mealpilot.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hu.mealpilot.app.MainActivity
import hu.mealpilot.app.R
import hu.mealpilot.app.appContainer
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.i18n.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Az étkezési emlékeztetőket megjelenítő és a gyors műveleteket kiszolgáló vevő.
 *
 * Az értesítésről egy koppintással naplózható az étkezés, így a nyomon követés nem
 * igényli az app megnyitását — ez az, ami napi szinten használhatóvá teszi a naplózást.
 */
class MealReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> showMealReminder(appContext, intent.getLongExtra(EXTRA_MEAL_ID, -1))
                    ACTION_ATE -> logAndDismiss(appContext, intent.getLongExtra(EXTRA_MEAL_ID, -1), LogStatus.EATEN)
                    ACTION_SKIP -> logAndDismiss(appContext, intent.getLongExtra(EXTRA_MEAL_ID, -1), LogStatus.SKIPPED)
                    ACTION_SNOOZE -> snooze(appContext, intent.getLongExtra(EXTRA_MEAL_ID, -1))
                    ACTION_DAILY_SUMMARY -> showDailySummary(appContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Az emlékeztető feldolgozása nem sikerült (${intent.action}).", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showMealReminder(context: Context, mealId: Long) {
        if (mealId <= 0) return
        val container = context.appContainer
        // A kitett ébresztőket a kikapcsolás visszavonja, de egy épp úton lévő
        // értesítést már nem lehet visszahívni. Ez a sor zárja azt a rést.
        if (!container.settings.currentSettings().remindersEnabled) return
        val meal = container.database.mealDao().byId(mealId) ?: return

        // Ha már naplózva lett (pl. korábban megette), ne emlékeztessünk rá újra.
        if (container.database.mealLogDao().eatenCountFor(mealId) > 0) return

        Notifications.ensureChannels(context)

        val slot = MealSlot.fromRaw(meal.slot)
        val n = meal.nutrients
        val strings = container.strings
        val details = strings[
            R.string.macro_line,
            n.kcal.roundToInt(),
            n.proteinG.roundToInt(),
            n.carbsG.roundToInt(),
            n.fatG.roundToInt(),
        ]

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_MEALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${slot.label(container.language)}: ${meal.name}")
            .setContentText(details)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(details)
                        if (meal.description.isNotBlank()) {
                            append("\n")
                            append(meal.description)
                        }
                        if (meal.prepMinutes > 0) {
                            append("\n" + strings.quantity(R.plurals.notif_prep_minutes, meal.prepMinutes, meal.prepMinutes))
                        }
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openMealIntent(context, meal))
            .addAction(0, strings[R.string.today_ate_it], actionIntent(context, ACTION_ATE, mealId))
            .addAction(0, strings[R.string.today_skipped], actionIntent(context, ACTION_SKIP, mealId))
            .addAction(0, strings[R.string.notif_snooze], actionIntent(context, ACTION_SNOOZE, mealId))
            .build()

        notify(context, Notifications.mealNotificationId(mealId), notification)
    }

    private suspend fun logAndDismiss(context: Context, mealId: Long, status: LogStatus) {
        if (mealId <= 0) return
        val container = context.appContainer
        container.trackingRepository.logPlannedMeal(mealId, status)
        NotificationManagerCompat.from(context).cancel(Notifications.mealNotificationId(mealId))

        // A naplózás új achievementet is feloldhat — erről rögtön szólunk.
        val plan = container.planRepository.activePlan() ?: return
        val fresh = container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
        // Az értesítés az app nyelvén szól, mint minden más szöveg. A `container.strings`
        // ugyanezt a nyelvet használja — itt a szöveg a katalógusból jön, nem az
        // erőforrásokból, ezért kell kézzel átadni.
        val language = container.language
        fresh.forEachIndexed { index, achievement ->
            val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ACHIEVEMENTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${achievement.emoji} ${achievement.title(language)}")
                .setContentText(achievement.description(language))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
            notify(context, Notifications.ID_ACHIEVEMENT_BASE + index, notification)
            container.statsRepository.markNotified(achievement.key)
        }
    }

    private suspend fun snooze(context: Context, mealId: Long) {
        if (mealId <= 0) return
        val meal = context.appContainer.database.mealDao().byId(mealId) ?: return
        NotificationManagerCompat.from(context).cancel(Notifications.mealNotificationId(mealId))
        MealAlarmScheduler.snoozeMeal(context, meal.id, System.currentTimeMillis() + SNOOZE_MILLIS)
    }

    private suspend fun showDailySummary(context: Context) {
        val container = context.appContainer
        // Ugyanaz a rés, mint az étkezési emlékeztetőnél: a kikapcsolás és a már
        // elsült ébresztő közé fér egy értesítés.
        if (!container.settings.currentSettings().dailySummaryEnabled) return
        val today = LocalDate.now()

        val plan = container.planRepository.activePlan()
        val eaten = container.trackingRepository.allMealLogs()
            .filter { it.epochDay == today.toEpochDay() && it.status != LogStatus.SKIPPED.name }
        val consumed = eaten.sumOf { it.nutrients.kcal }.roundToInt()
        val protein = eaten.sumOf { it.nutrients.proteinG }.roundToInt()
        val target = plan?.targetKcal ?: 0

        val strings = container.strings
        val text = if (target > 0) {
            strings[R.string.notif_summary_text, consumed, target, protein]
        } else {
            strings[R.string.notif_summary_text_no_target, consumed, protein]
        }

        val title = when {
            target <= 0 -> strings[R.string.notif_summary_title]
            consumed == 0 -> strings[R.string.notif_summary_nothing]
            consumed <= target -> strings[R.string.notif_summary_within]
            else -> strings[R.string.notif_summary_over, consumed - target]
        }

        Notifications.ensureChannels(context)
        notify(
            context,
            Notifications.ID_DAILY_SUMMARY,
            NotificationCompat.Builder(context, Notifications.CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build(),
        )

        // A következő napra is ki kell tenni az összefoglalót és a friss ébresztéseket.
        ReminderCoordinator.refreshAll(context)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (security: SecurityException) {
            // Az értesítési engedély futás közben is visszavonható.
            Log.w(TAG, "Nincs értesítési engedély.", security)
        }
    }

    private fun actionIntent(context: Context, action: String, mealId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action.hashCode() xor Notifications.mealNotificationId(mealId)),
            Intent(context, MealReminderReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_MEAL_ID, mealId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openMealIntent(context: Context, meal: MealEntity): PendingIntent =
        PendingIntent.getActivity(
            context,
            Notifications.mealNotificationId(meal.id),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_MEAL_ID, meal.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "MealReminderReceiver"

        const val ACTION_REMIND = "hu.mealpilot.app.REMIND"
        const val ACTION_ATE = "hu.mealpilot.app.ATE"
        const val ACTION_SKIP = "hu.mealpilot.app.SKIP"
        const val ACTION_SNOOZE = "hu.mealpilot.app.SNOOZE"
        const val ACTION_DAILY_SUMMARY = "hu.mealpilot.app.DAILY_SUMMARY"

        const val EXTRA_MEAL_ID = "meal_id"

        private val SNOOZE_MILLIS = TimeUnit.MINUTES.toMillis(15)
    }
}
