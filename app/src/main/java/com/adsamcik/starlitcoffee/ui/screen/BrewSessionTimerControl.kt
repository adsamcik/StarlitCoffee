package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.UserBrewTimer
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import java.util.UUID

@Composable
internal fun BrewSessionTimerControl(
    presentation: ActiveBrewSessionPresentation.Available,
    enabled: Boolean,
    onDispatch: (SessionEvent) -> Unit,
) {
    val stage = presentation.currentStage ?: return
    if (presentation.status !in setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED)) return
    if (!presentation.hasPhysicalClockStarted) return
    var editing by rememberSaveable(presentation.sessionId, stage.stageInstanceId.persistentKey) {
        mutableStateOf(false)
    }
    var editingOrigin by rememberSaveable(presentation.sessionId, stage.stageInstanceId.persistentKey) {
        mutableStateOf(false)
    }
    if (!presentation.isPhysicalOriginKnown) {
        Text(stringResource(R.string.msg_brew_start_unknown), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { editingOrigin = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_add_brew_start_time))
        }
    }
    TextButton(onClick = { editing = true }, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Timer, contentDescription = null)
        Text(stringResource(if (presentation.userTimer == null) R.string.action_set_brew_reminder
            else R.string.action_edit_brew_reminder))
    }
    if (presentation.userTimerRemainingMillis == 0L) {
        Text(stringResource(R.string.msg_brew_reminder_reached), style = MaterialTheme.typography.bodyMedium)
    }
    if (editing) {
        BrewTimerDurationDialog(
            initialMillis = suggestedReminderDuration(presentation),
            removable = presentation.userTimer != null,
            onDismiss = { editing = false },
            onSave = { duration ->
                editing = false
                onDispatch(SessionEvent.SetTimerTarget(stage.stageInstanceId, duration,
                    eventId = SessionEventId("ui:${UUID.randomUUID()}")))
            },
        )
    }
    if (editingOrigin) BrewClockOriginDialog(onDismiss = { editingOrigin = false }, onSave = { origin ->
        editingOrigin = false
        onDispatch(SessionEvent.EstablishClockOrigin(stage.stageInstanceId, origin,
            SessionEventId("ui:${UUID.randomUUID()}")))
    })
}

@Composable
private fun BrewClockOriginDialog(onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var hours by rememberSaveable { mutableStateOf("1") }
    var minutes by rememberSaveable { mutableStateOf("0") }
    val offset = earlierBrewStartOffset(hours, minutes)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_brew_start_time)) },
        text = { BrewEarlierStartFields(hours, { hours = it }, minutes, { minutes = it }) },
        confirmButton = { TextButton(onClick = { onSave(System.currentTimeMillis() - requireNotNull(offset)) },
            enabled = offset != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

internal fun suggestedReminderDuration(presentation: ActiveBrewSessionPresentation.Available): Long {
    presentation.userTimer?.let { return it.durationMillis }
    val stage = presentation.currentStage ?: return DEFAULT_REMINDER_MILLIS
    val duration = when (val completion = stage.completion) {
        is BrewStageCompletionPresentation.Countdown -> completion.targetElapsedMillis
        is BrewStageCompletionPresentation.ElapsedRange -> completion.maximumElapsedMillis
        else -> stage.referenceCues.filterIsInstance<BrewStageReferenceCuePresentation.Time>().firstOrNull()?.let {
            when (it.reference) {
                StageTimeReference.BREW_ELAPSED_AT_COMPLETION, StageTimeReference.BREW_ELAPSED_AT_START ->
                    it.maximumMillis - (presentation.totalActiveElapsedMillis - stage.elapsedActiveMillis)
                StageTimeReference.STAGE_DURATION -> it.maximumMillis
            }
        }
    }
    return duration?.takeIf { it in 1L..UserBrewTimer.MAX_DURATION_MILLIS } ?: DEFAULT_REMINDER_MILLIS
}

/** A contextual editor shared by live brews and the cold-brew Start flow. */
@Composable
internal fun BrewTimerDurationDialog(
    initialMillis: Long,
    removable: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
    originExplanationResId: Int = R.string.msg_brew_reminder_origin,
) {
    var unit by rememberSaveable {
        mutableStateOf(when {
            initialMillis % HOUR_MILLIS == 0L -> TimerDurationUnit.HOURS
            initialMillis % MINUTE_MILLIS == 0L -> TimerDurationUnit.MINUTES
            else -> TimerDurationUnit.SECONDS
        })
    }
    var value by rememberSaveable { mutableStateOf((initialMillis / unit.millis).toString()) }
    val duration = value.toLongOrNull()?.takeIf { it > 0L && it <= UserBrewTimer.MAX_DURATION_MILLIS / unit.millis }
        ?.times(unit.millis)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Timer, null) },
        title = { Text(stringResource(R.string.action_set_brew_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value, onValueChange = { value = it.filter(Char::isDigit).take(7) },
                    label = { Text(stringResource(R.string.label_timer_duration)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerDurationUnit.entries.forEach { choice ->
                        FilterChip(selected = unit == choice, onClick = {
                            val previousDuration = duration
                            unit = choice
                            if (previousDuration != null && previousDuration % choice.millis == 0L) {
                                value = (previousDuration / choice.millis).toString()
                            }
                        }, label = { Text(stringResource(choice.labelResId)) })
                    }
                }
                Text(stringResource(originExplanationResId),
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(duration) }, enabled = duration != null) {
            Text(stringResource(R.string.action_save))
        } },
        dismissButton = {
            FlowRow {
                if (removable) TextButton(onClick = { onSave(null) }) {
                    Text(stringResource(R.string.action_remove_brew_reminder))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private enum class TimerDurationUnit(val millis: Long, val labelResId: Int) {
    SECONDS(1_000L, R.string.label_timer_seconds),
    MINUTES(MINUTE_MILLIS, R.string.label_timer_minutes),
    HOURS(HOUR_MILLIS, R.string.label_timer_hours),
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val DEFAULT_REMINDER_MILLIS = 240_000L
