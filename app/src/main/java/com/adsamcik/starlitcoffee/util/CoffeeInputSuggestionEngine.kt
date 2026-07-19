package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import java.util.Locale

private const val NO_RECENCY = Int.MAX_VALUE
private const val MIN_FUZZY_LENGTH = 4
private const val LONG_FUZZY_LENGTH = 6

data class CoffeeInputSuggestion(
    val value: String,
    val aliases: List<String> = emptyList(),
    val recencyRank: Int? = null,
)

data class SuggestionAcceptance(
    val value: String,
    val cursorIndex: Int,
)

object CoffeeInputSuggestionEngine {

    fun merge(
        recentValues: List<String>,
        libraryValues: List<CoffeeInputSuggestion>,
    ): List<CoffeeInputSuggestion> {
        val merged = linkedMapOf<String, CoffeeInputSuggestion>()

        recentValues.forEachIndexed { index, value ->
            val trimmed = value.trim()
            val key = normalized(trimmed)
            if (key.isNotBlank() && key !in merged) {
                merged[key] = CoffeeInputSuggestion(trimmed, recencyRank = index)
            }
        }
        libraryValues.forEach { suggestion ->
            val trimmed = suggestion.value.trim()
            val key = normalized(trimmed)
            if (key.isBlank()) return@forEach

            val surfaceKeys = (listOf(trimmed) + suggestion.aliases)
                .mapTo(mutableSetOf(), ::normalized)
            val matchingKeys = merged.entries.filter { (_, existing) ->
                (listOf(existing.value) + existing.aliases)
                    .asSequence()
                    .map(::normalized)
                    .any(surfaceKeys::contains)
            }.map { it.key }
            val existingKey = matchingKeys.firstOrNull() ?: key
            val existing = merged[existingKey]
            merged[existingKey] = if (existing == null) {
                suggestion.copy(value = trimmed, aliases = suggestion.aliases.distinctNormalized())
            } else {
                val otherMatches = matchingKeys.drop(1).mapNotNull(merged::get)
                existing.copy(
                    aliases = (
                        existing.aliases +
                            otherMatches.flatMap { listOf(it.value) + it.aliases } +
                            suggestion.value +
                            suggestion.aliases
                        )
                        .filterNot { normalized(it) == normalized(existing.value) }
                        .distinctNormalized(),
                    recencyRank = (listOf(existing) + otherMatches)
                        .mapNotNull { it.recencyRank }
                        .plus(listOfNotNull(suggestion.recencyRank))
                        .minOrNull(),
                )
            }
            matchingKeys.drop(1).forEach(merged::remove)
        }

        return merged.values.toList()
    }

