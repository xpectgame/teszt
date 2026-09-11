package hu.mealpilot.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlanEntity::class,
        MealEntity::class,
        IngredientEntity::class,
        MealLogEntity::class,
        ActivityLogEntity::class,
        WeightLogEntity::class,
        ShoppingItemEntity::class,
        AchievementEntity::class,
        ChatMessageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao
    abstract fun mealDao(): MealDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun achievementDao(): AchievementDao
    abstract fun chatDao(): ChatDao

    companion object {

        /**
         * A beszélgetés táblája a 2. verzióban került be. Kézzel írt migráció kell hozzá,
         * mert a telefonon már valódi étkezés-, súly- és mozgásnapló van — azt egy
         * destruktív újraépítés törölné.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `role` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `sentAtMillis` INTEGER NOT NULL,
                        `actionLabel` TEXT NOT NULL,
                        `actionJson` TEXT NOT NULL,
                        `pendingAction` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mealpilot.db",
            )
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { instance = it }
        }
    }
}
