package com.adsamcik.starlitcoffee.ui.screen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
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
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetAnimation
import com.adsamcik.starlitcoffee.ui.component.WarningCard
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BrewTimerScreen(
    brewViewModel: BrewViewModel,
    bloomSpritesheetWeights: Map<String, Int> = emptyMap(),
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vibrator = remember { getVibrator(context) }

    KeepScreenOn()

    // Auto-start the timer only when the brew begins immediately.
    // For bloom methods, the timer starts when the user taps Start Bloom —
    // pre-bloom setup (tare, kettle, etc.) shouldn't pollute elapsed time.
    LaunchedEffect(Unit) {
        if (!state.timerRunning && state.elapsedSeconds == 0 && !state.method.hasBloom) {
            brewViewModel.startTimer()
        }
    }

    BackHandler {
        brewViewModel.pauseTimer()
        onBack()
    }

    // Minute-boundary haptic + tone (only fires when NOT in bloom countdown — bloom has its own alerts)
    val currentMinute = state.elapsedSeconds / 60
    val bloomActive = state.bloomMarkedAtSeconds != null && !state.bloomFinished
    LaunchedEffect(currentMinute) {
        if (currentMinute > 0 && state.timerRunning && state.minuteAlertEnabled && !bloomActive) {
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
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
                IconButton(
                    onClick = { brewViewModel.pauseTimer(); onBack() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
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
                val showBloomAnimation = state.method.hasBloom &&
                    state.bloomG > 0f &&
                    (!state.bloomFinished || bloomJustEndedFlash)
                if (showBloomAnimation) {
                    BloomSpritesheetAnimation(
                        bloomCountdownSeconds = state.bloomCountdownSeconds,
                        bloomDurationSeconds = state.effectiveBloomDurationSeconds,
                        isRunning = state.timerRunning && bloomActive,
                        spritesheetWeights = bloomSpritesheetWeights,
                        modifier = Modifier.size(if (bloomActive || bloomJustEndedFlash) 148.dp else 132.dp),
                    )
                }

                // Hero number: bloom countdown if active, otherwise elapsed total
                val heroSeconds = if (bloomActive) {
                    state.bloomCountdownSeconds ?: 0
                } else {
                    state.elapsedSeconds
                }
                Text(
                    text = formatBrewTime(heroSeconds),
                    style = MaterialTheme.typography.displayLarge,
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
                BrewGuidanceCard(
                    state = state,
                    bloomActive = bloomActive,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Metadata row: target time · temp · total water
                BrewMetadataRow(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
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
                    ElevatedButton(
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
                        colors = ButtonDefaults.elevatedButtonColors(
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
                    secondary = if (isPulsar) stringResource(R.string.instruction_open_valve_short) else null,
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
            secondary = if (isPulsar) stringResource(R.string.instruction_open_valve_short) else null,
        )
        state.waterG > 0f -> Guidance(
            primary = stringResource(R.string.instruction_pour_total, state.waterG),
            secondary = null,
        )
        else -> return
    }

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
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

/**
 * Compact row of supporting info — target brew time, water temp, total water.
 * These help the user gauge progress without competing with the primary guidance.
 */
@Composable
private fun BrewMetadataRow(
    state: com.adsamcik.starlitcoffee.viewmodel.BrewUiState,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        if (state.timeTargetLowS > 0 && state.timeTargetHighS > 0) {
            add(
                "Target " +
                    formatBrewTime(state.timeTargetLowS) +
                    "–" +
                    formatBrewTime(state.timeTargetHighS),
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

private fun formatBrewTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val prefix = if (seconds < 0) "-" else ""
    return "$prefix$minutes:%02d".format(secs)
}
