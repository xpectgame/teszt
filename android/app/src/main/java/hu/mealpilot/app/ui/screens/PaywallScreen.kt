package hu.mealpilot.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.mealpilot.app.BuildConfig
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.AppContainer
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.app.R
import hu.mealpilot.app.ui.components.BackButton
import hu.mealpilot.app.ui.components.NumberText
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.SectionHeading
import hu.mealpilot.app.ui.theme.PlateShape
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.billing.PREMIUM_SUBSCRIPTION_ID
import hu.mealpilot.core.billing.BillingPeriod
import hu.mealpilot.core.billing.Tiers

/**
 * Az app jogi és bolti hivatkozásai. A Play a nyilvános adatvédelmi URL-t kötelezően kéri,
 * és a generatív AI nyilatkozathoz az adattörlési meg a támogatási címet is elvárja.
 *
 * Az oldalak a repó `mealpilot/` könyvtárában vannak; a cím fordításkor állítható
 * (MEALPILOT_SITE_URL), mert a domain a kiadás előtt még változni fog. A lépések és az
 * alapértelmezés korlátai: docs/DOMAIN.md.
 */
object LegalLinks {
    /**
     * A jogi oldalak címe. Fordításkor állítható (MEALPILOT_SITE_URL), mert a domain a
     * kiadás előtt még változni fog — így nem kell kódot módosítani hozzá.
     */
    val SITE: String = BuildConfig.SITE_URL

    /**
     * Az angol oldalak külön könyvtárban élnek, a magyarok a gyökérben.
     *
     * A jogi szöveget azon a nyelven kell megmutatni, amit a felhasználó ért — egy
     * elfogadó pipa magyar feltételek alatt egy angolul olvasó embertől nem ér semmit.
     */
    /**
     * Hamis, ha a build nem tudja, hol vannak a jogi oldalak.
     *
     * Ilyenkor a gombok letiltva jelennek meg. Egy beégetett tartalék cím kényelmesebb
     * lenne, de az csendben túlélné a kiadást — a kiadási build ezért inkább el sem
     * készül cím nélkül (lásd app/build.gradle.kts).
     */
    val isConfigured: Boolean get() = SITE.isNotBlank()

    private fun prefix(language: AppLanguage) = if (language == AppLanguage.EN) "en/" else ""

    fun privacy(language: AppLanguage) = "$SITE/${prefix(language)}privacy.html"
    fun terms(language: AppLanguage) = "$SITE/${prefix(language)}terms.html"
    fun support(language: AppLanguage) = "$SITE/${prefix(language)}support.html"
    fun deleteData(language: AppLanguage) = "$SITE/${prefix(language)}delete-data.html"

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
    val language = LocalAppLanguage.current

    LaunchedEffect(Unit) { container.billing.refresh() }
    LaunchedEffect(billing.subscribed) {
        if (billing.subscribed) {
            container.telemetry.record(TelemetryEvent.SUBSCRIBED)
            snackbarHostState.showSnackbar(context.getString(R.string.paywall_thanks))
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
        BackButton(
            text = stringResource(R.string.action_back),
            onClick = onClose,
            modifier = Modifier.padding(top = 4.dp),
        )

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
            stringResource(R.string.settings_go_premium),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.paywall_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(22.dp))

        // Az ár, az előnyök és a gomb EGY zöld lapon. Eddig három külön szakasz
        // volt, köztük térközzel: a felhasználónak magának kellett összeraknia,
        // hogy az ár melyik listához tartozik.
        val price = billing.formattedPrice
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = PlateShape.hero,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Column(Modifier.padding(24.dp)) {
                if (price != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        NumberText(price, style = MaterialTheme.typography.displayMedium)
                        billing.billingPeriodLabel?.let {
                            Spacer(Modifier.size(9.dp))
                            Text(
                                "/ $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Tiers.premiumBenefits(LocalAppLanguage.current).forEach { benefit ->
                    Text(
                        benefit,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        container.telemetry.record(TelemetryEvent.PURCHASE_STARTED)
                        context.findActivity()?.let { container.billing.launchPurchase(it) }
                    },
                    enabled = billing.available && price != null && !billing.subscribed,
                    shape = PlateShape.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (billing.connecting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        when {
                            billing.subscribed -> stringResource(R.string.paywall_already)
                            billing.pending -> stringResource(R.string.paywall_pending)
                            !billing.available -> stringResource(R.string.paywall_unavailable)
                            price == null -> stringResource(R.string.paywall_loading_price)
                            else -> stringResource(R.string.paywall_subscribe)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    stringResource(R.string.paywall_renewal_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { container.billing.restore() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.paywall_restore)) }

        Spacer(Modifier.height(20.dp))
        SectionHeading(stringResource(R.string.paywall_free_header))
        Spacer(Modifier.height(12.dp))
        // Ami előfizetés nélkül is jár, saját kártyán, pipával. A pontokból álló
        // lista úgy nézett ki, mint egy lábjegyzet — pedig ez a felhasználó
        // biztosítéka arról, hogy fizetés nélkül sem veszít el semmit.
        SectionCard {
            Tiers.freeBenefits(LocalAppLanguage.current).forEach { benefit ->
                Row(
                    Modifier.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        entitlement?.let { current ->
            if (!current.isPremium) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(
                        R.string.settings_quota_left,
                        current.remainingPlans(),
                        current.remainingMessages(),
                        BillingPeriod.daysUntilReset(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                enabled = LegalLinks.isConfigured,
                onClick = { context.openUrl(LegalLinks.terms(language)) },
            ) {
                Text(stringResource(R.string.legal_terms_short))
            }
            TextButton(
                enabled = LegalLinks.isConfigured,
                onClick = { context.openUrl(LegalLinks.privacy(language)) },
            ) {
                Text(stringResource(R.string.legal_privacy_short))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
