package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfile
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.LegacyStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode

/**
 * Profile-scoped curriculum for the calculator's seven legacy brewing-method
 * aliases.
 *
 * Pulsar's reviewed curriculum already lives in [PulsarBuiltInGuidanceCatalog].
 * This catalogue composes it with the six remaining aliases instead of copying
 * its IDs or silently substituting Pulsar copy for another brewer. Consumers
 * should use this one [catalog] in place of the separate Pulsar catalogue when
 * they need coverage for all seven legacy methods.
 *
 * Source stage plans remain the execution authority. The content below is
 * checked against [LegacyStagePlanFactory] as it is built, so a future stage
 * cannot become runnable without its own stage-linked instruction. Recipe
 * quantities, temperatures, and timing continue to come from the durable
 * recipe snapshot; this copy deliberately does not invent a generic recipe.
 */
object LegacyBuiltInGuidanceCatalog {

    /** Stable legacy aliases and the exact built-in profile each one resolves to. */
    val legacyMethodProfileIds: Map<BrewMethod, BrewerProfileId> = linkedMapOf(
        BrewMethod.PULSAR to BrewerProfileId("pulsar_standard"),
        BrewMethod.V60 to BrewerProfileId("v60_unspecified"),
        BrewMethod.FRENCH_PRESS to BrewerProfileId("french_press_generic"),
        BrewMethod.AEROPRESS to BrewerProfileId("aeropress_standard"),
        BrewMethod.ESPRESSO to BrewerProfileId("espresso_pump_generic"),
        BrewMethod.MOKA_POT to BrewerProfileId("moka_generic_unspecified"),
        BrewMethod.COLD_BREW to BrewerProfileId("cold_immersion_generic"),
    )

    /** The composed, deterministic source for all legacy aliases. */
    val catalog: BuiltInGuidanceCatalog by lazy(::buildCatalog)

    val supportedProfileIds: Set<BrewerProfileId>
        get() = legacyMethodProfileIds.values.toSet()

    private fun buildCatalog(): BuiltInGuidanceCatalog {
        val sources = sourceStages()
        val pulsarSources = sources.filter { source -> source.method == BrewMethod.PULSAR }
        val authoredSources = sources.filterNot { source -> source.method == BrewMethod.PULSAR }
        validateCoverage(pulsarSources, authoredSources)
        val stageEntries = authoredSources.map(::stageContent)
        val stageSafetyEntries = authoredSources.mapNotNull(::stageSafetyContent)
        val profileEntries = profileCopyById.flatMap(::profileContent)
        return BuiltInGuidanceCatalog(
            PulsarBuiltInGuidanceCatalog.catalog.content +
                stageEntries +
                stageSafetyEntries +
                profileEntries,
        )
    }

    private fun validateCoverage(
        pulsarSources: List<LegacySourceStage>,
        authoredSources: List<LegacySourceStage>,
    ) {
        val authoredContentIds = authoredSources.map { source -> source.definition.contentId }.toSet()
        require(stageCopyByContentId.keys == authoredContentIds) {
            "Legacy guidance must cover every non-Pulsar source stage without extras"
        }
        require(stageSafetyCopyByContentId.keys.all(authoredContentIds::contains)) {
            "Legacy stage safety must belong to a legacy source stage"
        }
        val requiredSafetyIds = authoredSources
            .filter { source -> source.definition.safetyMessages.isNotEmpty() }
            .map { source -> source.definition.contentId }
            .toSet()
        require(requiredSafetyIds.all(stageSafetyCopyByContentId::containsKey)) {
            "Legacy guidance must explain every stage-plan safety-bearing stage"
        }
        require(profileCopyById.map(ProfileCopy::profileId).toSet() ==
            authoredSources.map { source -> source.profile.id }.toSet()) {
            "Legacy guidance needs preparation, completion, utility, and safety copy per profile"
        }
        require(pulsarSources.all(::hasPulsarGuidance)) {
            "Pulsar guidance must cover every current legacy Pulsar source stage"
        }
    }

    private fun hasPulsarGuidance(source: LegacySourceStage): Boolean =
        PulsarBuiltInGuidanceCatalog.catalog.content.any { item ->
            item.profileId == source.profile.id &&
                item.stageId == source.definition.id &&
                item.id == source.definition.contentId &&
                !item.safetyCritical
        }

