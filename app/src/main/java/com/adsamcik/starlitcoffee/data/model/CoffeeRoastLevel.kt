package com.adsamcik.starlitcoffee.data.model

/**
 * Roast level with known presets and an [Other] escape hatch for custom values.
 */
sealed interface CoffeeRoastLevel {
    val displayName: String

    enum class Known(
        override val displayName: String,
        val searchAliases: List<String> = emptyList(),
    ) : CoffeeRoastLevel {
        LIGHT("Light"),
        MEDIUM_LIGHT("Medium-Light", listOf("medium light")),
        MEDIUM("Medium"),
        MEDIUM_DARK("Medium-Dark", listOf("medium dark")),
        DARK("Dark"),
        FILTER("Filter", listOf("filter roast", "city roast", "city")),
        ESPRESSO("Espresso", listOf("espresso roast", "full city", "full city roast", "vienna roast")),
        OMNIROAST("Omniroast", listOf("omni roast", "omni-roast")),
        CINNAMON("Cinnamon", listOf("cinnamon roast")),
    }

    data class Other(val value: String) : CoffeeRoastLevel {
        override val displayName: String get() = value
    }

    companion object {
        val known: List<CoffeeRoastLevel> get() = Known.entries.toList()

        /** All terms that OCR should match, longest first for greedy regex. */
        val allSearchTerms: List<String>
            get() = Known.entries
                .flatMap { listOf(it.displayName) + it.searchAliases }
                .sortedByDescending { it.length }

        fun fromString(value: String): CoffeeRoastLevel {
            val trimmed = value.trim()
            return Known.entries.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) ||
                    entry.searchAliases.any { it.equals(trimmed, ignoreCase = true) }
            } ?: Other(trimmed)
        }
    }
}
