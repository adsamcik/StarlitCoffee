package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfile
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity

/**
 * A planned visual is intentionally not an [InstructionAssetRecord]. No
 * drawable, localized companion copy, or review approval exists yet, so it
 * cannot be rendered or treated as release-ready. The stable ID makes the
 * missing work explicit and lets a future reviewed manifest use the same slot.
 */
enum class P1PlannedInstructionAssetStatus {
    NOT_PRODUCED,
}

data class P1PlannedInstructionAsset(
    val id: InstructionAssetId,
    val familyId: MethodFamilyId,
    val profileId: BrewerProfileId,
    val stageId: StageId,
    val contentId: StageContentId,
    val mandatoryForFullGuidance: Boolean = true,
    val status: P1PlannedInstructionAssetStatus = P1PlannedInstructionAssetStatus.NOT_PRODUCED,
) {
    init {
        require(id.value == expectedResourceName()) {
            "Planned P1 asset '${id.value}' must use '${expectedResourceName()}'"
        }
    }

    /** Matches the resource stem expected by [InstructionAssetRecord] later. */
    fun expectedResourceName(): String = buildString {
        append("instruction_")
        append(familyId.value)
        append('_')
        append(profileId.value)
        append('_')
        append(contentId.value)
        append("_default")
    }
}

/** A catalog record pairs authored text with its explicitly absent visual slot. */
data class P1GuidanceCatalogEntry(
    val content: BuiltInGuidanceContent,
    val plannedVisualAsset: P1PlannedInstructionAsset? = null,
)

/**
 * Shared P1 content for the seven built-in stage plans.
 *
 * Source-stage definitions remain the source of execution truth. This catalog
 * derives its live-stage entries from those definitions and fails at startup if
 * a newly introduced source stage has no reviewed text or visual plan. This is
 * deliberate: a new stage must never borrow copy or an illustration from a
 * different brewer profile.
 */
object P1BuiltInGuidanceCatalog {

    val entries: List<P1GuidanceCatalogEntry> by lazy(::buildEntries)

    /** Pass this directly alongside the Pulsar catalog to the session resolver. */
    val catalog: BuiltInGuidanceCatalog
        get() = BuiltInGuidanceCatalog(entries.map(P1GuidanceCatalogEntry::content))

    val plannedVisualAssets: List<P1PlannedInstructionAsset>
        get() = entries.mapNotNull(
            P1GuidanceCatalogEntry::plannedVisualAsset,
        )

    val supportedProfileIds: Set<BrewerProfileId>
        get() = entries
            .mapNotNull { entry -> entry.content.profileId }
            .toSet()

    /**
     * Profiles are exposed only when every mandatory planned slot has an exact,
     * approved record in the packaged instruction-asset manifest. The plan
     * status is planning evidence; the reviewed manifest is the release
     * authority.
     */
    val releaseEligibleProfileIds: Set<BrewerProfileId>
        get() = releaseEligibleProfileIds(BuiltInInstructionAssetCatalog.catalog)

    /**
     * Testable fail-closed release gate for a supplied reviewed asset catalog.
     * This lets approved artwork unlock only its exact family/profile/stage
     * slot, never a nearby brewer or similarly named instruction.
     */
    fun releaseEligibleProfileIds(
        instructionAssets: InstructionAssetCatalog,
    ): Set<BrewerProfileId> = supportedProfileIds.filterTo(linkedSetOf()) { profileId ->
        val mandatoryAssets = plannedVisualAssets.filter { asset ->
            asset.profileId == profileId && asset.mandatoryForFullGuidance
        }
        mandatoryAssets.isNotEmpty() && mandatoryAssets.all { plannedAsset ->
            instructionAssets.find(plannedAsset.id)?.isApprovedFor(plannedAsset) == true
        }
    }

    /**
     * True only for a persisted P1 profile whose approved visual curriculum is
     * absent.
     */
    fun isReleaseGatedProfile(
        rawProfileId: String,
        instructionAssets: InstructionAssetCatalog = BuiltInInstructionAssetCatalog.catalog,
    ): Boolean {
        val profileId = runCatching { BrewerProfileId(rawProfileId) }.getOrNull() ?: return false
        return profileId in supportedProfileIds &&
            profileId !in releaseEligibleProfileIds(instructionAssets)
    }

    fun plannedVisualAssetFor(contentId: StageContentId): P1PlannedInstructionAsset? =
        plannedVisualAssets.find { asset -> asset.contentId == contentId }

