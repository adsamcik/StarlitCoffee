package com.adsamcik.starlitcoffee.ui.brewing

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.data.brewing.guides.number
import com.adsamcik.starlitcoffee.data.brewing.guides.text
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactLearnGuide
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun ReviewedMethodGuide.toBrewOverviewContent(): BrewOverviewContent {
    val thermal = recipe["water_temperature_c"] as? JsonObject
    val minimum = thermal?.number("min")
    val maximum = thermal?.number("max")
    val equipment = (recipe["equipment"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull.normalizedBrewText() }
    val paragraphs = equipment + listOfNotNull(scope.normalizedBrewText(),
        thermal?.text("control")?.replace('_', ' ').normalizedBrewText())
    return BrewOverviewContent(id = id, profileId = profileId,
        quantities = listOfNotNull(
            doseG?.let { BrewOverviewQuantity(R.string.label_coffee, it, BrewQuantityUnit.GRAMS) },
            waterG?.let { BrewOverviewQuantity(R.string.label_water, it, BrewQuantityUnit.GRAMS) }
                ?: waterMl?.let { BrewOverviewQuantity(R.string.label_water, it, BrewQuantityUnit.MILLILITRES) },
            yieldG?.let { BrewOverviewQuantity(R.string.label_exact_learn_beverage_yield, it, BrewQuantityUnit.GRAMS) }),
        introduction = listOfNotNull(recipe.text("grind_description").normalizedBrewText()),
        temperature = if (minimum != null || maximum != null) BrewOverviewTemperature(minimum, maximum) else null,
        details = BrewOverviewDetails(paragraphs = paragraphs.distinct()),
        evidence = BrewOverviewEvidence(notes = (
            listOf(recipe.text("quantity_precision"), recipe.text("provenance").replace('_', ' ')) +
                variants.map { it.readableSourceText() } + troubleshooting.map { it.readableSourceText() } + uncertainties
            ).mapNotNull { it.normalizedBrewText() }.distinct(),
            sources = sources.map { BrewOverviewSource(it.title, it.url) }),
        requiresMeasuredDose = doseG == null, sourceLanguage = "en")
}

fun P1ExactLearnGuide.toBrewOverviewContent(): BrewOverviewContent {
    val quantities = recipe.quantities
    val water = quantities.brewWaterInputG ?: quantities.reservoirInputG
    val waterLabel = if (quantities.brewWaterInputG != null) R.string.label_brewer_profile_input_water
        else R.string.label_brewer_profile_input_reservoir_water
    return BrewOverviewContent(id = recipe.id.value,
        quantities = listOfNotNull(
            BrewOverviewQuantity(R.string.label_coffee, quantities.dryCoffeeDoseG, BrewQuantityUnit.GRAMS),
            water?.let { BrewOverviewQuantity(waterLabel, it, BrewQuantityUnit.GRAMS) },
            quantities.iceG.takeIf { it > 0.0 }?.let {
                BrewOverviewQuantity(R.string.label_exact_recipe_brew_ice, it, BrewQuantityUnit.GRAMS) }),
        details = BrewOverviewDetails(
            facts = listOf(BrewContentFact(R.string.label_exact_learn_provenance,
                "${guidance.evidenceStatus} · ${guidance.originalSourceOrProvenance}")),
            ratios = recipe.ratios, temperature = recipe.temperature, expectedTime = recipe.expectedTime,
            equipmentOptions = recipe.equipmentOptions, completion = recipe.completion))
}

private fun JsonObject.readableSourceText(): String =
    listOf("name", "symptom", "how_it_differs", "check_first", "adjustment")
        .mapNotNull { text(it).normalizedBrewText() }.joinToString("\n")
