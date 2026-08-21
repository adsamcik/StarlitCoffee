package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import com.adsamcik.starlitcoffee.util.AndroidDirectorySync
import com.adsamcik.starlitcoffee.util.AndroidFileSync
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import dev.tracebox.Tracebox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
enum class BagDraftPhase {
    CAPTURING,
    REVIEWING,
    BACKGROUND,
    SAVED,
    DISCARDED,
}

@Serializable
enum class BagDraftField(val wireName: String) {
    NAME("name"),
    ROASTER("roaster"),
    ORIGIN("origin"),
    REGION("region"),
    FARM("farm"),
    ALTITUDE("altitude"),
    ROAST_LEVEL("roastLevel"),
    BARCODE("barcode"),
    WEIGHT("weight"),
    NOTES("notes"),
    VARIETY("variety"),
    PROCESS_TYPE("processType"),
    TASTING_NOTES("tastingNotes"),
    IS_DECAF("isDecaf"),
    DECAF_PROCESS("decafProcess"),
    ROAST_DATE("roastDate"),
    EXPIRY_DATE("expiryDate"),
    ;

    companion object {
        fun fromWireName(value: String): BagDraftField? = entries.firstOrNull { it.wireName == value }
    }
}

@Serializable
enum class BagDraftFieldSource {
    EMPTY,
    RECOGNITION,
    USER,
    EXISTING_BAG,
}

@Serializable
enum class BagDraftReviewState {
    ACCEPTED,
    NEEDS_REVIEW,
    MISSING,
}

@Serializable
data class BagDraftSuggestion(
    val value: String,
    val sourceRevision: Long,
)

@Serializable
data class BagDraftFieldValue(
    val value: String? = null,
    val source: BagDraftFieldSource = BagDraftFieldSource.EMPTY,
    val sourceRevision: Long = 0L,
    val userRevision: Long = 0L,
    val reviewState: BagDraftReviewState = BagDraftReviewState.MISSING,
    val pendingSuggestion: BagDraftSuggestion? = null,
)

@Serializable
data class BagScanDraft(
    val schemaVersion: Int = BAG_DRAFT_SCHEMA_VERSION,
    val sessionId: String,
    val generationId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val phase: BagDraftPhase = BagDraftPhase.CAPTURING,
    val photoUris: List<String> = emptyList(),
    val fields: Map<String, BagDraftFieldValue> = emptyMap(),
    val recognitionCapability: RecognitionCapability = RecognitionCapability.READY,
    val recognitionRunState: RecognitionRunState = RecognitionRunState.IDLE,
    val recognitionPreference: RecognitionPreference = RecognitionPreference.UNDECIDED,
    val workId: String? = null,
    val reviewContext: BagReviewContext = BagReviewContext.addNew(),
    val resultJson: String? = null,
    val pendingExternalSetupReason: String? = null,
) {
    val isActive: Boolean
        get() = phase != BagDraftPhase.SAVED && phase != BagDraftPhase.DISCARDED

    fun field(field: BagDraftField): BagDraftFieldValue = fields[field.wireName] ?: BagDraftFieldValue()
}

const val BAG_DRAFT_SCHEMA_VERSION = 1

/** Process-local focus guard used by worker partials while the review is visible. */
object BagDraftFocusRegistry {
    private val focused = ConcurrentHashMap<String, Set<BagDraftField>>()

    fun update(sessionId: String, field: BagDraftField, hasFocus: Boolean) {
        focused.compute(sessionId) { _, current ->
            val next = if (hasFocus) current.orEmpty() + field else current.orEmpty() - field
            next.takeIf { it.isNotEmpty() }
        }
    }

    fun fields(sessionId: String): Set<BagDraftField> = focused[sessionId].orEmpty()

    fun clear(sessionId: String) {
        focused.remove(sessionId)
    }
}

