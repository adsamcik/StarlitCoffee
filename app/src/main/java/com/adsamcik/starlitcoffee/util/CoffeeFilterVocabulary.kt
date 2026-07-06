package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single curated filter value with OCR/spelling [aliases]. Sourced from the
 * bundled `coffee_filter_vocabulary.json` asset (specialty-roaster filter facets
 * plus authoritative catalogs — WCR varieties, SCA flavor wheel, producing
 * countries). [country] is optional review metadata for regions.
 */
@Serializable
data class CoffeeVocabularyEntry(
    val value: String,
    val aliases: List<String> = emptyList(),
    val country: String? = null,
) {
    /** Canonical value plus aliases — every surface form worth matching against OCR. */
    val allTerms: List<String> get() = listOf(value) + aliases
}

/**
 * Curated per-field coffee vocabulary used to seed [KnownFieldValues] hints for
 * the on-device LLM. This is a global reference vocabulary (independent of the
 * user's own bags); [CoffeeVocabularyMatcher] fuzzy-matches it against OCR text
 * to surface the most likely values per field.
 *
 * Loaded from `assets/coffee_filter_vocabulary.json` via [CoffeeFilterVocabularyLoader],
 * mirroring the `GrinderDataSource` asset-loading convention. Unknown JSON keys
 * (e.g. `_meta`) are ignored.
 */
@Serializable
data class CoffeeFilterVocabulary(
    val origins: List<CoffeeVocabularyEntry> = emptyList(),
    val regions: List<CoffeeVocabularyEntry> = emptyList(),
    val varieties: List<CoffeeVocabularyEntry> = emptyList(),
    val processTypes: List<CoffeeVocabularyEntry> = emptyList(),
    val roastLevels: List<CoffeeVocabularyEntry> = emptyList(),
    val tastingNotes: List<CoffeeVocabularyEntry> = emptyList(),
) {
    val isEmpty: Boolean
        get() = origins.isEmpty() && regions.isEmpty() && varieties.isEmpty() &&
            processTypes.isEmpty() && roastLevels.isEmpty() && tastingNotes.isEmpty()

    companion object {
        val EMPTY = CoffeeFilterVocabulary()
    }
}

/**
 * Loads and caches [CoffeeFilterVocabulary] from the bundled asset. A missing or
 * malformed asset is logged and degraded to [CoffeeFilterVocabulary.EMPTY] so the
 * scan pipeline continues without vocabulary hints rather than failing the scan.
 */
object CoffeeFilterVocabularyLoader {
    private const val ASSET_NAME = "coffee_filter_vocabulary.json"
    private const val TAG = "CoffeeFilterVocab"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var instance: CoffeeFilterVocabulary? = null

    fun getInstance(context: Context): CoffeeFilterVocabulary =
        instance ?: synchronized(this) {
            instance ?: load(context.applicationContext).also { instance = it }
        }

    private fun load(context: Context): CoffeeFilterVocabulary =
        try {
            val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            json.decodeFromString<CoffeeFilterVocabulary>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $ASSET_NAME; scan will run without vocabulary hints", e)
            CoffeeFilterVocabulary.EMPTY
        }
}
