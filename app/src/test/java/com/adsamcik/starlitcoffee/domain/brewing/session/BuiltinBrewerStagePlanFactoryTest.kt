package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinBrewerStagePlanFactoryTest {

    @Test
    fun `each supported P1 profile has a stable independently compilable plan`() {
        val expectedProfileIds = setOf(
            "clever_style",
            "hario_switch",
            "valve_release_generic",
            "cezve_generic",
            "automatic_batch_generic",
            "automatic_single_cup_generic",
            "vietnamese_phin",
        )

        assertEquals(
            expectedProfileIds,
            BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds.map(BrewerProfileId::value).toSet(),
        )

        expectedProfileIds.forEach { rawId ->
            val profileId = BrewerProfileId(rawId)
            val profile = requireNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(profileId))
            val byProfile = requireNotNull(BuiltinBrewerStagePlanFactory.create(profile))
            val byId = requireNotNull(BuiltinBrewerStagePlanFactory.create(profileId))

            assertEquals(byId, byProfile)
            assertEquals(BuiltinBrewerStagePlanFactory.PLAN_VERSION, byProfile.version)
            assertTrue("$rawId must have a source stage", sourceStages(byProfile).isNotEmpty())
            assertTrue(
                "$rawId stages must retain matching stable content IDs",
                sourceStages(byProfile).all { stage -> stage.id.value == stage.contentId.value },
            )
            assertCompiles(byProfile)
        }
    }

    @Test
    fun `Hario Switch exposes immersion and manual gravity workflows on one profile`() {
        val profileId = BrewerProfileId("hario_switch")
        val steepAndRelease = requireNotNull(
            BuiltinBrewerStagePlanFactory.create(
                profileId,
                HarioSwitchWorkflow.STEEP_AND_RELEASE,
            ),
        )
        val manualGravity = requireNotNull(
            BuiltinBrewerStagePlanFactory.create(
                profileId,
                HarioSwitchWorkflow.MANUAL_GRAVITY,
            ),
        )

        assertEquals("builtin_hario_switch_steep_and_release", steepAndRelease.id.value)
        assertEquals("builtin_hario_switch_manual_gravity", manualGravity.id.value)
        assertTrue(stageIds(steepAndRelease).contains("hario_switch_close_valve"))
        assertTrue(stageIds(steepAndRelease).contains("hario_switch_open_valve"))
        assertFalse(stageIds(manualGravity).contains("hario_switch_close_valve"))
        assertTrue(stageIds(manualGravity).contains("hario_switch_open_valve_for_manual_gravity"))
        assertTrue(
            sourceStages(manualGravity).any { stage ->
                stage.completionMode == StageCompletionMode.ObservedEvent(
                    StageObservationId("hario_switch_drawdown_complete"),
                )
            },
        )
        assertCompiles(steepAndRelease)
        assertCompiles(manualGravity)
    }

    @Test
    fun `steep and release plans wait for a drawdown observation and show overflow safety`() {
        listOf(
            BrewerProfileId("clever_style"),
            BrewerProfileId("hario_switch"),
            BrewerProfileId("valve_release_generic"),
        ).forEach { profileId ->
            val plan = requireNotNull(BuiltinBrewerStagePlanFactory.create(profileId))
            val stages = sourceStages(plan)

            assertTrue(
                "${profileId.value} must require a physical drawdown observation",
                stages.any { stage ->
                    stage.completionMode == StageCompletionMode.ObservedEvent(
                        StageObservationId("${profileId.value}_drawdown_complete"),
                    )
                },
            )
            assertTrue(
                "${profileId.value} must preserve its overflow warning",
                stages.flatMap(BrewStageDefinition::safetyMessages).contains(
                    StageSafetyMessage(
                        code = "${profileId.value}_overflow_risk",
                        severity = StageSafetySeverity.CRITICAL,
                    ),
                ),
            )
            assertFalse(
                "${profileId.value} must not invent an automatic finish",
                stages.any { stage ->
                    stage.completionMode is StageCompletionMode.Countdown ||
                        stage.completionMode == StageCompletionMode.Immediate ||
                        stage.completionMode is StageCompletionMode.ElapsedRange
                },
            )
            assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
        }
    }

    @Test
    fun `cezve uses an observed foam rise and always visible heat safety`() {
        val plan = requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("cezve_generic")))
        val stages = sourceStages(plan)
        val heatStage = requireNotNull(stages.find { it.id.value == "cezve_generic_apply_gentle_heat" })
        val optionalSugar = requireNotNull(plan.nodes.filterIsInstance<StagePlanNode.OptionalSection>().singleOrNull())
        val foamCycle = requireNotNull(plan.nodes.filterIsInstance<StagePlanNode.BoundedRepeat>().singleOrNull())

        assertEquals(StageConditionId("cezve_include_sugar"), optionalSugar.conditionId)
        assertEquals(StageRepeatId("cezve_foam_rise_cycles"), foamCycle.repeatId)
        assertEquals(1, foamCycle.minimumOccurrences)
        assertEquals(2, foamCycle.maximumOccurrences)
        assertEquals(
            setOf(
                "cezve_never_leave_unattended",
                "cezve_open_flame_and_hob_safety",
                "cezve_rapid_boil_over_risk",
                "cezve_hot_metal_burn_risk",
                "cezve_keep_small_vessel_stable",
            ),
            heatStage.safetyMessages
                .filter { it.severity == StageSafetySeverity.CRITICAL }
                .map(StageSafetyMessage::code)
                .toSet(),
        )
        assertTrue(
            stages.any { stage ->
                stage.completionMode == StageCompletionMode.ObservedEvent(
                    StageObservationId("cezve_generic_foam_rising"),
                )
            },
        )
        assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
        assertCompiles(
            plan = plan,
            selections = StagePlanSelections(
                includedConditions = setOf(StageConditionId("cezve_include_sugar")),
                repeatCounts = mapOf(StageRepeatId("cezve_foam_rise_cycles") to 2),
            ),
        )
    }

    @Test
    fun `automatic plans wait for an observed machine completion rather than a timer`() {
        listOf(
            BrewerProfileId("automatic_batch_generic"),
            BrewerProfileId("automatic_single_cup_generic"),
        ).forEach { profileId ->
            val plan = requireNotNull(BuiltinBrewerStagePlanFactory.create(profileId))
            val stages = sourceStages(plan)

            assertTrue(
                stages.any { stage ->
                    stage.completionMode == StageCompletionMode.ObservedEvent(
                        StageObservationId("${profileId.value}_machine_cycle_complete"),
                    )
                },
            )
            assertFalse(
                stages.any { stage ->
                    stage.completionMode is StageCompletionMode.Countdown ||
                        stage.completionMode == StageCompletionMode.Immediate ||
                        stage.completionMode is StageCompletionMode.ElapsedRange
                },
            )
            assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
        }
    }

    @Test
    fun `phin waits for first drip and completion observations before manual collection`() {
        val plan = requireNotNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("vietnamese_phin")))
        val stages = sourceStages(plan)

        assertTrue(
            stages.any { stage ->
                stage.completionMode == StageCompletionMode.ObservedEvent(
                    StageObservationId("vietnamese_phin_first_drip_observed"),
                )
            },
        )
        assertTrue(
            stages.any { stage ->
                stage.completionMode == StageCompletionMode.ObservedEvent(
                    StageObservationId("vietnamese_phin_drip_complete"),
                )
            },
        )
        assertTrue(
            stages.flatMap(BrewStageDefinition::safetyMessages).contains(
                StageSafetyMessage("phin_keep_cup_stable", StageSafetySeverity.CRITICAL),
            ),
        )
        assertTrue(
            stages.flatMap(BrewStageDefinition::safetyMessages).contains(
                StageSafetyMessage("phin_hot_metal_burn_risk", StageSafetySeverity.CRITICAL),
            ),
        )
        assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
        assertCompiles(plan)
    }

    @Test
    fun `unsupported profiles and incompatible Switch workflow requests never fall back`() {
        assertNull(BuiltinBrewerStagePlanFactory.create(BrewerProfileId("future_brewer")))
        assertNull(
            BuiltinBrewerStagePlanFactory.create(
                BrewerProfileId("clever_style"),
                HarioSwitchWorkflow.MANUAL_GRAVITY,
            ),
        )
    }

    private fun assertCompiles(
        plan: BrewStagePlan,
        selections: StagePlanSelections = StagePlanSelections(),
    ) {
        val context = StagePlanValidationContext(
            knownContentIds = sourceStages(plan).map(BrewStageDefinition::contentId).toSet(),
        )
        val result = StagePlanCompiler.compile(plan, selections, context)

        assertTrue(
            "${plan.id.value} did not compile: ${(result as? StagePlanCompileResult.Invalid)?.issues}",
            result is StagePlanCompileResult.Compiled,
        )
    }

    private fun stageIds(plan: BrewStagePlan): Set<String> = sourceStages(plan).mapTo(mutableSetOf()) {
        stage -> stage.id.value
    }

    private fun sourceStages(plan: BrewStagePlan): List<BrewStageDefinition> = sourceStages(plan.nodes)

    private fun sourceStages(nodes: List<StagePlanNode>): List<BrewStageDefinition> = nodes.flatMap { node ->
        when (node) {
            is StagePlanNode.Stage -> listOf(node.definition)
            is StagePlanNode.OptionalSection -> sourceStages(node.nodes)
            is StagePlanNode.BoundedRepeat -> sourceStages(node.nodes)
        }
    }
}
