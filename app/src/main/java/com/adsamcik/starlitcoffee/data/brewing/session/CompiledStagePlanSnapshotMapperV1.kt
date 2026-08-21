package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAdvanceConstraint
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertPolicy
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageEquipmentRequirement
import com.adsamcik.starlitcoffee.domain.brewing.session.StageEquipmentStateId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMarkerId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage

/** Bidirectional mapping for the immutable V1 compiled-plan document. */
internal object CompiledStagePlanSnapshotMapperV1 {

    fun toSnapshot(value: CompiledStagePlan): CompiledStagePlanSnapshotV1 =
        CompiledStagePlanSnapshotV1(
            stagePlanId = value.id.value,
            stagePlanVersion = value.version,
            stages = value.stages.map(::stageToSnapshot),
        )

    fun toDomain(value: CompiledStagePlanSnapshotV1): CompiledStagePlan {
        require(value.schemaVersion == CompiledStagePlanSnapshotV1.SCHEMA_VERSION) {
            "Unsupported compiled-plan snapshot schema: ${value.schemaVersion}"
        }
        require(value.stagePlanVersion > 0) { "Stage-plan version must be positive" }
        require(value.stages.isNotEmpty()) { "A compiled plan needs at least one stage" }
        val compiledStages = value.stages.map(::stageToDomain)
        require(compiledStages.map(CompiledBrewStage::instanceId).distinct().size == compiledStages.size) {
            "Compiled stage instances must be unique"
        }
        return CompiledStagePlan(
            id = StagePlanId(value.stagePlanId),
            version = value.stagePlanVersion,
            stages = compiledStages,
        )
    }

    private fun stageToSnapshot(value: CompiledBrewStage): CompiledBrewStageSnapshotV1 =
        CompiledBrewStageSnapshotV1(
            instance = StageInstanceSnapshotMapperV1.toSnapshot(value.instanceId),
            definition = definitionToSnapshot(value.definition),
        )

    private fun definitionToSnapshot(value: BrewStageDefinition): BrewStageDefinitionSnapshotV1 =
        BrewStageDefinitionSnapshotV1(
            stageId = value.id.value,
            action = value.action.name,
            contentId = value.contentId.value,
            instructionAssetId = value.instructionAssetId?.value,
            requiresIllustration = value.requiresIllustration,
            safetyMessages = value.safetyMessages.map { message ->
                StageSafetyMessageSnapshotV1(message.code, message.severity.name)
            },
            requiredEquipmentStateId = value.equipmentRequirement?.requiredState?.value,
            completion = completionToSnapshot(value.completionMode),
            referenceTargets = StageReferenceTargetsSnapshotMapper.toSnapshot(value.referenceTargets),
            advanceConstraint = StageAdvanceConstraintSnapshotV1(
                notBeforeStageElapsedMillis = value.advanceConstraint.notBeforeStageElapsedMillis,
                notBeforeBrewElapsedMillis = value.advanceConstraint.notBeforeBrewElapsedMillis,
            ),
            alertPolicy = StageAlertPolicySnapshotV1(
                alertOnStart = value.alertPolicy.alertOnStart,
                alertOnCompletion = value.alertPolicy.alertOnCompletion,
                scheduleDeadline = value.alertPolicy.scheduleDeadline,
            ),
            isSkippable = value.isSkippable,
        )

