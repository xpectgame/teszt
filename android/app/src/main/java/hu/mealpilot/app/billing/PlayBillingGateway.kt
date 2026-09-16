package hu.mealpilot.app.billing

import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.AppStrings
import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play előfizetés.
 *
 * A vásárlás igazolása itt, a kliensen történik — ez elég a bolt működéséhez, de
 * önmagában nem visszaélésbiztos: egy módosított app hazudhat a jogosultságáról.
 * Az igazi ellenőrzés a backend dolga lesz (Play Developer API `purchases.subscriptions.get`),
 * lásd docs/LAUNCH-CHECKLIST.md. Addig is minden tényleges költség (a modellhívás) a
 * felhasználó saját kulcsán fut, tehát a kockázat nem pénzügyi.
 */
class PlayBillingGateway(
    context: Context,
    private val strings: AppStrings,
    private val onEntitlementChanged: (subscribed: Boolean, pending: Boolean, expiresAtMillis: Long?) -> Unit,
) : BillingGateway {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(BillingState(connecting = true))
    override val state: StateFlow<BillingState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null

    /**
     * A kiválasztott ajánlat token azonosítója.
     *
     * Külön mező, mert az ÁR és a VÁSÁRLÁS ugyanabból az ajánlatból kell hogy jöjjön.
     * Korábban mindkét helyen külön `firstOrNull()` állt, ami két különböző ajánlatot
     * is elérhetett volna.
     */
    private var selectedOfferToken: String? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.value = _state.value.copy(error = null)
            else ->
                _state.value = _state.value.copy(error = readable(result))
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    override fun refresh() {
        if (client.isReady) {
            queryProduct()
            queryPurchases()
            return
        }
        _state.value = _state.value.copy(connecting = true)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(available = true, connecting = false, error = null)
                    queryProduct()
                    queryPurchases()
                } else {
                    _state.value = BillingState(
                        available = false,
                        connecting = false,
                        error = readable(billingResult),
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(available = false, connecting = false)
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(error = readable(result))
                return@queryProductDetailsAsync
            }
            val product = details.firstOrNull()
            productDetails = product

            val offers = product?.subscriptionOfferDetails.orEmpty()
            val offer = offers.getOrNull(
                bestOfferIndex(offers.map { it.pricingPhases.pricingPhaseList.map { phase -> phase.priceAmountMicros } }),
            )
            selectedOfferToken = offer?.offerToken

            // A VISSZATÉRŐ fázis ára jelenik meg, nem az elsőé. Ingyenes próbaidőszaknál
            // az első fázis 0 forint — azt kiírva az app azt állítaná, hogy az
            // előfizetés ingyenes.
            val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
            val phase = phases.getOrNull(recurringPhaseIndex(phases.map { it.recurrenceMode }))

            _state.value = _state.value.copy(
                formattedPrice = phase?.formattedPrice,
                billingPeriodLabel = phase?.billingPeriod?.let(::humanPeriod),
                error = if (product == null) {
                    strings[R.string.billing_unavailable_retry]
                } else {
                    null
                },
            )
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val relevant = purchases.filter { PREMIUM_SUBSCRIPTION_ID in it.products }
        val active = relevant.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING }

        active?.let { purchase ->
            // A Play visszavonja a vásárlást, ha három napon belül nem nyugtázzuk.
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "A vásárlás nyugtázása nem sikerült: ${readable(result)}")
                    }
                }
            }
        }

        _state.value = _state.value.copy(
            subscribed = active != null,
            pending = pending,
            purchaseToken = active?.purchaseToken,
            error = null,
        )
        onEntitlementChanged(active != null, pending, null)
    }

    override fun launchPurchase(activity: Activity) {
        val product = productDetails
        if (product == null) {
            _state.value = _state.value.copy(error = strings[R.string.billing_unavailable])
            return
        }
        // Ugyanaz az ajánlat, aminek az árát kiírtuk. Enélkül a felhasználó mást fizetne,
        // mint amit a fizetőfalon látott.
        val offerToken = selectedOfferToken
        if (offerToken == null) {
            _state.value = _state.value.copy(error = strings[R.string.billing_no_offer])
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(error = readable(result))
        }
    }

    override fun restore() {
        refresh()
    }

    private fun humanPeriod(iso: String): String = when (iso) {
        "P1M" -> strings[R.string.period_month]
        "P3M" -> strings[R.string.period_quarter]
        "P6M" -> strings[R.string.period_half_year]
        "P1Y" -> strings[R.string.period_year]
        "P1W" -> strings[R.string.period_week]
        else -> iso
    }

    private fun readable(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            strings[R.string.billing_no_play]
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            strings[R.string.billing_play_down]
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            strings[R.string.billing_not_purchasable]
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            strings[R.string.billing_already_owned]
        BillingClient.BillingResponseCode.NETWORK_ERROR ->
            "Nincs internetkapcsolat."
        else -> result.debugMessage.ifBlank { "Ismeretlen hiba (${result.responseCode})." }
    }

    private companion object {
        const val TAG = "PlayBillingGateway"
    }
}
