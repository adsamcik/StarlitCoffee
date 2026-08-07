package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.findApprovedAssetForContent

private val LearnStepContentMaxWidth = 720.dp

/**
 * Timer-free illustrated curriculum. It reads the same exact profile-scoped
 * guidance as durable sessions but never creates, resumes, or mutates a brew.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnBrewerScreen(
    title: String,
    resolution: LearnGuidanceCatalogResolution,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    instructionAssets: InstructionAssetCatalog? = null,
    isGuidancePreview: Boolean = false,
) {
    val steps = resolution.content
    var currentStepIndex by rememberSaveable(steps.firstOrNull()?.id?.value) {
        mutableIntStateOf(0)
    }
    val safeStepIndex = currentStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val currentStep = steps.getOrNull(safeStepIndex)
    var detailsExpanded by rememberSaveable(currentStep?.id?.value) {
        mutableStateOf(false)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
        bottomBar = {
            currentStep?.let {
                LearningStepControls(
                    stepIndex = safeStepIndex,
                    totalSteps = steps.size,
                    onPrevious = {
                        currentStepIndex = (safeStepIndex - 1).coerceAtLeast(0)
                    },
                    onNext = {
                        if (safeStepIndex == steps.lastIndex) {
                            onBack()
                        } else {
                            currentStepIndex = safeStepIndex + 1
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = LearnStepContentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isGuidancePreview) {
                    item(key = "guidance_preview_notice") {
                        GuidancePreviewNotice()
                    }
                }

                if (
                    resolution.availability !is LearnGuidanceCatalogAvailability.Available ||
                    currentStep == null
                ) {
                    item(key = "guidance_unavailable") {
                        Text(
                            text = stringResource(R.string.msg_brew_guidance_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    item(key = currentStep.id.value) {
                        LearningStepPage(
                            content = currentStep,
                            stepNumber = safeStepIndex + 1,
                            totalSteps = steps.size,
                            visualAsset = instructionAssets
                                ?.findApprovedAssetForContent(currentStep.id),
                            detailsExpanded = detailsExpanded,
                            onToggleDetails = { detailsExpanded = !detailsExpanded },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningStepPage(
    content: ResolvedLearnGuidanceContent,
    stepNumber: Int,
    totalSteps: Int,
    visualAsset: InstructionAssetRecord?,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        LinearProgressIndicator(
            progress = { stepNumber.toFloat() / totalSteps.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                visualAsset?.let { asset ->
                    ApprovedInstructionAssetImage(
                        asset = asset,
                        contentDescription = content.altText,
                    )
                }

                content.instruction.takeIf(String::isNotBlank)?.let { instruction ->
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                content.warning?.let { warning ->
                    LearnGuidanceWarning(
                        warning = warning,
                        safetyCritical = content.safetyCritical,
                    )
                }

                content.completionCue?.takeIf(String::isNotBlank)?.let { cue ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = cue,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                val details = content.additionalLearningDetails()
                if (details.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onToggleDetails,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (detailsExpanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(
                                if (detailsExpanded) {
                                    R.string.action_hide_details
                                } else {
                                    R.string.action_show_details
                                },
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    if (detailsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            details.forEach { detail ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = detail,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolvedLearnGuidanceContent.additionalLearningDetails(): List<String> {
    val details = mutableListOf<String>()
    target?.takeIf(String::isNotBlank)?.let(details::add)
    explanation?.takeIf(String::isNotBlank)?.let(details::add)
    tip?.takeIf(String::isNotBlank)?.let(details::add)
    nextAction?.takeIf(String::isNotBlank)?.let(details::add)
    controlRequirements.forEach { cue ->
        cue.localizedLabel().takeIf(String::isNotBlank)?.let(details::add)
    }
    utilities.forEach { utility ->
        utility.localizedLabel().takeIf(String::isNotBlank)?.let(details::add)
    }
    return details
}

@Composable
private fun LearningStepControls(
    stepIndex: Int,
    totalSteps: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = stepIndex > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_back))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (stepIndex == totalSteps - 1) {
                            R.string.action_finish
                        } else {
                            R.string.action_next
                        },
                    ),
                )
            }
        }
    }
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
        shape = MaterialTheme.shapes.large,
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
