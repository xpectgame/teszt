package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.billing.EntitlementRepository
import hu.mealpilot.core.billing.BillingPeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A helyi keretszámláló és a szerveré nem ugyanaz.
 *
 * A döntést a szerver hozza, a helyi számláló csak azért van, hogy a felület tudja,
 * mit írjon ki. A kettő viszont szét tud csúszni, és nem csak visszaélésből: egy
 * készülékcsere vagy egy visszaállítás a DataStore-t átviszi, a telepítési
 * azonosítót viszont NEM (az szándékosan ki van zárva a mentésből).
 *
 * Az új készüléken tehát az app elhasznált keretet mutatott, a szerver meg egy
 * vadonatúj azonosítót látott teli kerettel: a felhasználó a saját ingyenes tervei
 * elől kapott fizetőfalat, és sosem derült ki neki, hogy jártak volna.
 *
 * A `/v1/session` végpont ezt a kezdetektől meg tudta volna mondani — csak soha
 * senki nem hívta meg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EntitlementServerSyncTest {

    private fun repository() =
        EntitlementRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `the server's numbers win over the local ones`() = runTest {
        val entitlements = repository()
        entitlements.clearAll()
        repeat(3) { entitlements.recordPlanGenerated() }
        assertEquals(
            "A helyi számláló szerint elfogyott a keret",
            0,
            entitlements.current().remainingPlans(),
        )

        // A szerver egy visszaállítás után vadonatúj telepítést lát: nulla fogyasztás.
        entitlements.applyServerUsage(BillingPeriod.TRIAL, plans = 0, messages = 0)

        assertEquals(
            "A szerver szerint jár még mind a három terv — ezt kell mutatni",
            3,
            entitlements.current().remainingPlans(),
        )
        assertTrue(entitlements.current().canGeneratePlan())
    }

    @Test
    fun `the server can also say the quota is gone`() = runTest {
        // A másik irány: a helyi számláló nullázódott (törlés, újratelepítés), a
        // szerver viszont emlékszik. Eddig az app három tervet ígért, és a szerver
        // mindet elutasította.
        val entitlements = repository()
        entitlements.clearAll()
        assertEquals(3, entitlements.current().remainingPlans())

        entitlements.applyServerUsage(BillingPeriod.TRIAL, plans = 3, messages = 20)

        assertEquals(0, entitlements.current().remainingPlans())
        assertEquals(0, entitlements.current().remainingMessages())
    }

    @Test
    fun `a nonsense answer cannot make the counter negative`() = runTest {
        val entitlements = repository()
        entitlements.clearAll()

        entitlements.applyServerUsage(BillingPeriod.TRIAL, plans = -5, messages = -1)

        assertEquals(3, entitlements.current().remainingPlans())
        assertEquals(0, entitlements.current().usage.aiPlans)
    }
}
