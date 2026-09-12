package hu.mealpilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import hu.mealpilot.core.model.Nutrients
import kotlin.math.roundToInt

/**
 * Étkezés kézi felvitele vagy felülírása.
 *
 * A terv csak javaslat: ha valaki mást evett reggelire, azt rögzíteni kell tudni anélkül,
 * hogy az egész napot vagy a tervet át kellene írni. Csak a kalória kötelező — a makrók
 * megadása opcionális, mert a semminél a hiányos adat is többet ér.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEntrySheet(
    title: String,
    initialName: String = "",
    initialNutrients: Nutrients? = null,
    confirmLabel: String = stringResource(R.string.action_save),
    onDismiss: () -> Unit,
    onSave: (name: String, nutrients: Nutrients) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf(initialNutrients?.kcal?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialNutrients?.proteinG?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialNutrients?.carbsG?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialNutrients?.fatG?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }

    val kcalValue = kcal.toIntOrNull()
    val canSave = name.isNotBlank() && kcalValue != null && kcalValue in 1..5000

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.entry_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.entry_what)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            NumberField(kcal, stringResource(R.string.entry_kcal), Modifier.fillMaxWidth()) { kcal = it }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, stringResource(R.string.entry_protein), Modifier.weight(1f)) { protein = it }
                NumberField(carbs, stringResource(R.string.entry_carbs), Modifier.weight(1f)) { carbs = it }
                NumberField(fat, stringResource(R.string.entry_fat), Modifier.weight(1f)) { fat = it }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onSave(
                            name.trim(),
                            Nutrients(
                                kcal = (kcalValue ?: 0).toDouble(),
                                proteinG = protein.toDoubleOrNull() ?: 0.0,
                                carbsG = carbs.toDoubleOrNull() ?: 0.0,
                                fatG = fat.toDoubleOrNull() ?: 0.0,
                            ),
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text(confirmLabel) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
