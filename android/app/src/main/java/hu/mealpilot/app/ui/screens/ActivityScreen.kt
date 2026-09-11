package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.local.ActivityLogEntity
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.StatChip
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.energy.ExerciseCalculator
import hu.mealpilot.core.energy.ExerciseType
import hu.mealpilot.core.energy.MetTable
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ActivityUiState(
    val profile: UserProfile = UserProfile(),
    val today: List<ActivityLogEntity> = emptyList(),
    val week: List<ActivityLogEntity> = emptyList(),
) {
    val todayNetKcal: Int get() = today.sumOf { it.kcalNet }
    val weekMinutes: Int get() = week.sumOf { it.minutes }
    val weekNetKcal: Int get() = week.sumOf { it.kcalNet }
}

class ActivityViewModel(private val container: AppContainer) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivityUiState> = today.flatMapLatest { day ->
        combine(
            container.settings.profile,
            container.trackingRepository.observeActivityLogs(day),
            container.trackingRepository.observeActivityRange(day.minusDays(6), day),
        ) { profile, todayLogs, weekLogs ->
            ActivityUiState(profile = profile, today = todayLogs, week = weekLogs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    /** Élő becslés a felvitel előtt, hogy a felhasználó lássa, mit fog naplózni. */
    fun preview(exercise: ExerciseType, minutes: Int, heartRate: Int?): ExerciseCalculator.Result =
        ExerciseCalculator.estimate(state.value.profile, exercise, minutes, heartRate)

    fun log(exerciseKey: String, minutes: Int, heartRate: Int?, onDone: suspend (String) -> Unit) =
        viewModelScope.launch {
            container.telemetry.record(TelemetryEvent.ACTIVITY_LOGGED)
            val profile = container.settings.currentProfile()
            val result = container.trackingRepository.logActivity(
                profile = profile,
                date = today.value,
                exerciseKey = exerciseKey,
                minutes = minutes,
                avgHeartRate = heartRate,
            )
            val plan = container.planRepository.activePlan()
            if (plan != null) {
                container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
            }
            onDone(
                if (result == null) "Ismeretlen mozgástípus."
                else "Elmentve: ${result.kcalNet} kcal többlet (${result.kcalGross} kcal összesen)."
            )
        }

    fun delete(id: Long) = viewModelScope.launch { container.trackingRepository.deleteActivity(id) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val viewModel: ActivityViewModel = viewModel(factory = containerFactory(container) { ActivityViewModel(it) })
    val state by viewModel.state.collectAsState()

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(MetTable.byKey("walk_brisk")) }
    var minutesText by remember { mutableStateOf("30") }
    var heartRateText by remember { mutableStateOf("") }

    val minutes = minutesText.toIntOrNull() ?: 0
    val heartRate = heartRateText.toIntOrNull()
    val preview = selected?.let { viewModel.preview(it, minutes, heartRate) }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Vissza")
            }
            Text("Mozgás", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("ma elégetve", "${state.todayNetKcal} kcal")
                StatChip("7 nap perc", "${state.weekMinutes} p")
                StatChip("7 nap kcal", "${state.weekNetKcal}")
            }
        }

        item {
            SectionCard(title = "Új mozgás felvitele") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Keresés (pl. futás, kerékpár)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetTable.search(query).take(14).forEach { exercise ->
                        FilterChip(
                            selected = selected?.key == exercise.key,
                            onClick = { selected = exercise },
                            label = { Text(exercise.hu) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Perc") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heartRateText,
                        onValueChange = { heartRateText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Átlagpulzus") },
                        placeholder = { Text("opcionális") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (preview != null && minutes > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Becslés: ${preview.kcalNet} kcal többlet (összesen ${preview.kcalGross} kcal)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        preview.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (heartRate == null) {
                        Text(
                            "Tipp: az átlagpulzus megadásával lényegesen pontosabb a becslés.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val key = selected?.key ?: return@Button
                        viewModel.log(key, minutes, heartRate) { message ->
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    enabled = selected != null && minutes > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Naplózás") }
            }
        }

        item {
            Text("Mai mozgások", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (state.today.isEmpty()) {
            item {
                Text(
                    "Ma még nem naplóztál mozgást.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.today, key = { it.id }) { log ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(log.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${log.minutes} perc · ${log.kcalNet} kcal többlet" +
                                (log.avgHeartRate?.let { " · $it bpm" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.delete(log.id) }) { Text("Törlés") }
                }
            }
        }
    }
}
