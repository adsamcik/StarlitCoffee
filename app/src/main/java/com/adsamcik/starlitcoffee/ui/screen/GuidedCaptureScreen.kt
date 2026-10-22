package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.PhotoStoragePolicy
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val STABILIZE_MS = 700L
private const val CODE_STABILIZE_MS = 250L
private const val MOTION_STEADY = 7f
private const val MOTION_MOVED = 16f
private const val BURST_COUNT = 3
private const val SHARPNESS_MAX_LONG_EDGE_PX = 512

private object GuidedCaptureTraceboxTemplates {
    val CAMERA_UNAVAILABLE = LogTemplate.of("Coffee scan camera unavailable")
    val FAILED_TO_CAPTURE = LogTemplate.of("Failed to capture coffee photo")
    val FAILED_TO_IMPORT = LogTemplate.of("Failed to import coffee scan photos")
    val FAILED_TO_RELEASE_CAMERA = LogTemplate.of("Failed to release coffee scan camera")
}

/** One camera for coffee labels, product codes and QR links, with import available throughout. */
@Composable
fun GuidedCaptureScreen(
    captureViewModel: BagScanCaptureViewModel,
    onBack: () -> Unit,
    onBarcodeScanned: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val scope = rememberCoroutineScope()
    val state by captureViewModel.uiState.collectAsStateWithLifecycle()
    val currentBarcodeCallback by rememberUpdatedState(onBarcodeScanned)
    val choosingCoffee = onBarcodeScanned != null
    var hasCameraPermission by remember { mutableStateOf(context.canUseScanCamera()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permissionResponses by rememberSaveable { mutableIntStateOf(0) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resultDelivered by remember { mutableStateOf(false) }
    var acceptingResults by remember { mutableStateOf(true) }
    var autoEnabled by rememberSaveable { mutableStateOf(true) }
    var errorResId by remember { mutableStateOf<Int?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var photoPendingRemoval by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
        permissionResponses++
    }
    DisposableEffect(Unit) {
        onDispose { acceptingResults = false }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = context.canUseScanCamera()
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun deliverCode(codes: List<ScannedCoffeeCode>, fromPreview: Boolean = false): Boolean {
        val code = selectCoffeeCode(codes) ?: return false
        val requiredState = if (fromPreview) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
        if (!acceptingResults || resultDelivered || !lifecycleOwner.lifecycle.currentState.isAtLeast(requiredState)) return false
        resultDelivered = true
        acceptingResults = false
        currentBarcodeCallback?.invoke(code.value)
        return true
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 2)) { uris ->
        if (uris.isEmpty()) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        if (!acceptingResults) return@rememberLauncherForActivityResult
        busy = true
        errorResId = null
        scope.launch {
            var pendingUris = emptyList<String>()
            try {
                withContext(Dispatchers.IO) {
                    pendingUris = ScanPhotoStorage.copyPhotosToCache(context.applicationContext, uris.map { it.toString() }).orEmpty()
                }
                if (!acceptingResults || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                if (pendingUris.isEmpty()) {
                    errorResId = R.string.msg_could_not_read_label
                } else if (choosingCoffee) {
                    val codes = pendingUris.flatMap { readCoffeeCodes(context, it) }
                    if (!deliverCode(codes)) errorResId = R.string.msg_scan_code_not_found
                } else {
                    pendingUris.forEach(captureViewModel::addPhoto)
                    pendingUris = emptyList() // The durable draft now owns these staged files.
                    acceptingResults = false
                    captureViewModel.finishCapturing()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Tracebox.log.error(error, GuidedCaptureTraceboxTemplates.FAILED_TO_IMPORT)
                errorResId = R.string.msg_could_not_read_label
            } finally {
                deleteUnclaimedScanPhotos(context, pendingUris)
                busy = false
            }
        }
    }
    val importPhotos = {
        if (acceptingResults && !busy) {
            busy = true
            errorResId = null
            try {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (error: Exception) {
                Tracebox.log.error(error, GuidedCaptureTraceboxTemplates.FAILED_TO_IMPORT)
                errorResId = R.string.msg_could_not_read_label
                busy = false
            }
        }
    }
    fun leaveScan() {
        acceptingResults = false
        onBack()
    }
    fun reviewPhotos() {
        acceptingResults = false
        captureViewModel.finishCapturing()
    }
    fun handleBack() {
        if (state.hasPhotos) showDiscardDialog = true else leaveScan()
    }
    BackHandler { handleBack() }
    if (showDiscardDialog) AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text(stringResource(R.string.dialog_discard_scan_title)) },
        text = { Text(stringResource(R.string.msg_discard_scan_body)) },
        confirmButton = {
            TextButton(onClick = { showDiscardDialog = false; leaveScan() }) { Text(stringResource(R.string.action_discard)) }
        },
        dismissButton = {
            TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_keep_scanning)) }
        },
    )
    photoPendingRemoval?.let { uri ->
        DestructiveActionDialog(
            titleRes = R.string.cd_remove_photo,
            confirmLabelRes = R.string.cd_remove_photo,
            onConfirm = {
                captureViewModel.removePhoto(uri)
                scope.launch(Dispatchers.IO) { ScanPhotoStorage.deleteStagedCapture(context.applicationContext, uri) }
                photoPendingRemoval = null
            },
            onDismiss = { photoPendingRemoval = null },
        )
    }

    if (!hasCameraPermission || cameraUnavailable) {
        val activity = context.scanActivity()
        val needsSettings = permissionResponses > 0 && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        CoffeeScanFallback(
            cameraUnavailable = cameraUnavailable,
            enableCameraLabelResId = when {
                cameraUnavailable -> R.string.action_retry_camera
                needsSettings -> R.string.action_open_app_settings
                else -> R.string.action_allow_camera
            },
            busy = busy,
            errorResId = errorResId,
            onBack = ::handleBack,
            onEnableCamera = {
                when {
                    cameraUnavailable -> { cameraUnavailable = false; cameraAttempt++ }
                    needsSettings -> context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                    else -> { permissionRequested = true; permissionLauncher.launch(Manifest.permission.CAMERA) }
                }
            },
            onImport = importPhotos,
            onManualEntry = if (choosingCoffee) null else ::reviewPhotos,
        )
        return
    }

    val controller = remember(context, lifecycleOwner, cameraAttempt) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
        }
    }
    var cameraReady by remember(controller) { mutableStateOf(false) }
    var quality by remember(controller) { mutableStateOf(BagCaptureQuality(0f, 0f, 0f, 0f, 0, false)) }
    var motion by remember(controller) { mutableFloatStateOf(Float.MAX_VALUE) }
    var detectedCode by remember(controller) { mutableStateOf<ScannedCoffeeCode?>(null) }
    var autoArmed by remember(controller) { mutableStateOf(true) }

    DisposableEffect(controller, lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = CoffeeScanAnalyzer(
            mainExecutor = mainExecutor,
            onFraming = { current, movement ->
                cameraReady = true
                quality = current
                motion = movement
                if (!busy && movement > MOTION_MOVED) autoArmed = true
            },
            onCode = { code ->
                detectedCode = code
                if (choosingCoffee && !busy && code != null) deliverCode(listOf(code), fromPreview = true)
            },
        )
        var active = true
        val initialized = controller.initializationFuture
        initialized.addListener({
            if (active) {
                try {
                    initialized.get()
                    controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
                    controller.bindToLifecycle(lifecycleOwner)
                } catch (error: Exception) {
                    Tracebox.log.error(error, GuidedCaptureTraceboxTemplates.CAMERA_UNAVAILABLE)
                    cameraUnavailable = true
                }
            }
        }, mainExecutor)
        onDispose {
            active = false
            runCatching { controller.clearImageAnalysisAnalyzer() }
                .onFailure { Tracebox.log.error(it, GuidedCaptureTraceboxTemplates.FAILED_TO_RELEASE_CAMERA) }
            runCatching { controller.unbind() }
                .onFailure { Tracebox.log.error(it, GuidedCaptureTraceboxTemplates.FAILED_TO_RELEASE_CAMERA) }
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    fun capturePhoto() {
        if (!acceptingResults || busy || !cameraReady) return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        busy = true
        errorResId = null
        autoArmed = false
        scope.launch {
            var pendingUri: String? = null
            try {
                val bytes = captureBestBurst(controller, ContextCompat.getMainExecutor(context))
                if (bytes != null) withContext(Dispatchers.IO) {
                    pendingUri = ScanPhotoStorage.writeCaptureToCache(context.applicationContext, bytes)
                }
                val photoUri = pendingUri
                if (!acceptingResults || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                when {
                    photoUri == null -> errorResId = R.string.msg_scan_capture_failed
                    choosingCoffee -> {
                        if (!deliverCode(readCoffeeCodes(context, photoUri))) errorResId = R.string.msg_scan_code_not_found
                    }
                    else -> {
                        captureViewModel.addPhoto(photoUri)
                        pendingUri = null // Ownership moves to the existing capture session.
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Tracebox.log.error(error, GuidedCaptureTraceboxTemplates.FAILED_TO_CAPTURE)
                errorResId = R.string.msg_scan_capture_failed
            } finally {
                deleteUnclaimedScanPhotos(context, listOfNotNull(pendingUri))
                busy = false
            }
        }
    }

    val frameSteady = motion < MOTION_STEADY
    val frameReady = quality.glareOkay && quality.exposureOkay && frameSteady
    val autoGate = acceptingResults && !choosingCoffee && autoEnabled && autoArmed && !busy && frameReady &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !showDiscardDialog && photoPendingRemoval == null
    LaunchedEffect(autoGate, detectedCode) {
        if (autoGate) {
            delay(if (detectedCode != null) CODE_STABILIZE_MS else STABILIZE_MS)
            capturePhoto()
        }
    }
    val guidanceResId = when {
        choosingCoffee -> R.string.msg_point_camera
        !cameraReady -> R.string.msg_scan_coffee_aim
        !quality.glareOkay -> R.string.scan_warn_glare
        !quality.exposureOkay -> R.string.scan_warn_lighting
        !frameSteady -> R.string.guided_scan_hold_steady
        state.hasPhotos -> R.string.guided_scan_aim_more
        else -> R.string.msg_scan_coffee_aim
    }
    CoffeeScanContent(
        photos = state.photos.map { it.uri },
        choosingCoffee = choosingCoffee,
        cameraReady = cameraReady,
        frameReady = frameReady,
        autoEnabled = autoEnabled,
        busy = busy,
        guidanceResId = guidanceResId,
        errorResId = errorResId,
        onBack = ::handleBack,
        onImport = importPhotos,
        onCapture = ::capturePhoto,
        onToggleAuto = { autoEnabled = !autoEnabled },
        onRemovePhoto = { photoPendingRemoval = it },
        onReview = ::reviewPhotos,
    ) {
        AndroidView(
            factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun Context.canUseScanCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.scanActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.scanActivity()
    else -> null
}

private suspend fun deleteUnclaimedScanPhotos(context: Context, uris: List<String>) {
    if (uris.isEmpty()) return
    withContext(NonCancellable + Dispatchers.IO) {
        uris.forEach { ScanPhotoStorage.deleteStagedCapture(context.applicationContext, it) }
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
