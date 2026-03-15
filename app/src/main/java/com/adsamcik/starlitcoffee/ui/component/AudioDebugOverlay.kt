package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorState
import com.adsamcik.starlitcoffee.data.model.FrequencyBand

/**
 * Debug overlay card showing real-time audio analysis during brew.
 * Expandable: collapsed shows level meter + silence indicator,
 * expanded shows full details + waveform history.
 */
@Composable
fun AudioDebugOverlay(
    audioState: AudioAnalysisState,
    isMonitoring: Boolean,
    isRecording: Boolean,
    onToggleMonitoring: () -> Unit,
    onToggleRecording: () -> Unit,
    onMarkEvent: ((String) -> Unit)? = null,
    onMarkProblem: ((String) -> Unit)? = null,
    onSessionSetup: ((placement: String, environment: String, notes: String) -> Unit)? = null,
    onExportSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
){
    var expanded by remember { mutableStateOf(false) }
    var showLabSetup by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: clickable to expand/collapse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (isMonitoring) "Microphone active" else "Microphone off",
                    tint = if (isMonitoring) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )

                Spacer(Modifier.width(8.dp))

                // Level meter
                if (isMonitoring) {
                    val levelFraction = dbToFraction(audioState.rmsDb)
                    val levelColor = when {
                        audioState.isSilent -> MaterialTheme.colorScheme.outlineVariant
                        levelFraction > 0.8f -> MaterialTheme.colorScheme.error
                        levelFraction > 0.5f -> Color(0xFFFF9800) // orange
                        else -> MaterialTheme.colorScheme.primary
                    }
                    LinearProgressIndicator(
                        progress = { levelFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = levelColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    Text(
                        text = "Audio monitoring off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.width(4.dp))

                // dB readout
                if (isMonitoring) {
                    Text(
                        text = "${audioState.rmsDb.toInt()} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Detector state mini-badge (collapsed view)
                    if (audioState.detectorState != DetectorState.IDLE) {
                        Spacer(Modifier.width(4.dp))
                        val (stateLabel, _) = detectorStateDisplay(audioState.detectorState)
                        Text(
                            text = stateLabel,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Recording indicator
                if (isRecording) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(start = 4.dp),
                    )
                }

                // Expand/collapse
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Silence indicator
            if (isMonitoring && audioState.isSilent && audioState.silenceDurationMs > 0) {
                Text(
                    text = "🔇 Silence: ${audioState.silenceDurationMs / 1000}.${(audioState.silenceDurationMs % 1000) / 100}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp, start = 28.dp),
                )
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Controls row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onToggleMonitoring, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isMonitoring) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = if (isMonitoring) "Stop monitoring" else "Start monitoring",
                                tint = if (isMonitoring) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text(
                            text = if (isMonitoring) "Monitoring" else "Start mic",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Spacer(Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    onToggleRecording()
                                } else {
                                    showLabSetup = true
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FiberManualRecord,
                                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                                tint = if (isRecording) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text(
                            text = if (isRecording) "Recording" else "Record",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        // Export button — bundles all session files into a zip and shares
                        if (onExportSession != null) {
                            Spacer(Modifier.width(16.dp))

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onExportSession() },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Export brew data",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = "Export",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    // --- Event Marker Buttons (visible when recording + callbacks provided) ---
                    if (isRecording && onMarkEvent != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        var lastMarkedLabel by remember { mutableStateOf<String?>(null) }
                        var pourToggle by remember { mutableStateOf(false) }

                        LaunchedEffect(lastMarkedLabel) {
                            if (lastMarkedLabel != null) {
                                kotlinx.coroutines.delay(1500L)
                                lastMarkedLabel = null
                            }
                        }

                        Text(
                            text = if (lastMarkedLabel != null) "✓ Marked: $lastMarkedLabel" else "Mark Events:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastMarkedLabel != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val label = if (!pourToggle) "pour_start" else "pour_stop"
                                    onMarkEvent(label)
                                    lastMarkedLabel = label
                                    pourToggle = !pourToggle
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (!pourToggle) "🫗 Pour" else "🛑 Stop",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    onMarkEvent("drip_start")
                                    lastMarkedLabel = "drip_start"
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("💧 Drip", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    onMarkEvent("drawdown_complete")
                                    lastMarkedLabel = "drawdown_complete"
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("✅ Done", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    onMarkProblem?.invoke("incorrect_detection")
                                    lastMarkedLabel = "problem"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("⚠️ Wrong", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Audio features
                    if (isMonitoring) {
                        AudioFeaturesGrid(audioState)

                        Spacer(Modifier.height(8.dp))

                        // Detector state + spectral features
                        DetectorStateSection(audioState)

                        Spacer(Modifier.height(8.dp))

                        // Level history waveform
                        if (audioState.levelHistory.isNotEmpty()) {
                            Text(
                                text = "Level history",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LevelHistoryChart(
                                levels = audioState.levelHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(top = 4.dp),
                            )
                        }
                    }

                    // Recording file path
                    audioState.recordingFilePath?.let { path ->
                        Text(
                            text = "📁 ${path.substringAfterLast("/")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // Baseline + trajectory info
                    if (audioState.baselineCalibrated) {
                        Text(
                            text = "🎯 Trajectory: %s (%.0f%%)".format(
                                audioState.trajectoryPhase,
                                audioState.brewConfidence * 100,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    } else {
                        Text(
                            text = "⏳ Calibrating ambient…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    // Phase label
                    if (audioState.currentPhaseLabel.isNotEmpty()) {
                        Text(
                            text = "Phase: ${audioState.currentPhaseLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }


                }
            }
        }
    }

    if (showLabSetup) {
        BrewLabSetupDialog(
            onConfirm = { placement, environment, notes ->
                showLabSetup = false
                onSessionSetup?.invoke(placement, environment, notes)
                onToggleRecording()
            },
            onDismiss = {
                showLabSetup = false
                onToggleRecording()
            },
        )
    }
}

@Composable
private fun AudioFeaturesGrid(audioState: AudioAnalysisState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FeatureChip("RMS", "${audioState.rmsDb.toInt()} dB")
        FeatureChip("Peak", "${audioState.peakDb.toInt()} dB")
        FeatureChip("Freq", "${audioState.dominantFrequencyHz.toInt()} Hz")
        FeatureChip("ZCR", "%.2f".format(audioState.zeroCrossingRate))
    }
}

@Composable
private fun FeatureChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Simple line chart of recent dB levels.
 */
@Composable
private fun LevelHistoryChart(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val silenceLineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas

        val stepX = size.width / (AudioAnalysisState.LEVEL_HISTORY_SIZE - 1).coerceAtLeast(1)
        val minDb = -80f
        val maxDb = 0f
        val rangeDb = maxDb - minDb

        // Silence threshold line
        val silenceY = size.height * (1f - (-40f - minDb) / rangeDb)
        drawLine(
            color = silenceLineColor,
            start = Offset(0f, silenceY),
            end = Offset(size.width, silenceY),
            strokeWidth = 1f,
        )

        // Level line
        for (i in 1 until levels.size) {
            val x1 = (i - 1) * stepX
            val x2 = i * stepX
            val y1 = size.height * (1f - (levels[i - 1].coerceIn(minDb, maxDb) - minDb) / rangeDb)
            val y2 = size.height * (1f - (levels[i].coerceIn(minDb, maxDb) - minDb) / rangeDb)
            drawLine(
                color = lineColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Shows the brew event detector state, band energy levels, and drip rate.
 */
@Composable
private fun DetectorStateSection(audioState: AudioAnalysisState) {
    Column {
        // Detector state badge + last event
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val (stateLabel, stateColor) = detectorStateDisplay(audioState.detectorState)
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = stateColor,
            )

            Spacer(Modifier.width(12.dp))

            // Drip rate (only during DRIPPING)
            if (audioState.detectorState == DetectorState.DRIPPING && audioState.dripRate > 0f) {
                Text(
                    text = "💧 %.1f/s".format(audioState.dripRate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(12.dp))
            }

            // Spectral tilt (water signature indicator)
            val tilt = audioState.spectralFeatures.spectralTilt
            if (tilt > 1.5f) {
                Text(
                    text = "🌊 %.1f".format(tilt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Band energy bars
        for (band in FrequencyBand.entries) {
            val energy = audioState.spectralFeatures.bandEnergyDb[band] ?: -96f
            val floor = audioState.noiseFloorDb[band] ?: -60f
            BandEnergyBar(band, energy, floor)
        }

        // Last event
        audioState.lastBrewEvent?.let { event ->
            Text(
                text = "Event: ${formatBrewEvent(event)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BandEnergyBar(
    band: FrequencyBand,
    energyDb: Float,
    noiseFloorDb: Float,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        Text(
            text = band.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )

        val fraction = dbToFraction(energyDb)
        val floorFraction = dbToFraction(noiseFloorDb)
        val barColor = when (band) {
            FrequencyBand.POUR -> MaterialTheme.colorScheme.primary
            FrequencyBand.DRIP_LOW -> MaterialTheme.colorScheme.tertiary
            FrequencyBand.DRIP_HIGH -> MaterialTheme.colorScheme.tertiary
            FrequencyBand.HIGH_MID -> MaterialTheme.colorScheme.secondary
        }
        val floorColor = MaterialTheme.colorScheme.outlineVariant

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            // Noise floor marker
            val floorX = size.width * floorFraction
            drawLine(
                color = floorColor,
                start = Offset(floorX, 0f),
                end = Offset(floorX, size.height),
                strokeWidth = 2f,
            )
            // Energy bar
            drawLine(
                color = barColor,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width * fraction, size.height / 2),
                strokeWidth = size.height * 0.6f,
                cap = StrokeCap.Round,
            )
        }

        Text(
            text = "${energyDb.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}

@Composable
private fun detectorStateDisplay(state: DetectorState): Pair<String, Color> {
    return when (state) {
        DetectorState.IDLE -> "⏸ Idle" to MaterialTheme.colorScheme.onSurfaceVariant
        DetectorState.POURING -> "🫗 Pouring" to MaterialTheme.colorScheme.primary
        DetectorState.DRIPPING -> "💧 Dripping" to MaterialTheme.colorScheme.tertiary
        DetectorState.COMPLETE -> "✅ Complete" to MaterialTheme.colorScheme.secondary
    }
}

private fun formatBrewEvent(event: BrewAudioEvent): String = when (event) {
    is BrewAudioEvent.PourStarted -> "Pour started (+%.1fdB)".format(event.confidenceDb)
    is BrewAudioEvent.PourStopped -> "Pour stopped (${event.durationMs / 1000}s)"
    is BrewAudioEvent.DripDetected -> "Drip (${event.energyDb.toInt()}dB)"
    is BrewAudioEvent.DripRateUpdated -> "Rate: %.1f/s".format(event.dripsPerSecond)
    is BrewAudioEvent.DrawdownComplete -> "Drawdown done (${event.totalDrainTimeMs / 1000}s)"
}

/**
 * Maps dBFS value to 0..1 fraction for the level meter.
 * Range: -80 dB (silence) to 0 dB (max).
 */
private fun dbToFraction(db: Float): Float {
    val clamped = db.coerceIn(-80f, 0f)
    return (clamped + 80f) / 80f
}
