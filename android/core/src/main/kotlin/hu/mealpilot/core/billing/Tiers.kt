package hu.mealpilot.core.billing

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.Localized
import java.time.LocalDate

/** Csomag. A szabad sáv önmagában is használható app, nem csonkolt demó. */
enum class PlanTier(override val hu: String, override val en: String) : Localized {
    FREE("Ingyenes", "Free"),
    PREMIUM("Teljes", "Full");
}

/**
 * Egy csomag korlátai. A −1 korlátlant jelent.
 *
 * A felosztás vezérelve: ami a telefonon fut, az ingyenes marad — naplózás, bevásárlólista,
 * emlékeztetők, achievementek, az offline tervező. Ami külső hívásba és tényleges
 * pénzbe kerül (a tervezés és a beszélgetés), az a fizetős rész. Így az ingyenes app is
 * megtartja a felhasználót, a költség viszont csak ott keletkezik, ahol bevétel is van.
 */
data class TierLimits(
    /**
     * A számok ÉRTELMEZÉSE csomagfüggő, és ezt a [BillingPeriod.keyFor] dönti el:
     * az ingyenes sávban EGYSZERI próbakeret, a fizetősben havi. Ezért nincs a
     * nevükben "PerMonth" — az az ingyenes sávra hazugság lenne.
     */
    val aiPlans: Int,
    val chatMessages: Int,
    val maxPlanDays: Int,
    /** Egy nap átíratása szavakkal. */
    val canRefineDays: Boolean,
    /** A beszélgetésből indítható műveletek (újratervezés, időpontok, csere). */
    val canRunChatActions: Boolean,
) {
    fun isUnlimited(value: Int) = value < 0
}

object Tiers {

    // FONTOS: ezek a számok a backend `src/limits.ts` fájljában is megvannak, és ott a
    // döntés, itt csak a kiírás. Egy módosított app a saját számlálóját átírhatja, a
    // szerverét nem — ha a kettő szétcsúszik, a felhasználó mást lát, mint amit kap.
    // Próbaidőszak, nem havi keret: EGYSZER jár egy telepítésnek, és nem töltődik újra.
    val FREE = TierLimits(
        aiPlans = 3,
        chatMessages = 20,
        maxPlanDays = 3,
        canRefineDays = false,
        canRunChatActions = false,
    )

    val PREMIUM = TierLimits(
        aiPlans = -1,
        chatMessages = -1,
        maxPlanDays = 30,
        canRefineDays = true,
        canRunChatActions = true,
    )

    fun limitsFor(tier: PlanTier): TierLimits = when (tier) {
        PlanTier.FREE -> FREE
        PlanTier.PREMIUM -> PREMIUM
    }

    /** A fizetős csomag érvei — ugyanez a lista jelenik meg a paywallon. */
    fun premiumBenefits(language: AppLanguage): List<String> = when (language) {
        AppLanguage.EN -> listOf(
            "Unlimited meal plans, up to a month ahead",
            "Unlimited chat: change anything in your own words",
            "Rewrite a single day when something comes up",
            "Every new feature, as soon as it ships",
        )
        AppLanguage.HU -> listOf(
            "Korlátlan étrend, akár egy hónapra előre",
            "Korlátlan beszélgetés: bármit átírhatsz szavakkal",
            "Egy-egy nap külön átíratása, ha közbejön valami",
            "Minden új funkció, amint elkészül",
        )
    }

    /** Ami a fizetős csomag nélkül is jár — ezt is kiírjuk, hogy ne érezze csapdának. */
    fun freeBenefits(language: AppLanguage): List<String> = when (language) {
        AppLanguage.EN -> listOf(
            "Meal and weight log, unlimited",
            "Shopping list and meal reminders",
            "Plans from the built-in recipes, even offline",
            "Three full AI meal plans to try it out",
        )
        AppLanguage.HU -> listOf(
            "Étkezés- és súlynapló, korlátlanul",
            "Bevásárlólista és étkezési emlékeztetők",
            "Beépített receptekből készülő étrend, internet nélkül is",
            "Három teljes AI-étrend kipróbálásra",
        )
    }
}

/**
 * Elszámolási időszak. Csomagfüggő, és ez a különbség hordozza az üzleti modellt.
 *
 * Az ingyenes sáv EGYSZERI próbakeret: a kulcsa állandó, tehát soha nem nullázódik.
 * A fizetős sáv naptári hónaponként fordul, mert az előfizetés is havonta fordul.
 *
 * FONTOS: a backend `src/limits.ts` `periodFor` függvénye ugyanezt a szabályt követi,
 * és a döntés ott születik. Ha a kettő szétcsúszik, a felhasználó mást lát, mint amit kap.
 */
object BillingPeriod {

    /** Az ingyenes próbaidőszak állandó kulcsa. Nem dátum: nincs mihez fordulnia. */
    const val TRIAL = "trial"

