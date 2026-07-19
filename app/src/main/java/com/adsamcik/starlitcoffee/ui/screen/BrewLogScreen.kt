package com.adsamcik.starlitcoffee.ui.screen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewRating
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor
import com.adsamcik.starlitcoffee.data.model.TasteFeedback as TasteFeedbackModel
import com.adsamcik.starlitcoffee.ui.component.ChipEmphasis
import com.adsamcik.starlitcoffee.ui.component.DecafFilter
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.ui.component.InsightChip
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.BrewRatingBadge
import com.adsamcik.starlitcoffee.ui.component.SwipeToDismissCard
import com.adsamcik.starlitcoffee.ui.component.normalizedForCounts
import com.adsamcik.starlitcoffee.ui.adaptive.LocalWindowWidthClass
import com.adsamcik.starlitcoffee.ui.component.iconForMethod
import com.adsamcik.starlitcoffee.ui.util.emoji
import com.adsamcik.starlitcoffee.ui.util.displayNameRes
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewLogFeedbackViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewLogFeedbackViewModelFactory
import com.adsamcik.starlitcoffee.viewmodel.BrewLogListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BrewLogScreen"

// List-detail pane split used on Expanded windows: the log list keeps a compact
// master column while the selected entry gets the larger detail pane.
private const val BrewLogListPaneWeight = 0.42f
private const val BrewLogDetailPaneWeight = 0.58f

internal data class BrewLogSelectionState(
    val selectedLogId: Long? = null,
    val pendingLogId: Long? = null,
) {
    val isSavingBeforeSelection: Boolean get() = pendingLogId != null
}

internal fun requestBrewLogSelection(
    state: BrewLogSelectionState,
    requestedLogId: Long,
): BrewLogSelectionState = when {
    state.pendingLogId != null || state.selectedLogId == requestedLogId -> state
    state.selectedLogId == null -> state.copy(selectedLogId = requestedLogId)
    else -> state.copy(pendingLogId = requestedLogId)
}

internal fun completeBrewLogSelection(
    state: BrewLogSelectionState,
    saveSucceeded: Boolean,
): BrewLogSelectionState {
    val pendingLogId = state.pendingLogId ?: return state
    return if (saveSucceeded) {
        BrewLogSelectionState(selectedLogId = pendingLogId)
    } else {
        state.copy(pendingLogId = null)
    }
}

internal fun normalizeBrewLogDecafFilter(
    selected: DecafFilter,
    regularCount: Int,
    decafCount: Int,
): DecafFilter = selected.normalizedForCounts(regularCount, decafCount)

