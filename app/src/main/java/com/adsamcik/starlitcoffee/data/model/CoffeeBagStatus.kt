package com.adsamcik.starlitcoffee.data.model

enum class CoffeeBagStatus(val displayName: String) {
    SEALED("Sealed"),
    OPEN("Open"),
    FROZEN("Frozen"),
    FINISHED("Finished"),
}
