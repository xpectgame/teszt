package hu.mealpilot.app.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tartalék, ha nincs elérhető bolt (oldalról telepített build, emulátor Play nélkül).
 *
 * Fontos, hogy ilyenkor se dőljön el az app: az ingyenes sáv teljes értékűen működik,
 * csak előfizetni nem lehet, és ezt a felület meg is mondja.
 */
class NoBillingGateway(private val reason: String) : BillingGateway {

    private val _state = MutableStateFlow(
        BillingState(available = false, error = reason)
    )
    override val state: StateFlow<BillingState> = _state.asStateFlow()

    override fun refresh() = Unit
    override fun launchPurchase(activity: Activity) = Unit
    override fun restore() = Unit
}
