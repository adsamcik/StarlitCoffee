package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity

/** Match a scanned product code to physical bags that can still be brewed. */
internal fun inStockBagsForBarcode(
    barcode: String,
    bags: List<CoffeeBagEntity>,
): List<CoffeeBagEntity> {
    val code = brewBarcodeKey(barcode) ?: return emptyList()
    return bags.filter { bag ->
        bag.isInStockForBarcodeBrew() &&
            brewBarcodeKey(bag.barcode) == code
    }.sortedWith(
        compareBy<CoffeeBagEntity> { if (it.status == "OPEN") 0 else 1 }
            .thenBy { it.openedDate ?: it.createdAt }
            .thenBy { it.id },
    )
}

internal fun hasInStockBarcodeBags(bags: List<CoffeeBagEntity>): Boolean = bags.any {
    it.isInStockForBarcodeBrew() && brewBarcodeKey(it.barcode) != null
}

private fun CoffeeBagEntity.isInStockForBarcodeBrew(): Boolean =
    (status == "OPEN" || status == "SEALED") && (weightG == null || weightG > 0f)

private fun brewBarcodeKey(barcode: String?): String? {
    val raw = barcode?.trim()?.takeIf { it.isNotBlank() } ?: return null
    // UPC-A, EAN and GTIN-14 can represent the same product with leading zeros.
    // Only strip formatting from numeric codes; a QR URL must never become a
    // product match merely because it happens to contain the same digits.
    val numeric = raw.all { it.isDigit() || it.isWhitespace() || it == '-' }
    if (!numeric) return raw
    val digits = raw.filter(Char::isDigit).takeIf { it.isNotBlank() } ?: return null
    return if (digits.length in setOf(8, 12, 13, 14)) digits.padStart(14, '0') else digits
}
