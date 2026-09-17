package hu.mealpilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.mealpilot.app.R
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.model.Nutrients
import kotlinx.coroutines.launch
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
    /**
     * Szavakkal leírt étkezés megbecslése. Null, ha a képernyő nem tud becsülni —
     * ilyenkor a mező meg sem jelenik, nem egy működésképtelen gombot mutatunk.
     */
    onEstimate: (suspend (String) -> Result<AiMealEstimate>)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        MealEntryForm(
            title = title,
            initialName = initialName,
            initialNutrients = initialNutrients,
            confirmLabel = confirmLabel,
            onDismiss = onDismiss,
            onSave = onSave,
            onEstimate = onEstimate,
        )
    }
}

/**
 * Az ablak TARTALMA, a felugró keret nélkül.
 *
 * Azért külön, mert a `ModalBottomSheet` saját ablakba rajzol, és az a teszt alatt nem
 * mindig ér földet — a tartalom viszont közönséges Compose-fa, ami bármikor
 * megjeleníthető és állítható. A szétválasztás egyben azt is kimondja, hogy az űrlap
 * semmit nem tud arról, milyen keretben ül.
 */
@Composable
internal fun MealEntryForm(
    title: String,
    initialName: String = "",
    initialNutrients: Nutrients? = null,
    confirmLabel: String = stringResource(R.string.action_save),
    onDismiss: () -> Unit,
    onSave: (name: String, nutrients: Nutrients) -> Unit,
    onEstimate: (suspend (String) -> Result<AiMealEstimate>)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf(initialNutrients?.kcal?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialNutrients?.proteinG?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialNutrients?.carbsG?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialNutrients?.fatG?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }

    var description by remember { mutableStateOf("") }
    var estimating by remember { mutableStateOf(false) }
    var estimateError by remember { mutableStateOf<String?>(null) }
    var assumption by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val kcalValue = kcal.toIntOrNull()
    val kcalOk = kcalValue != null && kcalValue in MIN_KCAL..MAX_KCAL
    val canSave = name.isNotBlank() && kcalOk

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

        // A négy szám kézzel kérése volt eddig az EGYETLEN út. Aki fejből tudja a
        // gyros makróit, az nem ezt az appot használja — ezért a szöveges leírás
        // került előre, a mezők pedig alá, kitöltve vagy javítva.
        if (onEstimate != null) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; estimateError = null },
                label = { Text(stringResource(R.string.entry_describe)) },
                placeholder = { Text(stringResource(R.string.entry_describe_hint)) },
                minLines = 2,
                enabled = !estimating,
                // A címke szövegével nem lehet megbízhatóan megcímezni a mezőt a
                // tesztben; a jelölő erre van, és a felületen nem látszik.
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_DESCRIPTION),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val text = description.trim()
                    if (text.isEmpty()) return@Button
                    estimating = true
                    estimateError = null
                    scope.launch {
                        val result = onEstimate(text)
                        result.onSuccess { estimate ->
                            name = estimate.name
                            kcal = estimate.kcal.roundToInt().coerceIn(0, 9999).toString()
                            protein = estimate.proteinG.roundToInt().coerceIn(0, 9999).toString()
                            carbs = estimate.carbsG.roundToInt().coerceIn(0, 9999).toString()
                            fat = estimate.fatG.roundToInt().coerceIn(0, 9999).toString()
                            assumption = estimate.assumption.takeIf { it.isNotBlank() }
                        }.onFailure {
                            estimateError = it.message
                        }
                        estimating = false
                    }
                },
                enabled = !estimating && description.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (estimating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    stringResource(
                        if (estimating) R.string.entry_estimating else R.string.entry_estimate
                    )
                )
            }
            estimateError?.let {
                Spacer(Modifier.height(8.dp))
                WarningNote(it)
            }
            // Amit a becslés feltételezett. Ez nem dísz: ebből derül ki, hogy egy
            // adaggal vagy kettővel számolt — és ezt a felhasználó tudja javítani.
            assumption?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.entry_what)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        NumberField(kcal, stringResource(R.string.entry_kcal), Modifier.fillMaxWidth()) { kcal = it }
        // A Mentés gomb a kalóriától függ. Amíg ez a sor nem volt itt, a gomb
        // MAGYARÁZAT NÉLKÜL tiltódott le: a becslés 9999-ig tölthette ki a mezőt,
        // a mentés viszont 5000-nél megállt. Aki egy egész pizzát írt be, csak egy
        // halott gombot látott, és nem tudhatta, melyik szám a baj.
        if (kcal.isNotBlank() && !kcalOk) {
            Spacer(Modifier.height(6.dp))
            WarningNote(stringResource(R.string.entry_kcal_range, MIN_KCAL, MAX_KCAL))
        }
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

/** A szöveges leírás mezőjének jelölője — a teszt ezzel találja meg. */
internal const val TAG_DESCRIPTION = "entry-description"

/**
 * Amit egyetlen naplóbejegyzés kalóriája felvehet.
 *
 * A felső határ elgépelés ellen véd (a 4200 helyett beütött 42000 az egész napi
 * összesítőt hazuggá tenné), nem a felhasználó ellen: aki tényleg ennyit evett,
 * két bejegyzésre bontja. A számokat a figyelmeztető szöveg is kiírja, hogy ne
 * kelljen kitalálni őket.
 */
internal const val MIN_KCAL = 1
internal const val MAX_KCAL = 5000

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