    private fun stageContent(source: LegacySourceStage): BuiltInGuidanceContent {
        val copy = requireNotNull(stageCopyByContentId[source.definition.contentId])
        return BuiltInGuidanceContent(
            id = source.definition.contentId,
            familyId = source.profile.familyId,
            profileId = source.profile.id,
            stageId = source.definition.id,
            placement = BuiltInGuidancePlacement.LIVE_STAGE,
            text = copy.toGuidanceText(),
            visibility = routineVisibility(),
        )
    }

    private fun stageSafetyContent(source: LegacySourceStage): BuiltInGuidanceContent? =
        stageSafetyCopyByContentId[source.definition.contentId]?.let { copy ->
            BuiltInGuidanceContent(
                id = StageContentId("${source.definition.contentId.value}_safety"),
                familyId = source.profile.familyId,
                profileId = source.profile.id,
                stageId = source.definition.id,
                placement = BuiltInGuidancePlacement.LIVE_STAGE,
                text = copy.toGuidanceText(),
                visibility = alwaysVisibleSafety(),
                safetyCritical = true,
            )
        }

    private fun profileContent(copy: ProfileCopy): List<BuiltInGuidanceContent> {
        val profile = profile(copy.profileId)
        return listOf(
            profileContent(profile, "prepare", BuiltInGuidancePlacement.PREPARATION, copy.preparation, learnOverviewVisibility()),
            profileContent(profile, "finish", BuiltInGuidancePlacement.COMPLETION, copy.completion, learnOverviewVisibility()),
            profileContent(profile, "live_targets", BuiltInGuidancePlacement.UTILITY, copy.utility, utilityVisibility()),
            profileContent(
                profile,
                "global_safety",
                BuiltInGuidancePlacement.GLOBAL_SAFETY,
                copy.globalSafety,
                alwaysVisibleSafety(),
                safetyCritical = true,
            ),
        )
    }

    private fun profileContent(
        profile: BrewerProfile,
        idSuffix: String,
        placement: BuiltInGuidancePlacement,
        copy: InstructionCopy,
        visibility: GuidanceVisibilityPolicy,
        safetyCritical: Boolean = false,
    ): BuiltInGuidanceContent = BuiltInGuidanceContent(
        id = StageContentId("${profile.id.value}_$idSuffix"),
        familyId = profile.familyId,
        profileId = profile.id,
        placement = placement,
        text = copy.toGuidanceText(),
        visibility = visibility,
        safetyCritical = safetyCritical,
    )
    private fun sourceStages(): List<LegacySourceStage> = legacyMethodProfileIds.flatMap {
            (method, profileId) ->
        val profile = profile(profileId)
        collectStages(LegacyStagePlanFactory.create(method).nodes).map { definition ->
            LegacySourceStage(method, profile, definition)
        }
    }.also { sources ->
        require(sources.map { source -> source.definition.contentId }.distinct().size == sources.size) {
            "Legacy source stage content IDs must remain unique"
        }
    }

    private fun profile(profileId: BrewerProfileId): BrewerProfile = requireNotNull(
        BuiltinBrewingCatalog.instance.findBrewerProfile(profileId),
    ) {
        "Legacy guidance has no built-in profile for ${profileId.value}"
    }

    private fun collectStages(nodes: List<StagePlanNode>): List<BrewStageDefinition> = nodes.flatMap { node ->
        when (node) {
            is StagePlanNode.Stage -> listOf(node.definition)
            is StagePlanNode.OptionalSection -> collectStages(node.nodes)
            is StagePlanNode.BoundedRepeat -> collectStages(node.nodes)
        }
    }

    private fun routineVisibility(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = setOf(
            GuidancePresentationLevel.FULL,
            GuidancePresentationLevel.CONCISE,
            GuidancePresentationLevel.FOCUSED,
            GuidancePresentationLevel.CUSTOM,
        ),
    )

    private fun learnOverviewVisibility(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = setOf(
            GuidancePresentationLevel.FULL,
            GuidancePresentationLevel.CONCISE,
            GuidancePresentationLevel.CUSTOM,
        ),
    )

