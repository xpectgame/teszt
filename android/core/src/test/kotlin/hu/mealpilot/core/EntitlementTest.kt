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

    private fun free(plans: Int = 0, messages: Int = 0) = Entitlement(
        tier = PlanTier.FREE,
        usage = UsageCounters(period, aiPlans = plans, chatMessages = messages),
    )

    private fun premium(plans: Int = 99, messages: Int = 999) = Entitlement(
        tier = PlanTier.PREMIUM,
        usage = UsageCounters(period, aiPlans = plans, chatMessages = messages),
    )

    @Test
    fun `a fresh free account gets its monthly allowance`() {
        val entitlement = free()
        assertTrue(entitlement.canGeneratePlan(period))
        assertEquals(Tiers.FREE.aiPlansPerMonth, entitlement.remainingPlans(period))
        assertEquals(Tiers.FREE.chatMessagesPerMonth, entitlement.remainingMessages(period))
    }

    @Test
    fun `the free plan quota runs out and explains itself`() {
        val used = free(plans = Tiers.FREE.aiPlansPerMonth)
        assertFalse(used.canGeneratePlan(period))
        assertEquals(0, used.remainingPlans(period))

        val reason = used.blockReason(PaidFeature.PLAN_GENERATION, period)
        assertNotNull(reason)
        assertTrue("A mondat mondja meg, mikor újul meg", reason!!.contains("nullázódik"))
    }

    @Test
    fun `the chat quota is independent of the plan quota`() {
        val used = free(plans = Tiers.FREE.aiPlansPerMonth, messages = 0)
        assertFalse(used.canGeneratePlan(period))
        assertTrue("A beszélgetés még mehet", used.canSendMessage(period))
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
    fun `a new month resets the counters without touching stored history`() {
        val lastMonth = Entitlement(
            tier = PlanTier.FREE,
            usage = UsageCounters("2026-08", aiPlans = 5, chatMessages = 50),
        )
        // Az augusztusi fogyasztás nem számít bele a szeptemberi keretbe.
        assertTrue(lastMonth.canGeneratePlan("2026-09"))
        assertEquals(Tiers.FREE.aiPlansPerMonth, lastMonth.remainingPlans("2026-09"))
        // ...de a saját hónapjában még fogyott.
        assertFalse(lastMonth.canGeneratePlan("2026-08"))
    }

    @Test
    fun `plan length is capped on free but never to zero`() {
        assertEquals(Tiers.FREE.maxPlanDays, free().allowedPlanDays(30))
        assertEquals(1, free().allowedPlanDays(1))
        assertEquals(30, premium().allowedPlanDays(30))
    }

    @Test
    fun `period keys sort chronologically`() {
        assertEquals("2026-09", BillingPeriod.keyFor(LocalDate.of(2026, 9, 11)))
        assertEquals("2026-01", BillingPeriod.keyFor(LocalDate.of(2026, 1, 1)))
        assertTrue(BillingPeriod.keyFor(LocalDate.of(2026, 1, 1)) < BillingPeriod.keyFor(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `days until reset counts the current day as remaining`() {
        assertEquals(1, BillingPeriod.daysUntilReset(LocalDate.of(2026, 9, 30)))
        assertEquals(30, BillingPeriod.daysUntilReset(LocalDate.of(2026, 9, 1)))
        assertEquals(28, BillingPeriod.daysUntilReset(LocalDate.of(2026, 2, 1)))
        assertEquals(29, BillingPeriod.daysUntilReset(LocalDate.of(2028, 2, 1))) // szökőév
    }

    @Test
    fun `the free tier is a usable app, not a locked demo`() {
        // Ha ezek bármelyike fizetőssé válna, az ingyenes app elveszítené az értelmét.
        assertTrue(Tiers.FREE.aiPlansPerMonth > 0)
        assertTrue(Tiers.FREE.chatMessagesPerMonth > 0)
        assertTrue(Tiers.FREE.maxPlanDays > 0)
        assertTrue(Tiers.freeBenefits(AppLanguage.HU).isNotEmpty())
        assertTrue(Tiers.premiumBenefits(AppLanguage.HU).isNotEmpty())
    }
}