@Composable
fun BrewLogScreen(
    brewViewModel: BrewViewModel,
    operationViewModel: BrewLogListViewModel,
    feedbackViewModelFactory: BrewLogFeedbackViewModelFactory,
    onNavigateToDetail: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
){
    val logs by brewViewModel.brewLogs.collectAsStateWithLifecycle()
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val flavorTags by brewViewModel.flavorTags.collectAsStateWithLifecycle()
    val deleteState by operationViewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    val tagsByLog = remember(flavorTags) {
        flavorTags.groupBy { it.brewLogId }
    }

    var decafFilter by remember { mutableStateOf(DecafFilter.ALL) }
    val decafCounts = remember(logs) {
        mapOf(
            DecafFilter.ALL to logs.size,
            DecafFilter.REGULAR to logs.count { !it.isDecaf },
            DecafFilter.DECAF to logs.count { it.isDecaf },
        )
    }
    val effectiveDecafFilter = normalizeBrewLogDecafFilter(
        selected = decafFilter,
        regularCount = decafCounts[DecafFilter.REGULAR] ?: 0,
        decafCount = decafCounts[DecafFilter.DECAF] ?: 0,
    )
    val showDecafFilter = (decafCounts[DecafFilter.DECAF] ?: 0) > 0 &&
        (decafCounts[DecafFilter.REGULAR] ?: 0) > 0
    val filteredLogs = remember(logs, effectiveDecafFilter) {
        logs.filter { effectiveDecafFilter.matches(it.isDecaf) }
    }

    var selectedLogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingSelectionLogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = pendingDeleteId?.let { id -> logs.find { it.id == id } }
    val isDeleting = deleteState.deletingLogId != null
    val isSavingBeforeSelection = pendingSelectionLogId != null
    // Expanded windows (large tablets, desktop) show the log list and the
    // selected entry side by side; compact / medium keep the navigate-to-detail
    // flow. The detail route still exists for notification deep links.
    val listDetail = LocalWindowWidthClass.current.isExpanded

    LaunchedEffect(effectiveDecafFilter) {
        if (decafFilter != effectiveDecafFilter) decafFilter = effectiveDecafFilter
    }

    val logList: @Composable (Modifier, (Long) -> Unit) -> Unit = { listModifier, onTapLog ->
        LazyColumn(
            modifier = listModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            if (showDecafFilter) {
                item {
                    com.adsamcik.starlitcoffee.ui.component.DecafFilterChipRow(
                        selected = effectiveDecafFilter,
                        counts = decafCounts,
                        onSelected = { decafFilter = it },
                        enabled = !isSavingBeforeSelection,
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
                    selected = listDetail && log.id == selectedLogId,
                    enabled = !listDetail || !isSavingBeforeSelection,
                    onTap = { onTapLog(log.id) },
                    onDelete = {
                        if (!isDeleting && !isSavingBeforeSelection) pendingDeleteId = log.id
                    },
                )
            }
        }
    }

    LaunchedEffect(deleteState.deletedLogId) {
        val deletedId = deleteState.deletedLogId ?: return@LaunchedEffect
        if (selectedLogId == deletedId) selectedLogId = null
        if (pendingSelectionLogId == deletedId) pendingSelectionLogId = null
        pendingDeleteId = null
        operationViewModel.consumeResult()
    }

    BackHandler(enabled = isDeleting) {}

    pendingDelete?.let { log ->
        DestructiveActionDialog(
            titleRes = R.string.dialog_delete_brew_title,
            confirmLabelRes = R.string.action_delete,
            messageRes = if (deleteState.failedLogId == log.id) {
                R.string.msg_could_not_delete
            } else {
                R.string.dialog_delete_brew_message
            },
            enabled = !isDeleting,
            onConfirm = {
                operationViewModel.delete(log)
            },
            onDismiss = {
                pendingDeleteId = null
                operationViewModel.consumeResult()
            },
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Horizontal padding aligns the title with the 16dp-inset list/cards
            // below (there is no back button on the Log tab to inset it).
            ScreenTopBar(
                title = stringResource(R.string.screen_brew_log_title),
                onBack = onBack?.let { back -> { if (!isDeleting && !isSavingBeforeSelection) back() } },
                backEnabled = !isDeleting && !isSavingBeforeSelection,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            when {
                logs.isEmpty() -> EmptyStateBox(
                    icon = Icons.Filled.History,
                    message = stringResource(R.string.msg_log_empty_title),
                    subtitle = stringResource(R.string.msg_log_empty_subtitle),
                    modifier = Modifier.fillMaxSize(),
                )

                listDetail -> Row(modifier = Modifier.fillMaxSize()) {
                    logList(
                        Modifier
                            .weight(BrewLogListPaneWeight)
                            .fillMaxHeight(),
                    ) { id ->
                        val next = requestBrewLogSelection(
                            state = BrewLogSelectionState(selectedLogId, pendingSelectionLogId),
                            requestedLogId = id,
                        )
                        selectedLogId = next.selectedLogId
                        pendingSelectionLogId = next.pendingLogId
                    }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(BrewLogDetailPaneWeight)
                            .fillMaxHeight(),
                    ) {
                        val sel = selectedLogId
                        if (sel == null) {
                            EmptyStateBox(
                                icon = Icons.Filled.History,
                                message = stringResource(R.string.msg_select_brew_log),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // Re-key on the selected id so the detail screen's
                            // internal remembered state resets when switching logs.
                            key(sel) {
                                val feedbackViewModel: BrewLogFeedbackViewModel = viewModel(
                                    key = "brew-log-feedback-$sel",
                                    factory = feedbackViewModelFactory,
                                )
                                BrewLogDetailScreen(
                                    brewViewModel = brewViewModel,
                                    feedbackViewModel = feedbackViewModel,
                                    logId = sel,
                                    onBack = { selectedLogId = null },
                                    selectionRequestLogId = pendingSelectionLogId,
                                    onSelectionReady = { requestedId ->
                                        if (pendingSelectionLogId == requestedId) {
                                            val next = completeBrewLogSelection(
                                                state = BrewLogSelectionState(
                                                    selectedLogId,
                                                    pendingSelectionLogId,
                                                ),
                                                saveSucceeded = true,
                                            )
                                            selectedLogId = next.selectedLogId
                                            pendingSelectionLogId = next.pendingLogId
                                        }
                                    },
                                    onSelectionSaveFailed = { requestedId ->
                                        if (pendingSelectionLogId == requestedId) {
                                            val next = completeBrewLogSelection(
                                                state = BrewLogSelectionState(
                                                    selectedLogId,
                                                    pendingSelectionLogId,
                                                ),
                                                saveSucceeded = false,
                                            )
                                            selectedLogId = next.selectedLogId
                                            pendingSelectionLogId = next.pendingLogId
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                else -> logList(Modifier.fillMaxSize(), onNavigateToDetail)
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
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val feedbackEmoji = log.tasteFeedback?.let { name ->
        try {
            TasteFeedbackModel.valueOf(name).emoji()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse taste feedback", e)
            null
        }
    }

    val rating = log.rating
    val isUnrated = rating == null
    val accent = ratingAccent(rating)
    val brewMethod = remember(log.method) {
        runCatching { BrewMethod.valueOf(log.method) }.getOrNull()
    }
    val methodLabel = brewMethod?.localizedDisplayName()
        ?: log.method.lowercase().replaceFirstChar { it.uppercase() }

    SwipeToDismissCard(onDismiss = onDelete) {
        ElevatedCard(
            onClick = onTap,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isUnrated) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.large,
                        )
                    } else {
                        Modifier
                    },
                )
                .testTag("brew_log_card_${log.id}"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrewLogCardHeader(
                    methodLabel = methodLabel,
                    methodIcon = brewMethod,
                    accent = accent,
                    bagName = bagName,
                    dateText = dateFormat.format(Date(log.createdAt)),
                    rating = rating,
                    feedbackEmoji = feedbackEmoji,
                )

                BrewLogStatsRow(log = log)

                AnimatedVisibility(
                    visible = isUnrated,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                ) {
                    FilledTonalButton(
                        onClick = onTap,
                        enabled = enabled,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_rate_brew))
                    }
                }

                if (flavorTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        flavorTags.forEach { tag ->
                            val descriptor = FlavorDescriptor.entries.find {
                                it.displayName == tag.descriptor
                            }
                            InsightChip(
                                label = descriptor?.let { stringResource(it.displayNameRes()) }
                                    ?: tag.descriptor,
                                emphasis = ChipEmphasis.WARNING,
                            )
                        }
                    }
                }

                if (!log.freeformNotes.isNullOrBlank()) {
                    Text(
                        text = log.freeformNotes.take(NOTES_PREVIEW_CHARS) +
                            if (log.freeformNotes.length > NOTES_PREVIEW_CHARS) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrewLogCardHeader(
    methodLabel: String,
    methodIcon: BrewMethod?,
    accent: RatingAccent,
    bagName: String?,
    dateText: String,
    rating: Float?,
    feedbackEmoji: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MethodAvatar(method = methodIcon, accent = accent)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = methodLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (bagName != null) {
                Text(
                    text = bagName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (rating != null && rating > 0f) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BrewRatingBadge(ratingValue = rating)
                if (feedbackEmoji != null) {
                    Text(
                        text = feedbackEmoji,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else if (feedbackEmoji != null) {
            Text(
                text = feedbackEmoji,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrewLogStatsRow(log: BrewLogEntity) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InsightChip(
            label = stringResource(
                R.string.format_dose_water_ratio,
                log.doseG,
                log.waterG,
                log.ratio,
            ),
            emphasis = ChipEmphasis.NEUTRAL,
        )
        if (log.filterType != null) {
            InsightChip(
                label = formatFilterType(log.filterType),
                emphasis = ChipEmphasis.NEUTRAL,
            )
        }
        if (log.isDecaf) {
            InsightChip(
                label = stringResource(R.string.label_decaf),
                emphasis = ChipEmphasis.NEUTRAL,
            )
        }
    }
}

@Composable
private fun MethodAvatar(method: BrewMethod?, accent: RatingAccent) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.container,
        contentColor = accent.onContainer,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = method?.let { iconForMethod(it) } ?: Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private data class RatingAccent(
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun ratingAccent(rating: Float?): RatingAccent {
    val scheme = MaterialTheme.colorScheme
    return when (BrewRating.fromStoredValue(rating)) {
        null -> RatingAccent(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        BrewRating.AWESOME -> RatingAccent(scheme.primaryContainer, scheme.onPrimaryContainer)
        BrewRating.GOOD -> RatingAccent(scheme.secondaryContainer, scheme.onSecondaryContainer)
        BrewRating.MEH -> RatingAccent(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        BrewRating.BAD -> RatingAccent(scheme.errorContainer, scheme.onErrorContainer)
    }
}

private const val NOTES_PREVIEW_CHARS = 80

@Composable
private fun formatFilterType(filterType: String): String =
    runCatching { com.adsamcik.starlitcoffee.data.model.FilterType.valueOf(filterType) }
        .getOrNull()
        ?.localizedDisplayName()
        ?: filterType
