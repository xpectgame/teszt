package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.ui.components.BackButton
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.currentDayFlow
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.progress.TrendPoint
import hu.mealpilot.core.progress.WeightTrend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Egy naplózott nap összes bevitele. */
data class LoggedDay(val epochDay: Long, val kcal: Int)

/**
 * Ennyi napot rajzolunk a súlygrafikonra.
 *
 * Nem az adat korlátja, csak a rajzé: telefonszélességben ennél több pont már
 * egybefolyik, és a mérési pontok sávvá olvadnának. A trend és a heti ütem
 * továbbra is a TELJES előzményből számol.
 */
private const val CHART_DAYS = 90

data class ProgressState(
    val trend: List<TrendPoint> = emptyList(),
    val weeklyChangeKg: Double? = null,
    val targetWeightKg: Double? = null,
    val daysToTargetMeasured: Int? = null,
    val daysToTargetPlanned: Int? = null,
    val days: List<LoggedDay> = emptyList(),
    val targetKcal: Int = 0,
)

class ProgressViewModel(container: AppContainer) : ViewModel() {

    /**
     * A naplózott napok ablaka a MAI naphoz képest mozog.
     *
     * A `LocalDate.now()` egyszeri kiolvasása itt csendes hiba lenne: aki nyitva
     * hagyja az appot éjfélkor, másnap is a tegnapi ablakot látná, és a mai
     * étkezései sosem jelennének meg a képernyőn. Ugyanez a Ma képernyőn már
     * egyszer megvolt — lásd [currentDayFlow].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ProgressState> = currentDayFlow().flatMapLatest { today ->
        combine(
            container.database.weightLogDao().observeAll(),
            container.database.mealLogDao().observeRange(
                today.minusDays(WINDOW_DAYS).toEpochDay(),
                today.toEpochDay(),
            ),
            container.planRepository.observeActivePlan(),
            container.settings.profile,
        ) { weights, logs, plan, profile ->
            val trend = WeightTrend.series(weights.map { it.epochDay to it.weightKg })
            val target = profile.targetWeightKg
            val budget = EnergyCalculator.budget(profile)

            ProgressState(
                trend = trend,
                weeklyChangeKg = WeightTrend.weeklyChangeKg(trend),
                targetWeightKg = target,
                daysToTargetMeasured = target?.let {
                    WeightTrend.daysToTargetAtMeasuredRate(trend, it)
                },
                daysToTargetPlanned = EnergyCalculator.daysToTarget(profile, budget),
                days = logs.groupBy { it.epochDay }
                    .map { (day, entries) -> LoggedDay(day, entries.sumOf { it.nutrients.kcal }.roundToInt()) }
                    .filter { it.kcal > 0 }
                    .sortedBy { it.epochDay },
                targetKcal = plan?.targetKcal ?: budget.target.kcal,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    private companion object {
        const val WINDOW_DAYS = 27L
    }
}

/**
 * „Hol tartok?" — a haladás egy helyen.
 *
 * A képernyő szándékosan NEM hív modellt: mindent a saját naplódból számol, tehát
 * offline is teljes, és nem fogy tőle semmi.
 *
 * A két ábra formája a feladatból következik. A súly IDŐSOR, ezért vonal; a napi
 * bevitel MÉRT ÉRTÉK EGY CÉLHOZ KÉPEST, ezért oszlop egy vonatkoztatási vonallal —
 * az „átléptem-e" kérdést így a geometria válaszolja meg, nem egy színkód.
 */
@Composable
fun ProgressScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ProgressViewModel = viewModel(
        factory = containerFactory(container) { ProgressViewModel(it) },
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        BackButton(stringResource(R.string.action_back), onBack)
        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        SectionCard(title = stringResource(R.string.progress_trend_title)) {
            val current = state.trend.lastOrNull()
            if (current == null) {
                Text(
                    stringResource(R.string.progress_weight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.progress_trend_value, "%.1f".format(current.trendKg)),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(rateText(state.weeklyChangeKg), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(14.dp))
                // A TREND a teljes előzményből számol (a simításnak kell a felfutás),
                // a RAJZ viszont csak az utolsó néhány hónapot mutatja. Enélkül egy
                // két éve mérő felhasználónál hétszáz pont kerülne a telefon
                // szélességébe: a mérési pontok egybefüggő sávvá olvadnának, és a
                // rajzoló minden képkockán hétszáz kört húzna meg.
                val shown = state.trend.takeLast(CHART_DAYS)
                WeightTrendChart(
                    points = shown,
                    lineColor = MaterialTheme.colorScheme.primary,
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    surfaceColor = MaterialTheme.colorScheme.surface,
                    description = context.getString(
                        R.string.progress_chart_weight_description,
                        "%.1f".format(shown.first().trendKg),
                        "%.1f".format(current.trendKg),
                        shown.size,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.progress_trend_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Text(etaText(state), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = stringResource(R.string.progress_kcal_title)) {
            if (state.days.isEmpty()) {
                Text(
                    stringResource(R.string.progress_kcal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val average = state.days.sumOf { it.kcal } / state.days.size
                Text(
                    stringResource(R.string.progress_kcal_average, average, state.days.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(14.dp))
                IntakeChart(
                    days = state.days,
                    targetKcal = state.targetKcal,
                    barColor = MaterialTheme.colorScheme.primary,
                    targetLineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = context.getString(
                        R.string.progress_chart_kcal_description,
                        state.days.size,
                        state.days.minOf { it.kcal },
                        state.days.maxOf { it.kcal },
                        state.targetKcal,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.progress_kcal_note, state.targetKcal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun rateText(weekly: Double?): String = when {
    weekly == null -> stringResource(R.string.progress_rate_unknown)
    weekly < -0.05 -> stringResource(R.string.progress_rate_losing, "%.2f".format(abs(weekly)))
    weekly > 0.05 -> stringResource(R.string.progress_rate_gaining, "%.2f".format(weekly))
    else -> stringResource(R.string.progress_rate_flat)
}

@Composable
private fun etaText(state: ProgressState): String {
    val target = state.targetWeightKg ?: return stringResource(R.string.progress_no_target)
    val planned = state.daysToTargetPlanned
        ?.let { stringResource(R.string.progress_eta_planned, LocalDate.now().plusDays(it.toLong()).formatted()) }
        .orEmpty()
    val measured = state.daysToTargetMeasured
        ?: return listOf(stringResource(R.string.progress_eta_none), planned)
            .filter { it.isNotBlank() }.joinToString(" ")
    return listOf(
        stringResource(
            R.string.progress_eta_measured,
            "%.1f".format(target),
            LocalDate.now().plusDays(measured.toLong()).formatted(),
        ),
        planned,
    ).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun LocalDate.formatted(): String =
    format(DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern), Locale.getDefault()))

/**
 * Súlytrend: folytonos vonal, mellette a mérések apró körökkel.
 *
 * A kettőt a FORMÁJA különbözteti meg, nem a színe — és ez nem stílus kérdése. A
 * paletta zöldje és a halvány tinta protanópiában ΔE 2,0-ra van egymástól (a
 * validátor mérése), vagyis színnel megkülönböztetve egy vörös-zöld színtévesztő
 * számára a két jelölés EGYFORMA lenne. Vonal kontra pont viszont mindenkinek más.
 *
 * Egyetlen adatsor, tehát jelmagyarázat nincs: a cím és az alatta lévő mondat
 * megnevezi, mit lát.
 */
@Composable
private fun WeightTrendChart(
    points: List<TrendPoint>,
    lineColor: Color,
    dotColor: Color,
    /** A gyűrű színe a mai pont körül. Fix fehérrel sötét módban folt lenne. */
    surfaceColor: Color,
    description: String,
) {
    if (points.size < 2) return
    val values = points.map { it.trendKg } + points.mapNotNull { it.measuredKg }
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.2 } ?: 1.0

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description },
    ) {
        fun x(index: Int) = size.width * index / (points.size - 1).toFloat()
        fun y(kg: Double) = (size.height - 6.dp.toPx()) *
            (1f - ((kg - min) / span).toFloat()) + 3.dp.toPx()

        // A mérések ELŐSZÖR, hogy a trendvonal fölöttük fusson: a vonal a lényeg.
        points.forEachIndexed { index, point ->
            val kg = point.measuredKg ?: return@forEachIndexed
            drawCircle(
                color = dotColor.copy(alpha = 0.45f),
                radius = 2.5.dp.toPx(),
                center = Offset(x(index), y(kg)),
            )
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = Offset(x(i), y(points[i].trendKg)),
                end = Offset(x(i + 1), y(points[i + 1].trendKg)),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // A mai trendpont kiemelve: a felület kis gyűrűje elválasztja a vonaltól.
        val last = Offset(x(points.size - 1), y(points.last().trendKg))
        drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = last)
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = last)
    }
}

/**
 * Napi bevitel oszlopokkal, a napi cél vonatkoztatási vonalával.
 *
 * Egy hue, semmi színkód: hogy egy nap a cél fölött van-e, azt az oszlop és a vonal
 * viszonya mondja meg. Egy háromszínű „alatta / közel / fölötte" jelölés ugyanezt
 * mondaná, csak rosszabbul — a paletta borostyánsárgája fehér felületen 2,17:1
 * kontrasztú, ami a validátor szerint önmagában nem elég.
 */
@Composable
private fun IntakeChart(
    days: List<LoggedDay>,
    targetKcal: Int,
    barColor: Color,
    targetLineColor: Color,
    description: String,
) {
    if (days.isEmpty()) return
    val top = maxOf(days.maxOf { it.kcal }, targetKcal) * 1.1

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 2.dp.toPx()
        val barWidth = ((size.width + gap) / days.size) - gap
        fun y(kcal: Number) = size.height * (1f - (kcal.toDouble() / top).toFloat())

        days.forEachIndexed { index, day ->
            val height = size.height - y(day.kcal)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + gap), y(day.kcal)),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
        // A cél vonala a szaggatottságtól olvasódik vonatkoztatásnak, nem adatnak.
        val targetY = y(targetKcal)
        drawLine(
            color = targetLineColor,
            start = Offset(0f, targetY),
            end = Offset(size.width, targetY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}