internal fun newBagScanDraft(
    sessionId: String,
    photoUris: List<String>,
    reviewContext: BagReviewContext,
    preference: RecognitionPreference,
    nowMillis: Long,
): BagScanDraft = BagScanDraft(
    sessionId = UUID.fromString(sessionId).toString(),
    createdAtMillis = nowMillis,
    updatedAtMillis = nowMillis,
    photoUris = photoUris.distinct(),
    recognitionPreference = preference,
    reviewContext = reviewContext,
)

internal fun BagScanDraft.applyRecognitionResult(
    incoming: BagPhotoProcessingResult,
    sourceRevision: Long,
    generationId: String,
    workId: String?,
    focusedFields: Set<BagDraftField> = emptySet(),
    terminal: Boolean,
): BagScanDraft {
    if (!isActive || this.generationId != null && this.generationId != generationId) return this
    val incomingFields = incoming.toDraftFieldValues()
    return copy(
        generationId = generationId,
        updatedAtMillis = sourceRevision,
        phase = if (phase == BagDraftPhase.CAPTURING) BagDraftPhase.REVIEWING else phase,
        photoUris = incoming.updatedPhotoUrisOr(photoUris),
        fields = mergeRecognitionFields(incomingFields, sourceRevision, focusedFields),
        recognitionRunState = incoming.resolveRecognitionRunState(incomingFields, terminal),
        recognitionCapability = incoming.updatedRecognitionCapabilityOr(recognitionCapability),
        workId = workId ?: this.workId,
        resultJson = incoming.encodeToStoredJson(),
    )
}

private fun BagScanDraft.mergeRecognitionFields(
    incomingFields: Map<BagDraftField, BagDraftFieldValue>,
    sourceRevision: Long,
    focusedFields: Set<BagDraftField>,
): Map<String, BagDraftFieldValue> = fields.toMutableMap().also { merged ->
    incomingFields.forEach { (draftField, candidate) ->
        val current = field(draftField)
        val mayReplace = current.userRevision == 0L &&
            draftField !in focusedFields &&
            sourceRevision > current.sourceRevision
        merged[draftField.wireName] = when {
            mayReplace -> current.copy(
                value = candidate.value,
                source = BagDraftFieldSource.RECOGNITION,
                sourceRevision = sourceRevision,
                reviewState = candidate.reviewState,
                pendingSuggestion = null,
            )
            draftField !in focusedFields &&
                candidate.value != null &&
                candidate.value != current.value -> current.copy(
                pendingSuggestion = BagDraftSuggestion(candidate.value, sourceRevision),
            )
            else -> current
        }
    }
}

private fun BagPhotoProcessingResult.updatedPhotoUrisOr(
    currentPhotoUris: List<String>,
): List<String> = capturedPhotoUris
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?.distinct()
    ?.takeIf { it.isNotEmpty() }
    ?: currentPhotoUris

private fun BagPhotoProcessingResult.resolveRecognitionRunState(
    incomingFields: Map<BagDraftField, BagDraftFieldValue>,
    terminal: Boolean,
): RecognitionRunState = when {
    terminal && (
        llmStatus == LlmEnrichmentStatus.FAILED ||
            llmStatus == LlmEnrichmentStatus.TIMED_OUT ||
            incomingFields.isEmpty()
        ) -> RecognitionRunState.RETRIABLE_FAILURE
    terminal -> RecognitionRunState.COMPLETE
    incomingFields.isNotEmpty() -> RecognitionRunState.PARTIAL
    else -> RecognitionRunState.RUNNING
}

private fun BagPhotoProcessingResult.updatedRecognitionCapabilityOr(
    currentCapability: RecognitionCapability,
): RecognitionCapability = when (llmStatus) {
    LlmEnrichmentStatus.SETUP_REQUIRED -> RecognitionCapability.ASSET_SETUP_REQUIRED
    LlmEnrichmentStatus.UNAVAILABLE -> RecognitionCapability.TEMPORARILY_UNAVAILABLE
    else -> currentCapability
}

