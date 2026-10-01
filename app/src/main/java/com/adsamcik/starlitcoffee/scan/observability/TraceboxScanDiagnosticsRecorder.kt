package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticOutcome
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleDiagnostic
import dev.tracebox.Tracebox
import dev.tracebox.api.LogLevel
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.TraceboxLogger
import dev.tracebox.api.argument

/** Uses normal INFO capture and Tracebox's policy, deletion, privacy, and package export. */
class TraceboxScanDiagnosticsRecorder(
    private val logger: TraceboxLogger = Tracebox.log,
) {
    fun record(diagnostic: ScanLifecycleDiagnostic) {
        val identity = diagnostic.context
        val level = if (diagnostic.outcome == ScanDiagnosticOutcome.ERROR) LogLevel.WARN else LogLevel.INFO
        logger.log(
            level,
            LogTemplate.of(
                "Scan event={} session={} gen={} work={} mode={} photos={} ms={} queue={} fields={} at={} seen={} plan={} try={} ai={} outcome={} failure={}",
            ),
            argument(diagnostic.event),
            argument(identity.sessionKey),
            argument(identity.generationKey),
            argument(identity.workKey),
            argument(identity.mode),
            argument(identity.photoCount.coerceAtLeast(0)),
            argument(diagnostic.elapsedMs.coerceAtLeast(0L)),
            argument(diagnostic.queueMs.coerceAtLeast(0L)),
            argument(diagnostic.fieldsResolved.coerceAtLeast(0)),
            argument(diagnostic.stage),
            argument(diagnostic.stagesObserved.coerceAtLeast(0)),
            argument(diagnostic.stagesPlanned.coerceAtLeast(0)),
            argument(diagnostic.runAttempt.coerceAtLeast(0)),
            argument(diagnostic.aiRequested),
            argument(diagnostic.outcome),
            argument(diagnostic.failure),
        )
    }
}
