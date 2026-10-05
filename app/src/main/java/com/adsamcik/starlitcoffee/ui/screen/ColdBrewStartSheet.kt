package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.viewmodel.ColdBrewStartFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColdBrewStartSheet(
    sessionKey: String,
    onDismiss: () -> Unit,
    onStart: (timerOnly: Boolean, durationMillis: Long, originKnown: Boolean, originMillis: Long?) -> Unit,
) {
    var timerOnly by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var duration by rememberSaveable(sessionKey) { mutableLongStateOf(ColdBrewStartFactory.DEFAULT_DURATION_MILLIS) }
    var editingDuration by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var startChoice by rememberSaveable(sessionKey) { mutableStateOf(BrewStartChoice.NOW) }
    var hoursAgo by rememberSaveable(sessionKey) { mutableStateOf("1") }
    var minutesAgo by rememberSaveable(sessionKey) { mutableStateOf("0") }
    val earlierMillis = earlierBrewStartOffset(hoursAgo, minutesAgo)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (timerOnly) R.string.title_cold_brew_timer else R.string.title_cold_brew),
                style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.msg_cold_brew_refrigerated), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { editingDuration = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (duration >= 3_600_000L) R.string.format_brew_activity_hours
                    else R.string.format_brew_activity_minutes,
                    if (duration >= 3_600_000L) duration / 3_600_000L else duration / 60_000L,
                    if (duration >= 3_600_000L) duration % 3_600_000L / 60_000L else duration % 60_000L / 1_000L))
            }
            if (timerOnly) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrewStartChoice.entries.forEach { choice ->
                        FilterChip(selected = startChoice == choice, onClick = { startChoice = choice },
                            label = { Text(stringResource(choice.labelResId)) })
                    }
                }
                when (startChoice) {
                    BrewStartChoice.EARLIER -> {
                        BrewEarlierStartFields(hoursAgo, { hoursAgo = it }, minutesAgo, { minutesAgo = it })
                    }
                    BrewStartChoice.UNKNOWN -> Text(stringResource(R.string.msg_brew_start_unknown))
                    BrewStartChoice.NOW -> Text(stringResource(R.string.msg_cold_brew_start_origin))
                }
                Button(onClick = {
                    val origin = when (startChoice) {
                        BrewStartChoice.NOW -> null
                        BrewStartChoice.EARLIER -> System.currentTimeMillis() - requireNotNull(earlierMillis)
                        BrewStartChoice.UNKNOWN -> null
                    }
                    onStart(true, duration, startChoice != BrewStartChoice.UNKNOWN, origin)
                }, enabled = startChoice != BrewStartChoice.EARLIER || earlierMillis != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_start_timer))
                }
                TextButton(onClick = { timerOnly = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_follow_brew_guide))
                }
            } else {
                Button(onClick = { onStart(false, duration, true, null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_follow_brew_guide))
                }
                OutlinedButton(onClick = { timerOnly = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_just_set_brew_timer))
                }
            }
        }
    }
    if (editingDuration) BrewTimerDurationDialog(duration, removable = false,
        originExplanationResId = R.string.msg_cold_brew_start_origin,
        onDismiss = { editingDuration = false }, onSave = { value ->
            if (value != null) duration = value
            editingDuration = false
        })
}

@Composable
internal fun BrewEarlierStartFields(hours: String, onHours: (String) -> Unit, minutes: String, onMinutes: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(hours, onValueChange = { onHours(it.filter(Char::isDigit).take(3)) },
            label = { Text(stringResource(R.string.label_brew_hours_ago)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(minutes, onValueChange = { onMinutes(it.filter(Char::isDigit).take(2)) },
            label = { Text(stringResource(R.string.label_brew_minutes_ago)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    }
}

internal fun earlierBrewStartOffset(hours: String, minutes: String): Long? {
    val hourValue = hours.toLongOrNull()?.takeIf { it in 0L..168L } ?: return null
    val minuteValue = minutes.toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
    return (hourValue * 3_600_000L + minuteValue * 60_000L).takeIf { it in 1L..604_800_000L }
}

private enum class BrewStartChoice(val labelResId: Int) {
    NOW(R.string.label_brew_start_now), EARLIER(R.string.label_brew_start_earlier), UNKNOWN(R.string.label_brew_start_unknown),
}
