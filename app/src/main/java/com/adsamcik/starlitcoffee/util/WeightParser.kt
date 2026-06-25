package com.adsamcik.starlitcoffee.util

object WeightParser {
    private val weightPattern = Regex(
        """^\s*(\d+(?:[.,]\d+)?)\s*(kg|kilograms?|g|grams?|lb|lbs|pounds?|oz|ounces?)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // Unanchored variant: finds a value+unit token anywhere in a noisy string.
    // Unit is REQUIRED here (no bare number) so we don't pick up an unrelated
    // integer from a garbled OCR cell.
    private val weightTokenPattern = Regex(
        """(\d+(?:[.,]\d+)?)\s*(kg|kilograms?|g|grams?|lb|lbs|pounds?|oz|ounces?)""",
        RegexOption.IGNORE_CASE,
    )

    fun parseToGrams(weightText: String?): Float? {
        val match = weightText
            ?.takeIf { it.isNotBlank() }
            ?.let { weightPattern.matchEntire(it.trim()) }
            ?: return null

        val value = match.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()

        return when {
            unit.startsWith("kg") || unit.startsWith("kilogram") -> value * 1000f
            unit.startsWith("lb") || unit.startsWith("pound") -> value * 453.59237f
            unit.startsWith("oz") || unit.startsWith("ounce") -> value * 28.349523f
            else -> value
        }
    }

    /**
     * Recover the first plausible weight token from a noisy string — e.g. an
     * OCR grid-cell merge like "250gC1000g" or "Hmotnost 250 g". Returns a
     * clean "<value><unit>" token (lowercased unit, no spaces) or null if no
     * value+unit token is present.
     *
     * Picks the FIRST match on purpose: a bag label prints the net weight
     * before any price-per-kg reference, so the leading token is the dose.
     * A value that already parses cleanly is returned trimmed, unchanged.
     */
    fun extractFirstWeightToken(weightText: String?): String? {
        val cleaned = weightText?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (parseToGrams(cleaned) != null) return cleaned
        val match = weightTokenPattern.find(cleaned) ?: return null
        val value = match.groupValues[1]
        val unit = match.groupValues[2].lowercase()
        return "$value$unit"
    }
}
