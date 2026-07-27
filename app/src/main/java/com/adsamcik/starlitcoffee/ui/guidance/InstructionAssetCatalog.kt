package com.adsamcik.starlitcoffee.ui.guidance

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import java.lang.reflect.Modifier
import java.time.LocalDate

/**
 * Display density for the shared Learn and live-brew guidance content.
 *
 * These values only choose how much supporting material is visible. They must
 * never change the recipe or stage engine, and safety-critical content is
 * explicitly marked as always visible below.
 */
enum class GuidancePresentationLevel {
    FULL,
    CONCISE,
    FOCUSED,
    UTILITIES_ONLY,
    CUSTOM,
}

data class GuidanceVisibilityPolicy(
    val visibleIn: Set<GuidancePresentationLevel> = GuidancePresentationLevel.entries.toSet(),
    val alwaysVisible: Boolean = false,
) {
    init {
        require(visibleIn.isNotEmpty() || alwaysVisible) {
            "Guidance must be visible in at least one presentation level"
        }
    }

    fun isVisibleAt(level: GuidancePresentationLevel): Boolean = alwaysVisible || level in visibleIn
}

/** A stable suffix used to distinguish reviewed variants of the same action. */
@JvmInline
value class InstructionAssetVariant(val value: String) {
    init {
        require(value.matches(STABLE_ID_PATTERN)) {
            "Instruction asset variants must use lower_snake_case: $value"
        }
    }

    companion object {
        val DEFAULT = InstructionAssetVariant("default")
    }
}

enum class InstructionAssetReviewStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    RETIRED,
}

data class InstructionAssetReview(
    val status: InstructionAssetReviewStatus,
    val reviewer: String? = null,
    val reviewedOn: LocalDate? = null,
    val notes: String? = null,
) {
    init {
        if (status in REVIEWED_STATUSES) {
            require(!reviewer.isNullOrBlank()) {
                "Reviewed instruction assets must record a reviewer"
            }
            requireNotNull(reviewedOn) {
                "Reviewed instruction assets must record a review date"
            }
        }
    }

    val isApproved: Boolean
        get() = status == InstructionAssetReviewStatus.APPROVED

    private companion object {
        val REVIEWED_STATUSES = setOf(
            InstructionAssetReviewStatus.APPROVED,
            InstructionAssetReviewStatus.REJECTED,
            InstructionAssetReviewStatus.RETIRED,
        )
    }
}

/** The intended delivery dimensions for one static, crop-safe illustration. */
data class InstructionAssetGeometry(
    val widthPx: Int = RECOMMENDED_WIDTH_PX,
    val heightPx: Int = RECOMMENDED_HEIGHT_PX,
) {
    init {
        require(widthPx > 0 && heightPx > 0) {
            "Instruction asset dimensions must be positive"
        }
        require(widthPx.toLong() * ASPECT_HEIGHT == heightPx.toLong() * ASPECT_WIDTH) {
            "Instruction assets must use a 4:3 composition (received ${widthPx}x${heightPx})"
        }
    }

    companion object {
        const val ASPECT_WIDTH = 4
        const val ASPECT_HEIGHT = 3
        const val RECOMMENDED_WIDTH_PX = 1024
        const val RECOMMENDED_HEIGHT_PX = 768
    }
}

data class InstructionAssetProvenance(
    val promptDocument: String,
    val promptRevision: String,
    val generatedOn: LocalDate? = null,
) {
    init {
        require(promptDocument.isNotBlank()) {
            "Instruction assets must record their prompt document"
        }
        require(promptRevision.isNotBlank()) {
            "Instruction assets must record their prompt revision"
        }
    }
}

/**
 * A compile-time-safe manifest record for one local instruction illustration.
 *
 * The app must reference [drawableRes] and [altTextRes] directly. Runtime
 * name lookup is deliberately not supported, so a missing resource is caught
 * by Android compilation. [id] is also the expected drawable resource stem,
 * which makes the file name auditable without duplicating another string.
 */
