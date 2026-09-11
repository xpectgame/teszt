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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.BuildConfig
import hu.mealpilot.app.data.prefs.AiEffort
import hu.mealpilot.app.data.prefs.AiModel
import hu.mealpilot.app.data.prefs.AppSettings
import hu.mealpilot.app.notify.MealAlarmScheduler
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.ui.components.ProfileForm
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.billing.BillingPeriod
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings
    val profile = container.settings.profile

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
    val scope = rememberCoroutineScope()

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
        TextButton(onClick = onBack) { Text("← Vissza") }
        Text("Beállítások", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Csomag") {
            val plan = entitlement
            Text(
                plan?.tier?.hu ?: "…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            if (plan != null && !plan.isPremium) {
                Text(
                    "Ebben a hónapban még ${plan.remainingPlans()} étrend és " +
                        "${plan.remainingMessages()} üzenet maradt. " +
                        "A keret ${BillingPeriod.daysUntilReset()} nap múlva nullázódik.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                    Text("Teljes csomag")
                }
            } else if (plan != null) {
                Text(
                    "Korlátlan étrend és beszélgetés. Köszönjük!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { context.openUrl(LegalLinks.manageSubscription(context.packageName)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Előfizetés kezelése") }
            }
        }

        Spacer(Modifier.height(12.dp))

        // A modellválasztás és a hozzáférési kulcs nem felhasználói döntés: aki az appot
        // használja, étrendet akar, nem tervezőmotort konfigurálni. Ezért ezek a Névjegy
        // hétszeri megérintésével előhozható fejlesztői részbe kerültek.
        if (current.developerMode) {
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
                    Text(
                        "⚠ A titkosított tároló nem érhető el ezen az eszközön, ezért a kulcs " +
                            "egyszerű tárolóba került. Csak akkor add meg, ha ezt elfogadod.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
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
        SectionCard(title = "Emlékeztetők") {
            SettingSwitch(
                label = "Étkezési emlékeztetők",
                checked = current.remindersEnabled,
                onChange = { viewModel.saveSettings(current.copy(remindersEnabled = it)) },
            )
            SettingSwitch(
                label = "Esti összefoglaló",
                checked = current.dailySummaryEnabled,
                onChange = { viewModel.saveSettings(current.copy(dailySummaryEnabled = it)) },
            )
            Spacer(Modifier.height(8.dp))
            // A csúszkákat helyi állapoton mozgatjuk, és csak elengedéskor mentünk —
            // különben minden pixelnyi mozgás külön DataStore írást indítana.
            var summaryHour by remember(current.dailySummaryHour) {
                mutableStateOf(current.dailySummaryHour.toFloat())
            }
            Text("Összefoglaló ideje: ${summaryHour.toInt()}:00", style = MaterialTheme.typography.labelLarge)
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
            Text("Előre szóljon: ${leadMinutes.toInt()} perccel", style = MaterialTheme.typography.labelLarge)
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
                    "A pontos időzítés nincs engedélyezve, ezért az emlékeztetők ±10 percen belül szólnak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Pontos időzítés engedélyezése") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Mozgás beszámítása") {
            var eatBack by remember(current.eatBackRatio) { mutableStateOf(current.eatBackRatio.toFloat()) }
            Text(
                "Az elégetett kalória ${(eatBack * 100).toInt()}%-a írható vissza a napi keretbe.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = eatBack,
                onValueChange = { eatBack = it },
                onValueChangeFinished = {
                    viewModel.saveSettings(current.copy(eatBackRatio = (eatBack * 20).toInt() / 20.0))
                },
                valueRange = 0f..1f,
                steps = 19,
            )
            Text(
                "Az edzésbecslések jellemzően felülbecsülnek, ezért az 50% biztonságos alapérték.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Profil") {
            ProfileForm(profile = profile, onChange = { editedProfile = it })
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.saveProfile(profile)
                    scope.launch { snackbarHostState.showSnackbar("Profil mentve. Az új kalóriakeret a következő tervnél él.") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Profil mentése") }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Jogi tudnivalók és adatok") {
            TextButton(onClick = { context.openUrl(LegalLinks.TERMS) }) {
                Text("Felhasználási feltételek")
            }
            TextButton(onClick = { context.openUrl(LegalLinks.PRIVACY) }) {
                Text("Adatkezelési tájékoztató")
            }
            TextButton(onClick = { context.openUrl(LegalLinks.DELETE_DATA) }) {
                Text("Adatok törlése — mi hol van")
            }
            TextButton(onClick = { context.openUrl(LegalLinks.SUPPORT) }) {
                Text("Támogatás és gyakori kérdések")
            }
            TextButton(onClick = { context.openUrl("mailto:${LegalLinks.SUPPORT_EMAIL}") }) {
                Text("Kapcsolat: ${LegalLinks.SUPPORT_EMAIL}")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Az étrended, a naplóid és a testadataid a telefonodon maradnak. " +
                    "Tervezéskor a profilodból származó adatok (nem, életkor, testadatok, " +
                    "étrendi kizárások, kéréseid) kimennek a tervezőszolgáltatáshoz.",
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
                    Text("Hibajelentés és névtelen statisztika", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Ha az app összeomlik, elküldi a hiba helyét, és napi szinten " +
                            "megszámolja, hány terv és bejegyzés készül. Étrend, napló, " +
                            "testadat nem megy el. Kikapcsolva a gyűjtés is leáll.",
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
                    "Elfogadva: " + java.time.Instant.ofEpochMilli(current.acceptedTermsAtMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "A feltételek frissültek. Kérünk, nézd át és fogadd el.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = { viewModel.acceptTerms() }) { Text("Elfogadom") }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { confirmWipe = true }) { Text("Minden adat törlése") }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Névjegy") {
            Text(
                "MealPilot ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    if (current.developerMode) return@clickable
                    versionTaps++
                    if (versionTaps >= 7) {
                        viewModel.saveSettings(current.copy(developerMode = true))
                        scope.launch { snackbarHostState.showSnackbar("Fejlesztői beállítások bekapcsolva.") }
                    }
                },
            )
            if (container.isOwnerBuild) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tulajdonosi build — a teljes csomag vásárlás nélkül aktív. " +
                        "Ezt a csomagot ne töltsd fel a Play Console-ba.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Az étrendeket gépi tervező állítja össze a megadott adataid alapján. " +
                    "Az app tájékoztató jellegű, nem orvosi tanács — betegség, terhesség vagy " +
                    "rendszeres gyógyszerszedés esetén beszéld át orvossal.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Minden adat törlése") },
            text = {
                Text(
                    "Törlődik az étrended, az összes naplód, a súly- és mozgásadataid, a " +
                        "beszélgetésed és a beállításaid. Ez nem vonható vissza. " +
                        "Az előfizetésedet ez nem mondja le."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmWipe = false
                    viewModel.wipeAllData()
                    scope.launch { snackbarHostState.showSnackbar("Minden adat törölve.") }
                }) { Text("Törlés") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Mégse") } },
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
