package com.adsamcik.starlitcoffee.ui.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P1ExactIndependentVisualReviewCatalogTest {

    @Test
    fun `ledger covers every exact stage and pins each tracker asset hash`() {
        val reviews = P1ExactIndependentVisualReviewCatalog.reviews
        val assetsById = BuiltInInstructionAssetCatalog.catalog.assets.associateBy { it.id }

        assertEquals(114, reviews.size)
        assertEquals(114, reviews.map { it.assetId }.toSet().size)
        reviews.forEach { review ->
            val asset = requireNotNull(assetsById[review.assetId])
            assertEquals(asset.resourceSha256, review.resourceSha256)
            assertFalse(asset.review.reviewer == review.reviewer)
            assertTrue(review.fullResolutionReviewed)
            assertTrue(review.phoneScaleReviewed)
        }
    }

    @Test
    fun `renewed production frames all have complete independent pixel approval`() {
        val reviews = P1ExactIndependentVisualReviewCatalog.reviews
        assertEquals(114, reviews.count { it.isApproved })
        assertTrue(reviews.all { it.mechanicsReviewed && it.altTextReviewed })
    }
}
