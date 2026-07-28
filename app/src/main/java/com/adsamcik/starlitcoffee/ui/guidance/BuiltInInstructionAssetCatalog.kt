package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId

/**
 * Production source for locally packaged instructional illustrations.
 *
 * It is deliberately empty until an illustration has its local drawable,
 * localized companion and alt text, provenance, and recorded approval. An
 * empty source means a required lookup remains unavailable; it is not a claim
 * that a profile's visual curriculum is complete. In particular, P1 release
 * eligibility remains governed by its explicit planned-asset gate.
 */
object BuiltInInstructionAssetCatalog {
    val catalog: InstructionAssetCatalog = InstructionAssetCatalog(emptyList())
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