internal fun BagScanDraft.applyUserEdit(
    field: BagDraftField,
    value: String?,
    nowMillis: Long,
): BagScanDraft {
    if (!isActive) return this
    val current = this.field(field)
    if (current.value == value && current.source == BagDraftFieldSource.USER) return this
    return copy(
        updatedAtMillis = nowMillis,
        fields = fields + (
            field.wireName to current.copy(
                value = value,
                source = BagDraftFieldSource.USER,
                userRevision = current.userRevision + 1L,
                reviewState = if (value.isNullOrBlank()) {
                    BagDraftReviewState.MISSING
                } else {
                    BagDraftReviewState.ACCEPTED
                },
                pendingSuggestion = current.pendingSuggestion?.takeUnless { it.value == value },
            )
        ),
    )
}

internal fun BagScanDraft.acceptPendingSuggestion(
    field: BagDraftField,
    nowMillis: Long,
): BagScanDraft {
    if (!isActive) return this
    val current = this.field(field)
    val suggestion = current.pendingSuggestion ?: return this
    return copy(
        updatedAtMillis = nowMillis,
        fields = fields + (
            field.wireName to current.copy(
                value = suggestion.value,
                source = BagDraftFieldSource.USER,
                userRevision = current.userRevision + 1L,
                reviewState = BagDraftReviewState.ACCEPTED,
                pendingSuggestion = null,
            )
        ),
    )
}

internal fun BagScanDraft.closeAsTombstone(
    phase: BagDraftPhase,
    nowMillis: Long,
): BagScanDraft {
    require(phase == BagDraftPhase.SAVED || phase == BagDraftPhase.DISCARDED)
    return copy(
        updatedAtMillis = nowMillis,
        phase = phase,
        photoUris = emptyList(),
        fields = emptyMap(),
        recognitionCapability = RecognitionCapability.READY,
        recognitionRunState = RecognitionRunState.IDLE,
        reviewContext = BagReviewContext.addNew(),
        resultJson = null,
        pendingExternalSetupReason = null,
    )
}

private fun BagPhotoProcessingResult.toDraftFieldValues(): Map<BagDraftField, BagDraftFieldValue> {
    val values = linkedMapOf<BagDraftField, Pair<String?, BagFieldEvidence?>>()
    val prefill = ocrPrefill
    values[BagDraftField.NAME] = (prefill?.name ?: offLookupName) to fieldEvidence["name"]
    values[BagDraftField.ROASTER] = (prefill?.roaster ?: offLookupRoaster) to fieldEvidence["roaster"]
    values[BagDraftField.ORIGIN] = prefill?.origin to fieldEvidence["origin"]
    values[BagDraftField.REGION] = prefill?.region to fieldEvidence["region"]
    values[BagDraftField.FARM] = prefill?.farm to fieldEvidence["farm"]
    values[BagDraftField.ALTITUDE] = prefill?.altitude to fieldEvidence["altitude"]
    values[BagDraftField.ROAST_LEVEL] = prefill?.roastLevel to fieldEvidence["roastLevel"]
    values[BagDraftField.BARCODE] = detectedBarcode to fieldEvidence["barcode"]
    values[BagDraftField.WEIGHT] = prefill?.weight to fieldEvidence["weight"]
    values[BagDraftField.VARIETY] = prefill?.variety to fieldEvidence["variety"]
    values[BagDraftField.PROCESS_TYPE] = prefill?.processType to fieldEvidence["processType"]
    values[BagDraftField.TASTING_NOTES] = prefill?.tastingNotes to fieldEvidence["tastingNotes"]
    values[BagDraftField.IS_DECAF] = prefill?.isDecaf?.toString() to fieldEvidence["isDecaf"]
    values[BagDraftField.ROAST_DATE] = prefill?.roastDate to fieldEvidence["roastDate"]
    values[BagDraftField.EXPIRY_DATE] = prefill?.expiryDate to fieldEvidence["expiryDate"]
    return values.mapNotNull { (field, pair) ->
        val value = pair.first?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val reviewState = when (pair.second?.confidence) {
            BagFieldConfidence.HIGH -> BagDraftReviewState.ACCEPTED
            BagFieldConfidence.MEDIUM,
            BagFieldConfidence.LOW,
            BagFieldConfidence.NEEDS_REVIEW,
            null,
            -> BagDraftReviewState.NEEDS_REVIEW
        }
        field to BagDraftFieldValue(value = value, reviewState = reviewState)
    }.toMap()
}

