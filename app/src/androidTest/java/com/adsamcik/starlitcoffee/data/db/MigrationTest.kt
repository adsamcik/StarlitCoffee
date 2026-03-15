package com.adsamcik.starlitcoffee.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrate9to10_createsIndices() {
        withDatabase(
            name = "starlit-test-db-v10",
            version = 9,
            createSchema = { createVersion9Schema() },
        ) { db ->
            AppDatabase.MIGRATION_9_10.migrate(db)

            val cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%'",
            )
            val indexNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(0))
            }
            cursor.close()

            assertTrue(
                "Expected index_brew_logs_coffeeBagId",
                indexNames.contains("index_brew_logs_coffeeBagId"),
            )
            assertTrue(
                "Expected index_brew_logs_recipeId",
                indexNames.contains("index_brew_logs_recipeId"),
            )
            assertTrue(
                "Expected index_coffee_bags_barcode",
                indexNames.contains("index_coffee_bags_barcode"),
            )
        }
    }

    @Test
    fun migrate11to12_addsDecafColumnsToRecipesAndLogs() {
        withDatabase(
            name = "starlit-test-db-v12",
            version = 11,
            createSchema = { createVersion11Schema() },
        ) { db ->
            db.execSQL(
                """
                INSERT INTO saved_recipes (
                    id, coffeeName, roaster, roastLevel, processType, method, ratio, doseG, waterG,
                    grinderId, grindSetting, filterType, notes, createdAt
                ) VALUES (
                    1, 'Night Shift', 'Beansmith''s', 'Light', 'Washed', 'PULSAR', 17.0, 20.0, 340.0,
                    NULL, NULL, 'PAPER', NULL, 1735689600000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO brew_logs (
                    id, recipeId, coffeeBagId, method, doseG, waterG, ratio, grindSetting,
                    filterType, tasteFeedback, rating, freeformNotes, brewTimeSeconds, createdAt
                ) VALUES (
                    1, NULL, NULL, 'PULSAR', 20.0, 340.0, 17.0, NULL,
                    'PAPER', NULL, NULL, NULL, NULL, 1735689600000
                )
                """.trimIndent(),
            )

            AppDatabase.MIGRATION_11_12.migrate(db)

            val recipeCursor = db.query("SELECT isDecaf FROM saved_recipes WHERE id = 1")
            recipeCursor.moveToFirst()
            assertEquals(0, recipeCursor.getInt(0))
            recipeCursor.close()

            val brewLogCursor = db.query("SELECT isDecaf FROM brew_logs WHERE id = 1")
            brewLogCursor.moveToFirst()
            assertEquals(0, brewLogCursor.getInt(0))
            brewLogCursor.close()
        }
    }

    private fun withDatabase(
        name: String,
        version: Int,
        createSchema: SupportSQLiteDatabase.() -> Unit,
        block: (SupportSQLiteDatabase) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)

        val helper = openHelper(
            name = name,
            version = version,
            createSchema = createSchema,
        )

        val db = helper.writableDatabase
        try {
            block(db)
        } finally {
            db.close()
            helper.close()
            context.deleteDatabase(name)
        }
    }

    private fun openHelper(
        name: String,
        version: Int,
        createSchema: SupportSQLiteDatabase.() -> Unit,
    ): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.createSchema()
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
    }

    private fun SupportSQLiteDatabase.createVersion9Schema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS brew_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recipeId INTEGER,
                coffeeBagId INTEGER
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS coffee_bags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                barcode TEXT
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.createVersion11Schema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_recipes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                coffeeName TEXT,
                roaster TEXT,
                roastLevel TEXT,
                processType TEXT,
                method TEXT NOT NULL,
                ratio REAL NOT NULL,
                doseG REAL NOT NULL,
                waterG REAL NOT NULL,
                grinderId TEXT,
                grindSetting TEXT,
                filterType TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS brew_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recipeId INTEGER,
                coffeeBagId INTEGER,
                method TEXT NOT NULL,
                doseG REAL NOT NULL,
                waterG REAL NOT NULL,
                ratio REAL NOT NULL,
                grindSetting TEXT,
                filterType TEXT,
                tasteFeedback TEXT,
                rating REAL,
                freeformNotes TEXT,
                brewTimeSeconds INTEGER,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
