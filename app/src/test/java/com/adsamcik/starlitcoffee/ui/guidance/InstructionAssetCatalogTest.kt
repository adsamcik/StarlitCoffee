package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionAssetCatalogTest {

    @Test
    fun `asset ID deterministically matches its drawable resource name`() {
        val asset = approvedAsset()

        assertEquals(
            "instruction_manual_gravity_v60_02_pour_bloom_default",
            asset.expectedDrawableResourceName(),
        )
        assertEquals(asset.id.value, asset.expectedDrawableResourceName())
    }

    @Test
    fun `family-default assets use an explicit family stem`() {
        val asset = approvedAsset(
            id = InstructionAssetId("instruction_manual_gravity_family_pour_bloom_default"),
            profileId = null,
        )

        assertEquals(
            "instruction_manual_gravity_family_pour_bloom_default",
            asset.expectedDrawableResourceName(),
        )
    }

    @Test
    fun `exact stage assets use their canonical content ID naming convention`() {
        val contentId = StageContentId("p1_chemex_42_700_stage_01_instruction")
        val asset = approvedAsset(
            id = InstructionAssetId("instruction_${contentId.value}_default"),
            contentId = contentId,
            namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,
        )

        assertEquals(
            "instruction_p1_chemex_42_700_stage_01_instruction_default",
            asset.expectedDrawableResourceName(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            approvedAsset(
                id = asset.id,
                contentId = contentId,
                namingConvention = InstructionAssetNamingConvention.SCOPED_SLOT,
            )
        }
    }

    @Test
    fun `catalog marks unreviewed mandatory and safety assets as incomplete`() {
        val mandatory = approvedAsset(
            review = InstructionAssetReview(InstructionAssetReviewStatus.DRAFT),
        )
        val safetyAsset = approvedAsset(
            id = InstructionAssetId("instruction_manual_gravity_v60_02_pour_safely_default"),
            contentId = StageContentId("pour_safely"),
            mandatoryForFullGuidance = false,
            safetySensitive = true,
            review = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW),
        )
        val catalog = InstructionAssetCatalog(listOf(mandatory, safetyAsset))

        val readiness = catalog.releaseReadiness(FAMILY, PROFILE)

        assertFalse(readiness.isReleaseComplete)
        assertEquals(setOf(mandatory.id, safetyAsset.id), readiness.unapprovedAssetIds)
    }

    @Test
    fun `content references asset ID rather than drawable and shares instruction text`() {
        val asset = approvedAsset()
        val assets = InstructionAssetCatalog(listOf(asset))
        val content = GuidanceContentRecord(
            id = CONTENT,
            familyId = FAMILY,
            profileId = PROFILE,
            stageId = STAGE,
            primaryInstructionRes = R.string.instruction_pour_total,
            instructionAssetId = asset.id,
            requiresVisualForFullGuidance = true,
        )

        val catalog = GuidanceContentCatalog(listOf(content), assets)

        assertTrue(catalog.releaseReadiness(FAMILY, PROFILE).isReleaseComplete)
    }

    @Test
    fun `safety-critical content requires explicit always-visible warning text`() {
        assertThrows(IllegalArgumentException::class.java) {
            GuidanceContentRecord(
                id = CONTENT,
                familyId = FAMILY,
                primaryInstructionRes = R.string.instruction_pour_total,
                safetyCritical = true,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            GuidanceContentRecord(
                id = CONTENT,
                familyId = FAMILY,
                primaryInstructionRes = R.string.instruction_pour_total,
                warningRes = R.string.instruction_pour_total,
                safetyCritical = true,
                visibility = GuidanceVisibilityPolicy(
                    visibleIn = setOf(GuidancePresentationLevel.FULL),
                ),
            )
        }
    }

    @Test
    fun `manifest validator compares explicit drawable refs with expected names`() {
        val matchingCatalog = InstructionAssetCatalog(
            listOf(approvedAsset(drawableRes = FakeDrawableResources.instruction_manual_gravity_v60_02_pour_bloom_default)),
        )
        val mismatchedCatalog = InstructionAssetCatalog(
            listOf(approvedAsset(drawableRes = FakeDrawableResources.wrong_instruction_name)),
        )

        assertTrue(
            InstructionAssetManifestValidator
                .findDrawableResourceNameMismatches(matchingCatalog, FakeDrawableResources::class.java)
                .isEmpty(),
        )
        val mismatches = InstructionAssetManifestValidator
            .findDrawableResourceNameMismatches(mismatchedCatalog, FakeDrawableResources::class.java)

        assertEquals(1, mismatches.size)
        assertEquals("wrong_instruction_name", mismatches.single().actualResourceName)
    }

    @Test
    fun `geometry rejects illustrations that are not four by three`() {
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetGeometry(widthPx = 1000, heightPx = 1000)
        }
    }

    private fun approvedAsset(
        id: InstructionAssetId = ASSET_ID,
        profileId: BrewerProfileId? = PROFILE,
        contentId: StageContentId = CONTENT,
        namingConvention: InstructionAssetNamingConvention =
            InstructionAssetNamingConvention.SCOPED_SLOT,
        drawableRes: Int = R.drawable.vessel_icon_mug,
        mandatoryForFullGuidance: Boolean = true,
        safetySensitive: Boolean = false,
        review: InstructionAssetReview = approvedReview(),
    ): InstructionAssetRecord = InstructionAssetRecord(
        id = id,
        familyId = FAMILY,
        profileId = profileId,
        stageId = STAGE,
        contentId = contentId,
        namingConvention = namingConvention,
        drawableRes = drawableRes,
        altTextRes = if (namingConvention == InstructionAssetNamingConvention.SCOPED_SLOT) {
            R.string.app_name
        } else {
            null
        },
        companionInstructionRes =
            if (namingConvention == InstructionAssetNamingConvention.SCOPED_SLOT) {
                R.string.instruction_pour_total
            } else {
                null
            },
        mandatoryForFullGuidance = mandatoryForFullGuidance,
        safetySensitive = safetySensitive,
        provenance = InstructionAssetProvenance(
            promptDocument = "docs/brewing/asset-production.md",
            promptRevision = "test-v1",
        ),
        review = review,
    )

    private fun approvedReview(): InstructionAssetReview = InstructionAssetReview(
        status = InstructionAssetReviewStatus.APPROVED,
        reviewer = "QA",
        reviewedOn = LocalDate.of(2026, 7, 27),
    )

    private companion object {
        val FAMILY = MethodFamilyId("manual_gravity")
        val PROFILE = BrewerProfileId("v60_02")
        val STAGE = StageId("bloom")
        val CONTENT = StageContentId("pour_bloom")
        val ASSET_ID = InstructionAssetId("instruction_manual_gravity_v60_02_pour_bloom_default")
    }
}

object FakeDrawableResources {
    const val instruction_manual_gravity_v60_02_pour_bloom_default = 101

    const val wrong_instruction_name = 102
}
