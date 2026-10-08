package com.adsamcik.starlitcoffee.diagnostics

import android.content.Context
import android.os.SystemClock
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import com.adsamcik.starlitcoffee.scan.observability.LegacyLlmDiagnosticsStore
import com.adsamcik.starlitcoffee.scan.observability.TraceboxLlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.util.commitSynchronously
import dev.tracebox.Tracebox
import dev.tracebox.TraceboxConfiguration
import dev.tracebox.api.DeleteReport
import dev.tracebox.api.DeleteRequest
import dev.tracebox.api.PolicyUpdateResult
import dev.tracebox.api.TraceboxPolicy
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run only in a disposable app installation: these cases exercise real diagnostic deletion. */
@RunWith(AndroidJUnit4::class)
class AiDiagnosticsPrivacyInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireDisposableInstallation() {
        assumeTrue("privacy deletion tests require an isolated app ID", context.packageName.endsWith(".privacycheck"))
    }

    @Test
    fun legacyMigrationDeletesSamplesUnknownKeysAndBackup() {
        seedLegacySamples()
        val backup = File(context.dataDir, "shared_prefs/${LegacyLlmDiagnosticsStore.PREFS_NAME}.xml.bak")
        backup.writeText("legacy-output-marker legacy-error-marker")

        assertTrue(LegacyLlmDiagnosticsStore.clear(context))
        assertLegacyAbsent()
        assertTrue(LegacyLlmDiagnosticsStore.clear(context))
    }

    @Test
    fun disableDeleteAndSeedRestartState() {
        // Managed-only fixture uses the installed, pinned runtime and real disk/policy fences.
        Tracebox.current()?.close()
        val activePolicy = TraceboxPolicy.standard().copy(captures = emptySet())
        val handle = Tracebox.install(
            context,
            TraceboxConfiguration.Builder()
                .setInitialPolicy(activePolicy)
                .setPersistRequestedProfile(true)
                .setNativeCaptureEnabled(false)
                .build(),
        )
        assertEquals(PolicyUpdateResult.SUCCESS, handle.updatePolicy(activePolicy))
        val recorder = TraceboxLlmDiagnosticsRecorder(handle.log)
        recorder.record(diagnostic())
        awaitCondition { containsAiRecord() }
        assertFalse(traceboxFilesContain("legacy-output-marker"))
        assertFalse(traceboxFilesContain("legacy-error-marker"))

        val finishPass = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val inFlightPass = executor.submit {
                check(finishPass.await(5, TimeUnit.SECONDS))
                recorder.record(diagnostic())
            }
            assertEquals(PolicyUpdateResult.SUCCESS, handle.updatePolicy(TraceboxPolicy.disabled()))
            val recordedBeforeCompletion = handle.summary.value.recordedValueCount
            finishPass.countDown()
            inFlightPass.get(5, TimeUnit.SECONDS)
            assertEquals(recordedBeforeCompletion, handle.summary.value.recordedValueCount)
            assertFalse(handle.policy.value.enabled)
        } finally {
            finishPass.countDown()
            executor.shutdownNow()
        }

        seedLegacySamples()
        val controlsHandle = LegacyCleanupTraceboxHandle(handle) { LegacyLlmDiagnosticsStore.clear(context) }
        assertEquals(DeleteReport.COMPLETE, controlsHandle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertLegacyAbsent()
        assertFalse(handle.policy.value.enabled)
        assertFalse(containsAiRecord())
        recorder.record(diagnostic())
        assertFalse(containsAiRecord())

        // The separate restart case proves Application.attachBaseContext removes old files
        // and the default production install respects Tracebox's persisted disabled state.
        assertTrue(context.getSharedPreferences("ai_privacy_test_state", Context.MODE_PRIVATE).commitSynchronously {
            putInt("before_restart_pid", Process.myPid())
        })
        seedLegacySamples()
    }

    @Test
    fun restartPreservesDisableAndPurgesLegacySamples() {
        val previousPid = context.getSharedPreferences("ai_privacy_test_state", Context.MODE_PRIVATE)
            .getInt("before_restart_pid", 0)
        assumeTrue("run after the separate setup phase and process restart", previousPid != 0 && previousPid != Process.myPid())
        val handle = requireNotNull(Tracebox.current())
        assertFalse("disabled policy must survive a process restart", handle.policy.value.enabled)
        assertLegacyAbsent()
        assertFalse(containsAiRecord())
        TraceboxLlmDiagnosticsRecorder(handle.log).record(diagnostic())
        assertFalse(containsAiRecord())
    }

    private fun diagnostic() = LlmPassDiagnostic(
        timestampMs = System.currentTimeMillis(),
        pass = LlmPassDiagnostic.Pass.TEXT,
        status = LlmPassDiagnostic.Status.SUCCESS,
        elapsedMs = 15L,
        maxTokens = 4096,
        promptCharLen = 30,
        outputCharLen = 20,
    )

    private fun seedLegacySamples() {
        assertTrue(
            context.getSharedPreferences(LegacyLlmDiagnosticsStore.PREFS_NAME, Context.MODE_PRIVATE)
                .commitSynchronously {
                    putString("passes", "[{\"outputSample\":\"legacy-output-marker\",\"errorMessage\":\"legacy-error-marker\"}]")
                    putString("unknown_legacy_key", "legacy-output-marker")
                },
        )
    }

    private fun assertLegacyAbsent() {
        val directory = File(context.dataDir, "shared_prefs")
        assertFalse(File(directory, "${LegacyLlmDiagnosticsStore.PREFS_NAME}.xml").exists())
        assertFalse(File(directory, "${LegacyLlmDiagnosticsStore.PREFS_NAME}.xml.bak").exists())
    }

    private fun containsAiRecord(): Boolean = traceboxFilesContain("AI pass=")

    private fun traceboxFilesContain(marker: String): Boolean =
        context.noBackupFilesDir.walkTopDown().filter(File::isFile).any { file ->
            file.readBytes().toString(Charsets.ISO_8859_1).contains(marker)
        }

    private fun awaitCondition(condition: () -> Boolean) {
        val expires = SystemClock.elapsedRealtime() + 5_000L
        while (!condition() && SystemClock.elapsedRealtime() < expires) Thread.sleep(20L)
        assertTrue("diagnostic record must reach durable storage", condition())
    }
}
