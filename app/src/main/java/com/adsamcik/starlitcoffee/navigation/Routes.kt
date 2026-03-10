package com.adsamcik.starlitcoffee.navigation

import kotlinx.serialization.Serializable

@Serializable
object MethodPicker

@Serializable
object AmountStrength

@Serializable
object Result

@Serializable
object BrewTimer

@Serializable
object TasteFeedback

@Serializable
object SavedRecipes

@Serializable
object BagInventory

@Serializable
object BrewLogList

@Serializable
data class BrewLogDetail(val logId: Long)

@Serializable
object BarcodeScanner

@Serializable
object LiveScan

@Serializable
object OnboardingMethods

@Serializable
object OnboardingPersonalize

@Serializable
object Settings
