package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow

/** Input for a Learn surface backed by the same profile-scoped content as live Brew. */
data class LearnGuidanceCatalogRequest(
    val methodFamilyId: String,
    val brewerProfileId: String,
    val preferences: DurableBrewSessionGuidancePreferences = DurableBrewSessionGuidancePreferences(),
    val harioSwitchWorkflow: HarioSwitchWorkflow? = null,
    val exactStageOrder: List<StageId>? = null,
)

sealed interface LearnGuidanceCatalogAvailability {
    data object Available : LearnGuidanceCatalogAvailability

    /** Compatible siblings remain readable; absence never changes the executable stage list. */
    data class Partial(
        val profileId: BrewerProfileId,
        val missingStageIds: List<StageId>,
    ) : LearnGuidanceCatalogAvailability

    data class InvalidMethodFamilyId(
        val rawId: String,
    ) : LearnGuidanceCatalogAvailability

    data class InvalidBrewerProfileId(
        val rawId: String,
    ) : LearnGuidanceCatalogAvailability

    data class UnknownBrewerProfile(
        val profileId: BrewerProfileId,
    ) : LearnGuidanceCatalogAvailability

    data class ProfileFamilyMismatch(
        val profileId: BrewerProfileId,
        val requestedFamilyId: MethodFamilyId,
        val catalogueFamilyId: MethodFamilyId,
    ) : LearnGuidanceCatalogAvailability

    data class NoGuidanceCatalogForProfile(
        val methodFamilyId: MethodFamilyId,
        val brewerProfileId: BrewerProfileId,
    ) : LearnGuidanceCatalogAvailability
    data class HarioSwitchWorkflowRequired(
        val profileId: BrewerProfileId,
    ) : LearnGuidanceCatalogAvailability
    data class ExactStageOrderMismatch(
        val profileId: BrewerProfileId,
    ) : LearnGuidanceCatalogAvailability

}

/**
 * Renderer-ready Learn content. Critical safety records retain their warning
 * at every density, while routine supporting copy follows the same policy as
 * durable live Brew.
 */
data class ResolvedLearnGuidanceContent(
    val id: StageContentId,
    val placement: BuiltInGuidancePlacement,
    val instruction: String,
    val target: String?,
    val completionCue: String?,
    val explanation: String?,
    val tip: String?,
    val nextAction: String?,
    val controlRequirements: List<GuidanceOperationalCue>,
    val warning: String?,
    val utilities: List<GuidanceOperationalCue>,
    val altText: String,
    val safetyCritical: Boolean,
    val visualContentRevision: String? = null,
    val visualInstruction: String = instruction,
)

data class LearnGuidanceCatalogResolution(
    val policy: ResolvedGuidancePolicy?,
    val availability: LearnGuidanceCatalogAvailability,
    val content: List<ResolvedLearnGuidanceContent>,
)

private sealed interface LearnGuidanceScopeResolution {
    data class Ready(
        val methodFamilyId: MethodFamilyId,
        val brewerProfileId: BrewerProfileId,
    ) : LearnGuidanceScopeResolution

    data class Unavailable(
        val availability: LearnGuidanceCatalogAvailability,
    ) : LearnGuidanceScopeResolution
}

/**
 * Resolves an entire profile curriculum for Learn without creating or changing
 * a brew session. It shares policy precedence with live Brew and refuses to
 * substitute content from another profile when a catalogue is absent.
 */
