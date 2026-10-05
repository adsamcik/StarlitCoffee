package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.adsamcik.starlitcoffee.data.brewing.guides.text
import com.adsamcik.starlitcoffee.data.brewing.guides.number
import com.adsamcik.starlitcoffee.ui.component.EquipmentVisual
import com.adsamcik.starlitcoffee.ui.component.EquipmentVisualBadge
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** Source values stay visible; secondary equipment, reasoning and evidence are disclosed together. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ReviewedGuideOverview(guide: ReviewedMethodGuide) {
    var expanded by rememberSaveable(guide.id) { mutableStateOf(false) }
    var evidenceExpanded by rememberSaveable(guide.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EquipmentVisualBadge(EquipmentVisual.profile(guide.profileId), size = 80.dp)
        if (LocalConfiguration.current.locales[0].language != Locale.ENGLISH.language) {
            Text(stringResource(R.string.label_reviewed_guide_english), style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 3, modifier = Modifier.fillMaxWidth()) {
            guide.doseG?.let { GuideQuantity(stringResource(R.string.label_coffee), formatMass(it), Modifier.weight(1f)) }
            val water = guide.waterG?.let { formatMass(it) } ?: guide.waterMl?.let { "${formatNumber(it)} mL" }
            water?.let { GuideQuantity(stringResource(R.string.label_water), it, Modifier.weight(1f)) }
            guide.yieldG?.let { GuideQuantity(stringResource(R.string.label_exact_learn_beverage_yield), formatMass(it), Modifier.weight(1f)) }
        }
        if (guide.doseG == null) Text(stringResource(R.string.msg_reviewed_fill_quantities))
        Text(guide.recipe.text("grind_description"), style = MaterialTheme.typography.bodyMedium)
        val temperature = guide.recipe.getValue("water_temperature_c").jsonObject
        val minC = temperature.number("min")
        val maxC = temperature.number("max")
        val thermalLabel = when {
            minC != null && maxC == minC -> "${formatNumber(minC)} °C"
            minC != null && maxC != null -> "${formatNumber(minC)}–${formatNumber(maxC)} °C"
            maxC != null -> "≤ ${formatNumber(maxC)} °C"
            guide.methodId == "aeropress" || guide.methodId == "clever" -> stringResource(R.string.label_freshly_boiled_water)
            guide.methodId in setOf("espresso", "automatic-drip", "percolator") -> stringResource(R.string.label_machine_temperature)
            else -> null
        }
        thermalLabel?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
        ConnectedGuideDisclosure(stringResource(R.string.heading_exact_recipe_equipment), expanded,
            onExpandedChange = { expanded = it }) {
            guide.equipmentItems().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(guide.scope, style = MaterialTheme.typography.bodyMedium)
            Text(temperature.text("control").replace('_', ' '), style = MaterialTheme.typography.bodyMedium)
        }
        ConnectedGuideDisclosure(stringResource(R.string.heading_reviewed_sources), evidenceExpanded,
            onExpandedChange = { evidenceExpanded = it }) {
            guide.recipe.text("quantity_precision").takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(guide.recipe.text("provenance").replace('_', ' '), style = MaterialTheme.typography.labelLarge)
            guide.sources.forEach { source ->
                TextButton(onClick = { uriHandler.openUri(source.url) }) { Text(source.title) }
            }
            guide.variants.forEach { Text(it.toReadableGuideText(), style = MaterialTheme.typography.bodyMedium) }
            guide.troubleshooting.forEach { Text(it.toReadableGuideText(), style = MaterialTheme.typography.bodyMedium) }
            guide.uncertainties.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

internal fun ReviewedMethodGuide.equipmentItems(): List<String> = recipe.getValue("equipment").jsonArray
    .map { it.jsonPrimitive.content }

private fun kotlinx.serialization.json.JsonObject.toReadableGuideText(): String =
    listOf("name", "symptom", "how_it_differs", "check_first", "adjustment").mapNotNull { key ->
        text(key).takeIf(String::isNotBlank)
    }.joinToString("\n")
