package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId

/**
 * Where content belongs in a shared Learn/live experience. The same record is
 * intentionally reusable by both surfaces; this marker only gives renderers a
 * safe contextual default.
 */
enum class BuiltInGuidancePlacement {
    PREPARATION,
    LIVE_STAGE,
    COMPLETION,
    GLOBAL_SAFETY,
    UTILITY,
}

/**
 * Canonical, reviewable English copy for a built-in guidance record.
 *
 * This is content metadata rather than an Android string-resource binding.
 * Later localization maps the stable [BuiltInGuidanceContent.id] to resources
 * while Learn and live Brew continue to consume this one semantic record.
 */
data class GuidanceTextMetadata(
    val primaryInstruction: String,
    val conciseInstruction: String? = null,
    val explanation: String? = null,
    val tip: String? = null,
    val warning: String? = null,
    val altText: String,
) {
    init {
        require(primaryInstruction.isNotBlank()) {
            "Built-in guidance needs a primary instruction"
        }
        require(altText.isNotBlank()) {
            "Built-in guidance needs meaningful alt text"
        }
        require(conciseInstruction == null || conciseInstruction.isNotBlank()) {
            "Concise guidance cannot be blank"
        }
        require(explanation == null || explanation.isNotBlank()) {
            "Guidance explanation cannot be blank"
        }
        require(tip == null || tip.isNotBlank()) {
            "Guidance tip cannot be blank"
        }
        require(warning == null || warning.isNotBlank()) {
            "Guidance warning cannot be blank"
        }
    }
}

/** Closed set of operational cues authored by the exact source library. */
enum class GuidanceOperationalCue(
    val stableId: String,
    val fallbackLabel: String,
) {
    BEVERAGE_YIELD_TARGET("beverage_yield_target", "Beverage yield target"),
    COUNTDOWN_OR_TIMESTAMP("countdown_or_timestamp", "Countdown or timestamp"),
    CUMULATIVE_WATER_TARGET("cumulative_water_target", "Cumulative water target"),
    CURRENT_POUR_TARGET("current_pour_target", "Current pour target"),
    ELAPSED_TIMER("elapsed_timer", "Elapsed timer"),
    HEAT_STATE("heat_state", "Heat state"),
    STAGE_ADVANCE("stage_advance", "Next-stage control"),
    VALVE_STATE("valve_state", "Valve state"),
    ;

    companion object {
        private val byStableId = entries.associateBy(GuidanceOperationalCue::stableId)

        fun fromStableId(stableId: String): GuidanceOperationalCue? = byStableId[stableId]

        fun requireFromStableId(stableId: String): GuidanceOperationalCue =
            requireNotNull(fromStableId(stableId)) {
                "Unknown exact guidance operational cue: $stableId"
            }
    }
}

/**
 * One fully authored guidance density.
 *
 * Exact recipes use this instead of deriving focused and utilities-only views
 * from the full paragraph. Legacy catalogues leave the map empty and retain
 * their established compact-copy behavior.
 */
data class AuthoredGuidancePresentation(
    val instruction: String?,
    val target: String?,
    val completionCue: String?,
    val explanation: String?,
    val practicalTip: String?,
    val nextAction: String?,
    val controlRequirements: List<GuidanceOperationalCue>,
    val warning: String?,
    val utilities: List<GuidanceOperationalCue>,
    val accessibleAltText: String,
) {
    init {
        require(accessibleAltText.isNotBlank()) {
            "Authored guidance needs meaningful alt text"
        }
        val controlsAreUnique = controlRequirements.distinct().size == controlRequirements.size
        val utilitiesAreUnique = utilities.distinct().size == utilities.size
        require(controlsAreUnique && utilitiesAreUnique) {
            "Authored operational cues cannot contain duplicates"
        }
    }
}

/**
 * A resource-independent content record for built-in methods.
 *
 * It intentionally carries no drawable or string resource. The instructional
 * asset manifest owns those references once reviewed assets exist; this source
 * keeps the factual copy, visibility, and accessibility description together
 * before UI/resource integration begins.
 */