private val BagDraftJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Private, no-backup, atomic persistence for resumable coffee drafts. */
object BagDraftStore {
    private const val DIRECTORY_NAME = "bag_scan_drafts"
    private val changes = MutableStateFlow(0L)

    fun observeActive(context: Context): Flow<List<BagScanDraft>> = changes
        .onStart { emit(changes.value) }
        .map { readActive(context) }

    @Synchronized
    fun ensure(
        context: Context,
        sessionId: String,
        photoUris: List<String>,
        reviewContext: BagReviewContext,
        preference: RecognitionPreference,
        nowMillis: Long = System.currentTimeMillis(),
    ): BagScanDraft {
        val current = read(context, sessionId)
        val next = when {
            current == null -> newBagScanDraft(sessionId, photoUris, reviewContext, preference, nowMillis)
            !current.isActive -> current
            else -> current.copy(
                updatedAtMillis = nowMillis,
                photoUris = photoUris.distinct(),
                reviewContext = reviewContext,
                recognitionPreference = preference,
            )
        }
        if (next != current) write(context, next)
        return next
    }

    @Synchronized
    fun beginGeneration(
        context: Context,
        sessionId: String,
        generationId: String,
        workId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): BagScanDraft? = update(context, sessionId) { draft ->
        if (!draft.isActive) draft else draft.copy(
            generationId = generationId,
            workId = workId ?: draft.workId,
            updatedAtMillis = nowMillis,
            recognitionRunState = RecognitionRunState.RUNNING,
        )
    }

    @Synchronized
    fun attachWork(
        context: Context,
        sessionId: String,
        generationId: String,
        workId: String,
    ): BagScanDraft? = update(context, sessionId) { draft ->
        if (!draft.isActive || draft.generationId != generationId) draft else draft.copy(workId = workId)
    }

    @Synchronized
    fun applyResult(
        context: Context,
        sessionId: String,
        generationId: String,
        workId: String?,
        result: BagPhotoProcessingResult,
        terminal: Boolean,
        focusedFields: Set<BagDraftField> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): BagScanDraft? = update(context, sessionId) { draft ->
        draft.applyRecognitionResult(
            incoming = result,
            sourceRevision = nowMillis.coerceAtLeast(draft.updatedAtMillis + 1L),
            generationId = generationId,
            workId = workId,
            focusedFields = focusedFields + BagDraftFocusRegistry.fields(sessionId),
            terminal = terminal,
        )
    }

    @Synchronized
    fun applyUserEdit(
        context: Context,
        sessionId: String,
        field: BagDraftField,
        value: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): BagScanDraft? = update(context, sessionId) { draft ->
        draft.applyUserEdit(field, value, nowMillis.coerceAtLeast(draft.updatedAtMillis + 1L))
    }

    @Synchronized
    fun acceptSuggestion(
        context: Context,
        sessionId: String,
        field: BagDraftField,
        nowMillis: Long = System.currentTimeMillis(),
    ): BagScanDraft? = update(context, sessionId) { draft ->
        draft.acceptPendingSuggestion(field, nowMillis.coerceAtLeast(draft.updatedAtMillis + 1L))
    }

