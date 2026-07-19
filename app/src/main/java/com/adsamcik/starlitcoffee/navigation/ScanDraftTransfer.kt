package com.adsamcik.starlitcoffee.navigation

import androidx.lifecycle.SavedStateHandle
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val CAPTURED_PHOTOS_RESULT_KEY = "captured_photos"
internal const val SCAN_FIELDS_RESULT_KEY = "scan_fields"
internal const val SCANNED_BARCODE_RESULT_KEY = "scanned_barcode"
internal const val SCAN_DRAFT_TRANSFER_RESULT_KEY = "scan_draft_transfer"

@Serializable
data class ScanThumbnailFocus(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
) {
    fun toBagPhotoRect(): BagPhotoRect = BagPhotoRect(
        leftFraction = leftFraction,
        topFraction = topFraction,
        rightFraction = rightFraction,
        bottomFraction = bottomFraction,
    )

    companion object {
        fun from(focus: BagPhotoRect?): ScanThumbnailFocus? = focus?.let {
            ScanThumbnailFocus(
                leftFraction = it.leftFraction,
                topFraction = it.topFraction,
                rightFraction = it.rightFraction,
                bottomFraction = it.bottomFraction,
            )
        }
    }
}

@Serializable
data class ScanDraftTransfer(
    val eventId: String = UUID.randomUUID().toString(),
    val fields: Map<String, String>,
    val capturedPhotoUris: String? = null,
    val scanSessionId: String,
    val generationId: String? = null,
    val thumbnailFocus: ScanThumbnailFocus? = null,
    val detectedBarcode: String? = null,
    val detectedQrUrl: String? = null,
)

internal object ScanDraftTransferCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(transfer: ScanDraftTransfer): String =
        json.encodeToString(ScanDraftTransfer.serializer(), transfer)

    fun decode(encoded: String?): ScanDraftTransfer? = encoded?.let {
        runCatching {
            json.decodeFromString(ScanDraftTransfer.serializer(), it)
        }.getOrNull()
    }
}

internal fun <T> SavedStateHandle.consumeOneShotResult(key: String): T? = remove(key)
