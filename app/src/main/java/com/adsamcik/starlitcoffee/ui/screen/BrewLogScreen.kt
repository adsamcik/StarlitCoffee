package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.ui.util.emoji
import com.adsamcik.starlitcoffee.navigation.BrewLogDetail
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.StarRatingRow
import com.adsamcik.starlitcoffee.ui.component.SwipeToDismissCard
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BrewLogScreen"

@Composable
fun BrewLogScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val logs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

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
                    onTap = { navController.navigate(BrewLogDetail(logId = log.id)) },
                    onDelete = { brewViewModel.deleteBrewLog(log) },
                )
            }
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
    val feedbackEmoji = log.tasteFeedback?.let { name ->
        try {
            TasteFeedbackModel.valueOf(name).emoji()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse taste feedback", e)
            null
        }
    }

    SwipeToDismissCard(onDismiss = onDelete) {
        ElevatedCard(
            onClick = onTap,
            shape = MaterialTheme.shapes.large,
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

