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
    )

    // --- Regexes built from sealed interface search terms (single source of truth) ---

    private fun buildRegex(terms: List<String>, wordBoundary: Boolean = true): Regex {
        val pattern = terms
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        return if (wordBoundary) {
            Regex("\\b(?:$pattern)\\b", RegexOption.IGNORE_CASE)
        } else {
            Regex(pattern, RegexOption.IGNORE_CASE)
        }
    }

    private val countryRegex = buildRegex(
        CoffeeOrigin.Known.entries.map { it.displayName },
        wordBoundary = false,
    )

    private val regionRegex = buildRegex(
        CoffeeRegion.allSearchTerms,
        wordBoundary = false,
    )

    private val varietyRegex = buildRegex(CoffeeVariety.allSearchTerms)

    private val processRegex = buildRegex(CoffeeProcessType.allSearchTerms)

    private val altitudeRegex = Regex(
        """(\d{3,4}\s*[-–]\s*\d{3,4}\s*(?:m\.?a\.?s\.?l\.?|meters?|masl|m)\b|\d{3,4}\s*(?:m\.?a\.?s\.?l\.?|meters?|masl|m)\b)""",
        RegexOption.IGNORE_CASE,
    )

    private val roastLevelRegex = buildRegex(CoffeeRoastLevel.allSearchTerms)

    private val tastingNotesLabelRegex = Regex(
        """(?:tasting\s+notes|cupping\s+notes|notes|flavor|flavour|tastes?\s+like|chuťové?\s+poznámky|chuť|geschmacksnoten|geschmack|notas?\s+de\s+cata|note\s+di\s+degustazione)\s*:\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )

    // Matches SHORT lines with 3+ comma-separated words (likely flavor descriptors)
    // Max ~80 chars to avoid matching long prose paragraphs
    private val commaFlavorLineRegex = Regex(
        """^([\w\s\u00C0-\u024F]+,\s*[\w\s\u00C0-\u024F]+(?:,\s*[\w\s\u00C0-\u024F]+)+)$""",
    )

    // Matches lines using alternative delimiters: · • | /
    private val altDelimiterFlavorRegex = Regex(
        """^([\w\s\u00C0-\u024F]+[·•|/]\s*[\w\s\u00C0-\u024F]+(?:\s*[·•|/]\s*[\w\s\u00C0-\u024F]+)*)$""",
    )

    private val weightRegex = Regex(
        """(?<!\d)\b(\d{1,4}(?:[.,]\d{1,2})?)\s*(?:kg|kilogram[s]?|lb[s]?|pound[s]?|g|gram[s]?|oz|ounce[s]?)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Matches common roaster label patterns like "ROASTERY", "COFFEE ROASTERS", "KAFFEERÖSTEREI"
    private val roasteryLabelRegex = Regex(
        """([\w\s':.\u00C0-\u024F]+(?:roaster[sy]?|coffee\s*roaster[sy]?|rösterei|pražírna|torrefazione|torréfacteur|tostador|palarnia))\b""",
        RegexOption.IGNORE_CASE,
    )

    // Matches farm/producer/estate labels
    private val farmLabelRegex = Regex(
        """(?:farm|finca|producer|estate|fazenda|hacienda|producent|statek|gut|plantáž|plantation)\s*[:.]?\s*(.+)""",
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

    fun extractFields(rawText: String): OcrExtractionResult {
        val fullText = rawText.trim()
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Try full country name first, then abbreviations
        val origin = countryRegex.find(fullText)?.value
            ?: abbreviationRegex.find(fullText)?.let { match ->
                CoffeeOrigin.abbreviationMap[match.value.uppercase()]
            }
        val region = regionRegex.find(fullText)?.value
        val variety = extractAll(fullText, varietyRegex)
        val processType = processRegex.find(fullText)?.value
        val altitude = altitudeRegex.find(fullText)?.value
        val roastLevel = extractAllRoastLevels(fullText)
        val labeledDates = extractLabeledDates(fullText)
        val tastingNotes = extractTastingNotes(lines)
        val weight = extractWeight(fullText)
        val roaster = extractRoaster(lines)
        val farm = extractFarm(lines)

        // Derive name from origin + region as fallback for text-only extraction
        val name = listOfNotNull(origin, region).joinToString(" ").ifEmpty { null }

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
            roaster = roaster,
        )
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
            // Check alternative delimiters: · • | /
            val altMatch = altDelimiterFlavorRegex.find(line)
            if (altMatch != null && line.length <= 80) {
                // Normalize delimiters to commas for consistency
                return altMatch.groupValues[1]
                    .replace(Regex("""[·•|/]"""), ",")
                    .split(",").joinToString(", ") { it.trim() }
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

    private fun extractRoaster(lines: List<String>): String? {
        // Look for lines containing roastery-related keywords
        val match = roasteryLabelRegex.find(lines.joinToString("\n"))
        if (match != null) {
            return match.groupValues[1].trim()
                .replace(Regex("""\s+"""), " ")
        }
        return null
    }

    private fun extractFarm(lines: List<String>): String? {
        for (line in lines) {
            val match = farmLabelRegex.find(line)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private val roastLabelRegex = Regex(
        """(?:(?:datum\s+)?(?:roast(?:ed)?|pražen[íoá]|geröst(?:et)?|tostado|torrado|tostato|torréfié))\s*(?:on|date|:)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val expiryLabelRegex = Regex(
        """(?:(?:best\s*before|use\s*by|expir(?:y|es?|ation)|consume\s*before|BB|EXP|MHD|spotřebujte\s*do|nejlépe\s*do|datum\s+minimální\s+trvanlivosti|mindestens\s*haltbar|à\s*consommer\s*avant)\s*(?:date)?)\s*[:.]?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private data class LabeledDates(val roastDate: String?, val expiryDate: String?)

    private fun extractLabeledDates(text: String): LabeledDates {
        var roastDate: String? = null
        var expiryDate: String? = null

        // Try labeled dates first (label immediately preceding a date pattern)
        for (line in text.lines()) {
            val trimmed = line.trim()
            // Check for expiry label + date
            if (expiryDate == null) {
                val expiryLabel = expiryLabelRegex.find(trimmed)
                if (expiryLabel != null) {
                    val afterLabel = trimmed.substring(expiryLabel.range.last + 1)
                    for (pattern in datePatterns) {
                        val match = pattern.find(afterLabel)
                        if (match != null) { expiryDate = match.value; break }
                    }
                }
            }
            // Check for roast label + date
            if (roastDate == null) {
                val roastLabel = roastLabelRegex.find(trimmed)
                if (roastLabel != null) {
                    val afterLabel = trimmed.substring(roastLabel.range.last + 1)
                    for (pattern in datePatterns) {
                        val match = pattern.find(afterLabel)
                        if (match != null) { roastDate = match.value; break }
                    }
                }
            }
        }

        // Fallback: first unlabeled date → roastDate (most common on specialty bags)
        if (roastDate == null && expiryDate == null) {
            roastDate = extractFirstDate(text)
        }

        return LabeledDates(roastDate, expiryDate)
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

            // Signal 5: Known name match
            val matchesKnownName = knownNames.any { known ->
                text.equals(known, ignoreCase = true) ||
                    text.contains(known, ignoreCase = true) ||
                    known.contains(text, ignoreCase = true)
            }
            if (matchesKnownName) score += 50f

            // Signal 6: Contains roastery keyword
            val hasRoasteryKeyword = roasteryLabelRegex.containsMatchIn(text)
            if (hasRoasteryKeyword) score += 30f

            // Signal 7: Short text (1-4 words) — more likely a name/brand
            val wordCount = text.split(Regex("""\s+""")).size
            if (wordCount in 1..4) score += 5f

            CandidateScore(
                block = block,
                score = score,
                isKnownRoaster = matchesKnownRoaster || hasRoasteryKeyword,
                isKnownName = matchesKnownName,
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
    ): OcrExtractionResult {
        if (blocks.isEmpty()) return OcrExtractionResult()

        val fullText = blocks.joinToString("\n") { it.text }
        val baseResult = extractFields(fullText)

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

        val scored = scoreCandidateBlocks(candidates, maxHeight, knownRoasters, knownNames)

        val keywordRoaster = baseResult.roaster
        val roaster: String?
        val name: String?

        if (keywordRoaster != null) {
            // Roaster found by keyword — pick name from highest-scoring non-roaster
            roaster = keywordRoaster
            name = scored.firstOrNull {
                !it.block.text.contains(keywordRoaster, ignoreCase = true)
            }?.block?.text?.trim()
        } else {
            // No keyword roaster — use scoring
            val roasterCandidate = scored.firstOrNull { it.isKnownRoaster }
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