    private fun InstructionAssetRecord.isApprovedFor(
        plannedAsset: P1PlannedInstructionAsset,
    ): Boolean = review.isApproved &&
        mandatoryForFullGuidance &&
        id == plannedAsset.id &&
        familyId == plannedAsset.familyId &&
        profileId == plannedAsset.profileId &&
        stageId == plannedAsset.stageId &&
        contentId == plannedAsset.contentId

    private fun buildEntries(): List<P1GuidanceCatalogEntry> {
        val sourceStages = sourceStages()
        val sourceContentIds = sourceStages.map { source -> source.definition.contentId }.toSet()
        require(stageCopyByContentId.keys == sourceContentIds) {
            "P1 guidance must cover every source-stage content ID without extras"
        }
        val safetyStageIds = sourceStages
            .filter { source -> source.definition.safetyMessages.isNotEmpty() }
            .mapTo(linkedSetOf()) { source -> source.definition.contentId }
        require(safetyCopyByContentId.keys == safetyStageIds) {
            "P1 guidance must provide visible context for every safety-bearing stage"
        }

        val stageEntries = sourceStages.map { source ->
            val copy = requireNotNull(stageCopyByContentId[source.definition.contentId])
            val content = BuiltInGuidanceContent(
                id = source.definition.contentId,
                familyId = source.profile.familyId,
                profileId = source.profile.id,
                stageId = source.definition.id,
                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                text = GuidanceTextMetadata(
                    primaryInstruction = copy.primaryInstruction,
                    conciseInstruction = copy.conciseInstruction,
                    explanation = copy.explanation,
                    tip = copy.tip,
                    altText = copy.altText,
                ),
                visibility = standardVisibility(),
            )
            P1GuidanceCatalogEntry(
                content = content,
                plannedVisualAsset = plannedVisualAsset(source),
            )
        }
        val stageSafetyEntries = sourceStages.mapNotNull { source ->
            safetyCopyByContentId[source.definition.contentId]?.let { copy ->
                P1GuidanceCatalogEntry(
                    content = BuiltInGuidanceContent(
                        id = StageContentId("${source.definition.contentId.value}_safety"),
                        familyId = source.profile.familyId,
                        profileId = source.profile.id,
                        stageId = source.definition.id,
                        placement = BuiltInGuidancePlacement.LIVE_STAGE,
                        text = GuidanceTextMetadata(
                            primaryInstruction = copy.primaryInstruction,
                            warning = copy.warning,
                            altText = copy.altText,
                        ),
                        visibility = alwaysVisibleSafety(),
                        safetyCritical = source.definition.safetyMessages.any { message ->
                            message.severity == StageSafetySeverity.CRITICAL
                        },
                    ),
                )
            }
        }
        val profileEntries = profileSafetyById.map { (profileId, safety) ->
            val profile = profile(profileId)
            listOf(
                P1GuidanceCatalogEntry(
                    content = BuiltInGuidanceContent(
                        id = StageContentId("${profile.id.value}_global_safety"),
                        familyId = profile.familyId,
                        profileId = profile.id,
                        placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
                        text = GuidanceTextMetadata(
                            primaryInstruction = safety.primaryInstruction,
                            warning = safety.warning,
                            altText = safety.altText,
                        ),
                        visibility = alwaysVisibleSafety(),
                        safetyCritical = true,
                    ),
                ),
                P1GuidanceCatalogEntry(
                    content = BuiltInGuidanceContent(
                        id = StageContentId("${profile.id.value}_live_targets"),
                        familyId = profile.familyId,
                        profileId = profile.id,
                        placement = BuiltInGuidancePlacement.UTILITY,
                        text = GuidanceTextMetadata(
                            primaryInstruction =
                                "Keep the quantities and observations from your selected recipe visible while you brew.",
                            conciseInstruction = "Keep the selected recipe targets visible.",
                            explanation =
                                "The recipe is the source of its own amounts and timing; this guidance does not replace it with a generic preset.",
                            altText = "A brewer beside a scale and the selected recipe targets.",
                        ),
                        visibility = standardVisibility(),
                    ),
                ),
            )
        }.flatten()
        return stageEntries + stageSafetyEntries + profileEntries
    }

