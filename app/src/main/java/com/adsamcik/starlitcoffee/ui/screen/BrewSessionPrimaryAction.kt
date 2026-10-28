package com.adsamcik.starlitcoffee.ui.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.ui.guidance.liveSourceConfirmationAction
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import java.util.UUID

internal enum class BrewSessionPrimaryAction { START, RESUME, COMPLETE_STEP, FINISH, PAUSE }

internal fun primaryBrewSessionAction(actions: BrewSessionActionAvailability): BrewSessionPrimaryAction? = when {
    actions.canStart -> BrewSessionPrimaryAction.START
    actions.canResume -> BrewSessionPrimaryAction.RESUME
    actions.canFinish -> BrewSessionPrimaryAction.FINISH
    actions.canManualAdvance -> BrewSessionPrimaryAction.COMPLETE_STEP
    actions.canPause -> BrewSessionPrimaryAction.PAUSE
    else -> null
}

@StringRes
internal fun brewStepConfirmationLabel(
    action: BrewStageAction?,
    contentId: StageContentId? = null,
    equipmentProfileId: String? = null,
): Int = when (liveSourceConfirmationAction(action, contentId, equipmentProfileId)) {
    BrewStageAction.PREPARE -> R.string.action_brew_ready
    BrewStageAction.RINSE -> R.string.action_brew_rinse_done
    BrewStageAction.ADD_COFFEE -> R.string.action_brew_coffee_added
    BrewStageAction.ADD_WATER -> R.string.action_brew_water_added
    BrewStageAction.BLOOM -> R.string.action_brew_bloom_done
    BrewStageAction.POUR -> R.string.action_brew_pour_done
    BrewStageAction.AGITATE -> R.string.action_brew_agitation_done
    BrewStageAction.STEEP -> R.string.action_brew_steep_done
    BrewStageAction.RELEASE -> R.string.action_brew_released
    BrewStageAction.PRESS -> R.string.action_brew_press_done
    BrewStageAction.HEAT -> R.string.action_brew_heating_done
    BrewStageAction.OBSERVE -> R.string.action_brew_checked
    BrewStageAction.FILTER -> R.string.action_brew_filtered
    BrewStageAction.SERVE -> R.string.action_brew_served
    BrewStageAction.CLEAN_UP -> R.string.action_brew_cleanup_done
    BrewStageAction.CUSTOM, null -> R.string.action_brew_step_done
}

@Composable
internal fun BrewSessionPrimaryActionBar(
    action: BrewSessionPrimaryAction,
    presentation: ActiveBrewSessionPresentation.Available,
    isDispatching: Boolean,
    onDispatch: (SessionEvent) -> Unit,
    equipmentProfileId: String? = null,
    backgroundBrews: @Composable () -> Unit = {},
) {
    val actions = presentation.actions
    val paused = actions.canResume
    val toggle = if (paused) BrewSessionPrimaryAction.RESUME else BrewSessionPrimaryAction.PAUSE
    val onToggle: (() -> Unit)? = if (actions.canPause || paused) ({ onDispatch(toggle.event()) }) else null
    val hasStepConfirmation = actions.canManualAdvance && !actions.canFinish
    val primary = when {
        action == BrewSessionPrimaryAction.START -> R.string.action_brew_start_short
        hasStepConfirmation -> brewStepConfirmationLabel(
            presentation.currentStage?.action, presentation.currentStage?.contentId, equipmentProfileId)
        action == BrewSessionPrimaryAction.FINISH && onToggle == null -> R.string.action_finish
        else -> null
    }
    val primaryAction = if (hasStepConfirmation) BrewSessionPrimaryAction.COMPLETE_STEP else action
    CenteredBrewActions(
        primaryLabel = primary?.let { stringResource(it) },
        onPrimary = primary?.let { { onDispatch(primaryAction.event()) } },
        onToggle = onToggle,
        paused = paused,
        finishLabel = if (actions.canFinish && primary == null) stringResource(
            if (presentation.isTimerOnly) R.string.action_end_brew_timer else R.string.action_finish) else null,
        onFinish = if (actions.canFinish) ({ onDispatch(BrewSessionPrimaryAction.FINISH.event()) }) else null,
        enabled = !isDispatching,
        backgroundBrews = backgroundBrews,
    )
}

internal fun BrewSessionPrimaryAction.event(): SessionEvent {
    val eventId = SessionEventId("ui:" + UUID.randomUUID())
    return when (this) {
        BrewSessionPrimaryAction.START -> SessionEvent.Start(eventId)
        BrewSessionPrimaryAction.RESUME -> SessionEvent.Resume(eventId)
        BrewSessionPrimaryAction.COMPLETE_STEP -> SessionEvent.ManualAdvance(eventId)
        BrewSessionPrimaryAction.FINISH -> SessionEvent.Finish(eventId)
        BrewSessionPrimaryAction.PAUSE -> SessionEvent.Pause(eventId)
    }
}
