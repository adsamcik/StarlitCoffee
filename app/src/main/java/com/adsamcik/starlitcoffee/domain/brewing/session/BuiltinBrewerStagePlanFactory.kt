package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfile
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId

/**
 * The Hario Switch can use either its immersion valve or its ordinary
 * gravity-dripper behaviour. These workflows belong to one brewer profile,
 * but have distinct stable plans because their physical transitions differ.
 */
enum class HarioSwitchWorkflow {
    STEEP_AND_RELEASE,
    MANUAL_GRAVITY,
}

/**
 * Source plans for the P1 built-in brewer profiles.
 *
 * The factory intentionally returns `null` for an unsupported profile rather
 * than substituting a superficially similar brewer. A recipe that references
 * a newer or custom profile must remain unavailable until it has a real plan.
 * Recipe-specific targets, quantities, and optional serving choices are
 * supplied by the caller; these plans only define the durable physical flow.
 */
object BuiltinBrewerStagePlanFactory {

    const val PLAN_VERSION: Int = 1

    val supportedBrewerProfileIds: Set<BrewerProfileId>
        get() = setOf(
            CLEVER_STYLE,
            HARIO_SWITCH,
            VALVE_RELEASE_GENERIC,
            CEZVE_GENERIC,
            AUTOMATIC_BATCH_GENERIC,
            AUTOMATIC_SINGLE_CUP_GENERIC,
            VIETNAMESE_PHIN,
        )

    /** Creates a plan from the immutable profile snapshot used by a recipe. */
    fun create(
        profile: BrewerProfile,
        harioSwitchWorkflow: HarioSwitchWorkflow? = null,
    ): BrewStagePlan? = create(profile.id, harioSwitchWorkflow)

    /** Creates a plan from a stable built-in profile ID without a fallback. */
    fun create(
        brewerProfileId: BrewerProfileId,
        harioSwitchWorkflow: HarioSwitchWorkflow? = null,
    ): BrewStagePlan? = when (brewerProfileId) {
        CLEVER_STYLE -> if (harioSwitchWorkflow == null) cleverStylePlan() else null
        HARIO_SWITCH -> harioSwitchPlan(harioSwitchWorkflow ?: HarioSwitchWorkflow.STEEP_AND_RELEASE)
        VALVE_RELEASE_GENERIC -> if (harioSwitchWorkflow == null) valveReleasePlan() else null
        CEZVE_GENERIC -> if (harioSwitchWorkflow == null) cezvePlan() else null
        AUTOMATIC_BATCH_GENERIC -> if (harioSwitchWorkflow == null) automaticBatchPlan() else null
        AUTOMATIC_SINGLE_CUP_GENERIC -> if (harioSwitchWorkflow == null) automaticSingleCupPlan() else null
        VIETNAMESE_PHIN -> if (harioSwitchWorkflow == null) vietnamesePhinPlan() else null
        else -> null
    }

    private fun cleverStylePlan(): BrewStagePlan = steepAndReleasePlan(
        profileId = CLEVER_STYLE,
        releaseStageSuffix = "place_on_server",
    )

    private fun valveReleasePlan(): BrewStagePlan = steepAndReleasePlan(
        profileId = VALVE_RELEASE_GENERIC,
        releaseStageSuffix = "open_valve",
    )

    private fun steepAndReleasePlan(
        profileId: BrewerProfileId,
        releaseStageSuffix: String,
    ): BrewStagePlan = plan(
        profileId,
        stage(profileId, "insert_and_rinse_filter", BrewStageAction.RINSE),
        stage(profileId, "close_valve", BrewStageAction.PREPARE),
        stage(profileId, "add_coffee", BrewStageAction.ADD_COFFEE),
        stage(profileId, "add_water", BrewStageAction.ADD_WATER),
        stage(profileId, "agitate", BrewStageAction.AGITATE),
        stage(profileId, "steep", BrewStageAction.STEEP),
        stage(
            profileId = profileId,
            suffix = releaseStageSuffix,
            action = BrewStageAction.RELEASE,
            safetyMessages = releaseSafety(profileId),
        ),
        observedStage(
            profileId = profileId,
            suffix = "observe_drawdown",
            observationSuffix = "drawdown_complete",
        ),
        stage(profileId, "remove_and_serve", BrewStageAction.SERVE),
    )

