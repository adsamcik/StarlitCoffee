package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.guidance.BrewingTerminologyReference
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

    @Test
    fun `terminology references preserve first appearance and deduplicate concepts`() {
        val bed = terminology("coffee_bed", "kávové lože", "coffee bed")
        val drawdown = terminology("drawdown", "dokapání", "drawdown")
        val first = content(
            id = "first",
            placement = BuiltInGuidancePlacement.LIVE_STAGE,
            terminologyReferences = listOf(bed, drawdown),
        )
        val second = content(
            id = "second",
            placement = BuiltInGuidancePlacement.UTILITY,
            terminologyReferences = listOf(drawdown),
        )

        assertEquals(
            listOf(bed, drawdown),
            distinctTerminologyReferences(listOf(first, second)),
        )
    }

    private fun terminology(
        conceptId: String,
        preferredLocal: String,
        canonicalEnglish: String,
    ) = BrewingTerminologyReference(conceptId, preferredLocal, canonicalEnglish)

    private fun content(
        id: String,
        placement: BuiltInGuidancePlacement,
        safetyCritical: Boolean = false,
        terminologyReferences: List<BrewingTerminologyReference> = emptyList(),
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
        terminologyReferences = terminologyReferences,
    )
}
