package hu.mealpilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.ActivityLevel
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import hu.mealpilot.core.model.MacroPreset
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile

/**
 * A profil szerkesztő űrlapja. Az onboarding és a beállítások ugyanezt használja,
 * hogy a validáció és a mezők ne csússzanak szét a két helyen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileForm(
    profile: UserProfile,
    onChange: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current

    Column(modifier.fillMaxWidth()) {

        OutlinedTextField(
            value = profile.name,
            onValueChange = { onChange(profile.copy(name = it)) },
            label = { Text(stringResource(R.string.profile_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = profile.sex == Sex.MALE,
                onClick = { onChange(profile.copy(sex = Sex.MALE)) },
                label = { Text(stringResource(R.string.profile_sex_male)) },
            )
            FilterChip(
                selected = profile.sex == Sex.FEMALE,
                onClick = { onChange(profile.copy(sex = Sex.FEMALE)) },
                label = { Text(stringResource(R.string.profile_sex_female)) },
            )
        }
        Text(
            stringResource(R.string.profile_sex_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                initial = profile.ageYears.toString(),
                label = stringResource(R.string.profile_age),
                onValidValue = { onChange(profile.copy(ageYears = it.toInt())) },
                validRange = 14.0..100.0,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                initial = profile.heightCm.trimmed(),
                label = stringResource(R.string.profile_height),
                decimal = true,
                onValidValue = { onChange(profile.copy(heightCm = it)) },
                validRange = 120.0..230.0,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                initial = profile.weightKg.trimmed(),
                label = stringResource(R.string.profile_weight),
                decimal = true,
                onValidValue = { onChange(profile.copy(weightKg = it)) },
                validRange = 35.0..300.0,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                initial = profile.targetWeightKg?.trimmed() ?: "",
                label = stringResource(R.string.profile_target_weight),
                decimal = true,
                allowEmpty = true,
                onValidValue = { onChange(profile.copy(targetWeightKg = it)) },
                onCleared = { onChange(profile.copy(targetWeightKg = null)) },
                validRange = 35.0..300.0,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        NumberField(
            initial = profile.bodyFatPercent?.trimmed() ?: "",
            label = stringResource(R.string.profile_body_fat),
            decimal = true,
            allowEmpty = true,
            onValidValue = { onChange(profile.copy(bodyFatPercent = it)) },
            onCleared = { onChange(profile.copy(bodyFatPercent = null)) },
            validRange = 3.0..70.0,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.profile_activity), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Column {
            ActivityLevel.entries.forEach { level ->
                FilterChip(
                    selected = profile.activityLevel == level,
                    onClick = { onChange(profile.copy(activityLevel = level)) },
                    label = { Text(level.label(language)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        Text(
            stringResource(R.string.profile_activity_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.profile_rate, "%.2f".format(profile.targetRateKgPerWeek)),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = profile.targetRateKgPerWeek.toFloat(),
            onValueChange = { onChange(profile.copy(targetRateKgPerWeek = (it * 100).toInt() / 100.0)) },
            valueRange = 0.1f..1.0f,
            steps = 17,
        )
        Text(
            stringResource(R.string.profile_rate_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.profile_diet_style), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DietStyle.entries.forEach { style ->
                FilterChip(
                    selected = profile.dietStyle == style,
                    onClick = { onChange(profile.copy(dietStyle = style)) },
                    label = { Text(style.label(language)) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        RestrictionSurvey(profile = profile, onChange = onChange)
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.profile_macro_preset), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroPreset.entries.forEach { preset ->
                FilterChip(
                    selected = profile.macroPreset == preset,
                    onClick = { onChange(profile.copy(macroPreset = preset)) },
                    label = { Text(preset.label(language)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.profile_meals_per_day, profile.mealsPerDay),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { count ->
                FilterChip(
                    selected = profile.mealsPerDay == count,
                    onClick = {
                        onChange(
                            profile.copy(
                                mealsPerDay = count,
                                mealTimes = MealSlot.forMealsPerDay(count).map { it.defaultTime },
                            )
                        )
                    },
                    label = { Text("$count") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.profile_meal_times), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        val slots = MealSlot.forMealsPerDay(profile.mealsPerDay)
        slots.forEachIndexed { index, slot ->
            OutlinedTextField(
                value = profile.mealTimes.getOrNull(index) ?: slot.defaultTime,
                onValueChange = { newTime ->
                    val times = MutableList(slots.size) { i ->
                        profile.mealTimes.getOrNull(i) ?: slots[i].defaultTime
                    }
                    times[index] = newTime
                    onChange(profile.copy(mealTimes = times))
                },
                label = { Text(stringResource(R.string.profile_meal_time_field, slot.label(language))) },
                singleLine = true,
                isError = !TIME_REGEX.matches(profile.mealTimes.getOrNull(index) ?: slot.defaultTime),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = profile.preferences,
            onValueChange = { onChange(profile.copy(preferences = it)) },
            label = { Text(stringResource(R.string.profile_preferences)) },
            placeholder = {
                Text(stringResource(R.string.profile_preferences_hint))
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.profile_preferences_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Allergia- és érzékenységfelmérés.
 *
 * Amit itt bejelölsz, az kikerül az étrendből: bemegy a generálási promptba, és a kész
 * tervet gépileg is átnézzük ellene ([hu.mealpilot.core.ai.RestrictionChecker]), mert egy
 * allergiát nem helyes pusztán a modell jólneveltségére bízni.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestrictionSurvey(
    profile: UserProfile,
    onChange: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val implied = DietRestriction.impliedBy(profile.dietStyle)
    val language = LocalAppLanguage.current

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.restrictions_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.restrictions_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DietRestriction.byGroup().forEach { (group, items) ->
            Spacer(Modifier.height(14.dp))
            Text(
                group.label(language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { restriction ->
                    val auto = restriction in implied
                    val checked = auto || restriction in profile.restrictions
                    FilterChip(
                        selected = checked,
                        // Az étrendi stílusból következő kizárásokat nem lehet kikapcsolni:
                        // vegánként a tej akkor is tiltott, ha nincs külön bejelölve.
                        enabled = !auto,
                        onClick = {
                            val next = profile.restrictions.toMutableSet()
                            if (restriction in next) next -= restriction else next += restriction
                            onChange(profile.copy(restrictions = next))
                        },
                        label = { Text(restriction.label(language)) },
                        leadingIcon = if (checked) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
            val notes = items.filter {
                it.note(language).isNotBlank() && (it in profile.restrictions || it in implied)
            }
            notes.forEach { restriction ->
                Text(
                    "${restriction.label(language)}: ${restriction.note(language)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (implied.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.restrictions_implied,
                    profile.dietStyle.label(language),
                    implied.map { it.label(language) }.sorted().joinToString(", "),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val total = profile.effectiveRestrictions.size
        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.restrictions_total, total),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

/**
 * Számbeviteli mező saját szövegállapottal.
 *
 * Azért kell a helyi állapot, mert ha a mező közvetlenül a modellből olvasna, a gépelés
 * közbeni részleges érték (pl. "8" a 85 helyett) azonnal a tartomány aljára ugrana,
 * és a felhasználó nem tudná végigírni a számot. Így csak az érvényes érték megy tovább.
 */
@Composable
private fun NumberField(
    initial: String,
    label: String,
    onValidValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    allowEmpty: Boolean = false,
    onCleared: () -> Unit = {},
    validRange: ClosedFloatingPointRange<Double> = 0.0..1_000_000.0,
) {
    var text by remember { mutableStateOf(initial) }
    val parsed = text.toDoubleOrNull()
    val isError = if (text.isBlank()) !allowEmpty else parsed == null || parsed !in validRange

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }
            val value = text.toDoubleOrNull()
            when {
                text.isBlank() && allowEmpty -> onCleared()
                value != null && value in validRange -> onValidValue(value)
            }
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier,
    )
}

private fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this).replace(',', '.')
