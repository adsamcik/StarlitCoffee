package com.adsamcik.starlitcoffee.ui.guidance

import android.content.Context
import android.util.Log
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class P1ExactLocalizationStatus {
    REVIEWED,
    PREVIEW,
    ;

    companion object {
        fun fromEncoded(value: String): P1ExactLocalizationStatus = when (value) {
            "approved" -> REVIEWED
            "preview" -> PREVIEW
            else -> throw IllegalArgumentException("Unknown exact-guidance localization status: $value")
        }
    }
}

enum class BrewingEnglishReferencePolicy(val encodedValue: String) {
    GLOSSARY_AND_SEARCH("glossary_and_search"),
    ESTABLISHED_LOCAL_USAGE("established_local_usage"),
    CONTEXTUAL_FIRST_OCCURRENCE("contextual_first_occurrence"),
    CONTEXTUAL_ADVANCED_GUIDANCE("contextual_advanced_guidance"),
    CONTEXTUAL_WHEN_RELEVANT("contextual_when_relevant"),
    REGION_SPECIFIC("region_specific"),
    SUPPRESS_USE_DESCRIPTION("suppress_use_description"),
    SUPPRESS_PENDING_REVIEW("suppress_pending_review"),
    ;

    val exposesReference: Boolean
        get() = this !in setOf(
            ESTABLISHED_LOCAL_USAGE,
            SUPPRESS_USE_DESCRIPTION,
            SUPPRESS_PENDING_REVIEW,
        )

    companion object {
        fun fromEncoded(value: String): BrewingEnglishReferencePolicy =
            entries.firstOrNull { policy -> policy.encodedValue == value }
                ?: throw IllegalArgumentException("Unknown English-reference policy: $value")
    }
}

data class BrewingTerminologyReference(
    val conceptId: String,
    val preferredLocal: String,
    val canonicalEnglish: String,
    val englishReferencePolicy: BrewingEnglishReferencePolicy =
        BrewingEnglishReferencePolicy.GLOSSARY_AND_SEARCH,
    val acceptedAliases: List<String> = emptyList(),
)

data class BrewingTerminologyUiCopy(
    val showEnglishTerms: String,
    val hideEnglishTerms: String,
    val heading: String,
)

class P1ExactTerminologyCatalog internal constructor(
    val localeTag: String,
    val localizationStatus: P1ExactLocalizationStatus = P1ExactLocalizationStatus.REVIEWED,
    val uiCopy: BrewingTerminologyUiCopy,
    referencesByContentId: Map<StageContentId, List<BrewingTerminologyReference>>,
) {
    private val referencesByContentId = referencesByContentId.toMap()

    val hasDistinctEnglishReferences: Boolean = referencesByContentId.values
        .flatten()
        .any(BrewingTerminologyReference::isDistinctFromEnglish)

    fun referencesFor(contentId: StageContentId): List<BrewingTerminologyReference> =
        referencesByContentId[contentId]
            .orEmpty()
            .filter(BrewingTerminologyReference::isDistinctFromEnglish)
}

private fun BrewingTerminologyReference.isDistinctFromEnglish(): Boolean =
    englishReferencePolicy.exposesReference &&
        preferredLocal.trim().lowercase() != canonicalEnglish.trim().lowercase()

sealed interface BuiltInP1ExactTerminologyLoadResult {
    data class Loaded(
        val catalog: P1ExactTerminologyCatalog,
    ) : BuiltInP1ExactTerminologyLoadResult

    data class Unavailable(val reason: String) : BuiltInP1ExactTerminologyLoadResult
}

