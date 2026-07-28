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
            InstructionAssetRecord(
                id = InstructionAssetId(
                    "instruction_steep_and_release_hario_switch_" +
                        "hario_switch_add_coffee_default",
                ),
                familyId = MethodFamilyId("steep_and_release"),
                profileId = BrewerProfileId("hario_switch"),
                stageId = StageId("hario_switch_add_coffee"),
                contentId = StageContentId("hario_switch_add_coffee"),
                drawableRes = R.drawable
                    .instruction_steep_and_release_hario_switch_hario_switch_add_coffee_default,
                // Reuse established copy that is already translated in every supported locale.
                altTextRes = R.string.action_brew_add_coffee,
                companionInstructionRes = R.string.action_brew_add_coffee,
                mandatoryForFullGuidance = true,
                safetySensitive = false,
                provenance = InstructionAssetProvenance(
                    promptDocument = "docs/brewing/asset-production.md",
                    promptRevision = "hario-switch-add-coffee-imagegen-v2",
                    generatedOn = LocalDate.of(2026, 7, 28),
                ),
                review = InstructionAssetReview(
                    status = InstructionAssetReviewStatus.PENDING_REVIEW,
                    notes = "Incorrect handled-cone draft rejected; corrected asset awaits expert sign-off.",
                ),
            ),
            InstructionAssetRecord(
                id = InstructionAssetId(
                    "instruction_restricted_flow_gravity_concentrate_vietnamese_phin_" +
                        "vietnamese_phin_place_on_stable_cup_default",
                ),
                familyId = MethodFamilyId("restricted_flow_gravity_concentrate"),
                profileId = BrewerProfileId("vietnamese_phin"),
                stageId = StageId("vietnamese_phin_place_on_stable_cup"),
                contentId = StageContentId("vietnamese_phin_place_on_stable_cup"),
                drawableRes = R.drawable
                    .instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_on_stable_cup_default,
                // Reuse established copy that is already translated in every supported locale.
                altTextRes = R.string.warning_brew_safety_stability,
                companionInstructionRes = R.string.warning_brew_safety_stability,
                mandatoryForFullGuidance = true,
                safetySensitive = true,
                provenance = InstructionAssetProvenance(
                    promptDocument = "docs/brewing/asset-production.md",
                    promptRevision = "phin-stable-cup-imagegen-v2",
                    generatedOn = LocalDate.of(2026, 7, 28),
                ),
                review = InstructionAssetReview(
                    status = InstructionAssetReviewStatus.PENDING_REVIEW,
                    notes = "Decorated draft rejected; corrected safety asset awaits expert sign-off.",
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
