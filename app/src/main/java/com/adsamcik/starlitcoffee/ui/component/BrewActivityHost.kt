package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.session.RestoredActiveBrewSession
import com.adsamcik.starlitcoffee.ui.screen.label
import com.adsamcik.starlitcoffee.ui.session.BrewActivityPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewActivityPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewActivityState
import com.adsamcik.starlitcoffee.ui.util.localizedBrewMethodLabel
import kotlinx.coroutines.delay

/** Clocks tick for display while visible; the durable coordinator owns effects. */
@Composable
fun BrewActivityHost(
    sessions: List<RestoredActiveBrewSession>,
    focusedSessionId: String?,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner, sessions) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(DISPLAY_REFRESH_MILLIS)
            }
        }
    }
    val activities = remember(sessions, now, focusedSessionId) {
        BrewActivityPresentationMapper.visible(
            sessions.mapNotNull { BrewActivityPresentationMapper.map(it, now) },
            focusedSessionId,
        )
    }
    BrewActivityStrip(activities, onOpenSession, modifier)
}

/** Kept stateless with respect to brew data so empty and multiple states share one UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrewActivityStrip(
    activities: List<BrewActivityPresentation>,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = activities.firstOrNull() ?: return
    var showChooser by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BrewActivityRow(
            activity = primary,
            count = activities.size,
            onClick = {
                if (activities.size == 1) onOpenSession(primary.sessionId) else showChooser = true
            },
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    if (showChooser) {
        ModalBottomSheet(onDismissRequest = { showChooser = false }) {
            Text(
                stringResource(R.string.title_active_brews),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activities, key = { it.sessionId }) { activity ->
                    BrewActivityRow(
                        activity = activity,
                        count = 1,
                        onClick = {
                            showChooser = false
                            onOpenSession(activity.sessionId)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrewActivityRow(
    activity: BrewActivityPresentation,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = brewActivityStatus(activity)
    val methodLabel = localizedBrewMethodLabel(activity.methodLabel)
    val stage = activity.stageTitle ?: activity.stageAction?.label()
    val description = listOfNotNull(stage, status).joinToString(" · ")
    val action = stringResource(
        if (count > 1) R.string.cd_choose_active_brews else R.string.cd_open_active_brew,
        methodLabel,
        description,
        count,
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (activity.needsAttention) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = action },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(methodLabel, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            if (count > 1) {
                Text(count.toString(), style = MaterialTheme.typography.labelLarge)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun brewActivityStatus(activity: BrewActivityPresentation): String = when (activity.state) {
    BrewActivityState.READY -> stringResource(R.string.label_brew_activity_ready)
    BrewActivityState.UNKNOWN_START -> stringResource(R.string.label_brew_start_unknown)
    BrewActivityState.PAUSED -> stringResource(R.string.label_brew_activity_paused)
    BrewActivityState.COMPLETED -> stringResource(R.string.label_brew_activity_save)
    BrewActivityState.TIMING_REACHED -> stringResource(R.string.label_brew_activity_timing_reached)
    BrewActivityState.ELAPSED -> stringResource(R.string.format_brew_activity_elapsed, activityDuration(activity.timeMillis))
    BrewActivityState.TIME_LEFT -> stringResource(R.string.format_brew_activity_time_left, activityDuration(activity.timeMillis))
}

@Composable
private fun activityDuration(millis: Long): String {
    // Remaining time rounds up so a positive target never looks already reached.
    val seconds = millis / 1_000L + if (millis % 1_000L > 0L) 1L else 0L
    return if (seconds >= SECONDS_PER_HOUR) {
        stringResource(R.string.format_brew_activity_hours, seconds / SECONDS_PER_HOUR, seconds % SECONDS_PER_HOUR / 60L)
    } else {
        stringResource(R.string.format_brew_activity_minutes, seconds / 60L, seconds % 60L)
    }
}

private const val DISPLAY_REFRESH_MILLIS = 1_000L
private const val SECONDS_PER_HOUR = 3_600L
