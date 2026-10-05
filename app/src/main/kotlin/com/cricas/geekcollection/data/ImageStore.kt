package com.cricas.geekcollection.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Manages pictures taken or picked by the user. */
class ImageStore(private val context: Context) {

    private val coversDir: File get() = File(context.filesDir, "covers").apply { mkdirs() }
    private val capturesDir: File get() = File(context.cacheDir, "captures").apply { mkdirs() }

    /** Creates a temporary file + content URI that the system camera can write to. */
    fun newCaptureUri(): Uri {
        val file = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Copies any readable URI (camera capture or gallery pick) into permanent storage. */
    suspend fun persist(source: Uri): String = withContext(Dispatchers.IO) {
        val target = File(coversDir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Não foi possível ler a imagem")
        target.absolutePath
    }

    /** Writes raw image bytes (e.g. a thumbnail received from the cloud) into permanent storage. */
    suspend fun saveBytes(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(coversDir, "${UUID.randomUUID()}.jpg")
        target.writeBytes(bytes)
        target.absolutePath
    }

    /** Small JPEG (max [maxSide] px) of a stored picture as a base64 data URL, or null. */
    suspend fun thumbnailDataUrl(path: String, maxSide: Int = 400, quality: Int = 70): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile == coversDir && file.exists()) file.delete()
    }

    suspend fun clearCaptures() = withContext(Dispatchers.IO) {
        capturesDir.listFiles()?.forEach { it.delete() }
    }
}
