package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewUris
import com.adsamcik.starlitcoffee.util.ThumbnailLoader

/** The source is independent of the editable value and never substitutes a different photo. */
@Composable
fun LabelEvidencePreview(
    evidence: BagFieldEvidence,
    capturedPhotoUris: String?,
    modifier: Modifier = Modifier,
) {
    val source = remember(evidence, capturedPhotoUris) { resolveLabelEvidencePhoto(evidence, capturedPhotoUris) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.label_review_source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(evidenceSourceLabel(evidence.sourceType)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        source.uri?.let { uri ->
            key(uri) { LabelEvidencePhoto(uri = uri, region = source.region) }
        }
        if (source.sourceUnavailable) {
            Text(
                stringResource(R.string.msg_review_source_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        evidence.supportingText?.takeIf(String::isNotBlank)?.let { text ->
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LabelEvidencePhoto(uri: String, region: BagPhotoRect?) {
    val targetSizePx = with(LocalDensity.current) { 320.dp.roundToPx() }
    val loaded by produceState(initialValue = EvidencePhotoLoad(), key1 = uri, key2 = targetSizePx) {
        value = EvidencePhotoLoad()
        value = EvidencePhotoLoad(bitmap = ThumbnailLoader.loadThumbnailFromUri(uri, targetSizePx), loading = false)
    }
    var viewingPhoto by rememberSaveable(uri) { mutableStateOf(false) }
    val bitmap = loaded.bitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = bitmap != null, role = Role.Button) { viewingPhoto = true }
            .testTag("label-evidence-photo"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_bag_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            region?.let { LabelEvidenceOutline(bitmap = bitmap, region = it) }
        } else if (loaded.loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Text(
                stringResource(R.string.msg_review_source_unavailable),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
    if (viewingPhoto && bitmap != null) {
        FullScreenImageViewer(uri = uri, onDismiss = { viewingPhoto = false })
    }
}

@Composable
private fun LabelEvidenceOutline(bitmap: Bitmap, region: BagPhotoRect) {
    val outlineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        // ContentScale.Fit centers the whole EXIF-oriented bitmap, including its letterboxing.
        val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        val left = (size.width - imageWidth) / 2f + region.leftFraction * imageWidth
        val top = (size.height - imageHeight) / 2f + region.topFraction * imageHeight
        val regionSize = Size(
            width = (region.rightFraction - region.leftFraction) * imageWidth,
            height = (region.bottomFraction - region.topFraction) * imageHeight,
        )
        drawRect(Color.Black.copy(alpha = 0.75f), Offset(left, top), regionSize, style = Stroke(width = 4.dp.toPx()))
        drawRect(outlineColor, Offset(left, top), regionSize, style = Stroke(width = 2.dp.toPx()))
    }
}

private data class EvidencePhotoLoad(val bitmap: Bitmap? = null, val loading: Boolean = true)

internal data class LabelEvidencePhotoSource(
    val uri: String? = null,
    val region: BagPhotoRect? = null,
    val sourceUnavailable: Boolean = false,
)

internal fun resolveLabelEvidencePhoto(evidence: BagFieldEvidence, capturedPhotoUris: String?): LabelEvidencePhotoSource {
    val exactUri = evidence.previewUri?.takeIf(String::isNotBlank)
    val retainedPhotos = BagPhotoReviewUris.parse(capturedPhotoUris)
    if (exactUri == null) {
        // A side does not identify a retained photo after removal/reordering. Legacy evidence
        // without an exact URI can still explain its text, but cannot safely highlight a photo.
        return LabelEvidencePhotoSource(sourceUnavailable = evidence.side != null || evidence.sourceType in photoEvidenceSources)
    }
    if (exactUri !in retainedPhotos) return LabelEvidencePhotoSource(sourceUnavailable = true)
    return LabelEvidencePhotoSource(
        uri = exactUri,
        region = evidence.previewRect?.takeIf { evidence.sourceType in photoEvidenceSources && it.isValidEvidenceRegion() },
    )
}

private val photoEvidenceSources = setOf(BagFieldSourceType.OCR, BagFieldSourceType.CONSENSUS, BagFieldSourceType.OBSERVED_BARCODE_STEM)

private fun BagPhotoRect.isValidEvidenceRegion(): Boolean =
    listOf(leftFraction, topFraction, rightFraction, bottomFraction).all { it.isFinite() && it in 0f..1f } &&
        leftFraction < rightFraction && topFraction < bottomFraction

private fun evidenceSourceLabel(sourceType: BagFieldSourceType): Int = when (sourceType) {
    BagFieldSourceType.OCR, BagFieldSourceType.CONSENSUS -> R.string.evidence_from_label
    BagFieldSourceType.QR_LINK_LOOKUP -> R.string.evidence_from_qr_website
    BagFieldSourceType.OBSERVED_BARCODE_STEM -> R.string.evidence_from_label_code
    BagFieldSourceType.LOCAL_BARCODE_MATCH -> R.string.evidence_from_saved_bag
    BagFieldSourceType.BARCODE_LOOKUP -> R.string.evidence_from_barcode_lookup
    BagFieldSourceType.LLM -> R.string.evidence_suggested_from_label_context
}
