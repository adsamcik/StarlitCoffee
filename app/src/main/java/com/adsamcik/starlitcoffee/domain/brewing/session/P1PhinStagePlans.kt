package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction.*
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.MANDATORY
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.SAFETY_CRITICAL
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference.*

internal val P1PhinPlanSpecs = listOf(
    p1Plan(
        "phin_gravity_14_118",
        p1Stage(ADD_COFFEE, visualPriority = SAFETY_CRITICAL),
        p1Stage(
            PREPARE,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, EXACT, 45),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 30.0),
                waterCue(BREW_CUMULATIVE, EXACT, 30.0),
            ),
            temperatureTarget = temperatureCue(RANGE, 91.0, 93.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            ADD_WATER,
            completion = cumulativeWaterCompletion(118.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 45)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 88.0),
                waterCue(BREW_CUMULATIVE, EXACT, 118.0),
            ),
            temperatureTarget = temperatureCue(RANGE, 91.0, 93.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, NO_LATER_THAN, 120)),
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 300)),
            visualPriority = MANDATORY,
        ),
        p1Stage(SERVE, visualPriority = SAFETY_CRITICAL),
    ),
    p1Plan(
        "phin_screw_18_120",
        p1Stage(ADD_COFFEE, visualPriority = SAFETY_CRITICAL),
        p1Stage(PREPARE, visualPriority = SAFETY_CRITICAL),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, RANGE, 30, 45),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 25.0),
                waterCue(BREW_CUMULATIVE, EXACT, 25.0),
            ),
            temperatureTarget = temperatureCue(STARTING_POINT, 94.0, 98.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            ADD_WATER,
            completion = cumulativeWaterCompletion(120.0),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 95.0),
                waterCue(BREW_CUMULATIVE, EXACT, 120.0),
            ),
            temperatureTarget = temperatureCue(STARTING_POINT, 94.0, 98.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            SERVE,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
    ),
)
