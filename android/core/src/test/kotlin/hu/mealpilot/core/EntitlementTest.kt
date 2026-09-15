package hu.mealpilot.core

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.billing.BillingPeriod
import hu.mealpilot.core.billing.Entitlement
import hu.mealpilot.core.billing.PaidFeature
import hu.mealpilot.core.billing.PlanTier
import hu.mealpilot.core.billing.Tiers
import hu.mealpilot.core.billing.UsageCounters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntitlementTest {

    private val period = "2026-09"

    /** Az ingyenes sáv alanya a próbaidőszak, nem egy naptári hónap. */
    private fun free(plans: Int = 0, messages: Int = 0) = Entitlement(
        tier = PlanTier.FREE,
        usage = UsageCounters(BillingPeriod.TRIAL, aiPlans = plans, chatMessages = messages),
    )

    private fun premium(plans: Int = 99, messages: Int = 999) = Entitlement(
        tier = PlanTier.PREMIUM,
        usage = UsageCounters(period, aiPlans = plans, chatMessages = messages),
    )

    @Test
    fun `a fresh free account gets its trial allowance`() {
        val entitlement = free()
        assertTrue(entitlement.canGeneratePlan())
        assertEquals(Tiers.FREE.aiPlans, entitlement.remainingPlans())
        assertEquals(Tiers.FREE.chatMessages, entitlement.remainingMessages())
    }

    @Test
    fun `the trial runs out and does not promise a reset that never comes`() {
        val used = free(plans = Tiers.FREE.aiPlans)
        assertFalse(used.canGeneratePlan())
        assertEquals(0, used.remainingPlans())

        val reason = used.blockReason(PaidFeature.PLAN_GENERATION)
        assertNotNull(reason)
        // A próbakeret nem töltődik újra. Egy „X nap múlva nullázódik" mondat olyasmit
        // ígérne, ami soha nem jön el — az apró hazugság viszi el a bizalmat.
        assertFalse("Ne ígérjen megújulást", reason!!.contains("nullázódik"))
        assertTrue("Mondja meg, mi a kiút", reason.contains("előfizetés", ignoreCase = true))
    }

    @Test
    fun `the chat quota is independent of the plan quota`() {
        val used = free(plans = Tiers.FREE.aiPlans, messages = 0)
        assertFalse(used.canGeneratePlan())
        assertTrue("A beszélgetés még mehet", used.canSendMessage())
    }

    @Test
    fun `premium is unlimited on both counters`() {
        val entitlement = premium()
        assertTrue(entitlement.canGeneratePlan(period))
        assertTrue(entitlement.canSendMessage(period))
        assertNull(entitlement.blockReason(PaidFeature.PLAN_GENERATION, period))
        assertNull(entitlement.blockReason(PaidFeature.CHAT, period))
    }

    @Test
    fun `paid-only features are named, not silently missing`() {
        val entitlement = free()
        assertNotNull(entitlement.blockReason(PaidFeature.CHAT_ACTIONS, period))
        assertNotNull(entitlement.blockReason(PaidFeature.DAY_REFINE, period))
        assertNotNull(entitlement.blockReason(PaidFeature.LONG_PLAN, period))

        val paid = premium()
        assertNull(paid.blockReason(PaidFeature.CHAT_ACTIONS, period))
        assertNull(paid.blockReason(PaidFeature.DAY_REFINE, period))
        assertNull(paid.blockReason(PaidFeature.LONG_PLAN, period))
    }

    @Test
    fun `the trial never refills, however much time passes`() {
        // Ez az üzleti modell tesztje. Ha a próbakeret hónapfordulóra újratöltődne,
        // minden ingyenes felhasználó visszatérő költség lenne bevétel nélkül, és a
        // szolgáltatás annál többet veszítene, minél népszerűbb.
        val exhausted = free(plans = Tiers.FREE.aiPlans, messages = Tiers.FREE.chatMessages)
        assertFalse(exhausted.canGeneratePlan())
        assertFalse(exhausted.canSendMessage())

        // A kulcs állandó, tehát nincs az a dátum, ami nullázná.
        assertEquals(BillingPeriod.TRIAL, BillingPeriod.keyFor(PlanTier.FREE, LocalDate.of(2026, 9, 1)))
        assertEquals(BillingPeriod.TRIAL, BillingPeriod.keyFor(PlanTier.FREE, LocalDate.of(2099, 12, 31)))
        assertEquals(0, exhausted.remainingPlans())
    }

    @Test
    fun `a paying subscriber does get a fresh month`() {
        // Amit kifizetett, azt minden hónapban megkapja — itt a fordulás helyes.
        val august = Entitlement(
            tier = PlanTier.PREMIUM,
            usage = UsageCounters("2026-08", aiPlans = 5, chatMessages = 50),
        )
        assertTrue(august.canGeneratePlan("2026-09"))
        assertEquals("2026-09", BillingPeriod.keyFor(PlanTier.PREMIUM, LocalDate.of(2026, 9, 11)))
    }

    @Test
    fun `plan length is capped on free but never to zero`() {
        assertEquals(Tiers.FREE.maxPlanDays, free().allowedPlanDays(30))
        assertEquals(1, free().allowedPlanDays(1))
        assertEquals(30, premium().allowedPlanDays(30))
    }

    @Test
    fun `subscriber period keys sort chronologically`() {
        val premiumKey = { d: LocalDate -> BillingPeriod.keyFor(PlanTier.PREMIUM, d) }
        assertEquals("2026-09", premiumKey(LocalDate.of(2026, 9, 11)))
        assertEquals("2026-01", premiumKey(LocalDate.of(2026, 1, 1)))
        assertTrue(premiumKey(LocalDate.of(2026, 1, 1)) < premiumKey(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `days until reset counts the current day, and is absent for the trial`() {
        val p = PlanTier.PREMIUM
        assertEquals(1, BillingPeriod.daysUntilReset(p, LocalDate.of(2026, 9, 30)))
        assertEquals(30, BillingPeriod.daysUntilReset(p, LocalDate.of(2026, 9, 1)))
        assertEquals(28, BillingPeriod.daysUntilReset(p, LocalDate.of(2026, 2, 1)))
        assertEquals(29, BillingPeriod.daysUntilReset(p, LocalDate.of(2028, 2, 1))) // szökőév

        // A próbaidőszaknak nincs fordulónapja, és ezt null mondja ki — nem egy szám,
        // amit a felület véletlenül kiírhatna.
        assertNull(BillingPeriod.daysUntilReset(PlanTier.FREE, LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `the free tier is a usable app, not a locked demo`() {
        // Ha ezek bármelyike fizetőssé válna, az ingyenes app elveszítené az értelmét.
        assertTrue(Tiers.FREE.aiPlans > 0)
        assertTrue(Tiers.FREE.chatMessages > 0)
        assertTrue(Tiers.FREE.maxPlanDays > 0)
        assertTrue(Tiers.freeBenefits(AppLanguage.HU).isNotEmpty())
        assertTrue(Tiers.premiumBenefits(AppLanguage.HU).isNotEmpty())
    }
}
