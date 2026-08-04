package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.guidance.BrewingTerminologyReference
import com.adsamcik.starlitcoffee.ui.guidance.BrewingTerminologyUiCopy
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent

/**
 * Keeps ordinary live guidance safety-first, but lets an approved stage visual
 * introduce the exact action it depicts.
 *
 * Critical catalogue content is always retained. Only routine content is
 * deduplicated after the illustrated primary item has been selected, so a
 * safety warning cannot disappear because of the visual presentation order.
 */
internal fun distinctTerminologyReferences(
    content: List<ResolvedBrewGuidanceContent>,
): List<BrewingTerminologyReference> = buildList {
    val seenConceptIds = mutableSetOf<String>()
    content.forEach { item ->
        item.terminologyReferences.forEach { reference ->
            if (seenConceptIds.add(reference.conceptId)) add(reference)
        }
    }
}

internal fun orderedLiveGuidanceContent(
    routineContent: List<ResolvedBrewGuidanceContent>,
    criticalContent: List<ResolvedBrewGuidanceContent>,
    illustratedContentId: StageContentId?,
): List<ResolvedBrewGuidanceContent> {
    val safetyFirstContent = (criticalContent + routineContent)
        .distinctBy(ResolvedBrewGuidanceContent::id)
    val illustratedPrimary = illustratedContentId?.let { contentId ->
        routineContent.firstOrNull { content ->
            content.id == contentId &&
                content.placement == BuiltInGuidancePlacement.LIVE_STAGE
        }
    } ?: return safetyFirstContent

    return buildList {
        add(illustratedPrimary)
        addAll(criticalContent)
        val renderedRoutineContentIds = buildSet {
            add(illustratedPrimary.id)
            addAll(criticalContent.map(ResolvedBrewGuidanceContent::id))
        }
        routineContent.forEach { content ->
            if (content.id !in renderedRoutineContentIds) add(content)
        }
    }
}

/**
 * Renders the policy-selected shared guidance beside a live durable session.
 *
 * This is deliberately a presentation-only control: changing the density
 * never modifies the recipe, plan, timer, safety messages, or persisted stage
 * state. Structured stage safety is rendered separately by [BrewSessionScreen]
 * so unavailable content can never hide a safety warning.
 */
