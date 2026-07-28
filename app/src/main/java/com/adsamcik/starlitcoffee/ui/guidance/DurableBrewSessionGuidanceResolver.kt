package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation

/**
 * Guidance choices supplied by the session surface. Stable preference keys
 * remain raw strings here because a persisted newer profile or family must
 * remain unavailable, not be coerced to a known catalogue entry.
 */
data class DurableBrewSessionGuidancePreferences(
    val sessionOverride: GuidancePresentationLevel? = null,
    val profileOverrides: Map<String, GuidancePresentationLevel> = emptyMap(),
    val familyPreferences: Map<String, GuidancePresentationLevel> = emptyMap(),
)

/**
 * The immutable information needed to resolve live guidance for one durable
 * session. The recipe snapshot owns the two raw identifiers, while the active
 * session presentation owns the current stage and its structured safety data.
 */
data class DurableBrewSessionGuidanceRequest(
    val methodFamilyId: String,
    val brewerProfileId: String,
    val currentStage: CurrentBrewStagePresentation?,
    val preferences: DurableBrewSessionGuidancePreferences = DurableBrewSessionGuidancePreferences(),
)

/**
 * A renderer-ready piece of shared Learn/live content. It preserves the stable
 * content ID and meaningful alt text while selecting only the copy appropriate
 * for the resolved presentation level.
 */
data class ResolvedBrewGuidanceContent(
    val id: StageContentId,
    val placement: BuiltInGuidancePlacement,
    val instruction: String,
    val explanation: String?,
    val tip: String?,
    val warning: String?,
    val altText: String,
    val safetyCritical: Boolean,
)

/**
 * Explains why routine content is absent. The caller must render
 * [DurableBrewSessionGuidanceResolution.stageSafetyMessages] independently in
 * every state; no unavailable state is permitted to remove a critical warning.
 */
sealed interface DurableBrewGuidanceAvailability {
    data object Available : DurableBrewGuidanceAvailability

    data object NoActiveStage : DurableBrewGuidanceAvailability

    data class InvalidMethodFamilyId(
        val rawId: String,
    ) : DurableBrewGuidanceAvailability

    data class InvalidBrewerProfileId(
        val rawId: String,
    ) : DurableBrewGuidanceAvailability

    data class UnknownBrewerProfile(
        val profileId: BrewerProfileId,
    ) : DurableBrewGuidanceAvailability

    data class ProfileFamilyMismatch(
        val profileId: BrewerProfileId,
        val requestedFamilyId: MethodFamilyId,
        val catalogueFamilyId: MethodFamilyId,
    ) : DurableBrewGuidanceAvailability

    data class NoGuidanceCatalogForProfile(
        val methodFamilyId: MethodFamilyId,
        val brewerProfileId: BrewerProfileId,
    ) : DurableBrewGuidanceAvailability

    data class MissingStageContent(
        val stageId: StageId,
        val contentId: StageContentId,
    ) : DurableBrewGuidanceAvailability
}

/**
 * The visual state is deliberately explicit. The resolver never substitutes
 * an illustration from a different profile, stage, or unreviewed asset.
 */
sealed interface DurableBrewGuidanceVisualStatus {
    data object NotRequested : DurableBrewGuidanceVisualStatus

    data object RequiredAssetIdMissing : DurableBrewGuidanceVisualStatus

    data class ManifestUnavailable(
        val assetId: String,
    ) : DurableBrewGuidanceVisualStatus

    data class MissingFromManifest(
        val assetId: String,
    ) : DurableBrewGuidanceVisualStatus

    data class ManifestScopeMismatch(
        val assetId: String,
    ) : DurableBrewGuidanceVisualStatus

    data class NotApproved(
        val asset: InstructionAssetRecord,
    ) : DurableBrewGuidanceVisualStatus

    data class Approved(
        val asset: InstructionAssetRecord,
    ) : DurableBrewGuidanceVisualStatus
}

