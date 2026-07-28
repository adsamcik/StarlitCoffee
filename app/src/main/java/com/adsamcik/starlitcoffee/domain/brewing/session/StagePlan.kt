package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId

private val stagePlanIdentifierPattern = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

private fun requireStagePlanIdentifier(value: String, label: String) {
    require(value.matches(stagePlanIdentifierPattern)) {
        "$label must use lower_snake_case: $value"
    }
}

/** A named, compile-time-bounded branch in a stage plan. */
@JvmInline
value class StageConditionId(val value: String) {
    init {
        requireStagePlanIdentifier(value, "Stage condition IDs")
    }
}

/** A named, compile-time-bounded repeated section in a stage plan. */
@JvmInline
value class StageRepeatId(val value: String) {
    init {
        requireStagePlanIdentifier(value, "Stage repeat IDs")
    }
}

/**
 * An equipment state is deliberately a stable domain token rather than a UI
 * label. The brewing catalogue can map it to concrete equipment later.
 */
@JvmInline
value class StageEquipmentStateId(val value: String) {
    init {
        requireStagePlanIdentifier(value, "Equipment state IDs")
    }
}

/** An observed physical event that can complete a stage. */
@JvmInline
value class StageObservationId(val value: String) {
    init {
        requireStagePlanIdentifier(value, "Observation IDs")
    }
}

/** An externally recorded marker, such as a machine completion signal. */
@JvmInline
value class StageMarkerId(val value: String) {
    init {
        requireStagePlanIdentifier(value, "Marker IDs")
    }
}

/**
 * A concrete execution occurrence. Repeated source stages retain one stable
 * [StageId] while each compiled occurrence receives a unique durable key.
 */
data class StageInstanceId(
    val sourceStageId: StageId,
    val occurrence: Int,
) {
    init {
        require(occurrence > 0) { "Stage occurrence must be positive" }
    }

    val persistentKey: String
        get() = "${sourceStageId.value}_$occurrence"
}

/**
 * Actions intentionally describe the user-facing task rather than a brewer
 * implementation. New actions can be added without creating a separate timer
 * or renderer for every method family.
 */
enum class BrewStageAction {
    PREPARE,
    RINSE,
    ADD_COFFEE,
    ADD_WATER,
    BLOOM,
    POUR,
    AGITATE,
    STEEP,
    RELEASE,
    PRESS,
    HEAT,
    OBSERVE,
    FILTER,
    SERVE,
    CLEAN_UP,
    CUSTOM,
}

enum class StageSafetySeverity {
    ADVICE,
    WARNING,
    CRITICAL,
}

/** A structured safety code; localized copy belongs in the presentation layer. */
data class StageSafetyMessage(
    val code: String,
    val severity: StageSafetySeverity,
) {
    init {
        require(code.isNotBlank()) { "Safety codes cannot be blank" }
    }
}

data class StageEquipmentRequirement(
    val requiredState: StageEquipmentStateId,
)

/**
 * The policy is data rather than UI behavior, so notifications and in-app
 * feedback can be driven from the same stage transition.
 */
data class StageAlertPolicy(
    val alertOnStart: Boolean = false,
    val alertOnCompletion: Boolean = true,
    val scheduleDeadline: Boolean = true,
)

/** How literally a source target should be interpreted by guidance and utilities. */
enum class StageTargetQualifier {
    EXACT,
    APPROXIMATE,
    RANGE,
    NO_LATER_THAN,
    NO_EARLIER_THAN,
    STARTING_POINT,
}

/** Stable identity of one source cue when a stage exposes several reference targets. */
@JvmInline
value class StageTargetId(val value: String) {
    init {
        requireStagePlanIdentifier(value, "Stage target IDs")
    }
}

/** Time targets can describe the current stage or the complete brew clock. */
enum class StageTimeReference {
    STAGE_DURATION,
    BREW_ELAPSED_AT_START,
    BREW_ELAPSED_AT_COMPLETION,
}

data class StageTimeTarget(
    val reference: StageTimeReference,
    val id: StageTargetId,
    val qualifier: StageTargetQualifier,
    val minimumMillis: Long,
    val maximumMillis: Long = minimumMillis,
) {
    init {
        require(minimumMillis >= 0L) { "Time-target minimum cannot be negative" }
        require(maximumMillis >= minimumMillis) { "Time-target maximum cannot precede minimum" }
        if (qualifier == StageTargetQualifier.EXACT) {
            require(minimumMillis == maximumMillis) { "An exact time target must have one value" }
        }
    }
}

