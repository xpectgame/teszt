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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.mealpilot.core.i18n.AppLanguage

/**
 * Nyelvválasztás az első indításkor.
 *
 * Az első képernyő, még az adatfelvétel előtt — és nem véletlenül: ez nem csak a
 * feliratokat állítja be, hanem az ÉTREND nyelvét is. A fogásnevek, a hozzávalók és a
 * bevásárlólista is a választott nyelven készül, ezért utólag váltani annyit jelent,
 * hogy a meglévő terv más nyelven marad, mint a felület.
 */
@Composable
fun LanguageScreen(
    initial: AppLanguage,
    onConfirm: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(initial) }

    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Milyen nyelven beszéljünk?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "What language should we use?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ez az étrend nyelvét is meghatározza: a fogásnevek, a hozzávalók és a " +
                "bevásárlólista is ezen a nyelven készül.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "This also sets the language of your meal plans: dish names, ingredients and " +
                "the shopping list are all written in it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        AppLanguage.entries.forEach { language ->
            LanguageRow(
                language = language,
                selected = selected == language,
                onSelect = { selected = language },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onConfirm(selected) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Mindkét nyelven, mert ezen a képernyőn még nincs eldöntve, melyiket érti.
            Text(if (selected == AppLanguage.EN) "Continue" else "Tovább")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Később a Beállításokban átváltható. · You can change this later in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val subtitle = when (language) {
        AppLanguage.HU -> "Hazai alapanyagokkal, magyar fogásnevekkel"
        AppLanguage.EN -> "Recipes and shopping list in English"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    language.selfName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
