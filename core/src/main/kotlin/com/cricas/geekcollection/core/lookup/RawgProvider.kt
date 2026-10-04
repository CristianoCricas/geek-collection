package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.Platforms

/**
 * Video games, via the RAWG database. RAWG requires a free API key that the
 * user pastes in the settings screen.
 */
class RawgProvider(
    private val http: HttpFetcher,
    private val apiKeyProvider: () -> String?,
    /** Platform the user is interested in; used to pick one of the game's platforms. */
    private val preferredPlatform: () -> String? = { null },
) : LookupProvider {

    override val id: String = "rawg"
    override val categories: Set<ItemCategory> = setOf(ItemCategory.VIDEO_GAME)

    val isConfigured: Boolean get() = !apiKeyProvider().isNullOrBlank()

    override suspend fun searchByTitle(title: String, limit: Int): List<LookupResult> {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val url = "https://api.rawg.io/api/games?key=$key&search=${title.urlEncode()}&page_size=$limit&search_precise=true"
        return parseSearch(http.get(url), preferredPlatform())
    }

    internal fun parseSearch(body: String, preferred: String?): List<LookupResult> {
        val json = parseJson(body)
        return json["results"].arr.orEmpty().mapNotNull { game ->
            val name = game["name"].str ?: return@mapNotNull null
            val platforms = game["platforms"].arr.orEmpty().mapNotNull { it["platform"]["name"].str }
            LookupResult(
                title = name,
                category = ItemCategory.VIDEO_GAME,
                source = id,
                externalId = game["id"].int?.toString(),
                platform = pickPlatform(platforms, preferred),
                releaseYear = game["released"].str.extractYear(),
                coverUrl = game["background_image"].str,
            )
        }
    }

    private fun pickPlatform(platforms: List<String>, preferred: String?): String? {
        if (platforms.isEmpty()) return null
        if (preferred != null) {
            platforms.firstOrNull { it.equals(preferred, ignoreCase = true) }?.let { return normalize(it) }
        }
        return normalize(platforms.first())
    }

    /** Maps RAWG platform names onto our curated list when possible. */
    private fun normalize(name: String): String {
        Platforms.byName(name)?.let { return it.name }
        val lower = name.lowercase()
        return Platforms.all.firstOrNull { p -> p.aliases.any { it.lowercase() == lower } }?.name ?: name
    }
}
