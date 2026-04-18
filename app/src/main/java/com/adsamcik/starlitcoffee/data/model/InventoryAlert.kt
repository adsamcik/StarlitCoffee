package com.adsamcik.starlitcoffee.data.model

data class InventoryAlert(
    val type: InventoryAlertType,
    val bagName: String,
    val message: String,
    /** Null for alerts that don't reference a specific bag (e.g. DECAF_COVERAGE). */
    val bagId: Long?,
)

enum class InventoryAlertType {
    FRESHNESS,
    EXPIRY,
    STALENESS,
    AGING_SEALED,
    FOCUS,
    /** User has been brewing decaf recently but has no available decaf bags on hand. */
    DECAF_COVERAGE,
}
