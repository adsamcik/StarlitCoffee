package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P1TrackerAcceptedInstructionAssetCatalogTest {

    @Test
    fun `tracker accepted records retain exact identities but are not production visuals`() {
        val assets = P1TrackerAcceptedInstructionAssetCatalog.assets

        assertTrue(assets.isNotEmpty())
        assertEquals(assets.size, assets.map { asset -> asset.id }.toSet().size)
        assertTrue(
            assets.all { asset ->
                asset.id.value == "instruction_${asset.contentId.value}_default"
            },
        )
        assertTrue(P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets().isEmpty())
    }

    @Test
    fun `runtime promotion needs reviewed copy and complete recipe localization`() {
        val candidate = P1TrackerAcceptedInstructionAssetCatalog.assets.first()
        // These generic resources are test sentinels only. Production remains empty until
        // reviewed, stage-specific Android strings are available for every supported locale.
        val localization = P1ExactInstructionAssetLocalization(
            instructionAssetId = candidate.id,
            altTextRes = R.string.app_name,
            companionInstructionRes = R.string.instruction_pour_total,
            runtimeReview = approvedReview(),
        )
        val localizations = P1ExactInstructionAssetLocalizationCatalog(listOf(localization))
        val incompleteCoverage = P1ExactRecipeLocalizationCoverage(
            supportedLocaleTags = P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags,
            coveredLocaleTagsByRecipe = emptyMap(),
        )

        assertTrue(
            P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets(
                localizations = localizations,
                localizationCoverage = incompleteCoverage,
            ).isEmpty(),
        )

        val completeCoverage = P1ExactRecipeLocalizationCoverage(
            supportedLocaleTags = P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags,
            coveredLocaleTagsByRecipe = mapOf(
                candidate.recipeId to P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags,
            ),
        )
        val runtimeAsset = P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets(
            localizations = localizations,
            localizationCoverage = completeCoverage,
        ).single()

        assertEquals(candidate.id, runtimeAsset.id)
        assertEquals(candidate.drawableRes, runtimeAsset.drawableRes)
        assertEquals(candidate.familyId, runtimeAsset.familyId)
        assertEquals(candidate.profileId, runtimeAsset.profileId)
        assertEquals(candidate.stageId, runtimeAsset.stageId)
        assertEquals(candidate.contentId, runtimeAsset.contentId)
        assertEquals(InstructionAssetNamingConvention.EXACT_CONTENT_ID, runtimeAsset.namingConvention)
        assertTrue(runtimeAsset.review.isApproved)
        assertEquals(
            candidate.visualPriority == P1ExactVisualPriority.SAFETY_CRITICAL,
            runtimeAsset.safetySensitive,
        )
    }

    @Test
    fun `unreviewed localization cannot promote accepted artwork`() {
        val candidate = P1TrackerAcceptedInstructionAssetCatalog.assets.first()

        assertThrows(IllegalArgumentException::class.java) {
            P1ExactInstructionAssetLocalization(
                instructionAssetId = InstructionAssetId(candidate.id.value),
                altTextRes = R.string.app_name,
                companionInstructionRes = R.string.instruction_pour_total,
                runtimeReview = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW),
            )
        }
    }

    private fun approvedReview() = InstructionAssetReview(
        status = InstructionAssetReviewStatus.APPROVED,
        reviewer = "Accessibility QA",
        reviewedOn = LocalDate.of(2026, 8, 3),
    )
}
