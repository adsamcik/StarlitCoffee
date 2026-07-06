package com.adsamcik.starlitcoffee.data.model

/**
 * Coffee origin country with known presets and an [Other] escape hatch.
 * [searchAliases] include common OCR abbreviations (e.g. "ETH" → Ethiopia).
 */
sealed interface CoffeeOrigin {
    val displayName: String

    enum class Known(
        override val displayName: String,
        val searchAliases: List<String> = emptyList(),
    ) : CoffeeOrigin {
        ETHIOPIA("Ethiopia", listOf("ETH")),
        COLOMBIA("Colombia", listOf("COL")),
        BRAZIL("Brazil", listOf("BRA")),
        KENYA("Kenya", listOf("KEN")),
        GUATEMALA("Guatemala", listOf("GUA")),
        COSTA_RICA("Costa Rica", listOf("CRI")),
        HONDURAS("Honduras", listOf("HON")),
        PERU("Peru", listOf("PER")),
        RWANDA("Rwanda", listOf("RWA")),
        BURUNDI("Burundi"),
        INDONESIA("Indonesia", listOf("IND")),
        PAPUA_NEW_GUINEA("Papua New Guinea", listOf("PNG")),
        YEMEN("Yemen"),
        PANAMA("Panama", listOf("PAN")),
        EL_SALVADOR("El Salvador", listOf("SAL")),
        MEXICO("Mexico", listOf("MEX")),
        NICARAGUA("Nicaragua", listOf("NIC")),
        TANZANIA("Tanzania", listOf("TAN")),
        UGANDA("Uganda", listOf("UGA")),
        DR_CONGO("DR Congo"),
        INDIA("India"),
        VIETNAM("Vietnam"),
        MYANMAR("Myanmar"),
        NEPAL("Nepal", listOf("NPL")),
        LAOS("Laos"),
        THAILAND("Thailand"),
        CHINA("China"),
        ECUADOR("Ecuador", listOf("ECU")),
        BOLIVIA("Bolivia", listOf("BOL")),
        MALAWI("Malawi"),
        ZAMBIA("Zambia"),
    }

    data class Other(val value: String) : CoffeeOrigin {
        override val displayName: String get() = value
    }

    companion object {
        val known: List<CoffeeOrigin> get() = Known.entries.toList()

        val allSearchTerms: List<String>
            get() = Known.entries
                .flatMap { listOf(it.displayName) + it.searchAliases }
                .sortedByDescending { it.length }

        /** Maps OCR abbreviations to canonical country names. */
        val abbreviationMap: Map<String, String>
            get() = Known.entries
                .flatMap { entry -> entry.searchAliases.map { it.uppercase() to entry.displayName } }
                .toMap()

        fun fromString(value: String): CoffeeOrigin {
            val trimmed = value.trim()
            return Known.entries.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) ||
                    entry.searchAliases.any { it.equals(trimmed, ignoreCase = true) }
            } ?: Other(trimmed)
        }
    }
}
