package com.adsamcik.starlitcoffee.ui.screen

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check // Check as done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewPhase
import com.adsamcik.starlitcoffee.data.model.PhaseMode
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BrewTimerScreen(
    brewViewModel: BrewViewModel,
    onBack: () -> Unit,
) {
    val state by brewViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide system bars for immersive mode
        // Note: Using deprecated methods for now as this is a quick prototype.
        // In production, use WindowInsetsController or EdgeToEdge
        @Suppress("DEPRECATION")
        activity?.window?.decorView?.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    // Start timer on entry if not running
    LaunchedEffect(Unit) {
        if (!state.timerRunning && state.elapsedSeconds == 0) {
            brewViewModel.startTimer()
        }
    }
    
    // Back handler
    BackHandler {
        brewViewModel.pauseTimer()
        onBack()
    }

    val phases = state.timerPhases
    val currentIndex = state.currentPhaseIndex
    val currentPhase = phases.getOrNull(currentIndex)

    if (currentPhase == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active brew")
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    // Determine valve state
    val isValveOpen = currentPhase.valveState.equals("open", ignoreCase = true) || 
                      currentPhase.instruction.contains("open", ignoreCase = true)
    
    // Determine primary metric
    val showTimeAsPrimary = currentPhase.phaseType == PhaseType.BLOOM && 
                           currentPhase.mode == PhaseMode.AUTO_TIMED
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // BAND 1: VALVE STATE (Top, fixed height)
                ValveStateBand(
                    isOpen = isValveOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f)
                )

                // BAND 2: PRIMARY METRIC (Middle, dominant)
                PrimaryMetricBand(
                    phase = currentPhase,
                    remainingSeconds = state.phaseSecondsRemaining,
                    showTimeAsPrimary = showTimeAsPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2.2f)
                )

                // BAND 3: CONTEXT & CONTROLS (Bottom)
                ContextBand(
                    phase = currentPhase,
                    phases = phases,
                    currentIndex = currentIndex,
                    elapsedSeconds = state.elapsedSeconds,
                    timerRunning = state.timerRunning,
                    onNext = { brewViewModel.advancePhase() },
                    onToggleTimer = { 
                        if (state.timerRunning) brewViewModel.pauseTimer() else brewViewModel.startTimer() 
                    },
                    showTimeAsPrimary = showTimeAsPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                )
            }
            
            // Close button overlay
            IconButton(
                onClick = {
                    brewViewModel.pauseTimer()
                    onBack()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = if (isValveOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ValveStateBand(
    isOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isOpen) 
        MaterialTheme.colorScheme.primaryContainer 
    else 
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (isOpen)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        width = 3.dp,
                        color = contentColor,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(
                        color = if (isOpen) Color.Transparent else contentColor,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            AnimatedContent(
                targetState = isOpen,
                label = "ValveState"
            ) { open ->
                Text(
                    text = if (open) "OPEN" else "CLOSED",
                    style = MaterialTheme.typography.displaySmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PrimaryMetricBand(
    phase: BrewPhase,
    remainingSeconds: Int,
    showTimeAsPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = showTimeAsPrimary,
                transitionSpec = {
                    fadeIn() + slideInVertically { it / 2 } togetherWith fadeOut() + slideOutVertically { -it / 2 }
                },
                label = "MetricSwitch"
            ) { timePrimary ->
                if (timePrimary) {
                    val timeText = formatTime(remainingSeconds)
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    val weightText = "${phase.cumulativeWaterG.roundToInt()}"
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = weightText,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                            lineHeight = 100.sp
                        )
                        Text(
                            text = "g",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = phase.name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun ContextBand(
    phase: BrewPhase,
    phases: List<BrewPhase>,
    currentIndex: Int,
    elapsedSeconds: Int,
    timerRunning: Boolean,
    onNext: () -> Unit,
    onToggleTimer: () -> Unit,
    showTimeAsPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showTimeAsPrimary) {
                Column {
                    Text(
                        text = "${phase.cumulativeWaterG.roundToInt()}g",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "POURED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${phase.durationSeconds}s",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "TARGET",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Column {
                    Text(
                        text = "${currentIndex + 1} / ${phases.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "STEP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                
                IconButton(
                    onClick = onToggleTimer,
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                ) {
                    Icon(
                        imageVector = if (timerRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (timerRunning) "Pause" else "Resume",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = if (currentIndex == phases.lastIndex) "Done" else "Next",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val absSeconds = abs(seconds)
    val m = absSeconds / 60
    val s = absSeconds % 60
    return "%d:%02d".format(m, s)
}
