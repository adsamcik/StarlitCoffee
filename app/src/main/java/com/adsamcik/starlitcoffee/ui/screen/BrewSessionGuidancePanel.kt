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
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent

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
    modifier: Modifier = Modifier,
) {
    val visibleContent = remember(resolution.routineContent, resolution.criticalContent) {
        (resolution.criticalContent + resolution.routineContent).distinctBy(ResolvedBrewGuidanceContent::id)
    }
    val approvedVisual = resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved
    val shouldRender = resolution.policy != null ||
        visibleContent.isNotEmpty() ||
        approvedVisual != null
    if (!shouldRender) return

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

            resolution.policy?.let { policy ->
                GuidanceLevelControl(
                    selectedLevel = policy.level,
                    hasSessionOverride = sessionOverride != null,
                    onSessionOverride = onSessionOverride,
                    onRememberForBrewer = onRememberForBrewer,
                )
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

            // The visual reinforces the instruction and any safety copy instead
            // of making users interpret an unexplained image first.
            approvedVisual?.let { approved -> ApprovedInstructionAssetImage(approved.asset) }
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
            GuidanceDetail(cue.fallbackLabel)
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
            GuidanceDetail(utility.fallbackLabel)
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
