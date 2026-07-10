package com.adsamcik.starlitcoffee.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Button
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.model.BrewRating
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.FreshnessPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOW_COFFEE_THRESHOLD_G = 30f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BagDetailSheet(
    bag: CoffeeBagEntity,
    brewLogs: List<BrewLogEntity>,
    flavorTags: List<FlavorTagEntity> = emptyList(),
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onStatusChange: (CoffeeBagStatus) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit = {},
    onWeightAdjust: (Long, Float) -> Unit = { _, _ -> },
    onSelectForBrewing: (() -> Unit)? = null,
){
    var statusMenuExpanded by remember { mutableStateOf(false) }
    val localizedMetadata = CoffeeMetadataNormalizer.resolveBagMetadata(bag)
    val freshness = remember(bag.roastDate) {
        CoffeeBagInsights.freshnessInsight(bag.roastDate)
    }
    val sensorySnapshot = remember(bag, brewLogs, flavorTags) {
        CoffeeBagInsights.buildSensorySnapshot(
            bag = bag,
            brewLogs = brewLogs,
            flavorTags = flavorTags,
        )
    }
    val grindInsight = remember(bag, brewLogs) {
        CoffeeBagInsights.buildGrindInsight(
            bag = bag,
            brewLogs = brewLogs,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
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
                IconButton(onClick = onRescan) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.action_rescan_label),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.action_edit_bag),
                    )
                }
            }

            // Photo gallery
            var fullScreenPhotoUri by remember { mutableStateOf<String?>(null) }
            bag.photoUris?.let { urisStr ->
                val uriList = urisStr.split(",").filter { it.isNotBlank() }
                if (uriList.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        uriList.forEach { uriStr ->
                            BagThumbnail(
                                uri = uriStr,
                                size = 80.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.clickable { fullScreenPhotoUri = uriStr },
                            )
                        }
                    }
                }
            }
            fullScreenPhotoUri?.let { uri ->
                FullScreenImageViewer(uri = uri) { fullScreenPhotoUri = null }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                .weight(1f),
            ) {
                // Bag details
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FreshnessRing(
                                insight = freshness,
                                size = 72.dp,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                            ) {
                                val phaseColor = when (freshness.phase) {
                                    FreshnessPhase.DEGASSING -> MaterialTheme.colorScheme.secondary
                                    FreshnessPhase.PEAK -> MaterialTheme.colorScheme.primary
                                    FreshnessPhase.MELLOWING -> MaterialTheme.colorScheme.tertiary
                                    FreshnessPhase.VINTAGE -> MaterialTheme.colorScheme.error
                                    FreshnessPhase.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = freshness.phase.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = phaseColor,
                                )
                                Text(
                                    text = freshness.headline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = freshness.coachText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Text(
                                    text = freshness.windowText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = phaseColor,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }

                    if (sensorySnapshot.topChips.isNotEmpty() || sensorySnapshot.totalRatings > 0) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.label_sensory_snapshot),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = sensorySnapshot.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                if (sensorySnapshot.totalRatings > 0) {
                                    Text(
                                        text = CoffeeBagInsights.formatRatingDistribution(sensorySnapshot.ratingCounts),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                InsightChipRow(
                                    chips = sensorySnapshot.topChips,
                                    modifier = Modifier.padding(top = 12.dp),
                                    maxVisible = 6,
                                )
                            }
                        }
                    }

                    if (
                        grindInsight.lastGrindSetting != null ||
                        grindInsight.bestGrindSetting != null ||
                        grindInsight.recentOutcomes.isNotEmpty()
                    ) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.label_grind_intelligence),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = grindInsight.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                grindInsight.adjustmentHint?.let { hint ->
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                if (grindInsight.recentOutcomes.isNotEmpty()) {
                                    androidx.compose.foundation.layout.FlowRow(
                                        modifier = Modifier.padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        grindInsight.recentOutcomes.forEach { outcome ->
                                            GrindOutcomeChip(entry = outcome)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    localizedMetadata.origin?.let { DetailRow("Origin", it) }
                    localizedMetadata.region?.let { DetailRow("Region", it) }
                    if (bag.farm != null) DetailRow("Farm", bag.farm)
                    localizedMetadata.variety?.let { DetailRow("Variety", it) }
                    localizedMetadata.processType?.let { DetailRow("Process", it) }
                    if (bag.altitude != null) DetailRow("Altitude", bag.altitude)
                    localizedMetadata.roastLevel?.let { DetailRow("Roast", it) }
                    if (bag.roastDate != null) DetailRow("Roast date", dateFormat.format(Date(bag.roastDate)))
                    if (bag.openedDate != null) DetailRow("Opened", dateFormat.format(Date(bag.openedDate)))
                    if (bag.expiryDate != null) DetailRow("Best before", dateFormat.format(Date(bag.expiryDate)))
                    if (bag.grindSetting != null) DetailRow("Dialed-in grind", bag.grindSetting)
                    if (bag.priceAmount != null) {
                        DetailRow(
                            "Price",
                            "${"%.2f".format(Locale.US, bag.priceAmount)} ${bag.priceCurrency ?: "USD"}",
                        )
                    }
                    if (bag.barcode != null) DetailRow("Barcode", bag.barcode)
                    if (bag.isDecaf) DetailRow("Decaf", "Yes")
                    // Weight section with progress bar, estimated doses, and adjust button
                    if (bag.weightG != null) {
                        val remaining = bag.weightG
                        val initial = bag.initialWeightG ?: remaining
                        val progress = if (initial > 0f) (remaining / initial).coerceIn(0f, 1f) else 0f
                        val isLow = remaining in 0.01f..LOW_COFFEE_THRESHOLD_G

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.label_remaining_coffee),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
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
                            Text(stringResource(R.string.action_adjust_weight))
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
                            Text(stringResource(R.string.action_add_weight_tracking))
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
                    localizedMetadata.tastingNotes?.let { DetailRow("Tasting notes", it) }
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
                            text = stringResource(R.string.label_brew_history),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    // Stats summary
                    item {
                        val ratingCounts = brewLogs
                            .mapNotNull { BrewRating.fromStoredValue(it.rating) }
                            .groupingBy { it }
                            .eachCount()
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
                            if (ratingCounts.isNotEmpty()) {
                                Text(
                                    text = CoffeeBagInsights.formatRatingDistribution(ratingCounts),
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
                                    val grindLine = buildString {
                                        log.grindSetting?.let {
                                            append("Grind $it")
                                        }
                                        log.tasteFeedback?.let { feedback ->
                                            if (isNotEmpty()) append(" • ")
                                            append(formatTasteFeedback(feedback))
                                        }
                                    }
                                    if (grindLine.isNotBlank()) {
                                        Text(
                                            text = grindLine,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                                BrewRating.fromStoredValue(log.rating)?.let { tier ->
                                    Text(
                                        text = tier.emoji,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Brew with this bag — primary action when bag isn't finished
            if (onSelectForBrewing != null && bag.status != CoffeeBagStatus.FINISHED.name) {
                Button(
                    onClick = onSelectForBrewing,
                    shape = MaterialTheme.shapes.large,
                    colors = primaryActionButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.LocalCafe, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.action_brew_now))
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

private fun formatTasteFeedback(raw: String): String {
    return raw.lowercase(Locale.getDefault())
        .replace('_', ' ')
        .replaceFirstChar { first ->
            if (first.isLowerCase()) {
                first.titlecase(Locale.getDefault())
            } else {
                first.toString()
            }
        }
}
