package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.PhaseMode
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.viewmodel.BrewPhase

/**
 * Pure builder for brew timer phases.
 * No Android dependencies, no mutable state — every call is deterministic.
 */
object PhasePlanBuilder {

    fun buildPhases(
        method: BrewMethod,
        bloomG: Float,
        pulseSizeG: Float,
        effectivePulseCount: Int,
        waterG: Float,
        timeTargetLowS: Int,
        isDecaf: Boolean = false,
    ): List<BrewPhase> {
        val phases = mutableListOf<BrewPhase>()
        var cumulative = 0f
        val isPulsar = method == BrewMethod.PULSAR
        val capacity = method.capacityMaxG?.toFloat()

        // Tracks water poured since last drain to know when to refill
        var waterSinceDrain = 0f

        if (method.hasBloom && bloomG > 0f) {
            cumulative += bloomG
            waterSinceDrain += bloomG
            phases.add(
                BrewPhase(
                    name = "Bloom",
                    phaseType = PhaseType.BLOOM,
                    mode = if (isPulsar) PhaseMode.EVENT_GATED else PhaseMode.TIMED,
                    waterG = bloomG,
                    cumulativeWaterG = cumulative,
                    durationSeconds = when {
                        isDecaf && isPulsar -> 35
                        isDecaf -> 30
                        isPulsar -> 50
                        else -> 45
                    },
                    instruction = if (isPulsar) {
                        val decafHint = if (isDecaf) " · Decaf: shorter steep" else ""
                        "Valve OPEN → pour to ${"%.0f".format(cumulative)}g → wait ~10s → CLOSE valve → gentle swirl$decafHint"
                    } else {
                        "Pour to ${"%.0f".format(cumulative)}g, let CO₂ escape"
                    },
                    valveState = if (isPulsar) "open → close" else "",
                ),
            )
        }

        if (method.hasPulses && effectivePulseCount > 0 && pulseSizeG > 0f) {
            val pourDuration = if (effectivePulseCount > 0) {
                val bloomTime = if (method.hasBloom) { if (isPulsar) 50 else 45 } else 0
                val totalPourTime = timeTargetLowS - bloomTime - 30
                (totalPourTime.coerceAtLeast(effectivePulseCount) / effectivePulseCount)
                    .coerceAtLeast(1)
            } else {
                30
            }

            var drainCount = 0
            for (i in 1..effectivePulseCount) {
                // Insert drain phase when next pulse would exceed capacity
                if (capacity != null && waterSinceDrain + pulseSizeG > capacity) {
                    drainCount++
                    phases.add(
                        BrewPhase(
                            name = "Drain & Refill" +
                                if (drainCount > 1) " $drainCount" else "",
                            phaseType = PhaseType.DRAIN_AND_REFILL,
                            mode = PhaseMode.EVENT_GATED,
                            waterG = 0f,
                            cumulativeWaterG = cumulative,
                            durationSeconds = 30,
                            instruction = if (isPulsar) {
                                "Let it drain until slurry drops · then continue pouring"
                            } else {
                                "Let it drain, then continue"
                            },
                            valveState = if (isPulsar) "open" else "",
                        ),
                    )
                    waterSinceDrain = 0f
                }

                cumulative += pulseSizeG
                waterSinceDrain += pulseSizeG
                val isFirst = i == 1
                val isLast = i == effectivePulseCount
                phases.add(
                    BrewPhase(
                        name = "Pour $i/$effectivePulseCount",
                        phaseType = PhaseType.POUR,
                        mode = PhaseMode.TIMED,
                        waterG = pulseSizeG,
                        cumulativeWaterG = cumulative,
                        durationSeconds = pourDuration,
                        instruction = if (isPulsar) {
                            buildString {
                                if (isFirst) append("OPEN valve → ")
                                append("Pour to ${"%.0f".format(cumulative)}g")
                                append(" · keep slurry ~1cm above bed")
                                if (isLast) append(" → gentle swirl")
                            }
                        } else {
                            "Pour to ${"%.0f".format(cumulative)}g (+${"%.0f".format(pulseSizeG)}g)"
                        },
                        valveState = if (isPulsar) "open" else "",
                    ),
                )
            }
        } else if (!method.hasPulses && waterG > 0f) {
            val pourWater = waterG - cumulative
            if (pourWater > 0f) {
                cumulative += pourWater
                val pourDuration = (timeTargetLowS - 30).coerceAtLeast(1)
                phases.add(
                    BrewPhase(
                        name = "Pour",
                        phaseType = PhaseType.POUR,
                        mode = PhaseMode.TIMED,
                        waterG = pourWater,
                        cumulativeWaterG = cumulative,
                        durationSeconds = pourDuration,
                        instruction = "Pour to ${"%.0f".format(cumulative)}g total",
                    ),
                )
            }
        }

        if (phases.isNotEmpty()) {
            phases.add(
                BrewPhase(
                    name = "Drawdown",
                    phaseType = PhaseType.DRAWDOWN,
                    mode = PhaseMode.EVENT_GATED,
                    waterG = 0f,
                    cumulativeWaterG = cumulative,
                    durationSeconds = 30,
                    instruction = if (isPulsar) {
                        "Valve open · let it drain completely"
                    } else {
                        "Let it drain"
                    },
                    valveState = if (isPulsar) "open" else "",
                ),
            )
        }

        return phases
    }
}
