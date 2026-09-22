package hu.mealpilot.app.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.mealpilot.core.billing.BillingPeriod
import hu.mealpilot.core.billing.Entitlement
import hu.mealpilot.core.billing.PlanTier
import hu.mealpilot.core.billing.UsageCounters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.billingStore: DataStore<Preferences> by preferencesDataStore(name = "mealpilot_billing")

/**
 * A jogosultság és a havi kvóta tárolása.
 *
 * A számlálók helyben élnek, tehát egy elszánt felhasználó megkerülheti őket — de ez
 * már nem sokat ér neki: ha a hívás a backenden megy ki, a döntést a szerver hozza, és
 * ott a saját számlálója számít. Ez a példány a FELÜLETÉRT felel: abból tudja az app,
 * mit írjon ki és mit ajánljon fel, mielőtt egyáltalán kimenne egy kérés.
 */
class EntitlementRepository(
    context: Context,
    /**
     * A fejlesztő saját buildje. Ilyenkor a teljes csomag jár, bolt és vásárlás nélkül —
     * a backend ugyanezt a kulcsot ellenőrzi, tehát a szerver is így szolgál ki.
     */
    private val ownerBuild: Boolean = false,
) {

    private val store = context.billingStore

    val entitlement: Flow<Entitlement> = store.data.map { it.toEntitlement() }

    suspend fun current(): Entitlement = entitlement.first()

    /** A bolt állapotából frissíti a jogosultságot. */
    suspend fun applyPurchaseState(subscribed: Boolean, pending: Boolean, expiresAtMillis: Long?) {
        store.edit { prefs ->
            prefs[K_TIER] = if (subscribed) PlanTier.PREMIUM.name else PlanTier.FREE.name
            prefs[K_PENDING] = pending
            if (expiresAtMillis != null) prefs[K_EXPIRES] = expiresAtMillis else prefs.remove(K_EXPIRES)
        }
    }

    /**
     * Fejlesztői kapcsoló: előfizetés nélkül is bekapcsolható a teljes csomag.
     * A rejtett fejlesztői részből érhető el, hogy a fizetős élmény tesztelhető legyen,
     * mielőtt a Play Console-ban él a termék.
     */
    suspend fun setDeveloperPremium(enabled: Boolean) {
        store.edit { prefs ->
            prefs[K_DEV_PREMIUM] = enabled
            if (enabled) prefs[K_TIER] = PlanTier.PREMIUM.name
        }
    }

    suspend fun isDeveloperPremium(): Boolean = store.data.first()[K_DEV_PREMIUM] ?: false

    /**
     * A SZERVER számlálóinak átvétele.
     *
     * A helyi számlálók a felületért felelnek, a döntést a szerver hozza — de a kettő
     * szét tud csúszni, és nem csak visszaélésből. Egy készülékcsere vagy egy
     * visszaállítás a DataStore-t átviszi, a telepítési azonosítót viszont NEM (az
     * szándékosan ki van zárva a mentésből). Az új készüléken tehát az app elhasznált
     * keretet mutatott, a szerver meg egy vadonatúj azonosítót látott teli kerettel:
     * a felhasználó a saját ingyenes tervei elől kapott fizetőfalat, és sosem derült
     * ki neki, hogy jártak volna.
     *
     * A szerver az igazság forrása, tehát felül is írjuk vele a helyi értékeket.
     */
    suspend fun applyServerUsage(periodKey: String, plans: Int, messages: Int) {
        store.edit { prefs ->
            prefs[K_PERIOD] = periodKey.ifBlank { BillingPeriod.keyFor(prefs.tier()) }
            prefs[K_PLANS] = plans.coerceAtLeast(0)
            prefs[K_MESSAGES] = messages.coerceAtLeast(0)
        }
    }

    suspend fun recordPlanGenerated() = bumpUsage { it.copy(aiPlans = it.aiPlans + 1) }

    suspend fun recordChatMessage() = bumpUsage { it.copy(chatMessages = it.chatMessages + 1) }

    /** Tesztekhez és a „minden adat törlése" művelethez. */
    suspend fun resetUsage() {
        store.edit { prefs ->
            prefs[K_PERIOD] = BillingPeriod.TRIAL
            prefs[K_PLANS] = 0
            prefs[K_MESSAGES] = 0
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private suspend fun bumpUsage(transform: (UsageCounters) -> UsageCounters) {
        store.edit { prefs ->
            // A csomag dönti el, melyik időszakba könyvelünk: a próbakeret sosem fordul,
            // az előfizetőé havonta. A szerver `periodFor` függvénye ugyanez.
            // A tranzakción BELÜL olvassuk ki, hogy ne egy közben elavult csomagot lássunk.
            val period = BillingPeriod.keyFor(prefs.tier())
            val stored = UsageCounters(
                periodKey = prefs[K_PERIOD] ?: period,
                aiPlans = prefs[K_PLANS] ?: 0,
                chatMessages = prefs[K_MESSAGES] ?: 0,
            ).normalizedFor(period)
            val next = transform(stored)
            prefs[K_PERIOD] = next.periodKey
            prefs[K_PLANS] = next.aiPlans
            prefs[K_MESSAGES] = next.chatMessages
        }
    }

    /**
     * A hatályos csomag. A fejlesztői kapcsoló és a saját build felülírja a tároltat,
     * és ezt a kvóta könyvelésének is ugyanígy kell látnia — különben a fejlesztői
     * teljes csomag a próbakeretbe könyvelne.
     */
    private fun Preferences.tier(): PlanTier {
        val devPremium = ownerBuild || (this[K_DEV_PREMIUM] ?: false)
        if (devPremium) return PlanTier.PREMIUM
        return runCatching { PlanTier.valueOf(this[K_TIER] ?: PlanTier.FREE.name) }
            .getOrDefault(PlanTier.FREE)
    }

    private fun Preferences.toEntitlement(): Entitlement {
        val tier = tier()
        return Entitlement(
            tier = tier,
            expiresAtMillis = this[K_EXPIRES],
            pending = this[K_PENDING] ?: false,
            usage = UsageCounters(
                periodKey = this[K_PERIOD] ?: BillingPeriod.keyFor(tier),
                aiPlans = this[K_PLANS] ?: 0,
                chatMessages = this[K_MESSAGES] ?: 0,
            ).normalizedFor(BillingPeriod.keyFor(tier)),
        )
    }

    private companion object {
        val K_TIER = stringPreferencesKey("tier")
        val K_EXPIRES = longPreferencesKey("expires_at")
        val K_PENDING = booleanPreferencesKey("pending")
        val K_DEV_PREMIUM = booleanPreferencesKey("developer_premium")
        val K_PERIOD = stringPreferencesKey("usage_period")
        val K_PLANS = intPreferencesKey("usage_plans")
        val K_MESSAGES = intPreferencesKey("usage_messages")
    }
}
