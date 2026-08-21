package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction.*
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.MANDATORY
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.SAFETY_CRITICAL
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference.*

internal val P1ImmersionPlanSpecs = listOf(
    p1Plan(
        "clever_water_first_15_250",
        p1Stage(RINSE, visualPriority = MANDATORY),
        p1Stage(
            ADD_WATER,
            completion = cumulativeWaterCompletion(250.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 0)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 250.0),
                waterCue(BREW_CUMULATIVE, EXACT, 250.0),
            ),
            temperatureTarget = temperatureCue(APPROXIMATE, 95.0, 100.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            ADD_COFFEE,
            completion = P1ExactCompletion.Observation,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            STEEP,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 120),
                timeCue(BREW_ELAPSED_AT_COMPLETION, EXACT, 150),
            ),
        ),
        p1Stage(
            RELEASE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 150),
                timeCue(STAGE_DURATION, APPROXIMATE, 60),
                timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 210),
            ),
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(SERVE, visualPriority = MANDATORY),
    ),
    p1Plan(
        "clever_coffee_first_15_250",
        p1Stage(PREPARE, visualPriority = MANDATORY),
        p1Stage(
            ADD_WATER,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, APPROXIMATE, 15),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 250.0),
                waterCue(BREW_CUMULATIVE, EXACT, 250.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 95.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            STEEP,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 135)),
        ),
        p1Stage(
            RELEASE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 180, 210)),
            visualPriority = SAFETY_CRITICAL,
        ),
    ),
    p1Plan(
        "switch_official_20_240",
        p1Stage(RINSE, visualPriority = SAFETY_CRITICAL),
        p1Stage(
            ADD_WATER,
            completion = cumulativeWaterCompletion(240.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 0)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 240.0),
                waterCue(BREW_CUMULATIVE, EXACT, 240.0),
            ),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            STEEP,
            completion = countdownCompletion(120),
            timeTargets = listOf(timeCue(STAGE_DURATION, EXACT, 120)),
        ),
        p1Stage(
            RELEASE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 120)),
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            visualPriority = MANDATORY,
        ),
    ),
    p1Plan(
        "switch_ole_boen_hybrid_16_5_240",
        p1Stage(
            BLOOM,
            completion = countdownCompletion(40),
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, EXACT, 40),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 50.0),
                waterCue(BREW_CUMULATIVE, EXACT, 50.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 96.0),
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(150.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 40)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 100.0),
                waterCue(BREW_CUMULATIVE, EXACT, 150.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(240.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, APPROXIMATE, 90)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 90.0),
                waterCue(BREW_CUMULATIVE, EXACT, 240.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 96.0),
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            RELEASE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, APPROXIMATE, 130)),
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, RANGE, 180, 195)),
        ),
    ),
    p1Plan(
        "switch_gravity_15_250",
        p1Stage(RINSE, visualPriority = SAFETY_CRITICAL),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, APPROXIMATE, 30),
            ),
            temperatureTarget = temperatureCue(RANGE, 92.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(250.0),
            massTargets = listOf(waterCue(BREW_CUMULATIVE, EXACT, 250.0)),
            temperatureTarget = temperatureCue(RANGE, 92.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, STARTING_POINT, 150)),
        ),
    ),
)
