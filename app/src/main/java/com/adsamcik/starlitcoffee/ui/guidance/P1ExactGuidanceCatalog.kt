package com.adsamcik.starlitcoffee.ui.guidance

import android.content.Context
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.SourceBrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.tracebox.Tracebox

/** Canonical illustration importance. It does not imply review approval. */
enum class P1ExactVisualPriority {
    OPTIONAL,
    MANDATORY,
    SAFETY_CRITICAL,
}

data class P1ExactFullGuidance(
    val imperativeInstruction: String,
    val conciseExplanation: String,
    val optionalPracticalTip: String?,
    val warning: String?,
    val observableCompletionCue: String,
    val textFreeIllustrationBrief: String,
    val accessibleAltText: String,
)

data class P1ExactConciseGuidance(
    val currentAction: String,
    val currentTarget: String,
    val completionCue: String,
    val essentialWarning: String?,
)

data class P1ExactFocusedGuidance(
    val actionLabel: String,
    val numericalOrStateTarget: String,
    val nextAction: String,
    val timerOrStageControlRequirement: String,
)

/**
 * Renderer-neutral selection of one of the four canonical source densities.
 * `CUSTOM` deliberately reuses full guidance because the source defines four,
 * not five, authored variants. It never changes execution or recipe values.
 */
data class P1ExactGuidancePresentation(
    val level: GuidancePresentationLevel,
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
)

