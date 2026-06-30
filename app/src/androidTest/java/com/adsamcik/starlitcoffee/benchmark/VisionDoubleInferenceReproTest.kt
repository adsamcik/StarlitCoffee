package com.adsamcik.starlitcoffee.benchmark

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.mindlayer.sdk.InferenceHandle
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * MINIMAL REPRO + diagnostic for LiteRT-LM #2028 ("the on-device engine SIGSEGVs
 * in `liblitertlm_jni.so` on the SECOND multimodal inference per process").
 *
 * StarlitCoffee's [MindlayerLlmInferenceProvider] guards against this with a
 * process-static one-shot flag (`visionInferenceConsumed`) that permanently
 * disables vision after a single image inference, assuming the second is FATAL.
 * This test bypasses that guard — calling the raw Mindlayer SDK image inference
 * TWICE in the same process — and writes a durable marker after every step.
 *
 * EMPIRICAL RESULT (Mindlayer SDK 1.0.0-alpha.2, emulator API 36, 2026-06-29):
 *  - #2028 is REAL: the second image inference DID SIGSEGV — logcat shows
 *    "Process <pid> exited due to signal 11 (Segmentation fault)" + a tombstone,
 *    followed by `liblitertlm_jni.so` reloading.
 *  - BUT the crash is CONTAINED to Mindlayer's ISOLATED inference SERVICE
 *    process — the app/test process was untouched. The SDK surfaced a graceful,
 *    catchable `engine_initializing` (MLERR:1001) error, the service
 *    auto-restarted, and after a retry the SECOND vision inference SUCCEEDED
 *    (`V1_OK chars=135` → `V2_OK chars=135` → `DONE`).
 *
 * CONCLUSION: the "second vision = fatal, never retry" assumption behind the
 * hard one-shot guard is OVER-CONSERVATIVE for the current SDK. Vision is
 * RECOVERABLE: wait out the engine restart and retry. This means StarlitCoffee
 * could (a) run multiple vision passes — e.g. front AND back — instead of one,
 * and (b) use `outputJson(max_retries>0)` on the vision pass safely. The cost is
 * latency: every "extra" image inference forces a service process restart +
 * native reload + engine re-init (seconds).
 *
 * Guarded with @Ignore because it deliberately crashes the inference service
 * (tombstones, slow restart) — remove @Ignore and run it by class to re-verify
 * the behaviour after an SDK bump:
 * `adb shell am instrument -w -e class \
 *   com.adsamcik.starlitcoffee.benchmark.VisionDoubleInferenceReproTest \
 *   com.adsamcik.starlitcoffee.debug.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class VisionDoubleInferenceReproTest {

    @Ignore("Diagnostic only — deliberately crashes the inference service; remove to re-verify #2028")
    @Test
    fun secondVisionInferenceInSameProcess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val log = File(CorpusFixture.fixturesDir(context), "repro-2028.log")
        log.writeText("") // fresh
        fun mark(line: String) {
            val stamped = "${System.currentTimeMillis()} $line"
            log.appendText(stamped + "\n")
            Log.i(TAG, stamped)
        }

        // Readiness via the shared client the provider also uses.
        val provider = MindlayerLlmInferenceProvider(context)
        assumeTrue(
            "Mindlayer not available — start the service + approve the app.",
            QualityTestSupport.awaitTrue { provider.isAvailable() },
        )

        val corpus = CorpusFixture.load()
        assumeTrue("Corpus not pushed — run ./gradlew pushTestImages.", corpus != null)
        val bag = corpus!!.bags.firstOrNull { CorpusFixture.frontPhotoFile(it).isFile }
        assumeTrue("No corpus front image on device.", bag != null)
        val photo = CorpusFixture.frontPhotoFile(bag!!)

        val mindlayer = Mindlayer.shared(context.applicationContext)

        suspend fun visionOnce(): String {
            // Retry on the graceful `engine_initializing` error: the service
            // restarts its engine after a multimodal inference (the #2028
            // mitigation), so the next call must wait for that to finish.
            var attempt = 0
            while (true) {
                attempt++
                mindlayer.awaitConnected(kotlin.time.Duration.INFINITE)
                val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                    ?: error("Could not decode ${photo.absolutePath}")
                try {
                    return withTimeout(TIMEOUT_MS) {
                        val handle = mindlayer.infer {
                            ephemeralSession {
                                systemPrompt = "You read coffee bag labels."
                                maxTokens = 8192
                            }
                            text("List the brand and any roast level you can read on this label.")
                            image(bitmap)
                            sampling { temperature = 0.1f; topK = 20; topP = 0.9f }
                            outputText()
                        }
                        (handle as InferenceHandle.Text).awaitText()
                    }
                } catch (e: Exception) {
                    val initializing = e.message?.contains("engine_initializing", ignoreCase = true) == true
                    if (initializing && attempt <= MAX_REINIT_RETRIES) {
                        mark("  retry#$attempt — engine_initializing, waiting ${REINIT_WAIT_MS}ms")
                        Thread.sleep(REINIT_WAIT_MS)
                    } else {
                        throw e
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }

        mark("START photo=${photo.name}")
        val first = visionOnce()
        mark("V1_OK chars=${first.length}")

        // The critical step: a SECOND image inference in the SAME process.
        mark("V2_START")
        val second = visionOnce()
        mark("V2_OK chars=${second.length}")
        mark("DONE — second vision inference completed; #2028 did NOT crash the process")
    }

    companion object {
        private const val TAG = "Repro2028"
        private const val TIMEOUT_MS = 360_000L
        private const val MAX_REINIT_RETRIES = 30
        private const val REINIT_WAIT_MS = 3_000L
    }
}
