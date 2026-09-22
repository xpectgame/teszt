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
    /**
     * A terv NYELVE — az [hu.mealpilot.core.i18n.AppLanguage] neve.
     *
     * Az app kimondja a nyelvválasztónál: „a már elkészült terv nem fordítódik le, az
     * a nyelvén marad, amin készült". Ehhez tudni kell, melyik az. E nélkül egy
     * nyelvváltás után a fogáscsere és a chates átírás a MOSTANI nyelven írt bele a
     * régi tervbe, és a bevásárlólista ugyanazt a hozzávalót két sorban hozta
     * („Paradicsom 300 g" és „Tomato 150 g"), mert az összevonás névre megy.
     *
     * Üres a migráció előtt készült terveknél: ott nincs mit tudni, és marad a
     * korábbi viselkedés (a mostani nyelv).
     */
    val language: String = "",
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

/**
 * Egy TÁROLT bevásárlólista-tartomány. Nem tábla: a `shopping_items` sorainak
 * csoportosításából jön.
 *
 * Azért kell, mert egy tervhez több lista is tartozhat — a „hét" és az „egész terv"
 * nézet külön sorokat tárol —, és ha a terv változik, MINDET újra kell építeni.
 */
data class ShoppingListRange(
    val fromEpochDay: Long,
    val toEpochDay: Long,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val key: String,
    val unlockedAtMillis: Long,
    val notified: Boolean = false,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "USER" vagy "ASSISTANT". */
    val role: String,
    val body: String,
    val sentAtMillis: Long,
    /** Egy mondat arról, mit tenne az app — ez kerül a megerősítő gombra. */
    val actionLabel: String = "",
    /** A felismert művelet JSON-je, hogy megerősítés után végrehajtható legyen. */
    val actionJson: String = "",
    /** Igaz, amíg a művelet megerősítésre vár; végrehajtás vagy elvetés után false. */
    val pendingAction: Boolean = false,
)

/**
 * Egy KEDVENCNEK jelölt fogás — teljes másolattal, nem a `meals` sorára mutató hivatkozással.
 *
 * Miért másolat: a tervek törölhetők, és a `meals` sorai a tervvel EGYÜTT törlődnek
 * (`ForeignKey.CASCADE`). Egy kedvenc, ami a terv törlésekor eltűnik, nem kedvenc. A
 * felhasználó azért jelöl meg egy fogást, hogy később is megtalálja — akkor is, ha az a
 * terv, amiben először szerepelt, már rég nincs meg.
 *
 * A [nameKey] a kisbetűs, levágott név: ezen van az egyediség. E nélkül ugyanaz a fogás
 * több napról, több tervből is bekerülne, és a kedvencek listája hamar önmaga
 * ismétlésévé válna.
 */
@Entity(
    tableName = "favorite_meals",
    indices = [Index(value = ["nameKey"], unique = true), Index("addedAtMillis")],
)
data class FavoriteMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A név kisbetűsen, levágva — az egyediség ezen áll. */
    val nameKey: String,
    val description: String = "",
    /** Melyik étkezésre szólt, amikor megjelölték. Csak besorolás, nem korlát. */
    val slot: String,
    val prepMinutes: Int = 0,
    val servings: Double = 1.0,
    @Embedded(prefix = "n_") val nutrients: NutrientsColumns = NutrientsColumns(),
    /** JSON tömb az elkészítés lépéseivel. */
    val recipeStepsJson: String = "[]",
    /**
     * Milyen NYELVŰ tervből került ide — az [hu.mealpilot.core.i18n.AppLanguage] neve.
     *
     * A kedvenc neve bemegy a tervezési promptba („ezek közül tegyél be néhányat").
     * Nyelv nélkül a magyar kedvencek nevei egy angol terv kérésébe is bekerültek,
     * magyarul — az angol tervben pedig nincs semmi, ami a magyar maradványt elkapná
     * (a [hu.mealpilot.core.ai.LanguageChecker] szándékosan csak fordítva néz).
     *
     * Üres a migráció előtt megjelölt kedvenceknél: azoknál nincs mit tudni, és marad
     * a korábbi viselkedés.
     */
    val language: String = "",
    val addedAtMillis: Long,
)

@Entity(
    tableName = "favorite_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = FavoriteMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["favoriteId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("favoriteId")],
)
data class FavoriteIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val favoriteId: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: String,
    val note: String = "",
    val pantryStaple: Boolean = false,
)

data class FavoriteWithIngredients(
    @Embedded val favorite: FavoriteMealEntity,
    @Relation(parentColumn = "id", entityColumn = "favoriteId")
    val ingredients: List<FavoriteIngredientEntity>,
)