data class BuiltInGuidanceContent(
    val id: StageContentId,
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId? = null,
    val stageId: StageId? = null,
    val placement: BuiltInGuidancePlacement,
    val text: GuidanceTextMetadata,
    override val visibility: GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(),
    override val safetyCritical: Boolean = false,
    val authoredPresentations: Map<GuidancePresentationLevel, AuthoredGuidancePresentation> =
        emptyMap(),
    val terminologyReferences: List<BrewingTerminologyReference> = emptyList(),
) : GuidanceVisibilityItem {
    init {
        require(placement != BuiltInGuidancePlacement.LIVE_STAGE || stageId != null) {
            "Live-stage guidance must identify its stage"
        }
        require(!safetyCritical || !text.warning.isNullOrBlank()) {
            "Safety-critical guidance must include warning text"
        }
        require(!safetyCritical || visibility.alwaysVisible) {
            "Safety-critical guidance must remain visible at every guidance level"
        }
        require(
            authoredPresentations.isEmpty() ||
                authoredPresentations.keys == GuidancePresentationLevel.entries.toSet(),
        ) {
            "Authored guidance must cover every presentation level"
        }
        require(
            terminologyReferences.map(BrewingTerminologyReference::conceptId).distinct().size ==
                terminologyReferences.size,
        ) {
            "Guidance terminology references cannot contain duplicate concepts"
        }
    }
}

/**
 * Immutable content lookup shared by Learn and live Brew. It does not decide
 * stage execution; it only chooses copy already described by a stage plan.
 */
class BuiltInGuidanceCatalog(
    val content: List<BuiltInGuidanceContent>,
) {
    private val contentById = content.associateBy(BuiltInGuidanceContent::id)

    init {
        require(content.map { item -> item.id.value }.distinct().size == content.size) {
            "Duplicate built-in guidance content ID"
        }
    }

    fun find(id: StageContentId): BuiltInGuidanceContent? = contentById[id]

    fun forLearn(policy: ResolvedGuidancePolicy): List<BuiltInGuidanceContent> =
        policy.visibleItems(content)

    /**
     * Returns the current stage, global safety content, and (by default) the
     * utility content needed beside a live brew. Safety is included even when a
     * low-detail policy would otherwise hide all supporting copy.
     */
    fun forLiveStage(
        stageId: StageId,
        policy: ResolvedGuidancePolicy,
        includeUtilities: Boolean = true,
    ): List<BuiltInGuidanceContent> = policy.visibleItems(
        content.filter { item ->
            item.stageId == stageId ||
                item.placement == BuiltInGuidancePlacement.GLOBAL_SAFETY ||
                (includeUtilities && item.placement == BuiltInGuidancePlacement.UTILITY)
        },
    )
}

/**
 * Initial curated content for the documented Pulsar standard profile only.
 *
 * The content deliberately makes no technical claim about the internal
 * `pulsar_19k_metal` or `pulsar_40k_metal` identifiers. Those profiles remain
 * outside this curriculum until their nomenclature and behavior have primary
 * evidence. Recipe quantities and timings are likewise taken from the active,
 * user-selected recipe rather than invented in instructional copy.
 */
object PulsarBuiltInGuidanceCatalog {
    val familyId = MethodFamilyId("valve_controlled_no_bypass")
    val profileId = BrewerProfileId("pulsar_standard")

