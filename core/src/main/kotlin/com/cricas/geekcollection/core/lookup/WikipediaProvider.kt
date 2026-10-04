package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory

/**
 * General purpose fallback that works for anything (consoles, accessories,
 * action figures, collectibles) using the Wikipedia search API.
 */
class WikipediaProvider(
    private val http: HttpFetcher,
    private val language: () -> String = { "pt" },
) : LookupProvider {

    override val id: String = "wikipedia"
    override val categories: Set<ItemCategory> = emptySet()

    override suspend fun searchByTitle(title: String, limit: Int): List<LookupResult> {
        val lang = language()
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrsearch=${title.urlEncode()}&gsrlimit=$limit&prop=pageimages%7Cextracts" +
            "&exintro=1&explaintext=1&exsentences=3&exlimit=$limit&piprop=thumbnail&pithumbsize=600"
        return parse(http.get(url, mapOf("User-Agent" to USER_AGENT)))
    }

    internal fun parse(body: String): List<LookupResult> {
        val pages = parseJson(body)["query"]["pages"].obj ?: return emptyList()
        return pages.values
            .sortedBy { it["index"].int ?: Int.MAX_VALUE }
            .mapNotNull { page ->
                val name = page["title"].str ?: return@mapNotNull null
                val extract = page["extract"].str
                LookupResult(
                    title = name,
                    category = null,
                    source = id,
                    externalId = page["pageid"].int?.toString(),
                    description = extract,
                    releaseYear = extract.extractYear(),
                    coverUrl = page["thumbnail"]["source"].str,
                )
            }
    }

    companion object {
        const val USER_AGENT = "GeekCollection/1.0 (Android; github.com/CristianoCricas/geek-collection)"
    }
}
