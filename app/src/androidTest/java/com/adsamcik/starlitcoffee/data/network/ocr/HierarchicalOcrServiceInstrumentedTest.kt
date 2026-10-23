package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HierarchicalOcrServiceInstrumentedTest {

    @Test
    fun refinedBlockAndLineGeometryMapsThroughPaddingAndUpscale() = runBlocking {
        val input = createInput()
        val localBounds = Rect(80, 40, 400, 120)
        val localCorners = arrayOf(Point(80, 40), Point(400, 40), Point(400, 120), Point(80, 120))
        val delegate = RecordingOcrService(
            refinementResult = RecognizedText(
                fullText = "Colombia",
                blocks = listOf(
                    RecognizedTextBlock(
                        text = "Colombia",
                        boundingBox = localBounds,
                        lines = listOf(RecognizedTextLine("Colombia", localBounds, localCorners)),
                    ),
                ),
            ),
        )
        try {
            val result = requireNotNull(HierarchicalOcrService(delegate).recognize(input))
            val refined = result.blocks.last()

            // Original 100,100..250,150 region gets 7px/2px padding and a 4x upscale.
            assertEquals(Rect(113, 108, 193, 128), refined.boundingBox)
            assertEquals(refined.boundingBox, refined.lines.single().boundingBox)
            assertArrayEquals(
                arrayOf(Point(113, 108), Point(193, 108), Point(193, 128), Point(113, 128)),
                refined.lines.single().cornerPoints,
            )
            assertEquals(
                "original OCR geometry must be preserved",
                Rect(100, 100, 250, 150),
                result.blocks.first().boundingBox,
            )
            assertEquals("delegate result must not be mutated", Rect(80, 40, 400, 120), localBounds)
            assertEquals(Point(80, 40), localCorners.first())
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

    @Test
    fun refinedGeometryUsesClippedPaddingAndActualRoundedScale() = runBlocking {
        val input = createInput()
        val delegate = RecordingOcrService(
            initialResult = problemResult(Rect(0, 100, 631, 500)),
            refinementResult = RecognizedText(
                fullText = "Colombia",
                blocks = listOf(
                    RecognizedTextBlock("Colombia", Rect(0, 0, 450, 600), emptyList()),
                ),
            ),
        )
        try {
            val result = requireNotNull(HierarchicalOcrService(delegate).recognize(input))

            // Left padding clips to zero: crop is 662x440, rounded upscale is 902x600.
            // 450 * 662 / 902 = 330.266..., so the enclosing right edge must be 331.
            assertEquals(Rect(0, 80, 331, 520), result.blocks.last().boundingBox)
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

    @Test
    fun unscaledRefinementOffsetsGeometryAndKeepsMissingGeometry() = runBlocking {
        val input = createInput(width = 1_400, height = 1_400)
        val delegate = RecordingOcrService(
            initialResult = problemResult(Rect(300, 300, 1_000, 1_000)),
            refinementResult = RecognizedText(
                fullText = "Colombia",
                blocks = listOf(
                    RecognizedTextBlock(
                        text = "Colombia",
                        boundingBox = Rect(10, 20, 110, 120),
                        lines = listOf(RecognizedTextLine("Colombia", null, null)),
                    ),
                ),
            ),
        )
        try {
            val result = requireNotNull(HierarchicalOcrService(delegate).recognize(input))

            assertEquals(Rect(275, 285, 375, 385), result.blocks.last().boundingBox)
            assertEquals(RecognizedTextLine("Colombia", null, null), result.blocks.last().lines.single())
        } finally {
            input.recycleUnlessAlreadyRecycled()
        }
    }

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