/**
 * Pure outcome for one current durable-brew stage.
 *
 * [routineContent] obeys the chosen guidance level. [criticalContent] and
 * [stageSafetyMessages] do not: the former is authored critical catalogue
 * copy and the latter is the stage plan's structured safety contract.
 */
data class DurableBrewSessionGuidanceResolution(
    val policy: ResolvedGuidancePolicy?,
    val availability: DurableBrewGuidanceAvailability,
    val routineContent: List<ResolvedBrewGuidanceContent>,
    val criticalContent: List<ResolvedBrewGuidanceContent>,
    val stageSafetyMessages: List<StageSafetyMessage>,
    val visualStatus: DurableBrewGuidanceVisualStatus,
) {
    val criticalStageSafetyMessages: List<StageSafetyMessage>
        get() = stageSafetyMessages.filter { message ->
            message.severity == StageSafetySeverity.CRITICAL
        }
}

/**
 * Resolves a durable session's current stage from a profile-scoped content
 * catalogue. It is intentionally Android- and persistence-free: callers pass
 * restored recipe identifiers and the existing presentation-stage value.
 *
 * The default source covers all migrated legacy curriculums. Other
 * built-in profiles therefore return a named unavailable state until their own
 * content is supplied; this is intentional, rather than a fallback to Pulsar
 * or a superficially similar brewer.
 */
