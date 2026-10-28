package com.adsamcik.starlitcoffee.ui.screen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetAnimation
import com.adsamcik.starlitcoffee.ui.component.ExitBrewConfirmationDialog
import com.adsamcik.starlitcoffee.ui.util.DimModeScaffold
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.ui.util.keepScreenOnTimeoutMillis
import com.adsamcik.starlitcoffee.ui.util.rememberDimModeController
import com.adsamcik.starlitcoffee.util.VibrationHelper
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BloomTimerScreen(
    brewViewModel: BrewViewModel,
    bloomSpritesheetWeights: Map<String, Int> = emptyMap(),
    dimModeEnabled: Boolean = true,
    dimModeTrueBlack: Boolean = false,
    dimModeReduceBrightness: Boolean = false,
    dimModeFullscreen: Boolean = false,
    dimModeForceDarkInLight: Boolean = false,
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vibrator = remember { getBloomVibrator(context) }

    // Bloom is hands-busy (pouring), so hold the screen awake — but only for a
    // generous multiple of the bloom duration, so an abandoned bloom eventually
    // lets the screen sleep instead of holding it forever.
    KeepScreenOn(timeoutMillis = keepScreenOnTimeoutMillis(state.effectiveBloomDurationSeconds))

    // Pick the bloom spritesheet eagerly once weights are available so the
    // bud frame shown before bloom starts and the animation that runs during
    // bloom both reference the same flower. Idempotent — only sets state on
    // the first call per brew session.
    LaunchedEffect(bloomSpritesheetWeights) {
        brewViewModel.selectBloomSpritesheetIfNeeded(bloomSpritesheetWeights)
    }

    // Auto-start timer on first entry
    LaunchedEffect(Unit) {
        if (!state.timerRunning && state.elapsedSeconds == 0) {
            brewViewModel.startTimer()
        }
    }

    // Auto-mark bloom once timer is ticking
    LaunchedEffect(state.timerRunning, state.elapsedSeconds) {
        if (state.timerRunning && state.elapsedSeconds > 0 && state.bloomMarkedAtSeconds == null) {
            brewViewModel.markBloom()
        }
    }

    // 5-second countdown crescendo — ticks at T-5, -4, -3, -2, -1 with rising
    // haptic intensity and tone volume, building anticipation for the finale.
    val bloomCountdown = state.bloomCountdownSeconds
    LaunchedEffect(bloomCountdown, state.timerRunning, state.minuteAlertEnabled) {
        if (!state.timerRunning || !state.minuteAlertEnabled) return@LaunchedEffect
        if (bloomCountdown == null || bloomCountdown !in 1..5) return@LaunchedEffect
        // Step 0 at T-5 → step 4 at T-1
        val step = 5 - bloomCountdown
        val amplitude = 80 + step * 30 // 80, 110, 140, 170, 200
        val tickDuration = 40L + step * 10L // 40..80 ms
        vibrator?.vibrate(
            VibrationEffect.createOneShot(tickDuration, amplitude),
        )
        val volume = 40 + step * 12 // 40..88
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                delay(120L)
            } finally {
                tone.release()
            }
        } catch (_: Exception) { }
    }

    // Auto-advance to brew timer when bloom finishes — grand 3-note finale
    LaunchedEffect(state.bloomFinished) {
        if (state.bloomFinished && state.timerRunning) {
            VibrationHelper.vibrate(context, VibrationHelper.BrewHaptic.BLOOM_FINISHED)
            // Ding-ding-DONG: two rising high beeps, landing on a lower bell tone.
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 130)
                    delay(170L)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
                    delay(210L)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
                    delay(450L)
                } finally {
                    tone.release()
                }
            } catch (_: Exception) { }
            delay(500L)
            onNavigateToBrew()
        }
    }

    // Exit confirmation — leaving an active brew throws away progress, so
    // back/close prompts the user. Confirming resets timer/bloom state via
    // stopTimer() so re-entering starts from a clean slate.
    var showExitDialog by remember { mutableStateOf(false) }
    val hasBrewProgress = state.timerRunning ||
        state.elapsedSeconds > 0 ||
        state.bloomMarkedAtSeconds != null
    val requestExit: () -> Unit = {
        if (hasBrewProgress) {
            showExitDialog = true
        } else {
            brewViewModel.stopTimer()
            onBack()
        }
    }
    val confirmExit: () -> Unit = {
        showExitDialog = false
        brewViewModel.stopTimer()
        onBack()
    }
    if (showExitDialog) {
        ExitBrewConfirmationDialog(
            onConfirm = confirmExit,
            onDismiss = { showExitDialog = false },
        )
    }

    BackHandler(onBack = requestExit)

    // Minute-boundary haptic + tone
    val currentMinute = state.elapsedSeconds / 60
    LaunchedEffect(currentMinute) {
        if (currentMinute > 0 && state.timerRunning && state.minuteAlertEnabled) {
            vibrator?.let { v ->
                v.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1),
                )
            }
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                } finally {
                    tone.release()
                }
            } catch (_: Exception) { }
        }
    }

    var showHelp by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        val dimController = rememberDimModeController(featureEnabled = dimModeEnabled)
        LaunchedEffect(state.bloomFinished) {
            if (state.bloomFinished) dimController.wake()
        }
        DimModeScaffold(
            controller = dimController,
            modifier = Modifier.fillMaxSize(),
            trueBlackBackground = dimModeTrueBlack,
            reduceBrightness = dimModeReduceBrightness,
            hideSystemBars = dimModeFullscreen,
            forceDarkInLight = dimModeForceDarkInLight,
        ) {
            val remaining = state.bloomCountdownSeconds
            val elapsedLabel = stringResource(
                if (!state.timerRunning && state.elapsedSeconds > 0) {
                    R.string.label_brew_elapsed_paused
                } else {
                    R.string.label_elapsed
                },
            )
            Column(Modifier.fillMaxSize()) {
                BrewSessionHeader(onBack = requestExit, onHelp = { showHelp = true })
                CenteredBrewBody(Modifier.weight(1f)) {
                    CenteredBrewHero(
                        primary = CenteredBrewValue(
                            label = if (remaining != null) stringResource(R.string.label_remaining) else elapsedLabel,
                            value = formatBloomTime(remaining ?: state.elapsedSeconds),
                        ),
                        secondary = remaining?.let {
                            CenteredBrewValue(label = elapsedLabel, value = formatBloomTime(state.elapsedSeconds))
                        },
                    ) { bloomModifier ->
                        BloomSpritesheetAnimation(
                            bloomCountdownSeconds = remaining,
                            bloomDurationSeconds = state.effectiveBloomDurationSeconds,
                            isRunning = state.timerRunning,
                            selectedSpritesheetId = state.bloomSpritesheetId,
                            modifier = bloomModifier,
                        )
                    }
                }
                CenteredBrewActions(
                    onToggle = {
                        if (state.timerRunning) brewViewModel.pauseTimer() else brewViewModel.startTimer()
                    },
                    paused = !state.timerRunning,
                )
            }
            if (showHelp) {
                LegacyBrewHelpSheet(
                    state = state,
                    bloomActive = state.bloomMarkedAtSeconds != null && !state.bloomFinished,
                    onToggleMinuteAlert = brewViewModel::toggleMinuteAlert,
                    onDismiss = { showHelp = false },
                )
            }
        }
    }
}

private fun getBloomVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

private fun formatBloomTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val prefix = if (seconds < 0) "-" else ""
    return "$prefix$minutes:%02d".format(secs)
}
