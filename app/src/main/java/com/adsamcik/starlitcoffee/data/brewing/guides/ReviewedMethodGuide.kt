package com.adsamcik.starlitcoffee.data.brewing.guides

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Source-faithful procedure. The complete record is frozen into each brew's recipe snapshot. */
@Serializable
data class ReviewedMethodGuide(
    val id: String,
    val methodId: String,
    val profileId: String,
    val scope: String,
    val reviewedOn: String,
    val sourceSha256: String,
    val originStepId: String,
    val endStepId: String,
    val recipe: JsonObject,
    val safety: List<String>,
    val sources: List<ReviewedGuideSource>,
    val variants: List<JsonObject>,
    val troubleshooting: List<JsonObject>,
    val uncertainties: List<String>,
    val doNotClaim: List<String>,
) {
    val name: String get() = recipe.text("name")
    val methodName: String get() = requireNotNull(REVIEWED_METHOD_NAMES[methodId])
    val doseG: Double? get() = recipe.number("coffee_dose_g")
    val waterG: Double? get() = recipe.number("brew_water_g")
    val waterMl: Double? get() = recipe.number("brew_water_ml")
    val yieldG: Double? get() = recipe.number("target_beverage_g")
    val steps: List<ReviewedGuideStep> get() = recipe.getValue("steps").jsonArray.map { raw ->
        val step = raw.jsonObject
        ReviewedGuideStep(step.text("id"), step.text("instruction"), step.text("why"),
            step.text("completion"), step.number("water_add_g"), step.number("water_cumulative_g"),
            step.number("start_s"), step.number("duration_s"), step.number("water_add_ml"),
            step.number("water_cumulative_ml"), step.text("time_precision"))
    }

    fun validate(): ReviewedMethodGuide = apply {
        require(id.matches(Regex("[a-z][a-z0-9_]+")))
        require(methodId in REVIEWED_METHOD_NAMES && profileId.matches(Regex("[a-z][a-z0-9_]+")))
        require(scope.isNotBlank() && name.isNotBlank() && reviewedOn.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(sourceSha256.matches(Regex("[0-9a-f]{64}")) && sources.isNotEmpty())
        require(sources.all { it.id.isNotBlank() && it.title.isNotBlank() && it.url.startsWith("https://") })
        require(steps.isNotEmpty() && steps.map { it.id }.distinct().size == steps.size)
        require(steps.all { it.instruction.isNotBlank() && it.completion.isNotBlank() })
        val origin = steps.indexOfFirst { it.id == originStepId }
        require(origin >= 0 && steps.indexOfFirst { it.id == endStepId } >= origin)
        require(listOf(doseG, waterG, waterMl, yieldG).all { it == null || it.isFinite() && it > 0.0 })
        require(steps.all { step -> listOf(step.startSeconds, step.durationSeconds).all {
            it == null || it.isFinite() && it in 0.0..604_800.0
        } })
    }
}

@Serializable
data class ReviewedGuideSource(val id: String, val title: String, val url: String)

data class ReviewedGuideStep(
    val id: String,
    val instruction: String,
    val why: String,
    val completion: String,
    val addedWaterG: Double?,
    val cumulativeWaterG: Double?,
    val startSeconds: Double?,
    val durationSeconds: Double?,
    val addedWaterMl: Double? = null,
    val cumulativeWaterMl: Double? = null,
    val timePrecision: String = "",
)

@Serializable
private data class ReviewedGuideDocument(val schemaVersion: Int, val guides: List<ReviewedMethodGuide>)

object ReviewedMethodGuideLibrary {
    const val ASSET_PATH = "brewing/reviewed-method-guides.json"

    fun decode(raw: String): List<ReviewedMethodGuide> {
        val document = Json.decodeFromString<ReviewedGuideDocument>(raw)
        require(document.schemaVersion == 1)
        require(document.guides.map { it.id }.distinct().size == document.guides.size)
        return document.guides.map(ReviewedMethodGuide::validate)
    }
}

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull

val ReviewedMethodGuide.familyId: com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId get() = requireNotNull(
    com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog.instance.findBrewerProfile(
        com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId(profileId))).familyId

val REVIEWED_METHOD_NAMES: Map<String, String> = linkedMapOf(
    "aeropress" to "AeroPress", "automatic-drip" to "Automatic drip", "chemex" to "Chemex",
    "clever" to "Clever", "cold-brew" to "Cold brew", "espresso" to "Espresso",
    "french-press" to "French press", "hario-switch" to "Hario Switch", "kalita-wave" to "Kalita Wave",
    "melitta" to "Melitta", "moka-pot" to "Moka pot", "percolator" to "Percolator",
    "phin" to "Vietnamese phin", "pulsar" to "Pulsar", "siphon" to "Siphon",
    "turkish" to "Turkish coffee", "v60" to "V60",
)
