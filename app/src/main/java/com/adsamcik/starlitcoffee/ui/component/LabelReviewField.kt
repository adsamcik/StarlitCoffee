package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence

/** A stable field summary that opens its existing editor and source in place. */
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
    LaunchedEffect(pendingSuggestion, requestedResolution) {
        val requestedCandidate = requestedResolution ?: return@LaunchedEffect
        if (pendingSuggestion == null) {
            // The callback already confirmed the choice; typed values may have a localized display.
            correctionAnnouncement = "$label: ${value.ifBlank { notSetLabel }}"
            if (enabled) resolutionFocus.requestFocus()
            requestedResolution = null
        } else if (pendingSuggestion != requestedCandidate) {
            // A replacement candidate is a different decision, not a successful stale acceptance.
            requestedResolution = null
        }
    }
    fun expectResolution() {
        correctionAnnouncement = null
        requestedResolution = pendingSuggestion
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("label-review-$fieldName")) {
        if (alwaysEditable) {
            editor()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showSourceToggle || sourceRemainsAvailable) {
                    TextButton(
                        onClick = { onExpandedChange(!expanded) },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = if (reviewNeeded)
                            MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary),
                        modifier = Modifier.heightIn(min = 48.dp).focusRequester(resolutionFocus)
                            .focusProperties { canFocus = enabled }
                            .background(if (reviewNeeded) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                                MaterialTheme.shapes.small)
                            .testTag("label-review-focus-$fieldName"),
                    ) {
                        if (reviewNeeded) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp).size(20.dp))
                        }
                        Text(
                            stringResource(if (reviewNeeded) R.string.label_review_check else R.string.label_review_source),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }
        } else {
            val editLabel = stringResource(R.string.action_review_edit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .focusRequester(resolutionFocus)
                    .focusProperties { canFocus = enabled }
                    .testTag("label-review-focus-$fieldName")
                    .background(if (reviewNeeded) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        MaterialTheme.shapes.small)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = editLabel) {
                        onExpandedChange(!expanded)
                    }
                    .padding(horizontal = if (reviewNeeded) 8.dp else 0.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (reviewNeeded)
                        MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        value.ifBlank { notSetLabel },
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            reviewNeeded -> MaterialTheme.colorScheme.onTertiaryContainer
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (reviewNeeded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                stringResource(R.string.label_review_check),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        error?.takeIf(String::isNotBlank)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("label-review-editor-$fieldName"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!alwaysEditable) editor()
                if (pendingSuggestion != null) {
                    Text(
                        stringResource(R.string.msg_review_check_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The accepted value stays in the editor above; this reading is still a choice.
                    Text(pendingSuggestion, style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                sourceEvidence?.let {
                    LabelEvidencePreview(evidence = it, capturedPhotoUris = capturedPhotoUris)
                }
                val missingCorrectionSource = alwaysEditable && correctionAnnouncement != null
                val needsSourceMessage = pendingSuggestion != null || missingCorrectionSource
                if (sourceEvidence == null && needsSourceMessage) {
                    Text(
                        stringResource(R.string.msg_review_source_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                clearAction?.let { clear ->
                    TextButton(onClick = clear, enabled = enabled) {
                        Text(stringResource(if (value.isBlank()) R.string.action_review_leave_blank else R.string.action_clear))
                    }
                }
            }
        }
        correctionAnnouncement?.let { message ->
            // Keep one stable announcement, separate from subsequent recognition/value updates.
            Spacer(Modifier.size(1.dp).testTag("label-review-correction-$fieldName").semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            })
        }
    }
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
