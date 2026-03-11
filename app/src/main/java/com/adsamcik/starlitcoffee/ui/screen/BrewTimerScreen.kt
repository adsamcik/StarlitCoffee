package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.filled.Check
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
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.service.BrewTimerService
import com.adsamcik.starlitcoffee.ui.component.AudioDebugOverlay
import com.adsamcik.starlitcoffee.ui.component.AudioDetectionIndicator
import com.adsamcik.starlitcoffee.ui.component.BrewGuide
import com.adsamcik.starlitcoffee.util.VibrationHelper
import com.adsamcik.starlitcoffee.util.VibrationHelper.BrewHaptic
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

@Composable
fun BrewTimerScreen(
    brewViewModel: BrewViewModel,
    onBack: () -> Unit,
){
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()
    val audioState by brewViewModel.audioState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showStopDialog by remember { mutableStateOf(false) }

    // Intercept system back — show stop dialog instead of silently leaving
    BackHandler(enabled = uiState.timerRunning || uiState.elapsedSeconds > 0) {
        showStopDialog = true
    }

    // Request notification permission for foreground service (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Timer works regardless */ }

    // Audio permission — init audio manager when granted
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val audioDir = java.io.File(context.getExternalFilesDir(null), "brew_audio")
            brewViewModel.initAudioManager(audioDir)
        }
    }

    // Request audio permission & init manager on first composition
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val audioDir = java.io.File(context.getExternalFilesDir(null), "brew_audio")
            brewViewModel.initAudioManager(audioDir)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Keep screen on + stop audio on dispose (safety net for unexpected exits)
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            brewViewModel.stopAudioMonitoring()
        }
    }

    // Restart audio monitoring and ensure timer coroutine on app resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                brewViewModel.ensureTimerRunning()
                if (uiState.timerRunning) {
                    brewViewModel.startAudioMonitoring()
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                brewViewModel.stopAudioMonitoring()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phases = uiState.timerPhases
    val currentPhaseIndex = uiState.currentPhaseIndex
    val totalElapsed = uiState.elapsedSeconds
    val running = uiState.timerRunning
    val phaseRemaining = uiState.phaseSecondsRemaining
    val phaseOvertime = uiState.phaseOvertime
    val showNext = uiState.showNextPreview

    val currentPhase = phases.getOrNull(currentPhaseIndex)
    val nextPhase = phases.getOrNull(currentPhaseIndex + 1)
    val totalDuration = phases.sumOf { it.durationSeconds }
    val finished = !running && totalElapsed > 0 && currentPhaseIndex >= phases.lastIndex && phaseOvertime

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

    // Auto-pause removed: with elastic drift, phases don't auto-advance.
    // Brew completion is detected when user advances past the last phase.

    // Phase-change vibrations
    var previousPhaseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentPhaseIndex) {
        if (currentPhaseIndex != previousPhaseIndex && currentPhaseIndex > 0) {
            val phase = phases.getOrNull(currentPhaseIndex)
            val haptic = when (phase?.phaseType) {
                PhaseType.BLOOM -> BrewHaptic.BLOOM
                PhaseType.POUR -> BrewHaptic.POUR
                PhaseType.DRAIN_AND_REFILL -> BrewHaptic.DRAIN
                PhaseType.DRAWDOWN -> BrewHaptic.DRAWDOWN
                null -> BrewHaptic.POUR
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
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Arc color responds to phase overtime state
    val arcColor by animateColorAsState(
        targetValue = when {
            finished -> MaterialTheme.colorScheme.tertiary
            phaseOvertime && kotlin.math.abs(phaseRemaining) > 15 -> errorColor
            phaseOvertime -> tertiaryColor
            else -> primaryColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "arc_color",
    )

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
                drawArc(arcColor, -90f, 360f * animatedProgress, false, topLeft, arcSize, style = stroke)
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

        // Audio detection status indicator (subtle, user-facing)
        if (audioState.isMonitoring) {
            AudioDetectionIndicator(audioState = audioState)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Brew Guide visualization (Pulsar only)
        if (uiState.method == BrewMethod.PULSAR && phases.isNotEmpty()) {
            BrewGuide(
                phases = phases,
                coffeeG = uiState.coffeeG,
                waterG = uiState.waterG,
                capacityMaxG = uiState.method.capacityMaxG?.toFloat(),
                refillCount = uiState.refillCount,
                activePhaseIndex = currentPhaseIndex,
                showNextPreview = showNext,
            )
        }

        // Audio debug overlay
        AudioDebugOverlay(
            audioState = audioState,
            isMonitoring = audioState.isMonitoring,
            isRecording = audioState.isRecording,
            onToggleMonitoring = { brewViewModel.toggleAudioMonitoring() },
            onToggleRecording = { brewViewModel.toggleAudioRecording() },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Phase remaining countdown (drift-aware)
        if (currentPhase != null && !finished) {
            val isEventGated = currentPhase.mode == com.adsamcik.starlitcoffee.data.model.PhaseMode.EVENT_GATED
            val displayText = when {
                isEventGated -> {
                    val elapsed = currentPhase.durationSeconds - phaseRemaining
                    if (currentPhase.durationSeconds > 0) {
                        "${elapsed}s / ~${currentPhase.durationSeconds}s"
                    } else {
                        "${elapsed}s"
                    }
                }
                phaseOvertime -> "+${kotlin.math.abs(phaseRemaining)}s"
                else -> "${phaseRemaining}s"
            }
            val displayColor = when {
                phaseOvertime && kotlin.math.abs(phaseRemaining) > 15 ->
                    MaterialTheme.colorScheme.error
                phaseOvertime ->
                    MaterialTheme.colorScheme.tertiary
                else ->
                    MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleLarge,
                color = displayColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Drift rebalance feedback — shows briefly when phases are adjusted
        val drift = uiState.lastDriftSeconds
        var showDriftHint by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.currentPhaseIndex, drift) {
            if (drift != 0 && uiState.currentPhaseIndex > 0) {
                showDriftHint = true
                kotlinx.coroutines.delay(3000L)
                showDriftHint = false
            }
        }
        AnimatedVisibility(
            visible = showDriftHint,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val driftText = if (drift > 0) {
                "⏩ ${drift}s early · remaining phases extended"
            } else {
                "⏪ ${kotlin.math.abs(drift)}s over · remaining phases shortened"
            }
            Text(
                text = driftText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
        if (uiState.method != BrewMethod.PULSAR) {
            AnimatedVisibility(
                visible = showNext && nextPhase != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = "Next: ${nextPhase?.name ?: ""}" +
                        if ((nextPhase?.waterG ?: 0f) > 0f) " · → ${"%.0f".format(nextPhase?.cumulativeWaterG)}g" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = finished,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(
                    text = "Brew complete! ☕",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val brewMin = totalElapsed / 60
                val brewSec = totalElapsed % 60
                Text(
                    text = "Brewed in %d:%02d · ${"%.0f".format(uiState.waterG)}g water".format(brewMin, brewSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Controls — large targets for wet hands
        if (!finished) {
            if (phases.size > 1 && currentPhaseIndex < phases.lastIndex) {
                val isEventGated = currentPhase?.mode == com.adsamcik.starlitcoffee.data.model.PhaseMode.EVENT_GATED
                val buttonLabel = if (isEventGated) "Done ✓" else "Next Phase"
                val buttonIcon = if (isEventGated) Icons.Filled.Check else Icons.Filled.SkipNext
                Button(
                    onClick = {
                        VibrationHelper.vibrate(context, BrewHaptic.POUR)
                        brewViewModel.advancePhase()
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(
                        buttonIcon,
                        contentDescription = buttonLabel,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        buttonLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (currentPhaseIndex >= phases.lastIndex && phases.isNotEmpty()) {
                // Last phase — show "Finish Brew" button
                Button(
                    onClick = {
                        VibrationHelper.vibrate(context, BrewHaptic.BREW_COMPLETE)
                        brewViewModel.pauseTimer()
                        BrewTimerService.stop(context)
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Finish brew",
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Finish Brew ☕",
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
                    shape = MaterialTheme.shapes.large,
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
                    shape = MaterialTheme.shapes.large,
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
                    onBack()
                },
                shape = MaterialTheme.shapes.large,
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
        val stopMin = totalElapsed / 60
        val stopSec = totalElapsed % 60
        val phasesCompleted = currentPhaseIndex
        val totalPhases = phases.size
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop brewing?") },
            text = {
                Column {
                    Text("End brew and go back?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%d:%02d elapsed · Phase %d/%d · ${"%.0f".format(uiState.coffeeG)}g dose".format(
                            stopMin, stopSec, phasesCompleted + 1, totalPhases,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    brewViewModel.stopTimer()
                    BrewTimerService.stop(context)
                    brewViewModel.logBrew()
                    brewViewModel.requestFeedbackSnackbar()
                    onBack()
                }) { Text("End Brew") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showStopDialog = false
                        brewViewModel.stopTimer()
                        BrewTimerService.stop(context)
                        onBack()
                    }) { Text("Discard") }
                }
            },
        )
    }
}