    private fun sourceStages(): List<P1SourceStage> {
        val sources = sourcePlans()
            .flatMap { source ->
                collectStages(source.plan.nodes).map { definition ->
                    P1SourceStage(source.profile, definition)
                }
            }
            .groupBy { source -> source.definition.contentId }
            .map { (contentId, duplicates) ->
                require(duplicates.map(P1SourceStage::definition).distinct().size == 1) {
                    "P1 plans disagree about duplicate source stage '${contentId.value}'"
                }
                duplicates.first()
            }
        return sources
    }

    private fun sourcePlans(): List<P1SourcePlan> = listOf(
        sourcePlan("clever_style"),
        sourcePlan("hario_switch", HarioSwitchWorkflow.STEEP_AND_RELEASE),
        sourcePlan("hario_switch", HarioSwitchWorkflow.MANUAL_GRAVITY),
        sourcePlan("valve_release_generic"),
        sourcePlan("cezve_generic"),
        sourcePlan("automatic_batch_generic"),
        sourcePlan("automatic_single_cup_generic"),
        sourcePlan("vietnamese_phin"),
    )

    private fun sourcePlan(
        rawProfileId: String,
        workflow: HarioSwitchWorkflow? = null,
    ): P1SourcePlan {
        val profileId = BrewerProfileId(rawProfileId)
        return P1SourcePlan(
            profile = profile(profileId),
            plan = requireNotNull(BuiltinBrewerStagePlanFactory.create(profileId, workflow)) {
                "P1 guidance has no source plan for $rawProfileId"
            },
        )
    }

    private fun profile(profileId: BrewerProfileId): BrewerProfile = requireNotNull(
        BuiltinBrewingCatalog.instance.findBrewerProfile(profileId),
    ) {
        "P1 guidance has no built-in profile for ${profileId.value}"
    }

    private fun plannedVisualAsset(source: P1SourceStage): P1PlannedInstructionAsset {
        val contentId = source.definition.contentId
        return P1PlannedInstructionAsset(
            id = InstructionAssetId(
                "instruction_${source.profile.familyId.value}_${source.profile.id.value}_${contentId.value}_default",
            ),
            familyId = source.profile.familyId,
            profileId = source.profile.id,
            stageId = source.definition.id,
            contentId = contentId,
        )
    }

    private fun collectStages(nodes: List<StagePlanNode>): List<BrewStageDefinition> = nodes.flatMap { node ->
        when (node) {
            is StagePlanNode.Stage -> listOf(node.definition)
            is StagePlanNode.OptionalSection -> collectStages(node.nodes)
            is StagePlanNode.BoundedRepeat -> collectStages(node.nodes)
        }
    }

    private fun standardVisibility(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = GuidancePresentationLevel.entries.toSet(),
    )

    private fun alwaysVisibleSafety(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = emptySet(),
        alwaysVisible = true,
    )

    private data class P1SourcePlan(
        val profile: BrewerProfile,
        val plan: BrewStagePlan,
    )

    private data class P1SourceStage(
        val profile: BrewerProfile,
        val definition: BrewStageDefinition,
    )

    private data class P1StageCopy(
        val primaryInstruction: String,
        val conciseInstruction: String,
        val explanation: String? = null,
        val altText: String,
        val tip: String? = null,
    )

    private data class P1SafetyCopy(
        val primaryInstruction: String,
        val warning: String,
        val altText: String,
    )

