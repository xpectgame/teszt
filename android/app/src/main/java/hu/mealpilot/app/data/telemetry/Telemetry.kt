package hu.mealpilot.app.data.telemetry

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Context.telemetryStore: DataStore<Preferences> by preferencesDataStore(name = "mealpilot_telemetry")

/**
 * Névtelen használati számlálók.
 *
 * Szándékosan NEM eseménynapló: nem tároljuk, mikor mi történt, csak azt, hogy aznap
 * hányszor. Így a kérdésekre, amik érdekesek („készül-e terv", „hol morzsolódnak le",
 * „mennyi bejelentés jön"), van válasz, de egy ember napirendjét nem lehet
 * visszaolvasni belőle.
 */
enum class TelemetryEvent(val key: String) {
    APP_OPEN("app_open"),
    ONBOARDING_DONE("onboarding_done"),
    PLAN_REQUESTED("plan_requested"),
    PLAN_GENERATED("plan_generated"),
    PLAN_FAILED("plan_failed"),
    DAY_REFINED("day_refined"),
    MEAL_LOGGED("meal_logged"),
    WEIGHT_LOGGED("weight_logged"),
    CHAT_MESSAGE("chat_message"),
    CHAT_ACTION_CONFIRMED("chat_action_confirmed"),
    SHOPPING_OPENED("shopping_opened"),
    PAYWALL_SHOWN("paywall_shown"),
    PURCHASE_STARTED("purchase_started"),
    SUBSCRIBED("subscribed"),
    REPORT_SENT("report_sent"),
    QUOTA_BLOCKED("quota_blocked"),

    /** A tervezőszolgáltatás elakadt, és a beépített tervező fejezte be a tervet. */
    PLANNER_FALLBACK("planner_fallback"),
}

/** Egy feltöltésnyi adag. */
data class TelemetrySnapshot(
    val day: String,
    val counts: Map<String, Int>,
    /**
     * Ez az első sikeres feltöltés erre a napra ebből a telepítésből?
     *
     * A szerver ebből számolja, hány ember használta aznap az appot. Enélkül minden
     * feltöltés új felhasználónak látszott, és az app indításakor is feltöltünk —
     * vagyis aki naponta négyszer nyitotta meg, az négy embernek számított. Pont a
     * legaktívabb felhasználók torzították a legjobban a számot, felfelé.
     */
    val firstToday: Boolean = true,
) {
    val isEmpty: Boolean get() = counts.isEmpty()
}

class Telemetry(
    context: Context,
    private val scope: CoroutineScope,
    /** A felhasználó kikapcsolhatja. Kikapcsolva semmit nem gyűjtünk, nem csak nem küldünk. */
    private val enabled: suspend () -> Boolean,
) {

    private val appContext = context.applicationContext
    private val store = appContext.telemetryStore

    /**
     * Nem felfüggesztő, mert a hívási helyeken (gombnyomás, mentés) nincs értelme
     * megvárni. Ha elveszik egy számláló, az senkit nem érdekel — cserébe a telemetria
     * soha nem lassítja a felületet.
     */
    fun record(event: TelemetryEvent, count: Int = 1) {
        if (count <= 0) return
        scope.launch {
            if (!enabled()) return@launch
            recordOn(LocalDate.now().toString(), event, count)
        }
    }

    /**
     * A tényleges könyvelés, megadott napra.
     *
     * `internal`, mert a NAP szerepét csak így lehet megmérni: a hiba akkor jelentkezik,
     * ha egy adag több napon át bent ragad, és ezt a rendszeróra átállítása nélkül
     * másképp nem lehet előállítani.
     */
    internal suspend fun recordOn(day: String, event: TelemetryEvent, count: Int = 1) {
        store.edit { prefs ->
            val key = keyFor(day, event.key)
            prefs[key] = (prefs[key] ?: 0) + count
        }
    }

    /**
     * A LEGRÉGEBBI még fel nem töltött nap adagja.
     *
     * Naponta külön számlálók, mert egy adag egy naphoz tartozik. Korábban egyetlen
     * közös számlálókészlet volt, a nap pedig egy külön mezőben, amit csak akkor
     * állítottunk be, ha üres volt — vagyis amíg egy feltöltés nem sikerült (nincs
     * hálózat, nem futott le a háttérmunka), a KÖVETKEZŐ napok eseményei is a régi
     * nap rovatába gyűltek. A szerver így három nap eseményeit egy napra könyvelte,
     * a másik kettőről meg azt hitte, hogy senki nem használta az appot.
     *
     * Egy futás egy napot visz. A háttérmunka indításkor is fut, tehát a torlódás
     * gyorsan leürül.
     */
    suspend fun snapshot(): TelemetrySnapshot {
        val prefs = store.data.first()
        val byDay = sortedMapOf<String, MutableMap<String, Int>>()
        prefs.asMap().forEach { (key, value) ->
            val parsed = parseKey(key.name) ?: return@forEach
            val count = value as? Int ?: return@forEach
            byDay.getOrPut(parsed.first) { mutableMapOf() }[parsed.second] = count
        }
        val oldest = byDay.entries.firstOrNull()
        val day = oldest?.key ?: LocalDate.now().toString()
        return TelemetrySnapshot(
            day = day,
            counts = oldest?.value.orEmpty(),
            firstToday = prefs[K_COUNTED_DAY] != day,
        )
    }

    /** Csak sikeres feltöltés után hívjuk — így egy elveszett kérés nem visz el egy napot. */
    suspend fun clear(snapshot: TelemetrySnapshot) {
        store.edit { prefs ->
            snapshot.counts.forEach { (name, uploaded) ->
                val key = keyFor(snapshot.day, name)
                val remaining = (prefs[key] ?: 0) - uploaded
                if (remaining > 0) prefs[key] = remaining else prefs.remove(key)
            }
            // Innentől ezt a napot már jelentettük: a további feltöltések nem
            // növelhetik a napi felhasználószámot.
            prefs[K_COUNTED_DAY] = snapshot.day
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private companion object {
        const val PREFIX = "evt_"

        /** `evt_2026-09-24_app_open` — a nap mindig tíz karakter, tehát egyértelmű. */
        const val DAY_LENGTH = 10

        val K_COUNTED_DAY = stringPreferencesKey("counted_day")

        fun keyFor(day: String, event: String) = intPreferencesKey("$PREFIX${day}_$event")

        /** (nap, esemény), vagy null, ha nem számlálókulcs. */
        fun parseKey(name: String): Pair<String, String>? {
            val rest = name.removePrefix(PREFIX).takeIf { it != name } ?: return null
            if (rest.length <= DAY_LENGTH + 1 || rest[DAY_LENGTH] != '_') return null
            return rest.take(DAY_LENGTH) to rest.substring(DAY_LENGTH + 1)
        }
    }
}
