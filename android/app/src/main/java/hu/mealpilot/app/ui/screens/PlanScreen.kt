package hu.mealpilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.PlanGenerationOutcome
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.Nutrients
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class PlanUiState(
    val plan: PlanEntity? = null,
    val meals: List<MealWithIngredients> = emptyList(),
    /** Igaz, ha a tervezőszolgáltatás elérhető, tehát egy nap szavakkal átírható. */
    val canRefine: Boolean = false,
) {
    val byDay: Map<Int, List<MealWithIngredients>>
        get() = meals.groupBy { it.meal.dayIndex }.toSortedMap()
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Running(val progress: GenerationProgress) : GenerationState
    data class Failed(val message: String) : GenerationState
    data class Done(val outcome: PlanGenerationOutcome) : GenerationState
}

class PlanViewModel(private val container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlanUiState> = container.planRepository.observeActivePlan()
        .flatMapLatest { plan ->
            if (plan == null) flowOf(PlanUiState(canRefine = container.hasApiKey))
            else container.planRepository.observePlanMeals(plan.id).map { meals ->
                PlanUiState(plan = plan, meals = meals, canRefine = container.hasApiKey)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    private val _generation = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generation: StateFlow<GenerationState> = _generation.asStateFlow()

    private var generationJob: Job? = null

    fun generate(days: Int, startTomorrow: Boolean, freeText: String) {
        if (generationJob?.isActive == true) return
        // Alkalmazás-élettartamú scope: a háttérben készülő napok akkor is elkészülnek,
        // ha a felhasználó közben átvált egy másik fülre.
        generationJob = container.backgroundScope.launch {
            _generation.value = GenerationState.Running(
                GenerationProgress(GenerationProgress.Stage.PREPARING, message = "Indulás…")
            )
            val profile = container.settings.currentProfile()
            val budget = EnergyCalculator.budget(profile)
            val start = if (startTomorrow) LocalDate.now().plusDays(1) else LocalDate.now()

            val result = container.planRepository.generateAndSave(
                ai = container.mealAi(),
                profile = profile,
                budget = budget,
                startDate = start,
                days = days,
                freeText = freeText,
            ) { progress ->
                _generation.value = GenerationState.Running(progress)
            }

            _generation.value = result.fold(
                onSuccess = { outcome ->
                    ReminderRefreshWorker.refreshNow(container.appContext)
                    GenerationState.Done(outcome)
                },
                onFailure = { GenerationState.Failed(it.message ?: "Ismeretlen hiba.") },
            )
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _generation.value = GenerationState.Idle
    }

    fun dismissGenerationResult() {
        _generation.value = GenerationState.Idle
    }

    fun refineDay(dayIndex: Int, instruction: String, onResult: suspend (String) -> Unit) = viewModelScope.launch {
        val plan = container.planRepository.activePlan() ?: return@launch
        val profile = container.settings.currentProfile()
        val budget = EnergyCalculator.budget(profile)
        val result = container.planRepository.refineDay(
            ai = container.mealAi(),
            profile = profile,
            budget = budget,
            planId = plan.id,
            dayIndex = dayIndex,
            instruction = instruction,
        )
        onResult(result.getOrElse { it.message ?: "Nem sikerült módosítani." })
        ReminderRefreshWorker.refreshNow(container.appContext)
    }

    fun coachNotes(plan: PlanEntity): List<String> = PlanRepository.decodeStrings(plan.coachNotesJson)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onOpenMeal: (Long) -> Unit,
) {
    val viewModel: PlanViewModel = viewModel(factory = containerFactory(container) { PlanViewModel(it) })
    val state by viewModel.state.collectAsState()
    val generation by viewModel.generation.collectAsState()
    var showGenerator by remember { mutableStateOf(false) }
    var refineDayIndex by remember { mutableStateOf<Int?>(null) }
    var expandedDay by remember { mutableStateOf<Int?>(0) }

    LaunchedEffect(generation) {
        when (val current = generation) {
            is GenerationState.Running -> {
                // Amint az első napok megvannak, elengedjük a párbeszédet: a terv már
                // használható, a többi nap a háttérben töltődik tovább.
                if (current.progress.daysReady > 0 && showGenerator) {
                    showGenerator = false
                    snackbarHostState.showSnackbar(
                        "${current.progress.daysReady} nap kész — a többi közben töltődik."
                    )
                }
            }
            is GenerationState.Done -> {
                val outcome = current.outcome
                viewModel.dismissGenerationResult()
                showGenerator = false
                snackbarHostState.showSnackbar(
                    if (outcome.isComplete) "Kész az étrended!"
                    else "${outcome.daysSaved} nap készült el a(z) ${outcome.requestedDays}-ból. " +
                        "A többit újra megpróbálhatod."
                )
            }
            is GenerationState.Failed -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.dismissGenerationResult()
            }
            else -> Unit
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Étrend", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(onClick = { showGenerator = true }) { Text("Új terv") }
            }
        }

        val plan = state.plan
        if (plan == null) {
            item {
                EmptyState(
                    title = "Nincs aktív terved",
                    message = "A testadataid és a szabad szöveges kéréseid alapján összeáll egy " +
                        "kalóriadeficites étrend, hozzá bevásárlólista és emlékeztetők.",
                    action = { Button(onClick = { showGenerator = true }) { Text("Terv készítése") } },
                )
            }
        } else {
            item {
                SectionCard(title = plan.title) {
                    if (plan.summary.isNotBlank()) {
                        Text(plan.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("${plan.targetKcal} kcal/nap") })
                        AssistChip(onClick = {}, label = { Text("${plan.dayCount} nap") })
                        AssistChip(onClick = {}, label = { Text("F ${plan.targetProteinG} g") })
                    }
                    val notes = viewModel.coachNotes(plan)
                    if (notes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        notes.forEach { note ->
                            Text("• $note", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Az étrendet gépi tervező állította össze a megadott adataid alapján. " +
                            "Tájékoztató jellegű, nem orvosi tanács.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.byDay.entries.toList(), key = { it.key }) { (dayIndex, meals) ->
                DayCard(
                    startDate = LocalDate.ofEpochDay(plan.startEpochDay),
                    dayIndex = dayIndex,
                    meals = meals,
                    targetKcal = plan.targetKcal,
                    expanded = expandedDay == dayIndex,
                    onToggle = { expandedDay = if (expandedDay == dayIndex) null else dayIndex },
                    onOpenMeal = onOpenMeal,
                    onRefine = { refineDayIndex = dayIndex },
                    refineEnabled = state.canRefine,
                )
            }
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            generation = generation,
            onDismiss = {
                if (generation is GenerationState.Running) viewModel.cancelGeneration()
                showGenerator = false
            },
            onGenerate = { days, tomorrow, text -> viewModel.generate(days, tomorrow, text) },
        )
    }

    refineDayIndex?.let { dayIndex ->
        RefineDialog(
            dayIndex = dayIndex,
            onDismiss = { refineDayIndex = null },
            onSubmit = { instruction ->
                refineDayIndex = null
                viewModel.refineDay(dayIndex, instruction) { message ->
                    snackbarHostState.showSnackbar(message)
                }
            },
        )
    }
}

@Composable
private fun DayCard(
    startDate: LocalDate,
    dayIndex: Int,
    meals: List<MealWithIngredients>,
    targetKcal: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onRefine: () -> Unit,
    refineEnabled: Boolean,
) {
    val total = Nutrients.sum(meals.map { it.meal.nutrients.toNutrients() })
    val date = startDate.plusDays(dayIndex.toLong())

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(date.hungarianLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${total.kcal.roundToInt()} / $targetKcal kcal · F ${total.proteinG.roundToInt()} g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Összecsukás" else "Kinyitás",
                )
            }

            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    meals.forEach { mw ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenMeal(mw.meal.id) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${mw.meal.timeText} · ${MealSlot.fromRaw(mw.meal.slot).hu}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(mw.meal.name, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${mw.meal.nutrients.kcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (refineEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRefine) { Text("Írd át szavakkal") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratorDialog(
    generation: GenerationState,
    onDismiss: () -> Unit,
    onGenerate: (days: Int, startTomorrow: Boolean, freeText: String) -> Unit,
) {
    var days by remember { mutableStateOf(7) }
    var startTomorrow by remember { mutableStateOf(false) }
    var freeText by remember { mutableStateOf("") }
    val running = generation is GenerationState.Running

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(if (running) "Készül a terved…" else "Új étrend") },
        text = {
            Column {
                if (running) {
                    val progress = (generation as GenerationState.Running).progress
                    Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    if (progress.totalChunks > 1) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${progress.currentChunk + 1}. / ${progress.totalChunks} szakasz",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (progress.receivedChars > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${progress.receivedChars} karakter érkezett",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text("Meddig tervezzek?", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3 to "3 nap", 7 to "1 hét", 14 to "2 hét", 30 to "1 hónap").forEach { (value, label) ->
                            FilterChip(
                                selected = days == value,
                                onClick = { days = value },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = startTomorrow, onCheckedChange = { startTomorrow = it })
                        Text("  Holnaptól induljon", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        label = { Text("Mit vegyek figyelembe?") },
                        placeholder = {
                            Text("pl. laktózérzékeny vagyok, nem eszem halat, hétköznap max 20 perc főzés, olcsó alapanyagok")
                        },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Egy hét összeállítása fél-egy perc. A terv a megadott adataid és " +
                            "kéréseid alapján készül, tájékoztató jellegű, nem orvosi tanács.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp))
                }
            } else {
                Button(onClick = { onGenerate(days, startTomorrow, freeText) }) {
                    Text("Generálás")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (running) "Megszakítás" else "Mégse") }
        },
    )
}

@Composable
private fun RefineDialog(
    dayIndex: Int,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${dayIndex + 1}. nap átírása") },
        text = {
            Column {
                Text(
                    "Írd le szavakkal, mit szeretnél másképp. A napi kalória és fehérje cél így is megmarad.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("pl. az ebéd legyen hidegen vihető, a vacsora hústalan") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("Átírás") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
