package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.P1CompletionSemantics
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTemperatureTarget
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactLearnGuide
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactLearnStageFacts
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
    exactGuide: P1ExactLearnGuide? = null,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        containerColor = MaterialTheme.colorScheme.surface,
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
                    exactGuide?.let { guide ->
                        item(key = "exact_recipe_summary") {
                            ExactLearnRecipeOverview(guide)
                        }
                    }
                    item(key = "learning_step") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LearningStepProgress(
                                stepNumber = safeStepIndex + 1,
                                totalSteps = steps.size,
                            )
                            AnimatedContent(
                                targetState = safeStepIndex,
                                modifier = Modifier.fillMaxWidth(),
                                transitionSpec = {
                                    val direction = if (targetState > initialState) 1 else -1
                                    val enter = slideInHorizontally(
                                        animationSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    ) { fullWidth -> direction * fullWidth / 4 } +
                                        fadeIn(
                                            animationSpec = tween(
                                                durationMillis = 220,
                                                delayMillis = 60,
                                            ),
                                        )
                                    val exit = slideOutHorizontally(
                                        animationSpec = tween(
                                            durationMillis = 220,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    ) { fullWidth -> -direction * fullWidth / 6 } +
                                        fadeOut(animationSpec = tween(durationMillis = 160))
                                    enter.togetherWith(exit)
                                },
                                label = "learning_step_content_change",
                            ) { animatedStepIndex ->
                                val animatedStep = steps[animatedStepIndex]
                                LearningStepContent(
                                    content = animatedStep,
                                    exactFacts = exactGuide?.stageFactsByContentId?.get(animatedStep.id),
                                    visualAsset = instructionAssets
                                        ?.findApprovedAssetForContent(animatedStep.id),
                                    detailsExpanded = detailsExpanded &&
                                        animatedStepIndex == safeStepIndex,
                                    onToggleDetails = { detailsExpanded = !detailsExpanded },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningStepContent(
    content: ResolvedLearnGuidanceContent,
    exactFacts: P1ExactLearnStageFacts?,
    visualAsset: InstructionAssetRecord?,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
                visualAsset?.let { asset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        ApprovedInstructionAssetImage(
                            asset = asset,
                            contentDescription = content.altText,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = if (visualAsset == null) 20.dp else 8.dp,
                        end = 20.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content.instruction.takeIf(String::isNotBlank)?.let { instruction ->
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    exactFacts?.let { facts -> ExactLearnStageTargets(facts) }

                    content.warning?.let { warning ->
                        LearnGuidanceWarning(
                            warning = warning,
                            safetyCritical = content.safetyCritical,
                        )
                    }

                    content.completionCue?.takeIf(String::isNotBlank)?.let { cue ->
                        LearningCompletionCue(cue)
                    }

                    val details = content.additionalLearningDetails(includeTarget = exactFacts == null)
                    if (details.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onToggleDetails,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (detailsExpanded) {
                                        R.string.action_hide_details
                                    } else {
                                        R.string.action_show_details
                                    },
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (detailsExpanded) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription = null,
                            )
                        }

                        AnimatedVisibility(
                            visible = detailsExpanded,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    details.forEach { detail ->
                                        LearningDetailRow(detail)
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
private fun ExactLearnRecipeOverview(guide: P1ExactLearnGuide) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_exact_learn_recipe_summary),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            DetailsRow(
                stringResource(R.string.label_coffee),
                formatMass(guide.recipe.quantities.dryCoffeeDoseG),
            )
            guide.recipe.quantities.brewWaterInputG?.let { grams ->
                DetailsRow(stringResource(R.string.label_brewer_profile_input_water), formatMass(grams))
            }
            guide.recipe.quantities.reservoirInputG?.let { grams ->
                DetailsRow(
                    stringResource(R.string.label_brewer_profile_input_reservoir_water),
                    formatMass(grams),
                )
            }
            guide.recipe.quantities.iceG.takeIf { grams -> grams > 0.0 }?.let { grams ->
                DetailsRow(stringResource(R.string.label_exact_recipe_brew_ice), formatMass(grams))
            }
            guide.recipe.ratios.forEachIndexed { index, ratio ->
                DetailsRow(
                    if (index == 0) stringResource(R.string.label_ratio)
                    else stringResource(R.string.label_exact_recipe_combined_ratio),
                    stringResource(
                        R.string.format_exact_recipe_ratio,
                        ratio.ratioValue?.let(::formatNumber)
                            ?: stringResource(R.string.label_exact_recipe_unresolved),
                        ratioDenominatorLabel(ratio.includedDenominatorRoles),
                    ),
                )
            }
            DetailsRow(stringResource(R.string.label_temperature), temperatureLabel(guide.recipe.temperature))
            DetailsRow(stringResource(R.string.label_brew_time), timeLabel(guide.recipe.expectedTime))
            DetailsRow(
                stringResource(R.string.label_exact_learn_grind),
                stringResource(R.string.msg_exact_learn_grind_source_scoped),
            )
            val equipmentLabels = mutableListOf<String>()
            for (option in guide.recipe.equipmentOptions) {
                equipmentLabels += equipmentOptionLabel(option)
            }
            DetailsRow(
                stringResource(R.string.heading_exact_recipe_equipment),
                equipmentLabels.joinToString(
                    stringResource(R.string.separator_exact_recipe_equipment_alternatives),
                ),
            )
            DetailsRow(
                stringResource(R.string.label_exact_learn_completion),
                exactCompletionLabel(guide.recipe.completion),
            )
            DetailsRow(
                stringResource(R.string.label_exact_learn_provenance),
                "${guide.guidance.evidenceStatus} · ${guide.guidance.originalSourceOrProvenance}",
            )
        }
    }
}

@Composable
private fun ExactLearnStageTargets(facts: P1ExactLearnStageFacts) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.heading_exact_learn_stage_targets),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            facts.startCondition?.let {
                DetailsRow(stringResource(R.string.label_exact_learn_start), it)
            }
            facts.timing?.let {
                DetailsRow(stringResource(R.string.label_exact_learn_timing), it)
            }
            facts.addedWater?.let {
                DetailsRow(stringResource(R.string.label_exact_learn_added_water), it)
            }
            facts.cumulativeWater?.let {
                DetailsRow(stringResource(R.string.label_exact_learn_cumulative_water), it)
            }
            facts.beverageYield?.let {
                DetailsRow(stringResource(R.string.label_exact_learn_beverage_yield), it)
            }
            facts.temperatureTarget?.let { target ->
                DetailsRow(stringResource(R.string.label_temperature), stageTemperatureLabel(target))
            }
            DetailsRow(stringResource(R.string.label_exact_learn_equipment_state), facts.equipmentState)
        }
    }
}

