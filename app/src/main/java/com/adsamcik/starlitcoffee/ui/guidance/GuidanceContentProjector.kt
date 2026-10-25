package com.adsamcik.starlitcoffee.ui.guidance

/** Shared, resource-free density projection for Learn, live Brew and Help. */
internal fun BuiltInGuidanceContent.projectGuidanceContent(
    level: GuidancePresentationLevel,
): AuthoredGuidancePresentation {
    authoredPresentations[level]?.let { authored ->
        // Safety remains independent of optional copy and authored density omissions.
        return authored.copy(warning = authored.warning ?: text.warning)
    }
    val supporting = level == GuidancePresentationLevel.FULL || level == GuidancePresentationLevel.CUSTOM
    val compact = level == GuidancePresentationLevel.CONCISE ||
        level == GuidancePresentationLevel.FOCUSED || level == GuidancePresentationLevel.UTILITIES_ONLY
    return AuthoredGuidancePresentation(
        instruction = (if (compact) text.conciseInstruction ?: text.primaryInstruction else text.primaryInstruction)
            .takeIf(String::isNotBlank),
        target = text.target,
        completionCue = text.completionCue,
        explanation = text.explanation.takeIf { supporting },
        practicalTip = text.tip.takeIf { supporting },
        nextAction = text.nextAction,
        controlRequirements = text.controlRequirements,
        warning = text.warning,
        utilities = text.utilities,
        accessibleAltText = text.altText,
    )
}
