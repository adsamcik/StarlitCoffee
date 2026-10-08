package com.adsamcik.starlitcoffee.data.brewing.guides

import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAdvanceConstraint
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTemperatureTarget
import kotlinx.serialization.json.jsonObject

fun ReviewedMethodGuide.contentId(step: ReviewedGuideStep): StageContentId =
    StageContentId("${id}_${step.id.replace('-', '_')}")

/** Source time cues never operate a valve, pump, burner or physical completion. */
fun ReviewedMethodGuide.stagePlan(): CompiledStagePlan {
    validate()
    val originIndex = steps.indexOfFirst { it.id == originStepId }
    val endIndex = steps.indexOfFirst { it.id == endStepId }
    return CompiledStagePlan(StagePlanId(id), 1, steps.mapIndexed { index, step ->
        val content = contentId(step)
        val duration = step.durationSeconds?.takeIf { it > 0.0 }?.let { (it * 1_000.0).toLong() }
        val physicalStage = index in originIndex..endIndex
        val nextStart = steps.getOrNull(index + 1)?.startSeconds?.takeIf { it > 0.0 }
        // Missing start_s means a distinct stage clock, e.g. Switch after filling
        // and V60 after reaching 45 g. It cannot be inferred as first water.
        val sourceStart = step.startSeconds
        val brewBoundary = if (physicalStage && methodId != "cold-brew") {
            listOfNotNull(nextStart, sourceStart?.let { start ->
                duration?.let { start + it / 1_000.0 }
            }).maxOrNull()?.let { (it * 1_000.0).toLong() }
        } else null
        val stageBoundary = duration?.takeIf { brewBoundary == null && methodId != "cold-brew" }
        val definition = BrewStageDefinition(StageId(content.value), step.action(), content,
            completionMode = StageCompletionMode.Manual,
            referenceTargets = targetsFor(step),
            advanceConstraint = StageAdvanceConstraint(notBeforeStageElapsedMillis = stageBoundary,
                notBeforeBrewElapsedMillis = brewBoundary))
        CompiledBrewStage(StageInstanceId(definition.id, index + 1), definition)
    })
}

fun ReviewedGuideStep.referenceTargets(): StageReferenceTargets = StageReferenceTargets(
    timeTargets = buildList {
        durationSeconds?.takeIf { it > 0.0 }?.let {
            add(StageTimeTarget(StageTimeReference.STAGE_DURATION, StageTargetId("duration"),
                if (timePrecision.contains("approximate")) StageTargetQualifier.APPROXIMATE
                else StageTargetQualifier.EXACT, (it * 1_000.0).toLong()))
        }
        startSeconds?.let {
            add(StageTimeTarget(StageTimeReference.BREW_ELAPSED_AT_START, StageTargetId("start"),
                StageTargetQualifier.EXACT, (it * 1_000.0).toLong()))
        }
    },
    massTargets = buildList {
        addedWaterG?.takeIf { it > 0.0 }?.let { add(mass("added", it, StageMassReference.STAGE_ADDED)) }
        cumulativeWaterG?.takeIf { it > 0.0 }?.let { add(mass("cumulative", it, StageMassReference.BREW_CUMULATIVE)) }
    },
)

fun ReviewedMethodGuide.targetsFor(step: ReviewedGuideStep): StageReferenceTargets {
    val reference = step.referenceTargets()
    val extraMass = buildList {
        doseG?.takeIf { step.action() == BrewStageAction.ADD_COFFEE }?.let {
            add(StageMassTarget(StageTargetId("coffee"), QuantityRole.DRY_COFFEE_DOSE,
                StageMassReference.RECIPE_TOTAL, StageTargetQualifier.EXACT, it))
        }
        yieldG?.takeIf { step.id in setOf("start_shot", "stop_and_taste") }?.let {
            add(StageMassTarget(StageTargetId("cup"), QuantityRole.BEVERAGE_YIELD,
                StageMassReference.RECIPE_TOTAL, StageTargetQualifier.STARTING_POINT, it))
        }
    }
    val temperature = recipe.getValue("water_temperature_c").jsonObject
    val min = temperature.number("min")
    val max = temperature.number("max")
    val waterAction = step.action() in setOf(BrewStageAction.POUR, BrewStageAction.BLOOM, BrewStageAction.HEAT)
    val thermalTarget = if (waterAction && min != null && max != null) {
        StageTemperatureTarget(if (min == max) StageTargetQualifier.STARTING_POINT else StageTargetQualifier.RANGE, min, max)
    } else null
    return reference.copy(massTargets = reference.massTargets + extraMass, temperatureTarget = thermalTarget)
}

private fun mass(id: String, grams: Double, reference: StageMassReference) = StageMassTarget(
    StageTargetId(id), QuantityRole.BREW_WATER_INPUT, reference, StageTargetQualifier.EXACT, grams)

fun ReviewedGuideStep.action(): BrewStageAction = ACTION_WORDS.firstOrNull { (_, words) ->
    words.any(id::contains)
}?.first ?: BrewStageAction.OBSERVE

private val ACTION_WORDS = listOf(
    BrewStageAction.CLEAN_UP to listOf("clean"),
    BrewStageAction.PREPARE to listOf("prepare", "setup", "assemble", "inspect"),
    BrewStageAction.BLOOM to listOf("bloom", "wetting"),
    BrewStageAction.AGITATE to listOf("stir", "swirl", "agitat", "mix"),
    BrewStageAction.PRESS to listOf("press", "plunge"),
    BrewStageAction.STEEP to listOf("steep", "rest"),
    BrewStageAction.HEAT to listOf("heat", "brew"),
    BrewStageAction.RINSE to listOf("rinse", "prime"),
    BrewStageAction.FILTER to listOf("filter", "strain"),
    BrewStageAction.RELEASE to listOf("release", "drain", "return"),
    BrewStageAction.POUR to listOf("pour", "fill", "water"),
    BrewStageAction.ADD_COFFEE to listOf("coffee", "dose", "load"),
    BrewStageAction.SERVE to listOf("serve", "decant", "dilut"),
)

fun ReviewedGuideStep.title(): String = id.replace('_', ' ').replace('-', ' ')
    .replaceFirstChar { it.uppercaseChar() }.let { title -> STEP_TITLES[id] ?: title }

private val STEP_TITLES = mapOf(
    "open_start_bloom" to "Start the bloom", "retained_bloom" to "Let it bloom",
    "dose_level_tare" to "Add coffee and zero the scale", "prime_paper" to "Rinse the paper",
    "open_and_pulse" to "Open and pour", "add_coffee_start_clock" to "Add coffee and start the clock",
    "seal_steep" to "Seal and steep", "bloom_pour" to "First pour", "bloom_wait" to "Let it bloom",
    "close_and_add_coffee" to "Close the valve and add coffee", "stop_and_taste" to "Stop at the cup target",
    "initial_wetting" to "Wet the coffee", "restir" to "Stir again", "check_fit" to "Check the basket fit",
)
