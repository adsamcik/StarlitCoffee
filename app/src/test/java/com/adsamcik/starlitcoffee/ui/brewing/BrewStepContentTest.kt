package com.adsamcik.starlitcoffee.ui.brewing

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.GuidanceOperationalCue
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetNamingConvention
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetProvenance
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetReview
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetReviewStatus
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.learnFacts
import com.adsamcik.starlitcoffee.ui.guidance.safetyForStep
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BrewStepContentTest {
    @Test
    fun `absent prose does not remove operational and safety content`() {
        val step = source().copy(instruction = "  ", target = "None", explanation = "N/A", tip = "  ",
            warning = " Keep hands clear ", safetyCritical = true,
            utilities = listOf(GuidanceOperationalCue.ELAPSED_TIMER)).toBrewStepContent()
        assertNull(step.instruction)
        assertNull(step.target)
        assertTrue(step.details.isEmpty())
        assertEquals("Keep hands clear", step.warning)
        assertTrue(step.safetyCritical)
        assertEquals(listOf(GuidanceOperationalCue.ELAPSED_TIMER), step.utilities)
        assertTrue(step.isReadable)
    }

    @Test
    fun `title and empty warning alone do not create a reader step`() {
        val step = BrewStepContent(StageContentId("empty"), title = "Pour", warning = " ")
        assertFalse(step.isReadable)
        assertTrue(BrewReaderContent("reader", steps = listOf(step)).readableSteps.isEmpty())
        assertTrue(step.copy(target = "340 g").isReadable)
    }

    @Test
    fun `legacy selection follows content identity past common records and reconstructed stages`() {
        val a = source().copy(id = StageContentId("a"), target = "60 g")
        val c = source().copy(id = StageContentId("c"), target = "340 g")
        val common = source().copy(id = StageContentId("common"), placement = BuiltInGuidancePlacement.GLOBAL_SAFETY,
            warning = "Hot water")
        val raw = listOf(common, a, c)
        val reader = BrewReaderContent("reader", steps = listOf(a.toBrewStepContent(),
            BrewStepContent(StageContentId("b"), target = "180 g"), c.toBrewStepContent()))
        val selectedA = reader.readableSteps[reader.readableStepIndexFor(raw[1].id)]
        val selectedC = reader.readableSteps[reader.readableStepIndexFor(raw[2].id)]
        assertEquals(a.id, selectedA.id)
        assertEquals(c.id, selectedC.id)
        assertEquals(0, reader.readableStepIndexFor(StageContentId("absent")))
    }

    @Test
    fun `cumulative target leads without losing added water or temperature`() {
        val added = mass(StageMassReference.STAGE_ADDED, 280.0)
        val cumulative = mass(StageMassReference.BREW_CUMULATIVE, 340.0)
        val temperature = BrewStageReferenceCuePresentation.Temperature(StageTargetQualifier.EXACT, 93.0, 93.0)
        val step = BrewStepContent(StageContentId("pour"), referenceCues = listOf(temperature, added, cumulative))
        assertEquals(cumulative, step.primaryReferenceCue)
        assertEquals(listOf(temperature, added, cumulative), step.referenceCues)
        assertTrue(step.isReadable)
    }

    @Test
    fun `illustration requires approved artwork and a meaningful caption`() {
        val asset = asset()
        assertNull(approvedBrewStepIllustration(asset, " "))
        assertNull(approvedBrewStepIllustration(asset, "None"))
        assertNull(approvedBrewStepIllustration(asset.copy(review = InstructionAssetReview(
            InstructionAssetReviewStatus.PENDING_REVIEW)), "Water above coffee"))
        val illustration = requireNotNull(approvedBrewStepIllustration(asset, " Water above coffee "))
        assertEquals("Water above coffee", illustration.description)
        assertTrue(BrewStepContent(StageContentId("picture"), illustration = illustration).isReadable)
    }

    @Test
    fun `partial and workflow-required availability retain usable common and sibling content`() {
        val sibling = source().copy(target = "340 g", controlRequirements = listOf(
            GuidanceOperationalCue.ELAPSED_TIMER, GuidanceOperationalCue.STAGE_ADVANCE, GuidanceOperationalCue.VALVE_STATE))
        val common = source().copy(id = StageContentId("common_safety"),
            placement = BuiltInGuidancePlacement.GLOBAL_SAFETY, warning = "Hot water")
        val resolution = LearnGuidanceCatalogResolution(null,
            LearnGuidanceCatalogAvailability.Partial(BrewerProfileId("v60_02"), emptyList()), listOf(sibling, common))
        val partial = resolution.toBrewReaderContent("partial")
        assertEquals(listOf(sibling.id), partial.readableSteps.map { it.id })
        assertEquals(listOf(GuidanceOperationalCue.VALVE_STATE), partial.readableSteps.single().controlRequirements)
        assertEquals(listOf(common.id), partial.commonContent.map { it.id })
        val workflow = resolution.copy(availability = LearnGuidanceCatalogAvailability.HarioSwitchWorkflowRequired(
            BrewerProfileId("hario_switch_03")), content = listOf(common)).toBrewReaderContent("switch")
        assertTrue(workflow.readableSteps.isEmpty())
        assertEquals("Hot water", workflow.commonContent.single().warning)
    }

    @Test
    fun `missing reviewed teaching keeps ordered physical targets and does not invent copy`() {
        val guide = guides().first { it.methodId == "pulsar" }
        val facts = guide.learnFacts()
        val expected = guide.steps.filter { step ->
            requireNotNull(facts[guide.contentId(step)]).toBrewStepContent(guide.contentId(step)).isReadable ||
                guide.safetyForStep(step.id).isNotEmpty()
        }.map { guide.contentId(it) }
        val reader = LearnGuidanceCatalogResolution(null, LearnGuidanceCatalogAvailability.Partial(
            BrewerProfileId(guide.profileId), emptyList()), emptyList()).toBrewReaderContent("pulsar", reviewedGuide = guide)
        assertEquals(expected, reader.readableSteps.map { it.id })
        assertTrue(reader.readableSteps.any { it.referenceCues.isNotEmpty() })
        assertTrue(reader.readableSteps.all { it.instruction == null && it.illustration == null })
        assertNotNull(reader.overview)
    }

    @Test
    fun `omitting espresso teaching retains required pressure warnings without inventing prose or art`() {
        val guide = guides().first { it.methodId == "espresso" }
        val shot = guide.steps.first { it.id == "start_shot" }
        val id = guide.contentId(shot)
        val absent = LearnGuidanceCatalogResolution(null, LearnGuidanceCatalogAvailability.Partial(
            BrewerProfileId(guide.profileId), emptyList()), emptyList())
        val fallback = absent.toBrewReaderContent("espresso", reviewedGuide = guide).readableSteps.first { it.id == id }
        assertTrue(guide.safetyForStep(shot.id).isNotEmpty())
        guide.safetyForStep(shot.id).forEach { assertTrue(requireNotNull(fallback.warning).contains(it)) }
        assertTrue(fallback.safetyCritical)
        assertNull(fallback.instruction)
        assertNull(fallback.illustration)
        val supplied = absent.copy(content = listOf(source().copy(id = id, instruction = "Extract", warning = null)))
            .toBrewReaderContent("espresso", reviewedGuide = guide).readableSteps.first { it.id == id }
        assertEquals(fallback.warning, supplied.warning)
        assertEquals("Extract", supplied.instruction)
    }

    @Test
    fun `overview never infers temperature from brewer ID and preserves actual units`() {
        val guide = guides().first { it.methodId == "automatic-drip" }
        val overview = guide.toBrewOverviewContent()
        assertNull(overview.temperature)
        assertTrue(overview.details.paragraphs.contains("machine"))
        assertEquals(guide.sources.map { it.url }, overview.evidence.sources.map { it.url })
        guides().first { it.waterMl != null && it.waterG == null }.let { volumeGuide ->
            val water = volumeGuide.toBrewOverviewContent().quantities.first { it.labelRes == R.string.label_water }
            assertEquals(BrewQuantityUnit.MILLILITRES, water.unit)
            assertEquals(volumeGuide.waterMl, water.value)
        }
    }

    private fun source() = ResolvedLearnGuidanceContent(StageContentId("pour"), BuiltInGuidancePlacement.LIVE_STAGE,
        instruction = "", target = null, completionCue = null, explanation = null, tip = null, nextAction = null,
        controlRequirements = emptyList(), warning = null, utilities = emptyList(), altText = "", safetyCritical = false)

    private fun mass(reference: StageMassReference, value: Double) = BrewStageReferenceCuePresentation.Mass(
        QuantityRole.BREW_WATER_INPUT, reference, StageTargetQualifier.EXACT, value, value)

    private fun asset() = InstructionAssetRecord(InstructionAssetId("instruction_picture_default"),
        MethodFamilyId("manual_gravity"), contentId = StageContentId("picture"),
        namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID, drawableRes = 1,
        mandatoryForFullGuidance = false, safetySensitive = false,
        provenance = InstructionAssetProvenance("test-prompt", "test"),
        review = InstructionAssetReview(InstructionAssetReviewStatus.APPROVED, "test", LocalDate.of(2026, 10, 25)))

    private fun guides() = ReviewedMethodGuideLibrary.decode(listOf(
        File("src/main/assets/${ReviewedMethodGuideLibrary.ASSET_PATH}"),
        File("app/src/main/assets/${ReviewedMethodGuideLibrary.ASSET_PATH}"),
    ).first(File::isFile).readText())
}
