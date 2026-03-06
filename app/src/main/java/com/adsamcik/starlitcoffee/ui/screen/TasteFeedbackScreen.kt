package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel

private data class FeedbackOption(
    val feedback: TasteFeedbackModel,
    val emoji: String,
    val label: String,
)

private val feedbackOptions = listOf(
    FeedbackOption(TasteFeedbackModel.TOO_SOUR, "🍋", "Too sour / weak"),
    FeedbackOption(TasteFeedbackModel.BALANCED, "✅", "Balanced (just right!)"),
    FeedbackOption(TasteFeedbackModel.TOO_BITTER, "😬", "Too bitter / harsh"),
    FeedbackOption(TasteFeedbackModel.ASTRINGENT, "😣", "Astringent / dry"),
    FeedbackOption(TasteFeedbackModel.CLOGGED, "🚫", "Clogged / stalled"),
)

@Composable
fun TasteFeedbackScreen(
    brewViewModel: BrewViewModel,
    onSaveAndFinish: () -> Unit,
    onNavigateToResult: () -> Unit,
){
    val uiState by brewViewModel.uiState.collectAsStateWithLifecycle()
    val selectedFeedback = uiState.tasteFeedback
    val rating = uiState.rating
    val notes = uiState.feedbackNotes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "How did it taste?",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 20.dp)
                .semantics { heading() },
        )

        // Feedback cards
        feedbackOptions.forEach { option ->
            val selected = selectedFeedback == option.feedback
            ElevatedCard(
                onClick = { brewViewModel.setTasteFeedback(option.feedback) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.emoji,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Star rating
        Text(
            text = "Rating",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (star in 1..5) {
                IconButton(
                    onClick = { brewViewModel.setRating(star) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "$star stars",
                        tint = if (star <= rating) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { brewViewModel.setFeedbackNotes(it) },
            label = { Text("Notes (optional)") },
            shape = MaterialTheme.shapes.small,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )

        // Action buttons
        Button(
            onClick = {
                brewViewModel.logBrew()
                onSaveAndFinish()
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Save & Done", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                onNavigateToResult()
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Brew Again", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
