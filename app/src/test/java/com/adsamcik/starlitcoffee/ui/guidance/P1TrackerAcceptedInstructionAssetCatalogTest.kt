package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1TrackerAcceptedInstructionAssetCatalogTest {

    @Test
    fun `tracker candidates retain independent approvals and rejections in generic guidance`() {
        val candidates = P1TrackerAcceptedInstructionAssetCatalog.assets
        val runtimeAssets = P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets()

        assertEquals(114, candidates.size)
        assertEquals(candidates.size, runtimeAssets.size)
        assertEquals(candidates.map { it.id }, runtimeAssets.map { it.id })
        val reviews = P1ExactIndependentVisualReviewCatalog.reviews
        assertEquals(reviews.count { it.isApproved }, runtimeAssets.count { it.review.isApproved })
        assertEquals(reviews.count { !it.isApproved },
            runtimeAssets.count { it.review.status == InstructionAssetReviewStatus.REJECTED })
        val catalog = InstructionAssetCatalog(runtimeAssets)
        runtimeAssets.forEach { asset ->
            if (asset.review.isApproved) assertEquals(asset, catalog.findApprovedAssetForContent(asset.contentId))
            else assertNull(catalog.findApprovedAssetForContent(asset.contentId))
        }
        assertTrue(runtimeAssets.all { asset ->
            asset.namingConvention == InstructionAssetNamingConvention.EXACT_CONTENT_ID
        })
        assertTrue(runtimeAssets.all { asset ->
            asset.id.value == "instruction_${asset.contentId.value}_default"
        })
        assertTrue(runtimeAssets.all { asset -> asset.provenance.promptRevision.startsWith("accepted_") })
        assertTrue(runtimeAssets.all { asset -> asset.resourceSha256?.matches(Regex("[0-9a-f]{64}")) == true })
        assertTrue(runtimeAssets.all { asset -> asset.altTextRes == null })
        assertTrue(runtimeAssets.all { asset -> asset.companionInstructionRes == null })
    }

    @Test
    fun `missing ambiguous changed or nonindependent pixel review cannot approve tracker artwork`() {
        assertTrue(P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets(emptyList()).none { it.review.isApproved })
        val review = P1ExactIndependentVisualReviewCatalog.reviews.first { it.isApproved }
        val invalidReviews = listOf(listOf(review, review),
            listOf(review.copy(resourceSha256 = "0".repeat(64))),
            listOf(review.copy(reviewer = "Starlit tactile production tracker")))
        invalidReviews.forEach { ledger ->
            val asset = P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets(ledger).single { it.id == review.assetId }
            assertEquals(InstructionAssetReviewStatus.PENDING_REVIEW, asset.review.status)
            assertNull(InstructionAssetCatalog(listOf(asset)).findApprovedAssetForContent(asset.contentId))
        }
        val rejected = P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets(listOf(review.copy(mechanicsReviewed = false)))
            .single { it.id == review.assetId }
        assertEquals(InstructionAssetReviewStatus.REJECTED, rejected.review.status)
        assertNull(InstructionAssetCatalog(listOf(rejected)).findApprovedAssetForContent(rejected.contentId))
    }

    @Test
    fun `runtime assets retain canonical exact identities and visual priority`() {
        val candidatesById = P1TrackerAcceptedInstructionAssetCatalog.assets.associateBy { it.id }

        P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets().forEach { runtimeAsset ->
            val candidate = requireNotNull(candidatesById[runtimeAsset.id])
            val definition = requireNotNull(BuiltInP1RecipeCatalog.find(candidate.recipeId))
            assertEquals(candidate.drawableRes, runtimeAsset.drawableRes)
            assertEquals(definition.sourceMethodFamilyId, candidate.familyId.value)
            assertEquals(definition.sourceBrewerProfileId.value, candidate.profileId.value)
            assertEquals(definition.methodFamilyId, runtimeAsset.familyId)
            assertEquals(definition.brewerProfileId, runtimeAsset.profileId)
            assertEquals(candidate.familyId, runtimeAsset.physicalFamilyId)
            assertEquals(candidate.profileId, runtimeAsset.physicalProfileId)
            assertEquals(candidate.stageId, runtimeAsset.stageId)
            assertEquals(candidate.contentId, runtimeAsset.contentId)
            assertEquals(candidate.resourceSha256, runtimeAsset.resourceSha256)
            assertEquals(
                candidate.visualPriority == P1ExactVisualPriority.SAFETY_CRITICAL,
                runtimeAsset.safetySensitive,
            )
            assertNull(runtimeAsset.altTextRes)
            assertNull(runtimeAsset.companionInstructionRes)
        }
    }
}
