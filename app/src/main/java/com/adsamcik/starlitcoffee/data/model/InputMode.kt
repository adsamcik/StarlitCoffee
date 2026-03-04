package com.adsamcik.starlitcoffee.data.model

enum class InputMode(
    val displayName: String,
    val shortLabel: String,
    val description: String,
) {
    COFFEE_TO_WATER("Coffee → Water", "Coffee→Water", "Enter coffee dose, calculate water"),
    WATER_TO_COFFEE("Water → Coffee", "Water→Coffee", "Enter water amount, calculate coffee"),
    CUP_SIZE_TO_BOTH("Cup Size → Both", "Cup→Both", "Enter cup size, calculate coffee and water"),
}
