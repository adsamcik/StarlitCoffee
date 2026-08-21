package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId

/**
 * Minimal, explicit plans for the methods represented by the pre-catalogue
 * [BrewMethod] enum.
 *
 * This is a narrow compatibility seam: it turns the legacy selection into
 * immutable session data, without teaching the existing UI a new workflow.
 * The plans intentionally do not turn the short-brew timing ranges into
 * automatic completion rules. A brew remains user-directed after a bloom or
 * another observable preparation action. Cold brew is the single passive
 * exception: its steep is a durable countdown, followed by a manual filter
 * step so the session does not finish by itself.
 */
object LegacyStagePlanFactory {

    const val PLAN_VERSION: Int = 1

    /** Returns the stable source plan for one supported legacy method. */
    fun create(method: BrewMethod): BrewStagePlan = when (method) {
        BrewMethod.PULSAR,
        BrewMethod.V60,
        -> bloomPlan(method)

        BrewMethod.FRENCH_PRESS -> plan(
            method = method,
            manualStage("french_press_steep", BrewStageAction.STEEP),
            manualStage("french_press_press", BrewStageAction.PRESS),
        )

        BrewMethod.AEROPRESS -> plan(
            method = method,
            manualStage(
                id = "aeropress_steep",
                action = BrewStageAction.STEEP,
                safetyMessages = listOf(
                    StageSafetyMessage(
                        code = "aeropress_standard_orientation_sturdy_vessel",
                        severity = StageSafetySeverity.CRITICAL,
                    ),
                ),
            ),
            manualStage(
                id = "aeropress_press",
                action = BrewStageAction.PRESS,
                safetyMessages = listOf(
                    StageSafetyMessage(
                        code = "aeropress_sturdy_vessel_hands_clear",
                        severity = StageSafetySeverity.CRITICAL,
                    ),
                ),
            ),
        )

        BrewMethod.ESPRESSO -> plan(
            method = method,
            manualStage("espresso_pull", BrewStageAction.OBSERVE),
        )

        BrewMethod.MOKA_POT -> plan(
            method = method,
            manualStage(
                id = "moka_heat",
                action = BrewStageAction.HEAT,
                safetyMessages = listOf(
                    StageSafetyMessage(
                        code = "moka_fill_below_safety_valve",
                        severity = StageSafetySeverity.CRITICAL,
                    ),
                    StageSafetyMessage(
                        code = "moka_use_low_to_medium_heat",
                        severity = StageSafetySeverity.WARNING,
                    ),
                ),
            ),
            // Moka completion depends on visible flow, not a generic duration.
            manualStage("moka_observe_flow", BrewStageAction.OBSERVE),
        )

        BrewMethod.COLD_BREW -> plan(
            method = method,
            countdownStage(
                id = "cold_brew_steep",
                action = BrewStageAction.STEEP,
                durationMillis = secondsToMillis(method.timeTargetLow),
                safetyMessages = listOf(
                    StageSafetyMessage(
                        code = "food_refrigerate_4c_during_steep",
                        severity = StageSafetySeverity.WARNING,
                    ),
                ),
            ),
            manualStage(
                id = "cold_brew_filter",
                action = BrewStageAction.FILTER,
                safetyMessages = listOf(
                    StageSafetyMessage(
                        code = "food_refrigerate_4c_promptly_after_filtering",
                        severity = StageSafetySeverity.WARNING,
                    ),
                ),
            ),
        )
    }

    private fun bloomPlan(method: BrewMethod): BrewStagePlan = plan(
        method = method,
        countdownStage(
            id = "${method.name.lowercase()}_bloom",
            action = BrewStageAction.BLOOM,
            durationMillis = secondsToMillis(method.bloomDurationSeconds),
        ),
        manualStage("${method.name.lowercase()}_manual_brew", BrewStageAction.POUR),
    )

    private fun plan(
        method: BrewMethod,
        vararg stages: BrewStageDefinition,
    ): BrewStagePlan = BrewStagePlan(
        id = StagePlanId("legacy_${method.name.lowercase()}"),
        version = PLAN_VERSION,
        nodes = stages.map { stage -> StagePlanNode.Stage(stage) },
    )

    private fun manualStage(
        id: String,
        action: BrewStageAction,
        safetyMessages: List<StageSafetyMessage> = emptyList(),
    ): BrewStageDefinition = BrewStageDefinition(
        id = StageId(id),
        action = action,
        contentId = StageContentId(id),
        safetyMessages = safetyMessages,
        completionMode = StageCompletionMode.Manual,
    )

    private fun countdownStage(
        id: String,
        action: BrewStageAction,
        durationMillis: Long,
        safetyMessages: List<StageSafetyMessage> = emptyList(),
    ): BrewStageDefinition = BrewStageDefinition(
        id = StageId(id),
        action = action,
        contentId = StageContentId(id),
        safetyMessages = safetyMessages,
        completionMode = StageCompletionMode.Countdown(durationMillis),
        alertPolicy = StageAlertPolicy(alertOnStart = true),
    )

    private fun secondsToMillis(seconds: Int): Long = seconds.toLong() * MILLIS_PER_SECOND

    private const val MILLIS_PER_SECOND = 1_000L
}
