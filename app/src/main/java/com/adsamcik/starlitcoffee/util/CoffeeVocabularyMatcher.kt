package com.adsamcik.starlitcoffee.util

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Selects the most likely per-field coffee values from a large OCR blob by
 * matching it against a curated [CoffeeFilterVocabulary], returning a
 * [KnownFieldValues] bundle that the on-device LLM prompt renders as
 * "candidate options per field".
 *
 * The pipeline feeds `KnownFieldValues` (origins/varieties/processes/…) into the
 * LLM prompt as "prefer a close match" guidance. Historically that bundle came
 * only from the user's existing bags, so a first-time user got no grounding. This
 * matcher grounds *every* scan against a global reference vocabulary derived from
 * real roaster filter facets + authoritative catalogs (WCR varieties, SCA flavor
 * wheel, producing countries).
 *
 * ## Design goals
 * - **Intelligent selection** — entries are ranked by match strength
 *   (whole phrase > exact token > OCR-tolerant fuzzy) then specificity, and only
 *   the top [DEFAULT_MAX_PER_FIELD] per field are surfaced.
 * - **OCR resilient** — [CoffeeMetadataNormalizer.normalizeSearch] folds
 *   diacritics/casing/punctuation; single/double-character glyph errors are
 *   absorbed by bounded Levenshtein ([FuzzyMatcher.levenshteinDistance]).
 * - **Performant & scalable** — the vocabulary is compiled once into a
 *   [VocabularyIndex] (exact-token hash map, phrase list, and length-bucketed
 *   fuzzy terms), cached per vocabulary instance. Matching a scan is then roughly
 *   O(tokens) for exact hits, O(phrases) for phrases, and fuzzy comparisons are
 *   bounded to a ±2 length window per OCR token — no per-scan re-normalization of
 *   the vocabulary and no all-pairs edit-distance scan.
 * - **Deterministic** — results are fully ordered (score, specificity, value) so
 *   output does not depend on hash iteration order.
 *
 * Matching only counts whole-token / whole-phrase hits, which keeps short common
 * words from matching mid-word noise. Hints are advisory only — the prompt tells
 * the model not to force a match — so a rare false positive is low risk.
 */
object CoffeeVocabularyMatcher {

    /** Default number of candidate values surfaced per field. */
    const val DEFAULT_MAX_PER_FIELD = 8

    /** Default number of close candidates offered per field to the refinement LLM pass. */
    const val DEFAULT_MAX_SUGGESTIONS = 5

    private const val SCORE_PHRASE = 3
    private const val SCORE_TOKEN = 2
    private const val SCORE_FUZZY = 1

    /** Lowest similarity (0..1) for a value to count as "matches somewhat" in [suggest]. */
    private const val SUGGEST_SIMILARITY_FLOOR = 0.5
    private const val CONTAINMENT_SIMILARITY = 0.9

    /** Shortest term considered for exact whole-token matching. */
    private const val MIN_TOKEN_LEN = 3

    /** Shortest term considered for edit-distance (fuzzy) matching. */
    private const val MIN_FUZZY_LEN = 5

    /** Terms at least this long tolerate up to [MAX_FUZZY_DISTANCE] edits; shorter ones tolerate 1. */
    private const val FUZZY_DISTANCE_2_MIN_LEN = 8
    private const val MAX_FUZZY_DISTANCE = 2

    // Compiled indexes are cached by vocabulary identity. The vocabulary is a
    // process-wide singleton (CoffeeFilterVocabularyLoader), so this builds once.
    private val indexCache =
        Collections.synchronizedMap(IdentityHashMap<CoffeeFilterVocabulary, VocabularyIndex>())

