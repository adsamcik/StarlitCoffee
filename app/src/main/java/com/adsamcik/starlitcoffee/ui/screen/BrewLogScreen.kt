package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.ui.util.emoji
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.StarRatingRow
import com.adsamcik.starlitcoffee.ui.component.SwipeToDismissCard
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BrewLogScreen"

@Composable
fun BrewLogScreen(
    brewViewModel: BrewViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
){
    val logs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val flavorTags by brewViewModel.flavorTags.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    val tagsByLog = remember(flavorTags) {
        flavorTags.groupBy { it.brewLogId }
    }

    var decafFilter by remember { mutableStateOf(com.adsamcik.starlitcoffee.ui.component.DecafFilter.ALL) }
    val decafCounts = remember(logs) {
        mapOf(
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.ALL to logs.size,
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.REGULAR to logs.count { !it.isDecaf },
            com.adsamcik.starlitcoffee.ui.component.DecafFilter.DECAF to logs.count { it.isDecaf },
        )
    }
    val showDecafFilter = (decafCounts[com.adsamcik.starlitcoffee.ui.component.DecafFilter.DECAF] ?: 0) > 0 &&
        (decafCounts[com.adsamcik.starlitcoffee.ui.component.DecafFilter.REGULAR] ?: 0) > 0
    val filteredLogs = remember(logs, decafFilter) {
        logs.filter { decafFilter.matches(it.isDecaf) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenTopBar(title = stringResource(R.string.screen_brew_log_title), onBack = onBack)

            if (logs.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Filled.History,
                    message = stringResource(R.string.msg_log_empty_title),
                    subtitle = stringResource(R.string.msg_log_empty_subtitle),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                ) {
                    if (showDecafFilter) {
                        item {
                            com.adsamcik.starlitcoffee.ui.component.DecafFilterChipRow(
                                selected = decafFilter,
                                counts = decafCounts,
                                onSelected = { decafFilter = it },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(filteredLogs, key = { it.id }) { log ->
                        val bagName = log.coffeeBagId?.let { bagId ->
                            bags.find { it.id == bagId }?.let { bag ->
                                bag.name + (bag.roaster?.let { " · $it" } ?: "")
                            }
                        }
                        val logTags = tagsByLog[log.id]?.take(3) ?: emptyList()

                        BrewLogCard(
                            log = log,
                            bagName = bagName,
                            flavorTags = logTags,
                            dateFormat = dateFormat,
                            onTap = { onNavigateToDetail(log.id) },
                            onDelete = { brewViewModel.deleteBrewLog(log) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrewLogCard(
    log: BrewLogEntity,
    bagName: String?,
    flavorTags: List<FlavorTagEntity>,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val feedbackEmoji = log.tasteFeedback?.let { name ->
        try {
            TasteFeedbackModel.valueOf(name).emoji()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse taste feedback", e)
            null
        }
    }

    val isUnrated = log.rating == null
    val accentColor = ratingAccentColor(log.rating)
    val decafSuffix = stringResource(R.string.label_decaf_suffix)

    SwipeToDismissCard(onDismiss = onDelete) {
        ElevatedCard(
            onClick = onTap,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isUnrated) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
            modifier = Modifier.fillMaxWidth().testTag("brew_log_card_${log.id}"),
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Color accent strip
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.large)
                        .background(accentColor),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                ) {
                    // Row 1: Method + bean name | date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = buildString {
                                    append(
                                        log.method.lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                    )
                                    if (log.isDecaf) append(decafSuffix)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (bagName != null) {
                                Text(
                                    text = bagName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                        Text(
                            text = dateFormat.format(Date(log.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Row 2: Dose/water + filter
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.format_dose_water_ratio, log.doseG, log.waterG, log.ratio),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (log.filterType != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatFilterType(log.filterType),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (log.isDecaf) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.label_decaf),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    // Row 3: Rating + feedback OR "Tap to rate"
                    if (!isUnrated) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (log.rating != null && log.rating > 0) {
                                StarRatingRow(rating = log.rating, starSize = 16.dp)
                            }
                            if (feedbackEmoji != null) {
                                if (log.rating != null && log.rating > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = feedbackEmoji,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        AssistChip(
                            onClick = onTap,
                            label = { Text(stringResource(R.string.action_rate_brew)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    // Row 4: Flavor tags
                    if (flavorTags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            flavorTags.forEach { tag ->
                                Text(
                                    text = tag.descriptor,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            CircleShape,
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    // Row 5: Notes preview
                    if (!log.freeformNotes.isNullOrBlank()) {
                        Text(
                            text = log.freeformNotes.take(80) + if (log.freeformNotes.length > 80) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ratingAccentColor(rating: Float?): Color = when {
    rating == null -> MaterialTheme.colorScheme.outlineVariant
    rating >= 4.5f -> MaterialTheme.colorScheme.primary
    rating >= 3.0f -> MaterialTheme.colorScheme.secondary
    rating >= 2.0f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun formatFilterType(filterType: String): String = when (filterType) {
    "PAPER" -> "Paper"
    "METAL_19K" -> "19K"
    "METAL_40K" -> "40K"
    else -> filterType
}
