package com.adsamcik.starlitcoffee.ui.screen

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlin.math.abs

@Composable
fun BrewTimerScreen(
    brewViewModel: BrewViewModel,
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Keep screen on while brewing
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-start on first entry
    LaunchedEffect(Unit) {
        if (!state.timerRunning && state.elapsedSeconds == 0) {
            brewViewModel.startTimer()
        }
    }

    BackHandler {
        brewViewModel.pauseTimer()
        onBack()
    }

    // Minute-boundary haptic + tone
    val currentMinute = state.elapsedSeconds / 60
    val vibrator = remember { getVibrator(context) }
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

    // Timer color — spring-animated to convey time state at a glance
    val hasTarget = state.timeTargetLowS > 0 && state.timeTargetHighS > 0
    val timerColor by animateColorAsState(
        targetValue = when {
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
        label = "timerColor",
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
                IconButton(onClick = { brewViewModel.toggleMinuteAlert() }) {
                    Icon(
                        imageVector = if (state.minuteAlertEnabled) {
                            Icons.Filled.Notifications
                        } else {
                            Icons.Filled.NotificationsOff
                        },
                        contentDescription = if (state.minuteAlertEnabled) {
                            stringResource(R.string.cd_disable_minute_alerts)
                        } else {
                            stringResource(R.string.cd_enable_minute_alerts)
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (state.minuteAlertEnabled) 0.7f else 0.35f,
                        ),
                    )
                }
                IconButton(onClick = { brewViewModel.pauseTimer(); onBack() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // ── Center — timer + info ───────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Hero timer
                Text(
                    text = formatTime(state.elapsedSeconds),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 96.sp,
                        letterSpacing = (-3).sp,
                    ),
                    fontWeight = FontWeight.Light,
                    color = timerColor,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bloom badge — spring-animated entrance
                AnimatedVisibility(
                    visible = state.bloomMarkedAtSeconds != null,
                    enter = fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "", fontSize = 16.sp)
                        Text(
                            text = stringResource(R.string.format_bloom_at, formatTime(state.bloomMarkedAtSeconds ?: 0)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target range
                if (hasTarget) {
                    Text(
                        text = "Target ${formatTime(state.timeTargetLowS)} – ${formatTime(state.timeTargetHighS)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Bottom controls ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Play / Pause — prominent circular button
                FilledTonalIconButton(
                    onClick = {
                        if (state.timerRunning) brewViewModel.pauseTimer()
                        else brewViewModel.startTimer()
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (state.timerRunning) Icons.Filled.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = if (state.timerRunning) stringResource(R.string.action_pause) else stringResource(R.string.action_resume),
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bloom action
                if (state.bloomMarkedAtSeconds == null) {
                    // State A: Bloom not started
                    Button(
                        onClick = { brewViewModel.markBloom() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.action_start_bloom),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                } else if (!state.bloomFinished) {
                    // State B: Bloom counting down
                    val bloomDuration = state.method.bloomDurationSeconds
                    val progress = if (bloomDuration > 0) {
                        (state.bloomCountdownSeconds ?: 0).toFloat() / bloomDuration
                    } else {
                        0f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.format_bloom_countdown, formatTime(state.bloomCountdownSeconds ?: 0)),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                            )
                        }
                    }
                } else {
                    // State C: Bloom finished — fire alert once
                    LaunchedEffect(state.bloomFinished) {
                        if (state.bloomFinished && state.timerRunning) {
                            vibrator?.vibrate(
                                VibrationEffect.createWaveform(
                                    longArrayOf(0, 200, 100, 200, 100, 300), -1,
                                ),
                            )
                            try {
                                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                                try {
                                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
                                } finally {
                                    tone.release()
                                }
                            } catch (_: Exception) { }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.label_bloom_done),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
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

private fun formatTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val prefix = if (seconds < 0) "-" else ""
    return "$prefix$minutes:%02d".format(secs)
}
