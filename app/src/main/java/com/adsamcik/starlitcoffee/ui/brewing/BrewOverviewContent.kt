package com.adsamcik.starlitcoffee.ui.brewing

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.P1CompletionSemantics
import com.adsamcik.starlitcoffee.domain.brewing.P1EquipmentOption
import com.adsamcik.starlitcoffee.domain.brewing.P1RatioSemantics
import com.adsamcik.starlitcoffee.domain.brewing.P1TemperatureSemantics
import com.adsamcik.starlitcoffee.domain.brewing.P1TimeSemantics

/** Source adapters supply facts; composables do not inspect recipe JSON or infer brewer behavior. */
data class BrewOverviewContent(
    val id: String,
    val profileId: String? = null,
    val quantities: List<BrewOverviewQuantity> = emptyList(),
    val introduction: List<String> = emptyList(),
    val temperature: BrewOverviewTemperature? = null,
    val details: BrewOverviewDetails = BrewOverviewDetails(),
    val evidence: BrewOverviewEvidence = BrewOverviewEvidence(),
    val requiresMeasuredDose: Boolean = false,
    val sourceLanguage: String? = null,
)

data class BrewOverviewQuantity(@param:StringRes val labelRes: Int, val value: Double, val unit: BrewQuantityUnit)

enum class BrewQuantityUnit { GRAMS, MILLILITRES }

data class BrewOverviewTemperature(val minimumC: Double?, val maximumC: Double?)

data class BrewOverviewDetails(
    val facts: List<BrewContentFact> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    val ratios: List<P1RatioSemantics> = emptyList(),
    val temperature: P1TemperatureSemantics? = null,
    val expectedTime: P1TimeSemantics? = null,
    val equipmentOptions: List<P1EquipmentOption> = emptyList(),
    val completion: P1CompletionSemantics? = null,
) {
    val isPresent: Boolean
        get() = listOf(facts.isNotEmpty(), paragraphs.isNotEmpty(), ratios.isNotEmpty(), temperature != null,
            expectedTime != null, equipmentOptions.isNotEmpty(), completion != null).any { it }
}

data class BrewOverviewEvidence(val notes: List<String> = emptyList(), val sources: List<BrewOverviewSource> = emptyList()) {
    val isPresent: Boolean get() = notes.isNotEmpty() || sources.isNotEmpty()
}

data class BrewOverviewSource(val title: String, val url: String)

data class BrewReaderContent(
    val key: String,
    val steps: List<BrewStepContent> = emptyList(),
    val commonContent: List<BrewStepContent> = emptyList(),
    val overview: BrewOverviewContent? = null,
) {
    val readableSteps: List<BrewStepContent> get() = steps.filter { it.isReadable }

    /** Navigation follows source identity when optional records change the visible list. */
    fun readableStepIndexFor(contentId: StageContentId?): Int =
        readableSteps.indexOfFirst { it.id == contentId }.coerceAtLeast(0)
}
