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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.ui.dayLabel
import hu.mealpilot.app.ui.icon
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.repo.FavoriteRepository
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.ui.components.BudgetHero
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.HeroLabels
import hu.mealpilot.app.ui.components.MacroLine
import hu.mealpilot.app.ui.components.MealEntrySheet
import hu.mealpilot.app.ui.components.MealStamp
import hu.mealpilot.app.ui.components.NumberText
import hu.mealpilot.app.ui.components.SectionHeading
import hu.mealpilot.app.ui.components.WarningNote
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.currentDayFlow
import hu.mealpilot.app.ui.theme.LocalDarkTheme
import hu.mealpilot.app.ui.theme.MealColors
import hu.mealpilot.app.ui.theme.MealLabelStyle
import hu.mealpilot.app.ui.theme.PlateShape
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.Nutrients
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

/**
 * Melyik nap legyen kiválasztva lapozás után. A null a „ma".
 *
 * Ha a lapozás visszaér a mai napra, szándékosan null-t adunk vissza: onnantól megint
 * automatikusan továbblép éjfélkor. Ha a felhasználó máshol jár, ott is marad — egy
 * átnézett régi nap ne ugorjon el alóla, amíg olvassa.
 */
internal fun nextSelectedDay(current: LocalDate?, days: Long, today: LocalDate): LocalDate? {
    val next = (current ?: today).plusDays(days)
    return next.takeIf { it != today }
}

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: PlanEntity? = null,
    val meals: List<MealWithIngredients> = emptyList(),
    val logs: List<MealLogEntity> = emptyList(),
    /**
     * A profil MOSTANI kizárásai — nem azok, amik a terv készítésekor éltek.
     *
     * A kettő eltérhet, és pont az eltérés a lényeg: aki ma jelenti be a
     * mogyoróallergiát, annak a tegnap készült tervében még ott a mogyoróvaj.
     */
    val restrictions: Set<DietRestriction> = emptySet(),
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

    /**
     * Amit a felhasználó kiválasztott. A null a „ma" — ez éjfélkor magától továbblép.
     *
     * Korábban egyetlen, a ViewModel születésekor rögzített dátum volt. Aki nyitva
     * hagyta az appot éjfélkor, másnap is a tegnapot látta, és a terven kívül felvitt
     * étkezés is oda került. A képernyő nem életciklus-tudatosan gyűjt, tehát a
     * háttérbe kerülés sem indította újra a folyamot.
     */
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** A mai nap, éjfélkor frissülve. Lásd [currentDayFlow]. */
    private val today: Flow<LocalDate> = currentDayFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> =
        combine(today, selectedDay) { now, chosen -> chosen ?: now }
            .distinctUntilChanged()
            .flatMapLatest { day ->
                combine(
                    container.planRepository.observeActivePlan(),
                    container.planRepository.observeDay(day),
                    container.trackingRepository.observeMealLogs(day),
                    container.settings.profile.map { it.effectiveRestrictions }.distinctUntilChanged(),
                ) { plan, meals, logs, restrictions ->
                    TodayUiState(
                        date = day,
                        plan = plan,
                        meals = meals,
                        logs = logs,
                        restrictions = restrictions,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun shiftDay(days: Long) {
        selectedDay.value = nextSelectedDay(selectedDay.value, days, LocalDate.now())
    }

    /** A megjelölt fogások kulcsai — a szív ikon állapota ezen múlik. */
    val favoriteKeys: StateFlow<Set<String>> = container.favoriteRepository.observeKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(data: MealWithIngredients) = viewModelScope.launch {
        container.favoriteRepository.toggle(data, System.currentTimeMillis())
    }

    /** Fogáscsere a beépített bankból; a hibaüzenetet is továbbadja. */
    fun swap(mealId: Long, onDone: (Result<String>) -> Unit) = viewModelScope.launch {
        onDone(
            container.planRepository.swapMeal(
                mealId = mealId,
                restrictions = container.settings.currentProfile().effectiveRestrictions,
                language = container.language,
            )
        )
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
        container.trackingRepository.logCustomMeal(state.value.date, name, nutrients)
        refreshAchievements()
    }

    /**
     * Szavakkal leírt étkezés megbecslése. Null, ha nincs mivel — a felület ilyenkor
     * meg sem mutatja a mezőt, nem egy működésképtelen gombot kínál.
     */
    // Egyszer dől el, a képernyő létrejöttekor. A mealAi() titkosított tárolót olvas,
    // azt nem szabad minden újrarajzolásnál megtenni a fő szálon.
    val estimator: (suspend (String) -> Result<AiMealEstimate>)? =
        if (container.mealAi().canEstimate) {
            // A hívás pillanatában oldjuk fel újra: ha közben kulcs került a gépre,
            // az a következő becslésnél már érvényes.
            { text -> container.mealAi().estimate(text) }
        } else {
            null
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
    val favoriteKeys by viewModel.favoriteKeys.collectAsState()
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    // A csere üzenete a fogás NEVÉT hordozza, ami csak a művelet után derül ki: a
    // lambdában `stringResource` fordítási hiba lenne.
    val context = LocalContext.current
    val consumed = state.consumed
    val consumedKcal = consumed.kcal.roundToInt()
    val remainingKcal = state.targetKcal - consumedKcal
    val isToday = state.date == LocalDate.now()
    // Csak a keresztnév: „Szia, Kovács Máté!" úgy szól, mint egy hivatalos levél.
    val profile by container.settings.profile.collectAsState(initial = null)
    val firstName = profile?.name?.trim()?.substringBefore(' ').orEmpty()
    val greeting = if (firstName.isBlank()) {
        stringResource(R.string.today_greeting_anon)
    } else {
        stringResource(R.string.today_greeting, firstName)
    }
    val subtitle = when {
        state.targetKcal <= 0 -> state.plan?.title.orEmpty()
        remainingKcal > 0 -> stringResource(R.string.today_subtitle_left, remainingKcal)
        remainingKcal < 0 -> stringResource(R.string.today_subtitle_over, -remainingKcal)
        else -> stringResource(R.string.today_subtitle_done)
    }
    val doneCount = state.meals.count { state.statusOf(it.meal.id) != null }
    // A tervet EGYSZER olvassuk ki, és a lambdák ezt a helyi értéket látják.
    // A LazyColumn építője azonnal fut, az `item { }` tartalma viszont csak később:
    // ha közben a terv eltűnik (adattörlés, tervcsere), a lambdán belüli újraolvasás
    // már nullát adna, és a lenti makrósor összeomlana rajta.
    val plan = state.plan
    var replacing by remember { mutableStateOf<MealWithIngredients?>(null) }
    var addingExtra by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
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
                Column(Modifier.weight(1f)) {
                    // Ma köszönünk, más napon a dátum áll a helyén: a köszönés tegnapra
                    // vagy holnapra értelmetlen lenne, a lapozás viszont megmarad.
                    Text(
                        text = if (isToday) greeting else state.date.dayLabel(),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.shiftDay(-1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.today_prev_day))
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
            BudgetHero(
                consumedKcal = consumedKcal,
                targetKcal = state.targetKcal,
                labels = HeroLabels(
                    lines = listOf(
                        MacroLine(stringResource(R.string.macro_protein), consumed.proteinG, plan.targetProteinG),
                        MacroLine(stringResource(R.string.macro_carbs), consumed.carbsG, plan.targetCarbsG),
                        MacroLine(stringResource(R.string.macro_fat), consumed.fatG, plan.targetFatG),
                        MacroLine(stringResource(R.string.macro_fiber), consumed.fiberG, plan.targetFiberG),
                    ),
                    leftLabel = stringResource(R.string.today_hero_left),
                    overLabel = stringResource(R.string.today_hero_over),
                    totalLabel = stringResource(R.string.today_kcal_of, consumedKcal, state.targetKcal),
                ),
            )
        }

        if (state.meals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                SectionHeading(
                    title = stringResource(R.string.today_meals_title),
                    trailing = stringResource(R.string.today_meals_done, doneCount, state.meals.size),
                )
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
                violations = RestrictionChecker.violatedBy(
                    mealName = mealWithIngredients.meal.name,
                    ingredientNames = mealWithIngredients.ingredients.map { it.name },
                    restrictions = state.restrictions,
                    language = language,
                ),
                isFavorite = FavoriteRepository.key(mealWithIngredients.meal.name) in favoriteKeys,
                onToggleFavorite = { viewModel.toggleFavorite(mealWithIngredients) },
                onSwap = {
                    viewModel.swap(mealWithIngredients.meal.id) { result ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                result.fold(
                                    onSuccess = { context.getString(R.string.meal_swapped, it) },
                                    onFailure = { it.message.orEmpty() },
                                )
                            )
                        }
                    }
                },
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
            onEstimate = viewModel.estimator,
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
            onEstimate = viewModel.estimator,
        )
    }
}

