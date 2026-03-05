package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.service.BrewTimerService
import com.adsamcik.starlitcoffee.ui.component.BrewGuide
import com.adsamcik.starlitcoffee.util.VibrationHelper
import com.adsamcik.starlitcoffee.util.VibrationHelper.BrewHaptic
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

@Composable
fun BrewTimerScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showStopDialog by remember { mutableStateOf(false) }

    // Request notification permission for foreground service (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Timer works regardless */ }

    // Keep screen on
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val phases = uiState.timerPhases
    val currentPhaseIndex = uiState.currentPhaseIndex
    val totalElapsed = uiState.elapsedSeconds
    val running = uiState.timerRunning
    val phaseRemaining = uiState.phaseSecondsRemaining
    val showNext = uiState.showNextPreview

    val currentPhase = phases.getOrNull(currentPhaseIndex)
    val nextPhase = phases.getOrNull(currentPhaseIndex + 1)
    val totalDuration = phases.sumOf { it.durationSeconds }
    val finished = !running && totalElapsed > 0 && totalElapsed >= totalDuration

    // Auto-start on first composition
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

    // Auto-pause at end + brew-complete vibration
    LaunchedEffect(totalElapsed, totalDuration, running) {
        if (phases.isNotEmpty() && totalElapsed >= totalDuration && running) {
            VibrationHelper.vibrate(context, BrewHaptic.BREW_COMPLETE)
            brewViewModel.pauseTimer()
            BrewTimerService.stop(context)
        }
    }

    // Phase-change vibrations
    var previousPhaseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentPhaseIndex) {
        if (currentPhaseIndex != previousPhaseIndex && currentPhaseIndex > 0) {
            val phase = phases.getOrNull(currentPhaseIndex)
            val haptic = when {
                phase?.name == "Bloom" -> BrewHaptic.BLOOM
                phase?.name?.startsWith("Pour") == true -> BrewHaptic.POUR
                phase?.name?.startsWith("Drain") == true -> BrewHaptic.DRAIN
                phase?.name == "Drawdown" -> BrewHaptic.DRAWDOWN
                else -> BrewHaptic.POUR
            }
            VibrationHelper.vibrate(context, haptic)
        }
        previousPhaseIndex = currentPhaseIndex
    }

    // "Get ready" vibration 5s before phase ends
    LaunchedEffect(phaseRemaining) {
        if (phaseRemaining == 5 && running && currentPhaseIndex < phases.lastIndex) {
            VibrationHelper.vibrate(context, BrewHaptic.GET_READY)
        }
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Circular timer — kept as user preferred
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                val topLeft = Offset(stroke.width / 2, stroke.width / 2)

                drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = stroke)
                drawArc(primaryColor, -90f, 360f * animatedProgress, false, topLeft, arcSize, style = stroke)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                val min = totalElapsed / 60
                val sec = totalElapsed % 60
                Text(
                    text = "%d:%02d".format(min, sec),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "$min minutes $sec seconds elapsed"
                    },
                )
                if (currentPhase != null) {
                    Text(
                        text = currentPhase.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Brew Guide visualization (Pulsar only)
        if (uiState.method == BrewMethod.PULSAR && phases.isNotEmpty()) {
            BrewGuide(
                phases = phases,
                coffeeG = uiState.coffeeG,
                waterG = uiState.waterG,
                capacityMaxG = uiState.method.capacityMaxG?.toFloat(),
                refillCount = uiState.refillCount,
                activePhaseIndex = currentPhaseIndex,
                nextPhaseName = nextPhase?.name,
                nextPhaseWaterG = nextPhase?.waterG,
                showNextPreview = showNext,
            )
        }

        // Phase remaining countdown
        if (currentPhase != null && !finished) {
            Text(
                text = "${phaseRemaining}s",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Next-up preview (non-Pulsar fallback, or when brew guide not shown)
        if (uiState.method != BrewMethod.PULSAR) {
            AnimatedVisibility(
                visible = showNext && nextPhase != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = "Next: ${nextPhase?.name ?: ""}" +
                        if ((nextPhase?.waterG ?: 0f) > 0f) " · +${"%.0f".format(nextPhase?.waterG)}g" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        if (finished) {
            Text(
                text = "Brew complete! ☕",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Controls — large targets for wet hands
        if (!finished) {
            if (phases.size > 1) {
                Button(
                    onClick = {
                        VibrationHelper.vibrate(context, BrewHaptic.POUR)
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
                        contentDescription = if (running) "Pause" else "Resume",
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        if (running) "Pause" else "Resume",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { showStopDialog = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop",
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
                onClick = {
                    brewViewModel.logBrew()
                    brewViewModel.requestFeedbackSnackbar()
                    navController.popBackStack()
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop brewing?") },
            text = { Text("End brew and go back?") },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    brewViewModel.stopTimer()
                    BrewTimerService.stop(context)
                    brewViewModel.logBrew()
                    brewViewModel.requestFeedbackSnackbar()
                    navController.popBackStack()
                }) { Text("End Brew") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showStopDialog = false
                        brewViewModel.stopTimer()
                        BrewTimerService.stop(context)
                        navController.popBackStack()
                    }) { Text("Discard") }
                }
            },
        )
    }
}
