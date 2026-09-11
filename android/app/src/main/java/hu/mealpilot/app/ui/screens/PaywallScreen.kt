package hu.mealpilot.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.billing.PREMIUM_SUBSCRIPTION_ID
import hu.mealpilot.core.billing.BillingPeriod
import hu.mealpilot.core.billing.Tiers

/**
 * Az app jogi és bolti hivatkozásai. A Play a nyilvános adatvédelmi URL-t kötelezően kéri.
 *
 * FIGYELEM kiadás előtt: a [SITE] jelenleg arra a domainre mutat, amit a repó GitHub Pages
 * oldala kiszolgál — az viszont egy másik projekthez tartozik. Éles kiadáshoz olyan domain
 * kell, amit a MealPilot néven te birtokolsz, és a privacy.html / terms.html oda kerüljön.
 * A fájlok csak a main ágra merge után kerülnek ki, mert a Pages onnan épül.
 */
object LegalLinks {
    const val SITE = "https://hernadicsaba.hu"
    const val PRIVACY = "$SITE/privacy.html"
    const val TERMS = "$SITE/terms.html"
    const val SUPPORT_EMAIL = "mate.teke@gmail.com"

    /**
     * A jogi szövegek aktuális verziója. Ha érdemben változik a feltétel vagy az
     * adatkezelés, ezt emeld — a felhasználótól így újra elfogadást kér az app.
     */
    const val VERSION = "2026-09-11"

    fun manageSubscription(packageName: String) =
        "https://play.google.com/store/account/subscriptions" +
            "?sku=$PREMIUM_SUBSCRIPTION_ID&package=$packageName"
}

/** A Compose kontextusából kiásott Activity — a Play fizetési folyamatához kell. */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun PaywallScreen(
    container: AppContainer,
    reason: String?,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
) {
    val billing by container.billing.state.collectAsState()
    val entitlement by container.entitlements.entitlement.collectAsState(initial = null)
    val context = LocalContext.current

    LaunchedEffect(Unit) { container.billing.refresh() }
    LaunchedEffect(billing.subscribed) {
        if (billing.subscribed) {
            container.telemetry.record(TelemetryEvent.SUBSCRIBED)
            snackbarHostState.showSnackbar("Köszönjük! A teljes csomag aktív.")
            onClose()
        }
    }
    LaunchedEffect(billing.error) {
        billing.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        TextButton(onClick = onClose, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("  Vissza")
        }

        if (reason != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        Text(
            "Teljes csomag",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Az étrend tervezése és a beszélgetés valódi költséggel jár. Az előfizetés ezt " +
                "fedezi — cserébe nincs korlát.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Tiers.premiumBenefits.forEach { benefit ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(benefit, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))

        val price = billing.formattedPrice
        if (price != null) {
            Text(
                price + (billing.billingPeriodLabel?.let { " / $it" } ?: ""),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = {
                container.telemetry.record(TelemetryEvent.PURCHASE_STARTED)
                context.findActivity()?.let { container.billing.launchPurchase(it) }
            },
            enabled = billing.available && price != null && !billing.subscribed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (billing.connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
            }
            Text(
                when {
                    billing.subscribed -> "Már előfizettél"
                    billing.pending -> "Fizetés feldolgozás alatt"
                    !billing.available -> "Az előfizetés most nem elérhető"
                    price == null -> "Ár betöltése…"
                    else -> "Előfizetek"
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { container.billing.restore() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Korábbi vásárlás visszaállítása") }

        Spacer(Modifier.height(12.dp))
        Text(
            "Az előfizetés automatikusan megújul, amíg le nem mondod. A lemondás a Google Play " +
                "előfizetéseknél bármikor elvégezhető, a megújulás előtt legalább 24 órával. " +
                "A fizetés a Google Play fiókodat terheli.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            "Ami előfizetés nélkül is jár",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Tiers.freeBenefits.forEach { benefit ->
            Text("• $benefit", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
        }

        entitlement?.let { current ->
            if (!current.isPremium) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ebben a hónapban még ${current.remainingPlans()} étrend és " +
                        "${current.remainingMessages()} üzenet maradt. " +
                        "A keret ${BillingPeriod.daysUntilReset()} nap múlva nullázódik.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { context.openUrl(LegalLinks.TERMS) }) { Text("Feltételek") }
            TextButton(onClick = { context.openUrl(LegalLinks.PRIVACY) }) { Text("Adatkezelés") }
        }
        Spacer(Modifier.height(32.dp))
    }
}

fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