    val catalog: BuiltInGuidanceCatalog = BuiltInGuidanceCatalog(
        listOf(
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_prepare"),
                familyId = familyId,
                profileId = profileId,
                placement = BuiltInGuidancePlacement.PREPARATION,
                text = GuidanceTextMetadata(
                    primaryInstruction =
                        "Prepare the selected compatible filter, add your measured coffee, level the bed, and place the dispersion cap.",
                    conciseInstruction = "Prepare the selected filter, level the coffee bed, and place the cap.",
                    explanation =
                        "The dispersion cap helps distribute water over the coffee bed while the valve controls when liquid can drain.",
                    tip = "Set the brewer on a stable server and tare your scale before pouring.",
                    altText =
                        "A Pulsar brewer on a stable server with a selected filter seated in the base, level coffee, and a dispersion cap.",
                ),
                visibility = visibilityAt(
                    GuidancePresentationLevel.FULL,
                    GuidancePresentationLevel.CONCISE,
                    GuidancePresentationLevel.CUSTOM,
                ),
            ),
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_bloom"),
                familyId = familyId,
                profileId = profileId,
                stageId = StageId("pulsar_bloom"),
                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                text = GuidanceTextMetadata(
                    primaryInstruction =
                        "Close the valve, pour the bloom water through the dispersion cap, then let the coffee bloom for your recipe's time.",
                    conciseInstruction = "Close valve. Pour bloom water. Let it bloom.",
                    explanation =
                        "Closing the valve retains water during the bloom instead of letting it drain immediately.",
                    altText =
                        "A hand closing the valve on a Pulsar brewer while bloom water is retained above the coffee bed.",
                ),
                visibility = visibilityAt(*GuidancePresentationLevel.entries.toTypedArray()),
            ),
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_manual_brew"),
                familyId = familyId,
                profileId = profileId,
                stageId = StageId("pulsar_manual_brew"),
                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                text = GuidanceTextMetadata(
                    primaryInstruction =
                        "Open the valve for drawdown, then add the remaining water through the dispersion cap according to your recipe.",
                    conciseInstruction = "Open valve and continue to your recipe's water target.",
                    tip = "Watch the liquid level and pause pouring before it nears the rim.",
                    altText =
                        "A hand opening the valve on a Pulsar brewer on a stable server while coffee draws down into the server.",
                ),
                visibility = visibilityAt(*GuidancePresentationLevel.entries.toTypedArray()),
            ),
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_live_targets"),
                familyId = familyId,
                profileId = profileId,
                placement = BuiltInGuidancePlacement.UTILITY,
                text = GuidanceTextMetadata(
                    primaryInstruction =
                        "Keep your selected coffee and water targets visible while you brew.",
                    conciseInstruction = "Watch the recipe targets on your scale and timer.",
                    explanation =
                        "The scale and timer show progress; the selected recipe remains the source of the target amounts and times.",
                    altText =
                        "A Pulsar brewer on a scale beside visible coffee, water, and timer targets.",
                ),
                visibility = visibilityAt(*GuidancePresentationLevel.entries.toTypedArray()),
            ),
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_finish"),
                familyId = familyId,
                profileId = profileId,
                placement = BuiltInGuidancePlacement.COMPLETION,
                text = GuidanceTextMetadata(
                    primaryInstruction =
                        "When drawdown is complete, remove the brewer carefully and serve the coffee.",
                    conciseInstruction = "When drawdown is complete, remove the brewer and serve.",
                    altText =
                        "A hand carefully lifting a Pulsar brewer from its server after drawdown has finished.",
                ),
                visibility = visibilityAt(
                    GuidancePresentationLevel.FULL,
                    GuidancePresentationLevel.CONCISE,
                    GuidancePresentationLevel.CUSTOM,
                ),
            ),
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_hot_liquid_safety"),
                familyId = familyId,
                profileId = profileId,
                placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
                text = GuidanceTextMetadata(
                    primaryInstruction = "Keep the brewer and server stable while handling hot water and coffee.",
                    warning =
                        "Hot water and brewed coffee can burn. Keep hands clear of the hot liquid and use a stable surface.",
                    altText =
                        "A stable Pulsar brewing setup on a level surface with hands kept clear of hot water and coffee.",
                ),
                visibility = alwaysVisibleSafety(),
                safetyCritical = true,
            ),
            BuiltInGuidanceContent(
                id = StageContentId("pulsar_overflow_safety"),
                familyId = familyId,
                profileId = profileId,
                stageId = StageId("pulsar_manual_brew"),
                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                text = GuidanceTextMetadata(
                    primaryInstruction = "Pause pouring before the liquid reaches the rim.",
                    warning =
                        "Do not overfill the brewer. Stop pouring if the liquid level approaches the rim or the server is unstable.",
                    altText =
                        "A Pulsar brewer on a stable server with the liquid level safely below the rim.",
                ),
                visibility = alwaysVisibleSafety(),
                safetyCritical = true,
            ),
        ),
    )

    private fun visibilityAt(
        vararg levels: GuidancePresentationLevel,
    ): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(visibleIn = levels.toSet())

    private fun alwaysVisibleSafety(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = emptySet(),
        alwaysVisible = true,
    )
}