@Suppress("LongParameterList")
data class InstructionAssetRecord(
    val id: InstructionAssetId,
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId? = null,
    val stageId: StageId? = null,
    val contentId: StageContentId,
    val variant: InstructionAssetVariant = InstructionAssetVariant.DEFAULT,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val altTextRes: Int,
    @param:StringRes val companionInstructionRes: Int,
    val geometry: InstructionAssetGeometry = InstructionAssetGeometry(),
    val mandatoryForFullGuidance: Boolean,
    val safetySensitive: Boolean,
    val provenance: InstructionAssetProvenance,
    val review: InstructionAssetReview,
) {
    init {
        require(drawableRes != 0) { "Instruction assets must use a drawable resource" }
        require(altTextRes != 0) { "Instruction assets must use a localized alt-text resource" }
        require(companionInstructionRes != 0) {
            "Instruction assets must use a localized companion instruction"
        }
        require(id.value == expectedDrawableResourceName()) {
            "Instruction asset ID '${id.value}' must match expected drawable name " +
                "'${expectedDrawableResourceName()}'"
        }
    }

    fun expectedDrawableResourceName(): String = buildString {
        append("instruction_")
        append(familyId.value)
        append('_')
        append(profileId?.value ?: FAMILY_DEFAULT_PROFILE_SEGMENT)
        append('_')
        append(contentId.value)
        append('_')
        append(variant.value)
    }

    companion object {
        const val FAMILY_DEFAULT_PROFILE_SEGMENT = "family"
    }
}

/**
 * Text content is intentionally linked to illustration IDs rather than a
 * drawable. Learn and live Brew can therefore present the same content while
 * selecting their own layout and density.
 */
@Suppress("LongParameterList")
data class GuidanceContentRecord(
    val id: StageContentId,
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId? = null,
    val stageId: StageId? = null,
    @param:StringRes val primaryInstructionRes: Int,
    @param:StringRes val conciseInstructionRes: Int? = null,
    @param:StringRes val explanationRes: Int? = null,
    @param:StringRes val tipRes: Int? = null,
    @param:StringRes val warningRes: Int? = null,
    val visibility: GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(),
    val safetyCritical: Boolean = false,
    val instructionAssetId: InstructionAssetId? = null,
    val requiresVisualForFullGuidance: Boolean = false,
) {
    init {
        require(primaryInstructionRes != 0) {
            "Guidance content must include a localized primary instruction"
        }
        require(!safetyCritical || warningRes != null) {
            "Safety-critical guidance must include explicit warning text"
        }
        require(!safetyCritical || visibility.alwaysVisible) {
            "Safety-critical guidance must remain visible at every guidance level"
        }
        require(!requiresVisualForFullGuidance || instructionAssetId != null) {
            "Full guidance cannot require an illustration without an instruction asset ID"
        }
    }
}

/**
 * Immutable lookup for reviewed instruction assets. It validates metadata but
 * intentionally permits drafts so a build can surface release-readiness gaps
 * before a catalog entry becomes shippable.
 */
class InstructionAssetCatalog(
    val assets: List<InstructionAssetRecord>,
) {
    private val assetsById = assets.associateBy(InstructionAssetRecord::id)

    init {
        requireUnique(assets.map { it.id.value }, "instruction asset")
        requireUnique(assets.map { it.slot }, "instruction asset slot")
    }

    fun find(id: InstructionAssetId): InstructionAssetRecord? = assetsById[id]

    fun releaseReadiness(
        familyId: MethodFamilyId,
        profileId: BrewerProfileId? = null,
    ): InstructionAssetReleaseReadiness {
        val requiredAssets = assets.filter { asset ->
            asset.familyId == familyId &&
                (asset.profileId == null || asset.profileId == profileId) &&
                (asset.mandatoryForFullGuidance || asset.safetySensitive)
        }
        return InstructionAssetReleaseReadiness(
            familyId = familyId,
            profileId = profileId,
            unapprovedAssetIds = requiredAssets
                .filterNot { it.review.isApproved }
                .mapTo(linkedSetOf(), InstructionAssetRecord::id),
        )
    }

    private val InstructionAssetRecord.slot: InstructionAssetSlot
        get() = InstructionAssetSlot(familyId, profileId, stageId, contentId, variant)
}

data class InstructionAssetReleaseReadiness(
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId?,
    val unapprovedAssetIds: Set<InstructionAssetId>,
) {
    val isReleaseComplete: Boolean
        get() = unapprovedAssetIds.isEmpty()
}

