package com.adsamcik.starlitcoffee.diagnostics

import dev.tracebox.api.CrashReporter
import dev.tracebox.api.DeleteReport
import dev.tracebox.api.DeleteRequest
import dev.tracebox.api.DiagnosticPackages
import dev.tracebox.api.DiagnosticSummary
import dev.tracebox.api.Diagnostics
import dev.tracebox.api.DiagnosticsProfile
import dev.tracebox.api.PolicyUpdateResult
import dev.tracebox.api.Readiness
import dev.tracebox.api.TraceboxHandle
import dev.tracebox.api.TraceboxHealth
import dev.tracebox.api.TraceboxLogger
import dev.tracebox.api.TraceboxPolicy
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyCleanupTraceboxHandleTest {
    @Test
    fun `delete all completes only after both stores are cleared`() {
        val delegate = RecordingHandle(DeleteReport.COMPLETE)
        var cleanupCalls = 0
        val handle = LegacyCleanupTraceboxHandle(delegate) {
            cleanupCalls += 1
            true
        }

        assertEquals(DeleteReport.COMPLETE, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertEquals(1, cleanupCalls)
        assertEquals(listOf(DeleteRequest.ALL_TRACEBOX_DATA), delegate.requests)
    }

    @Test
    fun `legacy cleanup failure still deletes Tracebox and prevents completion`() {
        val delegate = RecordingHandle(DeleteReport.COMPLETE)
        val handle = LegacyCleanupTraceboxHandle(delegate) { false }

        assertEquals(DeleteReport.PENDING_FAILURE, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertEquals(listOf(DeleteRequest.ALL_TRACEBOX_DATA), delegate.requests)
    }

    @Test
    fun `legacy cleanup exception still deletes Tracebox and prevents completion`() {
        val delegate = RecordingHandle(DeleteReport.COMPLETE)
        val handle = LegacyCleanupTraceboxHandle(delegate) {
            throw SecurityException("Legacy preferences could not be deleted")
        }

        assertEquals(DeleteReport.PENDING_FAILURE, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertEquals(listOf(DeleteRequest.ALL_TRACEBOX_DATA), delegate.requests)
    }

    @Test
    fun `Tracebox pending failure and rejection remain unchanged`() {
        listOf(DeleteReport.PENDING_FAILURE, DeleteReport.REJECTED).forEach { report ->
            listOf(true, false).forEach { legacyCleared ->
                val delegate = RecordingHandle(report)
                var cleanupCalls = 0
                val handle = LegacyCleanupTraceboxHandle(delegate) {
                    cleanupCalls += 1
                    legacyCleared
                }

                assertEquals(report, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
                assertEquals(1, cleanupCalls)
                assertEquals(listOf(DeleteRequest.ALL_TRACEBOX_DATA), delegate.requests)
            }
        }
    }

    @Test
    fun `expired snapshots request does not purge legacy preferences`() {
        val delegate = RecordingHandle(DeleteReport.COMPLETE)
        val handle = LegacyCleanupTraceboxHandle(delegate) {
            error("Expired snapshots must not purge legacy preferences")
        }

        assertEquals(DeleteReport.COMPLETE, handle.delete(DeleteRequest.EXPIRED_SNAPSHOTS))
        assertEquals(listOf(DeleteRequest.EXPIRED_SNAPSHOTS), delegate.requests)
    }

    @Test
    fun `delete all retries a previously failed legacy purge`() {
        val delegate = RecordingHandle(DeleteReport.COMPLETE)
        var cleanupCalls = 0
        val handle = LegacyCleanupTraceboxHandle(delegate) {
            cleanupCalls += 1
            cleanupCalls > 1
        }

        assertEquals(DeleteReport.PENDING_FAILURE, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertEquals(DeleteReport.COMPLETE, handle.delete(DeleteRequest.ALL_TRACEBOX_DATA))
        assertEquals(2, cleanupCalls)
        assertEquals(List(2) { DeleteRequest.ALL_TRACEBOX_DATA }, delegate.requests)
    }

    private class RecordingHandle(private val report: DeleteReport) : TraceboxHandle {
        val requests = mutableListOf<DeleteRequest>()

        override fun delete(request: DeleteRequest): DeleteReport {
            requests += request
            return report
        }

        override val diagnostics: Diagnostics get() = error("Unused in deletion tests")
        override val log: TraceboxLogger get() = error("Unused in deletion tests")
        override val crashes: CrashReporter get() = error("Unused in deletion tests")
        override val policy: StateFlow<TraceboxPolicy> get() = error("Unused in deletion tests")
        override val summary: StateFlow<DiagnosticSummary> get() = error("Unused in deletion tests")
        override val readiness: StateFlow<Readiness> get() = error("Unused in deletion tests")
        override val health: StateFlow<TraceboxHealth> get() = error("Unused in deletion tests")
        override val packages: DiagnosticPackages get() = error("Unused in deletion tests")
        override fun updateProfile(profile: DiagnosticsProfile): PolicyUpdateResult =
            error("Unused in deletion tests")
        override fun updatePolicy(policy: TraceboxPolicy): PolicyUpdateResult =
            error("Unused in deletion tests")
        override fun close() = Unit
    }
}
