package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A lightweight record of coffee consumed without running a guided brew. */
@Entity(
    tableName = "coffee_usage_entries",
    foreignKeys = [
        ForeignKey(
            entity = CoffeeBagEntity::class,
            parentColumns = ["id"],
            childColumns = ["coffeeBagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("coffeeBagId")],
)
data class CoffeeUsageEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val coffeeBagId: Long,
    val amountG: Float,
    val createdAt: Long = System.currentTimeMillis(),
)
