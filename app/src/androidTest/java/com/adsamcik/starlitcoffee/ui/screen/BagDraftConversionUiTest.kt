package com.adsamcik.starlitcoffee.ui.screen

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.work.BagDraftStore
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.viewmodel.BagScanDraftViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drafts live in a unique cache directory; the installed app's data and workers are untouched. */
@RunWith(AndroidJUnit4::class)
class BagDraftConversionUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun newBagConvertsTheCurrentRouteWithoutPoppingAndReopensAsAddNew() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "draft-conversion-${UUID.randomUUID()}")
        val application = object : Application() {
            override fun getNoBackupFilesDir(): File = directory
        }
        val sessionId = UUID.randomUUID().toString()
        val store = ViewModelStore()
        var exits = 0
        var missing = 0
        lateinit var draft: BagScanDraftViewModel
        lateinit var brew: BrewViewModel
        try {
            BagDraftStore.ensure(application, sessionId, emptyList(), BagReviewContext.rescan(42L), RecognitionPreference.DISABLED)
            BagDraftStore.beginGeneration(application, sessionId, "rescan-generation", "rescan-work")
            BagDraftStore.applyResult(
                application, sessionId, "rescan-generation", "rescan-work",
                BagPhotoProcessingResult(
                    ocrPrefill = OcrFieldExtractor.OcrExtractionResult(name = "New coffee"),
                    fieldEvidence = mapOf("name" to BagFieldEvidence(
                        fieldName = "name", value = "New coffee", sourceType = BagFieldSourceType.OCR,
                        confidence = BagFieldConfidence.HIGH,
                    )),
                    llmStatus = LlmEnrichmentStatus.SUCCEEDED,
                ),
                terminal = true,
            )
            composeRule.runOnIdle {
                draft = BagScanDraftViewModel(application, SavedStateHandle(), sessionId)
                brew = BrewViewModel()
                brew.disableLabelRecognition()
                store.put("draft", draft)
                store.put("brew", brew)
            }
            composeRule.setContent {
                StarlitCoffeeTheme(dynamicColor = false) {
                    BagDraftReviewRoute(
                        draftViewModel = draft, brewViewModel = brew,
                        existingBags = listOf(CoffeeBagEntity(id = 42L, name = "Former target")),
                        onExit = { exits++ }, onScanMore = {}, onMissing = { missing++ },
                    )
                }
            }
            composeRule.onNodeWithText(context.getString(R.string.action_new_bag)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.action_save_coffee)).assertIsDisplayed()
            composeRule.runOnIdle {
                assertEquals(0, exits)
                assertEquals(0, missing)
                assertEquals(BagReviewContext.addNew(), BagDraftStore.read(application, sessionId)?.reviewContext)
                val reopened = BagScanDraftViewModel(application, SavedStateHandle(), sessionId)
                store.put("reopened", reopened)
                assertEquals(BagReviewContext.addNew(), reopened.draft.value?.reviewContext)
            }
        } finally {
            composeRule.runOnIdle { store.clear() }
            directory.deleteRecursively()
        }
    }
}
