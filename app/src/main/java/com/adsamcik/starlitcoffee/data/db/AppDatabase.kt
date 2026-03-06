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
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.db.entity.GrinderEntity
import com.adsamcik.starlitcoffee.data.db.entity.RatioPresetEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity

@Database(
    entities = [
        SavedRecipeEntity::class,
        CoffeeBagEntity::class,
        BrewLogEntity::class,
        GrinderEntity::class,
        RatioPresetEntity::class,
        FlavorTagEntity::class,
    ],
    version = 10,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN initialWeightG REAL")
                db.execSQL("UPDATE coffee_bags SET initialWeightG = weightG WHERE weightG IS NOT NULL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN grindSetting TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN expiryDate INTEGER")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "starlit_coffee.db",
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
