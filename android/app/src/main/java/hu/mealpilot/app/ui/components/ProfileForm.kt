package hu.mealpilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.model.ActivityLevel
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
    Column(modifier.fillMaxWidth()) {

        OutlinedTextField(
            value = profile.name,
            onValueChange = { onChange(profile.copy(name = it)) },
            label = { Text("Neved (opcionális)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Text("Biológiai nem", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = profile.sex == Sex.MALE,
                onClick = { onChange(profile.copy(sex = Sex.MALE)) },
                label = { Text("Férfi") },
            )
            FilterChip(
                selected = profile.sex == Sex.FEMALE,
                onClick = { onChange(profile.copy(sex = Sex.FEMALE)) },
                label = { Text("Nő") },
            )
        }
        Text(
            "Csak az anyagcsere-képletekhez kell.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                initial = profile.ageYears.toString(),
                label = "Életkor",
                onValidValue = { onChange(profile.copy(ageYears = it.toInt())) },
                validRange = 14.0..100.0,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                initial = profile.heightCm.trimmed(),
                label = "Magasság (cm)",
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
                label = "Testsúly (kg)",
                decimal = true,
                onValidValue = { onChange(profile.copy(weightKg = it)) },
                validRange = 35.0..300.0,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                initial = profile.targetWeightKg?.trimmed() ?: "",
                label = "Célsúly (kg)",
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
            label = "Testzsír % (ha tudod — pontosabb számítás)",
            decimal = true,
            allowEmpty = true,
            onValidValue = { onChange(profile.copy(bodyFatPercent = it)) },
            onCleared = { onChange(profile.copy(bodyFatPercent = null)) },
            validRange = 3.0..70.0,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Text("Napi mozgás edzés nélkül", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Column {
            ActivityLevel.entries.forEach { level ->
                FilterChip(
                    selected = profile.activityLevel == level,
                    onClick = { onChange(profile.copy(activityLevel = level)) },
                    label = { Text(level.hu) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        Text(
            "A naplózott edzések ezen felül számítanak — így nem duplázódik a mozgás.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "Fogyás üteme: ${"%.2f".format(profile.targetRateKgPerWeek)} kg/hét",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = profile.targetRateKgPerWeek.toFloat(),
            onValueChange = { onChange(profile.copy(targetRateKgPerWeek = (it * 100).toInt() / 100.0)) },
            valueRange = 0.1f..1.0f,
            steps = 17,
        )
        Text(
            "0,25–0,75 kg/hét a fenntartható sáv. Az app nem enged az alapanyagcsere alá menni.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text("Étrendi stílus", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DietStyle.entries.forEach { style ->
                FilterChip(
                    selected = profile.dietStyle == style,
                    onClick = { onChange(profile.copy(dietStyle = style)) },
                    label = { Text(style.hu) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("Makró beállítás", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroPreset.entries.forEach { preset ->
                FilterChip(
                    selected = profile.macroPreset == preset,
                    onClick = { onChange(profile.copy(macroPreset = preset)) },
                    label = { Text(preset.hu) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("Étkezések száma naponta: ${profile.mealsPerDay}", style = MaterialTheme.typography.labelLarge)
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

        Text("Étkezési időpontok", style = MaterialTheme.typography.labelLarge)
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
                label = { Text("${slot.hu} (ÓÓ:PP)") },
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
            label = { Text("Állandó preferenciák") },
            placeholder = {
                Text("pl. laktózérzékeny vagyok, nem eszem kelbimbót, hétköznap max 20 perc főzés, olcsó alapanyagok")
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Ezt minden tervezésnél figyelembe veszi az AI.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
