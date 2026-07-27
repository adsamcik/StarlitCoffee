package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StagePlanCompilerTest {

    @Test
    fun `compiler expands selected optional and bounded repeat sections deterministically`() {
        val plan = BrewStagePlan(
            id = StagePlanId("test_plan"),
            version = 1,
            nodes = listOf(
                StagePlanNode.Stage(stage("prepare", StageCompletionMode.Manual)),
                StagePlanNode.OptionalSection(
                    conditionId = StageConditionId("include_bloom"),
                    nodes = listOf(StagePlanNode.Stage(stage("bloom", StageCompletionMode.Manual))),
                ),
                StagePlanNode.BoundedRepeat(
                    repeatId = StageRepeatId("pours"),
                    minimumOccurrences = 1,
                    maximumOccurrences = 3,
                    nodes = listOf(StagePlanNode.Stage(stage("pour", StageCompletionMode.Manual))),
                ),
            ),
        )

        val result = StagePlanCompiler.compile(
            plan = plan,
            selections = StagePlanSelections(
                includedConditions = setOf(StageConditionId("include_bloom")),
                repeatCounts = mapOf(StageRepeatId("pours") to 2),
            ),
        )

        val compiled = result as StagePlanCompileResult.Compiled
        assertEquals(
            listOf("prepare_1", "bloom_1", "pour_1", "pour_2"),
            compiled.value.stageIds.map(StageInstanceId::persistentKey),
        )
    }

    @Test
    fun `validator prevents critical safety from being hidden in an optional branch`() {
        val plan = BrewStagePlan(
            id = StagePlanId("safety_plan"),
            version = 1,
            nodes = listOf(
                StagePlanNode.OptionalSection(
                    conditionId = StageConditionId("optional_heat"),
                    nodes = listOf(
                        StagePlanNode.Stage(
                            stage(
                                id = "heat",
                                completion = StageCompletionMode.Manual,
                                safety = listOf(
                                    StageSafetyMessage("hot_surface", StageSafetySeverity.CRITICAL),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = StagePlanValidator.validate(plan)

        assertTrue(
            result.issues.any { it.code == StagePlanValidationCode.HIDDEN_CRITICAL_SAFETY },
        )
    }

    @Test
    fun `compiler rejects a selection that removes every executable stage`() {
        val plan = BrewStagePlan(
            id = StagePlanId("conditional_plan"),
            version = 1,
            nodes = listOf(
                StagePlanNode.OptionalSection(
                    conditionId = StageConditionId("only_when_iced"),
                    nodes = listOf(StagePlanNode.Stage(stage("ice", StageCompletionMode.Manual))),
                ),
            ),
        )

        val result = StagePlanCompiler.compile(plan)

        val invalid = result as StagePlanCompileResult.Invalid
        assertTrue(invalid.issues.any { it.code == StagePlanValidationCode.EMPTY_COMPILED_PLAN })
    }

    private fun stage(
        id: String,
        completion: StageCompletionMode,
        safety: List<StageSafetyMessage> = emptyList(),
    ): BrewStageDefinition = BrewStageDefinition(
        id = StageId(id),
        action = BrewStageAction.CUSTOM,
        contentId = StageContentId("${id}_content"),
        safetyMessages = safety,
        completionMode = completion,
    )
}
