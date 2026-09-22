package hu.mealpilot.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Update
    suspend fun update(plan: PlanEntity)

    @Query("SELECT * FROM plans ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE isActive = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeActive(): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE isActive = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun activePlan(): PlanEntity?

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun count(): Int

    /** Pillanatkép minden tervről. Az adatkivitelhez kell, ahol nem folyam kell, hanem lista. */
    @Query("SELECT * FROM plans ORDER BY createdAtMillis")
    suspend fun all(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: Long): PlanEntity?

    @Query("UPDATE plans SET isActive = 0 WHERE id != :keepId")
    suspend fun deactivateOthers(keepId: Long)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: Long)

    /** A régi tervek törlése. A naplóbejegyzések megmaradnak: saját névvel és tápértékkel. */
    @Query("DELETE FROM plans WHERE id != :keepId")
    suspend fun deleteOthers(keepId: Long)
}

@Dao
interface MealDao {

    @Insert
    suspend fun insert(meal: MealEntity): Long

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Update
    suspend fun update(meal: MealEntity)

    @Query("DELETE FROM meals WHERE planId = :planId AND dayIndex = :dayIndex")
    suspend fun deleteDay(planId: Long, dayIndex: Int)

    /**
     * Egy fogás hozzávalóinak törlése, a fogás SORÁNAK megtartásával.
     *
     * A fogáscseréhez kell: ha a sort is törölnénk, a rá mutató naplóbejegyzés
     * elárvulna, és a kaszkád a cserével együtt elvinné azt is, amit a felhasználó
     * valójában megevett.
     */
    @Query("DELETE FROM ingredients WHERE mealId = :mealId")
    suspend fun deleteIngredients(mealId: Long)

    /**
     * Csak az aktív terv étkezései. A tervszűrés nélkül egy korábbi, már deaktivált terv
     * átfedő napjai is bejönnének, és minden étkezés kétszer jelenne meg.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM meals
        WHERE epochDay = :epochDay
          AND planId IN (SELECT id FROM plans WHERE isActive = 1)
        ORDER BY scheduledAtMillis
        """
    )
    fun observeDay(epochDay: Long): Flow<List<MealWithIngredients>>

    @Transaction
    @Query("SELECT * FROM meals WHERE planId = :planId ORDER BY epochDay, scheduledAtMillis")
    fun observePlan(planId: Long): Flow<List<MealWithIngredients>>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    fun observeMeal(id: Long): Flow<MealWithIngredients?>

    @Transaction
    @Query("SELECT * FROM meals WHERE planId = :planId AND epochDay BETWEEN :from AND :to ORDER BY epochDay, scheduledAtMillis")
    suspend fun mealsInRange(planId: Long, from: Long, to: Long): List<MealWithIngredients>

    @Query(
        """
        SELECT * FROM meals
        WHERE scheduledAtMillis BETWEEN :from AND :to
          AND planId IN (SELECT id FROM plans WHERE isActive = 1)
        ORDER BY scheduledAtMillis
        """
    )
    suspend fun scheduledBetween(from: Long, to: Long): List<MealEntity>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun byId(id: Long): MealEntity?

    @Query("SELECT * FROM meals WHERE planId = :planId")
    suspend fun allForPlan(planId: Long): List<MealEntity>

    @Query("SELECT name FROM meals WHERE planId = :planId")
    suspend fun namesInPlan(planId: Long): List<String>
}

@Dao
interface MealLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MealLogEntity): Long

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meal_logs WHERE mealId = :mealId")
    suspend fun deleteForMeal(mealId: Long)

    @Query("SELECT * FROM meal_logs WHERE epochDay = :epochDay ORDER BY loggedAtMillis")
    fun observeDay(epochDay: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, loggedAtMillis")
    fun observeRange(from: Long, to: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs ORDER BY epochDay")
    suspend fun all(): List<MealLogEntity>

    @Query("SELECT COUNT(*) FROM meal_logs WHERE mealId = :mealId AND status = 'EATEN'")
    suspend fun eatenCountFor(mealId: Long): Int

    /**
     * A megevett étkezés naplóbejegyzésének leválasztása a törlésre ítélt fogásról.
     *
     * A naplóbejegyzés nem a TERVRŐL szól, hanem arról, amit a felhasználó tényleg
     * megevett — azt egy újratervezés nem írhatja felül. A `mealId` viszont egy
     * mindjárt nem létező sorra mutatna, és az ilyen bejegyzés SEHOL nem látszik: a
     * napi lista csak a `mealId = NULL` sorokat mutatja terven kívüli tételként. A
     * kalóriákat közben tovább számolja — tehát a nap összesítője magasabb, mint amit
     * a lista indokol, és a felhasználó a fogást újra bejelölheti megevettként.
     *
     * A leválasztás után ugyanaz a bejegyzés terven kívüli tételként jelenik meg,
     * a saját nevével és tápértékével, és törölhető is.
     */
    @Query(
        """
        UPDATE meal_logs SET mealId = NULL, planId = NULL
        WHERE status IN ('EATEN', 'REPLACED')
          AND mealId IN (SELECT id FROM meals WHERE planId = :planId AND dayIndex = :dayIndex)
        """
    )
    suspend fun detachDay(planId: Long, dayIndex: Int)