/** Flat source-faithful stage record; grouping fields would hide quantity and cue semantics. */
@Suppress("LongParameterList")
data class P1ExactStageGuidance(
    val recipeId: BuiltInRecipeId,
    val sourceStageId: String,
    val order: Int,
    val stageId: StageId,
    val contentId: StageContentId,
    val instructionAssetId: InstructionAssetId,
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId,
    val action: String,
    val startTimeOrPrecedingCondition: String,
    val targetDurationOrRange: String,
    val addedWaterTarget: String,
    val cumulativeWaterTarget: String,
    val beverageYieldTarget: String,
    val equipmentState: String,
    val completionCriterion: String,
    val observableSigns: String,
    val evidenceSourceIds: List<String>,
    val visualPriority: P1ExactVisualPriority,
    val safetySeverity: StageSafetySeverity?,
    val full: P1ExactFullGuidance,
    val concise: P1ExactConciseGuidance,
    val focused: P1ExactFocusedGuidance,
    val utilitiesOnly: List<String>,
) {
    val warning: String?
        get() = full.warning

    val requiresSafetyCriticalExpertReview: Boolean
        get() = visualPriority == P1ExactVisualPriority.SAFETY_CRITICAL

    fun presentation(level: GuidancePresentationLevel): P1ExactGuidancePresentation = when (level) {
        GuidancePresentationLevel.FULL,
        GuidancePresentationLevel.CUSTOM,
        -> P1ExactGuidancePresentation(
            level = level,
            instruction = full.imperativeInstruction,
            target = concise.currentTarget,
            completionCue = full.observableCompletionCue,
            explanation = null,
            practicalTip = full.optionalPracticalTip,
            nextAction = null,
            controlRequirements = operationalCues,
            warning = warning,
            utilities = emptyList(),
            accessibleAltText = full.accessibleAltText,
        )

        GuidancePresentationLevel.CONCISE -> P1ExactGuidancePresentation(
            level = level,
            instruction = concise.currentAction,
            target = concise.currentTarget,
            completionCue = concise.completionCue,
            explanation = null,
            practicalTip = null,
            nextAction = null,
            controlRequirements = emptyList(),
            warning = warning,
            utilities = emptyList(),
            accessibleAltText = full.accessibleAltText,
        )

        GuidancePresentationLevel.FOCUSED -> P1ExactGuidancePresentation(
            level = level,
            instruction = focused.actionLabel,
            target = focused.numericalOrStateTarget,
            completionCue = null,
            explanation = null,
            practicalTip = null,
            nextAction = focused.nextAction,
            controlRequirements = operationalCues,
            warning = warning,
            utilities = emptyList(),
            accessibleAltText = full.accessibleAltText,
        )

        GuidancePresentationLevel.UTILITIES_ONLY -> P1ExactGuidancePresentation(
            level = level,
            instruction = null,
            target = null,
            completionCue = null,
            explanation = null,
            practicalTip = null,
            nextAction = null,
            controlRequirements = emptyList(),
            warning = warning,
            utilities = operationalCues,
            accessibleAltText = full.accessibleAltText,
        )
    }

    private val operationalCues: List<GuidanceOperationalCue>
        get() = utilitiesOnly.map(GuidanceOperationalCue::requireFromStableId)

    /** Compatibility adapter for the existing shared Learn/live catalogue. */
    fun toBuiltInGuidanceContent(
        terminologyCatalog: P1ExactTerminologyCatalog? = null,
    ): BuiltInGuidanceContent = BuiltInGuidanceContent(
        id = contentId,
        familyId = methodFamilyId,
        profileId = brewerProfileId,
        stageId = stageId,
        placement = BuiltInGuidancePlacement.LIVE_STAGE,
        text = GuidanceTextMetadata(
            primaryInstruction = full.imperativeInstruction,
            conciseInstruction = concise.currentAction,
            explanation = null,
            tip = full.optionalPracticalTip,
            warning = warning,
            altText = full.accessibleAltText,
        ),
        visibility = GuidanceVisibilityPolicy(
            visibleIn = GuidancePresentationLevel.entries.toSet(),
            alwaysVisible = isAuthoredCriticalWarning,
        ),
        // The routine instruction remains primary; structured stage safety is rendered separately.
        safetyCritical = false,
        authoredPresentations = GuidancePresentationLevel.entries.associateWith { level ->
            presentation(level).toAuthoredPresentation(
                warningOverride = warning,
            )
        },
        terminologyReferences = terminologyCatalog?.referencesFor(contentId).orEmpty(),
    )

    private fun P1ExactGuidancePresentation.toAuthoredPresentation(
        warningOverride: String? = warning,
    ) = AuthoredGuidancePresentation(
            instruction = instruction,
            target = target,
            completionCue = completionCue,
            explanation = explanation,
            practicalTip = practicalTip,
            nextAction = nextAction,
            controlRequirements = controlRequirements,
            warning = warningOverride,
            utilities = utilities,
            accessibleAltText = accessibleAltText,
        )

    private val isAuthoredCriticalWarning: Boolean
        get() = safetySeverity == StageSafetySeverity.CRITICAL && warning != null
}

data class P1ExactRecipeGuidance(
    val recipeId: BuiltInRecipeId,
    val recipeName: String,
    val sourceMethodFamilyId: String,
    val sourceBrewerProfileId: SourceBrewerProfileId,
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId,
    val recipeApproach: String,
    val evidenceStatus: String,
    val confidence: String,
    val originalSourceOrProvenance: String,
    val stages: List<P1ExactStageGuidance>,
)

/** Immutable, exact-recipe-scoped lookup with no profile-level fallback. */
class P1ExactGuidanceCatalog internal constructor(
    val recipes: List<P1ExactRecipeGuidance>,
) {
    val stages: List<P1ExactStageGuidance> = recipes.flatMap(P1ExactRecipeGuidance::stages)

    private val recipesById = recipes.associateBy(P1ExactRecipeGuidance::recipeId)
    private val stagesByContentId = stages.associateBy(P1ExactStageGuidance::contentId)

    init {
        require(recipesById.size == recipes.size) { "Duplicate exact P1 guidance recipe ID" }
        require(stagesByContentId.size == stages.size) { "Duplicate exact P1 stage content ID" }
    }

    fun findRecipe(recipeId: BuiltInRecipeId): P1ExactRecipeGuidance? = recipesById[recipeId]

    fun findStage(contentId: StageContentId): P1ExactStageGuidance? = stagesByContentId[contentId]

    fun forRecipe(
        recipeId: BuiltInRecipeId,
        terminologyCatalog: P1ExactTerminologyCatalog? = null,
    ): BuiltInGuidanceCatalog? = findRecipe(recipeId)
        ?.stages
        ?.map { stage ->
            stage.toBuiltInGuidanceContent(
                terminologyCatalog = terminologyCatalog,
            )
        }
        ?.let(::BuiltInGuidanceCatalog)

    fun asBuiltInGuidanceCatalog(
        terminologyCatalog: P1ExactTerminologyCatalog? = null,
    ): BuiltInGuidanceCatalog = BuiltInGuidanceCatalog(
        stages.map { stage -> stage.toBuiltInGuidanceContent(terminologyCatalog) },
    )
}

