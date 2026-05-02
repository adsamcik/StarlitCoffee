package com.adsamcik.starlitcoffee.ui.screen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetAnimation
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.util.VibrationHelper
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BloomTimerScreen(
    brewViewModel: BrewViewModel,
    bloomSpritesheetWeights: Map<String, Int> = emptyMap(),
    onNavigateToBrew: () -> Unit,
    onBack: () -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vibrator = remember { getBloomVibrator(context) }

    KeepScreenOn()

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

    BackHandler {
        brewViewModel.pauseTimer()
        onBack()
    }

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
                        contentDescription = stringResource(
                            if (state.minuteAlertEnabled) R.string.cd_disable_minute_alerts
                            else R.string.cd_enable_minute_alerts,
                        ),
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

            // ── Center — timer + bloom info ─────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BloomSpritesheetAnimation(
                    bloomCountdownSeconds = state.bloomCountdownSeconds,
                    bloomDurationSeconds = state.effectiveBloomDurationSeconds,
                    isRunning = state.timerRunning,
                    spritesheetWeights = bloomSpritesheetWeights,
                    modifier = Modifier.size(172.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Hero timer
                Text(
                    text = formatBloomTime(state.elapsedSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bloom countdown
                if (state.bloomCountdownSeconds != null) {
                    Text(
                        text = formatBloomTime(state.bloomCountdownSeconds ?: 0),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.label_bloom_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val bloomDuration = state.effectiveBloomDurationSeconds
                    val progress = if (bloomDuration > 0) {
                        (state.bloomCountdownSeconds ?: 0).toFloat() / bloomDuration
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                            alpha = 0.12f,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Water info text
                val waterText = if (state.bloomMarkedAtSeconds == null) {
                    stringResource(R.string.format_pour_bloom_water, state.bloomG)
                } else {
                    stringResource(R.string.format_bloom_total, state.bloomG, state.waterG)
                }
                Text(
                    text = waterText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Bottom controls ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Play / Pause
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
                        contentDescription = stringResource(
                            if (state.timerRunning) R.string.action_pause else R.string.action_resume,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.instruction_bloom_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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
