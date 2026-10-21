package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BagDraftStoreTest {
    @Test
    fun `review location survives reopen without changing edits revisions or recent activity`() {
        val directory = Files.createTempDirectory("bag-draft-review-location").toFile()
        val draft = recognizedDraft().applyUserEdit(BagDraftField.NAME, "My coffee", 3L)
        val location = BagDraftReviewLocation(
            scrollIndex = 8,
            scrollOffset = 36,
            expandedFields = listOf("origin", "weight"),
            showMoreDetails = true,
        )
        try {
            val locatedDraft = draft.withReviewLocation(location)
            BagDraftStore.write(directory, locatedDraft, FileSync { }, DirectorySync { })
            val restored = requireNotNull(BagDraftStore.read(directory, draft.sessionId))

            assertEquals(location, restored.reviewLocation)
            assertEquals(draft.fields, restored.fields)
            assertEquals(draft.updatedAtMillis, restored.updatedAtMillis)
            assertEquals(draft.generationId, restored.generationId)
            assertSame(restored, restored.withReviewLocation(location))
            val closed = restored.closeAsTombstone(BagDraftPhase.SAVED, 4L)
            assertEquals(BagDraftReviewLocation(), closed.reviewLocation)
            assertSame(closed, closed.withReviewLocation(location))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `uncertain reading stays outside saved values until explicitly accepted`() {
        val draft = emptyDraft().applyRecognitionResult(
            incoming = result("Possible coffee", null, BagFieldConfidence.NEEDS_REVIEW),
            sourceRevision = 2L,
            generationId = "generation-1",
            workId = null,
            terminal = true,
        )

        assertNull(draft.field(BagDraftField.NAME).value)
        assertEquals("Possible coffee", draft.field(BagDraftField.NAME).pendingSuggestion?.value)
        val accepted = draft.acceptPendingSuggestion(BagDraftField.NAME, 3L, "Possible coffee")
        assertEquals("Possible coffee", accepted.field(BagDraftField.NAME).value)
        assertEquals(BagDraftFieldSource.USER, accepted.field(BagDraftField.NAME).source)
        assertNull(accepted.field(BagDraftField.NAME).pendingSuggestion)
    }

    @Test
    fun `accepted automatic reading fills an empty untouched field but material replacements are suggestions`() {
        val first = recognizedDraft()
        assertEquals("Recognized coffee", first.field(BagDraftField.NAME).value)
        assertEquals(0L, first.field(BagDraftField.NAME).userRevision)

        val refined = first.applyRecognitionResult(
            incoming = result("Different coffee", null),
            sourceRevision = 3L,
            generationId = "generation-1",
            workId = null,
            terminal = true,
        )
        assertEquals("Recognized coffee", refined.field(BagDraftField.NAME).value)
        assertEquals("Different coffee", refined.field(BagDraftField.NAME).pendingSuggestion?.value)
    }

    @Test
    fun `explicitly cleared field stays blank when a later accepted reading arrives`() {
        val cleared = recognizedDraft().applyUserEdit(BagDraftField.NAME, "", 3L)
        val refined = cleared.applyRecognitionResult(
            incoming = result("Recognized again", null),
            sourceRevision = 4L,
            generationId = "generation-1",
            workId = null,
            terminal = true,
        )
        assertEquals("", refined.field(BagDraftField.NAME).value)
        assertEquals("Recognized again", refined.field(BagDraftField.NAME).pendingSuggestion?.value)
    }

    @Test
    fun `keep mine resolves a pending reading and repeated passes do not restore it`() {
        val mine = recognizedDraft().applyUserEdit(BagDraftField.NAME, "My coffee", 3L)
        val suggested = mine.applyRecognitionResult(
            incoming = result("Different coffee", null),
            sourceRevision = 4L,
            generationId = "generation-1",
            workId = null,
            terminal = false,
        )
        val kept = suggested.applyUserEdit(BagDraftField.NAME, "My coffee", 5L)
        val repeated = kept.applyRecognitionResult(
            incoming = result("Different coffee", null),
            sourceRevision = 6L,
            generationId = "generation-1",
            workId = null,
            terminal = true,
        )

        assertEquals("My coffee", repeated.field(BagDraftField.NAME).value)
        assertNull(repeated.field(BagDraftField.NAME).pendingSuggestion)
        assertEquals(2L, repeated.field(BagDraftField.NAME).userRevision)
    }

    @Test
    fun `stale suggestion action does not accept a newer reading`() {
        val suggested = recognizedDraft().applyRecognitionResult(
            incoming = result("Newer reading", null),
            sourceRevision = 3L,
            generationId = "generation-1",
            workId = null,
            terminal = true,
        )

        assertEquals(suggested, suggested.acceptPendingSuggestion(BagDraftField.NAME, 4L, "Older reading"))
    }

    @Test
    fun `restored legacy uncertain reading becomes a suggestion while user choices remain saved values`() {
        val directory = Files.createTempDirectory("bag-draft-legacy-review").toFile()
        val legacy = emptyDraft().copy(fields = mapOf(
            "name" to BagDraftFieldValue(
                value = "Uncertain coffee",
                source = BagDraftFieldSource.RECOGNITION,
                sourceRevision = 2L,
                reviewState = BagDraftReviewState.NEEDS_REVIEW,
            ),
            "roaster" to BagDraftFieldValue(
                value = "Chosen roaster",
                source = BagDraftFieldSource.USER,
                userRevision = 1L,
                reviewState = BagDraftReviewState.ACCEPTED,
            ),
        ))
        try {
            BagDraftStore.write(directory, legacy, FileSync { }, DirectorySync { })
            val restored = requireNotNull(BagDraftStore.read(directory, legacy.sessionId))

            assertNull(restored.field(BagDraftField.NAME).value)
            assertEquals("Uncertain coffee", restored.field(BagDraftField.NAME).pendingSuggestion?.value)
            assertEquals("Chosen roaster", restored.field(BagDraftField.ROASTER).value)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `late recognition never overwrites user or focused fields`() {
        val sessionId = UUID.randomUUID().toString()
        val original = newBagScanDraft(
            sessionId = sessionId,
            photoUris = listOf("content://front"),
            reviewContext = BagReviewContext.addNew(),
            preference = RecognitionPreference.UNDECIDED,
            nowMillis = 1L,
        ).applyRecognitionResult(
            incoming = result(name = "Recognized", roaster = "First roaster"),
            sourceRevision = 2L,
            generationId = "generation-1",
            workId = "work-1",
            terminal = false,
        ).applyUserEdit(BagDraftField.NAME, "My corrected name", nowMillis = 3L)

        val refined = original.applyRecognitionResult(
            incoming = result(name = "Later name", roaster = "Later roaster"),
            sourceRevision = 4L,
            generationId = "generation-1",
            workId = "work-1",
            focusedFields = setOf(BagDraftField.ROASTER),
            terminal = true,
        )

        assertEquals("My corrected name", refined.field(BagDraftField.NAME).value)
        assertEquals("Later name", refined.field(BagDraftField.NAME).pendingSuggestion?.value)
        assertEquals("First roaster", refined.field(BagDraftField.ROASTER).value)
        assertEquals(null, refined.field(BagDraftField.ROASTER).pendingSuggestion)
        assertEquals(RecognitionRunState.COMPLETE, refined.recognitionRunState)

        val accepted = refined.acceptPendingSuggestion(BagDraftField.NAME, nowMillis = 5L)
        assertEquals("Later name", accepted.field(BagDraftField.NAME).value)
        assertEquals(2L, accepted.field(BagDraftField.NAME).userRevision)
        assertEquals(null, accepted.field(BagDraftField.NAME).pendingSuggestion)
    }

    @Test
    fun `stale generations and closed tombstones suppress late work`() {
        val active = newBagScanDraft(
            sessionId = UUID.randomUUID().toString(),
            photoUris = emptyList(),
            reviewContext = BagReviewContext.addNew(),
            preference = RecognitionPreference.UNDECIDED,
            nowMillis = 1L,
        ).copy(generationId = "current")

        val stale = active.applyRecognitionResult(
            incoming = result(name = "Stale", roaster = null),
            sourceRevision = 2L,
            generationId = "old",
            workId = null,
            terminal = true,
        )
        val discarded = active.copy(phase = BagDraftPhase.DISCARDED)
        val late = discarded.applyRecognitionResult(
            incoming = result(name = "Late", roaster = null),
            sourceRevision = 3L,
            generationId = "current",
            workId = null,
            terminal = true,
        )

        assertEquals(active, stale)
        assertEquals(discarded, late)
        assertFalse(late.isActive)
    }

    @Test
    fun `draft round trip retains active and closed ownership state`() {
        val directory = Files.createTempDirectory("bag-drafts").toFile()
        val noOpFileSync = FileSync { }
        val noOpDirectorySync = DirectorySync { }
        val draft = newBagScanDraft(
            sessionId = UUID.randomUUID().toString(),
            photoUris = listOf("content://front", "content://back"),
            reviewContext = BagReviewContext.addNew(),
            preference = RecognitionPreference.ENABLED,
            nowMillis = 42L,
        )
        try {
            BagDraftStore.write(directory, draft, noOpFileSync, noOpDirectorySync)
            assertEquals(draft, BagDraftStore.read(directory, draft.sessionId))

            val saved = draft.copy(phase = BagDraftPhase.SAVED, updatedAtMillis = 43L)
            BagDraftStore.write(directory, saved, noOpFileSync, noOpDirectorySync)
            val restored = BagDraftStore.read(directory, draft.sessionId)
            assertNotNull(restored)
            assertEquals(BagDraftPhase.SAVED, restored?.phase)
            assertFalse(requireNotNull(restored).isActive)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `terminal empty result is recoverable rather than a false success`() {
        val draft = newBagScanDraft(
            sessionId = UUID.randomUUID().toString(),
            photoUris = emptyList(),
            reviewContext = BagReviewContext.addNew(),
            preference = RecognitionPreference.UNDECIDED,
            nowMillis = 1L,
        ).applyRecognitionResult(
            incoming = BagPhotoProcessingResult(llmStatus = LlmEnrichmentStatus.FAILED),
            sourceRevision = 2L,
            generationId = "generation-1",
            workId = null,
            terminal = true,
        )

        assertEquals(RecognitionRunState.RETRIABLE_FAILURE, draft.recognitionRunState)
    }

    @Test
    fun `authorized transient failure retains fields and remains retryable after restoration`() {
        val directory = Files.createTempDirectory("bag-draft-recovery").toFile()
        val draft = recognizedDraft().applyRecognitionResult(
            incoming = BagPhotoProcessingResult(llmStatus = LlmEnrichmentStatus.UNAVAILABLE),
            sourceRevision = 3L,
            generationId = "generation-1",
            workId = "work-1",
            terminal = true,
        )
        try {
            BagDraftStore.write(directory, draft, FileSync { }, DirectorySync { })
            val restored = requireNotNull(BagDraftStore.read(directory, draft.sessionId))

            assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, restored.recognitionCapability)
            assertEquals(RecognitionRunState.RETRIABLE_FAILURE, restored.recognitionRunState)
            assertEquals("Recognized coffee", restored.field(BagDraftField.NAME).value)
            assertEquals(LlmEnrichmentStatus.UNAVAILABLE, decodeBagExtractionResult(restored.resultJson!!).llmStatus)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `authorization and setup blockers survive restoration without a competing retry`() {
        val directory = Files.createTempDirectory("bag-draft-blockers").toFile()
        val blockers = listOf(
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED to RecognitionCapability.AUTHORIZATION_REQUIRED,
            LlmEnrichmentStatus.SETUP_REQUIRED to RecognitionCapability.ASSET_SETUP_REQUIRED,
        )
        try {
            blockers.forEach { (status, capability) ->
                val draft = recognizedDraft().applyRecognitionResult(
                    incoming = BagPhotoProcessingResult(llmStatus = status),
                    sourceRevision = 3L,
                    generationId = "generation-1",
                    workId = "work-1",
                    terminal = true,
                )
                BagDraftStore.write(directory, draft, FileSync { }, DirectorySync { })
                val restored = requireNotNull(BagDraftStore.read(directory, draft.sessionId))

                assertEquals(capability, restored.recognitionCapability)
                assertEquals(RecognitionRunState.IDLE, restored.recognitionRunState)
                assertEquals("Recognized coffee", restored.field(BagDraftField.NAME).value)
                assertEquals(status, decodeBagExtractionResult(restored.resultJson!!).llmStatus)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `successful recognition clears prior authorization setup and temporary blockers`() {
        listOf(
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED,
            LlmEnrichmentStatus.SETUP_REQUIRED,
            LlmEnrichmentStatus.UNAVAILABLE,
        ).forEach { status ->
            val blocked = recognizedDraft().applyRecognitionResult(
                incoming = BagPhotoProcessingResult(llmStatus = status),
                sourceRevision = 3L,
                generationId = "generation-1",
                workId = "work-1",
                terminal = true,
            )
            val recovered = blocked.applyRecognitionResult(
                incoming = result(name = "Recovered coffee", roaster = "Recovered roaster"),
                sourceRevision = 4L,
                generationId = "generation-1",
                workId = "work-1",
                terminal = true,
            )

            assertEquals(RecognitionCapability.READY, recovered.recognitionCapability)
            assertEquals(RecognitionRunState.COMPLETE, recovered.recognitionRunState)
            assertEquals("Recognized coffee", recovered.field(BagDraftField.NAME).value)
            assertEquals("Recovered coffee", recovered.field(BagDraftField.NAME).pendingSuggestion?.value)
        }
    }

    @Test
    fun `temporary unavailability does not offer AI retry before recognition is enabled`() {
        listOf(RecognitionPreference.UNDECIDED, RecognitionPreference.DISABLED).forEach { preference ->
            val draft = recognizedDraft().copy(recognitionPreference = preference).applyRecognitionResult(
                incoming = BagPhotoProcessingResult(llmStatus = LlmEnrichmentStatus.UNAVAILABLE),
                sourceRevision = 3L,
                generationId = "generation-1",
                workId = "work-1",
                terminal = true,
            )

            assertEquals(RecognitionCapability.TEMPORARILY_UNAVAILABLE, draft.recognitionCapability)
            assertEquals(RecognitionRunState.IDLE, draft.recognitionRunState)
            assertEquals("Recognized coffee", draft.field(BagDraftField.NAME).value)
        }
    }

    @Test
    fun `closed tombstones retain ownership but redact draft content`() {
        val draft = newBagScanDraft(
            sessionId = UUID.randomUUID().toString(),
            photoUris = listOf("content://front"),
            reviewContext = BagReviewContext.rescan(42L),
            preference = RecognitionPreference.ENABLED,
            nowMillis = 1L,
        ).applyRecognitionResult(
            incoming = result(name = "Private coffee", roaster = "Private roaster"),
            sourceRevision = 2L,
            generationId = "generation-1",
            workId = "work-1",
            terminal = true,
        )

        val tombstone = draft.closeAsTombstone(BagDraftPhase.DISCARDED, nowMillis = 3L)

        assertEquals(BagDraftPhase.DISCARDED, tombstone.phase)
        assertEquals("generation-1", tombstone.generationId)
        assertEquals("work-1", tombstone.workId)
        assertEquals(emptyList<String>(), tombstone.photoUris)
        assertEquals(emptyMap<String, BagDraftFieldValue>(), tombstone.fields)
        assertEquals(null, tombstone.resultJson)
        assertFalse(tombstone.isActive)
    }

    private fun recognizedDraft(): BagScanDraft = newBagScanDraft(
        sessionId = UUID.randomUUID().toString(),
        photoUris = listOf("content://front"),
        reviewContext = BagReviewContext.addNew(),
        preference = RecognitionPreference.ENABLED,
        nowMillis = 1L,
    ).applyRecognitionResult(
        incoming = result(name = "Recognized coffee", roaster = "Recognized roaster"),
        sourceRevision = 2L,
        generationId = "generation-1",
        workId = "work-1",
        terminal = true,
    )

    private fun emptyDraft(): BagScanDraft = newBagScanDraft(
        sessionId = UUID.randomUUID().toString(),
        photoUris = listOf("content://front"),
        reviewContext = BagReviewContext.addNew(),
        preference = RecognitionPreference.ENABLED,
        nowMillis = 1L,
    )

    private fun result(
        name: String,
        roaster: String?,
        confidence: BagFieldConfidence = BagFieldConfidence.HIGH,
    ): BagPhotoProcessingResult =
        BagPhotoProcessingResult(
            ocrPrefill = OcrFieldExtractor.OcrExtractionResult(
                name = name,
                roaster = roaster,
            ),
            fieldEvidence = listOfNotNull("name" to name, roaster?.let { "roaster" to it }).associate { (field, value) ->
                field to BagFieldEvidence(
                    fieldName = field,
                    value = value,
                    sourceType = BagFieldSourceType.OCR,
                    confidence = confidence,
                )
            },
            llmStatus = LlmEnrichmentStatus.SUCCEEDED,
        )
}
