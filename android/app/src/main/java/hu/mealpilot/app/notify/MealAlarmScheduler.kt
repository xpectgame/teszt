package hu.mealpilot.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import hu.mealpilot.app.data.local.MealEntity
import java.time.Instant
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

    /** Egy tervezett étkezés azonosítója és időpontja — ennyi kell az időzítéshez. */
    internal data class PlannedMeal(val mealId: Long, val scheduledAtMillis: Long)

    /** Mit kell kitenni, mit visszavonni, és mi marad a nyilvántartásban. */
    internal data class AlarmPlan(
        val place: List<ScheduledAlarms.Entry>,
        val cancel: List<Long>,
        val keep: List<ScheduledAlarms.Entry>,
    )

    /**
     * Az ébresztéssor összehangolása a tervvel — a döntés, Android nélkül.
     *
     * Három szabály, és mindhárom egy-egy valódi hibából jött:
     *
     * 1. Amire már nincs szükség, azt VISSZA kell vonni. Egy másik napra csúsztatott
     *    vagy újratervezett étkezés ébresztője magától a RÉGI időpontjában maradna.
     *    (Üres `meals` lista mellett ez a kikapcsolás: minden ébresztő megy.)
     * 2. A lejárt nyilvántartási bejegyzés ébresztője már elsült, nincs mit
     *    visszavonni rajta — csak kikerül a nyilvántartásból.
     * 3. A halasztott ébresztő marad. Azt a tervből nem lehet visszaszámolni, mert az
     *    étkezés saját időpontja ilyenkor már elmúlt: ha visszavonnánk, a „később"
     *    gomb után a felhasználó soha nem kapna emlékeztetőt.
     */
    internal fun alarmPlan(
        meals: List<PlannedMeal>,
        leadMinutes: Int,
        held: List<ScheduledAlarms.Entry>,
        nowMillis: Long,
    ): AlarmPlan {
        val lead = TimeUnit.MINUTES.toMillis(leadMinutes.toLong())
        val place = meals.mapNotNull { meal ->
            val triggerAt = meal.scheduledAtMillis - lead
            if (triggerAt <= nowMillis) null
            else ScheduledAlarms.Entry(meal.mealId, triggerAt, snoozed = false)
        }

        val placedIds = place.mapTo(mutableSetOf()) { it.mealId }
        val keep = place.toMutableList()
        val cancel = mutableListOf<Long>()

        for (entry in held) {
            if (entry.mealId in placedIds) continue
            if (entry.triggerAtMillis <= nowMillis) continue
            if (entry.snoozed) keep += entry else cancel += entry.mealId
        }

        return AlarmPlan(place = place, cancel = cancel, keep = keep)
    }

    /** Az [alarmPlan] végrehajtása a rendszer ébresztéskezelőjén. */
    fun syncMeals(context: Context, meals: List<MealEntity>, leadMinutes: Int) {
        val plan = alarmPlan(
            meals = meals.map { PlannedMeal(it.id, it.scheduledAtMillis) },
            leadMinutes = leadMinutes,
            held = ScheduledAlarms.all(context),
            nowMillis = System.currentTimeMillis(),
        )
        plan.place.forEach { place(context, it.mealId, it.triggerAtMillis) }
        plan.cancel.forEach { cancelMeal(context, it) }
        ScheduledAlarms.replace(context, plan.keep)
    }

    /** A „később" gomb: ez az egy ébresztő nem a tervből jön, ezért külön jelöljük. */
    fun snoozeMeal(context: Context, mealId: Long, triggerAtMillis: Long) {
        place(context, mealId, triggerAtMillis)
        val entry = ScheduledAlarms.Entry(mealId, triggerAtMillis, snoozed = true)
        val others = ScheduledAlarms.all(context).filterNot { it.mealId == mealId }
        ScheduledAlarms.replace(context, others + entry)
    }

    /**
     * Minden étkezési ébresztő visszavonása.
     *
     * Az emlékeztetők kikapcsolásakor ez a lényeg: a beállítás nem csak a JÖVŐBELI
     * időzítésre vonatkozik. Aki átbillenti a kapcsolót, most akar csendet, nem 36 óra
     * múlva.
     */
    fun cancelAllMeals(context: Context) {
        ScheduledAlarms.all(context).forEach { cancelMeal(context, it.mealId) }
        ScheduledAlarms.clear(context)
    }

    private fun place(context: Context, mealId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pendingIntent = reminderIntent(context, mealId)
        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, INEXACT_WINDOW_MILLIS, pendingIntent,
                )
            }
        } catch (security: SecurityException) {
            // A pontos ébresztés engedélye menet közben is visszavonható.
            Log.w(TAG, "Pontos ébresztés megtagadva, ablakos ébresztésre váltok.", security)
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, INEXACT_WINDOW_MILLIS, pendingIntent,
            )
        }
    }

    fun cancelMeal(context: Context, mealId: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(reminderIntent(context, mealId))
    }

    /** Napi összefoglaló a megadott órában — mindig a következő előfordulásra. */
    fun scheduleDailySummary(context: Context, hour: Int) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val trigger = nextDailySummaryAt(hour, System.currentTimeMillis(), ZoneId.systemDefault())

        val pendingIntent = summaryIntent(context)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } catch (security: SecurityException) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, trigger, TimeUnit.MINUTES.toMillis(30), pendingIntent,
            )
        }
    }

    /**
     * Mikor van legközelebb a megadott óra.
     *
     * A NAPOT léptetjük, nem 24 órát adunk hozzá. Óraátállításkor a nap 23 vagy 25
     * órás, tehát a „holnap ugyanekkor" nem 86 400 000 ezredmásodperc: az órák
     * visszaállításakor az összefoglaló egy órával korábban, tavasszal egy órával
     * később szólalt volna meg.
     *
     * Ha a kiválasztott időpont az átállás miatt nem létezik (tavasszal a 2 és 3 óra
     * közötti óra kimarad), az `atZone` előre tolja a legközelebbi létező pillanatra —
     * egy emlékeztetőnél pontosan ez a kívánt viselkedés.
     */
    internal fun nextDailySummaryAt(hour: Int, nowMillis: Long, zone: ZoneId): Long {
        val time = LocalTime.of(hour.coerceIn(0, 23), 0)
        val now = Instant.ofEpochMilli(nowMillis)
        val today = now.atZone(zone).toLocalDate()
        val candidate = today.atTime(time).atZone(zone)
        val next = if (candidate.toInstant().isAfter(now)) candidate
        else today.plusDays(1).atTime(time).atZone(zone)
        return next.toInstant().toEpochMilli()
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
