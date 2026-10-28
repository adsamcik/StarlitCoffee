package com.adsamcik.starlitcoffee.ui.screen

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewTimingMode
import com.adsamcik.starlitcoffee.data.model.BrewVibrationEvent
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetAnimation
import com.adsamcik.starlitcoffee.ui.component.ExitBrewConfirmationDialog
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.ui.util.DimModeScaffold
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.ui.util.keepScreenOnTimeoutMillis
import com.adsamcik.starlitcoffee.ui.util.rememberDimModeController
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BrewTimerScreen(
    brewViewModel: BrewViewModel,
    bloomSpritesheetWeights: Map<String, Int> = emptyMap(),
    dimModeEnabled: Boolean = true,
    dimModeTrueBlack: Boolean = false,
    dimModeReduceBrightness: Boolean = false,
    dimModeFullscreen: Boolean = false,
    vibrationTheme: BrewVibrationTheme = BrewVibrationTheme.CLASSIC,
    dimModeForceDarkInLight: Boolean = false,
    showBrewingInstructions: Boolean = true,
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val usesActiveTimer = state.method.timingMode == BrewTimingMode.ACTIVE_TIMER
    val context = LocalContext.current
    val vibrator = remember { getVibrator(context) }

    val activity = context.findActivity() as? MainActivity
    val isPictureInPicture = activity?.isShowingBrewPictureInPicture == true
    DisposableEffect(activity, usesActiveTimer, state.timerRunning) {
        activity?.setBrewPictureInPictureAvailable(usesActiveTimer && state.timerRunning)
        onDispose { activity?.setBrewPictureInPictureAvailable(false) }
    }
    if (isPictureInPicture) {
        BrewPictureInPictureContent(state)
        return
    }

    if (usesActiveTimer) {
        // Safety net so a paused or abandoned brew doesn't pin the display on
        // forever: hold the screen awake for a generous multiple of the brew's
        // target time, then let the system timeout reclaim it.
        KeepScreenOn(timeoutMillis = keepScreenOnTimeoutMillis(state.timeTargetHighS))
    }

    // Pick the bloom spritesheet eagerly once weights are available so the
    // bud frame shown before Start is tapped, the animation that runs
    // during bloom, and the final flower through the rest of the brew reference
    // the same flower. Idempotent — only sets state on the first call per
    // brew session.
    LaunchedEffect(bloomSpritesheetWeights) {
        brewViewModel.selectBloomSpritesheetIfNeeded(bloomSpritesheetWeights)
    }

    // Auto-start the timer only when the brew begins immediately.
    // For bloom methods, the timer starts when the user taps Start Bloom —
    // pre-bloom setup (tare, kettle, etc.) shouldn't pollute elapsed time.
    LaunchedEffect(state.method) {
        val needsAutoStart = usesActiveTimer &&
            !state.timerRunning &&
            state.elapsedSeconds == 0 &&
            !state.method.hasBloom
        if (needsAutoStart) {
            brewViewModel.startTimer()
        }
    }

    // Exit confirmation — leaving an active brew throws away progress, so
    // back/close prompts the user. If nothing has happened yet (timer not
    // started, no bloom marked) we exit silently. Confirming the dialog
    // resets timer/bloom state via stopTimer() so re-entering the brew flow
    // (e.g. via GrindPrep -> BrewTimer without going through CalculatorBrew)
    // starts from a clean slate.
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

    // Minute-boundary haptic + tone (only fires when NOT in bloom countdown — bloom has its own alerts)
    val currentMinute = state.elapsedSeconds / 60
    val bloomActive = state.bloomMarkedAtSeconds != null && !state.bloomFinished
    LaunchedEffect(currentMinute) {
        val isAtMinuteBoundary = usesActiveTimer &&
            currentMinute > 0 &&
            state.timerRunning &&
            state.minuteAlertEnabled &&
            !bloomActive
        if (isAtMinuteBoundary) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    vibrationTheme.patternFor(BrewVibrationEvent.MINUTE),
                    -1,
                ),
            )
            playTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    // Strong alert when bloom ends — triple buzz + triple beep.
    LaunchedEffect(state.bloomFinished) {
        if (state.bloomFinished && state.timerRunning && state.bloomMarkedAtSeconds != null) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    vibrationTheme.patternFor(BrewVibrationEvent.BLOOM_COMPLETE),
                    -1,
                ),
            )
            repeat(3) {
                playTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                delay(300L)
            }
        }
    }

    // Pre-bloom-end warning at T-3s: single haptic
    val bloomCountdown = state.bloomCountdownSeconds
    LaunchedEffect(bloomCountdown) {
        if (bloomActive && bloomCountdown != null && bloomCountdown in 1..3) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    vibrationTheme.patternFor(BrewVibrationEvent.BLOOM_WARNING),
                    -1,
                ),
            )
            playTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }


    LaunchedEffect(state.elapsedSeconds, state.timeTargetHighS) {
        val reachedUpperTarget = state.timeTargetHighS > 0 &&
            state.elapsedSeconds == state.timeTargetHighS
        if (usesActiveTimer && state.timerRunning && reachedUpperTarget) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    vibrationTheme.patternFor(BrewVibrationEvent.TARGET_REACHED),
                    -1,
                ),
            )
            playTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        }
    }

    val hasTarget = state.timeTargetLowS > 0 && state.timeTargetHighS > 0
    val heroIsTotal = usesActiveTimer &&
        state.method.hasBloom &&
        !bloomActive &&
        state.waterG > 0f
    var showHelp by remember { mutableStateOf(false) }

    // ── Dim mode ─────────────────────────────────────────────
    // [DimModeScaffold] handles the touch+inactivity book-keeping and applies
    // the monochrome color-scheme override once the user goes idle. We only
    // need to wake it imperatively on brew transitions the user must not miss
    // (bloom finishing, target window reached).
    val dimController = rememberDimModeController(featureEnabled = dimModeEnabled)
    LaunchedEffect(state.bloomFinished, state.bloomMarkedAtSeconds) {
        if (state.bloomFinished && state.bloomMarkedAtSeconds != null) dimController.wake()
    }
    LaunchedEffect(state.elapsedSeconds, hasTarget) {
        if (usesActiveTimer && hasTarget && state.elapsedSeconds == state.timeTargetLowS) {
            dimController.wake()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        DimModeScaffold(
            controller = dimController,
            modifier = Modifier.fillMaxSize(),
            trueBlackBackground = dimModeTrueBlack,
            reduceBrightness = dimModeReduceBrightness,
            hideSystemBars = dimModeFullscreen,
            forceDarkInLight = dimModeForceDarkInLight,
        ) {
            val targetLabel = stringResource(R.string.label_live_target)
            val elapsedLabel = stringResource(
                if (!state.timerRunning && state.elapsedSeconds > 0) {
                    R.string.label_brew_elapsed_paused
                } else {
                    R.string.label_elapsed
                },
            )
            val grams = stringResource(R.string.unit_grams)
            val primary = when {
                heroIsTotal -> {
                    val value = "%.0f".format(state.waterG)
                    CenteredBrewValue(
                        label = targetLabel + " · " + stringResource(R.string.label_live_brew_total),
                        value = value + " " + grams,
                        unitSuffix = " " + grams,
                        contentDescription = stringResource(
                            R.string.format_brew_stage_target_grams,
                            state.waterG,
                        ) + " · " + stringResource(R.string.label_brew_reference_brew_total),
                    )
                }
                usesActiveTimer -> CenteredBrewValue(
                    label = if (bloomActive) stringResource(R.string.label_remaining) else elapsedLabel,
                    value = formatBrewTime(
                        if (bloomActive) state.bloomCountdownSeconds ?: 0 else state.elapsedSeconds,
                    ),
                )
                else -> CenteredBrewValue(
                    label = targetLabel,
                    value = formatLegacyTargetDurationRange(state.timeTargetLowS, state.timeTargetHighS),
                    numeric = false,
                )
            }
            val secondary = if (usesActiveTimer && (heroIsTotal || bloomActive)) {
                CenteredBrewValue(
                    label = elapsedLabel,
                    value = formatBrewTime(state.elapsedSeconds),
                )
            } else {
                null
            }
            val finish: () -> Unit = {
                brewViewModel.stopTimer()
                onComplete()
            }
            val toggleTimer: () -> Unit = {
                if (state.timerRunning) brewViewModel.pauseTimer() else brewViewModel.startTimer()
            }
            val showStartBloom = state.method.hasBloom &&
                state.bloomMarkedAtSeconds == null &&
                state.bloomG > 0f
            Column(Modifier.fillMaxSize()) {
                BrewSessionHeader(onBack = requestExit, onHelp = { showHelp = true })
                CenteredBrewBody(Modifier.weight(1f)) {
                    if (!state.timerRunning && state.elapsedSeconds == 0) {
                        state.ratioWarning?.let { WarningCard(it, Modifier.fillMaxWidth()) }
                        state.bloomWarning?.let { WarningCard(it, Modifier.fillMaxWidth()) }
                    }
                    CenteredBrewHero(primary = primary, secondary = secondary) { bloomModifier ->
                        BloomSpritesheetAnimation(
                            bloomCountdownSeconds = state.bloomCountdownSeconds.takeIf { state.method.hasBloom },
                            bloomDurationSeconds = state.effectiveBloomDurationSeconds.takeIf { state.method.hasBloom } ?: 0,
                            isRunning = state.timerRunning && bloomActive,
                            selectedSpritesheetId = state.bloomSpritesheetId,
                            modifier = bloomModifier,
                        )
                    }
                }
                when {
                    showStartBloom -> CenteredBrewActions(
                        primaryLabel = stringResource(R.string.action_brew_start_short),
                        onPrimary = brewViewModel::startBloom,
                    )
                    usesActiveTimer -> CenteredBrewActions(
                        onToggle = toggleTimer,
                        paused = !state.timerRunning,
                        finishLabel = stringResource(R.string.action_finish),
                        onFinish = finish,
                    )
                    else -> CenteredBrewActions(
                        primaryLabel = stringResource(R.string.action_finish),
                        onPrimary = finish,
                    )
                }
            }
            if (showHelp) {
                LegacyBrewHelpSheet(
                    state = state,
                    bloomActive = bloomActive,
                    showInstructions = showBrewingInstructions,
                    onToggleMinuteAlert = brewViewModel::toggleMinuteAlert,
                    onDismiss = { showHelp = false },
                )
            }
        }
    }
}

private fun playTone(toneType: Int, durationMs: Int) {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        try {
            tone.startTone(toneType, durationMs)
        } finally {
            tone.release()
        }
    } catch (_: Exception) { }
}

private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

private fun formatBrewTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val prefix = if (seconds < 0) "-" else ""
    return "$prefix$minutes:%02d".format(secs)
}


@Composable
private fun BrewPictureInPictureContent(state: BrewUiState) {
    val bloomActive = state.bloomMarkedAtSeconds != null && !state.bloomFinished
    val phase = when {
        bloomActive -> "Bloom"
        state.timerRunning -> "Brewing"
        else -> "Paused"
    }
    val visibleSeconds = if (bloomActive) state.bloomCountdownSeconds ?: 0 else state.elapsedSeconds

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = phase,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatBrewTime(visibleSeconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!bloomActive && state.timeTargetHighS > 0) {
                Text(
                    text = "Target ${formatBrewTime(state.timeTargetHighS)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
