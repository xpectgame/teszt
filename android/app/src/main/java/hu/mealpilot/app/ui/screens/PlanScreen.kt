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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
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
import hu.mealpilot.app.work.GenerationCoordinator
import hu.mealpilot.core.billing.Tiers
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.ReportDialog
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
import kotlinx.coroutines.flow.combine
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
    /** Igaz, ha a tervezőszolgáltatás elérhető ÉS a csomag engedi a napok átírását. */
    val canRefine: Boolean = false,
    /** A csomagban kérhető leghosszabb terv. */
    val maxPlanDays: Int = Tiers.PREMIUM.maxPlanDays,
) {
    val byDay: Map<Int, List<MealWithIngredients>>
        get() = meals.groupBy { it.meal.dayIndex }.toSortedMap()
}

class PlanViewModel(private val container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlanUiState> = combine(
        container.planRepository.observeActivePlan(),
        container.entitlements.entitlement,
    ) { plan, entitlement -> plan to entitlement }
        .flatMapLatest { (plan, entitlement) ->
            val base = PlanUiState(
                canRefine = container.hasPlanner && entitlement.limits.canRefineDays,
                maxPlanDays = entitlement.limits.maxPlanDays,
            )
            if (plan == null) flowOf(base)
            else container.planRepository.observePlanMeals(plan.id).map { meals ->
                base.copy(plan = plan, meals = meals)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun requestPaywall(reason: String) = container.generation.requestPaywall(reason)

    /** A tervezés állapota — ugyanaz a forrás, amit az alkalmazás összes képernyője figyel. */
    val status: StateFlow<GenerationCoordinator.Status> = container.generation.status
    val outcome: StateFlow<Result<PlanGenerationOutcome>?> = container.generation.lastOutcome

    fun generate(days: Int, startTomorrow: Boolean, freeText: String) =
        container.generation.generatePlan(days, startTomorrow, freeText)

    fun cancelGeneration() = container.generation.cancel()

    fun consumeOutcome() = container.generation.consumeOutcome()

    fun refineDay(dayIndex: Int, instruction: String, onResult: suspend (String) -> Unit) =
        viewModelScope.launch {
            container.telemetry.record(TelemetryEvent.DAY_REFINED)
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
    val status by viewModel.status.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    var showGenerator by remember { mutableStateOf(false) }
    var refineDayIndex by remember { mutableStateOf<Int?>(null) }
    var reporting by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var expandedDay by remember { mutableStateOf<Int?>(0) }

    // Amint az első napok megvannak, elengedjük a párbeszédet: a terv már használható,
    // a többi a háttérben töltődik tovább, és a felső sávon végig látszik a haladás.
    LaunchedEffect(status.hasUsableDays, status.running) {
        if (status.running && status.hasUsableDays && showGenerator) {
            showGenerator = false
            snackbarHostState.showSnackbar("${status.daysReady} nap kész — a többi közben töltődik.")
        }
    }

    LaunchedEffect(outcome) {
        val current = outcome ?: return@LaunchedEffect
        showGenerator = false
        viewModel.consumeOutcome()
        snackbarHostState.showSnackbar(
            current.fold(
                onSuccess = {
                    when {
                        // A sablonos terv is terv, de a felhasználónak joga van tudni,
                        // hogy nem azt kapta, amit kért.
                        it.usedFallback ->
                            "Az étrend elkészült, de a tervező nem volt elérhető — " +
                                "a hiányzó napok sablonból készültek."
                        it.isComplete -> "Kész az étrended!"
                        else -> "${it.daysSaved} nap készült el a(z) ${it.requestedDays}-ból."
                    }
                },
                onFailure = { it.message ?: "Nem sikerült elkészíteni a tervet." },
            )
        )
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
                    TextButton(
                        onClick = { reporting = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Hibás vagy zavaró? Jelentsd.") }
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
            status = status,
            maxDays = state.maxPlanDays,
            onLocked = { viewModel.requestPaywall(it) },
            onDismiss = {
                if (status.running) viewModel.cancelGeneration()
                showGenerator = false
            },
            onGenerate = { days, tomorrow, text -> viewModel.generate(days, tomorrow, text) },
        )
    }

    if (reporting) {
        ReportDialog(
            container = container,
            kind = ReportKind.PLAN,
            payload = state.plan?.let { plan -> planReportPayload(plan, state.byDay) },
            onDismiss = { reporting = false },
            onResult = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
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
    status: GenerationCoordinator.Status,
    maxDays: Int,
    onLocked: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: (days: Int, startTomorrow: Boolean, freeText: String) -> Unit,
) {
    var days by remember { mutableStateOf(minOf(7, maxDays)) }
    var startTomorrow by remember { mutableStateOf(false) }
    var freeText by remember { mutableStateOf("") }
    val running = status.running

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(if (running) "Készül a terved…" else "Új étrend") },
        text = {
            Column {
                if (running) {
                    Text(status.detail, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    val fraction = status.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Nyugodtan zárd be — a háttérben tovább készül, és szólok, ha megvan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Meddig tervezzek?", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3 to "3 nap", 7 to "1 hét", 14 to "2 hét", 30 to "1 hónap").forEach { (value, label) ->
                            val locked = value > maxDays
                            FilterChip(
                                selected = days == value && !locked,
                                onClick = {
                                    if (locked) {
                                        onLocked(
                                            "Az ingyenes csomagban legfeljebb $maxDays napos terv kérhető."
                                        )
                                    } else {
                                        days = value
                                    }
                                },
                                label = { Text(label) },
                                leadingIcon = if (locked) {
                                    { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
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
            TextButton(onClick = onDismiss) { Text(if (running) "Háttérbe" else "Mégse") }
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

/**
 * A bejelentéshez küldött összefoglaló.
 *
 * Szándékosan csak az, ami a hiba megítéléséhez kell: fogásnevek és tápértékek. Név,
 * testsúly, étkezési napló nem megy ki — azok nem segítenének, és nem is ránk tartoznak.
 */
private fun planReportPayload(plan: PlanEntity, byDay: Map<Int, List<MealWithIngredients>>): String =
    buildString {
        appendLine("${plan.title} — ${plan.dayCount} nap, cél ${plan.targetKcal} kcal/nap")
        byDay.entries.take(7).forEach { (dayIndex, meals) ->
            appendLine("${dayIndex + 1}. nap:")
            meals.forEach { item ->
                val meal = item.meal
                appendLine("  - ${meal.name} (${meal.nutrients.kcal.roundToInt()} kcal)")
            }
        }
    }