    private fun harioSwitchPlan(workflow: HarioSwitchWorkflow): BrewStagePlan = when (workflow) {
        HarioSwitchWorkflow.STEEP_AND_RELEASE -> planWithSuffix(
            HARIO_SWITCH,
            "steep_and_release",
            stage(HARIO_SWITCH, "insert_and_rinse_filter", BrewStageAction.RINSE),
            stage(HARIO_SWITCH, "close_valve", BrewStageAction.PREPARE),
            stage(HARIO_SWITCH, "add_coffee", BrewStageAction.ADD_COFFEE),
            stage(HARIO_SWITCH, "add_water", BrewStageAction.ADD_WATER),
            stage(HARIO_SWITCH, "agitate", BrewStageAction.AGITATE),
            stage(HARIO_SWITCH, "steep", BrewStageAction.STEEP),
            stage(
                profileId = HARIO_SWITCH,
                suffix = "open_valve",
                action = BrewStageAction.RELEASE,
                safetyMessages = releaseSafety(HARIO_SWITCH) + StageSafetyMessage(
                    code = "hario_switch_hot_glass_thermal_shock",
                    severity = StageSafetySeverity.CRITICAL,
                ),
            ),
            observedStage(
                profileId = HARIO_SWITCH,
                suffix = "observe_drawdown",
                observationSuffix = "drawdown_complete",
            ),
            stage(HARIO_SWITCH, "remove_and_serve", BrewStageAction.SERVE),
        )

        HarioSwitchWorkflow.MANUAL_GRAVITY -> planWithSuffix(
            HARIO_SWITCH,
            "manual_gravity",
            stage(HARIO_SWITCH, "insert_and_rinse_filter", BrewStageAction.RINSE),
            stage(HARIO_SWITCH, "open_valve_for_manual_gravity", BrewStageAction.PREPARE),
            stage(HARIO_SWITCH, "add_coffee", BrewStageAction.ADD_COFFEE),
            stage(
                profileId = HARIO_SWITCH,
                suffix = "pour_water",
                action = BrewStageAction.POUR,
                safetyMessages = releaseSafety(HARIO_SWITCH) + StageSafetyMessage(
                    code = "hario_switch_hot_glass_thermal_shock",
                    severity = StageSafetySeverity.CRITICAL,
                ),
            ),
            observedStage(
                profileId = HARIO_SWITCH,
                suffix = "observe_drawdown",
                observationSuffix = "drawdown_complete",
            ),
            stage(HARIO_SWITCH, "remove_and_serve", BrewStageAction.SERVE),
        )
    }

