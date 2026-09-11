package hu.mealpilot.core.billing

import java.time.LocalDate

/** Csomag. A szabad sáv önmagában is használható app, nem csonkolt demó. */
enum class PlanTier(val hu: String) {
    FREE("Ingyenes"),
    PREMIUM("Teljes");
}

/**
 * Egy csomag korlátai. A −1 korlátlant jelent.
 *
 * A felosztás vezérelve: ami a telefonon fut, az ingyenes marad — naplózás, bevásárlólista,
 * mozgás, emlékeztetők, achievementek, az offline tervező. Ami külső hívásba és tényleges
 * pénzbe kerül (a tervezés és a beszélgetés), az a fizetős rész. Így az ingyenes app is
 * megtartja a felhasználót, a költség viszont csak ott keletkezik, ahol bevétel is van.
 */
data class TierLimits(
    val aiPlansPerMonth: Int,
    val chatMessagesPerMonth: Int,
    val maxPlanDays: Int,
    /** Egy nap átíratása szavakkal. */
    val canRefineDays: Boolean,
    /** A beszélgetésből indítható műveletek (újratervezés, időpontok, csere). */
    val canRunChatActions: Boolean,
) {
    fun isUnlimited(value: Int) = value < 0
}

object Tiers {

    val FREE = TierLimits(
        aiPlansPerMonth = 1,
        chatMessagesPerMonth = 10,
        maxPlanDays = 3,
        canRefineDays = false,
        canRunChatActions = false,
    )

    val PREMIUM = TierLimits(
        aiPlansPerMonth = -1,
        chatMessagesPerMonth = -1,
        maxPlanDays = 30,
        canRefineDays = true,
        canRunChatActions = true,
    )

    fun limitsFor(tier: PlanTier): TierLimits = when (tier) {
        PlanTier.FREE -> FREE
        PlanTier.PREMIUM -> PREMIUM
    }

    /** A fizetős csomag érvei — ugyanez a lista jelenik meg a paywallon. */
    val premiumBenefits: List<String> = listOf(
        "Korlátlan étrend, akár egy hónapra előre",
        "Korlátlan beszélgetés: bármit átírhatsz szavakkal",
        "Egy-egy nap külön átíratása, ha közbejön valami",
        "Minden új funkció, amint elkészül",
    )

    /** Ami a fizetős csomag nélkül is jár — ezt is kiírjuk, hogy ne érezze csapdának. */
    val freeBenefits: List<String> = listOf(
        "Étkezés-, súly- és mozgásnapló, korlátlanul",
        "Bevásárlólista és étkezési emlékeztetők",
        "Beépített receptekből készülő étrend, internet nélkül is",
        "Havonta egy teljes AI-étrend",
    )
}

/**
 * Havi elszámolási időszak. A kvóta naptári hónaponként nullázódik — ez az, amit a
 * felhasználó is így ért ("havi egy terv"), és nem igényel szerveroldali órajelet.
 */
object BillingPeriod {

    fun keyFor(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)

    fun currentKey(): String = keyFor(LocalDate.now())

    /** Hány nap múlva nullázódik a kvóta. */
    fun daysUntilReset(today: LocalDate = LocalDate.now()): Int =
        today.lengthOfMonth() - today.dayOfMonth + 1
}

/** Használati számlálók az aktuális időszakban. */
data class UsageCounters(
    val periodKey: String = BillingPeriod.currentKey(),
    val aiPlans: Int = 0,
    val chatMessages: Int = 0,
) {
    /** Új hónapban tiszta lappal indulunk. */
    fun normalizedFor(periodKey: String): UsageCounters =
        if (this.periodKey == periodKey) this else UsageCounters(periodKey = periodKey)
}

/**
 * A felhasználó aktuális jogosultsága. Egyetlen forrás, amit az app minden pontja kérdez —
 * így nem szóródik szét a felületen, hogy mi jár és mi nem.
 */
data class Entitlement(
    val tier: PlanTier = PlanTier.FREE,
    /** Az előfizetés vége, ha ismert. */
    val expiresAtMillis: Long? = null,
    val usage: UsageCounters = UsageCounters(),
    /** Igaz, ha a Play szerint fizetett, de még nem dolgozták fel (pl. utalásos fizetés). */
    val pending: Boolean = false,
) {
    val limits: TierLimits get() = Tiers.limitsFor(tier)
    val isPremium: Boolean get() = tier == PlanTier.PREMIUM

    private fun current(now: String) = usage.normalizedFor(now)

    fun remainingPlans(now: String = BillingPeriod.currentKey()): Int =
        if (limits.aiPlansPerMonth < 0) Int.MAX_VALUE
        else (limits.aiPlansPerMonth - current(now).aiPlans).coerceAtLeast(0)

    fun remainingMessages(now: String = BillingPeriod.currentKey()): Int =
        if (limits.chatMessagesPerMonth < 0) Int.MAX_VALUE
        else (limits.chatMessagesPerMonth - current(now).chatMessages).coerceAtLeast(0)

    fun canGeneratePlan(now: String = BillingPeriod.currentKey()): Boolean = remainingPlans(now) > 0

    fun canSendMessage(now: String = BillingPeriod.currentKey()): Boolean = remainingMessages(now) > 0

    /** A kérhető terv hossza. Az ingyenes sávban rövidebb, de nem nulla. */
    fun allowedPlanDays(requested: Int): Int = requested.coerceAtMost(limits.maxPlanDays)

    /**
     * Miért nem indulhat a művelet — a felületnek szánt, kész mondat.
     * Null, ha mehet.
     */
    fun blockReason(feature: PaidFeature, now: String = BillingPeriod.currentKey()): String? = when (feature) {
        PaidFeature.PLAN_GENERATION ->
            if (canGeneratePlan(now)) null
            else "Ebben a hónapban elhasználtad az ingyenes étrended. " +
                "A kvóta ${BillingPeriod.daysUntilReset()} nap múlva nullázódik."

        PaidFeature.CHAT ->
            if (canSendMessage(now)) null
            else "Ebben a hónapban elfogyott az ingyenes üzenetkereted. " +
                "A kvóta ${BillingPeriod.daysUntilReset()} nap múlva nullázódik."

        PaidFeature.CHAT_ACTIONS ->
            if (limits.canRunChatActions) null
            else "A beszélgetésből indított módosításokhoz előfizetés kell."

        PaidFeature.DAY_REFINE ->
            if (limits.canRefineDays) null
            else "Egy-egy nap átíratásához előfizetés kell."

        PaidFeature.LONG_PLAN ->
            if (limits.maxPlanDays >= 7) null
            else "Az ingyenes csomagban legfeljebb ${limits.maxPlanDays} napos terv kérhető."
    }
}

/** Amiért fizetni kell. A felület ezekre hivatkozva kérdezi le a jogosultságot. */
enum class PaidFeature {
    PLAN_GENERATION,
    CHAT,
    CHAT_ACTIONS,
    DAY_REFINE,
    LONG_PLAN,
}