/** Strict decoder for the generated built-in asset. Invalid content fails closed. */
object BuiltInP1ExactGuidanceCatalog {
    const val ASSET_NAME = "p1_exact_guidance_2026_07_27.json"
    const val SOURCE_SHA256 = BuiltInP1RecipeCatalog.SOURCE_SHA256
    const val SOURCE_SCHEMA_VERSION = BuiltInP1RecipeCatalog.SOURCE_SCHEMA_VERSION
    const val SOURCE_EXECUTION_DATE = "2026-07-27"

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun decode(encoded: String): P1ExactGuidanceCatalog =
        json.decodeFromString<P1ExactGuidanceManifestDto>(encoded).toCatalog()
}

sealed interface BuiltInP1ExactGuidanceLoadResult {
    data class Loaded(
        val catalog: P1ExactGuidanceCatalog,
        val localeTag: String = "en",
    ) : BuiltInP1ExactGuidanceLoadResult

    data class Unavailable(val reason: String) : BuiltInP1ExactGuidanceLoadResult
}

/** Application-scoped, fail-closed loader for the packaged exact guidance. */
object BuiltInP1ExactGuidanceLoader {

    private val cacheByLocale = mutableMapOf<String, BuiltInP1ExactGuidanceLoadResult>()

    fun getInstance(context: Context): BuiltInP1ExactGuidanceLoadResult {
        val applicationContext = context.applicationContext
        val localeKey = P1ExactGuidanceLocaleResolver.resolve(applicationContext)
        return synchronized(this) {
            cacheByLocale.getOrPut(localeKey) {
                load(
                    context = applicationContext,
                    localeTag = localeKey,
                )
            }
        }
    }

    private fun load(
        context: Context,
        localeTag: String,
    ): BuiltInP1ExactGuidanceLoadResult = try {
        val encoded = context.resources.openRawResource(R.raw.p1_exact_guidance)
            .bufferedReader()
            .use { reader -> reader.readText() }
        BuiltInP1ExactGuidanceLoadResult.Loaded(
            catalog = BuiltInP1ExactGuidanceCatalog.decode(encoded),
            localeTag = localeTag,
        )
    } catch (exception: IOException) {
        unavailable(exception)
    } catch (exception: RuntimeException) {
        unavailable(exception)
    }

    private fun unavailable(exception: Exception): BuiltInP1ExactGuidanceLoadResult.Unavailable {
        Tracebox.log.error(exception, "Exact P1 guidance is unavailable; no fallback will be used")
        return BuiltInP1ExactGuidanceLoadResult.Unavailable(
            reason = exception.message ?: exception::class.java.simpleName,
        )
    }
}

