package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction.*
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.MANDATORY
import com.adsamcik.starlitcoffee.domain.brewing.session.P1VisualPriority.SAFETY_CRITICAL
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier.*
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference.*

internal val P1ManualGravityPlanSpecs = listOf(
    p1Plan(
        "v60_official_15_250",
        p1Stage(RINSE, visualPriority = MANDATORY),
        p1Stage(ADD_COFFEE),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(STAGE_DURATION, APPROXIMATE, 30)),
            temperatureTarget = temperatureCue(RANGE, 92.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(250.0),
            massTargets = listOf(waterCue(BREW_CUMULATIVE, EXACT, 250.0)),
            temperatureTarget = temperatureCue(RANGE, 92.0, 96.0),
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 150)),
        ),
    ),
    p1Plan(
        "v60_rao_20_330",
        p1Stage(PREPARE, visualPriority = MANDATORY),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, EXACT, 40)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 60.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 97.0),
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(200.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 40)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 140.0),
                waterCue(BREW_CUMULATIVE, EXACT, 200.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 97.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            AGITATE,
            timeTargets = listOf(timeCue(STAGE_DURATION, NO_LATER_THAN, 1)),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(330.0),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 130.0),
                waterCue(BREW_CUMULATIVE, EXACT, 330.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 97.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            AGITATE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, RANGE, 240, 270)),
        ),
    ),
    p1Plan(
        "v60_kasuya_4_6_20_300",
        p1Stage(RINSE),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(BREW_ELAPSED_AT_COMPLETION, EXACT, 45),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 50.0),
                waterCue(BREW_CUMULATIVE, EXACT, 50.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 92.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 45),
                timeCue(BREW_ELAPSED_AT_COMPLETION, EXACT, 90),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 70.0),
                waterCue(BREW_CUMULATIVE, EXACT, 120.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 92.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(180.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 90)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 180.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 92.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(240.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 130)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 240.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 92.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(300.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 160)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 300.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 92.0),
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, APPROXIMATE, 210),
                timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 210),
            ),
        ),
    ),
    p1Plan(
        "v60_kurasu_flash_16_150_70",
        p1Stage(PREPARE, visualPriority = MANDATORY),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, APPROXIMATE, 10),
                timeCue(BREW_ELAPSED_AT_COMPLETION, EXACT, 40),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 40.0),
                waterCue(BREW_CUMULATIVE, EXACT, 40.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 91.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(100.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 40)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 100.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 91.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(150.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 70)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 50.0),
                waterCue(BREW_CUMULATIVE, EXACT, 150.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 91.0),
        ),
        p1Stage(
            AGITATE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 130)),
            visualPriority = MANDATORY,
        ),
        p1Stage(SERVE),
    ),
    p1Plan(
        "wave185_ozone_25_400",
        p1Stage(RINSE, visualPriority = MANDATORY),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, EXACT, 30),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 50.0),
                waterCue(BREW_CUMULATIVE, EXACT, 50.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 93.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(160.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 30)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 110.0),
                waterCue(BREW_CUMULATIVE, EXACT, 160.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 93.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(220.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 45)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 220.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 93.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(280.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 60)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 280.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 93.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(340.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 75)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 340.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 93.0),
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(400.0),
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_START, EXACT, 105)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 60.0),
                waterCue(BREW_CUMULATIVE, EXACT, 400.0),
            ),
            temperatureTarget = temperatureCue(EXACT, 93.0),
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 180)),
        ),
    ),
    p1Plan(
        "wedge_pulse_23_5_400",
        p1Stage(RINSE, visualPriority = MANDATORY),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, EXACT, 40),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 50.0),
                waterCue(BREW_CUMULATIVE, EXACT, 50.0),
            ),
            temperatureTarget = temperatureCue(APPROXIMATE, 91.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 50.0),
                waterCue(BREW_CUMULATIVE, EXACT, 100.0),
            ),
            temperatureTarget = temperatureCue(APPROXIMATE, 91.0, 96.0),
        ),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 100.0),
                waterCue(BREW_CUMULATIVE, EXACT, 200.0),
            ),
            temperatureTarget = temperatureCue(APPROXIMATE, 91.0, 96.0),
        ),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 100.0),
                waterCue(BREW_CUMULATIVE, EXACT, 300.0),
            ),
            temperatureTarget = temperatureCue(APPROXIMATE, 91.0, 96.0),
        ),
        p1Stage(
            POUR,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, APPROXIMATE, 210, 240)),
            massTargets = listOf(
                waterCue(STAGE_ADDED, EXACT, 100.0),
                waterCue(BREW_CUMULATIVE, EXACT, 400.0),
            ),
            temperatureTarget = temperatureCue(APPROXIMATE, 91.0, 96.0),
            sourceWarning = true,
            visualPriority = MANDATORY,
        ),
    ),
    p1Plan(
        "chemex_42_700",
        p1Stage(PREPARE, visualPriority = SAFETY_CRITICAL),
        p1Stage(RINSE),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, EXACT, 45),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, RANGE, 100.0, 125.0),
                waterCue(BREW_CUMULATIVE, RANGE, 100.0, 125.0),
            ),
            temperatureTarget = temperatureCue(STARTING_POINT, 94.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(400.0),
            massTargets = listOf(waterCue(BREW_CUMULATIVE, EXACT, 400.0)),
            temperatureTarget = temperatureCue(STARTING_POINT, 94.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(700.0),
            massTargets = listOf(waterCue(BREW_CUMULATIVE, EXACT, 700.0)),
            temperatureTarget = temperatureCue(STARTING_POINT, 94.0, 96.0),
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, STARTING_POINT, 300, 390)),
        ),
        p1Stage(AGITATE),
    ),
    p1Plan(
        "generic_conical_low_agitation_20_320",
        p1Stage(RINSE, visualPriority = MANDATORY),
        p1Stage(
            BLOOM,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(
                timeCue(BREW_ELAPSED_AT_START, EXACT, 0),
                timeCue(STAGE_DURATION, RANGE, 30, 45),
            ),
            massTargets = listOf(
                waterCue(STAGE_ADDED, RANGE, 50.0, 60.0),
                waterCue(BREW_CUMULATIVE, RANGE, 50.0, 60.0),
            ),
            temperatureTarget = temperatureCue(STARTING_POINT, 93.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            massTargets = listOf(waterCue(BREW_CUMULATIVE, APPROXIMATE, 200.0)),
            temperatureTarget = temperatureCue(STARTING_POINT, 93.0, 96.0),
            visualPriority = MANDATORY,
        ),
        p1Stage(
            POUR,
            completion = cumulativeWaterCompletion(320.0),
            massTargets = listOf(waterCue(BREW_CUMULATIVE, EXACT, 320.0)),
            temperatureTarget = temperatureCue(STARTING_POINT, 93.0, 96.0),
        ),
        p1Stage(
            OBSERVE,
            completion = P1ExactCompletion.Observation,
            timeTargets = listOf(timeCue(BREW_ELAPSED_AT_COMPLETION, STARTING_POINT, 150, 240)),
        ),
    ),
)
