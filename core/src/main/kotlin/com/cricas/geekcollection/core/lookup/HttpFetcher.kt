package com.cricas.geekcollection.core.lookup

/**
 * Minimal HTTP abstraction so the lookup providers stay free of any
 * networking library and can be unit tested with canned responses.
 */
interface HttpFetcher {
    /** Performs a GET request and returns the response body, throwing on failure. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
}

internal fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
