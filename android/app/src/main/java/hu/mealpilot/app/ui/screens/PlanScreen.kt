package hu.mealpilot.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.ui.dayLabel
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.repo.PlanGenerationOutcome
import hu.mealpilot.app.work.GenerationCoordinator
import hu.mealpilot.core.billing.Tiers
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.ui.components.NumberText
import hu.mealpilot.app.ui.components.PlatePill
import hu.mealpilot.app.ui.components.SectionHeading
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.ReportDialog
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.icon
import hu.mealpilot.app.ui.theme.LocalDarkTheme
import hu.mealpilot.app.ui.theme.MealColors
import hu.mealpilot.app.ui.theme.PlateShape
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.Nutrients
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /**
     * A profil MOSTANI kizárásai. A terv a KÉSZÍTÉSEKOR ismert kizárásokkal készült;
     * ami később került a profilba, azt csak itt, utólag lehet észrevenni.
     */
    val restrictions: Set<DietRestriction> = emptySet(),
) {
    val byDay: Map<Int, List<MealWithIngredients>>
        get() = meals.groupBy { it.meal.dayIndex }.toSortedMap()
}

class PlanViewModel(private val container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlanUiState> = combine(
        container.planRepository.observeActivePlan(),
        container.entitlements.entitlement,
        container.settings.profile.map { it.effectiveRestrictions }.distinctUntilChanged(),
    ) { plan, entitlement, restrictions -> Triple(plan, entitlement, restrictions) }
        .flatMapLatest { (plan, entitlement, restrictions) ->
            val base = PlanUiState(
                canRefine = container.hasPlanner && entitlement.limits.canRefineDays,
                maxPlanDays = entitlement.limits.maxPlanDays,
                restrictions = restrictions,
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
            val budget = EnergyCalculator.budget(profile, container.language)
            val result = container.planRepository.refineDay(
                ai = container.mealAi(),
                profile = profile,
                budget = budget,
                planId = plan.id,
                dayIndex = dayIndex,
                instruction = instruction,
                language = container.language,
            )
            onResult(result.getOrElse {
                it.message ?: container.strings[R.string.plan_refine_failed]
            })
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
    onOpenFavorites: () -> Unit,
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
    val context = LocalContext.current

    // Amint az első napok megvannak, elengedjük a párbeszédet: a terv már használható,
    // a többi a háttérben töltődik tovább, és a felső sávon végig látszik a haladás.
    LaunchedEffect(status.hasUsableDays, status.running) {
        if (status.running && status.hasUsableDays && showGenerator) {
            showGenerator = false
            snackbarHostState.showSnackbar(
                context.resources.getQuantityString(
                    R.plurals.plan_partial_ready, status.daysReady, status.daysReady,
                )
            )
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
                        it.usedFallback -> context.getString(R.string.plan_done_fallback)
                        it.isComplete -> context.getString(R.string.plan_done)
                        else -> context.resources.getQuantityString(
                            R.plurals.plan_partial, it.daysSaved, it.daysSaved, it.requestedDays,
                        )
                    }
                },
                onFailure = { it.message ?: context.getString(R.string.plan_failed) },
            )
        )
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.plan_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A kedvencek a TERV fejlécében vannak: itt dől el, mi kerül a
                    // következő hétre, és a megjelölt fogások épp ezt befolyásolják.
                    IconButton(onClick = onOpenFavorites) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.favorites_open),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { showGenerator = true },
                        shape = PlateShape.button,
                    ) { Text(stringResource(R.string.plan_new)) }
                }
            }
        }

        val plan = state.plan
        if (plan == null) {
            item {
                EmptyState(
                    title = stringResource(R.string.plan_empty_title),
                    message = stringResource(R.string.plan_empty_message),
                    action = { Button(onClick = { showGenerator = true }) { Text(stringResource(R.string.today_create_plan)) } },
                )
            }
        } else {
            item {
                SectionCard(title = plan.title) {
                    if (plan.summary.isNotBlank()) {
                        Text(plan.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    // Jelvények, nem AssistChip: az kattintható elemnek látszik, holott
                    // nem az volt — a három szám csak tájékoztat. A magyar szöveg eddig a
                    // kódban állt, tehát angol felületen is „nap" jelent volna meg.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatePill(stringResource(R.string.plan_chip_kcal, plan.targetKcal))
                        PlatePill(
                            pluralStringResource(R.plurals.plan_chip_days, plan.dayCount, plan.dayCount),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        PlatePill(
                            stringResource(R.string.plan_chip_protein, plan.targetProteinG),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
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
                        stringResource(R.string.plan_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { reporting = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text(stringResource(R.string.plan_report_link)) }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                SectionHeading(stringResource(R.string.plan_days_title))
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
                    restrictions = state.restrictions,
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
    restrictions: Set<DietRestriction> = emptySet(),
) {
    val total = Nutrients.sum(meals.map { it.meal.nutrients.toNutrients() })
    val date = startDate.plusDays(dayIndex.toLong())
    val language = LocalAppLanguage.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PlateShape.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        date.dayLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    NumberText(
                        stringResource(
                            R.string.plan_day_totals,
                            total.kcal.roundToInt(),
                            targetKcal,
                            total.proteinG.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    meals.forEach { mw ->
                        val slot = MealSlot.fromRaw(mw.meal.slot)
                        // Ugyanaz a színkód, mint a Ma képernyő bélyegein: a reggeli itt is
                        // narancs, az ebéd zöld. Így a két képernyő ugyanarról beszél.
                        val accent = MealColors.of(slot.ordinal, LocalDarkTheme.current)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenMeal(mw.meal.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Itt is az étkezés saját ikonja, csak aprón — a színes pont
                            // csak annyit mondott, hogy „ez egy másik étkezés".
                            Icon(
                                slot.icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(11.dp))
                            NumberText(
                                mw.meal.timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(11.dp))
                            Text(
                                mw.meal.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            // Ha egy utólag felvett kizárásba ütközik, itt is látszik.
                            // A teljes mondat a fogás lapján van; itt egy jel elég
                            // ahhoz, hogy a hét áttekintésekor kiszúrja az ember.
                            val violations = RestrictionChecker.violatedBy(
                                mealName = mw.meal.name,
                                ingredientNames = mw.ingredients.map { it.name },
                                restrictions = restrictions,
                                language = language,
                            )
                            if (violations.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = stringResource(
                                        R.string.meal_breaks_exclusions,
                                        violations.joinToString { it.label(language) },
                                    ),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            NumberText(
                                "${mw.meal.nutrients.kcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (refineEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRefine, shape = PlateShape.button) {
                            Text(stringResource(R.string.plan_refine_button))
                        }
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
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = {
            Text(stringResource(if (running) R.string.plan_building else R.string.plan_new_title))
        },
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
                        stringResource(R.string.plan_background_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.plan_length_question), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            3 to R.string.plan_len_3_days,
                            7 to R.string.plan_len_1_week,
                            14 to R.string.plan_len_2_weeks,
                            30 to R.string.plan_len_1_month,
                        ).forEach { (value, label) ->
                            val locked = value > maxDays
                            FilterChip(
                                selected = days == value && !locked,
                                onClick = {
                                    if (locked) {
                                        onLocked(context.getString(R.string.plan_length_locked, maxDays))
                                    } else {
                                        days = value
                                    }
                                },
                                label = { Text(stringResource(label)) },
                                leadingIcon = if (locked) {
                                    { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = startTomorrow, onCheckedChange = { startTomorrow = it })
                        Text(
                            "  " + stringResource(R.string.plan_start_tomorrow),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        label = { Text(stringResource(R.string.plan_free_text_label)) },
                        placeholder = {
                            Text(stringResource(R.string.plan_free_text_hint))
                        },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.plan_generator_note),
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
                    Text(stringResource(R.string.plan_generate))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (running) R.string.action_background else R.string.action_cancel))
            }
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
        title = { Text(stringResource(R.string.plan_refine_title, dayIndex + 1)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.plan_refine_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.plan_refine_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.plan_refine_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
        appendLine("${plan.title} — ${plan.dayCount} days, target ${plan.targetKcal} kcal/day")
        byDay.entries.take(7).forEach { (dayIndex, meals) ->
            appendLine("Day ${dayIndex + 1}:")
            meals.forEach { item ->
                val meal = item.meal
                appendLine("  - ${meal.name} (${meal.nutrients.kcal.roundToInt()} kcal)")
            }
        }
    }
