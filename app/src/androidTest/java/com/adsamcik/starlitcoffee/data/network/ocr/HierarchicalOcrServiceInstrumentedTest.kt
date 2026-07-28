package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HierarchicalOcrServiceInstrumentedTest {

    @Test
    fun refinementCropIsRecycledAfterSuccessWhileInputRemainsCallerOwned() = runBlocking {
        val input = createInput()
        val delegate = RecordingOcrService(refinementResult = EMPTY_RESULT)
        try {
            HierarchicalOcrService(delegate, maxRefineRegions = 1).recognize(input)

            val refinement = requireNotNull(delegate.refinementBitmap)
            assertTrue("service-owned refinement bitmap must be recycled", refinement.isRecycled)
            assertFalse("caller-owned input must remain valid", input.isRecycled)
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

    @Test
    fun refinementCropIsRecycledWhenDelegateThrows() = runBlocking {
        val input = createInput()
        val delegate = RecordingOcrService(refinementFailure = IllegalStateException("boom"))
        try {
            try {
                HierarchicalOcrService(delegate, maxRefineRegions = 1).recognize(input)
                fail("delegate exception should propagate")
            } catch (_: IllegalStateException) {
                // Expected: the ownership assertion below is the regression check.
            }

            val refinement = requireNotNull(delegate.refinementBitmap)
            assertTrue("failed refinement bitmap must be recycled", refinement.isRecycled)
            assertFalse("delegate failure must not recycle caller input", input.isRecycled)
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

    @Test
    fun refinementCropIsRecycledWhenDelegateIsCancelled() = runBlocking {
        val input = createInput()
        val delegate = RecordingOcrService(
            refinementFailure = CancellationException("cancel refinement"),
        )
        try {
            try {
                HierarchicalOcrService(delegate, maxRefineRegions = 1).recognize(input)
                fail("cancellation should propagate")
            } catch (_: CancellationException) {
                // Expected: cancellation must still execute the recycling finally block.
            }

            val refinement = requireNotNull(delegate.refinementBitmap)
            assertTrue("cancelled refinement bitmap must be recycled", refinement.isRecycled)
            assertFalse("cancellation must not recycle caller input", input.isRecycled)
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

    @Test
    fun fullFrameProblemRegionNeverRecyclesCallerInput() = runBlocking {
        val input = createInput(width = 800, height = 800)
        val fullFrame = Rect(0, 0, input.width, input.height)
        val delegate = RecordingOcrService(
            initialResult = problemResult(fullFrame),
            refinementResult = EMPTY_RESULT,
        )
        try {
            HierarchicalOcrService(delegate, maxRefineRegions = 1).recognize(input)

            assertNotNull(delegate.refinementBitmap)
            assertFalse(
                "a full-frame crop may alias the source and must never recycle caller input",
                input.isRecycled,
            )
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

    private class RecordingOcrService(
        private val initialResult: RecognizedText = problemResult(Rect(100, 100, 250, 150)),
        private val refinementResult: RecognizedText? = null,
        private val refinementFailure: Throwable? = null,
    ) : OcrService {
        private var calls = 0
        var refinementBitmap: Bitmap? = null
            private set

        override fun close() = Unit

        override suspend fun isAvailable(): Boolean = true

        override suspend fun recognize(bitmap: Bitmap): RecognizedText? {
            calls++
            if (calls == 1) return initialResult

            refinementBitmap = bitmap
            refinementFailure?.let { throw it }
            return refinementResult
        }
    }

    private companion object {
        val EMPTY_RESULT = RecognizedText(fullText = "", blocks = emptyList())

        fun createInput(width: Int = 1_000, height: Int = 1_000): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        fun problemResult(bounds: Rect): RecognizedText {
            val text = "MerrybeansKolumbieTumbagaDecaf"
            return RecognizedText(
                fullText = text,
                blocks = listOf(
                    RecognizedTextBlock(
                        text = text,
                        boundingBox = bounds,
                        lines = emptyList(),
                    ),
                ),
            )
        }

        fun Bitmap.recycleUnlessAlreadyRecycled() {
            if (!isRecycled) recycle()
        }
    }
}