    fun keyFor(tier: PlanTier, date: LocalDate = LocalDate.now()): String = when (tier) {
        PlanTier.FREE -> TRIAL
        PlanTier.PREMIUM -> "%04d-%02d".format(date.year, date.monthValue)
    }

    /**
     * Hány nap múlva nullázódik a kvóta, vagy null, ha soha.
     *
     * A próbaidőszakra szándékosan null: egy „X nap múlva újra tervezhetsz" üzenet
     * olyasmit ígérne, ami nem jön el, és az apró hazugság viszi el a bizalmat.
     */
    fun daysUntilReset(tier: PlanTier, today: LocalDate = LocalDate.now()): Int? =
        if (tier == PlanTier.FREE) null else today.lengthOfMonth() - today.dayOfMonth + 1
}

/** Használati számlálók az aktuális időszakban. */
data class UsageCounters(
    /** Alapból a próbaidőszak, mert az alapértelmezett csomag is az ingyenes. */
    val periodKey: String = BillingPeriod.TRIAL,
    val aiPlans: Int = 0,
    val chatMessages: Int = 0,
) {
    /**
     * Új időszakban tiszta lappal indulunk.
     *
     * A próbaidőszak kulcsa állandó, tehát ott ez SOHA nem nulláz — pontosan ez tartja
     * egyszerinek az ingyenes keretet. Előfizetés után viszont havonta fordul.
     */
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

    /** A csomaghoz tartozó elszámolási kulcs: próbaidőszak vagy aktuális hónap. */
    val periodKey: String get() = BillingPeriod.keyFor(tier)

    fun remainingPlans(now: String = periodKey): Int =
        if (limits.aiPlans < 0) Int.MAX_VALUE
        else (limits.aiPlans - current(now).aiPlans).coerceAtLeast(0)

    fun remainingMessages(now: String = periodKey): Int =
        if (limits.chatMessages < 0) Int.MAX_VALUE
        else (limits.chatMessages - current(now).chatMessages).coerceAtLeast(0)

    fun canGeneratePlan(now: String = periodKey): Boolean = remainingPlans(now) > 0

    fun canSendMessage(now: String = periodKey): Boolean = remainingMessages(now) > 0

    /** A kérhető terv hossza. Az ingyenes sávban rövidebb, de nem nulla. */
    fun allowedPlanDays(requested: Int): Int = requested.coerceAtMost(limits.maxPlanDays)

    /**
     * Miért nem indulhat a művelet — a felületnek szánt, kész mondat.
     * Null, ha mehet.
     */
    fun blockReason(
        feature: PaidFeature,
        now: String = periodKey,
        language: AppLanguage = AppLanguage.DEFAULT,
    ): String? {
        // Null a próbaidőszakban: ott nincs mire várni, és nem ígérünk olyat, ami
        // nem jön el. Előfizetőnél viszont van értelme kiírni, mikor fordul a hónap.
        val days = BillingPeriod.daysUntilReset(tier)
        val english = language == AppLanguage.EN
        return when (feature) {
            PaidFeature.PLAN_GENERATION ->
                if (canGeneratePlan(now)) null
                else if (days == null) {
                    if (english) "You have used all ${limits.aiPlans} free meal plans. " +
                        "A subscription gives you unlimited plans."
                    else "Elhasználtad mind a ${limits.aiPlans} ingyenes étrended. " +
                        "Az előfizetéssel korlátlanul tervezhetsz."
                } else if (english) "You have used this month's meal plans. " +
                    "Your quota resets in $days days."
                else "Ebben a hónapban elhasználtad az étrendjeidet. " +
                    "A kvóta $days nap múlva nullázódik."

            PaidFeature.CHAT ->
                if (canSendMessage(now)) null
                else if (days == null) {
                    if (english) "You have used all ${limits.chatMessages} free messages. " +
                        "A subscription gives you unlimited chat."
                    else "Elfogyott mind a ${limits.chatMessages} ingyenes üzeneted. " +
                        "Az előfizetéssel korlátlanul beszélgethetsz."
                } else if (english) "You have used up this month's messages. " +
                    "Your quota resets in $days days."
                else "Ebben a hónapban elfogyott az üzenetkereted. " +
                    "A kvóta $days nap múlva nullázódik."

            PaidFeature.CHAT_ACTIONS ->
                if (limits.canRunChatActions) null
                else if (english) "Changes made from chat need a subscription."
                else "A beszélgetésből indított módosításokhoz előfizetés kell."

            PaidFeature.DAY_REFINE ->
                if (limits.canRefineDays) null
                else if (english) "Rewriting a single day needs a subscription."
                else "Egy-egy nap átíratásához előfizetés kell."

            PaidFeature.LONG_PLAN ->
                if (limits.maxPlanDays >= 7) null
                else if (english) "The free plan allows at most ${limits.maxPlanDays} days."
                else "Az ingyenes csomagban legfeljebb ${limits.maxPlanDays} napos terv kérhető."
        }
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
