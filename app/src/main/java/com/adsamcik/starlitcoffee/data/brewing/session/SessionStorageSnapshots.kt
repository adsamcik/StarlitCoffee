package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Explicit storage form of a compiled plan. It deliberately retains the
 * compiled sequence rather than relying on a later catalogue revision to
 * reconstruct a session which is already underway.
 */
@Serializable
data class CompiledStagePlanSnapshotV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val stagePlanId: String,
    val stagePlanVersion: Int,
    val stages: List<CompiledBrewStageSnapshotV1>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class CompiledBrewStageSnapshotV1(
    val instance: StageInstanceSnapshotV1,
    val definition: BrewStageDefinitionSnapshotV1,
)

@Serializable
data class StageInstanceSnapshotV1(
    val sourceStageId: String,
    val occurrence: Int,
)

@Serializable
data class BrewStageDefinitionSnapshotV1(
    val stageId: String,
    val action: String,
    val contentId: String,
    val instructionAssetId: String? = null,
    val requiresIllustration: Boolean = false,
    val safetyMessages: List<StageSafetyMessageSnapshotV1> = emptyList(),
    val requiredEquipmentStateId: String? = null,
    val completion: StageCompletionModeSnapshotV1,
    val referenceTargets: StageReferenceTargetsSnapshotV1 = StageReferenceTargetsSnapshotV1(),
    val alertPolicy: StageAlertPolicySnapshotV1 = StageAlertPolicySnapshotV1(),
    val isSkippable: Boolean = false,
)

@Serializable
data class StageSafetyMessageSnapshotV1(
    val code: String,
    val severity: String,
)

@Serializable
data class StageAlertPolicySnapshotV1(
    val alertOnStart: Boolean = false,
    val alertOnCompletion: Boolean = true,
    val scheduleDeadline: Boolean = true,
)

/**
 * A manually discriminated completion mode avoids serialization's generated
 * sealed-class labels becoming a persistence contract. Unknown values fail
 * visibly during restoration instead of being interpreted as a manual stage.
 */
@Serializable
data class StageCompletionModeSnapshotV1(
    val kind: String,
    val durationMillis: Long? = null,
    val minimumMillis: Long? = null,
    val maximumMillis: Long? = null,
    val targetGrams: Double? = null,
    val observationId: String? = null,
    val markerId: String? = null,
) {
    companion object {
        const val MANUAL = "MANUAL"
        const val IMMEDIATE = "IMMEDIATE"
        const val COUNTDOWN = "COUNTDOWN"
        const val ELAPSED_RANGE = "ELAPSED_RANGE"
        const val CUMULATIVE_AMOUNT = "CUMULATIVE_AMOUNT"
        const val ADDED_AMOUNT = "ADDED_AMOUNT"
        const val BEVERAGE_YIELD = "BEVERAGE_YIELD"
        const val OBSERVED_EVENT = "OBSERVED_EVENT"
        const val EXTERNAL_MARKER = "EXTERNAL_MARKER"
    }
}

@Serializable
data class StageReferenceTargetsSnapshotV1(
    val timeTargets: List<StageTimeTargetSnapshotV1> = emptyList(),
    val massTargets: List<StageMassTargetSnapshotV1> = emptyList(),
    val temperatureTarget: StageTemperatureTargetSnapshotV1? = null,
)

@Serializable
data class StageTimeTargetSnapshotV1(
    val id: String? = null,
    val reference: String,
    val qualifier: String,
    val minimumMillis: Long,
    val maximumMillis: Long,
)

@Serializable
data class StageMassTargetSnapshotV1(
    val id: String? = null,
    val role: String? = null,
    val reference: String? = null,
    /** Legacy pre-role discriminator retained for tolerant V1 restoration. */
    val kind: String? = null,
    val qualifier: String,
    val minimumGrams: Double,
    val maximumGrams: Double,
)

@Serializable
data class StageTemperatureTargetSnapshotV1(
    val qualifier: String,
    val minimumC: Double,
    val maximumC: Double,
)

/** Runtime-only fields stored separately from the immutable compiled plan. */
@Serializable
data class SessionRuntimeSnapshotV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val sessionId: String,
    val status: String,
    val currentStageIndex: Int? = null,
    val stageProgress: List<StageRuntimeProgressSnapshotV1>,
    val totalActiveElapsedMillis: Long = 0L,
    val activeClockAnchor: ActiveClockAnchorSnapshotV1? = null,
    val startedAtWallClockMillis: Long? = null,
    val pausedAtWallClockMillis: Long? = null,
    val endedAtWallClockMillis: Long? = null,
    val updatedAtWallClockMillis: Long? = null,
    val revision: Long = 0L,
    val processedEventIds: List<String> = emptyList(),
    val pendingEffects: List<PendingSessionEffectSnapshotV1> = emptyList(),
    val acknowledgedEffectIds: List<String> = emptyList(),
    val lastClockReconciliation: ClockReconciliationSnapshotV1? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class StageRuntimeProgressSnapshotV1(
    val status: String,
    val elapsedActiveMillis: Long = 0L,
    val startedAtWallClockMillis: Long? = null,
    val completedAtWallClockMillis: Long? = null,
    val completionKind: String? = null,
    val actuals: StageActualsSnapshotV1 = StageActualsSnapshotV1(),
)

@Serializable
data class StageActualsSnapshotV1(
    val addedAmountGrams: Double? = null,
    val cumulativeAmountGrams: Double? = null,
    val beverageYieldGrams: Double? = null,
    val observationIds: List<String> = emptyList(),
    val markerIds: List<String> = emptyList(),
)

