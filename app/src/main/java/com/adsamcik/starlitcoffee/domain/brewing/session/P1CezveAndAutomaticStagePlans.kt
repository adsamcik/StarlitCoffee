package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction.*
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.MANDATORY
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.SAFETY_CRITICAL
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference.*

internal val P1CezveAndAutomaticPlanSpecs = listOf(
    p1Plan(
        "cezve_turkish_single_rise_6_65",
        p1Stage(PREPARE, visualPriority = SAFETY_CRITICAL),
        p1Stage(AGITATE, visualPriority = MANDATORY),
        p1Stage(
            HEAT,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            HEAT,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            SERVE,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(STAGE_DURATION, APPROXIMATE, 60, 120)),
            sourceWarning = true,
        ),
    ),
    p1Plan(
        "cezve_bounded_repeated_rise_12_130",
        p1Stage(PREPARE, visualPriority = SAFETY_CRITICAL),
        p1Stage(AGITATE, visualPriority = MANDATORY),
        p1Stage(
            HEAT,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            HEAT,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            HEAT,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            SERVE,
            completion = P1ExactCompletion.Observation,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
    ),
    p1Plan(
        "auto_batch_500_30",
        p1Stage(ADD_COFFEE, visualPriority = SAFETY_CRITICAL),
        p1Stage(
            ADD_WATER,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            CUSTOM,
            completion = P1ExactCompletion.Observation,
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            AGITATE,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
    ),
    p1Plan(
        "auto_batch_1000_60",
        p1Stage(ADD_COFFEE, visualPriority = SAFETY_CRITICAL),
        p1Stage(ADD_WATER, visualPriority = MANDATORY),
        p1Stage(
            CUSTOM,
            completion = P1ExactCompletion.Observation,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            AGITATE,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
    ),
    p1Plan(
        "auto_cupone_20_300",
        p1Stage(PREPARE, visualPriority = SAFETY_CRITICAL),
        p1Stage(ADD_COFFEE, visualPriority = MANDATORY),
        p1Stage(ADD_WATER, visualPriority = SAFETY_CRITICAL),
        p1Stage(
            CUSTOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(STAGE_DURATION, APPROXIMATE, 240)),
            temperatureTarget = temperatureCue(APPROXIMATE, 92.0, 96.0),
            visualPriority = SAFETY_CRITICAL,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(CLEAN_UP, visualPriority = SAFETY_CRITICAL),
    ),
)
