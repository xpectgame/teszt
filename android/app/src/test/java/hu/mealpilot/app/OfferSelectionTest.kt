package hu.mealpilot.app

import com.android.billingclient.api.ProductDetails
import hu.mealpilot.app.billing.bestOfferIndex
import hu.mealpilot.app.billing.recurringPhaseIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Az előfizetési ajánlat és a kiírt ár kiválasztása.
 *
 * Mindkét hiba akkor aktiválódott volna, amikor a Play Console-ban bekapcsol a
 * 7 napos ingyenes próbaidőszak: addig egyetlen ajánlat és egyetlen fázis van, és a
 * `firstOrNull()` véletlenül jót adott.
 */
class OfferSelectionTest {

    private val monthly = 2_490_000_000L   // 2490 Ft mikroegységben
    private val cheaper = 1_990_000_000L

    @Test
    fun `the free trial offer wins over the bare base plan`() {
        // A Play sorrendjére nincs garancia, ezért mindkét sorrendet nézzük.
        assertEquals(1, bestOfferIndex(listOf(listOf(monthly), listOf(0L, monthly))))
        assertEquals(0, bestOfferIndex(listOf(listOf(0L, monthly), listOf(monthly))))
    }

    @Test
    fun `without a trial the cheaper recurring price wins`() {
        assertEquals(1, bestOfferIndex(listOf(listOf(monthly), listOf(cheaper))))
    }

    @Test
    fun `a single offer is chosen, and no offer is reported as none`() {
        assertEquals(0, bestOfferIndex(listOf(listOf(monthly))))
        assertEquals(-1, bestOfferIndex(emptyList()))
    }

    @Test
    fun `the displayed price is the recurring phase, not the free trial`() {
        val trialThenMonthly = listOf(
            ProductDetails.RecurrenceMode.FINITE_RECURRING,
            ProductDetails.RecurrenceMode.INFINITE_RECURRING,
        )
        assertEquals(
            "Az ingyenes fázis ára nem az előfizetés ára",
            1,
            recurringPhaseIndex(trialThenMonthly),
        )
    }

    @Test
    fun `a plain subscription still reports its only phase`() {
        assertEquals(0, recurringPhaseIndex(listOf(ProductDetails.RecurrenceMode.INFINITE_RECURRING)))
    }

    @Test
    fun `an unexpected shape falls back to the last phase instead of the first`() {
        // Ha egyik fázis sem jelöli magát végtelenül ismétlődőnek, akkor is a
        // visszatérő árat akarjuk — az a lista végén áll.
        val odd = listOf(
            ProductDetails.RecurrenceMode.NON_RECURRING,
            ProductDetails.RecurrenceMode.FINITE_RECURRING,
        )
        assertEquals(1, recurringPhaseIndex(odd))
        assertEquals(0, recurringPhaseIndex(emptyList()))
    }
}
