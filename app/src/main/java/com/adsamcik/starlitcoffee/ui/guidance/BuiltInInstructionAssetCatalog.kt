package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import java.time.LocalDate

/**
 * Production source for locally packaged instructional illustrations.
 *
 * Pending production inputs live here with their real review status so their
 * resource linkage and provenance are validated by normal Android builds.
 * Only approved records are ever returned for presentation, and P1 release
 * eligibility remains governed by the complete planned-asset gate.
 */
object BuiltInInstructionAssetCatalog {
    val catalog: InstructionAssetCatalog = InstructionAssetCatalog(
        assets = listOf(
            InstructionAssetRecord(
                id = InstructionAssetId(
                    "instruction_steep_and_release_clever_style_" +
                        "clever_style_insert_and_rinse_filter_default",
                ),
                familyId = MethodFamilyId("steep_and_release"),
                profileId = BrewerProfileId("clever_style"),
                stageId = StageId("clever_style_insert_and_rinse_filter"),
                contentId = StageContentId("clever_style_insert_and_rinse_filter"),
                drawableRes = R.drawable
                    .instruction_steep_and_release_clever_style_clever_style_insert_and_rinse_filter_default,
                // Reuse established copy that is already translated in every supported locale.
                altTextRes = R.string.prep_tip_pour_over_paper,
                companionInstructionRes = R.string.prep_tip_pour_over_paper,
                mandatoryForFullGuidance = true,
                safetySensitive = false,
                provenance = InstructionAssetProvenance(
                    promptDocument = "docs/brewing/asset-production.md",
                    promptRevision = "clever-rinse-imagegen-v1",
                    generatedOn = LocalDate.of(2026, 7, 28),
                ),
                review = InstructionAssetReview(
                    status = InstructionAssetReviewStatus.PENDING_REVIEW,
                    notes = "Automated visual inspection passed; awaiting brewer-expert sign-off.",
                ),
            ),
        ),
    )
}

/**
 * Returns the canonical approved visual for one resolved content item.
 *
 * A default variant is preferred only when all matching records have the same
 * family, profile, and stage. If content IDs span scopes, returning no asset
 * is safer than showing an illustration for the wrong brewer or stage.
 */
fun InstructionAssetCatalog.findApprovedAssetForContent(
    contentId: StageContentId,
): InstructionAssetRecord? {
    val matchingAssets = assets.filter { asset -> asset.contentId == contentId }
    val canonicalAsset = matchingAssets.firstOrNull() ?: return null
    val hasAmbiguousScope = matchingAssets.any { asset ->
        asset.familyId != canonicalAsset.familyId ||
            asset.profileId != canonicalAsset.profileId ||
            asset.stageId != canonicalAsset.stageId
    }
    if (hasAmbiguousScope) return null

    val defaultAssets = matchingAssets.filter { asset ->
        asset.variant == InstructionAssetVariant.DEFAULT
    }
    return defaultAssets.singleOrNull()
        ?.takeIf { asset -> asset.review.isApproved }
        ?: matchingAssets.singleOrNull()?.takeIf { asset -> asset.review.isApproved }
}
