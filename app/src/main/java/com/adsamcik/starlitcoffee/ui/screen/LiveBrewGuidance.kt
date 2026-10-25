// This Compose file is named for its main composable, with a small selection value beside it.
@file:Suppress("MatchingDeclarationName")

package com.adsamcik.starlitcoffee.ui.screen

import androidx.annotation.StringRes
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
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
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

/** Selects the current operation, with familiar basic-timer advice available in Help. */
internal fun selectLiveBrewGuidance(
    resolution: DurableBrewSessionGuidanceResolution,
    equipmentProfileId: String? = null,
    minimal: Boolean = false,
): LiveBrewGuidanceSelection {
    // Basic timers keep familiar handling advice in Help. Preserve unfamiliar warnings
    // and every structured stage-safety message; neither is classified as routine here.
    val helpOnlyIds = when {
        !minimal -> emptySet()
        equipmentProfileId == PulsarLearnGuideCatalog.profileId.value ->
            setOf("pulsar_hot_liquid_safety", "pulsar_overflow_safety")
        equipmentProfileId == "cold_immersion_generic" && resolution.stageSafetyMessages.any {
            it.code == "food_refrigerate_4c_during_steep"
        } -> setOf("cold_immersion_generic_global_safety")
        else -> emptySet()
    }
    val illustratedContentId = (resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved)
        ?.targetContentId
    val routineStages = resolution.routineContent.filter { it.placement == BuiltInGuidancePlacement.LIVE_STAGE }
    val primaryCandidate = routineStages.firstOrNull { it.id == illustratedContentId }
        ?: routineStages.firstOrNull()
        ?: resolution.criticalContent.firstOrNull { it.placement == BuiltInGuidancePlacement.LIVE_STAGE }
    val content = (resolution.criticalContent + resolution.routineContent)
        .filterNot { it.id.value in helpOnlyIds }.distinctBy { it.id }
    val primary = primaryCandidate?.let { candidate -> content.firstOrNull { it.id == candidate.id } }
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
    minimal: Boolean = false,
    showSupportingCopy: Boolean = true,
) {
    val selection = selectLiveBrewGuidance(resolution, equipmentProfileId, minimal)
    val primary = selection.primary
    val showInstruction = showSupportingCopy && (!minimal || primary?.safetyCritical == true)
    val showAvailability = showSupportingCopy && !minimal
    val primaryTarget = primary?.target?.takeIf(String::isNotBlank)
    val approvedVisual = resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (targetCue != null) {
                    ReferenceCueRow(targetCue, prominent = true, compact = true)
                } else if (primaryTarget == null && showInstruction) {
                    primary?.instruction?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (primaryTarget != null) {
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
        if (showAvailability && primary == null && resolution.availability !is DurableBrewGuidanceAvailability.Available) {
            Text(
                stringResource(R.string.msg_brew_guidance_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        primary?.let { content ->
            if (showInstruction && (primaryTarget != null || targetCue != null)) {
                val instruction = when {
                    hasPulsarWaterHeightTarget(content.id, equipmentProfileId) ->
                        stringResource(R.string.msg_brew_pulsar_main_pour)
                    equipmentProfileId == PulsarLearnGuideCatalog.profileId.value &&
                        content.id.value == "pulsar_manual_brew" -> stringResource(R.string.msg_brew_pulsar_basic_pour)
                    else -> content.instruction
                }
                instruction.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                // A structured target already preserves its quantity role, reference and qualifier.
            }
            when {
                hasPulsarWaterHeightTarget(content.id, equipmentProfileId) ->
                    PulsarWaterHeightTarget(Modifier.align(Alignment.CenterHorizontally))
                approvedVisual != null && approvedVisual.targetContentId == content.id && content.altText.isNotBlank() -> {
                    ApprovedInstructionAssetImage(
                        asset = approvedVisual.asset,
                        contentDescription = approvedVisual.altText ?: content.altText,
                        modifier = Modifier.widthIn(max = 260.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        if (showSupportingCopy) LiveBrewSafetyCopy(selection, equipmentProfileId)
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
                modifier = Modifier.size(16.dp),
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

/** Reviewed conceptual water/bed artwork; the quantitative target remains accessible text. */
@Composable
private fun PulsarWaterHeightTarget(modifier: Modifier = Modifier) {
    val caption = stringResource(R.string.label_brew_water_height_target)
    val description = stringResource(R.string.cd_brew_water_height_target)
    Column(
        modifier = modifier.widthIn(max = 340.dp).fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BuiltInInstructionAssetCatalog.catalog.find(
                InstructionAssetId("instruction_scene_pulsar_water_height_default"),
            )?.takeIf { it.review.isApproved }?.let { asset ->
                ApprovedInstructionAssetImage(
                    asset = asset,
                    contentDescription = description,
                    modifier = Modifier.weight(1.2f).height(108.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.label_brew_water_height_value), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Normal)
                Text(stringResource(R.string.label_brew_water_height_reference), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
