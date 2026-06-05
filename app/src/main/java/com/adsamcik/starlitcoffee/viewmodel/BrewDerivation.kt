package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.DecafProcess
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.domain.BrewCalculator

/**
 * Derived brew output computed purely from a [BrewUiState] snapshot plus the
 * selected bag and grinder data. Mirrors the fields [BrewViewModel] copies back
 * into its UI state after recalculation.
 */
internal data class BrewDerivedFields(
    val coffeeG: Float,
    val waterG: Float,
    val effectiveRatio: Float,
    val bloomG: Float,
    val remainingWaterG: Float,
    val pulseSizeG: Float,
    val effectivePulseCount: Int,
    val timeTargetLowS: Int,
    val timeTargetHighS: Int,
    val grindResult: GrindResult,
    val refillCount: Int,
    val ratioWarning: String?,
    val bloomWarning: String?,
    val effectiveBloomDurationSeconds: Int,
    val isDecafBrew: Boolean,
    val decafMismatchWithBag: Boolean,
    val retainedWaterG: Float,
    val predictedCupVolumeG: Float,
)

/**
 * Pure brew-derivation engine extracted from [BrewViewModel].
 *
 * Given a UI-state snapshot, the selected coffee bag, and grinder data it
 * returns the fully derived brew output. Deterministic given [nowMs] (used only
 * for the roast-freshness bloom adjustment). It has no Android dependencies and
 * is unit-testable in isolation; [BrewViewModel] keeps ownership of state and
 * simply applies the returned fields.
 */
internal object BrewDerivation {

    fun derive(
        state: BrewUiState,
        selectedBag: CoffeeBagEntity?,
        grinderData: GrinderDataProvider,
        nowMs: Long,
    ): BrewDerivedFields {
        val amount = state.amount.toFloatOrNull() ?: 0f
        val method = state.method

        val decafState = resolveDecafState(state, selectedBag)
        val effectiveRatio = resolveEffectiveRatio(state, method)
        val effectiveBloomMultiplier = resolveEffectiveBloomMultiplier(state, method)
        val effectivePulseCount = resolveEffectivePulseCount(state, method)

        val calculation = BrewCalculator.calculate(
            method = method,
            inputMode = state.inputMode,
            amount = amount,
            effectiveRatio = effectiveRatio,
            bloomMultiplier = effectiveBloomMultiplier,
            pulseCount = effectivePulseCount,
            isDecaf = decafState.effectiveIsDecaf,
        )
        val effectiveBloomDurationSeconds = resolveEffectiveBloomDurationSeconds(method, selectedBag, nowMs)
        val grindResult = resolveGrindResult(
            grinderData = grinderData,
            grinderId = state.selectedGrinderId,
            method = method,
            filterType = state.filterType,
            calibrationStyle = state.calibrationStyle,
            isDecaf = decafState.effectiveIsDecaf,
            roastLevel = selectedBag?.roastLevel,
            decafProcess = selectedBag?.decafProcess,
        )

        return BrewDerivedFields(
            coffeeG = calculation.coffeeG,
            waterG = calculation.waterG,
            effectiveRatio = effectiveRatio,
            bloomG = calculation.bloomG,
            remainingWaterG = calculation.remainingWaterG,
            pulseSizeG = calculation.pulseSizeG,
            effectivePulseCount = calculation.effectivePulseCount,
            timeTargetLowS = calculation.timeTargetLowS,
            timeTargetHighS = calculation.timeTargetHighS,
            grindResult = grindResult,
            refillCount = calculation.refillCount,
            ratioWarning = calculation.ratioWarning,
            bloomWarning = calculation.bloomWarning,
            effectiveBloomDurationSeconds = effectiveBloomDurationSeconds,
            isDecafBrew = decafState.effectiveIsDecaf,
            decafMismatchWithBag = decafState.decafMismatchWithBag,
            retainedWaterG = calculation.retainedWaterG,
            predictedCupVolumeG = calculation.predictedCupVolumeG,
        )
    }

    private data class DecafState(val effectiveIsDecaf: Boolean, val decafMismatchWithBag: Boolean)

    /**
     * Single source of truth for the brew-time decaf flag: manual override
     * wins; else follow the selected bag; else default to non-decaf. A
     * mismatch is only reported when the user explicitly overrode AND a bag
     * is selected AND the two disagree — picking no bag never produces a
     * mismatch.
     */
    private fun resolveDecafState(state: BrewUiState, selectedBag: CoffeeBagEntity?): DecafState {
        val effectiveIsDecaf = state.manualDecafOverride
            ?: selectedBag?.isDecaf
            ?: false
        val decafMismatchWithBag = state.manualDecafOverride != null &&
            selectedBag != null &&
            selectedBag.isDecaf != state.manualDecafOverride
        return DecafState(effectiveIsDecaf, decafMismatchWithBag)
    }

