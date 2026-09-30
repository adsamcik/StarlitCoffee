package com.adsamcik.starlitcoffee.data.model

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Common interface for grinder data providers.
 * [DefaultGrinders] provides a static fallback (used in tests),
 * while [GrinderDataSource] loads from `assets/grinders.json` at runtime.
 */
interface GrinderDataProvider {
    val grinders: List<Grinder>
    val recommendations: List<GrindRecommendation>
}

fun GrinderDataProvider.recommendationFor(
    grinderId: String,
    method: BrewMethod,
    filterType: FilterType?,
): GrindRecommendation? {
    val filter = filterType.takeIf { method == BrewMethod.PULSAR }
    return recommendations.find {
        it.grinderId == grinderId && it.methodId == method.name && it.filterType == filter
    }
}

fun GrinderDataProvider.grindersFor(method: BrewMethod, filterType: FilterType?): List<Grinder> =
    grinders.filter { recommendationFor(it.id, method, filterType) != null }

@Serializable
private data class GrinderJson(
    val id: String,
    val brand: String,
    val model: String,
    val isManual: Boolean,
    val scaleType: String,
    val clicksPerRotation: Int? = null,
    val dial: GrinderDial,
    val sourceUrls: List<String>,
)

@Serializable
private data class RecommendationJson(
    val grinderId: String,
    val methodId: String,
    val filterType: String? = null,
    val rangeStart: Float,
    val rangeEnd: Float,
    val suggestedStart: Float,
    val adjustmentStepSize: Float,
    val adjustmentNote: String,
    val sourceUrls: List<String>,
)

@Serializable
private data class GrinderDataJson(
    val grinders: List<GrinderJson>,
    val recommendations: List<RecommendationJson>,
)

class GrinderDataSource private constructor(
    override val grinders: List<Grinder>,
    override val recommendations: List<GrindRecommendation>,
) : GrinderDataProvider {
    companion object {
        @Volatile
        private var INSTANCE: GrinderDataSource? = null

        private val json = Json { ignoreUnknownKeys = true }

        fun getInstance(context: Context): GrinderDataSource {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: load(context).also { INSTANCE = it }
            }
        }

        private fun load(context: Context): GrinderDataSource {
            val raw = context.assets.open("grinders.json")
                .bufferedReader()
                .use { it.readText() }
            return fromJson(raw)
        }

        internal fun fromJson(raw: String): GrinderDataSource {
            val data = json.decodeFromString<GrinderDataJson>(raw)

            val grinders = data.grinders.map { g ->
                Grinder(
                    id = g.id,
                    brand = g.brand,
                    model = g.model,
                    isManual = g.isManual,
                    scaleType = GrinderScaleType.valueOf(g.scaleType),
                    clicksPerRotation = g.clicksPerRotation,
                    dial = g.dial,
                    sourceUrls = g.sourceUrls,
                )
            }

            val recommendations = data.recommendations.map { r ->
                GrindRecommendation(
                    grinderId = r.grinderId,
                    methodId = r.methodId,
                    filterType = r.filterType?.let { FilterType.valueOf(it) },
                    rangeStart = r.rangeStart,
                    rangeEnd = r.rangeEnd,
                    suggestedStart = r.suggestedStart,
                    adjustmentStepSize = r.adjustmentStepSize,
                    adjustmentNote = r.adjustmentNote,
                    sourceUrls = r.sourceUrls,
                )
            }

            validateCatalog(grinders, recommendations)
            return GrinderDataSource(grinders, recommendations)
        }

        private fun validateCatalog(grinders: List<Grinder>, recommendations: List<GrindRecommendation>) {
            require(grinders.map { it.id }.distinct().size == grinders.size) { "Duplicate grinder IDs" }
            require(recommendations.map { Triple(it.grinderId, it.methodId, it.filterType) }.distinct().size ==
                recommendations.size) { "Duplicate grinder recommendations" }
            grinders.forEach { grinder ->
                validateGrinder(grinder)
                require(recommendations.any { it.grinderId == grinder.id }) { "Grinder has no supported method" }
            }
            recommendations.forEach { recommendation ->
                val grinder = requireNotNull(grinders.find { it.id == recommendation.grinderId })
                validateRecommendation(grinder, recommendation)
            }
        }

        private fun validateGrinder(grinder: Grinder) {
            val dial = requireNotNull(grinder.dial)
            require(grinder.sourceUrls.isNotEmpty() && grinder.sourceUrls.all(::isSourceUrl))
            require(dial.minimum.isFinite() && (dial.maximum == null ||
                dial.maximum.isFinite() && dial.maximum > dial.minimum))
            require(dial.stepSize == null || dial.stepSize.isFinite() && dial.stepSize > 0f)
            when (dial.notation) {
                GrinderDialNotation.DECIMAL_CLICKS -> {
                    require(dial.minimum == 0f && dial.stepSize == 0.1f)
                    require(requireNotNull(dial.numbersPerRotation) > 0)
                    require(grinder.clicksPerRotation == dial.numbersPerRotation * 10)
                }
                GrinderDialNotation.CLICK_COUNT -> require(dial.minimum == 0f && dial.stepSize == 1f)
                GrinderDialNotation.FELLOW_ODE ->
                    require(dial.minimum == 1f && dial.maximum == 11f && dial.stepSize == 1f / 3f)
                GrinderDialNotation.NUMBERED -> Unit
            }
        }

        private fun validateRecommendation(grinder: Grinder, recommendation: GrindRecommendation) {
            val dial = requireNotNull(grinder.dial)
            val method = BrewMethod.valueOf(recommendation.methodId)
            require(method == BrewMethod.PULSAR || recommendation.filterType == null)
            require(method != BrewMethod.PULSAR || recommendation.filterType != null)
            require(recommendation.sourceUrls.isNotEmpty() && recommendation.sourceUrls.all(::isSourceUrl))
            require(listOf(recommendation.rangeStart, recommendation.rangeEnd, recommendation.suggestedStart)
                .all(dial::accepts)) { "Recommendation cannot be set on ${grinder.id}" }
            require(recommendation.suggestedStart in recommendation.rangeStart..recommendation.rangeEnd)
            require(recommendation.adjustmentStepSize.isFinite() && recommendation.adjustmentStepSize > 0f)
            require(dial.stepSize == null || dial.accepts(dial.minimum + recommendation.adjustmentStepSize))
        }

        private fun isSourceUrl(value: String): Boolean =
            runCatching { java.net.URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() }
                ?: false
    }
}
