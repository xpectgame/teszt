package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.ui.dayLabel
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.ui.components.CalorieRing
import hu.mealpilot.app.ui.components.MealEntrySheet
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.MacroBar
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.Nutrients
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Amit tényleg megevett: a tervezett, a helyette megevett és a terven kívüli is. */
private val CONSUMED_STATUSES = setOf(
    LogStatus.EATEN.name,
    LogStatus.REPLACED.name,
    LogStatus.EXTRA.name,
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: PlanEntity? = null,
    val meals: List<MealWithIngredients> = emptyList(),
    val logs: List<MealLogEntity> = emptyList(),
) {
    val consumed: Nutrients
        get() = Nutrients.sum(
            logs.filter { it.status in CONSUMED_STATUSES }.map { it.nutrients.toNutrients() }
        )

    val targetKcal: Int get() = plan?.targetKcal ?: 0

    fun logFor(mealId: Long): MealLogEntity? = logs.firstOrNull { it.mealId == mealId }

    fun statusOf(mealId: Long): LogStatus? =
        logFor(mealId)?.let { runCatching { LogStatus.valueOf(it.status) }.getOrNull() }

    /** A terven kívül felvitt étkezések — ezek nem tartoznak egy tervezett fogáshoz sem. */
    val extras: List<MealLogEntity> get() = logs.filter { it.mealId == null }
}

class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> = date.flatMapLatest { day ->
        combine(
            container.planRepository.observeActivePlan(),
            container.planRepository.observeDay(day),
            container.trackingRepository.observeMealLogs(day),
        ) { plan, meals, logs ->
            TodayUiState(date = day, plan = plan, meals = meals, logs = logs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun shiftDay(days: Long) {
        date.value = date.value.plusDays(days)
    }

    fun log(mealId: Long, status: LogStatus) = viewModelScope.launch {
        container.telemetry.record(TelemetryEvent.MEAL_LOGGED)
        container.trackingRepository.logPlannedMeal(mealId, status)
        val plan = container.planRepository.activePlan() ?: return@launch
        container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
    }

    fun undo(mealId: Long) = viewModelScope.launch {
        container.database.mealLogDao().deleteForMeal(mealId)
    }

    /** „Nem ezt ettem, hanem ezt" — a terv marad, csak a napló lesz pontos. */
    fun logReplaced(mealId: Long, name: String, nutrients: Nutrients) = viewModelScope.launch {
        container.telemetry.record(TelemetryEvent.MEAL_LOGGED)
        container.trackingRepository.logReplacedMeal(mealId, name, nutrients)
        refreshAchievements()
    }

    fun logExtra(name: String, nutrients: Nutrients) = viewModelScope.launch {
        container.telemetry.record(TelemetryEvent.MEAL_LOGGED)
        container.trackingRepository.logCustomMeal(date.value, name, nutrients)
        refreshAchievements()
    }

    fun deleteLog(id: Long) = viewModelScope.launch {
        container.trackingRepository.deleteMealLog(id)
    }

    private suspend fun refreshAchievements() {
        val plan = container.planRepository.activePlan() ?: return
        container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
    }
}

@Composable
fun TodayScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onOpenMeal: (Long) -> Unit,
    onCreatePlan: () -> Unit,
) {
    val viewModel: TodayViewModel = viewModel(
        factory = containerFactory(container) { TodayViewModel(it) }
    )
    val state by viewModel.state.collectAsState()
    val consumed = state.consumed
    // A tervet EGYSZER olvassuk ki, és a lambdák ezt a helyi értéket látják.
    // A LazyColumn építője azonnal fut, az `item { }` tartalma viszont csak később:
    // ha közben a terv eltűnik (adattörlés, tervcsere), a lambdán belüli újraolvasás
    // már nullát adna, és a lenti makrósor összeomlana rajta.
    val plan = state.plan
    var replacing by remember { mutableStateOf<MealWithIngredients?>(null) }
    var addingExtra by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.shiftDay(-1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.today_prev_day))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.date.dayLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (plan != null) {
                        Text(plan.title, style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { viewModel.shiftDay(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.today_next_day))
                }
            }
        }

        if (plan == null) {
            item {
                EmptyState(
                    title = stringResource(R.string.today_no_plan_title),
                    message = stringResource(R.string.today_no_plan_message),
                    action = { Button(onClick = onCreatePlan) { Text(stringResource(R.string.today_create_plan)) } },
                )
            }
            return@LazyColumn
        }

        item {
            SectionCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CalorieRing(
                        consumed = consumed.kcal.roundToInt(),
                        budget = state.targetKcal,
                    )
                }
                Spacer(Modifier.height(16.dp))
                MacroBar(stringResource(R.string.macro_protein), consumed.proteinG, plan.targetProteinG, Color(0xFF00897B))
                Spacer(Modifier.height(8.dp))
                MacroBar(stringResource(R.string.macro_carbs), consumed.carbsG, plan.targetCarbsG, Color(0xFF7CB342))
                Spacer(Modifier.height(8.dp))
                MacroBar(stringResource(R.string.macro_fat), consumed.fatG, plan.targetFatG, Color(0xFFFB8C00))
                Spacer(Modifier.height(8.dp))
                MacroBar(stringResource(R.string.macro_fiber), consumed.fiberG, plan.targetFiberG, Color(0xFF8D6E63))
            }
        }

        if (state.meals.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.today_no_meals_title),
                    message = stringResource(R.string.today_no_meals_message),
                )
            }
        }

        items(state.meals, key = { it.meal.id }) { mealWithIngredients ->
            MealRow(
                meal = mealWithIngredients,
                log = state.logFor(mealWithIngredients.meal.id),
                status = state.statusOf(mealWithIngredients.meal.id),
                onOpen = { onOpenMeal(mealWithIngredients.meal.id) },
                onAte = { viewModel.log(mealWithIngredients.meal.id, LogStatus.EATEN) },
                onSkip = { viewModel.log(mealWithIngredients.meal.id, LogStatus.SKIPPED) },
                onReplace = { replacing = mealWithIngredients },
                onUndo = { viewModel.undo(mealWithIngredients.meal.id) },
            )
        }

        items(state.extras, key = { "extra-${it.id}" }) { extra ->
            ExtraRow(log = extra, onDelete = { viewModel.deleteLog(extra.id) })
        }

        item {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { addingExtra = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.today_add_extra))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    replacing?.let { target ->
        MealEntrySheet(
            title = stringResource(R.string.today_ate_something_else),
            initialName = target.meal.name,
            initialNutrients = target.meal.nutrients.toNutrients(),
            confirmLabel = stringResource(R.string.today_i_ate_this),
            onDismiss = { replacing = null },
            onSave = { name, nutrients ->
                viewModel.logReplaced(target.meal.id, name, nutrients)
                replacing = null
            },
        )
    }

    if (addingExtra) {
        MealEntrySheet(
            title = stringResource(R.string.today_extra_meal),
            confirmLabel = stringResource(R.string.today_add),
            onDismiss = { addingExtra = false },
            onSave = { name, nutrients ->
                viewModel.logExtra(name, nutrients)
                addingExtra = false
            },
        )
    }
}

