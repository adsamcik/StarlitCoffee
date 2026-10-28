package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import java.util.UUID

/** The fixed live-brew action preserves a separate, explicit physical-clock pause. */
internal enum class BrewSessionPrimaryAction {
    START,
    RESUME,
    COMPLETE_STEP,
    FINISH,
    PAUSE_GUIDANCE,
    RESUME_GUIDANCE,
}

internal fun primaryBrewSessionAction(
    actions: BrewSessionActionAvailability,
    isGuidancePaused: Boolean = false,
): BrewSessionPrimaryAction? = when {
    actions.canStart -> BrewSessionPrimaryAction.START
    actions.canResume -> BrewSessionPrimaryAction.RESUME
    actions.canFinish -> BrewSessionPrimaryAction.FINISH
    actions.canManualAdvance -> BrewSessionPrimaryAction.COMPLETE_STEP
    actions.canPause -> if (isGuidancePaused) {
        BrewSessionPrimaryAction.RESUME_GUIDANCE
    } else {
        BrewSessionPrimaryAction.PAUSE_GUIDANCE
    }
    else -> null
}

@Composable
internal fun BrewSessionPrimaryActionBar(
    action: BrewSessionPrimaryAction,
    presentation: ActiveBrewSessionPresentation.Available,
    startsBloom: Boolean,
    isDispatching: Boolean,
    onDispatch: (SessionEvent) -> Unit,
    showGuideToggle: Boolean = true,
    backgroundBrews: @Composable () -> Unit = {},
) {
    val isGuidanceAction = action == BrewSessionPrimaryAction.PAUSE_GUIDANCE ||
        action == BrewSessionPrimaryAction.RESUME_GUIDANCE
    val labelRes = when (action) {
        BrewSessionPrimaryAction.START -> when {
            presentation.currentStage?.action == com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction.STEEP ->
                R.string.action_start_steeping
            startsBloom -> R.string.action_start_bloom
            else -> R.string.action_start_brewing
        }
        BrewSessionPrimaryAction.RESUME -> R.string.action_resume
        BrewSessionPrimaryAction.COMPLETE_STEP -> R.string.action_complete_step
        BrewSessionPrimaryAction.FINISH -> if (presentation.isTimerOnly) R.string.action_end_brew_timer else R.string.action_finish
        BrewSessionPrimaryAction.PAUSE_GUIDANCE -> R.string.action_pause_brew_guide
        BrewSessionPrimaryAction.RESUME_GUIDANCE -> R.string.action_resume_brew_guide
    }
    val onClick = { onDispatch(action.event()) }
    val buttonHeight = 64.dp
    val buttonShapes = ButtonDefaults.shapesFor(buttonHeight)
    val contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight)
    val buttonModifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if ((action == BrewSessionPrimaryAction.FINISH && presentation.actions.canPause) ||
                    action == BrewSessionPrimaryAction.RESUME) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (presentation.actions.canFinish) {
                            TextButton(enabled = !isDispatching,
                                onClick = { onDispatch(BrewSessionPrimaryAction.FINISH.event()) },
                                modifier = Modifier.weight(1f)) {
                                Text(stringResource(if (presentation.isTimerOnly) R.string.action_end_brew_timer
                                    else R.string.action_finish), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            enabled = !isDispatching,
                            onClick = { onDispatch(if (action == BrewSessionPrimaryAction.RESUME) action.event()
                                else SessionEvent.Pause(SessionEventId("ui:${UUID.randomUUID()}"))) },
                            modifier = Modifier.size(72.dp), shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        ) {
                            Icon(if (action == BrewSessionPrimaryAction.RESUME) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                stringResource(if (action == BrewSessionPrimaryAction.RESUME) R.string.action_resume
                                    else R.string.action_pause_brew_clock),
                                Modifier.size(36.dp))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                } else if (isGuidanceAction) {
                    OutlinedButton(
                        enabled = !isDispatching,
                        onClick = onClick,
                        modifier = buttonModifier,
                        shapes = buttonShapes,
                        contentPadding = contentPadding,
                    ) {
                        ActionLabel(labelRes)
                    }
                    Text(
                        stringResource(R.string.msg_brew_clock_keeps_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(
                        enabled = !isDispatching,
                        onClick = onClick,
                        modifier = buttonModifier,
                        shapes = buttonShapes,
                        contentPadding = contentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    ) {
                        ActionLabel(labelRes)
                    }
                    if (showGuideToggle && presentation.actions.canPause) {
                        val guideAction = if (presentation.isGuidancePaused) {
                            BrewSessionPrimaryAction.RESUME_GUIDANCE
                        } else {
                            BrewSessionPrimaryAction.PAUSE_GUIDANCE
                        }
                        TextButton(
                            enabled = !isDispatching,
                            onClick = { onDispatch(guideAction.event()) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(if (presentation.isGuidancePaused) {
                                R.string.action_resume_brew_guide
                            } else {
                                R.string.action_pause_brew_guide
                            }))
                        }
                    }
                }
            }
            backgroundBrews()
        }
    }
}

@Composable
private fun ActionLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
        fontWeight = FontWeight.SemiBold,
    )
}

internal fun BrewSessionPrimaryAction.event(): SessionEvent {
    val eventId = SessionEventId("ui:${UUID.randomUUID()}")
    return when (this) {
        BrewSessionPrimaryAction.START -> SessionEvent.Start(eventId)
        BrewSessionPrimaryAction.RESUME -> SessionEvent.Resume(eventId)
        BrewSessionPrimaryAction.COMPLETE_STEP -> SessionEvent.ManualAdvance(eventId)
        BrewSessionPrimaryAction.FINISH -> SessionEvent.Finish(eventId)
        BrewSessionPrimaryAction.PAUSE_GUIDANCE -> SessionEvent.SetGuidancePaused(true, eventId)
        BrewSessionPrimaryAction.RESUME_GUIDANCE -> SessionEvent.SetGuidancePaused(false, eventId)
    }
}
