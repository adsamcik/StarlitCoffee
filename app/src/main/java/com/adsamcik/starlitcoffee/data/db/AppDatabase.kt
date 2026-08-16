package com.adsamcik.starlitcoffee.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adsamcik.starlitcoffee.data.db.dao.ActiveBrewSessionDao
import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeUsageDao
import com.adsamcik.starlitcoffee.data.db.dao.CustomBrewerProfileDao
import com.adsamcik.starlitcoffee.data.db.dao.FlavorTagDao
import com.adsamcik.starlitcoffee.data.db.dao.GrinderDao
import com.adsamcik.starlitcoffee.data.db.dao.RatioPresetDao
import com.adsamcik.starlitcoffee.data.db.dao.RecipeDao
import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.dao.UserBarcodeStemDao
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeUsageEntryEntity
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.db.entity.CustomBrewerProfileEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.GrinderEntity
import com.adsamcik.starlitcoffee.data.db.entity.RatioPresetEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.db.entity.UserBarcodeStemEntity

@Database(
    entities = [
        ActiveBrewSessionEntity::class,
        SavedRecipeEntity::class,
        CoffeeBagEntity::class,
        CoffeeUsageEntryEntity::class,
        BrewLogEntity::class,
        CustomBrewerProfileEntity::class,
        GrinderEntity::class,
        RatioPresetEntity::class,
        FlavorTagEntity::class,
        UserBarcodeStemEntity::class,
        CupPresetEntity::class,
    ],
    version = 19,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun coffeeBagDao(): CoffeeBagDao
    abstract fun coffeeUsageDao(): CoffeeUsageDao
    abstract fun brewLogDao(): BrewLogDao
    abstract fun activeBrewSessionDao(): ActiveBrewSessionDao
    abstract fun customBrewerProfileDao(): CustomBrewerProfileDao
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

        /**
         * Rating scale rework: the app moved from a continuous 0-5 star rating
         * to a discrete 4-tier scale (1=Bad, 2=Meh, 3=Good, 4=Awesome). This
         * is a data-only migration (the `rating REAL` column is unchanged) that
         * normalizes any existing continuous values into the new tiers so old
         * and new ratings share one interpretation. Thresholds mirror the app's
         * previous emoji buckets (>=4.5 was "amazing", >=3.0 "good").
         */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE brew_logs SET rating = CASE
                        WHEN rating >= 4.5 THEN 4.0
                        WHEN rating >= 3.0 THEN 3.0
                        WHEN rating >= 2.0 THEN 2.0
                        WHEN rating > 0 THEN 1.0
                        ELSE rating
                    END
                    WHERE rating IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN scanSessionId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_coffee_bags_scanSessionId ON coffee_bags(scanSessionId)",
                )
            }
        }

        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            // One ordered Room transaction owns this schema step; extracting individual SQL
            // statements would obscure the atomic migration and its review order.
            @Suppress("LongMethod")
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_recipes ADD COLUMN methodFamilyId TEXT")
                db.execSQL("ALTER TABLE saved_recipes ADD COLUMN brewerProfileId TEXT")
                db.execSQL("ALTER TABLE saved_recipes ADD COLUMN snapshotVersion INTEGER")
                db.execSQL("ALTER TABLE saved_recipes ADD COLUMN recipeSnapshotJson TEXT")
                db.execSQL("ALTER TABLE brew_logs ADD COLUMN methodFamilyId TEXT")
                db.execSQL("ALTER TABLE brew_logs ADD COLUMN brewerProfileId TEXT")
                db.execSQL("ALTER TABLE brew_logs ADD COLUMN snapshotVersion INTEGER")
                db.execSQL("ALTER TABLE brew_logs ADD COLUMN brewSnapshotJson TEXT")
                db.execSQL("ALTER TABLE brew_logs ADD COLUMN sourceSessionId TEXT")
                db.execSQL("ALTER TABLE ratio_presets ADD COLUMN methodFamilyId TEXT")
                db.execSQL("ALTER TABLE ratio_presets ADD COLUMN brewerProfileId TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS active_brew_sessions (
                        sessionId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        recipeSnapshotVersion INTEGER NOT NULL,
                        recipeSnapshotJson TEXT NOT NULL,
                        compiledPlanSchemaVersion INTEGER NOT NULL,
                        compiledPlanJson TEXT NOT NULL,
                        runtimeSchemaVersion INTEGER NOT NULL,
                        runtimeJson TEXT NOT NULL,
                        executionContextSchemaVersion INTEGER,
                        executionContextJson TEXT,
                        currentStageId TEXT,
                        currentStageIndex INTEGER,
                        startedAtWallClockMillis INTEGER,
                        pausedAtWallClockMillis INTEGER,
                        deadlineAtWallClockMillis INTEGER,
                        scheduledEventToken TEXT,
                        notificationStateJson TEXT,
                        lastProcessedEventId TEXT,
                        completedLogId INTEGER,
                        revision INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(sessionId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS custom_brewer_profiles (
                        id TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        methodFamilyId TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        profileJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_brew_logs_sourceSessionId " +
                        "ON brew_logs(sourceSessionId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_active_brew_sessions_status " +
                        "ON active_brew_sessions(status)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_active_brew_sessions_updatedAt " +
                        "ON active_brew_sessions(updatedAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_custom_brewer_profiles_methodFamilyId " +
                        "ON custom_brewer_profiles(methodFamilyId)",
                )

                db.execSQL(
                    """
                    UPDATE saved_recipes
                    SET methodFamilyId = CASE method
                        WHEN 'PULSAR' THEN 'valve_controlled_no_bypass'
                        WHEN 'V60' THEN 'manual_gravity'
                        WHEN 'FRENCH_PRESS' THEN 'full_immersion_press'
                        WHEN 'AEROPRESS' THEN 'chamber_plunger'
                        WHEN 'ESPRESSO' THEN 'espresso'
                        WHEN 'MOKA_POT' THEN 'steam_pressure_multichamber'
                        WHEN 'COLD_BREW' THEN 'cold_immersion'
                        ELSE NULL
                    END,
                    brewerProfileId = CASE method
                        WHEN 'PULSAR' THEN 'pulsar_standard'
                        WHEN 'V60' THEN 'v60_unspecified'
                        WHEN 'FRENCH_PRESS' THEN 'french_press_generic'
                        WHEN 'AEROPRESS' THEN 'aeropress_standard'
                        WHEN 'ESPRESSO' THEN 'espresso_pump_generic'
                        WHEN 'MOKA_POT' THEN 'moka_generic_unspecified'
                        WHEN 'COLD_BREW' THEN 'cold_immersion_generic'
                        ELSE NULL
                    END
                    WHERE method IN (
                        'PULSAR', 'V60', 'FRENCH_PRESS', 'AEROPRESS', 'ESPRESSO', 'MOKA_POT', 'COLD_BREW'
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE brew_logs
                    SET methodFamilyId = CASE method
                        WHEN 'PULSAR' THEN 'valve_controlled_no_bypass'
                        WHEN 'V60' THEN 'manual_gravity'
                        WHEN 'FRENCH_PRESS' THEN 'full_immersion_press'
                        WHEN 'AEROPRESS' THEN 'chamber_plunger'
                        WHEN 'ESPRESSO' THEN 'espresso'
                        WHEN 'MOKA_POT' THEN 'steam_pressure_multichamber'
                        WHEN 'COLD_BREW' THEN 'cold_immersion'
                        ELSE NULL
                    END,
                    brewerProfileId = CASE method
                        WHEN 'PULSAR' THEN 'pulsar_standard'
                        WHEN 'V60' THEN 'v60_unspecified'
                        WHEN 'FRENCH_PRESS' THEN 'french_press_generic'
                        WHEN 'AEROPRESS' THEN 'aeropress_standard'
                        WHEN 'ESPRESSO' THEN 'espresso_pump_generic'
                        WHEN 'MOKA_POT' THEN 'moka_generic_unspecified'
                        WHEN 'COLD_BREW' THEN 'cold_immersion_generic'
                        ELSE NULL
                    END
                    WHERE method IN (
                        'PULSAR', 'V60', 'FRENCH_PRESS', 'AEROPRESS', 'ESPRESSO', 'MOKA_POT', 'COLD_BREW'
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE ratio_presets
                    SET methodFamilyId = CASE methodName
                        WHEN 'PULSAR' THEN 'valve_controlled_no_bypass'
                        WHEN 'V60' THEN 'manual_gravity'
                        WHEN 'FRENCH_PRESS' THEN 'full_immersion_press'
                        WHEN 'AEROPRESS' THEN 'chamber_plunger'
                        WHEN 'ESPRESSO' THEN 'espresso'
                        WHEN 'MOKA_POT' THEN 'steam_pressure_multichamber'
                        WHEN 'COLD_BREW' THEN 'cold_immersion'
                        ELSE NULL
                    END,
                    brewerProfileId = CASE methodName
                        WHEN 'PULSAR' THEN 'pulsar_standard'
                        WHEN 'V60' THEN 'v60_unspecified'
                        WHEN 'FRENCH_PRESS' THEN 'french_press_generic'
                        WHEN 'AEROPRESS' THEN 'aeropress_standard'
                        WHEN 'ESPRESSO' THEN 'espresso_pump_generic'
                        WHEN 'MOKA_POT' THEN 'moka_generic_unspecified'
                        WHEN 'COLD_BREW' THEN 'cold_immersion_generic'
                        ELSE NULL
                    END
                    WHERE methodName IN (
                        'PULSAR', 'V60', 'FRENCH_PRESS', 'AEROPRESS', 'ESPRESSO', 'MOKA_POT', 'COLD_BREW'
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coffee_usage_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        coffeeBagId INTEGER NOT NULL,
                        amountG REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(coffeeBagId) REFERENCES coffee_bags(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_coffee_usage_entries_coffeeBagId " +
                        "ON coffee_usage_entries(coffeeBagId)",
                )
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
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
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
                ).apply {
                    ALL_MIGRATIONS.forEach { migration -> addMigrations(migration) }
                }.build().also { INSTANCE = it }
            }
        }
    }
}