private fun P1ExactGuidanceManifestDto.toCatalog(): P1ExactGuidanceCatalog {
    require(sourceSchemaVersion == BuiltInP1ExactGuidanceCatalog.SOURCE_SCHEMA_VERSION) {
        "Exact P1 guidance schema version mismatch"
    }
    require(sourceExecutionDate == BuiltInP1ExactGuidanceCatalog.SOURCE_EXECUTION_DATE) {
        "Exact P1 guidance source date mismatch"
    }
    require(sourceSha256 == BuiltInP1ExactGuidanceCatalog.SOURCE_SHA256) {
        "Exact P1 guidance source hash mismatch"
    }
    require(recipeCount == EXPECTED_RECIPE_COUNT && stageCount == EXPECTED_STAGE_COUNT) {
        "Exact P1 guidance manifest counts must remain 20 recipes and 114 stages"
    }

    val definitions = BuiltInP1RecipeCatalog.recipes
    require(recipes.map(P1ExactRecipeDto::recipeId) == definitions.map { it.id.value }) {
        "Exact P1 guidance recipe order or identity differs from the canonical catalog"
    }

    val mappedRecipes = recipes.zip(definitions).map { (sourceRecipe, definition) ->
        require(sourceRecipe.methodFamilyId == definition.sourceMethodFamilyId) {
            "Source method family differs for ${sourceRecipe.recipeId}"
        }
        require(sourceRecipe.brewerProfileId == definition.sourceBrewerProfileId.value) {
            "Source brewer profile differs for ${sourceRecipe.recipeId}"
        }
        require(sourceRecipe.stages.size == definition.orderedStageCount) {
            "Ordered stage count differs for ${sourceRecipe.recipeId}"
        }
        sourceRecipe.requireRecipeCopy()

        val planStages = requireNotNull(BuiltInP1ExactStagePlanCatalog.find(definition.id)) {
            "Missing exact plan for ${sourceRecipe.recipeId}"
        }.nodes.map { node ->
            require(node is StagePlanNode.Stage) {
                "Exact P1 plans must remain direct ordered stages"
            }
            node.definition
        }
        require(planStages.size == sourceRecipe.stages.size) {
            "Exact plan stage count differs for ${sourceRecipe.recipeId}"
        }

        P1ExactRecipeGuidance(
            recipeId = definition.id,
            recipeName = sourceRecipe.recipeName,
            sourceMethodFamilyId = sourceRecipe.methodFamilyId,
            sourceBrewerProfileId = SourceBrewerProfileId(sourceRecipe.brewerProfileId),
            methodFamilyId = definition.methodFamilyId,
            brewerProfileId = definition.brewerProfileId,
            recipeApproach = sourceRecipe.recipeApproach,
            evidenceStatus = sourceRecipe.evidenceStatus,
            confidence = sourceRecipe.confidence,
            originalSourceOrProvenance = sourceRecipe.originalSourceOrProvenance,
            stages = sourceRecipe.stages.zip(planStages).map { (stage, planStage) ->
                stage.toDomain(
                    recipeId = definition.id,
                    recipeSourceIds = definition.evidence.sourceIds,
                    methodFamilyId = definition.methodFamilyId,
                    brewerProfileId = definition.brewerProfileId,
                    planStage = planStage,
                )
            },
        ).applyVerifiedFactualErrata()
    }
    require(mappedRecipes.sumOf { recipe -> recipe.stages.size } == stageCount) {
        "Exact P1 guidance stage total differs from its manifest"
    }
    return P1ExactGuidanceCatalog(mappedRecipes)
}

private fun P1ExactRecipeDto.requireRecipeCopy() {
    listOf(
        recipeName,
        recipeApproach,
        evidenceStatus,
        confidence,
        originalSourceOrProvenance,
    ).forEach { value -> require(value.isNotBlank()) { "Exact recipe copy cannot be blank" } }
}

