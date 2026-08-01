package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewSessionGuidanceContentOrderTest {

    @Test
    fun `approved visual prioritizes its exact routine stage content before critical and support content`() {
        val supporting = content(
            id = "supporting_content",
            placement = BuiltInGuidancePlacement.UTILITY,
        )
        val primary = content(
            id = "illustrated_stage_content",
            placement = BuiltInGuidancePlacement.LIVE_STAGE,
        )
        val critical = content(
            id = "critical_content",
            placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
            safetyCritical = true,
        )

        val ordered = orderedLiveGuidanceContent(
            routineContent = listOf(supporting, primary),
            criticalContent = listOf(critical),
            illustratedContentId = primary.id,
        )

        assertEquals(listOf(primary, critical, supporting), ordered)
    }

    @Test
    fun `without a visual the existing critical first ordering is preserved`() {
        val primary = content(
            id = "routine_stage_content",
            placement = BuiltInGuidancePlacement.LIVE_STAGE,
        )
        val critical = content(
            id = "critical_content",
            placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
            safetyCritical = true,
        )

        val ordered = orderedLiveGuidanceContent(
            routineContent = listOf(primary),
            criticalContent = listOf(critical),
            illustratedContentId = null,
        )

        assertEquals(listOf(critical, primary), ordered)
    }

    private fun content(
        id: String,
        placement: BuiltInGuidancePlacement,
        safetyCritical: Boolean = false,
    ): ResolvedBrewGuidanceContent = ResolvedBrewGuidanceContent(
        id = StageContentId(id),
        placement = placement,
        instruction = id,
        target = null,
        completionCue = null,
        explanation = null,
        tip = null,
        nextAction = null,
        controlRequirements = emptyList(),
        warning = null,
        utilities = emptyList(),
        altText = id,
        safetyCritical = safetyCritical,
    )
}