    private fun utilityVisibility(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = GuidancePresentationLevel.entries.toSet(),
    )

    private fun alwaysVisibleSafety(): GuidanceVisibilityPolicy = GuidanceVisibilityPolicy(
        visibleIn = emptySet(),
        alwaysVisible = true,
    )

    private data class LegacySourceStage(
        val method: BrewMethod,
        val profile: BrewerProfile,
        val definition: BrewStageDefinition,
    )

    private data class InstructionCopy(
        val primaryInstruction: String,
        val conciseInstruction: String,
        val explanation: String? = null,
        val tip: String? = null,
        val altText: String,
        val warning: String? = null,
    ) {
        fun toGuidanceText(): GuidanceTextMetadata = GuidanceTextMetadata(
            primaryInstruction = primaryInstruction,
            conciseInstruction = conciseInstruction,
            explanation = explanation,
            tip = tip,
            warning = warning,
            altText = altText,
        )
    }

    private data class ProfileCopy(
        val profileId: BrewerProfileId,
        val preparation: InstructionCopy,
        val completion: InstructionCopy,
        val utility: InstructionCopy,
        val globalSafety: InstructionCopy,
    )

    private val stageCopyByContentId: Map<StageContentId, InstructionCopy> = linkedMapOf(
        StageContentId("v60_bloom") to InstructionCopy(
            primaryInstruction =
                "Pour the selected bloom water evenly over the grounds, make sure they are all wet, then let the bloom run for the recipe's countdown.",
            conciseInstruction = "Wet all grounds and let the bloom finish.",
            explanation =
                "A controlled bloom lets trapped gas escape before the main pour; the active recipe remains the source of water and time targets.",
            tip = "Keep the dripper and server centered on a stable scale.",
            altText = "A cone dripper centered on a server while all coffee grounds are evenly wetted for bloom.",
        ),
        StageContentId("v60_manual_brew") to InstructionCopy(
            primaryInstruction =
                "Pour the remaining water in calm, controlled additions. Follow the selected " +
                    "recipe's cadence, pausing for drainage only where that recipe calls for it.",
            conciseInstruction = "Pour calmly to the target, following the selected recipe's cadence.",
            explanation =
                "A steady pour helps avoid disturbing the coffee bed or filling the brewer faster than it can drain.",
            tip = "Pause pouring before the liquid nears the rim.",
            altText = "A controlled circular pour into a cone dripper with the liquid safely below the rim.",
        ),
        StageContentId("french_press_steep") to InstructionCopy(
            primaryInstruction =
                "Add water to the measured coffee and let it steep for the time in your selected recipe.",
            conciseInstruction = "Add water and steep to the recipe time.",
            explanation =
                "The durable session tracks the chosen timing; this stage stays user-directed so you can make the observation that suits your coffee.",
            tip = "Keep the plunger ready without pressing it down during the steep.",
            altText = "A French press with coffee and water steeping beneath an unpressed plunger lid.",
        ),
        StageContentId("french_press_press") to InstructionCopy(
            primaryInstruction =
                "When the steep is ready, lower the plunger slowly and evenly, then stop when it reaches the coffee bed.",
            conciseInstruction = "Press slowly and evenly to the coffee bed.",
            explanation =
                "A gentle press keeps the movement controlled and helps avoid forcing hot coffee upward.",
            altText = "Hands slowly lowering a French press plunger on a stable counter.",
        ),
        StageContentId("aeropress_steep") to InstructionCopy(
            primaryInstruction =
                "Use the standard upright AeroPress orientation on a sturdy, stable mug or server, then steep for the time in your selected recipe.",
            conciseInstruction = "Steep upright on a sturdy, stable vessel to the recipe time.",
            explanation =
                "The recipe and equipment arrangement determine the amount and time; this stage only keeps the active brew visible and deliberate.",
            tip = "Do not use the inverted method or a thin glass vessel for this legacy guide.",
            altText = "An upright AeroPress chamber steeping securely on a sturdy, stable serving vessel.",
        ),
        StageContentId("aeropress_press") to InstructionCopy(
            primaryInstruction =
                "Keep the upright AeroPress centered on a sturdy, stable mug or server. Keep " +
                    "hands clear of the outlet and press steadily without forcing the plunger.",
            conciseInstruction = "Keep it upright and centered; keep hands clear and press steadily.",
            explanation =
                "A stable base and a gradual press make it easier to notice resistance before it becomes unsafe.",
            altText = "An AeroPress centered securely on a sturdy server while a hand presses the plunger steadily.",
        ),
        StageContentId("espresso_pull") to InstructionCopy(
            primaryInstruction =
                "Start the extraction, watch the stream and scale, and stop according to the beverage target in your selected recipe.",
            conciseInstruction = "Watch the stream and stop at the selected beverage target.",
            explanation =
                "The selected recipe owns the target yield and timing; this stage asks for a deliberate visual check " +
                    "rather than inventing a generic stop point.",
            tip = "Keep the cup and scale stable before starting the extraction.",
            altText = "Espresso flowing from a group head into a cup on a stable scale.",
        ),
        StageContentId("moka_heat") to InstructionCopy(
            primaryInstruction =
                "Assemble the pot correctly, apply low to medium heat, and keep watching it as it warms.",
            conciseInstruction = "Use gentle heat and watch the pot.",
            explanation =
                "Moka brewing is completed by the physical flow you observe, not by a generic countdown.",
            altText = "A closed moka pot heating on a controlled low setting with its handle clear of the heat source.",
        ),
        StageContentId("moka_observe_flow") to InstructionCopy(
            primaryInstruction =
                "Watch the coffee flow; when it turns pale or begins to sputter, remove the pot from heat and let it settle before handling.",
            conciseInstruction = "When flow pales or sputters, remove heat and let it settle.",
            explanation =
                "The visible flow is the meaningful completion signal for this stage.",
            altText = "Coffee flowing from a moka pot while a hand safely turns off the heat.",
        ),
        StageContentId("cold_brew_steep") to InstructionCopy(
            primaryInstruction =
                "Combine the measured coffee and water in a clean vessel, cover it, refrigerate " +
                    "at 4 °C or colder, and steep for the selected recipe countdown.",
            conciseInstruction = "Combine, cover, and steep refrigerated at 4 °C or colder.",
            explanation =
                "Cold brew is the one passive legacy stage: the durable countdown can complete while the vessel remains undisturbed.",
            tip = "Label the vessel if more than one coffee is steeping nearby.",
            altText = "A covered cold-brew vessel with coffee steeping beside its visible recipe timer.",
        ),
        StageContentId("cold_brew_filter") to InstructionCopy(
            primaryInstruction =
                "Filter the brewed coffee slowly into a clean covered server, discard the " +
                    "grounds, and refrigerate promptly at 4 °C or colder unless serving immediately.",
            conciseInstruction = "Filter cleanly, then serve or refrigerate promptly at 4 °C or colder.",
            explanation =
                "Filtering is a manual final step so you can confirm that the vessel and receiving server are stable.",
            altText = "Cold brew passing through a filter into a clean server on a stable surface.",
        ),
    )

