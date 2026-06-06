package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared, Android-free model of the synthetic coffee-bag corpus.
 *
 * This is the single definition of the corpus schema (`schema_version 1`)
 * consumed by BOTH the pure-JVM unit tests (`src/test`) and the on-device
 * instrumented tests (`src/androidTest`). It previously lived as two
 * drifting copies — one in `CoffeeBagCorpusExtractionTest` and one in
 * `benchmark/CorpusFixture` — which is exactly the kind of duplication that
 * lets ground-truth and parser contracts diverge silently.
 *
 * The metadata uses the LLM-side field keys (`process`, `roastLevel`,
 * `isDecaf`, ...). Mapping to the app-internal field names (`processType`,
 * ...) is owned by [FieldSpec]; nothing here should hard-code that mapping.
 */
@Serializable
data class CoffeeBagCorpus(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    val bags: List<CoffeeBagFixture> = emptyList(),
)

@Serializable
data class CoffeeBagFixture(
    val id: String,
    val photos: CoffeeBagPhotos,
    val language: List<String> = emptyList(),
    val fields: Map<String, JsonElement> = emptyMap(),
    val extras: Map<String, JsonElement> = emptyMap(),
    val notes: String? = null,
) {
    /**
     * Capture-quality tier (`Q0`..`Q4`) declared in `extras.captureTier`,
     * uppercased. Returns `null` when the bag omits it.
     */
    val captureTier: String?
        get() = (extras["captureTier"] as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()

    /** True when this bag declares both a front and a back photo. */
    val isTwoSided: Boolean get() = photos.back != null

    /**
     * Ground-truth value for [metadataKey] as a comparable string, or `null`
     * when the field is explicitly `not_visible` (JSON `null`) or absent.
     * Booleans surface as `"true"`/`"false"` to match the LLM wire contract.
     */
    fun groundTruth(metadataKey: String): String? {
        val element = fields[metadataKey] ?: return null
        if (element is JsonNull) return null
        return runCatching {
            val prim = element.jsonPrimitive
            prim.booleanOrNull?.toString() ?: prim.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** True when [metadataKey] is explicitly declared `not_visible` (JSON null). */
    fun isNotVisible(metadataKey: String): Boolean = fields[metadataKey] is JsonNull
}

@Serializable
data class CoffeeBagPhotos(
    val front: String,
    val back: String? = null,
)
