package hu.mealpilot.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.data.repo.ReportOutcome
import hu.mealpilot.app.data.repo.ReportReason
import hu.mealpilot.app.ui.screens.LegalLinks
import kotlinx.coroutines.launch

/**
 * „Jelentsd ezt a tervet".
 *
 * A Play a generatív AI funkcióknál elvárja, hogy a felhasználó jelenteni tudja a
 * problémás tartalmat — de ettől függetlenül is ez az egyetlen jelzés arról, ha a
 * tervező rendszeresen mellényúl. Ha nincs hova küldeni, e-mailre esik vissza:
 * a visszajelzési út akkor sem szakadhat meg, ha a szolgáltatás nem elérhető.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(
    container: AppContainer,
    kind: ReportKind,
    /** A kifogásolt tartalom, hogy a bejelentés önmagában is értelmezhető legyen. */
    payload: String?,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf(ReportReason.WRONG_NUTRITION) }
    var detail by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Mi a baj vele?") },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ReportReason.entries.forEach { option ->
                        FilterChip(
                            selected = reason == option,
                            onClick = { reason = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(1_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Írd le pár szóban (nem kötelező)") },
                    minLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "A bejelentéssel a kifogásolt fogás vagy terv szövege is elmegy. " +
                        "Testsúly, név és étkezési napló nem.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        val outcome = container.reportRepository.submit(kind, reason, detail, payload)
                        when (outcome) {
                            is ReportOutcome.Sent -> onResult("Köszönjük, megkaptuk. Átnézzük.")
                            is ReportOutcome.UseEmail -> {
                                val sent = context.sendSupportEmail(outcome.subject, outcome.body)
                                onResult(
                                    if (sent) "Nyitottunk egy levelet — küldd el, és megnézzük."
                                    else "Most nem sikerült elküldeni. Írj a ${LegalLinks.SUPPORT_EMAIL} címre."
                                )
                            }
                        }
                        sending = false
                        onDismiss()
                    }
                },
            ) { Text(if (sending) "Küldés…" else "Jelentem") }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) { Text("Mégse") }
        },
    )
}

/** Igaz, ha volt levelező alkalmazás, ami elvállalta. */
private fun android.content.Context.sendSupportEmail(subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${LegalLinks.SUPPORT_EMAIL}")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { startActivity(intent) }.isSuccess
}
