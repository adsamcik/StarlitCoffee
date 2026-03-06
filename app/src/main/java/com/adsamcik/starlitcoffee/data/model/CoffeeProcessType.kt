package com.adsamcik.starlitcoffee.data.model

/**
 * Coffee processing method with known presets and an [Other] escape hatch.
 */
sealed interface CoffeeProcessType {
    val displayName: String

    enum class Known(
        override val displayName: String,
        val searchAliases: List<String> = emptyList(),
    ) : CoffeeProcessType {
        WASHED("Washed", listOf("praný", "praná", "lavado", "lavé", "gewaschen", "lavato", "vasket")),
        NATURAL("Natural", listOf("sun-dried", "sun dried", "sundried", "přírodní", "naturell", "naturale", "naturlig", "soltørret")),
        HONEY("Honey", listOf("honey process", "miel", "medový", "medová", "honningproces")),
        ANAEROBIC("Anaerobic", listOf("anaerobic fermentation", "anaerobní")),
        CARBONIC_MACERATION("Carbonic Maceration"),
        SEMI_WASHED("Semi-Washed", listOf("semi washed", "polopraný")),
        WET_HULLED("Wet-Hulled", listOf("wet hulled", "giling basah", "mokré loupání")),
        PULPED_NATURAL("Pulped Natural"),
        DOUBLE_FERMENTED("Double Fermented"),
        THERMAL_SHOCK("Thermal Shock"),
    }

    data class Other(val value: String) : CoffeeProcessType {
        override val displayName: String get() = value
    }

    companion object {
        val known: List<CoffeeProcessType> get() = Known.entries.toList()

        val allSearchTerms: List<String>
            get() = Known.entries
                .flatMap { listOf(it.displayName) + it.searchAliases }
                .sortedByDescending { it.length }

        fun fromString(value: String): CoffeeProcessType {
            val trimmed = value.trim()
            return Known.entries.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) ||
                    entry.searchAliases.any { it.equals(trimmed, ignoreCase = true) }
            } ?: Other(trimmed)
        }
    }
}
