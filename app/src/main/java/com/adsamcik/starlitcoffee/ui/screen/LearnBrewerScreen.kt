package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.ui.guidance.learnFacts

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
    initialStepIndex: Int = 0,
    initiallyReading: Boolean = false,
    reviewedGuide: ReviewedMethodGuide? = null,
    onBrewGuide: (() -> Unit)? = null,
) {
    val steps = resolution.content
    val stageFacts = exactGuide?.stageFactsByContentId ?: reviewedGuide?.learnFacts().orEmpty()
    val guideKey = steps.firstOrNull()?.id?.value
    var currentStepIndex by rememberSaveable(guideKey) { mutableIntStateOf(initialStepIndex) }
    var reading by rememberSaveable(guideKey) { mutableStateOf(initiallyReading) }
    var showSteps by rememberSaveable(guideKey) { mutableStateOf(false) }
    val safeStepIndex = currentStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val currentStep = steps.getOrNull(safeStepIndex)
    var detailsExpanded by rememberSaveable(currentStep?.id?.value) { mutableStateOf(false) }
    val available = resolution.availability is LearnGuidanceCatalogAvailability.Available && currentStep != null
    val returnToOverview = { reading = false }
    BackHandler(enabled = reading && !showSteps, onBack = returnToOverview)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (reading && exactGuide != null) title.substringBefore(" · ") else title,
                        Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = if (reading) returnToOverview else onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (available && reading) {
                        TextButton(onClick = { showSteps = true }) {
                            Text(stringResource(R.string.action_guide_steps))
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (available && reading) {
                LearningStepControls(
                    stepIndex = safeStepIndex,
                    totalSteps = steps.size,
                    onPrevious = { currentStepIndex = (safeStepIndex - 1).coerceAtLeast(0) },
                    onNext = {
                        if (safeStepIndex == steps.lastIndex) reading = false
                        else currentStepIndex = safeStepIndex + 1
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            // Each reading position owns its scroll state; a new step starts at its action.
            key(reading, safeStepIndex) {
                LazyColumn(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = LearnStepContentMaxWidth)
                        .fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (!available) {
                        item { Text(stringResource(R.string.msg_brew_guidance_unavailable)) }
                    } else if (!reading) {
                        item {
                            Text(stringResource(R.string.label_guide_untimed),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        steps.firstOrNull()?.let { introduction ->
                            instructionAssets?.findApprovedAssetForContent(introduction.id)?.let { asset ->
                                item { ApprovedInstructionAssetImage(asset, introduction.altText) }
                            }
                        }
                        exactGuide?.let { guide -> item { ExactLearnRecipeOverview(guide) } }
                        reviewedGuide?.let { guide -> item { ReviewedGuideOverview(guide) } }
                        item {
                            Button(onClick = { reading = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(if (safeStepIndex > 0) R.string.action_continue_reading else R.string.action_read_guide))
                            }
                        }
                        onBrewGuide?.let { start -> item {
                            FilledTonalButton(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.action_brew_this_guide))
                            }
                        } }
                        itemsIndexed(steps, key = { _, step -> step.id.value }) { index, step ->
                            GuideStepListItem(index, steps.size, step, stageFacts[step.id], index == safeStepIndex) {
                                currentStepIndex = index
                                reading = true
                            }
                        }
                    } else {
                        item {
                            LearningStepProgress(safeStepIndex + 1, steps.size)
                        }
                        item {
                            val step = requireNotNull(currentStep)
                            LearningStepContent(step, stageFacts[step.id],
                                instructionAssets?.findApprovedAssetForContent(step.id), detailsExpanded,
                                onToggleDetails = { detailsExpanded = !detailsExpanded },
                                equipmentProfileId = reviewedGuide?.profileId)
                        }
                    }
                }
            }
        }
    }
    if (showSteps && available) {
        ModalBottomSheet(onDismissRequest = { showSteps = false }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(steps, key = { _, step -> step.id.value }) { index, step ->
                    GuideStepListItem(index, steps.size, step, stageFacts[step.id], index == safeStepIndex) {
                        currentStepIndex = index
                        showSteps = false
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStepListItem(
    index: Int,
    total: Int,
    step: ResolvedLearnGuidanceContent,
    facts: P1ExactLearnStageFacts?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { this.selected = selected },
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.format_scan_stage_step, index + 1, total),
                style = MaterialTheme.typography.labelMedium)
            val action = facts?.title ?: facts?.action?.label()
            Text(action ?: step.instruction.ifBlank { step.warning.orEmpty() },
                style = MaterialTheme.typography.titleMedium)
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
    equipmentProfileId: String? = null,
) {
    GuideStepCanvas(
        copy = GuideStepCopy(
            instruction = content.instruction,
            target = content.target.takeIf { exactFacts?.startCondition == null },
            completionCue = content.completionCue,
            essentialOperations = listOfNotNull(
                exactFacts?.equipmentState?.takeUnless { it == "As specified" }?.let {
                    "${stringResource(R.string.label_exact_learn_equipment_state)}: $it"
                },
                content.nextAction,
            ),
            warning = content.warning,
            safetyCritical = content.safetyCritical,
            explanation = listOfNotNull(exactFacts?.explanation, content.explanation, content.tip)
                .filterNot { it == "None" }.distinct(),
            altText = content.altText,
        ),
        visualAsset = visualAsset,
        title = exactFacts?.title ?: exactFacts?.action?.label(),
        expanded = detailsExpanded,
        onExpandedChange = { onToggleDetails() },
        targets = exactFacts?.takeIf { facts ->
            listOf(facts.startCondition, facts.timing, facts.addedWater, facts.cumulativeWater,
                facts.beverageYield, facts.temperatureTarget).any { it != null } ||
                ActiveBrewSessionPresentationMapper.referenceCuePresentations(facts.referenceTargets).isNotEmpty()
        }?.let { facts -> { ExactLearnStageTargets(facts) } },
        equipmentProfileId = equipmentProfileId,
    )
}

@Composable
private fun ExactLearnRecipeOverview(guide: P1ExactLearnGuide) {
    var expanded by rememberSaveable(guide.recipe.id.value) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideQuantity(stringResource(R.string.label_coffee),
                formatMass(guide.recipe.quantities.dryCoffeeDoseG), Modifier.weight(1f))
            val water = guide.recipe.quantities.brewWaterInputG ?: guide.recipe.quantities.reservoirInputG
            water?.let { grams ->
                GuideQuantity(stringResource(if (guide.recipe.quantities.brewWaterInputG != null)
                    R.string.label_brewer_profile_input_water else R.string.label_brewer_profile_input_reservoir_water),
                    formatMass(grams), Modifier.weight(1f))
            }
        }
        ConnectedGuideDisclosure(stringResource(R.string.heading_exact_learn_recipe_summary), expanded,
            onExpandedChange = { expanded = it }) {
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
internal fun GuideQuantity(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge)
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
            val cues = ActiveBrewSessionPresentationMapper.referenceCuePresentations(facts.referenceTargets)
            val primaryCue = cues.firstOrNull { it is BrewStageReferenceCuePresentation.Mass } ?: cues.firstOrNull()
            primaryCue?.let { ReferenceCueRow(it, prominent = true) }
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
@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier
                    .widthIn(max = LearnStepContentMaxWidth)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
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
                                R.string.action_guide_overview
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
