package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
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
    fun `independent verdicts approve 103 assets and reject the 11 incorrect frames`() {
        val reviews = P1ExactIndependentVisualReviewCatalog.reviews
        val rejected = reviews.filterNot { it.isApproved }.mapTo(linkedSetOf()) { it.assetId }

        assertEquals(103, reviews.count { it.isApproved })
        assertEquals(
            setOf(
                "instruction_p1_switch_official_20_240_stage_02_instruction_default",
                "instruction_p1_auto_batch_500_30_stage_01_instruction_default",
                "instruction_p1_auto_batch_500_30_stage_02_instruction_default",
                "instruction_p1_auto_batch_500_30_stage_03_instruction_default",
                "instruction_p1_auto_batch_500_30_stage_04_instruction_default",
                "instruction_p1_auto_batch_500_30_stage_05_instruction_default",
                "instruction_p1_auto_batch_1000_60_stage_01_instruction_default",
                "instruction_p1_auto_batch_1000_60_stage_02_instruction_default",
                "instruction_p1_auto_batch_1000_60_stage_03_instruction_default",
                "instruction_p1_auto_batch_1000_60_stage_04_instruction_default",
                "instruction_p1_phin_screw_18_120_stage_02_instruction_default",
            ).mapTo(linkedSetOf(), ::InstructionAssetId),
            rejected,
        )

        val screwPhin = reviews.single {
            it.assetId.value ==
                "instruction_p1_phin_screw_18_120_stage_02_instruction_default"
        }
        assertTrue(screwPhin.fullResolutionReviewed)
        assertTrue(screwPhin.phoneScaleReviewed)
        assertFalse(screwPhin.mechanicsReviewed)
        assertFalse(screwPhin.altTextReviewed)
    }
}
