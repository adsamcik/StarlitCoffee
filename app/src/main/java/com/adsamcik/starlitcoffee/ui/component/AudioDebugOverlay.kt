package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: mic icon, level bar, controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
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

                        IconButton(onClick = onToggleRecording, modifier = Modifier.size(36.dp)) {
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
                    }

                    Spacer(Modifier.height(8.dp))

                    // Audio features
                    if (isMonitoring) {
                        AudioFeaturesGrid(audioState)

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
 * Maps dBFS value to 0..1 fraction for the level meter.
 * Range: -80 dB (silence) to 0 dB (max).
 */
private fun dbToFraction(db: Float): Float {
    val clamped = db.coerceIn(-80f, 0f)
    return (clamped + 80f) / 80f
}
