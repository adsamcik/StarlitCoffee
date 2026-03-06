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

@Serializable
private data class GrinderJson(
    val id: String,
    val brand: String,
    val model: String,
    val isManual: Boolean,
    val scaleType: String,
    val clicksPerRotation: Int? = null,
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
            val data = json.decodeFromString<GrinderDataJson>(raw)

            val grinders = data.grinders.map { g ->
                Grinder(
                    id = g.id,
                    brand = g.brand,
                    model = g.model,
                    isManual = g.isManual,
                    scaleType = GrinderScaleType.valueOf(g.scaleType),
                    clicksPerRotation = g.clicksPerRotation,
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
                )
            }

            return GrinderDataSource(grinders, recommendations)
        }
    }
}