    /**
     * Builds a [KnownFieldValues] containing only the vocabulary-backed fields
     * (origins, regions, varieties, processTypes, roastLevels, tastingNotes),
     * each capped at [maxPerField] best matches. Non-vocabulary fields (names,
     * roasters, farms) are left empty for the caller to merge.
     */
    fun match(
        ocrText: String,
        vocabulary: CoffeeFilterVocabulary,
        maxPerField: Int = DEFAULT_MAX_PER_FIELD,
    ): KnownFieldValues {
        if (ocrText.isBlank() || vocabulary.isEmpty || maxPerField <= 0) {
            return KnownFieldValues.EMPTY
        }
        val normalized = CoffeeMetadataNormalizer.normalizeSearch(ocrText)
        if (normalized.isBlank()) return KnownFieldValues.EMPTY
        val paddedText = " $normalized "
        val tokens = normalized.split(' ').filterTo(HashSet()) { it.isNotBlank() }

        val index = indexCache.getOrPut(vocabulary) { buildIndex(vocabulary) }
        return KnownFieldValues(
            origins = index.origins.select(paddedText, tokens, maxPerField),
            regions = index.regions.select(paddedText, tokens, maxPerField),
            varieties = index.varieties.select(paddedText, tokens, maxPerField),
            processTypes = index.processTypes.select(paddedText, tokens, maxPerField),
            roastLevels = index.roastLevels.select(paddedText, tokens, maxPerField),
            tastingNotes = index.tastingNotes.select(paddedText, tokens, maxPerField),
        )
    }

    /**
     * Unions [boost] hints into [base] per vocabulary field, boost values first
     * (they were spotted on the bag currently being scanned, so they are the most
     * relevant grounding), de-duplicated case-insensitively. Non-vocabulary fields
     * on [base] pass through unchanged.
     */
    fun merge(base: KnownFieldValues, boost: KnownFieldValues): KnownFieldValues =
        base.copy(
            origins = union(boost.origins, base.origins),
            regions = union(boost.regions, base.regions),
            varieties = union(boost.varieties, base.varieties),
            processTypes = union(boost.processTypes, base.processTypes),
            roastLevels = union(boost.roastLevels, base.roastLevels),
            tastingNotes = union(boost.tastingNotes, base.tastingNotes),
        )

    /**
     * Given a single already-English field [value], returns up to [maxSuggestions]
     * close canonical values from [entries] ("matches somewhat"), ranked by
     * similarity. Unlike [match] (which scans an OCR blob for whole-token/phrase
     * hits), this compares one short value against each entry's terms using an
     * edit-distance similarity plus containment, so spelling/canonical variants
     * ("wet processed" → "Washed" via its alias, "gesha" → "Geisha") float to the
     * top. Advisory only: the caller hands these to the LLM, which may or may not
     * adopt one. Runs on the LLM's already-translated English output, so it needs
     * no multilingual vocabulary.
     */
    fun suggest(
        value: String,
        entries: List<CoffeeVocabularyEntry>,
        maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,
    ): List<String> {
        val normalizedValue = CoffeeMetadataNormalizer.normalizeSearch(value)
        if (normalizedValue.isBlank() || maxSuggestions <= 0) return emptyList()
        val scored = ArrayList<Pair<String, Double>>()
        for (entry in entries) {
            val score = entry.allTerms
                .asSequence()
                .map { CoffeeMetadataNormalizer.normalizeSearch(it) }
                .filter { it.isNotBlank() }
                .maxOfOrNull { similarity(normalizedValue, it) }
                ?: 0.0
            if (score >= SUGGEST_SIMILARITY_FLOOR) scored.add(entry.value to score)
        }
        return scored
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .take(maxSuggestions)
            .map { it.first }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLength = maxOf(a.length, b.length)
        if (maxLength == 0) return 0.0
        val editSimilarity = 1.0 - FuzzyMatcher.levenshteinDistance(a, b).toDouble() / maxLength
        val containment = if (a.contains(b) || b.contains(a)) CONTAINMENT_SIMILARITY else 0.0
        return maxOf(editSimilarity, containment)
    }

    // --- Index construction (once per vocabulary) ---

    private fun buildIndex(vocabulary: CoffeeFilterVocabulary): VocabularyIndex =
        VocabularyIndex(
            origins = buildFieldIndex(vocabulary.origins),
            regions = buildFieldIndex(vocabulary.regions),
            varieties = buildFieldIndex(vocabulary.varieties),
            processTypes = buildFieldIndex(vocabulary.processTypes),
            roastLevels = buildFieldIndex(vocabulary.roastLevels),
            tastingNotes = buildFieldIndex(vocabulary.tastingNotes),
        )

    private fun buildFieldIndex(entries: List<CoffeeVocabularyEntry>): FieldIndex {
        val exactTokens = HashMap<String, MutableList<String>>()
        val phrases = ArrayList<Phrase>()
        val fuzzyByLength = HashMap<Int, MutableList<FuzzyTerm>>()
        entries.forEach { entry ->
            entry.allTerms.forEach { term ->
                indexTerm(entry.value, term, exactTokens, phrases, fuzzyByLength)
            }
        }
        return FieldIndex(exactTokens, phrases, fuzzyByLength)
    }

