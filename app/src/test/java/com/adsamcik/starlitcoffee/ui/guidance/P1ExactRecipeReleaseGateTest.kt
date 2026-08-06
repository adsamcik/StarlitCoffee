package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P1ExactRecipeReleaseGateTest {

    private val exactCatalog: P1ExactGuidanceCatalog by lazy {
        BuiltInP1ExactGuidanceCatalog.decode(loadGuidanceAsset())
    }
    private val loaded: BuiltInP1ExactGuidanceLoadResult
        get() = BuiltInP1ExactGuidanceLoadResult.Loaded(exactCatalog)

    @Test
    fun `approved visuals and reviewed English guidance unlock the recipe`() {
        val gate = gate(
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = P1ExactRecipeLocalizationCoverage.production,
        )

        assertTrue(gate.isEligible(PRIMARY_RECIPE))
        assertEquals(setOf(PRIMARY_RECIPE), gate.eligibleRecipeIds)
        assertNull(gate.terminologyUiCopyFor(PRIMARY_RECIPE))
    }

    @Test
    fun `unreviewed active locale remains gated without English fallback`() {
        val gate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = BuiltInP1ExactGuidanceLoadResult.Loaded(
                catalog = exactCatalog,
                localeTag = "cs",
            ),
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = P1ExactRecipeLocalizationCoverage.production,
        )

        assertFalse(gate.isEligible(PRIMARY_RECIPE))
        assertTrue(gate.eligibleRecipeIds.isEmpty())
    }

    @Test
    fun `reviewed non-English guidance also requires its approved terminology glossary`() {
        val guidance = BuiltInP1ExactGuidanceLoadResult.Loaded(
            catalog = exactCatalog,
            localeTag = "cs",
        )
        val coverage = fullyLocalized(PRIMARY_RECIPE, localeTag = "cs")
        val primaryStage = requireNotNull(exactCatalog.findRecipe(PRIMARY_RECIPE)).stages.first()
        val reference = BrewingTerminologyReference(
            conceptId = "drawdown",
            preferredLocal = "dokapání",
            canonicalEnglish = "drawdown",
        )
        val uiCopy = BrewingTerminologyUiCopy(
            showEnglishTerms = "Zobrazit anglické termíny",
            hideEnglishTerms = "Skrýt anglické termíny",
            heading = "Anglická terminologie",
        )
        val withoutTerminology = P1ExactRecipeReleaseGate(
            guidanceLoadResult = guidance,
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = coverage,
        )
        val withTerminology = P1ExactRecipeReleaseGate(
            guidanceLoadResult = guidance,
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            terminologyLoadResult = BuiltInP1ExactTerminologyLoadResult.Loaded(
                P1ExactTerminologyCatalog(
                    localeTag = "cs",
                    uiCopy = uiCopy,
                    referencesByContentId = mapOf(
                        primaryStage.contentId to listOf(reference),
                    ),
                ),
            ),
            localizationCoverage = coverage,
        )

        assertFalse(withoutTerminology.isEligible(PRIMARY_RECIPE))
        assertTrue(withTerminology.isEligible(PRIMARY_RECIPE))
        assertEquals(uiCopy, withTerminology.terminologyUiCopyFor(PRIMARY_RECIPE))
        assertEquals(
            listOf(reference),
            withTerminology.catalogFor(PRIMARY_RECIPE)
                ?.find(primaryStage.contentId)
                ?.terminologyReferences,
        )
    }

    @Test
    fun `preview locale stays gated until explicit consent`() {
        val guidance = BuiltInP1ExactGuidanceLoadResult.Loaded(
            catalog = exactCatalog,
            localeTag = "cs",
            canonicalEnglishCatalog = exactCatalog,
        )
        val terminology = BuiltInP1ExactTerminologyLoadResult.Loaded(
            P1ExactTerminologyCatalog(
                localeTag = "cs",
                localizationStatus = P1ExactLocalizationStatus.PREVIEW,
                uiCopy = BrewingTerminologyUiCopy("Show", "Hide", "English terms"),
                referencesByContentId = emptyMap(),
            ),
        )
        val coverage = P1ExactRecipeLocalizationCoverage(
            supportedLocaleTags = P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags,
            coveredLocaleTagsByRecipe = emptyMap(),
            previewLocaleTagsByRecipe = mapOf(PRIMARY_RECIPE to setOf("cs")),
        )
        val blocked = P1ExactRecipeReleaseGate(
            guidanceLoadResult = guidance,
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            terminologyLoadResult = terminology,
            localizationCoverage = coverage,
        )
        val enabled = P1ExactRecipeReleaseGate(
            guidanceLoadResult = guidance,
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            terminologyLoadResult = terminology,
            localizationCoverage = coverage,
            allowPreview = true,
        )

        assertTrue(blocked.eligibleRecipeIds.isEmpty())
        assertEquals(setOf(PRIMARY_RECIPE), blocked.previewEligibleRecipeIds)
        assertTrue(blocked.requiresPreviewConsent)
        assertTrue(enabled.isEligible(PRIMARY_RECIPE))
        assertTrue(enabled.isPreview(PRIMARY_RECIPE))
        assertFalse(enabled.requiresPreviewConsent)
    }

    @Test
    fun `preview safety warning includes canonical English reference`() {
        val stage = exactCatalog.stages.first { it.requiresSafetyCriticalExpertReview && it.warning != null }
        val localized = stage.copy(
            full = stage.full.copy(warning = "Localized safety warning"),
        )
        val content = localized.toBuiltInGuidanceContent(
            englishSafetyWarning = requireNotNull(stage.warning),
        )

        assertTrue(content.text.warning?.contains("Localized safety warning") == true)
        assertTrue(content.text.warning?.contains("English safety reference:") == true)
        assertTrue(content.text.warning?.contains(requireNotNull(stage.warning)) == true)
    }

    @Test
    fun `pending exact visuals fail closed even with complete localization`() {
        val pendingAssets = requiredStages(PRIMARY_RECIPE).map { stage ->
            stage.toAsset(
                review = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW),
            )
        }
        val gate = gate(
            instructionAssets = InstructionAssetCatalog(pendingAssets),
            localizationCoverage = fullyLocalized(PRIMARY_RECIPE),
        )

        assertFalse(gate.isEligible(PRIMARY_RECIPE))
        assertNull(gate.catalogFor(PRIMARY_RECIPE))
    }

    @Test
    fun `full exact approvals and locale coverage unlock only that recipe`() {
        val gate = gate(
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = fullyLocalized(PRIMARY_RECIPE),
        )

        assertEquals(setOf(PRIMARY_RECIPE), gate.eligibleRecipeIds)
        assertTrue(gate.isEligible(PRIMARY_RECIPE))
        assertFalse(gate.isEligible(SAME_PROFILE_RECIPE))
        assertTrue(gate.catalogFor(PRIMARY_RECIPE)?.content?.isNotEmpty() == true)
        assertNull(gate.catalogFor(SAME_PROFILE_RECIPE))
        assertTrue(
            gate.shouldGatePersistedSession(
                SAME_PROFILE_RECIPE.value,
                rawBrewerProfileId = "clever_style",
            ),
        )
    }

    @Test
    fun `unavailable loader and missing exact plan fail closed`() {
        val unavailableGate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = BuiltInP1ExactGuidanceLoadResult.Unavailable("test"),
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = fullyLocalized(PRIMARY_RECIPE),
        )
        val missingPlanGate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = loaded,
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = fullyLocalized(PRIMARY_RECIPE),
            stagePlanFor = { null },
        )

        assertTrue(unavailableGate.eligibleRecipeIds.isEmpty())
        assertTrue(missingPlanGate.eligibleRecipeIds.isEmpty())
    }

    @Test
    fun `legacy sessions are not hidden by the exact recipe gate`() {
        val gate = gate(
            instructionAssets = InstructionAssetCatalog(emptyList()),
            localizationCoverage = P1ExactRecipeLocalizationCoverage.production,
        )

        assertFalse(gate.shouldGatePersistedSession(null, "pulsar_standard"))
        assertFalse(gate.shouldGatePersistedSession("future_legacy_recipe", "pulsar_standard"))
        assertTrue(gate.shouldGatePersistedSession("future_exact_recipe", "clever_style"))
    }

    @Test
    fun `declared app locales match the production localization gate`() {
        val config = localeConfigFile().readText()
        val configured = Regex("""android:name="([^"]+)"""")
            .findAll(config)
            .mapTo(linkedSetOf()) { match -> match.groupValues[1] }

        assertEquals(P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags, configured)
        assertEquals(23, configured.size)
    }

    @Test
    fun `localization contract cannot reduce the supported locale set`() {
        assertThrows(IllegalArgumentException::class.java) {
            P1ExactRecipeLocalizationCoverage(
                supportedLocaleTags = setOf("en"),
                coveredLocaleTagsByRecipe = mapOf(
                    PRIMARY_RECIPE to setOf("en"),
                ),
            )
        }
    }

    private fun gate(
        instructionAssets: InstructionAssetCatalog,
        localizationCoverage: P1ExactRecipeLocalizationCoverage,
    ): P1ExactRecipeReleaseGate = P1ExactRecipeReleaseGate(
        guidanceLoadResult = loaded,
        instructionAssets = instructionAssets,
        localizationCoverage = localizationCoverage,
    )

    private fun fullyLocalized(
        recipeId: BuiltInRecipeId,
        localeTag: String = "en",
    ) = P1ExactRecipeLocalizationCoverage(
        supportedLocaleTags = P1ExactRecipeLocalizationCoverage.supportedAppLocaleTags,
        coveredLocaleTagsByRecipe = mapOf(
            recipeId to setOf(localeTag),
        ),
    )

    private fun approvedAssetsFor(recipeId: BuiltInRecipeId): InstructionAssetCatalog =
        InstructionAssetCatalog(
            requiredStages(recipeId).map { stage -> stage.toAsset(approvedReview()) },
        )

    private fun requiredStages(recipeId: BuiltInRecipeId): List<P1ExactStageGuidance> =
        requireNotNull(exactCatalog.findRecipe(recipeId)).stages.filter { stage ->
            stage.visualPriority != P1ExactVisualPriority.OPTIONAL
        }

    private fun P1ExactStageGuidance.toAsset(
        review: InstructionAssetReview,
    ): InstructionAssetRecord = InstructionAssetRecord(
        id = instructionAssetId,
        familyId = methodFamilyId,
        profileId = brewerProfileId,
        stageId = stageId,
        contentId = contentId,
        namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,
        drawableRes = R.drawable.vessel_icon_mug,
        mandatoryForFullGuidance = true,
        safetySensitive = visualPriority == P1ExactVisualPriority.SAFETY_CRITICAL,
        provenance = InstructionAssetProvenance(
            promptDocument = "docs/brewing/asset-production.md",
            promptRevision = "test-v1",
        ),
        review = review,
    )

    private fun approvedReview() = InstructionAssetReview(
        status = InstructionAssetReviewStatus.APPROVED,
        reviewer = "QA",
        reviewedOn = LocalDate.of(2026, 7, 28),
    )

    private fun loadGuidanceAsset(): String = guidanceAssetFile().readText()

    private fun guidanceAssetFile(): File = listOf(
        File("src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}"),
        File("app/src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}"),
    ).first(File::isFile)

    private fun localeConfigFile(): File = listOf(
        File("src/main/res/xml/locales_config.xml"),
        File("app/src/main/res/xml/locales_config.xml"),
    ).first(File::isFile)

    private companion object {
        val PRIMARY_RECIPE = BuiltInRecipeId("clever_water_first_15_250")
        val SAME_PROFILE_RECIPE = BuiltInRecipeId("clever_coffee_first_15_250")
    }
}