// Validation intentionally stays beside mapping so no unverified source field can enter runtime.
@Suppress("LongMethod", "LongParameterList")
private fun P1ExactStageDto.toDomain(
    recipeId: BuiltInRecipeId,
    recipeSourceIds: Set<String>,
    methodFamilyId: MethodFamilyId,
    brewerProfileId: BrewerProfileId,
    planStage: BrewStageDefinition,
): P1ExactStageGuidance {
    val expectedSourceStageId = "stage_${order.toString().padStart(2, '0')}"
    require(stageId == expectedSourceStageId) { "Source stage order differs for ${recipeId.value}" }
    require(planStage.id.value == "p1_${recipeId.value}_$stageId") {
        "Exact stage ID differs for ${recipeId.value}/$stageId"
    }
    require(planStage.contentId.value == "${planStage.id.value}_instruction") {
        "Exact content ID differs for ${recipeId.value}/$stageId"
    }
    val planAssetId = requireNotNull(planStage.instructionAssetId) {
        "Exact instruction asset ID is missing for ${recipeId.value}/$stageId"
    }
    require(planAssetId.value == "instruction_${planStage.contentId.value}_default") {
        "Exact instruction asset ID differs for ${recipeId.value}/$stageId"
    }

    requireStageCopy(recipeId)
    val normalizedTip = optionalTip.optionalSourceText()
    val normalizedWarning = warning.optionalSourceText()
    require(full.imperativeInstruction.removeTerminalPeriod() == action.removeTerminalPeriod()) {
        "Full action differs for ${recipeId.value}/$stageId"
    }
    require(concise.currentAction.removeTerminalPeriod() == action.removeTerminalPeriod()) {
        "Concise action differs for ${recipeId.value}/$stageId"
    }
    require(focused.actionLabel.removeTerminalPeriod() == action.removeTerminalPeriod()) {
        "Focused action differs for ${recipeId.value}/$stageId"
    }
    require(full.observableCompletionCue == completionCriterion) {
        "Full completion cue differs for ${recipeId.value}/$stageId"
    }
    require(concise.completionCue == completionCriterion) {
        "Concise completion cue differs for ${recipeId.value}/$stageId"
    }
    require(full.optionalPracticalTip.optionalSourceText() == normalizedTip) {
        "Full practical tip differs for ${recipeId.value}/$stageId"
    }
    require(full.warning.optionalSourceText() == normalizedWarning) {
        "Full warning differs for ${recipeId.value}/$stageId"
    }
    require(concise.essentialWarning.optionalSourceText() == normalizedWarning) {
        "Concise warning differs for ${recipeId.value}/$stageId"
    }
    require(evidenceSources.isNotEmpty() && evidenceSources.toSet().size == evidenceSources.size) {
        "Stage evidence sources must be non-empty and unique for ${recipeId.value}/$stageId"
    }
    require(evidenceSources.all { source -> source in recipeSourceIds }) {
        "Stage evidence escapes recipe provenance for ${recipeId.value}/$stageId"
    }
    require(utilitiesOnly.isNotEmpty() && utilitiesOnly.toSet().size == utilitiesOnly.size) {
        "Utilities-only guidance must be non-empty and unique for ${recipeId.value}/$stageId"
    }
    require(utilitiesOnly.all { utility -> utility.matches(STABLE_UTILITY_ID) }) {
        "Utility IDs must use lower snake case for ${recipeId.value}/$stageId"
    }
    require(utilitiesOnly.all { utility -> GuidanceOperationalCue.fromStableId(utility) != null }) {
        "Unknown operational cue for ${recipeId.value}/$stageId"
    }
    require(focused.timerOrStageControlRequirement == utilitiesOnly.joinToString()) {
        "Focused controls differ from utilities-only guidance for ${recipeId.value}/$stageId"
    }

    val priority = visualPriority.toDomain()
    require(planStage.requiresIllustration == (priority != P1ExactVisualPriority.OPTIONAL)) {
        "Illustration requirement differs for ${recipeId.value}/$stageId"
    }
    require(planStage.safetyMessages.size <= 1) {
        "Exact stages support at most one action-local safety message for ${recipeId.value}/$stageId"
    }
    val safetySeverity = planStage.safetyMessages.singleOrNull()?.severity
    require(safetySeverity == null || normalizedWarning != null) {
        "Action-local safety requires authored warning copy for ${recipeId.value}/$stageId"
    }

    return P1ExactStageGuidance(
        recipeId = recipeId,
        sourceStageId = stageId,
        order = order,
        stageId = planStage.id,
        contentId = planStage.contentId,
        instructionAssetId = planAssetId,
        methodFamilyId = methodFamilyId,
        brewerProfileId = brewerProfileId,
        action = action,
        startTimeOrPrecedingCondition = startTimeOrPrecedingCondition,
        targetDurationOrRange = targetDurationOrRange,
        addedWaterTarget = addedWaterTarget,
        cumulativeWaterTarget = cumulativeWaterTarget,
        beverageYieldTarget = beverageYieldTarget,
        equipmentState = equipmentState,
        completionCriterion = completionCriterion,
        observableSigns = observableSigns,
        evidenceSourceIds = evidenceSources,
        visualPriority = priority,
        safetySeverity = safetySeverity,
        full = P1ExactFullGuidance(
            imperativeInstruction = full.imperativeInstruction,
            conciseExplanation = full.conciseExplanation,
            optionalPracticalTip = normalizedTip,
            warning = normalizedWarning,
            observableCompletionCue = full.observableCompletionCue,
            textFreeIllustrationBrief = full.textFreeIllustrationBrief,
            accessibleAltText = full.accessibleAltText,
        ),
        concise = P1ExactConciseGuidance(
            currentAction = concise.currentAction,
            currentTarget = concise.currentTarget,
            completionCue = concise.completionCue,
            essentialWarning = normalizedWarning,
        ),
        focused = P1ExactFocusedGuidance(
            actionLabel = focused.actionLabel,
            numericalOrStateTarget = focused.numericalOrStateTarget,
            nextAction = focused.nextAction,
            timerOrStageControlRequirement = focused.timerOrStageControlRequirement,
        ),
        utilitiesOnly = utilitiesOnly,
    ).applyVerifiedFactualErrata()
}

