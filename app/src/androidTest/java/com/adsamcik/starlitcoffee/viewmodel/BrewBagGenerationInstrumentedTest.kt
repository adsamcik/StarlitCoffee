package com.adsamcik.starlitcoffee.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.work.BagDraftField
import com.adsamcik.starlitcoffee.data.work.BagDraftPhase
import com.adsamcik.starlitcoffee.data.work.BagDraftStore
import com.adsamcik.starlitcoffee.data.work.BagExtractionScheduler
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises retry/skip's shared generation and delivery boundaries without enqueueing work. */
@RunWith(AndroidJUnit4::class)
class BrewBagGenerationInstrumentedTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val app: Application
        get() = instrumentation.targetContext.applicationContext as Application

    @Before
    fun requireDisposableInstallation() {
        assumeTrue("draft recovery fixtures require an isolated app ID", app.packageName.endsWith(".privacycheck"))
    }

    @Test
    fun retryAndSkipGenerationBoundariesAcceptTheirOwnResultsAndRejectOldResults() {
        withDraftViewModel { vm, sessionId, initialGeneration ->
            // Both public retry and skip paths use this central generation boundary.
            val retryGeneration = startGeneration(vm, sessionId)
            assertNotEquals(initialGeneration, retryGeneration)
            assertEquals(retryGeneration, BagDraftStore.read(app, sessionId)?.generationId)
            assertFalse(BagDraftStore.isAcceptingResult(app, sessionId, initialGeneration))
            assertTrue(BagDraftStore.isAcceptingResult(app, sessionId, retryGeneration))

            deliverResult(vm, sessionId, initialGeneration, result("Stale coffee", LlmEnrichmentStatus.SUCCEEDED))
            assertNull(vm.bagPhotoResult.value)
            assertNull(BagDraftStore.read(app, sessionId)?.field(BagDraftField.NAME)?.value)

            deliverResult(vm, sessionId, retryGeneration, result("Retry coffee", LlmEnrichmentStatus.SUCCEEDED))
            assertEquals(retryGeneration, vm.bagPhotoResult.value?.generationId)
            assertEquals("Retry coffee", BagDraftStore.read(app, sessionId)?.field(BagDraftField.NAME)?.value)
            assertEquals(RecognitionRunState.COMPLETE, BagDraftStore.read(app, sessionId)?.recognitionRunState)
            onMain { vm.consumeBagPhotoResult(sessionId) }

            val skipGeneration = startGeneration(vm, sessionId)
            assertNotEquals(retryGeneration, skipGeneration)
            assertEquals(skipGeneration, BagDraftStore.read(app, sessionId)?.generationId)
            assertFalse(BagDraftStore.isAcceptingResult(app, sessionId, retryGeneration))
            assertTrue(BagDraftStore.isAcceptingResult(app, sessionId, skipGeneration))

            deliverResult(vm, sessionId, retryGeneration, result("Late retry coffee", LlmEnrichmentStatus.SUCCEEDED))
            assertNull(vm.bagPhotoResult.value)
            assertEquals("Retry coffee", BagDraftStore.read(app, sessionId)?.field(BagDraftField.NAME)?.value)

            deliverResult(vm, sessionId, skipGeneration, result("Skip coffee", LlmEnrichmentStatus.NOT_RUN))
            assertEquals(skipGeneration, vm.bagPhotoResult.value?.generationId)
            assertEquals(LlmEnrichmentStatus.NOT_RUN, vm.bagPhotoResult.value?.result?.llmStatus)
            assertEquals("Skip coffee", BagDraftStore.read(app, sessionId)?.field(BagDraftField.NAME)?.value)
            assertEquals(RecognitionRunState.COMPLETE, BagDraftStore.read(app, sessionId)?.recognitionRunState)
        }
    }

    @Test
    fun newGenerationCannotReopenSavedOrDiscardedDrafts() {
        listOf(BagDraftPhase.SAVED, BagDraftPhase.DISCARDED).forEach { phase ->
            withDraftViewModel { vm, sessionId, initialGeneration ->
                val tombstone = requireNotNull(BagDraftStore.markPhase(app, sessionId, phase))

                val generation = startGeneration(vm, sessionId)

                assertNotEquals(initialGeneration, generation)
                assertEquals(tombstone, BagDraftStore.read(app, sessionId))
                assertFalse(BagDraftStore.isAcceptingResult(app, sessionId, initialGeneration))
                assertFalse(BagDraftStore.isAcceptingResult(app, sessionId, generation))
                deliverResult(vm, sessionId, generation, result("Late coffee", LlmEnrichmentStatus.SUCCEEDED))
                assertNull(vm.bagPhotoResult.value)
                assertEquals(tombstone, BagDraftStore.read(app, sessionId))
                assertTrue(tombstone.fields.isEmpty())
                assertNull(tombstone.resultJson)
            }
        }
    }

    private fun withDraftViewModel(block: (BrewViewModel, String, String) -> Unit) {
        val sessionId = UUID.randomUUID().toString()
        val initialGeneration = UUID.randomUUID().toString()
        val store = ViewModelStore()
        val vm = onMain {
            BrewViewModel(application = app).also { store.put("generation-fixture", it) }
        }
        try {
            BagDraftStore.ensure(
                app, sessionId, listOf("content://generation-fixture/front"), BagReviewContext.addNew(),
                RecognitionPreference.ENABLED,
            )
            BagDraftStore.beginGeneration(app, sessionId, initialGeneration)
            block(vm, sessionId, initialGeneration)
        } finally {
            onMain { store.clear() }
            BagExtractionScheduler.invalidateSessionGeneration(app, sessionId)
            // Only this fixture's unique file is removed; existing drafts are never enumerated.
            File(app.noBackupFilesDir, "bag_scan_drafts/draft_$sessionId.json").delete()
        }
    }

    private fun startGeneration(vm: BrewViewModel, sessionId: String): String = onMain {
        BrewViewModel::class.java.getDeclaredMethod("startNewBagExtractionGeneration", String::class.java)
            .apply { isAccessible = true }
            .invoke(vm, sessionId) as String
    }

    private fun deliverResult(
        vm: BrewViewModel,
        sessionId: String,
        generationId: String,
        result: BagPhotoProcessingResult,
    ) = onMain {
        BrewViewModel::class.java.getDeclaredMethod(
            "deliverBagPhotoResult", BagPhotoProcessingResult::class.java, String::class.java,
            String::class.java, String::class.java, BagReviewContext::class.java,
        ).apply { isAccessible = true }
            .invoke(vm, result, "in-memory", sessionId, generationId, BagReviewContext.addNew())
        Unit
    }

    private fun result(name: String, status: LlmEnrichmentStatus) = BagPhotoProcessingResult(
        ocrPrefill = OcrFieldExtractor.OcrExtractionResult(name = name),
        capturedPhotoUris = "content://generation-fixture/front",
        llmStatus = status,
    )

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
