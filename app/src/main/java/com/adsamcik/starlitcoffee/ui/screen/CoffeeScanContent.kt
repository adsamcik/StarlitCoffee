package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.BagThumbnail
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar

/** The common camera surface; the caller owns camera resources and scan-session state. */
@Composable
internal fun CoffeeScanContent(
    photos: List<String>,
    choosingCoffee: Boolean,
    cameraReady: Boolean,
    frameReady: Boolean,
    autoEnabled: Boolean,
    busy: Boolean,
    guidanceResId: Int,
    errorResId: Int?,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onCapture: () -> Unit,
    onToggleAuto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onReview: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("coffee-scan"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.action_scan_coffee),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Surface(
            color = if (errorResId != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (errorResId != null) {
                MaterialTheme.colorScheme.onErrorContainer
            } else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(errorResId ?: guidanceResId),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).then(
                    if (errorResId != null) Modifier.testTag("coffee-scan-error").semantics {
                        liveRegion = LiveRegionMode.Polite
                    } else Modifier,
                ),
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                .clip(MaterialTheme.shapes.large).background(Color.Black).testTag("coffee-scan-preview"),
        ) {
            preview()
            ScanFramingOverlay(frameReady)
            if (!cameraReady) CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (photos.isNotEmpty()) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(photos, key = { _, uri -> uri }) { index, uri ->
                        CapturedCoffeePhoto(uri, index, !busy) { onRemovePhoto(uri) }
                    }
                }
                if (!choosingCoffee) Button(
                    onClick = onReview,
                    enabled = !busy,
                    modifier = Modifier.sizeIn(minWidth = 144.dp, minHeight = 64.dp).testTag("coffee-scan-review"),
                ) {
                    Text(stringResource(R.string.action_review_scan), textAlign = TextAlign.Center)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    OutlinedIconButton(
                        onClick = onImport,
                        enabled = !busy,
                        modifier = Modifier.size(56.dp).testTag("coffee-scan-import"),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.action_choose_photos))
                    }
                }
                ScanShutter(cameraReady && !busy, busy, onCapture)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (!choosingCoffee) FilterChip(
                        selected = autoEnabled,
                        onClick = onToggleAuto,
                        enabled = !busy,
                        label = {
                            Text(
                                stringResource(if (autoEnabled) R.string.guided_scan_auto_on else R.string.guided_scan_auto_off),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.testTag("coffee-scan-auto"),
                    )
                }
            }
            if (!choosingCoffee && photos.isEmpty()) {
                TextButton(
                    onClick = onReview,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("coffee-scan-manual"),
                ) {
                    Text(stringResource(R.string.action_enter_manually))
                }
            }
        }
    }
}

@Composable
internal fun CoffeeScanFallback(
    cameraUnavailable: Boolean,
    enableCameraLabelResId: Int,
    busy: Boolean,
    errorResId: Int?,
    onBack: () -> Unit,
    onEnableCamera: () -> Unit,
    onImport: () -> Unit,
    onManualEntry: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("coffee-scan-fallback")) {
        ScreenTopBar(stringResource(R.string.action_scan_coffee), onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(if (cameraUnavailable) R.string.msg_scan_camera_unavailable else R.string.msg_scan_camera_permission),
                style = MaterialTheme.typography.bodyLarge,
            )
            errorResId?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(onEnableCamera, Modifier.fillMaxWidth(), enabled = !busy) {
                Text(stringResource(enableCameraLabelResId))
            }
            OutlinedButton(onImport, Modifier.fillMaxWidth().testTag("coffee-scan-import"), enabled = !busy) {
                Text(stringResource(R.string.action_choose_photos))
            }
            onManualEntry?.let {
                TextButton(it, Modifier.fillMaxWidth().testTag("coffee-scan-manual"), enabled = !busy) {
                    Text(stringResource(R.string.action_enter_manually))
                }
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ScanShutter(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.guided_scan_capture)
    Box(
        Modifier.size(72.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }.testTag("coffee-scan-capture"),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp))
        else Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun CapturedCoffeePhoto(uri: String, index: Int, enabled: Boolean, onRemove: () -> Unit) {
    val description = stringResource(R.string.format_cd_scan_photo, index + 1)
    val removeLabel = "${stringResource(R.string.cd_remove_photo)}: $description"
    Box(Modifier.size(64.dp)) {
        BagThumbnail(uri, size = 64.dp, shape = MaterialTheme.shapes.medium, contentDescription = description)
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.align(Alignment.BottomEnd).size(48.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(Icons.Filled.Close, removeLabel, Modifier.padding(4.dp).size(18.dp))
            }
        }
    }
}

@Composable
private fun ScanFramingOverlay(ready: Boolean) {
    val border by animateColorAsState(
        if (ready) Color(0xFF5ED18D) else Color.White.copy(alpha = 0.85f),
        label = "scanFrame",
    )
    Canvas(Modifier.fillMaxSize()) {
        val width = size.width * 0.82f
        val height = minOf(width * 4f / 3f, size.height * 0.70f)
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val rect = Rect(left, top, left + width, top + height)
        val corner = 24.dp.toPx()
        val cutout = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
            fillType = PathFillType.EvenOdd
        }
        drawPath(cutout, Color.Black.copy(alpha = 0.40f))
        drawRoundRect(border, Offset(left, top), rect.size, CornerRadius(corner, corner), style = Stroke(2.dp.toPx()))
    }
}