enum class StageMassReference {
    STAGE_ADDED,
    BREW_CUMULATIVE,
    RECIPE_TOTAL,
}

data class StageMassTarget(
    val id: StageTargetId,
    val role: QuantityRole,
    val reference: StageMassReference,
    val qualifier: StageTargetQualifier,
    val minimumGrams: Double,
    val maximumGrams: Double = minimumGrams,
) {
    init {
        require(minimumGrams.isFinite() && minimumGrams > 0.0) {
            "Mass-target minimum must be positive and finite"
        }
        require(maximumGrams.isFinite() && maximumGrams >= minimumGrams) {
            "Mass-target maximum cannot precede minimum"
        }
        if (qualifier == StageTargetQualifier.EXACT) {
            require(minimumGrams == maximumGrams) { "An exact mass target must have one value" }
        }
    }
}

data class StageTemperatureTarget(
    val qualifier: StageTargetQualifier,
    val minimumC: Double,
    val maximumC: Double = minimumC,
) {
    init {
        require(minimumC.isFinite() && minimumC in 0.0..100.0) {
            "Temperature-target minimum is invalid"
        }
        require(maximumC.isFinite() && maximumC in minimumC..100.0) {
            "Temperature-target maximum cannot precede minimum"
        }
        if (qualifier == StageTargetQualifier.EXACT) {
            require(minimumC == maximumC) { "An exact temperature target must have one value" }
        }
    }
}

/**
 * Source reference values shown alongside, but independent from, the single
 * completion trigger. A pour can therefore retain both its mass target and a
 * total-elapsed cue without asking the reducer to guess which value advances
 * the stage.
 */
data class StageReferenceTargets(
    val timeTargets: List<StageTimeTarget> = emptyList(),
    val massTargets: List<StageMassTarget> = emptyList(),
    val temperatureTarget: StageTemperatureTarget? = null,
) {
    init {
        require(timeTargets.map(StageTimeTarget::id).distinct().size == timeTargets.size) {
            "A stage cannot define two reference targets with the same stable ID"
        }
        require(massTargets.map(StageMassTarget::id).distinct().size == massTargets.size) {
            "A stage cannot define two mass targets with the same stable ID"
        }
    }
}

/**
 * A completion rule has all data for its single trigger in one immutable
 * value. Additional source reference targets stay in [StageReferenceTargets],
 * preventing a timer cue from being interpreted as a mass trigger by a
 * renderer or reducer.
 */
sealed interface StageCompletionMode {
    data object Manual : StageCompletionMode

    data object Immediate : StageCompletionMode

    data class Countdown(val durationMillis: Long) : StageCompletionMode

    /**
     * A user may advance at [minimumMillis]; the stage automatically advances
     * at [maximumMillis] so an interrupted brew cannot remain indefinitely in
     * a completed target window.
     */
    data class ElapsedRange(
        val minimumMillis: Long,
        val maximumMillis: Long,
    ) : StageCompletionMode

    data class CumulativeAmount(val targetGrams: Double) : StageCompletionMode

    data class AddedAmount(val targetGrams: Double) : StageCompletionMode

    data class BeverageYield(val targetGrams: Double) : StageCompletionMode

    data class ObservedEvent(val observationId: StageObservationId) : StageCompletionMode

    data class ExternalMarker(val markerId: StageMarkerId) : StageCompletionMode
}

/** One immutable source-stage definition before optional and repeat expansion. */
data class BrewStageDefinition(
    val id: StageId,
    val action: BrewStageAction,
    val contentId: StageContentId,
    val instructionAssetId: InstructionAssetId? = null,
    val requiresIllustration: Boolean = false,
    val safetyMessages: List<StageSafetyMessage> = emptyList(),
    val equipmentRequirement: StageEquipmentRequirement? = null,
    val completionMode: StageCompletionMode,
    val referenceTargets: StageReferenceTargets = StageReferenceTargets(),
    val alertPolicy: StageAlertPolicy = StageAlertPolicy(),
    val isSkippable: Boolean = false,
)

/**
 * Stage plans are intentionally a finite tree. There is no expression language
 * and no unbounded looping construct, which makes compilation deterministic and
 * makes a persisted compiled plan safe to restore after an app update.
 */
sealed interface StagePlanNode {
    data class Stage(val definition: BrewStageDefinition) : StagePlanNode

    data class OptionalSection(
        val conditionId: StageConditionId,
        val nodes: List<StagePlanNode>,
    ) : StagePlanNode

    data class BoundedRepeat(
        val repeatId: StageRepeatId,
        val minimumOccurrences: Int = 0,
        val maximumOccurrences: Int,
        val nodes: List<StagePlanNode>,
    ) : StagePlanNode
}

