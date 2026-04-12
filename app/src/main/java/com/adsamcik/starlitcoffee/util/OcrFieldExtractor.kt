package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeVariety

/**
 * Extracts structured coffee bag label fields from raw OCR text.
 */
object OcrFieldExtractor {

    data class OcrExtractionResult(
        val name: String? = null,
        val roaster: String? = null,
        val origin: String? = null,
        val region: String? = null,
        val farm: String? = null,
        val variety: String? = null,
        val processType: String? = null,
        val altitude: String? = null,
        val tastingNotes: String? = null,
        val roastLevel: String? = null,
        val roastDate: String? = null,
        val expiryDate: String? = null,
        val weight: String? = null,
        val isDecaf: Boolean? = null,
        val fieldConfidence: Map<String, BagFieldConfidence> = emptyMap(),
        /** Full raw OCR text from ML Kit, preserved for LLM context. */
        val rawText: String = "",
    )

    /**
     * Lightweight representation of an ML Kit text block with spatial info.
     * [heightPx] is the bounding box height (font-size proxy).
     * [topPx] is the Y position from the top of the image.
     */
    data class OcrTextBlock(
        val text: String,
        val heightPx: Int,
        val topPx: Int,
        val leftPx: Int = 0,
        val widthPx: Int = 0,
        val imageWidthPx: Int = 0,
        val imageHeightPx: Int = 0,
    ) {
        fun normalizedBounds(paddingFraction: Float = 0.04f): BagPhotoRect? {
            if (widthPx <= 0 || heightPx <= 0 || imageWidthPx <= 0 || imageHeightPx <= 0) return null
            val left = ((leftPx.toFloat() / imageWidthPx) - paddingFraction).coerceIn(0f, 1f)
            val top = ((topPx.toFloat() / imageHeightPx) - paddingFraction).coerceIn(0f, 1f)
            val right = (((leftPx + widthPx).toFloat() / imageWidthPx) + paddingFraction).coerceIn(0f, 1f)
            val bottom = (((topPx + heightPx).toFloat() / imageHeightPx) + paddingFraction).coerceIn(0f, 1f)
            if (right <= left || bottom <= top) return null
            return BagPhotoRect(
                leftFraction = left,
                topFraction = top,
                rightFraction = right,
                bottomFraction = bottom,
            )
        }
    }

    // --- Regexes built from sealed interface search terms (single source of truth) ---

