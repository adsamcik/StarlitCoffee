package com.adsamcik.starlitcoffee.diagnostics

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.adsamcik.starlitcoffee.data.work.BagDraftField
import com.adsamcik.starlitcoffee.data.work.BagDraftPhase
import com.adsamcik.starlitcoffee.data.work.BagDraftStore
import com.adsamcik.starlitcoffee.data.work.BagExtractionScheduler
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticContext
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticFailure
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticMode
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticOutcome
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleEvent
import com.adsamcik.starlitcoffee.domain.scandiagnostics.scanCorrelationKey
import com.adsamcik.starlitcoffee.scan.observability.LegacyLlmDiagnosticsStore
import com.adsamcik.starlitcoffee.scan.observability.TraceboxLlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.scan.observability.TraceboxScanDiagnosticsRecorder
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import dev.tracebox.Tracebox
import dev.tracebox.TraceboxConfiguration
import dev.tracebox.TraceboxPackageDisclosureActivity
import dev.tracebox.api.ApprovalToken
import dev.tracebox.api.DeleteReport
import dev.tracebox.api.DeleteRequest
import dev.tracebox.api.DiagnosticPackage
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.PackagePreparationResult
import dev.tracebox.api.PackageRequest
import dev.tracebox.api.PackageResult
import dev.tracebox.api.PolicyUpdateResult
import dev.tracebox.api.TraceboxHandle
import dev.tracebox.api.TraceboxPolicy
import dev.tracebox.api.argument
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the published runtime and its approval UI without saving or sharing externally. */
@RunWith(AndroidJUnit4::class)
class AiDiagnosticsSupportPackageInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val activePolicy = TraceboxPolicy.standard().copy(captures = emptySet())

    @Before
    fun requireDisposableInstallation() {
        assumeTrue("support package deletion tests require an isolated app ID", context.packageName.endsWith(".privacycheck"))
    }

    @Test
    fun approvedSupportPackageRetainsTypedFailureAndCorrelationWithoutPrivateText() {
        installCleanRuntime().use { handle ->
            val recorder = TraceboxLlmDiagnosticsRecorder(handle.log)
            LlmPassDiagnostic.FailureReason.entries.forEach { reason ->
                recorder.record(
                    diagnostic(reason = reason).copy(
                        readinessCode = if (reason == LlmPassDiagnostic.FailureReason.MODEL_FAILED) {
                            LlmPassDiagnostic.ReadinessCode.LOW_MEMORY
                        } else {
                            null
                        },
                    ),
                )
            }
            recordLifecycle(handle)
            // Confirms the installed runtime also redacts ordinary String arguments.
            handle.log.warn(LogTemplate.of("Private diagnostic fixture={}"), argument(PRIVATE_MARKER))

            approvedPackage(handle).use { diagnosticPackage ->
                val entries = exportedEntries(diagnosticPackage)
                val records = entries.filterKeys { it.startsWith("records/") }.values
                assertTrue(entries.containsKey("manifest.cbor"))
                LlmPassDiagnostic.FailureReason.entries.forEach { reason ->
                    val code = if (reason == LlmPassDiagnostic.FailureReason.INFERENCE_FAILED) "3006" else "null"
                    assertTrue(
                        "the finalized package must preserve typed diagnosis $reason",
                        records.any { "error_code=$code reason=$reason session=101 generation=202 work=303 photos=2 mode=RESCAN" in it },
                    )
                }
                assertTrue(records.any { "reason=MODEL_FAILED" in it && "ready=LOW_MEMORY" in it })
                assertTrue(records.any { "Scan event=COMPLETED" in it && "failure=INFERENCE_FAILED" in it })
                assertFalse(entries.values.any { PRIVATE_MARKER in it })
                assertFalse(entries.values.any { "legacy-output-marker" in it || "legacy-error-marker" in it })
            }
        }
    }

    @Test
    fun disableAndDeleteFenceBothSinksAndRetireApprovedSupportBytes() {
        installCleanRuntime().use { handle ->
            val recorder = TraceboxLlmDiagnosticsRecorder(handle.log)
            recorder.record(diagnostic())
            recordLifecycle(handle)
            approvedPackage(handle).use { previousPackage ->
                assertTrue(exportedEntries(previousPackage).values.any { "AI pass=TEXT" in it })

                val release = CountDownLatch(1)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val pending = executor.submit {
                        check(release.await(5, TimeUnit.SECONDS))
                        recorder.record(diagnostic())
                        recordLifecycle(handle)
                    }
                    assertEquals(PolicyUpdateResult.SUCCESS, handle.updatePolicy(TraceboxPolicy.disabled()))
                    val beforeCompletion = handle.summary.value.recordedValueCount
                    release.countDown()
                    pending.get(5, TimeUnit.SECONDS)
                    assertEquals(beforeCompletion, handle.summary.value.recordedValueCount)
                    assertNull(previousPackage.useInputStream { it.readBytes() })
                    assertEquals(PackagePreparationResult.NotReady, handle.packages.prepare(PackageRequest.STANDARD))
                } finally {
                    release.countDown()
                    executor.shutdownNow()
                }

                val controls = LegacyCleanupTraceboxHandle(handle) { LegacyLlmDiagnosticsStore.clear(context) }
                assertEquals(DeleteReport.COMPLETE, controls.delete(DeleteRequest.ALL_TRACEBOX_DATA))
                recorder.record(diagnostic())
                recordLifecycle(handle)
                assertFalse(handle.policy.value.enabled)
                assertEquals(PolicyUpdateResult.SUCCESS, handle.updatePolicy(activePolicy))
                approvedPackage(handle).use { afterDelete ->
                    val records = exportedEntries(afterDelete).values
                    assertFalse(records.any { "AI pass=TEXT" in it || "Scan event=COMPLETED" in it })
                }
            }
        }
    }

    @Test
    fun productionQueueAndWorkerEventsReachApprovedSupportPackageWithNumericCorrelation() {
        val sessionId = UUID.randomUUID().toString()
        val generationId = UUID.randomUUID().toString()
        val reviewContext = BagReviewContext.addNew()
        val manager = WorkManager.getInstance(context)
        var workId: String? = null
        installCleanRuntime().use { handle ->
            try {
                BagDraftStore.ensure(context, sessionId, emptyList(), reviewContext, RecognitionPreference.DISABLED)
                BagDraftStore.beginGeneration(context, sessionId, generationId)
                BagExtractionScheduler.rememberLatestGeneration(context, sessionId, generationId)
                BagDraftStore.applyUserEdit(context, sessionId, BagDraftField.NAME, PRIVATE_MARKER)
                val queuedId = runBlocking {
                    BagExtractionScheduler.enqueue(
                        context = context,
                        photoUrisCsv = "",
                        knownValuesJson = "{}",
                        runLlm = false,
                        sessionId = sessionId,
                        generationId = generationId,
                        reviewContext = reviewContext,
                    )
                }
                workId = queuedId
                val completed = awaitFinishedWork(manager, UUID.fromString(queuedId))
                assertEquals("the real empty-input worker must finish successfully", WorkInfo.State.SUCCEEDED, completed.state)
                val correlation = "session=${scanCorrelationKey(sessionId)} gen=${scanCorrelationKey(generationId)} work=${scanCorrelationKey(queuedId)}"

                approvedPackage(handle).use { diagnosticPackage ->
                    val entries = exportedEntries(diagnosticPackage)
                    val records = entries.filterKeys { it.startsWith("records/") }.values
                    listOf(ScanLifecycleEvent.QUEUED, ScanLifecycleEvent.STARTED, ScanLifecycleEvent.COMPLETED).forEach { event ->
                        assertTrue(
                            "production $event must carry this scan's numeric correlation",
                            records.any { "Scan event=$event $correlation" in it && "photos=0" in it },
                        )
                    }
                    assertTrue(records.any { "Scan event=COMPLETED $correlation" in it && "outcome=NO_RESULT" in it })
                    listOf(sessionId, generationId, queuedId, PRIVATE_MARKER).forEach { privateValue ->
                        assertFalse("private scan data must not enter the support package", entries.values.any { privateValue in it })
                    }
                }
            } finally {
                BagDraftStore.markPhase(context, sessionId, BagDraftPhase.DISCARDED)
                workId?.let { ownedWorkId ->
                    manager.cancelWorkById(UUID.fromString(ownedWorkId)).result.get(5, TimeUnit.SECONDS)
                    manager.cancelUniqueWork("bag_analysis_notification_$ownedWorkId").result.get(5, TimeUnit.SECONDS)
                    BagExtractionScheduler.cancel(context, ownedWorkId)
                }
                BagExtractionScheduler.cancelSession(context, sessionId)
                val draft = File(context.noBackupFilesDir, "bag_scan_drafts/draft_$sessionId.json")
                assertTrue("remove only this fixture's unique draft", !draft.exists() || draft.delete())
            }
        }
    }

    private fun awaitFinishedWork(manager: WorkManager, workId: UUID): WorkInfo {
        val expires = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < expires) {
            val remaining = (expires - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            val info = manager.getWorkInfoById(workId).get(remaining, TimeUnit.MILLISECONDS)
            if (info?.state?.isFinished == true) return info
            Thread.sleep(minOf(50L, remaining))
        }
        error("the real queued scan must finish within 30 seconds")
    }

    private fun installCleanRuntime(): TraceboxHandle {
        Tracebox.current()?.close()
        val handle = Tracebox.install(
            context,
            TraceboxConfiguration.Builder()
                .setInitialPolicy(activePolicy)
                .setPersistRequestedProfile(false)
                .setNativeCaptureEnabled(false)
                .build(),
        )
        assertEquals(DeleteReport.COMPLETE, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertEquals(PolicyUpdateResult.SUCCESS, handle.updatePolicy(activePolicy))
        return handle
    }

    private fun approvedPackage(handle: TraceboxHandle): DiagnosticPackage {
        // Preparing the snapshot drains the same runtime queue used by both recorders.
        val prepared = handle.packages.prepare(PackageRequest.STANDARD)
        assertTrue("the pinned runtime must prepare a support package", prepared is PackagePreparationResult.Ready)
        val preview = (prepared as PackagePreparationResult.Ready).preview
        val intent = requireNotNull(handle.packages.approvalIntent(context, preview))
        val approval = ActivityScenario.launchActivityForResult<TraceboxPackageDisclosureActivity>(intent).use { scenario ->
            onView(withText(context.getString(dev.tracebox.R.string.tracebox_disclosure_approve))).perform(click())
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            requireNotNull(ApprovalToken.fromActivityResult(scenario.result.resultData))
        }
        val created = handle.packages.create(PackageRequest.STANDARD, approval)
        assertTrue("approval must create the exact reviewed package", created is PackageResult.Created)
        return (created as PackageResult.Created).diagnosticPackage
    }

    private fun exportedEntries(diagnosticPackage: DiagnosticPackage): Map<String, String> =
        requireNotNull(diagnosticPackage.useInputStream { input ->
            ZipInputStream(input).use { zip ->
                buildMap {
                    var entry = zip.nextEntry
                    while (entry != null) {
                        put(entry.name, zip.readBytes().toString(Charsets.ISO_8859_1))
                        entry = zip.nextEntry
                    }
                }
            }
        })

    private fun diagnostic(
        reason: LlmPassDiagnostic.FailureReason = LlmPassDiagnostic.FailureReason.INFERENCE_FAILED,
    ) = LlmPassDiagnostic(
        timestampMs = System.currentTimeMillis(),
        pass = LlmPassDiagnostic.Pass.TEXT,
        status = LlmPassDiagnostic.Status.ERROR,
        elapsedMs = 15L,
        maxTokens = 4096,
        promptCharLen = 30,
        outputCharLen = 20,
        errorCode = if (reason == LlmPassDiagnostic.FailureReason.INFERENCE_FAILED) 3006 else null,
        failureReason = reason,
        sessionKey = 101L,
        generationKey = 202L,
        workKey = 303L,
        photoCount = 2,
        mode = ScanDiagnosticMode.RESCAN,
    )

    private fun recordLifecycle(handle: TraceboxHandle) {
        TraceboxScanDiagnosticsRecorder(handle.log).record(
            ScanLifecycleDiagnostic(
                context = ScanDiagnosticContext(101L, 202L, 303L, 2, ScanDiagnosticMode.RESCAN),
                event = ScanLifecycleEvent.COMPLETED,
                fieldsResolved = 2,
                outcome = ScanDiagnosticOutcome.ERROR,
                failure = ScanDiagnosticFailure.INFERENCE_FAILED,
            ),
        )
    }

    private companion object {
        const val PRIVATE_MARKER = "PRIVATE_AI_PROMPT_OUTPUT_EXCEPTION_81f0"
    }
}
