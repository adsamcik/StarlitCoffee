package com.adsamcik.starlitcoffee.ui.guidance

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewWarning
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
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
    @StringRes val helpInstruction: Int? = null,
    val knownInstructionRevision: String? = null,
) {
    fun recognizesWarning(warning: String?): Boolean = warning != null &&
        knownWarningRevision == visualContractRevision(listOf(warning))

    fun recognizesInstruction(instruction: String?): Boolean = instruction != null &&
        knownInstructionRevision == visualContractRevision(listOf(instruction))
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
            knownInstructionRevision = "b1d7a6685b3032ad7e29601ad92bfceef5e21f00c4d20eb104d809d3e5eba5d0",
            warningSummary = R.string.msg_brew_pulsar_hot_liquid, warningInHelpWhenMinimal = true,
            knownWarningRevision = "19456199c9f526273143dc0e0945e05111e961bb7bbaacdd74389ef7b4af685c",
        )
        "pulsar_overflow_safety" -> BrewLiveSourceMetadata(
            knownInstructionRevision = "fadabdc43da8944b12016c2eda1597c9b01789ba4807ca32f112e9554526b10a",
            warningSummary = R.string.msg_brew_pulsar_overflow, warningInHelpWhenMinimal = true,
            knownWarningRevision = "fcd577c9d602b10d28db307c28733907e70321a3f6fce02b0c1d38275d5d979b",
        )
        "pulsar_manual_brew" -> BrewLiveSourceMetadata(instruction = R.string.msg_brew_pulsar_basic_pour)
        "${PulsarLearnGuideCatalog.GUIDE_ID}_stage_06_instruction", "reviewed_pulsar_v1_open_and_pulse" ->
            BrewLiveSourceMetadata(
                instruction = R.string.msg_brew_pulsar_main_pour,
                helpInstruction = R.string.msg_brew_pulsar_main_pour_help,
                knownInstructionRevision = "87f0d411a05291b256a0b89a7f7e453798bf5ef09c0b93dd4929300bf0a47af2",
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

/** Keep confirmations precise for saved plans whose combined pour step was classified as observation. */
internal fun liveSourceConfirmationAction(
    action: BrewStageAction?,
    contentId: StageContentId?,
    profileId: String?,
): BrewStageAction? = if (action == BrewStageAction.OBSERVE && profileId == PulsarLearnGuideCatalog.profileId.value &&
    contentId?.value == "reviewed_pulsar_v1_open_and_pulse") BrewStageAction.POUR else action

/** Familiar notes live in Help; an immediate lifting cue remains at removal. Unknown revisions stay live. */
internal fun liveSourceWarnings(
    warnings: List<LiveBrewWarning>,
    profileId: String?,
    contentId: StageContentId?,
    action: BrewStageAction?,
): List<LiveBrewWarning> {
    if (profileId != PulsarLearnGuideCatalog.profileId.value || !isReviewedPulsarStage(contentId, action)) return warnings
    return warnings.flatMap { warning ->
        warning.text.lineSequence().filter(String::isNotBlank).mapNotNull { line ->
            when (visualContractRevision(listOf(line))) {
                "e6588f44882e4570bd1c02256709e66c689e13ce0697af2a2c76ae4515a3ba15" ->
                    warning.copy(text = line, summaryResource = R.string.warning_live_lift_base)
                        .takeIf { action == BrewStageAction.RELEASE }
                "c95b8d5db3713f0f5b767ace4b5a982039ee3db6161baf100e3e77e3de317a45",
                "378b5b4a4b003536c7baad6c3695c35659422afe4d8cbdd07e13c2ed13fd4708",
                "720fa02b8e91c844efac06c5a1665f46e6c3ef2d505737b238db26d298e2b7bc",
                "33f5aafea8cf1b25dbf3b33b46b7204a1be53e42e02d3ad296f9bf6bb8600d3f" -> null
                else -> warning.copy(text = line)
            }
        }.toList()
    }.distinctBy { it.summaryResource to if (it.summaryResource == null) it.text else null }
}

private fun isReviewedPulsarStage(contentId: StageContentId?, action: BrewStageAction?): Boolean = when (contentId?.value) {
    "reviewed_pulsar_v1_prime_paper" -> action == BrewStageAction.RINSE
    "reviewed_pulsar_v1_dose_level_tare" -> action == BrewStageAction.ADD_COFFEE
    "reviewed_pulsar_v1_open_start_bloom", "reviewed_pulsar_v1_retained_bloom" -> action == BrewStageAction.BLOOM
    "reviewed_pulsar_v1_open_and_pulse" -> action in setOf(BrewStageAction.OBSERVE, BrewStageAction.POUR)
    "reviewed_pulsar_v1_drain_and_serve" -> action == BrewStageAction.RELEASE
    else -> false
}

/** The completed Pulsar pour amount is a recipe reference once final drawdown begins. */
internal fun hasSpentWaterReference(action: BrewStageAction?, contentId: StageContentId?, profileId: String?): Boolean =
    action == BrewStageAction.RELEASE && profileId == PulsarLearnGuideCatalog.profileId.value &&
        contentId?.value == "reviewed_pulsar_v1_drain_and_serve"
