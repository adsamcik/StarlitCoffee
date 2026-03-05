package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coffee_bags")
data class CoffeeBagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val roaster: String? = null,
    val origin: String? = null,
    val region: String? = null,
    val farm: String? = null,
    val variety: String? = null,
    val altitude: String? = null,
    val roastLevel: String? = null,
    val processType: String? = null,
    val tastingNotes: String? = null,
    val roastDate: Long? = null,
    val openedDate: Long? = null,
    val barcode: String? = null,
    val weightG: Float? = null,
    val priceAmount: Float? = null,
    val priceCurrency: String? = "USD",
    val notes: String? = null,
    val photoUri: String? = null,
    val photoUris: String? = null,
    val traceabilityUrl: String? = null,
    val status: String = "SEALED",
    val createdAt: Long = System.currentTimeMillis(),
)