private fun P1ExactStageDto.requireStageCopy(recipeId: BuiltInRecipeId) {
    listOf(
        action,
        startTimeOrPrecedingCondition,
        targetDurationOrRange,
        addedWaterTarget,
        cumulativeWaterTarget,
        beverageYieldTarget,
        equipmentState,
        completionCriterion,
        observableSigns,
        full.imperativeInstruction,
        full.conciseExplanation,
        full.observableCompletionCue,
        full.textFreeIllustrationBrief,
        full.accessibleAltText,
        concise.currentAction,
        concise.currentTarget,
        concise.completionCue,
        focused.actionLabel,
        focused.numericalOrStateTarget,
        focused.nextAction,
        focused.timerOrStageControlRequirement,
    ).forEach { value ->
        require(value.isNotBlank()) { "Exact stage copy cannot be blank for ${recipeId.value}/$stageId" }
    }
}

private fun String.optionalSourceText(): String? = when (this) {
    SOURCE_NONE -> null
    else -> also { value -> require(value.isNotBlank()) { "Source text cannot be blank" } }
}

private fun String.removeTerminalPeriod(): String = trim().removeSuffix(".")

private fun P1ExactVisualPriorityDto.toDomain(): P1ExactVisualPriority = when (this) {
    P1ExactVisualPriorityDto.OPTIONAL -> P1ExactVisualPriority.OPTIONAL
    P1ExactVisualPriorityDto.MANDATORY -> P1ExactVisualPriority.MANDATORY
    P1ExactVisualPriorityDto.SAFETY_CRITICAL -> P1ExactVisualPriority.SAFETY_CRITICAL
}

@Serializable
private data class P1ExactGuidanceManifestDto(
    @SerialName("source_schema_version") val sourceSchemaVersion: String,
    @SerialName("source_execution_date") val sourceExecutionDate: String,
    @SerialName("source_sha256") val sourceSha256: String,
    @SerialName("recipe_count") val recipeCount: Int,
    @SerialName("stage_count") val stageCount: Int,
    val recipes: List<P1ExactRecipeDto>,
)

