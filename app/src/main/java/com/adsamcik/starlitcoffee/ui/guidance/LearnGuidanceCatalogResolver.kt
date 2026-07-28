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
)

data class LearnGuidanceCatalogResolution(
    val policy: ResolvedGuidancePolicy?,
    val availability: LearnGuidanceCatalogAvailability,
    val content: List<ResolvedLearnGuidanceContent>,
)

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
    ),
) {
    private val allContent = guidanceCatalogs
        .flatMap(BuiltInGuidanceCatalog::content)
        .also(::requireUniqueLearnContentIds)

    fun resolve(request: LearnGuidanceCatalogRequest): LearnGuidanceCatalogResolution {
        val methodFamilyId = request.methodFamilyId.toLearnMethodFamilyIdOrNull()
            ?: return unavailable(
                LearnGuidanceCatalogAvailability.InvalidMethodFamilyId(request.methodFamilyId),
            )
        val brewerProfileId = request.brewerProfileId.toLearnBrewerProfileIdOrNull()
            ?: return unavailable(
                LearnGuidanceCatalogAvailability.InvalidBrewerProfileId(request.brewerProfileId),
            )
        val profile = brewingCatalog.findBrewerProfile(brewerProfileId)
            ?: return unavailable(LearnGuidanceCatalogAvailability.UnknownBrewerProfile(brewerProfileId))
        if (profile.familyId != methodFamilyId) {
            return unavailable(
                LearnGuidanceCatalogAvailability.ProfileFamilyMismatch(
                    profileId = brewerProfileId,
                    requestedFamilyId = methodFamilyId,
                    catalogueFamilyId = profile.familyId,
                ),
            )
        }

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
        val exactStageOrder = request.exactStageOrder
        val planSelection = if (exactStageOrder == null) {
            P1LearnContentSelector.select(
                content = scopedContent,
                profileId = brewerProfileId,
                harioSwitchWorkflow = request.harioSwitchWorkflow,
            )
        } else {
            selectExactContent(scopedContent, exactStageOrder)
                ?: return unavailable(
                    LearnGuidanceCatalogAvailability.ExactStageOrderMismatch(brewerProfileId),
                )
        }
        return LearnGuidanceCatalogResolution(
            policy = policy,
            availability = if (planSelection.requiresHarioSwitchWorkflow) {
                LearnGuidanceCatalogAvailability.HarioSwitchWorkflowRequired(brewerProfileId)
            } else {
                LearnGuidanceCatalogAvailability.Available
            },
            content = planSelection.content
                .filter { content -> policy.isVisible(content.visibility, content.safetyCritical) }
                .map { content -> content.toLearnContent(policy.level) },
        )
    }

    private fun selectExactContent(
        scopedContent: List<BuiltInGuidanceContent>,
        exactStageOrder: List<StageId>,
    ): P1LearnContentSelection? {
        val orderByStage = exactStageOrder.withIndex().associate { (index, stageId) ->
            stageId to index
        }
        val liveContent = scopedContent.filter { content ->
            content.placement == BuiltInGuidancePlacement.LIVE_STAGE
        }
        val hasExactStageSet = listOf(
            exactStageOrder.isNotEmpty(),
            orderByStage.size == exactStageOrder.size,
            liveContent.size == exactStageOrder.size,
            liveContent.all { content -> content.stageId in orderByStage },
        ).all { requirement -> requirement }
        if (!hasExactStageSet) return null

        return P1LearnContentSelection(
            content = scopedContent.sortedBy { content ->
                content.stageId?.let(orderByStage::get)
            },
            requiresHarioSwitchWorkflow = false,
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
        authoredPresentations[level]?.let { authored ->
            return ResolvedLearnGuidanceContent(
                id = id,
                placement = placement,
                instruction = authored.instruction.orEmpty(),
                target = authored.target,
                completionCue = authored.completionCue,
                explanation = authored.explanation,
                tip = authored.practicalTip,
                nextAction = authored.nextAction,
                controlRequirements = authored.controlRequirements,
                warning = authored.warning ?: text.warning,
                utilities = authored.utilities,
                altText = authored.accessibleAltText,
                safetyCritical = safetyCritical,
            )
        }
        val showSupportingCopy = level in setOf(
            GuidancePresentationLevel.FULL,
            GuidancePresentationLevel.CUSTOM,
        )
        val compactInstruction = level in setOf(
            GuidancePresentationLevel.CONCISE,
            GuidancePresentationLevel.FOCUSED,
            GuidancePresentationLevel.UTILITIES_ONLY,
        )
        return ResolvedLearnGuidanceContent(
            id = id,
            placement = placement,
            instruction = if (compactInstruction) {
                text.conciseInstruction ?: text.primaryInstruction
            } else {
                text.primaryInstruction
            },
            target = null,
            completionCue = null,
            explanation = text.explanation.takeIf { showSupportingCopy },
            tip = text.tip.takeIf { showSupportingCopy },
            nextAction = null,
            controlRequirements = emptyList(),
            warning = text.warning,
            utilities = emptyList(),
            altText = text.altText,
            safetyCritical = safetyCritical,
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
