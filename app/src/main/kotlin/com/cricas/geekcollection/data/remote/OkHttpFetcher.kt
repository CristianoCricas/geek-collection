package com.cricas.geekcollection.data.remote

import com.cricas.geekcollection.core.lookup.HttpException
import com.cricas.geekcollection.core.lookup.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : HttpFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            header("User-Agent", DEFAULT_USER_AGENT)
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Resposta vazia")
        }
    }

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
        method: String,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            header("User-Agent", DEFAULT_USER_AGENT)
            headers.forEach { (k, v) -> header(k, v) }
            method(method, body.toRequestBody(contentType.toMediaType()))
        }.build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpException(response.code, errorMessage(text, response.code))
            text
        }
    }

    /** Extracts Google's `error.message` (e.g. EMAIL_EXISTS) from a JSON error body. */
    private fun errorMessage(body: String, code: Int): String {
        val match = Regex("\"message\"\s*:\s*\"([^\"]+)\"").find(body)
        return match?.groupValues?.get(1) ?: "HTTP $code"
    }

    private companion object {
        const val DEFAULT_USER_AGENT = "GeekCollection/1.0 (Android; +https://github.com/CristianoCricas/geek-collection)"
    }
}
