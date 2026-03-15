package com.adsamcik.starlitcoffee.util

object WeightParser {
    private val weightPattern = Regex(
        """^\s*(\d+(?:[.,]\d+)?)\s*(kg|kilograms?|g|grams?|lb|lbs|pounds?|oz|ounces?)?\s*$""",
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
}
