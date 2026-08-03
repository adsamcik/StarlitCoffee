package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltInInstructionAssetCatalogTest {

    @Test
    fun `production source excludes retired legacy inputs`() {
        val retiredIds = setOf(
            "instruction_steep_and_release_clever_style_" +
                "clever_style_insert_and_rinse_filter_default",
            "instruction_steep_and_release_hario_switch_" +
                "hario_switch_add_coffee_default",
            "instruction_restricted_flow_gravity_concentrate_vietnamese_phin_" +
                "vietnamese_phin_place_on_stable_cup_default",
        )

        assertEquals(
            emptySet<String>(),
            BuiltInInstructionAssetCatalog.catalog.assets
                .mapTo(linkedSetOf()) { asset -> asset.id.value }
                .intersect(retiredIds),
        )
    }
    @Test
    fun `content lookup returns a reviewed default and never a pending record`() {
        val pending = asset(
            variant = InstructionAssetVariant("pending_review"),
            review = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW),
        )
        val approved = asset(review = approvedReview())
        val catalog = InstructionAssetCatalog(listOf(pending, approved))

        assertEquals(approved, catalog.findApprovedAssetForContent(CONTENT))
    }

    @Test
    fun `content lookup refuses an unreviewed sole record`() {
        val catalog = InstructionAssetCatalog(
            listOf(asset(review = InstructionAssetReview(InstructionAssetReviewStatus.DRAFT))),
        )

        assertNull(catalog.findApprovedAssetForContent(CONTENT))
    }

    @Test
    fun `content lookup refuses an ambiguous approved default`() {
        val first = asset(profileId = BrewerProfileId("v60_01"), review = approvedReview())
        val second = asset(profileId = BrewerProfileId("v60_02"), review = approvedReview())
        val catalog = InstructionAssetCatalog(listOf(first, second))

        assertNull(catalog.findApprovedAssetForContent(CONTENT))
    }

    private fun asset(
        profileId: BrewerProfileId = PROFILE,
        variant: InstructionAssetVariant = InstructionAssetVariant.DEFAULT,
        review: InstructionAssetReview,
    ): InstructionAssetRecord = InstructionAssetRecord(
        id = InstructionAssetId(
            "instruction_${FAMILY.value}_${profileId.value}_${CONTENT.value}_${variant.value}",
        ),
        familyId = FAMILY,
        profileId = profileId,
        stageId = STAGE,
        contentId = CONTENT,
        variant = variant,
        drawableRes = R.drawable.vessel_icon_mug,
        altTextRes = R.string.app_name,
        companionInstructionRes = R.string.instruction_pour_total,
        mandatoryForFullGuidance = true,
        safetySensitive = false,
        provenance = InstructionAssetProvenance(
            promptDocument = "docs/brewing/asset-production.md",
            promptRevision = "test-v1",
        ),
        review = review,
    )

    private fun approvedReview(): InstructionAssetReview = InstructionAssetReview(
        status = InstructionAssetReviewStatus.APPROVED,
        reviewer = "QA",
        reviewedOn = LocalDate.of(2026, 7, 28),
    )

    private companion object {
        val FAMILY = MethodFamilyId("manual_gravity")
        val PROFILE = BrewerProfileId("v60_02")
        val STAGE = StageId("bloom")
        val CONTENT = StageContentId("pour_bloom")
    }
}
