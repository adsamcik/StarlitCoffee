package com.adsamcik.starlitcoffee.util

object FuzzyMatcher {

    private data class MatchResult(
        val candidate: String,
        val distance: Int,
        val isExactLength: Boolean,
        val lengthDelta: Int,
    )

    /** Standard Levenshtein distance between two strings (case-insensitive). */
    fun levenshteinDistance(a: String, b: String): Int {
        val left = a.lowercase()
        val right = b.lowercase()

        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val substitutionCost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitutionCost,
                )
            }

            val temp = previous
            previous = current
            current = temp
        }

        return previous[right.length]
    }

    /**
     * Returns the best fuzzy match from [candidates] within [maxDistance].
     * Only considers candidates where length >= [minLength] to avoid short-word false positives.
     * Returns null if no match is within threshold.
     */
    fun fuzzyMatch(
        input: String,
        candidates: List<String>,
        maxDistance: Int = 2,
        minLength: Int = 5,
    ): String? = bestMatch(input, candidates, maxDistance, minLength)?.candidate

    /**
     * Tokenizes text into words and multi-word phrases (up to 3 words),
     * then fuzzy-matches each token against candidates.
     * Returns the best match found across all tokens, or null.
     */
    fun fuzzyMatchInText(
        text: String,
        candidates: List<String>,
        maxDistance: Int = 2,
        minLength: Int = 5,
    ): String? {
        val words = WORD_REGEX.findAll(text)
            .map { it.value }
            .toList()
        if (words.isEmpty()) return null

        var best: MatchResult? = null

        for (windowSize in 1..3) {
            for (startIndex in 0..words.size - windowSize) {
                val phrase = words.subList(startIndex, startIndex + windowSize).joinToString(" ")
                val match = bestMatch(phrase, candidates, maxDistance, minLength) ?: continue
                if (isBetterMatch(match, best)) {
                    best = match
                }
            }
        }

        return best?.candidate
    }

    @Suppress(
        // Each loop iteration has two short-circuit guards: minimum length
        // and maximum edit distance. Both are proper preconditions before
        // the expensive `levenshteinDistance` call; combining them would
        // duplicate the distance computation.
        "LoopWithTooManyJumpStatements",
    )
    private fun bestMatch(
        input: String,
        candidates: List<String>,
        maxDistance: Int,
        minLength: Int,
    ): MatchResult? {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) return null

        var best: MatchResult? = null

        for (candidate in candidates) {
            val normalizedCandidate = candidate.trim()
            if (normalizedCandidate.length < minLength) continue

            val distance = levenshteinDistance(normalizedInput, normalizedCandidate)
            if (distance > maxDistance) continue

            val match = MatchResult(
                candidate = candidate,
                distance = distance,
                isExactLength = normalizedInput.length == normalizedCandidate.length,
                lengthDelta = kotlin.math.abs(normalizedInput.length - normalizedCandidate.length),
            )

            if (isBetterMatch(match, best)) {
                best = match
            }
        }

        return best
    }

    private fun isBetterMatch(candidate: MatchResult, currentBest: MatchResult?): Boolean {
        if (currentBest == null) return true
        return when {
            candidate.distance != currentBest.distance -> candidate.distance < currentBest.distance
            candidate.isExactLength != currentBest.isExactLength -> candidate.isExactLength
            else -> candidate.lengthDelta < currentBest.lengthDelta
        }
    }

    private val WORD_REGEX = Regex("""[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)?""")
}
