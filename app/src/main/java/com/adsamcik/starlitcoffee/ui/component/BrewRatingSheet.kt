package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrewRatingSheet(
    onDismiss: () -> Unit,
    onSave: (rating: Float, selectedDescriptors: List<String>, notes: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rating by remember { mutableFloatStateOf(0f) }
    var selectedDescriptors by remember { mutableStateOf(emptySet<FlavorDescriptor>()) }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(36.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = "How was your brew?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Star rating
            HalfStarRatingRow(
                rating = rating,
                onRatingChanged = { rating = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Flavor tags
            Text(
                text = "Flavor notes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlavorDescriptor.entries.forEach { descriptor ->
                    val selected = descriptor in selectedDescriptors
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDescriptors = if (selected) {
                                selectedDescriptors - descriptor
                            } else {
                                selectedDescriptors + descriptor
                            }
                        },
                        label = {
                            Text("${descriptor.emoji} ${descriptor.displayName}")
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = {
                    Text("Add notes...")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            FilledTonalButton(
                onClick = {
                    onSave(
                        rating,
                        selectedDescriptors.map { it.displayName },
                        notes,
                    )
                },
                enabled = rating > 0f,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("Save Rating")
            }
        }
    }
}

@Composable
private fun HalfStarRatingRow(
    rating: Float,
    onRatingChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        for (i in 1..5) {
            var starWidthPx by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .onSizeChanged { starWidthPx = it.width.toFloat() }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* handled by pointerInput below */ },
            ) {
                // Detect tap on left vs right half
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // Use the center of the click area as a rough heuristic:
                            // full star by default; we split via two overlay boxes below
                        },
                )

                // Left half — half star
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 36.dp)
                        .align(Alignment.CenterStart)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRatingChanged(i - 0.5f) },
                )

                // Right half — full star
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 36.dp)
                        .align(Alignment.CenterEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRatingChanged(i.toFloat()) },
                )

                // Draw the appropriate star icon
                val icon = when {
                    rating >= i -> Icons.Filled.Star
                    rating >= i - 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                    else -> Icons.Outlined.Star
                }
                val tint = if (rating >= i - 0.5f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Icon(
                    imageVector = icon,
                    contentDescription = "Star $i",
                    tint = tint,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (rating > 0f) {
            Text(
                text = if (rating % 1f == 0f) {
                    "${rating.toInt()}.0"
                } else {
                    "%.1f".format(rating)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
