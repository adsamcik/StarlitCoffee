package com.adsamcik.starlitcoffee.data.db

import androidx.room.TypeConverter
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus

class Converters {
    @TypeConverter
    fun fromBrewMethod(value: BrewMethod): String = value.name

    @TypeConverter
    fun toBrewMethod(value: String): BrewMethod = BrewMethod.valueOf(value)

    @TypeConverter
    fun fromCoffeeBagStatus(value: CoffeeBagStatus): String = value.name

    @TypeConverter
    fun toCoffeeBagStatus(value: String): CoffeeBagStatus = CoffeeBagStatus.valueOf(value)

    @TypeConverter
    fun fromCalibrationStyle(value: CalibrationStyle): String = value.name

    @TypeConverter
    fun toCalibrationStyle(value: String): CalibrationStyle = CalibrationStyle.valueOf(value)
}
