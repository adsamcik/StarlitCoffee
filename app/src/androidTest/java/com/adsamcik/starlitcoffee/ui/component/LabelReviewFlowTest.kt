package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.network.QrCoffeeMetadata
import com.adsamcik.starlitcoffee.data.work.BagDraftReviewLocation
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionPresentation
import com.adsamcik.starlitcoffee.util.RecognitionStatusText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real review form without an optional recognizer, network request, or persisted inventory. */
@RunWith(AndroidJUnit4::class)
class LabelReviewFlowTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rapidTypingWithHistoryKeepsKeyboardFocusAndSavesExactName() {
        val harness = ReviewHarness(BagFormSnapshot()).apply {
            existingBags = listOf(CoffeeBagEntity(id = 77L, name = "LasFlores", roaster = "April Coffee"))
        }
        show(harness)
        editor("name").performClick()
        editor("name").assertIsFocused()
        val inputMethod = requireNotNull(context.getSystemService(InputMethodManager::class.java))
        rule.waitUntil(10_000) { inputMethod.isAcceptingText }
        InstrumentationRegistry.getInstrumentation().sendStringSync("Las Flores")
        editor("name").assertIsFocused()
        editor("name").assertTextContains("Las Flores")
        save()
        rule.runOnIdle { assertEquals("Las Flores", harness.saved.single().name) }
    }

    @Test
    fun compactManualReviewLeavesMissingOptionalDetailsQuietAndSavesExactValues() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores", roaster = "April Coffee"))
        show(harness)

        editor("name").assertTextContains("Las Flores")
        rule.onNodeWithTag("label-review-origin").assertIsDisplayed()
        rule.onNodeWithTag("label-review-editor-origin").assertDoesNotExist()
        rule.onNodeWithText(label(R.string.label_review_check)).assertDoesNotExist()
        rule.onNodeWithTag("label-review-save").assertIsDisplayed().assertIsEnabled()
        capture("label-review-manual-light")
        save()

        rule.runOnIdle {
            assertEquals(listOf(SavedValues("Las Flores", "April Coffee", null, null, null)), harness.saved)
            harness.form.value = harness.form.value.copy(originCountry = "Colombia", originRegion = "Huila", weight = "250",
                roastDateMillis = DateParser.parse("2026-10-01"))
        }
        rule.onNodeWithText("Colombia").assertIsDisplayed()
        rule.onNodeWithText("Huila").assertIsDisplayed()
        capture("label-review-populated-light")
        rule.runOnIdle { harness.dark.value = true }
        capture("label-review-populated-dark")
        rule.runOnIdle { harness.dynamicColor.value = true }
        capture("label-review-populated-dynamic-dark")
    }

    @Test
    fun sourceBoundSuggestionsNeedExplicitKeepOrUseAndPhotoReturnsToSameEditor() {
        val photo = labelPhoto()
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores", roaster = "April Coffee",
            originCountry = "Colombia", originRegion = "Huila", weight = "250"), dark = true)
        harness.photoUris = listOf(photo, labelPhoto("label-review-back.jpg"), labelPhoto("label-review-extra.jpg")).joinToString(",")
        harness.evidence = mapOf(
            "origin" to evidence("origin", "Ethiopia", photo, "Origin: Ethiopia", BagPhotoRect(0.04f, 0.20f, 0.96f, 0.30f)),
            "region" to evidence("region", "Sidama", photo, "Region: Sidama", BagPhotoRect(0.04f, 0.29f, 0.96f, 0.36f)),
        )
        harness.suggestions.value = mapOf("origin" to "Ethiopia", "region" to "Sidama")
        show(harness)
        rule.onNodeWithContentDescription(label(R.string.action_review_add_photo)).performClick()
        rule.runOnIdle { assertEquals(1, harness.photoRequests) }
        capture("label-review-gallery-dark")

        rule.onNodeWithTag("label-review-correction-origin").assertDoesNotExist()
        open("origin")
        editor("origin").assertTextContains("Colombia")
        scrollTo(hasText("Ethiopia"))
        rule.onNodeWithText("Ethiopia").assertIsDisplayed()
        scrollTo(hasTestTag("label-evidence-photo"))
        rule.waitUntil(10_000) {
            !rule.onNodeWithTag("label-evidence-photo").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        capture("label-review-source-dark")
        rule.onNodeWithTag("label-evidence-photo").performClick()
        rule.onNodeWithContentDescription(label(R.string.action_close)).performClick()
        scrollTo(hasText(label(R.string.action_review_keep_mine)))
        rule.onNodeWithText(label(R.string.action_review_keep_mine)).performClick()
        rule.onNodeWithTag("label-review-focus-origin").assertIsFocused()
        rule.onNodeWithTag("label-review-correction-origin")
            .assertContentDescriptionEquals("${label(R.string.label_origin)}: Colombia")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val correctionId = rule.onNodeWithTag("label-review-correction-origin").fetchSemanticsNode().id
        rule.runOnIdle { harness.dark.value = false }
        assertEquals(correctionId, rule.onNodeWithTag("label-review-correction-origin").fetchSemanticsNode().id)
        editor("origin").assertTextContains("Colombia")
        rule.onNodeWithText("Ethiopia").assertDoesNotExist()

        open("region")
        editor("region").assertTextContains("Huila")
        scrollTo(hasText(label(R.string.action_review_use_reading)))
        rule.runOnIdle { harness.allowAcceptance = false }
        rule.onNodeWithText(label(R.string.action_review_use_reading)).performClick()
        rule.onNodeWithTag("label-review-correction-region").assertDoesNotExist()
        editor("region").assertTextContains("Huila")
        rule.runOnIdle { harness.allowAcceptance = true }
        rule.onNodeWithText(label(R.string.action_review_use_reading)).performClick()
        rule.onNodeWithTag("label-review-focus-region").assertIsFocused()
        rule.onNodeWithTag("label-review-correction-region")
            .assertContentDescriptionEquals("${label(R.string.label_region)}: Sidama")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        editor("region").assertTextContains("Sidama")
        save()
        rule.runOnIdle {
            assertEquals(listOf("region" to "Sidama"), harness.accepted)
            assertEquals(SavedValues("Las Flores", "April Coffee", "Colombia", "Sidama", 250f), harness.saved.single())
        }
    }

    @Test
    fun typedAndExplicitlyClearedFieldsSurviveLateOcrAndDurableFormRefresh() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Read name", originCountry = "Colombia"))
        show(harness)
        editor("name").performTextReplacement("My weekend coffee")
        open("origin")
        editor("origin").performTextReplacement("")

        rule.runOnIdle {
            harness.form.value = BagFormSnapshot(name = "Late durable name", originCountry = "Ethiopia", originRegion = "Sidama")
            harness.ocr.value = OcrFieldExtractor.OcrExtractionResult(name = "Late OCR name", origin = "Kenya")
        }
        editor("origin").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        scrollTo(hasTestTag("label-review-name"))
        editor("name").assertTextContains("My weekend coffee")
        save()

        rule.runOnIdle {
            assertEquals("My weekend coffee", harness.saved.single().name)
            assertEquals(null, harness.saved.single().origin)
            assertEquals("Sidama", harness.saved.single().region)
            assertTrue(harness.changes.contains("origin" to ""))
        }
    }

    @Test
    fun runningRecognitionAndApprovedQrLookupDoNotBlockSaveOrAcceptPendingReading() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores", originCountry = "Colombia"))
        harness.processing = true
        harness.traceabilityUrl = "https://example.com/coffee/las-flores"
        harness.suggestions.value = mapOf("origin" to "Ethiopia")
        show(harness)
        capture("label-review-processing")

        scrollTo(hasText(label(R.string.action_show_optional_details)))
        rule.onNodeWithText(label(R.string.action_show_optional_details)).performClick()
        scrollTo(hasText(label(R.string.label_review_source_notes)))
        rule.onNodeWithText(label(R.string.label_review_source_notes)).performClick()
        scrollTo(hasText(label(R.string.action_explore_extract)))
        rule.onNodeWithText(label(R.string.action_explore_extract)).performClick()
        rule.runOnIdle { assertNotNull(harness.qrResult) }
        rule.onNodeWithTag("label-review-save").assertIsDisplayed().assertIsEnabled()
        save()

        rule.runOnIdle {
            assertEquals("Colombia", harness.saved.single().origin)
            assertTrue(harness.accepted.isEmpty())
        }
        rule.runOnIdle { harness.isSaving.value = true }
        rule.waitForIdle()
        rule.runOnIdle { harness.qrResult?.invoke(qrMetadata(origin = "Ethiopia", region = "Sidama")) }
        rule.runOnIdle { harness.isSaving.value = false }
        save()
        rule.runOnIdle {
            assertEquals(2, harness.saved.size)
            assertEquals(harness.saved[0], harness.saved[1])
            assertEquals(null, harness.saved[1].region)
        }
    }

    @Test
    fun lateQrLookupUsesCurrentEnrichedFieldInsteadOfItsOriginalBlankSnapshot() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores"))
        harness.traceabilityUrl = "https://example.com/coffee/las-flores"
        show(harness)
        startQrLookup()
        rule.runOnIdle { harness.form.value = harness.form.value.copy(originCountry = "Colombia") }
        rule.waitForIdle()
        rule.runOnIdle { harness.qrResult?.invoke(qrMetadata(origin = "Ethiopia")) }
        save()

        rule.runOnIdle { assertEquals("Colombia", harness.saved.single().origin) }
    }

    @Test
    fun qrResultFromClosedReviewCannotWriteIntoReopenedDraft() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores"))
        harness.traceabilityUrl = "https://example.com/coffee/las-flores"
        show(harness)
        startQrLookup()
        val previousCallback = requireNotNull(harness.qrResult)
        rule.onNodeWithContentDescription(label(R.string.action_back)).performClick()
        rule.onNodeWithTag("label-review-list").assertDoesNotExist()
        rule.runOnIdle { harness.visible.value = true }
        open("origin")
        editor("origin").performTextReplacement("My chosen origin")
        rule.runOnIdle { previousCallback(qrMetadata(origin = "Ethiopia")) }
        save()
        rule.runOnIdle {
            assertEquals(listOf("origin" to "My chosen origin"), harness.changes.filter { it.first == "origin" })
            assertEquals("My chosen origin", harness.saved.single().origin)
        }
    }

    @Test
    fun leavingAndReopeningReviewRestoresOptionalEditorAndScrollPosition() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores", roaster = "April Coffee", weight = "250"))
        show(harness)
        scrollTo(hasText(label(R.string.action_show_optional_details)))
        rule.onNodeWithText(label(R.string.action_show_optional_details)).performClick()
        open("expiryDate")
        scrollTo(hasTestTag("label-review-editor-expiryDate"))
        rule.waitUntil(5_000) {
            harness.location.value.let { it.showMoreDetails && "expiryDate" in it.expandedFields && it.scrollIndex > 0 }
        }
        val retained = harness.location.value
        rule.onNodeWithContentDescription(label(R.string.action_back)).performClick()
        rule.onNodeWithTag("label-review-list").assertDoesNotExist()
        rule.runOnIdle { harness.visible.value = true }

        rule.onNodeWithTag("label-review-editor-expiryDate").assertIsDisplayed()
        rule.runOnIdle { assertEquals(retained, harness.location.value) }
        rule.onNodeWithTag("label-review-save").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun typedDateAcceptanceAndLeavingDecafBlankPreserveTheirLocalizedResultAndUncertainty() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores"))
        harness.suggestions.value = mapOf("roastDate" to "2026-10-01", "isDecaf" to "true")
        show(harness)
        open("roastDate")
        scrollTo(hasText(label(R.string.action_review_use_reading)))
        rule.onNodeWithText(label(R.string.action_review_use_reading)).performClick()
        val roastDate = requireNotNull(DateParser.parse("2026-10-01"))
        rule.onNodeWithTag("label-review-focus-roastDate").assertIsFocused()
        rule.onNodeWithTag("label-review-correction-roastDate")
            .assertContentDescriptionEquals("${label(R.string.label_roast_date)}: ${DateParser.format(roastDate)}")
        rule.onNodeWithText(DateParser.format(roastDate)).assertIsDisplayed()

        val moreLabel = context.getString(R.string.format_review_more_details, 1)
        scrollTo(hasText(moreLabel))
        rule.onNodeWithText(moreLabel).performClick()
        open("isDecaf")
        scrollTo(hasText(label(R.string.action_review_leave_blank)))
        rule.onNodeWithText(label(R.string.action_review_leave_blank)).performClick()
        rule.onNodeWithTag("label-review-focus-isDecaf").assertIsFocused()
        rule.onNodeWithTag("label-review-correction-isDecaf")
            .assertContentDescriptionEquals("${label(R.string.label_decaf_coffee)}: ${label(R.string.label_review_not_set)}")
        save()
        rule.runOnIdle {
            assertEquals(listOf("roastDate" to "2026-10-01"), harness.accepted)
            assertEquals(roastDate, harness.saved.single().roastDate)
            assertFalse(harness.saved.single().isDecaf)
            assertTrue(harness.changes.contains("isDecaf" to null))
        }
        rule.runOnIdle { harness.form.value = harness.form.value.copy(isDecaf = true) }
        rule.onNodeWithTag("label-review-decaf-unknown").assertIsSelected()
        rule.runOnIdle { harness.form.value = harness.form.value.copy(roastDateMillis = roastDate, isDecaf = false) }
        rule.onNodeWithContentDescription(label(R.string.action_back)).performClick()
        rule.runOnIdle { harness.visible.value = true }
        rule.onNodeWithTag("label-review-decaf-unknown").assertIsSelected()
        rule.onNodeWithTag("label-review-correction-isDecaf").assertDoesNotExist()
        rule.onNodeWithTag("label-review-decaf-false").performClick().assertIsSelected()
        save()
        rule.runOnIdle {
            assertTrue(harness.changes.contains("isDecaf" to "false"))
            assertFalse(harness.saved.last().isDecaf)
        }
    }

    @Test
    fun saveFindsInvalidNameThenWeightAndKeepsBothCorrections() {
        val harness = ReviewHarness(BagFormSnapshot(weight = "not a weight"))
        show(harness)
        save()
        rule.onNodeWithText(label(R.string.msg_review_name_required)).assertIsDisplayed()
        editor("name").assertIsFocused().performTextReplacement("Las Flores")
        save()
        rule.onNodeWithTag("label-review-editor-weight").assertIsDisplayed()
        editor("weight").assertIsFocused()
        rule.onNodeWithText(label(R.string.msg_invalid_weight)).assertIsDisplayed()
        capture("label-review-invalid-weight")
        rule.runOnIdle { assertTrue(harness.saved.isEmpty()) }
        editor("weight").performTextReplacement("250")
        save()

        rule.runOnIdle { assertEquals(SavedValues("Las Flores", null, null, null, 250f), harness.saved.single()) }
    }

    @Test
    fun failedSaveStaysVisibleAndRetryRetainsReviewedValues() {
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores", originCountry = "Colombia", weight = "250"))
        harness.failFirstSave = true
        show(harness)
        save()
        rule.onNodeWithText(label(R.string.msg_review_save_failed)).assertIsDisplayed()
        capture("label-review-save-error")
        rule.onNodeWithText(label(R.string.action_review_retry_save)).assertIsDisplayed().assertIsEnabled()
        scrollTo(hasTestTag("label-review-name"))
        editor("name").assertTextContains("Las Flores")
        rule.onNodeWithText(label(R.string.msg_review_save_failed)).assertIsDisplayed()
        save()

        rule.runOnIdle {
            assertEquals(2, harness.saved.size)
            assertEquals(harness.saved[0], harness.saved[1])
        }
        rule.onNodeWithText(label(R.string.msg_review_save_failed)).assertDoesNotExist()
    }

    @Test
    fun compactReviewAt200PercentKeepsSaveAndConflictActionsReachableInBothThemes() {
        assumeTrue(
            "Run with adb shell settings put system font_scale 2.0 and adb shell wm density 540.",
            context.resources.configuration.fontScale >= 1.9f,
        )
        val harness = ReviewHarness(BagFormSnapshot(name = "Las Flores", roaster = "April Coffee",
            originCountry = "Colombia", originRegion = "Huila", weight = "250"))
        val photo = labelPhoto()
        harness.photoUris = photo
        harness.evidence = mapOf("origin" to evidence("origin", "Ethiopia", photo, "Origin: Ethiopia",
            BagPhotoRect(0.04f, 0.20f, 0.96f, 0.30f)))
        harness.suggestions.value = mapOf("origin" to "Ethiopia")
        show(harness)
        rule.onNodeWithTag("label-review-save").assertIsDisplayed().assertIsEnabled()
        capture("label-review-200-light")
        assertTextFits()

        open("origin")
        capture("label-review-200-editor-light")
        scrollTo(hasText(label(R.string.action_review_use_reading)))
        rule.onNodeWithText(label(R.string.action_review_use_reading)).assertIsDisplayed().assertIsEnabled()
        scrollTo(hasText(label(R.string.action_review_keep_mine)))
        rule.onNodeWithText(label(R.string.action_review_keep_mine)).assertIsDisplayed().assertIsEnabled()
        rule.runOnIdle { harness.dark.value = true }
        capture("label-review-200-dark")
        assertTextFits()
        rule.runOnIdle { harness.dark.value = false }
        capture("label-review-200-return-light")
        save()
        rule.runOnIdle { assertEquals("Colombia", harness.saved.single().origin) }
    }

    private fun show(harness: ReviewHarness) {
        rule.setContent {
            StarlitCoffeeTheme(dynamicColor = harness.dynamicColor.value, darkTheme = harness.dark.value) {
                if (harness.visible.value) {
                    AddBagSheet(
                        initialFormOverride = harness.form.value,
                        ocrPrefill = harness.ocr.value,
                        capturedPhotoUris = harness.photoUris,
                        fieldEvidence = harness.evidence,
                        recognition = if (harness.processing) RecognitionPresentation(status = RecognitionStatusText.CHECKING_MORE_DETAILS)
                            else RecognitionPresentation(),
                        isProcessing = harness.processing,
                        isSaving = harness.isSaving.value,
                        existingBags = harness.existingBags,
                        traceabilityUrl = harness.traceabilityUrl,
                        pendingSuggestions = harness.suggestions.value,
                        reviewedFields = harness.reviewed.value,
                        unknownFields = harness.unknown.value,
                        saveErrorResId = harness.saveError.value,
                        onAcceptSuggestion = { key, expected ->
                            if (harness.allowAcceptance && harness.suggestions.value[key] == expected) {
                                harness.accepted += key to expected
                                harness.suggestions.value = harness.suggestions.value - key
                                harness.reviewed.value = harness.reviewed.value + key
                                harness.unknown.value = harness.unknown.value - key
                                true
                            } else false
                        },
                        onUserFieldChange = { key, value ->
                            harness.changes += key to value
                            harness.suggestions.value = harness.suggestions.value - key
                            harness.reviewed.value = harness.reviewed.value + key
                            harness.unknown.value = if (value.isNullOrBlank()) harness.unknown.value + key else harness.unknown.value - key
                        },
                        onExploreQrUrl = { _, result -> harness.qrResult = result },
                        onScanMorePhotos = ({ harness.photoRequests += 1 }).takeIf { harness.photoUris != null },
                        onDismiss = { harness.visible.value = false },
                        initialReviewLocation = harness.location.value,
                        onReviewLocationChange = { harness.location.value = it },
                        preserveDraftOnDismiss = true,
                        onSave = { name, roaster, origin, region, _, _, _, _, weight, _, _, _, _, isDecaf, _, roastDate, _ ->
                            harness.saved += SavedValues(name, roaster, origin, region, weight, roastDate, isDecaf)
                            harness.saveError.value = if (harness.failFirstSave && harness.saved.size == 1)
                                R.string.msg_review_save_failed else null
                        },
                    )
                }
            }
        }
    }

    private fun open(key: String) {
        scrollTo(hasTestTag("label-review-$key"))
        rule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("label-review-$key"))).performClick()
    }

    private fun editor(key: String): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("label-review-$key")), useUnmergedTree = true)

    private fun scrollTo(matcher: SemanticsMatcher) {
        rule.onNodeWithTag("label-review-list").performScrollToNode(matcher)
    }

    private fun save() {
        // IME insets can animate the button between coordinate lookup and touch dispatch.
        rule.onNodeWithTag("label-review-save").performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun startQrLookup() {
        scrollTo(hasText(label(R.string.action_show_optional_details)))
        rule.onNodeWithText(label(R.string.action_show_optional_details)).performClick()
        scrollTo(hasText(label(R.string.label_review_source_notes)))
        rule.onNodeWithText(label(R.string.label_review_source_notes)).performClick()
        scrollTo(hasText(label(R.string.action_explore_extract)))
        rule.onNodeWithText(label(R.string.action_explore_extract)).performClick()
    }

    private fun qrMetadata(origin: String, region: String? = null) = QrCoffeeMetadata(
        sourceUrl = "https://example.com/coffee/las-flores",
        finalUrl = "https://example.com/coffee/las-flores",
        host = "example.com",
        origin = origin,
        region = region,
    )

    private fun label(resourceId: Int): String = context.getString(resourceId)

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val configuration = context.resources.configuration
        val output = File(context.getExternalFilesDir(null),
            "label-review-polish/$name-${configuration.screenWidthDp}dp-${(configuration.fontScale * 100).toInt()}pct.png")
        output.parentFile?.mkdirs()
        output.outputStream().use {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } finally { screenshot.recycle() }
        }
    }

    private fun assertTextFits() {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            val node = nodes[index].fetchSemanticsNode()
            if (!node.config.contains(SemanticsActions.SetText)) {
                val layouts = mutableListOf<TextLayoutResult>()
                nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                layouts.forEach { layout ->
                    val message = "Clipped text: ${layout.layoutInput.text}; size=${layout.size}"
                    assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                    repeat(layout.lineCount) { line ->
                        assertTrue(message, layout.getLineLeft(line) >= -1f)
                        assertTrue(message, layout.getLineRight(line) <= layout.size.width + 1f)
                    }
                }
            }
        }
    }

    private fun labelPhoto(fileName: String = "label-review-test-source.jpg"): String {
        val output = File(context.cacheDir, fileName)
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(247, 243, 230))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 36f }
            listOf("LAS FLORES" to 80f, "April Coffee" to 132f, "Origin: Ethiopia" to 210f,
                "Region: Sidama" to 268f, "250 g" to 340f, "Washed" to 396f).forEach { (text, top) ->
                canvas.drawText(text, 28f, top, paint)
            }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally { bitmap.recycle() }
        return output.toURI().toString()
    }

    private fun evidence(key: String, reading: String, uri: String, text: String, region: BagPhotoRect) = BagFieldEvidence(
        fieldName = key,
        value = reading,
        sourceType = BagFieldSourceType.OCR,
        confidence = BagFieldConfidence.NEEDS_REVIEW,
        supportingText = text,
        previewUri = uri,
        previewRect = region,
    )

    private data class SavedValues(
        val name: String,
        val roaster: String?,
        val origin: String?,
        val region: String?,
        val weight: Float?,
        val roastDate: Long? = null,
        val isDecaf: Boolean = false,
    )

    private class ReviewHarness(initial: BagFormSnapshot, dark: Boolean = false) {
        val form = mutableStateOf(initial)
        val ocr = mutableStateOf<OcrFieldExtractor.OcrExtractionResult?>(null)
        val dark = mutableStateOf(dark)
        val dynamicColor = mutableStateOf(false)
        val suggestions = mutableStateOf<Map<String, String>>(emptyMap())
        val reviewed = mutableStateOf<Set<String>>(emptySet())
        val unknown = mutableStateOf<Set<String>>(emptySet())
        val saveError = mutableStateOf<Int?>(null)
        val isSaving = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val location = mutableStateOf(BagDraftReviewLocation())
        val accepted = mutableListOf<Pair<String, String>>()
        val changes = mutableListOf<Pair<String, String?>>()
        val saved = mutableListOf<SavedValues>()
        var evidence: Map<String, BagFieldEvidence> = emptyMap()
        var existingBags: List<CoffeeBagEntity> = emptyList()
        var photoUris: String? = null
        var photoRequests = 0
        var processing = false
        var traceabilityUrl: String? = null
        var qrResult: ((QrCoffeeMetadata?) -> Unit)? = null
        var failFirstSave = false
        var allowAcceptance = true
    }
}
