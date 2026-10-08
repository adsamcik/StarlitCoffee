package com.adsamcik.starlitcoffee.diagnostics

import dev.tracebox.api.DeleteReport
import dev.tracebox.api.DeleteRequest
import dev.tracebox.api.TraceboxHandle

/** Includes retired app diagnostics in the existing delete-all action. */
internal class LegacyCleanupTraceboxHandle(
    private val delegate: TraceboxHandle,
    private val clearLegacyData: () -> Boolean,
) : TraceboxHandle by delegate {
    override fun delete(request: DeleteRequest): DeleteReport {
        if (request != DeleteRequest.ALL_TRACEBOX_DATA) return delegate.delete(request)

        val legacyCleared = try {
            clearLegacyData()
        } catch (_: Exception) {
            false
        }
        val report = delegate.delete(request)
        return if (!legacyCleared && report == DeleteReport.COMPLETE) {
            DeleteReport.PENDING_FAILURE
        } else {
            report
        }
    }
}