    /** Ugyanaz egy egész tervre — a `keepPlanId` terv bejegyzései érintetlenek. */
    @Query(
        """
        UPDATE meal_logs SET mealId = NULL, planId = NULL
        WHERE status IN ('EATEN', 'REPLACED')
          AND planId IS NOT NULL AND planId != :keepPlanId
        """
    )
    suspend fun detachOtherPlans(keepPlanId: Long)

    /**
     * A ki nem hagyott — vagyis meg nem evett — bejegyzések törlése a törölt fogásokról.
     *
     * Egy kihagyás önmagában nem hordoz információt: nulla tápérték, és a fogás, amire
     * vonatkozott, már nem létezik. Leválasztva egy nulla kalóriás „terven kívüli"
     * sorként jelenne meg a listában, amit a felhasználó nem tud hova tenni.
     */
    @Query(
        """
        DELETE FROM meal_logs
        WHERE status NOT IN ('EATEN', 'REPLACED')
          AND mealId IN (SELECT id FROM meals WHERE planId = :planId AND dayIndex = :dayIndex)
        """
    )
    suspend fun deleteUneatenForDay(planId: Long, dayIndex: Int)

    @Query(
        """
        DELETE FROM meal_logs
        WHERE status NOT IN ('EATEN', 'REPLACED')
          AND planId IS NOT NULL AND planId != :keepPlanId
        """
    )
    suspend fun deleteUneatenForOtherPlans(keepPlanId: Long)

    /** Egyetlen terv törlésekor — a többi terv bejegyzései érintetlenek. */
    @Query(
        """
        UPDATE meal_logs SET mealId = NULL, planId = NULL
        WHERE status IN ('EATEN', 'REPLACED') AND planId = :planId
        """
    )
    suspend fun detachPlan(planId: Long)

    @Query("DELETE FROM meal_logs WHERE status NOT IN ('EATEN', 'REPLACED') AND planId = :planId")
    suspend fun deleteUneatenForPlan(planId: Long)
}

