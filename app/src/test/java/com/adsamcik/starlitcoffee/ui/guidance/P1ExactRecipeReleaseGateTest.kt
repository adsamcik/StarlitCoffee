package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
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
    fun `production visual review unlocks only recipes whose required pixels pass`() {
        val gate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = loaded,
            instructionAssets = BuiltInInstructionAssetCatalog.catalog,
            localizationCoverage = P1ExactRecipeLocalizationCoverage.production,
        )

        assertEquals(
            setOf(
                "v60_official_15_250",
                "v60_rao_20_330",
                "wave185_ozone_25_400",
                "wedge_pulse_23_5_400",
                "chemex_42_700",
                "generic_conical_low_agitation_20_320",
                "clever_water_first_15_250",
                "clever_coffee_first_15_250",
                "switch_ole_boen_hybrid_16_5_240",
                "switch_gravity_15_250",
                "auto_cupone_20_300",
                "phin_gravity_14_118",
            ).mapTo(linkedSetOf(), ::BuiltInRecipeId),
            gate.eligibleRecipeIds,
        )
        assertFalse(gate.isEligible(BuiltInRecipeId("switch_official_20_240")))
        assertFalse(gate.isEligible(BuiltInRecipeId("auto_batch_500_30")))
        assertFalse(gate.isEligible(BuiltInRecipeId("auto_batch_1000_60")))
        assertFalse(gate.isEligible(BuiltInRecipeId("phin_screw_18_120")))
    }

    @Test
    fun `localized guidance remains gated when its terminology resource is unavailable`() {
        val gate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = BuiltInP1ExactGuidanceLoadResult.Loaded(
                catalog = exactCatalog,
                localeTag = "cs",
            ),
            instructionAssets = approvedAssetsFor(PRIMARY_RECIPE),
            localizationCoverage = P1ExactRecipeLocalizationCoverage.production,
            releaseReadiness = P1ExactRecipeReleaseReadiness.allClear,
            visualReviewLedger = approvedVisualReviews(approvedAssetsFor(PRIMARY_RECIPE)),
        )

        assertFalse(gate.isEligible(PRIMARY_RECIPE))
        assertTrue(gate.eligibleRecipeIds.isEmpty())
    }

    @Test
    fun `released non-English guidance also requires its terminology glossary`() {
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
            releaseReadiness = P1ExactRecipeReleaseReadiness.allClear,
            visualReviewLedger = approvedVisualReviews(approvedAssetsFor(PRIMARY_RECIPE)),
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
            releaseReadiness = P1ExactRecipeReleaseReadiness.allClear,
            visualReviewLedger = approvedVisualReviews(approvedAssetsFor(PRIMARY_RECIPE)),
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
        assertTrue(P1ExactRecipeLocalizationCoverage.production.isComplete(PRIMARY_RECIPE, "en"))
        assertFalse(P1ExactRecipeLocalizationCoverage.production.isComplete(PRIMARY_RECIPE, "cs"))
    }

    @Test
    fun `open recipe evidence blocker fails closed after all other approvals`() {
        val assets = approvedAssetsFor(PRIMARY_RECIPE)
        val gate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = loaded,
            instructionAssets = assets,
            localizationCoverage = fullyLocalized(PRIMARY_RECIPE),
            releaseReadiness = P1ExactRecipeReleaseReadiness(
                mapOf(PRIMARY_RECIPE to setOf("BLOCK-TEST-EVIDENCE")),
            ),
            visualReviewLedger = approvedVisualReviews(assets),
        )

        assertFalse(gate.isEligible(PRIMARY_RECIPE))
    }

    @Test
    fun `visual verdict must match final drawable hash`() {
        val assets = approvedAssetsFor(PRIMARY_RECIPE)
        val wrongHashReviews = P1ExactVisualReviewLedger(
            assets.assets.map { asset -> approvedVisualReview(asset, WRONG_TEST_HASH) },
        )
        val gate = P1ExactRecipeReleaseGate(
            guidanceLoadResult = loaded,
            instructionAssets = assets,
            localizationCoverage = fullyLocalized(PRIMARY_RECIPE),
            releaseReadiness = P1ExactRecipeReleaseReadiness.allClear,
            visualReviewLedger = wrongHashReviews,
        )

        assertFalse(gate.isEligible(PRIMARY_RECIPE))
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
        releaseReadiness = P1ExactRecipeReleaseReadiness.allClear,
        visualReviewLedger = approvedVisualReviews(instructionAssets),
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
        resourceSha256 = TEST_HASH,
    )

    private fun approvedVisualReviews(
        assets: InstructionAssetCatalog,
    ): P1ExactVisualReviewLedger = P1ExactVisualReviewLedger(
        assets.assets.map(::approvedVisualReview),
    )

    private fun approvedVisualReview(
        asset: InstructionAssetRecord,
        hash: String = TEST_HASH,
    ) = P1ExactInstructionVisualReview(
        assetId = asset.id,
        resourceSha256 = hash,
        reviewer = "Independent QA",
        reviewedOn = LocalDate.of(2026, 8, 21),
        fullResolutionReviewed = true,
        phoneScaleReviewed = true,
        mechanicsReviewed = true,
        altTextReviewed = true,
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
        const val TEST_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val WRONG_TEST_HASH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
