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
        val weight: String? = null,
    )

    private val COUNTRIES = listOf(
        "Ethiopia", "Colombia", "Brazil", "Kenya", "Guatemala", "Costa Rica",
        "Honduras", "Peru", "Rwanda", "Burundi", "Indonesia", "Papua New Guinea",
        "Yemen", "Panama", "El Salvador", "Mexico", "Nicaragua", "Tanzania",
        "Uganda", "DR Congo", "India", "Vietnam", "Myanmar", "Laos",
        "Thailand", "China", "Ecuador", "Bolivia", "Malawi", "Zambia",
    )

    // Common OCR abbreviations and misspellings for country names
    private val COUNTRY_ABBREVIATIONS = mapOf(
        "ETH" to "Ethiopia", "COL" to "Colombia", "BRA" to "Brazil",
        "KEN" to "Kenya", "GUA" to "Guatemala", "HON" to "Honduras",
        "PER" to "Peru", "RWA" to "Rwanda", "IND" to "Indonesia",
        "PAN" to "Panama", "MEX" to "Mexico", "NIC" to "Nicaragua",
        "TAN" to "Tanzania", "ECU" to "Ecuador", "BOL" to "Bolivia",
    )

    private val REGIONS = listOf(
        // Ethiopia
        "Yirgacheffe", "Sidamo", "Sidama", "Guji", "Gedeb", "Gedeo",
        "Limmu", "Djimmah", "Harrar", "Harar", "Bench Maji", "Kaffa",
        "Bale", "Arsi", "West Arsi", "Borena",
        // Colombia
        "Huila", "Nariño", "Cauca", "Tolima", "Antioquia", "Quindio",
        "Risaralda", "Santander", "Sierra Nevada", "Caldas",
        // Brazil
        "Cerrado", "Mogiana", "Sul de Minas", "Matas de Minas",
        "Chapada de Minas", "Bahia", "Espírito Santo",
        // Kenya
        "Nyeri", "Kiambu", "Kirinyaga", "Murang'a", "Embu", "Meru",
        "Thika", "Ruiru",
        // Central America
        "Antigua", "Acatenango", "Tarrazu", "West Valley", "Marcala",
        "Copan", "Atitlan", "Fraijanes",
        // South America
        "Cajamarca", "Chanchamayo", "San Martin", "Junin",
        // Indonesia
        "Sumatra", "Mandheling", "Gayo", "Toraja", "Flores", "Bali",
        // Africa
        "Kivu", "Kayanza", "Ngozi", "Kigali",
    )

    private val VARIETIES = listOf(
        "Pink Bourbon", "Yellow Bourbon", "Red Bourbon", "Yellow Catuai", "Red Catuai",
        "Mundo Novo", "Ruiru 11", "SL28", "SL34",
        "Bourbon", "Typica", "Geisha", "Gesha", "Caturra", "Catuai", "Pacamara",
        "Maragogype", "Castillo", "Heirloom", "Java", "Catimor", "Batian",
        "Marsellesa", "Parainema", "Obata", "Tabi", "74110", "74112", "74158",
        "Sidra", "Eugenioides", "Liberica", "Maracaturra", "Villa Sarchi",
    )

    private val PROCESSES = listOf(
        "carbonic maceration", "double fermented", "pulped natural",
        "semi-washed", "wet-hulled", "anaerobic", "thermal shock",
        "washed", "natural", "honey",
    )

    private val ROAST_LEVELS = listOf(
        "medium-light", "medium-dark",
        "espresso roast", "filter roast", "omniroast",
        "light", "medium", "dark",
        "filter", "espresso",
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

    // Matches SHORT lines with 3+ comma-separated words (likely flavor descriptors)
    // Max ~80 chars to avoid matching long prose paragraphs
    private val commaFlavorLineRegex = Regex(
        """^([\w\s\u00C0-\u024F]+,\s*[\w\s\u00C0-\u024F]+(?:,\s*[\w\s\u00C0-\u024F]+)+)$""",
    )

    private val weightRegex = Regex(
        """(?<!\d)\b(\d{2,4})\s*(?:g|grams?|oz)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Matches common roaster label patterns like "ROASTERY", "COFFEE ROASTERS", "KAFFEERÖSTEREI"
    private val roasteryLabelRegex = Regex(
        """([\w\s':.\u00C0-\u024F]+(?:roaster[sy]?|coffee\s*roaster[sy]?|rösterei|pražírna|torrefazione))\b""",
        RegexOption.IGNORE_CASE,
    )

    private val abbreviationRegex = COUNTRY_ABBREVIATIONS.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex("\\b(?:$it)\\b", RegexOption.IGNORE_CASE) }

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

        // Try full country name first, then abbreviations
        val origin = countryRegex.find(fullText)?.value
            ?: abbreviationRegex.find(fullText)?.let { match ->
                COUNTRY_ABBREVIATIONS[match.value.uppercase()]
            }
        val region = regionRegex.find(fullText)?.value
        val variety = extractAll(fullText, varietyRegex)
        val processType = processRegex.find(fullText)?.value
        val altitude = altitudeRegex.find(fullText)?.value
        val roastLevel = roastLevelRegex.find(fullText)?.value
        val roastDate = extractRoastDate(fullText)
        val tastingNotes = extractTastingNotes(lines)
        val weight = extractWeight(fullText)
        val roaster = extractRoaster(lines)

        return OcrExtractionResult(
            origin = origin,
            region = region,
            variety = variety,
            processType = processType,
            altitude = altitude,
            tastingNotes = tastingNotes,
            roastLevel = roastLevel,
            roastDate = roastDate,
            weight = weight,
            roaster = roaster,
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
            // Skip long lines (>80 chars) — likely prose, not flavor descriptors
            if (line.length > 80) continue
            val commaMatch = commaFlavorLineRegex.find(line)
            if (commaMatch != null) {
                return commaMatch.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractWeight(text: String): String? {
        val match = weightRegex.find(text) ?: return null
        val value = match.groupValues[1]
        val unit = match.value.replace(value, "").trim().lowercase()
        return if (unit.startsWith("oz")) "${value}oz" else "${value}g"
    }

    private fun extractRoaster(lines: List<String>): String? {
        // Look for lines containing roastery-related keywords
        val match = roasteryLabelRegex.find(lines.joinToString("\n"))
        if (match != null) {
            return match.groupValues[1].trim()
                .replace(Regex("""\s+"""), " ")
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

    // EAN-13 barcode regex: OCR may read spaces between digit groups (e.g., "8 594206 183060")
    private val eanBarcodeRegex = Regex("""(\d[\d ]{11,15}\d)""")

    /**
     * Extracts a barcode number from OCR text as a fallback when ML Kit barcode
     * scanning fails. Looks for 12-13 digit sequences (possibly space-separated).
     */
    fun extractBarcodeFromText(rawText: String): String? {
        for (match in eanBarcodeRegex.findAll(rawText)) {
            val digits = match.value.replace(" ", "")
            if (digits.length == 13 || digits.length == 12) {
                return digits
            }
        }
        return null
    }
}
