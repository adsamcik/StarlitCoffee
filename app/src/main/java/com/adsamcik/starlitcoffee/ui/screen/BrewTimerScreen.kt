package com.adsamcik.starlitcoffee.ui.screen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewTimingMode
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetAnimation
import com.adsamcik.starlitcoffee.ui.component.ExitBrewConfirmationDialog
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.ui.util.DimModeScaffold
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
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
    dimModeForceDarkInLight: Boolean = false,
    showBrewingInstructions: Boolean = true,
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val usesActiveTimer = state.method.timingMode == BrewTimingMode.ACTIVE_TIMER
    val context = LocalContext.current
    val vibrator = remember { getVibrator(context) }

    if (usesActiveTimer) {
        KeepScreenOn()
    }

    // Pick the bloom spritesheet eagerly once weights are available so the
    // bud frame shown before "Start Bloom" is tapped, the animation that runs
    // during bloom, and the post-bloom "final flower" flash all reference
    // the same flower. Idempotent — only sets state on the first call per
    // brew session.
    LaunchedEffect(bloomSpritesheetWeights, state.method.hasBloom) {
        if (state.method.hasBloom) {
            brewViewModel.selectBloomSpritesheetIfNeeded(bloomSpritesheetWeights)
        }
    }

    // Auto-start the timer only when the brew begins immediately.
    // For bloom methods, the timer starts when the user taps Start Bloom —
    // pre-bloom setup (tare, kettle, etc.) shouldn't pollute elapsed time.
    LaunchedEffect(state.method) {
        if (
            usesActiveTimer &&
            !state.timerRunning &&
            state.elapsedSeconds == 0 &&
            !state.method.hasBloom
        ) {
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
        if (usesActiveTimer && currentMinute > 0 && state.timerRunning && state.minuteAlertEnabled && !bloomActive) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1),
            )
            playTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    // Strong alert when bloom ends — triple buzz + triple beep + short flash
    LaunchedEffect(state.bloomFinished) {
        if (state.bloomFinished && state.timerRunning && state.bloomMarkedAtSeconds != null) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 300, 120, 300, 120, 500), -1,
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
                VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE),
            )
            playTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }

    // Track when bloom just ended so the "done!" visual flash is brief, not persistent.
    var bloomEndedAtMs by remember { mutableStateOf<Long?>(null) }
    var bloomJustEndedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(state.bloomFinished) {
        if (state.bloomFinished && state.bloomMarkedAtSeconds != null) {
            bloomEndedAtMs = System.currentTimeMillis()
            bloomJustEndedFlash = true
            delay(5000L)
            bloomJustEndedFlash = false
        } else if (!state.bloomFinished) {
            bloomEndedAtMs = null
            bloomJustEndedFlash = false
        }
    }

    // Timer color — animate based on bloom state and target window
    val hasTarget = state.timeTargetLowS > 0 && state.timeTargetHighS > 0
    val heroColor by animateColorAsState(
        targetValue = when {
            bloomJustEndedFlash -> MaterialTheme.colorScheme.error
            bloomActive -> MaterialTheme.colorScheme.tertiary
            !hasTarget -> MaterialTheme.colorScheme.onSurface
            state.elapsedSeconds > state.timeTargetHighS ->
                MaterialTheme.colorScheme.error
            state.elapsedSeconds >= state.timeTargetLowS ->
                MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "heroColor",
    )

    // ── Dim mode ─────────────────────────────────────────────
    // [DimModeScaffold] handles the touch+inactivity book-keeping and applies
    // the monochrome color-scheme override once the user goes idle. We only
    // need to wake it imperatively on brew transitions the user must not miss
    // (bloom finishing, target window reached).
    val dimController = rememberDimModeController(featureEnabled = dimModeEnabled)
    LaunchedEffect(bloomJustEndedFlash) {
        if (bloomJustEndedFlash) dimController.wake()
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
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (usesActiveTimer) {
                    IconButton(
                        onClick = { brewViewModel.toggleMinuteAlert() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (state.minuteAlertEnabled) {
                                Icons.Filled.Notifications
                            } else {
                                Icons.Filled.NotificationsOff
                            },
                            contentDescription = if (state.minuteAlertEnabled) {
                                "Disable minute alerts"
                            } else {
                                "Enable minute alerts"
                            },
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (state.minuteAlertEnabled) 0.7f else 0.35f,
                            ),
                        )
                    }
                }
                IconButton(
                    onClick = requestExit,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Surface guardrail warnings — visible before the user starts the
            // timer. Once the bloom is running, the user has committed to the
            // current setup so we hide the noise.
            val showWarnings = !state.timerRunning && state.elapsedSeconds == 0
            if (showWarnings) {
                state.ratioWarning?.let { warning ->
                    WarningCard(
                        message = warning,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                state.bloomWarning?.let { warning ->
                    WarningCard(
                        message = warning,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // ── Center — hero timer + secondary info ────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                // Keep the flower visible for the full brew session: the bud
                // is shown pre-bloom, the animation runs during bloom, and the
                // fully-bloomed final frame stays as decoration through the
                // remaining pours. BloomSpritesheetAnimation renders the last
                // frame automatically once bloomCountdownSeconds reaches 0.
                val showBloomAnimation = state.method.hasBloom && state.bloomG > 0f
                if (showBloomAnimation) {
                    BloomSpritesheetAnimation(
                        bloomCountdownSeconds = state.bloomCountdownSeconds,
                        bloomDurationSeconds = state.effectiveBloomDurationSeconds,
                        isRunning = state.timerRunning && bloomActive,
                        selectedSpritesheetId = state.bloomSpritesheetId,
                        modifier = Modifier.size(if (bloomActive || bloomJustEndedFlash) 148.dp else 132.dp),
                    )
                }

                // Hero number: bloom countdown if active, otherwise elapsed total.
                // Passive long-duration methods show the target window instead of
                // implying an active stopwatch session.
                val heroText = if (usesActiveTimer) {
                    val heroSeconds = if (bloomActive) {
                        state.bloomCountdownSeconds ?: 0
                    } else {
                        state.elapsedSeconds
                    }
                    formatBrewTime(heroSeconds)
                } else {
                    formatTargetDurationRange(state.timeTargetLowS, state.timeTargetHighS)
                }
                Text(
                    text = heroText,
                    style = if (usesActiveTimer) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    fontWeight = FontWeight.Light,
                    color = heroColor,
                    modifier = Modifier.semantics { heading() },
                )
                when {
                    bloomActive -> {
                        // Bloom badge
                        Text(
                            text = stringResource(R.string.label_bloom_badge),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )

                        val bloomDuration = state.effectiveBloomDurationSeconds
                        val progress = if (bloomDuration > 0) {
                            1f - (state.bloomCountdownSeconds ?: 0).toFloat() / bloomDuration
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                        )

                        Text(
                            text = "Total ${formatBrewTime(state.elapsedSeconds)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    bloomJustEndedFlash -> {
                        Text(
                            text = stringResource(R.string.label_bloom_done),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                // ── Primary guidance card — the most important info right now
                // Skipped when the user has turned off in-brew instructions.
                if (showBrewingInstructions) {
                    BrewGuidanceCard(
                        state = state,
                        bloomActive = bloomActive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Metadata row: target time · temp · total water
                BrewMetadataRow(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            }

            // ── Bottom controls ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Start Bloom — primary CTA before bloom is marked
                val showStartBloom = state.method.hasBloom &&
                    state.bloomMarkedAtSeconds == null &&
                    state.bloomG > 0f
                if (showStartBloom) {
                    Button(
                        onClick = {
                            // Start Bloom is the real "begin brew" moment for bloom methods —
                            // start the timer first if it hasn't started yet.
                            if (!state.timerRunning && state.elapsedSeconds == 0) {
                                brewViewModel.startTimer()
                            }
                            brewViewModel.markBloom()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.action_start_bloom),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (usesActiveTimer) {
                    // Play/Pause + Finish row (Finish is now a subdued text button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                brewViewModel.stopTimer()
                                onComplete()
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.action_finish_brew),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (state.timerRunning) brewViewModel.pauseTimer()
                                else brewViewModel.startTimer()
                            },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = if (state.timerRunning) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                contentDescription = if (state.timerRunning) "Pause" else "Resume",
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        // Spacer to balance the row
                        Spacer(modifier = Modifier.size(72.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            brewViewModel.stopTimer()
                            onComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text = stringResource(R.string.action_finish_brew),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Primary guidance card surfaces the single most important instruction right now:
 * - Pre-bloom: how much bloom water to pour + Pulsar valve hint
 * - During bloom: pour target + valve hint
 * - After bloom: pour to total + valve hint
 */
@Composable
private fun BrewGuidanceCard(
    state: com.adsamcik.starlitcoffee.viewmodel.BrewUiState,
    bloomActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (state.waterG <= 0f && state.bloomG <= 0f) return

    val isPulsar = state.method == BrewMethod.PULSAR
    val hasBloom = state.method.hasBloom && state.bloomG > 0f

    data class Guidance(val primary: String, val secondary: String?)

    val guidance = when {
        bloomActive -> {
            val bloomDuration = state.effectiveBloomDurationSeconds
            val bloomElapsed = bloomDuration - (state.bloomCountdownSeconds ?: bloomDuration)
            // Give the user a short pour window at the start of bloom before
            // switching the instruction to "swirl & wait". Cap at duration/2.
            val pourWindow = minOf(10, bloomDuration / 2).coerceAtLeast(3)
            val inPourPhase = bloomElapsed < pourWindow
            if (inPourPhase) {
                Guidance(
                    primary = stringResource(R.string.instruction_bloom_pour, state.bloomG),
                    // Pulsar bloom = valve closed for the entire bloom phase
                    // so the bloom water saturates the grounds instead of
                    // draining straight through. Valve opens only for the
                    // post-bloom main pour below.
                    secondary = if (isPulsar) stringResource(R.string.instruction_close_valve_short) else null,
                )
            } else {
                Guidance(
                    primary = stringResource(R.string.instruction_bloom_wait),
                    secondary = if (isPulsar) stringResource(R.string.instruction_close_valve_short) else null,
                )
            }
        }
        state.bloomFinished && state.bloomMarkedAtSeconds != null -> Guidance(
            primary = stringResource(R.string.instruction_pour_total, state.waterG),
            secondary = if (isPulsar) stringResource(R.string.instruction_open_valve_short) else null,
        )
        hasBloom -> Guidance(
            primary = stringResource(R.string.format_pour_bloom_water, state.bloomG),
            // Pre-bloom guidance — the user is about to tap "Start Bloom" and
            // begin pouring. Valve stays closed so the very first drops of
            // bloom water are captured by the grounds.
            secondary = if (isPulsar) stringResource(R.string.instruction_close_valve_short) else null,
        )
        state.waterG > 0f -> {
            val methodGuidanceRes = methodTimerGuidanceRes(state)
            Guidance(
                primary = if (methodGuidanceRes != null) {
                    stringResource(methodGuidanceRes)
                } else {
                    stringResource(R.string.instruction_pour_total, state.waterG)
                },
                secondary = null,
            )
        }
        else -> return
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = guidance.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (guidance.secondary != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = guidance.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@StringRes
private fun methodTimerGuidanceRes(
    state: BrewUiState,
): Int? {
    if (state.method.hasBloom) return null

    val guidance = state.method.stageGuidance
    return when {
        state.timeTargetLowS > 0 && state.elapsedSeconds >= state.timeTargetLowS -> {
            guidance.timerReadyRes ?: guidance.timerActiveRes ?: guidance.timerStartRes
        }
        state.timerRunning || state.elapsedSeconds > 0 -> {
            guidance.timerActiveRes ?: guidance.timerStartRes
        }
        else -> guidance.timerStartRes ?: guidance.timerActiveRes
    }
}

/**
 * Compact row of supporting info — target brew time, water temp, total water.
 * These help the user gauge progress without competing with the primary guidance.
 */
@Composable
private fun BrewMetadataRow(
    state: BrewUiState,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        if (state.timeTargetLowS > 0 && state.timeTargetHighS > 0) {
            add(
                "Target " + formatTargetDurationRange(state.timeTargetLowS, state.timeTargetHighS),
            )
        }
        if (state.method.tempRangeLow > 0 && state.method.tempRangeHigh > 0) {
            add("${state.method.tempRangeLow}–${state.method.tempRangeHigh}°C")
        }
        if (state.waterG > 0f) {
            add("Total ${"%.0f".format(state.waterG)}g")
        }
    }
    if (items.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { idx, text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (idx < items.lastIndex) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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

private fun formatTargetDurationRange(lowSeconds: Int, highSeconds: Int): String {
    if (lowSeconds <= 0 || highSeconds <= 0) return "–"
    return "${formatLongDuration(lowSeconds)}–${formatLongDuration(highSeconds)}"
}

private fun formatLongDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> formatBrewTime(seconds)
    }
}

private fun formatBrewTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val prefix = if (seconds < 0) "-" else ""
    return "$prefix$minutes:%02d".format(secs)
}
