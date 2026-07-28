package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import java.text.NumberFormat
import java.util.Locale

/**
 * Secondary, non-interactive targets that help users pace a stage without
 * competing with or changing its completion action.
 */
@Composable
internal fun BrewSessionReferenceCues(
    sessionKey: String,
    cues: List<BrewStageReferenceCuePresentation>,
    modifier: Modifier = Modifier,
) {
    // Remember the user's preferred density as stages advance, but reset it
    // when a different durable session is shown.
    var expanded by rememberSaveable(sessionKey) { mutableStateOf(false) }
    if (cues.isEmpty()) return

    val hasAdditionalCues = cues.size > 1

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.label_brew_reference_cues),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.msg_brew_reference_cues_not_completion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ReferenceCueRow(cues.first())

            AnimatedVisibility(visible = expanded && hasAdditionalCues) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cues.drop(1).forEach { cue -> ReferenceCueRow(cue) }
                }
            }

            if (hasAdditionalCues) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) {
                                R.string.action_hide_brew_reference_cues
                            } else {
                                R.string.action_show_more_brew_reference_cues
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceCueRow(cue: BrewStageReferenceCuePresentation) {
    val content = cue.content()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = content.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = content.value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class ReferenceCueContent(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun BrewStageReferenceCuePresentation.content(): ReferenceCueContent = when (this) {
    is BrewStageReferenceCuePresentation.Time -> ReferenceCueContent(
        icon = Icons.Filled.Schedule,
        label = stringResource(reference.labelRes),
        value = qualifier.formatValue(
            minimum = formatReferenceDuration(minimumMillis),
            maximum = formatReferenceDuration(maximumMillis),
            hasDistinctMaximum = minimumMillis != maximumMillis,
        ),
    )

    is BrewStageReferenceCuePresentation.Mass -> ReferenceCueContent(
        icon = Icons.Filled.Scale,
        label = "${stringResource(role.labelRes)} · ${stringResource(reference.labelRes)}",
        value = qualifier.formatValue(
            minimum = "${formatReferenceNumber(minimumGrams)} g",
            maximum = "${formatReferenceNumber(maximumGrams)} g",
            hasDistinctMaximum = minimumGrams != maximumGrams,
        ),
    )

    is BrewStageReferenceCuePresentation.Temperature -> {
        val formattedValue = qualifier.formatValue(
            minimum = formatReferenceNumber(minimumC),
            maximum = formatReferenceNumber(maximumC),
            hasDistinctMaximum = minimumC != maximumC,
        )
        ReferenceCueContent(
            icon = Icons.Filled.Thermostat,
            label = stringResource(R.string.label_temperature),
            value = "$formattedValue °C",
        )
    }
}

@Composable
private fun StageTargetQualifier.formatValue(
    minimum: String,
    maximum: String,
    hasDistinctMaximum: Boolean,
): String {
    val range = stringResource(
        R.string.format_brew_reference_range,
        minimum,
        maximum,
    )
    val boundedValue = if (hasDistinctMaximum) range else minimum
    return when (this) {
        StageTargetQualifier.EXACT -> minimum
        StageTargetQualifier.APPROXIMATE -> stringResource(
            R.string.format_brew_reference_approximate,
            boundedValue,
        )

        StageTargetQualifier.RANGE -> range

        StageTargetQualifier.NO_LATER_THAN -> stringResource(
            R.string.format_brew_reference_no_later_than,
            maximum,
        )

        StageTargetQualifier.NO_EARLIER_THAN -> stringResource(
            R.string.format_brew_reference_no_earlier_than,
            minimum,
        )

        StageTargetQualifier.STARTING_POINT -> stringResource(
            R.string.format_brew_reference_starting_point,
            boundedValue,
        )
    }
}

private val StageTimeReference.labelRes: Int
    get() = when (this) {
        StageTimeReference.STAGE_DURATION -> R.string.label_brew_reference_stage_duration
        StageTimeReference.BREW_ELAPSED_AT_START -> R.string.label_brew_reference_brew_start_time
        StageTimeReference.BREW_ELAPSED_AT_COMPLETION -> {
            R.string.label_brew_reference_brew_completion_time
        }
    }

private val StageMassReference.labelRes: Int
    get() = when (this) {
        StageMassReference.STAGE_ADDED -> R.string.label_brew_reference_added_this_step
        StageMassReference.BREW_CUMULATIVE -> R.string.label_brew_reference_brew_total
        StageMassReference.RECIPE_TOTAL -> R.string.label_brew_reference_recipe_total
    }

private val QuantityRole.labelRes: Int
    get() = when (this) {
        QuantityRole.DRY_COFFEE_DOSE -> R.string.label_brew_reference_coffee_dose
        QuantityRole.BREW_WATER_INPUT -> R.string.label_brew_reference_brew_water
        QuantityRole.RESERVOIR_INPUT -> R.string.label_brew_reference_reservoir_water
        QuantityRole.BEVERAGE_YIELD -> R.string.label_brew_reference_beverage_yield
        QuantityRole.CONCENTRATE_YIELD -> R.string.label_brew_reference_concentrate_yield
        QuantityRole.FINAL_SERVED_BEVERAGE -> R.string.label_brew_reference_served_beverage
        QuantityRole.ICE -> R.string.label_brew_reference_ice
        QuantityRole.BYPASS_WATER -> R.string.label_brew_reference_bypass_water
        QuantityRole.DILUTION_WATER -> R.string.label_brew_reference_dilution_water
        QuantityRole.MEASURED_OUTPUT -> R.string.label_brew_reference_measured_output
    }

private fun formatReferenceDuration(millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        seconds / SECONDS_PER_MINUTE,
        seconds % SECONDS_PER_MINUTE,
    )
}

private fun formatReferenceNumber(value: Double): String = NumberFormat.getNumberInstance(
    Locale.getDefault(),
).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 1
}.format(value)

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