    private fun indexTerm(
        value: String,
        term: String,
        exactTokens: HashMap<String, MutableList<String>>,
        phrases: MutableList<Phrase>,
        fuzzyByLength: HashMap<Int, MutableList<FuzzyTerm>>,
    ) {
        val normalized = CoffeeMetadataNormalizer.normalizeSearch(term)
        if (normalized.isBlank()) return
        if (normalized.contains(' ')) {
            phrases.add(Phrase(normalized, value))
            return
        }
        if (normalized.length >= MIN_TOKEN_LEN) {
            val values = exactTokens.getOrPut(normalized) { ArrayList() }
            if (value !in values) values.add(value)
        }
        if (normalized.length >= MIN_FUZZY_LEN) {
            fuzzyByLength.getOrPut(normalized.length) { ArrayList() }.add(FuzzyTerm(normalized, value))
        }
    }

    private class VocabularyIndex(
        val origins: FieldIndex,
        val regions: FieldIndex,
        val varieties: FieldIndex,
        val processTypes: FieldIndex,
        val roastLevels: FieldIndex,
        val tastingNotes: FieldIndex,
    )

    private class Phrase(val normalized: String, val value: String)
    private class FuzzyTerm(val normalized: String, val value: String)

    private class FieldIndex(
        private val exactTokens: Map<String, List<String>>,
        private val phrases: List<Phrase>,
        private val fuzzyByLength: Map<Int, List<FuzzyTerm>>,
    ) {
        fun select(paddedText: String, tokens: Set<String>, maxPerField: Int): List<String> {
            val best = HashMap<String, Int>()

            for (token in tokens) {
                exactTokens[token]?.forEach { value -> record(best, value, SCORE_TOKEN) }
            }
            for (phrase in phrases) {
                if (paddedText.contains(" ${phrase.normalized} ")) {
                    record(best, phrase.value, SCORE_PHRASE)
                }
            }
            if (fuzzyByLength.isNotEmpty()) {
                collectFuzzy(tokens, best)
            }
            if (best.isEmpty()) return emptyList()

            return best.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenByDescending { it.key.length }
                        .thenBy { it.key },
                )
                .take(maxPerField)
                .map { it.key }
        }

        private fun collectFuzzy(tokens: Set<String>, best: HashMap<String, Int>) {
            tokens.forEach { token ->
                if (token.length >= MIN_FUZZY_LEN) matchTokenFuzzy(token, best)
            }
        }

        private fun matchTokenFuzzy(token: String, best: HashMap<String, Int>) {
            for (length in (token.length - MAX_FUZZY_DISTANCE)..(token.length + MAX_FUZZY_DISTANCE)) {
                val maxDistance = if (length >= FUZZY_DISTANCE_2_MIN_LEN) MAX_FUZZY_DISTANCE else 1
                val bucket = fuzzyByLength[length]
                    ?.takeIf { kotlin.math.abs(token.length - length) <= maxDistance }
                    ?: continue
                matchBucket(token, bucket, maxDistance, best)
            }
        }

        private fun matchBucket(
            token: String,
            bucket: List<FuzzyTerm>,
            maxDistance: Int,
            best: HashMap<String, Int>,
        ) {
            bucket.forEach { fuzzy ->
                // Skip terms already matched at token/phrase strength — a weaker fuzzy
                // hit can't improve them, and this avoids the edit-distance call.
                if ((best[fuzzy.value] ?: 0) < SCORE_TOKEN &&
                    FuzzyMatcher.levenshteinDistance(token, fuzzy.normalized) <= maxDistance
                ) {
                    record(best, fuzzy.value, SCORE_FUZZY)
                }
            }
        }

        private fun record(best: HashMap<String, Int>, value: String, score: Int) {
            val existing = best[value]
            if (existing == null || score > existing) best[value] = score
        }
    }

    private fun union(first: List<String>, second: List<String>): List<String> {
        if (first.isEmpty()) return second
        val seen = HashSet<String>()
        val result = ArrayList<String>(first.size + second.size)
        for (value in first + second) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) continue
            if (seen.add(trimmed.lowercase())) result.add(trimmed)
        }
        return result
    }
}
