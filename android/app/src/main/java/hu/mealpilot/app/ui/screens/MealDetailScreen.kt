package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.ui.components.ReportDialog
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.StatChip
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.Aisle
import hu.mealpilot.core.ai.Units
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.ai.MealSlot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MealDetailViewModel(
    private val container: AppContainer,
    mealId: Long,
) : ViewModel() {

    val meal: StateFlow<MealWithIngredients?> = container.planRepository.observeMeal(mealId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun log(mealId: Long, status: LogStatus) = viewModelScope.launch {
        container.telemetry.record(TelemetryEvent.MEAL_LOGGED)
        container.trackingRepository.logPlannedMeal(mealId, status)
        val plan = container.planRepository.activePlan() ?: return@launch
        container.statsRepository.refreshAndCollectNew(plan.targetKcal, plan.targetProteinG)
    }
}

@Composable
fun MealDetailScreen(
    container: AppContainer,
    mealId: Long,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val viewModel: MealDetailViewModel = viewModel(
        key = "meal-$mealId",
        factory = containerFactory(container) { MealDetailViewModel(it, mealId) },
    )
    val mealWithIngredients by viewModel.meal.collectAsState()
    val scope = rememberCoroutineScope()
    var reporting by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }

        val data = mealWithIngredients
        if (data == null) {
            Text(stringResource(R.string.meal_gone), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val meal = data.meal
        val n = meal.nutrients

        Text(
            MealSlot.fromRaw(meal.slot).label(language) + " · " + meal.timeText,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(meal.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (meal.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(meal.description, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(stringResource(R.string.meal_calories), "${n.kcal.roundToInt()}")
            StatChip(stringResource(R.string.meal_prep), stringResource(R.string.meal_minutes, meal.prepMinutes))
            StatChip(stringResource(R.string.meal_servings), "${meal.servings}")
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = stringResource(R.string.meal_nutrition)) {
            NutrientRow(stringResource(R.string.macro_protein), "${n.proteinG.roundToInt()} g")
            NutrientRow(stringResource(R.string.macro_carbs), "${n.carbsG.roundToInt()} g")
            NutrientRow("  " + stringResource(R.string.meal_of_which_sugar), "${n.sugarG.roundToInt()} g")
            NutrientRow(stringResource(R.string.macro_fat), "${n.fatG.roundToInt()} g")
            NutrientRow("  " + stringResource(R.string.meal_of_which_saturated), "${n.saturatedFatG.roundToInt()} g")
            NutrientRow(stringResource(R.string.macro_fiber), "${n.fiberG.roundToInt()} g")
            NutrientRow(stringResource(R.string.macro_sodium), "${n.sodiumMg.roundToInt()} mg")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NutrientRow(
                stringResource(R.string.meal_energy_from_macros),
                "${n.toNutrients().kcalFromMacros.roundToInt()} kcal",
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.meal_ingredients)) {
            data.ingredients.groupBy { Aisle.fromRaw(it.aisle) }.forEach { (aisle, items) ->
                Text(
                    aisle.label(language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                items.forEach { ing ->
                    val quantity = if (ing.quantity % 1.0 == 0.0) ing.quantity.toInt().toString()
                    else ing.quantity.toString()
                    NutrientRow(
                        ing.name.replaceFirstChar(Char::uppercaseChar) +
                            if (ing.note.isNotBlank()) " (${ing.note})" else "",
                        "$quantity ${Units.label(ing.unit, ing.quantity, language)}",
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        val steps = PlanRepository.decodeStrings(meal.recipeStepsJson)
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = stringResource(R.string.meal_steps)) {
                steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (meal.swapHint.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = stringResource(R.string.meal_swap)) {
                Text(meal.swapHint, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { viewModel.log(meal.id, LogStatus.EATEN); onBack() },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.today_ate_it)) }
            OutlinedButton(
                onClick = { viewModel.log(meal.id, LogStatus.SKIPPED); onBack() },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.today_skipped)) }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.meal_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { reporting = true }) { Text(stringResource(R.string.meal_report)) }
        Spacer(Modifier.height(24.dp))
    }

    if (reporting) {
        ReportDialog(
            container = container,
            kind = ReportKind.MEAL,
            payload = mealWithIngredients?.let(::mealReportPayload),
            onDismiss = { reporting = false },
            onResult = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
}

/** Csak a fogás maga megy el a bejelentéssel — napló és testadat nem. */
private fun mealReportPayload(data: MealWithIngredients): String = buildString {
    val meal = data.meal
    val n = meal.nutrients
    appendLine("${meal.name} (${meal.slot})")
    appendLine(
        "kcal ${n.kcal.roundToInt()} · P ${n.proteinG.roundToInt()} g · " +
            "C ${n.carbsG.roundToInt()} g · F ${n.fatG.roundToInt()} g",
    )
    if (meal.description.isNotBlank()) appendLine(meal.description)
    appendLine("Ingredients:")
    data.ingredients.forEach { item ->
        appendLine("  - ${item.name} ${item.quantity} ${item.unit}")
    }
}

@Composable
private fun NutrientRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
