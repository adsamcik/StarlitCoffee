package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.BagThumbnail
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.PhotoStoragePolicy
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val READY_COLOR = Color(0xFF5ED18D)

// Auto-shutter tuning. Readiness gates on exposure/glare + frame steadiness
// (low motion) rather than an absolute blur threshold, since blur scores on the
// downsampled preview grid are unreliable; burst-pick-best handles sharpness.
private const val STABILIZE_MS = 700L
private const val MOTION_STEADY = 7f
private const val MOTION_MOVED = 16f
private const val BURST_COUNT = 3
private const val PREVIEW_GRID_WIDTH = 160
private const val SHARPNESS_MAX_LONG_EDGE_PX = 512

/**
 * Guided bag-scan capture screen. The user centers the bag inside a framing
 * overlay (reducing perspective distortion); the app auto-captures a short
 * burst once the frame is steady and well-lit, keeping the sharpest frame. A
 * manual shutter is always available and never blocked. Captured photos are
 * handed to [BagScanCaptureViewModel], which drives incremental extraction.
 */
@Composable
fun GuidedCaptureScreen(
    captureViewModel: BagScanCaptureViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by captureViewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var quality by remember { mutableStateOf(BagCaptureQuality(0f, 0f, 0f, 0f, 0, false)) }
    var motion by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var autoEnabled by remember { mutableStateOf(true) }
    var autoArmed by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }

    DisposableEffect(cameraController, analysisExecutor) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analyzer = BagFramingAnalyzer { q, m ->
            mainExecutor.execute {
                quality = q
                motion = m
                // Re-arm auto-capture once the user clearly moves the phone
                // (e.g. flipping to the back), so a static scene isn't captured
                // repeatedly.
                if (m > MOTION_MOVED) autoArmed = true
            }
        }
        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdown()
        }
    }

    val frameSteady = motion < MOTION_STEADY
    val frameReady = quality.glareOkay && quality.exposureOkay && frameSteady

    fun triggerCapture(fromAuto: Boolean) {
        if (capturing) return
        capturing = true
        if (fromAuto) autoArmed = false
        scope.launch {
            // CameraController.takePicture() must be invoked on the main thread,
            // so the burst runs on this (Main) coroutine; only the per-frame
            // sharpness scoring and the file write are offloaded.
            val bytes = captureBestBurst(cameraController, ContextCompat.getMainExecutor(context))
            val uri = bytes?.let {
                withContext(Dispatchers.IO) { ScanPhotoStorage.writeCaptureToCache(context, it) }
            }
            if (uri != null) captureViewModel.addPhoto(uri)
            capturing = false
        }
    }

    // Auto-shutter: when the frame is ready (steady + well-lit) and armed, wait
    // a short stabilization delay then capture. Keying the effect on the gate
    // means motion breaking readiness cancels the pending capture automatically.
    val autoGate = autoEnabled && autoArmed && !capturing && frameReady && hasCameraPermission
    LaunchedEffect(autoGate) {
        if (autoGate) {
            kotlinx.coroutines.delay(STABILIZE_MS)
            triggerCapture(fromAuto = true)
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var photoPendingRemoval by remember { mutableStateOf<String?>(null) }
    fun handleBack() {
        if (state.hasPhotos) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = true) { handleBack() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_discard_scan_title)) },
            text = { Text(stringResource(R.string.msg_discard_scan_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_scanning))
                }
            },
        )
    }

    photoPendingRemoval?.let { uri ->
        DestructiveActionDialog(
            titleRes = R.string.cd_remove_photo,
            confirmLabelRes = R.string.cd_remove_photo,
            onConfirm = {
                captureViewModel.removePhoto(uri)
                ScanPhotoStorage.deleteStagedCapture(context.applicationContext, uri)
                photoPendingRemoval = null
            },
            onDismiss = { photoPendingRemoval = null },
        )
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.msg_camera_permission_required),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        FramingOverlay(ready = frameReady, modifier = Modifier.fillMaxSize())

        CaptureTopBar(onBack = ::handleBack)

        // Guidance + non-blocking warnings, anchored above the centre frame.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GuidanceLabel(side = state.nextSide, photoCount = state.photos.size)
            Spacer(Modifier.height(8.dp))
            CaptureWarning(quality = quality, steady = frameSteady, capturing = capturing)
        }

        CaptureBottomBar(
            photos = state.photos.map { it.uri },
            capturing = capturing,
            autoEnabled = autoEnabled,
            onToggleAuto = { autoEnabled = !autoEnabled },
            onCapture = { triggerCapture(fromAuto = false) },
            onRemovePhoto = { uri -> photoPendingRemoval = uri },
            onSkip = { captureViewModel.finishCapturing() },
            onFinished = { captureViewModel.finishCapturing() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CaptureTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Color.White,
            )
        }
        Text(
            text = stringResource(R.string.screen_scan_bag_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun GuidanceLabel(side: BagCaptureSide, photoCount: Int) {
    val text = when {
        photoCount == 0 -> stringResource(R.string.guided_scan_aim_front)
        photoCount == 1 && side == BagCaptureSide.BACK -> stringResource(R.string.guided_scan_aim_back)
        else -> stringResource(R.string.guided_scan_aim_more)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = Color.Black.copy(alpha = 0.55f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CaptureWarning(quality: BagCaptureQuality, steady: Boolean, capturing: Boolean) {
    val warning = when {
        capturing -> null
        !quality.glareOkay -> stringResource(R.string.scan_warn_glare)
        !quality.exposureOkay -> stringResource(R.string.scan_warn_lighting)
        !steady -> stringResource(R.string.guided_scan_hold_steady)
        else -> null
    }
    if (warning != null) {
        Surface(shape = MaterialTheme.shapes.small, color = Color(0xCCFFD166)) {
            Text(
                text = warning,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FramingOverlay(ready: Boolean, modifier: Modifier = Modifier) {
    val borderColor by animateColorAsState(
        targetValue = if (ready) READY_COLOR else Color.White.copy(alpha = 0.85f),
        label = "frameBorder",
    )
    val scrim = Color.Black.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val targetWidth = size.width * 0.80f
        val maxHeight = size.height * 0.62f
        val targetHeight = minOf(targetWidth * 4f / 3f, maxHeight)
        val left = (size.width - targetWidth) / 2f
        val top = (size.height - targetHeight) / 2f
        val rect = Rect(left, top, left + targetWidth, top + targetHeight)
        val corner = 28.dp.toPx()
        val cutout = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
            fillType = PathFillType.EvenOdd
        }
        drawPath(cutout, scrim)
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(rect.left, rect.top),
            size = rect.size,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun CaptureBottomBar(
    photos: List<String>,
    capturing: Boolean,
    autoEnabled: Boolean,
    onToggleAuto: () -> Unit,
    onCapture: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (photos.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                items(photos, key = { it }) { uri ->
                    CapturedThumbnail(uri = uri, onRemove = { onRemovePhoto(uri) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Skip (no photos) or Finished (>=1 photo)
            if (photos.isEmpty()) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.guided_scan_skip_photos), color = Color.White)
                }
            } else {
                Button(onClick = onFinished) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.guided_scan_finished))
                }
            }

            ShutterButton(enabled = !capturing, onClick = onCapture)

            AutoToggle(enabled = autoEnabled, onToggle = onToggleAuto)
        }
    }
}

@Composable
private fun AutoToggle(enabled: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Icon(
            Icons.Default.Bolt,
            contentDescription = null,
            tint = if (enabled) READY_COLOR else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(
                if (enabled) R.string.guided_scan_auto_on else R.string.guided_scan_auto_off,
            ),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val captureDesc = stringResource(R.string.guided_scan_capture)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.95f else 0.5f))
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = captureDesc },
    )
}

@Composable
private fun CapturedThumbnail(uri: String, onRemove: () -> Unit) {
    Box {
        BagThumbnail(
            uri = uri,
            size = 64.dp,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .size(64.dp)
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_remove_photo),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// --- Camera helpers ---

/**
 * ImageAnalysis analyzer that turns each preview frame into a [BagCaptureQuality]
 * estimate plus a frame-to-frame motion score (mean abs luma delta on a small
 * downsampled grid). Throttled; always closes the frame.
 */
private class BagFramingAnalyzer(
    private val onResult: (BagCaptureQuality, Float) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {
    private var lastProcessedMs = 0L
    private var previousGrid: ByteArray? = null

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastProcessedMs < THROTTLE_MS) return
            lastProcessedMs = now

            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height
            if (width < 2 || height < 2) return

            val gridW = minOf(PREVIEW_GRID_WIDTH, width)
            val gridH = (gridW.toLong() * height / width).toInt().coerceAtLeast(1)
            val grid = ByteArray(gridW * gridH)
            val xStep = width.toFloat() / gridW
            val yStep = height.toFloat() / gridH
            for (gy in 0 until gridH) {
                val srcY = (gy * yStep).toInt().coerceIn(0, height - 1)
                for (gx in 0 until gridW) {
                    val srcX = (gx * xStep).toInt().coerceIn(0, width - 1)
                    grid[gy * gridW + gx] = buffer.get(srcY * rowStride + srcX)
                }
            }

            val quality = BagCaptureQualityAnalyzer.analyzeLumaFrame(
                luma = grid,
                width = gridW,
                height = gridH,
            )
            val motion = previousGrid?.let { prev -> meanAbsDelta(prev, grid) } ?: Float.MAX_VALUE
            previousGrid = grid
            onResult(quality, motion)
        } catch (_: Exception) {
            // Frame-level failures are non-fatal; skip this frame.
        } finally {
            image.close()
        }
    }

    private fun meanAbsDelta(a: ByteArray, b: ByteArray): Float {
        if (a.size != b.size || a.isEmpty()) return Float.MAX_VALUE
        var sum = 0L
        for (i in a.indices) {
            sum += kotlin.math.abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
        }
        return sum.toFloat() / a.size
    }

    private companion object {
        const val THROTTLE_MS = 120L
    }
}

/** Capture a short burst and return the JPEG bytes of the sharpest frame. */
private suspend fun captureBestBurst(
    controller: LifecycleCameraController,
    executor: java.util.concurrent.Executor,
): ByteArray? {
    var best: ByteArray? = null
    var bestScore = -1f
    repeat(BURST_COUNT) {
        val bytes = captureOnce(controller, executor) ?: return@repeat
        // Decoding for the blur score is CPU work — keep it off the main thread.
        val score = withContext(Dispatchers.Default) { scoreSharpness(bytes) }
        if (score > bestScore) {
            bestScore = score
            best = bytes
        }
    }
    return best
}

private suspend fun captureOnce(
    controller: LifecycleCameraController,
    executor: java.util.concurrent.Executor,
): ByteArray? = suspendCancellableCoroutine { cont ->
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = try {
                    val buffer = image.planes[0].buffer
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                } catch (_: Exception) {
                    null
                } finally {
                    image.close()
                }
                if (cont.isActive) cont.resume(bytes)
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resume(null)
            }
        },
    )
}

private fun scoreSharpness(jpeg: ByteArray): Float {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val sourceLongEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (sourceLongEdge <= 0) return 0f
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PhotoStoragePolicy.boundedDecodeSampleSize(
                sourceLongEdgePx = sourceLongEdge,
                maxLongEdgePx = SHARPNESS_MAX_LONG_EDGE_PX,
            )
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return 0f
        try {
            BagCaptureQualityAnalyzer.analyzeBitmap(bitmap).blurScore
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    } catch (_: Exception) {
        0f
    }
}
