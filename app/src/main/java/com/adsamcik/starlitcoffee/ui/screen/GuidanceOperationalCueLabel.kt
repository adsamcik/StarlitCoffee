package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.guidance.GuidanceOperationalCue

/** Resolves stable operational cue IDs at the rendering locale boundary. */
@Composable
internal fun GuidanceOperationalCue.localizedLabel(): String = stringResource(
    when (this) {
        GuidanceOperationalCue.BEVERAGE_YIELD_TARGET ->
            R.string.guidance_cue_beverage_yield_target
        GuidanceOperationalCue.COUNTDOWN_OR_TIMESTAMP ->
            R.string.guidance_cue_countdown_or_timestamp
        GuidanceOperationalCue.CUMULATIVE_WATER_TARGET ->
            R.string.guidance_cue_cumulative_water_target
        GuidanceOperationalCue.CURRENT_POUR_TARGET ->
            R.string.guidance_cue_current_pour_target
        GuidanceOperationalCue.ELAPSED_TIMER -> R.string.guidance_cue_elapsed_timer
        GuidanceOperationalCue.HEAT_STATE -> R.string.guidance_cue_heat_state
        GuidanceOperationalCue.STAGE_ADVANCE -> R.string.guidance_cue_stage_advance
        GuidanceOperationalCue.VALVE_STATE -> R.string.guidance_cue_valve_state
    },
)
