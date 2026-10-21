// This Compose file is named for its main composable, with a small selection value beside it.
@file:Suppress("MatchingDeclarationName")

package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.Canvas
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.PulsarLearnGuideCatalog
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference

internal data class LiveBrewGuidanceSelection(
    val primary: ResolvedBrewGuidanceContent?,
    val supporting: List<ResolvedBrewGuidanceContent>,
)

/** Selects the current operation without filtering safety or operational content. */
internal fun selectLiveBrewGuidance(
    resolution: DurableBrewSessionGuidanceResolution,
): LiveBrewGuidanceSelection {
    val illustratedContentId = (resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved)
        ?.asset?.contentId
    val routineStages = resolution.routineContent.filter { it.placement == BuiltInGuidancePlacement.LIVE_STAGE }
    val primaryCandidate = routineStages.firstOrNull { it.id == illustratedContentId }
        ?: routineStages.firstOrNull()
        ?: resolution.criticalContent.firstOrNull { it.placement == BuiltInGuidancePlacement.LIVE_STAGE }
    val content = (resolution.criticalContent + resolution.routineContent).distinctBy { it.id }
    val primary = primaryCandidate?.let { candidate -> content.first { it.id == candidate.id } }
    return LiveBrewGuidanceSelection(
        primary = primary,
        supporting = content.filterNot { it.id == primary?.id },
    )
}

/** The quantitative diagram belongs only to this reviewed recipe's main pour. */
internal fun hasPulsarWaterHeightTarget(contentId: StageContentId, equipmentProfileId: String?): Boolean =
    equipmentProfileId == PulsarLearnGuideCatalog.profileId.value &&
        contentId.value in setOf("${PulsarLearnGuideCatalog.GUIDE_ID}_stage_06_instruction",
            "reviewed_pulsar_v1_open_and_pulse")

internal fun primaryLiveBrewCue(cues: List<BrewStageReferenceCuePresentation>): BrewStageReferenceCuePresentation? =
    cues.filterIsInstance<BrewStageReferenceCuePresentation.Mass>()
        .firstOrNull { it.reference == StageMassReference.BREW_CUMULATIVE }
        ?: cues.filterIsInstance<BrewStageReferenceCuePresentation.Mass>().firstOrNull()
        ?: cues.firstOrNull()

/** Only the known source warnings are condensed; unfamiliar warnings stay verbatim. */
@StringRes
internal fun compactLiveWarning(contentId: StageContentId, equipmentProfileId: String?): Int? {
    if (equipmentProfileId != PulsarLearnGuideCatalog.profileId.value) return null
    return when (contentId.value) {
        "pulsar_hot_liquid_safety" -> R.string.msg_brew_pulsar_hot_liquid
        "pulsar_overflow_safety" -> R.string.msg_brew_pulsar_overflow
        "reviewed_pulsar_v1_open_and_pulse" -> R.string.msg_brew_pulsar_pour_safety
        else -> null
    }
}

/** Compact live copy; the complete source explanations remain available through Help. */
@Composable
internal fun LiveBrewGuidance(
    resolution: DurableBrewSessionGuidanceResolution,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    equipmentProfileId: String? = null,
    targetCue: BrewStageReferenceCuePresentation? = null,
) {
    val selection = selectLiveBrewGuidance(resolution)
    val primary = selection.primary
    val primaryTarget = primary?.target?.takeIf(String::isNotBlank)
    val approvedAsset = (resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved)?.asset
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                if (targetCue != null) {
                    ReferenceCueRow(targetCue, prominent = true, compact = true)
                } else if (primaryTarget == null) {
                    primary?.instruction?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(
                        primaryTarget,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            IconButton(onClick = onHelp, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.action_brew_help))
            }
        }
        if (primary == null && resolution.availability !is DurableBrewGuidanceAvailability.Available) {
            Text(
                stringResource(R.string.msg_brew_guidance_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        primary?.let { content ->
            if (primaryTarget != null || targetCue != null) {
                val instruction = when {
                    hasPulsarWaterHeightTarget(content.id, equipmentProfileId) ->
                        stringResource(R.string.msg_brew_pulsar_main_pour)
                    equipmentProfileId == PulsarLearnGuideCatalog.profileId.value &&
                        content.id.value == "pulsar_manual_brew" -> stringResource(R.string.msg_brew_pulsar_basic_pour)
                    else -> content.instruction
                }
                instruction.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                // A structured target already preserves its quantity role, reference and qualifier.
            }
            when {
                hasPulsarWaterHeightTarget(content.id, equipmentProfileId) -> PulsarWaterHeightTarget()
                approvedAsset?.contentId == content.id && content.altText.isNotBlank() -> {
                    ApprovedInstructionAssetImage(
                        asset = approvedAsset,
                        contentDescription = content.altText,
                        modifier = Modifier.widthIn(max = 260.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        LiveBrewSafetyCopy(selection, equipmentProfileId)
    }
}

@Composable
private fun LiveBrewSafetyCopy(selection: LiveBrewGuidanceSelection, equipmentProfileId: String?) {
    val content = listOfNotNull(selection.primary) + selection.supporting
    val warnings = content.flatMap { item ->
        val warning = item.warning?.takeIf(String::isNotBlank)
        val summary = warning?.let { compactLiveWarning(item.id, equipmentProfileId) }
        if (summary != null) listOf(stringResource(summary))
        else listOfNotNull(
            item.target?.takeIf { item.safetyCritical && item.id != selection.primary?.id && it.isNotBlank() },
            item.instruction.takeIf { item.safetyCritical && item.id != selection.primary?.id && it.isNotBlank() },
            warning,
        )
    }.distinct()
    if (warnings.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (content.any { it.safetyCritical }) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                warnings.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** A waterline/bed measurement, without a substitute brewer illustration or sensor claim. */
@Composable
private fun PulsarWaterHeightTarget() {
    val caption = stringResource(R.string.label_brew_water_height_target)
    val description = stringResource(R.string.cd_brew_water_height_target)
    val water = MaterialTheme.colorScheme.primary
    val bed = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(caption, style = MaterialTheme.typography.labelLarge)
        Canvas(
            modifier = Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = description },
        ) {
            val left = size.width * 0.08f
            val right = size.width * 0.74f
            val waterline = size.height * 0.18f
            val bedTop = size.height * 0.66f
            val bedBottom = size.height * 0.92f
            val bracket = size.width * 0.86f
            val stroke = 2.dp.toPx()
            drawRect(water.copy(alpha = 0.14f), Offset(left, waterline), Size(right - left, bedTop - waterline))
            drawRect(bed.copy(alpha = 0.3f), Offset(left, bedTop), Size(right - left, bedBottom - bedTop))
            drawLine(water, Offset(left, waterline), Offset(right, waterline), stroke)
            drawLine(bed, Offset(left, bedTop), Offset(right, bedTop), stroke)
            drawLine(line, Offset(bracket, waterline), Offset(bracket, bedTop), stroke)
            val tick = 6.dp.toPx()
            listOf(waterline, bedTop).forEach { y ->
                drawLine(line, Offset(bracket - tick, y), Offset(bracket + tick, y), stroke)
            }
            drawLine(line, Offset(left, 0f), Offset(left, bedBottom), stroke)
            drawLine(line, Offset(right, 0f), Offset(right, bedBottom), stroke)
            drawLine(line, Offset(left, bedBottom), Offset(right, bedBottom), stroke)
        }
        Text(stringResource(R.string.msg_brew_pulsar_pause_high), style = MaterialTheme.typography.bodySmall)
    }
}
