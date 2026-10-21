package com.adsamcik.starlitcoffee.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetAnimation
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import java.text.NumberFormat

/** Display-only projection: the session coordinator remains the sole owner of brew clocks. */
@Composable
internal fun BrewSessionHero(
    presentation: ActiveBrewSessionPresentation.Available,
    recipe: BrewRecipeSnapshotV1?,
    runtime: SessionRuntimeState?,
    bloomSpritesheetId: String?,
    modifier: Modifier = Modifier,
) {
    val bloom = brewSessionBloomState(presentation, runtime).takeIf { !bloomSpritesheetId.isNullOrEmpty() }
    val quantityText = brewHeroQuantities(recipe).map { quantity ->
        val amount = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(quantity.amount)
        stringResource(quantity.labelRes) + " " + stringResource(
            R.string.format_brewer_profile_mass, amount, stringResource(quantity.unitRes),
        )
    }.joinToString(" · ")
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (quantityText.isNotEmpty()) {
            Text(quantityText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val sideBySide = maxWidth >= 320.dp && fontScale < 1.5f
            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrewHeroClock(presentation, Modifier.weight(1f))
                    bloom?.let { BrewHeroBloom(it, bloomSpritesheetId, Modifier.size(108.dp)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrewHeroClock(presentation, Modifier.fillMaxWidth())
                    bloom?.let { BrewHeroBloom(it, bloomSpritesheetId, Modifier.size(88.dp).align(Alignment.End)) }
                }
            }
        }
    }
}

