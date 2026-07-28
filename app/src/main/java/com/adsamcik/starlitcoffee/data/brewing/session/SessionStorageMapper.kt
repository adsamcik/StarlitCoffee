package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState

enum class SessionStorageDocument {
    COMPILED_PLAN,
    RUNTIME,
    EXECUTION_CONTEXT,
}

data class RestoredSessionStorage(
    val state: SessionRuntimeState,
    val executionContext: SessionExecutionContextSnapshotV1,
)

/**
 * Restoration never substitutes defaults for unavailable data. A caller can
 * surface the document and raw payload for repair while keeping the durable
 * record intact.
 */
sealed interface SessionStorageRestoreResult {
    data class Restored(val value: RestoredSessionStorage) : SessionStorageRestoreResult

    data class UnsupportedDocument(
        val document: SessionStorageDocument,
        val schemaVersion: Int,
        val rawJson: String,
    ) : SessionStorageRestoreResult

    data class InvalidDocument(
        val document: SessionStorageDocument,
        val reason: String,
        val rawJson: String? = null,
    ) : SessionStorageRestoreResult
}

private sealed interface SessionStorageDocumentResult<out T> {
    data class Value<T>(val value: T) : SessionStorageDocumentResult<T>
    data class Failure(val result: SessionStorageRestoreResult) : SessionStorageDocumentResult<Nothing>
}

/**
 * Stable facade for converting the Android-free session engine to explicit
 * storage documents. Version-specific mappers own discriminator and field
 * validation while this type preserves the public failure boundary.
 */
object SessionStorageMapper {

    fun snapshot(
        state: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
    ): SessionStorageSnapshots = SessionStorageSnapshots(
        compiledPlan = CompiledStagePlanSnapshotMapperV1.toSnapshot(state.stagePlan),
        runtime = SessionRuntimeSnapshotMapperV1.toSnapshot(state),
        executionContext = executionContext,
    )

    fun encode(
        state: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
    ): EncodedSessionStorageDocuments = snapshot(state, executionContext).let { snapshots ->
        EncodedSessionStorageDocuments(
            compiledPlanSchemaVersion = snapshots.compiledPlan.schemaVersion,
            compiledPlanJson = SessionStorageSnapshotCodec.encodeCompiledPlan(snapshots.compiledPlan),
            runtimeSchemaVersion = snapshots.runtime.schemaVersion,
            runtimeJson = SessionStorageSnapshotCodec.encodeRuntime(snapshots.runtime),
            executionContextSchemaVersion = snapshots.executionContext.schemaVersion,
            executionContextJson = SessionStorageSnapshotCodec.encodeExecutionContext(
                snapshots.executionContext,
            ),
        )
    }

    fun restore(snapshots: SessionStorageSnapshots): SessionStorageRestoreResult {
        val plan = when (val result = mapDocument(SessionStorageDocument.COMPILED_PLAN) {
            CompiledStagePlanSnapshotMapperV1.toDomain(snapshots.compiledPlan)
        }) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val runtime = when (val result = mapDocument(SessionStorageDocument.RUNTIME) {
            SessionRuntimeSnapshotMapperV1.toDomain(snapshots.runtime, plan)
        }) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val context = when (val result = mapDocument(SessionStorageDocument.EXECUTION_CONTEXT) {
            SessionExecutionContextSnapshotValidatorV1.validate(snapshots.executionContext)
        }) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        return SessionStorageRestoreResult.Restored(RestoredSessionStorage(runtime, context))
    }

    fun decodeAndRestore(
        compiledPlanJson: String,
        runtimeJson: String,
        executionContextJson: String,
    ): SessionStorageRestoreResult {
        val planSnapshot = when (
            val result = SessionStorageSnapshotCodec.decodeCompiledPlan(compiledPlanJson)
                .decodedOrFailure(SessionStorageDocument.COMPILED_PLAN)
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val runtimeSnapshot = when (
            val result = SessionStorageSnapshotCodec.decodeRuntime(runtimeJson)
                .decodedOrFailure(SessionStorageDocument.RUNTIME)
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val contextSnapshot = when (
            val result = SessionStorageSnapshotCodec.decodeExecutionContext(executionContextJson)
                .decodedOrFailure(SessionStorageDocument.EXECUTION_CONTEXT)
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }

        val plan = when (
            val result = mapDocument(SessionStorageDocument.COMPILED_PLAN, compiledPlanJson) {
            CompiledStagePlanSnapshotMapperV1.toDomain(planSnapshot)
        }
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val runtime = when (
            val result = mapDocument(SessionStorageDocument.RUNTIME, runtimeJson) {
            SessionRuntimeSnapshotMapperV1.toDomain(runtimeSnapshot, plan)
        }
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val context = when (
            val result = mapDocument(SessionStorageDocument.EXECUTION_CONTEXT, executionContextJson) {
            SessionExecutionContextSnapshotValidatorV1.validate(contextSnapshot)
        }
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        return SessionStorageRestoreResult.Restored(RestoredSessionStorage(runtime, context))
    }

    private fun <T> mapDocument(
        document: SessionStorageDocument,
        rawJson: String? = null,
        block: () -> T,
    ): SessionStorageDocumentResult<T> = try {
        SessionStorageDocumentResult.Value(block())
    } catch (error: IllegalArgumentException) {
        SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.InvalidDocument(
                document = document,
                reason = error.message ?: "Invalid session storage document",
                rawJson = rawJson,
            ),
        )
    } catch (error: IllegalStateException) {
        SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.InvalidDocument(
                document = document,
                reason = error.message ?: "Invalid session storage document",
                rawJson = rawJson,
            ),
        )
    }

    private fun <T> SnapshotDecodeResult<T>.decodedOrFailure(
        document: SessionStorageDocument,
    ): SessionStorageDocumentResult<T> = when (this) {
        is SnapshotDecodeResult.Decoded -> SessionStorageDocumentResult.Value(value)
        is SnapshotDecodeResult.Invalid -> SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.InvalidDocument(document, reason, rawJson),
        )

        is SnapshotDecodeResult.UnsupportedVersion -> SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.UnsupportedDocument(
                document = document,
                schemaVersion = schemaVersion,
                rawJson = rawJson,
            ),
        )
    }
}
