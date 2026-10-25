package com.adsamcik.starlitcoffee.ui.guidance

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage

/** Reviewed presentation associations belong to source adapters, never to layout code. */
internal data class BrewLiveSourceMetadata(
    @StringRes val instruction: Int? = null,
    @StringRes val warningSummary: Int? = null,
    val warningInHelpWhenMinimal: Boolean = false,
    val warningInHelpWhenFocused: Boolean = false,
    val visualCue: BrewLiveVisualCue? = null,
    val focusOnTarget: Boolean = false,
    val knownWarningRevision: String? = null,
) {
    fun recognizesWarning(warning: String?): Boolean = warning != null &&
        knownWarningRevision == visualContractRevision(listOf(warning))
}

internal data class BrewLiveVisualCue(
    val asset: InstructionAssetRecord,
    @StringRes val description: Int,
    @StringRes val label: Int,
    @StringRes val value: Int,
    @StringRes val reference: Int,
)

/** Explicit reviewed bindings; unknown content receives no inferred technical advice. */
internal fun liveSourceMetadata(
    contentId: StageContentId,
    profileId: String?,
    stageSafety: List<StageSafetyMessage> = emptyList(),
    assets: InstructionAssetCatalog = BuiltInInstructionAssetCatalog.catalog,
): BrewLiveSourceMetadata {
    if (profileId == "cold_immersion_generic" && contentId.value == "cold_immersion_generic_global_safety") {
        return BrewLiveSourceMetadata(
            knownWarningRevision = "8f2e216970b5dcb29b2dd87151592370e79a69f3f7e51b9e4bd8dce0cc3e403d",
            warningInHelpWhenMinimal = stageSafety.any { it.code == "food_refrigerate_4c_during_steep" },
        )
    }
    if (profileId != PulsarLearnGuideCatalog.profileId.value) return BrewLiveSourceMetadata()
    return when (contentId.value) {
        "pulsar_hot_liquid_safety" -> BrewLiveSourceMetadata(
            warningSummary = R.string.msg_brew_pulsar_hot_liquid, warningInHelpWhenMinimal = true,
            knownWarningRevision = "19456199c9f526273143dc0e0945e05111e961bb7bbaacdd74389ef7b4af685c",
        )
        "pulsar_overflow_safety" -> BrewLiveSourceMetadata(
            warningSummary = R.string.msg_brew_pulsar_overflow, warningInHelpWhenMinimal = true,
            knownWarningRevision = "fcd577c9d602b10d28db307c28733907e70321a3f6fce02b0c1d38275d5d979b",
        )
        "pulsar_manual_brew" -> BrewLiveSourceMetadata(instruction = R.string.msg_brew_pulsar_basic_pour)
        "${PulsarLearnGuideCatalog.GUIDE_ID}_stage_06_instruction", "reviewed_pulsar_v1_open_and_pulse" ->
            BrewLiveSourceMetadata(
                instruction = R.string.msg_brew_pulsar_main_pour,
                warningSummary = R.string.msg_brew_pulsar_pour_safety.takeIf {
                    contentId.value == "reviewed_pulsar_v1_open_and_pulse"
                },
                warningInHelpWhenFocused = contentId.value == "reviewed_pulsar_v1_open_and_pulse",
                focusOnTarget = true,
                knownWarningRevision = "c4b8e287f7b4b171054e61abff1439608811d7f654ee7de1993c4d221f536c81",
                visualCue = assets.find(InstructionAssetId("instruction_scene_pulsar_water_height_default"))
                    ?.takeIf { it.review.isApproved }?.let {
                        BrewLiveVisualCue(it, R.string.cd_brew_water_height_target,
                            R.string.label_brew_water_height_target, R.string.label_brew_water_height_value,
                            R.string.label_brew_water_height_reference)
                    },
            )
        else -> BrewLiveSourceMetadata()
    }
}
