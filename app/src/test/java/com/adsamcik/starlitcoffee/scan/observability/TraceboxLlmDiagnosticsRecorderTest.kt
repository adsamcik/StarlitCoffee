package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticMode
import dev.tracebox.api.LogArgument
import dev.tracebox.api.LogCategory
import dev.tracebox.api.LogLevel
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.PerformanceMeasurement
import dev.tracebox.api.Privacy
import dev.tracebox.api.PrivacyConfiguration
import dev.tracebox.api.TraceboxLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceboxLlmDiagnosticsRecorderTest {

    @Test
    fun `success records useful public enum and count facts through Tracebox`() {
        val logger = CapturingLogger()
        val recorder = TraceboxLlmDiagnosticsRecorder(logger)

        recorder.record(diagnostic())

        val event = logger.events.single()
        assertEquals(LogLevel.INFO, event.level)
        assertEquals(
            "AI pass={} status={} ms={} tokens={} prompt={} out={} error_code={} reason={} session={} generation={} work={} photos={} mode={} ready={}",
            event.template,
        )
        assertEquals(
            listOf("TEXT", "SUCCESS", "1500", "4096", "1206", "27") + List(8) { "null" },
            event.arguments.map { it.text },
        )
        assertTrue(event.arguments.all { it.privacy == Privacy.PUBLIC && !it.transformed })
    }

    @Test
    fun `every unsuccessful outcome uses warning logs without a throwable`() {
        val logger = CapturingLogger()
        val recorder = TraceboxLlmDiagnosticsRecorder(logger)
        val statuses = listOf(
            LlmPassDiagnostic.Status.TIMEOUT,
            LlmPassDiagnostic.Status.ERROR,
            LlmPassDiagnostic.Status.UNAVAILABLE,
        )

        statuses.forEach { status ->
            recorder.record(diagnostic(status = status, errorCode = if (status == LlmPassDiagnostic.Status.ERROR) 3006 else null))
        }

        assertEquals(statuses.size, logger.events.size)
        logger.events.zip(statuses).forEach { (event, status) ->
            assertEquals(LogLevel.WARN, event.level)
            assertEquals(status.name, event.arguments[1].text)
            assertEquals(if (status == LlmPassDiagnostic.Status.ERROR) "3006" else "null", event.arguments[6].text)
            assertEquals(14, event.arguments.size)
            assertTrue(event.arguments.all { it.privacy == Privacy.PUBLIC && !it.transformed })
        }
    }

    @Test
    fun `all pass enums remain observable under default privacy rules`() {
        val logger = CapturingLogger()
        val recorder = TraceboxLlmDiagnosticsRecorder(logger)

        LlmPassDiagnostic.Pass.entries.forEach { pass -> recorder.record(diagnostic(pass = pass)) }

        assertEquals(LlmPassDiagnostic.Pass.entries.map { it.name }, logger.events.map { it.arguments.first().text })
        assertTrue(logger.events.all { it.arguments.first().privacy == Privacy.PUBLIC })
    }

    @Test
    fun `valid empty responses are informative rather than warning failures`() {
        val logger = CapturingLogger()

        TraceboxLlmDiagnosticsRecorder(logger).record(diagnostic(status = LlmPassDiagnostic.Status.NO_RESULT))

        assertEquals(LogLevel.INFO, logger.events.single().level)
        assertEquals("NO_RESULT", logger.events.single().arguments[1].text)
    }

    @Test
    fun `all typed failure reasons and numeric scan keys survive default privacy`() {
        val logger = CapturingLogger()
        val recorder = TraceboxLlmDiagnosticsRecorder(logger)
        LlmPassDiagnostic.FailureReason.entries.forEach { reason ->
            recorder.record(
                diagnostic(status = LlmPassDiagnostic.Status.UNAVAILABLE).copy(
                    failureReason = reason,
                    sessionKey = 101L,
                    generationKey = 202L,
                    workKey = 303L,
                    photoCount = 2,
                    mode = ScanDiagnosticMode.entries.first(),
                ),
            )
        }

        assertEquals(LlmPassDiagnostic.FailureReason.entries.map { it.name }, logger.events.map { it.arguments[7].text })
        logger.events.forEach { event ->
            assertEquals(
                listOf("101", "202", "303", "2", ScanDiagnosticMode.entries.first().name, "null"),
                event.arguments.drop(8).map { it.text },
            )
            assertTrue(event.arguments.all { it.privacy == Privacy.PUBLIC && !it.transformed })
        }
    }

    @Test
    fun `known readiness causes remain public enums alongside model failed diagnosis`() {
        val logger = CapturingLogger()
        val recorder = TraceboxLlmDiagnosticsRecorder(logger)
        LlmPassDiagnostic.ReadinessCode.entries.forEach { readinessCode ->
            recorder.record(
                diagnostic(status = LlmPassDiagnostic.Status.UNAVAILABLE).copy(
                    failureReason = LlmPassDiagnostic.FailureReason.MODEL_FAILED,
                    readinessCode = readinessCode,
                ),
            )
        }

        assertEquals(LlmPassDiagnostic.ReadinessCode.entries.map { it.name }, logger.events.map { it.arguments.last().text })
        logger.events.forEach { event ->
            assertEquals("MODEL_FAILED", event.arguments[7].text)
            assertEquals(LogLevel.WARN, event.level)
            assertEquals(14, event.arguments.size)
            assertTrue(event.arguments.all { it.privacy == Privacy.PUBLIC && !it.transformed })
        }
    }

    private fun diagnostic(
        pass: LlmPassDiagnostic.Pass = LlmPassDiagnostic.Pass.TEXT,
        status: LlmPassDiagnostic.Status = LlmPassDiagnostic.Status.SUCCESS,
        errorCode: Int? = null,
    ) = LlmPassDiagnostic(
        timestampMs = 1_700_000_000_001L,
        pass = pass,
        status = status,
        elapsedMs = 1500L,
        maxTokens = 4096,
        promptCharLen = 1206,
        outputCharLen = 27,
        errorCode = errorCode,
    )

    /** Exercises the logger boundary without Context, preferences, or a Tracebox installation. */
    private class CapturingLogger : TraceboxLogger {
        private val privacy = PrivacyConfiguration.defaults()
        val events = mutableListOf<Event>()

        override fun isEnabled(level: LogLevel, category: LogCategory): Boolean = true

        override fun log(level: LogLevel, template: LogTemplate, vararg arguments: LogArgument) {
            events += Event(level, template.value, arguments.map { privacy.render(it) })
        }

        override fun error(throwable: Throwable, template: LogTemplate, vararg arguments: LogArgument) {
            throw AssertionError("AI diagnostics must never log a throwable")
        }

        override fun performanceStart(template: LogTemplate, vararg arguments: LogArgument): PerformanceMeasurement {
            throw AssertionError("AI diagnostics must use the policy-controlled log sink")
        }
    }

    private data class Event(
        val level: LogLevel,
        val template: String,
        val arguments: List<PrivacyConfiguration.Rendered>,
    )
}
