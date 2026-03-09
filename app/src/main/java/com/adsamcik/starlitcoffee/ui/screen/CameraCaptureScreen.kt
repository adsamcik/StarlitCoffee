package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.BagPhotoImportSupport
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val EMPTY_CAPTURE_QUALITY = BagCaptureQuality(
    blurScore = 0f,
    glarePercent = 0f,
    overexposedPercent = 0f,
    underexposedPercent = 0f,
    textBlockCount = 0,
    textDetected = false,
)

private val READY_GUIDE_COLOR = Color(0xFF5ED18D)
private val CAUTION_GUIDE_COLOR = Color(0xFFFFD166)

private data class CameraGuidanceState(
    val quality: BagCaptureQuality = EMPTY_CAPTURE_QUALITY,
    val detectedFields: OcrExtractionResult? = null,
)

@Composable
fun CameraCaptureScreen(
    brewViewModel: BrewViewModel,
    onBack: () -> Unit,
    onPhotosCaptured: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val knownFieldValues by brewViewModel.knownFieldValues.collectAsStateWithLifecycle()
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
    ) { granted ->
        hasCameraPermission = granted
    }
    var isImportingGallery by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { selectedPhotos ->
        if (selectedPhotos.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImportingGallery = true
            try {
                val importedPhotos = BagPhotoImportSupport.importPhotosToCache(
                    context = context,
                    sourceUris = selectedPhotos,
                )
                if (importedPhotos.isNotEmpty()) {
                    onPhotosCaptured(importedPhotos.joinToString(","))
                }
            } finally {
                isImportingGallery = false
            }
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraCaptureContent(
            knownFieldValues = knownFieldValues,
            onBack = onBack,
            onPhotosCaptured = onPhotosCaptured,
            onUseGallery = { galleryLauncher.launch("image/*") },
            isImportingGallery = isImportingGallery,
        )
    } else {
        PermissionRequestContent(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onChooseFromGallery = { galleryLauncher.launch("image/*") },
            isImportingGallery = isImportingGallery,
            onBack = onBack,
        )
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    onChooseFromGallery: () -> Unit,
    isImportingGallery: Boolean,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera permission needed for live capture",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You can still import bag photos from your gallery instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Camera Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onChooseFromGallery,
                enabled = !isImportingGallery,
            ) {
                Text(
                    text = if (isImportingGallery) {
                        "Importing gallery photos..."
                    } else {
                        "Choose from gallery instead"
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraCaptureContent(
    knownFieldValues: KnownFieldValues,
    onBack: () -> Unit,
    onPhotosCaptured: (String) -> Unit,
    onUseGallery: () -> Unit,
    isImportingGallery: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val capturedPhotos = remember { mutableStateListOf<Uri>() }
    var isCapturing by remember { mutableStateOf(false) }
    var motionStable by remember { mutableStateOf(false) }
    var autoCaptureProgress by remember { mutableStateOf(0f) }
    val guidanceFlow = remember { MutableStateFlow(CameraGuidanceState()) }
    val guidance by guidanceFlow.collectAsState()

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            bindToLifecycle(lifecycleOwner)
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val liveRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val analyzer = remember {
        LiveCaptureAnalyzer(
            recognizer = liveRecognizer,
            onGuidanceUpdate = { state -> guidanceFlow.value = state },
        )
    }

    // Update known field values without recreating the analyzer
    LaunchedEffect(knownFieldValues) {
        analyzer.currentKnownFieldValues = knownFieldValues
    }

    DisposableEffect(cameraController, analysisExecutor, analyzer) {
        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdown()
            liveRecognizer.close()
        }
    }

    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    DisposableEffect(sensorManager) {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            onDispose { }
        } else {
            var lastValues: FloatArray? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val current = event.values.copyOf()
                    val previous = lastValues
                    if (previous != null) {
                        val delta = kotlin.math.abs(current[0] - previous[0]) +
                            kotlin.math.abs(current[1] - previous[1]) +
                            kotlin.math.abs(current[2] - previous[2])
                        motionStable = delta < 0.35f
                    }
                    lastValues = current
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val step = capturedPhotos.size + 1
    val backOptional = capturedPhotos.isNotEmpty()
    val stepLabel = if (backOptional) "Step 2 of 2 optional" else "Step 1 of 2"
    val guideText = if (backOptional) {
        "Back of bag - optional for notes, roast date, and barcode"
    } else {
        "Front of bag - name, roaster, origin"
    }
    val isBusy = isCapturing || isImportingGallery
    val captureArmed = guidance.quality.readyForCapture && motionStable && !isBusy
    val guideColor = when {
        guidance.quality.readyForCapture -> READY_GUIDE_COLOR
        guidance.quality.textDetected -> CAUTION_GUIDE_COLOR
        else -> Color.White.copy(alpha = 0.7f)
    }
    // TODO: Phase 2 — pass live OCR result to BrewViewModel before navigating
    val completeCapture: (String) -> Unit = { photoUris ->
        onPhotosCaptured(photoUris)
    }

    LaunchedEffect(captureArmed, capturedPhotos.size) {
        autoCaptureProgress = 0f
        if (!captureArmed) return@LaunchedEffect
        repeat(12) { index ->
            delay(75L)
            if (!(guidance.quality.readyForCapture && motionStable && !isCapturing)) {
                autoCaptureProgress = 0f
                return@LaunchedEffect
            }
            autoCaptureProgress = (index + 1) / 12f
        }
        triggerBurstCapture(
            context = context,
            cameraController = cameraController,
            isCapturing = isCapturing,
            onCapturingChanged = { isCapturing = it },
            onPhotoChosen = { uri ->
                capturedPhotos.add(uri)
                if (capturedPhotos.size >= 2) {
                    completeCapture(capturedPhotos.joinToString(","))
                }
            },
            scope = scope,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.88f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = guideText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            CornerBracketGuide(
                color = guideColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GuidanceBadge(
                    modifier = Modifier.weight(1f),
                    label = "Sharp",
                    ok = guidance.quality.sharpEnough,
                    readyText = "Ready",
                    blockedText = "Stabilizing…",
                )
                GuidanceBadge(
                    modifier = Modifier.weight(1f),
                    label = "Light",
                    ok = guidance.quality.glareOkay && guidance.quality.exposureOkay,
                    readyText = "Good",
                    blockedText = "Fix glare",
                )
                GuidanceBadge(
                    modifier = Modifier.weight(1f),
                    label = "Text",
                    ok = guidance.quality.textDetected,
                    readyText = "Found",
                    blockedText = "Reframe",
                )
            }

            DetectedFieldChips(
                detectedFields = guidance.detectedFields,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            if (guidance.quality.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = guidance.quality.issues.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (capturedPhotos.isNotEmpty()) {
                // Step 2: front captured, back optional
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    val bitmap = remember(capturedPhotos.size) {
                        val path = capturedPhotos.last().path ?: return@remember null
                        val raw = BitmapFactory.decodeFile(path) ?: return@remember null
                        ImagePreprocessor.applyExifRotation(raw, path)
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Selected photo",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Front captured ✓",
                            style = MaterialTheme.typography.bodyMedium,
                            color = READY_GUIDE_COLOR,
                        )
                        Text(
                            text = "Add back photo or continue with front only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }

                // Primary action on Step 2: skip back
                Button(
                    onClick = { completeCapture(capturedPhotos.joinToString(",")) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(bottom = 12.dp),
                ) {
                    Text("Use front only →")
                }
            }

            if (captureArmed) {
                Text(
                    text = "Auto-capture armed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = READY_GUIDE_COLOR,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Box(contentAlignment = Alignment.Center) {
                if (autoCaptureProgress > 0f && !isCapturing) {
                    CircularProgressIndicator(
                        progress = { autoCaptureProgress },
                        modifier = Modifier.size(88.dp),
                        color = READY_GUIDE_COLOR,
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                }
                IconButton(
                    onClick = {
                        triggerBurstCapture(
                            context = context,
                            cameraController = cameraController,
                            isCapturing = isCapturing,
                            onCapturingChanged = { isCapturing = it },
                            onPhotoChosen = { uri ->
                                capturedPhotos.add(uri)
                                if (capturedPhotos.size >= 2) {
                                    completeCapture(capturedPhotos.joinToString(","))
                                }
                            },
                            scope = scope,
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = Color.White,
                            shape = CircleShape,
                        ),
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Capture photo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (backOptional) "Capture back of bag" else "Burst x3 — keeps the sharpest shot",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )

            if (!backOptional) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUseGallery,
                    enabled = !isBusy,
                ) {
                    Text(
                        text = if (isImportingGallery) {
                            "Importing gallery photos..."
                        } else {
                            "Use gallery instead"
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { completeCapture("skipped") }) {
                Text(
                    text = if (capturedPhotos.isEmpty()) "Add manually instead" else "Discard photos and add manually",
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun CornerBracketGuide(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val bracketLen = 28.dp.toPx()
        val cornerRadius = 6.dp.toPx()

        // Top-left
        drawLine(color, Offset(0f, cornerRadius), Offset(0f, bracketLen), strokeWidth = strokeWidth)
        drawLine(color, Offset(cornerRadius, 0f), Offset(bracketLen, 0f), strokeWidth = strokeWidth)

        // Top-right
        drawLine(color, Offset(size.width, cornerRadius), Offset(size.width, bracketLen), strokeWidth = strokeWidth)
        drawLine(color, Offset(size.width - cornerRadius, 0f), Offset(size.width - bracketLen, 0f), strokeWidth = strokeWidth)

        // Bottom-left
        drawLine(color, Offset(0f, size.height - cornerRadius), Offset(0f, size.height - bracketLen), strokeWidth = strokeWidth)
        drawLine(color, Offset(cornerRadius, size.height), Offset(bracketLen, size.height), strokeWidth = strokeWidth)

        // Bottom-right
        drawLine(color, Offset(size.width, size.height - cornerRadius), Offset(size.width, size.height - bracketLen), strokeWidth = strokeWidth)
        drawLine(color, Offset(size.width - cornerRadius, size.height), Offset(size.width - bracketLen, size.height), strokeWidth = strokeWidth)
    }
}

@Composable
private fun GuidanceBadge(
    label: String,
    ok: Boolean,
    readyText: String,
    blockedText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = if (ok) readyText else blockedText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) READY_GUIDE_COLOR else CAUTION_GUIDE_COLOR,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedFieldChips(
    detectedFields: OcrExtractionResult?,
    modifier: Modifier = Modifier,
) {
    val chipItems = remember(detectedFields) {
        listOfNotNull(
            detectedFields?.origin?.let { DetectedFieldChipItem("🌍", "origin", it) },
            detectedFields?.region?.let { DetectedFieldChipItem("🗺️", "region", it) },
            detectedFields?.processType?.let { DetectedFieldChipItem("☕", "process", it) },
            detectedFields?.variety?.let { DetectedFieldChipItem("🫘", "variety", it) },
            detectedFields?.farm?.let { DetectedFieldChipItem("🚜", "farm", it) },
            detectedFields?.roaster?.let { DetectedFieldChipItem("🔥", "roaster", it) },
            detectedFields?.name?.let { DetectedFieldChipItem("🏷️", "name", it) },
            detectedFields?.altitude?.let { DetectedFieldChipItem("⛰️", "altitude", it) },
            detectedFields?.tastingNotes?.let { DetectedFieldChipItem("🍓", "notes", it) },
            detectedFields?.roastLevel?.let { DetectedFieldChipItem("🌗", "roast", it) },
            detectedFields?.roastDate?.let { DetectedFieldChipItem("📅", "date", it) },
            detectedFields?.expiryDate?.let { DetectedFieldChipItem("⏳", "expiry", it) },
            detectedFields?.weight?.let { DetectedFieldChipItem("⚖️", "weight", it) },
        )
    }

    // Track which field keys have been seen so AnimatedVisibility can animate entrance
    var visibleKeys by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(chipItems) {
        visibleKeys = chipItems.map { it.key }.toSet()
    }

    if (chipItems.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chipItems.forEach { chip ->
            key(chip.key) {
                AnimatedVisibility(
                    visible = chip.key in visibleKeys,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.42f),
                    ) {
                        Text(
                            text = "${chip.emoji} ${chip.value}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class DetectedFieldChipItem(
    val emoji: String,
    val key: String,
    val value: String,
)

private fun triggerBurstCapture(
    context: Context,
    cameraController: LifecycleCameraController,
    isCapturing: Boolean,
    onCapturingChanged: (Boolean) -> Unit,
    onPhotoChosen: (Uri) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (isCapturing) return
    scope.launch {
        onCapturingChanged(true)
        val selectedPhoto = captureBestShot(context, cameraController)
        onCapturingChanged(false)
        if (selectedPhoto != null) {
            onPhotoChosen(selectedPhoto)
        }
    }
}

private suspend fun captureBestShot(
    context: Context,
    cameraController: LifecycleCameraController,
): Uri? {
    val shots = mutableListOf<Pair<Uri, Float>>()
    repeat(3) {
        val uri = awaitCapturedPhoto(context, cameraController) ?: return@repeat
        val path = uri.path ?: return@repeat
        val raw = BitmapFactory.decodeFile(path) ?: return@repeat
        val rotated = ImagePreprocessor.applyExifRotation(raw, path)
        val blurScore = BagCaptureQualityAnalyzer.analyzeBitmap(rotated).blurScore
        shots += uri to blurScore
    }

    val selected = shots.maxByOrNull { it.second } ?: return null
    shots
        .filter { it.first != selected.first }
        .forEach { (uri, _) ->
            val path = uri.path ?: return@forEach
            File(path).delete()
        }
    return selected.first
}

private suspend fun awaitCapturedPhoto(
    context: Context,
    cameraController: LifecycleCameraController,
): Uri? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val photoFile = File(
        context.cacheDir,
        "coffee_label_${System.currentTimeMillis()}.jpg",
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    cameraController.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                continuation.resume(photoFile.toUri(), null)
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resume(null, null)
            }
        },
    )
}

private class LiveCaptureAnalyzer(
    private val recognizer: com.google.mlkit.vision.text.TextRecognizer,
    private val onGuidanceUpdate: (CameraGuidanceState) -> Unit,
) : ImageAnalysis.Analyzer {
    @Volatile var currentKnownFieldValues: KnownFieldValues = KnownFieldValues.EMPTY
    @Volatile private var lastTextCheckAtMs = 0L
    @Volatile private var textDetectionInFlight = false
    @Volatile private var lastTextBlockCount = 0
    @Volatile private var lastTextDetected = false
    @Volatile private var lastDetectedFields: OcrExtractionResult? = null

    override fun analyze(image: ImageProxy) {
        val luma = extractLumaBytes(image)
        if (luma == null) {
            image.close()
            return
        }

        val baseQuality = BagCaptureQualityAnalyzer.analyzeLumaFrame(
            luma = luma,
            width = image.width,
            height = image.height,
            textBlockCount = lastTextBlockCount,
            textDetected = lastTextDetected,
        )
        val shouldRunTextCheck = !textDetectionInFlight &&
            (SystemClock.elapsedRealtime() - lastTextCheckAtMs) >= 700L

        if (shouldRunTextCheck && image.image != null) {
            textDetectionInFlight = true
            lastTextCheckAtMs = SystemClock.elapsedRealtime()
            recognizer.process(
                InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees),
            )
                .addOnSuccessListener { text ->
                    lastTextBlockCount = text.textBlocks.size
                    lastTextDetected = text.textBlocks.isNotEmpty()
                    lastDetectedFields = OcrFieldExtractor.extractFields(text.text, currentKnownFieldValues)
                        .takeIf { it.hasDetectedValue() }
                    onGuidanceUpdate(
                        CameraGuidanceState(
                            quality = baseQuality.copy(
                                textBlockCount = lastTextBlockCount,
                                textDetected = lastTextDetected,
                            ),
                            detectedFields = lastDetectedFields,
                        ),
                    )
                }
                .addOnFailureListener {
                    lastTextBlockCount = 0
                    lastTextDetected = false
                    lastDetectedFields = null
                    onGuidanceUpdate(
                        CameraGuidanceState(
                            quality = baseQuality.copy(
                                textBlockCount = 0,
                                textDetected = false,
                            ),
                            detectedFields = lastDetectedFields,
                        ),
                    )
                }
                .addOnCompleteListener {
                    textDetectionInFlight = false
                    image.close()
                }
            return
        }

        onGuidanceUpdate(
            CameraGuidanceState(
                quality = baseQuality,
                detectedFields = lastDetectedFields,
            ),
        )
        image.close()
    }

    private fun extractLumaBytes(image: ImageProxy): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val bytes = ByteArray(width * height)

        val rowBuffer = ByteArray(buffer.remaining())
        buffer.get(rowBuffer)
        var targetIndex = 0

        for (row in 0 until height) {
            val rowOffset = row * rowStride
            for (column in 0 until width) {
                val sourceIndex = rowOffset + (column * pixelStride)
                if (sourceIndex >= rowBuffer.size) return null
                bytes[targetIndex++] = rowBuffer[sourceIndex]
            }
        }

        return bytes
    }
}

private fun OcrExtractionResult.hasDetectedValue(): Boolean = listOf(
    name,
    roaster,
    origin,
    region,
    farm,
    variety,
    processType,
    altitude,
    tastingNotes,
    roastLevel,
    roastDate,
    expiryDate,
    weight,
).any { !it.isNullOrBlank() }
