package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
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
        container.settings.setOnboardingDone(true)
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

    // A mentett értékekkel indulunk, de a szerkesztés közben nem írjuk felül a felhasználót.
    LaunchedEffect(stored) {
        if (profile == null) profile = stored
    }

    val current = profile ?: return
    val budget = EnergyCalculator.budget(current)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Üdv! 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Néhány adat kell ahhoz, hogy az étrended tényleg rólad szóljon. " +
                "Ezek az adatok a telefonodon maradnak.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        ProfileForm(profile = current, onChange = { profile = it })

        Spacer(Modifier.height(20.dp))
        SectionCard(title = "Ez lesz a napi kereted") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("alapanyagcsere", "${budget.bmr}")
                StatChip("napi felhasználás", "${budget.tdee}")
                StatChip("napi cél", "${budget.target.kcal}")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Fehérje ${budget.target.proteinG} g · Szénhidrát ${budget.target.carbsG} g · " +
                    "Zsír ${budget.target.fatG} g",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Várható ütem: ${"%.2f".format(budget.expectedRateKgPerWeek)} kg/hét",
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
            "Az étrendeket gépi tervező állítja össze a megadott adataid alapján. " +
                "Az app tájékoztató jellegű, nem orvosi tanács — ha betegséged van, terhes vagy, " +
                "vagy gyógyszert szedsz, beszéld át orvossal a diétát.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.finish(current) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kezdjük") }
        Spacer(Modifier.height(32.dp))
    }
}
