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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.Color
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
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isReading) {
                        item { LearningStepProgress(safeStepIndex + 1, steps.size) }
                        item {
                            currentStep?.let { GuideStepCanvas(it, detailsExpanded, { expanded -> detailsExpanded = expanded }) }
                        }
                    } else {
                        if (steps.isNotEmpty()) item {
                            Text(stringResource(R.string.label_guide_untimed), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        steps.firstOrNull()?.illustration?.let { illustration -> item {
                            ApprovedInstructionAssetImage(illustration.asset, illustration.description)
                        } }
                        content.overview?.let { overview -> item { BrewRecipeOverview(overview) } }
                        if (steps.isNotEmpty()) item {
                            Button(onClick = { reading = true }, shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(if (safeStepIndex > 0) R.string.action_continue_reading else R.string.action_read_guide))
                            }
                        }
                        onBrewGuide?.let { start -> item {
                            FilledTonalButton(onClick = start, shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.format_scan_stage_step, index + 1, total),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.format_scan_stage_step, stepNumber, totalSteps),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
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
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center) {
            FlowRow(modifier = Modifier.widthIn(max = LearnStepContentMaxWidth).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2) {
                if (stepIndex > 0) {
                    TextButton(onClick = onPrevious, modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_back))
                    }
                }
                Button(onClick = onNext, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1.1f).heightIn(min = 48.dp)) {
                    val isLastStep = stepIndex == totalSteps - 1
                    Text(stringResource(if (isLastStep) R.string.action_guide_overview else R.string.action_next))
                    Spacer(Modifier.width(8.dp))
                    Icon(if (isLastStep) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null)
                }
            }
        }
    }
}