    private val stageSafetyCopyByContentId: Map<StageContentId, InstructionCopy> = linkedMapOf(
        StageContentId("french_press_press") to InstructionCopy(
            primaryInstruction = "Keep the press on a stable surface and use a slow, even motion.",
            conciseInstruction = "Keep it stable and press slowly.",
            warning =
                "Hot coffee can surge if the plunger is forced. Stop pressing if the press shifts, binds, or feels unsafe.",
            altText = "A French press held securely on a level surface while the plunger is lowered slowly.",
        ),
        StageContentId("aeropress_steep") to InstructionCopy(
            primaryInstruction = "Use only the standard upright orientation on a sturdy, stable mug or server.",
            conciseInstruction = "Use the standard upright orientation on a sturdy vessel.",
            warning =
                "Do not use the inverted method or thin glass. Hot water can spill if the brewer or receiving vessel tips.",
            altText = "An upright AeroPress centered on a sturdy vessel with hands clear of the outlet.",
        ),
        StageContentId("aeropress_press") to InstructionCopy(
            primaryInstruction = "Keep the chamber and serving vessel stable throughout the press.",
            conciseInstruction = "Keep the chamber and vessel stable.",
            warning =
                "Hot coffee can escape if the chamber or cup is unstable. Keep hands clear of " +
                    "the outlet, never force the plunger, and stop if resistance suddenly increases.",
            altText = "A stable AeroPress and sturdy serving vessel with hands clear of the outlet.",
        ),
        StageContentId("espresso_pull") to InstructionCopy(
            primaryInstruction = "Keep hands clear of the hot group and use the machine only as its manufacturer directs.",
            conciseInstruction = "Keep clear of hot equipment and follow the machine guidance.",
            warning =
                "Espresso equipment uses hot water and pressure. Stop the extraction if the machine behaves unexpectedly " +
                    "and allow hot parts to cool before touching them.",
            altText = "An espresso machine extracting into a cup with hands kept clear of hot metal and water.",
        ),
        StageContentId("moka_heat") to InstructionCopy(
            primaryInstruction = "Fill the lower chamber only below the safety valve and keep the valve clear before heating.",
            conciseInstruction = "Fill below the safety valve and keep it clear.",
            warning =
                "Do not cover or block the safety valve. Use low to medium heat and never leave the pot unattended while it is heating.",
            altText = "An open moka pot lower chamber filled below a clearly unobstructed safety valve.",
        ),
        StageContentId("moka_observe_flow") to InstructionCopy(
            primaryInstruction = "Use the handle and keep hands clear while the pot is hot and the flow is active.",
            conciseInstruction = "Use the handle and keep clear of hot flow.",
            warning =
                "Metal, steam, and coffee can burn. Do not open the pot until it has cooled and pressure has settled.",
            altText = "A hand holding only the moka pot handle while hot coffee flow and steam remain clear of hands.",
        ),
        StageContentId("cold_brew_steep") to InstructionCopy(
            primaryInstruction = "Keep the covered vessel refrigerated at 4 °C or colder for the full steep.",
            conciseInstruction = "Steep covered at 4 °C or colder.",
            warning =
                "Do not steep this legacy cold brew at room temperature. Keep it refrigerated at 4 °C or colder.",
            altText = "A covered cold-brew vessel stored in a refrigerator at 4 degrees Celsius or colder.",
        ),
        StageContentId("cold_brew_filter") to InstructionCopy(
            primaryInstruction = "After filtering, serve immediately or cover and refrigerate promptly at 4 °C or colder.",
            conciseInstruction = "Serve or refrigerate promptly at 4 °C or colder.",
            warning =
                "Do not leave filtered cold brew at room temperature; refrigerate promptly at 4 °C or colder.",
            altText = "Filtered cold brew in a clean covered server ready for prompt refrigeration.",
        ),
    )

