package com.adsamcik.starlitcoffee.ui.component

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import java.text.SimpleDateFormat
import java.util.Date

private const val TAG = "BagDetailSheet"
private const val LOW_COFFEE_THRESHOLD_G = 30f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BagDetailSheet(
    bag: CoffeeBagEntity,
    brewLogs: List<BrewLogEntity>,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onStatusChange: (CoffeeBagStatus) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onWeightAdjust: (Long, Float) -> Unit = { _, _ -> },
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bag.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 4.dp, top = 16.dp),
                    )
                    if (bag.roaster != null) {
                        Text(
                            text = "by ${bag.roaster}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit bag",
                    )
                }
            }

            // Photo gallery
            bag.photoUris?.let { urisStr ->
                val uriList = urisStr.split(",").filter { it.isNotBlank() }
                if (uriList.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        uriList.forEach { uriStr ->
                            val bitmap = remember(uriStr) {
                                try {
                                    val file = java.io.File(android.net.Uri.parse(uriStr).path ?: return@remember null)
                                    val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                        ?: return@remember null
                                    ImagePreprocessor.applyExifRotation(raw, file.absolutePath)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to load bag detail photo", e)
                                    null
                                }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Bag photo",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // Bag details
                item {
                    if (bag.origin != null) DetailRow("Origin", bag.origin)
                    if (bag.variety != null) DetailRow("Variety", bag.variety)
                    if (bag.roastLevel != null) DetailRow("Roast", bag.roastLevel)
                    if (bag.roastDate != null) DetailRow("Roast date", dateFormat.format(Date(bag.roastDate)))
                    // Weight section with progress bar, estimated doses, and adjust button
                    if (bag.weightG != null) {
                        val remaining = bag.weightG
                        val initial = bag.initialWeightG ?: remaining
                        val progress = if (initial > 0f) (remaining / initial).coerceIn(0f, 1f) else 0f
                        val isLow = remaining in 0.01f..LOW_COFFEE_THRESHOLD_G

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Remaining Coffee",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                isLow -> MaterialTheme.colorScheme.error
                                progress < 0.3f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${"%.0f".format(remaining)}g / ${"%.0f".format(initial)}g",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLow) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${"%.0f".format(progress * 100)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Estimated doses remaining
                        val avgDose = brewLogs.takeIf { it.isNotEmpty() }
                            ?.map { it.doseG }?.average()?.toFloat()
                            ?: 20f
                        if (remaining > 0f) {
                            val estimatedDoses = (remaining / avgDose).toInt()
                            Text(
                                text = "~$estimatedDoses dose${if (estimatedDoses != 1) "s" else ""} remaining" +
                                    " (at ${"%.0f".format(avgDose)}g/dose)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (isLow) {
                            Text(
                                text = "⚠ Running low — consider reordering",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        // Manual adjust button
                        var showWeightDialog by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showWeightDialog = true },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Adjust weight")
                        }
                        if (showWeightDialog) {
                            WeightAdjustDialog(
                                currentWeight = remaining,
                                onDismiss = { showWeightDialog = false },
                                onConfirm = { newWeight ->
                                    showWeightDialog = false
                                    onWeightAdjust(bag.id, newWeight)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        // No weight tracked yet — offer to add it
                        var showWeightDialog by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showWeightDialog = true },
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Text("Add weight tracking")
                        }
                        if (showWeightDialog) {
                            WeightAdjustDialog(
                                currentWeight = 0f,
                                onDismiss = { showWeightDialog = false },
                                onConfirm = { newWeight ->
                                    showWeightDialog = false
                                    onWeightAdjust(bag.id, newWeight)
                                },
                            )
                        }
                    }
                    if (bag.tastingNotes != null) DetailRow("Tasting notes", bag.tastingNotes)
                    if (bag.notes != null) DetailRow("Notes", bag.notes)
                    if (bag.traceabilityUrl != null) {
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        TextButton(
                            onClick = { uriHandler.openUri(bag.traceabilityUrl) },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("🔗 Traceability info")
                        }
                    }
                }

                // Brew history section
                if (brewLogs.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            text = "Brew History",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    // Stats summary
                    item {
                        val avgRating = brewLogs.mapNotNull { it.rating }.let { ratings ->
                            if (ratings.isNotEmpty()) ratings.average().toFloat() else null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${brewLogs.size} brew${if (brewLogs.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (avgRating != null) {
                                Text(
                                    text = "⭐ ${"%.1f".format(avgRating)} avg",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // Individual brew entries
                    items(brewLogs.sortedByDescending { it.createdAt }) { log ->
                        ElevatedCard(
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${log.method} · ${"%.0f".format(log.doseG)}g → ${"%.0f".format(log.waterG)}g",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.createdAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                log.rating?.let { rating ->
                                    Text(
                                        text = "⭐ $rating",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status changer
            Box(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = { statusMenuExpanded = true },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Status: ${bag.status.lowercase().replaceFirstChar { it.uppercase() }}")
                }
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false },
                ) {
                    CoffeeBagStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                onStatusChange(status)
                                statusMenuExpanded = false
                            },
                        )
                    }
                }
            }

            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Delete Bag",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
