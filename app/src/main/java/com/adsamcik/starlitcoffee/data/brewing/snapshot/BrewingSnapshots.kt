package com.adsamcik.starlitcoffee.data.brewing.snapshot

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Durable recipe intent. IDs and enum-like values deliberately stay raw strings
 * so a newer app's values survive an older app without lossy enum parsing.
 */
@Serializable
data class BrewRecipeSnapshotV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val methodFamilyId: String,
    val brewerProfileId: String,
    /** Stable built-in/library recipe identity; absent for legacy or custom recipes. */
    val builtInRecipeId: String? = null,
    val equipment: EquipmentConfigurationSnapshotV1,
    val quantities: BrewQuantitiesSnapshotV1,
    val ratioDefinition: RatioDefinitionSnapshotV1,
    val ratioValue: Double? = null,
    val temperatureC: Int? = null,
    val grinderId: String? = null,
    val grindSetting: String? = null,
    val technique: RecipeTechniqueSnapshotV1 = RecipeTechniqueSnapshotV1(),
    val servingAdditions: List<ServingAdditionSnapshotV1> = emptyList(),
    val isDecaf: Boolean = false,
    val notes: String? = null,
    val outputModel: OutputModelSnapshotV1,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class EquipmentConfigurationSnapshotV1(
    val brewerProfileId: String,
    val capacityOverrideG: Double? = null,
    val filterSelection: FilterSelectionSnapshotV1 = FilterSelectionSnapshotV1(),
    val accessoryIds: List<String> = emptyList(),
    val basketId: String? = null,
    val heatSource: String = "NONE",
)

@Serializable
data class FilterSelectionSnapshotV1(
    /** UNSPECIFIED, INTENTIONALLY_UNFILTERED, or STACK. */
    val mode: String = "UNSPECIFIED",
    val entries: List<FilterStackEntrySnapshotV1> = emptyList(),
)

@Serializable
data class FilterStackEntrySnapshotV1(
    val filterProfileId: String,
    val position: Int,
    val role: String = "PRIMARY",
)

@Serializable
data class BrewQuantitiesSnapshotV1(
    val dryCoffeeDoseG: Double,
    val brewWaterInputG: Double? = null,
    val reservoirInputG: Double? = null,
    val targetBeverageYieldG: Double? = null,
    val targetConcentrateYieldG: Double? = null,
    val finalServedBeverageG: Double? = null,
    val iceG: Double = 0.0,
    val bypassWaterG: Double = 0.0,
    val dilutionWaterG: Double = 0.0,
    val measuredOutputG: Double? = null,
)

@Serializable
data class RatioDefinitionSnapshotV1(
    val numerator: String,
    val denominator: String,
)

@Serializable
data class RecipeTechniqueSnapshotV1(
    val bloomWaterG: Double? = null,
    val bloomDurationSeconds: Int? = null,
    val pourPattern: String = "NONE",
    val pulseCount: Int? = null,
    val agitation: String = "NONE",
    val steepDurationSeconds: Int? = null,
    val orientation: String = "STANDARD",
    val valveSequence: List<String> = emptyList(),
    val preInfusionSeconds: Int? = null,
    val heatStrategy: String = "NONE",
    val stagePlanVariantId: String? = null,
)

@Serializable
data class ServingAdditionSnapshotV1(
    val id: String,
    val massG: Double? = null,
)

@Serializable
data class OutputModelSnapshotV1(
    /** See the domain OutputModel names; unknown kinds remain readable. */
    val kind: String,
    val retainedWaterGPerCoffeeG: Double? = null,
    val internalRetentionG: Double? = null,
)

