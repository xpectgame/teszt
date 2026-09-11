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

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: Long): PlanEntity?

    @Query("UPDATE plans SET isActive = 0 WHERE id != :keepId")
    suspend fun deactivateOthers(keepId: Long)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: Long)
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

    @Transaction
    @Query("SELECT * FROM meals WHERE epochDay = :epochDay ORDER BY scheduledAtMillis")
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
}

@Dao
interface ActivityLogDao {

    @Insert
    suspend fun insert(log: ActivityLogEntity): Long

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM activity_logs WHERE epochDay = :epochDay ORDER BY loggedAtMillis DESC")
    fun observeDay(epochDay: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, loggedAtMillis DESC")
    fun observeRange(from: Long, to: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT COALESCE(SUM(kcalNet), 0) FROM activity_logs WHERE epochDay = :epochDay")
    fun observeNetKcal(epochDay: Long): Flow<Int>

    @Query("SELECT * FROM activity_logs ORDER BY epochDay")
    suspend fun all(): List<ActivityLogEntity>
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

    @Query(
        """
        SELECT COUNT(*) FROM shopping_items
        WHERE planId = :planId AND fromEpochDay = :from AND toEpochDay = :to
        """
    )
    suspend fun countInRange(planId: Long, from: Long, to: Long): Int

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
