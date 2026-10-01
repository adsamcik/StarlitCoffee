package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticContext
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticFailure
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticMode
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticOutcome
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanDiagnosticStage
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleDiagnostic
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleEvent
import com.adsamcik.starlitcoffee.domain.scandiagnostics.scanCorrelationKey
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BagScanTelemetryPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `context publishes only UUID correlation photo count and contextual mode`() {
        val identity = bagScanDiagnosticContext(
            sessionId = SESSION_ID,
            generationId = GENERATION_ID,
            workId = WORK_ID,
            photoUrisCsv = " content://private/front, ,content://private/back, ",
            reviewContext = BagReviewContext.rescan(targetBagId = 321L),
        )

        assertEquals(scanCorrelationKey(SESSION_ID), identity.sessionKey)
        assertEquals(scanCorrelationKey(GENERATION_ID), identity.generationKey)
        assertEquals(scanCorrelationKey(WORK_ID), identity.workKey)
        assertEquals(2, identity.photoCount)
        assertEquals(ScanDiagnosticMode.RESCAN, identity.mode)
        assertEquals(
            ScanDiagnosticMode.ADD_NEW,
            bagScanDiagnosticContext(null, null, null, null, BagReviewContext.addNew()).mode,
        )
    }

    @Test
    fun `legacy or private IDs are dropped and absent inputs use empty unknown context`() {
        val identity = bagScanDiagnosticContext(
            "private-label", "1-1-1-1-1", "in-memory", null, null,
        )

        assertNull(identity.sessionKey)
        assertNull(identity.generationKey)
        assertNull(identity.workKey)
        assertEquals(0, identity.photoCount)
        assertEquals(ScanDiagnosticMode.UNKNOWN, identity.mode)
    }

    @Test
    fun `resolved count excludes blank invalid and unknown model fields`() {
        val result = result(
            "name" to "Private coffee",
            "roaster" to " ",
            "isDecaf" to "possibly",
            "privateUnknownModelField" to "Private model output",
        )

        assertEquals(1, recognizedFieldCount(result))
        assertEquals(0, recognizedFieldCount(BagPhotoProcessingResult()))
    }

    @Test
    fun `successful worker result distinguishes complete partial and empty recognition`() {
        LlmEnrichmentStatus.entries.forEach { status ->
            val expected = when (status) {
                LlmEnrichmentStatus.NOT_RUN, LlmEnrichmentStatus.SUCCEEDED -> ScanDiagnosticOutcome.COMPLETE
                else -> ScanDiagnosticOutcome.PARTIAL
            }
            assertEquals(status.name, expected, bagScanDiagnosticOutcome(result("name" to "Private coffee", status = status), true))
            assertEquals(
                status.name,
                ScanDiagnosticOutcome.NO_RESULT,
                bagScanDiagnosticOutcome(BagPhotoProcessingResult(llmStatus = status), true),
            )
        }
    }

    @Test
    fun `worker failure dominates available fields and AI status`() {
        LlmEnrichmentStatus.entries.forEach { status ->
            val result = result("name" to "Private coffee", status = status)
            assertEquals(ScanDiagnosticOutcome.ERROR, bagScanDiagnosticOutcome(result, false))
            assertEquals(ScanDiagnosticFailure.WORKER_FAILED, bagScanDiagnosticFailure(result, false))
        }
    }

    @Test
    fun `AI failures preserve distinct actionable typed reasons`() {
        val expected = mapOf(
            LlmEnrichmentStatus.NOT_RUN to null,
            LlmEnrichmentStatus.SUCCEEDED to null,
            LlmEnrichmentStatus.FAILED to ScanDiagnosticFailure.INFERENCE_FAILED,
            LlmEnrichmentStatus.UNAVAILABLE to ScanDiagnosticFailure.RECOGNITION_UNAVAILABLE,
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED to ScanDiagnosticFailure.AUTHORIZATION_REQUIRED,
            LlmEnrichmentStatus.SETUP_REQUIRED to ScanDiagnosticFailure.MODEL_SETUP_REQUIRED,
            LlmEnrichmentStatus.TIMED_OUT to ScanDiagnosticFailure.TIMED_OUT,
        )

        assertEquals(LlmEnrichmentStatus.entries.toSet(), expected.keys)
        expected.forEach { (status, failure) ->
            assertEquals(status.name, failure, bagScanDiagnosticFailure(BagPhotoProcessingResult(llmStatus = status), true))
        }
    }

    @Test
    fun `run records bounded timing identity attempt and requested AI facts`() {
        var now = 1000L
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val identity = ScanDiagnosticContext(1L, 2L, 3L, 2, ScanDiagnosticMode.ADD_NEW)
        val telemetry = BagScanRunTelemetry(identity, runAttempt = 2, aiRequested = true, clock = { now }, emit = events::add)

        now = 1125L
        telemetry.start(queueMs = 250L)
        now = 1250L
        telemetry.finish(result("name" to "Private coffee", status = LlmEnrichmentStatus.SUCCEEDED), successful = true)

        val start = events.first()
        assertEquals(ScanLifecycleEvent.STARTED, start.event)
        assertEquals(125L, start.elapsedMs)
        assertEquals(250L, start.queueMs)
        assertEquals(2, start.runAttempt)
        assertEquals(true, start.aiRequested)
        assertSame(identity, start.context)
        val finish = events.last()
        assertEquals(ScanLifecycleEvent.COMPLETED, finish.event)
        assertEquals(250L, finish.elapsedMs)
        assertEquals(1, finish.fieldsResolved)
        assertEquals(ScanDiagnosticOutcome.COMPLETE, finish.outcome)
        assertNull(finish.failure)
        assertSame(identity, finish.context)
    }

    @Test
    fun `repeated progress emits each stage once and preserves largest planned count`() {
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val telemetry = telemetry(events)

        telemetry.progress(ScanProgress(ScanStage.OCR, stepIndex = 1, stepCount = 4))
        telemetry.progress(ScanProgress(ScanStage.OCR, stepIndex = 1, stepCount = 7))
        telemetry.progress(ScanProgress(ScanStage.BARCODE_LOOKUP, stepIndex = 2, stepCount = 3))
        telemetry.progress(ScanProgress(ScanStage.BARCODE_LOOKUP, stepIndex = 2, stepCount = 3))

        assertEquals(listOf(ScanLifecycleEvent.STAGE_STARTED, ScanLifecycleEvent.STAGE_STARTED), events.map { it.event })
        assertEquals(listOf(ScanDiagnosticStage.OCR, ScanDiagnosticStage.BARCODE_LOOKUP), events.map { it.stage })
        assertEquals(listOf(1, 2), events.map { it.stagesObserved })
        assertEquals(listOf(4, 7), events.map { it.stagesPlanned })
    }

    @Test
    fun `all pipeline stages map to typed lifecycle stages`() {
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val telemetry = telemetry(events)

        ScanStage.entries.forEachIndexed { index, stage ->
            telemetry.progress(ScanProgress(stage, stepIndex = index + 1, stepCount = ScanStage.entries.size))
        }

        assertEquals(ScanStage.entries.map { it.name }, events.map { it.stage?.name })
        assertEquals((1..ScanStage.entries.size).toList(), events.map { it.stagesObserved })
        assertTrue(events.all { it.stagesPlanned == ScanStage.entries.size })
    }

    @Test
    fun `partial snapshots emit only when sanitized field count changes`() {
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val telemetry = telemetry(events)

        telemetry.partial(BagPhotoProcessingResult())
        telemetry.partial(result("name" to "First private name"))
        telemetry.partial(result("name" to "Corrected private name"))
        telemetry.partial(result("name" to "Corrected private name", "roaster" to "Private roaster"))
        telemetry.partial(result("name" to "Corrected private name", "isDecaf" to "possibly"))

        assertEquals(List(3) { ScanLifecycleEvent.PARTIAL_RESULTS }, events.map { it.event })
        assertEquals(listOf(1, 2, 1), events.map { it.fieldsResolved })
    }

    @Test
    fun `terminal completion emits once and rejects late progress partials cancellation and replays`() {
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val telemetry = telemetry(events)
        val result = result("name" to "Private name", status = LlmEnrichmentStatus.UNAVAILABLE)

        telemetry.finish(result, successful = true)
        telemetry.finish(result, successful = true)
        telemetry.finish(result, successful = true, replayed = true)
        telemetry.progress(ScanProgress(ScanStage.FINALIZING, 7, 7))
        telemetry.partial(result("name" to "Private name", "roaster" to "Private roaster"))
        telemetry.cancel()

        val terminal = events.single()
        assertEquals(ScanLifecycleEvent.COMPLETED, terminal.event)
        assertEquals(ScanDiagnosticOutcome.PARTIAL, terminal.outcome)
        assertEquals(ScanDiagnosticFailure.RECOGNITION_UNAVAILABLE, terminal.failure)
        assertEquals(1, terminal.fieldsResolved)
        assertEquals(0, terminal.stagesObserved)
    }

    @Test
    fun `a persisted result replay is distinct from its original completion`() {
        val original = mutableListOf<ScanLifecycleDiagnostic>()
        val replay = mutableListOf<ScanLifecycleDiagnostic>()
        val result = result("name" to "Private name", status = LlmEnrichmentStatus.SUCCEEDED)

        telemetry(original).finish(result, successful = true)
        telemetry(replay).finish(result, successful = true, replayed = true)

        assertEquals(ScanLifecycleEvent.COMPLETED, original.single().event)
        assertEquals(ScanLifecycleEvent.RESULT_REPLAYED, replay.single().event)
        assertEquals(original.single().outcome, replay.single().outcome)
        assertEquals(original.single().fieldsResolved, replay.single().fieldsResolved)
    }

    @Test
    fun `cancellation terminates an attempt without claiming user discard`() {
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val telemetry = telemetry(events)

        telemetry.cancel()
        telemetry.cancel()
        telemetry.finish(result("name" to "Late private name"), successful = true)
        telemetry.partial(result("name" to "Late private name"))
        telemetry.progress(ScanProgress(ScanStage.OCR, 1, 7))

        val event = events.single()
        assertEquals(ScanLifecycleEvent.CANCELLED, event.event)
        assertEquals(ScanDiagnosticOutcome.CANCELLED, event.outcome)
        assertNull(event.failure)
        assertFalse(events.any { it.event == ScanLifecycleEvent.DISCARDED })
    }

    @Test
    fun `backwards clock negative queue and invalid stage plan cannot emit negative timing or counts`() {
        var now = 100L
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        val telemetry = BagScanRunTelemetry(ScanDiagnosticContext(), clock = { now }, emit = events::add)

        now = 20L
        telemetry.start(queueMs = -10L)
        telemetry.progress(ScanProgress(ScanStage.OCR, stepIndex = -1, stepCount = -7))
        telemetry.finish(BagPhotoProcessingResult(), successful = true)

        assertTrue(events.all { it.elapsedMs == 0L && it.queueMs == 0L })
        assertTrue(events.all { it.fieldsResolved >= 0 && it.stagesObserved >= 0 && it.stagesPlanned >= 0 })
    }

    @Test
    fun `diagnostic sink exceptions cannot fail or reopen extraction lifecycle`() {
        var calls = 0
        val telemetry = BagScanRunTelemetry(
            ScanDiagnosticContext(),
            clock = { 100L },
            emit = { calls++; error("Private diagnostic failure") },
        )

        telemetry.start()
        telemetry.progress(ScanProgress(ScanStage.OCR, 1, 7))
        telemetry.partial(result("name" to "Private name"))
        telemetry.finish(result("name" to "Private name"), successful = true)
        telemetry.cancel()
        recordBagScanLifecycle(ScanLifecycleDiagnostic(ScanDiagnosticContext(), ScanLifecycleEvent.QUEUED)) {
            error("Private enqueue diagnostic failure")
        }

        assertEquals(4, calls)
    }

    @Test
    fun `actual phase changes have one distinct event and repeated updates have none`() {
        val events = mapOf(
            BagDraftPhase.REVIEWING to ScanLifecycleEvent.REVIEW_OPENED,
            BagDraftPhase.BACKGROUND to ScanLifecycleEvent.BACKGROUNDED,
            BagDraftPhase.SAVED to ScanLifecycleEvent.SAVED,
            BagDraftPhase.DISCARDED to ScanLifecycleEvent.DISCARDED,
        )
        val before = draft()

        events.forEach { (phase, event) ->
            val after = before.copy(phase = phase, updatedAtMillis = 1250L)
            assertEquals(event, requireNotNull(bagDraftPhaseDiagnostic(before, after)).event)
            assertNull(bagDraftPhaseDiagnostic(after, after.copy(updatedAtMillis = 1500L)))
        }
        assertNull(bagDraftPhaseDiagnostic(before, before.copy(updatedAtMillis = 1500L)))
        assertNull(bagDraftPhaseDiagnostic(before.copy(phase = BagDraftPhase.REVIEWING), before.copy(updatedAtMillis = 1500L)))
    }

    @Test
    fun `save and discard summarize the active draft before tombstone clears private content`() {
        val before = draft().copy(
            photoUris = listOf("content://private/front", "content://private/back"),
            reviewContext = BagReviewContext.rescan(321L),
            fields = mapOf(
                "name" to BagDraftFieldValue(value = "Private name"),
                "roaster" to BagDraftFieldValue(value = "Private roaster"),
                "origin" to BagDraftFieldValue(value = " "),
                "region" to BagDraftFieldValue(),
            ),
            resultJson = "private result JSON",
        )

        listOf(BagDraftPhase.SAVED, BagDraftPhase.DISCARDED).forEach { phase ->
            val after = before.closeAsTombstone(phase, nowMillis = 1500L)
            val diagnostic = requireNotNull(bagDraftPhaseDiagnostic(before, after))
            assertEquals(500L, diagnostic.elapsedMs)
            assertEquals(2, diagnostic.fieldsResolved)
            assertEquals(2, diagnostic.context.photoCount)
            assertEquals(ScanDiagnosticMode.RESCAN, diagnostic.context.mode)
            assertEquals(scanCorrelationKey(SESSION_ID), diagnostic.context.sessionKey)
            assertEquals(scanCorrelationKey(GENERATION_ID), diagnostic.context.generationKey)
            assertEquals(scanCorrelationKey(WORK_ID), diagnostic.context.workKey)
            assertTrue(after.fields.isEmpty())
            assertTrue(after.photoUris.isEmpty())
            assertNull(after.resultJson)
            BagDraftPhase.entries.forEach { next ->
                assertNull(bagDraftPhaseDiagnostic(after, after.copy(phase = next, updatedAtMillis = 2000L)))
            }
        }
    }

    @Test
    fun `phase duration remains nonnegative when wall clock moves backwards`() {
        val before = draft()
        val after = before.copy(phase = BagDraftPhase.BACKGROUND, updatedAtMillis = 50L)

        assertEquals(0L, requireNotNull(bagDraftPhaseDiagnostic(before, after)).elapsedMs)
    }

    @Test
    fun `phase event observes the persisted draft only after file and directory sync complete`() {
        val directory = persistedDirectory()
        val order = mutableListOf<String>()
        var observedAtEmission: BagScanDraft? = null
        val events = mutableListOf<ScanLifecycleDiagnostic>()

        val updated = BagDraftStore.markPhase(
            directory = directory,
            sessionId = SESSION_ID,
            phase = BagDraftPhase.BACKGROUND,
            nowMillis = 1500L,
            fileSync = FileSync { order += "file-sync" },
            directorySync = DirectorySync { order += "directory-sync" },
            emit = {
                order += "emit"
                observedAtEmission = BagDraftStore.read(directory, SESSION_ID)
                events += it
            },
        )

        assertEquals(listOf("file-sync", "directory-sync", "emit"), order)
        assertEquals(BagDraftPhase.BACKGROUND, observedAtEmission?.phase)
        assertEquals(updated, observedAtEmission)
        assertEquals(updated, BagDraftStore.read(directory, SESSION_ID))
        assertEquals(ScanLifecycleEvent.BACKGROUNDED, events.single().event)
    }

    @Test
    fun `persisted repeated phase changes emit once and closed drafts cannot reopen`() {
        val phases = mapOf(
            BagDraftPhase.REVIEWING to ScanLifecycleEvent.REVIEW_OPENED,
            BagDraftPhase.BACKGROUND to ScanLifecycleEvent.BACKGROUNDED,
            BagDraftPhase.SAVED to ScanLifecycleEvent.SAVED,
            BagDraftPhase.DISCARDED to ScanLifecycleEvent.DISCARDED,
        )

        phases.forEach { (phase, expectedEvent) ->
            val directory = persistedDirectory()
            val events = mutableListOf<ScanLifecycleDiagnostic>()
            fun mark(nextPhase: BagDraftPhase, now: Long) = BagDraftStore.markPhase(
                directory, SESSION_ID, nextPhase, now, FileSync { }, DirectorySync { }, events::add,
            )

            mark(phase, 1250L)
            mark(phase, 1500L)
            assertEquals(expectedEvent, events.single().event)
            assertEquals(phase, BagDraftStore.read(directory, SESSION_ID)?.phase)
            if (phase == BagDraftPhase.SAVED || phase == BagDraftPhase.DISCARDED) {
                val closed = BagDraftStore.read(directory, SESSION_ID)
                mark(BagDraftPhase.BACKGROUND, 2000L)
                assertEquals(closed, BagDraftStore.read(directory, SESSION_ID))
                assertEquals(1, events.size)
            }
        }
    }

    @Test
    fun `persisted tombstone event retains only the original counts and correlation`() {
        val before = draft().copy(
            photoUris = listOf("content://private/front", "content://private/back"),
            fields = mapOf("name" to BagDraftFieldValue(value = "Private name")),
            reviewContext = BagReviewContext.rescan(321L),
            resultJson = "private result JSON",
        )
        val directory = persistedDirectory(before)
        val events = mutableListOf<ScanLifecycleDiagnostic>()

        val saved = requireNotNull(
            BagDraftStore.markPhase(
                directory, SESSION_ID, BagDraftPhase.SAVED, 1500L,
                FileSync { }, DirectorySync { }, events::add,
            ),
        )

        assertEquals(saved, BagDraftStore.read(directory, SESSION_ID))
        assertTrue(saved.fields.isEmpty())
        assertTrue(saved.photoUris.isEmpty())
        assertNull(saved.resultJson)
        val event = events.single()
        assertEquals(ScanLifecycleEvent.SAVED, event.event)
        assertEquals(1, event.fieldsResolved)
        assertEquals(2, event.context.photoCount)
        assertEquals(ScanDiagnosticMode.RESCAN, event.context.mode)
        assertEquals(scanCorrelationKey(GENERATION_ID), event.context.generationKey)
    }

    @Test
    fun `failed file sync leaves the original phase intact and emits no transition`() {
        val before = draft()
        val directory = persistedDirectory(before)
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        var directorySyncs = 0

        assertThrows(IOException::class.java) {
            BagDraftStore.markPhase(
                directory = directory,
                sessionId = SESSION_ID,
                phase = BagDraftPhase.SAVED,
                nowMillis = 1500L,
                fileSync = FileSync { throw IOException("Injected file sync failure") },
                directorySync = DirectorySync { directorySyncs++ },
                emit = events::add,
            )
        }

        assertEquals(before, BagDraftStore.read(directory, SESSION_ID))
        assertEquals(0, directorySyncs)
        assertTrue(events.isEmpty())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `failed directory sync emits no event even after atomic replacement`() {
        val directory = persistedDirectory()
        val events = mutableListOf<ScanLifecycleDiagnostic>()

        assertThrows(IOException::class.java) {
            BagDraftStore.markPhase(
                directory = directory,
                sessionId = SESSION_ID,
                phase = BagDraftPhase.DISCARDED,
                nowMillis = 1500L,
                fileSync = FileSync { },
                directorySync = DirectorySync { throw IOException("Injected directory sync failure") },
                emit = events::add,
            )
        }

        assertEquals(BagDraftPhase.DISCARDED, BagDraftStore.read(directory, SESSION_ID)?.phase)
        assertTrue(events.isEmpty())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `failing phase sink cannot fail the persisted transition or create a duplicate event`() {
        val directory = persistedDirectory()
        var attempts = 0
        val failingSink: (ScanLifecycleDiagnostic) -> Unit = { attempts++; error("Private sink failure") }

        val saved = BagDraftStore.markPhase(
            directory, SESSION_ID, BagDraftPhase.SAVED, 1500L, FileSync { }, DirectorySync { }, failingSink,
        )
        val repeated = BagDraftStore.markPhase(
            directory, SESSION_ID, BagDraftPhase.SAVED, 2000L, FileSync { }, DirectorySync { }, failingSink,
        )

        assertEquals(BagDraftPhase.SAVED, saved?.phase)
        assertEquals(saved, BagDraftStore.read(directory, SESSION_ID))
        assertEquals(saved, repeated)
        assertEquals(1, attempts)
    }

    @Test
    fun `missing draft cannot create a phase event or a new stored draft`() {
        val directory = temporaryFolder.newFolder()
        val events = mutableListOf<ScanLifecycleDiagnostic>()
        var syncs = 0

        val updated = BagDraftStore.markPhase(
            directory, SESSION_ID, BagDraftPhase.SAVED, 1500L,
            FileSync { syncs++ }, DirectorySync { syncs++ }, events::add,
        )

        assertNull(updated)
        assertNull(BagDraftStore.read(directory, SESSION_ID))
        assertTrue(events.isEmpty())
        assertEquals(0, syncs)
    }

    private fun persistedDirectory(before: BagScanDraft = draft()) = temporaryFolder.newFolder().also { directory ->
        BagDraftStore.write(directory, before, FileSync { }, DirectorySync { })
    }

    private fun telemetry(events: MutableList<ScanLifecycleDiagnostic>) = BagScanRunTelemetry(
        identity = ScanDiagnosticContext(),
        clock = { 100L },
        emit = events::add,
    )

    private fun result(
        vararg fields: Pair<String, String>,
        status: LlmEnrichmentStatus = LlmEnrichmentStatus.NOT_RUN,
    ) = BagPhotoProcessingResult(
        fieldEvidence = fields.associate { (field, value) ->
            field to BagFieldEvidence(
                fieldName = field,
                value = value,
                sourceType = BagFieldSourceType.OCR,
                confidence = BagFieldConfidence.HIGH,
            )
        },
        llmStatus = status,
    )

    private fun draft() = BagScanDraft(
        sessionId = SESSION_ID,
        generationId = GENERATION_ID,
        workId = WORK_ID,
        createdAtMillis = 1000L,
        updatedAtMillis = 1000L,
    )

    private companion object {
        const val SESSION_ID = "b9ef87a5-4285-4cbd-a12d-987be7026d39"
        const val GENERATION_ID = "fa2367d0-a789-4b26-9578-35e5fbdbd610"
        const val WORK_ID = "8b44ca43-98ba-4c41-9ecb-bfed78794ef7"
    }
}
