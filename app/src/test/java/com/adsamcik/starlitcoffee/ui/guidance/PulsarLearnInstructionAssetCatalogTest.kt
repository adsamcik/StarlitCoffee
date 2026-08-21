package com.adsamcik.starlitcoffee.ui.guidance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulsarLearnInstructionAssetCatalogTest {
    private val guideCatalog = PulsarLearnGuideCatalog.decode(
        pulsarGuideResource().readText(),
    )

    @Test
    fun `each Pulsar stage has one approved mandatory exact-content asset`() {
        val assets = PulsarLearnInstructionAssetCatalog.runtimeAssets()
        val stages = guideCatalog.content

        assertEquals(8, assets.size)
        assertEquals(stages.map { stage -> stage.id }, assets.map { asset -> asset.contentId })
        assertEquals(stages.map { stage -> stage.stageId }, assets.map { asset -> asset.stageId })
        assertTrue(assets.all { asset -> asset.review.isApproved })
        assertTrue(assets.all { asset -> asset.mandatoryForFullGuidance })
        assertTrue(
            assets.all { asset ->
                asset.namingConvention == InstructionAssetNamingConvention.EXACT_CONTENT_ID
            },
        )
        assertTrue(
            assets.all { asset ->
                asset.geometry == InstructionAssetGeometry(widthPx = 1024, heightPx = 768)
            },
        )
    }

    @Test
    fun `production catalog resolves every Pulsar illustration without ambiguity`() {
        guideCatalog.content.forEach { stage ->
            val asset = BuiltInInstructionAssetCatalog.catalog
                .findApprovedAssetForContent(stage.id)

            assertEquals(stage.id, requireNotNull(asset).contentId)
        }
    }

    @Test
    fun `safe-removal illustration is marked safety-sensitive`() {
        val assets = PulsarLearnInstructionAssetCatalog.runtimeAssets()

        assertTrue(assets.last().safetySensitive)
        assertTrue(assets.dropLast(1).none { asset -> asset.safetySensitive })
    }

    private fun pulsarGuideResource(): File = listOf(
        File("src/main/res/raw/pulsar_learn_guidance.json"),
        File("app/src/main/res/raw/pulsar_learn_guidance.json"),
    ).first(File::isFile)
}