    fun resolveEffectiveRatio(state: BrewUiState, method: BrewMethod): Float {
        val selectedPreset = state.ratioPresets.getOrNull(state.selectedPresetIndex)
        val presetRatio = selectedPreset?.ratio ?: method.defaultRatio
        return if (state.customRatio.isNotEmpty()) {
            state.customRatio.toFloatOrNull() ?: presetRatio
        } else {
            presetRatio
        }
    }

    private fun resolveEffectiveBloomMultiplier(state: BrewUiState, method: BrewMethod): Float =
        if (state.bloomMultiplier.isNotEmpty()) {
            state.bloomMultiplier.toFloatOrNull() ?: method.bloomMultiplier
        } else {
            method.bloomMultiplier
        }

    private fun resolveEffectivePulseCount(state: BrewUiState, method: BrewMethod): Int =
        if (state.pulseCount.isNotEmpty()) {
            state.pulseCount.toIntOrNull() ?: method.defaultPulses
        } else {
            method.defaultPulses
        }

    /**
     * Bloom duration adjusted by roast freshness when a bag is selected.
     * Very fresh beans (<= 7 days off-roast) need a longer bloom because
     * they degas more; older beans (> 21 days) bloom shorter. Methods
     * without a bloom step always return their static base duration.
     */
    private fun resolveEffectiveBloomDurationSeconds(
        method: BrewMethod,
        selectedBag: CoffeeBagEntity?,
        nowMs: Long,
    ): Int {
        val baseDuration = method.bloomDurationSeconds
        val roastDateMillis = selectedBag?.roastDate
        if (roastDateMillis == null || !method.hasBloom) {
            return baseDuration
        }
        val daysOff = ((nowMs - roastDateMillis) / MILLIS_PER_DAY)
            .toInt().coerceAtLeast(0)
        return when {
            daysOff <= BLOOM_FRESH_DAYS -> (baseDuration + BLOOM_FRESH_BONUS_SECONDS).coerceAtMost(BLOOM_MAX_SECONDS)
            daysOff <= BLOOM_NORMAL_DAYS -> baseDuration
            else -> (baseDuration - BLOOM_OLD_PENALTY_SECONDS).coerceAtLeast(BLOOM_MIN_SECONDS)
        }
    }

    private fun resolveGrindResult(
        grinderData: GrinderDataProvider,
        grinderId: String?,
        method: BrewMethod,
        filterType: FilterType?,
        calibrationStyle: CalibrationStyle?,
        isDecaf: Boolean = false,
        roastLevel: String? = null,
        decafProcess: String? = null,
    ): GrindResult {
        if (grinderId == null) {
            return GrindResult.Generic(method.defaultGrindDescriptor)
        }

        val grinder = grinderData.grinders.find { it.id == grinderId }
            ?: return GrindResult.Generic(method.defaultGrindDescriptor)

        // Try exact filterType match first, then fall back to filter-agnostic recommendation
        var recommendation = grinderData.recommendations.find { rec ->
            rec.grinderId == grinder.id &&
                rec.methodId == method.name &&
                rec.filterType == filterType
        } ?: grinderData.recommendations.find { rec ->
            rec.grinderId == grinder.id &&
                rec.methodId == method.name &&
                rec.filterType == null
        } ?: return GrindResult.Generic(method.defaultGrindDescriptor)

        // Decaf offset: start COARSER, not finer. Decaf beans shatter into more
        // fines at the same grinder gap, which reduces bed permeability and slows
        // flow — so going finer often makes things worse, especially for
        // percolation and espresso. Magnitude depends on brew family (immersion is
        // less fines-sensitive) plus a roast brittleness modifier.
        // Refs: Coffee ad Astra (kettle-flow / V60 brewing), Scientific Reports
        // 2024 on espresso fines & permeability, Sweet Maria's on decaf
        // structural changes, Al-Shemmeri grinding study.
        if (isDecaf) {
            val decafSteps = decafCoarserStepsFor(method, roastLevel, decafProcess)
            val processNote = decafProcessNote(decafProcess)
            if (decafSteps > 0) {
                val offset = recommendation.adjustmentStepSize * decafSteps
                val stepLabel = if (decafSteps == 1) "1 step coarser" else "$decafSteps steps coarser"
                recommendation = recommendation.copy(
                    suggestedStart = (recommendation.suggestedStart + offset)
                        .coerceAtMost(recommendation.rangeEnd),
                    adjustmentNote = recommendation.adjustmentNote +
                        " · Decaf: $stepLabel (more fines → coarsen for permeability)" +
                        processNote,
                )
            } else {
                recommendation = recommendation.copy(
                    adjustmentNote = recommendation.adjustmentNote +
                        " · Decaf: same start (immersion or gentle process — fines impact small); dial by taste" +
                        processNote,
                )
            }
        }

        if (calibrationStyle == null) {
            return GrindResult.Specific(recommendation, grinder)
        }

        val multiplier = calibrationStyle.rangeWidthMultiplier
        val midpoint = (recommendation.rangeStart + recommendation.rangeEnd) / 2f
        val halfWidth = (recommendation.rangeEnd - recommendation.rangeStart) / 2f
        val adjustedStart = midpoint - halfWidth * multiplier
        val adjustedEnd = midpoint + halfWidth * multiplier

        return GrindResult.Specific(
            recommendation.copy(
                rangeStart = adjustedStart,
                rangeEnd = adjustedEnd,
            ),
            grinder,
        )
    }

