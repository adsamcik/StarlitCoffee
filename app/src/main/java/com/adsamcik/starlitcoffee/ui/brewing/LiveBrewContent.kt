package com.adsamcik.starlitcoffee.ui.brewing

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.ui.guidance.BrewLiveVisualCue
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation

/** Live optional content. Clocks, completion and actions stay owned by the session. */
internal data class LiveBrewContent(
    val step: BrewStepContent? = null,
    val target: BrewStageReferenceCuePresentation? = null,
    val visualCue: BrewLiveVisualCue? = null,
    val instruction: String? = step?.instruction,
    @StringRes val instructionResource: Int? = null,
    val warnings: List<LiveBrewWarning> = emptyList(),
)

internal data class LiveBrewWarning(
    val text: String,
    @StringRes val summaryResource: Int? = null,
    val critical: Boolean = false,
)
