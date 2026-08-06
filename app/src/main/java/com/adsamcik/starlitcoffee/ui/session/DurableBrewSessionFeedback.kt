package com.adsamcik.starlitcoffee.ui.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.adsamcik.starlitcoffee.data.model.BrewVibrationEvent
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.ui.util.DimModeController

/**
 * Keeps the durable live-brew surface as responsive as the legacy timer while
 * leaving persisted stage effects as the sole authority for background alerts.
 * It deliberately ignores first composition/restoration, so reopening a
 * session never replays an old cue.
 */
@Composable
fun DurableBrewSessionFeedback(
    presentation: ActiveBrewSessionPresentation.Available,
    vibrationTheme: BrewVibrationTheme,
    dimController: DimModeController,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val vibrator = remember(context) { context.durableBrewVibrator() }
    val stage = presentation.currentStage
    var previousStageId by remember(presentation.sessionId) { mutableStateOf<StageInstanceId?>(null) }
    var previousStageAction by remember(presentation.sessionId) { mutableStateOf<BrewStageAction?>(null) }
    var observedMinute by remember(presentation.sessionId) { mutableIntStateOf(-1) }
    var observedStatus by remember(presentation.sessionId) {
        mutableStateOf<BrewSessionStatus?>(null)
    }

    LaunchedEffect(presentation.sessionId, stage?.stageInstanceId, presentation.status, enabled) {
        val priorStageId = previousStageId
        val priorStageAction = previousStageAction
        val priorStatus = observedStatus
        val runningStageChanged = presentation.status == BrewSessionStatus.RUNNING &&
            priorStageId != null &&
            priorStageId != stage?.stageInstanceId
        if (enabled && runningStageChanged) {
            if (priorStageAction == BrewStageAction.BLOOM) {
                vibrator.playDurableCue(vibrationTheme, BrewVibrationEvent.BLOOM_COMPLETE)
                playDurableTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            } else {
                vibrator.playDurableCue(vibrationTheme, BrewVibrationEvent.TARGET_REACHED)
                playDurableTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            }
            dimController.wake()
        } else if (
            enabled && presentation.justCompletedFrom(priorStatus)
        ) {
            vibrator.playDurableCue(vibrationTheme, BrewVibrationEvent.TARGET_REACHED)
            playDurableTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            dimController.wake()
        }
        previousStageId = stage?.stageInstanceId
        previousStageAction = stage?.action
        observedStatus = presentation.status
    }

    val currentMinute = (presentation.totalActiveElapsedMillis / MILLIS_PER_MINUTE).toInt()
    LaunchedEffect(presentation.sessionId, currentMinute, presentation.status, enabled) {
        val previousMinute = observedMinute
        if (
            shouldPlayDurableMinuteCue(
                status = presentation.status,
                action = stage?.action,
                completion = stage?.completion,
                previousMinute = previousMinute,
                currentMinute = currentMinute,
                enabled = enabled,
            )
        ) {
            vibrator.playDurableCue(vibrationTheme, BrewVibrationEvent.MINUTE)
            playDurableTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
        observedMinute = currentMinute
    }

    val bloomWarningSeconds = (stage?.completion as? BrewStageCompletionPresentation.Countdown)
        ?.remainingMillis
        ?.let { remaining -> ((remaining.coerceAtLeast(0L) + 999L) / 1_000L).toInt() }
    LaunchedEffect(presentation.sessionId, stage?.stageInstanceId, bloomWarningSeconds, enabled) {
        if (
            enabled && stage?.action == BrewStageAction.BLOOM &&
                bloomWarningSeconds?.let { seconds -> seconds in 1..3 } == true
        ) {
            vibrator.playDurableCue(vibrationTheme, BrewVibrationEvent.BLOOM_WARNING)
            playDurableTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }
}

internal fun shouldPlayDurableMinuteCue(
    status: BrewSessionStatus,
    action: BrewStageAction?,
    completion: BrewStageCompletionPresentation?,
    previousMinute: Int,
    currentMinute: Int,
    enabled: Boolean,
): Boolean = enabled &&
    status == BrewSessionStatus.RUNNING &&
    action != BrewStageAction.BLOOM &&
    previousMinute >= 0 &&
    currentMinute > previousMinute &&
    (completion is BrewStageCompletionPresentation.Countdown ||
        completion is BrewStageCompletionPresentation.ElapsedRange)

private fun Vibrator?.playDurableCue(
    theme: BrewVibrationTheme,
    event: BrewVibrationEvent,
) {
    try {
        this?.vibrate(VibrationEffect.createWaveform(theme.patternFor(event), -1))
    } catch (_: Exception) {
        // Feedback is opportunistic; an unavailable vibrator must not affect the brew state.
    }
}

private fun playDurableTone(toneType: Int, durationMs: Int) {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        try {
            tone.startTone(toneType, durationMs)
        } finally {
            tone.release()
        }
    } catch (_: Exception) {
        // Some devices do not expose an audible notification stream.
    }
}

private fun Context.durableBrewVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

private fun ActiveBrewSessionPresentation.Available.justCompletedFrom(
    priorStatus: BrewSessionStatus?,
): Boolean = priorStatus != null &&
    priorStatus != BrewSessionStatus.COMPLETED &&
    status == BrewSessionStatus.COMPLETED

private const val MILLIS_PER_MINUTE = 60_000L