@Composable
private fun stageTemperatureLabel(target: StageTemperatureTarget): String {
    val minimum = formatNumber(target.minimumC)
    val maximum = formatNumber(target.maximumC)
    return when (target.qualifier) {
        StageTargetQualifier.EXACT -> stringResource(R.string.format_exact_recipe_temperature, minimum)
        StageTargetQualifier.APPROXIMATE -> stringResource(
            R.string.format_exact_recipe_temperature_approximate_range,
            minimum,
            maximum,
        )
        StageTargetQualifier.RANGE -> stringResource(
            R.string.format_exact_recipe_temperature_range,
            minimum,
            maximum,
        )
        StageTargetQualifier.STARTING_POINT -> stringResource(
            R.string.format_exact_recipe_temperature_starting_range,
            minimum,
            maximum,
        )
        StageTargetQualifier.NO_EARLIER_THAN,
        StageTargetQualifier.NO_LATER_THAN,
        -> stringResource(R.string.format_exact_recipe_temperature_range, minimum, maximum)
    }
}

@Composable
private fun exactCompletionLabel(completion: P1CompletionSemantics): String = stringResource(
    when (completion) {
        P1CompletionSemantics.DRAWDOWN -> R.string.exact_completion_drawdown
        P1CompletionSemantics.DRAWDOWN_AND_BREW_ICE_MELT -> R.string.exact_completion_ice_drawdown
        P1CompletionSemantics.VALVE_RELEASE_AND_DRAWDOWN -> R.string.exact_completion_valve_drawdown
        P1CompletionSemantics.FIRST_FOAM_RISE_BEFORE_ROLLING_BOIL -> R.string.exact_completion_first_rise
        P1CompletionSemantics.SECOND_FOAM_RISE_BEFORE_ROLLING_BOIL -> R.string.exact_completion_second_rise
        P1CompletionSemantics.MACHINE_CYCLE_DRAINAGE_AND_HOMOGENIZATION ->
            R.string.exact_completion_machine_mix
        P1CompletionSemantics.MACHINE_CYCLE_AND_RESIDUAL_DRAINAGE ->
            R.string.exact_completion_machine_drain
        P1CompletionSemantics.FIRST_AND_LAST_DRIP_WITHOUT_FORCED_PRESSURE ->
            R.string.exact_completion_first_last_drip
        P1CompletionSemantics.GRAVITY_DRIP_WITHOUT_FORCED_PRESSURE ->
            R.string.exact_completion_gravity_drip
    },
)

@Composable
private fun LearningStepProgress(
    stepNumber: Int,
    totalSteps: Int,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = stepNumber.toFloat() / totalSteps.coerceAtLeast(1),
        animationSpec = tween(
            durationMillis = 420,
            easing = FastOutSlowInEasing,
        ),
        label = "learning_step_progress",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.format_scan_stage_step,
                        stepNumber,
                        totalSteps,
                    ),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun LearningCompletionCue(cue: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = cue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LearningDetailRow(detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResolvedLearnGuidanceContent.additionalLearningDetails(
    includeTarget: Boolean,
): List<String> {
    val details = mutableListOf<String>()
    if (includeTarget) target?.takeIf(String::isNotBlank)?.let(details::add)
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = LearnStepContentMaxWidth)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onPrevious,
                    enabled = stepIndex > 0,
                    modifier = Modifier
                        .weight(0.9f)
                        .heightIn(min = 56.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_back))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 56.dp),
                ) {
                    val isLastStep = stepIndex == totalSteps - 1
                    Text(
                        stringResource(
                            if (isLastStep) {
                                R.string.action_finish
                            } else {
                                R.string.action_next
                            },
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isLastStep) {
                            Icons.Filled.Check
                        } else {
                            Icons.AutoMirrored.Filled.ArrowForward
                        },
                        contentDescription = null,
                    )
                }
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
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
