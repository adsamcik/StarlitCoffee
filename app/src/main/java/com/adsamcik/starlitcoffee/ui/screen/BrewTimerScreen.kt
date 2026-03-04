package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.navigation.TasteFeedback
import com.adsamcik.starlitcoffee.service.BrewTimerService
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

@Composable
fun BrewTimerScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Request notification permission for the foreground service (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Timer works regardless of permission */ }

    // Keep screen on during brew
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val phases = uiState.timerPhases
    val currentPhaseIndex = uiState.currentPhaseIndex
    val totalElapsed = uiState.elapsedSeconds
    val running = uiState.timerRunning

    val currentPhase = phases.getOrNull(currentPhaseIndex)
    val totalDuration = phases.sumOf { it.durationSeconds }
    val finished = !running && totalElapsed > 0 && totalElapsed >= totalDuration

    // Auto-start on first composition + start foreground service
    LaunchedEffect(Unit) {
        if (!running && totalElapsed == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            brewViewModel.startTimer()
            BrewTimerService.start(context)
        }
    }

    // Auto-pause when timer exceeds total duration
    LaunchedEffect(totalElapsed, totalDuration, running) {
        if (phases.isNotEmpty() && totalElapsed >= totalDuration && running) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            brewViewModel.pauseTimer()
            BrewTimerService.stop(context)
        }
    }

    // Haptic on phase change
    var previousPhaseIndex = remember { mutableIntStateOf(0) }
    LaunchedEffect(currentPhaseIndex) {
        if (currentPhaseIndex != previousPhaseIndex.intValue && currentPhaseIndex > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previousPhaseIndex.intValue = currentPhaseIndex
    }

    val progress = if (totalDuration > 0) totalElapsed.toFloat() / totalDuration else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "timer_progress",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Circular timer
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(260.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                val topLeft = Offset(stroke.width / 2, stroke.width / 2)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                val min = totalElapsed / 60
                val sec = totalElapsed % 60
                val timeText = "%d:%02d".format(min, sec)
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "$min minutes $sec seconds elapsed"
                    },
                )
                if (currentPhase != null) {
                    Text(
                        text = currentPhase.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Phase instruction (Pulsar-specific valve guidance, brew steps)
        if (currentPhase != null && currentPhase.instruction.isNotEmpty()) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (currentPhase.valveState.isNotEmpty()) {
                        Text(
                            text = "🔧 Valve: ${currentPhase.valveState}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = currentPhase.instruction,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Phase info
        if (currentPhase != null && currentPhase.waterG > 0) {
            Text(
                text = "${"%.0f".format(currentPhase.cumulativeWaterG)}g of ${"%.0f".format(uiState.waterG)}g poured",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }

        if (finished) {
            Text(
                text = "Brew complete!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        val remaining = (totalDuration - totalElapsed).coerceAtLeast(0)
        val remMin = remaining / 60
        val remSec = remaining % 60
        Text(
            text = "Remaining: %d:%02d".format(remMin, remSec),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Controls — large targets for wet hands
        if (!finished) {
            if (phases.size > 1) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        brewViewModel.advancePhase()
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Skip to next phase",
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Next Phase",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                FilledTonalButton(
                    onClick = {
                        if (running) {
                            brewViewModel.pauseTimer()
                        } else {
                            brewViewModel.startTimer()
                            BrewTimerService.start(context)
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    Icon(
                        if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (running) "Pause timer" else "Resume timer",
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        if (running) "Pause" else "Resume",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        brewViewModel.stopTimer()
                        BrewTimerService.stop(context)
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop timer",
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Stop",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = { navController.navigate(TasteFeedback) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text("Rate this brew", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}