/** Immutable completed-brew record; never derive historical data from the live catalogue. */
@Serializable
data class BrewRecordSnapshotV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val recipe: BrewRecipeSnapshotV1,
    val stageActuals: List<StageActualSnapshotV1> = emptyList(),
    val completedAtWallClockMillis: Long? = null,
    val sourceSessionId: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class StageActualSnapshotV1(
    val stageInstanceId: String,
    val elapsedActiveMillis: Long? = null,
    val addedAmountG: Double? = null,
    val cumulativeAmountG: Double? = null,
    val beverageYieldG: Double? = null,
    val observationIds: List<String> = emptyList(),
    val markerIds: List<String> = emptyList(),
    val completionKind: String? = null,
)

/**
 * A durable envelope for an in-progress session. The compiled-plan and runtime
 * documents get independent schema versions so their migration can evolve
 * without rewriting the recipe intent.
 */
@Serializable
data class BrewSessionSnapshotV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val recipe: BrewRecipeSnapshotV1,
    val compiledPlanSchemaVersion: Int,
    val compiledPlanJson: String,
    val runtimeSchemaVersion: Int,
    val runtimeJson: String,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

sealed interface SnapshotDecodeResult<out T> {
    data class Decoded<T>(val value: T) : SnapshotDecodeResult<T>

    data class UnsupportedVersion(val schemaVersion: Int, val rawJson: String) : SnapshotDecodeResult<Nothing>

    data class Invalid(val rawJson: String, val reason: String) : SnapshotDecodeResult<Nothing>
}

/**
 * Tolerant JSON boundary for persisted snapshots. It deliberately ignores
 * newer fields while treating an unknown top-level schema as inspectable,
 * unsupported data rather than replacing it with a default recipe.
 */
object BrewingSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encodeRecipe(snapshot: BrewRecipeSnapshotV1): String = json.encodeToString(snapshot)

    fun encodeRecord(snapshot: BrewRecordSnapshotV1): String = json.encodeToString(snapshot)

    fun encodeSession(snapshot: BrewSessionSnapshotV1): String = json.encodeToString(snapshot)

    fun decodeRecipe(rawJson: String): SnapshotDecodeResult<BrewRecipeSnapshotV1> =
        decode(rawJson, BrewRecipeSnapshotV1.SCHEMA_VERSION) { value -> value.schemaVersion }

    fun decodeRecord(rawJson: String): SnapshotDecodeResult<BrewRecordSnapshotV1> =
        decode(rawJson, BrewRecordSnapshotV1.SCHEMA_VERSION) { value -> value.schemaVersion }

    fun decodeSession(rawJson: String): SnapshotDecodeResult<BrewSessionSnapshotV1> =
        decode(rawJson, BrewSessionSnapshotV1.SCHEMA_VERSION) { value -> value.schemaVersion }

    private inline fun <reified T> decode(
        rawJson: String,
        expectedSchemaVersion: Int,
        schemaVersion: (T) -> Int,
    ): SnapshotDecodeResult<T> {
        return try {
            val element = json.parseToJsonElement(rawJson)
            val encodedVersion = element.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
            if (encodedVersion == null) {
                SnapshotDecodeResult.Invalid(rawJson, "Missing schemaVersion")
            } else if (encodedVersion != expectedSchemaVersion) {
                SnapshotDecodeResult.UnsupportedVersion(encodedVersion, rawJson)
            } else {
                val decoded = json.decodeFromJsonElement<T>(element)
                if (schemaVersion(decoded) == expectedSchemaVersion) {
                    SnapshotDecodeResult.Decoded(decoded)
                } else {
                    SnapshotDecodeResult.Invalid(rawJson, "Schema version did not round-trip")
                }
            }
        } catch (error: SerializationException) {
            SnapshotDecodeResult.Invalid(rawJson, error.message ?: "Invalid snapshot JSON")
        } catch (error: IllegalArgumentException) {
            SnapshotDecodeResult.Invalid(rawJson, error.message ?: "Invalid snapshot JSON")
        } catch (error: IllegalStateException) {
            SnapshotDecodeResult.Invalid(rawJson, error.message ?: "Invalid snapshot JSON")
        }
    }
}
