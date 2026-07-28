package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P1BuiltInGuidanceCatalogTest {

    @Test
    fun `catalog covers every source stage in both Switch workflows`() {
        val expectedContentIds = sourceStages().mapTo(linkedSetOf()) { stage -> stage.contentId }
        val cataloguedContentIds = P1BuiltInGuidanceCatalog.entries
            .filter { entry -> entry.plannedVisualAsset != null }
            .mapTo(linkedSetOf()) { entry -> entry.content.id }

        assertEquals(expectedContentIds, cataloguedContentIds)
        assertEquals(
            BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds,
            P1BuiltInGuidanceCatalog.supportedProfileIds,
        )
        P1BuiltInGuidanceCatalog.entries
            .filter { entry -> entry.plannedVisualAsset != null }
            .forEach { entry ->
                assertTrue(entry.content.text.primaryInstruction.isNotBlank())
                assertTrue(entry.content.text.conciseInstruction?.isNotBlank() == true)
                assertTrue(entry.content.text.altText.isNotBlank())
            }
    }

    @Test
    fun `every safety bearing source stage has always visible authored context`() {
        val sourceSafety = sourceStages().filter { stage -> stage.safetyMessages.isNotEmpty() }

        sourceSafety.forEach { stage ->
            val safety = requireNotNull(
                P1BuiltInGuidanceCatalog.catalog.find(
                    StageContentId("${stage.contentId.value}_safety"),
                ),
            )

            assertEquals(stage.id, safety.stageId)
            assertTrue(safety.visibility.alwaysVisible)
            assertTrue(safety.text.warning?.isNotBlank() == true)
            assertEquals(
                stage.safetyMessages.any { message ->
                    message.severity == StageSafetySeverity.CRITICAL
                },
                safety.safetyCritical,
            )
        }
        BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds.forEach { profileId ->
            val global = requireNotNull(
                P1BuiltInGuidanceCatalog.catalog.find(
                    StageContentId("${profileId.value}_global_safety"),
                ),
            )

            assertEquals(BuiltInGuidancePlacement.GLOBAL_SAFETY, global.placement)
            assertTrue(global.safetyCritical)
            assertTrue(global.visibility.alwaysVisible)
        }
    }

    @Test
    fun `every P1 visual slot is explicit and not presented as an approved asset`() {
        val stageEntries = P1BuiltInGuidanceCatalog.entries.filter { entry ->
            entry.plannedVisualAsset != null
        }

        assertEquals(stageEntries.size, P1BuiltInGuidanceCatalog.plannedVisualAssets.size)
        P1BuiltInGuidanceCatalog.plannedVisualAssets.forEach { asset ->
            assertEquals(P1PlannedInstructionAssetStatus.NOT_PRODUCED, asset.status)
            assertTrue(asset.mandatoryForFullGuidance)
            assertEquals(asset.id.value, asset.expectedResourceName())
            assertEquals(asset, P1BuiltInGuidanceCatalog.plannedVisualAssetFor(asset.contentId))
        }
    }

    @Test
    fun `resolver accepts P1 beside Pulsar without borrowing Pulsar content`() {
        val resolver = DurableBrewSessionGuidanceResolver(
            guidanceCatalogs = listOf(
                PulsarBuiltInGuidanceCatalog.catalog,
                P1BuiltInGuidanceCatalog.catalog,
            ),
        )
        val resolution = resolver.resolve(
            DurableBrewSessionGuidanceRequest(
                methodFamilyId = "steep_and_release",
                brewerProfileId = "hario_switch",
                currentStage = stage(
                    StageId("hario_switch_open_valve"),
                    StageContentId("hario_switch_open_valve"),
                ),
                preferences = DurableBrewSessionGuidancePreferences(
                    sessionOverride = GuidancePresentationLevel.FULL,
                ),
            ),
        )

        assertEquals(DurableBrewGuidanceAvailability.Available, resolution.availability)
        assertTrue(resolution.routineContent.any { content ->
            content.id == StageContentId("hario_switch_open_valve") &&
                content.instruction.contains("Hario Switch")
        })
        assertTrue(resolution.criticalContent.any { content ->
            content.id == StageContentId("hario_switch_open_valve_safety")
        })
        assertFalse(resolution.routineContent.any { content ->
            content.id.value.startsWith("pulsar_")
        })
    }

    @Test
    fun `generic valve copy does not claim another brewer control or a fixed drawdown`() {
        val allGenericValveCopy = P1BuiltInGuidanceCatalog.entries
            .filter { entry -> entry.content.profileId == BrewerProfileId("valve_release_generic") }
            .joinToString(" ") { entry ->
                listOfNotNull(
                    entry.content.text.primaryInstruction,
                    entry.content.text.conciseInstruction,
                    entry.content.text.explanation,
                    entry.content.text.tip,
                    entry.content.text.warning,
                    entry.content.text.altText,
                ).joinToString(" ")
            }
            .lowercase()

        assertFalse(allGenericValveCopy.contains("flip"))
        assertFalse(allGenericValveCopy.contains("switch"))
        assertTrue(allGenericValveCopy.contains("do not infer a specific release motion or fixed drawdown plan"))
    }

    private fun sourceStages(): List<BrewStageDefinition> = sourcePlans()
        .flatMap { plan -> collectStages(plan.nodes) }
        .distinctBy(BrewStageDefinition::contentId)

    private fun sourcePlans() = listOf(
        requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("clever_style"))),
        requireNotNull(
            BuiltinBrewerStagePlanFactory.create(
                BrewerProfileId("hario_switch"),
                HarioSwitchWorkflow.STEEP_AND_RELEASE,
            ),
        ),
        requireNotNull(
            BuiltinBrewerStagePlanFactory.create(
                BrewerProfileId("hario_switch"),
                HarioSwitchWorkflow.MANUAL_GRAVITY,
            ),
        ),
        requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("valve_release_generic"))),
        requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("cezve_generic"))),
        requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("automatic_batch_generic"))),
        requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("automatic_single_cup_generic"))),
        requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("vietnamese_phin"))),
    )

    private fun collectStages(nodes: List<StagePlanNode>): List<BrewStageDefinition> = nodes.flatMap { node ->
        when (node) {
            is StagePlanNode.Stage -> listOf(node.definition)
            is StagePlanNode.OptionalSection -> collectStages(node.nodes)
            is StagePlanNode.BoundedRepeat -> collectStages(node.nodes)
        }
    }

    private fun stage(
        stageId: StageId,
        contentId: StageContentId,
    ): CurrentBrewStagePresentation = CurrentBrewStagePresentation(
        stageInstanceId = StageInstanceId(stageId, occurrence = 1),
        action = BrewStageAction.RELEASE,
        contentId = contentId,
        instructionAssetId = null,
        requiresIllustration = false,
        runStatus = StageRunStatus.ACTIVE,
        elapsedActiveMillis = 0L,
        completion = BrewStageCompletionPresentation.Manual,
        safetyMessages = emptyList(),
    )
}
