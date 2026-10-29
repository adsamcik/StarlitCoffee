// Selection and projection are pure; the composable only lays out supplied content.
@file:Suppress("MatchingDeclarationName")

package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.guidance.hasSpentWaterReference
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewContent
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewWarning
import com.adsamcik.starlitcoffee.ui.brewing.approvedBrewStepIllustration
import com.adsamcik.starlitcoffee.ui.brewing.primaryBrewReferenceCue
import com.adsamcik.starlitcoffee.ui.brewing.toBrewStepContent
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
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

/** Only the current task gets a live target; the complete reference list remains in Help. */
internal fun liveHeroTarget(
    stage: CurrentBrewStagePresentation?,
    supplied: BrewStageReferenceCuePresentation?,
    equipmentProfileId: String? = null,
): BrewStageReferenceCuePresentation? {
    if (stage?.action == BrewStageAction.ADD_COFFEE) {
        return stage.referenceCues.filterIsInstance<BrewStageReferenceCuePresentation.Mass>()
            .firstOrNull { it.role == QuantityRole.DRY_COFFEE_DOSE } ?: supplied
    }
    val countdown = stage?.completion as? BrewStageCompletionPresentation.Countdown
    if (supplied is BrewStageReferenceCuePresentation.Time && countdown != null) {
        val sameDuration = supplied.minimumMillis == countdown.targetElapsedMillis &&
            supplied.maximumMillis == countdown.targetElapsedMillis
        if (sameDuration && supplied.reference == StageTimeReference.STAGE_DURATION &&
            supplied.qualifier == StageTargetQualifier.EXACT) return null
    }
    if (supplied is BrewStageReferenceCuePresentation.Mass && supplied.role == QuantityRole.BREW_WATER_INPUT &&
        hasSpentWaterReference(stage?.action, stage?.contentId, equipmentProfileId)) return null
    return supplied
}

/** Missing guidance never removes a supplied target or a required operational warning. */
internal fun projectLiveBrewContent(
    resolution: DurableBrewSessionGuidanceResolution,
    equipmentProfileId: String? = null,
    targetCue: BrewStageReferenceCuePresentation? = null,
    minimal: Boolean = false,
    showSupportingCopy: Boolean = true,
    requiredWarnings: List<String> = emptyList(),
): LiveBrewContent {
    val withoutGuidance = minimal || resolution.policy?.level == GuidancePresentationLevel.UTILITIES_ONLY
    val selection = selectLiveBrewGuidance(resolution, equipmentProfileId, withoutGuidance)
    val primary = selection.primary
    val metadata = primary?.let { liveSourceMetadata(it.id, equipmentProfileId) }
    val visual = (resolution.visualStatus as? DurableBrewGuidanceVisualStatus.Approved)
        ?.takeIf { !withoutGuidance && it.targetContentId == primary?.id }
    val step = primary?.toBrewStepContent(illustration = approvedBrewStepIllustration(
        visual?.asset, visual?.altText ?: primary.altText,
    ))
    val showInstruction = showSupportingCopy && (!withoutGuidance || primary?.safetyCritical == true)
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
        visualCue = metadata?.visualCue.takeUnless { withoutGuidance },
        instruction = step?.instruction.takeIf { showInstruction },
        instructionResource = metadata?.instruction.takeIf { showInstruction },
        warnings = (warnings + requiredForLive).distinct(),
    )
}

/** Operational warnings stay separate from optional instructions and artwork. */
@Composable
internal fun LiveBrewWarnings(warnings: List<LiveBrewWarning>) {
    if (warnings.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = if (warnings.any { it.critical }) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(warnings.map { it.summaryResource?.let { resource -> stringResource(resource) } ?: it.text }
            .distinct().joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
