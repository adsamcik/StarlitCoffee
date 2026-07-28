package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun `reference targets preserve distinct cues and reject ambiguous duplicates`() {
        assertThrows(IllegalArgumentException::class.java) {
            StageReferenceTargets(
                timeTargets = listOf(
                    StageTimeTarget(
                        id = StageTargetId("drip_complete"),
                        reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
                        qualifier = StageTargetQualifier.APPROXIMATE,
                        minimumMillis = 30_000L,
                    ),
                    StageTimeTarget(
                        id = StageTargetId("drip_complete"),
                        reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
                        qualifier = StageTargetQualifier.RANGE,
                        minimumMillis = 40_000L,
                        maximumMillis = 50_000L,
                    ),
                ),
            )
        }
        val distinctDripCues = StageReferenceTargets(
            timeTargets = listOf(
                StageTimeTarget(
                    id = StageTargetId("first_drip"),
                    reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
                    qualifier = StageTargetQualifier.NO_LATER_THAN,
                    minimumMillis = 0L,
                    maximumMillis = 120_000L,
                ),
                StageTimeTarget(
                    id = StageTargetId("drip_complete"),
                    reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
                    qualifier = StageTargetQualifier.RANGE,
                    minimumMillis = 300_000L,
                    maximumMillis = 480_000L,
                ),
            ),
        )

        assertEquals(
            listOf("first_drip", "drip_complete"),
            distinctDripCues.timeTargets.map { target -> target.id.value },
        )

        assertThrows(IllegalArgumentException::class.java) {
            StageReferenceTargets(
                massTargets = listOf(
                    StageMassTarget(
                        id = StageTargetId("cumulative_water"),
                        role = QuantityRole.BREW_WATER_INPUT,
                        reference = StageMassReference.BREW_CUMULATIVE,
                        qualifier = StageTargetQualifier.EXACT,
                        minimumGrams = 100.0,
                    ),
                    StageMassTarget(
                        id = StageTargetId("cumulative_water"),
                        role = QuantityRole.BREW_WATER_INPUT,
                        reference = StageMassReference.BREW_CUMULATIVE,
                        qualifier = StageTargetQualifier.EXACT,
                        minimumGrams = 200.0,
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StageTimeTarget(
                id = StageTargetId("stage_duration"),
                reference = StageTimeReference.STAGE_DURATION,
                qualifier = StageTargetQualifier.EXACT,
                minimumMillis = 30_000L,
                maximumMillis = 45_000L,
            )
        }
    }

    @Test
    fun `validator rejects a completion trigger outside its reference target`() {
        val pour = stage(
            id = "pour",
            completion = StageCompletionMode.CumulativeAmount(100.0),
        ).copy(
            referenceTargets = StageReferenceTargets(
                massTargets = listOf(
                    StageMassTarget(
                        id = StageTargetId("cumulative_water"),
                        role = QuantityRole.BREW_WATER_INPUT,
                        reference = StageMassReference.BREW_CUMULATIVE,
                        qualifier = StageTargetQualifier.EXACT,
                        minimumGrams = 200.0,
                    ),
                ),
            ),
        )
        val plan = BrewStagePlan(
            id = StagePlanId("contradictory_target_plan"),
            version = 1,
            nodes = listOf(StagePlanNode.Stage(pour)),
        )

        val result = StagePlanValidator.validate(plan)

        assertTrue(result.issues.any { issue ->
            issue.code == StagePlanValidationCode.INVALID_COMPLETION_TARGET
        })
    }

    @Test
    fun `elapsed completion and reference ranges use inclusive overlap boundaries`() {
        fun timedStage(referenceStartMillis: Long): BrewStageDefinition = stage(
            id = "drawdown",
            completion = StageCompletionMode.ElapsedRange(
                minimumMillis = 30_000L,
                maximumMillis = 45_000L,
            ),
        ).copy(
            referenceTargets = StageReferenceTargets(
                timeTargets = listOf(
                    StageTimeTarget(
                        id = StageTargetId("drawdown_duration"),
                        reference = StageTimeReference.STAGE_DURATION,
                        qualifier = StageTargetQualifier.RANGE,
                        minimumMillis = referenceStartMillis,
                        maximumMillis = 60_000L,
                    ),
                ),
            ),
        )

        fun validate(referenceStartMillis: Long): StagePlanValidationResult =
            StagePlanValidator.validate(
                BrewStagePlan(
                    id = StagePlanId("drawdown_$referenceStartMillis"),
                    version = 1,
                    nodes = listOf(
                        StagePlanNode.Stage(timedStage(referenceStartMillis)),
                    ),
                ),
            )

        assertTrue(validate(45_000L).isValid)
        assertTrue(
            validate(45_001L).issues.any { issue ->
                issue.code == StagePlanValidationCode.INVALID_COMPLETION_TARGET
            },
        )
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