@Serializable
data class ActiveClockAnchorSnapshotV1(
    val monotonicMillis: Long? = null,
    val wallClockMillis: Long,
)

@Serializable
data class ClockReconciliationSnapshotV1(
    val kind: String,
    val observedDeltaMillis: Long,
    val appliedDeltaMillis: Long,
)

/**
 * A manually discriminated, persisted outbox. Its stable effect IDs survive
 * process death so notification, scheduling, and final-log adapters can retry
 * safely without inventing fresh side effects.
 */
@Serializable
data class PendingSessionEffectSnapshotV1(
    val kind: String,
    val effectId: String,
    val sessionId: String,
    val stageInstance: StageInstanceSnapshotV1? = null,
    val alertKind: String? = null,
    val scheduleToken: String? = null,
    val dueAtWallClockMillis: Long? = null,
) {
    companion object {
        const val STAGE_ALERT = "STAGE_ALERT"
        const val SCHEDULE_STAGE_DEADLINE = "SCHEDULE_STAGE_DEADLINE"
        const val CANCEL_STAGE_DEADLINE = "CANCEL_STAGE_DEADLINE"
        const val FINALIZE_BREW_LOG = "FINALIZE_BREW_LOG"
        const val CANCEL_SESSION_WORK = "CANCEL_SESSION_WORK"
    }
}

/**
 * The contextual data a completed session needs to write a log without asking
 * the user to recreate selections after interruption or process death.
 */
@Serializable
data class SessionExecutionContextSnapshotV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val coffeeBagId: Long? = null,
    val sourceRecipeId: Long? = null,
    val logPresentation: BrewLogPresentationContextSnapshotV1,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Stable, user-visible log context. It preserves presentation labels as
 * entered for this brew rather than asking a later catalogue revision or
 * locale change to reinterpret historical output.
 */
@Serializable
data class BrewLogPresentationContextSnapshotV1(
    val methodLabel: String,
    val doseG: Double,
    val waterG: Double,
    val ratio: Double,
    val grindLabel: String? = null,
    val filterLabel: String? = null,
    val isDecaf: Boolean = false,
    val notes: String? = null,
)

/** A convenient three-document boundary matching active-session storage columns. */
data class SessionStorageSnapshots(
    val compiledPlan: CompiledStagePlanSnapshotV1,
    val runtime: SessionRuntimeSnapshotV1,
    val executionContext: SessionExecutionContextSnapshotV1,
)

data class EncodedSessionStorageDocuments(
    val compiledPlanSchemaVersion: Int,
    val compiledPlanJson: String,
    val runtimeSchemaVersion: Int,
    val runtimeJson: String,
    val executionContextSchemaVersion: Int,
    val executionContextJson: String,
)

/**
 * JSON boundary for the separate session documents. It intentionally mirrors
 * [com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingSnapshotCodec]: a
 * document with a future root schema remains available to repair or inspect.
 */
object SessionStorageSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encodeCompiledPlan(snapshot: CompiledStagePlanSnapshotV1): String = json.encodeToString(snapshot)

    fun encodeRuntime(snapshot: SessionRuntimeSnapshotV1): String = json.encodeToString(snapshot)

    fun encodeExecutionContext(snapshot: SessionExecutionContextSnapshotV1): String = json.encodeToString(snapshot)

    fun decodeCompiledPlan(rawJson: String): SnapshotDecodeResult<CompiledStagePlanSnapshotV1> =
        decode(rawJson, CompiledStagePlanSnapshotV1.SCHEMA_VERSION) { value -> value.schemaVersion }

    fun decodeRuntime(rawJson: String): SnapshotDecodeResult<SessionRuntimeSnapshotV1> =
        decode(rawJson, SessionRuntimeSnapshotV1.SCHEMA_VERSION) { value -> value.schemaVersion }

    fun decodeExecutionContext(rawJson: String): SnapshotDecodeResult<SessionExecutionContextSnapshotV1> =
        decode(rawJson, SessionExecutionContextSnapshotV1.SCHEMA_VERSION) { value -> value.schemaVersion }

    private inline fun <reified T> decode(
        rawJson: String,
        expectedSchemaVersion: Int,
        schemaVersion: (T) -> Int,
    ): SnapshotDecodeResult<T> = try {
        val element = json.parseToJsonElement(rawJson)
        val encodedVersion = element.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
        when {
            encodedVersion == null -> SnapshotDecodeResult.Invalid(rawJson, "Missing schemaVersion")
            encodedVersion != expectedSchemaVersion -> {
                SnapshotDecodeResult.UnsupportedVersion(encodedVersion, rawJson)
            }

            else -> {
                val decoded = json.decodeFromJsonElement<T>(element)
                if (schemaVersion(decoded) == expectedSchemaVersion) {
                    SnapshotDecodeResult.Decoded(decoded)
                } else {
                    SnapshotDecodeResult.Invalid(rawJson, "Schema version did not round-trip")
                }
            }
        }
    } catch (error: SerializationException) {
        SnapshotDecodeResult.Invalid(rawJson, error.message ?: "Invalid session snapshot JSON")
    } catch (error: IllegalArgumentException) {
        SnapshotDecodeResult.Invalid(rawJson, error.message ?: "Invalid session snapshot JSON")
    } catch (error: IllegalStateException) {
        SnapshotDecodeResult.Invalid(rawJson, error.message ?: "Invalid session snapshot JSON")
    }
}
