package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adsamcik.starlitcoffee.viewmodel.BagScanCaptureViewModel

/** Brewing keeps its pack-selection result contract while sharing the coffee camera and import. */
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
) {
    val captureViewModel: BagScanCaptureViewModel = viewModel()
    GuidedCaptureScreen(captureViewModel, onBack, onBarcodeScanned)
}
