package com.adsamcik.starlitcoffee.util

/**
 * Extracts structured coffee bag label fields from raw OCR text.
 */
object OcrFieldExtractor {

    data class OcrExtractionResult(
        val roaster: String? = null,
        val origin: String? = null,
        val region: String? = null,
        val variety: String? = null,
        val processType: String? = null,
        val altitude: String? = null,
        val tastingNotes: String? = null,
        val roastLevel: String? = null,
        val roastDate: String? = null,
    )

    private val COUNTRIES = listOf(
        "Ethiopia", "Colombia", "Brazil", "Kenya", "Guatemala", "Costa Rica",
        "Honduras", "Peru", "Rwanda", "Burundi", "Indonesia", "Papua New Guinea",
        "Yemen", "Panama", "El Salvador", "Mexico", "Nicaragua", "Tanzania",
        "Uganda", "DR Congo", "India", "Vietnam", "Myanmar", "Laos",
        "Thailand", "China", "Ecuador", "Bolivia", "Malawi", "Zambia",
    )

    private val REGIONS = listOf(
        "Yirgacheffe", "Sidamo", "Guji", "Huila", "Nariño", "Cauca", "Tolima",
        "Cerrado", "Mogiana", "Sul de Minas", "Nyeri", "Kiambu", "Kirinyaga",
        "Murang'a", "Antigua", "Acatenango", "Tarrazu", "West Valley", "Marcala",
        "Cajamarca", "Chanchamayo",
    )

    private val VARIETIES = listOf(
        "Pink Bourbon", "Yellow Bourbon", "Red Bourbon", "Yellow Catuai", "Red Catuai",
        "Mundo Novo", "Ruiru 11", "SL28", "SL34",
        "Bourbon", "Typica", "Geisha", "Gesha", "Caturra", "Catuai", "Pacamara",
        "Maragogype", "Castillo", "Heirloom", "Java", "Catimor", "Batian",
        "Marsellesa", "Parainema", "Obata", "Tabi",
    )

    private val PROCESSES = listOf(
        "carbonic maceration", "double fermented", "pulped natural",
        "semi-washed", "wet-hulled", "anaerobic",
        "washed", "natural", "honey",
    )

    private val ROAST_LEVELS = listOf(
        "medium-light", "medium-dark",
        "espresso roast", "filter roast", "omniroast",
        "light", "medium", "dark",
    )

    private val countryRegex = COUNTRIES
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex(it, RegexOption.IGNORE_CASE) }

    private val regionRegex = REGIONS
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex(it, RegexOption.IGNORE_CASE) }

    private val varietyRegex = VARIETIES
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex("\\b(?:$it)\\b", RegexOption.IGNORE_CASE) }

    private val processRegex = PROCESSES
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex("\\b(?:$it)\\b", RegexOption.IGNORE_CASE) }

    private val altitudeRegex = Regex(
        """(\d{3,4}\s*[-–]\s*\d{3,4}\s*(?:m\.?a\.?s\.?l\.?|meters?|masl|m)\b|\d{3,4}\s*(?:m\.?a\.?s\.?l\.?|meters?|masl|m)\b)""",
        RegexOption.IGNORE_CASE,
    )

    private val roastLevelRegex = ROAST_LEVELS
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex("\\b(?:$it)\\b", RegexOption.IGNORE_CASE) }

    private val tastingNotesLabelRegex = Regex(
        """(?:tasting\s+notes|cupping\s+notes|notes|flavor|flavour|tastes\s+like)\s*:\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )

    // Matches lines with 3+ comma-separated words (likely flavor descriptors)
    private val commaFlavorLineRegex = Regex(
        """^([\w\s]+,\s*[\w\s]+(?:,\s*[\w\s]+)+)$""",
    )

    private val datePatterns = listOf(
        // DD/MM/YYYY or MM/DD/YYYY
        Regex("""\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b"""),
        // Month DD, YYYY
        Regex("""\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},?\s+\d{4}\b""", RegexOption.IGNORE_CASE),
        // DD Month YYYY
        Regex("""\b\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4}\b""", RegexOption.IGNORE_CASE),
        // Month YYYY
        Regex("""\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4}\b""", RegexOption.IGNORE_CASE),
    )

    fun extractFields(rawText: String): OcrExtractionResult {
        val fullText = rawText.trim()
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val origin = countryRegex.find(fullText)?.value
        val region = regionRegex.find(fullText)?.value
        val variety = extractAll(fullText, varietyRegex)
        val processType = processRegex.find(fullText)?.value
        val altitude = altitudeRegex.find(fullText)?.value
        val roastLevel = roastLevelRegex.find(fullText)?.value
        val roastDate = extractRoastDate(fullText)
        val tastingNotes = extractTastingNotes(lines)

        return OcrExtractionResult(
            origin = origin,
            region = region,
            variety = variety,
            processType = processType,
            altitude = altitude,
            tastingNotes = tastingNotes,
            roastLevel = roastLevel,
            roastDate = roastDate,
        )
    }

    private fun extractAll(text: String, regex: Regex): String? {
        val matches = regex.findAll(text)
            .map { it.value }
            .distinct()
            .toList()
        return matches.joinToString(", ").ifEmpty { null }
    }

    private fun extractTastingNotes(lines: List<String>): String? {
        for (line in lines) {
            val labelMatch = tastingNotesLabelRegex.find(line)
            if (labelMatch != null) {
                return labelMatch.groupValues[1].trim()
            }
        }
        for (line in lines) {
            val commaMatch = commaFlavorLineRegex.find(line)
            if (commaMatch != null) {
                return commaMatch.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractRoastDate(text: String): String? {
        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) return match.value
        }
        return null
    }
}
