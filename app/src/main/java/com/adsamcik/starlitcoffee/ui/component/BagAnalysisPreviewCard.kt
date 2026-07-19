package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage

private val previewFieldOrder = listOf(
    "name",
    "roaster",
    "origin",
    "variety",
    "processType",
    "roastLevel",
    "weight",
)

/**
 * A provisional inventory item. Its contents are evidence from the running scan,
 * not a persisted bag, so it deliberately has no action or click target.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BagAnalysisPreviewCard(
    result: BagPhotoProcessingResult,
    progress: ScanProgress?,
    modifier: Modifier = Modifier,
) {
    val previewUri = result.capturedPhotoUris
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val fields = previewFieldOrder.mapNotNull { fieldName ->
        result.fieldEvidence[fieldName]?.takeIf { it.value.isNotBlank() }
    }
    val determinateProgress = progress?.takeIf { it.stepCount > 0 }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { disabled() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_analyzing_bag_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.msg_analyzing_bag_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                previewUri?.let { uri ->
                    BagThumbnail(
                        uri = uri,
                        size = 56.dp,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = result.fieldEvidence["name"]?.value
                            ?: stringResource(R.string.scan_stage_ocr),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    result.fieldEvidence["roaster"]?.value?.let { roaster ->
                        Text(
                            text = roaster,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (determinateProgress != null) {
                Text(
                    text = stringResource(determinateProgress.stage.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                LinearProgressIndicator(
                    progress = { determinateProgress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.format_scan_stage_step,
                        determinateProgress.stepIndex,
                        determinateProgress.stepCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (fields.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fields.forEach { evidence ->
                        PreviewField(
                            label = stringResource(evidence.fieldName.labelRes()),
                            value = evidence.value,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewField(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "$label · $value",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun String.labelRes(): Int = when (this) {
    "name" -> R.string.label_name
    "roaster" -> R.string.label_roaster
    "origin" -> R.string.label_origin
    "variety" -> R.string.label_variety
    "processType" -> R.string.label_process
    "roastLevel" -> R.string.label_roast_level
    "weight" -> R.string.label_weight_grams
    else -> R.string.label_name
}

private fun ScanStage.labelRes(): Int = when (this) {
    ScanStage.OCR -> R.string.scan_stage_ocr
    ScanStage.BARCODE_LOOKUP -> R.string.scan_stage_barcode
    ScanStage.LLM_EXTRACT -> R.string.scan_stage_llm
    ScanStage.LABEL_CROP -> R.string.scan_stage_crop
    ScanStage.VISION -> R.string.scan_stage_vision
    ScanStage.COMBINING -> R.string.scan_stage_combine
    ScanStage.FINALIZING -> R.string.scan_stage_finalizing
}
