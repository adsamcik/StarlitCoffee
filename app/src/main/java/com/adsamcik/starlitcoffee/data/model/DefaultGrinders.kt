package com.adsamcik.starlitcoffee.data.model

object DefaultGrinders {
    val grinders: List<Grinder> = listOf(
        Grinder(
            id = "1zpresso-zp6-special",
            brand = "1Zpresso",
            model = "ZP6 Special",
            isManual = true,
            scaleType = GrinderScaleType.DIAL_CLICKS,
            clicksPerRotation = 90,
        ),
        Grinder(
            id = "comandante-c40",
            brand = "Comandante",
            model = "C40",
            isManual = true,
            scaleType = GrinderScaleType.PURE_CLICKS,
        ),
        Grinder(
            id = "fellow-ode-gen2",
            brand = "Fellow",
            model = "Ode Gen 2",
            isManual = false,
            scaleType = GrinderScaleType.NUMBERED_DIAL,
        ),
        Grinder(
            id = "baratza-encore-esp",
            brand = "Baratza",
            model = "Encore ESP",
            isManual = false,
            scaleType = GrinderScaleType.NUMBERED_DIAL,
        ),
        Grinder(
            id = "niche-zero",
            brand = "Niche",
            model = "Zero",
            isManual = false,
            scaleType = GrinderScaleType.NUMBERED_DIAL,
        ),
        Grinder(
            id = "df64",
            brand = "DF64",
            model = "DF64",
            isManual = false,
            scaleType = GrinderScaleType.NUMBERED_DIAL,
        ),
    )

    val recommendations: List<GrindRecommendation> = listOf(
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.PAPER,
            rangeStart = 5.0f,
            rangeEnd = 5.4f,
            suggestedStart = 5.2f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Paper filter Pulsar on ZP6 Special",
        ),
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.METAL_19K,
            rangeStart = 5.2f,
            rangeEnd = 5.6f,
            suggestedStart = 5.4f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Metal 19K filter Pulsar on ZP6 Special",
        ),
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.METAL_40K,
            rangeStart = 5.5f,
            rangeEnd = 5.9f,
            suggestedStart = 5.7f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Metal 40K filter Pulsar on ZP6 Special",
        ),
    )
}