@Composable
private fun BrewHeroClock(presentation: ActiveBrewSessionPresentation.Available, modifier: Modifier) {
    val clockIsKnown = presentation.hasPhysicalClockStarted && presentation.isPhysicalOriginKnown
    val clock = when {
        !presentation.hasPhysicalClockStarted -> stringResource(R.string.label_brew_activity_ready)
        !presentation.isPhysicalOriginKnown -> stringResource(R.string.label_brew_start_unknown)
        else -> brewHeroDuration(presentation.totalActiveElapsedMillis)
    }
    // Read the clock with its elapsed/remaining labels, without announcing each tick.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Reserve enough width for each character at large system font sizes, including h/min labels.
            val fittingSize = maxWidth.value / (clock.length.coerceAtLeast(1) * 0.65f * LocalDensity.current.fontScale)
            val fontSize = if (clockIsKnown) fittingSize.coerceAtMost(76f).sp else 26.sp
            Text(
                text = clock,
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                fontWeight = FontWeight.Light,
                style = MaterialTheme.typography.displayLarge,
            )
        }
        if (clockIsKnown) {
            Text(stringResource(R.string.label_elapsed), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sourceRemaining = if (presentation.isPhysicalOriginKnown) {
            when (val completion = presentation.currentStage?.completion) {
                is BrewStageCompletionPresentation.Countdown -> completion.remainingMillis
                is BrewStageCompletionPresentation.ElapsedRange -> completion.maximumRemainingMillis
                else -> null
            }
        } else null
        sourceRemaining?.let {
            val label = stringResource(R.string.label_remaining)
            Text("$label · ${brewHeroDuration(it)}", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        if (presentation.isPhysicalOriginKnown) {
            presentation.userTimerRemainingMillis?.let {
                val label = stringResource(R.string.label_brew_reminder)
                Text("$label · ${brewHeroDuration(it)}", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BrewHeroBloom(state: BrewSessionBloomState, spritesheetId: String?, modifier: Modifier) {
    BloomSpritesheetAnimation(
        bloomCountdownSeconds = state.countdownSeconds,
        bloomDurationSeconds = state.durationSeconds,
        selectedSpritesheetId = spritesheetId,
        isRunning = state.isRunning,
        modifier = modifier,
    )
}

@Composable
internal fun brewHeroDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600L) {
        stringResource(R.string.format_brew_activity_hours, seconds / 3_600L, seconds % 3_600L / 60L)
    } else {
        stringResource(R.string.format_brew_activity_minutes, seconds / 60L, seconds % 60L)
    }
}

internal data class BrewHeroQuantity(
    val amount: Double,
    @StringRes val labelRes: Int,
    @StringRes val unitRes: Int = R.string.unit_grams,
)

internal fun brewHeroQuantities(recipe: BrewRecipeSnapshotV1?): List<BrewHeroQuantity> {
    if (recipe == null) return emptyList()
    val quantities = recipe.quantities
    val primary = when {
        recipe.methodFamilyId == "espresso" -> quantities.targetBeverageYieldG?.let {
            BrewHeroQuantity(it, R.string.guidance_cue_beverage_yield_target)
        }
        quantities.brewWaterInputMl != null -> BrewHeroQuantity(
            quantities.brewWaterInputMl, R.string.label_brew_reference_brew_water, R.string.unit_ml,
        )
        quantities.brewWaterInputG != null -> BrewHeroQuantity(quantities.brewWaterInputG, R.string.label_brew_reference_brew_water)
        quantities.reservoirInputG != null -> BrewHeroQuantity(quantities.reservoirInputG, R.string.label_brew_reference_reservoir_water)
        else -> null
    }
    return listOfNotNull(BrewHeroQuantity(quantities.dryCoffeeDoseG, R.string.label_coffee), primary)
}

internal data class BrewSessionBloomState(
    val countdownSeconds: Int? = null,
    val durationSeconds: Int = 1,
    val isRunning: Boolean = false,
)

internal fun brewSessionBloomState(
    presentation: ActiveBrewSessionPresentation.Available,
    runtime: SessionRuntimeState?,
): BrewSessionBloomState? {
    val stages = runtime?.stagePlan?.stages ?: return null
    val bloomIndices = stages.indices.filter { stages[it].definition.action == BrewStageAction.BLOOM }
    if (bloomIndices.isEmpty()) return null
    val currentIndex = runtime.currentStageIndex
    if (currentIndex !in bloomIndices) {
        val hasPassedBloom = bloomIndices.any { index ->
            runtime.stageProgress.getOrNull(index)?.status in setOf(StageRunStatus.COMPLETED, StageRunStatus.SKIPPED) ||
                (currentIndex != null && currentIndex > index)
        }
        return BrewSessionBloomState(countdownSeconds = if (hasPassedBloom) 0 else null)
    }
    if (!presentation.hasPhysicalClockStarted || !presentation.isPhysicalOriginKnown) return BrewSessionBloomState()
    return continuousBloomState(presentation, runtime, requireNotNull(currentIndex))
        ?: activeBloomState(presentation, stages[currentIndex].definition)
}

/** A retained bloom can span the manual pour and a later wait, sharing one first-water boundary. */
private fun continuousBloomState(
    presentation: ActiveBrewSessionPresentation.Available,
    runtime: SessionRuntimeState,
    currentIndex: Int,
): BrewSessionBloomState? {
    val stage = presentation.currentStage ?: return null
    if (stage.completion is BrewStageCompletionPresentation.Countdown ||
        stage.completion is BrewStageCompletionPresentation.ElapsedRange
    ) return null
    val stages = runtime.stagePlan.stages
    val preceding = (currentIndex - 1 downTo 0).takeWhile { stages[it].definition.action == BrewStageAction.BLOOM }
    val following = (currentIndex + 1..stages.lastIndex).takeWhile { stages[it].definition.action == BrewStageAction.BLOOM }
    val boundary = (preceding + currentIndex + following).mapNotNull { index ->
        val definition = stages[index].definition
        definition.advanceConstraint.notBeforeBrewElapsedMillis
            ?: definition.referenceTargets.timeTargets.filter {
                it.reference == StageTimeReference.BREW_ELAPSED_AT_COMPLETION && it.maximumMillis > 0L
            }.maxOfOrNull { it.maximumMillis }
    }.maxOrNull() ?: return null
    val previousElapsed = preceding.sumOf { runtime.stageProgress[it].elapsedActiveMillis }
    val elapsedBeforeGroup = (presentation.totalActiveElapsedMillis - stage.elapsedActiveMillis - previousElapsed)
        .coerceAtLeast(0L)
    return timedBloomState(boundary - elapsedBeforeGroup, previousElapsed + stage.elapsedActiveMillis,
        presentation.status == BrewSessionStatus.RUNNING)
}

private fun activeBloomState(
    presentation: ActiveBrewSessionPresentation.Available,
    definition: BrewStageDefinition,
): BrewSessionBloomState {
    val stage = presentation.currentStage ?: return BrewSessionBloomState()
    val stageElapsed = stage.elapsedActiveMillis
    val elapsedAtStart = (presentation.totalActiveElapsedMillis - stageElapsed).coerceAtLeast(0L)
    val duration = bloomDurationMillis(definition, elapsedAtStart) ?: return BrewSessionBloomState()
    return timedBloomState(duration, stageElapsed, presentation.status == BrewSessionStatus.RUNNING)
}

private fun timedBloomState(duration: Long, elapsed: Long, isRunning: Boolean): BrewSessionBloomState {
    if (duration <= 0L) return BrewSessionBloomState(countdownSeconds = 0)
    return BrewSessionBloomState(
        countdownSeconds = ceilSeconds((duration - elapsed).coerceAtLeast(0L)),
        durationSeconds = ceilSeconds(duration),
        isRunning = isRunning,
    )
}

private fun bloomDurationMillis(definition: BrewStageDefinition, elapsedAtStart: Long): Long? =
    when (val completion = definition.completionMode) {
        is StageCompletionMode.Countdown -> completion.durationMillis
        is StageCompletionMode.ElapsedRange -> completion.maximumMillis
        else -> {
            val stageBoundary = definition.advanceConstraint.notBeforeStageElapsedMillis
            val brewBoundary = definition.advanceConstraint.notBeforeBrewElapsedMillis
            val reference = definition.referenceTargets.timeTargets.firstOrNull {
                it.reference != StageTimeReference.BREW_ELAPSED_AT_START && it.maximumMillis > 0L
            }
            when {
                brewBoundary != null -> brewBoundary - elapsedAtStart
                stageBoundary != null -> stageBoundary
                reference?.reference == StageTimeReference.BREW_ELAPSED_AT_COMPLETION ->
                    reference.maximumMillis - elapsedAtStart
                reference != null -> reference.maximumMillis
                else -> null
            }
        }
    }

private fun ceilSeconds(millis: Long): Int =
    (millis / 1_000L + if (millis % 1_000L > 0L) 1L else 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
