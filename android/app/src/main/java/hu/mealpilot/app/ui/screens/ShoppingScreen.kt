package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.local.ShoppingItemEntity
import hu.mealpilot.app.data.local.endEpochDay
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.Aisle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Melyik időszakra kérjük a bevásárlólistát. */
enum class ShoppingRange(val label: String) {
    WEEK("Következő 7 nap"),
    ALL("Teljes terv"),
}

data class ShoppingUiState(
    val items: List<ShoppingItemEntity> = emptyList(),
    val range: ShoppingRange = ShoppingRange.WEEK,
    val hasPlan: Boolean = false,
    val rangeLabel: String = "",
) {
    val grouped: Map<Aisle, List<ShoppingItemEntity>>
        get() = items.groupBy { Aisle.fromRaw(it.aisle) }.toSortedMap()

    val checkedCount: Int get() = items.count { it.checked }
}

class ShoppingViewModel(private val container: AppContainer) : ViewModel() {

    private val range = MutableStateFlow(ShoppingRange.WEEK)

    init {
        // Ha új terv lesz aktív vagy változik a tartomány, gondoskodunk róla,
        // hogy legyen mentett lista — a meglévőt (és a pipákat) nem bántjuk.
        viewModelScope.launch {
            combine(container.planRepository.observeActivePlan(), range) { plan, selected -> plan to selected }
                .collect { (plan, selected) ->
                    if (plan != null) {
                        val (start, end) = rangeBounds(plan.startEpochDay, plan.endEpochDay, selected)
                        container.planRepository.rebuildShoppingListIfMissing(plan.id, start, end)
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ShoppingUiState> =
        combine(container.planRepository.observeActivePlan(), range) { plan, selected -> plan to selected }
            .flatMapLatest { (plan, selected) ->
                if (plan == null) {
                    flowOf(ShoppingUiState(range = selected))
                } else {
                    val (start, end) = rangeBounds(plan.startEpochDay, plan.endEpochDay, selected)
                    container.planRepository.observeShoppingList(plan.id, start, end).map { items ->
                        ShoppingUiState(
                            items = items,
                            range = selected,
                            hasPlan = true,
                            rangeLabel = "${LocalDate.ofEpochDay(start)} – ${LocalDate.ofEpochDay(end)}",
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun setRange(value: ShoppingRange) {
        range.value = value
    }

    fun toggle(item: ShoppingItemEntity) = viewModelScope.launch {
        container.planRepository.setShoppingChecked(item.id, !item.checked)
        val plan = container.planRepository.activePlan() ?: return@launch
        container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
    }

    fun uncheckAll() = viewModelScope.launch {
        val plan = container.planRepository.activePlan() ?: return@launch
        val items = state.value.items
        if (items.isEmpty()) return@launch
        container.planRepository.uncheckShoppingList(
            planId = plan.id,
            from = items.first().fromEpochDay,
            to = items.first().toEpochDay,
        )
    }
}

/** A hét nézet a mától (vagy a terv kezdetétől) számított 7 napot fedi le. */
private fun rangeBounds(planStart: Long, planEnd: Long, range: ShoppingRange): Pair<Long, Long> = when (range) {
    ShoppingRange.ALL -> planStart to planEnd
    ShoppingRange.WEEK -> {
        val from = maxOf(planStart, LocalDate.now().toEpochDay()).coerceAtMost(planEnd)
        from to minOf(from + 6, planEnd)
    }
}

@Composable
fun ShoppingScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
) {
    val viewModel: ShoppingViewModel = viewModel(factory = containerFactory(container) { ShoppingViewModel(it) })
    val state by viewModel.state.collectAsState()

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Bevásárlólista", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (!state.hasPlan) {
            item {
                EmptyState(
                    title = "Nincs aktív terv",
                    message = "A bevásárlólista a tervezett ételek hozzávalóiból áll össze — előbb készíts étrendet.",
                )
            }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoppingRange.entries.forEach { option ->
                    FilterChip(
                        selected = state.range == option,
                        onClick = { viewModel.setRange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    "${state.checkedCount} / ${state.items.size} megvan · ${state.rangeLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.items.isEmpty()) 0f
                        else state.checkedCount.toFloat() / state.items.size
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.grouped.forEach { (aisle, aisleItems) ->
            item(key = "aisle-${aisle.name}") {
                Text(
                    aisle.hu,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(aisleItems, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = item.checked, onCheckedChange = { viewModel.toggle(item) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        )
                        if (item.notes.isNotBlank()) {
                            Text(
                                item.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(displayQuantity(item), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { viewModel.uncheckAll() }) { Text("Pipák törlése") }
        }
    }
}

/** A tárolt alapegységet (g/ml) olvasható formára hozza. */
private fun displayQuantity(item: ShoppingItemEntity): String {
    fun trim(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
    return when {
        item.unit == "g" && item.quantity >= 1000 -> "${trim(item.quantity / 1000)} kg"
        item.unit == "ml" && item.quantity >= 1000 -> "${trim(item.quantity / 1000)} l"
        else -> "${trim(item.quantity)} ${item.unit}"
    }
}
