package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.brewing.BrewStepContent
import com.adsamcik.starlitcoffee.ui.guidance.LegacyBuiltInGuidanceCatalog
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BrewHelpProjectionTest {
    @Test fun `short procedure retains the complete source and rationale in details`() {
        val path = "src/main/assets/${ReviewedMethodGuideLibrary.ASSET_PATH}"
        val source = listOf(File(path), File("app/$path")).first(File::isFile).readText()
        val step = ReviewedMethodGuideLibrary.decode(source).single { it.methodId == "pulsar" }.steps[4]
        val full = BrewStepContent(StageContentId("reviewed_pulsar_v1_open_and_pulse"),
            instruction = step.instruction, completionCue = step.completion,
            warning = "Distinct safety stays visible", safetyCritical = true, details = listOf(step.why))
        val help = projectBrewHelpStep(full, "pulsar_standard", "Short reviewed procedure")

        assertEquals("Short reviewed procedure", help.instruction)
        assertEquals(listOf(step.instruction, step.why), help.details)
        assertEquals(full.completionCue, help.completionCue)
        assertEquals(full.warning, help.warning)
        assertTrue(help.safetyCritical)
    }

    @Test fun `known repeated safety instruction leaves the complete warning visible`() {
        val full = safetyStep()
        val help = projectBrewHelpStep(full, "pulsar_standard")
        assertNull(help.instruction)
        assertEquals(full.warning, help.warning)
        assertTrue(help.safetyCritical)
        assertTrue(help.details.isEmpty())
    }

    @Test fun `changed or differently scoped safety is never consolidated`() {
        val full = safetyStep()
        assertEquals(full, projectBrewHelpStep(full, "another_brewer"))
        val changedInstruction = full.copy(instruction = "New distinct handling requirement")
        assertEquals(changedInstruction, projectBrewHelpStep(changedInstruction, "pulsar_standard"))
        val changedWarning = full.copy(warning = "New distinct hazard")
        assertEquals(changedWarning, projectBrewHelpStep(changedWarning, "pulsar_standard"))
    }

    private fun safetyStep(): BrewStepContent {
        val id = StageContentId("pulsar_hot_liquid_safety")
        val text = requireNotNull(LegacyBuiltInGuidanceCatalog.catalog.find(id)).text
        return BrewStepContent(id, instruction = text.primaryInstruction, warning = text.warning, safetyCritical = true)
    }
}
