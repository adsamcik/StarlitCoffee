package com.adsamcik.starlitcoffee.navigation

import kotlinx.serialization.Serializable

@Serializable
object CalculatorBrew

@Serializable
object BrewTimer

@Serializable
object GrindPrep

@Serializable
object BloomTimer

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

@Serializable
object BloomAnimationSettings

@Serializable
data class CupPresetEditor(val presetId: Long? = null)

@Serializable
object More

@Serializable
data class RescanBag(val bagId: Long)
