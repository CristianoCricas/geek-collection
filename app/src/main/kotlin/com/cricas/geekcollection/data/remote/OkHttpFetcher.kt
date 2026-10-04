package com.cricas.geekcollection.data.remote

import com.cricas.geekcollection.core.lookup.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private companion object {
        const val DEFAULT_USER_AGENT = "GeekCollection/1.0 (Android; +https://github.com/CristianoCricas/geek-collection)"
    }
}
