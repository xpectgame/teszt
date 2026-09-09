package hu.mealpilot.core.model

/** Biológiai nem — kizárólag az anyagcsere-képletek (Mifflin-St Jeor, Keytel) bemenete. */
enum class Sex { MALE, FEMALE }

/**
 * Napi mozgásszint EDZÉS NÉLKÜL (NEAT: munka, közlekedés, háztartás).
 * A naplózott edzések ezen felül adódnak hozzá — így nem duplázódik a mozgás.
 */
enum class ActivityLevel(val factor: Double, val hu: String) {
    SEDENTARY(1.20, "Ülő (irodai munka, kevés mozgás)"),
    LIGHT(1.375, "Enyhén aktív (napi séta, álló munka)"),
    MODERATE(1.55, "Közepesen aktív (sokat mozog napközben)"),
    HIGH(1.725, "Nagyon aktív (fizikai munka)"),
    EXTREME(1.90, "Extrém aktív (nehéz fizikai munka)");
}

enum class DietStyle(val hu: String) {
    OMNIVORE("Mindenevő"),
    VEGETARIAN("Vegetáriánus"),
    VEGAN("Vegán"),
    PESCATARIAN("Pescatariánus"),
    LOW_CARB("Alacsony szénhidrát"),
    MEDITERRANEAN("Mediterrán");
}

enum class MacroPreset(val hu: String, val proteinPerKg: Double, val fatPerKg: Double) {
    BALANCED("Kiegyensúlyozott", 1.8, 0.9),
    HIGH_PROTEIN("Magas fehérje", 2.2, 0.8),
    LOW_CARB("Alacsony szénhidrát", 2.0, 1.3);
}

/**
 * Felhasználói profil. A [bodyFatPercent] opcionális: ha megvan, a pontosabb
 * Katch-McArdle képlet fut le a Mifflin-St Jeor helyett.
 */
data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val ageYears: Int = 30,
    val heightCm: Double = 178.0,
    val weightKg: Double = 85.0,
    val bodyFatPercent: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val targetWeightKg: Double? = null,
    /** Kívánt fogyás üteme kg/hét. 0.25–1.0 közé ajánlott. */
    val targetRateKgPerWeek: Double = 0.5,
    val dietStyle: DietStyle = DietStyle.OMNIVORE,
    val macroPreset: MacroPreset = MacroPreset.HIGH_PROTEIN,
    val mealsPerDay: Int = 4,
    /** Szabad szöveges testreszabás: allergiák, utált ételek, konyha, időkeret, büdzsé. */
    val preferences: String = "",
    /** Étkezési idősávok "HH:mm" formában, hossza legfeljebb [mealsPerDay]. */
    val mealTimes: List<String> = listOf("07:30", "12:30", "16:00", "19:30"),
) {
    val bmi: Double get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    /** Zsírmentes testtömeg, ha ismert a testzsírszázalék. */
    val leanBodyMassKg: Double?
        get() = bodyFatPercent?.let { weightKg * (1.0 - it / 100.0) }
}
