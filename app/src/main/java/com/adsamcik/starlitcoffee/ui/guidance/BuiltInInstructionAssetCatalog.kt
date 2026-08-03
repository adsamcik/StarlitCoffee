package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId

/**
 * Production source for locally packaged instructional illustrations.
 *
 * Rejected legacy inputs are intentionally absent from this catalog and from
 * app resources. Approved records may be added only after the normal review
 * and exact-stage coverage gates pass.
 */
object BuiltInInstructionAssetCatalog {
    val catalog: InstructionAssetCatalog = InstructionAssetCatalog(
        assets = P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets(),
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
