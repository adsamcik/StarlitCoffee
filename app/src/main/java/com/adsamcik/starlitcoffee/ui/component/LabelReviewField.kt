package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.DateParser

/** A stable field header opens its editor and source in the same connected group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelReviewField(
    fieldName: String,
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    evidence: BagFieldEvidence? = null,
    capturedPhotoUris: String? = null,
    suggestion: String? = null,
    onAcceptSuggestion: (() -> Boolean)? = null,
    onKeepMine: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    error: String? = null,
    alwaysEditable: Boolean = false,
    needsReview: Boolean? = null,
    shape: Shape? = null,
    editor: @Composable () -> Unit,
) {
    val pendingSuggestion = suggestion?.takeIf(String::isNotBlank)
    val sourceEvidence = labelReviewSourceEvidence(fieldName, evidence, pendingSuggestion)
    val reviewNeeded = labelReviewNeedsCheck(value, evidence, pendingSuggestion, needsReview)
    val resolutionFocus = remember(fieldName) { FocusRequester() }
    var requestedResolution by remember(fieldName) { mutableStateOf<String?>(null) }
    var correctionAnnouncement by remember(fieldName) { mutableStateOf<String?>(null) }
    val notSetLabel = stringResource(R.string.label_review_not_set)
    val sourceRemainsAvailable = requestedResolution != null || correctionAnnouncement != null
    val showSourceToggle = evidence != null || pendingSuggestion != null || reviewNeeded
    val leavingPendingBlank = pendingSuggestion != null && value.isBlank()
    val clearAction = onClear.takeUnless { leavingPendingBlank }
    val containerColor = if (reviewNeeded) MaterialTheme.colorScheme.secondaryContainer else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.surface)
    }
    val contentColor = if (reviewNeeded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    LaunchedEffect(pendingSuggestion, requestedResolution) {
        val requestedCandidate = requestedResolution ?: return@LaunchedEffect
        if (pendingSuggestion == null) {
            // The callback confirmed the choice; typed values may have a localized display.
            correctionAnnouncement = "$label: ${value.ifBlank { notSetLabel }}"
            if (enabled) resolutionFocus.requestFocus()
            requestedResolution = null
        } else if (pendingSuggestion != requestedCandidate) {
            requestedResolution = null
        }
    }
    fun expectResolution() {
        correctionAnnouncement = null
        requestedResolution = pendingSuggestion
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(shape ?: MaterialTheme.shapes.large)
                .background(containerColor).testTag("label-review-$fieldName"),
        ) {
            if (alwaysEditable) {
                // Filled editors provide their own 16dp content inset inside this shared surface.
                editor()
                error?.takeIf(String::isNotBlank)?.let {
                    LabelReviewError(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                if (showSourceToggle || sourceRemainsAvailable) {
                    TextButton(
                        onClick = { onExpandedChange(!expanded) },
                        enabled = enabled,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                        modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)
                            .focusRequester(resolutionFocus).focusProperties { canFocus = enabled }
                            .testTag("label-review-focus-$fieldName"),
                    ) {
                        if (reviewNeeded) {
                            Icon(Icons.Outlined.WarningAmber, null, Modifier.padding(end = 6.dp).size(20.dp))
                        }
                        Text(stringResource(if (reviewNeeded) R.string.label_review_check else R.string.label_review_source))
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
                            Modifier.padding(start = 6.dp).size(20.dp))
                    }
                }
            } else {
                val editLabel = stringResource(R.string.action_review_edit)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .focusRequester(resolutionFocus).focusProperties { canFocus = enabled }
                        .testTag("label-review-focus-$fieldName")
                        .clickable(enabled = enabled, role = Role.Button, onClickLabel = editLabel) {
                            onExpandedChange(!expanded)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = if (expanded) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                                color = if (reviewNeeded) contentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (reviewNeeded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(16.dp))
                                    Text(stringResource(R.string.label_review_check), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (!expanded) {
                            Text(value.ifBlank { notSetLabel },
                                style = if (value.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                                color = if (value.isBlank() && !reviewNeeded) MaterialTheme.colorScheme.onSurfaceVariant else contentColor)
                        }
                    }
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(24.dp))
                }
                if (!expanded) {
                    error?.takeIf(String::isNotBlank)?.let {
                        LabelReviewError(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                    }
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .testTag("label-review-editor-$fieldName"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!alwaysEditable) {
                        error?.takeIf(String::isNotBlank)?.let { LabelReviewError(it) }
                        editor()
                    }
                    if (pendingSuggestion != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.label_review_reading), style = MaterialTheme.typography.labelMedium,
                                color = if (reviewNeeded) contentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(labelReviewReadingText(fieldName, pendingSuggestion), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    sourceEvidence?.let { LabelEvidencePreview(it, capturedPhotoUris) }
                    val missingCorrectionSource = alwaysEditable && correctionAnnouncement != null
                    val needsSourceMessage = pendingSuggestion != null || missingCorrectionSource
                    if (sourceEvidence == null && needsSourceMessage) {
                        Text(stringResource(R.string.msg_review_source_unavailable), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pendingSuggestion != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            onAcceptSuggestion?.let { accept ->
                                OutlinedButton(onClick = { if (accept()) expectResolution() }, enabled = enabled) {
                                    Text(stringResource(R.string.action_review_use_reading))
                                }
                            }
                            onKeepMine?.let { keep ->
                                TextButton(onClick = { keep(); expectResolution() }, enabled = enabled) {
                                    Text(stringResource(if (value.isBlank()) R.string.action_review_leave_blank else R.string.action_review_keep_mine))
                                }
                            }
                        }
                    }
                    clearAction?.let { clear ->
                        TextButton(onClick = clear, enabled = enabled, contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text(stringResource(if (value.isBlank()) R.string.action_review_leave_blank else R.string.action_clear))
                        }
                    }
                }
            }
            correctionAnnouncement?.let { message ->
                Spacer(Modifier.size(1.dp).testTag("label-review-correction-$fieldName").semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = message
                })
            }
        }
    }
}

@Composable
private fun LabelReviewError(error: String, modifier: Modifier = Modifier) {
    Text(error, modifier = modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun labelReviewReadingText(fieldName: String, rawValue: String): String = when (fieldName) {
    "isDecaf" -> when (rawValue) {
        "true" -> stringResource(R.string.label_decaf)
        "false" -> stringResource(R.string.label_review_not_decaf)
        else -> rawValue
    }
    "roastDate", "expiryDate" -> (rawValue.trim().toLongOrNull() ?: DateParser.parse(rawValue))
        ?.let(DateParser::format) ?: rawValue
    else -> rawValue
}

internal fun labelReviewNeedsCheck(
    value: String,
    evidence: BagFieldEvidence?,
    suggestion: String?,
    needsReview: Boolean?,
): Boolean = !suggestion.isNullOrBlank() || (needsReview ?: (
    value.isNotBlank() && evidence != null && evidence.value.isNotBlank() &&
        evidence.confidence in setOf(BagFieldConfidence.LOW, BagFieldConfidence.NEEDS_REVIEW)
    ))

/** A later reading's source cannot establish a retained, different pending candidate. */
internal fun labelReviewSourceEvidence(
    fieldName: String,
    evidence: BagFieldEvidence?,
    pendingSuggestion: String?,
): BagFieldEvidence? = if (pendingSuggestion.isNullOrBlank()) evidence else evidence?.takeIf {
    it.fieldName == fieldName && it.value == pendingSuggestion
}
