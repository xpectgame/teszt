package hu.mealpilot.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import hu.mealpilot.app.R

object Notifications {

    const val CHANNEL_MEALS = "meals"
    const val CHANNEL_SUMMARY = "daily_summary"
    const val CHANNEL_ACHIEVEMENTS = "achievements"
    const val CHANNEL_GENERATION = "generation"

    /** Az étkezés-értesítések azonosítója az étkezés id-jéből származik, hogy frissíthetők legyenek. */
    fun mealNotificationId(mealId: Long): Int = (mealId % Int.MAX_VALUE).toInt()

    const val ID_DAILY_SUMMARY = 1_000_001
    const val ID_ACHIEVEMENT_BASE = 2_000_000
    const val ID_GENERATION = 1_000_002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEALS,
                context.getString(R.string.channel_meals_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_meals_desc)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUMMARY,
                context.getString(R.string.channel_summary_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_summary_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERATION,
                context.getString(R.string.channel_generation_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_generation_desc)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACHIEVEMENTS,
                context.getString(R.string.channel_achievements_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_achievements_desc) }
        )
    }
}
