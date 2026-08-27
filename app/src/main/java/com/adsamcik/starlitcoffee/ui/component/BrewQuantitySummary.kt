package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import java.text.NumberFormat
import java.util.Locale

/**
 * A read-only summary of the quantities planned for a completed brew.
 *
 * Planned quantities remain visible after the user adds measurements, so the
 * history explains both what the brew planned and what actually reached the cup.
 * Measurement editing is deliberately a single contextual action rather than a
 * permanent form in the ordinary log-detail flow.
 */
@Composable
fun BrewQuantitySummary(
    estimatedCoffeeDoseG: Float,
    estimatedWaterInputG: Float?,
    estimatedBeverageOutputG: Float?,
    measuredWaterInputG: Float?,
    measuredBeverageOutputG: Float?,
    onEditMeasurements: (() -> Unit)?,
    modifier: Modifier = Modifier,
    measurementsEnabled: Boolean = true,
    beverageOutputIsEstimate: Boolean = true,
) {
    val measuredWater = measuredWaterInputG.validQuantityOrNull()
    val measuredOutput = measuredBeverageOutputG.validQuantityOrNull()
    val hasMeasurements = measuredWater != null || measuredOutput != null

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BrewQuantitiesSummaryTestTag),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = LocalDensity.current.fontScale >= BrewQuantitiesStackedFontScale ||
                maxWidth < BrewQuantitiesStackedWidth
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BrewQuantitiesOuterPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrewQuantitiesHeader(
                    stacked = stacked,
                    hasMeasurements = hasMeasurements,
                    measurementsEnabled = measurementsEnabled,
                    onEditMeasurements = onEditMeasurements,
                )

                val items = brewQuantitySummaryItems(
                    estimatedCoffeeDoseG = estimatedCoffeeDoseG,
                    estimatedWaterInputG = estimatedWaterInputG,
                    estimatedBeverageOutputG = estimatedBeverageOutputG,
                    measuredWaterInputG = measuredWater,
                    measuredBeverageOutputG = measuredOutput,
                    beverageOutputIsEstimate = beverageOutputIsEstimate,
                )
                if (stacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(BrewQuantitiesItemSpacing),
                    ) {
                        items.forEach { item ->
                            BrewQuantitySummaryItem(
                                item = item,
                                horizontal = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(BrewQuantitiesItemSpacing),
                    ) {
                        items.forEach { item ->
                            BrewQuantitySummaryItem(
                                item = item,
                                horizontal = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrewQuantitiesHeader(
    stacked: Boolean,
    hasMeasurements: Boolean,
    measurementsEnabled: Boolean,
    onEditMeasurements: (() -> Unit)?,
) {
    val title: @Composable () -> Unit = {
        Text(
            text = stringResource(R.string.brew_quantities_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
    }
    val action: @Composable () -> Unit = {
        if (onEditMeasurements != null) {
            TextButton(
                onClick = onEditMeasurements,
                enabled = measurementsEnabled,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(BrewQuantitiesMeasurementActionTestTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (hasMeasurements) {
                            R.string.brew_quantities_edit_measurements
                        } else {
                            R.string.brew_quantities_add_measurements
                        },
                    ),
                )
            }
        }
    }

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            title()
            action()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            title()
            action()
        }
    }
}

@Composable
private fun brewQuantitySummaryItems(
    estimatedCoffeeDoseG: Float,
    estimatedWaterInputG: Float?,
    estimatedBeverageOutputG: Float?,
    measuredWaterInputG: Float?,
    measuredBeverageOutputG: Float?,
    beverageOutputIsEstimate: Boolean,
): List<BrewQuantitySummaryItemModel> {
    val coffeeEstimate = formatBrewQuantity(estimatedCoffeeDoseG)
    val waterEstimate = estimatedWaterInputG
        ?.validQuantityOrNull()
        ?.let(::formatBrewQuantity)
    val beverageEstimate = estimatedBeverageOutputG
        ?.validQuantityOrNull()
        ?.let(::formatBrewQuantity)

    val coffee = BrewQuantitySummaryItemModel(
        tag = BrewQuantitiesCoffeeTestTag,
        label = stringResource(R.string.brew_quantities_coffee),
        value = coffeeEstimate,
        supporting = stringResource(R.string.brew_quantities_planned),
        spoken = stringResource(
            R.string.brew_quantities_accessibility_planned,
            stringResource(R.string.brew_quantities_coffee),
            coffeeEstimate,
        ),
        icon = CalculationQuantityIconType.COFFEE_DOSE,
        tint = MaterialTheme.colorScheme.primary,
    )
    val water = if (waterEstimate != null && measuredWaterInputG != null) {
        val measured = formatBrewQuantity(measuredWaterInputG)
        BrewQuantitySummaryItemModel(
            tag = BrewQuantitiesWaterTestTag,
            label = stringResource(R.string.brew_quantities_water_in),
            value = measured,
            supporting = stringResource(
                R.string.brew_quantities_measured_planned_value,
                waterEstimate,
            ),
            spoken = stringResource(
                R.string.brew_quantities_accessibility_measured_planned,
                stringResource(R.string.brew_quantities_water_in),
                measured,
                waterEstimate,
            ),
            icon = CalculationQuantityIconType.WATER_IN,
            tint = MaterialTheme.colorScheme.secondary,
        )
    } else if (waterEstimate != null) {
        BrewQuantitySummaryItemModel(
            tag = BrewQuantitiesWaterTestTag,
            label = stringResource(R.string.brew_quantities_water_in),
            value = waterEstimate,
            supporting = stringResource(R.string.brew_quantities_planned),
            spoken = stringResource(
                R.string.brew_quantities_accessibility_planned,
                stringResource(R.string.brew_quantities_water_in),
                waterEstimate,
            ),
            icon = CalculationQuantityIconType.WATER_IN,
            tint = MaterialTheme.colorScheme.secondary,
        )
    } else null
    val beverage = when {
        measuredBeverageOutputG != null -> {
            val measured = formatBrewQuantity(measuredBeverageOutputG)
            BrewQuantitySummaryItemModel(
                tag = BrewQuantitiesBeverageTestTag,
                label = stringResource(R.string.brew_quantities_in_cup),
                value = measured,
                supporting = beverageEstimate?.let { expected ->
                    stringResource(
                        if (beverageOutputIsEstimate) {
                            R.string.brew_quantities_measured_estimated_value
                        } else {
                            R.string.brew_quantities_measured_planned_value
                        },
                        expected,
                    )
                } ?: stringResource(R.string.brew_quantities_measured),
                spoken = beverageEstimate?.let { expected ->
                    stringResource(
                        if (beverageOutputIsEstimate) {
                            R.string.brew_quantities_accessibility_measured_estimated
                        } else {
                            R.string.brew_quantities_accessibility_measured_planned
                        },
                        stringResource(R.string.brew_quantities_in_cup),
                        measured,
                        expected,
                    )
                } ?: stringResource(
                    R.string.brew_quantities_accessibility_measured,
                    stringResource(R.string.brew_quantities_in_cup),
                    measured,
                ),
                icon = CalculationQuantityIconType.CUP_OUTPUT,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }

        beverageEstimate != null && beverageOutputIsEstimate -> BrewQuantitySummaryItemModel(
            tag = BrewQuantitiesBeverageTestTag,
            label = stringResource(R.string.brew_quantities_in_cup),
            value = stringResource(R.string.brew_quantities_approximate_value, beverageEstimate),
            supporting = stringResource(R.string.brew_quantities_estimated),
            spoken = stringResource(
                R.string.brew_quantities_accessibility_estimated,
                stringResource(R.string.brew_quantities_in_cup),
                beverageEstimate,
            ),
            icon = CalculationQuantityIconType.CUP_OUTPUT,
            tint = MaterialTheme.colorScheme.tertiary,
        )

        beverageEstimate != null -> BrewQuantitySummaryItemModel(
            tag = BrewQuantitiesBeverageTestTag,
            label = stringResource(R.string.brew_quantities_in_cup),
            value = beverageEstimate,
            supporting = stringResource(R.string.brew_quantities_planned),
            spoken = stringResource(
                R.string.brew_quantities_accessibility_planned,
                stringResource(R.string.brew_quantities_in_cup),
                beverageEstimate,
            ),
            icon = CalculationQuantityIconType.CUP_OUTPUT,
            tint = MaterialTheme.colorScheme.tertiary,
        )

        else -> BrewQuantitySummaryItemModel(
            tag = BrewQuantitiesBeverageTestTag,
            label = stringResource(R.string.brew_quantities_in_cup),
            value = EmDash,
            supporting = stringResource(R.string.brew_quantities_unavailable),
            spoken = stringResource(
                R.string.brew_quantities_accessibility_unavailable,
                stringResource(R.string.brew_quantities_in_cup),
            ),
            icon = CalculationQuantityIconType.CUP_OUTPUT,
            tint = MaterialTheme.colorScheme.tertiary,
            showUnit = false,
        )
    }

    return listOfNotNull(coffee, water, beverage)
}

@Composable
private fun BrewQuantitySummaryItem(
    item: BrewQuantitySummaryItemModel,
    horizontal: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = if (horizontal) 76.dp else 132.dp)
            .testTag(item.tag)
            .clearAndSetSemantics { contentDescription = item.spoken },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BrewQuantityIcon(item)
                BrewQuantityText(item, textAlign = TextAlign.Start)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrewQuantityIcon(item)
                Spacer(modifier = Modifier.height(8.dp))
                BrewQuantityText(item, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun BrewQuantityIcon(item: BrewQuantitySummaryItemModel) {
    CalculationQuantityIcon(
        icon = item.icon,
        contentDescription = null,
        tint = item.tint,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun BrewQuantityText(
    item: BrewQuantitySummaryItemModel,
    textAlign: TextAlign,
) {
    Column(
        horizontalAlignment = if (textAlign == TextAlign.Center) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        },
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
        Text(
            text = if (item.showUnit) {
                stringResource(R.string.brew_quantities_value_grams, item.value)
            } else {
                item.value
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = textAlign,
        )
        Text(
            text = item.supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

private data class BrewQuantitySummaryItemModel(
    val tag: String,
    val label: String,
    val value: String,
    val supporting: String,
    val spoken: String,
    val icon: CalculationQuantityIconType,
    val tint: Color,
    val showUnit: Boolean = true,
)

internal fun formatBrewQuantity(value: Float, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(value)

private fun Float?.validQuantityOrNull(): Float? = this?.takeIf { it.isFinite() && it > 0f }

internal const val BrewQuantitiesSummaryTestTag = "brew_quantities_summary"
internal const val BrewQuantitiesMeasurementActionTestTag = "brew_quantities_measurements_action"
internal const val BrewQuantitiesCoffeeTestTag = "brew_quantities_item_coffee"
internal const val BrewQuantitiesWaterTestTag = "brew_quantities_item_water_in"
internal const val BrewQuantitiesBeverageTestTag = "brew_quantities_item_in_cup"

private const val BrewQuantitiesStackedFontScale = 1.3f
private val BrewQuantitiesStackedWidth = 300.dp
private val BrewQuantitiesOuterPadding = 20.dp
private val BrewQuantitiesItemSpacing = 10.dp
private const val EmDash = "—"
