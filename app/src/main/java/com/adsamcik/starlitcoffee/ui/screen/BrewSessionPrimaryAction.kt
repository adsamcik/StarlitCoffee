package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
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

@Composable
internal fun BrewSessionPrimaryActionBar(
    action: BrewSessionPrimaryAction,
    presentation: ActiveBrewSessionPresentation.Available,
    isDispatching: Boolean,
    onDispatch: (SessionEvent) -> Unit,
    backgroundBrews: @Composable () -> Unit = {},
) {
    val actions = presentation.actions
    val paused = actions.canResume
    val toggle = if (paused) BrewSessionPrimaryAction.RESUME else BrewSessionPrimaryAction.PAUSE
    val onToggle: (() -> Unit)? = if (actions.canPause || paused) ({ onDispatch(toggle.event()) }) else null
    val hasNext = actions.canManualAdvance && !actions.canFinish
    val primary = when {
        action == BrewSessionPrimaryAction.START -> R.string.action_brew_start_short
        hasNext -> R.string.action_next
        action == BrewSessionPrimaryAction.FINISH && onToggle == null -> R.string.action_finish
        else -> null
    }
    val primaryAction = if (hasNext) BrewSessionPrimaryAction.COMPLETE_STEP else action
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