/**
 * Validates the text-to-asset relationship once both manifests are assembled.
 * A content record references an asset ID; it never embeds a drawable ID.
 */
class GuidanceContentCatalog(
    val content: List<GuidanceContentRecord>,
    val instructionAssets: InstructionAssetCatalog,
) {
    init {
        requireUnique(content.map { it.id.value }, "guidance content")
        content.forEach(::validateAssetLink)
    }

    fun releaseReadiness(
        familyId: MethodFamilyId,
        profileId: BrewerProfileId? = null,
    ): GuidanceReleaseReadiness {
        val relevantContent = content.filter { item ->
            item.familyId == familyId && (item.profileId == null || item.profileId == profileId)
        }
        val requiredAssetIds = relevantContent
            .filter(GuidanceContentRecord::requiresVisualForFullGuidance)
            .mapNotNull(GuidanceContentRecord::instructionAssetId)
            .toSet()
        val unapprovedAssetIds = requiredAssetIds.filterTo(linkedSetOf()) { assetId ->
            instructionAssets.find(assetId)?.review?.isApproved != true
        }
        unapprovedAssetIds += instructionAssets
            .releaseReadiness(familyId, profileId).unapprovedAssetIds
        return GuidanceReleaseReadiness(
            familyId = familyId,
            profileId = profileId,
            unapprovedAssetIds = unapprovedAssetIds,
        )
    }

    private fun validateAssetLink(content: GuidanceContentRecord) {
        val assetId = content.instructionAssetId ?: return
        val asset = requireNotNull(instructionAssets.find(assetId)) {
            "Guidance content '${content.id.value}' references unknown asset '${assetId.value}'"
        }
        require(asset.familyId == content.familyId) {
            "Guidance content '${content.id.value}' and asset '${asset.id.value}' must share a family"
        }
        require(asset.profileId == null || asset.profileId == content.profileId) {
            "Guidance content '${content.id.value}' cannot use a different profile's asset"
        }
        require(asset.stageId == content.stageId) {
            "Guidance content '${content.id.value}' and asset '${asset.id.value}' must share a stage"
        }
        require(asset.contentId == content.id) {
            "Guidance content '${content.id.value}' and asset '${asset.id.value}' must share a content ID"
        }
        require(asset.companionInstructionRes == content.primaryInstructionRes) {
            "Guidance content '${content.id.value}' must share its asset's companion instruction"
        }
        require(!content.requiresVisualForFullGuidance || asset.mandatoryForFullGuidance) {
            "Required full-guidance content '${content.id.value}' must reference a mandatory asset"
        }
    }
}

data class GuidanceReleaseReadiness(
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId?,
    val unapprovedAssetIds: Set<InstructionAssetId>,
) {
    val isReleaseComplete: Boolean
        get() = unapprovedAssetIds.isEmpty()
}

data class DrawableResourceNameMismatch(
    val assetId: InstructionAssetId,
    val expectedResourceName: String,
    val actualResourceName: String?,
)

/**
 * JVM-friendly manifest check for use in tests. Android compilation proves
 * that [InstructionAssetRecord.drawableRes] exists; this verifies that the
 * explicit reference still uses the resource name derived from its stable ID.
 */
object InstructionAssetManifestValidator {
    fun findDrawableResourceNameMismatches(
        catalog: InstructionAssetCatalog,
        drawableResourceClass: Class<*>,
    ): List<DrawableResourceNameMismatch> {
        val resourceNamesById = drawableResourceClass.fields
            .asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType
            }
            .associate { field -> field.getInt(null) to field.name }

        return catalog.assets.mapNotNull { asset ->
            val actualName = resourceNamesById[asset.drawableRes]
            val expectedName = asset.expectedDrawableResourceName()
            if (actualName == expectedName) {
                null
            } else {
                DrawableResourceNameMismatch(asset.id, expectedName, actualName)
            }
        }
    }
}

private data class InstructionAssetSlot(
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId?,
    val stageId: StageId?,
    val contentId: StageContentId,
    val variant: InstructionAssetVariant,
)

private fun requireUnique(ids: List<Any>, label: String) {
    require(ids.distinct().size == ids.size) { "Duplicate $label" }
}

private val STABLE_ID_PATTERN = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
