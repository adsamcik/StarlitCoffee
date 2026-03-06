package com.adsamcik.starlitcoffee.data.model

/**
 * Coffee growing region with known presets and an [Other] escape hatch.
 * Regions are grouped by country in comments for readability.
 */
sealed interface CoffeeRegion {
    val displayName: String

    enum class Known(
        override val displayName: String,
        val searchAliases: List<String> = emptyList(),
        val countries: List<String> = emptyList(),
    ) : CoffeeRegion {
        // Ethiopia
        YIRGACHEFFE("Yirgacheffe", countries = listOf("Ethiopia")),
        SIDAMO("Sidamo", listOf("Sidama"), countries = listOf("Ethiopia")),
        GUJI("Guji", countries = listOf("Ethiopia")),
        GEDEB("Gedeb", listOf("Gedeo"), countries = listOf("Ethiopia")),
        LIMMU("Limmu", countries = listOf("Ethiopia")),
        DJIMMAH("Djimmah", countries = listOf("Ethiopia")),
        HARRAR("Harrar", listOf("Harar"), countries = listOf("Ethiopia")),
        BENCH_MAJI("Bench Maji", countries = listOf("Ethiopia")),
        KAFFA("Kaffa", countries = listOf("Ethiopia")),
        BALE("Bale", countries = listOf("Ethiopia")),
        ARSI("Arsi", countries = listOf("Ethiopia")),
        WEST_ARSI("West Arsi", countries = listOf("Ethiopia")),
        BORENA("Borena", countries = listOf("Ethiopia")),
        // Colombia
        HUILA("Huila", countries = listOf("Colombia")),
        NARINO("Nariño", countries = listOf("Colombia")),
        CAUCA("Cauca", countries = listOf("Colombia")),
        TOLIMA("Tolima", countries = listOf("Colombia")),
        ANTIOQUIA("Antioquia", countries = listOf("Colombia")),
        QUINDIO("Quindio", countries = listOf("Colombia")),
        RISARALDA("Risaralda", countries = listOf("Colombia")),
        SANTANDER("Santander", countries = listOf("Colombia")),
        SIERRA_NEVADA("Sierra Nevada", countries = listOf("Colombia")),
        CALDAS("Caldas", countries = listOf("Colombia")),
        // Brazil
        CERRADO("Cerrado", countries = listOf("Brazil")),
        MOGIANA("Mogiana", countries = listOf("Brazil")),
        SUL_DE_MINAS("Sul de Minas", countries = listOf("Brazil")),
        MATAS_DE_MINAS("Matas de Minas", countries = listOf("Brazil")),
        CHAPADA_DE_MINAS("Chapada de Minas", countries = listOf("Brazil")),
        BAHIA("Bahia", countries = listOf("Brazil")),
        ESPIRITO_SANTO("Espírito Santo", countries = listOf("Brazil")),
        // Kenya
        NYERI("Nyeri", countries = listOf("Kenya")),
        KIAMBU("Kiambu", countries = listOf("Kenya")),
        KIRINYAGA("Kirinyaga", countries = listOf("Kenya")),
        MURANGA("Murang'a", countries = listOf("Kenya")),
        EMBU("Embu", countries = listOf("Kenya")),
        MERU("Meru", countries = listOf("Kenya")),
        THIKA("Thika", countries = listOf("Kenya")),
        RUIRU("Ruiru", countries = listOf("Kenya")),
        // Central America
        ANTIGUA("Antigua", countries = listOf("Guatemala")),
        ACATENANGO("Acatenango", countries = listOf("Guatemala")),
        TARRAZU("Tarrazu", countries = listOf("Costa Rica")),
        WEST_VALLEY("West Valley", countries = listOf("Costa Rica")),
        MARCALA("Marcala", countries = listOf("Honduras")),
        COPAN("Copan", countries = listOf("Honduras")),
        ATITLAN("Atitlan", countries = listOf("Guatemala")),
        FRAIJANES("Fraijanes", countries = listOf("Guatemala")),
        // South America
        CAJAMARCA("Cajamarca", countries = listOf("Peru")),
        CHANCHAMAYO("Chanchamayo", countries = listOf("Peru")),
        SAN_MARTIN("San Martin", countries = listOf("Peru")),
        JUNIN("Junin", countries = listOf("Peru")),
        // Indonesia
        SUMATRA("Sumatra", countries = listOf("Indonesia")),
        MANDHELING("Mandheling", countries = listOf("Indonesia")),
        GAYO("Gayo", countries = listOf("Indonesia")),
        TORAJA("Toraja", countries = listOf("Indonesia")),
        FLORES("Flores", countries = listOf("Indonesia")),
        BALI("Bali", countries = listOf("Indonesia")),
        // Africa
        KIVU("Kivu", countries = listOf("DR Congo", "Rwanda")),
        KAYANZA("Kayanza", countries = listOf("Burundi")),
        NGOZI("Ngozi", countries = listOf("Burundi")),
        KIGALI("Kigali", countries = listOf("Rwanda")),
    }

    data class Other(val value: String) : CoffeeRegion {
        override val displayName: String get() = value
    }

    companion object {
        val known: List<CoffeeRegion> get() = Known.entries.toList()

        /** Returns regions for a specific country, sorted alphabetically. */
        fun forCountry(country: String): List<CoffeeRegion> =
            Known.entries
                .filter { it.countries.any { c -> c.equals(country, ignoreCase = true) } }
                .sortedBy { it.displayName }

        val allSearchTerms: List<String>
            get() = Known.entries
                .flatMap { listOf(it.displayName) + it.searchAliases }
                .sortedByDescending { it.length }

        fun fromString(value: String): CoffeeRegion {
            val trimmed = value.trim()
            return Known.entries.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) ||
                    entry.searchAliases.any { it.equals(trimmed, ignoreCase = true) }
            } ?: Other(trimmed)
        }
    }
}
