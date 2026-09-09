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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.ui.components.CalorieRing
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.MacroBar
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.energy.EnergyCalculator
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

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: PlanEntity? = null,
    val meals: List<MealWithIngredients> = emptyList(),
    val logs: List<MealLogEntity> = emptyList(),
    val burnedNetKcal: Int = 0,
    val eatBackRatio: Double = 0.5,
) {
    val consumed: Nutrients
        get() = Nutrients.sum(
            logs.filter { it.status == LogStatus.EATEN.name || it.status == LogStatus.EXTRA.name }
                .map { it.nutrients.toNutrients() }
        )

    val baseTarget: Int get() = plan?.targetKcal ?: 0

    /** A mozgással megnövelt napi keret. */
    val adjustedTarget: Int
        get() = if (baseTarget <= 0) 0
        else EnergyCalculator.adjustedDailyKcal(baseTarget, burnedNetKcal, eatBackRatio)

    fun statusOf(mealId: Long): LogStatus? =
        logs.firstOrNull { it.mealId == mealId }?.let { runCatching { LogStatus.valueOf(it.status) }.getOrNull() }
}

class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> = date.flatMapLatest { day ->
        combine(
            container.planRepository.observeActivePlan(),
            container.planRepository.observeDay(day),
            container.trackingRepository.observeMealLogs(day),
            container.database.activityLogDao().observeNetKcal(day.toEpochDay()),
            container.settings.settings,
        ) { plan, meals, logs, burned, settings ->
            TodayUiState(
                date = day,
                plan = plan,
                meals = meals,
                logs = logs,
                burnedNetKcal = burned,
                eatBackRatio = settings.eatBackRatio,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun shiftDay(days: Long) {
        date.value = date.value.plusDays(days)
    }

    fun log(mealId: Long, status: LogStatus) = viewModelScope.launch {
        container.trackingRepository.logPlannedMeal(mealId, status)
        val plan = container.planRepository.activePlan() ?: return@launch
        container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
    }

    fun undo(mealId: Long) = viewModelScope.launch {
        container.database.mealLogDao().deleteForMeal(mealId)
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
                IconButton(onClick = { viewModel.shiftDay(-1) }) { Text("◀") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.date.hungarianLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.plan?.let {
                        Text(it.title, style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { viewModel.shiftDay(1) }) { Text("▶") }
            }
        }

        if (state.plan == null) {
            item {
                EmptyState(
                    title = "Még nincs étrended",
                    message = "Készíts egyet pár másodperc alatt — megadhatod, mit szeretsz, mire van időd, mit nem eszel meg.",
                    action = { Button(onClick = onCreatePlan) { Text("Terv készítése") } },
                )
            }
            return@LazyColumn
        }

        item {
            SectionCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CalorieRing(
                        consumed = consumed.kcal.roundToInt(),
                        budget = state.adjustedTarget,
                        burned = state.burnedNetKcal,
                    )
                }
                Spacer(Modifier.height(16.dp))
                MacroBar("Fehérje", consumed.proteinG, state.plan!!.targetProteinG, Color(0xFF00897B))
                Spacer(Modifier.height(8.dp))
                MacroBar("Szénhidrát", consumed.carbsG, state.plan!!.targetCarbsG, Color(0xFF7CB342))
                Spacer(Modifier.height(8.dp))
                MacroBar("Zsír", consumed.fatG, state.plan!!.targetFatG, Color(0xFFFB8C00))
                Spacer(Modifier.height(8.dp))
                MacroBar("Rost", consumed.fiberG, state.plan!!.targetFiberG, Color(0xFF8D6E63))
            }
        }

        if (state.meals.isEmpty()) {
            item {
                EmptyState(
                    title = "Erre a napra nincs étkezés",
                    message = "A terv nem fedi le ezt a napot. Lépj vissza egy napot, vagy készíts új tervet.",
                )
            }
        }

        items(state.meals, key = { it.meal.id }) { mealWithIngredients ->
            MealRow(
                meal = mealWithIngredients,
                status = state.statusOf(mealWithIngredients.meal.id),
                onOpen = { onOpenMeal(mealWithIngredients.meal.id) },
                onAte = { viewModel.log(mealWithIngredients.meal.id, LogStatus.EATEN) },
                onSkip = { viewModel.log(mealWithIngredients.meal.id, LogStatus.SKIPPED) },
                onUndo = { viewModel.undo(mealWithIngredients.meal.id) },
            )
        }
    }
}

@Composable
private fun MealRow(
    meal: MealWithIngredients,
    status: LogStatus?,
    onOpen: () -> Unit,
    onAte: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
) {
    val slot = MealSlot.fromRaw(meal.meal.slot)
    val n = meal.meal.nutrients

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                LogStatus.EATEN -> MaterialTheme.colorScheme.primaryContainer
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
                Text(slot.hu, style = MaterialTheme.typography.labelSmall)
                Text(
                    meal.meal.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (status == LogStatus.SKIPPED) TextDecoration.LineThrough else null,
                )
                Text(
                    "${n.kcal.roundToInt()} kcal · F ${n.proteinG.roundToInt()} g · " +
                        "Sz ${n.carbsG.roundToInt()} g · Zs ${n.fatG.roundToInt()} g",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == null) {
                FilledTonalIconButton(onClick = onAte) {
                    Icon(Icons.Filled.Check, contentDescription = "Megettem")
                }
                IconButton(onClick = onSkip) {
                    Icon(Icons.Filled.Close, contentDescription = "Kihagytam")
                }
            } else {
                androidx.compose.material3.TextButton(onClick = onUndo) { Text("Vissza") }
            }
        }
    }
}

/** "2026. szeptember 9., szerda" alakú, magyar dátumfelirat. */
fun LocalDate.hungarianLabel(): String {
    val months = listOf(
        "január", "február", "március", "április", "május", "június",
        "július", "augusztus", "szeptember", "október", "november", "december",
    )
    val days = listOf("hétfő", "kedd", "szerda", "csütörtök", "péntek", "szombat", "vasárnap")
    val prefix = when (this) {
        LocalDate.now() -> "Ma · "
        LocalDate.now().plusDays(1) -> "Holnap · "
        LocalDate.now().minusDays(1) -> "Tegnap · "
        else -> ""
    }
    return "$prefix${monthValue.let { months[it - 1] }} $dayOfMonth., ${days[dayOfWeek.value - 1]}"
}
