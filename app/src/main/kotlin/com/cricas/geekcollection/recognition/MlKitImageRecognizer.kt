package com.cricas.geekcollection.recognition

import android.content.Context
import android.net.Uri
import com.cricas.geekcollection.core.recognition.RecognitionResult
import com.cricas.geekcollection.core.recognition.RecognizedLabel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Runs the on-device ML Kit models (barcode, OCR and image labeling) over a
 * picture and converts the output into the platform-agnostic [RecognitionResult].
 */
class MlKitImageRecognizer(private val context: Context) {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
                )
                .build()
        )
    }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
    }

    suspend fun recognize(uri: Uri): RecognitionResult {
        val image = InputImage.fromFilePath(context, uri)
        return coroutineScope {
            val barcodes = async {
                runCatching { barcodeScanner.process(image).await() }.getOrDefault(emptyList())
                    .mapNotNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
            }
            val text = async {
                runCatching { textRecognizer.process(image).await() }.getOrNull()
                    ?.textBlocks.orEmpty()
                    .flatMap { block -> block.lines.map { it.text } }
                    .filter { it.isNotBlank() }
            }
            val labels = async {
                runCatching { labeler.process(image).await() }.getOrDefault(emptyList())
                    .map { RecognizedLabel(it.text, it.confidence) }
                    .sortedByDescending { it.confidence }
            }
            RecognitionResult(
                barcodes = barcodes.await().distinct(),
                textLines = text.await(),
                labels = labels.await(),
            )
        }
    }
}
