package com.adsamcik.starlitcoffee.ai

/**
 * Validates AI-extracted fields against source OCR text to catch hallucinations.
 *
 * Each extracted field is checked for a fuzzy match in the raw OCR text:
 * - **GROUNDED**: Value (or close variant) found in the text.
 * - **INFERRED**: Value not found verbatim but matches a known semantic derivation
 *   (e.g., "Yirgacheffe" in text → origin "Ethiopia" is a valid inference).
 * - **UNGROUNDED**: No match at all — likely hallucinated, should be dropped.
 */
object GroundingChecker {

    private const val DEFAULT_SIMILARITY_THRESHOLD = 0.75

    /**
     * Known semantic inferences: if the OCR text contains the key,
     * the value is a valid inference for the given field.
     * Map structure: field name → (inferred value → OCR trigger terms).
     */
    private val KNOWN_INFERENCES: Map<String, Map<String, List<String>>> = mapOf(
        "origin" to buildMap {
            put("Ethiopia", listOf(
                "Yirgacheffe", "Sidamo", "Sidama", "Guji", "Gedeb", "Gedeo",
                "Limmu", "Djimmah", "Harrar", "Harar", "Bench Maji", "Kaffa",
                "Bale", "Arsi", "West Arsi", "Borena",
            ))
            put("Colombia", listOf(
                "Huila", "Nariño", "Cauca", "Tolima", "Antioquia", "Quindio",
                "Risaralda", "Santander", "Sierra Nevada", "Caldas",
            ))
            put("Brazil", listOf(
                "Cerrado", "Mogiana", "Sul de Minas", "Matas de Minas",
                "Chapada de Minas", "Bahia", "Espírito Santo",
            ))
            put("Kenya", listOf(
                "Nyeri", "Kiambu", "Kirinyaga", "Murang'a", "Embu", "Meru",
                "Thika", "Ruiru",
            ))
            put("Guatemala", listOf("Antigua", "Acatenango", "Atitlan", "Fraijanes"))
            put("Costa Rica", listOf("Tarrazu", "West Valley"))
            put("Honduras", listOf("Marcala", "Copan"))
            put("Peru", listOf("Cajamarca", "Chanchamayo", "San Martin", "Junin"))
            put("Indonesia", listOf(
                "Sumatra", "Mandheling", "Gayo", "Toraja", "Flores", "Bali",
            ))
            put("Rwanda", listOf("Kigali"))
            put("DR Congo", listOf("Kivu"))
            put("Burundi", listOf("Kayanza", "Ngozi"))
        },
        "processType" to mapOf(
            "Natural" to listOf("sun-dried", "sun dried", "raised beds", "dry process"),
            "Washed" to listOf("wet process", "fully washed", "café lavado"),
            "Honey" to listOf("pulped natural", "miel"),
            "Anaerobic" to listOf("anaerobic fermentation"),
        ),
    )

    /**
     * Checks each field in [result] against [ocrText] and assigns confidence.
     * Returns a new [AiExtractionResult] with the [AiExtractionResult.fieldConfidence] map populated.
     */
    fun ground(result: AiExtractionResult, ocrText: String): AiExtractionResult {
        val normalizedOcr = ocrText.lowercase()
        val confidence = mutableMapOf<String, FieldConfidence>()

        fun check(fieldName: String, value: String?) {
            if (value == null) return
            confidence[fieldName] = classifyField(fieldName, value, normalizedOcr)
        }

        check("name", result.name)
        check("roaster", result.roaster)
        check("origin", result.origin)
        check("region", result.region)
        check("farm", result.farm)
        check("variety", result.variety)
        check("altitude", result.altitude)
        check("processType", result.processType)
        check("tastingNotes", result.tastingNotes)
        check("roastLevel", result.roastLevel)
        check("roastDate", result.roastDate)
        check("weight", result.weight)

        return result.copy(fieldConfidence = confidence)
    }

    internal fun classifyField(
        fieldName: String,
        value: String,
        normalizedOcr: String,
        similarityThreshold: Double = DEFAULT_SIMILARITY_THRESHOLD,
    ): FieldConfidence {
        val normalizedValue = value.lowercase().trim()

        // Direct containment check (fast path)
        if (normalizedOcr.contains(normalizedValue)) return FieldConfidence.GROUNDED

        // Token overlap: check if individual tokens from the value appear in OCR text
        val valueTokens = normalizedValue.split(Regex("[\\s,;·•|/]+")).filter { it.length >= 3 }
        if (valueTokens.isNotEmpty()) {
            val matchedTokens = valueTokens.count { token -> normalizedOcr.contains(token) }
            val tokenOverlap = matchedTokens.toDouble() / valueTokens.size
            if (tokenOverlap >= similarityThreshold) return FieldConfidence.GROUNDED
        }

        // Fuzzy match: check against OCR text segments
        val ocrSegments = normalizedOcr.split(Regex("[\\n\\r,;·•|]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (segment in ocrSegments) {
            if (normalizedLevenshteinSimilarity(normalizedValue, segment) >= similarityThreshold) {
                return FieldConfidence.GROUNDED
            }
            // Also check if the value is a fuzzy substring of a longer segment
            if (segment.length > normalizedValue.length) {
                val windows = segment.windowed(
                    size = normalizedValue.length,
                    step = 1,
                    partialWindows = false,
                )
                if (windows.any { normalizedLevenshteinSimilarity(normalizedValue, it) >= similarityThreshold }) {
                    return FieldConfidence.GROUNDED
                }
            }
        }

        // Known inference check
        val inferences = KNOWN_INFERENCES[fieldName]
        if (inferences != null) {
            val triggers = inferences[value] ?: inferences[value.replaceFirstChar { it.uppercaseChar() }]
            if (triggers != null && triggers.any { trigger -> normalizedOcr.contains(trigger.lowercase()) }) {
                return FieldConfidence.INFERRED
            }
        }

        // Multi-value fields: check each comma-separated value individually
        if (normalizedValue.contains(",")) {
            val parts = normalizedValue.split(",").map { it.trim() }
            val groundedParts = parts.count { part ->
                normalizedOcr.contains(part) || ocrSegments.any { seg ->
                    normalizedLevenshteinSimilarity(part, seg) >= similarityThreshold
                }
            }
            if (groundedParts.toDouble() / parts.size >= 0.5) return FieldConfidence.GROUNDED
        }

        return FieldConfidence.UNGROUNDED
    }

    /**
     * Normalized Levenshtein similarity between two strings.
     * Returns 1.0 for identical strings, 0.0 for completely different.
     */
    internal fun normalizedLevenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val maxLen = maxOf(a.length, b.length)
        val distance = levenshteinDistance(a, b)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Standard Levenshtein edit distance (dynamic programming).
     */
    internal fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[m][n]
    }
}
