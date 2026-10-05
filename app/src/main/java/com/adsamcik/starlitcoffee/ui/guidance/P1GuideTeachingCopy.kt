package com.adsamcik.starlitcoffee.ui.guidance

/**
 * Concise teaching copy for the existing app-authored six-cup 42/700 recipe.
 * It changes presentation only. Quantities, timing and equipment still come
 * from the exact plan, and the existing English/content/asset gate still applies.
 * Handling rationale: reviewed Chemex guide, 2026-10-02, manufacturer FAQ/care.
 */
internal object P1GuideTeachingCopy {
    data class Step(val title: String, val explanation: String)

    private val reviewedActions = listOf(
        "Place the folded filter with three layers over the spout",
        "Rinse the filter and discard the rinse",
        "Bloom with 100–125 g",
        "Pour in controlled stages to 400 g cumulative",
        "Continue staged pouring to 700 g",
        "Let the thick paper finish draining",
        "Swirl the carafe gently before serving",
    )

    private val chemex = listOf(
        Step("Fit the paper", "The spout groove lets air escape while coffee enters the carafe."),
        Step("Rinse and prepare", "Rinsing wets the paper and warms the carafe. Zeroing after adding the coffee makes the scale show water added."),
        Step("Bloom", "Wet the whole coffee bed before the main pours."),
        Step("Pour to 400 g", "Keep water below the paper rim and leave the spout air channel open."),
        Step("Pour to 700 g", "This is all water added, including the bloom and earlier pours."),
        Step("Let it drain", "Watch the drainage. The suggested time is a reference; flow varies with the coffee and grind."),
        Step("Swirl and serve", "Swirling mixes the coffee before you pour."),
    )

    fun forStage(stage: P1ExactStageGuidance): Step? {
        val index = stage.order - 1
        val matchesReviewedSource = stage.recipeId.value == "chemex_42_700" &&
            stage.sourceStageId == "stage_${stage.order.toString().padStart(2, '0')}" &&
            reviewedActions.getOrNull(index) == stage.action
        return if (matchesReviewedSource) chemex.getOrNull(index) else null
    }
}