    private val stageCopyByContentId: Map<StageContentId, P1StageCopy> = mapOf(
        copy("clever_style_insert_and_rinse_filter", "Fit the filter selected for this Clever-style brewer, then rinse it with hot water.", "Fit and rinse the selected filter.", "Use only the filter configuration selected for this brewer; this generic profile does not establish a universal paper size.", "A Clever-style brewer with a selected filter being rinsed over a stable server."),
        copy("clever_style_close_valve", "Set the brewer to its retained-water position according to its own instructions.", "Set the brewer for retained water.", "Clever-style hardware can differ, so follow the selected brewer's documented control rather than assuming another brewer's mechanism.", "A Clever-style brewer prepared in its retained-water position."),
        copy("clever_style_add_coffee", "Add the measured coffee from your selected recipe and level the bed.", "Add and level the coffee.", altText = "Measured coffee being added to a Clever-style brewer."),
        copy("clever_style_add_water", "Pour the selected water amount over the coffee without filling beyond a stable working level.", "Pour the selected water amount.", "The app does not infer capacity for this generic profile.", "Water being poured into a Clever-style brewer below the rim."),
        copy("clever_style_agitate", "Agitate only as your selected recipe calls for.", "Agitate as your recipe calls for.", altText = "A gentle agitation motion in a Clever-style brewer."),
        copy("clever_style_steep", "Let the coffee steep until you are ready to continue with your selected recipe.", "Let the coffee steep.", "No universal steep time is claimed for this generic profile.", "Coffee steeping in a Clever-style brewer."),
        copy("clever_style_place_on_server", "Place the brewer on a stable server to begin the release described by its manufacturer.", "Place the brewer on a stable server.", "Use only the release action documented for this brewer.", "A Clever-style brewer being placed securely on a server."),
        copy("clever_style_observe_drawdown", "Watch for drawdown to finish before removing the brewer.", "Wait for drawdown to finish.", altText = "Coffee drawing down from a Clever-style brewer into a server."),
        copy("clever_style_remove_and_serve", "Remove the brewer carefully and serve the coffee.", "Remove carefully and serve.", altText = "A Clever-style brewer being removed from a server after drawdown."),

        copy("hario_switch_insert_and_rinse_filter", "Fit the selected V60-compatible filter in the Hario Switch and rinse it with hot water.", "Fit and rinse the selected filter.", "Choose the filter for the selected Switch configuration; do not infer a capacity from this step.", "A Hario Switch with a selected paper filter being rinsed."),
        copy("hario_switch_close_valve", "Close the Hario Switch valve before the immersion brew begins.", "Close the Switch valve.", "The closed valve retains water for the selected immersion workflow.", "A hand closing the valve on a Hario Switch."),
        copy("hario_switch_add_coffee", "Add the measured coffee from your selected recipe and level the bed.", "Add and level the coffee.", altText = "Measured coffee being added to a Hario Switch."),
        copy("hario_switch_add_water", "Pour the selected water amount over the coffee while the valve remains closed.", "Pour the selected water amount.", "Use the recipe's amount rather than a generic Switch capacity.", "Water being poured into a Hario Switch with its valve closed."),
        copy("hario_switch_agitate", "Agitate only as your selected recipe calls for while the valve remains closed.", "Agitate as your recipe calls for.", altText = "A gentle agitation motion in a Hario Switch with the valve closed."),
        copy("hario_switch_steep", "Let the coffee steep according to your selected recipe before opening the valve.", "Let the coffee steep.", "A selected recipe may define its own timing; this catalog does not impose one.", "Coffee steeping in a Hario Switch with the valve closed."),
        copy("hario_switch_open_valve", "Open the Hario Switch valve to begin drawdown into the server.", "Open the Switch valve for drawdown.", "The valve transition is specific to the Switch immersion workflow.", "A hand opening the valve on a Hario Switch above a stable server."),
        copy("hario_switch_observe_drawdown", "Watch for drawdown to finish before removing the Switch.", "Wait for drawdown to finish.", altText = "Coffee drawing down from a Hario Switch into a server."),
        copy("hario_switch_remove_and_serve", "Remove the Hario Switch carefully and serve the coffee.", "Remove carefully and serve.", altText = "A Hario Switch being removed from a server after drawdown."),
        copy("hario_switch_open_valve_for_manual_gravity", "Keep the Hario Switch valve open before brewing it as a gravity dripper.", "Keep the Switch valve open.", "This is the manual-gravity workflow, not the immersion workflow.", "A Hario Switch with its valve open for gravity brewing."),
        copy("hario_switch_pour_water", "Pour water in the pattern and amount your selected recipe calls for while the valve stays open.", "Pour to the selected recipe target.", "The Switch now behaves as a gravity dripper; no universal pulse pattern is assumed.", "Water being poured into a Hario Switch with its valve open."),

        copy("valve_release_generic_insert_and_rinse_filter", "Fit the filter selected for this valve-release brewer, then rinse it with hot water.", "Fit and rinse the selected filter.", "This generic profile does not establish a universal filter size or material.", "A valve-release brewer with a selected filter being rinsed."),
        copy("valve_release_generic_close_valve", "Set the brewer to the retained-water position described by its manufacturer.", "Set the brewer for retained water.", "Do not assume that another brewer's valve motion applies to this generic profile.", "A generic valve-release brewer prepared to retain water."),
        copy("valve_release_generic_add_coffee", "Add the measured coffee from your selected recipe and level the bed.", "Add and level the coffee.", altText = "Measured coffee being added to a valve-release brewer."),
        copy("valve_release_generic_add_water", "Pour the selected water amount over the coffee without exceeding a stable working level.", "Pour the selected water amount.", "Capacity remains a property of the actual brewer configuration.", "Water being poured into a valve-release brewer below the rim."),
        copy("valve_release_generic_agitate", "Agitate only as your selected recipe calls for.", "Agitate as your recipe calls for.", altText = "A gentle agitation motion in a valve-release brewer."),
        copy("valve_release_generic_steep", "Let the coffee steep according to your selected recipe.", "Let the coffee steep.", "This generic profile does not claim a fixed steep time.", "Coffee steeping in a valve-release brewer."),
        copy("valve_release_generic_open_valve", "Use the selected brewer's documented release control to begin drawdown.", "Begin drawdown with the documented control.", "Do not infer a specific release motion or fixed drawdown plan for this generic brewer.", "A hand operating the documented release control on a valve-release brewer."),
        copy("valve_release_generic_observe_drawdown", "Watch for drawdown to finish before removing the brewer.", "Wait for drawdown to finish.", altText = "Coffee drawing down from a valve-release brewer into a server."),
        copy("valve_release_generic_remove_and_serve", "Remove the brewer carefully and serve the coffee.", "Remove carefully and serve.", altText = "A valve-release brewer being removed from a server after drawdown."),

        copy("cezve_generic_select_pot_capacity", "Choose a cezve or ibrik and confirm that your selected recipe fits it safely.", "Choose a vessel that fits the recipe.", "This generic profile does not claim one universal vessel capacity.", "A small cezve or ibrik beside a measured recipe."),
        copy("cezve_generic_add_water", "Add the water amount from your selected recipe to the vessel.", "Add the selected water amount.", altText = "Measured water being added to a cezve or ibrik."),
        copy("cezve_generic_add_finely_ground_coffee", "Add the coffee amount and grind specified by your selected recipe.", "Add the selected coffee.", "This catalog does not present one regional preparation as universally authoritative.", "Coffee being added to a cezve or ibrik."),
        copy("cezve_generic_add_sugar_before_heating", "If your own selected recipe includes sugar, add it before heating.", "Add recipe sugar before heating.", altText = "Sugar being added to a cezve or ibrik before heating."),
        copy("cezve_generic_mix_before_heating", "Stir only enough to combine the selected ingredients before heating.", "Stir to combine before heating.", altText = "A spoon gently mixing ingredients in a cezve or ibrik."),
        copy("cezve_generic_apply_gentle_heat", "Apply heat appropriate for your heat source and watch the vessel continuously.", "Apply heat and watch continuously.", "Foam rise is an observed event, not a timer or an automatic completion signal.", "A cezve or ibrik attended closely while being heated."),
        copy("cezve_generic_observe_foam_rising", "Watch for foam to rise and record the observation when it happens.", "Observe the foam rise.", altText = "Foam beginning to rise in an attended cezve or ibrik."),
        copy("cezve_generic_reduce_or_remove_heat", "Reduce or remove heat when foam approaches the rim.", "Reduce or remove heat at the rim.", "Act on the visible foam and vessel state rather than waiting for a generic time.", "A hand safely reducing heat beneath a cezve or ibrik."),
        copy("cezve_generic_pour_with_foam", "Pour carefully into the selected serving vessel when it is safe to do so.", "Pour carefully when safe.", altText = "A cezve or ibrik being poured carefully into a cup."),
        copy("cezve_generic_allow_grounds_to_settle", "Allow the grounds to settle according to your own selected recipe or preference.", "Allow grounds to settle.", "No universal settling duration is claimed for this generic profile.", "A served cup resting while coffee grounds settle."),

        copy("automatic_batch_generic_select_filter_and_basket", "Use the filter and basket specified for your particular batch brewer.", "Use the machine's specified filter and basket.", "Generic automatic brewers do not share one filter size or basket configuration.", "A batch brewer basket with its selected machine-specific filter."),
        copy("automatic_batch_generic_rinse_filter", "If your machine documentation calls for it, rinse the selected filter before brewing.", "Rinse the filter if your machine calls for it.", altText = "A selected filter being rinsed in an automatic batch brewer basket."),
        copy("automatic_batch_generic_add_coffee", "Add the measured coffee from your selected recipe to the basket.", "Add the selected coffee.", altText = "Measured coffee being added to an automatic batch brewer basket."),
        copy("automatic_batch_generic_level_coffee_bed", "Level the coffee bed gently before starting the machine.", "Gently level the coffee bed.", altText = "A level coffee bed in an automatic batch brewer basket."),
        copy("automatic_batch_generic_add_reservoir_water", "Add the selected reservoir water amount without exceeding your machine's marked limit.", "Add the selected reservoir water.", "The app does not infer reservoir capacity for a generic machine.", "Water being added to an automatic batch brewer reservoir below its marked limit."),
        copy("automatic_batch_generic_start_machine", "Start the machine using its own controls and keep clear of hot coffee.", "Start the machine and keep clear.", "The machine owns its internal cycle; this app only tracks the visible preparation and completion.", "A hand starting an automatic batch brewer while clear of hot liquid."),
        copy("automatic_batch_generic_observe_machine_completion", "Wait for your machine's own completion indicator or visible end of flow.", "Observe the machine's completion.", "Do not substitute a universal timer, auto-off claim, or heat-plate behavior for the machine's instructions.", "An automatic batch brewer showing its own completion indicator."),
        copy("automatic_batch_generic_stir_or_swirl_carafe", "If it is safe for your carafe and machine, gently swirl before serving.", "Gently swirl only if safe.", altText = "A stable batch-brewer carafe being gently swirled."),
        copy("automatic_batch_generic_serve", "Serve carefully from the carafe and avoid contact with hot surfaces.", "Serve carefully from the carafe.", altText = "Coffee being served carefully from a batch-brewer carafe."),

        copy("automatic_single_cup_generic_select_and_insert_filter", "Use the filter configuration specified for your particular single-cup brewer.", "Use the machine's specified filter.", "Generic single-cup brewers do not share one filter size or configuration.", "A single-cup brewer with its selected machine-specific filter."),
        copy("automatic_single_cup_generic_rinse_filter", "If your machine documentation calls for it, rinse the selected filter before brewing.", "Rinse the filter if your machine calls for it.", altText = "A selected filter being rinsed in a single-cup brewer."),
        copy("automatic_single_cup_generic_add_coffee", "Add the measured coffee from your selected recipe.", "Add the selected coffee.", altText = "Measured coffee being added to a single-cup brewer."),
        copy("automatic_single_cup_generic_level_coffee_bed", "Level the coffee bed gently before starting the machine.", "Gently level the coffee bed.", altText = "A level coffee bed in a single-cup brewer."),
        copy("automatic_single_cup_generic_add_reservoir_water", "Add the selected reservoir water amount without exceeding your machine's marked limit.", "Add the selected reservoir water.", "The app does not infer reservoir capacity for a generic machine.", "Water being added to a single-cup brewer reservoir below its marked limit."),
        copy("automatic_single_cup_generic_start_machine", "Start the machine using its own controls and keep clear of hot coffee.", "Start the machine and keep clear.", "The machine owns its internal cycle; this app does not simulate it with a generic timer.", "A hand starting a single-cup brewer while clear of hot liquid."),
        copy("automatic_single_cup_generic_observe_machine_completion", "Wait for your machine's own completion indicator or visible end of flow.", "Observe the machine's completion.", "Do not substitute a universal timer or auto-off claim for the machine's instructions.", "A single-cup brewer showing its own completion indicator."),
        copy("automatic_single_cup_generic_serve", "Serve carefully and avoid contact with hot coffee or machine surfaces.", "Serve carefully.", altText = "Coffee being served carefully from a single-cup brewer."),

        copy("vietnamese_phin_place_on_stable_cup", "Place the phin on a stable cup or server before adding anything.", "Place the phin on a stable cup.", "A stable support reduces the risk of a hot spill during the slow extraction.", "A Vietnamese phin resting securely on a stable cup."),
        copy("vietnamese_phin_add_coffee", "Add the measured coffee from your selected recipe to the phin.", "Add the selected coffee.", altText = "Measured coffee being added to a Vietnamese phin."),
        copy("vietnamese_phin_level_coffee", "Level the coffee gently without assuming a universal packing method.", "Gently level the coffee.", "Phin insert geometry varies, so this guidance does not prescribe one packing technique.", "A level coffee bed in a Vietnamese phin."),
        copy("vietnamese_phin_place_gravity_or_screw_insert", "Fit the gravity or screw insert exactly as documented for your selected phin.", "Fit the selected phin insert.", "Do not generalize a screw-insert arrangement to every phin.", "A Vietnamese phin with its documented insert placed over the coffee."),
        copy("vietnamese_phin_pre_wet", "Add the small initial pour from your selected recipe and let the coffee wet evenly.", "Add the selected initial pour.", "Use the selected recipe's amount rather than a universal pre-wet quantity.", "A small initial pour wetting coffee in a Vietnamese phin."),
        copy("vietnamese_phin_fill_chamber", "Add the remaining water without overfilling the phin or destabilising the cup.", "Fill carefully without overfilling.", "The app does not infer a universal phin capacity.", "Water being added carefully to a Vietnamese phin below the rim."),
        copy("vietnamese_phin_cover", "Cover the phin while the coffee begins to drip.", "Cover the phin.", altText = "A covered Vietnamese phin resting on a cup."),
        copy("vietnamese_phin_observe_first_drip", "Observe the first drip rather than waiting for a universal time.", "Observe the first drip.", "First drip is a useful visible cue, not a timing guarantee.", "The first drop of coffee leaving a Vietnamese phin."),
        copy("vietnamese_phin_check_drip_rate", "Check the drip rate and adjust only if your selected phin guidance supports an adjustment.", "Check the drip rate.", "Do not assume every phin uses the same insert or adjustment method.", "Coffee dripping steadily from a Vietnamese phin into a cup."),
        copy("vietnamese_phin_observe_drip_completion", "Observe when dripping has finished before removing the phin.", "Observe when dripping finishes.", altText = "A Vietnamese phin after the final drip has fallen into a cup."),
        copy("vietnamese_phin_remove_hot_filter", "Remove the phin carefully by a safe handling point after dripping finishes.", "Remove the phin carefully.", "Metal parts may remain hot after extraction.", "A hand safely removing a hot Vietnamese phin from a cup."),
        copy("vietnamese_phin_collect_concentrate", "Collect the brewed concentrate; any serving additions remain separate from extraction.", "Collect the brewed concentrate.", "Do not treat concentrate as an automatically calculated ready-to-drink beverage.", "Collected coffee concentrate in a cup beneath a Vietnamese phin."),
    )

