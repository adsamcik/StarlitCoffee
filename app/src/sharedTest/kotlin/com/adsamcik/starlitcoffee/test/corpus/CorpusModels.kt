package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Shared, Android-free model of the synthetic coffee-bag corpus.
 *
 * This is the single definition of the corpus schema consumed by BOTH the
 * pure-JVM unit tests (`src/test`) and the on-device instrumented tests
 * (`src/androidTest`). The committed corpus now uses per-fixture sidecars
 * (`*.metadata.json`, schema v2) with one image set per JSON file; the loader
 * still understands the old monolithic `corpus_metadata.json` (schema v1) for
 * ad hoc local overrides.
 *
 * The metadata uses the LLM-side field keys (`process`, `roastLevel`,
 * `isDecaf`, ...). Mapping to the app-internal field names (`processType`,
 * ...) is owned by [FieldSpec]; nothing here should hard-code that mapping.
 */
@Serializable
data class CoffeeBagCorpus(
    @SerialName("schema_version")
    val schemaVersion: Int = 2,
    val bags: List<CoffeeBagFixture> = emptyList(),
)

@Serializable
data class CoffeeBagFixture(
    val id: String,
    @SerialName("automation_ready")
    val automationReady: Boolean = true,
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
    val isTwoSided: Boolean get() = photos.front != null && photos.back != null

    /** True when at least one side image is declared. */
    val hasAnyPhoto: Boolean get() = photos.front != null || photos.back != null

    /** True when a front-side image is declared. */
    val hasFrontPhoto: Boolean get() = photos.front != null

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
    val front: String? = null,
    val back: String? = null,
)

@Serializable
private data class CoffeeBagFixtureSidecar(
    @SerialName("schema_version")
    val schemaVersion: Int = 2,
    @SerialName("fixture_type")
    val fixtureType: String = "coffee_bag",
    val id: String,
    @SerialName("automation_ready")
    val automationReady: Boolean = true,
    val photos: CoffeeBagPhotos,
    val language: List<String> = emptyList(),
    val fields: Map<String, JsonElement> = emptyMap(),
    val extras: Map<String, JsonElement> = emptyMap(),
    val notes: String? = null,
) {
    fun toFixture(): CoffeeBagFixture =
        CoffeeBagFixture(
            id = id,
            automationReady = automationReady,
            photos = photos,
            language = language,
            fields = fields,
            extras = extras,
            notes = notes,
        )
}

object CoffeeBagCorpusLoader {

    const val LEGACY_SCHEMA_VERSION: Int = 1
    const val SIDECAR_SCHEMA_VERSION: Int = 2

    private val parser: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = false
    }

    fun looksLikeCorpusDir(dir: File): Boolean =
        sidecarFiles(dir).isNotEmpty() || File(dir, LEGACY_METADATA_FILE).isFile

    fun loadAll(dir: File): CoffeeBagCorpus = load(dir, automationReadyOnly = false)

    fun loadAutomationReady(dir: File): CoffeeBagCorpus = load(dir, automationReadyOnly = true)

    fun sidecarFiles(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(SIDECAR_SUFFIX) }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun load(dir: File, automationReadyOnly: Boolean): CoffeeBagCorpus {
        val sidecars = sidecarFiles(dir)
        if (sidecars.isNotEmpty()) {
            val fixtures = sidecars
                .map { file ->
                    val sidecar = parser.decodeFromString(CoffeeBagFixtureSidecar.serializer(), file.readText())
                    require(sidecar.schemaVersion == SIDECAR_SCHEMA_VERSION) {
                        "Unsupported sidecar schema v${sidecar.schemaVersion} in ${file.name}"
                    }
                    require(sidecar.fixtureType == "coffee_bag") {
                        "Unsupported fixture_type '${sidecar.fixtureType}' in ${file.name}"
                    }
                    sidecar.toFixture()
                }
                .filter { !automationReadyOnly || it.automationReady }
                .sortedBy { it.id }
            return CoffeeBagCorpus(schemaVersion = SIDECAR_SCHEMA_VERSION, bags = fixtures)
        }

        val legacyFile = File(dir, LEGACY_METADATA_FILE)
        require(legacyFile.isFile) {
            "Synthetic coffee-bag corpus not found in ${dir.path} (expected *.metadata.json sidecars)."
        }
        val corpus = parser.decodeFromString(CoffeeBagCorpus.serializer(), legacyFile.readText())
        return if (automationReadyOnly) {
            corpus.copy(bags = corpus.bags.filter(CoffeeBagFixture::automationReady))
        } else {
            corpus
        }
    }

    private const val LEGACY_METADATA_FILE: String = "corpus_metadata.json"
    private const val SIDECAR_SUFFIX: String = ".metadata.json"
}
