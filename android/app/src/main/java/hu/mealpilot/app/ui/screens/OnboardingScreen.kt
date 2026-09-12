package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.ui.components.ProfileForm
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.StatChip
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    val storedProfile = container.settings.profile

    fun finish(profile: UserProfile) = viewModelScope.launch {
        container.settings.saveProfile(profile)
        // A Play elvárja, hogy a feltételek és az adatkezelés elfogadása megtörténjen
        // és utólag igazolható legyen — ezért a verziót és az időpontot is eltesszük.
        container.settings.recordConsent(LegalLinks.VERSION)
        container.settings.setOnboardingDone(true)
        container.telemetry.record(TelemetryEvent.ONBOARDING_DONE)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnboardingViewModel = viewModel(
        factory = containerFactory(container) { OnboardingViewModel(it) }
    )
    val stored by viewModel.storedProfile.collectAsState(initial = UserProfile())
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var consented by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // A mentett értékekkel indulunk, de a szerkesztés közben nem írjuk felül a felhasználót.
    LaunchedEffect(stored) {
        if (profile == null) profile = stored
    }

    val current = profile ?: return
    val language = LocalAppLanguage.current
    val budget = EnergyCalculator.budget(current, language)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_welcome),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        ProfileForm(profile = current, onChange = { profile = it })

        Spacer(Modifier.height(20.dp))
        SectionCard(title = stringResource(R.string.budget_title)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(stringResource(R.string.budget_bmr), "${budget.bmr}")
                StatChip(stringResource(R.string.budget_tdee), "${budget.tdee}")
                StatChip(stringResource(R.string.budget_target), "${budget.target.kcal}")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    R.string.budget_macros,
                    budget.target.proteinG,
                    budget.target.carbsG,
                    budget.target.fatG,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.budget_rate, "%.2f".format(budget.expectedRateKgPerWeek)),
                style = MaterialTheme.typography.bodyMedium,
            )
            budget.warnings.forEach { warning ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consented, onCheckedChange = { consented = it })
            Column {
                Text(
                    stringResource(R.string.onboarding_consent),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { context.openUrl(LegalLinks.terms(language)) },
                        enabled = LegalLinks.isConfigured,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(stringResource(R.string.legal_terms_short), style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { context.openUrl(LegalLinks.privacy(language)) },
                        enabled = LegalLinks.isConfigured,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(stringResource(R.string.legal_privacy_short), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.finish(current) },
            enabled = consented,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onboarding_start)) }
        Spacer(Modifier.height(32.dp))
    }
}
