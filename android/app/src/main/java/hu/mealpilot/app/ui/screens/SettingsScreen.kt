package hu.mealpilot.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.BuildConfig
import hu.mealpilot.app.data.telemetry.CrashReporter
import hu.mealpilot.app.data.prefs.AiEffort
import hu.mealpilot.app.data.prefs.AiModel
import hu.mealpilot.app.data.prefs.AppSettings
import hu.mealpilot.app.notify.MealAlarmScheduler
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.ui.components.BackButton
import hu.mealpilot.app.ui.components.ProfileForm
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.components.WarningNote
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings
    val profile = container.settings.profile

    /**
     * Kiírja az adatokat a gyorsítótár `export/` könyvtárába, és visszaadja a
     * megosztható hivatkozást. Null, ha nem sikerült — a hívó ilyenkor szól.
     *
     * Miért a gyorsítótárba: a fájl egyszer használatos, a rendszer takaríthatja, és
     * csak ez az egy könyvtár van megosztásra engedve (`file_paths.xml`).
     */
    fun exportData(onDone: (android.net.Uri?) -> Unit) = viewModelScope.launch {
        val uri = runCatching {
            val directory = java.io.File(container.appContext.cacheDir, "export").apply { mkdirs() }
            val file = java.io.File(directory, container.exportRepository.fileName())
            file.writeText(container.exportRepository.buildJson())
            androidx.core.content.FileProvider.getUriForFile(
                container.appContext,
                "${container.appContext.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
        onDone(uri)
    }

    fun maskedKey(): String? = container.secureKeyStore.maskedApiKey()
    fun usingInsecureFallback(): Boolean = container.secureKeyStore.usingFallback

    fun saveApiKey(value: String) {
        container.secureKeyStore.setApiKey(value)
    }

    fun clearApiKey() {
        container.secureKeyStore.setApiKey(null)
    }

    fun saveSettings(settings: AppSettings) = viewModelScope.launch {
        container.settings.saveSettings(settings)
        ReminderRefreshWorker.refreshNow(container.appContext)
    }

    /** A frissített jogi szövegek elfogadása a beállításokból. */
    fun acceptTerms() = viewModelScope.launch {
        container.settings.recordConsent(LegalLinks.VERSION)
    }

    val entitlement = container.entitlements.entitlement

    fun setDeveloperPremium(enabled: Boolean) = viewModelScope.launch {
        container.entitlements.setDeveloperPremium(enabled)
    }

    fun wipeAllData() = viewModelScope.launch {
        container.wipeAllData()
    }

    fun saveProfile(profile: UserProfile) = viewModelScope.launch {
        container.settings.saveProfile(profile)
        ReminderRefreshWorker.refreshNow(container.appContext)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = containerFactory(container) { SettingsViewModel(it) })
    val settings by viewModel.settings.collectAsState(initial = null)
    val storedProfile by viewModel.profile.collectAsState(initial = null)
    val context = LocalContext.current
    // A visszajelzés szövegei a lambdákon kívül: ott @Composable hívás nem lehet.
    val exportFailed = stringResource(R.string.settings_export_failed)
    val exportShare = stringResource(R.string.settings_export_share)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val language by container.languageStore.language.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var maskedKey by remember { mutableStateOf(viewModel.maskedKey()) }
    var editedProfile by remember { mutableStateOf<UserProfile?>(null) }
    var versionTaps by remember { mutableStateOf(0) }

    LaunchedEffect(storedProfile) {
        if (editedProfile == null && storedProfile != null) editedProfile = storedProfile
    }

    val current = settings ?: return
    val profile = editedProfile ?: return
    val entitlement by viewModel.entitlement.collectAsState(initial = null)
    var confirmWipe by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BackButton(stringResource(R.string.action_back), onBack)
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        SectionCard(title = stringResource(R.string.settings_plan)) {
            val plan = entitlement
            Text(
                plan?.tier?.label(language) ?: "…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            if (plan != null && !plan.isPremium) {
                Text(
                    stringResource(
                        R.string.settings_quota_left,
                        plan.remainingPlans(),
                        plan.remainingMessages(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_go_premium))
                }
            } else if (plan != null) {
                Text(
                    stringResource(R.string.settings_premium_thanks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { context.openUrl(LegalLinks.manageSubscription(context.packageName)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_manage_subscription)) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // A modellválasztás és a hozzáférési kulcs nem felhasználói döntés: aki az appot
        // használja, étrendet akar, nem tervezőmotort konfigurálni. Ezért ezek a Névjegy
        // hétszeri megérintésével előhozható fejlesztői részbe kerültek.
        // A fejlesztői rész szövegei szándékosan magyarok maradnak: ide csak a
        // verziószám hétszeri megérintésével lehet bejutni, felhasználóhoz nem jut el,
        // és a kétnyelvűsítése csak karbantartandó fordítást szülne.
        if (current.developerMode) {
            // Az elmentett összeomlások. A CrashReporter eddig is fájlba írta őket, de
            // csak a backend olvasta vissza — backend nélküli buildben tehát senki.
            // Éppen a tesztelésnél, ahol a legtöbbet érnének.
            var crashes by remember { mutableStateOf(CrashReporter.pending(context)) }
            if (crashes.isNotEmpty()) {
                SectionCard(title = "Összeomlások (${crashes.size})") {
                    crashes.asReversed().forEach { crash ->
                        Text(
                            crash.fingerprint,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            crash.exception,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            // A teljes verem hosszú; az eleje mondja meg, hol tört el.
                            crash.stack.lineSequence().take(24).joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    crashes.joinToString("\n\n---\n\n") {
                                        "${it.fingerprint}\n${it.exception}\n${it.stack}"
                                    }
                                )
                            )
                        }) { Text("Másolás") }
                        OutlinedButton(onClick = {
                            CrashReporter.clear(context)
                            crashes = emptyList()
                            scope.launch { snackbarHostState.showSnackbar("Összeomlások törölve.") }
                        }) { Text("Törlés") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionCard(title = "Fejlesztői beállítások") {
                // Melyik úton mennek ki a hívások. Ez a leggyakoribb félreértés forrása
                // tesztelés közben: a saját kulcs megelőzi a backendet.
                Text(
                    when {
                        maskedKey != null ->
                            "Tervező: saját Anthropic kulcs (a backendet megelőzi, kvóta nélkül)."

                        container.backendClient != null ->
                            "Tervező: saját backend — ${BuildConfig.BACKEND_URL}"

                        else ->
                            "Tervező: beépített offline sablonok. Nincs backend a buildben " +
                                "(MEALPILOT_BACKEND_URL) és nincs megadott kulcs sem."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Saját Anthropic hozzáférési kulcs. A kulcs a telefonon marad, titkosítva, " +
                        "és nem kerül felhőmentésbe. Éles kiadásban ezt a saját backend váltja ki.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (viewModel.usingInsecureFallback()) {
                    Spacer(Modifier.height(8.dp))
                    WarningNote(
                        "A titkosított tároló nem érhető el ezen az eszközön, ezért a kulcs " +
                            "egyszerű tárolóba került. Csak akkor add meg, ha ezt elfogadod.",
                    )
                }
                Spacer(Modifier.height(12.dp))
                maskedKey?.let {
                    Text("Jelenlegi kulcs: $it", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it.trim() },
                    label = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveApiKey(apiKeyInput)
                            maskedKey = viewModel.maskedKey()
                            apiKeyInput = ""
                            scope.launch { snackbarHostState.showSnackbar("Kulcs elmentve.") }
                        },
                        enabled = apiKeyInput.length > 20,
                    ) { Text("Mentés") }
                    if (maskedKey != null) {
                        OutlinedButton(onClick = {
                            viewModel.clearApiKey()
                            maskedKey = null
                            scope.launch { snackbarHostState.showSnackbar("Kulcs törölve.") }
                        }) { Text("Törlés") }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Modell", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiModel.entries.forEach { model ->
                        FilterChip(
                            selected = current.model == model,
                            onClick = { viewModel.saveSettings(current.copy(model = model)) },
                            label = { Text(model.label) },
                        )
                    }
                }
                Text(
                    current.model.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Text("Alaposság", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiEffort.entries.forEach { effort ->
                        FilterChip(
                            selected = current.effort == effort,
                            onClick = { viewModel.saveSettings(current.copy(effort = effort)) },
                            label = { Text(effort.label) },
                        )
                    }
                }
                Text(
                    "Az alaposabb beállítás pontosabban tartja a kalóriakeretet, de több tokent használ.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))
                SettingSwitch(
                    label = "Teljes csomag teszthez",
                    checked = entitlement?.isPremium == true,
                    onChange = { viewModel.setDeveloperPremium(it) },
                )
                Text(
                    "Vásárlás nélkül bekapcsolja a fizetős funkciókat, hogy tesztelhető " +
                        "legyen, mielőtt a Play Console-ban él a termék.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    versionTaps = 0
                    viewModel.saveSettings(current.copy(developerMode = false))
                }) { Text("Fejlesztői mód kikapcsolása") }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.settings_language)) {
            Text(
                stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = {
                            if (option != language) {
                                container.languageStore.set(option)
                                // Az Activity a nyelvet indításkor veszi fel, ezért újra
                                // kell építeni — enélkül a fele felület a régi nyelven
                                // maradna a következő indításig.
                                context.findActivity()?.recreate()
                            }
                        },
                        label = { Text(option.selfName) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.settings_reminders)) {
            SettingSwitch(
                label = stringResource(R.string.settings_meal_reminders),
                checked = current.remindersEnabled,
                onChange = { viewModel.saveSettings(current.copy(remindersEnabled = it)) },
            )
            SettingSwitch(
                label = stringResource(R.string.settings_evening_summary),
                checked = current.dailySummaryEnabled,
                onChange = { viewModel.saveSettings(current.copy(dailySummaryEnabled = it)) },
            )
            Spacer(Modifier.height(8.dp))
            // A csúszkákat helyi állapoton mozgatjuk, és csak elengedéskor mentünk —
            // különben minden pixelnyi mozgás külön DataStore írást indítana.
            var summaryHour by remember(current.dailySummaryHour) {
                mutableStateOf(current.dailySummaryHour.toFloat())
            }
            Text(
                stringResource(R.string.settings_summary_time, summaryHour.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = summaryHour,
                onValueChange = { summaryHour = it },
                onValueChangeFinished = {
                    viewModel.saveSettings(current.copy(dailySummaryHour = summaryHour.toInt()))
                },
                valueRange = 16f..23f,
                steps = 6,
            )
            Spacer(Modifier.height(8.dp))
            var leadMinutes by remember(current.reminderLeadMinutes) {
                mutableStateOf(current.reminderLeadMinutes.toFloat())
            }
            Text(
                stringResource(R.string.settings_lead_minutes, leadMinutes.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = leadMinutes,
                onValueChange = { leadMinutes = it },
                onValueChangeFinished = {
                    viewModel.saveSettings(current.copy(reminderLeadMinutes = leadMinutes.toInt()))
                },
                valueRange = 0f..60f,
                steps = 11,
            )

            if (!MealAlarmScheduler.canScheduleExact(context)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_inexact_alarms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text(stringResource(R.string.settings_allow_exact_alarms)) }
                }
            }
        }


        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.settings_profile)) {
            // A hibás számot a mező nem adja tovább, tehát a `profile` a RÉGI értéket
            // hordozza. Mentés gomb nélkül ez azt jelentette, hogy a felhasználó a
            // beírt számát látta, kapott egy „Profil elmentve." üzenetet, és közben az
            // app a régivel számolt tovább. A gombot ezért a mezők érvényessége tiltja.
            var profileValid by remember { mutableStateOf(true) }
            ProfileForm(
                profile = profile,
                onChange = { editedProfile = it },
                onValidityChange = { profileValid = it },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.saveProfile(profile)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.settings_profile_saved))
                    }
                },
                enabled = profileValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_save_profile)) }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.settings_legal)) {
            TextButton(
                enabled = LegalLinks.isConfigured,
                onClick = { context.openUrl(LegalLinks.terms(language)) },
            ) {
                Text(stringResource(R.string.settings_terms))
            }
            TextButton(
                enabled = LegalLinks.isConfigured,
                onClick = { context.openUrl(LegalLinks.privacy(language)) },
            ) {
                Text(stringResource(R.string.settings_privacy))
            }
            TextButton(
                enabled = LegalLinks.isConfigured,
                onClick = { context.openUrl(LegalLinks.deleteData(language)) },
            ) {
                Text(stringResource(R.string.settings_delete_data))
            }
            // A kivitel a törlés MELLETT van: a kettő ugyanannak a kérdésnek a két
            // fele — mi van rólam tárolva, és hogyan szabadulok meg tőle.
            TextButton(onClick = {
                viewModel.exportData { uri ->
                    if (uri == null) {
                        scope.launch { snackbarHostState.showSnackbar(exportFailed) }
                    } else {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                exportShare,
                            )
                        )
                    }
                }
            }) {
                Text(stringResource(R.string.settings_export))
            }
            Text(
                stringResource(R.string.settings_export_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = LegalLinks.isConfigured,
                onClick = { context.openUrl(LegalLinks.support(language)) },
            ) {
                Text(stringResource(R.string.settings_support))
            }
            TextButton(onClick = { context.openUrl("mailto:${LegalLinks.SUPPORT_EMAIL}") }) {
                Text(stringResource(R.string.settings_contact, LegalLinks.SUPPORT_EMAIL))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_data_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_telemetry), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_telemetry_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = current.telemetryEnabled,
                    onCheckedChange = { viewModel.saveSettings(current.copy(telemetryEnabled = it)) },
                )
            }

            Spacer(Modifier.height(12.dp))
            // Az elfogadás tényét a Play is elvárja, és vita esetén ez az egyetlen
            // nyoma annak, hogy a felhasználó mikor és melyik szöveget fogadta el.
            if (current.acceptedTermsVersion == LegalLinks.VERSION) {
                Text(
                    stringResource(
                        R.string.settings_accepted_on,
                        java.time.Instant.ofEpochMilli(current.acceptedTermsAtMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.settings_terms_updated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = { viewModel.acceptTerms() }) { Text(stringResource(R.string.settings_accept)) }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { confirmWipe = true }) { Text(stringResource(R.string.settings_wipe)) }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.settings_about)) {
            Text(
                "MealPilot ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    if (current.developerMode) return@clickable
                    versionTaps++
                    if (versionTaps >= 7) {
                        viewModel.saveSettings(current.copy(developerMode = true))
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.settings_dev_on))
                        }
                    }
                },
            )
            if (container.isOwnerBuild) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_owner_build),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.settings_wipe)) },
            text = {
                Text(
                    stringResource(R.string.settings_wipe_body)
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmWipe = false
                    viewModel.wipeAllData()
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_wiped)) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
