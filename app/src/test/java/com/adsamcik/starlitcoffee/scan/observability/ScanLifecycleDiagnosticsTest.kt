package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticContext
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticFailure
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticMode
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticOutcome
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticStage
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleEvent
import com.adsamcik.starlitcoffee.domain.scandiagnostics.scanCorrelationKey
import dev.tracebox.api.LogArgument
import dev.tracebox.api.LogCategory
import dev.tracebox.api.LogLevel
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.PerformanceMeasurement
import dev.tracebox.api.Privacy
import dev.tracebox.api.PrivacyConfiguration
import dev.tracebox.api.TraceboxLogger
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLifecycleDiagnosticsTest {

    @Test
    fun `canonical UUID correlation is stable and case insensitive`() {
        val identifier = "b9ef87a5-4285-4cbd-a12d-987be7026d39"
        val uuid = UUID.fromString(identifier)
        val expected = uuid.mostSignificantBits xor uuid.leastSignificantBits

        assertEquals(expected, scanCorrelationKey(identifier))
        assertEquals(expected, scanCorrelationKey(identifier.uppercase()))
        assertEquals(scanCorrelationKey(identifier), scanCorrelationKey(identifier))
    }

    @Test
    fun `distinct generations have distinct numeric keys under one session`() {
        val session = "b9ef87a5-4285-4cbd-a12d-987be7026d39"
        val first = context(session, "fa2367d0-a789-4b26-9578-35e5fbdbd610")
        val second = context(session, "fa2367d0-a789-4b26-9578-35e5fbdbd611")

        assertEquals(first.sessionKey, second.sessionKey)
        assertNotEquals(first.generationKey, second.generationKey)
        assertEquals(first.generationKey, context(session, "fa2367d0-a789-4b26-9578-35e5fbdbd610").generationKey)
    }

    @Test
    fun `private malformed and abbreviated identifiers are dropped rather than hashed`() {
        val invalid = listOf(
            null,
            "",
            "default-bag-photo-session",
            "in-memory",
            "content://private.photos/coffee-label",
            "private-roaster-name@example.test",
            "1-1-1-1-1", // UUID.fromString accepts abbreviated groups.
            "00000001-0001-0001-0001-00000000001",
            "b9ef87a5-4285-4cbd-a12d-987be7026d3z",
            "b9ef87a5_4285_4cbd_a12d_987be7026d39",
            " b9ef87a5-4285-4cbd-a12d-987be7026d39",
            "b9ef87a5-4285-4cbd-a12d-987be7026d39\n",
            "b9ef87a5-4285-4cbd-a12d-987be7026d39; private-label",
            "00000000-0000-0000-0000-00000000000+",
        )

        invalid.forEach { identifier -> assertNull("Identifier must be dropped: $identifier", scanCorrelationKey(identifier)) }
    }

    @Test
    fun `overlapping coroutines retain their own correlation after suspension and restore parent`() = runTest {
        val parent = ScanDiagnosticContext(sessionKey = 10L, generationKey = 11L)
        val children = listOf(
            ScanDiagnosticContext(sessionKey = 20L, generationKey = 21L, workKey = 22L),
            ScanDiagnosticContext(sessionKey = 30L, generationKey = 31L, workKey = 32L),
        )
        val bothStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var started = 0

        withContext(parent) {
            val tasks = children.map { expected ->
                async(expected) {
                    assertSame(expected, currentCoroutineContext()[ScanDiagnosticContext])
                    started++
                    if (started == children.size) bothStarted.complete(Unit)
                    release.await()
                    yield()
                    assertSame(expected, currentCoroutineContext()[ScanDiagnosticContext])
                    currentCoroutineContext()[ScanDiagnosticContext]
                }
            }
            bothStarted.await()
            assertSame(parent, currentCoroutineContext()[ScanDiagnosticContext])
            release.complete(Unit)
            assertEquals(children, tasks.awaitAll())
            assertSame(parent, currentCoroutineContext()[ScanDiagnosticContext])
        }

        assertNull(currentCoroutineContext()[ScanDiagnosticContext])
    }

    @Test
    fun `lifecycle records useful typed public facts at INFO without raw payload or throwable`() {
        val logger = CapturingLogger()
        val identity = ScanDiagnosticContext(101L, 202L, 303L, 2, ScanDiagnosticMode.RESCAN)

        TraceboxScanDiagnosticsRecorder(logger).record(
            ScanLifecycleDiagnostic(
                context = identity,
                event = ScanLifecycleEvent.COMPLETED,
                elapsedMs = 1500L,
                queueMs = 125L,
                fieldsResolved = 4,
                stage = ScanDiagnosticStage.LLM_EXTRACT,
                stagesObserved = 3,
                stagesPlanned = 7,
                runAttempt = 1,
                aiRequested = true,
                outcome = ScanDiagnosticOutcome.PARTIAL,
                failure = ScanDiagnosticFailure.MODEL_SETUP_REQUIRED,
            ),
        )

        val event = logger.events.single()
        assertEquals(LogLevel.INFO, event.level)
        assertEquals(
            "Scan event={} session={} gen={} work={} mode={} photos={} ms={} queue={} fields={} at={} seen={} plan={} try={} ai={} outcome={} failure={}",
            event.template,
        )
        assertEquals(
            listOf("COMPLETED", "101", "202", "303", "RESCAN", "2", "1500", "125", "4", "LLM_EXTRACT", "3", "7", "1", "true", "PARTIAL", "MODEL_SETUP_REQUIRED"),
            event.arguments.map { it.text },
        )
        assertPublic(event)
    }

    @Test
    fun `absent and rejected correlation stays null and public under default privacy`() {
        val logger = CapturingLogger()

        TraceboxScanDiagnosticsRecorder(logger).record(
            ScanLifecycleDiagnostic(
                context = ScanDiagnosticContext(
                    sessionKey = scanCorrelationKey("private-roaster"),
                    generationKey = scanCorrelationKey("content://private.photos/label"),
                    workKey = scanCorrelationKey("in-memory"),
                ),
                event = ScanLifecycleEvent.QUEUED,
            ),
        )

        val event = logger.events.single()
        assertEquals(LogLevel.INFO, event.level)
        assertEquals(
            listOf("QUEUED", "null", "null", "null", "UNKNOWN", "0", "0", "0", "0", "null", "0", "0", "0", "null", "null", "null"),
            event.arguments.map { it.text },
        )
        assertPublic(event)
    }

    @Test
    fun `all nonerror outcomes remain at normal capture level and errors warn`() {
        val logger = CapturingLogger()
        val recorder = TraceboxScanDiagnosticsRecorder(logger)
        val outcomes = listOf(null) + ScanDiagnosticOutcome.entries

        outcomes.forEach { outcome -> recorder.record(diagnostic().copy(outcome = outcome)) }

        logger.events.zip(outcomes).forEach { (event, outcome) ->
            assertEquals(if (outcome == ScanDiagnosticOutcome.ERROR) LogLevel.WARN else LogLevel.INFO, event.level)
            assertEquals(outcome?.name ?: "null", event.arguments[14].text)
            assertPublic(event)
        }
    }

    @Test
    fun `every lifecycle event stage mode and failure remains observable under default privacy`() {
        val logger = CapturingLogger()
        val recorder = TraceboxScanDiagnosticsRecorder(logger)
        ScanLifecycleEvent.entries.forEach { recorder.record(diagnostic().copy(event = it)) }
        assertEquals(ScanLifecycleEvent.entries.map { it.name }, logger.events.map { it.arguments[0].text })
        logger.events.clear()
        ScanDiagnosticStage.entries.forEach { recorder.record(diagnostic().copy(stage = it)) }
        assertEquals(ScanDiagnosticStage.entries.map { it.name }, logger.events.map { it.arguments[9].text })
        logger.events.clear()
        ScanDiagnosticMode.entries.forEach {
            recorder.record(diagnostic().copy(context = ScanDiagnosticContext(mode = it)))
        }
        assertEquals(ScanDiagnosticMode.entries.map { it.name }, logger.events.map { it.arguments[4].text })
        logger.events.clear()
        ScanDiagnosticFailure.entries.forEach { recorder.record(diagnostic().copy(failure = it)) }
        assertEquals(ScanDiagnosticFailure.entries.map { it.name }, logger.events.map { it.arguments[15].text })
        logger.events.forEach(::assertPublic)
    }

    @Test
    fun `negative metrics clamp to zero while signed correlation keys remain intact`() {
        val logger = CapturingLogger()

        TraceboxScanDiagnosticsRecorder(logger).record(
            diagnostic().copy(
                context = ScanDiagnosticContext(-101L, Long.MIN_VALUE, -303L, -2),
                elapsedMs = Long.MIN_VALUE,
                queueMs = -125L,
                fieldsResolved = -4,
                stagesObserved = -3,
                stagesPlanned = Int.MIN_VALUE,
                runAttempt = -1,
            ),
        )

        val event = logger.events.single()
        assertEquals(listOf("-101", Long.MIN_VALUE.toString(), "-303"), event.arguments.subList(1, 4).map { it.text })
        listOf(5, 6, 7, 8, 10, 11, 12).forEach { index -> assertEquals("0", event.arguments[index].text) }
        assertPublic(event)
    }

    private fun context(sessionId: String, generationId: String) = ScanDiagnosticContext(
        sessionKey = scanCorrelationKey(sessionId),
        generationKey = scanCorrelationKey(generationId),
    )

    private fun diagnostic() = ScanLifecycleDiagnostic(
        context = ScanDiagnosticContext(),
        event = ScanLifecycleEvent.COMPLETED,
    )

    private fun assertPublic(event: Event) {
        assertEquals(16, event.arguments.size)
        assertTrue(event.arguments.all { it.privacy == Privacy.PUBLIC && !it.transformed })
    }

    private class CapturingLogger : TraceboxLogger {
        private val privacy = PrivacyConfiguration.defaults()
        val events = mutableListOf<Event>()

        override fun isEnabled(level: LogLevel, category: LogCategory): Boolean = true

        override fun log(level: LogLevel, template: LogTemplate, vararg arguments: LogArgument) {
            events += Event(level, template.value, arguments.map { privacy.render(it) })
        }

        override fun error(throwable: Throwable, template: LogTemplate, vararg arguments: LogArgument) {
            throw AssertionError("Lifecycle diagnostics must never log a throwable")
        }

        override fun performanceStart(template: LogTemplate, vararg arguments: LogArgument): PerformanceMeasurement {
            throw AssertionError("Lifecycle diagnostics must use the policy-controlled log sink")
        }
    }

    private data class Event(
        val level: LogLevel,
        val template: String,
        val arguments: List<PrivacyConfiguration.Rendered>,
    )
}