@Dao
interface WeightLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WeightLogEntity)

    @Query("SELECT * FROM weight_logs ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): WeightLogEntity?

    @Query("SELECT * FROM weight_logs ORDER BY epochDay")
    suspend fun all(): List<WeightLogEntity>

    @Query("DELETE FROM weight_logs WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}

@Dao
interface ShoppingDao {

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to")
    suspend fun clearRange(planId: Long, from: Long, to: Long)

    @Query("SELECT * FROM shopping_items WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to ORDER BY aisle, name")
    fun observeRange(planId: Long, from: Long, to: Long): Flow<List<ShoppingItemEntity>>

    @Query(
        """
        SELECT name FROM shopping_items
        WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to AND checked = 1
        """
    )
    suspend fun checkedNames(planId: Long, from: Long, to: Long): List<String>

    /**
     * Egy tartomány tételei EGYSZERI olvasással.
     *
     * A képernyő az [observeRange] folyamát figyeli; a teszteknek viszont pont az
     * kell, hogy egy művelet UTÁN mi van a táblában — egy végtelen folyam első
     * kibocsátására várni erre törékeny lenne.
     */
    @Query(
        """
        SELECT * FROM shopping_items
        WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to
        ORDER BY name
        """
    )
    suspend fun itemsInRange(planId: Long, from: Long, to: Long): List<ShoppingItemEntity>

    @Query(
        """
        SELECT COUNT(*) FROM shopping_items
        WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to
        """
    )
    suspend fun countInRange(planId: Long, from: Long, to: Long): Int

    /**
     * Milyen tartományokra van MENTETT listája ennek a tervnek.
     *
     * A „hét" és az „egész terv" nézet külön sorokat tárol. Ha a terv megváltozik,
     * mindegyiket újra kell építeni — egyet frissíteni annyit jelent, hogy a másik
     * nézet csendben a régi hozzávalókat mutatja.
     */
    @Query("SELECT DISTINCT fromEpochDay, toEpochDay FROM shopping_items WHERE planId = :planId")
    suspend fun rangesFor(planId: Long): List<ShoppingListRange>

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE shopping_items SET checked = 0 WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to")
    suspend fun uncheckAll(planId: Long, from: Long, to: Long)

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT planId, fromEpochDay, toEpochDay
            FROM shopping_items
            GROUP BY planId, fromEpochDay, toEpochDay
            HAVING SUM(CASE WHEN checked = 0 THEN 1 ELSE 0 END) = 0
        )
        """
    )
    suspend fun completedListCount(): Int
}

@Dao
interface AchievementDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AchievementEntity)

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT key FROM achievements")
    suspend fun unlockedKeys(): List<String>

    /**
     * Amiről még nem szóltunk a felhasználónak.
     *
     * A `notified` oszlop régóta megvan, de SENKI nem olvasta: a feloldott
     * achievementről csak akkor kapott hírt a felhasználó, ha épp az értesítésből
     * naplózott. Az appon belüli naplózás és a bevásárlólista kipipálása némán oldott
     * fel — és mivel utána már a „feloldottak" között volt, soha többé nem került elő.
     */
    @Query("SELECT * FROM achievements WHERE notified = 0 ORDER BY unlockedAtMillis")
    fun observeUnannounced(): Flow<List<AchievementEntity>>

    /** Pillanatkép az adatkivitelhez. */
    @Query("SELECT * FROM achievements ORDER BY unlockedAtMillis")
    suspend fun all(): List<AchievementEntity>

    @Query("UPDATE achievements SET notified = 1 WHERE key = :key")
    suspend fun markNotified(key: String)
}

@Dao
interface ChatDao {

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY sentAtMillis, id")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY sentAtMillis, id")
    suspend fun all(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET pendingAction = 0 WHERE id = :id")
    suspend fun clearPending(id: Long)

    @Query("UPDATE chat_messages SET pendingAction = 0")
    suspend fun clearAllPending()

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface FavoriteDao {

    @Transaction
    @Query("SELECT * FROM favorite_meals ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<FavoriteWithIngredients>>

    /**
     * Csak a kulcsok. A szív ikonnak ennyi kell, és ez a lekérdezés akkor sem terheli
     * a felületet, ha a kedvencek között hosszú receptek vannak.
     */
    @Query("SELECT nameKey FROM favorite_meals")
    fun observeKeys(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM favorite_meals WHERE id = :id")
    suspend fun byId(id: Long): FavoriteWithIngredients?

    /** Pillanatkép az adatkivitelhez. */
    @Transaction
    @Query("SELECT * FROM favorite_meals ORDER BY addedAtMillis")
    suspend fun all(): List<FavoriteWithIngredients>

    @Query("SELECT id FROM favorite_meals WHERE nameKey = :nameKey")
    suspend fun idOf(nameKey: String): Long?

    /**
     * A tervezési prompthoz: csak az ILYEN nyelvű kedvencek nevei.
     *
     * Az üres nyelv is átmegy — a migráció előtt megjelölt kedvencekről nem tudjuk,
     * milyen nyelvűek, és egy néma eltűnés rosszabb lenne, mint a korábbi viselkedés.
     */
    @Query(
        "SELECT name FROM favorite_meals WHERE language = :language OR language = '' " +
            "ORDER BY addedAtMillis DESC LIMIT :limit"
    )
    suspend fun recentNames(language: String, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM favorite_meals")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteMealEntity): Long

    @Insert
    suspend fun insertIngredients(items: List<FavoriteIngredientEntity>)

    @Query("DELETE FROM favorite_meals WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * A hozzávalók darabszáma. Az árva sorokat méri: a kedvenc törlésekor a
     * hozzávalóknak a kaszkádon keresztül el kell tűnniük, különben gyűlnek, és a
     * következő megjelölés duplán hozná őket.
     */
    @Query("SELECT COUNT(*) FROM favorite_ingredients")
    suspend fun ingredientCount(): Int

    @Query("DELETE FROM favorite_meals")
    suspend fun clear()
}
