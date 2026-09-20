package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
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
import hu.mealpilot.app.data.repo.FavoriteRepository
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.ui.components.BackButton
import hu.mealpilot.app.ui.components.GroupLabel
import hu.mealpilot.app.ui.components.MealStamp
import hu.mealpilot.app.ui.components.PlatePill
import hu.mealpilot.app.ui.components.ReportDialog
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.WarningNote
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.icon
import hu.mealpilot.app.ui.theme.LocalDarkTheme
import hu.mealpilot.app.ui.theme.MealColors
import hu.mealpilot.app.ui.theme.MealLabelStyle
import hu.mealpilot.core.ai.Aisle
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.ai.Units
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.model.DietRestriction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MealDetailViewModel(
    private val container: AppContainer,
    mealId: Long,
) : ViewModel() {

    val meal: StateFlow<MealWithIngredients?> = container.planRepository.observeMeal(mealId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A profil MOSTANI kizárásai — nem azok, amik a terv készítésekor éltek. */
    val restrictions: StateFlow<Set<DietRestriction>> = container.settings.profile
        .map { it.effectiveRestrictions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * A megjelölt fogások kulcsai. Névre megy, nem azonosítóra: ugyanaz az étel a hét
     * két napján két külön `meals` sor, a felhasználónak viszont egy étel — ha az
     * azonosító döntene, a szív a másik napon üres lenne.
     */
    val favoriteKeys: StateFlow<Set<String>> = container.favoriteRepository.observeKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Megjelöli vagy leveszi a jelölést; az új állapotot adja vissza. */
    fun toggleFavorite(data: MealWithIngredients, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(container.favoriteRepository.toggle(data, System.currentTimeMillis()))
    }

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
    val restrictions by viewModel.restrictions.collectAsState()
    val favoriteKeys by viewModel.favoriteKeys.collectAsState()
    val scope = rememberCoroutineScope()
    var reporting by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    // A visszajelzés szövege a lambdában kellene, oda viszont @Composable hívás nem
    // mehet. Itt olvassuk ki, ahol még szabad.
    val addLabel = stringResource(R.string.favorites_add)
    val removeLabel = stringResource(R.string.favorites_remove)
    val addedText = stringResource(R.string.favorites_added)
    val removedText = stringResource(R.string.favorites_removed)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BackButton(stringResource(R.string.action_back), onBack)

        val data = mealWithIngredients
        if (data == null) {
            Text(stringResource(R.string.meal_gone), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val meal = data.meal
        val n = meal.nutrients

        // Ugyanaz a bélyeg, mint a Ma képernyőn, csak nagyobb: a megnyitott étkezés
        // így felismerhetően AZ, amire az előző képernyőn koppintott.
        val slot = MealSlot.fromRaw(meal.slot)
        val accent = MealColors.of(slot.ordinal, LocalDarkTheme.current)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MealStamp(icon = slot.icon, color = accent, done = false, size = 66.dp)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (slot.label(language) + " · " + meal.timeText).uppercase(),
                    style = MealLabelStyle,
                    color = accent,
                )
                Spacer(Modifier.height(5.dp))
                Text(meal.name, style = MaterialTheme.typography.headlineSmall)
            }
            // A szív ITT van, a név mellett: ez az a képernyő, ahol a felhasználó a
            // receptet elolvasva eldönti, hogy újra kéri-e. A gomb lezárt étkezésnél
            // is megmarad — a „megettem és jó volt" a leggyakoribb pillanat, amikor
            // valaki megjelöl valamit.
            val isFavorite = FavoriteRepository.key(meal.name) in favoriteKeys
            IconButton(
                onClick = {
                    viewModel.toggleFavorite(data) { added ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (added) addedText else removedText
                            )
                        }
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) removeLabel else addLabel,
                    tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Ez az a képernyő, ahol a felhasználó a recept elolvasása előtt eldönti, hogy
        // megfőzi-e. Ha egy utólag felvett kizárásba ütközik, ITT kell szólni — a
        // tervező csak a készítéskor ismert kizárásokat szűrte ki.
        val violations = RestrictionChecker.violatedBy(
            mealName = meal.name,
            ingredientNames = data.ingredients.map { it.name },
            restrictions = restrictions,
            language = language,
        )
        if (violations.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            WarningNote(
                stringResource(
                    R.string.meal_breaks_exclusions,
                    violations.joinToString { it.label(language) },
                )
            )
        }

        if (meal.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                meal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlatePill(stringResource(R.string.meal_kcal_value, n.kcal.roundToInt()))
            PlatePill(
                stringResource(R.string.meal_minutes, meal.prepMinutes),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            PlatePill(
                // A servings Double (fél adag is lehet), ezért NEM mehet %d-be: a
                // formázó IllegalFormatConversionException-nel száll el, és épp ezt
                // a képernyőt viszi magával.
                if (meal.servings == 1.0) {
                    stringResource(R.string.meal_servings_one)
                } else {
                    stringResource(R.string.meal_servings_value, trimZeroDecimal(meal.servings))
                },
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
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
                GroupLabel(
                    aisle.label(language),
                    MealColors.of(aisle.ordinal, LocalDarkTheme.current),
                    Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                items.forEach { ing ->
                    NutrientRow(
                        ing.name.replaceFirstChar(Char::uppercaseChar) +
                            if (ing.note.isNotBlank()) " (${ing.note})" else "",
                        "${trimZeroDecimal(ing.quantity)} ${Units.label(ing.unit, ing.quantity, language)}",
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

/** „2.0" helyett „2", de „0.5" marad „0.5" — a mennyiségek és az adagszám így olvashatók. */
private fun trimZeroDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

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
