package com.adsamcik.starlitcoffee.ui.guidance

import android.content.Context
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Standalone Learn curriculum for Jonathan Gagné's 20 g / 340 g Pulsar recipe.
 *
 * This intentionally does not join the immutable exact-session recipe contract. It is a
 * timer-free educational guide whose quantities and valve sequence are kept together as one
 * sourced method.
 */
object PulsarLearnGuideCatalog {
    const val GUIDE_ID = "pulsar_gagne_20_340"

    val familyId = MethodFamilyId("valve_controlled_no_bypass")
    val profileId = BrewerProfileId("pulsar_standard")
    val stageIds: List<StageId> = (1..8).map(::stageId)

    private const val SCHEMA_VERSION = 1
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun load(context: Context): BuiltInGuidanceCatalog {
        val applicationContext = context.applicationContext
        val localeTag = P1ExactGuidanceLocaleResolver.resolve(applicationContext)
        val encoded = applicationContext.resources.openRawResource(R.raw.pulsar_learn_guidance)
            .bufferedReader()
            .use { reader -> reader.readText() }
        return decode(encoded, expectedLocaleTag = localeTag)
    }

    fun decode(
        encoded: String,
        expectedLocaleTag: String = P1ExactGuidanceLocaleResolver.DEFAULT_LANGUAGE_TAG,
    ): BuiltInGuidanceCatalog {
        val document = json.decodeFromString<PulsarLearnGuideDocument>(encoded)
        require(document.schemaVersion == SCHEMA_VERSION) { "Pulsar Learn schema mismatch" }
        require(document.guideId == GUIDE_ID) { "Pulsar Learn guide identity mismatch" }
        require(document.locale == expectedLocaleTag) { "Pulsar Learn locale mismatch" }
        require(document.releaseStatus == "released") { "Pulsar Learn translation is not released" }
        require(document.stages.map(PulsarLearnStageSpec::number) == (1..8).toList()) {
            "Pulsar Learn stages must be complete and ordered"
        }
        return BuiltInGuidanceCatalog(document.stages.map(::stage))
    }

    private fun stage(spec: PulsarLearnStageSpec): BuiltInGuidanceContent = BuiltInGuidanceContent(
        id = contentId(spec.number),
        familyId = familyId,
        profileId = profileId,
        stageId = stageId(spec.number),
        placement = BuiltInGuidancePlacement.LIVE_STAGE,
        text = GuidanceTextMetadata(
            primaryInstruction = spec.instruction,
            conciseInstruction = spec.conciseInstruction,
            explanation = spec.explanation,
            tip = spec.practicalTip,
            warning = spec.warning,
            altText = spec.altText,
        ),
        visibility = if (spec.safetyCritical) {
            GuidanceVisibilityPolicy(visibleIn = emptySet(), alwaysVisible = true)
        } else {
            GuidanceVisibilityPolicy()
        },
        safetyCritical = spec.safetyCritical,
        authoredPresentations = GuidancePresentationLevel.entries.associateWith { level ->
            val showDetails = level in setOf(
                GuidancePresentationLevel.FULL,
                GuidancePresentationLevel.CUSTOM,
            )
            AuthoredGuidancePresentation(
                instruction = if (level == GuidancePresentationLevel.FULL) {
                    spec.instruction
                } else {
                    spec.conciseInstruction
                },
                target = spec.target,
                completionCue = spec.completionCue,
                explanation = spec.explanation.takeIf { showDetails },
                practicalTip = spec.practicalTip.takeIf { showDetails },
                nextAction = null,
                controlRequirements = spec.controls,
                warning = spec.warning,
                utilities = spec.utilities,
                accessibleAltText = spec.altText,
            )
        },
    )

    @Serializable
    private data class PulsarLearnGuideDocument(
        @SerialName("schema_version") val schemaVersion: Int,
        @SerialName("guide_id") val guideId: String,
        val locale: String,
        @SerialName("release_status") val releaseStatus: String,
        val stages: List<PulsarLearnStageSpec>,
    )

    @Serializable
    private data class PulsarLearnStageSpec(
        val number: Int,
        val instruction: String,
        @SerialName("concise_instruction") val conciseInstruction: String,
        val target: String,
        @SerialName("completion_cue") val completionCue: String,
        val explanation: String,
        @SerialName("practical_tip") val practicalTip: String,
        @SerialName("alt_text") val altText: String,
        val warning: String? = null,
    ) {
        val controls: List<GuidanceOperationalCue>
            get() = when (number) {
                1, 4, 8 -> listOf(GuidanceOperationalCue.VALVE_STATE)
                3, 5, 6 -> listOf(
                    GuidanceOperationalCue.VALVE_STATE,
                    GuidanceOperationalCue.ELAPSED_TIMER,
                )
                7 -> listOf(GuidanceOperationalCue.ELAPSED_TIMER)
                else -> emptyList()
            }

        val utilities: List<GuidanceOperationalCue>
            get() = if (number in setOf(2, 3, 6)) {
                listOf(GuidanceOperationalCue.CUMULATIVE_WATER_TARGET)
            } else {
                emptyList()
            }

        val safetyCritical: Boolean
            get() = number == 8
    }

    private fun stageId(number: Int): StageId = StageId(
        "${GUIDE_ID}_stage_${number.toString().padStart(2, '0')}",
    )

    private fun contentId(number: Int): StageContentId = StageContentId(
        "${stageId(number).value}_instruction",
    )
}