/** Terven kívül felvitt étkezés a napi listán. */
@Composable
private fun ExtraRow(log: MealLogEntity, onDelete: () -> Unit) {
    val n = log.nutrients
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_off_plan), style = MaterialTheme.typography.labelSmall)
                Text(log.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.macro_line,
                        n.kcal.roundToInt(),
                        n.proteinG.roundToInt(),
                        n.carbsG.roundToInt(),
                        n.fatG.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun MealRow(
    meal: MealWithIngredients,
    log: MealLogEntity?,
    status: LogStatus?,
    onOpen: () -> Unit,
    onAte: () -> Unit,
    onSkip: () -> Unit,
    onReplace: () -> Unit,
    onUndo: () -> Unit,
) {
    val slot = MealSlot.fromRaw(meal.meal.slot).label(LocalAppLanguage.current)
    val replaced = status == LogStatus.REPLACED
    // Felülírásnál azt mutatjuk, amit tényleg megevett — nem azt, amit terveztünk.
    val shownName = if (replaced) log?.name.orEmpty().ifBlank { meal.meal.name } else meal.meal.name
    val n = if (replaced && log != null) log.nutrients else meal.meal.nutrients
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                LogStatus.EATEN, LogStatus.REPLACED -> MaterialTheme.colorScheme.primaryContainer
                LogStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(meal.meal.timeText, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (replaced) stringResource(R.string.today_slot_replaced, slot) else slot,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    shownName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (status == LogStatus.SKIPPED) TextDecoration.LineThrough else null,
                )
                Text(
                    stringResource(
                        R.string.macro_line,
                        n.kcal.roundToInt(),
                        n.proteinG.roundToInt(),
                        n.carbsG.roundToInt(),
                        n.fatG.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == null) {
                FilledTonalIconButton(onClick = onAte) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.today_ate_it))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_ate_other)) },
                            onClick = { menuOpen = false; onReplace() },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_skipped)) },
                            onClick = { menuOpen = false; onSkip() },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                        )
                    }
                }
            } else {
                androidx.compose.material3.TextButton(onClick = onUndo) { Text(stringResource(R.string.action_undo)) }
            }
        }
    }
}

