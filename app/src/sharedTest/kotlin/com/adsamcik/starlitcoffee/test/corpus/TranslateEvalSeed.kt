package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data model for the multilingual translate-eval seed
 * (`androidTest/assets/translate-eval-seed.json`). Source terms are pulled
 * verbatim from the scraped `docs/coffee-filter-vocabulary.json`; English
 * targets are curated. Lives in the shared JVM harness so both the on-device
 * eval ([com.adsamcik.starlitcoffee] instrumented test) and pure-JVM validator
 * tests can decode it.
 */
@Serializable
data class TranslateEvalSeed(
    val version: Int = 1,
    val description: String = "",
    /** Tasting notes are grouped this many per synthetic label. */
    val chunkSize: Int = 5,
    val languages: List<TranslateEvalLang> = emptyList(),
)

@Serializable
data class TranslateEvalLang(
    val lang: String,
    val name: String,
    val labels: TranslateEvalLabels,
    val sampleName: String,
    val sampleRoaster: String,
    val tastingNotes: List<TranslateEvalTerm> = emptyList(),
    val process: List<TranslateEvalTerm> = emptyList(),
    val roastLevel: List<TranslateEvalTerm> = emptyList(),
    val origin: List<TranslateEvalTerm> = emptyList(),
)

/** Localized on-label field captions used to build realistic synthetic OCR. */
@Serializable
data class TranslateEvalLabels(
    val notes: String,
    val process: String,
    val roast: String,
    val origin: String,
)

/**
 * One localized term and its acceptable canonical-English forms.
 * [loanword] marks a term that is already English-ish (`natural`, `bergamot`):
 * it can never "leak" as an untranslated source token, so it is excluded from
 * the leak metric (but still counts toward coverage). [provenance] is
 * `"supplement"` for a handful of canonical terms not present in the scrape.
 */
@Serializable
data class TranslateEvalTerm(
    val src: String,
    val en: List<String> = emptyList(),
    val loanword: Boolean = false,
    val provenance: String? = null,
)

/** Decodes the seed JSON, tolerating unknown keys. */
object TranslateEvalSeedLoader {
    private val parser = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): TranslateEvalSeed =
        parser.decodeFromString(TranslateEvalSeed.serializer(), raw)
}
