package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedGuideStep
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import java.security.MessageDigest

/** Hash the English human contract; density and session clocks never change image admission. */
internal fun visualContractRevision(parts: List<String>): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("\u001f").toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

/** Includes canonical self-bindings in coverage without aliasing over their immutable IDs. */
@Suppress("LongParameterList")
data class AdmittedGuidanceTarget(
    val contentId: String,
    val familyId: String,
    val profileId: String,
    val catalog: String,
    val contentRevision: String,
    val sourceAssetId: String,
    val altText: String,
    val purpose: String = "instruction",
)

internal fun BuiltInGuidanceContent.withVisualContract(catalog: String): BuiltInGuidanceContent = copy(
    visualContentRevision = visualContractRevision(visualContractParts(catalog)),
)

internal fun BuiltInGuidanceContent.visualContractParts(catalog: String): List<String> =
    listOf(catalog, familyId.value, profileId?.value.orEmpty(), id.value, placement.name,
        text.primaryInstruction, text.conciseInstruction.orEmpty(), text.explanation.orEmpty(),
        text.tip.orEmpty(), text.warning.orEmpty(), text.altText)

internal fun ReviewedMethodGuide.visualContractRevision(step: ReviewedGuideStep): String =
    visualContractRevision(listOf(id, profileId, scope, step.id, step.instruction, step.why, step.completion))