/** Terven kívül felvitt étkezés a napi listán. */
@Composable
private fun ExtraRow(log: MealLogEntity, onDelete: () -> Unit) {
    val n = log.nutrients
    // Ugyanaz a lapkaforma, mint a tervezett étkezéseké — csak bélyeg nélkül, mert
    // ez nem tartozik egy fogáshoz sem.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PlateShape.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
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

/**
 * A nap egy étkezésének lapkája.
 *
 * `internal`, hogy a modul tesztjei is meg tudják jeleníteni. Semmilyen tárolót nem
 * ismer — adatot és visszahívásokat kap, tehát önmagában vizsgálható.
 */
@Composable
internal fun MealRow(
    meal: MealWithIngredients,
    log: MealLogEntity?,
    status: LogStatus?,
    /**
     * A kizárások, amikbe ez a fogás beleütközik. Rendes esetben üres: a tervező
     * kiszűri őket. NEM üres akkor, ha a kizárás a terv elkészülte UTÁN került a
     * profilba — ilyenkor a tervben ott marad az allergén, és eddig semmi nem szólt.
     */
    violations: List<DietRestriction> = emptyList(),
    /**
     * Meg van-e jelölve kedvencként. Szándékosan NINCS alapértelmezése: egy
     * alapértelmezés azt jelentené, hogy egy hívó észrevétlenül kihagyhatja a
     * bekötést, és a szív minden fogásnál üres maradna.
     */
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSwap: () -> Unit,
    onOpen: () -> Unit,
    onAte: () -> Unit,
    onSkip: () -> Unit,
    onReplace: () -> Unit,
    onUndo: () -> Unit,
) {
    val mealSlot = MealSlot.fromRaw(meal.meal.slot)
    // A nyelvet EGYSZER olvassuk ki. A `joinToString` lambdája nem @Composable
    // környezet, tehát ott a `LocalAppLanguage.current` fordítási hiba lenne.
    val language = LocalAppLanguage.current
    val slot = mealSlot.label(language)
    val replaced = status == LogStatus.REPLACED
    // Felülírásnál azt mutatjuk, amit tényleg megevett — nem azt, amit terveztünk.
    val shownName = if (replaced) log?.name.orEmpty().ifBlank { meal.meal.name } else meal.meal.name
    val n = if (replaced && log != null) log.nutrients else meal.meal.nutrients
    val settled = status != null
    val accent = MealColors.of(mealSlot.ordinal, LocalDarkTheme.current)
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // A lezárt étkezés halványabb: a szem így a következő fogásra esik, nem
            // arra, amivel már nincs dolga. Törölni viszont nem szabad — a nap végén
            // pont az a kérdés, mit evett.
            .alpha(if (settled) 0.62f else 1f)
            .clickable(onClick = onOpen),
        shape = PlateShape.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (settled) 0.dp else 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MealStamp(
                    icon = mealSlot.icon,
                    color = accent,
                    done = status == LogStatus.EATEN || replaced,
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = slot.uppercase(),
                            style = MealLabelStyle,
                            color = accent,
                        )
                        Spacer(Modifier.size(8.dp))
                        NumberText(
                            meal.meal.timeText,
                            style = MealLabelStyle.copy(
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        shownName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (status == LogStatus.SKIPPED) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.height(3.dp))
                    NumberText(
                        stringResource(
                            R.string.macro_line,
                            n.kcal.roundToInt(),
                            n.proteinG.roundToInt(),
                            n.carbsG.roundToInt(),
                            n.fatG.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A szív a fejlécben van, nem a túlcsordulás menüben: az csak a még
                // le nem zárt étkezéseknél jelenik meg, márpedig a „megettem és jó
                // volt" a leggyakoribb pillanat, amikor valaki megjelöl valamit.
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.favorites_remove else R.string.favorites_add
                        ),
                        tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settled) {
                    // Lezárt étkezésnél csak a visszavonás marad — a „megettem" gombpár
                    // helye felszabadul, és a lapka alacsonyabb lesz.
                    androidx.compose.material3.TextButton(onClick = onUndo) {
                        Text(stringResource(R.string.action_undo))
                    }
                }
            }

            // A figyelmeztetés a gombok FÖLÖTT: ez a döntés bemenete, nem a
            // következménye. Lezárt étkezésnél is kint marad — ha kiderül, hogy
            // allergént evett, azt akkor is tudnia kell.
            if (violations.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                WarningNote(
                    stringResource(
                        R.string.meal_breaks_exclusions,
                        violations.joinToString { it.label(language) },
                    )
                )
            }

            if (settled) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = when (status) {
                        LogStatus.SKIPPED -> stringResource(R.string.today_state_skipped)
                        LogStatus.REPLACED -> stringResource(R.string.today_state_replaced)
                        else -> stringResource(R.string.today_state_eaten)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == LogStatus.SKIPPED) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            } else {
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = onAte,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = PlateShape.innerButton,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.today_action_ate), style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onSkip,
                        modifier = Modifier.height(44.dp),
                        shape = PlateShape.innerButton,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    ) {
                        Text(stringResource(R.string.today_action_skip), style = MaterialTheme.typography.labelLarge)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // Két KÜLÖNBÖZŐ dolog, és könnyű összekeverni őket: a csere
                            // a TERVET írja át (mást fogsz főzni), a „mást ettem" a
                            // NAPLÓT (már megetted, csak nem azt).
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.today_swap)) },
                                onClick = { menuOpen = false; onSwap() },
                                leadingIcon = { Icon(Icons.Filled.Autorenew, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.today_ate_other)) },
                                onClick = { menuOpen = false; onReplace() },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}
