package com.adsamcik.starlitcoffee.benchmark

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.test.corpus.QualityReport
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Cross-cutting helpers for the instrumented quality tests.
 *
 * # Required vs best-effort skipping
 *
 * By default a missing dependency (corpus not pushed, Mindlayer not installed)
 * cleanly SKIPS via JUnit `Assume`, so a casual `connectedDebugAndroidTest`
 * run on a device without Mindlayer stays green instead of red-failing on
 * setup. The dedicated quality lane must NOT be able to silently pass without
 * actually running, so pass the instrumentation argument
 * `-e starlit.quality.required true` to turn those skips into hard failures:
 *
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.starlit.quality.required=true \
 *   -Pandroid.testInstrumentationRunnerArguments.class=...BagScanBestCaseGateTest
 * ```
 */
internal object QualityTestSupport {

    private const val REQUIRED_ARG = "starlit.quality.required"

    /**
     * Poll [check] until it returns true or [timeoutMs] elapses. The Mindlayer
     * SDK binds its service asynchronously, so a freshly-constructed provider
     * reports `isAvailable() == false` for the first second or two on a cold
     * connection. Without this wait the quality tests skip spuriously even
     * though the service is up.
     */
    fun awaitTrue(timeoutMs: Long = 60_000L, intervalMs: Long = 1_000L, check: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (check()) return true
            Thread.sleep(intervalMs)
        }
        return check()
    }

    /**
     * Suspend variant of [awaitTrue] for checks that are themselves suspend
     * (e.g. `OcrService.isAvailable()`). Uses a cooperative delay so it can run
     * on the test's coroutine without blocking the dispatcher thread.
     */
    suspend fun awaitTrueSuspending(
        timeoutMs: Long = 60_000L,
        intervalMs: Long = 1_000L,
        check: suspend () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (check()) return true
            kotlinx.coroutines.delay(intervalMs)
        }
        return check()
    }

    /** True when the run was started in the required quality lane. */
    fun isRequiredMode(): Boolean =
        InstrumentationRegistry.getArguments().getString(REQUIRED_ARG)?.equals("true", ignoreCase = true) == true

    /**
     * Hard-fail when in required mode, otherwise skip. Use for setup
     * preconditions (corpus present, Mindlayer available) so the required
     * lane can never go green without exercising the gate.
     */
    fun requireOrAssume(message: String, condition: Boolean) {
        if (isRequiredMode()) {
            assertTrue("[required] $message", condition)
        } else {
            assumeTrue(message, condition)
        }
    }

    /**
     * Persist [report] as both machine-readable JSON and a human-readable text
     * table under the app's external fixtures dir, and echo the table to
     * logcat. Returns the JSON file.
     */
    fun writeReport(context: Context, report: QualityReport, baseName: String): File {
        val dir = CorpusFixture.fixturesDir(context)
        val jsonFile = File(dir, "$baseName.json")
        val textFile = File(dir, "$baseName.txt")
        jsonFile.writeText(report.toJson())
        textFile.writeText(report.toText())
        report.toText().lineSequence().forEach { Log.i(CorpusFixture.BENCHMARK_TAG, it) }
        Log.i(CorpusFixture.BENCHMARK_TAG, "Report written: ${jsonFile.absolutePath}")
        return jsonFile
    }
}
