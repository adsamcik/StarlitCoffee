package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetReviewStatus
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.findApprovedAssetForContent

/**
 * A timer-free view of the same profile-scoped curriculum used by a durable
 * session. It does not create, resume, or mutate any brew state.
 */
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            resolution.policy?.let { policy ->
                Text(
                    text = stringResource(
                        R.string.format_brew_guidance_level,
                        policy.level.label(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (resolution.availability !is LearnGuidanceCatalogAvailability.Available) {
                Text(
                    text = stringResource(R.string.msg_brew_guidance_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (hasPendingVisualAssets) {
                Text(
                    text = stringResource(R.string.msg_brew_guidance_asset_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            resolution.content.forEach { content ->
                val matchingVisualAssets = instructionAssets
                    ?.assets
                    ?.filter { asset -> asset.contentId == content.id }
                    .orEmpty()
                LearnGuidanceCard(
                    content = content,
                    visualAsset = instructionAssets?.findApprovedAssetForContent(content.id),
                    matchingVisualAssets = matchingVisualAssets,
                )
            }
        }
    }
}

@Composable
private fun LearnGuidanceCard(
    content: ResolvedLearnGuidanceContent,
    visualAsset: InstructionAssetRecord?,
    matchingVisualAssets: List<InstructionAssetRecord>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (content.safetyCritical) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = content.instruction,
                style = MaterialTheme.typography.bodyLarge,
            )
            visualAsset?.let { asset -> ApprovedInstructionAssetImage(asset) }
            LearnGuidanceVisualStatus(
                visualAsset = visualAsset,
                matchingVisualAssets = matchingVisualAssets,
            )
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
            content.warning?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (content.safetyCritical) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun LearnGuidanceVisualStatus(
    visualAsset: InstructionAssetRecord?,
    matchingVisualAssets: List<InstructionAssetRecord>,
) {
    if (visualAsset != null || matchingVisualAssets.isEmpty()) return

    val awaitingReview = matchingVisualAssets.any { asset ->
        asset.review.status == InstructionAssetReviewStatus.DRAFT ||
            asset.review.status == InstructionAssetReviewStatus.PENDING_REVIEW
    }
    val message = if (awaitingReview) {
        R.string.msg_brew_guidance_asset_pending
    } else {
        R.string.msg_brew_guidance_asset_unavailable
    }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodySmall,
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