    private val profileCopyById: List<ProfileCopy> = listOf(
        ProfileCopy(
            profileId = BrewerProfileId("v60_unspecified"),
            preparation = InstructionCopy(
                primaryInstruction =
                    "Rinse the selected paper filter, warm the server, add the measured coffee, and level the bed before starting the session.",
                conciseInstruction = "Rinse the filter, add coffee, and level the bed.",
                explanation = "A level bed and a seated filter make the later pour easier to control.",
                altText = "A rinsed paper filter seated in a cone dripper with a level bed of measured coffee.",
            ),
            completion = InstructionCopy(
                primaryInstruction = "When drawdown is complete, remove the dripper carefully and serve the coffee.",
                conciseInstruction = "When drawdown ends, remove the dripper and serve.",
                altText = "A cone dripper being carefully lifted from a server after drawdown.",
            ),
            utility = InstructionCopy(
                primaryInstruction = "Keep the selected coffee, water, and timer targets visible while you pour.",
                conciseInstruction = "Keep the selected targets visible.",
                explanation = "The active recipe remains the source of its amounts and timing.",
                altText = "A cone dripper beside a scale and visible selected recipe targets.",
            ),
            globalSafety = InstructionCopy(
                primaryInstruction = "Keep the dripper, server, and scale stable while you pour.",
                conciseInstruction = "Keep the brewing setup stable.",
                warning =
                    "Hot water and coffee can burn. Stop pouring if the brewer, server, or scale becomes unstable.",
                altText = "A cone dripper and server resting squarely on a stable scale.",
            ),
        ),
        ProfileCopy(
            profileId = BrewerProfileId("french_press_generic"),
            preparation = InstructionCopy(
                primaryInstruction = "Add the measured coffee to a clean press and keep the water, scale, and plunger ready.",
                conciseInstruction = "Add coffee to a clean press and prepare the plunger.",
                explanation = "Preparing the stable setup first keeps the steep and press stages simple.",
                altText = "Measured coffee in a clean French press beside water, a scale, and its plunger.",
            ),
            completion = InstructionCopy(
                primaryInstruction = "Serve carefully, then empty and rinse the press after the hot equipment has cooled enough to handle.",
                conciseInstruction = "Serve carefully, then rinse after cooling.",
                altText = "A French press beside a served cup after the hot brew has settled.",
            ),
            utility = InstructionCopy(
                primaryInstruction = "Keep the selected coffee, water, and steep-time targets visible.",
                conciseInstruction = "Keep the selected targets visible.",
                explanation = "The active recipe provides the chosen amount and time rather than this guidance supplying a substitute preset.",
                altText = "A French press next to a scale and visible recipe timing target.",
            ),
            globalSafety = InstructionCopy(
                primaryInstruction = "Keep the press stable and keep hands clear of hot glass and coffee.",
                conciseInstruction = "Keep hot glass and coffee stable.",
                warning =
                    "Hot coffee and glass can burn. Use a stable surface and handle the press carefully while it is hot.",
                altText = "A French press on a stable counter with hands clear of the hot glass body.",
            ),
        ),
        ProfileCopy(
            profileId = BrewerProfileId("aeropress_standard"),
            preparation = InstructionCopy(
                primaryInstruction =
                    "Fit the selected filter and cap, add the measured coffee, and prepare a sturdy cup or server before starting.",
                conciseInstruction = "Fit the filter, add coffee, and prepare a sturdy vessel.",
                explanation = "Having the receiving vessel ready avoids a rushed change between steeping and pressing.",
                altText = "An AeroPress with its selected filter and cap ready beside measured coffee and a sturdy server.",
            ),
            completion = InstructionCopy(
                primaryInstruction = "Remove the brewer carefully, serve the coffee, and clean the chamber once it is safe to handle.",
                conciseInstruction = "Remove carefully, serve, and clean after cooling.",
                altText = "An AeroPress set aside safely beside a served cup after pressing.",
            ),
            utility = InstructionCopy(
                primaryInstruction = "Keep the selected coffee, water, and steep-time targets visible.",
                conciseInstruction = "Keep the selected targets visible.",
                explanation = "The active recipe remains the authority for its amounts and timing.",
                altText = "An AeroPress beside a scale and visible selected recipe targets.",
            ),
            globalSafety = InstructionCopy(
                primaryInstruction = "Keep the chamber, cap, and serving vessel stable while handling hot coffee.",
                conciseInstruction = "Keep the chamber and vessel stable.",
                warning =
                    "Hot coffee can burn. Keep hands clear of the outlet and stop if the brewer or serving vessel becomes unstable.",
                altText = "A stable AeroPress setup with a chamber centered on a sturdy serving vessel.",
            ),
        ),
        ProfileCopy(
            profileId = BrewerProfileId("espresso_pump_generic"),
            preparation = InstructionCopy(
                primaryInstruction =
                    "Prepare a clean basket and portafilter with the measured coffee, then use your machine's documented operating steps.",
                conciseInstruction = "Prepare the basket and follow the machine's operating steps.",
                explanation = "Machine-specific setup remains with the machine manufacturer; the selected recipe " +
                    "remains the source of its target beverage yield.",
                altText = "A clean espresso basket and portafilter prepared with measured coffee beside an espresso machine.",
            ),
            completion = InstructionCopy(
                primaryInstruction = "Serve the espresso promptly and clean the equipment according to the machine manufacturer's instructions.",
                conciseInstruction = "Serve promptly and clean as the manufacturer directs.",
                altText = "A served espresso beside an espresso machine with the portafilter set down safely.",
            ),
            utility = InstructionCopy(
                primaryInstruction = "Keep the selected dose, beverage-yield target, and timer visible during extraction.",
                conciseInstruction = "Keep dose, yield, and timer visible.",
                explanation = "The active recipe owns the chosen yield and timing; this guidance does not replace them with a generic shot prescription.",
                altText = "An espresso cup on a scale with visible dose, beverage-yield, and timer targets.",
            ),
            globalSafety = InstructionCopy(
                primaryInstruction = "Treat the machine, portafilter, and water path as hot equipment while brewing.",
                conciseInstruction = "Keep clear of hot espresso equipment.",
                warning =
                    "Hot water, steam, and pressurized equipment can burn. Follow the machine manufacturer’s " +
                        "safety instructions and let hot parts cool before handling.",
                altText = "An espresso machine with hot surfaces marked by a safely distant hand.",
            ),
        ),
        ProfileCopy(
            profileId = BrewerProfileId("moka_generic_unspecified"),
            preparation = InstructionCopy(
                primaryInstruction =
                    "Inspect the gasket, filter, and safety valve; add water below the valve and fill the coffee basket without packing the coffee tightly.",
                conciseInstruction = "Inspect the pot, fill below the valve, and do not pack the coffee.",
                explanation = "A clear safety valve and correctly assembled parts are prerequisites before heat is applied.",
                altText = "A disassembled moka pot with a clear safety valve, filter, gasket, water below the valve, and loosely filled coffee basket.",
            ),
            completion = InstructionCopy(
                primaryInstruction = "Serve carefully, then let the pot cool before disassembling and cleaning it.",
                conciseInstruction = "Serve carefully and let the pot cool before cleaning.",
                altText = "A moka pot set on a heat-safe surface to cool beside a served cup.",
            ),
            utility = InstructionCopy(
                primaryInstruction = "Keep the selected coffee and water targets visible while you watch the physical flow.",
                conciseInstruction = "Keep targets visible and watch the flow.",
                explanation = "The observed flow, not a generic timer, determines when the active brew is ready to leave the heat.",
                altText = "A moka pot beside a scale and visible selected coffee and water targets.",
            ),
            globalSafety = InstructionCopy(
                primaryInstruction = "Treat the moka pot as hot, pressurized equipment throughout heating and cooling.",
                conciseInstruction = "Treat the moka pot as hot and pressurized.",
                warning =
                    "Hot metal, steam, and coffee can burn. Do not open the pot while it is hot or pressurized, and keep the safety valve unobstructed.",
                altText = "A moka pot on a heat-safe surface with its hot metal body and safety valve kept clear.",
            ),
        ),
        ProfileCopy(
            profileId = BrewerProfileId("cold_immersion_generic"),
            preparation = InstructionCopy(
                primaryInstruction =
                    "Clean and dry the steeping vessel, filter, and receiving server before measuring coffee and water.",
                conciseInstruction = "Clean the vessel and filter before measuring coffee and water.",
                explanation = "A clean, food-safe setup is especially important for a long passive steep.",
                altText = "A clean cold-brew vessel, filter, and receiving server ready beside measured coffee and water.",
            ),
            completion = InstructionCopy(
                primaryInstruction = "After filtering, serve immediately or cover and refrigerate promptly at 4 °C or colder, then clean the vessel.",
                conciseInstruction = "Serve or refrigerate promptly at 4 °C or colder, then clean.",
                altText = "Filtered cold brew in a clean covered server beside the rinsed steeping vessel.",
            ),
            utility = InstructionCopy(
                primaryInstruction = "Keep the selected coffee, water, and long-steep countdown visible.",
                conciseInstruction = "Keep the selected targets and countdown visible.",
                explanation = "The durable session owns the recipe's long countdown while the vessel remains undisturbed.",
                altText = "A covered cold-brew vessel beside visible coffee, water, and long-steep countdown targets.",
            ),
            globalSafety = InstructionCopy(
                primaryInstruction = "Use clean, food-safe equipment and keep the covered brew at 4 °C or colder during steeping and storage.",
                conciseInstruction = "Use clean equipment and keep the covered brew at 4 °C or colder.",
                warning =
                    "Refrigerate promptly at 4 °C or colder. Do not rely on smell alone to determine whether a stored brew is safe.",
                altText = "A clean covered cold-brew container being inspected before serving.",
            ),
        ),
    )
}