    @Synchronized
    fun markPhase(
        context: Context,
        sessionId: String,
        phase: BagDraftPhase,
        nowMillis: Long = System.currentTimeMillis(),
    ): BagScanDraft? = update(context, sessionId) { draft ->
        when {
            !draft.isActive -> draft
            phase == BagDraftPhase.SAVED || phase == BagDraftPhase.DISCARDED ->
                draft.closeAsTombstone(phase, nowMillis)
            else -> draft.copy(phase = phase, updatedAtMillis = nowMillis)
        }
    }.also {
        if (phase == BagDraftPhase.SAVED || phase == BagDraftPhase.DISCARDED) {
            BagDraftFocusRegistry.clear(sessionId)
        }
    }

    fun isAcceptingResult(context: Context, sessionId: String, generationId: String): Boolean =
        read(context, sessionId)?.let { draft ->
            draft.isActive && (draft.generationId == null || draft.generationId == generationId)
        } == true

    fun read(context: Context, sessionId: String): BagScanDraft? =
        read(directory(context), sessionId)

    fun readActive(context: Context): List<BagScanDraft> =
        readAll(directory(context)).filter(BagScanDraft::isActive).sortedByDescending { it.updatedAtMillis }

    fun findByWorkId(context: Context, workId: String): BagScanDraft? =
        readAll(directory(context)).firstOrNull { it.workId == workId }

    @Synchronized
    fun write(context: Context, draft: BagScanDraft) {
        write(directory(context), draft)
        changes.value += 1L
    }

    @Synchronized
    private fun update(
        context: Context,
        sessionId: String,
        transform: (BagScanDraft) -> BagScanDraft,
    ): BagScanDraft? {
        val current = read(context, sessionId) ?: return null
        val next = transform(current)
        if (next != current) write(context, next)
        return next
    }

    internal fun write(
        directory: File,
        draft: BagScanDraft,
        fileSync: FileSync = AndroidFileSync,
        directorySync: DirectorySync = AndroidDirectorySync,
    ) {
        val destination = draftFile(directory, draft.sessionId)
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        check(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true) {
            "Could not create bag draft directory"
        }
        try {
            requireSafeDestination(destination)
            require(!Files.isSymbolicLink(temporary.toPath())) {
                "Bag draft temporary file cannot be a symbolic link"
            }
            FileOutputStream(temporary).use { output ->
                output.write(BagDraftJson.encodeToString(draft).toByteArray(Charsets.UTF_8))
                output.flush()
            }
            fileSync.sync(temporary)
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            directorySync.sync(directory)
        } finally {
            temporary.delete()
        }
    }

    internal fun read(directory: File, sessionId: String): BagScanDraft? {
        val file = draftFile(directory, sessionId).takeIf(::isSafeRegularFile) ?: return null
        return runCatching { BagDraftJson.decodeFromString<BagScanDraft>(file.readText()) }
            .onFailure { error -> Tracebox.log.error(error, "Failed to read bag draft") }
            .getOrNull()
            ?.takeIf { it.schemaVersion <= BAG_DRAFT_SCHEMA_VERSION }
    }

    internal fun readAll(directory: File): List<BagScanDraft> {
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("draft_") && it.name.endsWith(".json") }
            .mapNotNull { file ->
                val sessionId = file.name.removePrefix("draft_").removeSuffix(".json")
                runCatching { read(directory, sessionId) }.getOrNull()
            }
    }

    private fun directory(context: Context): File = File(context.noBackupFilesDir, DIRECTORY_NAME)

    private fun draftFile(directory: File, sessionId: String): File {
        val normalizedSessionId = UUID.fromString(sessionId).toString()
        return directory.toPath().toAbsolutePath().normalize()
            .resolve("draft_$normalizedSessionId.json")
            .toFile()
    }

    private fun requireSafeDestination(destination: File) {
        if (!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        require(isSafeRegularFile(destination)) {
            "Bag draft destination must be a non-symlink regular file"
        }
    }

    private fun isSafeRegularFile(file: File): Boolean =
        !Files.isSymbolicLink(file.toPath()) &&
            Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
}
