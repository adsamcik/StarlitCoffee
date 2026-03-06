package com.adsamcik.starlitcoffee.ui.component

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import java.text.SimpleDateFormat
import java.util.Date

private const val TAG = "BagCard"
private const val LOW_COFFEE_THRESHOLD_G = 30f

@Composable
fun BagCard(
    bag: CoffeeBagEntity,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
) {
    val statusColor = when (bag.status) {
        "SEALED" -> MaterialTheme.colorScheme.outline
        "OPEN" -> MaterialTheme.colorScheme.primary
        "FROZEN" -> MaterialTheme.colorScheme.tertiary
        "FINISHED" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
    }

    ElevatedCard(
        onClick = onTap,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Photo thumbnail
            bag.photoUri?.let { uri ->
                val bitmap = remember(uri) {
                    try {
                        val file = java.io.File(android.net.Uri.parse(uri).path ?: return@remember null)
                        val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            ?: return@remember null
                        ImagePreprocessor.applyExifRotation(raw, file.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load bag thumbnail", e)
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Bag photo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bag.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (bag.roaster != null) {
                    Text(
                        text = bag.roaster,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bag.weightG?.let { w ->
                    val initial = bag.initialWeightG ?: w
                    val progress = if (initial > 0f) (w / initial).coerceIn(0f, 1f) else 0f
                    val isLow = w in 0.01f..LOW_COFFEE_THRESHOLD_G
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                isLow -> MaterialTheme.colorScheme.error
                                progress < 0.3f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${"%.0f".format(w)}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLow) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isLow) {
                        Text(
                            text = "⚠ Low coffee",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (bag.roastDate != null) {
                    Text(
                        text = "Roasted: ${dateFormat.format(Date(bag.roastDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val freshness = DateParser.assessFreshness(bag.roastDate)
                    Text(
                        "${freshness.emoji} ${freshness.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (freshness) {
                            DateParser.Freshness.PEAK -> MaterialTheme.colorScheme.primary
                            DateParser.Freshness.STALE, DateParser.Freshness.OLD -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (bag.isDecaf) {
                    Text(
                        text = "☘ Decaf",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        bag.status.lowercase()
                            .replaceFirstChar { it.uppercase() },
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = statusColor,
                ),
            )
        }
    }
}
