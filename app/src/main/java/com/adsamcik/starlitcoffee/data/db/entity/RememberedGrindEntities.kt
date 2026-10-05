package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import com.adsamcik.starlitcoffee.data.model.GrindContext
import com.adsamcik.starlitcoffee.data.model.RememberedGrindValue

@Entity(tableName = "type_grind_settings", primaryKeys = ["coffeeType", "grinderId", "methodId", "filterKey"])
data class TypeGrindSettingEntity(
    val coffeeType: String,
    @Embedded val context: GrindContext,
    @Embedded val value: RememberedGrindValue,
)

@Entity(
    tableName = "coffee_grind_settings",
    primaryKeys = ["coffeeId", "grinderId", "methodId", "filterKey"],
    foreignKeys = [ForeignKey(
        entity = CoffeeIdentityEntity::class,
        parentColumns = ["id"], childColumns = ["coffeeId"], onDelete = ForeignKey.CASCADE,
    )],
)
data class CoffeeGrindSettingEntity(
    val coffeeId: Long,
    @Embedded val context: GrindContext,
    @Embedded val value: RememberedGrindValue,
)

@Entity(
    tableName = "pack_grind_settings",
    primaryKeys = ["packId", "grinderId", "methodId", "filterKey"],
    foreignKeys = [ForeignKey(
        entity = CoffeeBagEntity::class,
        parentColumns = ["id"], childColumns = ["packId"], onDelete = ForeignKey.CASCADE,
    )],
)
data class PackGrindSettingEntity(
    val packId: Long,
    @Embedded val context: GrindContext,
    @Embedded val value: RememberedGrindValue,
)
