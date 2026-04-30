package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.ui.component.DetailRow
import com.adsamcik.starlitcoffee.ui.component.shareBrewCard
import com.adsamcik.starlitcoffee.ui.util.displayNameRes
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
    brewViewModel: BrewViewModel,
    logId: Long,
    onBack: () -> Unit,
){
    val context = LocalContext.current
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    var log by remember { mutableStateOf<BrewLogEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showSavedConfirmation by remember { mutableStateOf(false) }

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

    // Persist any unsaved feedback before leaving the screen so users don't
    // lose ratings/notes when they navigate back without tapping Save.
    val saveAndExit: () -> Unit = saveAndExit@{
        val entity = log
        if (hasChanges && entity != null) {
            brewViewModel.updateBrewLogFeedback(
                logId = logId,
                rating = rating.takeIf { it > 0f },
                notes = notes,
                tasteFeedback = entity.tasteFeedback,
                descriptors = selectedDescriptors.map { it.displayName },
            )
        }
        onBack()
    }

    BackHandler(enabled = !isLoading) { saveAndExit() }

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
            title = { Text(stringResource(R.string.dialog_delete_brew_title)) },
            text = { Text(stringResource(R.string.dialog_delete_brew_message)) },
            confirmButton = {
                TextButton(onClick = {
                    log?.let { brewViewModel.deleteBrewLog(it) }
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_brew_details_title)) },
                navigationIcon = {
                    IconButton(onClick = saveAndExit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val entity = log ?: return@IconButton
                            val bagName = entity.coffeeBagId?.let { bagId ->
                                bags.find { it.id == bagId }?.let { bag ->
                                    bag.name + (bag.roaster?.let { " · $it" } ?: "")
                                }
                            }
                            val tags = flavorTags.map { it.descriptor }
                            shareBrewCard(context, entity, bagName, tags)
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.action_share_brew),
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.testTag("delete_brew_log_button")) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
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
                Text(stringResource(R.string.label_loading), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val entity = log
        if (entity == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.msg_brew_not_found), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_go_back))
                }
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
            val decafSuffix = stringResource(R.string.label_decaf_suffix)
            val unitGrams = stringResource(R.string.unit_grams)
            Text(
                text = buildString {
                    append(entity.method.lowercase().replaceFirstChar { it.uppercase() })
                    if (entity.isDecaf) append(decafSuffix)
                },
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
                    DetailRow(stringResource(R.string.label_coffee), "${"%.1f".format(entity.doseG)}$unitGrams")
                    DetailRow(stringResource(R.string.label_water), "${"%.0f".format(entity.waterG)}$unitGrams")

                    if (entity.coffeeBagId != null) {
                        val bagName = bags.find { it.id == entity.coffeeBagId }?.let { bag ->
                            bag.name + (bag.roaster?.let { " ($it)" } ?: "")
                        }
                        if (bagName != null) {
                            DetailRow(stringResource(R.string.label_coffee_bag), bagName)
                        }
                    }

                    if (entity.brewTimeSeconds != null && entity.brewTimeSeconds > 0) {
                        val min = entity.brewTimeSeconds / 60
                        val sec = entity.brewTimeSeconds % 60
                        DetailRow(stringResource(R.string.label_brew_time), "%d:%02d".format(min, sec))
                    }

                    if (entity.isDecaf) {
                        DetailRow(stringResource(R.string.label_coffee_type), stringResource(R.string.label_decaf))
                    }

                    if (entity.grindSetting != null) {
                        DetailRow(stringResource(R.string.label_grind), entity.grindSetting)
                    }

                    if (entity.filterType != null) {
                        val filterDisplayName = FilterType.entries
                            .find { it.name == entity.filterType }?.displayName
                            ?: entity.filterType
                        DetailRow(stringResource(R.string.label_filter), filterDisplayName)
                    }

                    if (entity.tasteFeedback != null) {
                        val feedback = try {
                            TasteFeedbackModel.valueOf(entity.tasteFeedback)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse taste feedback", e)
                            null
                        }
                        if (feedback != null) {
                            DetailRow(stringResource(R.string.label_taste), "${feedback.emoji()} ${stringResource(feedback.displayNameRes())}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Rating ---
            Text(
                text = stringResource(R.string.label_rating),
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
                text = stringResource(R.string.label_flavor_notes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
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
                text = stringResource(R.string.label_notes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(stringResource(R.string.hint_add_notes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("detail_notes_input"),
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
                        showSavedConfirmation = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("save_changes_button"),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }

            // Brief "Saved ✓" confirmation so user knows the save worked
            if (showSavedConfirmation && !hasChanges) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000L)
                    showSavedConfirmation = false
                }
                Text(
                    text = stringResource(R.string.action_saved),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
