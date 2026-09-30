package com.adsamcik.starlitcoffee.data.model

/** Small, source-backed in-memory fallback. Production uses the complete asset catalog. */
object DefaultGrinders : GrinderDataProvider {
    override val grinders: List<Grinder> = listOf(
        Grinder(
            id = "1zpresso-zp6-special",
            brand = "1Zpresso",
            model = "ZP6 Special",
            isManual = true,
            scaleType = GrinderScaleType.DIAL_CLICKS,
            clicksPerRotation = 90,
            dial = GrinderDial(GrinderDialNotation.DECIMAL_CLICKS, minimum = 0f, stepSize = 0.1f, numbersPerRotation = 9),
            sourceUrls = listOf("https://1zpresso.coffee/grind-setting/"),
        ),
    )

    override val recommendations: List<GrindRecommendation> = listOf(
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.PAPER,
            rangeStart = 5f,
            rangeEnd = 6f,
            suggestedStart = 5.2f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Pulsar paper — Kaldi's published 5–6 starting range; tune to the recipe and taste",
            sourceUrls = listOf("https://kaldiscoffee.com/blogs/recipes/nextlevel-pulsar-dripper-brew-recipe"),
        ),
    )
}
