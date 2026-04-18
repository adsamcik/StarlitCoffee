package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.QuickRating
import com.adsamcik.starlitcoffee.data.model.TasteIssue
import com.adsamcik.starlitcoffee.ui.util.labelRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostBrewCheckInCard(
    brew: BrewLogEntity,
    onQuickRate: (QuickRating) -> Unit,
    onIssueRate: (TasteIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIssueFollowUp by remember(brew.id) { mutableStateOf(false) }
    var selectedIssue by remember(brew.id) { mutableStateOf<TasteIssue?>(null) }

    val timeAgo = formatTimeAgo(brew.createdAt)
    val methodLabel = brew.method.lowercase().replaceFirstChar { it.uppercase() }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.label_how_was_last_cup),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "$methodLabel · ${"%.0f".format(brew.doseG)}g · $timeAgo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = !showIssueFollowUp,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickRating.entries.forEach { rating ->
                        FilledTonalButton(
                            onClick = {
                                if (rating == QuickRating.NOT_GREAT) {
                                    showIssueFollowUp = true
                                } else {
                                    onQuickRate(rating)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = "${rating.emoji} ${stringResource(rating.labelRes())}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showIssueFollowUp,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_what_was_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TasteIssue.entries.forEach { issue ->
                            FilledTonalButton(
                                onClick = {
                                    selectedIssue = issue
                                    onIssueRate(issue)
                                },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    text = "${issue.emoji} ${stringResource(issue.labelRes())}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeAgo(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 2 -> "just now"
        diffMinutes < 60 -> "${diffMinutes}min ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 2 -> "yesterday"
        else -> "${diffDays}d ago"
    }
}