data class BrewStagePlan(
    val id: StagePlanId,
    val version: Int,
    val nodes: List<StagePlanNode>,
)

/** Concrete user choices used to compile optional and repeated plan sections. */
data class StagePlanSelections(
    val includedConditions: Set<StageConditionId> = emptySet(),
    val repeatCounts: Map<StageRepeatId, Int> = emptyMap(),
)

/**
 * Optional catalogue context supplied by the owning feature. The session
 * package remains Android-free and does not depend on drawable or string IDs.
 */
data class StagePlanValidationContext(
    val knownContentIds: Set<StageContentId>? = null,
    val knownInstructionAssetIds: Set<InstructionAssetId>? = null,
    val availableEquipmentStates: Set<StageEquipmentStateId>? = null,
)

enum class StagePlanValidationCode {
    INVALID_PLAN_VERSION,
    EMPTY_PLAN,
    EMPTY_COMPILED_PLAN,
    EMPTY_SECTION,
    DUPLICATE_STAGE_ID,
    DUPLICATE_REPEAT_ID,
    INVALID_REPEAT_BOUNDS,
    REPEAT_COUNT_OUT_OF_RANGE,
    UNKNOWN_CONTENT,
    MISSING_INSTRUCTION_ASSET,
    UNKNOWN_INSTRUCTION_ASSET,
    INVALID_COMPLETION_TARGET,
    INCOMPATIBLE_EQUIPMENT_STATE,
    HIDDEN_CRITICAL_SAFETY,
}

data class StagePlanValidationIssue(
    val code: StagePlanValidationCode,
    val path: String,
)

data class StagePlanValidationResult(
    val issues: List<StagePlanValidationIssue>,
) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

data class CompiledBrewStage(
    val instanceId: StageInstanceId,
    val definition: BrewStageDefinition,
)

data class CompiledStagePlan(
    val id: StagePlanId,
    val version: Int,
    val stages: List<CompiledBrewStage>,
) {
    val stageIds: List<StageInstanceId>
        get() = stages.map(CompiledBrewStage::instanceId)
}

sealed interface StagePlanCompileResult {
    data class Compiled(val value: CompiledStagePlan) : StagePlanCompileResult

    data class Invalid(val issues: List<StagePlanValidationIssue>) : StagePlanCompileResult
}

/** Validates structural constraints independently from a specific recipe selection. */
object StagePlanValidator {

    fun validate(
        plan: BrewStagePlan,
        context: StagePlanValidationContext = StagePlanValidationContext(),
    ): StagePlanValidationResult {
        val issues = mutableListOf<StagePlanValidationIssue>()
        val seenRepeats = mutableSetOf<StageRepeatId>()
        val stageValidation = StageDefinitionValidationPass(context, issues)

        if (plan.version <= 0) {
            issues += StagePlanValidationIssue(StagePlanValidationCode.INVALID_PLAN_VERSION, "plan")
        }
        if (plan.nodes.isEmpty()) {
            issues += StagePlanValidationIssue(StagePlanValidationCode.EMPTY_PLAN, "plan")
        }

        fun validateNodes(
            nodes: List<StagePlanNode>,
            path: String,
            isConditionallyVisible: Boolean,
        ) {
            nodes.forEachIndexed { index, node ->
                val nodePath = "$path/$index"
                when (node) {
                    is StagePlanNode.Stage -> stageValidation.validate(
                        stage = node.definition,
                        path = nodePath,
                        isConditionallyVisible = isConditionallyVisible,
                    )

                    is StagePlanNode.OptionalSection -> {
                        if (node.nodes.isEmpty()) {
                            issues += StagePlanValidationIssue(StagePlanValidationCode.EMPTY_SECTION, nodePath)
                        }
                        validateNodes(
                            nodes = node.nodes,
                            path = nodePath,
                            isConditionallyVisible = true,
                        )
                    }

                    is StagePlanNode.BoundedRepeat -> {
                        if (!seenRepeats.add(node.repeatId)) {
                            issues += StagePlanValidationIssue(
                                StagePlanValidationCode.DUPLICATE_REPEAT_ID,
                                nodePath,
                            )
                        }
                        if (
                            node.minimumOccurrences < 0 ||
                            node.maximumOccurrences < node.minimumOccurrences ||
                            node.maximumOccurrences <= 0
                        ) {
                            issues += StagePlanValidationIssue(
                                StagePlanValidationCode.INVALID_REPEAT_BOUNDS,
                                nodePath,
                            )
                        }
                        if (node.nodes.isEmpty()) {
                            issues += StagePlanValidationIssue(StagePlanValidationCode.EMPTY_SECTION, nodePath)
                        }
                        validateNodes(
                            nodes = node.nodes,
                            path = nodePath,
                            isConditionallyVisible = isConditionallyVisible,
                        )
                    }
                }
            }
        }

        validateNodes(plan.nodes, "plan", isConditionallyVisible = false)
        return StagePlanValidationResult(issues)
    }
}

