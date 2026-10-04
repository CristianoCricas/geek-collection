package com.cricas.geekcollection.core.recognition

/** A label produced by an on-device image classifier. */
data class RecognizedLabel(val text: String, val confidence: Float)

/**
 * Everything the on-device recognizers extracted from a picture.
 * The platform-specific layer (ML Kit on Android) produces this object and
 * the pure Kotlin heuristics in this package interpret it.
 */
data class RecognitionResult(
    val barcodes: List<String> = emptyList(),
    val textLines: List<String> = emptyList(),
    val labels: List<RecognizedLabel> = emptyList(),
) {
    val isEmpty: Boolean get() = barcodes.isEmpty() && textLines.isEmpty() && labels.isEmpty()
}