@Composable
fun BrewSessionGuidancePanel(
    resolution: DurableBrewSessionGuidanceResolution,
    sessionOverride: GuidancePresentationLevel?,
    onSessionOverride: (GuidancePresentationLevel?) -> Unit,
    onRememberForBrewer: (GuidancePresentationLevel) -> Unit,
    terminologyUiCopy: BrewingTerminologyUiCopy? = null,
    showEnglishTerminology: Boolean = false,
    onShowEnglishTerminology: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val approvedVisual = resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved
    val visibleContent = remember(
        resolution.routineContent,
        resolution.criticalContent,
        approvedVisual?.asset?.contentId,
    ) {
        orderedLiveGuidanceContent(
            routineContent = resolution.routineContent,
            criticalContent = resolution.criticalContent,
            illustratedContentId = approvedVisual?.asset?.contentId,
        )
    }
    val shouldRender = resolution.policy != null ||
        visibleContent.isNotEmpty() ||
        approvedVisual != null
    if (!shouldRender) return
    val illustratedAltText = approvedVisual?.asset?.contentId?.let { contentId ->
        visibleContent.firstOrNull { content -> content.id == contentId }?.altText
    }
    val terminologyReferences = remember(visibleContent) {
        distinctTerminologyReferences(visibleContent)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.label_guidance),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            // An approved visual introduces the active stage before its concise
            // instruction; unapproved art remains absent.
            if (approvedVisual != null && !illustratedAltText.isNullOrBlank()) {
                ApprovedInstructionAssetImage(
                    asset = approvedVisual.asset,
                    contentDescription = illustratedAltText,
                )
            }

            if (approvedVisual == null) {
                resolution.policy?.let { policy ->
                    GuidanceLevelControl(
                        selectedLevel = policy.level,
                        hasSessionOverride = sessionOverride != null,
                        onSessionOverride = onSessionOverride,
                        onRememberForBrewer = onRememberForBrewer,
                    )
                }
            }

            if (resolution.availability !is DurableBrewGuidanceAvailability.Available &&
                visibleContent.isEmpty()
            ) {
                Text(
                    text = stringResource(R.string.msg_brew_guidance_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Production/review state is intentionally not user-facing. Missing or
            // unapproved art fails closed while the localized instruction remains.
            visibleContent.forEach { content -> GuidanceContent(content) }
            if (terminologyUiCopy != null && terminologyReferences.isNotEmpty()) {
                TerminologyReferenceControl(
                    references = terminologyReferences,
                    uiCopy = terminologyUiCopy,
                    expanded = showEnglishTerminology,
                    onExpandedChange = onShowEnglishTerminology,
                )
            }
            if (approvedVisual != null) {
                resolution.policy?.let { policy ->
                    GuidanceLevelControl(
                        selectedLevel = policy.level,
                        hasSessionOverride = sessionOverride != null,
                        onSessionOverride = onSessionOverride,
                        onRememberForBrewer = onRememberForBrewer,
                    )
                }
            }

        }
    }
}

@Composable
private fun TerminologyReferenceControl(
    references: List<BrewingTerminologyReference>,
    uiCopy: BrewingTerminologyUiCopy,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    TextButton(onClick = { onExpandedChange(!expanded) }) {
        Text(
            text = if (expanded) uiCopy.hideEnglishTerms else uiCopy.showEnglishTerms,
        )
    }
    if (expanded) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = uiCopy.heading,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { heading() },
                )
                references.forEach { reference ->
                    Text(
                        text = "${reference.preferredLocal} — ${reference.canonicalEnglish}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidanceLevelControl(
    selectedLevel: GuidancePresentationLevel,
    hasSessionOverride: Boolean,
    onSessionOverride: (GuidancePresentationLevel?) -> Unit,
    onRememberForBrewer: (GuidancePresentationLevel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                text = stringResource(
                    R.string.format_brew_guidance_level,
                    selectedLevel.label(),
                ),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            GuidancePresentationLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label()) },
                    onClick = {
                        onSessionOverride(level)
                        expanded = false
                    },
                )
            }
        }
    }
    if (hasSessionOverride) {
        TextButton(onClick = { onSessionOverride(null) }) {
            Text(stringResource(R.string.action_use_saved_guidance))
        }
        TextButton(onClick = { onRememberForBrewer(selectedLevel) }) {
            Text(stringResource(R.string.action_remember_guidance_for_brewer))
        }
    }
}

@Composable
private fun GuidanceContent(content: ResolvedBrewGuidanceContent) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content.instruction.takeIf(String::isNotBlank)?.let { instruction ->
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        content.target?.let { target ->
            GuidanceDetail(target)
        }
        content.completionCue?.let { cue ->
            GuidanceDetail(cue)
        }
        content.explanation?.let { explanation ->
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content.tip?.let { tip ->
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content.nextAction?.let { nextAction ->
            GuidanceDetail(nextAction)
        }
        content.controlRequirements.forEach { cue ->
            GuidanceDetail(cue.localizedLabel())
        }
        content.warning?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = if (content.safetyCritical) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        content.utilities.forEach { utility ->
            GuidanceDetail(utility.localizedLabel())
        }
    }
}

@Composable
private fun GuidanceDetail(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GuidancePresentationLevel.label(): String = stringResource(
    when (this) {
        GuidancePresentationLevel.FULL -> R.string.guidance_level_full
        GuidancePresentationLevel.CONCISE -> R.string.guidance_level_concise
        GuidancePresentationLevel.FOCUSED -> R.string.guidance_level_focused
        GuidancePresentationLevel.UTILITIES_ONLY -> R.string.guidance_level_utilities
        GuidancePresentationLevel.CUSTOM -> R.string.guidance_level_custom
    },
)