@Serializable
private data class P1ExactRecipeDto(
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("recipe_name") val recipeName: String,
    @SerialName("method_family_id") val methodFamilyId: String,
    @SerialName("brewer_profile_id") val brewerProfileId: String,
    @SerialName("recipe_approach") val recipeApproach: String,
    @SerialName("evidence_status") val evidenceStatus: String,
    val confidence: String,
    @SerialName("original_source_or_provenance") val originalSourceOrProvenance: String,
    val stages: List<P1ExactStageDto>,
)

@Serializable
// Mirrors the canonical JSON schema exactly; nested convenience DTOs would fragment compatibility.
@Suppress("LongParameterList")
private data class P1ExactStageDto(
    @SerialName("stage_id") val stageId: String,
    val order: Int,
    val action: String,
    @SerialName("start_time_or_preceding_condition") val startTimeOrPrecedingCondition: String,
    @SerialName("target_duration_or_range") val targetDurationOrRange: String,
    @SerialName("added_water_target") val addedWaterTarget: String,
    @SerialName("cumulative_water_target") val cumulativeWaterTarget: String,
    @SerialName("beverage_yield_target") val beverageYieldTarget: String,
    @SerialName("equipment_state") val equipmentState: String,
    @SerialName("completion_criterion") val completionCriterion: String,
    @SerialName("observable_signs") val observableSigns: String,
    @SerialName("optional_tip") val optionalTip: String,
    val warning: String,
    @SerialName("evidence_sources") val evidenceSources: List<String>,
    @SerialName("visual_priority") val visualPriority: P1ExactVisualPriorityDto,
    val guidance: P1ExactGuidanceDto,
) {
    val full: P1ExactFullGuidanceDto
        get() = guidance.full
    val concise: P1ExactConciseGuidanceDto
        get() = guidance.concise
    val focused: P1ExactFocusedGuidanceDto
        get() = guidance.focused
    val utilitiesOnly: List<String>
        get() = guidance.utilitiesOnly
}

@Serializable
private enum class P1ExactVisualPriorityDto {
    @SerialName("optional")
    OPTIONAL,

    @SerialName("mandatory")
    MANDATORY,

    @SerialName("safety-critical")
    SAFETY_CRITICAL,
}

@Serializable
private data class P1ExactGuidanceDto(
    val full: P1ExactFullGuidanceDto,
    val concise: P1ExactConciseGuidanceDto,
    val focused: P1ExactFocusedGuidanceDto,
    @SerialName("utilities_only") val utilitiesOnly: List<String>,
)

@Serializable
private data class P1ExactFullGuidanceDto(
    @SerialName("imperative_instruction") val imperativeInstruction: String,
    @SerialName("concise_explanation") val conciseExplanation: String,
    @SerialName("optional_practical_tip") val optionalPracticalTip: String,
    val warning: String,
    @SerialName("observable_completion_cue") val observableCompletionCue: String,
    @SerialName("text_free_illustration_brief") val textFreeIllustrationBrief: String,
    @SerialName("accessible_alt_text") val accessibleAltText: String,
)

@Serializable
private data class P1ExactConciseGuidanceDto(
    @SerialName("current_action") val currentAction: String,
    @SerialName("current_target") val currentTarget: String,
    @SerialName("completion_cue") val completionCue: String,
    @SerialName("essential_warning") val essentialWarning: String,
)

@Serializable
private data class P1ExactFocusedGuidanceDto(
    @SerialName("action_label") val actionLabel: String,
    @SerialName("numerical_or_state_target") val numericalOrStateTarget: String,
    @SerialName("next_action") val nextAction: String,
    @SerialName("timer_or_stage_control_requirement") val timerOrStageControlRequirement: String,
)

private const val EXPECTED_RECIPE_COUNT = 20
private const val EXPECTED_STAGE_COUNT = 114
private const val SOURCE_NONE = "None"
private val STABLE_UTILITY_ID = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
