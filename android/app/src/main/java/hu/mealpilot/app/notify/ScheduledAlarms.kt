package hu.mealpilot.app.notify

import android.content.Context

/**
 * Nyilvántartás arról, melyik étkezésre van éppen kitett ébresztőnk.
 *
 * Miért kell külön nyilvántartás: az AlarmManager nem mondja meg, mit tettünk ki, és
 * nincs „töröld mindet" művelete sem — egy ébresztést csak ugyanazzal a
 * PendingIntenttel lehet visszavonni, tehát tudni kell, milyen azonosítókra kértük.
 *
 * Enélkül két dolog romlott el csendben: a kikapcsolt emlékeztető még a következő 36
 * órában szólt, és egy másik napra átcsúsztatott étkezés a RÉGI időpontjában.
 *
 * SharedPreferences és nem DataStore: ez az ébresztéskezelés szinkron, rövid
 * olvasásokat végez az értesítésvevőben, és ez a néhány azonosító nem indokol
 * coroutine-t.
 */
internal object ScheduledAlarms {

    private const val FILE = "mealpilot_scheduled_alarms"
    private const val KEY = "meals"

    /**
     * Egy kitett ébresztő.
     *
     * A [snoozed] azért külön jelölés, mert a halasztott ébresztőt a tervből nem lehet
     * visszaszámolni: az étkezés saját időpontja ilyenkor már elmúlt. Enélkül a
     * következő összehangolás visszavonná, és a „később" gomb után a felhasználó soha
     * nem kapna emlékeztetőt.
     */
    data class Entry(val mealId: Long, val triggerAtMillis: Long, val snoozed: Boolean)

    fun all(context: Context): List<Entry> =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty().mapNotNull(::decode)

    fun replace(context: Context, entries: Collection<Entry>) {
        prefs(context).edit().putStringSet(KEY, entries.map(::encode).toSet()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    internal fun encode(entry: Entry): String =
        "${entry.mealId}|${entry.triggerAtMillis}|${if (entry.snoozed) 1 else 0}"

    internal fun decode(raw: String): Entry? {
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val mealId = parts[0].toLongOrNull() ?: return null
        val triggerAt = parts[1].toLongOrNull() ?: return null
        return Entry(mealId, triggerAt, parts[2] == "1")
    }
}