    private fun buildRegex(terms: List<String>, wordBoundary: Boolean = true): Regex {
        val pattern = terms
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        return if (wordBoundary) {
            // Use Unicode-aware boundaries instead of \b (which only handles ASCII)
            Regex("(?<![\\p{L}\\p{N}])(?:$pattern)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        } else {
            Regex(pattern, RegexOption.IGNORE_CASE)
        }
    }

    private val countryRegex = buildRegex(
        CoffeeMetadataNormalizer.searchTermsForField("origin").filter { it.length > 3 },
        wordBoundary = false,
    )

    private val regionRegex = buildRegex(
        CoffeeMetadataNormalizer.searchTermsForField("region"),
        wordBoundary = false,
    )

    private val varietyRegex = buildRegex(CoffeeMetadataNormalizer.searchTermsForField("variety"))

    private val processRegex = buildRegex(CoffeeMetadataNormalizer.searchTermsForField("processType"))

    private val altitudeRegex = Regex(
        """(\d{3,4}\s*[-–]\s*\d{3,4}\s*(?:m\.?\s*n\.?\s*m\.?|m\.?a\.?s\.?l\.?|meters?|masl|m)\b|\d{3,4}\s*(?:m\.?\s*n\.?\s*m\.?|m\.?a\.?s\.?l\.?|meters?|masl|m)\b)""",
        RegexOption.IGNORE_CASE,
    )

    private val roastLevelRegex = buildRegex(CoffeeMetadataNormalizer.searchTermsForField("roastLevel"))

    private val tastingNotesLabelRegex = buildSectionLabelRegex(CountrySectionLabels::tastingNotes)

    private fun buildSectionLabelRegex(
        fieldSelector: (CountrySectionLabels) -> List<String>,
        countryHint: CoffeeCountryDictionary? = null,
    ): Regex {
        val labels = CoffeeCountryDictionaries.allSectionLabels(fieldSelector, countryHint)
        if (labels.isEmpty()) return Regex("""(?!x)x""") // never matches
        val pattern = labels
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        return Regex("""(?:$pattern)\s*:\s*(.+)""", RegexOption.IGNORE_CASE)
    }

    private val roasteryKeywords = CoffeeCountryDictionaries.allSectionLabels(CountrySectionLabels::roaster)
        .sortedByDescending { it.length }

    private fun buildRoasteryLabelRegex(countryHint: CoffeeCountryDictionary? = null): Regex {
        val keywords = CoffeeCountryDictionaries.allSectionLabels(CountrySectionLabels::roaster, countryHint)
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        return Regex(
            """([\w\s':.\u00C0-\u024F]+(?:$keywords))\b""",
            RegexOption.IGNORE_CASE,
        )
    }

    private val roasteryLabelRegex = buildRoasteryLabelRegex()

    private fun buildFarmLabelRegex(countryHint: CoffeeCountryDictionary? = null): Regex {
        val keywords = CoffeeCountryDictionaries.allSectionLabels(CountrySectionLabels::farm, countryHint)
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        return Regex("""(?:$keywords)\s*[:.]\s*(.+)""", RegexOption.IGNORE_CASE)
    }

    private val farmLabelRegex = buildFarmLabelRegex()

    // Matches SHORT lines with 3+ comma-separated words (likely flavor descriptors)
    private val commaFlavorLineRegex = Regex(
        """^([\w\s\u00C0-\u024F]+,\s*[\w\s\u00C0-\u024F]+(?:,\s*[\w\s\u00C0-\u024F]+)+)$""",
    )

    // Matches lines using alternative delimiters: · • | /
    private val altDelimiterFlavorRegex = Regex(
        """^([\w\s\u00C0-\u024F]+[·•|/]\s*[\w\s\u00C0-\u024F]+(?:\s*[·•|/]\s*[\w\s\u00C0-\u024F]+)*)$""",
    )

    // Matches lines using dash/en-dash delimiters with 3+ items (avoids false positives like "Heirloom - Washed")
    private val dashDelimiterFlavorRegex = Regex(
        """^([\w\s\u00C0-\u024F]+\s+[-–]\s+[\w\s\u00C0-\u024F]+(?:\s+[-–]\s+[\w\s\u00C0-\u024F]+)+)$""",
    )

    private val weightRegex = Regex(
        """(?<!\d)\b(\d{1,4}(?:[.,]\d{1,2})?)\s*(?:kg|kilogram[s]?|lb[s]?|pound[s]?|g|gram[s]?|oz|ounce[s]?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val abbreviationRegex = buildRegex(
        CoffeeOrigin.abbreviationMap.keys.toList(),
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

    fun extractFields(
        rawText: String,
        knownFields: KnownFieldValues = KnownFieldValues.EMPTY,
        countryHint: CoffeeCountryDictionary? = null,
    ): OcrExtractionResult {
        val fullText = rawText.trim()
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var originConfidence: BagFieldConfidence? = null
        // Try full country name first, then abbreviations
        val origin = countryRegex.find(fullText)?.value
            ?.also { originConfidence = BagFieldConfidence.HIGH }
            ?: abbreviationRegex.find(fullText)?.let { match ->
                CoffeeOrigin.abbreviationMap[match.value.uppercase()]
            }
                ?.also { originConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.origins)
                ?.also { originConfidence = BagFieldConfidence.HIGH }
            ?: countryHint?.let {
                extractSectionLabelValue(lines, CountrySectionLabels::origin, it)
            }?.also { originConfidence = BagFieldConfidence.HIGH }
            ?: fuzzyMatchField(
                fullText,
                CoffeeMetadataNormalizer.searchTermsForField("origin"),
                knownFields.origins,
                maxDistance = 1,
            )
                ?.also { originConfidence = BagFieldConfidence.MEDIUM }

        var regionConfidence: BagFieldConfidence? = null
        val region = regionRegex.find(fullText)?.value
            ?.also { regionConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.regions)
                ?.also { regionConfidence = BagFieldConfidence.HIGH }
            ?: fuzzyMatchField(fullText, CoffeeMetadataNormalizer.searchTermsForField("region"), knownFields.regions)
                ?.also { regionConfidence = BagFieldConfidence.MEDIUM }

        var varietyConfidence: BagFieldConfidence? = null
        val variety = extractAll(fullText, varietyRegex)
            ?.also { varietyConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.varieties)
                ?.also { varietyConfidence = BagFieldConfidence.HIGH }
            ?: extractSectionLabelValue(lines, CountrySectionLabels::variety, countryHint)
                ?.also { varietyConfidence = BagFieldConfidence.MEDIUM }
            ?: fuzzyMatchField(fullText, CoffeeMetadataNormalizer.searchTermsForField("variety"), knownFields.varieties)
                ?.also { varietyConfidence = BagFieldConfidence.MEDIUM }

        var processTypeConfidence: BagFieldConfidence? = null
        val processType = processRegex.find(fullText)?.value
            ?.also { processTypeConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.processTypes)
                ?.also { processTypeConfidence = BagFieldConfidence.HIGH }
            ?: fuzzyMatchField(fullText, CoffeeMetadataNormalizer.searchTermsForField("processType"), knownFields.processTypes)
                ?.also { processTypeConfidence = BagFieldConfidence.MEDIUM }

        val altitude = altitudeRegex.find(fullText)?.value
        val altitudeConfidence = altitude?.let { BagFieldConfidence.HIGH }

        var roastLevelConfidence: BagFieldConfidence? = null
        val roastLevel = extractAllRoastLevels(fullText)
            ?.also { roastLevelConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.roastLevels)
                ?.also { roastLevelConfidence = BagFieldConfidence.HIGH }
            ?: fuzzyMatchField(fullText, CoffeeMetadataNormalizer.searchTermsForField("roastLevel"), knownFields.roastLevels)
                ?.also { roastLevelConfidence = BagFieldConfidence.MEDIUM }
        val labeledDates = extractLabeledDates(fullText, countryHint)
        val roastDateConfidence = labeledDates.roastDate?.let { BagFieldConfidence.HIGH }
        val expiryDateConfidence = labeledDates.expiryDate?.let { BagFieldConfidence.HIGH }
        val tastingNotes = extractTastingNotes(lines, countryHint)
        val tastingNotesConfidence = tastingNotes?.let { BagFieldConfidence.HIGH }
        val weight = extractWeight(fullText)
        val weightConfidence = weight?.let { BagFieldConfidence.HIGH }
        val isDecaf = CoffeeMetadataNormalizer.containsDecafMarker(fullText).takeIf { it }
        val isDecafConfidence = isDecaf?.let { BagFieldConfidence.HIGH }

        var roasterConfidence: BagFieldConfidence? = null
        val roaster = extractRoaster(lines, countryHint)
            ?.also { roasterConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.roasters)
                ?.also { roasterConfidence = BagFieldConfidence.HIGH }

        var farmConfidence: BagFieldConfidence? = null
        val farm = extractFarm(lines, countryHint)
            ?.also { farmConfidence = BagFieldConfidence.HIGH }
            ?: findKnownMatch(fullText, knownFields.farms)
                ?.also { farmConfidence = BagFieldConfidence.HIGH }

        // Derive name from origin + region as fallback for text-only extraction
        val name = listOfNotNull(origin, region).joinToString(" ").ifEmpty { null }
            ?: findKnownMatch(fullText, knownFields.names)
        val nameConfidence = when {
            !name.isNullOrBlank() && !origin.isNullOrBlank() && !region.isNullOrBlank() -> BagFieldConfidence.LOW
            name != null -> BagFieldConfidence.HIGH
            else -> null
        }

        val fieldConfidence = buildMap {
            originConfidence?.let { put("origin", it) }
            regionConfidence?.let { put("region", it) }
            varietyConfidence?.let { put("variety", it) }
            processTypeConfidence?.let { put("processType", it) }
            altitudeConfidence?.let { put("altitude", it) }
            roastLevelConfidence?.let { put("roastLevel", it) }
            roastDateConfidence?.let { put("roastDate", it) }
            expiryDateConfidence?.let { put("expiryDate", it) }
            tastingNotesConfidence?.let { put("tastingNotes", it) }
            weightConfidence?.let { put("weight", it) }
            roasterConfidence?.let { put("roaster", it) }
            farmConfidence?.let { put("farm", it) }
            nameConfidence?.let { put("name", it) }
            isDecafConfidence?.let { put("isDecaf", it) }
        }

        return OcrExtractionResult(
            name = name,
            origin = origin,
            region = region,
            farm = farm,
            variety = variety,
            processType = processType,
            altitude = altitude,
            tastingNotes = tastingNotes,
            roastLevel = roastLevel,
            roastDate = labeledDates.roastDate,
            expiryDate = labeledDates.expiryDate,
            weight = weight,
            isDecaf = isDecaf,
            roaster = roaster,
            fieldConfidence = fieldConfidence,
            rawText = rawText,
        )
    }

    private fun findKnownMatch(text: String, knownValues: List<String>): String? {
        return knownValues.firstOrNull { known ->
            text.contains(known, ignoreCase = true)
        }
    }

    private fun fuzzyMatchField(
        text: String,
        sealedTerms: List<String>,
        historyTerms: List<String>,
        maxDistance: Int = 2,
        minLength: Int = 5,
    ): String? {
        return FuzzyMatcher.fuzzyMatchInText(text, sealedTerms, maxDistance, minLength)
            ?: FuzzyMatcher.fuzzyMatchInText(text, historyTerms, maxDistance, minLength)
    }

    /**
     * Extracts all distinct roast levels, suppressing shorter matches
     * that are substrings of longer ones (e.g., "filter" is dropped
     * when "filter roast" is also found).
     */
    private fun extractAllRoastLevels(text: String): String? {
        val matches = roastLevelRegex.findAll(text)
            .map { it.value }
            .distinct()
            .toList()
        // Remove shorter matches that are substrings of longer ones
        val filtered = matches.filter { match ->
            matches.none { other ->
                other.length > match.length && other.contains(match, ignoreCase = true)
            }
        }
        return filtered.joinToString(", ").ifEmpty { null }
    }

    private fun extractAll(text: String, regex: Regex): String? {
        val matches = regex.findAll(text)
            .map { it.value }
            .distinct()
            .toList()
        return matches.joinToString(", ").ifEmpty { null }
    }

    /**
     * Generic section-label extraction: finds a line matching a section label
     * from the country dictionaries and returns the text after it.
     */
    private fun extractSectionLabelValue(
        lines: List<String>,
        fieldSelector: (CountrySectionLabels) -> List<String>,
        countryHint: CoffeeCountryDictionary? = null,
    ): String? {
        val labelRegex = buildSectionLabelRegex(fieldSelector, countryHint)
        for (line in lines) {
            val match = labelRegex.find(line)
            if (match != null) {
                val value = match.groupValues[1].trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun extractTastingNotes(lines: List<String>, countryHint: CoffeeCountryDictionary? = null): String? {
        val labelRegex = if (countryHint != null) {
            buildSectionLabelRegex(CountrySectionLabels::tastingNotes, countryHint)
        } else {
            tastingNotesLabelRegex
        }
        for (line in lines) {
            val labelMatch = labelRegex.find(line)
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
            // Check alternative delimiters: · • | /
            val altMatch = altDelimiterFlavorRegex.find(line)
            if (altMatch != null && line.length <= 80) {
                // Normalize delimiters to commas for consistency
                return altMatch.groupValues[1]
                    .replace(Regex("""[·•|/]"""), ",")
                    .split(",").joinToString(", ") { it.trim() }
            }
            // Check dash/en-dash delimiters (requires 3+ items to avoid false positives)
            val dashMatch = dashDelimiterFlavorRegex.find(line)
            if (dashMatch != null && line.length <= 80) {
                return dashMatch.groupValues[1]
                    .split(Regex("""\s+[-–]\s+"""))
                    .joinToString(", ") { it.trim() }
            }
        }
        return null
    }

    private fun extractWeight(text: String): String? {
        val match = weightRegex.find(text) ?: return null
        val value = match.groupValues[1]
        val unit = match.value.replace(value, "").trim().lowercase()
        return when {
            unit.startsWith("kg") || unit.startsWith("kilogram") -> "${value}kg"
            unit.startsWith("lb") || unit.startsWith("pound") -> "${value}lb"
            unit.startsWith("oz") || unit.startsWith("ounce") -> "${value}oz"
            else -> "${value}g"
        }
    }

    private fun extractRoaster(lines: List<String>, countryHint: CoffeeCountryDictionary? = null): String? {
        val regex = if (countryHint != null) buildRoasteryLabelRegex(countryHint) else roasteryLabelRegex
        val match = regex.find(lines.joinToString("\n"))
        if (match != null) {
            return match.groupValues[1].trim()
                .replace(Regex("""\s+"""), " ")
        }
        return null
    }

    private fun extractFarm(lines: List<String>, countryHint: CoffeeCountryDictionary? = null): String? {
        val regex = if (countryHint != null) buildFarmLabelRegex(countryHint) else farmLabelRegex
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private val roastLabelRegex = Regex(
        """(?:(?:datum\s+)?(?:roast(?:ed)?|pražen[íoá]|upražili(?:\s+jsme)?|upražen[oá]|geröst(?:et)?|tostado|torrado|tostato|torréfié|ristet|rostad|brent|paahdettu|pražen[éý]|gebrand|palono|wypalono))\s*(?:on|date|:)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val expiryLabelRegex = Regex(
        """(?:(?:best\s*before|best\s*by|use\s*by|expir(?:y|es?|ation)|consume\s*(?:before|by)|BB|EXP|MHD|THT|TMC|DLUO|DDM|spotřebujte\s*do|nejlépe\s*do|(?:datum\s+)?minimální\s+trvanlivost[i]?|mindestens\s*haltbar|haltbar\s*bis|à\s*consommer\s*(?:de\s*préférence\s*)?avant|bedst\s*før|bäst\s*före|best\s*før|parasta\s*ennen|ten\s*minste\s*houdbaar\s*tot|najlepiej\s*spożyć\s*przed|termin\s*przydatności|scadenza|caducidad|validade|spotrebujte\s*do|minimálna\s*trvanlivosť|賞味期限|消費期限|유통기한|소비기한)\s*(?:date)?)\s*[:.]?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private fun buildDateLabelRegex(
        fieldSelector: (CountrySectionLabels) -> List<String>,
        fallbackRegex: Regex,
        countryHint: CoffeeCountryDictionary?,
    ): Regex {
        if (countryHint == null) return fallbackRegex
        val labels = CoffeeCountryDictionaries.allSectionLabels(fieldSelector, countryHint)
        if (labels.isEmpty()) return fallbackRegex
        val pattern = labels
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        return Regex("""(?:$pattern)\s*(?:date)?\s*[:.]?\s*""", RegexOption.IGNORE_CASE)
    }

    private data class LabeledDates(val roastDate: String?, val expiryDate: String?)

    private fun extractLabeledDates(text: String, countryHint: CoffeeCountryDictionary? = null): LabeledDates {
        val activeRoastRegex = buildDateLabelRegex(CountrySectionLabels::roastDate, roastLabelRegex, countryHint)
        val activeExpiryRegex = buildDateLabelRegex(CountrySectionLabels::expiryDate, expiryLabelRegex, countryHint)
        var roastDate: String? = null
        var expiryDate: String? = null

        val lines = text.lines()
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (expiryDate == null) {
                val expiryLabel = activeExpiryRegex.find(trimmed)
                if (expiryLabel != null) {
                    expiryDate = findDateAfterLabel(trimmed, expiryLabel)
                        ?: findDateOnNextLine(lines, i)
                }
            }
            if (roastDate == null) {
                val roastLabel = activeRoastRegex.find(trimmed)
                if (roastLabel != null) {
                    roastDate = findDateAfterLabel(trimmed, roastLabel)
                        ?: findDateOnNextLine(lines, i)
                }
            }
        }

        // Fallback: first unlabeled date → roastDate (most common on specialty bags)
        if (roastDate == null && expiryDate == null) {
            roastDate = extractFirstDate(text)
        }

        return LabeledDates(roastDate, expiryDate)
    }

    private fun findDateAfterLabel(line: String, labelMatch: MatchResult): String? {
        if (labelMatch.range.last + 1 >= line.length) return null
        val afterLabel = line.substring(labelMatch.range.last + 1)
        for (pattern in datePatterns) {
            val match = pattern.find(afterLabel)
            if (match != null) return match.value
        }
        return null
    }

    private fun findDateOnNextLine(lines: List<String>, currentIndex: Int): String? {
        val nextIndex = currentIndex + 1
        if (nextIndex >= lines.size) return null
        val nextLine = lines[nextIndex].trim()
        for (pattern in datePatterns) {
            val match = pattern.find(nextLine)
            if (match != null) return match.value
        }
        return null
    }

    private fun extractFirstDate(text: String): String? {
        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) return match.value
        }
        return null
    }

    // --- Spatial (block-based) extraction for name & roaster ---

    private val NOISE_WORDS = setOf(
        "the", "a", "an", "of", "by", "from", "for", "in", "to", "and", "or", "with",
        "coffee", "café", "kaffee", "káva", "kaffe",
        "single", "origin", "blend", "micro", "lot",
        "specialty", "speciality",
        "arabica", "robusta",
        "net", "wt", "netto",
    )

    private val noiseWordRegex = NOISE_WORDS
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .let { Regex("\\b(?:$it)\\b", RegexOption.IGNORE_CASE) }

    /**
     * Checks whether a text block is fully explained by known-field regex matches
     * (origin, variety, process, etc.) and noise words — meaning it's NOT a
     * candidate for name or roaster.
     */
    internal fun isBlockConsumedByKnownFields(blockText: String): Boolean {
        val text = blockText.trim()
        if (text.isEmpty()) return true

        // Entire-block patterns that explain the whole block
        if (tastingNotesLabelRegex.containsMatchIn(text)) return true
        if (text.length <= 80 && commaFlavorLineRegex.containsMatchIn(text)) return true
        if (text.length <= 80 && altDelimiterFlavorRegex.containsMatchIn(text)) return true
        if (text.length <= 80 && dashDelimiterFlavorRegex.containsMatchIn(text)) return true
        if (farmLabelRegex.containsMatchIn(text)) return true

        // Strip all known-field regex matches
        var remaining = text
        val fieldRegexes = listOf(
            countryRegex, regionRegex, varietyRegex, processRegex,
            altitudeRegex, roastLevelRegex, weightRegex, abbreviationRegex,
        )
        for (regex in fieldRegexes) {
            remaining = regex.replace(remaining, " ")
        }
        for (pattern in datePatterns) {
            remaining = pattern.replace(remaining, " ")
        }

        // Strip noise words
        remaining = noiseWordRegex.replace(remaining, " ")

        // Strip punctuation, digits, and whitespace
        remaining = remaining.replace(Regex("""[\s\d\-–—,.:;/|+*#@!?'"()\[\]{}%]+"""), "")

        return remaining.length < 3
    }

    // --- Multi-signal scoring for name/roaster detection ---

    internal data class CandidateScore(
        val block: OcrTextBlock,
        val score: Float,
        val isKnownRoaster: Boolean = false,
        val isKnownName: Boolean = false,
    )

    internal fun scoreCandidateBlocks(
        candidates: List<OcrTextBlock>,
        maxHeight: Int,
        knownRoasters: List<String>,
        knownNames: List<String>,
        knownFields: KnownFieldValues = KnownFieldValues(
            names = knownNames,
            roasters = knownRoasters,
        ),
    ): List<CandidateScore> {
        return candidates.map { block ->
            val text = block.text.trim()
            var score = 0f

            // Signal 1: Font size (normalized to 0-1 range, weighted heavily)
            val fontScore = block.heightPx.toFloat() / maxHeight
            score += fontScore * 40f

            // Signal 2: Position — blocks near top score higher
            val maxTop = candidates.maxOf { it.topPx }.coerceAtLeast(1)
            val positionScore = 1f - (block.topPx.toFloat() / maxTop)
            score += positionScore * 15f

            // Signal 3: ALL CAPS — brand names and coffee names are often stylized
            val isAllCaps = text.length >= 3 && text == text.uppercase() && text.any { it.isLetter() }
            if (isAllCaps) score += 10f

            // Signal 4: Known roaster match (strongest signal)
            val matchesKnownRoaster = knownRoasters.any { known ->
                text.equals(known, ignoreCase = true) ||
                    text.contains(known, ignoreCase = true) ||
                    known.contains(text, ignoreCase = true)
            }
            if (matchesKnownRoaster) score += 50f

            // Signal 4b: Fuzzy roaster match (weaker than exact)
            var fuzzyRoaster: String? = null
            if (!matchesKnownRoaster) {
                fuzzyRoaster = FuzzyMatcher.fuzzyMatch(text, knownRoasters, maxDistance = 2, minLength = 4)
                if (fuzzyRoaster != null) score += 30f
            }

            // Signal 5: Known name match
            val matchesKnownName = knownNames.any { known ->
                text.equals(known, ignoreCase = true) ||
                    text.contains(known, ignoreCase = true) ||
                    known.contains(text, ignoreCase = true)
            }
            if (matchesKnownName) score += 50f

            // Signal 5b: Fuzzy name match (weaker than exact)
            var fuzzyName: String? = null
            if (!matchesKnownName) {
                fuzzyName = FuzzyMatcher.fuzzyMatch(text, knownNames, maxDistance = 2, minLength = 4)
                if (fuzzyName != null) score += 30f
            }

            // Signal 6: Known field matches from bag history
            if (findKnownMatch(text, knownFields.origins) != null) score += 20f
            if (findKnownMatch(text, knownFields.regions) != null) score += 15f
            if (findKnownMatch(text, knownFields.varieties) != null) score += 15f

            // Signal 7: Contains roastery keyword
            val hasRoasteryKeyword = roasteryLabelRegex.containsMatchIn(text)
            if (hasRoasteryKeyword) score += 30f

            // Signal 8: Short text (1-4 words) — more likely a name/brand
            val wordCount = text.split(Regex("""\s+""")).size
            if (wordCount in 1..4) score += 5f

            CandidateScore(
                block = block,
                score = score,
                isKnownRoaster = matchesKnownRoaster || fuzzyRoaster != null || hasRoasteryKeyword,
                isKnownName = matchesKnownName || fuzzyName != null,
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Enhanced extraction that uses ML Kit text block spatial data to identify
     * name and roaster. Blocks are ranked by a multi-signal scoring system;
     * known roasters/names from saved bags boost matching confidence.
     */
    fun extractFieldsFromBlocks(
        blocks: List<OcrTextBlock>,
        knownRoasters: List<String> = emptyList(),
        knownNames: List<String> = emptyList(),
        knownFields: KnownFieldValues = KnownFieldValues(
            names = knownNames,
            roasters = knownRoasters,
        ),
        countryHint: CoffeeCountryDictionary? = null,
    ): OcrExtractionResult {
        if (blocks.isEmpty()) return OcrExtractionResult()

        val fullText = blocks.joinToString("\n") { it.text }
        val baseResult = extractFields(fullText, knownFields, countryHint)

        val maxHeight = blocks.maxOf { it.heightPx }
        val minCandidateHeight = (maxHeight * 0.25).toInt()

        // Filter to candidate blocks: prominent + not explained by known fields
        val candidates = blocks.filter { block ->
            val text = block.text.trim()
            if (text.length < 2) return@filter false
            if (block.heightPx < minCandidateHeight) return@filter false
            if (text.contains("www.", ignoreCase = true)) return@filter false
            if (text.startsWith("http", ignoreCase = true)) return@filter false
            !isBlockConsumedByKnownFields(text)
        }

        val scored = scoreCandidateBlocks(candidates, maxHeight, knownRoasters, knownNames, knownFields)

        val keywordRoaster = baseResult.roaster
        val roaster: String?
        val name: String?

        if (keywordRoaster != null) {
            // Roaster found by keyword — pick name from highest-scoring non-roaster
            roaster = scored.firstOrNull {
                it.isKnownRoaster || it.block.text.contains(keywordRoaster, ignoreCase = true)
            }?.block?.text?.trim() ?: keywordRoaster
            name = scored.firstOrNull {
                it.block.text.trim() != roaster
            }?.block?.text?.trim()
        } else {
            // No keyword roaster — use scoring
            val roasterCandidate = scored.firstOrNull { it.isKnownRoaster }
                ?: scored.firstOrNull { !it.isKnownName }
                ?: scored.firstOrNull()

            roaster = roasterCandidate?.block?.text?.trim()

            // Name = highest-scoring block that isn't the roaster
            name = scored.firstOrNull { candidate ->
                candidate.block != roasterCandidate?.block
            }?.block?.text?.trim()
        }

        val derivedName = name ?: baseResult.name

        return baseResult.copy(
            name = derivedName,
            roaster = roaster ?: baseResult.roaster,
        )
    }

    fun extractFieldsFromBlocks(
        blocks: List<OcrTextBlock>,
        knownFields: KnownFieldValues,
        countryHint: CoffeeCountryDictionary? = null,
    ): OcrExtractionResult = extractFieldsFromBlocks(
        blocks = blocks,
        knownRoasters = knownFields.roasters,
        knownNames = knownFields.names,
        knownFields = knownFields,
        countryHint = countryHint,
    )

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
