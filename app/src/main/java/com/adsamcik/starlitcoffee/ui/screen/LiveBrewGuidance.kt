// Selection and projection are pure; the composable only lays out supplied content.
@file:Suppress("MatchingDeclarationName")

package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewContent
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewWarning
import com.adsamcik.starlitcoffee.ui.brewing.approvedBrewStepIllustration
import com.adsamcik.starlitcoffee.ui.brewing.primaryBrewReferenceCue
import com.adsamcik.starlitcoffee.ui.brewing.toBrewStepContent
import com.adsamcik.starlitcoffee.ui.guidance.BrewLiveVisualCue
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.liveSourceMetadata
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation

internal data class LiveBrewGuidanceSelection(
    val primary: ResolvedBrewGuidanceContent?,
    val supporting: List<ResolvedBrewGuidanceContent>,
)

internal fun selectLiveBrewGuidance(
    resolution: DurableBrewSessionGuidanceResolution,
    equipmentProfileId: String? = null,
    minimal: Boolean = false,
): LiveBrewGuidanceSelection {
    val illustratedContentId = (resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved)?.targetContentId
    val routineStages = resolution.routineContent.filter { it.placement == BuiltInGuidancePlacement.LIVE_STAGE }
    val candidate = routineStages.firstOrNull { it.id == illustratedContentId } ?: routineStages.firstOrNull()
        ?: resolution.criticalContent.firstOrNull { it.placement == BuiltInGuidancePlacement.LIVE_STAGE }
    val content = (resolution.criticalContent + resolution.routineContent).distinctBy { it.id }
        .filterNot { item ->
            val metadata = liveSourceMetadata(item.id, equipmentProfileId, resolution.stageSafetyMessages)
            minimal && metadata.warningInHelpWhenMinimal && metadata.recognizesWarning(item.warning)
        }
    val primary = candidate?.let { selected -> content.firstOrNull { it.id == selected.id } }
    return LiveBrewGuidanceSelection(primary, content.filterNot { it.id == primary?.id })
}

internal fun hasPulsarWaterHeightTarget(contentId: StageContentId, equipmentProfileId: String?): Boolean =
    liveSourceMetadata(contentId, equipmentProfileId).focusOnTarget

internal fun primaryLiveBrewCue(cues: List<BrewStageReferenceCuePresentation>): BrewStageReferenceCuePresentation? =
    cues.primaryBrewReferenceCue()

/** Missing guidance never removes a supplied target or a required operational warning. */
internal fun projectLiveBrewContent(
    resolution: DurableBrewSessionGuidanceResolution,
    equipmentProfileId: String? = null,
    targetCue: BrewStageReferenceCuePresentation? = null,
    minimal: Boolean = false,
    showSupportingCopy: Boolean = true,
    requiredWarnings: List<String> = emptyList(),
): LiveBrewContent {
    val selection = selectLiveBrewGuidance(resolution, equipmentProfileId, minimal)
    val primary = selection.primary
    val metadata = primary?.let { liveSourceMetadata(it.id, equipmentProfileId) }
    val visual = (resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved)
        ?.takeIf { it.targetContentId == primary?.id }
    val step = primary?.toBrewStepContent(illustration = approvedBrewStepIllustration(
        visual?.asset, visual?.altText ?: primary.altText,
    ))
    val showInstruction = showSupportingCopy && (!minimal || primary?.safetyCritical == true)
    val warningContent = (listOfNotNull(primary) + selection.supporting).filterNot {
        !showSupportingCopy && liveSourceMetadata(it.id, equipmentProfileId).let { metadata ->
            metadata.warningInHelpWhenFocused && metadata.recognizesWarning(it.warning)
        }
    }
    val warnings = warningContent.flatMap { item ->
        val warning = item.warning?.takeIf(String::isNotBlank)
        val sourceMetadata = liveSourceMetadata(item.id, equipmentProfileId)
        val summary = sourceMetadata.warningSummary.takeIf { sourceMetadata.recognizesWarning(warning) }
        if (summary != null && warning != null) listOf(LiveBrewWarning(warning, summary, item.safetyCritical))
        else listOfNotNull(
            item.target?.takeIf { item.safetyCritical && item.id != primary?.id && it.isNotBlank() },
            item.instruction.takeIf { item.safetyCritical && item.id != primary?.id && it.isNotBlank() },
            warning,
        ).map { LiveBrewWarning(it, critical = item.safetyCritical) }
    }
    val knownHelpWarning = primary?.warning.takeIf {
        !showSupportingCopy && metadata?.warningInHelpWhenFocused == true && metadata.recognizesWarning(primary?.warning)
    }
    val requiredForLive = requiredWarnings.filterNot { warning ->
        knownHelpWarning?.lineSequence()?.any { it == warning } == true ||
            warningContent.any { item -> item.warning?.lineSequence()?.any { it == warning } == true }
    }.map { LiveBrewWarning(it, critical = true) }
    return LiveBrewContent(
        step = step,
        target = targetCue,
        visualCue = metadata?.visualCue,
        instruction = step?.instruction.takeIf { showInstruction },
        instructionResource = metadata?.instruction.takeIf { showInstruction },
        warnings = (warnings + requiredForLive).distinct(),
    )
}

@Composable
internal fun LiveBrewGuidance(
    resolution: DurableBrewSessionGuidanceResolution,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    equipmentProfileId: String? = null,
    targetCue: BrewStageReferenceCuePresentation? = null,
    minimal: Boolean = false,
    showSupportingCopy: Boolean = true,
    requiredWarnings: List<String> = emptyList(),
) {
    LiveBrewStep(
        content = projectLiveBrewContent(resolution, equipmentProfileId, targetCue, minimal,
            showSupportingCopy, requiredWarnings),
        onHelp = onHelp,
        modifier = modifier,
    )
}

/** Optional slots take no space when omitted. No source, recipe or brewer branching lives here. */
@Composable
internal fun LiveBrewStep(
    content: LiveBrewContent,
    onHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val instruction = content.instructionResource?.let { stringResource(it) } ?: content.instruction
    val target = content.step?.target
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (content.target != null || target != null || onHelp != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (content.target != null) ReferenceCueRow(content.target, prominent = true, compact = true)
                    else if (target != null) Text(target, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                if (onHelp != null) IconButton(onClick = onHelp, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.action_brew_help))
                }
            }
        }
        instruction?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val visualCue = content.visualCue
        val illustration = content.step?.illustration
        if (visualCue != null) LiveBrewVisualCue(visualCue, Modifier.align(Alignment.CenterHorizontally))
        else if (illustration != null) ApprovedInstructionAssetImage(
            asset = illustration.asset,
            contentDescription = illustration.description,
            modifier = Modifier.widthIn(max = 260.dp).align(Alignment.CenterHorizontally),
        )
        if (content.warnings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = if (content.warnings.any { it.critical }) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(content.warnings.map { it.summaryResource?.let { resource -> stringResource(resource) } ?: it.text }
                    .distinct().joinToString("\n"), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LiveBrewVisualCue(cue: BrewLiveVisualCue, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 340.dp).fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ApprovedInstructionAssetImage(cue.asset, stringResource(cue.description),
                modifier = Modifier.weight(1.2f).height(108.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(cue.label), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(cue.value), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(cue.reference), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
