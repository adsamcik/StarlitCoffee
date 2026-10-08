package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.coffeeBarcodeKey

/** Match a scanned product code to physical bags that can still be brewed. */
internal fun inStockBagsForBarcode(
    barcode: String,
    bags: List<CoffeeBagEntity>,
): List<CoffeeBagEntity> {
    val code = coffeeBarcodeKey(barcode) ?: return emptyList()
    return bags.filter { bag ->
        bag.isInStockForBarcodeBrew() &&
            coffeeBarcodeKey(bag.barcode) == code
    }.sortedWith(
        compareBy<CoffeeBagEntity> { if (it.status == "OPEN") 0 else 1 }
            .thenBy { it.openedDate ?: it.createdAt }
            .thenBy { it.id },
    )
}

internal fun hasInStockBarcodeBags(bags: List<CoffeeBagEntity>): Boolean = bags.any {
    it.isInStockForBarcodeBrew() && coffeeBarcodeKey(it.barcode) != null
}

private fun CoffeeBagEntity.isInStockForBarcodeBrew(): Boolean =
    (status == "OPEN" || status == "SEALED") && (weightG == null || weightG > 0f)
