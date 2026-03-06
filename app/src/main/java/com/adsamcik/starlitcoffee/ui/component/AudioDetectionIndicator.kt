package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.data.model.DetectorState

/**
 * Subtle user-facing audio detection status. Shows what the mic is hearing
 * as a single-line chip below the timer circle.
 */
@Composable
fun AudioDetectionIndicator(
    audioState: AudioAnalysisState,
    modifier: Modifier = Modifier,
) {
    val (icon, label, color) = when (audioState.detectorState) {
        DetectorState.IDLE -> Triple("🎙️", "Listening…", MaterialTheme.colorScheme.onSurfaceVariant)
        DetectorState.POURING -> Triple("🫗", "Pour detected", MaterialTheme.colorScheme.primary)
        DetectorState.DRIPPING -> {
            val rateText = if (audioState.dripRate > 0.1f) {
                " · %.0f/s".format(audioState.dripRate)
            } else {
                ""
            }
            Triple("💧", "Dripping$rateText", MaterialTheme.colorScheme.tertiary)
        }
        DetectorState.COMPLETE -> Triple("✅", "Drawdown complete", MaterialTheme.colorScheme.secondary)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$icon $label",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
