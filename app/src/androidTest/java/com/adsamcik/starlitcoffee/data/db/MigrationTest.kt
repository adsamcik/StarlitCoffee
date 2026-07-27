package com.adsamcik.starlitcoffee.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * Canonical safety net: create the database at the earliest exported
     * schema (v10) from the real schema JSON, then run the production
     * migration set up to the current version and let Room validate that the
     * resulting schema exactly matches the `@Entity` definitions. This is what
     * the hand-written per-migration tests below cannot do — it catches schema
     * drift (a forgotten column/index) that would crash on app upgrade.
     */
    @Test
    fun migrateAll10To18_matchesExportedSchema() {
        helper.createDatabase(MIGRATION_TEST_DB, 10).close()
        helper.runMigrationsAndValidate(
            MIGRATION_TEST_DB,
            18,
            true,
            *AppDatabase.ALL_MIGRATIONS,
        ).close()
    }

    @Test
    fun migrate16to17_addsUniqueScanSessionWithoutDroppingBags() {
        helper.createDatabase("starlit-test-db-v17", 16).apply {
            execSQL(
                """
                INSERT INTO coffee_bags (id, name, isDecaf, status, createdAt)
                VALUES (1, 'Existing bag', 0, 'SEALED', 1735689600000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "starlit-test-db-v17",
            17,
            true,
            AppDatabase.MIGRATION_16_17,
        ).use { db ->
            val cursor = db.query("SELECT name, scanSessionId FROM coffee_bags WHERE id = 1")
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing bag", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            cursor.close()
        }
    }

    @Test
    fun migrate17to18_preservesLegacyRowsAndAddsStableBrewingState() {
        val databaseName = "starlit-test-db-v18"
        helper.createDatabase(databaseName, 17).apply {
            execSQL(
                """
                INSERT INTO saved_recipes (
                    id, coffeeName, roaster, roastLevel, processType, method, ratio, doseG, waterG,
                    grinderId, grindSetting, filterType, isDecaf, notes, createdAt
                ) VALUES (
                    1, 'Yirgacheffe', 'Starlit Roasters', 'LIGHT', 'WASHED', 'V60', 16.0, 15.0, 240.0,
                    'grinder-1', '18', 'PAPER', 0, 'Legacy V60 recipe', 1735689600000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO saved_recipes (
                    id, coffeeName, roaster, roastLevel, processType, method, ratio, doseG, waterG,
                    grinderId, grindSetting, filterType, isDecaf, notes, createdAt
                ) VALUES (
                    2, 'Experimental', NULL, NULL, NULL, 'FUTURE_METHOD', 17.0, 20.0, 340.0,
                    NULL, NULL, NULL, 1, NULL, 1735689601000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_logs (
                    id, recipeId, coffeeBagId, method, doseG, waterG, ratio, grindSetting, filterType,
                    isDecaf, tasteFeedback, rating, freeformNotes, brewTimeSeconds, createdAt
                ) VALUES (
                    1, 1, NULL, 'ESPRESSO', 18.0, 36.0, 2.0, '4', NULL,
                    0, 'Sweet', 4.0, 'Legacy espresso log', 29, 1735689602000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_logs (
                    id, recipeId, coffeeBagId, method, doseG, waterG, ratio, grindSetting, filterType,
                    isDecaf, tasteFeedback, rating, freeformNotes, brewTimeSeconds, createdAt
                ) VALUES (
                    2, NULL, NULL, 'FUTURE_METHOD', 12.0, 200.0, 16.7, NULL, NULL,
                    0, NULL, NULL, NULL, NULL, 1735689603000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO ratio_presets (id, methodName, ratio, label, sortOrder)
                VALUES (1, 'FRENCH_PRESS', 15.0, 'Full body', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO ratio_presets (id, methodName, ratio, label, sortOrder)
                VALUES (2, 'FUTURE_METHOD', 18.0, 'Unknown legacy method', 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            18,
            true,
            AppDatabase.MIGRATION_17_18,
        ).use { db ->
            db.query(
                """
                SELECT method, methodFamilyId, brewerProfileId, snapshotVersion, recipeSnapshotJson
                FROM saved_recipes WHERE id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("V60", cursor.getString(0))
                assertEquals("manual_gravity", cursor.getString(1))
                assertEquals("v60_unspecified", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
            db.query(
                """
                SELECT method, methodFamilyId, brewerProfileId, snapshotVersion, recipeSnapshotJson
                FROM saved_recipes WHERE id = 2
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FUTURE_METHOD", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }

            db.query(
                """
                SELECT method, methodFamilyId, brewerProfileId, snapshotVersion, brewSnapshotJson, sourceSessionId
                FROM brew_logs WHERE id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ESPRESSO", cursor.getString(0))
                assertEquals("espresso", cursor.getString(1))
                assertEquals("espresso_pump_generic", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
            }
            db.query(
                """
                SELECT method, methodFamilyId, brewerProfileId, snapshotVersion, brewSnapshotJson, sourceSessionId
                FROM brew_logs WHERE id = 2
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FUTURE_METHOD", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
            }

            db.query(
                """
                SELECT methodName, methodFamilyId, brewerProfileId FROM ratio_presets WHERE id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FRENCH_PRESS", cursor.getString(0))
                assertEquals("full_immersion_press", cursor.getString(1))
                assertEquals("french_press_generic", cursor.getString(2))
            }
            db.query(
                """
                SELECT methodName, methodFamilyId, brewerProfileId FROM ratio_presets WHERE id = 2
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FUTURE_METHOD", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }

            val tableNames = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) {
                    tableNames += cursor.getString(0)
                }
            }
            assertTrue(tableNames.contains("active_brew_sessions"))
            assertTrue(tableNames.contains("custom_brewer_profiles"))

            db.execSQL(
                """
                INSERT INTO active_brew_sessions (
                    sessionId, status, recipeSnapshotVersion, recipeSnapshotJson,
                    compiledPlanSchemaVersion, compiledPlanJson, runtimeSchemaVersion, runtimeJson,
                    executionContextSchemaVersion, executionContextJson,
                    currentStageId, currentStageIndex, startedAtWallClockMillis, pausedAtWallClockMillis,
                    deadlineAtWallClockMillis, scheduledEventToken, notificationStateJson,
                    lastProcessedEventId, completedLogId, revision, createdAt, updatedAt
                ) VALUES (
                    'session-1', 'RUNNING', 1, '{}', 1, '{}', 1, '{}',
                    1, '{"coffeeBagId":42}', 'bloom', 0, 1735689604000, NULL, 1735689610000, 'work-1', '{}',
                    'event-1', NULL, 3, 1735689604000, 1735689605000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO active_brew_sessions (
                    sessionId, status, recipeSnapshotVersion, recipeSnapshotJson,
                    compiledPlanSchemaVersion, compiledPlanJson, runtimeSchemaVersion, runtimeJson,
                    revision, createdAt, updatedAt
                ) VALUES (
                    'session-no-context', 'PAUSED', 1, '{}', 1, '{}', 1, '{}', 0, 1735689604000, 1735689605000
                )
                """.trimIndent(),
            )
            db.query(
                """
                SELECT executionContextSchemaVersion, executionContextJson, revision
                FROM active_brew_sessions WHERE sessionId = 'session-no-context'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertEquals(0L, cursor.getLong(2))
            }

            db.execSQL(
                """
                INSERT INTO custom_brewer_profiles (
                    id, displayName, methodFamilyId, schemaVersion, profileJson, createdAt, updatedAt
                ) VALUES (
                    'custom-v60', 'My V60', 'manual_gravity', 1, '{}', 1735689604000, 1735689605000
                )
                """.trimIndent(),
            )
            db.query(
                """
                SELECT status, executionContextSchemaVersion, executionContextJson, currentStageId,
                    deadlineAtWallClockMillis, revision
                FROM active_brew_sessions WHERE sessionId = 'session-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("RUNNING", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("""{"coffeeBagId":42}""", cursor.getString(2))
                assertEquals("bloom", cursor.getString(3))
                assertEquals(1735689610000, cursor.getLong(4))
                assertEquals(3, cursor.getLong(5))
            }
            db.query(
                """
                SELECT displayName, methodFamilyId, schemaVersion
                FROM custom_brewer_profiles WHERE id = 'custom-v60'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("My V60", cursor.getString(0))
                assertEquals("manual_gravity", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
            }

            var sourceSessionIndexIsUnique = false
            db.query("PRAGMA index_list('brew_logs')").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "index_brew_logs_sourceSessionId") {
                        sourceSessionIndexIsUnique = cursor.getInt(2) == 1
                    }
                }
            }
            assertTrue("Expected a unique source-session index for idempotent log writes", sourceSessionIndexIsUnique)
        }
    }

    @Test
    fun migrate5to6_copiesWeightIntoInitialWeight() {
        withDatabase(
            name = "starlit-test-db-v6",
            version = 5,
            createSchema = { createVersion5CoffeeBagsSchema() },
        ) { db ->
            db.execSQL("INSERT INTO coffee_bags (id, name, weightG) VALUES (1, 'Has Weight', 250.0)")
            db.execSQL("INSERT INTO coffee_bags (id, name, weightG) VALUES (2, 'No Weight', NULL)")

            AppDatabase.MIGRATION_5_6.migrate(db)

            val cursor = db.query("SELECT weightG, initialWeightG FROM coffee_bags ORDER BY id")
            assertTrue(cursor.moveToNext())
            assertEquals(250.0, cursor.getDouble(0), 0.001)
            assertEquals(250.0, cursor.getDouble(1), 0.001) // weightG copied into initialWeightG
            assertTrue(cursor.moveToNext())
            assertTrue("weightG should stay NULL", cursor.isNull(0))
            assertTrue("initialWeightG should stay NULL when weightG is NULL", cursor.isNull(1))
            cursor.close()
        }
    }

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

    @Test
    fun migrate14to15_addsDecafProcessWithoutDroppingBags() {
        withDatabase(
            name = "starlit-test-db-v15",
            version = 14,
            createSchema = { createVersion14Schema() },
        ) { db ->
            db.execSQL(
                """
                INSERT INTO coffee_bags (id, name, isDecaf)
                VALUES (1, 'Night Shift', 1)
                """.trimIndent(),
            )

            AppDatabase.MIGRATION_14_15.migrate(db)

            val cursor = db.query(
                """
                SELECT name, isDecaf, decafProcess
                FROM coffee_bags
                WHERE id = 1
                """.trimIndent(),
            )
            assertTrue(cursor.moveToFirst())
            assertEquals("Night Shift", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(null, cursor.getString(2))
            cursor.close()
        }
    }

    @Test
    fun migrate15to16_normalizesLegacyRatingsToTiers() {
        withDatabase(
            name = "starlit-test-db-v16",
            version = 15,
            createSchema = { createVersion15BrewLogsRatingSchema() },
        ) { db ->
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (1, 5.0)")   // -> 4 Awesome
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (2, 4.5)")   // -> 4 Awesome
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (3, 3.5)")   // -> 3 Good
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (4, 3.0)")   // -> 3 Good
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (5, 2.0)")   // -> 2 Meh
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (6, 1.5)")   // -> 1 Bad
            db.execSQL("INSERT INTO brew_logs (id, rating) VALUES (7, NULL)")  // stays unrated

            AppDatabase.MIGRATION_15_16.migrate(db)

            val expected = mapOf(1 to 4.0, 2 to 4.0, 3 to 3.0, 4 to 3.0, 5 to 2.0, 6 to 1.0)
            val cursor = db.query("SELECT id, rating FROM brew_logs ORDER BY id")
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                if (id == 7) {
                    assertTrue("Unrated brew must stay NULL", cursor.isNull(1))
                } else {
                    assertEquals("Rating for id=$id", expected.getValue(id), cursor.getDouble(1), 0.001)
                }
            }
            cursor.close()
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

    private fun SupportSQLiteDatabase.createVersion5CoffeeBagsSchema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS coffee_bags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                weightG REAL
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.createVersion14Schema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS coffee_bags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isDecaf INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.createVersion15BrewLogsRatingSchema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS brew_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                rating REAL
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val MIGRATION_TEST_DB = "migration-helper-test.db"
    }
}
