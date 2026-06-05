package com.adsamcik.starlitcoffee.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.dao.FlavorTagDao
import com.adsamcik.starlitcoffee.data.db.dao.GrinderDao
import com.adsamcik.starlitcoffee.data.db.dao.RatioPresetDao
import com.adsamcik.starlitcoffee.data.db.dao.RecipeDao
import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.dao.UserBarcodeStemDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.GrinderEntity
import com.adsamcik.starlitcoffee.data.db.entity.RatioPresetEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.db.entity.UserBarcodeStemEntity

@Database(
    entities = [
        SavedRecipeEntity::class,
        CoffeeBagEntity::class,
        BrewLogEntity::class,
        GrinderEntity::class,
        RatioPresetEntity::class,
        FlavorTagEntity::class,
        UserBarcodeStemEntity::class,
        CupPresetEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun coffeeBagDao(): CoffeeBagDao
    abstract fun brewLogDao(): BrewLogDao
    abstract fun grinderDao(): GrinderDao
    abstract fun ratioPresetDao(): RatioPresetDao
    abstract fun flavorTagDao(): FlavorTagDao
    abstract fun userBarcodeStemDao(): UserBarcodeStemDao
    abstract fun cupPresetDao(): CupPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN initialWeightG REAL")
                db.execSQL("UPDATE coffee_bags SET initialWeightG = weightG WHERE weightG IS NOT NULL")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN grindSetting TEXT")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN expiryDate INTEGER")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN isDecaf INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brew_logs_coffeeBagId ON brew_logs(coffeeBagId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brew_logs_recipeId ON brew_logs(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coffee_bags_barcode ON coffee_bags(barcode)")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN originId TEXT")
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN regionId TEXT")
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN varietyIds TEXT")
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN roastLevelIds TEXT")
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN processTypeId TEXT")
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN tasteNoteIds TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coffee_bags_originId ON coffee_bags(originId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coffee_bags_regionId ON coffee_bags(regionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coffee_bags_processTypeId ON coffee_bags(processTypeId)")
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_recipes ADD COLUMN isDecaf INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE brew_logs ADD COLUMN isDecaf INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS user_barcode_stems (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        prefix TEXT NOT NULL,
                        roasterName TEXT NOT NULL,
                        observationCount INTEGER NOT NULL DEFAULT 1,
                        confidence TEXT NOT NULL DEFAULT 'LOW',
                        firstSeenAt INTEGER NOT NULL,
                        lastSeenAt INTEGER NOT NULL
                    )""",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_barcode_stems_prefix ON user_barcode_stems(prefix)")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS cup_presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        iconName TEXT NOT NULL,
                        doseG REAL NOT NULL,
                        waterMl REAL NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        colorHex TEXT
                    )""",
                )
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN decafProcess TEXT")
            }
        }

        // Single source of truth for the migration set, shared by the
        // production builder and MigrationTest so the two cannot drift.
        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
        )

        /**
         * Manual singleton accessor. The repo intentionally avoids a DI
         * framework today (see project conventions: "No DI framework yet;
         * factories / manual wiring are intentional"); when DI lands this
         * can be replaced with a Hilt `@Provides` binding.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "starlit_coffee.db",
                ).addMigrations(*ALL_MIGRATIONS)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
