package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.local.ShoppingItemEntity
import hu.mealpilot.app.data.local.endEpochDay
import hu.mealpilot.app.ui.components.CheckCircle
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.GroupLabel
import hu.mealpilot.app.ui.components.NumberText
import hu.mealpilot.app.ui.components.SegmentedToggle
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.theme.LocalDarkTheme
import hu.mealpilot.app.ui.theme.MealColors
import hu.mealpilot.app.ui.theme.PlateShape
import hu.mealpilot.core.ai.Aisle
import hu.mealpilot.core.ai.Units
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.label
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
enum class ShoppingRange(@StringRes val label: Int) {
    WEEK(R.string.shopping_range_week),
    ALL(R.string.shopping_range_all),
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
    val language = LocalAppLanguage.current

    LaunchedEffect(Unit) { container.telemetry.record(TelemetryEvent.SHOPPING_OPENED) }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.shopping_title),
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        if (!state.hasPlan) {
            item {
                EmptyState(
                    title = stringResource(R.string.shopping_empty_title),
                    message = stringResource(R.string.shopping_empty_message),
                )
            }
            return@LazyColumn
        }

        item {
            SegmentedToggle(
                options = ShoppingRange.entries,
                selected = state.range,
                label = { stringResource(it.label) },
                onSelect = { viewModel.setRange(it) },
            )
        }

        item {
            // A haladás saját kártyán: a lista hosszú, és ez az egyetlen szám,
            // amit görgetés közben is tudni akar az ember.
            val total = state.items.size
            val left = total - state.checkedCount
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PlateShape.card,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            stringResource(R.string.shopping_have, state.checkedCount, total),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        NumberText(
                            stringResource(R.string.shopping_left, left),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(9.dp)
                            .clip(PlateShape.pill)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (total == 0) 0f else state.checkedCount.toFloat() / total)
                                .height(9.dp)
                                .clip(PlateShape.pill)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    if (state.rangeLabel.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        NumberText(
                            state.rangeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Polconként egy kártya. Eddig a csoportcím és a tételek külön sorok voltak a
        // háttéren: húsz tétel után nem látszott, hol ér véget egy polc.
        state.grouped.forEach { (aisle, aisleItems) ->
            item(key = "aisle-${aisle.name}") {
                val accent = MealColors.of(aisle.ordinal, LocalDarkTheme.current)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PlateShape.card,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) {
                    Column(Modifier.padding(17.dp)) {
                        GroupLabel(aisle.label(language), accent)
                        aisleItems.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .toggleable(
                                        value = item.checked,
                                        role = Role.Checkbox,
                                        onValueChange = { viewModel.toggle(item) },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CheckCircle(item.checked)
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (item.checked) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
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
                                Spacer(Modifier.width(10.dp))
                                NumberText(
                                    displayQuantity(item, language),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            TextButton(onClick = { viewModel.uncheckAll() }) {
                Text(stringResource(R.string.shopping_clear_checks))
            }
        }
    }
}

/** A tárolt alapegységet (g/ml) olvasható formára hozza, a felület nyelvén. */
private fun displayQuantity(item: ShoppingItemEntity, language: AppLanguage): String {
    fun trim(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
    return when {
        item.unit == "g" && item.quantity >= 1000 -> "${trim(item.quantity / 1000)} kg"
        item.unit == "ml" && item.quantity >= 1000 -> "${trim(item.quantity / 1000)} l"
        else -> "${trim(item.quantity)} ${Units.label(item.unit, item.quantity, language)}"
    }
}