    private fun completionToSnapshot(value: StageCompletionMode): StageCompletionModeSnapshotV1 =
        when (value) {
            StageCompletionMode.Manual ->
                StageCompletionModeSnapshotV1(StageCompletionModeSnapshotV1.MANUAL)
            StageCompletionMode.Immediate ->
                StageCompletionModeSnapshotV1(StageCompletionModeSnapshotV1.IMMEDIATE)
            is StageCompletionMode.Countdown -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.COUNTDOWN,
                durationMillis = value.durationMillis,
            )
            is StageCompletionMode.ElapsedRange -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.ELAPSED_RANGE,
                minimumMillis = value.minimumMillis,
                maximumMillis = value.maximumMillis,
            )
            is StageCompletionMode.CumulativeAmount -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.CUMULATIVE_AMOUNT,
                targetGrams = value.targetGrams,
            )
            is StageCompletionMode.AddedAmount -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.ADDED_AMOUNT,
                targetGrams = value.targetGrams,
            )
            is StageCompletionMode.BeverageYield -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.BEVERAGE_YIELD,
                targetGrams = value.targetGrams,
            )
            is StageCompletionMode.ObservedEvent -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.OBSERVED_EVENT,
                observationId = value.observationId.value,
            )
            is StageCompletionMode.ExternalMarker -> StageCompletionModeSnapshotV1(
                kind = StageCompletionModeSnapshotV1.EXTERNAL_MARKER,
                markerId = value.markerId.value,
            )
        }

    private fun stageToDomain(value: CompiledBrewStageSnapshotV1): CompiledBrewStage {
        val definition = definitionToDomain(value.definition)
        val instance = StageInstanceSnapshotMapperV1.toDomain(value.instance)
        require(instance.sourceStageId == definition.id) {
            "Compiled stage instance must reference its definition's stage ID"
        }
        return CompiledBrewStage(instance, definition)
    }

    private fun definitionToDomain(value: BrewStageDefinitionSnapshotV1): BrewStageDefinition =
        BrewStageDefinition(
            id = StageId(value.stageId),
            action = SessionSnapshotValueDecoder.enumValue(value.action, "stage action"),
            contentId = StageContentId(value.contentId),
            instructionAssetId = value.instructionAssetId?.let(::InstructionAssetId),
            requiresIllustration = value.requiresIllustration,
            safetyMessages = value.safetyMessages.map { message ->
                StageSafetyMessage(
                    message.code,
                    SessionSnapshotValueDecoder.enumValue(message.severity, "safety severity"),
                )
            },
            equipmentRequirement = value.requiredEquipmentStateId?.let { stateId ->
                StageEquipmentRequirement(StageEquipmentStateId(stateId))
            },
            completionMode = completionToDomain(value.completion),
            referenceTargets = StageReferenceTargetsSnapshotMapper.toDomain(value.referenceTargets),
            advanceConstraint = StageAdvanceConstraint(
                notBeforeStageElapsedMillis = value.advanceConstraint.notBeforeStageElapsedMillis,
                notBeforeBrewElapsedMillis = value.advanceConstraint.notBeforeBrewElapsedMillis,
            ),
            alertPolicy = StageAlertPolicy(
                alertOnStart = value.alertPolicy.alertOnStart,
                alertOnCompletion = value.alertPolicy.alertOnCompletion,
                scheduleDeadline = value.alertPolicy.scheduleDeadline,
            ),
            isSkippable = value.isSkippable,
        )

    private fun completionToDomain(value: StageCompletionModeSnapshotV1): StageCompletionMode =
        when (value.kind) {
            StageCompletionModeSnapshotV1.MANUAL -> StageCompletionMode.Manual
            StageCompletionModeSnapshotV1.IMMEDIATE -> StageCompletionMode.Immediate
            StageCompletionModeSnapshotV1.COUNTDOWN -> StageCompletionMode.Countdown(
                SessionSnapshotValueDecoder.requiredPositive(value.durationMillis, "Countdown duration"),
            )
            StageCompletionModeSnapshotV1.ELAPSED_RANGE -> {
                val minimum = SessionSnapshotValueDecoder.requiredNonNegative(
                    value.minimumMillis,
                    "Elapsed-range minimum",
                )
                val maximum = SessionSnapshotValueDecoder.requiredPositive(
                    value.maximumMillis,
                    "Elapsed-range maximum",
                )
                require(maximum >= minimum) { "Elapsed-range maximum must not precede minimum" }
                StageCompletionMode.ElapsedRange(minimum, maximum)
            }
            StageCompletionModeSnapshotV1.CUMULATIVE_AMOUNT -> StageCompletionMode.CumulativeAmount(
                SessionSnapshotValueDecoder.requiredPositive(value.targetGrams, "Cumulative amount target"),
            )
            StageCompletionModeSnapshotV1.ADDED_AMOUNT -> StageCompletionMode.AddedAmount(
                SessionSnapshotValueDecoder.requiredPositive(value.targetGrams, "Added amount target"),
            )
            StageCompletionModeSnapshotV1.BEVERAGE_YIELD -> StageCompletionMode.BeverageYield(
                SessionSnapshotValueDecoder.requiredPositive(value.targetGrams, "Beverage-yield target"),
            )
            StageCompletionModeSnapshotV1.OBSERVED_EVENT -> StageCompletionMode.ObservedEvent(
                StageObservationId(
                    SessionSnapshotValueDecoder.requireNotBlank(value.observationId, "Observation ID"),
                ),
            )
            StageCompletionModeSnapshotV1.EXTERNAL_MARKER -> StageCompletionMode.ExternalMarker(
                StageMarkerId(SessionSnapshotValueDecoder.requireNotBlank(value.markerId, "Marker ID")),
            )
            else -> throw IllegalArgumentException(
                "Unknown stage completion discriminator: ${value.kind}",
            )
        }
}

/** Shared V1 stage-instance mapping used by plan stages and runtime effects. */
internal object StageInstanceSnapshotMapperV1 {
    fun toSnapshot(value: StageInstanceId): StageInstanceSnapshotV1 = StageInstanceSnapshotV1(
        sourceStageId = value.sourceStageId.value,
        occurrence = value.occurrence,
    )

    fun toDomain(value: StageInstanceSnapshotV1): StageInstanceId = StageInstanceId(
        sourceStageId = StageId(value.sourceStageId),
        occurrence = value.occurrence,
    )
}
