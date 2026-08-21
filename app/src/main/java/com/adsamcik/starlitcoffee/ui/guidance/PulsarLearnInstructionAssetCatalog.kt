package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import java.time.LocalDate

/** Reviewed, locally packaged illustrations for the standalone Pulsar Learn guide. */
object PulsarLearnInstructionAssetCatalog {
    const val TRACKER_PATH = "docs/brewing/pulsar-learning-guide-production-2026-08-19.json"
    private const val PROMPT_PATH =
        "docs/brewing/pulsar-learning-guide-image-prompts-2026-08-19.md"

    fun runtimeAssets(): List<InstructionAssetRecord> = listOf(
        asset(1, R.drawable.instruction_pulsar_gagne_20_340_stage_01_instruction_default, "v3"),
        asset(2, R.drawable.instruction_pulsar_gagne_20_340_stage_02_instruction_default, "v3"),
        asset(3, R.drawable.instruction_pulsar_gagne_20_340_stage_03_instruction_default, "v3"),
        asset(4, R.drawable.instruction_pulsar_gagne_20_340_stage_04_instruction_default, "v1"),
        asset(5, R.drawable.instruction_pulsar_gagne_20_340_stage_05_instruction_default, "v2"),
        asset(6, R.drawable.instruction_pulsar_gagne_20_340_stage_06_instruction_default, "v2"),
        asset(7, R.drawable.instruction_pulsar_gagne_20_340_stage_07_instruction_default, "v2"),
        asset(
            number = 8,
            drawableRes = R.drawable.instruction_pulsar_gagne_20_340_stage_08_instruction_default,
            revision = "v2",
            safetySensitive = true,
        ),
    )

    private fun asset(
        number: Int,
        drawableRes: Int,
        revision: String,
        safetySensitive: Boolean = false,
    ): InstructionAssetRecord {
        val suffix = number.toString().padStart(2, '0')
        val stageId = StageId("${PulsarLearnGuideCatalog.GUIDE_ID}_stage_$suffix")
        val contentId = StageContentId("${stageId.value}_instruction")
        return InstructionAssetRecord(
            id = InstructionAssetId("instruction_${contentId.value}_default"),
            familyId = PulsarLearnGuideCatalog.familyId,
            profileId = PulsarLearnGuideCatalog.profileId,
            stageId = stageId,
            contentId = contentId,
            namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,
            drawableRes = drawableRes,
            mandatoryForFullGuidance = true,
            safetySensitive = safetySensitive,
            provenance = InstructionAssetProvenance(
                promptDocument = PROMPT_PATH,
                promptRevision = "starlit_tactile_$revision",
                generatedOn = LocalDate.of(2026, 8, 19),
            ),
            review = InstructionAssetReview(
                status = InstructionAssetReviewStatus.APPROVED,
                reviewer = "Starlit Coffee Pulsar visual QA",
                reviewedOn = LocalDate.of(2026, 8, 19),
                notes = "Checked against $TRACKER_PATH for anatomy, valve state, action, alpha, and crop.",
            ),
        )
    }
}