    fun resolveCanonicalOrigin(
        input: String,
        vocabularyOrigins: List<CoffeeVocabularyEntry>,
        locale: Locale,
    ): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val canonicalKey = CoffeeMetadataNormalizer.normalizeField("origin", trimmed, locale)?.canonicalKey
        if (canonicalKey != null) {
            return CoffeeOrigin.Known.entries
                .firstOrNull { it.name == canonicalKey }
                ?.displayName
        }
        val normalizedInput = normalized(trimmed)
        return vocabularyOrigins.firstOrNull { entry ->
            entry.allTerms.any { normalized(it) == normalizedInput }
        }?.value
    }

    fun rank(
        input: String,
        suggestions: List<CoffeeInputSuggestion>,
        maxResults: Int = 6,
        multiValue: Boolean = false,
        cursorIndex: Int = input.length,
    ): List<String> {
        if (maxResults <= 0) return emptyList()
        val query = normalized(activeToken(input, multiValue, cursorIndex))

        return suggestions
            .asSequence()
            .mapIndexedNotNull { index, suggestion ->
                scoreSuggestion(query, suggestion, index)
            }
            .sortedWith(
                compareBy<ScoredSuggestion>(
                    { it.matchRank },
                    { it.fuzzyDistance },
                    { it.suggestion.recencyRank ?: NO_RECENCY },
                    { it.sourceIndex },
                    { normalized(it.suggestion.value) },
                ),
            )
            .map { it.suggestion.value }
            .distinctBy(::normalized)
            .take(maxResults)
            .toList()
    }

    fun accept(
        currentValue: String,
        suggestion: String,
        multiValue: Boolean,
        cursorIndex: Int = currentValue.length,
    ): String = acceptWithSelection(
        currentValue = currentValue,
        suggestion = suggestion,
        multiValue = multiValue,
        cursorIndex = cursorIndex,
    ).value

    fun acceptWithSelection(
        currentValue: String,
        suggestion: String,
        multiValue: Boolean,
        cursorIndex: Int = currentValue.length,
    ): SuggestionAcceptance {
        val trimmedSuggestion = suggestion.trim()
        if (!multiValue) {
            return SuggestionAcceptance(trimmedSuggestion, trimmedSuggestion.length)
        }

        val activeBounds = activeTokenBounds(currentValue, cursorIndex)
        val segments = tokenSegments(currentValue)
        val activeIndex = segments.indexOfFirst { it.start == activeBounds.first }
            .takeIf { it >= 0 }
            ?: segments.lastIndex
        val duplicateIndex = segments.indexOfFirst { segment ->
            segment.index != activeIndex &&
                normalized(segment.value) == normalized(trimmedSuggestion)
        }.takeIf { it >= 0 }

        val resultTokens = mutableListOf<String>()
        var selectedResultIndex = -1
        segments.forEach { segment ->
            when {
                segment.index == activeIndex && duplicateIndex != null -> Unit
                segment.index == activeIndex -> {
                    selectedResultIndex = resultTokens.size
                    resultTokens += trimmedSuggestion
                }
                segment.value.isNotBlank() -> {
                    if (segment.index == duplicateIndex) {
                        selectedResultIndex = resultTokens.size
                    }
                    resultTokens += segment.value
                }
            }
        }
        if (selectedResultIndex < 0) {
            selectedResultIndex = resultTokens.lastIndex.coerceAtLeast(0)
        }
        val acceptedValue = resultTokens.joinToString(", ")
        val acceptedCursor = resultTokens
            .take(selectedResultIndex)
            .sumOf { it.length + 2 } + resultTokens.getOrElse(selectedResultIndex) { "" }.length
        return SuggestionAcceptance(acceptedValue, acceptedCursor.coerceIn(0, acceptedValue.length))
    }

    private fun scoreSuggestion(
        query: String,
        suggestion: CoffeeInputSuggestion,
        sourceIndex: Int,
    ): ScoredSuggestion? {
        if (query.isBlank()) {
            return ScoredSuggestion(
                suggestion = suggestion,
                matchRank = if (suggestion.recencyRank == null) 1 else 0,
                fuzzyDistance = 0,
                sourceIndex = sourceIndex,
            )
        }

        val bestScore = (listOf(suggestion.value) + suggestion.aliases)
            .asSequence()
            .map(::normalized)
            .filter { it.isNotBlank() }
            .mapNotNull { scoreTerm(query, it) }
            .minWithOrNull(compareBy<TermScore>({ it.matchRank }, { it.fuzzyDistance }))
            ?: return null

        return ScoredSuggestion(
            suggestion = suggestion,
            matchRank = bestScore.matchRank,
            fuzzyDistance = bestScore.fuzzyDistance,
            sourceIndex = sourceIndex,
        )
    }

    private fun scoreTerm(query: String, term: String): TermScore? = when {
        term == query -> TermScore(matchRank = 0)
        term.startsWith(query) -> TermScore(matchRank = 1)
        term.split(' ').any { it.startsWith(query) } -> TermScore(matchRank = 2)
        term.contains(query) -> TermScore(matchRank = 3)
        else -> fuzzyScore(query, term)
    }

    private fun fuzzyScore(query: String, term: String): TermScore? {
        return term.split(' ')
            .asSequence()
            .mapNotNull { word ->
                val comparison = if (word.length > query.length) word.take(query.length) else word
                val shorterLength = minOf(query.length, comparison.length)
                if (shorterLength < MIN_FUZZY_LENGTH) return@mapNotNull null
                val maxDistance = if (shorterLength >= LONG_FUZZY_LENGTH) 2 else 1
                val distance = FuzzyMatcher.levenshteinDistance(query, comparison)
                distance.takeIf { it <= maxDistance }
            }
            .minOrNull()
            ?.let { distance -> TermScore(matchRank = 4, fuzzyDistance = distance) }
    }

    private fun activeToken(
        input: String,
        multiValue: Boolean,
        cursorIndex: Int,
    ): String {
        if (!multiValue) return input.trim()
        val bounds = activeTokenBounds(input, cursorIndex)
        return input.substring(bounds).trim()
    }

    private fun activeTokenBounds(input: String, cursorIndex: Int): IntRange {
        val cursor = cursorIndex.coerceIn(0, input.length)
        val start = input.substring(0, cursor).lastIndexOf(',') + 1
        val end = input.indexOf(',', startIndex = cursor).takeIf { it >= 0 } ?: input.length
        return start until end
    }

    private fun tokenSegments(input: String): List<TokenSegment> {
        val segments = mutableListOf<TokenSegment>()
        var start = 0
        var index = 0
        while (start <= input.length) {
            val end = input.indexOf(',', start).takeIf { it >= 0 } ?: input.length
            segments += TokenSegment(
                index = index++,
                start = start,
                value = input.substring(start, end).trim(),
            )
            if (end == input.length) break
            start = end + 1
        }
        return segments
    }

    private fun normalized(value: String): String =
        CoffeeMetadataNormalizer.normalizeSearch(value).trim()

    private fun List<String>.distinctNormalized(): List<String> =
        distinctBy(::normalized)

    private data class ScoredSuggestion(
        val suggestion: CoffeeInputSuggestion,
        val matchRank: Int,
        val fuzzyDistance: Int,
        val sourceIndex: Int,
    )

    private data class TermScore(
        val matchRank: Int,
        val fuzzyDistance: Int = 0,
    )

    private data class TokenSegment(
        val index: Int,
        val start: Int,
        val value: String,
    )
}
