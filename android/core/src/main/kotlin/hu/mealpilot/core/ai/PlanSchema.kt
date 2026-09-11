package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.Localized
import hu.mealpilot.core.model.Nutrients
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Az étkezés napon belüli helye — ez határozza meg az emlékeztető sorrendjét is. */
enum class MealSlot(
    override val hu: String,
    override val en: String,
    val defaultTime: String,
) : Localized {
    BREAKFAST("Reggeli", "Breakfast", "07:30"),
    MORNING_SNACK("Tízórai", "Morning snack", "10:00"),
    LUNCH("Ebéd", "Lunch", "12:30"),
    AFTERNOON_SNACK("Uzsonna", "Afternoon snack", "16:00"),
    DINNER("Vacsora", "Dinner", "19:30"),
    EVENING_SNACK("Esti falat", "Evening snack", "21:30");

    companion object {
        fun fromRaw(raw: String?): MealSlot = fromRawOrNull(raw) ?: AFTERNOON_SNACK

        /**
         * Szigorú változat: null, ha nem ismerjük fel a slotot. Ott kell, ahol a téves
         * találat kárt okozna — például időpont-átállításnál nem szabad a fel nem ismert
         * "reggeli" helyett csendben az uzsonnát átírni.
         */
        fun fromRawOrNull(raw: String?): MealSlot? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

        /** Az adott napi étkezésszámhoz illő slot-sorrend. */
        fun forMealsPerDay(count: Int): List<MealSlot> = when (count.coerceIn(2, 6)) {
            2 -> listOf(LUNCH, DINNER)
            3 -> listOf(BREAKFAST, LUNCH, DINNER)
            4 -> listOf(BREAKFAST, LUNCH, AFTERNOON_SNACK, DINNER)
            5 -> listOf(BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER)
            else -> listOf(BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER, EVENING_SNACK)
        }
    }
}

/** Bolti polc / kategória a bevásárlólista csoportosításához. */
enum class Aisle(override val hu: String, override val en: String) : Localized {
    ZOLDSEG_GYUMOLCS("Zöldség, gyümölcs", "Produce"),
    HUS_HAL("Hús, hal", "Meat and fish"),
    TEJTERMEK("Tejtermék, tojás", "Dairy and eggs"),
    PEKARU("Pékáru", "Bakery"),
    SZARAZARU("Szárazáru, konzerv", "Pantry and tinned"),
    FAGYASZTOTT("Fagyasztott", "Frozen"),
    FUSZER("Fűszer, olaj, alapok", "Spices, oils, staples"),
    ITAL("Ital", "Drinks"),
    EGYEB("Egyéb", "Other");

    companion object {
        fun fromRaw(raw: String?): Aisle =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EGYEB
    }
}

@Serializable
data class AiNutrition(
    val kcal: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("fiber_g") val fiberG: Double = 0.0,
    @SerialName("sugar_g") val sugarG: Double = 0.0,
    @SerialName("saturated_fat_g") val saturatedFatG: Double = 0.0,
    @SerialName("sodium_mg") val sodiumMg: Double = 0.0,
) {
    fun toNutrients() = Nutrients(kcal, proteinG, carbsG, fatG, fiberG, sugarG, saturatedFatG, sodiumMg)
}

@Serializable
data class AiIngredient(
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "g",
    val aisle: String = "EGYEB",
    val note: String = "",
    /** true, ha alapvetően otthon van (só, bors, olaj) — nem kell a bevásárlólistára. */
    @SerialName("pantry_staple") val pantryStaple: Boolean = false,
)

@Serializable
data class AiMeal(
    val slot: String = "LUNCH",
    /** "HH:mm" alakban. */
    val time: String = "12:30",
    val name: String = "",
    val description: String = "",
    @SerialName("prep_minutes") val prepMinutes: Int = 0,
    val servings: Double = 1.0,
    @SerialName("recipe_steps") val recipeSteps: List<String> = emptyList(),
    val ingredients: List<AiIngredient> = emptyList(),
    val nutrition: AiNutrition = AiNutrition(),
    /** Cserejavaslat, ha a felhasználó nem szeretné az adott fogást. */
    @SerialName("swap_hint") val swapHint: String = "",
)

@Serializable
data class AiDay(
    /** 0-alapú eltolás a terv kezdőnapjától. */
    @SerialName("day_index") val dayIndex: Int = 0,
    val title: String = "",
    val note: String = "",
    val meals: List<AiMeal> = emptyList(),
)

@Serializable
data class AiPlanResponse(
    @SerialName("plan_title") val planTitle: String = "",
    val summary: String = "",
    val days: List<AiDay> = emptyList(),
    @SerialName("coach_notes") val coachNotes: List<String> = emptyList(),
)

/** Egyetlen nap újragenerálásának válasza (finomhangoláshoz). */
@Serializable
data class AiDayResponse(
    val day: AiDay = AiDay(),
    val explanation: String = "",
)
