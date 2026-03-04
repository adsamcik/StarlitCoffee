package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.ui.component.DetailRow
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.StarRatingRow
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewLogScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val logs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())

    var selectedLog by remember { mutableStateOf<BrewLogEntity?>(null) }

    if (logs.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Filled.History,
            message = "Start brewing to see your history",
            subtitle = "Each completed brew with feedback will appear here",
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            item {
                Text(
                    text = "Brew Log",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            items(logs, key = { it.id }) { log ->
                BrewLogCard(
                    log = log,
                    dateFormat = dateFormat,
                    onTap = { selectedLog = log },
                    onDelete = { brewViewModel.deleteBrewLog(log) },
                )
            }
        }
    }

    selectedLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedLog = null },
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BrewLogDetail(
                log = log,
                dateFormat = dateFormat,
                onDelete = {
                    brewViewModel.deleteBrewLog(log)
                    selectedLog = null
                },
            )
        }
    }
}

@Composable
private fun BrewLogCard(
    log: BrewLogEntity,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    val feedbackEmoji = log.tasteFeedback?.let { name ->
        try {
            TasteFeedbackModel.valueOf(name).emoji
        } catch (_: Exception) {
            null
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.animateContentSize(),
    ) {
        ElevatedCard(
            onClick = onTap,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = log.method.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = dateFormat.format(Date(log.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "${"%.1f".format(log.doseG)}g / ${"%.0f".format(log.waterG)}g · 1:${"%.1f".format(log.ratio)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

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
                        Text(text = feedbackEmoji, style = MaterialTheme.typography.bodyMedium)
                    }
                }

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

@Composable
private fun BrewLogDetail(
    log: BrewLogEntity,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = log.method.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = dateFormat.format(Date(log.createdAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Coffee", "${"%.1f".format(log.doseG)}g")
        DetailRow("Water", "${"%.0f".format(log.waterG)}g")
        DetailRow("Ratio", "1:${"%.1f".format(log.ratio)}")

        if (log.brewTimeSeconds != null && log.brewTimeSeconds > 0) {
            val min = log.brewTimeSeconds / 60
            val sec = log.brewTimeSeconds % 60
            DetailRow("Brew time", "%d:%02d".format(min, sec))
        }

        if (log.filterType != null) {
            DetailRow("Filter", log.filterType)
        }

        if (log.tasteFeedback != null) {
            val feedback = try {
                TasteFeedbackModel.valueOf(log.tasteFeedback)
            } catch (_: Exception) {
                null
            }
            if (feedback != null) {
                DetailRow("Taste", "${feedback.emoji} ${feedback.displayName}")
            }
        }

        if (log.rating != null && log.rating > 0) {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Rating:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(100.dp),
                )
                StarRatingRow(rating = log.rating, starSize = 20.dp)
            }
        }

        if (!log.freeformNotes.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = log.freeformNotes,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Delete Log",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

