package com.adsamcik.starlitcoffee.data.brewing.snapshot

import com.adsamcik.starlitcoffee.data.brewing.LegacyBrewingAdapter
import com.adsamcik.starlitcoffee.data.brewing.LegacyBrewingReference
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity

/** Top-level durable IDs remain raw until the catalogue resolves them for presentation or repair. */
data class StoredBrewingIdentity(
    val methodFamilyId: String?,
    val brewerProfileId: String?,
)

sealed interface StoredRecipePayload {
    data class Versioned(val snapshot: BrewRecipeSnapshotV1) : StoredRecipePayload

    data class Legacy(val reference: LegacyBrewingReference) : StoredRecipePayload

    data class Unsupported(val schemaVersion: Int, val rawJson: String) : StoredRecipePayload

    data class Invalid(val rawJson: String, val reason: String) : StoredRecipePayload
}

sealed interface StoredBrewRecordPayload {
    data class Versioned(val snapshot: BrewRecordSnapshotV1) : StoredBrewRecordPayload

    data class Legacy(val reference: LegacyBrewingReference) : StoredBrewRecordPayload

    data class Unsupported(val schemaVersion: Int, val rawJson: String) : StoredBrewRecordPayload

    data class Invalid(val rawJson: String, val reason: String) : StoredBrewRecordPayload
}

data class PersistedRecipeRecord(
    val entity: SavedRecipeEntity,
    val identity: StoredBrewingIdentity,
    val payload: StoredRecipePayload,
)

data class PersistedBrewLogRecord(
    val entity: BrewLogEntity,
    val identity: StoredBrewingIdentity,
    val payload: StoredBrewRecordPayload,
)

/**
 * The only place that decides how legacy columns, stable IDs, and snapshot
 * payloads meet. Screens never decode a blob or default an unknown method.
 */
object BrewingPersistenceMapper {
    fun recipeRecord(entity: SavedRecipeEntity): PersistedRecipeRecord = PersistedRecipeRecord(
        entity = entity,
        identity = StoredBrewingIdentity(entity.methodFamilyId, entity.brewerProfileId),
        payload = entity.recipeSnapshotJson?.let(::recipePayload) ?: legacyRecipePayload(entity.method, entity.filterType),
    )

    fun brewLogRecord(entity: BrewLogEntity): PersistedBrewLogRecord = PersistedBrewLogRecord(
        entity = entity,
        identity = StoredBrewingIdentity(entity.methodFamilyId, entity.brewerProfileId),
        payload = entity.brewSnapshotJson?.let(::recordPayload) ?: legacyLogPayload(entity.method, entity.filterType),
    )

    fun withRecipeSnapshot(
        legacyFields: SavedRecipeEntity,
        snapshot: BrewRecipeSnapshotV1,
    ): SavedRecipeEntity = legacyFields.copy(
        methodFamilyId = snapshot.methodFamilyId,
        brewerProfileId = snapshot.brewerProfileId,
        snapshotVersion = snapshot.schemaVersion,
        recipeSnapshotJson = BrewingSnapshotCodec.encodeRecipe(snapshot),
    )

    fun withBrewRecordSnapshot(
        legacyFields: BrewLogEntity,
        snapshot: BrewRecordSnapshotV1,
        sourceSessionId: String? = snapshot.sourceSessionId,
    ): BrewLogEntity = legacyFields.copy(
        methodFamilyId = snapshot.recipe.methodFamilyId,
        brewerProfileId = snapshot.recipe.brewerProfileId,
        snapshotVersion = snapshot.schemaVersion,
        brewSnapshotJson = BrewingSnapshotCodec.encodeRecord(snapshot),
        sourceSessionId = sourceSessionId,
    )

    private fun recipePayload(rawJson: String): StoredRecipePayload = when (
        val decoded = BrewingSnapshotCodec.decodeRecipe(rawJson)
    ) {
        is SnapshotDecodeResult.Decoded -> StoredRecipePayload.Versioned(decoded.value)
        is SnapshotDecodeResult.Invalid -> StoredRecipePayload.Invalid(decoded.rawJson, decoded.reason)
        is SnapshotDecodeResult.UnsupportedVersion -> StoredRecipePayload.Unsupported(
            decoded.schemaVersion,
            decoded.rawJson,
        )
    }

    private fun recordPayload(rawJson: String): StoredBrewRecordPayload = when (
        val decoded = BrewingSnapshotCodec.decodeRecord(rawJson)
    ) {
        is SnapshotDecodeResult.Decoded -> StoredBrewRecordPayload.Versioned(decoded.value)
        is SnapshotDecodeResult.Invalid -> StoredBrewRecordPayload.Invalid(decoded.rawJson, decoded.reason)
        is SnapshotDecodeResult.UnsupportedVersion -> StoredBrewRecordPayload.Unsupported(
            decoded.schemaVersion,
            decoded.rawJson,
        )
    }

    private fun legacyRecipePayload(method: String, filter: String?): StoredRecipePayload.Legacy =
        StoredRecipePayload.Legacy(LegacyBrewingAdapter.fromLegacy(method, filter))

    private fun legacyLogPayload(method: String, filter: String?): StoredBrewRecordPayload.Legacy =
        StoredBrewRecordPayload.Legacy(LegacyBrewingAdapter.fromLegacy(method, filter))
}
