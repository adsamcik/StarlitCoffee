package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1TrackerAcceptedInstructionAssetCatalogTest {

    @Test
    fun `all tracker accepted records promote to approved exact asset metadata`() {
        val candidates = P1TrackerAcceptedInstructionAssetCatalog.assets
        val runtimeAssets = P1TrackerAcceptedInstructionAssetCatalog.runtimeAssets()

        assertEquals(114, candidates.size)
        assertEquals(candidates.size, runtimeAssets.size)
        assertEquals(candidates.map { it.id }, runtimeAssets.map { it.id })
        assertTrue(runtimeAssets.all { asset -> asset.review.isApproved })
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
