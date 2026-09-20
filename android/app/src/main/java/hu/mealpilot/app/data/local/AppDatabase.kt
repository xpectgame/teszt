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
        WeightLogEntity::class,
        ShoppingItemEntity::class,
        AchievementEntity::class,
        ChatMessageEntity::class,
        FavoriteMealEntity::class,
        FavoriteIngredientEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao
    abstract fun mealDao(): MealDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun achievementDao(): AchievementDao
    abstract fun chatDao(): ChatDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {

        /**
         * A beszélgetés táblája a 2. verzióban került be. Kézzel írt migráció kell hozzá,
         * mert a telefonon már valódi étkezés- és súlynapló van — azt egy destruktív
         * újraépítés törölné.
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

        /**
         * A mozgásnapló kikerült az appból: a MealPilot az étkezésről szól, és egy
         * félig használt edzésnapló csak elvette a helyet az étrend elől.
         *
         * A táblát eldobjuk. A napi kalóriakeretet ettől nem éri veszteség: a mozgást
         * mostantól a profil egyetlen mozgásszint-kérdése fedi le, ami az edzést is
         * tartalmazza (lásd ActivityLevel).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `activity_logs`")
            }
        }

        /**
         * A kedvencek táblái. Két tábla, mert egy kedvenc a HOZZÁVALÓIVAL együtt kedvenc
         * — recept nélkül csak egy név maradna.
         *
         * A `meals` sorát nem hivatkozzuk: a kedvenc MÁSOLAT. A tervek törölhetők, és a
         * fogásaik a tervvel együtt szűnnek meg; egy kedvenc, ami a terv törlésekor
         * eltűnik, nem kedvenc.
         *
         * Az egyediség a `nameKey`-en (kisbetűs név) van, nem a megjelenített néven: a
         * felhasználó ugyanazt a fogást több napról is megjelölheti, és nem akarjuk
         * háromszor látni a listában.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_meals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `nameKey` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `slot` TEXT NOT NULL,
                        `prepMinutes` INTEGER NOT NULL,
                        `servings` REAL NOT NULL,
                        `recipeStepsJson` TEXT NOT NULL,
                        `addedAtMillis` INTEGER NOT NULL,
                        `n_kcal` REAL NOT NULL,
                        `n_proteinG` REAL NOT NULL,
                        `n_carbsG` REAL NOT NULL,
                        `n_fatG` REAL NOT NULL,
                        `n_fiberG` REAL NOT NULL,
                        `n_sugarG` REAL NOT NULL,
                        `n_saturatedFatG` REAL NOT NULL,
                        `n_sodiumMg` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_meals_nameKey` " +
                        "ON `favorite_meals` (`nameKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorite_meals_addedAtMillis` " +
                        "ON `favorite_meals` (`addedAtMillis`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_ingredients` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `favoriteId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `aisle` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `pantryStaple` INTEGER NOT NULL,
                        FOREIGN KEY(`favoriteId`) REFERENCES `favorite_meals`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorite_ingredients_favoriteId` " +
                        "ON `favorite_ingredients` (`favoriteId`)"
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { instance = it }
        }
    }
}