    private fun cezvePlan(): BrewStagePlan = plan(
        CEZVE_GENERIC,
        stage(CEZVE_GENERIC, "select_pot_capacity", BrewStageAction.PREPARE),
        stage(CEZVE_GENERIC, "add_water", BrewStageAction.ADD_WATER),
        stage(CEZVE_GENERIC, "add_finely_ground_coffee", BrewStageAction.ADD_COFFEE),
        StagePlanNode.OptionalSection(
            conditionId = StageConditionId("cezve_include_sugar"),
            nodes = listOf(
                stage(CEZVE_GENERIC, "add_sugar_before_heating", BrewStageAction.CUSTOM),
            ),
        ),
        stage(CEZVE_GENERIC, "mix_before_heating", BrewStageAction.AGITATE),
        StagePlanNode.BoundedRepeat(
            repeatId = StageRepeatId("cezve_foam_rise_cycles"),
            minimumOccurrences = 1,
            maximumOccurrences = 2,
            nodes = listOf(
                stage(
                    profileId = CEZVE_GENERIC,
                    suffix = "apply_gentle_heat",
                    action = BrewStageAction.HEAT,
                    safetyMessages = cezveHeatSafety,
                ),
                observedStage(
                    profileId = CEZVE_GENERIC,
                    suffix = "observe_foam_rising",
                    observationSuffix = "foam_rising",
                ),
                stage(
                    profileId = CEZVE_GENERIC,
                    suffix = "reduce_or_remove_heat",
                    action = BrewStageAction.HEAT,
                    safetyMessages = listOf(
                        StageSafetyMessage(
                            code = "cezve_hot_metal_burn_risk",
                            severity = StageSafetySeverity.CRITICAL,
                        ),
                        StageSafetyMessage(
                            code = "cezve_rapid_boil_over_risk",
                            severity = StageSafetySeverity.CRITICAL,
                        ),
                    ),
                ),
            ),
        ),
        stage(
            profileId = CEZVE_GENERIC,
            suffix = "pour_with_foam",
            action = BrewStageAction.SERVE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "cezve_hot_liquid_burn_risk",
                    severity = StageSafetySeverity.WARNING,
                ),
            ),
        ),
        stage(CEZVE_GENERIC, "allow_grounds_to_settle", BrewStageAction.STEEP),
    )

    private fun automaticBatchPlan(): BrewStagePlan = plan(
        AUTOMATIC_BATCH_GENERIC,
        stage(AUTOMATIC_BATCH_GENERIC, "select_filter_and_basket", BrewStageAction.PREPARE),
        stage(
            profileId = AUTOMATIC_BATCH_GENERIC,
            suffix = "rinse_filter",
            action = BrewStageAction.RINSE,
            isSkippable = true,
        ),
        stage(AUTOMATIC_BATCH_GENERIC, "add_coffee", BrewStageAction.ADD_COFFEE),
        stage(AUTOMATIC_BATCH_GENERIC, "level_coffee_bed", BrewStageAction.AGITATE),
        stage(AUTOMATIC_BATCH_GENERIC, "add_reservoir_water", BrewStageAction.ADD_WATER),
        stage(
            profileId = AUTOMATIC_BATCH_GENERIC,
            suffix = "start_machine",
            action = BrewStageAction.PREPARE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "automatic_batch_hot_liquid_burn_risk",
                    severity = StageSafetySeverity.WARNING,
                ),
            ),
        ),
        observedStage(
            profileId = AUTOMATIC_BATCH_GENERIC,
            suffix = "observe_machine_completion",
            observationSuffix = "machine_cycle_complete",
        ),
        stage(
            profileId = AUTOMATIC_BATCH_GENERIC,
            suffix = "stir_or_swirl_carafe",
            action = BrewStageAction.AGITATE,
            isSkippable = true,
        ),
        stage(
            profileId = AUTOMATIC_BATCH_GENERIC,
            suffix = "serve",
            action = BrewStageAction.SERVE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "automatic_batch_hot_liquid_burn_risk",
                    severity = StageSafetySeverity.WARNING,
                ),
            ),
        ),
    )

    private fun automaticSingleCupPlan(): BrewStagePlan = plan(
        AUTOMATIC_SINGLE_CUP_GENERIC,
        stage(AUTOMATIC_SINGLE_CUP_GENERIC, "select_and_insert_filter", BrewStageAction.PREPARE),
        stage(
            profileId = AUTOMATIC_SINGLE_CUP_GENERIC,
            suffix = "rinse_filter",
            action = BrewStageAction.RINSE,
            isSkippable = true,
        ),
        stage(AUTOMATIC_SINGLE_CUP_GENERIC, "add_coffee", BrewStageAction.ADD_COFFEE),
        stage(AUTOMATIC_SINGLE_CUP_GENERIC, "level_coffee_bed", BrewStageAction.AGITATE),
        stage(AUTOMATIC_SINGLE_CUP_GENERIC, "add_reservoir_water", BrewStageAction.ADD_WATER),
        stage(
            profileId = AUTOMATIC_SINGLE_CUP_GENERIC,
            suffix = "start_machine",
            action = BrewStageAction.PREPARE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "automatic_single_cup_hot_liquid_burn_risk",
                    severity = StageSafetySeverity.WARNING,
                ),
            ),
        ),
        observedStage(
            profileId = AUTOMATIC_SINGLE_CUP_GENERIC,
            suffix = "observe_machine_completion",
            observationSuffix = "machine_cycle_complete",
        ),
        stage(
            profileId = AUTOMATIC_SINGLE_CUP_GENERIC,
            suffix = "serve",
            action = BrewStageAction.SERVE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "automatic_single_cup_hot_liquid_burn_risk",
                    severity = StageSafetySeverity.WARNING,
                ),
            ),
        ),
    )

    private fun vietnamesePhinPlan(): BrewStagePlan = plan(
        VIETNAMESE_PHIN,
        stage(
            profileId = VIETNAMESE_PHIN,
            suffix = "place_on_stable_cup",
            action = BrewStageAction.PREPARE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "phin_keep_cup_stable",
                    severity = StageSafetySeverity.CRITICAL,
                ),
                StageSafetyMessage(
                    code = "phin_hot_liquid_overflow_risk",
                    severity = StageSafetySeverity.CRITICAL,
                ),
            ),
        ),
        stage(VIETNAMESE_PHIN, "add_coffee", BrewStageAction.ADD_COFFEE),
        stage(VIETNAMESE_PHIN, "level_coffee", BrewStageAction.AGITATE),
        stage(VIETNAMESE_PHIN, "place_gravity_or_screw_insert", BrewStageAction.PREPARE),
        stage(VIETNAMESE_PHIN, "pre_wet", BrewStageAction.ADD_WATER),
        stage(
            profileId = VIETNAMESE_PHIN,
            suffix = "fill_chamber",
            action = BrewStageAction.ADD_WATER,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "phin_hot_liquid_burn_risk",
                    severity = StageSafetySeverity.WARNING,
                ),
            ),
        ),
        stage(VIETNAMESE_PHIN, "cover", BrewStageAction.PREPARE),
        observedStage(
            profileId = VIETNAMESE_PHIN,
            suffix = "observe_first_drip",
            observationSuffix = "first_drip_observed",
        ),
        stage(VIETNAMESE_PHIN, "check_drip_rate", BrewStageAction.OBSERVE),
        observedStage(
            profileId = VIETNAMESE_PHIN,
            suffix = "observe_drip_completion",
            observationSuffix = "drip_complete",
        ),
        stage(
            profileId = VIETNAMESE_PHIN,
            suffix = "remove_hot_filter",
            action = BrewStageAction.PREPARE,
            safetyMessages = listOf(
                StageSafetyMessage(
                    code = "phin_hot_metal_burn_risk",
                    severity = StageSafetySeverity.CRITICAL,
                ),
            ),
        ),
        stage(VIETNAMESE_PHIN, "collect_concentrate", BrewStageAction.SERVE),
    )

    private fun releaseSafety(profileId: BrewerProfileId): List<StageSafetyMessage> = listOf(
        StageSafetyMessage(
            code = "${profileId.value}_overflow_risk",
            severity = StageSafetySeverity.CRITICAL,
        ),
        StageSafetyMessage(
            code = "${profileId.value}_keep_brewer_on_stable_vessel",
            severity = StageSafetySeverity.CRITICAL,
        ),
        StageSafetyMessage(
            code = "${profileId.value}_hot_liquid_burn_risk",
            severity = StageSafetySeverity.WARNING,
        ),
    )

    private fun plan(
        profileId: BrewerProfileId,
        vararg nodes: StagePlanNode,
    ): BrewStagePlan = planWithSuffix(profileId, null, *nodes)

    private fun planWithSuffix(
        profileId: BrewerProfileId,
        planSuffix: String?,
        vararg nodes: StagePlanNode,
    ): BrewStagePlan = BrewStagePlan(
        id = StagePlanId(
            listOfNotNull("builtin", profileId.value, planSuffix).joinToString(separator = "_"),
        ),
        version = PLAN_VERSION,
        nodes = nodes.toList(),
    )

    private fun stage(
        profileId: BrewerProfileId,
        suffix: String,
        action: BrewStageAction,
        safetyMessages: List<StageSafetyMessage> = emptyList(),
        isSkippable: Boolean = false,
    ): StagePlanNode.Stage {
        val stableId = "${profileId.value}_$suffix"
        return StagePlanNode.Stage(
            BrewStageDefinition(
                id = StageId(stableId),
                action = action,
                contentId = StageContentId(stableId),
                safetyMessages = safetyMessages,
                completionMode = StageCompletionMode.Manual,
                isSkippable = isSkippable,
            ),
        )
    }

    private fun observedStage(
        profileId: BrewerProfileId,
        suffix: String,
        observationSuffix: String,
    ): StagePlanNode.Stage {
        val stableId = "${profileId.value}_$suffix"
        return StagePlanNode.Stage(
            BrewStageDefinition(
                id = StageId(stableId),
                action = BrewStageAction.OBSERVE,
                contentId = StageContentId(stableId),
                completionMode = StageCompletionMode.ObservedEvent(
                    StageObservationId("${profileId.value}_$observationSuffix"),
                ),
            ),
        )
    }

    private val cezveHeatSafety = listOf(
        StageSafetyMessage(
            code = "cezve_never_leave_unattended",
            severity = StageSafetySeverity.CRITICAL,
        ),
        StageSafetyMessage(
            code = "cezve_open_flame_and_hob_safety",
            severity = StageSafetySeverity.CRITICAL,
        ),
        StageSafetyMessage(
            code = "cezve_rapid_boil_over_risk",
            severity = StageSafetySeverity.CRITICAL,
        ),
        StageSafetyMessage(
            code = "cezve_hot_metal_burn_risk",
            severity = StageSafetySeverity.CRITICAL,
        ),
        StageSafetyMessage(
            code = "cezve_keep_small_vessel_stable",
            severity = StageSafetySeverity.CRITICAL,
        ),
    )

    private val CLEVER_STYLE = BrewerProfileId("clever_style")
    private val HARIO_SWITCH = BrewerProfileId("hario_switch")
    private val VALVE_RELEASE_GENERIC = BrewerProfileId("valve_release_generic")
    private val CEZVE_GENERIC = BrewerProfileId("cezve_generic")
    private val AUTOMATIC_BATCH_GENERIC = BrewerProfileId("automatic_batch_generic")
    private val AUTOMATIC_SINGLE_CUP_GENERIC = BrewerProfileId("automatic_single_cup_generic")
    private val VIETNAMESE_PHIN = BrewerProfileId("vietnamese_phin")
}
