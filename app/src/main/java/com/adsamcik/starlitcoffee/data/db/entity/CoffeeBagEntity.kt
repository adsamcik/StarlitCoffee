package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coffee_bags",
    indices = [
        Index("barcode"),
        Index("originId"),
        Index("regionId"),
        Index("processTypeId"),
    ]
)
data class CoffeeBagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val roaster: String? = null,
    val origin: String? = null,
    val originId: String? = null,
    val region: String? = null,
    val regionId: String? = null,
    val farm: String? = null,
    val variety: String? = null,
    val varietyIds: String? = null,
    val altitude: String? = null,
    val roastLevel: String? = null,
    val roastLevelIds: String? = null,
    val processType: String? = null,
    val processTypeId: String? = null,
    val tastingNotes: String? = null,
    val tasteNoteIds: String? = null,
    val roastDate: Long? = null,
    val openedDate: Long? = null,
    val barcode: String? = null,
    val weightG: Float? = null,
    val initialWeightG: Float? = null,
    val priceAmount: Float? = null,
    val priceCurrency: String? = "USD",
    val notes: String? = null,
    val photoUri: String? = null,
    val photoUris: String? = null,
    val traceabilityUrl: String? = null,
    val grindSetting: String? = null,
    val expiryDate: Long? = null,
    val isDecaf: Boolean = false,
    val decafProcess: String? = null,
    val status: String = "SEALED",
    val createdAt: Long = System.currentTimeMillis(),
)
