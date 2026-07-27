package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewTimingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyStagePlanFactoryTest {

    @Test
    fun `each legacy method has one valid stable source plan`() {
        BrewMethod.entries.forEach { method ->
            val plan = LegacyStagePlanFactory.create(method)

            assertEquals("legacy_${method.name.lowercase()}", plan.id.value)
            assertEquals(LegacyStagePlanFactory.PLAN_VERSION, plan.version)
            assertTrue("${method.name} plan", StagePlanValidator.validate(plan).isValid)
        }
    }

    @Test
    fun `bloom methods count down bloom before a manual brew stage`() {
        BrewMethod.entries.filter(BrewMethod::hasBloom).forEach { method ->
            val stages = directStages(LegacyStagePlanFactory.create(method))

            assertEquals(BrewStageAction.BLOOM, stages.first().action)
            assertEquals(
                StageCompletionMode.Countdown(method.bloomDurationSeconds * 1_000L),
                stages.first().completionMode,
            )
            assertEquals(BrewStageAction.POUR, stages.last().action)
            assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
        }
    }

    @Test
    fun `short active methods leave every stage under manual control`() {
        BrewMethod.entries
            .filter { method ->
                !method.hasBloom && method.timingMode == BrewTimingMode.ACTIVE_TIMER
            }
            .forEach { method ->
                val stages = directStages(LegacyStagePlanFactory.create(method))

                assertTrue("${method.name} has stages", stages.isNotEmpty())
                assertTrue(
                    "${method.name} has no automatic completion",
                    stages.all { it.completionMode == StageCompletionMode.Manual },
                )
                assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
            }
    }

    @Test
    fun `cold brew uses a durable steep countdown then awaits manual filtering`() {
        val stages = directStages(LegacyStagePlanFactory.create(BrewMethod.COLD_BREW))

        assertEquals(BrewStageAction.STEEP, stages.first().action)
        assertEquals(
            StageCompletionMode.Countdown(BrewMethod.COLD_BREW.timeTargetLow * 1_000L),
            stages.first().completionMode,
        )
        assertTrue(stages.first().alertPolicy.scheduleDeadline)
        assertEquals(BrewStageAction.FILTER, stages.last().action)
        assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
    }

    @Test
    fun `moka plan waits for a visible flow observation instead of a generic timer`() {
        val stages = directStages(LegacyStagePlanFactory.create(BrewMethod.MOKA_POT))

        assertEquals(BrewStageAction.HEAT, stages.first().action)
        assertEquals(
            listOf(
                StageSafetyMessage("moka_fill_below_safety_valve", StageSafetySeverity.CRITICAL),
                StageSafetyMessage("moka_use_low_to_medium_heat", StageSafetySeverity.WARNING),
            ),
            stages.first().safetyMessages,
        )
        assertEquals(BrewStageAction.OBSERVE, stages.last().action)
        assertEquals(StageCompletionMode.Manual, stages.last().completionMode)
    }

    private fun directStages(plan: BrewStagePlan): List<BrewStageDefinition> = plan.nodes.map { node ->
        (node as? StagePlanNode.Stage)?.definition
            ?: throw AssertionError("Legacy plans must use direct ordered stages")
    }
}
