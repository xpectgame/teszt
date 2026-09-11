package hu.mealpilot.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.mealpilot.core.model.ActivityLevel
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import hu.mealpilot.core.model.MacroPreset
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mealpilot_settings")

/** Melyik modellel dolgozzon az app. A drágább modell pontosabban tartja a kalóriakereteket. */
enum class AiModel(val id: String, val label: String, val note: String) {
    SONNET("claude-sonnet-5", "Claude Sonnet 5", "Alapértelmezett: gyors és pontosan tartja a keretet"),
    OPUS("claude-opus-5", "Claude Opus 5", "Alaposabb, de lassabb és nagyságrenddel drágább"),
    HAIKU("claude-haiku-4-5", "Claude Haiku 4.5", "Leggyorsabb és legolcsóbb, pontatlanabb");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: SONNET
    }
}

enum class AiEffort(val apiValue: String, val label: String) {
    LOW("low", "Gyors"),
    MEDIUM("medium", "Kiegyensúlyozott"),
    HIGH("high", "Alapos");

    companion object {
        fun fromValue(value: String?) = entries.firstOrNull { it.apiValue == value } ?: MEDIUM
    }
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** Hány perccel az étkezés előtt szóljon. */
    val reminderLeadMinutes: Int = 0,
    val dailySummaryHour: Int = 21,
    val dailySummaryEnabled: Boolean = true,
    /** Az elégetett edzéskalória hány része írható jóvá a napi keretbe. */
    val eatBackRatio: Double = 0.5,
    val model: AiModel = AiModel.SONNET,
    val effort: AiEffort = AiEffort.MEDIUM,
    /**
     * Fejlesztői mód. A modellválasztás és a saját hozzáférési kulcs a felhasználónak
     * nem döntés — a tervezőmotor beállítása a fejlesztő dolga —, ezért ezek csak akkor
     * jelennek meg, ha ezt a kapcsolót valaki szándékosan bekapcsolja.
     */
    val developerMode: Boolean = false,
)

class SettingsRepository(context: Context) {

    private val store = context.dataStore

    val profile: Flow<UserProfile> = store.data.map { it.toProfile() }
    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun currentProfile(): UserProfile = profile.first()
    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun saveProfile(profile: UserProfile) {
        store.edit { p ->
            p[K_NAME] = profile.name
            p[K_SEX] = profile.sex.name
            p[K_AGE] = profile.ageYears
            p[K_HEIGHT] = profile.heightCm
            p[K_WEIGHT] = profile.weightKg
            profile.bodyFatPercent?.let { p[K_BODY_FAT] = it } ?: p.remove(K_BODY_FAT)
            p[K_ACTIVITY] = profile.activityLevel.name
            profile.targetWeightKg?.let { p[K_TARGET_WEIGHT] = it } ?: p.remove(K_TARGET_WEIGHT)
            p[K_RATE] = profile.targetRateKgPerWeek
            p[K_DIET] = profile.dietStyle.name
            p[K_MACRO] = profile.macroPreset.name
            p[K_MEALS_PER_DAY] = profile.mealsPerDay
            p[K_RESTRICTIONS] = profile.restrictions.map { it.name }.toSet()
            p[K_PREFERENCES] = profile.preferences
            p[K_MEAL_TIMES] = profile.mealTimes.joinToString(",")
        }
    }

