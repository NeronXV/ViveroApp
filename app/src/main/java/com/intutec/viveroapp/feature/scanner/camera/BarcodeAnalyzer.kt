package com.intutec.viveroapp.feature.scanner.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.intutec.viveroapp.feature.scanner.domain.model.ScanFormat
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer(
    private val onCodeDetected: (String, ScanFormat) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val duplicateGuard: DuplicateScanGuard = DuplicateScanGuard(),
) : ImageAnalysis.Analyzer, Closeable {
    private val processing = AtomicBoolean(false)
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
            )
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.let { barcode ->
                    val value = barcode.rawValue.orEmpty().trim()
                    val format = barcode.format.toScanFormat() ?: return@let
                    if (duplicateGuard.shouldAccept(value)) onCodeDetected(value, format)
                }
            }
            .addOnFailureListener(onFailure)
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    fun reset() = duplicateGuard.reset()

    override fun close() = scanner.close()
}

private fun Int.toScanFormat(): ScanFormat? = when (this) {
    Barcode.FORMAT_QR_CODE -> ScanFormat.QR
    Barcode.FORMAT_EAN_13 -> ScanFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> ScanFormat.EAN_8
    Barcode.FORMAT_CODE_128 -> ScanFormat.CODE_128
    else -> null
}
