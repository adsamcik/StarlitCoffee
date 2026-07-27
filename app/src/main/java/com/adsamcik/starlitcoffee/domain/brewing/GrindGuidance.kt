package com.adsamcik.starlitcoffee.domain.brewing


enum class GrindEvidenceClass {
    OFFICIAL,
    PROFESSIONAL,
    COMMUNITY,
    INFERRED,
    GENERIC,
}

enum class GenericGrindDescriptor {
    VERY_FINE,
    FINE,
    MEDIUM_FINE,
    MEDIUM,
    MEDIUM_COARSE,
    COARSE,
}

data class GrindEvidence(
    val evidenceClass: GrindEvidenceClass,
    val confidence: EvidenceConfidence,
    val sourceReferences: List<String> = emptyList(),
    val verifiedAtIsoDate: String? = null,
    val isInferred: Boolean = false,
)

data class GrindGuidanceRecord(
    val grinderId: String,
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId? = null,
    val filterStack: List<FilterProfileId>? = null,
    val recipeVariantId: RecipeVariantId? = null,
    val rangeStart: Double,
    val rangeEnd: Double,
    val suggestedStart: Double,
    val adjustmentStepSize: Double,
    val adjustmentNote: String,
    val evidence: GrindEvidence,
) {
    init {
        require(rangeStart <= suggestedStart && suggestedStart <= rangeEnd) {
            "Suggested grind must be inside its range"
        }
        require(adjustmentStepSize > 0.0) { "Adjustment step must be positive" }
    }
}

data class GrindGuidanceRequest(
    val grinderId: String?,
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId?,
    val filterStack: List<FilterProfileId>?,
    val recipeVariantId: RecipeVariantId? = null,
    val equipmentIsKnown: Boolean,
    val genericDescriptor: GenericGrindDescriptor,
)

sealed interface GrindGuidanceResolution {
    data class Specific(val record: GrindGuidanceRecord) : GrindGuidanceResolution

    data class Generic(val descriptor: GenericGrindDescriptor) : GrindGuidanceResolution
}

/**
 * Resolves only evidence-compatible recommendations. In particular, an unknown
 * equipment configuration cannot inherit an apparently precise family value.
 */
object GrindGuidanceResolver {
    fun resolve(
        request: GrindGuidanceRequest,
        records: List<GrindGuidanceRecord>,
    ): GrindGuidanceResolution {
        val grinderId = request.grinderId ?: return GrindGuidanceResolution.Generic(request.genericDescriptor)
        if (!request.equipmentIsKnown) return GrindGuidanceResolution.Generic(request.genericDescriptor)

        val candidates = records.filter { record ->
            record.grinderId == grinderId && record.methodFamilyId == request.methodFamilyId
        }
        val selected = candidates.maxByOrNull { score(it, request) }
            ?.takeIf { score(it, request) >= MINIMUM_MATCH_SCORE }
        return selected?.let(GrindGuidanceResolution::Specific)
            ?: GrindGuidanceResolution.Generic(request.genericDescriptor)
    }

    private fun score(
        record: GrindGuidanceRecord,
        request: GrindGuidanceRequest,
    ): Int {
        var score = FAMILY_MATCH_SCORE
        score += scopedMatchScore(record.brewerProfileId, request.brewerProfileId, PROFILE_MATCH_SCORE)
        score += scopedMatchScore(record.recipeVariantId, request.recipeVariantId, VARIANT_MATCH_SCORE)
        score += filterMatchScore(record.filterStack, request.filterStack)
        return score
    }

    private fun <T> scopedMatchScore(
        recordValue: T?,
        requestValue: T?,
        exactScore: Int,
    ): Int = when {
        recordValue == null -> 0
        requestValue == null -> INCOMPATIBLE_SCOPE_SCORE
        recordValue == requestValue -> exactScore
        else -> INCOMPATIBLE_SCOPE_SCORE
    }

    private fun filterMatchScore(
        recordFilters: List<FilterProfileId>?,
        requestFilters: List<FilterProfileId>?,
    ): Int = when {
        recordFilters == null -> 0
        requestFilters == null -> INCOMPATIBLE_SCOPE_SCORE
        recordFilters == requestFilters -> FILTER_MATCH_SCORE
        else -> INCOMPATIBLE_SCOPE_SCORE
    }

    private const val FAMILY_MATCH_SCORE = 10
    private const val PROFILE_MATCH_SCORE = 20
    private const val VARIANT_MATCH_SCORE = 10
    private const val FILTER_MATCH_SCORE = 20
    private const val INCOMPATIBLE_SCOPE_SCORE = -100
    private const val MINIMUM_MATCH_SCORE = FAMILY_MATCH_SCORE
}