class LearnGuidanceCatalogResolver(
    private val brewingCatalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
    guidanceCatalogs: List<BuiltInGuidanceCatalog> = listOf(
        LegacyBuiltInGuidanceCatalog.catalog,
        P1BuiltInGuidanceCatalog.catalog,
        ColdBrewGuidanceCatalog.catalog,
    ),
) {
    private val allContent = guidanceCatalogs
        .flatMap(BuiltInGuidanceCatalog::content)
        .also(::requireUniqueLearnContentIds)

    fun resolve(request: LearnGuidanceCatalogRequest): LearnGuidanceCatalogResolution {
        val scope = resolveScope(request)
        if (scope is LearnGuidanceScopeResolution.Unavailable) {
            return unavailable(scope.availability)
        }
        require(scope is LearnGuidanceScopeResolution.Ready)
        val methodFamilyId = scope.methodFamilyId
        val brewerProfileId = scope.brewerProfileId

        val policy = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = methodFamilyId,
                brewerProfileId = brewerProfileId,
                sessionOverride = request.preferences.sessionOverride,
                profileOverrides = request.preferences.profileOverrides.toLearnProfileOverrides(),
                familyPreferences = request.preferences.familyPreferences.toLearnFamilyPreferences(),
            ),
        )
        val scopedContent = allContent.filter { content ->
            content.familyId == methodFamilyId &&
                (content.profileId == null || content.profileId == brewerProfileId)
        }.let { content ->
            if (brewerProfileId.value == "cold_immersion_generic" && request.exactStageOrder == null) {
                content.filter { it.id.value.startsWith("cold_refrigerated_") }
            } else content
        }
        val exactStageOrder = request.exactStageOrder
        if (exactStageOrder != null &&
            (exactStageOrder.isEmpty() || exactStageOrder.distinct().size != exactStageOrder.size)
        ) {
            return unavailable(LearnGuidanceCatalogAvailability.ExactStageOrderMismatch(brewerProfileId))
        }
        if (scopedContent.isEmpty()) {
            return LearnGuidanceCatalogResolution(
                policy = policy,
                availability = LearnGuidanceCatalogAvailability.NoGuidanceCatalogForProfile(
                    methodFamilyId,
                    brewerProfileId,
                ),
                content = emptyList(),
            )
        }
        val exactSelection = exactStageOrder?.let { order ->
            selectExactContent(scopedContent, order)
                ?: return unavailable(
                    LearnGuidanceCatalogAvailability.ExactStageOrderMismatch(brewerProfileId),
                )
        }
        val planSelection = if (exactSelection == null) {
            P1LearnContentSelector.select(
                content = scopedContent,
                profileId = brewerProfileId,
                harioSwitchWorkflow = request.harioSwitchWorkflow,
            )
        } else {
            exactSelection.selection
        }
        return LearnGuidanceCatalogResolution(
            policy = policy,
            availability = when {
                planSelection.requiresHarioSwitchWorkflow ->
                    LearnGuidanceCatalogAvailability.HarioSwitchWorkflowRequired(brewerProfileId)
                exactSelection != null && exactSelection.missingStageIds.isNotEmpty() ->
                    LearnGuidanceCatalogAvailability.Partial(brewerProfileId, exactSelection.missingStageIds)
                else -> LearnGuidanceCatalogAvailability.Available
            },
            content = planSelection.content
                .filter { content -> policy.isVisible(content.visibility, content.safetyCritical) }
                .map { content -> content.toLearnContent(policy.level) },
        )
    }

    private fun resolveScope(request: LearnGuidanceCatalogRequest): LearnGuidanceScopeResolution {
        val methodFamilyId = request.methodFamilyId.toLearnMethodFamilyIdOrNull()
        val brewerProfileId = request.brewerProfileId.toLearnBrewerProfileIdOrNull()
        val profile = brewerProfileId?.let(brewingCatalog::findBrewerProfile)
        return when {
            methodFamilyId == null -> LearnGuidanceScopeResolution.Unavailable(
                LearnGuidanceCatalogAvailability.InvalidMethodFamilyId(request.methodFamilyId),
            )
            brewerProfileId == null -> LearnGuidanceScopeResolution.Unavailable(
                LearnGuidanceCatalogAvailability.InvalidBrewerProfileId(request.brewerProfileId),
            )
            profile == null -> LearnGuidanceScopeResolution.Unavailable(
                LearnGuidanceCatalogAvailability.UnknownBrewerProfile(brewerProfileId),
            )
            profile.familyId != methodFamilyId -> LearnGuidanceScopeResolution.Unavailable(
                LearnGuidanceCatalogAvailability.ProfileFamilyMismatch(
                    profileId = brewerProfileId,
                    requestedFamilyId = methodFamilyId,
                    catalogueFamilyId = profile.familyId,
                ),
            )
            else -> LearnGuidanceScopeResolution.Ready(methodFamilyId, brewerProfileId)
        }
    }

    private data class ExactContentSelection(
        val selection: P1LearnContentSelection,
        val missingStageIds: List<StageId>,
    )

    private fun selectExactContent(
        scopedContent: List<BuiltInGuidanceContent>,
        exactStageOrder: List<StageId>,
    ): ExactContentSelection? {
        val orderByStage = exactStageOrder.withIndex().associate { (index, stageId) -> stageId to index }
        val liveContent = scopedContent.filter { content ->
            content.placement == BuiltInGuidancePlacement.LIVE_STAGE && content.stageId in orderByStage
        }
        // Two records for one requested stage are ambiguous; never arbitrarily choose one.
        val contentByStage = liveContent.groupBy { it.stageId }
        if (contentByStage.values.any { it.size != 1 }) return null
        return ExactContentSelection(
            selection = P1LearnContentSelection(
                content = (scopedContent.filter { it.placement != BuiltInGuidancePlacement.LIVE_STAGE } +
                    liveContent).sortedBy { content -> content.stageId?.let(orderByStage::get) },
                requiresHarioSwitchWorkflow = false,
            ),
            missingStageIds = exactStageOrder.filterNot(contentByStage::containsKey),
        )
    }

    private fun unavailable(
        availability: LearnGuidanceCatalogAvailability,
    ): LearnGuidanceCatalogResolution = LearnGuidanceCatalogResolution(
        policy = null,
        availability = availability,
        content = emptyList(),
    )

    private fun Map<String, GuidancePresentationLevel>.toLearnProfileOverrides():
        Map<BrewerProfileId, GuidancePresentationLevel> = mapNotNull { (rawId, level) ->
        rawId.toLearnBrewerProfileIdOrNull()
            ?.takeIf { profileId -> brewingCatalog.findBrewerProfile(profileId) != null }
            ?.let { profileId -> profileId to level }
    }.toMap()

    private fun Map<String, GuidancePresentationLevel>.toLearnFamilyPreferences():
        Map<MethodFamilyId, GuidancePresentationLevel> = mapNotNull { (rawId, level) ->
        rawId.toLearnMethodFamilyIdOrNull()
            ?.takeIf { familyId -> brewingCatalog.findMethodFamily(familyId) != null }
            ?.let { familyId -> familyId to level }
    }.toMap()

    private fun BuiltInGuidanceContent.toLearnContent(
        level: GuidancePresentationLevel,
    ): ResolvedLearnGuidanceContent {
        val projected = projectGuidanceContent(level)
        return ResolvedLearnGuidanceContent(
            id = id,
            placement = placement,
            instruction = projected.instruction.orEmpty(),
            target = projected.target,
            completionCue = projected.completionCue,
            explanation = projected.explanation,
            tip = projected.practicalTip,
            nextAction = projected.nextAction,
            controlRequirements = projected.controlRequirements,
            warning = projected.warning,
            utilities = projected.utilities,
            altText = projected.accessibleAltText,
            safetyCritical = safetyCritical,
            visualContentRevision = visualContentRevision,
            visualInstruction = text.primaryInstruction,
        )
    }
}

private fun String.toLearnMethodFamilyIdOrNull(): MethodFamilyId? = runCatching {
    MethodFamilyId(this)
}.getOrNull()

private fun String.toLearnBrewerProfileIdOrNull(): BrewerProfileId? = runCatching {
    BrewerProfileId(this)
}.getOrNull()

private fun requireUniqueLearnContentIds(content: List<BuiltInGuidanceContent>) {
    require(content.map(BuiltInGuidanceContent::id).distinct().size == content.size) {
        "Duplicate guidance content IDs across Learn catalogues"
    }
}
