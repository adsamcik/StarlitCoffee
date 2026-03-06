package com.adsamcik.starlitcoffee.data.model

/**
 * Coffee plant variety/cultivar with known presets and an [Other] escape hatch.
 * Multi-word varieties (e.g. "Pink Bourbon") are listed before shorter forms
 * so OCR regex matching is greedy.
 */
sealed interface CoffeeVariety {
    val displayName: String

    enum class Known(
        override val displayName: String,
        val searchAliases: List<String> = emptyList(),
    ) : CoffeeVariety {
        // Multi-word varieties first (sorted long → short at regex build time)
        PINK_BOURBON("Pink Bourbon"),
        YELLOW_BOURBON("Yellow Bourbon"),
        RED_BOURBON("Red Bourbon"),
        YELLOW_CATUAI("Yellow Catuai"),
        RED_CATUAI("Red Catuai"),
        MUNDO_NOVO("Mundo Novo"),
        RUIRU_11("Ruiru 11"),
        SL28("SL28"),
        SL34("SL34"),
        VILLA_SARCHI("Villa Sarchi"),
        // Single-word varieties
        BOURBON("Bourbon"),
        TYPICA("Typica"),
        GEISHA("Geisha", listOf("Gesha")),
        CATURRA("Caturra"),
        CATUAI("Catuai"),
        PACAMARA("Pacamara"),
        MARAGOGYPE("Maragogype"),
        CASTILLO("Castillo"),
        HEIRLOOM("Heirloom"),
        JAVA("Java"),
        CATIMOR("Catimor"),
        BATIAN("Batian"),
        MARSELLESA("Marsellesa"),
        PARAINEMA("Parainema"),
        OBATA("Obata"),
        TABI("Tabi"),
        V_74110("74110"),
        V_74112("74112"),
        V_74158("74158"),
        SIDRA("Sidra"),
        EUGENIOIDES("Eugenioides"),
        LIBERICA("Liberica"),
        MARACATURRA("Maracaturra"),
    }

    data class Other(val value: String) : CoffeeVariety {
        override val displayName: String get() = value
    }

    companion object {
        val known: List<CoffeeVariety> get() = Known.entries.toList()

        val allSearchTerms: List<String>
            get() = Known.entries
                .flatMap { listOf(it.displayName) + it.searchAliases }
                .sortedByDescending { it.length }

        fun fromString(value: String): CoffeeVariety {
            val trimmed = value.trim()
            return Known.entries.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) ||
                    entry.searchAliases.any { it.equals(trimmed, ignoreCase = true) }
            } ?: Other(trimmed)
        }
    }
}
