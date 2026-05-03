package com.adsamcik.starlitcoffee.ui.screen

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
                Button(onClick = onBack) {
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

    // Background executor for image analysis + ML Kit listener callbacks.
    // Running on Main caused frame analysis (and `imageProxy.close()`) to
    // contend with UI rendering, which produced visible camera-preview hitches
    // whenever the main thread was busy.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    // Holds CameraX + ML Kit references so onDispose can tear down in the
    // correct order. CameraX is bound to a lifecycle but unbinds lazily;
    // shutting down the analysis executor before clearing the analyzer leads
    // to RejectedExecutionException for in-flight frames, and the ML Kit
    // BarcodeScanner holds native resources that need explicit close().
    val cameraResources = remember { BarcodeCameraResources() }
    DisposableEffect(analysisExecutor) {
        onDispose {
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

private fun analyzeBarcode(
    imageProxy: ImageProxy,
    hasDetected: Boolean,
    listenerExecutor: java.util.concurrent.Executor,
    mainExecutor: java.util.concurrent.Executor,
    onDetected: (String) -> Unit,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && !hasDetected) {
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        scanner.process(inputImage)
            // Listener callbacks run on the analyzer's background executor so
            // ML Kit work and `imageProxy.close()` never touch Main. We only
            // hop to the main executor for the actual Compose state write
            // (via `onDetected`) which navigates / flips `hasDetected`.
            .addOnSuccessListener(listenerExecutor) { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                mainExecutor.execute { onDetected(value) }
            }
            .addOnCompleteListener(listenerExecutor) {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
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
    @Volatile
    var cameraProvider: ProcessCameraProvider? = null

    @Volatile
    var imageAnalysis: ImageAnalysis? = null

    @Volatile
    var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null

    /**
     * Tear down resources in the correct order: stop CameraX from dispatching
     * new frames to the analyzer (clearAnalyzer + unbindAll), then close the
     * ML Kit scanner. The analysis executor must be shut down by the caller
     * *after* this returns — frames in-flight will already have completed
     * via the cleared analyzer, and no new ones will arrive.
     */
    fun release() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        scanner?.close()
        imageAnalysis = null
        cameraProvider = null
        scanner = null
    }
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
    hasDetected: () -> Boolean,
    onBarcodeDetected: (String) -> Unit,
): PreviewView {
    val previewView = PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val barcodeScanner = BarcodeScanning.getClient()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        analyzeBarcode(
                            imageProxy = imageProxy,
                            hasDetected = hasDetected(),
                            listenerExecutor = analysisExecutor,
                            mainExecutor = mainExecutor,
                            onDetected = onBarcodeDetected,
                            scanner = barcodeScanner,
                        )
                    }
                }
            // Publish references for orderly teardown by DisposableEffect.
            // Set BEFORE bindToLifecycle so a dispose racing this listener
            // already has a chance to unbind whatever is wired up.
            resources.cameraProvider = cameraProvider
            resources.imageAnalysis = analysis
            resources.scanner = barcodeScanner
            // Boundary catch: `bindToLifecycle` can raise IllegalState,
            // CameraInfoUnavailable, or various initialization errors that
            // the user can't act on — graceful degradation (no scanner) is
            // the desired UX.
            @Suppress("TooGenericExceptionCaught")
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bind camera for barcode scanning", e)
            }
        },
        mainExecutor,
    )
    return previewView
}