class DurableBrewSessionGuidanceResolver(
    private val brewingCatalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
    guidanceCatalogs: List<BuiltInGuidanceCatalog> = listOf(LegacyBuiltInGuidanceCatalog.catalog),
    private val instructionAssets: InstructionAssetCatalog? = null,
) {
    private val allContent = guidanceCatalogs
        .flatMap(BuiltInGuidanceCatalog::content)
        .also(::requireUniqueContentIds)

    fun resolve(request: DurableBrewSessionGuidanceRequest): DurableBrewSessionGuidanceResolution {
        val stageSafetyMessages = request.currentStage?.safetyMessages.orEmpty()
        val methodFamilyId = request.methodFamilyId.toMethodFamilyIdOrNull()
            ?: return unavailable(
                availability = DurableBrewGuidanceAvailability.InvalidMethodFamilyId(
                    request.methodFamilyId,
                ),
                stageSafetyMessages = stageSafetyMessages,
                visualStatus = visualStatus(request.currentStage, null),
            )
        val brewerProfileId = request.brewerProfileId.toBrewerProfileIdOrNull()
            ?: return unavailable(
                availability = DurableBrewGuidanceAvailability.InvalidBrewerProfileId(
                    request.brewerProfileId,
                ),
                stageSafetyMessages = stageSafetyMessages,
                visualStatus = visualStatus(request.currentStage, null),
            )
        val profile = brewingCatalog.findBrewerProfile(brewerProfileId)
            ?: return unavailable(
                availability = DurableBrewGuidanceAvailability.UnknownBrewerProfile(brewerProfileId),
                stageSafetyMessages = stageSafetyMessages,
                visualStatus = visualStatus(request.currentStage, null),
            )
        if (profile.familyId != methodFamilyId) {
            return unavailable(
                availability = DurableBrewGuidanceAvailability.ProfileFamilyMismatch(
                    profileId = brewerProfileId,
                    requestedFamilyId = methodFamilyId,
                    catalogueFamilyId = profile.familyId,
                ),
                stageSafetyMessages = stageSafetyMessages,
                visualStatus = visualStatus(request.currentStage, null),
            )
        }

        val policy = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = methodFamilyId,
                brewerProfileId = brewerProfileId,
                sessionOverride = request.preferences.sessionOverride,
                profileOverrides = request.preferences.profileOverrides.toKnownProfileOverrides(),
                familyPreferences = request.preferences.familyPreferences.toKnownFamilyPreferences(),
            ),
        )
        val scopedContent = allContent.filter { content ->
            content.familyId == methodFamilyId &&
                (content.profileId == null || content.profileId == brewerProfileId)
        }
        if (scopedContent.isEmpty()) {
            return unavailable(
                policy = policy,
                availability = DurableBrewGuidanceAvailability.NoGuidanceCatalogForProfile(
                    methodFamilyId,
                    brewerProfileId,
                ),
                stageSafetyMessages = stageSafetyMessages,
                visualStatus = visualStatus(request.currentStage, ProfileScope(methodFamilyId, brewerProfileId)),
            )
        }

        val currentStage = request.currentStage
            ?: return DurableBrewSessionGuidanceResolution(
                policy = policy,
                availability = DurableBrewGuidanceAvailability.NoActiveStage,
                routineContent = emptyList(),
                criticalContent = scopedContent
                    .filter { content ->
                        content.safetyCritical && content.placement == BuiltInGuidancePlacement.GLOBAL_SAFETY
                    }
                    .map { content -> content.toResolvedContent(policy.level) },
                stageSafetyMessages = stageSafetyMessages,
                visualStatus = DurableBrewGuidanceVisualStatus.NotRequested,
            )

        val scopedStageContent = scopedContent.filter { content ->
            content.belongsBeside(currentStage)
        }
        val hasPrimaryStageContent = scopedStageContent.any { content ->
            !content.safetyCritical &&
                content.placement == BuiltInGuidancePlacement.LIVE_STAGE &&
                content.matchesPrimaryStageContent(currentStage)
        }
        val routineContent = scopedStageContent
            .filterNot(BuiltInGuidanceContent::safetyCritical)
            .filter { content -> policy.isVisible(content.visibility, content.safetyCritical) }
            .map { content -> content.toResolvedContent(policy.level) }
        val criticalContent = scopedStageContent
            .filter(BuiltInGuidanceContent::safetyCritical)
            .map { content -> content.toResolvedContent(policy.level) }

        return DurableBrewSessionGuidanceResolution(
            policy = policy,
            availability = if (hasPrimaryStageContent) {
                DurableBrewGuidanceAvailability.Available
            } else {
                DurableBrewGuidanceAvailability.MissingStageContent(
                    stageId = currentStage.stageId,
                    contentId = currentStage.contentId,
                )
            },
            routineContent = routineContent,
            criticalContent = criticalContent,
            stageSafetyMessages = stageSafetyMessages,
            visualStatus = visualStatus(
                currentStage,
                ProfileScope(methodFamilyId, brewerProfileId),
            ),
        )
    }

    private fun unavailable(
        availability: DurableBrewGuidanceAvailability,
        stageSafetyMessages: List<StageSafetyMessage>,
        visualStatus: DurableBrewGuidanceVisualStatus,
        policy: ResolvedGuidancePolicy? = null,
    ): DurableBrewSessionGuidanceResolution = DurableBrewSessionGuidanceResolution(
        policy = policy,
        availability = availability,
        routineContent = emptyList(),
        criticalContent = emptyList(),
        stageSafetyMessages = stageSafetyMessages,
        visualStatus = visualStatus,
    )

    private fun Map<String, GuidancePresentationLevel>.toKnownProfileOverrides():
        Map<BrewerProfileId, GuidancePresentationLevel> = mapNotNull { (rawId, level) ->
        rawId.toBrewerProfileIdOrNull()
            ?.takeIf { profileId -> brewingCatalog.findBrewerProfile(profileId) != null }
            ?.let { profileId -> profileId to level }
    }.toMap()

    private fun Map<String, GuidancePresentationLevel>.toKnownFamilyPreferences():
        Map<MethodFamilyId, GuidancePresentationLevel> = mapNotNull { (rawId, level) ->
        rawId.toMethodFamilyIdOrNull()
            ?.takeIf { familyId -> brewingCatalog.findMethodFamily(familyId) != null }
            ?.let { familyId -> familyId to level }
    }.toMap()

    private fun visualStatus(
        currentStage: CurrentBrewStagePresentation?,
        scope: ProfileScope?,
    ): DurableBrewGuidanceVisualStatus {
        if (currentStage == null) return DurableBrewGuidanceVisualStatus.NotRequested
        val assetId = currentStage.instructionAssetId
        if (assetId == null) {
            return if (currentStage.requiresIllustration) {
                DurableBrewGuidanceVisualStatus.RequiredAssetIdMissing
            } else {
                DurableBrewGuidanceVisualStatus.NotRequested
            }
        }
        val assetCatalog = instructionAssets ?: return DurableBrewGuidanceVisualStatus.ManifestUnavailable(
            assetId.value,
        )
        val asset = assetCatalog.find(assetId)
            ?: return DurableBrewGuidanceVisualStatus.MissingFromManifest(assetId.value)
        if (scope == null || !asset.matches(scope, currentStage)) {
            return DurableBrewGuidanceVisualStatus.ManifestScopeMismatch(assetId.value)
        }
        return if (asset.review.isApproved) {
            DurableBrewGuidanceVisualStatus.Approved(asset)
        } else {
            DurableBrewGuidanceVisualStatus.NotApproved(asset)
        }
    }

    private fun BuiltInGuidanceContent.belongsBeside(
        stage: CurrentBrewStagePresentation,
    ): Boolean = when (placement) {
        BuiltInGuidancePlacement.GLOBAL_SAFETY,
        BuiltInGuidancePlacement.UTILITY,
        -> true

        BuiltInGuidancePlacement.LIVE_STAGE -> stageId == stage.stageId || id == stage.contentId
        BuiltInGuidancePlacement.PREPARATION,
        BuiltInGuidancePlacement.COMPLETION,
        -> false
    }

    private fun BuiltInGuidanceContent.matchesPrimaryStageContent(
        stage: CurrentBrewStagePresentation,
    ): Boolean = id == stage.contentId || stageId == stage.stageId

    private fun BuiltInGuidanceContent.toResolvedContent(
        level: GuidancePresentationLevel,
    ): ResolvedBrewGuidanceContent {
        val showSupportingCopy = level in setOf(
            GuidancePresentationLevel.FULL,
            GuidancePresentationLevel.CUSTOM,
        )
        val compactInstruction = level in setOf(
            GuidancePresentationLevel.CONCISE,
            GuidancePresentationLevel.FOCUSED,
            GuidancePresentationLevel.UTILITIES_ONLY,
        )
        return ResolvedBrewGuidanceContent(
            id = id,
            placement = placement,
            instruction = if (compactInstruction) {
                text.conciseInstruction ?: text.primaryInstruction
            } else {
                text.primaryInstruction
            },
            explanation = text.explanation.takeIf { showSupportingCopy },
            tip = text.tip.takeIf { showSupportingCopy },
            warning = text.warning.takeIf { safetyCritical || showSupportingCopy },
            altText = text.altText,
            safetyCritical = safetyCritical,
        )
    }

    private fun InstructionAssetRecord.matches(
        scope: ProfileScope,
        stage: CurrentBrewStagePresentation,
    ): Boolean = familyId == scope.methodFamilyId &&
        (profileId == null || profileId == scope.brewerProfileId) &&
        stageId == stage.stageId &&
        contentId == stage.contentId

    private fun BrewingCatalog.containsProfile(profileId: BrewerProfileId): Boolean =
        findBrewerProfile(profileId) != null

    private fun BrewingCatalog.containsFamily(familyId: MethodFamilyId): Boolean =
        findMethodFamily(familyId) != null

    private data class ProfileScope(
        val methodFamilyId: MethodFamilyId,
        val brewerProfileId: BrewerProfileId,
    )
}

private fun String.toMethodFamilyIdOrNull(): MethodFamilyId? = runCatching {
    MethodFamilyId(this)
}.getOrNull()

private fun String.toBrewerProfileIdOrNull(): BrewerProfileId? = runCatching {
    BrewerProfileId(this)
}.getOrNull()

private fun requireUniqueContentIds(content: List<BuiltInGuidanceContent>) {
    require(content.map(BuiltInGuidanceContent::id).distinct().size == content.size) {
        "Duplicate guidance content IDs across durable-session catalogues"
    }
}
