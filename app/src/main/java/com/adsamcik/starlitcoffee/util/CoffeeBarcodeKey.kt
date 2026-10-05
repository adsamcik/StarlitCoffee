package com.adsamcik.starlitcoffee.util

/** Numeric UPC/EAN formatting equivalence; custom identifiers retain their complete value. */
fun coffeeBarcodeKey(barcode: String?): String? {
    val raw = barcode?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (!raw.all { it.isDigit() || it.isWhitespace() || it == '-' }) return raw
    val digits = raw.filter(Char::isDigit).takeIf(String::isNotBlank) ?: return null
    return if (digits.length in setOf(8, 12, 13, 14)) digits.padStart(14, '0') else digits
}
