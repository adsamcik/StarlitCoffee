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
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactLearnGuide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.adsamcik.starlitcoffee.ui.brewing.BrewReaderContent
import com.adsamcik.starlitcoffee.ui.brewing.BrewStepContent
import com.adsamcik.starlitcoffee.ui.brewing.toBrewReaderContent

private val LearnStepContentMaxWidth = 720.dp

/** Existing catalogue entry point; source matching and start validation stay outside the reader. */
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
    val content = resolution.toBrewReaderContent(
        key = exactGuide?.recipe?.id?.value ?: reviewedGuide?.id ?: title,
        instructionAssets = instructionAssets, exactGuide = exactGuide, reviewedGuide = reviewedGuide)
    val initialContentId = resolution.content.getOrNull(initialStepIndex)?.id
    LearnBrewerScreen(title, content, onBack, modifier, content.readableStepIndexFor(initialContentId),
        initiallyReading, onBrewGuide)
}

/** Timer-free, data-driven reader. Any independently useful step can be read without sibling prose or artwork. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnBrewerScreen(
    title: String,
    content: BrewReaderContent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialStepIndex: Int = 0,
    initiallyReading: Boolean = false,
    onBrewGuide: (() -> Unit)? = null,
) {
    val steps = content.readableSteps
    var selectedStepId by rememberSaveable(content.key) {
        mutableStateOf(steps.getOrNull(initialStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0)))?.id?.value)
    }
    var reading by rememberSaveable(content.key) { mutableStateOf(initiallyReading) }
    var showSteps by rememberSaveable(content.key) { mutableStateOf(false) }
    val safeStepIndex = steps.indexOfFirst { it.id.value == selectedStepId }.coerceAtLeast(0)
    val currentStep = steps.getOrNull(safeStepIndex)
    val isReading = reading && currentStep != null
    var detailsExpanded by rememberSaveable(currentStep?.id?.value) { mutableStateOf(false) }
    val scrollStates = rememberSaveableStateHolder()
    val returnToOverview = { reading = false }
    BackHandler(enabled = isReading && !showSteps, onBack = returnToOverview)

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(title = {
                Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }, navigationIcon = {
                IconButton(onClick = if (isReading) returnToOverview else onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }, actions = {
                if (isReading) TextButton(onClick = { showSteps = true }) {
                    Text(stringResource(R.string.action_guide_steps))
                }
            })
        }, bottomBar = {
            if (isReading) LearningStepControls(safeStepIndex, steps.size,
                onPrevious = { selectedStepId = steps[(safeStepIndex - 1).coerceAtLeast(0)].id.value },
                onNext = {
                    if (safeStepIndex == steps.lastIndex) reading = false else selectedStepId = steps[safeStepIndex + 1].id.value
                })
        }) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            scrollStates.SaveableStateProvider("${content.key}:${if (isReading) currentStep?.id?.value else "overview"}") {
                LazyColumn(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = LearnStepContentMaxWidth)
                    .fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (isReading) {
                        item { LearningStepProgress(safeStepIndex + 1, steps.size) }
                        item {
                            currentStep?.let { GuideStepCanvas(it, detailsExpanded, { expanded -> detailsExpanded = expanded }) }
                        }
                    } else {
                        if (steps.isNotEmpty()) item {
                            Text(stringResource(R.string.label_guide_untimed), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        steps.firstOrNull()?.illustration?.let { illustration -> item {
                            ApprovedInstructionAssetImage(illustration.asset, illustration.description)
                        } }
                        content.overview?.let { overview -> item { BrewRecipeOverview(overview) } }
                        if (steps.isNotEmpty()) item {
                            Button(onClick = { reading = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(if (safeStepIndex > 0) R.string.action_continue_reading else R.string.action_read_guide))
                            }
                        }
                        onBrewGuide?.let { start -> item {
                            FilledTonalButton(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.action_brew_this_guide))
                            }
                        } }
                        items(content.commonContent.filter { it.isReadable }, key = { "common:${it.id.value}" }) { step ->
                            CommonLearningContent(step)
                        }
                        itemsIndexed(steps, key = { _, step -> step.id.value }) { index, step ->
                            GuideStepListItem(index, steps.size, step, index == safeStepIndex) {
                                selectedStepId = step.id.value
                                reading = true
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSteps && steps.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showSteps = false }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(steps, key = { _, step -> step.id.value }) { index, step ->
                    GuideStepListItem(index, steps.size, step, index == safeStepIndex) {
                        selectedStepId = step.id.value
                        showSteps = false
                    }
                }
            }
        }
    }
}

@Composable
private fun CommonLearningContent(content: BrewStepContent) {
    var expanded by rememberSaveable(content.id.value) { mutableStateOf(false) }
    GuideStepCanvas(content, expanded, { expanded = it })
}

@Composable
private fun GuideStepListItem(index: Int, total: Int, step: BrewStepContent, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { this.selected = selected },
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.format_scan_stage_step, index + 1, total), style = MaterialTheme.typography.labelMedium)
            val label = step.title ?: step.action?.label() ?: step.instruction ?: step.target ?: step.completionCue
                ?: step.warning ?: step.illustration?.description ?: step.details.firstOrNull() ?: step.operations.firstOrNull()
            label?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            if (label == null) {
                step.primaryReferenceCue?.let { ReferenceCueRow(it) }
                step.facts.firstOrNull()?.let { DetailsRow(stringResource(it.labelRes), it.value) }
                (step.controlRequirements + step.utilities).distinct().forEach { Text(it.localizedLabel()) }
            }
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
internal fun exactCompletionLabel(completion: P1CompletionSemantics): String = stringResource(
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
