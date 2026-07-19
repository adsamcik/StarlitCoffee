package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adsamcik.starlitcoffee.R
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

private const val TAG = "BarcodeScannerScreen"

@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
){
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        BarcodeCamera(
            onBarcodeDetected = { barcode ->
                onBarcodeScanned(barcode)
            },
            onCancel = onBack,
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.msg_camera_permission),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack, colors = primaryActionButtonColors()) {
                    Text(stringResource(R.string.action_go_back))
                }
            }
        }
    }
}

@Composable
private fun BarcodeCamera(
    onBarcodeDetected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasDetected by remember { mutableStateOf(false) }

    // Background executor for CameraX image analysis. ML Kit completion callbacks
    // stay on Main so disposal never dispatches them onto this shut-down executor.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val lifecyclePolicy = remember { BarcodeCameraLifecyclePolicy() }
    // Holds CameraX + ML Kit references so onDispose can tear down in the
    // correct order. CameraX is bound to a lifecycle but unbinds lazily;
    // shutting down the analysis executor before clearing the analyzer leads
    // to RejectedExecutionException for in-flight frames, and the ML Kit
    // BarcodeScanner holds native resources that need explicit close().
    val cameraResources = remember { BarcodeCameraResources() }
    DisposableEffect(analysisExecutor) {
        onDispose {
            lifecyclePolicy.dispose()
            cameraResources.release()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                createPreviewView(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    analysisExecutor = analysisExecutor,
                    mainExecutor = mainExecutor,
                    resources = cameraResources,
                    lifecyclePolicy = lifecyclePolicy,
                    hasDetected = { hasDetected },
                    onBarcodeDetected = { barcode ->
                        if (!hasDetected) {
                            hasDetected = true
                            onBarcodeDetected(barcode)
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_cancel),
                tint = Color.White,
            )
        }

        Text(
            text = stringResource(R.string.msg_point_camera),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeBarcode(
    imageProxy: ImageProxy,
    hasDetected: Boolean,
    mainExecutor: java.util.concurrent.Executor,
    onDetected: (String) -> Unit,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    lifecyclePolicy: BarcodeCameraLifecyclePolicy,
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && !hasDetected && lifecyclePolicy.isActive) {
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        try {
            scanner.process(inputImage)
                // The analysis executor is shut down during disposal. Keep ML Kit
                // completion callbacks on Main so an in-flight task can still close
                // its ImageProxy without dispatching onto that shut-down executor.
                .addOnSuccessListener(mainExecutor) { barcodes ->
                    if (!lifecyclePolicy.isActive) return@addOnSuccessListener
                    val value = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                    onDetected(value)
                }
                .addOnCompleteListener(mainExecutor) {
                    imageProxy.close()
                }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to process barcode frame", error)
            imageProxy.close()
        }
    } else {
        imageProxy.close()
    }
}

internal class BarcodeCameraLifecyclePolicy {
    private val disposed = AtomicBoolean(false)

    val isActive: Boolean
        get() = !disposed.get()

    fun dispose() {
        disposed.set(true)
    }

    fun continueOrRelease(release: () -> Unit): Boolean {
        if (isActive) return true
        release()
        return false
    }
}

/**
 * Mutable references the [BarcodeCamera] composable hands to
 * [createPreviewView] so its `DisposableEffect.onDispose` can unbind the
 * camera and close the scanner *before* shutting down the analysis
 * executor. Without this, CameraX kept dispatching frames to a shut-down
 * executor (`RejectedExecutionException`) and the ML Kit
 * [com.google.mlkit.vision.barcode.BarcodeScanner] (which holds native
 * resources) leaked.
 */
private class BarcodeCameraResources {
    private var released = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null

    @Synchronized
    fun install(
        cameraProvider: ProcessCameraProvider,
        imageAnalysis: ImageAnalysis,
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    ): Boolean {
        if (released) return false
        this.cameraProvider = cameraProvider
        this.imageAnalysis = imageAnalysis
        this.scanner = scanner
        return true
    }

    /**
     * Tear down resources in the correct order: stop CameraX from dispatching
     * new frames to the analyzer (clearAnalyzer + unbindAll), then close the
     * ML Kit scanner. The analysis executor must be shut down by the caller
     * *after* this returns — frames in-flight will already have completed
     * via the cleared analyzer, and no new ones will arrive.
     */
    fun release() {
        val snapshot = synchronized(this) {
            released = true
            CameraResourceSnapshot(cameraProvider, imageAnalysis, scanner).also {
                imageAnalysis = null
                cameraProvider = null
                scanner = null
            }
        }
        releaseCameraResources(
            cameraProvider = snapshot.cameraProvider,
            imageAnalysis = snapshot.imageAnalysis,
            scanner = snapshot.scanner,
        )
    }
}

private data class CameraResourceSnapshot(
    val cameraProvider: ProcessCameraProvider?,
    val imageAnalysis: ImageAnalysis?,
    val scanner: com.google.mlkit.vision.barcode.BarcodeScanner?,
)

private fun releaseCameraResources(
    cameraProvider: ProcessCameraProvider?,
    imageAnalysis: ImageAnalysis?,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner?,
) {
    runCatching { imageAnalysis?.clearAnalyzer() }
        .onFailure { Log.w(TAG, "Failed to clear barcode analyzer", it) }
    runCatching { cameraProvider?.unbindAll() }
        .onFailure { Log.w(TAG, "Failed to unbind barcode camera", it) }
    runCatching { scanner?.close() }
        .onFailure { Log.w(TAG, "Failed to close barcode scanner", it) }
}

/**
 * Build the CameraX [PreviewView] for the barcode scanner. Extracted from
 * [BarcodeCamera] to keep that composable's body small enough to satisfy
 * detekt's LongMethod check while still leaving the camera-binding logic
 * inline with the analyzer wiring it depends on.
 */
private fun createPreviewView(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    analysisExecutor: java.util.concurrent.Executor,
    mainExecutor: java.util.concurrent.Executor,
    resources: BarcodeCameraResources,
    lifecyclePolicy: BarcodeCameraLifecyclePolicy,
    hasDetected: () -> Boolean,
    onBarcodeDetected: (String) -> Unit,
): PreviewView {
    val previewView = PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            if (!lifecyclePolicy.isActive) return@addListener
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            if (!lifecyclePolicy.continueOrRelease { cameraProvider.unbindAll() }) {
                return@addListener
            }
            val barcodeScanner = BarcodeScanning.getClient()
            if (!lifecyclePolicy.continueOrRelease {
                    releaseCameraResources(cameraProvider, null, barcodeScanner)
                }
            ) {
                return@addListener
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            if (!lifecyclePolicy.continueOrRelease {
                    releaseCameraResources(cameraProvider, analysis, barcodeScanner)
                }
            ) {
                return@addListener
            }
            try {
                analysis.also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (!lifecyclePolicy.isActive) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        analyzeBarcode(
                            imageProxy = imageProxy,
                            hasDetected = hasDetected(),
                            mainExecutor = mainExecutor,
                            onDetected = onBarcodeDetected,
                            scanner = barcodeScanner,
                            lifecyclePolicy = lifecyclePolicy,
                        )
                    }
                }
            } catch (error: Exception) {
                releaseCameraResources(cameraProvider, analysis, barcodeScanner)
                if (lifecyclePolicy.isActive) {
                    Log.w(TAG, "Failed to install barcode analyzer", error)
                }
                return@addListener
            }
            if (!resources.install(cameraProvider, analysis, barcodeScanner)) {
                releaseCameraResources(cameraProvider, analysis, barcodeScanner)
                return@addListener
            }
            if (!lifecyclePolicy.continueOrRelease(resources::release)) {
                return@addListener
            }
            // Boundary catch: `bindToLifecycle` can raise IllegalState,
            // CameraInfoUnavailable, or various initialization errors that
            // the user can't act on — graceful degradation (no scanner) is
            // the desired UX.
            @Suppress("TooGenericExceptionCaught")
            try {
                cameraProvider.unbindAll()
                if (!lifecyclePolicy.continueOrRelease(resources::release)) {
                    return@addListener
                }
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                if (!lifecyclePolicy.isActive) {
                    resources.release()
                }
            } catch (e: Exception) {
                resources.release()
                Log.w(TAG, "Failed to bind camera for barcode scanning", e)
            }
        },
        mainExecutor,
    )
    return previewView
}
