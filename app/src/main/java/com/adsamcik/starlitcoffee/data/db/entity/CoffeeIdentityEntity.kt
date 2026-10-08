package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "coffee_identities")
data class CoffeeIdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val roaster: String? = null,
    val isDecaf: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "coffee_barcodes",
    primaryKeys = ["coffeeId", "normalizedCode"],
    indices = [Index("normalizedCode")],
    foreignKeys = [ForeignKey(
        entity = CoffeeIdentityEntity::class,
        parentColumns = ["id"], childColumns = ["coffeeId"], onDelete = ForeignKey.CASCADE,
    )],
)
data class CoffeeBarcodeEntity(val coffeeId: Long, val normalizedCode: String, val rawCode: String)
