package hu.mealpilot.app.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * A bolti fizetés absztrakciója.
 *
 * Külön interfész, mert az app többi része nem függhet attól, hogy éppen Google Play,
 * egy másik bolt, vagy semmi sem szolgálja ki a fizetést. Oldalról telepített és
 * fejlesztői buildekben a [NoBillingGateway] fut, és az app ettől még működik.
 */
interface BillingGateway {

    val state: StateFlow<BillingState>

    /** Kapcsolódás és a meglévő vásárlások lekérdezése. Többször is hívható. */
    fun refresh()

    /** Elindítja a Play fizetési folyamatát. Az eredmény a [state]-en keresztül érkezik. */
    fun launchPurchase(activity: Activity)

    /** Korábbi vásárlás visszaállítása — a Play szabályzata szerint kötelező felület. */
    fun restore()
}

data class BillingState(
    val available: Boolean = false,
    val connecting: Boolean = false,
    /** Formázott ár a boltból, pl. "1 990 Ft". Null, ha még nem tudjuk. */
    val formattedPrice: String? = null,
    val billingPeriodLabel: String? = null,
    /** Igaz, ha a boltnál aktív előfizetés van. */
    val subscribed: Boolean = false,
    /** Igaz, ha a fizetés folyamatban van (pl. banki jóváhagyásra vár). */
    val pending: Boolean = false,
    /**
     * Az aktív vásárlás tokenje. Ezt küldi az app a saját backendnek: a szerver ebből
     * kérdezi meg a Google-től, hogy tényleg van-e előfizetés — a kliens állítását
     * magában nem fogadja el.
     */
    val purchaseToken: String? = null,
    val error: String? = null,
) {
    companion object {
        /** Amikor nincs bolt: az app fut, csak nem lehet előfizetni. */
        val UNAVAILABLE = BillingState(available = false)
    }
}

/** A Play Console-ban létrehozandó termék azonosítója. */
const val PREMIUM_SUBSCRIPTION_ID = "mealpilot_premium_monthly"
