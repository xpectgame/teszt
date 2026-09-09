package hu.mealpilot.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import hu.mealpilot.core.model.Nutrients

/**
 * A dátumokat epoch day (LocalDate.toEpochDay) formában, az időpontokat epoch millis-ben
 * tároljuk: így nem kell TypeConverter, és a lekérdezések tartományra egyszerűen szűrhetők.
 */

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val summary: String,
    val startEpochDay: Long,
    val dayCount: Int,
    val createdAtMillis: Long,
    /** A felhasználó szabad szöveges kérése, ami alapján a terv készült. */
    val requestText: String,
    val targetKcal: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val targetFiberG: Int,
    /** JSON tömb a coach tippekkel. */
    val coachNotesJson: String = "[]",
    val isActive: Boolean = true,
)

/**
 * A terv utolsó napja. Szándékosan kiterjesztés és nem osztálytag: a Room a mezőkből
 * generálja a táblát, és a származtatott értékeknek nincs helyük az entitásban.
 */
val PlanEntity.endEpochDay: Long get() = startEpochDay + dayCount - 1

@Entity(
    tableName = "meals",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("planId"), Index("epochDay"), Index("scheduledAtMillis")],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayIndex: Int,
    val epochDay: Long,
    val slot: String,
    /** "HH:mm" — a megjelenítéshez. */
    val timeText: String,
    /** Az emlékeztető pontos időpontja. */
    val scheduledAtMillis: Long,
    val name: String,
    val description: String = "",
    val prepMinutes: Int = 0,
    val servings: Double = 1.0,
    @Embedded(prefix = "n_") val nutrients: NutrientsColumns = NutrientsColumns(),
    /** JSON tömb az elkészítés lépéseivel. */
    val recipeStepsJson: String = "[]",
    val swapHint: String = "",
)

/** A [Nutrients] beágyazott, oszlopokra bontott változata. */
data class NutrientsColumns(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val saturatedFatG: Double = 0.0,
    val sodiumMg: Double = 0.0,
) {
    fun toNutrients() = Nutrients(kcal, proteinG, carbsG, fatG, fiberG, sugarG, saturatedFatG, sodiumMg)

    companion object {
        fun from(n: Nutrients) = NutrientsColumns(
            n.kcal, n.proteinG, n.carbsG, n.fatG, n.fiberG, n.sugarG, n.saturatedFatG, n.sodiumMg,
        )
    }
}

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("mealId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: String,
    val note: String = "",
    val pantryStaple: Boolean = false,
)

data class MealWithIngredients(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val ingredients: List<IngredientEntity>,
)

enum class LogStatus { EATEN, SKIPPED, REPLACED, EXTRA }

@Entity(
    tableName = "meal_logs",
    indices = [Index("epochDay"), Index("mealId")],
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null, ha terven kívüli étkezés. */
    val mealId: Long? = null,
    val planId: Long? = null,
    val epochDay: Long,
    val loggedAtMillis: Long,
    val status: String,
    val name: String,
    @Embedded(prefix = "n_") val nutrients: NutrientsColumns = NutrientsColumns(),
    val note: String = "",
)

@Entity(tableName = "activity_logs", indices = [Index("epochDay")])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val loggedAtMillis: Long,
    val exerciseKey: String,
    val label: String,
    val minutes: Int,
    val met: Double,
    val avgHeartRate: Int? = null,
    val kcalGross: Int,
    val kcalNet: Int,
    /** Melyik módszer adta a becslést (pulzus / személyre szabott MET / tankönyvi MET). */
    val method: String,
    val note: String = "",
)

@Entity(tableName = "weight_logs")
data class WeightLogEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val note: String = "",
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("planId")],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    /** Melyik napokra készült a lista (inkluzív tartomány). */
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: String,
    val notes: String = "",
    val usedInMeals: Int = 1,
    val checked: Boolean = false,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val key: String,
    val unlockedAtMillis: Long,
    val notified: Boolean = false,
)
