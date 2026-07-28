package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow

/** A profile-plan selection for Learn, kept separate from presentation density. */
data class P1LearnContentSelection(
    val content: List<BuiltInGuidanceContent>,
    val requiresHarioSwitchWorkflow: Boolean,
)

/**
 * Restricts P1 Learn content to the selected physical plan and puts live
 * stages in source-plan order. It intentionally leaves non-P1 curricula in
 * their source-catalogue order.
 */
object P1LearnContentSelector {

    fun select(
        content: List<BuiltInGuidanceContent>,
        profileId: BrewerProfileId,
        harioSwitchWorkflow: HarioSwitchWorkflow?,
    ): P1LearnContentSelection {
        if (profileId !in BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds) {
            return P1LearnContentSelection(content, requiresHarioSwitchWorkflow = false)
        }
        val workflowRequired = profileId == P1LearnStageOrder.harioSwitchProfileId &&
            harioSwitchWorkflow == null
        val commonHarioSwitchStageIds: Set<StageId> = if (workflowRequired) {
            P1LearnStageOrder.commonHarioSwitchStageIds()
        } else {
            emptySet()
        }
        val stageOrder = if (workflowRequired) {
            P1LearnStageOrder
                .selectedStageIds(
                    P1LearnStageOrder.harioSwitchProfileId,
                    HarioSwitchWorkflow.STEEP_AND_RELEASE,
                )
                .orEmpty()
                .filter { stageId -> stageId in commonHarioSwitchStageIds }
        } else {
            P1LearnStageOrder.selectedStageIds(profileId, harioSwitchWorkflow).orEmpty()
        }
        val allowedStageIds = if (workflowRequired) {
            commonHarioSwitchStageIds
        } else {
            stageOrder.toSet()
        }
        val stageOrderById = stageOrder.withIndex().associate { (index, stageId) ->
            stageId to index
        }
        return P1LearnContentSelection(
            content = content
                .filter { item ->
                    item.placement != BuiltInGuidancePlacement.LIVE_STAGE ||
                        item.stageId in allowedStageIds
                }
                .sortedBy { item -> item.learnOrder(stageOrderById) },
            requiresHarioSwitchWorkflow = workflowRequired,
        )
    }

    private fun BuiltInGuidanceContent.learnOrder(
        stageOrderById: Map<StageId, Int>,
    ): Int = when (placement) {
        BuiltInGuidancePlacement.GLOBAL_SAFETY -> GLOBAL_SAFETY_ORDER
        BuiltInGuidancePlacement.PREPARATION -> PREPARATION_ORDER
        BuiltInGuidancePlacement.LIVE_STAGE -> {
            val stageOrder = stageId?.let(stageOrderById::get) ?: UNKNOWN_STAGE_ORDER
            val safetyAfterStage = if (id.value == stageId?.value) 0 else 1
            LIVE_STAGE_ORDER + (stageOrder * STAGE_ORDER_STRIDE) + safetyAfterStage
        }

        BuiltInGuidancePlacement.COMPLETION -> COMPLETION_ORDER
        BuiltInGuidancePlacement.UTILITY -> UTILITY_ORDER
    }

    private const val GLOBAL_SAFETY_ORDER = 0
    private const val PREPARATION_ORDER = 1_000
    private const val LIVE_STAGE_ORDER = 10_000
    private const val STAGE_ORDER_STRIDE = 10
    private const val UNKNOWN_STAGE_ORDER = 50_000
    private const val COMPLETION_ORDER = 900_000
    private const val UTILITY_ORDER = 1_000_000
}
