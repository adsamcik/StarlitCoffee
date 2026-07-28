package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.findApprovedAssetForContent

/**
 * A timer-free view of the same profile-scoped curriculum used by a durable
 * session. It does not create, resume, or mutate any brew state.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnBrewerScreen(
    resolution: LearnGuidanceCatalogResolution,
    hasPendingVisualAssets: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    instructionAssets: InstructionAssetCatalog? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.action_learn_this_brewer),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            resolution.policy?.let { policy ->
                item(key = "guidance_policy") {
                    Text(
                        text = stringResource(
                            R.string.format_brew_guidance_level,
                            policy.level.label(),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (resolution.availability !is LearnGuidanceCatalogAvailability.Available) {
                item(key = "guidance_unavailable") {
                    Text(
                        text = stringResource(R.string.msg_brew_guidance_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            itemsIndexed(
                items = resolution.content,
                key = { _, content -> content.id.value },
            ) { index, content ->
                LearnGuidanceCard(
                    content = content,
                    stepNumber = index + 1,
                    totalSteps = resolution.content.size,
                    visualAsset = instructionAssets?.findApprovedAssetForContent(content.id),
                )
            }
        }
    }
}

@Composable
private fun LearnGuidanceCard(
    content: ResolvedLearnGuidanceContent,
    stepNumber: Int,
    totalSteps: Int,
    visualAsset: InstructionAssetRecord?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.format_scan_stage_step,
                    stepNumber,
                    totalSteps,
                ),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content.instruction.takeIf(String::isNotBlank)?.let { instruction ->
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (content.safetyCritical) content.warning?.let { warning ->
                LearnGuidanceWarning(
                    warning = warning,
                    safetyCritical = true,
                )
            }
            visualAsset?.let { asset -> ApprovedInstructionAssetImage(asset) }
            if (!content.safetyCritical) content.warning?.let { warning ->
                LearnGuidanceWarning(warning = warning, safetyCritical = false)
            }
            content.target?.let { target ->
                LearnGuidanceDetail(target)
            }
            content.completionCue?.let { cue ->
                LearnGuidanceDetail(cue)
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
                LearnGuidanceDetail(nextAction)
            }
            content.controlRequirements.forEach { cue ->
                LearnGuidanceDetail(cue.fallbackLabel)
            }
            content.utilities.forEach { utility ->
                LearnGuidanceDetail(utility.fallbackLabel)
            }
        }
    }
}

@Composable
private fun LearnGuidanceDetail(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LearnGuidanceWarning(
    warning: String,
    safetyCritical: Boolean,
) {
    val containerColor = if (safetyCritical) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (safetyCritical) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.label_warning),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
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
