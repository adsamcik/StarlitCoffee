package com.adsamcik.starlitcoffee.ui.guidance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PulsarLearnGuideCatalogTest {
    private val catalog = PulsarLearnGuideCatalog.decode(
        pulsarGuideResource().readText(),
    )

    @Test
    fun `guide keeps the sourced eight-stage valve sequence intact`() {
        val stages = catalog.content

        assertEquals(8, stages.size)
        assertEquals(PulsarLearnGuideCatalog.stageIds, stages.map { stage -> stage.stageId })
        assertEquals(stages.size, stages.map { stage -> stage.id }.distinct().size)
        assertTrue(stages.all { stage -> stage.profileId == PulsarLearnGuideCatalog.profileId })
        assertTrue(stages.all { stage -> stage.familyId == PulsarLearnGuideCatalog.familyId })
        assertTrue(stages[2].text.primaryInstruction.contains("60 g"))
        assertTrue(stages[2].text.primaryInstruction.contains("Open the valve"))
        assertTrue(stages[5].text.primaryInstruction.contains("340 g"))
        assertTrue(
            stages[6].authoredPresentations[GuidancePresentationLevel.FULL]
                ?.target.orEmpty().contains("3:30"),
        )
    }

    @Test
    fun `every stage has complete full-density learning copy`() {
        catalog.content.forEach { stage ->
            assertEquals(
                GuidancePresentationLevel.entries.toSet(),
                stage.authoredPresentations.keys,
            )
            val full = requireNotNull(
                stage.authoredPresentations[GuidancePresentationLevel.FULL],
            )
            assertTrue(full.instruction.orEmpty().isNotBlank())
            assertTrue(full.target.orEmpty().isNotBlank())
            assertTrue(full.completionCue.orEmpty().isNotBlank())
            assertTrue(full.accessibleAltText.isNotBlank())
        }
    }

    @Test
    fun `quantity guidance explains its ratios without assuming another brewer`() {
        val stages = catalog.content
        val dose = stages[1]
        val bloom = stages[2]
        val finalPour = stages[5]
        val drawdown = stages[6]

        assertTrue(dose.text.primaryInstruction.contains("1:17"))
        assertTrue(dose.text.explanation.orEmpty().contains("17 g of water"))
        assertTrue(bloom.text.primaryInstruction.contains("three times the 20 g dose"))
        assertTrue(bloom.text.explanation.orEmpty().contains("1:3 bloom"))
        assertTrue(finalPour.text.primaryInstruction.contains("340 g total"))
        assertTrue(finalPour.text.explanation.orEmpty().contains("adds 280 g"))
        assertTrue(drawdown.text.explanation.orEmpty().contains("exact 20 g / 340 g recipe"))

        val allLearningCopy = stages.joinToString(separator = " ") { stage ->
            listOf(
                stage.text.primaryInstruction,
                stage.text.conciseInstruction,
                stage.text.explanation,
                stage.text.tip,
            ).joinToString(separator = " ")
        }
        assertFalse(allLearningCopy.contains("V60", ignoreCase = true))
    }

    @Test
    fun `safe removal remains explicit and always visible`() {
        val removal = catalog.content.last()

        assertTrue(removal.safetyCritical)
        assertTrue(removal.visibility.alwaysVisible)
        assertTrue(removal.text.warning.orEmpty().contains("only by its black base"))
    }

    @Test
    fun `standalone resolver returns only the exact Pulsar stages in order`() {
        val resolution = LearnGuidanceCatalogResolver(
            guidanceCatalogs = listOf(catalog),
        ).resolve(
            LearnGuidanceCatalogRequest(
                methodFamilyId = PulsarLearnGuideCatalog.familyId.value,
                brewerProfileId = PulsarLearnGuideCatalog.profileId.value,
                preferences = DurableBrewSessionGuidancePreferences(
                    sessionOverride = GuidancePresentationLevel.FULL,
                ),
                exactStageOrder = PulsarLearnGuideCatalog.stageIds,
            ),
        )

        assertTrue(resolution.availability is LearnGuidanceCatalogAvailability.Available)
        assertEquals(
            catalog.content.map { stage -> stage.id },
            resolution.content.map { stage -> stage.id },
        )
    }

    private fun pulsarGuideResource(): File = listOf(
        File("src/main/res/raw/pulsar_learn_guidance.json"),
        File("app/src/main/res/raw/pulsar_learn_guidance.json"),
    ).first(File::isFile)
}
