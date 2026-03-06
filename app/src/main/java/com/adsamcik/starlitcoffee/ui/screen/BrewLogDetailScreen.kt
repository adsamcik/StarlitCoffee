package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.ui.component.DetailRow
import com.adsamcik.starlitcoffee.ui.util.displayName
import com.adsamcik.starlitcoffee.ui.util.emoji
import com.adsamcik.starlitcoffee.ui.component.FlavorTagPicker
import com.adsamcik.starlitcoffee.ui.component.HalfStarRatingRow
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BrewLogDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewLogDetailScreen(
    navController: NavHostController,
    brewViewModel: BrewViewModel,
    logId: Long,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    var log by remember { mutableStateOf<BrewLogEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Editable state
    var rating by remember { mutableFloatStateOf(0f) }
    var selectedDescriptors by remember { mutableStateOf(emptySet<FlavorDescriptor>()) }
    var notes by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Track whether user has made changes
    var initialRating by remember { mutableFloatStateOf(0f) }
    var initialDescriptors by remember { mutableStateOf(emptySet<FlavorDescriptor>()) }
    var initialNotes by remember { mutableStateOf("") }

    val hasChanges = rating != initialRating ||
        selectedDescriptors != initialDescriptors ||
        notes != initialNotes

    // Load log entity
    LaunchedEffect(logId) {
        val entity = brewViewModel.getBrewLogById(logId)
        log = entity
        if (entity != null) {
            rating = entity.rating ?: 0f
            initialRating = entity.rating ?: 0f
            notes = entity.freeformNotes ?: ""
            initialNotes = entity.freeformNotes ?: ""
        }
        isLoading = false
    }

    // Load flavor tags
    val flavorTags by brewViewModel.getFlavorTagsForLog(logId).collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )

    // Sync initial flavor tags once loaded
    LaunchedEffect(flavorTags) {
        if (flavorTags.isNotEmpty() && initialDescriptors.isEmpty()) {
            val descriptors = flavorTags.mapNotNull { tag ->
                FlavorDescriptor.entries.find { it.displayName == tag.descriptor }
            }.toSet()
            selectedDescriptors = descriptors
            initialDescriptors = descriptors
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete brew log?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    log?.let { brewViewModel.deleteBrewLog(it) }
                    showDeleteDialog = false
                    navController.popBackStack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brew Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val entity = log
        if (entity == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Brew log not found", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // --- Brew Info ---
            Text(
                text = entity.method.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(start = 8.dp, top = 8.dp)
                    .semantics { heading() },
            )
            Text(
                text = dateFormat.format(Date(entity.createdAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )

            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    DetailRow("Coffee", "${"%.1f".format(entity.doseG)}g")
                    DetailRow("Water", "${"%.0f".format(entity.waterG)}g")
                    DetailRow("Ratio", "1:${"%.1f".format(entity.ratio)}")

                    if (entity.coffeeBagId != null) {
                        val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
                        val bagName = bags.find { it.id == entity.coffeeBagId }?.let { bag ->
                            bag.name + (bag.roaster?.let { " ($it)" } ?: "")
                        }
                        if (bagName != null) {
                            DetailRow("Coffee bag", bagName)
                        }
                    }

                    if (entity.brewTimeSeconds != null && entity.brewTimeSeconds > 0) {
                        val min = entity.brewTimeSeconds / 60
                        val sec = entity.brewTimeSeconds % 60
                        DetailRow("Brew time", "%d:%02d".format(min, sec))
                    }

                    if (entity.grindSetting != null) {
                        DetailRow("Grind", entity.grindSetting)
                    }

                    if (entity.filterType != null) {
                        DetailRow("Filter", entity.filterType)
                    }

                    if (entity.tasteFeedback != null) {
                        val feedback = try {
                            TasteFeedbackModel.valueOf(entity.tasteFeedback)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse taste feedback", e)
                            null
                        }
                        if (feedback != null) {
                            DetailRow("Taste", "${feedback.emoji()} ${feedback.displayName()}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Rating ---
            Text(
                text = "Rating",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(start = 8.dp, bottom = 8.dp)
                    .semantics { heading() },
            )

            HalfStarRatingRow(
                rating = rating,
                onRatingChange = { rating = it },
                modifier = Modifier.padding(start = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Flavor Tags ---
            Text(
                text = "Flavor notes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            FlavorTagPicker(
                selectedTags = selectedDescriptors,
                onTagToggle = { descriptor ->
                    selectedDescriptors = if (descriptor in selectedDescriptors) {
                        selectedDescriptors - descriptor
                    } else {
                        selectedDescriptors + descriptor
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Notes ---
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Add notes…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Save ---
            if (hasChanges) {
                FilledTonalButton(
                    onClick = {
                        brewViewModel.updateBrewLogFeedback(
                            logId = logId,
                            rating = rating.takeIf { it > 0f },
                            notes = notes,
                            tasteFeedback = entity.tasteFeedback,
                            descriptors = selectedDescriptors.map { it.displayName },
                        )
                        // Update local initial state to reflect saved state
                        initialRating = rating
                        initialDescriptors = selectedDescriptors
                        initialNotes = notes
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("Save Changes")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
