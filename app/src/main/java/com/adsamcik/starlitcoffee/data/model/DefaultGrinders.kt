package com.adsamcik.starlitcoffee.data.model

/**
 * Static fallback grinder data — used in tests and when [GrinderDataSource] is unavailable.
 * Production code should prefer [GrinderDataSource.getInstance] which loads from `assets/grinders.json`.
 */
object DefaultGrinders : GrinderDataProvider {
    override val grinders: List<Grinder> = listOf(
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

    override val recommendations: List<GrindRecommendation> = listOf(
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.PAPER,
            rangeStart = 4.3f,
            rangeEnd = 6.5f,
            suggestedStart = 5.2f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Pulsar + paper — Rao-style default 5.2; community range 4.3–6.5",
        ),
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.METAL_40K,
            rangeStart = 5.3f,
            rangeEnd = 5.7f,
            suggestedStart = 5.5f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Pulsar 40K — finer mesh; slight coarsen vs paper to limit clogging",
        ),
        GrindRecommendation(
            grinderId = "1zpresso-zp6-special",
            methodId = "PULSAR",
            filterType = FilterType.METAL_19K,
            rangeStart = 5.5f,
            rangeEnd = 5.9f,
            suggestedStart = 5.7f,
            adjustmentStepSize = 0.2f,
            adjustmentNote = "Pulsar 19K — coarsest, 150 µm holes for body & boost",
        ),
    )
}
