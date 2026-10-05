package com.cricas.geekcollection.core.lookup

/**
 * Minimal HTTP abstraction so the lookup providers stay free of any
 * networking library and can be unit tested with canned responses.
 */
interface HttpFetcher {
    /** Performs a GET request and returns the response body, throwing on failure. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Performs a POST (or PATCH) with the given body and returns the response body.
     * Implementations throw [HttpException] on non-2xx responses.
     */
    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        method: String = "POST",
    ): String = throw UnsupportedOperationException("POST not supported")
}

class HttpException(val status: Int, message: String) : RuntimeException(message)

internal fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