private class StageDefinitionValidationPass(
    private val context: StagePlanValidationContext,
    private val issues: MutableList<StagePlanValidationIssue>,
) {
    private val seenStages = mutableSetOf<StageId>()

    fun validate(
        stage: BrewStageDefinition,
        path: String,
        isConditionallyVisible: Boolean,
    ) {
        if (!seenStages.add(stage.id)) {
            issues += StagePlanValidationIssue(StagePlanValidationCode.DUPLICATE_STAGE_ID, path)
        }
        if (context.knownContentIds?.contains(stage.contentId) == false) {
            issues += StagePlanValidationIssue(StagePlanValidationCode.UNKNOWN_CONTENT, path)
        }
        if (stage.requiresIllustration && stage.instructionAssetId == null) {
            issues += StagePlanValidationIssue(
                StagePlanValidationCode.MISSING_INSTRUCTION_ASSET,
                path,
            )
        }
        if (
            stage.instructionAssetId != null &&
            context.knownInstructionAssetIds?.contains(stage.instructionAssetId) == false
        ) {
            issues += StagePlanValidationIssue(
                StagePlanValidationCode.UNKNOWN_INSTRUCTION_ASSET,
                path,
            )
        }
        if (
            stage.equipmentRequirement != null &&
            context.availableEquipmentStates?.contains(
                stage.equipmentRequirement.requiredState,
            ) == false
        ) {
            issues += StagePlanValidationIssue(
                StagePlanValidationCode.INCOMPATIBLE_EQUIPMENT_STATE,
                path,
            )
        }
        if (
            isConditionallyVisible &&
            stage.safetyMessages.any { it.severity == StageSafetySeverity.CRITICAL }
        ) {
            issues += StagePlanValidationIssue(
                StagePlanValidationCode.HIDDEN_CRITICAL_SAFETY,
                path,
            )
        }
        if (
            !StageCompletionContractValidator.isValid(
                stage.completionMode,
                stage.referenceTargets,
            )
        ) {
            issues += StagePlanValidationIssue(
                StagePlanValidationCode.INVALID_COMPLETION_TARGET,
                path,
            )
        }
    }
}

private object StageCompletionContractValidator {

    fun isValid(
        completionMode: StageCompletionMode,
        targets: StageReferenceTargets,
    ): Boolean = completionMode.isValid() && completionMode.isConsistentWith(targets)

    private fun StageCompletionMode.isValid(): Boolean = when (this) {
        StageCompletionMode.Immediate,
        StageCompletionMode.Manual,
        -> true

        is StageCompletionMode.AddedAmount -> targetGrams.isFinite() && targetGrams > 0.0
        is StageCompletionMode.BeverageYield -> targetGrams.isFinite() && targetGrams > 0.0
        is StageCompletionMode.Countdown -> durationMillis > 0L
        is StageCompletionMode.CumulativeAmount -> targetGrams.isFinite() && targetGrams > 0.0
        is StageCompletionMode.ElapsedRange -> {
            minimumMillis >= 0L && maximumMillis >= minimumMillis && maximumMillis > 0L
        }

        is StageCompletionMode.ExternalMarker,
        is StageCompletionMode.ObservedEvent,
        -> true
    }