    private val safetyCopyByContentId: Map<StageContentId, P1SafetyCopy> = mapOf(
        safety("clever_style_place_on_server", "Keep the brewer and server stable as drawdown begins.", "Hot coffee can burn and an unstable brewer can spill. Keep hands clear of hot liquid and use a level surface.", "A stable Clever-style brewer on a level server."),
        safety("hario_switch_open_valve", "Keep the Hario Switch and server stable while opening the valve.", "Hot glass and hot coffee can burn. Do not open the valve if the brewer or server is unstable.", "A stable Hario Switch and server during drawdown."),
        safety("hario_switch_pour_water", "Keep the Hario Switch and server stable while pouring.", "Hot glass and hot coffee can burn. Pause pouring if the vessel becomes unstable or liquid approaches the rim.", "A stable Hario Switch with liquid safely below the rim."),
        safety("valve_release_generic_open_valve", "Keep the brewer and server stable while beginning drawdown.", "Hot coffee can burn and an unstable brewer can spill. Pause if liquid approaches the rim or the vessel is unstable.", "A stable valve-release brewer on a server during drawdown."),
        safety("cezve_generic_apply_gentle_heat", "Stay with the cezve or ibrik while heat is applied.", "Never leave the vessel unattended. Open flame, hot metal, rapid boil-over, and an unstable small vessel can cause burns or spills.", "A cezve or ibrik attended continuously on a safe heat source."),
        safety("cezve_generic_reduce_or_remove_heat", "Reduce or remove heat before foam can boil over.", "Hot metal and hot liquid can burn. Keep the small vessel stable while reducing heat and avoid a rapid boil-over.", "A stable cezve or ibrik being moved safely away from heat."),
        safety("cezve_generic_pour_with_foam", "Keep hands clear of hot liquid while pouring.", "Hot coffee can burn. Pour only from a stable vessel into a stable serving cup.", "A stable cezve or ibrik pouring coffee carefully into a cup."),
        safety("automatic_batch_generic_start_machine", "Keep clear of hot coffee while starting the machine.", "Hot coffee can burn. Follow your machine's safety instructions and keep the carafe or server stable.", "A stable automatic batch brewer being started safely."),
        safety("automatic_batch_generic_serve", "Keep clear of hot coffee and hot machine surfaces while serving.", "Hot coffee can burn. Use a stable carafe and follow the machine's safety instructions.", "Coffee being served safely from a stable batch-brewer carafe."),
        safety("automatic_single_cup_generic_start_machine", "Keep clear of hot coffee while starting the machine.", "Hot coffee can burn. Follow your machine's safety instructions and keep the cup stable.", "A stable single-cup brewer being started safely."),
        safety("automatic_single_cup_generic_serve", "Keep clear of hot coffee and hot machine surfaces while serving.", "Hot coffee can burn. Keep the cup stable and follow the machine's safety instructions.", "Coffee being served safely from a stable single-cup brewer."),
        safety("vietnamese_phin_place_on_stable_cup", "Keep the phin and cup stable before pouring hot water.", "An unstable cup or phin can spill hot liquid. Use a level surface and stop if the setup is unstable.", "A Vietnamese phin resting securely on a stable cup."),
        safety("vietnamese_phin_fill_chamber", "Keep hands clear of hot water while filling the phin.", "Hot water can burn. Do not overfill the phin or use an unstable cup or server.", "A Vietnamese phin filled carefully below the rim on a stable cup."),
        safety("vietnamese_phin_remove_hot_filter", "Handle the phin carefully after extraction.", "The metal filter can remain hot and cause burns. Use a safe handling point and keep the cup stable.", "A hand safely removing a hot Vietnamese phin from a stable cup."),
    )

