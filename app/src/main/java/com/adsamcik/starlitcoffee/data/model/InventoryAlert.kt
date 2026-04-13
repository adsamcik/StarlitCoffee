package com.adsamcik.starlitcoffee.data.model

data class InventoryAlert(
    val type: InventoryAlertType,
    val bagName: String,
    val message: String,
    val bagId: Long,
)

enum class InventoryAlertType {
    FRESHNESS,
    EXPIRY,
    STALENESS,
    AGING_SEALED,
    FOCUS,
}
