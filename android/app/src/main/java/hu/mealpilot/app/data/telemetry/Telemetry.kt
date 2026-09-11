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
}

/** Egy feltöltésnyi adag. */
data class TelemetrySnapshot(
    val day: String,
    val counts: Map<String, Int>,
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
            val today = LocalDate.now().toString()
            store.edit { prefs ->
                val storedDay = prefs[K_DAY]
                if (storedDay == null) prefs[K_DAY] = today
                val key = intPreferencesKey(PREFIX + event.key)
                prefs[key] = (prefs[key] ?: 0) + count
            }
        }
    }

    suspend fun snapshot(): TelemetrySnapshot {
        val prefs = store.data.first()
        val counts = prefs.asMap()
            .mapNotNull { (key, value) ->
                val name = key.name.removePrefix(PREFIX).takeIf { it != key.name } ?: return@mapNotNull null
                val count = value as? Int ?: return@mapNotNull null
                name to count
            }
            .toMap()
        return TelemetrySnapshot(day = prefs[K_DAY] ?: LocalDate.now().toString(), counts = counts)
    }

    /** Csak sikeres feltöltés után hívjuk — így egy elveszett kérés nem visz el egy napot. */
    suspend fun clear(snapshot: TelemetrySnapshot) {
        store.edit { prefs ->
            snapshot.counts.forEach { (name, uploaded) ->
                val key = intPreferencesKey(PREFIX + name)
                val remaining = (prefs[key] ?: 0) - uploaded
                if (remaining > 0) prefs[key] = remaining else prefs.remove(key)
            }
            prefs.remove(K_DAY)
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private companion object {
        const val PREFIX = "evt_"
        val K_DAY = stringPreferencesKey("day")
    }
}
