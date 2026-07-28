package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.LegacyStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBuiltInGuidanceCatalogTest {

    private val catalog = LegacyBuiltInGuidanceCatalog.catalog

    @Test
    fun `catalogue maps every legacy alias to its exact built-in profile in stable order`() {
        assertEquals(
            listOf(
                BrewMethod.PULSAR,
                BrewMethod.V60,
                BrewMethod.FRENCH_PRESS,
                BrewMethod.AEROPRESS,
                BrewMethod.ESPRESSO,
                BrewMethod.MOKA_POT,
                BrewMethod.COLD_BREW,
            ),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds.keys.toList(),
        )
        assertEquals(
            BrewerProfileId("pulsar_standard"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.PULSAR],
        )
        assertEquals(
            BrewerProfileId("v60_unspecified"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.V60],
        )
        assertEquals(
            BrewerProfileId("french_press_generic"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.FRENCH_PRESS],
        )
        assertEquals(
            BrewerProfileId("aeropress_standard"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.AEROPRESS],
        )
        assertEquals(
            BrewerProfileId("espresso_pump_generic"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.ESPRESSO],
        )
        assertEquals(
            BrewerProfileId("moka_generic_unspecified"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.MOKA_POT],
        )
        assertEquals(
            BrewerProfileId("cold_immersion_generic"),
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds[BrewMethod.COLD_BREW],
        )
        assertEquals(
            LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds.values.toSet(),
            LegacyBuiltInGuidanceCatalog.supportedProfileIds,
        )
    }

    @Test
    fun `every legacy source stage has profile-scoped primary guidance`() {
        LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds.forEach { (method, profileId) ->
            val profile = requireNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(profileId))

            sourceStages(method).forEach { stage ->
                val primary = catalog.content.single { item ->
                    item.id == stage.contentId &&
                        item.familyId == profile.familyId &&
                        item.profileId == profileId &&
                        item.stageId == stage.id &&
                        item.placement == BuiltInGuidancePlacement.LIVE_STAGE &&
                        !item.safetyCritical
                }

                assertTrue(primary.text.primaryInstruction.isNotBlank())
                assertTrue(primary.text.conciseInstruction?.isNotBlank() == true)
                assertTrue(primary.text.altText.isNotBlank())
            }
        }
    }

    @Test
    fun `full concise focused and custom policy resolve every legacy live stage`() {
        val resolver = DurableBrewSessionGuidanceResolver(guidanceCatalogs = listOf(catalog))
        val routineLevels = listOf(
            GuidancePresentationLevel.FULL,
            GuidancePresentationLevel.CONCISE,
            GuidancePresentationLevel.FOCUSED,
            GuidancePresentationLevel.CUSTOM,
        )

        LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds.forEach { (method, profileId) ->
            val profile = requireNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(profileId))
            sourceStages(method).forEach { stage ->
                routineLevels.forEach { level ->
                    val resolution = resolver.resolve(
                        DurableBrewSessionGuidanceRequest(
                            methodFamilyId = profile.familyId.value,
                            brewerProfileId = profileId.value,
                            currentStage = currentStage(stage),
                            preferences = DurableBrewSessionGuidancePreferences(
                                sessionOverride = level,
                            ),
                        ),
                    )

                    assertEquals(DurableBrewGuidanceAvailability.Available, resolution.availability)
                    assertEquals(level, resolution.policy?.level)
                    assertTrue(resolution.routineContent.any { item ->
                        item.id == stage.contentId &&
                            item.placement == BuiltInGuidancePlacement.LIVE_STAGE
                    })
                }
            }
        }
    }

    @Test
    fun `utilities only keeps utility and safety content without a generic stage fallback`() {
        val resolver = DurableBrewSessionGuidanceResolver(guidanceCatalogs = listOf(catalog))

        LegacyBuiltInGuidanceCatalog.legacyMethodProfileIds.forEach { (method, profileId) ->
            val profile = requireNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(profileId))
            sourceStages(method).forEach { stage ->
                val resolution = resolver.resolve(
                    DurableBrewSessionGuidanceRequest(
                        methodFamilyId = profile.familyId.value,
                        brewerProfileId = profileId.value,
                        currentStage = currentStage(stage),
                        preferences = DurableBrewSessionGuidancePreferences(
                            sessionOverride = GuidancePresentationLevel.UTILITIES_ONLY,
                        ),
                    ),
                )

                assertEquals(DurableBrewGuidanceAvailability.Available, resolution.availability)
                assertTrue(resolution.routineContent.any { item ->
                    item.placement == BuiltInGuidancePlacement.UTILITY
                })
                if (method != BrewMethod.PULSAR) {
                    assertFalse(resolution.routineContent.any { item ->
                        item.id == stage.contentId &&
                            item.placement == BuiltInGuidancePlacement.LIVE_STAGE
                    })
                }
                assertTrue(resolution.criticalContent.any { item ->
                    item.placement == BuiltInGuidancePlacement.GLOBAL_SAFETY
                })
            }
        }
    }

    @Test
    fun `critical safety is visible at every policy level and never exposes raw stage codes`() {
        val safetyItems = catalog.content.filter { item -> item.safetyCritical }

        assertTrue(safetyItems.isNotEmpty())
        GuidancePresentationLevel.entries.forEach { level ->
            safetyItems.forEach { item ->
                val policy = GuidancePolicyResolver.resolve(
                    GuidancePolicyContext(
                        methodFamilyId = item.familyId,
                        brewerProfileId = item.profileId,
                        sessionOverride = level,
                    ),
                )
                assertTrue(policy.isVisible(item.visibility, item.safetyCritical))
                assertTrue(item.visibility.alwaysVisible)
                assertTrue(item.text.warning?.isNotBlank() == true)
            }
        }

        val allCopy = catalog.content.joinToString(" ") { item ->
            listOfNotNull(
                item.text.primaryInstruction,
                item.text.conciseInstruction,
                item.text.explanation,
                item.text.tip,
                item.text.warning,
                item.text.altText,
            ).joinToString(" ")
        }.lowercase()
        assertFalse(allCopy.contains("moka_fill_below_safety_valve"))
        assertFalse(allCopy.contains("moka_use_low_to_medium_heat"))
    }

    @Test
    fun `composed catalog remains deterministic and has no duplicate content identifiers`() {
        val firstRead = catalog.content.map { item -> item.id }
        val secondRead = LegacyBuiltInGuidanceCatalog.catalog.content.map { item -> item.id }

        assertEquals(firstRead, secondRead)
        assertEquals(firstRead.size, firstRead.distinct().size)
        assertTrue(StageContentId("pulsar_bloom") in firstRead)
        assertTrue(StageContentId("moka_heat") in firstRead)
        assertTrue(StageContentId("cold_brew_filter") in firstRead)
    }

    private fun sourceStages(method: BrewMethod): List<BrewStageDefinition> =
        collectStages(LegacyStagePlanFactory.create(method).nodes)

    private fun collectStages(nodes: List<StagePlanNode>): List<BrewStageDefinition> = nodes.flatMap { node ->
        when (node) {
            is StagePlanNode.Stage -> listOf(node.definition)
            is StagePlanNode.OptionalSection -> collectStages(node.nodes)
            is StagePlanNode.BoundedRepeat -> collectStages(node.nodes)
        }
    }

    private fun currentStage(stage: BrewStageDefinition): CurrentBrewStagePresentation =
        CurrentBrewStagePresentation(
            stageInstanceId = StageInstanceId(stage.id, occurrence = 1),
            action = stage.action,
            contentId = stage.contentId,
            instructionAssetId = stage.instructionAssetId,
            requiresIllustration = stage.requiresIllustration,
            runStatus = StageRunStatus.ACTIVE,
            elapsedActiveMillis = 0L,
            completion = BrewStageCompletionPresentation.Manual,
            safetyMessages = stage.safetyMessages,
        )
}