object BuiltInP1ExactTerminologyCatalog {
    const val REFERENCE_ASSET_NAME = "p1_exact_terminology_references_2026_07_27.json"
    private const val REFERENCE_SCHEMA_VERSION = 1
    private const val GLOSSARY_SCHEMA_VERSION = 2
    private val stableId = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
    private val reviewDate = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun decode(
        encodedReferences: String,
        encodedGlossary: String,
        activeLocaleTag: String,
    ): P1ExactTerminologyCatalog {
        val references = json.decodeFromString<TerminologyReferenceManifestDto>(encodedReferences)
        val glossary = json.decodeFromString<TerminologyGlossaryDto>(encodedGlossary)
        references.requireCanonicalIdentity()
        val localizationStatus = glossary.requireCanonicalIdentity(activeLocaleTag)

        val canonicalById = references.concepts.associate { concept ->
            require(concept.id.matches(stableId)) { "Invalid terminology concept ID: ${concept.id}" }
            require(concept.canonicalEnglish.isNotBlank()) {
                "Canonical English terminology cannot be blank: ${concept.id}"
            }
            concept.id to concept.canonicalEnglish
        }
        require(canonicalById.size == references.concepts.size) {
            "Duplicate canonical terminology concept ID"
        }
        val localById = glossary.terms.associate { term ->
            require(term.conceptId.matches(stableId)) {
                "Invalid localized terminology concept ID: ${term.conceptId}"
            }
            require(term.preferredLocal.isNotBlank()) {
                "Preferred localized terminology cannot be blank: ${term.conceptId}"
            }
            require(term.displayPolicy.isNotBlank()) {
                "Localized terminology display policy cannot be blank: ${term.conceptId}"
            }
            require(term.acceptedAliases.all(String::isNotBlank)) {
                "Localized terminology aliases cannot be blank: ${term.conceptId}"
            }
            require(term.acceptedAliases.distinct().size == term.acceptedAliases.size) {
                "Localized terminology aliases cannot be duplicated: ${term.conceptId}"
            }
            term.conceptId to term
        }
        require(localById.size == glossary.terms.size) {
            "Duplicate localized terminology concept ID"
        }
        require(localById.keys == canonicalById.keys) {
            "Localized terminology concepts differ from the canonical sidecar"
        }

        val referencesByContentId = references.stageReferences.associate { stage ->
            require(stage.contentId.isNotBlank()) { "Terminology stage content ID cannot be blank" }
            require(stage.conceptIds.isNotEmpty()) { "Terminology stage needs at least one concept" }
            require(stage.conceptIds.distinct().size == stage.conceptIds.size) {
                "Terminology stage concepts cannot contain duplicates: ${stage.contentId}"
            }
            require(stage.conceptIds.all(canonicalById::containsKey)) {
                "Terminology stage references an unknown concept: ${stage.contentId}"
            }
            StageContentId(stage.contentId) to stage.conceptIds.map { conceptId ->
                BrewingTerminologyReference(
                    conceptId = conceptId,
                    preferredLocal = requireNotNull(localById[conceptId]).preferredLocal,
                    canonicalEnglish = requireNotNull(canonicalById[conceptId]),
                    englishReferencePolicy = BrewingEnglishReferencePolicy.fromEncoded(
                        requireNotNull(localById[conceptId]).englishReferencePolicy,
                    ),
                    acceptedAliases = requireNotNull(localById[conceptId]).acceptedAliases,
                )
            }
        }
        require(referencesByContentId.size == references.stageReferences.size) {
            "Duplicate terminology stage content ID"
        }

        return P1ExactTerminologyCatalog(
            localeTag = glossary.locale,
            localizationStatus = localizationStatus,
            uiCopy = BrewingTerminologyUiCopy(
                showEnglishTerms = glossary.uiCopy.showEnglishTerms,
                hideEnglishTerms = glossary.uiCopy.hideEnglishTerms,
                heading = glossary.uiCopy.heading,
            ),
            referencesByContentId = referencesByContentId,
        )
    }

    private fun TerminologyReferenceManifestDto.requireCanonicalIdentity() {
        require(schemaVersion == REFERENCE_SCHEMA_VERSION) { "Terminology reference schema mismatch" }
        require(sourceSchemaVersion == BuiltInP1ExactGuidanceCatalog.SOURCE_SCHEMA_VERSION) {
            "Terminology reference source schema mismatch"
        }
        require(sourceExecutionDate == BuiltInP1ExactGuidanceCatalog.SOURCE_EXECUTION_DATE) {
            "Terminology reference source date mismatch"
        }
        require(sourceSha256 == BuiltInP1ExactGuidanceCatalog.SOURCE_SHA256) {
            "Terminology reference source hash mismatch"
        }
        require(concepts.isNotEmpty()) { "Canonical terminology concepts cannot be empty" }
        require(stageReferences.isNotEmpty()) { "Terminology stage references cannot be empty" }
    }

