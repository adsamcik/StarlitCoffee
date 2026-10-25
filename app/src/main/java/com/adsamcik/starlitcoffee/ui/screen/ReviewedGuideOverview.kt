package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.ui.brewing.BrewOverviewContent
import com.adsamcik.starlitcoffee.ui.brewing.BrewOverviewDetails
import com.adsamcik.starlitcoffee.ui.brewing.BrewOverviewTemperature
import com.adsamcik.starlitcoffee.ui.brewing.BrewQuantityUnit
import com.adsamcik.starlitcoffee.ui.brewing.toBrewOverviewContent

/** Compatibility adapter: all source interpretation belongs to the presentation adapter. */
@Composable
internal fun ReviewedGuideOverview(guide: ReviewedMethodGuide) = BrewRecipeOverview(guide.toBrewOverviewContent())

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BrewRecipeOverview(content: BrewOverviewContent) {
    var expanded by rememberSaveable(content.id) { mutableStateOf(false) }
    var evidenceExpanded by rememberSaveable(content.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (content.sourceLanguage == "en" && LocalConfiguration.current.locales[0].language != "en") {
            Text(stringResource(R.string.label_reviewed_guide_english), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (content.quantities.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 3,
                modifier = Modifier.fillMaxWidth()) {
                content.quantities.forEach { quantity ->
                    val value = when (quantity.unit) {
                        BrewQuantityUnit.GRAMS -> formatMass(quantity.value)
                        BrewQuantityUnit.MILLILITRES -> "${formatNumber(quantity.value)} mL"
                    }
                    GuideQuantity(stringResource(quantity.labelRes), value, Modifier.weight(1f))
                }
            }
        }
        if (content.requiresMeasuredDose) Text(stringResource(R.string.msg_reviewed_fill_quantities))
        content.introduction.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        content.temperature?.let { Text(overviewTemperatureLabel(it), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface) }
        if (content.details.isPresent) {
            ConnectedGuideDisclosure(stringResource(R.string.heading_exact_learn_recipe_summary), expanded,
                onExpandedChange = { expanded = it }) { BrewOverviewDetailsContent(content.details) }
        }
        if (content.evidence.isPresent) {
            ConnectedGuideDisclosure(stringResource(R.string.heading_reviewed_sources), evidenceExpanded,
                onExpandedChange = { evidenceExpanded = it }) {
                content.evidence.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                content.evidence.sources.forEach { source ->
                    TextButton(onClick = { uriHandler.openUri(source.url) }) { Text(source.title) }
                }
            }
        }
    }
}

@Composable
private fun BrewOverviewDetailsContent(details: BrewOverviewDetails) {
    details.ratios.forEachIndexed { index, ratio ->
        DetailsRow(stringResource(if (index == 0) R.string.label_ratio else R.string.label_exact_recipe_combined_ratio),
            stringResource(R.string.format_exact_recipe_ratio,
                ratio.ratioValue?.let(::formatNumber) ?: stringResource(R.string.label_exact_recipe_unresolved),
                ratioDenominatorLabel(ratio.includedDenominatorRoles)))
    }
    details.temperature?.let { DetailsRow(stringResource(R.string.label_temperature), temperatureLabel(it)) }
    details.expectedTime?.let { DetailsRow(stringResource(R.string.label_brew_time), timeLabel(it)) }
    if (details.equipmentOptions.isNotEmpty()) {
        val equipmentLabels = details.equipmentOptions.map { equipmentOptionLabel(it) }
        DetailsRow(stringResource(R.string.heading_exact_recipe_equipment),
            equipmentLabels.joinToString(stringResource(R.string.separator_exact_recipe_equipment_alternatives)))
    }
    details.completion?.let { DetailsRow(stringResource(R.string.label_exact_learn_completion), exactCompletionLabel(it)) }
    details.facts.forEach { DetailsRow(stringResource(it.labelRes), it.value) }
    details.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

private fun overviewTemperatureLabel(temperature: BrewOverviewTemperature): String {
    val minimum = temperature.minimumC
    val maximum = temperature.maximumC
    return when {
        minimum != null && maximum == minimum -> "${formatNumber(minimum)} °C"
        minimum != null && maximum != null -> "${formatNumber(minimum)}–${formatNumber(maximum)} °C"
        maximum != null -> "≤ ${formatNumber(maximum)} °C"
        minimum != null -> "≥ ${formatNumber(minimum)} °C"
        else -> ""
    }
}