    /** A napi súlyméréskor a profil súlyát is frissítjük, hogy a kalóriakeret követni tudja. */
    suspend fun updateWeight(weightKg: Double, bodyFatPercent: Double?) {
        store.edit { p ->
            p[K_WEIGHT] = weightKg
            bodyFatPercent?.let { p[K_BODY_FAT] = it }
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        store.edit { p ->
            p[K_ONBOARDING] = settings.onboardingDone
            p[K_REMINDERS] = settings.remindersEnabled
            p[K_LEAD_MINUTES] = settings.reminderLeadMinutes
            p[K_SUMMARY_HOUR] = settings.dailySummaryHour
            p[K_SUMMARY_ENABLED] = settings.dailySummaryEnabled
            p[K_EAT_BACK] = settings.eatBackRatio
            p[K_MODEL] = settings.model.id
            p[K_EFFORT] = settings.effort.apiValue
            p[K_DEVELOPER] = settings.developerMode
        }
    }

    /** Minden beállítás törlése — az app a bekapcsolás utáni állapotba kerül. */
    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[K_ONBOARDING] = done }
    }

    private fun Preferences.toProfile() = UserProfile(
        name = this[K_NAME] ?: "",
        sex = runCatching { Sex.valueOf(this[K_SEX] ?: "MALE") }.getOrDefault(Sex.MALE),
        ageYears = this[K_AGE] ?: 30,
        heightCm = this[K_HEIGHT] ?: 178.0,
        weightKg = this[K_WEIGHT] ?: 85.0,
        bodyFatPercent = this[K_BODY_FAT],
        activityLevel = runCatching { ActivityLevel.valueOf(this[K_ACTIVITY] ?: "LIGHT") }
            .getOrDefault(ActivityLevel.LIGHT),
        targetWeightKg = this[K_TARGET_WEIGHT],
        targetRateKgPerWeek = this[K_RATE] ?: 0.5,
        dietStyle = runCatching { DietStyle.valueOf(this[K_DIET] ?: "OMNIVORE") }
            .getOrDefault(DietStyle.OMNIVORE),
        macroPreset = runCatching { MacroPreset.valueOf(this[K_MACRO] ?: "HIGH_PROTEIN") }
            .getOrDefault(MacroPreset.HIGH_PROTEIN),
        mealsPerDay = this[K_MEALS_PER_DAY] ?: 4,
        restrictions = this[K_RESTRICTIONS]
            ?.mapNotNull(DietRestriction::byName)
            ?.toSet()
            ?: emptySet(),
        preferences = this[K_PREFERENCES] ?: "",
        mealTimes = this[K_MEAL_TIMES]?.split(",")?.filter { it.isNotBlank() }
            ?: listOf("07:30", "12:30", "16:00", "19:30"),
    )

    private fun Preferences.toSettings() = AppSettings(
        onboardingDone = this[K_ONBOARDING] ?: false,
        remindersEnabled = this[K_REMINDERS] ?: true,
        reminderLeadMinutes = this[K_LEAD_MINUTES] ?: 0,
        dailySummaryHour = this[K_SUMMARY_HOUR] ?: 21,
        dailySummaryEnabled = this[K_SUMMARY_ENABLED] ?: true,
        eatBackRatio = this[K_EAT_BACK] ?: 0.5,
        model = AiModel.fromId(this[K_MODEL]),
        effort = AiEffort.fromValue(this[K_EFFORT]),
        developerMode = this[K_DEVELOPER] ?: false,
    )

    private companion object {
        val K_NAME = stringPreferencesKey("name")
        val K_SEX = stringPreferencesKey("sex")
        val K_AGE = intPreferencesKey("age")
        val K_HEIGHT = doublePreferencesKey("height_cm")
        val K_WEIGHT = doublePreferencesKey("weight_kg")
        val K_BODY_FAT = doublePreferencesKey("body_fat")
        val K_ACTIVITY = stringPreferencesKey("activity")
        val K_TARGET_WEIGHT = doublePreferencesKey("target_weight")
        val K_RATE = doublePreferencesKey("rate_kg_per_week")
        val K_DIET = stringPreferencesKey("diet_style")
        val K_MACRO = stringPreferencesKey("macro_preset")
        val K_MEALS_PER_DAY = intPreferencesKey("meals_per_day")
        val K_RESTRICTIONS = stringSetPreferencesKey("restrictions")
        val K_PREFERENCES = stringPreferencesKey("preferences")
        val K_MEAL_TIMES = stringPreferencesKey("meal_times")

        val K_ONBOARDING = booleanPreferencesKey("onboarding_done")
        val K_REMINDERS = booleanPreferencesKey("reminders_enabled")
        val K_LEAD_MINUTES = intPreferencesKey("reminder_lead_minutes")
        val K_SUMMARY_HOUR = intPreferencesKey("summary_hour")
        val K_SUMMARY_ENABLED = booleanPreferencesKey("summary_enabled")
        val K_EAT_BACK = doublePreferencesKey("eat_back_ratio")
        val K_MODEL = stringPreferencesKey("ai_model")
        val K_EFFORT = stringPreferencesKey("ai_effort")
        val K_DEVELOPER = booleanPreferencesKey("developer_mode")
    }
}
