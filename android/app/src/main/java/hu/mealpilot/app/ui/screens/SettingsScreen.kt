package hu.mealpilot.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.prefs.AiEffort
import hu.mealpilot.app.data.prefs.AiModel
import hu.mealpilot.app.data.prefs.AppSettings
import hu.mealpilot.app.notify.MealAlarmScheduler
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.ui.components.ProfileForm
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings
    val profile = container.settings.profile

    fun maskedKey(): String? = container.secureKeyStore.maskedApiKey()
    fun usingInsecureFallback(): Boolean = container.secureKeyStore.usingFallback

    fun saveApiKey(value: String) {
        container.secureKeyStore.setApiKey(value)
    }

    fun clearApiKey() {
        container.secureKeyStore.setApiKey(null)
    }

    fun saveSettings(settings: AppSettings) = viewModelScope.launch {
        container.settings.saveSettings(settings)
        ReminderRefreshWorker.refreshNow(container.appContext)
    }

    fun saveProfile(profile: UserProfile) = viewModelScope.launch {
        container.settings.saveProfile(profile)
        ReminderRefreshWorker.refreshNow(container.appContext)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = containerFactory(container) { SettingsViewModel(it) })
    val settings by viewModel.settings.collectAsState(initial = null)
    val storedProfile by viewModel.profile.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKeyInput by remember { mutableStateOf("") }
    var maskedKey by remember { mutableStateOf(viewModel.maskedKey()) }
    var editedProfile by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(storedProfile) {
        if (editedProfile == null && storedProfile != null) editedProfile = storedProfile
    }

    val current = settings ?: return
    val profile = editedProfile ?: return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Vissza") }
        Text("Beállítások", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionCard(title = "AI hozzáférés") {
            Text(
                "Az étrendet az Anthropic Claude modellje írja. A kulcsod a telefonodon marad, " +
                    "titkosítva, és nem kerül felhőmentésbe. Kulcsot a console.anthropic.com oldalon kapsz.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (viewModel.usingInsecureFallback()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ A titkosított tároló nem érhető el ezen az eszközön, ezért a kulcs " +
                        "egyszerű tárolóba került. Csak akkor add meg, ha ezt elfogadod.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            maskedKey?.let {
                Text("Jelenlegi kulcs: $it", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it.trim() },
                label = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.saveApiKey(apiKeyInput)
                        maskedKey = viewModel.maskedKey()
                        apiKeyInput = ""
                        scope.launch { snackbarHostState.showSnackbar("Kulcs elmentve.") }
                    },
                    enabled = apiKeyInput.length > 20,
                ) { Text("Mentés") }
                if (maskedKey != null) {
                    OutlinedButton(onClick = {
                        viewModel.clearApiKey()
                        maskedKey = null
                        scope.launch { snackbarHostState.showSnackbar("Kulcs törölve.") }
                    }) { Text("Törlés") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Modell", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiModel.entries.forEach { model ->
                    FilterChip(
                        selected = current.model == model,
                        onClick = { viewModel.saveSettings(current.copy(model = model)) },
                        label = { Text(model.label) },
                    )
                }
            }
            Text(
                current.model.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Text("Alaposság", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiEffort.entries.forEach { effort ->
                    FilterChip(
                        selected = current.effort == effort,
                        onClick = { viewModel.saveSettings(current.copy(effort = effort)) },
                        label = { Text(effort.label) },
                    )
                }
            }
            Text(
                "Az alaposabb beállítás pontosabban tartja a kalóriakeretet, de több tokent használ.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Emlékeztetők") {
            SettingSwitch(
                label = "Étkezési emlékeztetők",
                checked = current.remindersEnabled,
                onChange = { viewModel.saveSettings(current.copy(remindersEnabled = it)) },
            )
            SettingSwitch(
                label = "Esti összefoglaló",
                checked = current.dailySummaryEnabled,
                onChange = { viewModel.saveSettings(current.copy(dailySummaryEnabled = it)) },
            )
            Spacer(Modifier.height(8.dp))
            // A csúszkákat helyi állapoton mozgatjuk, és csak elengedéskor mentünk —
            // különben minden pixelnyi mozgás külön DataStore írást indítana.
            var summaryHour by remember(current.dailySummaryHour) {
                mutableStateOf(current.dailySummaryHour.toFloat())
            }
            Text("Összefoglaló ideje: ${summaryHour.toInt()}:00", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = summaryHour,
                onValueChange = { summaryHour = it },
                onValueChangeFinished = {
                    viewModel.saveSettings(current.copy(dailySummaryHour = summaryHour.toInt()))
                },
                valueRange = 16f..23f,
                steps = 6,
            )
            Spacer(Modifier.height(8.dp))
            var leadMinutes by remember(current.reminderLeadMinutes) {
                mutableStateOf(current.reminderLeadMinutes.toFloat())
            }
            Text("Előre szóljon: ${leadMinutes.toInt()} perccel", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = leadMinutes,
                onValueChange = { leadMinutes = it },
                onValueChangeFinished = {
                    viewModel.saveSettings(current.copy(reminderLeadMinutes = leadMinutes.toInt()))
                },
                valueRange = 0f..60f,
                steps = 11,
            )

            if (!MealAlarmScheduler.canScheduleExact(context)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "A pontos időzítés nincs engedélyezve, ezért az emlékeztetők ±10 percen belül szólnak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Pontos időzítés engedélyezése") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Mozgás beszámítása") {
            var eatBack by remember(current.eatBackRatio) { mutableStateOf(current.eatBackRatio.toFloat()) }
            Text(
                "Az elégetett kalória ${(eatBack * 100).toInt()}%-a írható vissza a napi keretbe.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = eatBack,
                onValueChange = { eatBack = it },
                onValueChangeFinished = {
                    viewModel.saveSettings(current.copy(eatBackRatio = (eatBack * 20).toInt() / 20.0))
                },
                valueRange = 0f..1f,
                steps = 19,
            )
            Text(
                "Az edzésbecslések jellemzően felülbecsülnek, ezért az 50% biztonságos alapérték.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Profil") {
            ProfileForm(profile = profile, onChange = { editedProfile = it })
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.saveProfile(profile)
                    scope.launch { snackbarHostState.showSnackbar("Profil mentve. Az új kalóriakeret a következő tervnél él.") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Profil mentése") }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