    private fun StageCompletionMode.isConsistentWith(
        targets: StageReferenceTargets,
    ): Boolean = when (this) {
        is StageCompletionMode.Countdown -> targets.timeTargetsFor(StageTimeReference.STAGE_DURATION)
            .let { candidates -> candidates.isEmpty() || candidates.any { it.contains(durationMillis) } }

        is StageCompletionMode.ElapsedRange -> targets.timeTargetsFor(StageTimeReference.STAGE_DURATION)
            .let { candidates ->
                candidates.isEmpty() || candidates.any { it.overlaps(minimumMillis, maximumMillis) }
            }

        is StageCompletionMode.AddedAmount -> targets.massTargetsFor(
            role = QuantityRole.BREW_WATER_INPUT,
            reference = StageMassReference.STAGE_ADDED,
        ).let { candidates -> candidates.isEmpty() || candidates.any { it.contains(targetGrams) } }

        is StageCompletionMode.CumulativeAmount -> targets.massTargetsFor(
            role = QuantityRole.BREW_WATER_INPUT,
            reference = StageMassReference.BREW_CUMULATIVE,
        ).let { candidates -> candidates.isEmpty() || candidates.any { it.contains(targetGrams) } }

        is StageCompletionMode.BeverageYield -> targets.massTargetsFor(
            role = QuantityRole.BEVERAGE_YIELD,
            reference = StageMassReference.RECIPE_TOTAL,
        ).let { candidates -> candidates.isEmpty() || candidates.any { it.contains(targetGrams) } }

        StageCompletionMode.Immediate,
        StageCompletionMode.Manual,
        is StageCompletionMode.ExternalMarker,
        is StageCompletionMode.ObservedEvent,
        -> true
    }

    private fun StageReferenceTargets.timeTargetsFor(reference: StageTimeReference): List<StageTimeTarget> =
        timeTargets.filter { target -> target.reference == reference }

    private fun StageReferenceTargets.massTargetsFor(
        role: QuantityRole,
        reference: StageMassReference,
    ): List<StageMassTarget> = massTargets.filter { target ->
        target.role == role && target.reference == reference
    }

    private fun StageTimeTarget.contains(value: Long): Boolean = value in minimumMillis..maximumMillis

    private fun StageTimeTarget.overlaps(minimum: Long, maximum: Long): Boolean =
        minimum <= maximumMillis && maximum >= minimumMillis

    private fun StageMassTarget.contains(value: Double): Boolean =
        value >= minimumGrams && value <= maximumGrams
}

/** Expands bounded branches into the sequence persisted with a brew session. */
object StagePlanCompiler {

    fun compile(
        plan: BrewStagePlan,
        selections: StagePlanSelections = StagePlanSelections(),
        context: StagePlanValidationContext = StagePlanValidationContext(),
    ): StagePlanCompileResult {
        val issues = StagePlanValidator.validate(plan, context).issues.toMutableList()
        validateSelections(plan.nodes, selections, "plan", issues)
        if (issues.isNotEmpty()) return StagePlanCompileResult.Invalid(issues)

        val occurrences = mutableMapOf<StageId, Int>()
        val compiledStages = mutableListOf<CompiledBrewStage>()

        fun append(nodes: List<StagePlanNode>) {
            nodes.forEach { node ->
                when (node) {
                    is StagePlanNode.Stage -> {
                        val occurrence = (occurrences[node.definition.id] ?: 0) + 1
                        occurrences[node.definition.id] = occurrence
                        compiledStages += CompiledBrewStage(
                            instanceId = StageInstanceId(node.definition.id, occurrence),
                            definition = node.definition,
                        )
                    }

                    is StagePlanNode.OptionalSection -> {
                        if (node.conditionId in selections.includedConditions) append(node.nodes)
                    }

                    is StagePlanNode.BoundedRepeat -> {
                        val count = selections.repeatCounts[node.repeatId] ?: node.minimumOccurrences
                        repeat(count) { append(node.nodes) }
                    }
                }
            }
        }

        append(plan.nodes)
        if (compiledStages.isEmpty()) {
            return StagePlanCompileResult.Invalid(
                listOf(StagePlanValidationIssue(StagePlanValidationCode.EMPTY_COMPILED_PLAN, "compiled")),
            )
        }
        return StagePlanCompileResult.Compiled(
            CompiledStagePlan(
                id = plan.id,
                version = plan.version,
                stages = compiledStages,
            ),
        )
    }

    private fun validateSelections(
        nodes: List<StagePlanNode>,
        selections: StagePlanSelections,
        path: String,
        issues: MutableList<StagePlanValidationIssue>,
    ) {
        nodes.forEachIndexed { index, node ->
            when (node) {
                is StagePlanNode.Stage -> Unit
                is StagePlanNode.OptionalSection -> {
                    validateSelections(node.nodes, selections, "$path/$index", issues)
                }

                is StagePlanNode.BoundedRepeat -> {
                    val count = selections.repeatCounts[node.repeatId] ?: node.minimumOccurrences
                    if (count !in node.minimumOccurrences..node.maximumOccurrences) {
                        issues += StagePlanValidationIssue(
                            StagePlanValidationCode.REPEAT_COUNT_OUT_OF_RANGE,
                            "$path/$index",
                        )
                    }
                    validateSelections(node.nodes, selections, "$path/$index", issues)
                }
            }
        }
    }
}
