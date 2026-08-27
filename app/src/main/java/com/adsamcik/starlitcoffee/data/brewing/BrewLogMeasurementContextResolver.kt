package com.adsamcik.starlitcoffee.data.brewing

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterSelectionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StoredBrewRecordPayload
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewOutputSemantics
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.domain.BeverageOutputEstimator
import com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import java.util.Locale

/**
 * Resolves the immutable quantities and process identity that make a brew log
 * eligible for optional beverage-output measurements and calibration.
 *
 * Versioned rows deliberately use their snapshot rather than mutable legacy
 * columns. Exact/P1 recipes are excluded until their planner consumes the same
 * calibration profile; currently only legacy-calculator snapshots do so.
 */
object BrewLogMeasurementContextResolver {
    data class Context(
        val processKey: String,
        val plannedCoffeeG: Float,
        val plannedWaterInputG: Float,
        val frozenExpectedOutputG: Float?,
    )

    fun resolve(log: BrewLogEntity): Context? {
        return when (val payload = BrewingPersistenceMapper.brewLogRecord(log).payload) {
            is StoredBrewRecordPayload.Versioned -> resolveVersioned(log, payload)
            is StoredBrewRecordPayload.Legacy -> resolveLegacy(log, payload)

            is StoredBrewRecordPayload.Invalid,
            is StoredBrewRecordPayload.Unsupported,
            -> null
        }
    }

    private fun resolveVersioned(
        log: BrewLogEntity,
        payload: StoredBrewRecordPayload.Versioned,
    ): Context? {
        val recipe = payload.snapshot.recipe
        if (
            recipe.outputModel.kind !in CALIBRATABLE_OUTPUT_MODEL_KINDS ||
            recipe.technique.stagePlanVariantId != null
        ) {
            return null
        }
        val plannedCoffeeG = recipe.quantities.dryCoffeeDoseG.positiveFiniteFloatOrNull()
            ?: return null
        val plannedWaterInputG = recipe.quantities.brewWaterInputG
            ?.positiveFiniteFloatOrNull()
            ?: return null
        return Context(
            processKey = processKey(
                brewerProfileId = recipe.brewerProfileId,
                filterSignature = recipe.equipment.filterSelection.calibrationSignature(),
            ),
            plannedCoffeeG = plannedCoffeeG,
            plannedWaterInputG = plannedWaterInputG,
            frozenExpectedOutputG = log.expectedBeverageOutputG.positiveFiniteOrNull(),
        )
    }

    private fun resolveLegacy(
        log: BrewLogEntity,
        payload: StoredBrewRecordPayload.Legacy,
    ): Context? {
        val method = parseLegacyMethod(log.method) ?: return null
        if (BeverageOutputEstimator.modelFor(method) == null) return null
        val plannedCoffeeG = log.doseG.positiveFiniteOrNull() ?: return null
        val plannedWaterInputG = log.waterG.positiveFiniteOrNull() ?: return null
        val brewerProfileId = log.brewerProfileId ?: when (
            val resolution = payload.reference.brewerProfile
        ) {
            is CatalogResolution.Known -> resolution.value.value
            is CatalogResolution.Unknown -> log.method.lowercase(Locale.ROOT)
        }
        return Context(
            processKey = processKey(
                brewerProfileId = brewerProfileId,
                filterSignature = payload.reference.equipment.filterSelection.calibrationSignature(),
            ),
            plannedCoffeeG = plannedCoffeeG,
            plannedWaterInputG = plannedWaterInputG,
            frozenExpectedOutputG = log.expectedBeverageOutputG.positiveFiniteOrNull(),
        )
    }

    /** Resolves whether the persisted recipe's water-like quantity is already cup yield. */
    fun isDirectBeverageYield(log: BrewLogEntity): Boolean = when (
        val payload = BrewingPersistenceMapper.brewLogRecord(log).payload
    ) {
        is StoredBrewRecordPayload.Versioned ->
            payload.snapshot.recipe.outputModel.kind == OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD

        is StoredBrewRecordPayload.Legacy ->
            parseLegacyMethod(log.method)?.outputSemantics == BrewOutputSemantics.BEVERAGE_YIELD

        is StoredBrewRecordPayload.Invalid,
        is StoredBrewRecordPayload.Unsupported,
        -> false
    }

    /** Builds the same process identity for the calculator's current selection. */
    fun currentProcessKey(
        method: BrewMethod,
        brewerProfileId: String? = null,
        filterType: FilterType? = null,
    ): String? {
        if (BeverageOutputEstimator.modelFor(method) == null) return null
        val legacy = LegacyBrewingAdapter.fromLegacy(method, filterType)
        val resolvedProfileId = brewerProfileId ?: when (val resolution = legacy.brewerProfile) {
            is CatalogResolution.Known -> resolution.value.value
            is CatalogResolution.Unknown -> method.name.lowercase(Locale.ROOT)
        }
        return processKey(
            brewerProfileId = resolvedProfileId,
            filterSignature = legacy.equipment.filterSelection.calibrationSignature(),
        )
    }

    private fun processKey(
        brewerProfileId: String,
        filterSignature: String,
    ): String = "$brewerProfileId|$filterSignature"

    private fun FilterSelection.calibrationSignature(): String = when (this) {
        FilterSelection.Unspecified -> FILTER_UNSPECIFIED
        FilterSelection.IntentionallyUnfiltered -> FILTER_INTENTIONALLY_UNFILTERED
        is FilterSelection.Stack -> "stack:" + entries
            .sortedWith(compareBy({ it.position }, { it.filterProfileId.value }))
            .joinToString(",") { entry ->
                "${entry.filterProfileId.value}@${entry.position}:${entry.role.name.lowercase(Locale.ROOT)}"
            }
    }

    private fun FilterSelectionSnapshotV1.calibrationSignature(): String = when (mode) {
        "STACK" -> "stack:" + entries
            .sortedWith(compareBy({ it.position }, { it.filterProfileId }))
            .joinToString(",") { entry ->
                "${entry.filterProfileId}@${entry.position}:${entry.role.lowercase(Locale.ROOT)}"
            }
        "INTENTIONALLY_UNFILTERED" -> FILTER_INTENTIONALLY_UNFILTERED
        else -> FILTER_UNSPECIFIED
    }

    private fun Float?.positiveFiniteOrNull(): Float? = this?.takeIf { value ->
        value.isFinite() && value > 0f
    }

    private fun Double.positiveFiniteFloatOrNull(): Float? = toFloat().takeIf { value ->
        isFinite() && value.isFinite() && value > 0f
    }

    private fun parseLegacyMethod(rawMethod: String): BrewMethod? = BrewMethod.entries.firstOrNull { method ->
        method.name.equals(rawMethod, ignoreCase = true) ||
            method.displayName.equals(rawMethod, ignoreCase = true)
    }

    private const val OUTPUT_BREW_WATER_MINUS_RETENTION = "BREW_WATER_MINUS_RETENTION"
    private const val OUTPUT_COLLECTED_CONCENTRATE = "COLLECTED_CONCENTRATE"
    private const val OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD = "DIRECT_TARGET_BEVERAGE_YIELD"
    private const val FILTER_UNSPECIFIED = "unspecified"
    private const val FILTER_INTENTIONALLY_UNFILTERED = "intentionally_unfiltered"
    private val CALIBRATABLE_OUTPUT_MODEL_KINDS = setOf(
        OUTPUT_BREW_WATER_MINUS_RETENTION,
        OUTPUT_COLLECTED_CONCENTRATE,
    )
}