    private val profileSafetyById: Map<BrewerProfileId, P1SafetyCopy> = mapOf(
        BrewerProfileId("clever_style") to P1SafetyCopy("Keep the brewer and server stable while handling hot coffee.", "Hot water and coffee can burn. Use a stable surface and keep hands clear of hot liquid.", "A stable Clever-style brewer and server on a level surface."),
        BrewerProfileId("hario_switch") to P1SafetyCopy("Keep the Hario Switch and server stable while handling hot coffee.", "Hot glass, water, and coffee can burn. Use a stable surface and keep hands clear of hot liquid.", "A stable Hario Switch and server on a level surface."),
        BrewerProfileId("valve_release_generic") to P1SafetyCopy("Keep the brewer and server stable while handling hot coffee.", "Hot water and coffee can burn. Use a stable surface and keep hands clear of hot liquid.", "A stable valve-release brewer and server on a level surface."),
        BrewerProfileId("cezve_generic") to P1SafetyCopy("Attend the cezve or ibrik whenever heat is applied.", "Open flame or a hot hob, hot metal, and hot liquid can burn. Keep the small vessel stable and never leave it unattended.", "A cezve or ibrik attended on a safe heat source."),
        BrewerProfileId("automatic_batch_generic") to P1SafetyCopy("Keep clear of hot coffee and follow your machine's safety instructions.", "Hot coffee and machine surfaces can burn. Keep the carafe stable and follow the selected machine's documentation.", "A stable automatic batch brewer with a carafe on a level surface."),
        BrewerProfileId("automatic_single_cup_generic") to P1SafetyCopy("Keep clear of hot coffee and follow your machine's safety instructions.", "Hot coffee and machine surfaces can burn. Keep the cup stable and follow the selected machine's documentation.", "A stable automatic single-cup brewer with a cup on a level surface."),
        BrewerProfileId("vietnamese_phin") to P1SafetyCopy("Keep the phin and cup stable while handling hot water and metal.", "Hot water, coffee, and metal can burn. Use a stable cup or server and keep hands clear of hot surfaces.", "A Vietnamese phin resting stably on a cup on a level surface."),
    )

    private fun copy(
        rawContentId: String,
        primaryInstruction: String,
        conciseInstruction: String,
        explanation: String? = null,
        altText: String,
        tip: String? = null,
    ): Pair<StageContentId, P1StageCopy> = StageContentId(rawContentId) to P1StageCopy(
        primaryInstruction = primaryInstruction,
        conciseInstruction = conciseInstruction,
        explanation = explanation,
        tip = tip,
        altText = altText,
    )

    private fun safety(
        rawContentId: String,
        primaryInstruction: String,
        warning: String,
        altText: String,
    ): Pair<StageContentId, P1SafetyCopy> = StageContentId(rawContentId) to P1SafetyCopy(
        primaryInstruction = primaryInstruction,
        warning = warning,
        altText = altText,
    )
}
