package com.adsamcik.starlitcoffee.ui.brewing

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.ui.guidance.GuidanceOperationalCue
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation

/** Optional teaching content; execution, clocks and event permissions remain in the session model. */
@Suppress("LongParameterList")
data class BrewStepContent(
    val id: StageContentId,
    val title: String? = null,
    val action: BrewStageAction? = null,
    val instruction: String? = null,
    val target: String? = null,
    val completionCue: String? = null,
    val referenceCues: List<BrewStageReferenceCuePresentation> = emptyList(),
    val facts: List<BrewContentFact> = emptyList(),
    val operations: List<String> = emptyList(),
    val controlRequirements: List<GuidanceOperationalCue> = emptyList(),
    val utilities: List<GuidanceOperationalCue> = emptyList(),
    val warning: String? = null,
    val safetyCritical: Boolean = false,
    val details: List<String> = emptyList(),
    val illustration: BrewStepIllustration? = null,
) {
    val primaryReferenceCue: BrewStageReferenceCuePresentation?
        get() = referenceCues.primaryBrewReferenceCue()

    val isReadable: Boolean
        get() {
            val text = listOf(instruction, target, completionCue, warning) + facts.map { it.value } + operations + details
            val suppliedContent = listOf(referenceCues.isNotEmpty(), controlRequirements.isNotEmpty(),
                utilities.isNotEmpty(), illustration != null)
            return text.any { it.normalizedBrewText() != null } || suppliedContent.any { it }
        }
}

data class BrewContentFact(@param:StringRes val labelRes: Int, val value: String)

data class BrewStepIllustration(val asset: InstructionAssetRecord, val description: String) {
    init {
        require(asset.review.isApproved) { "Only admitted artwork may enter step presentation" }
        require(description.normalizedBrewText() != null) { "An illustration requires a truthful accessible caption" }
    }
}

fun approvedBrewStepIllustration(asset: InstructionAssetRecord?, description: String?): BrewStepIllustration? {
    val caption = description.normalizedBrewText()
    return if (asset?.review?.isApproved == true && caption != null) BrewStepIllustration(asset, caption) else null
}

fun List<BrewStageReferenceCuePresentation>.primaryBrewReferenceCue(): BrewStageReferenceCuePresentation? =
    filterIsInstance<BrewStageReferenceCuePresentation.Mass>()
        .firstOrNull { it.reference == StageMassReference.BREW_CUMULATIVE }
        ?: firstOrNull { it is BrewStageReferenceCuePresentation.Mass }
        ?: firstOrNull()

internal fun String?.normalizedBrewText(): String? = this?.trim()?.takeIf { text ->
    text.isNotEmpty() && text.lowercase() !in setOf("none", "as specified", "not applicable or not specified", "n/a", "—")
}
