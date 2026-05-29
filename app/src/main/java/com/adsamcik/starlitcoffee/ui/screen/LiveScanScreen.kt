package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.scan.LiveScanAnalyzer
import com.adsamcik.starlitcoffee.scan.LiveScanAnalyzerCallbacks
import com.adsamcik.starlitcoffee.scan.model.AccumulatedEvidence
import com.adsamcik.starlitcoffee.scan.model.FieldAccumulation
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.GuidanceType
import com.adsamcik.starlitcoffee.scan.model.ScanGuidance
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.adsamcik.starlitcoffee.util.ScanFieldSupport
import com.adsamcik.starlitcoffee.BuildConfig
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.component.ScanDebugOverlay
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CrossValidationWarning
import com.adsamcik.starlitcoffee.viewmodel.LiveScanViewModel
import com.adsamcik.starlitcoffee.viewmodel.LlmUiStatus
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

private val READY_COLOR = Color(0xFF5ED18D)
private val CAUTION_COLOR = Color(0xFFFFD166)

/**
 * Live scan screen: continuous OCR accumulation with real-time field materialization.
 *
 * Replaces the old 2-step CameraCaptureScreen. Fields appear as ghosts, solidify
 * as the accumulator builds confidence, and the user saves when ready.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveScanScreen(
    liveScanViewModel: LiveScanViewModel,
    brewViewModel: BrewViewModel,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    onNavigateToReview: (Map<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val evidence by liveScanViewModel.evidence.collectAsStateWithLifecycle()
    val uiState by liveScanViewModel.liveScanUiState.collectAsStateWithLifecycle()
    val perfStats by liveScanViewModel.perfStats.collectAsStateWithLifecycle()
    val knownFieldValues by brewViewModel.knownFieldValues.collectAsStateWithLifecycle()
    val crossValidationWarning by liveScanViewModel.crossValidationWarning.collectAsStateWithLifecycle()

    // --- Camera Permission ---

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
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // --- Camera Setup ---

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            bindToLifecycle(lifecycleOwner)
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    // --- Start accumulator ---

    LaunchedEffect(Unit) {
        liveScanViewModel.start(knownFieldValues)
    }

    // --- Wire analyzer to accumulator ---

    var latestOcrResult by remember {
        mutableStateOf(OcrFieldExtractor.OcrExtractionResult())
    }
    var latestQuality by remember {
        mutableStateOf(
            BagCaptureQuality(0f, 0f, 0f, 0f, 0, false),
        )
    }
    var analyzerRef by remember { mutableStateOf<LiveScanAnalyzer?>(null) }

    DisposableEffect(cameraController, analysisExecutor) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val callbacks = LiveScanAnalyzerCallbacks(
            onRawFrame = { quality, lumaGrid ->
                liveScanViewModel.onRawFrame(quality, lumaGrid)
            },
            onOcrResult = { ocrResult, quality, lumaGrid ->
                // ViewModel methods are thread-safe (StateFlow)
                liveScanViewModel.onOcrResult(ocrResult, quality, lumaGrid)
                // Compose state must be written on main thread
                mainHandler.post {
                    latestOcrResult = ocrResult
                    if (quality.textDetected) latestQuality = quality
                }
            },
            onGoldenFrameCapture = { jpegBytes, quality, ocrResult ->
                liveScanViewModel.onGoldenFrameCapture(jpegBytes, quality, ocrResult)
            },
            onBarcodeDetected = { barcode ->
                liveScanViewModel.onBarcodeDetected(barcode)
            },
            onBlankFrame = {
                liveScanViewModel.onBlankFrame()
            },
        )

        val analyzer = LiveScanAnalyzer(
            recognizer = textRecognizer,
            barcodeScanner = barcodeScanner,
            knownFieldValues = knownFieldValues,
            callbacks = callbacks,
            mlKitExecutor = analysisExecutor,
            throttleMsProvider = { liveScanViewModel.currentThrottleMs.value },
            perfTracer = liveScanViewModel.perfTracer,
        )
        analyzerRef = analyzer

        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)

        onDispose {
            analyzerRef = null
            cameraController.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdown()
            textRecognizer.close()
            barcodeScanner.close()
            liveScanViewModel.stop()
        }
    }

    // --- Haptic feedback on field lock ---

    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var previousLockedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(evidence) {
        val currentLocked = evidence.fields.values.count {
            it.status == FieldStatus.LOCKED || it.status == FieldStatus.USER_LOCKED
        }
        if (currentLocked > previousLockedCount && previousLockedCount > 0) {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        previousLockedCount = currentLocked
    }

    // --- Discard confirmation on back ---

    var showDiscardDialog by remember { mutableStateOf(false) }
    val hasAnyEvidence = evidence.fields.isNotEmpty()

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_discard_scan_title)) },
            text = { Text(stringResource(R.string.msg_discard_scan_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_scanning))
                }
            },
        )
    }

    // --- Debug overlay toggle ---

    var showDebugOverlay by remember { mutableStateOf(false) }

    // --- Main Layout ---

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.msg_camera_permission_required), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar (with discard-guarded back)
        TopBar(
            onBack = {
                if (hasAnyEvidence) showDiscardDialog = true else onBack()
            },
            sideCount = evidence.sideCount,
            sideFlipDetected = uiState.sideFlipDetected,
            onToggleDebug = { showDebugOverlay = !showDebugOverlay },
        )

        // Barcode detection chip
        val barcodeMessage = uiState.barcodeDetectedMessage
        var showBarcodeChip by remember { mutableStateOf(false) }

        LaunchedEffect(barcodeMessage) {
            if (barcodeMessage != null) {
                showBarcodeChip = true
                delay(3000)
                showBarcodeChip = false
            }
        }

        AnimatedVisibility(
            visible = showBarcodeChip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = READY_COLOR.copy(alpha = 0.9f),
                modifier = Modifier.clickable { showBarcodeChip = false },
            ) {
                Text(
                    text = barcodeMessage ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Cross-validation warning banner
        CrossValidationBanner(
            warning = crossValidationWarning,
            onUseBarcode = { liveScanViewModel.resolveCrossValidation(useBarcode = true) },
            onUseLabel = { liveScanViewModel.resolveCrossValidation(useBarcode = false) },
            onDismiss = liveScanViewModel::dismissCrossValidation,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp, start = 12.dp, end = 12.dp),
        )

        // Debug overlay
        if (showDebugOverlay) {
            ScanDebugOverlay(
                perfStats = perfStats,
                evidence = evidence,
                debugInfo = liveScanViewModel.debugInfo(),
                rejectionReason = uiState.lastRejectionReason,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 8.dp),
            )
        }

        // LLM status chip — always visible, compact
        LlmStatusChip(
            status = uiState.llmStatus,
            contributedFields = uiState.llmContributedFields,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 56.dp),
        )

        // Manual capture fallback button (center-right)
        ManualCaptureButton(
            onClick = {
                // Start a burst capture — collect ~5 frames, pick sharpest
                analyzerRef?.startBurst { bestJpeg, quality ->
                    liveScanViewModel.onManualCapture(
                        jpegBytes = bestJpeg,
                        quality = quality,
                        ocrResult = latestOcrResult,
                    )
                } ?: run {
                    // Fallback if analyzer not available yet
                    liveScanViewModel.forceCapture(latestOcrResult, latestQuality)
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )

        // Bottom overlay with field chips, progress, guidance
        BottomOverlay(
            evidence = evidence,
            guidance = evidence.guidance,
            isDraftReady = liveScanViewModel.isDraftReady(),
            llmContributedFields = uiState.llmContributedFields,
            onResolveConflict = liveScanViewModel::resolveConflict,
            onResetField = liveScanViewModel::resetField,
            onQuickSave = {
                val fields = liveScanViewModel.buildResolvedFields()
                val draft = ScanFieldSupport.buildDraft(fields) ?: return@BottomOverlay
                brewViewModel.addCoffeeBag(
                    BrewViewModel.CoffeeBagInput(
                        name = draft.name,
                        roaster = draft.roaster,
                        origin = draft.origin,
                        region = draft.region,
                        variety = draft.variety,
                        processType = draft.processType,
                        tastingNotes = draft.tastingNotes,
                        roastLevel = draft.roastLevel,
                        roastDate = draft.roastDateMillis,
                        expiryDate = draft.expiryDateMillis,
                        barcode = evidence.detectedBarcode,
                        weightG = draft.weightG,
                        isDecaf = draft.isDecaf,
                        traceabilityUrl = evidence.detectedQrUrl,
                    ),
                )
                onSaveComplete()
            },
            onReviewFirst = {
                onNavigateToReview(liveScanViewModel.buildResolvedFields())
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// --- Top Bar ---

@Composable
private fun TopBar(
    onBack: () -> Unit,
    sideCount: Int,
    sideFlipDetected: Boolean,
    onToggleDebug: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 8.dp),
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
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )

        val bothSidesScannedDesc = stringResource(R.string.msg_both_sides_scanned)
        val scanningFrontSideDesc = stringResource(R.string.msg_scanning_front_side)

        // Debug toggle (debug builds only)
        if (BuildConfig.DEBUG) {
            IconButton(
                onClick = onToggleDebug,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = stringResource(R.string.cd_toggle_debug_overlay),
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(visible = sideFlipDetected) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = READY_COLOR.copy(alpha = 0.9f),
                modifier = Modifier.semantics {
                    contentDescription = bothSidesScannedDesc
                },
            ) {
                Text(
                    text = "✓ Both sides",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (!sideFlipDetected && sideCount == 1) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.semantics {
                    contentDescription = scanningFrontSideDesc
                },
            ) {
                Text(
                    text = "Front",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// --- Bottom Overlay ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomOverlay(
    evidence: AccumulatedEvidence,
    guidance: ScanGuidance?,
    isDraftReady: Boolean,
    llmContributedFields: Set<String>,
    onResolveConflict: (String, String) -> Unit,
    onResetField: (String) -> Unit,
    onQuickSave: () -> Unit,
    onReviewFirst: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.65f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Progress bar
        val progress by animateFloatAsState(
            targetValue = evidence.scanProgress,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "progress",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = READY_COLOR,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${evidence.resolvedFieldCount}/${evidence.fields.size.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Field chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
        ) {
            evidence.fields.values
                .sortedBy { if (it.isResolved) 0 else 1 }
                .take(6)
                .forEach { field ->
                    FieldChip(
                        field = field,
                        isLlmContributed = field.fieldName in llmContributedFields,
                        onResolveConflict = onResolveConflict,
                        onReset = onResetField,
                    )
                }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Guidance message
        guidance?.let { g ->
            GuidanceBar(guidance = g)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Save buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Quick Save — only when enough fields resolved
            AnimatedVisibility(
                visible = isDraftReady,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    onClick = onQuickSave,
                    shape = MaterialTheme.shapes.small,
                    color = READY_COLOR,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_quick_save), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Review — always visible so user can continue with partial results
            Surface(
                onClick = onReviewFirst,
                shape = MaterialTheme.shapes.small,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isDraftReady) "Review First" else "Continue manually",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// --- Cross-Validation Warning Banner ---

@Composable
private fun CrossValidationBanner(
    warning: CrossValidationWarning?,
    onUseBarcode: () -> Unit,
    onUseLabel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = warning != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        warning?.let { w ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = CAUTION_COLOR,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text(
                        text = "⚠️ Barcode suggests \"${w.barcodeValue}\" but label reads \"${w.ocrValue}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        text = stringResource(R.string.msg_repackaged_product),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Surface(
                            onClick = onUseBarcode,
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color.Black.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = stringResource(R.string.action_use_barcode),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Surface(
                            onClick = onUseLabel,
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color.Black.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = stringResource(R.string.action_use_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Surface(
                            onClick = onDismiss,
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color.Black.copy(alpha = 0.08f),
                        ) {
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Field Chip (3-state: ghost → candidate → confirmed) ---

@Composable
private fun FieldChip(
    field: FieldAccumulation,
    isLlmContributed: Boolean,
    onResolveConflict: (String, String) -> Unit,
    onReset: (String) -> Unit,
) {
    if (field.status == FieldStatus.CONFLICT) {
        ConflictChips(field = field, onResolve = onResolveConflict)
        return
    }

    val chipAlpha by animateFloatAsState(
        targetValue = when (field.status) {
            FieldStatus.SCANNING -> 0.35f
            FieldStatus.PROVISIONAL -> 0.70f
            FieldStatus.LOCKED, FieldStatus.USER_LOCKED -> 1.0f
            FieldStatus.CONFLICT -> 0.70f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "chipAlpha",
    )

    val bgColor by animateColorAsState(
        targetValue = when (field.status) {
            FieldStatus.LOCKED, FieldStatus.USER_LOCKED -> READY_COLOR.copy(alpha = 0.25f)
            FieldStatus.PROVISIONAL -> Color.White.copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        label = "chipBg",
    )

    val emoji = fieldEmoji(field.fieldName)
    val displayValue = field.resolvedEvidence?.value
        ?: field.topCandidate?.rawVariants?.firstOrNull()
        ?: field.fieldName

    val statusLabel = when (field.status) {
        FieldStatus.SCANNING -> "scanning"
        FieldStatus.PROVISIONAL -> "candidate"
        FieldStatus.LOCKED -> "confirmed"
        FieldStatus.USER_LOCKED -> "confirmed by you"
        FieldStatus.CONFLICT -> "conflicting"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = Modifier
            .alpha(chipAlpha)
            .semantics {
                contentDescription = "${field.fieldName}: $displayValue, $statusLabel"
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(text = emoji, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = if (field.isResolved) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )

            // Edit icon for locked fields
            if (field.isResolved) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Reset ${field.fieldName}",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onReset(field.fieldName) },
                )
            }

            // Confirmed check for locked
            if (field.status == FieldStatus.LOCKED || field.status == FieldStatus.USER_LOCKED) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.label_confirmed),
                    tint = READY_COLOR,
                    modifier = Modifier.size(14.dp),
                )
            }

            // AI sparkle for LLM-contributed fields
            if (isLlmContributed) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.label_ai_contributed),
                    tint = Color(0xFF90CAF9),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// --- Conflict Chips (side-by-side tappable) ---

@Composable
private fun ConflictChips(
    field: FieldAccumulation,
    onResolve: (String, String) -> Unit,
) {
    val topTwo = field.candidates.values
        .sortedByDescending { it.posteriorProbability }
        .take(2)

    val emoji = fieldEmoji(field.fieldName)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        topTwo.forEach { candidate ->
            val displayValue = candidate.rawVariants.firstOrNull() ?: candidate.normalizedValue
            Surface(
                onClick = { onResolve(field.fieldName, candidate.normalizedValue) },
                shape = MaterialTheme.shapes.small,
                color = CAUTION_COLOR.copy(alpha = 0.3f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = " (${candidate.observationCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

// --- Guidance Bar ---

@Composable
private fun GuidanceBar(guidance: ScanGuidance) {
    val bgColor = when (guidance.type) {
        GuidanceType.SCAN_COMPLETE -> READY_COLOR.copy(alpha = 0.2f)
        GuidanceType.MISSING_FIELD -> Color.White.copy(alpha = 0.1f)
        GuidanceType.FLIP_SUGGESTION -> CAUTION_COLOR.copy(alpha = 0.15f)
        GuidanceType.QUALITY_ISSUE -> CAUTION_COLOR.copy(alpha = 0.15f)
        GuidanceType.ALMOST_DONE -> READY_COLOR.copy(alpha = 0.15f)
    }
    val icon = when (guidance.type) {
        GuidanceType.SCAN_COMPLETE -> "✅"
        GuidanceType.MISSING_FIELD -> "👀"
        GuidanceType.FLIP_SUGGESTION -> "🔄"
        GuidanceType.QUALITY_ISSUE -> "⚠️"
        GuidanceType.ALMOST_DONE -> "🎯"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = icon, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = guidance.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

// --- Manual Capture Fallback ---

@Composable
private fun ManualCaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manualCaptureDesc = stringResource(R.string.cd_manual_capture)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f),
        modifier = modifier
            .size(48.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .semantics { contentDescription = manualCaptureDesc },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// --- LLM Status Chip ---

private val LLM_COLOR_CONNECTING = Color(0xFFFFC107)
private val LLM_COLOR_WAITING = Color.Gray.copy(alpha = 0.7f)
private val LLM_COLOR_PROCESSING = Color(0xFF2196F3)
private val LLM_COLOR_COMPLETED = Color(0xFF4CAF50)
private val LLM_COLOR_FAILED = Color(0xFFF44336)
private val LLM_COLOR_UNAVAILABLE = Color.Gray.copy(alpha = 0.4f)

@Composable
private fun LlmStatusChip(
    status: LlmUiStatus,
    contributedFields: Set<String>,
    modifier: Modifier = Modifier,
) {
    if (status == LlmUiStatus.IDLE) return

    val (label, color) = when (status) {
        LlmUiStatus.IDLE -> "" to Color.Gray
        LlmUiStatus.CONNECTING -> "AI connecting…" to LLM_COLOR_CONNECTING
        LlmUiStatus.WAITING -> "AI ready" to LLM_COLOR_WAITING
        LlmUiStatus.PROCESSING -> "AI analyzing…" to LLM_COLOR_PROCESSING
        LlmUiStatus.COMPLETED -> "AI: +${contributedFields.size} fields" to LLM_COLOR_COMPLETED
        LlmUiStatus.FAILED -> "AI failed" to LLM_COLOR_FAILED
        LlmUiStatus.UNAVAILABLE -> "AI unavailable" to LLM_COLOR_UNAVAILABLE
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            when (status) {
                LlmUiStatus.PROCESSING -> {
                    LoadingIndicator(
                        modifier = Modifier.size(12.dp),
                        color = color,
                    )
                }
                LlmUiStatus.COMPLETED -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                }
                LlmUiStatus.FAILED -> {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// --- Helpers ---

private fun fieldEmoji(fieldName: String): String = when (fieldName) {
    "name" -> "☕"
    "roaster" -> "🏭"
    "origin" -> "🌍"
    "region" -> "📍"
    "farm" -> "🌱"
    "variety" -> "🫘"
    "processType" -> "⚙️"
    "altitude" -> "⛰️"
    "tastingNotes" -> "👅"
    "roastLevel" -> "🔥"
    "roastDate" -> "📅"
    "expiryDate" -> "🗓️"
    "weight" -> "⚖️"
    "isDecaf" -> "🌙"
    else -> "📋"
}
