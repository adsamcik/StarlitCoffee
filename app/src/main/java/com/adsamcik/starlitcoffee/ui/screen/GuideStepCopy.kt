package com.adsamcik.starlitcoffee.ui.screen

/** Presentation only. Essential operations and warnings never enter the disclosure. */
internal data class GuideStepCopy(
    val instruction: String,
    val target: String? = null,
    val completionCue: String? = null,
    val essentialOperations: List<String> = emptyList(),
    val warning: String? = null,
    val safetyCritical: Boolean = false,
    val explanation: List<String> = emptyList(),
    val altText: String = "",
)
