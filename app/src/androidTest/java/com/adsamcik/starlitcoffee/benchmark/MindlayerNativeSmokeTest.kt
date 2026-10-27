package com.adsamcik.starlitcoffee.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.mindlayer.sdk.InferenceBackend
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.starlitcoffee.data.network.ocr.MindlayerOcrService
import com.adsamcik.starlitcoffee.scan.observability.ConnectionStatus
import com.adsamcik.starlitcoffee.scan.observability.MindlayerConnectionTester
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration

/** Opt-in native readiness check; requires approval and installed OCR and chat models. */
@RunWith(AndroidJUnit4::class)
class MindlayerNativeSmokeTest {
    @Before
    fun requireNativeLane() {
        QualityTestSupport.requireOrAssume(
            "Native inference is opt-in: pass starlit.quality.required=true.",
            QualityTestSupport.isRequiredMode(),
        )
    }

    @Test
    fun nativeOcrReadsItsTestLabel() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ocr = MindlayerOcrService(context)
        val label = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        try {
            Canvas(label).apply {
                drawColor(Color.WHITE)
                drawText("COFFEE", 32f, 360f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 72f
                })
            }
            assertTrue("Mindlayer OCR is not ready", QualityTestSupport.awaitTrueSuspending { ocr.isAvailable() })
            val reading = requireNotNull(withTimeout(60_000L) { ocr.recognize(label) })
            assertTrue(
                "Mindlayer OCR did not read its label: blocks=${reading.blocks.size}, text='${reading.fullText.take(80)}'",
                reading.fullText.contains("COFFEE", ignoreCase = true),
            )
            Log.i(CorpusFixture.BENCHMARK_TAG, "Native OCR smoke: characters=${reading.fullText.length}")
        } finally {
            label.recycle()
            ocr.close()
        }
    }

    @Test
    fun cpuEngineAtScanContextGeneratesText() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = Mindlayer.connect(context.applicationContext)
        try {
            withTimeout(120_000L) {
                client.awaitConnected(Duration.INFINITE)
                client.prewarmForContext(8192, InferenceBackend.CPU)
                val ready = QualityTestSupport.awaitTrueSuspending(timeoutMs = 120_000L) {
                    try {
                        val info = client.getEngineInfo()
                        info.maxTokens == 8192 && info.backend == "CPU"
                    } catch (error: MindlayerException) {
                        if (error.codeName == "ENGINE_INITIALIZING") false else throw error
                    }
                }
                assertTrue("CPU engine did not reach the scan context", ready)
            }
            val result = withTimeout(120_000L) { MindlayerConnectionTester.runTestPrompt(context) }
            assertEquals(result.errorMessage, ConnectionStatus.CONNECTED, result.status)
            val generated = requireNotNull(result.testResult) { "The service returned no generation result" }
            assertFalse("Mock engines cannot satisfy native smoke", generated.response.startsWith("[mock]", ignoreCase = true))
            assertTrue("The native model returned no text", generated.response.isNotBlank())
            assertTrue("The native model emitted no text delta", generated.tokenCount > 0)
            val info = client.getEngineInfo()
            assertEquals(8192, info.maxTokens)
            assertEquals("CPU", info.backend)
            Log.i(CorpusFixture.BENCHMARK_TAG,
                "Native CPU smoke: model=${info.modelId}, context=${info.maxTokens}, " +
                    "textDeltas=${generated.tokenCount}, latencyMs=${generated.latencyMs}")
        } finally {
            client.disconnect()
        }
    }
}
