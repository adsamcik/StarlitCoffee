package com.adsamcik.starlitcoffee.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adsamcik.starlitcoffee.util.coffeeBarcodeKey

/** Preserve every physical ID, balance, history link and unscoped legacy setting. */
internal object CoffeeMemoryMigration : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS coffee_identities (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, roaster TEXT, " +
            "isDecaf INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("INSERT INTO coffee_identities(id,name,roaster,isDecaf,createdAt) " +
            "SELECT id,name,roaster,isDecaf,createdAt FROM coffee_bags")
        db.execSQL("ALTER TABLE coffee_bags ADD COLUMN coffeeId INTEGER " +
            "REFERENCES coffee_identities(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("ALTER TABLE coffee_bags ADD COLUMN packNumber INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE coffee_bags SET coffeeId = id")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_coffee_bags_coffeeId ON coffee_bags(coffeeId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS coffee_barcodes (coffeeId INTEGER NOT NULL, " +
            "normalizedCode TEXT NOT NULL, rawCode TEXT NOT NULL, PRIMARY KEY(coffeeId,normalizedCode), " +
            "FOREIGN KEY(coffeeId) REFERENCES coffee_identities(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_coffee_barcodes_normalizedCode ON coffee_barcodes(normalizedCode)")
        migrateBarcodes(db)
        createSettings(db, "type_grind_settings", "coffeeType", "TEXT", null)
        createSettings(db, "coffee_grind_settings", "coffeeId", "INTEGER", "coffee_identities")
        createSettings(db, "pack_grind_settings", "packId", "INTEGER", "coffee_bags")
    }

    private fun migrateBarcodes(db: SupportSQLiteDatabase) {
        db.query("SELECT id, barcode FROM coffee_bags WHERE barcode IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val raw = cursor.getString(1)
                val key = coffeeBarcodeKey(raw) ?: continue
                db.execSQL("INSERT INTO coffee_barcodes(coffeeId,normalizedCode,rawCode) VALUES(?,?,?)",
                    arrayOf<Any>(cursor.getLong(0), key, raw))
            }
        }
    }

    private fun createSettings(db: SupportSQLiteDatabase, table: String, owner: String, type: String, parent: String?) {
        val foreignKey = parent?.let {
            ", FOREIGN KEY($owner) REFERENCES $it(id) ON UPDATE NO ACTION ON DELETE CASCADE"
        }.orEmpty()
        db.execSQL("CREATE TABLE IF NOT EXISTS $table ($owner $type NOT NULL, " +
            "grinderId TEXT NOT NULL, methodId TEXT NOT NULL, filterKey TEXT NOT NULL, " +
            "scaleType TEXT NOT NULL, clicksPerRotation INTEGER, totalClicks INTEGER, dialValue TEXT, " +
            "updatedAt INTEGER NOT NULL, PRIMARY KEY($owner,grinderId,methodId,filterKey)$foreignKey)")
    }
}
