package com.adsamcik.starlitcoffee.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage

/**
 * Full-screen progress for a guided camera scan. Inventory/gallery scans instead
 * continue in the notification-backed background flow immediately.
 */
@Composable
fun BagAnalysisProgressScreen(
    onSkip: () -> Unit,
    onRunInBackground: () -> Unit,
    progress: ScanProgress? = null,
) {
    Dialog(
        onDismissRequest = onRunInBackground,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler { onRunInBackground() }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (progress != null && progress.stepCount > 0) {
                    val animatedFraction by animateFloatAsState(
                        targetValue = progress.fraction,
                        label = "scanProgress",
                    )
                    Text(
                        text = stringResource(progress.stage.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.format_scan_stage_step,
                            progress.stepIndex,
                            progress.stepCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.screen_analyzing_bag_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.msg_analyzing_bag_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.msg_analyzing_bag_background_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(36.dp))
                Button(
                    onClick = onRunInBackground,
                    colors = primaryActionButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_run_in_background))
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_skip_ai))
                }
            }
        }
    }
}

/** In-app stage label for the determinate analyzing bar. */
private fun ScanStage.labelRes(): Int = when (this) {
    ScanStage.OCR -> R.string.scan_stage_ocr
    ScanStage.BARCODE_LOOKUP -> R.string.scan_stage_barcode
    ScanStage.LLM_EXTRACT -> R.string.scan_stage_llm
    ScanStage.LABEL_CROP -> R.string.scan_stage_crop
    ScanStage.VISION -> R.string.scan_stage_vision
    ScanStage.COMBINING -> R.string.scan_stage_combine
    ScanStage.FINALIZING -> R.string.scan_stage_finalizing
}