    /**
     * How many steps **coarser** to start when brewing decaf, vs the caffeinated
     * baseline. Returns 0 (same start) for fines-insensitive immersion methods or
     * gentle decaf processes.
     *
     * Evidence-based default (2026 research pass):
     *   - Decaf grinds to a smaller median particle size + ~4 % more fines at the
     *     same gap (Al-Shemmeri grinding study).
     *   - Fines reduce bed permeability and slow flow more than they boost
     *     extraction (Sci. Rep. 2024 on espresso, Coffee ad Astra on V60).
     *   - Therefore "auto-finer" is wrong-direction. Default is **same to 1 step
     *     coarser**.
     *
     * Rule:
     *   - Percolation (Pulsar, V60, Moka) + Espresso: +1 step coarser baseline.
     *   - Immersion (French press, AeroPress, cold brew): no change — fines
     *     barely affect flow when there's no pressurised bed.
     *   - Roast modifier: dark / espresso / medium-dark roasts add +1 (more
     *     brittle, more fines).
     *   - Decaf-process modifier: gentle processes (Swiss Water / Mountain Water,
     *     supercritical CO₂) reduce the offset by 1 (clamped at 0). Solvent and
     *     unknown processes get no relief. ACS C&EN 2024 + Swiss Water brew guide
     *     support softer biasing for water/CO₂ processes.
     */
    private fun decafCoarserStepsFor(
        method: BrewMethod,
        roastLevel: String?,
        decafProcess: String? = null,
    ): Int {
        val baseSteps = when (method) {
            BrewMethod.PULSAR,
            BrewMethod.V60,
            BrewMethod.MOKA_POT,
            BrewMethod.ESPRESSO -> 1
            BrewMethod.FRENCH_PRESS,
            BrewMethod.AEROPRESS,
            BrewMethod.COLD_BREW -> 0
        }
        val known = roastLevel?.let {
            runCatching { CoffeeRoastLevel.Known.valueOf(it) }.getOrNull()
        }
        val roastModifier = when (known) {
            CoffeeRoastLevel.Known.MEDIUM_DARK,
            CoffeeRoastLevel.Known.DARK,
            CoffeeRoastLevel.Known.ESPRESSO -> 1
            else -> 0
        }
        val processRelief = when (DecafProcess.fromStorageKey(decafProcess)) {
            DecafProcess.SWISS_WATER,
            DecafProcess.MOUNTAIN_WATER,
            DecafProcess.CO2_SUPERCRITICAL -> 1
            else -> 0
        }
        return (baseSteps + roastModifier - processRelief).coerceAtLeast(0)
    }

    /** Short suffix appended to the grind adjustment note when a decaf process is known. */
    private fun decafProcessNote(decafProcess: String?): String {
        val process = DecafProcess.fromStorageKey(decafProcess) ?: return ""
        return when (process) {
            DecafProcess.SWISS_WATER,
            DecafProcess.MOUNTAIN_WATER -> " · ${process.shortLabel}: gentle, less coarsening needed"
            DecafProcess.CO2_SUPERCRITICAL -> " · CO₂: selective extraction, structure preserved"
            DecafProcess.EA_SUGARCANE,
            DecafProcess.EA_DIRECT,
            DecafProcess.MC_DIRECT -> " · ${process.shortLabel}: solvent process, expect more fines"
            DecafProcess.UNKNOWN -> ""
        }
    }

    // Bloom freshness adjustment thresholds (see resolveEffectiveBloomDurationSeconds)
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val BLOOM_FRESH_DAYS = 7
    private const val BLOOM_NORMAL_DAYS = 21
    private const val BLOOM_FRESH_BONUS_SECONDS = 10
    private const val BLOOM_OLD_PENALTY_SECONDS = 10
    private const val BLOOM_MIN_SECONDS = 30
    private const val BLOOM_MAX_SECONDS = 60
}
