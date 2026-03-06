package com.adsamcik.starlitcoffee.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test
    fun migrate9to10_createsIndices() {
        // Create database at version 9
        helper.createDatabase("starlit-test-db", 9).apply {
            close()
        }

        // Run migration 9 -> 10 and validate schema
        val db = helper.runMigrationsAndValidate(
            "starlit-test-db",
            10,
            true,
            AppDatabase.MIGRATION_9_10,
        )

        // Verify indices exist by querying sqlite_master
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

        db.close()
    }
}
