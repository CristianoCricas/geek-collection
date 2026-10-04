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

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile == coversDir && file.exists()) file.delete()
    }

    suspend fun clearCaptures() = withContext(Dispatchers.IO) {
        capturesDir.listFiles()?.forEach { it.delete() }
    }
}
