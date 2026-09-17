package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.local.WeightLogEntity
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.NumberText
import hu.mealpilot.app.ui.components.PlatePill
import hu.mealpilot.app.ui.components.WarningNote
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.achievements.AchievementState
import hu.mealpilot.core.achievements.AchievementTier
import hu.mealpilot.core.energy.EnergyBudget
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.ProfileLimits
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val budget: EnergyBudget? = null,
    val weights: List<WeightLogEntity> = emptyList(),
) {
    val latestWeight: Double? get() = weights.maxByOrNull { it.epochDay }?.weightKg
    val startWeight: Double? get() = weights.minByOrNull { it.epochDay }?.weightKg
    val change: Double? get() = if (weights.size >= 2) (latestWeight ?: 0.0) - (startWeight ?: 0.0) else null
}

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ProfileUiState> = combine(
        container.settings.profile,
        container.trackingRepository.observeWeights(),
    ) { profile, weights ->
        ProfileUiState(profile, EnergyCalculator.budget(profile, container.language), weights)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private val _achievements = MutableStateFlow<List<AchievementState>>(emptyList())
    val achievements: StateFlow<List<AchievementState>> = _achievements.asStateFlow()

    init {
        refreshAchievements()
    }

    fun refreshAchievements() = viewModelScope.launch {
        val plan = container.planRepository.activePlan()
        val budget = EnergyCalculator.budget(container.settings.currentProfile(), container.language)
        _achievements.value = container.statsRepository.evaluate(
            targetKcal = plan?.targetKcal ?: budget.target.kcal,
            targetProteinG = plan?.targetProteinG ?: budget.target.proteinG,
        )
    }

    fun logWeight(weightKg: Double, bodyFat: Double?, onDone: suspend (String) -> Unit) = viewModelScope.launch {
        container.telemetry.record(TelemetryEvent.WEIGHT_LOGGED)
        container.trackingRepository.logWeight(LocalDate.now(), weightKg, bodyFat)
        // A profil súlya követi a mérést, különben a kalóriakeret elavulna.
        container.settings.updateWeight(weightKg, bodyFat)
        refreshAchievements()
        onDone(container.strings[R.string.profile_weight_logged, "%.1f".format(weightKg)])
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel(factory = containerFactory(container) { ProfileViewModel(it) })
    val state by viewModel.state.collectAsState()
    val achievements by viewModel.achievements.collectAsState()

    var weightText by remember { mutableStateOf("") }
    var bodyFatText by remember { mutableStateOf("") }

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
                    stringResource(R.string.tab_profile),
                    style = MaterialTheme.typography.headlineLarge,
                )
                // Kerek fogaskerék-gomb a feliratos gomb helyett: a beállítás ritkán
                // kell, és a felirat elvette a helyet a képernyő címétől.
                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }

        state.budget?.let { budget ->
            item {
                SectionCard(title = stringResource(R.string.budget_card_title)) {
                    // A négy szám jelvényként: a StatChip alatta címkével kétsoros volt,
                    // és négy ilyen doboz kitöltötte a fél képernyőt. A jelvény egysoros,
                    // a címke a számba került.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlatePill(stringResource(R.string.budget_bmr_pill, budget.bmr))
                        PlatePill(
                            stringResource(R.string.budget_tdee_pill, budget.tdee),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        PlatePill(
                            stringResource(R.string.budget_target_pill, budget.target.kcal),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        PlatePill(
                            stringResource(R.string.budget_deficit_pill, budget.appliedDeficit),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    NumberText(
                        stringResource(
                            R.string.budget_macros_fiber,
                            budget.target.proteinG,
                            budget.target.carbsG,
                            budget.target.fatG,
                            budget.target.fiberG,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NumberText(
                        stringResource(R.string.budget_rate, "%.2f".format(budget.expectedRateKgPerWeek)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EnergyCalculator.daysToTarget(state.profile, budget)?.let { days ->
                        NumberText(
                            pluralStringResource(
                                R.plurals.budget_days_to_target,
                                days,
                                days,
                                LocalDate.now().plusDays(days.toLong()).toString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    budget.warnings.forEach { warning ->
                        Spacer(Modifier.height(8.dp))
                        WarningNote(warning)
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.weight_card_title)) {
                // A tartományokat a ProfileLimits adja, ugyanaz, amit a profilűrlap használ.
                // Ez a kártya korábban SEMMIT nem ellenőrzött: a testsúly is beírható volt a
                // testzsír mezőbe, és mivel a mérés felülírja a profilt, a napi kalóriacél
                // ebből számolódott újra.
                val loggedWeight = weightText.replace(',', '.').toDoubleOrNull()
                val loggedFat = bodyFatText.replace(',', '.').toDoubleOrNull()
                val weightValid = ProfileLimits.isValidWeight(loggedWeight)
                val fatValid = bodyFatText.isBlank() ||
                    (loggedFat != null && loggedFat in ProfileLimits.BODY_FAT_PERCENT)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("kg") },
                        singleLine = true,
                        isError = weightText.isNotBlank() && !weightValid,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = bodyFatText,
                        onValueChange = { bodyFatText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text(stringResource(R.string.weight_body_fat)) },
                        placeholder = { Text(stringResource(R.string.weight_optional)) },
                        singleLine = true,
                        isError = !fatValid,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val kg = loggedWeight ?: return@Button
                        viewModel.logWeight(kg, loggedFat) { snackbarHostState.showSnackbar(it) }
                        weightText = ""
                        bodyFatText = ""
                    },
                    enabled = weightValid && fatValid,
                ) { Text(stringResource(R.string.weight_log_today)) }

                state.change?.let { change ->
                    Spacer(Modifier.height(12.dp))
                    val label = stringResource(
                        if (change < 0) R.string.weight_lost_so_far else R.string.weight_gained_so_far,
                        "%.1f".format(abs(change)),
                    )
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (state.weights.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    WeightSparkline(state.weights)
                }
            }
        }

        item {
            Text(
                stringResource(R.string.achievements_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        items(achievements, key = { it.achievement.key }) { achievementState ->
            AchievementRow(achievementState)
        }
    }
}

@Composable
private fun AchievementRow(state: AchievementState) {
    val language = LocalAppLanguage.current
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .alpha(if (state.unlocked) 1f else 0.6f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                achievementIcon(state.achievement.key),
                contentDescription = null,
                tint = if (state.unlocked) tierColor(state.achievement.tier)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.achievement.title(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.achievement.description(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.unlocked) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
            if (state.unlocked) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.achievement_unlocked),
                    tint = tierColor(state.achievement.tier),
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    "${state.progress}/${state.achievement.goal}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Az achievementekhez ikon, nem emoji: az emoji platformonként máshogy néz ki, és
 * felületen elszórva olcsóvá teszi a megjelenést. Az értesítésben marad emoji, ott
 * viszont pont hasznos, mert onnan hiányzik a színes ikonkészlet.
 *
 * A lefelé mutató trend ikon az AutoMirrored változat: a jobbról balra író nyelveken
 * a „lefelé, jobbra" irány „lefelé, balra" lesz. Ugyanaz a megfontolás, ami miatt a
 * [BackButton] nyila sem karakter, hanem AutoMirrored ikon.
 */
private fun achievementIcon(key: String): ImageVector = when {
    key.startsWith("streak") -> Icons.Filled.LocalFireDepartment
    key.startsWith("target") -> Icons.Filled.TrackChanges
    key.startsWith("protein") -> Icons.Filled.Egg
    key.startsWith("lost") -> Icons.AutoMirrored.Filled.TrendingDown
    key.startsWith("weigh") -> Icons.Filled.MonitorWeight
    key.startsWith("shopping") -> Icons.Filled.ShoppingCart
    key.startsWith("recipes") -> Icons.Filled.Restaurant
    key.startsWith("plan") || key == "first_plan" -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.EmojiEvents
}

private fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.BRONZE -> Color(0xFFB06B3A)
    AchievementTier.SILVER -> Color(0xFF9AA0A6)
    AchievementTier.GOLD -> Color(0xFFD4A017)
}

/** Egyszerű oszlopdiagram a súlymérésekből — nem kell hozzá külön grafikonkönyvtár. */
@Composable
private fun WeightSparkline(weights: List<WeightLogEntity>) {
    val ordered = weights.sortedBy { it.epochDay }.takeLast(30)
    if (ordered.size < 2) return
    val min = ordered.minOf { it.weightKg }
    val max = ordered.maxOf { it.weightKg }
    val span = (max - min).takeIf { it > 0.1 } ?: 1.0

    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ordered.forEach { entry ->
            val fraction = ((entry.weightKg - min) / span).coerceIn(0.05, 1.0)
            Box(
                Modifier
                    .weight(1f)
                    .height((64 * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
    Text(
        pluralStringResource(
            R.plurals.weight_range_summary,
            ordered.size,
            "%.1f".format(min),
            "%.1f".format(max),
            ordered.size,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