    private fun TerminologyGlossaryDto.requireCanonicalIdentity(
        activeLocaleTag: String,
    ): P1ExactLocalizationStatus {
        require(schemaVersion == GLOSSARY_SCHEMA_VERSION) { "Terminology glossary schema mismatch" }
        require(sourceSchemaVersion == BuiltInP1ExactGuidanceCatalog.SOURCE_SCHEMA_VERSION) {
            "Terminology glossary source schema mismatch"
        }
        require(sourceExecutionDate == BuiltInP1ExactGuidanceCatalog.SOURCE_EXECUTION_DATE) {
            "Terminology glossary source date mismatch"
        }
        require(sourceSha256 == BuiltInP1ExactGuidanceCatalog.SOURCE_SHA256) {
            "Terminology glossary source hash mismatch"
        }
        require(locale == activeLocaleTag) {
            "Terminology glossary locale does not match the active locale"
        }
        val localizationStatus = P1ExactLocalizationStatus.fromEncoded(reviewStatus)
        when (localizationStatus) {
            P1ExactLocalizationStatus.REVIEWED -> {
                require(!reviewer.isNullOrBlank()) { "Terminology glossary reviewer is missing" }
                require(reviewedOn?.matches(reviewDate) == true) {
                    "Terminology glossary review date is invalid"
                }
            }
            P1ExactLocalizationStatus.PREVIEW -> {
                require(reviewer == null && reviewedOn == null) {
                    "Preview terminology cannot claim specialist review"
                }
            }
        }
        require(
            uiCopy.showEnglishTerms.isNotBlank() &&
                uiCopy.hideEnglishTerms.isNotBlank() &&
                uiCopy.heading.isNotBlank(),
        ) {
            "Terminology glossary UI copy cannot be blank"
        }
        return localizationStatus
    }
}

object BuiltInP1ExactTerminologyLoader {
    private const val TAG = "P1ExactTerminology"
    private val cacheByLocale = mutableMapOf<String, BuiltInP1ExactTerminologyLoadResult>()

    fun getInstance(context: Context): BuiltInP1ExactTerminologyLoadResult {
        val applicationContext = context.applicationContext
        val localeTag = applicationContext.resources.configuration.locales[0].language
        return synchronized(this) {
            cacheByLocale.getOrPut(localeTag) { load(applicationContext, localeTag) }
        }
    }

    private fun load(
        context: Context,
        localeTag: String,
    ): BuiltInP1ExactTerminologyLoadResult = try {
        val encodedReferences = context.assets
            .open(BuiltInP1ExactTerminologyCatalog.REFERENCE_ASSET_NAME)
            .bufferedReader()
            .use { reader -> reader.readText() }
        val encodedGlossary = context.resources.openRawResource(R.raw.p1_exact_terminology)
            .bufferedReader()
            .use { reader -> reader.readText() }
        BuiltInP1ExactTerminologyLoadResult.Loaded(
            BuiltInP1ExactTerminologyCatalog.decode(
                encodedReferences = encodedReferences,
                encodedGlossary = encodedGlossary,
                activeLocaleTag = localeTag,
            ),
        )
    } catch (exception: IOException) {
        unavailable(exception)
    } catch (exception: RuntimeException) {
        unavailable(exception)
    }

    private fun unavailable(exception: Exception): BuiltInP1ExactTerminologyLoadResult.Unavailable {
        Log.e(TAG, "Exact P1 terminology is unavailable; references will remain hidden", exception)
        return BuiltInP1ExactTerminologyLoadResult.Unavailable(
            reason = exception.message ?: exception::class.java.simpleName,
        )
    }
}

@Serializable
private data class TerminologyReferenceManifestDto(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("source_schema_version") val sourceSchemaVersion: String,
    @SerialName("source_execution_date") val sourceExecutionDate: String,
    @SerialName("source_sha256") val sourceSha256: String,
    val concepts: List<CanonicalTerminologyConceptDto>,
    @SerialName("stage_references") val stageReferences: List<TerminologyStageReferenceDto>,
)

@Serializable
private data class CanonicalTerminologyConceptDto(
    val id: String,
    @SerialName("canonical_english") val canonicalEnglish: String,
)

@Serializable
private data class TerminologyStageReferenceDto(
    @SerialName("content_id") val contentId: String,
    @SerialName("concept_ids") val conceptIds: List<String>,
)

@Serializable
private data class TerminologyGlossaryDto(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("source_schema_version") val sourceSchemaVersion: String,
    @SerialName("source_execution_date") val sourceExecutionDate: String,
    @SerialName("source_sha256") val sourceSha256: String,
    val locale: String,
    @SerialName("review_status") val reviewStatus: String,
    val reviewer: String?,
    @SerialName("reviewed_on") val reviewedOn: String?,
    @SerialName("ui_copy") val uiCopy: TerminologyUiCopyDto,
    val terms: List<LocalizedTerminologyTermDto>,
)

@Serializable
private data class TerminologyUiCopyDto(
    @SerialName("show_english_terms") val showEnglishTerms: String,
    @SerialName("hide_english_terms") val hideEnglishTerms: String,
    val heading: String,
)

@Serializable
private data class LocalizedTerminologyTermDto(
    @SerialName("concept_id") val conceptId: String,
    @SerialName("preferred_local") val preferredLocal: String,
    @SerialName("display_policy") val displayPolicy: String,
    @SerialName("english_reference_policy") val englishReferencePolicy: String,
    @SerialName("accepted_aliases") val acceptedAliases: List<String>,
)
