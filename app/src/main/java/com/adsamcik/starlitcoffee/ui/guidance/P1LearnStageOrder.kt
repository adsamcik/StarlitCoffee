package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode

/**
 * Physical source-plan order for P1 Learn content.
 *
 * Hario Switch has two legitimate workflows on one profile. A Learn surface
 * therefore must select one before it displays workflow-specific steps; only
 * stages shared by both flows are safe to show before that selection.
 */
object P1LearnStageOrder {
    val harioSwitchProfileId = BrewerProfileId("hario_switch")

    fun selectedStageIds(
        profileId: BrewerProfileId,
        harioSwitchWorkflow: HarioSwitchWorkflow? = null,
    ): List<StageId>? {
        if (profileId !in BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds) return null
        if (profileId == harioSwitchProfileId && harioSwitchWorkflow == null) return null
        val plan = BuiltinBrewerStagePlanFactory.create(profileId, harioSwitchWorkflow) ?: return null
        return collectStages(plan.nodes).map(BrewStageDefinition::id)
    }

    fun commonHarioSwitchStageIds(): Set<StageId> {
        val steepAndRelease = requireNotNull(
            selectedStageIds(harioSwitchProfileId, HarioSwitchWorkflow.STEEP_AND_RELEASE),
        )
        val manualGravity = requireNotNull(
            selectedStageIds(harioSwitchProfileId, HarioSwitchWorkflow.MANUAL_GRAVITY),
        )
        return steepAndRelease.toSet().intersect(manualGravity.toSet())
    }

    private fun collectStages(nodes: List<StagePlanNode>): List<BrewStageDefinition> = nodes.flatMap { node ->
        when (node) {
            is StagePlanNode.Stage -> listOf(node.definition)
            is StagePlanNode.OptionalSection -> collectStages(node.nodes)
            is StagePlanNode.BoundedRepeat -> collectStages(node.nodes)
        }
    }
}
